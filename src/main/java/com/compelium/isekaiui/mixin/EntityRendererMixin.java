package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.camera.PoseFlag;
import com.compelium.isekaiui.camera.PoseState;
import com.compelium.isekaiui.config.IsekaiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(
			method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
			at = @At("RETURN"))
	private void isekai$markLocalPlayer(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
		EntityRenderState state = cir.getReturnValue();
		if (state == null) return;
		float weight = PoseState.weight();
		boolean browsing = weight > 0.001f && entity == Minecraft.getInstance().player;
		((PoseFlag) state).isekai$setLocalBrowsing(browsing);
		if (browsing && weight > 0.25f && IsekaiConfig.get().hideHeldItemsWhileBrowsing && state instanceof ArmedEntityRenderState armed) {
			armed.rightHandItem.clear();
			armed.leftHandItem.clear();
		}
	}
}
