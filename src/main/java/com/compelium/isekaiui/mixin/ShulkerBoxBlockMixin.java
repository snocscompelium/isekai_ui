package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.tooltip.BackpackPreview;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Quando a grade aparece, esconde a lista em texto que o jogo mostra ("Diamante x3... e mais 4"). */
@Mixin(ShulkerBoxBlock.class)
public abstract class ShulkerBoxBlockMixin {
	@Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
	private void isekai$hideTextList(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag, CallbackInfo ci) {
		if (IsekaiConfig.get().shulkerPreview && BackpackPreview.shulker(stack) != null) ci.cancel();
	}
}
