package com.compelium.isekaiui.client;

import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.gui.Draw;
import com.compelium.isekaiui.gui.HudLayoutScreen;
import com.compelium.isekaiui.gui.IsekaiMapScreen;
import com.compelium.isekaiui.gui.IsekaiMenuScreen;
import com.compelium.isekaiui.gui.Rect;
import com.compelium.isekaiui.gui.SettingsScreen;
import com.compelium.isekaiui.gui.SkyDial;
import com.compelium.isekaiui.gui.Theme;
import com.compelium.isekaiui.lands.LandsTracker;
import com.compelium.isekaiui.map.DimMap;
import com.compelium.isekaiui.map.MapIcons;
import com.compelium.isekaiui.map.MapManager;
import com.compelium.isekaiui.map.MapRenderer;
import com.compelium.isekaiui.map.MapView;
import com.compelium.isekaiui.map.Waypoints;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * O que aparece na tela durante o jogo (so com os instrumentos certos): minimapa com bussola no inventario
 * e marcadores no horizonte segurando a bussola. Nada aparece com uma tela aberta, F1 ou F3.
 */
public final class HudOverlay {
	private static final MapView VIEW = new MapView();

	private HudOverlay() {
	}

	/** Altura reservada embaixo do minimapa para o nome do territorio e as coordenadas. */
	public static final int LABELS_H = 22;
	private static final int MARGIN = 11;

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
		if (mc.screen instanceof IsekaiMenuScreen<?> || mc.screen instanceof IsekaiMapScreen
				|| mc.screen instanceof SettingsScreen || mc.screen instanceof HudLayoutScreen) return;
		IsekaiConfig.InstrumentSettings cfg = IsekaiConfig.get().instruments;
		float pt = delta.getGameTimeDeltaPartialTick(false);
		if (cfg.horizonMarkers && Instruments.holdingCompass()) horizon(g, mc, pt);
		if (cfg.hudMinimap && Instruments.hasCompass()) {
			drawMinimap(g, mc, p, minimapRect(g.guiWidth(), g.guiHeight()), pt, 1f);
		}
	}

	/** Onde fica o mapa (sem a moldura): na posicao arrastada pelo jogador ou no canto escolhido. */
	public static Rect minimapRect(int W, int H) {
		IsekaiConfig.InstrumentSettings cfg = IsekaiConfig.get().instruments;
		int size = Mth.clamp(cfg.hudSize, 48, 160);
		if (cfg.hudX >= 0f && cfg.hudY >= 0f) {
			int x = MARGIN + Math.round(Mth.clamp(cfg.hudX, 0f, 1f) * Math.max(0, W - 2 * MARGIN - size));
			int y = MARGIN + Math.round(Mth.clamp(cfg.hudY, 0f, 1f) * Math.max(0, H - 2 * MARGIN - size - LABELS_H));
			return new Rect(x, y, size, size);
		}
		String corner = cfg.hudCorner == null ? "top_left" : cfg.hudCorner;
		boolean right = corner.endsWith("right");
		boolean bottom = corner.startsWith("bottom");
		int x = right ? W - MARGIN - size : MARGIN;
		int y = bottom ? H - MARGIN - size - LABELS_H : MARGIN;
		return new Rect(x, y, size, size);
	}

	/** Converte um canto superior-esquerdo arrastado para a posicao salva (0 a 1). */
	public static void savePosition(int x, int y, int W, int H) {
		IsekaiConfig.InstrumentSettings cfg = IsekaiConfig.get().instruments;
		int size = Mth.clamp(cfg.hudSize, 48, 160);
		int freeX = Math.max(1, W - 2 * MARGIN - size);
		int freeY = Math.max(1, H - 2 * MARGIN - size - LABELS_H);
		cfg.hudX = Mth.clamp((x - MARGIN) / (float) freeX, 0f, 1f);
		cfg.hudY = Mth.clamp((y - MARGIN) / (float) freeY, 0f, 1f);
	}

	/** Desenha o minimapa do HUD em r (tambem usado pela tela de posicionar, mesmo sem bussola). */
	public static void drawMinimap(GuiGraphics g, Minecraft mc, LocalPlayer p, Rect r, float pt, float alpha) {
		DimMap map = MapManager.current();
		boolean dialLeft = r.cx() > g.guiWidth() / 2;
		Draw.fill(g, r.x() - 2, r.y() + 3, r.right() + 6, r.bottom() + 9, Draw.alpha(Theme.SHADOW, alpha));
		if (map != null && p != null) {
			VIEW.cx = Mth.lerp(pt, p.xo, p.getX());
			VIEW.cz = Mth.lerp(pt, p.zo, p.getZ());
			VIEW.zoom = IsekaiConfig.get().map.minimapZoom;
			MapRenderer.draw(g, map, VIEW, r, alpha, pt, true);
		} else {
			Draw.fill(g, r.x(), r.y(), r.right(), r.bottom(), Draw.alpha(0xFFE3D3A8, alpha));
		}
		Draw.sprite(g, Theme.MAP_FRAME, r.x() - 5, r.y() - 5, r.w() + 10, r.h() + 10, alpha);
		SkyDial.draw(g, mc.font, dialLeft ? r.x() + 1 : r.right() - 1, r.y() + 1, alpha);
		int ty = r.bottom() + 7;
		String land = LandsTracker.currentLand();
		if (land != null) {
			Draw.textCentered(g, mc.font, Component.literal(land), r.cx(), ty, Draw.alpha(0xFF000000 | Theme.landColor(land), alpha), true);
			ty += 10;
		}
		if (p != null) {
			BlockPos bp = p.blockPosition();
			Component coords = Component.literal("X " + bp.getX() + "  Y " + bp.getY() + "  Z " + bp.getZ());
			Draw.textCentered(g, mc.font, coords, r.cx(), ty, Draw.alpha(Theme.TEXT, alpha), true);
		}
	}

	/** Marcadores no horizonte: cada marcador aparece na direcao em que esta, com o nome e a distancia. */
	private static void horizon(GuiGraphics g, Minecraft mc, float pt) {
		DimMap map = MapManager.current();
		if (map == null) return;
		Camera cam = mc.gameRenderer.getMainCamera();
		Vec3 pos = cam.getPosition();
		float yaw = cam.getYRot();
		float pitch = cam.getXRot();
		int W = g.guiWidth();
		int H = g.guiHeight();
		double tanV = Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
		double tanH = tanV * W / (double) H;
		for (Waypoints.Waypoint w : map.waypoints().all()) {
			double dx = w.x + 0.5 - pos.x;
			double dy = w.y + 1.0 - pos.y;
			double dz = w.z + 0.5 - pos.z;
			double horiz = Math.sqrt(dx * dx + dz * dz);
			if (horiz < 2.0) continue;
			float yawTo = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
			float rel = Mth.wrapDegrees(yawTo - yaw);
			if (Math.abs(rel) > 80f) continue;
			double sx = W / 2.0 + Math.tan(Math.toRadians(rel)) / tanH * W / 2.0;
			if (sx < -12 || sx > W + 12) continue;
			double pitchTo = -Math.toDegrees(Math.atan2(dy, horiz));
			double relP = Mth.clamp(pitchTo - pitch, -80.0, 80.0);
			double sy = H / 2.0 + Math.tan(Math.toRadians(relP)) / tanV * H / 2.0;
			sy = Mth.clamp(sy, 16.0, H - 64.0);
			float a = 1f - (float) Math.pow(Math.abs(rel) / 80f, 3) * 0.6f;
			int ix = (int) Math.round(sx);
			int iy = (int) Math.round(sy);
			MapIcons.get(w.icon).draw(g, ix, iy, 11, w.color, a);
			String name = w.name == null || w.name.isBlank() ? "" : w.name + "  ";
			Component label = Component.literal(name + Math.round(Math.sqrt(horiz * horiz + dy * dy)) + " m");
			Draw.push(g);
			Draw.translate(g, ix, iy + 8);
			Draw.scale(g, 0.75f);
			Draw.textCentered(g, mc.font, label, 0, 0, Draw.alpha(Theme.TEXT, a), true);
			Draw.pop(g);
		}
	}
}
