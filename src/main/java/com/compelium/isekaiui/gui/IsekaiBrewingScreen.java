package com.compelium.isekaiui.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;

import java.util.List;

/** Suporte de pocoes: ingrediente em cima, 3 frascos embaixo, po de blaze a esquerda. Mostra o tempo restante. */
public class IsekaiBrewingScreen extends IsekaiStationScreen<BrewingStandMenu> {
	private static final int W = 150;
	private static final int H = 9 + SLOT + 26 + SLOT + 6 + 10 + 7;
	private static final int BREW_TICKS = 400;

	public IsekaiBrewingScreen(BrewingStandMenu menu, Inventory inventory, Component title, Screen vanilla) {
		super(menu, inventory, title, vanilla, "brewing");
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
		return 5;
	}

	private static int bottlesX(Rect r) {
		return r.cx() - (3 * SLOT + 2 * 6) / 2 + 1 + 10;
	}

	private static int bottlesY(Rect r) {
		return r.y() + 10 + SLOT + 26;
	}

	private static int ingredientX(Rect r) {
		return bottlesX(r) + SLOT + 6;
	}

	private static int fuelX(Rect r) {
		return r.x() + 14;
	}

	@Override
	protected void placeStation(Rect r) {
		for (int i = 0; i < 3; i++) setSlot(i, bottlesX(r) + i * (SLOT + 6), bottlesY(r) + (i == 1 ? 4 : 0));
		setSlot(3, ingredientX(r), r.y() + 10);
		setSlot(4, fuelX(r), r.y() + 10);
	}

	@Override
	protected void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick) {
		for (int i = 0; i < 3; i++) drawSlot(g, bottlesX(r) + i * (SLOT + 6), bottlesY(r) + (i == 1 ? 4 : 0), alpha);
		drawSlot(g, ingredientX(r), r.y() + 10, alpha);
		drawSlot(g, fuelX(r), r.y() + 10, alpha);
		// po de blaze: barrinha embaixo do slot
		int fuel = this.menu.getFuel();
		int fx = fuelX(r) - 1;
		int fy = r.y() + 10 + SLOT + 2;
		Draw.fill(g, fx, fy, fx + 18, fy + 4, Draw.alpha(0xFF1A1620, alpha));
		Draw.fill(g, fx + 1, fy + 1, fx + 1 + Math.round(16 * Math.min(1f, fuel / 20f)), fy + 3, Draw.alpha(0xFFF29A3A, alpha));
		// progresso: canos do ingrediente para os frascos
		int ticks = this.menu.getBrewingTicks();
		float progress = ticks > 0 ? 1f - ticks / (float) BREW_TICKS : 0f;
		int cx = ingredientX(r) + 8;
		int top = r.y() + 10 + SLOT + 2;
		int len = 20;
		Draw.fill(g, cx - 2, top, cx + 2, top + len, Draw.alpha(0xFF1A1620, alpha));
		if (progress > 0f) Draw.fill(g, cx - 1, top + 1, cx + 1, top + 1 + Math.round((len - 2) * progress), Draw.alpha(0xFFB57BEA, alpha));
		Draw.fill(g, bottlesX(r) + 8, top + len - 3, bottlesX(r) + 2 * (SLOT + 6) + 9, top + len - 1, Draw.alpha(0xFF1A1620, alpha));
		Component info = ticks > 0 ? Component.translatable("isekai_ui.station.ready_in", clock(ticks))
				: fuel > 0 ? Component.translatable("isekai_ui.station.blaze_charges", fuel) : null;
		if (info != null) {
			int w = this.font.width(info);
			smallText(g, info, r.cx() - w / 2, r.bottom() - 15, Theme.TEXT_DIM, alpha);
		}
	}

	@Override
	protected List<Component> stationTooltip(Rect r, int mouseX, int mouseY) {
		Rect bar = new Rect(fuelX(r) - 1, r.y() + 10 + SLOT, 18, 8);
		if (bar.contains(mouseX, mouseY)) return List.of(Component.translatable("isekai_ui.station.blaze_charges", this.menu.getFuel()));
		return null;
	}
}
