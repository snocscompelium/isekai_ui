package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.tooltip.BackpackPreview;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Faz a shulker box mostrar a mochila com o conteudo no tooltip. */
@Mixin(Item.class)
public abstract class ItemMixin {
	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
	private void isekai$shulkerPreview(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (!IsekaiConfig.get().shulkerPreview || cir.getReturnValue().isPresent()) return;
		BackpackPreview preview = BackpackPreview.shulker(stack);
		if (preview != null) cir.setReturnValue(Optional.of(preview));
	}
}
