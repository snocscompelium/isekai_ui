package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.creative.ArmorSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Janela "Conjuntos de armadura": cada linha mostra as pecas e o nome; clique veste, botao direito renomeia,
 * X apaga (clique duas vezes). A ultima linha salva a armadura que esta vestida agora.
 */
public final class ArmorSetList {
	public static final int ROW_H = 18;
	public static final int MAX_ROWS = 6;
	public static final int WIDTH = 176;
	private static final long CONFIRM_NANOS = 3_000_000_000L;

	public interface Host {
		void addInput(EditBox box);

		void removeInput(EditBox box);

		/** true no criativo (as pecas sao copias). */
		boolean creative();

		InventoryMenu menu();
	}

	private final Font font;
	private final Host host;
	private int scroll;
	private int hoveredRow = -1;
	private int hoveredPart;
	private EditBox input;
	private ArmorSets.ArmorSet renaming;
	private ArmorSets.ArmorSet confirmDelete;
	private long confirmNanos;
	private Component status;
	private long statusNanos;

	public ArmorSetList(Font font, Host host) {
		this.font = font;
		this.host = host;
	}

	public static int height() {
		int rows = Math.max(1, Math.min(MAX_ROWS, ArmorSets.all().size() + 1));
		return 8 + rows * ROW_H + 8 + 10;
	}

	private int rowsShown() {
		return Math.min(MAX_ROWS, ArmorSets.all().size() + 1);
	}

	private static Rect rowRect(Rect p, int row) {
		return new Rect(p.x() + 6, p.y() + 8 + row * ROW_H, p.w() - 12, ROW_H);
	}

	public boolean isTyping() {
		return this.input != null;
	}

