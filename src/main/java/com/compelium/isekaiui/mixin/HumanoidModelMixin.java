package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.camera.CinematicCamera;
import com.compelium.isekaiui.camera.PoseFlag;
import com.compelium.isekaiui.camera.PoseState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pose do personagem "manipulando" a janela flutuante enquanto o inventario esta aberto. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Shadow
	@Final
	public ModelPart head;
	@Shadow
	@Final
	public ModelPart hat;
	@Shadow
	@Final
	public ModelPart rightArm;
	@Shadow
	@Final
	public ModelPart leftArm;

	@Unique
	private boolean isekai$headHidden;
	@Unique
	private boolean isekai$prevHeadVisible = true;
	@Unique
	private boolean isekai$prevHatVisible = true;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void isekai$browsePose(HumanoidRenderState state, CallbackInfo ci) {
		if (this.isekai$headHidden) {
			this.head.visible = this.isekai$prevHeadVisible;
			this.hat.visible = this.isekai$prevHatVisible;
			this.isekai$headHidden = false;
		}
		if (!(((Object) state) instanceof PoseFlag flag) || !flag.isekai$isLocalBrowsing()) return;
		float w = PoseState.weight();
		if (w <= 0.001f) return;

		float t = state.ageInTicks;
		float tap = PoseState.tapAmount();
		float breathe = Mth.sin(t * 0.085f) * 0.035f;
		float swipe = Mth.sin(t * 0.19f) * 0.06f;

		// braco direito: levantado tocando a janela; braco esquerdo: mais baixo, cruzando para frente
		float rx = -1.32f + breathe - tap * 0.32f;
		float ry = 0.34f + swipe;
		float rz = 0.06f;
		float lx = -0.78f - breathe * 0.6f;
		float ly = 0.66f;
		float lz = -0.08f;

		this.rightArm.xRot = Mth.lerp(w, this.rightArm.xRot, rx);
		this.rightArm.yRot = Mth.lerp(w, this.rightArm.yRot, ry);
		this.rightArm.zRot = Mth.lerp(w, this.rightArm.zRot, rz);
		this.leftArm.xRot = Mth.lerp(w, this.leftArm.xRot, lx);
		this.leftArm.yRot = Mth.lerp(w, this.leftArm.yRot, ly);
		this.leftArm.zRot = Mth.lerp(w, this.leftArm.zRot, lz);
		this.head.xRot = Mth.lerp(w, this.head.xRot, 0.30f);
		this.head.yRot = Mth.lerp(w, this.head.yRot, 0.30f);
		if (!this.head.hasChild("hat")) {
			this.hat.copyFrom(this.head);
		}

		if (CinematicCamera.nearHead()) {
			this.isekai$prevHeadVisible = this.head.visible;
			this.isekai$prevHatVisible = this.hat.visible;
			this.head.visible = false;
			this.hat.visible = false;
			this.isekai$headHidden = true;
		}
	}
}
