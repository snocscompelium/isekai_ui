package com.compelium.isekaiui.tooltip;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

/** Conteudo de uma shulker box para mostrar no tooltip. */
public record ShulkerPreview(List<ItemStack> items, int color) implements TooltipComponent {
	private static final int DEFAULT_COLOR = 0x8E6FA8;

	/** Cria a previa se o item for uma shulker box com alguma coisa dentro. */
	public static ShulkerPreview of(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock shulker)) return null;
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null || contents.nonEmptyStream().findAny().isEmpty()) return null;
		NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
		contents.copyInto(list);
		DyeColor dye = shulker.getColor();
		int color = dye == null ? DEFAULT_COLOR : (dye.getTextureDiffuseColor() & 0xFFFFFF);
		return new ShulkerPreview(list, color);
	}
}
