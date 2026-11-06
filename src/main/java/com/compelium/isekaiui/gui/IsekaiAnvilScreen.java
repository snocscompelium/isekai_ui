package com.compelium.isekaiui.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Bigorna: campo de nome, dois itens e o resultado. Mostra o custo em niveis e quantas vezes o item ja
 * passou por uma bigorna (cada vez deixa o proximo reparo mais caro).
 */
public class IsekaiAnvilScreen extends IsekaiStationScreen<AnvilMenu> implements ContainerListener {
	private static final int W = 160;
	private static final int H = 9 + 14 + 8 + 26 + 6 + 10 + 7;
	private EditBox name;

	public IsekaiAnvilScreen(AnvilMenu menu, Inventory inventory, Component title, Screen vanilla) {
		super(menu, inventory, title, vanilla, "anvil");
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

	@Override
	protected void createPanels() {
		super.createPanels();
		String old = this.name == null ? "" : this.name.getValue();
		boolean editable = this.name == null ? false : this.name.isFocused();
		this.name = new EditBox(this.font, 0, 0, W - 28, 10, Component.translatable("container.repair"));
		this.name.setBordered(false);
		this.name.setMaxLength(AnvilMenu.MAX_NAME_LENGTH);
		this.name.setTextColor(Theme.TEXT);
		this.name.setValue(old);
		this.name.setResponder(this::onNameChanged);
		this.name.setEditable(!this.menu.getSlot(0).getItem().isEmpty());
		addWidget(this.name);
		if (editable) setFocused(this.name);
	}

	@Override
	protected void init() {
		super.init();
		this.menu.removeSlotListener(this);
		this.menu.addSlotListener(this);
	}

	@Override
	public void removed() {
		this.menu.removeSlotListener(this);
		super.removed();
	}

	private void onNameChanged(String text) {
		ItemStack stack = this.menu.getSlot(0).getItem();
		if (stack.isEmpty()) return;
		String s = text;
		if (!stack.has(DataComponents.CUSTOM_NAME) && text.equals(stack.getHoverName().getString())) s = "";
		if (this.menu.setItemName(s) && this.minecraft.player != null) {
			this.minecraft.player.connection.send(new ServerboundRenameItemPacket(s));
		}
	}

	@Override
	public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {
		if (slot == 0 && this.name != null) {
			this.name.setValue(stack.isEmpty() ? "" : stack.getHoverName().getString());
			this.name.setEditable(!stack.isEmpty());
			setFocused(this.name);
		}
	}

	@Override
	public void dataChanged(AbstractContainerMenu menu, int index, int value) {
	}

	private static int rowY(Rect r) {
		return r.y() + 9 + 14 + 8 + 5;
	}

	private static int leftX(Rect r) {
		return r.x() + 14;
	}

	private static int rightX(Rect r) {
		return leftX(r) + SLOT + 16;
	}

	private static int resultX(Rect r) {
		return r.right() - 14 - 26 + 5;
	}

	@Override
	protected void placeStation(Rect r) {
		setSlot(0, leftX(r), rowY(r));
		setSlot(1, rightX(r), rowY(r));
		setSlot(2, resultX(r), rowY(r));
		if (this.name != null) {
			this.name.setX(r.x() + 14);
			this.name.setY(r.y() + 12);
		}
	}

	@Override
	protected void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick) {
		Draw.fill(g, r.x() + 9, r.y() + 9, r.right() - 9, r.y() + 23, Draw.alpha(0xC00A080E, alpha));
		Draw.fill(g, r.x() + 9, r.y() + 22, r.right() - 9, r.y() + 23, Draw.alpha(Theme.GOLD, alpha * 0.8f));
		if (this.name != null) this.name.render(g, mouseX, mouseY, partialTick);
		drawSlot(g, leftX(r), rowY(r), alpha);
		Draw.sprite(g, Theme.icon("plus"), leftX(r) + SLOT + 2, rowY(r) + 2, 12, 12, Draw.alpha(0xFF8C8296, alpha));
		drawSlot(g, rightX(r), rowY(r), alpha);
		boolean has = this.menu.getSlot(2).hasItem();
		int arrowX = rightX(r) + SLOT + 4;
		drawProgressArrow(g, arrowX, rowY(r) + 1, has ? 1f : 0f, alpha);
		drawResultSlot(g, resultX(r), rowY(r), 2, alpha);

		int cost = this.menu.getCost();
		boolean creative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;
		Component line = null;
		int color = Theme.TEXT_DIM;
		if (cost >= 40 && !creative) {
			line = Component.translatable("container.repair.expensive");
			color = 0xFFE0465E;
		} else if (cost > 0 && has) {
			line = Component.translatable("container.repair.cost", cost);
			boolean canPay = creative || (this.minecraft.player != null && this.minecraft.player.experienceLevel >= cost);
			color = canPay ? 0xFF7FD36B : 0xFFE0465E;
		} else {
			ItemStack left = this.menu.getSlot(0).getItem();
			int work = left.isEmpty() ? 0 : left.getOrDefault(DataComponents.REPAIR_COST, 0);
			if (work > 0) line = Component.translatable("isekai_ui.station.anvil_uses", 31 - Integer.numberOfLeadingZeros(work + 1));
		}
		if (line != null) {
			int w = this.font.width(line);
			smallText(g, line, r.cx() - w / 2, r.bottom() - 15, color, alpha);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers);
		if (this.name != null && (this.name.keyPressed(keyCode, scanCode, modifiers) || this.name.canConsumeInput())) return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected boolean clickStation(Rect r, double mouseX, double mouseY, int button) {
		if (this.name != null && new Rect(r.x() + 9, r.y() + 9, r.w() - 18, 14).contains(mouseX, mouseY)) {
			setFocused(this.name);
			this.name.setFocused(true);
			return true;
		}
		return false;
	}
}
