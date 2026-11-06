package com.compelium.isekaiui.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Mesa de encantamento: item e lapis a esquerda, as tres runas (opcoes) a direita. */
public class IsekaiEnchantingScreen extends IsekaiStationScreen<EnchantmentMenu> {
	private static final int ROW_W = 118;
	private static final int ROW_H = 19;
	private static final int W = 9 + SLOT + 6 + 8 + ROW_W + 9;
	private static final int H = 9 + 3 * ROW_H + 2 * 2 + 9;
	private int hovered = -1;

	public IsekaiEnchantingScreen(EnchantmentMenu menu, Inventory inventory, Component title, Screen vanilla) {
		super(menu, inventory, title, vanilla, "enchanting");
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

	private static int slotsX(Rect r) {
		return r.x() + 11;
	}

	private static Rect row(Rect r, int i) {
		return new Rect(r.right() - 9 - ROW_W, r.y() + 9 + i * (ROW_H + 2), ROW_W, ROW_H);
	}

	@Override
	protected void placeStation(Rect r) {
		setSlot(0, slotsX(r), r.y() + 14);
		setSlot(1, slotsX(r), r.y() + 14 + SLOT + 8);
	}

	private boolean canPick(int i) {
		if (this.minecraft.player == null) return false;
		int cost = this.menu.costs[i];
		if (cost <= 0) return false;
		if (this.minecraft.player.getAbilities().instabuild) return true;
		return this.minecraft.player.experienceLevel >= cost && this.menu.getGoldCount() >= i + 1;
	}

	@Override
	protected void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick) {
		drawSlot(g, slotsX(r), r.y() + 14, alpha);
		drawSlot(g, slotsX(r), r.y() + 14 + SLOT + 8, alpha);
		this.hovered = -1;
		EnchantmentNames.getInstance().initSeed(this.menu.getEnchantmentSeed());
		float time = time();
		for (int i = 0; i < 3; i++) {
			Rect row = row(r, i);
			int cost = this.menu.costs[i];
			boolean active = cost > 0;
			boolean can = canPick(i);
			boolean hover = active && !isDraggingPanel() && row.contains(mouseX, mouseY);
			if (hover) this.hovered = i;
			int bg = !active ? 0x700A080E : can ? (hover ? 0xE0473A63 : 0xC0302742) : 0xB01E1A26;
			Draw.fill(g, row.x(), row.y(), row.right(), row.bottom(), Draw.alpha(bg, alpha));
			int edge = can ? (hover ? 0xFFF2C76A : 0xFFB89552) : 0xFF4A4352;
			Draw.fill(g, row.x(), row.y(), row.right(), row.y() + 1, Draw.alpha(edge, alpha));
			Draw.fill(g, row.x(), row.bottom() - 1, row.right(), row.bottom(), Draw.alpha(edge, alpha * 0.6f));
			if (!active) continue;
			// selo com o numero de lapis (1, 2 ou 3)
			int sx = row.x() + 3;
			int sy = row.y() + 3;
			float pulse = can ? 0.75f + 0.25f * Mth.sin(time * 3f + i) : 0.5f;
			Draw.sprite(g, Theme.SLOT_HEX, sx - 2, sy - 2, 17, 15, Draw.alpha(can ? 0xFFF2C76A : 0xFF6E6578, alpha * pulse));
			Draw.textCentered(g, this.font, Component.literal(String.valueOf(i + 1)), sx + 6, sy + 1, Draw.alpha(can ? 0xFF2A1F10 : 0xFF9A90A4, alpha), false);
			// runas e custo
			String costText = String.valueOf(cost);
			int costW = this.font.width(costText);
			FormattedText runes = EnchantmentNames.getInstance().getRandomName(this.font, ROW_W - 26 - costW);
			int runeColor = can ? (hover ? 0xFFFFF4D6 : 0xFFD8CBA8) : 0xFF6E6578;
			g.drawWordWrap(this.font, runes, row.x() + 20, row.y() + 2, ROW_W - 26 - costW, Draw.alpha(runeColor, alpha), false);
			int costColor = can ? 0xFF8BE36B : 0xFF507A43;
			Draw.text(g, this.font, Component.literal(costText), row.right() - 4 - costW, row.y() + 10, Draw.alpha(costColor, alpha), true);
		}
	}

	@Override
	protected boolean clickStation(Rect r, double mouseX, double mouseY, int button) {
		if (button != 0 || this.minecraft.player == null || this.minecraft.gameMode == null) return false;
		for (int i = 0; i < 3; i++) {
			if (!row(r, i).contains(mouseX, mouseY)) continue;
			if (this.menu.clickMenuButton(this.minecraft.player, i)) {
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, i);
			}
			return true;
		}
		return false;
	}

	@Override
	protected List<Component> stationTooltip(Rect r, int mouseX, int mouseY) {
		int i = this.hovered;
		if (i < 0 || this.minecraft.level == null || this.minecraft.player == null) return null;
		int cost = this.menu.costs[i];
		int clue = this.menu.enchantClue[i];
		int level = this.menu.levelClue[i];
		List<Component> lines = new ArrayList<>();
		Optional<Holder.Reference<Enchantment>> ench = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(clue);
		if (ench.isPresent() && level >= 0) {
			lines.add(Component.translatable("container.enchant.clue", Enchantment.getFullname(ench.get(), level)).withStyle(ChatFormatting.WHITE));
		}
		if (!this.minecraft.player.getAbilities().instabuild) {
			lines.add(CommonComponents.EMPTY);
			if (this.minecraft.player.experienceLevel < cost) {
				lines.add(Component.translatable("container.enchant.level.requirement", cost).withStyle(ChatFormatting.RED));
			} else {
				int lapis = i + 1;
				lines.add((lapis == 1 ? Component.translatable("container.enchant.lapis.one") : Component.translatable("container.enchant.lapis.many", lapis))
						.withStyle(this.menu.getGoldCount() >= lapis ? ChatFormatting.GRAY : ChatFormatting.RED));
				lines.add((lapis == 1 ? Component.translatable("container.enchant.level.one") : Component.translatable("container.enchant.level.many", lapis))
						.withStyle(ChatFormatting.GRAY));
			}
		}
		return lines;
	}
}
