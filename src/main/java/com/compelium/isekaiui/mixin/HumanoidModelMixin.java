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
	@Shadow
	@Final
	public ModelPart body;
	@Shadow
	@Final
	public ModelPart rightLeg;
	@Shadow
	@Final
	public ModelPart leftLeg;

	@Unique
	private boolean isekai$hidden;
	@Unique
	private final boolean[] isekai$prevVisible = new boolean[7];

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void isekai$browsePose(HumanoidRenderState state, CallbackInfo ci) {
		if (this.isekai$hidden) {
			ModelPart[] parts = isekai$parts();
			for (int i = 0; i < parts.length; i++) parts[i].visible = this.isekai$prevVisible[i];
			this.isekai$hidden = false;
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
		this.head.xRot = Mth.lerp(w, this.head.xRot, 0.14f);
		this.head.yRot = Mth.lerp(w, this.head.yRot, 0.10f);
		if (!this.head.hasChild("hat")) {
			this.hat.copyFrom(this.head);
		}

		// camera passando "por dentro" do personagem (comeco/fim da animacao): esconde o modelo
		if (CinematicCamera.nearHead()) {
			ModelPart[] parts = isekai$parts();
			for (int i = 0; i < parts.length; i++) {
				this.isekai$prevVisible[i] = parts[i].visible;
				parts[i].visible = false;
			}
			this.isekai$hidden = true;
		}
	}

	@Unique
	private ModelPart[] isekai$parts() {
		return new ModelPart[]{this.head, this.hat, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg};
	}
}
