package com.compelium.isekaiui.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;

/** Mesa de trabalho: grade 3x3, seta e resultado. O botao de livro abre o grimorio de receitas. */
public class IsekaiCraftingScreen extends IsekaiStationScreen<CraftingMenu> {
	private static final int W = 9 + 3 * SLOT + 8 + 22 + 8 + 26 + 9;
	private static final int H = 9 + 3 * SLOT + 9;

	public IsekaiCraftingScreen(CraftingMenu menu, Inventory inventory, Component title, Screen vanilla) {
		super(menu, inventory, title, vanilla, "crafting");
	}

	@Override
	protected int stationW() {
		return W;
	}

	@Override
	protected int stationH() {
		return H;
	}

	@Override
	protected int stationSlots() {
		return 10;
	}

	private static int gridX(Rect r) {
		return r.x() + 10;
	}

	private static int gridY(Rect r) {
		return r.y() + 10;
	}

	private static int resultX(Rect r) {
		return r.right() - 9 - 26 + 5;
	}

	private static int resultY(Rect r) {
		return r.y() + 10 + SLOT - 8 + 9;
	}

	@Override
	protected void placeStation(Rect r) {
		setSlot(0, resultX(r), resultY(r));
		for (int i = 0; i < 9; i++) setSlot(1 + i, gridX(r) + (i % 3) * SLOT, gridY(r) + (i / 3) * SLOT);
	}

	@Override
	protected void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick) {
		drawGrid(g, gridX(r), gridY(r), 9, 3, alpha);
		boolean has = !this.menu.slots.get(0).getItem().isEmpty();
		drawProgressArrow(g, gridX(r) + 3 * SLOT + 7, resultY(r) + 1, has ? 1f : 0f, alpha);
		drawResultSlot(g, resultX(r), resultY(r), 0, alpha);
	}

	@Override
	protected RecipeBookPanel.Tab grimoireTab() {
		return RecipeBookPanel.Tab.CRAFTING;
	}
}
