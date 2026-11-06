package com.compelium.isekaiui.tooltip;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Conteudo de uma shulker box ou sacola para a previa "mochila" do tooltip.
 * items: o que aparece nos bolsos (vazios = ItemStack.EMPTY); extra: quantos itens nao couberam ("+N").
 */
public record BackpackPreview(List<ItemStack> items, int color, int columns, boolean bundle, int selected, float fullness, int extra,
		ItemStack selectedStack) implements TooltipComponent {
	private static final int SHULKER_DEFAULT = 0x8E6FA8;
	private static final int LEATHER = 0x9C6B3E;

	/** Shulker box com alguma coisa dentro (vazia continua com o tooltip normal). */
	public static BackpackPreview shulker(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock shulker)) return null;
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null || contents.nonEmptyStream().findAny().isEmpty()) return null;
		NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
		contents.copyInto(list);
		DyeColor dye = shulker.getColor();
		int color = dye == null ? SHULKER_DEFAULT : (dye.getTextureDiffuseColor() & 0xFFFFFF);
		return new BackpackPreview(list, color, 9, false, -1, 0f, 0, ItemStack.EMPTY);
	}

	/** Sacola (bundle): ate 12 itens a mostra, o resto vira "+N". */
	public static BackpackPreview bundle(ItemStack stack, BundleContents contents) {
		int color = LEATHER;
		for (DyeColor c : DyeColor.values()) {
			if (BundleItem.getByColor(c) == stack.getItem()) {
				color = c.getTextureDiffuseColor() & 0xFFFFFF;
				break;
			}
		}
		List<ItemStack> list = new ArrayList<>();
		int size = contents.size();
		int shown = size > BundleItem.MAX_SHOWN_GRID_ITEMS ? BundleItem.OVERFLOWING_MAX_SHOWN_GRID_ITEMS : size;
		for (int i = 0; i < shown; i++) list.add(contents.getItemUnsafe(i));
		int extra = 0;
		for (int i = shown; i < size; i++) extra += contents.getItemUnsafe(i).getCount();
		int selected = contents.hasSelectedItem() ? contents.getSelectedItem() : -1;
		ItemStack sel = selected >= 0 && selected < size ? contents.getItemUnsafe(selected) : ItemStack.EMPTY;
		if (selected >= shown) selected = -1;
		return new BackpackPreview(list, color, 4, true, selected, contents.weight().floatValue(), extra, sel);
	}
}
