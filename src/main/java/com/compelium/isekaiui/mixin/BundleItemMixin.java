package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.tooltip.BackpackPreview;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** A sacola (bundle) tambem ganha a previa em forma de mochila (com o item escolhido e o quanto esta cheia). */
@Mixin(BundleItem.class)
public abstract class BundleItemMixin {
	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
	private void isekai$backpack(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (!IsekaiConfig.get().shulkerPreview) return;
		Optional<TooltipComponent> value = cir.getReturnValue();
		if (value.isPresent() && value.get() instanceof BundleTooltip bundle) {
			cir.setReturnValue(Optional.of(BackpackPreview.bundle(stack, bundle.contents())));
		}
	}
}
