package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.config.IsekaiConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Set;

/**
 * Bau, bau grande, barril, bau do ender e shulker box no estilo isekai. So aparece quando o
 * titulo e o nome padrao do jogo: menus de plugin (que usam bau como "tela") ficam intactos.
 */
public class IsekaiContainerScreen extends IsekaiMenuScreen<AbstractContainerMenu> {
	private static final Set<String> VANILLA_TITLES = Set.of(
			"container.chest", "container.chestDouble", "container.shulkerBox", "container.enderchest", "container.barrel");
	private static boolean vanillaBypass;

	private final int rows;
	private final Screen vanilla;
	private Panel storage;
	private Panel bag;
	private int storageX;
	private int storageY;
	private int bagX;
	private int bagY;
	private int hotbarX;
	private int hotbarY;

	public IsekaiContainerScreen(AbstractContainerMenu menu, Inventory inventory, Component title, int rows, Screen vanilla) {
		super(menu, inventory, title);
		this.rows = rows;
		this.vanilla = vanilla;
	}

	/** True se for um bau/shulker "de verdade" (titulo padrao do jogo, sem fonte de resource pack). */
	public static boolean isVanillaStorage(Component title) {
		if (title == null || !title.getSiblings().isEmpty()) return false;
		if (!(title.getContents() instanceof TranslatableContents tc)) return false;
		Style style = title.getStyle();
		if (style.getFont() != null && !style.getFont().equals(Style.DEFAULT_FONT)) return false;
		return VANILLA_TITLES.contains(tc.getKey()) && IsekaiConfig.get().enableContainers;
	}

	public static boolean consumeVanillaBypass() {
		boolean b = vanillaBypass;
		vanillaBypass = false;
		return b;
	}

	@Override
	protected String layoutKey() {
		return "container";
	}

	@Override
	protected boolean screenFits() {
		return this.menu.slots.size() >= this.rows * 9 + 36 && layout(this.width, this.height) != null;
	}

	private static final int PANEL_W = 9 * InventoryLayout.SLOT + InventoryLayout.PAD * 2;
	private static final int BAG_H = 8 + 3 * InventoryLayout.SLOT + 9 + 30 + 7;

	/** Posicao padrao das duas janelas (bau em cima e bolsa embaixo, ou lado a lado); null se nao couber. */
	private Rect[] layout(int W, int H) {
		int storageH = 8 + this.rows * InventoryLayout.SLOT + 8;
		int tab = InventoryLayout.TAB;
		int stacked = tab + storageH + 10 + tab + BAG_H;
		if (stacked <= H - 10 && PANEL_W + 12 <= W) {
			int x = InventoryLayout.mainColumnX(W, PANEL_W, PANEL_W);
			int top = (H - stacked) / 2 + tab;
			Rect s = new Rect(x, top, PANEL_W, storageH);
			return new Rect[]{s, new Rect(x, s.bottom() + 10 + tab, PANEL_W, BAG_H)};
		}
		int pairW = PANEL_W * 2 + 12;
		if (pairW + 12 <= W && Math.max(storageH, BAG_H) + tab + 8 <= H) {
			int x = InventoryLayout.mainColumnX(W, pairW, pairW);
			x = Mth.clamp(x, 6, W - 6 - pairW);
			Rect s = new Rect(x, Math.max(tab + 4, (H - storageH) / 2), PANEL_W, storageH);
			return new Rect[]{s, new Rect(x + PANEL_W + 12, Math.max(tab + 4, (H - BAG_H) / 2), PANEL_W, BAG_H)};
		}
		return null;
	}

	@Override
	protected void openVanillaFallback() {
		vanillaBypass = true;
		this.minecraft.setScreen(this.vanilla);
	}

	@Override
	protected void createPanels() {
		Rect[] l = layout(this.width, this.height);
		if (l == null) return;
		Rect sRect = l[0];
		Rect bRect = l[1];
		int panelW = PANEL_W;
		this.storage = addPanel("storage", 0f, 0, -20, true, this.title, sRect);
		this.bag = addPanel("bag", 0.08f, 0, 22, true, Component.translatable("isekai_ui.panel.inventory"), bRect);
		this.storageX = sRect.x() + InventoryLayout.PAD + 1;
		this.storageY = sRect.y() + 8 + 1;
		this.bagX = bRect.x() + InventoryLayout.PAD + 1;
		this.bagY = bRect.y() + 8 + 1;
		this.hotbarX = bRect.x() + (panelW - (8 * 18 + InventoryLayout.HEX_W)) / 2;
		this.hotbarY = bRect.y() + 8 + 3 * InventoryLayout.SLOT + 9;
	}

	@Override
	protected void placeSlots() {
		if (this.storage == null) return;
		int n = this.rows * 9;
		placeGrid(0, n, 9, this.storageX + this.storage.dx(), this.storageY + this.storage.dy());
		placeGrid(n, 27, 9, this.bagX + this.bag.dx(), this.bagY + this.bag.dy());
		placeHexHotbar(n + 27, this.hotbarX + this.bag.dx(), this.hotbarY + this.bag.dy());
	}

	@Override
	protected void drawPanel(GuiGraphics g, Panel p, float partialTick, int mouseX, int mouseY) {
		if (p == this.storage) {
			drawGrid(g, this.storageX + p.dx(), this.storageY + p.dy(), this.rows * 9, 9, p.alpha);
		} else if (p == this.bag) {
			drawGrid(g, this.bagX + p.dx(), this.bagY + p.dy(), 27, 9, p.alpha);
			Panels.ornament(g, p.now.x() + 24, this.hotbarY - 7 + p.dy(), p.now.w() - 48, p.alpha * 0.9f);
			drawHexHotbar(g, this.hotbarX + p.dx(), this.hotbarY + p.dy(), p.alpha);
		}
	}
}
