package com.compelium.isekaiui.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.GrindstoneMenu;

/** Rebolo: dois itens empilhados, seta e resultado. */
public class IsekaiGrindstoneScreen extends IsekaiStationScreen<GrindstoneMenu> {
	private static final int W = 9 + SLOT + 12 + 22 + 8 + 26 + 9 + 30;
	private static final int H = 9 + 2 * SLOT + 4 + 9;

	public IsekaiGrindstoneScreen(GrindstoneMenu menu, Inventory inventory, Component title, Screen vanilla) {
		super(menu, inventory, title, vanilla, "grindstone");
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
		return 3;
	}

	private static int inputX(Rect r) {
		return r.x() + 25;
	}

	private static int resultX(Rect r) {
		return r.right() - 24 - 26 + 5;
	}

	private static int resultY(Rect r) {
		return r.y() + 10 + SLOT - 8 + 2;
	}

	@Override
	protected void placeStation(Rect r) {
		setSlot(0, inputX(r), r.y() + 10);
		setSlot(1, inputX(r), r.y() + 10 + SLOT + 4);
		setSlot(2, resultX(r), resultY(r));
	}

	@Override
	protected void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick) {
		drawSlot(g, inputX(r), r.y() + 10, alpha);
		drawSlot(g, inputX(r), r.y() + 10 + SLOT + 4, alpha);
		boolean has = this.menu.getSlot(2).hasItem();
		drawProgressArrow(g, inputX(r) + SLOT + 10, resultY(r) + 1, has ? 1f : 0f, alpha);
		drawResultSlot(g, resultX(r), resultY(r), 2, alpha);
	}
}
