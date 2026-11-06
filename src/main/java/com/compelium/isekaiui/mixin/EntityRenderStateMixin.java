package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.camera.PoseFlag;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements PoseFlag {
	@Unique
	private boolean isekai$localBrowsing;

	@Override
	public boolean isekai$isLocalBrowsing() {
		return this.isekai$localBrowsing;
	}

	@Override
	public void isekai$setLocalBrowsing(boolean value) {
		this.isekai$localBrowsing = value;
	}
}
