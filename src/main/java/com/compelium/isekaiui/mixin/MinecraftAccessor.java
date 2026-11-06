package com.compelium.isekaiui.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	/** O mesmo que apertar o botao direito do mouse (usar). */
	@Invoker("startUseItem")
	void isekai$startUseItem();
}
