package com.compelium.isekaiui.camera;

import com.compelium.isekaiui.anim.Ease;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.gui.IsekaiInventoryScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Camera cinematografica do inventario: sai da cabeca do personagem e vai para tras/esquerda,
 * enquadrando o personagem do lado esquerdo da tela e a janela flutuante a frente dele.
 */
public final class CinematicCamera {
	private static final float OPEN_SECONDS = 0.45f;
	private static final float CLOSE_SECONDS = 0.35f;

	private static float progress;
	private static long lastNanos;
	private static CameraType savedType;
	private static boolean changedType;
	private static boolean haveBase;
	private static float baseYaw;
	private static double distanceFromEye = 99;

	public record Result(Vec3 pos, float yaw, float pitch) {
	}

	private CinematicCamera() {
	}

	/** Chamado uma vez por frame (inicio do Camera.setup). */
	public static void frame() {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;

		boolean want = mc.player != null && mc.screen instanceof IsekaiInventoryScreen screen && screen.wantsCinematicCamera();
		boolean animate = IsekaiConfig.get().animations;
		if (want) {
			if (progress <= 0f && mc.player != null) {
				baseYaw = mc.player.getYRot();
				haveBase = true;
			}
			progress = animate ? Math.min(1f, progress + dt / OPEN_SECONDS) : 1f;
			if (progress <= 0f) progress = 0.0001f;
		} else {
			progress = animate ? Math.max(0f, progress - dt / CLOSE_SECONDS) : 0f;
		}

		if (progress > 0f && !changedType && mc.options.getCameraType().isFirstPerson()) {
			savedType = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			changedType = true;
		} else if (progress <= 0f && changedType) {
			mc.options.setCameraType(savedType == null ? CameraType.FIRST_PERSON : savedType);
			changedType = false;
		}
		if (progress <= 0f) {
			haveBase = false;
			distanceFromEye = 99;
		}
	}

	public static boolean active() {
		return progress > 0f && haveBase;
	}

	public static float eased() {
		return active() ? Ease.inOutCubic(progress) : 0f;
	}

	/** True quando a camera ainda esta tao perto que veria o interior da cabeca. */
	public static boolean nearHead() {
		return active() && distanceFromEye < 0.8;
	}

	public static Result compute(Entity entity, Vec3 vanillaPos, float vanillaYaw, float vanillaPitch, float partialTick) {
		if (!active() || entity != Minecraft.getInstance().player) return null;
		IsekaiConfig cfg = IsekaiConfig.get();
		Vec3 eye = entity.getEyePosition(partialTick);

		double yawRad = Math.toRadians(baseYaw);
		Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
		Vec3 right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
		Vec3 desired = eye
				.add(forward.scale(-cfg.cameraDistance))
				.add(right.scale(-cfg.cameraSide))
				.add(0, cfg.cameraHeight, 0);
		Vec3 framed = clip(entity, eye, desired);

		Vec3 look = eye.add(0, -0.55, 0).subtract(framed);
		double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)) + framingYawOffset(cfg.characterScreenX);
		float pitch = (float) -Math.toDegrees(Math.atan2(look.y, horizontal)) + 4f;

		float e = eased();
		Vec3 start = changedType ? eye : vanillaPos;
		float startYaw = changedType ? entity.getViewYRot(partialTick) : vanillaYaw;
		float startPitch = changedType ? entity.getViewXRot(partialTick) : vanillaPitch;

		Vec3 pos = start.lerp(framed, e);
		distanceFromEye = pos.distanceTo(eye);
		return new Result(pos, Mth.rotLerp(e, startYaw, yaw), Mth.lerp(e, startPitch, pitch));
	}

	/**
	 * Quantos graus girar a camera para o personagem aparecer na posicao X desejada da tela,
	 * levando em conta o campo de visao (FOV) e o formato da janela.
	 */
	private static float framingYawOffset(float screenX) {
		Minecraft mc = Minecraft.getInstance();
		double fov = mc.options.fov().get();
		double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		double tanHalfH = Math.tan(Math.toRadians(fov) / 2.0) * aspect;
		double ndc = (0.5 - screenX) * 2.0;
		return (float) Math.toDegrees(Math.atan(ndc * tanHalfH));
	}

	/** Evita que a camera atravesse paredes (8 raios, como a camera vanilla). */
	private static Vec3 clip(Entity entity, Vec3 from, Vec3 to) {
		Vec3 dir = to.subtract(from);
		double len = dir.length();
		if (len < 1.0e-4) return to;
		double best = len;
		for (int i = 0; i < 8; i++) {
			double ox = ((i & 1) * 2 - 1) * 0.1;
			double oy = (((i >> 1) & 1) * 2 - 1) * 0.1;
			double oz = (((i >> 2) & 1) * 2 - 1) * 0.1;
			Vec3 a = from.add(ox, oy, oz);
			Vec3 b = to.add(ox, oy, oz);
			BlockHitResult hit = entity.level().clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
			if (hit.getType() != HitResult.Type.MISS) {
				double d = hit.getLocation().distanceTo(a);
				if (d < best) best = d;
			}
		}
		double safe = best >= len ? len : Math.max(0.15, best - 0.2);
		return from.add(dir.scale(safe / len));
	}
}
