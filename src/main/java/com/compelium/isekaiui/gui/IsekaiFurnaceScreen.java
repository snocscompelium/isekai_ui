package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.mixin.AbstractFurnaceMenuAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;

import java.util.ArrayList;
import java.util.List;

/**
 * Fornalha, defumador e alto-forno. Alem do progresso, mostra quanto tempo falta para assar tudo que esta
 * na entrada e para quantos itens o combustivel ainda da (contas feitas so com o que o jogo ja manda).
 */
public class IsekaiFurnaceScreen extends IsekaiStationScreen<AbstractFurnaceMenu> {
	private static final int W = 150;
	private static final int H = 9 + 3 * SLOT + 6 + 10 + 7;

	public IsekaiFurnaceScreen(AbstractFurnaceMenu menu, Inventory inventory, Component title, Screen vanilla, String key) {
		super(menu, inventory, title, vanilla, key);
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
		return r.x() + 30;
	}

	private static int inputY(Rect r) {
		return r.y() + 10;
	}

	private static int fuelY(Rect r) {
		return r.y() + 10 + 2 * SLOT;
	}

	private static int arrowX(Rect r) {
		return inputX(r) + 16 + 14;
	}

	private static int resultX(Rect r) {
		return arrowX(r) + 22 + 14 + 5;
	}

	private static int resultY(Rect r) {
		return r.y() + 10 + SLOT;
	}

	@Override
	protected void placeStation(Rect r) {
		setSlot(0, inputX(r), inputY(r));
		setSlot(1, inputX(r), fuelY(r));
		setSlot(2, resultX(r), resultY(r));
	}

	private ContainerData data() {
		return ((AbstractFurnaceMenuAccessor) this.menu).isekai$data();
	}

	@Override
	protected void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick) {
		drawSlot(g, inputX(r), inputY(r), alpha);
		drawSlot(g, inputX(r), fuelY(r), alpha);
		float lit = this.menu.isLit() ? this.menu.getLitProgress() : 0f;
		drawFillingIcon(g, "flame", inputX(r) + 2, inputY(r) + SLOT + 3, 12, lit, 0xFF4A4352, 0xFFF29A3A, alpha);
		drawProgressArrow(g, arrowX(r), resultY(r) + 1, this.menu.getBurnProgress(), alpha);
		drawResultSlot(g, resultX(r), resultY(r), 2, alpha);
		Component info = info();
		if (info != null) {
			infoLine(g, info, r, Theme.TEXT_DIM, alpha);
		}
	}

	/** "Pronto em 1:20" / "Sem combustivel" / "Combustivel para ~12 itens". */
	private Component info() {
		ContainerData d = data();
		int litTime = d.get(0);
		int progress = d.get(2);
		if (d.get(3) <= 0) return null; // o jogo ainda nao mandou o tempo de cozimento
		int total = d.get(3);
		ItemStack input = this.menu.slots.get(0).getItem();
		ItemStack fuel = this.menu.slots.get(1).getItem();
		int fuelTicks = litTime;
		if (!fuel.isEmpty() && this.minecraft.player != null) {
			FuelValues values = this.minecraft.player.connection.fuelValues();
			if (values != null) fuelTicks += values.burnDuration(fuel) * fuel.getCount();
		}
		int fuelItems = fuelTicks / total;
		if (!input.isEmpty()) {
			if (fuelTicks <= 0) return Component.translatable("isekai_ui.station.no_fuel");
			int items = Math.min(input.getCount(), Math.max(1, fuelItems + (litTime > 0 ? 1 : 0)));
			int ticks = Math.max(0, items * total - progress);
			return Component.translatable("isekai_ui.station.ready_in", clock(ticks));
		}
		if (fuelTicks > 0) return Component.translatable("isekai_ui.station.fuel_for", fuelItems);
		return null;
	}

	@Override
	protected List<Component> stationTooltip(Rect r, int mouseX, int mouseY) {
		Rect flame = new Rect(inputX(r), inputY(r) + SLOT, 16, SLOT);
		if (!flame.contains(mouseX, mouseY)) return null;
		List<Component> lines = new ArrayList<>();
		ContainerData d = data();
		lines.add(Component.translatable(d.get(0) > 0 ? "isekai_ui.station.burning" : "isekai_ui.station.not_burning", clock(d.get(0))));
		return lines;
	}

	@Override
	protected RecipeBookPanel.Tab grimoireTab() {
		return RecipeBookPanel.Tab.FURNACES;
	}
}
