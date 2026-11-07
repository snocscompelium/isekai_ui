package com.compelium.isekaiui.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.List;

/** Cortador de pedras: entrada, grade de opcoes (com rolagem) e resultado. */
public class IsekaiStonecutterScreen extends IsekaiStationScreen<StonecutterMenu> {
	private static final int COLS = 5;
	private static final int ROWS = 3;
	private static final int W = 9 + SLOT + 8 + COLS * SLOT + 8 + 8 + 26 + 9;
	private static final int H = 9 + ROWS * SLOT + 9;
	private int scrollRow;
	private int hovered = -1;

	public IsekaiStonecutterScreen(StonecutterMenu menu, Inventory inventory, Component title, Screen vanilla) {
		super(menu, inventory, title, vanilla, "stonecutter");
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
		return 2;
	}

	private static int inputX(Rect r) {
		return r.x() + 10;
	}

	private static int gridX(Rect r) {
		return inputX(r) + SLOT + 8;
	}

	private static int gridY(Rect r) {
		return r.y() + 10;
	}

	private static int resultX(Rect r) {
		return r.right() - 9 - 26 + 5;
	}

	private static int resultY(Rect r) {
		return r.y() + 10 + SLOT;
	}

	@Override
	protected void placeStation(Rect r) {
		setSlot(0, inputX(r), resultY(r));
		setSlot(1, resultX(r), resultY(r));
	}

	private List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> options() {
		return this.menu.getVisibleRecipes().entries();
	}

	private int maxScroll() {
		int rows = (this.menu.getNumberOfVisibleRecipes() + COLS - 1) / COLS;
		return Math.max(0, rows - ROWS);
	}

	@Override
	protected void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick) {
		drawSlot(g, inputX(r), resultY(r), alpha);
		drawResultSlot(g, resultX(r), resultY(r), 1, alpha);
		this.scrollRow = Mth.clamp(this.scrollRow, 0, maxScroll());
		this.hovered = -1;
		int gx = gridX(r);
		int gy = gridY(r);
		Draw.fill(g, gx - 2, gy - 2, gx + COLS * SLOT + 1, gy + ROWS * SLOT + 1, Draw.alpha(0x900A080E, alpha));
		if (!this.menu.hasInputItem() || this.minecraft.level == null) return;
		ContextMap ctx = SlotDisplayContext.fromLevel(this.minecraft.level);
		List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> list = options();
		int selected = this.menu.getSelectedRecipeIndex();
		for (int i = 0; i < COLS * ROWS; i++) {
			int idx = this.scrollRow * COLS + i;
			if (idx >= list.size()) break;
			int x = gx + (i % COLS) * SLOT;
			int y = gy + (i / COLS) * SLOT;
			boolean hover = mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
			if (hover && !isDraggingPanel()) this.hovered = idx;
			Draw.sprite(g, Theme.SLOT, x - 1, y - 1, 18, 18, alpha);
			if (idx == selected) Draw.fill(g, x, y, x + 16, y + 16, Draw.alpha(0x66E6CB82, alpha));
			else if (hover) Draw.fill(g, x, y, x + 16, y + 16, Draw.alpha(0x30FFF2CF, alpha));
			ItemStack stack = list.get(idx).recipe().optionDisplay().resolveForFirstStack(ctx);
			if (!stack.isEmpty()) {
				g.renderItem(stack, x, y);
				g.renderItemDecorations(this.font, stack, x, y);
			}
		}
		if (maxScroll() > 0) {
			int trackX = gx + COLS * SLOT + 3;
			int trackH = ROWS * SLOT - 2;
			Draw.fill(g, trackX, gy, trackX + 3, gy + trackH, Draw.alpha(0xFF1A1620, alpha));
			int barH = Math.max(6, trackH * ROWS / (ROWS + maxScroll()));
			int barY = gy + (trackH - barH) * this.scrollRow / maxScroll();
			Draw.fill(g, trackX, barY, trackX + 3, barY + barH, Draw.alpha(Theme.GOLD, alpha));
		}
	}

	@Override
	protected boolean clickStation(Rect r, double mouseX, double mouseY, int button) {
		if (button != 0 || this.hovered < 0 || this.minecraft.player == null || this.minecraft.gameMode == null) return false;
		int idx = this.hovered;
		if (this.menu.clickMenuButton(this.minecraft.player, idx)) {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0f));
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, idx);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.station != null && this.station.now.contains(mouseX, mouseY) && overlayAt(mouseX, mouseY) == null && maxScroll() > 0) {
			this.scrollRow = Mth.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected List<Component> stationTooltip(Rect r, int mouseX, int mouseY) {
		if (this.hovered < 0 || this.minecraft.level == null) return null;
		List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> list = options();
		if (this.hovered >= list.size()) return null;
		ItemStack stack = list.get(this.hovered).recipe().optionDisplay().resolveForFirstStack(SlotDisplayContext.fromLevel(this.minecraft.level));
		return stack.isEmpty() ? null : List.of(stack.getHoverName());
	}

	@Override
	protected RecipeBookPanel.Tab grimoireTab() {
		return RecipeBookPanel.Tab.STONECUTTING;
	}
}