	public void draw(GuiGraphics g, Rect p, float a, int mouseX, int mouseY, float partialTick, boolean canHover) {
		List<ArmorSets.ArmorSet> list = ArmorSets.all();
		this.hoveredRow = -1;
		this.hoveredPart = 0;
		int total = list.size() + 1;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, total - MAX_ROWS));
		for (int row = 0; row < rowsShown(); row++) {
			int idx = row + this.scroll;
			if (idx >= total) break;
			Rect r = rowRect(p, row);
			boolean hover = canHover && r.contains(mouseX, mouseY);
			if (idx == list.size()) {
				if (hover) this.hoveredRow = idx;
				if (hover) Draw.fill(g, r.x(), r.y(), r.right(), r.bottom(), Draw.alpha(0x30FFF2CF, a));
				boolean full = list.size() >= ArmorSets.MAX_SETS;
				int c = full ? 0xFF5E5868 : (hover ? Theme.TEXT_GOLD : Theme.TEXT_DIM);
				Draw.sprite(g, Theme.icon("plus"), r.x() + 2, r.y() + 3, 12, 12, Draw.alpha(c, a));
				Draw.text(g, this.font, Component.translatable("isekai_ui.armor.save_current"), r.x() + 18, r.y() + 5, Draw.alpha(c, a), false);
				continue;
			}
			ArmorSets.ArmorSet set = list.get(idx);
			Rect del = new Rect(r.right() - 14, r.y() + 3, 12, 12);
			if (hover) {
				this.hoveredRow = idx;
				this.hoveredPart = del.contains(mouseX, mouseY) ? 2 : 0;
				Draw.fill(g, r.x(), r.y(), r.right(), r.bottom(), Draw.alpha(0x30FFF2CF, a));
			}
			if (a > 0.5f) {
				for (int k = 0; k < 4; k++) {
					ItemStack piece = set.pieces[k];
					if (!piece.isEmpty()) g.renderItem(piece, r.x() + 1 + k * 13, r.y() + 1);
				}
			}
			int textX = r.x() + 56;
			if (this.input != null && set == this.renaming) {
				Draw.fill(g, textX - 2, r.y() + 2, r.right() - 16, r.bottom() - 2, Draw.alpha(0xC00A080E, a));
				Draw.fill(g, textX - 2, r.bottom() - 3, r.right() - 16, r.bottom() - 2, Draw.alpha(Theme.GOLD, a * 0.8f));
				this.input.setX(textX);
				this.input.setY(r.y() + 5);
				this.input.setWidth(r.right() - 18 - textX);
				this.input.render(g, mouseX, mouseY, partialTick);
			} else {
				String text = this.font.plainSubstrByWidth(set.name, r.right() - 18 - textX);
				Draw.text(g, this.font, Component.literal(text), textX, r.y() + 5, Draw.alpha(Theme.TEXT, a), false);
			}
			boolean armed = set == this.confirmDelete && System.nanoTime() - this.confirmNanos < CONFIRM_NANOS;
			int delColor = armed ? 0xFFE0465E : (hover && this.hoveredPart == 2 ? 0xFFF2EEE2 : 0xFF7E7566);
			Draw.sprite(g, Theme.icon("close"), del.x(), del.y(), 12, 12, Draw.alpha(delColor, a));
		}
		if (total > MAX_ROWS) {
			int trackH = MAX_ROWS * ROW_H;
			int barH = Math.max(8, trackH * MAX_ROWS / total);
			int barY = p.y() + 8 + (trackH - barH) * this.scroll / Math.max(1, total - MAX_ROWS);
			Draw.fill(g, p.right() - 4, barY, p.right() - 2, barY + barH, Draw.alpha(Theme.GOLD, a * 0.8f));
		}
		Component line = this.status != null && System.nanoTime() - this.statusNanos < 4_000_000_000L
				? this.status : Component.translatable(this.host.creative() ? "isekai_ui.armor.hint_creative" : "isekai_ui.armor.hint_survival");
		String s = this.font.plainSubstrByWidth(line.getString(), p.w() - 14);
		Draw.text(g, this.font, Component.literal(s), p.x() + 7, p.bottom() - 15, Draw.alpha(Theme.TEXT_DIM, a), false);
	}

	/** Clique dentro da janela. Devolve true se usou o clique. */
	public boolean click(Rect p, double mouseX, double mouseY, int button) {
		List<ArmorSets.ArmorSet> list = ArmorSets.all();
		for (int row = 0; row < rowsShown(); row++) {
			int idx = row + this.scroll;
			Rect r = rowRect(p, row);
			if (!r.contains(mouseX, mouseY)) continue;
			if (idx == list.size()) {
				stopTyping(false);
				ArmorSets.ArmorSet created = ArmorSets.saveCurrent(Minecraft.getInstance().player);
				if (created != null) {
					UiSounds.toggle();
					startTyping(created);
				} else {
					setStatus(Component.translatable(list.size() >= ArmorSets.MAX_SETS ? "isekai_ui.armor.full" : "isekai_ui.armor.nothing_worn"));
				}
				return true;
			}
			if (idx >= list.size()) return true;
			ArmorSets.ArmorSet set = list.get(idx);
			if (new Rect(r.right() - 14, r.y() + 3, 12, 12).contains(mouseX, mouseY)) {
				if (set == this.confirmDelete && System.nanoTime() - this.confirmNanos < CONFIRM_NANOS) {
					stopTyping(false);
					ArmorSets.remove(set);
					this.confirmDelete = null;
					UiSounds.toggle();
				} else {
					this.confirmDelete = set;
					this.confirmNanos = System.nanoTime();
					setStatus(Component.translatable("isekai_ui.armor.delete_confirm"));
					UiSounds.click();
				}
				return true;
			}
			if (button == 1) {
				startTyping(set);
				return true;
			}
			stopTyping(false);
			int missing = ArmorSets.equip(set, this.host.menu(), Minecraft.getInstance().player, this.host.creative());
			if (missing > 0) setStatus(Component.translatable("isekai_ui.armor.missing", missing));
			else setStatus(Component.translatable("isekai_ui.armor.equipped", set.name));
			UiSounds.toggle();
			return true;
		}
		return false;
	}

	private void setStatus(Component c) {
		this.status = c;
		this.statusNanos = System.nanoTime();
	}

	private void startTyping(ArmorSets.ArmorSet set) {
		stopTyping(false);
		this.renaming = set;
		this.input = new EditBox(this.font, 0, 0, 100, 10, Component.translatable("isekai_ui.armor.rename"));
		this.input.setBordered(false);
		this.input.setMaxLength(32);
		this.input.setTextColor(Theme.TEXT);
		this.input.setValue(set.name);
		this.host.addInput(this.input);
		this.input.setFocused(true);
		List<ArmorSets.ArmorSet> list = ArmorSets.all();
		int idx = list.indexOf(set);
		if (idx >= 0) {
			if (idx < this.scroll) this.scroll = idx;
			if (idx >= this.scroll + MAX_ROWS) this.scroll = idx - MAX_ROWS + 1;
		}
	}

	public void stopTyping(boolean save) {
		if (this.input == null) return;
		EditBox box = this.input;
		ArmorSets.ArmorSet set = this.renaming;
		this.input = null;
		this.renaming = null;
		this.host.removeInput(box);
		if (save && set != null) {
			ArmorSets.rename(set, box.getValue());
			UiSounds.toggle();
		}
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.input == null) return false;
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			stopTyping(true);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			stopTyping(false);
			return true;
		}
		return this.input.keyPressed(keyCode, scanCode, modifiers) || this.input.canConsumeInput();
	}

	public boolean scroll(double scrollY) {
		int total = ArmorSets.all().size() + 1;
		this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, Math.max(0, total - MAX_ROWS));
		return true;
	}

	/** Dica para a linha embaixo do mouse (ou null). */
	public Component tooltip() {
		List<ArmorSets.ArmorSet> list = ArmorSets.all();
		if (this.hoveredRow < 0 || this.hoveredRow >= list.size()) return null;
		if (this.hoveredPart == 2) return Component.translatable("isekai_ui.armor.delete");
		return Component.translatable(this.host.creative() ? "isekai_ui.armor.row_tip_creative" : "isekai_ui.armor.row_tip_survival");
	}

	/** Usado pelos testes automaticos: veste o conjunto pelo numero. */
	public int debugEquip(int index) {
		List<ArmorSets.ArmorSet> list = ArmorSets.all();
		if (index < 0 || index >= list.size()) return -1;
		return ArmorSets.equip(list.get(index), this.host.menu(), Minecraft.getInstance().player, this.host.creative());
	}
}
