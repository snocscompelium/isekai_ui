package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.ReturnToContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Bau, bau grande, barril, bau do ender, shulker box, funil, ejetor e liberador no estilo isekai.
 * So aparece com o titulo padrao do jogo: menus de plugin (que usam bau como "tela") ficam intactos.
 */
public class IsekaiChestScreen extends IsekaiStationScreen<AbstractContainerMenu> {
	private final int cols;
	private final int rows;
	/** Bloco que foi aberto (para o botao "voltar ao bau" do inventario); null se abriu por comando. */
	private final BlockPos openedFrom;

	public IsekaiChestScreen(AbstractContainerMenu menu, Inventory inventory, Component title, int cols, int rows, Screen vanilla) {
		super(menu, inventory, title, vanilla, cols == 9 ? "container" : "container." + cols + "x" + rows);
		this.cols = cols;
		this.rows = rows;
		this.openedFrom = ReturnToContainer.lookedAtContainer();
	}

	@Override
	protected int stationW() {
		return Math.max(this.cols * SLOT + InventoryLayout.PAD * 2, this.cols < 9 ? 112 : 0);
	}

	@Override
	protected int stationH() {
		return 8 + this.rows * SLOT + 8;
	}

	@Override
	protected int stationSlots() {
		return this.cols * this.rows;
	}

	private int gridX(Rect r) {
		return r.x() + (r.w() - this.cols * SLOT) / 2 + 1;
	}

	@Override
	protected void placeStation(Rect r) {
		placeGrid(0, this.cols * this.rows, this.cols, gridX(r), r.y() + 9);
	}

	@Override
	protected void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick) {
		drawGrid(g, gridX(r), r.y() + 9, this.cols * this.rows, this.cols, alpha);
	}

	@Override
	protected void addStationButtons() {
		addSideButton(this.station, "craft", Component.translatable("isekai_ui.button.go_craft"), this::goCraft, () -> false);
	}

	/** Fecha o bau e abre o inventario (grade 2x2). Igual a apertar ESC e depois E. */
	private void goCraft() {
		BlockPos from = this.openedFrom;
		startClosing(CLOSE_SECONDS, null, () -> {
			LocalPlayer p = this.minecraft.player;
			if (p == null) return;
			p.closeContainer();
			ReturnToContainer.arm(from);
			this.minecraft.setScreen(new InventoryScreen(p));
		});
	}
}
