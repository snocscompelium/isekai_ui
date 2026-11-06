package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.camera.CinematicCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Entity entity;
	@Shadow
	private boolean detached;
	@Shadow
	private Vec3 position;
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("HEAD"))
	private void isekai$frame(CallbackInfo ci) {
		CinematicCamera.frame();
	}

	@Inject(method = "setup", at = @At("TAIL"))
	private void isekai$cinematic(CallbackInfo ci) {
		if (!this.detached || this.entity == null || !CinematicCamera.active()) return;
		float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		CinematicCamera.Result result = CinematicCamera.compute(this.entity, this.position, this.yRot, this.xRot, partialTick);
		if (result != null) {
			this.setRotation(result.yaw(), result.pitch());
			this.setPosition(result.pos());
		}
	}
}
