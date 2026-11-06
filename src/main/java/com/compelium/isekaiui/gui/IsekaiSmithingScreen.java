package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.SmithingPreview;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Mesa de ferraria: molde, item e material, seta e resultado. Novidade: quando o resultado e uma peca de
 * armadura, o personagem (na camera em 3a pessoa) aparece vestindo o resultado, como um provador.
 * E so visual, no seu jogo: nada e enviado ao servidor.
 */
public class IsekaiSmithingScreen extends IsekaiStationScreen<SmithingMenu> {
	private static final int W = 9 + 3 * SLOT + 8 + 22 + 8 + 26 + 9;
	private static final int H = 9 + 26 + 6 + 10 + 7;

	public IsekaiSmithingScreen(SmithingMenu menu, Inventory inventory, Component title, Screen vanilla) {
		super(menu, inventory, title, vanilla, "smithing");
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
		return 4;
	}

	private static int rowY(Rect r) {
		return r.y() + 9 + 5;
	}

	private static int firstX(Rect r) {
		return r.x() + 10;
	}

	private static int resultX(Rect r) {
		return r.right() - 9 - 26 + 5;
	}

	@Override
	protected void placeStation(Rect r) {
		for (int i = 0; i < 3; i++) setSlot(i, firstX(r) + i * SLOT, rowY(r));
		setSlot(3, resultX(r), rowY(r));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		ItemStack result = this.menu.getSlot(3).getItem();
		SmithingPreview.set(isClosing() ? null : result);
	}

	@Override
	public void removed() {
		SmithingPreview.set(null);
		super.removed();
	}

	@Override
	protected void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick) {
		drawGrid(g, firstX(r), rowY(r), 3, 3, alpha);
		int arrowX = firstX(r) + 3 * SLOT + 7;
		boolean has = this.menu.getSlot(3).hasItem();
		drawProgressArrow(g, arrowX, rowY(r) + 1, has ? 1f : 0f, alpha);
		if (this.menu.hasRecipeError()) {
			Draw.sprite(g, Theme.icon("close"), arrowX + 5, rowY(r) + 2, 12, 12, Draw.alpha(0xFFE0465E, alpha));
		}
		drawResultSlot(g, resultX(r), rowY(r), 3, alpha);
		Component line = null;
		if (SmithingPreview.isArmor(this.menu.getSlot(3).getItem())) line = Component.translatable("isekai_ui.station.smithing_preview");
		else if (this.menu.hasRecipeError()) line = Component.translatable("isekai_ui.station.smithing_error");
		if (line != null) {
			int w = this.font.width(line);
			smallText(g, line, r.cx() - w / 2, r.bottom() - 15, Theme.TEXT_DIM, alpha);
		}
	}
}
