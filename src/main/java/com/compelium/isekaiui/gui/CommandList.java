package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.commands.CommandMemory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Lista de comandos rapidos (usada no inventario e no criativo): clique usa o comando, alfinete fixa
 * na barra, X esquece, botao direito troca o icone e a ultima linha "+" deixa anotar um comando.
 */
public final class CommandList {
	public static final int ROW_H = 16;
	public static final int MAX_ROWS = 7;
	public static final int WIDTH = 176;

	/** O que a tela precisa fazer pela lista (adicionar/remover o campo de texto, refazer botoes). */
	public interface Host {
		void addInput(EditBox box);

		void removeInput(EditBox box);

		/** Algo mudou que afeta a barra (comando fixado/apagado/anotado). */
		void commandsChanged();
	}

	private final Font font;
	private final Host host;
	private int scroll;
	private int hoveredRow = -1;
	private int hoveredPart;
	private EditBox input;

	public CommandList(Font font, Host host) {
		this.font = font;
		this.host = host;
	}

	/** Altura da janela para o numero atual de comandos (+1 linha para anotar). */
	public static int height() {
		int rows = Math.max(1, Math.min(MAX_ROWS, CommandMemory.entries().size() + 1));
		return 8 + rows * ROW_H + 8;
	}

	private int rowsShown() {
		return Math.min(MAX_ROWS, CommandMemory.entries().size() + 1);
	}

	private static Rect rowRect(Rect p, int row) {
		return new Rect(p.x() + 6, p.y() + 8 + row * ROW_H, p.w() - 12, ROW_H);
	}

	public boolean isTyping() {
		return this.input != null;
	}

	public void draw(GuiGraphics g, Rect p, float a, int mouseX, int mouseY, float partialTick, boolean canHover) {
		List<CommandMemory.Entry> list = CommandMemory.entries();
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
				drawAddRow(g, r, a, hover, mouseX, mouseY, partialTick);
				if (hover) this.hoveredRow = idx;
				continue;
			}
			CommandMemory.Entry e = list.get(idx);
			Rect pin = new Rect(r.right() - 30, r.y() + 2, 12, 12);
			Rect del = new Rect(r.right() - 14, r.y() + 2, 12, 12);
			if (hover) {
				this.hoveredRow = idx;
				this.hoveredPart = pin.contains(mouseX, mouseY) ? 1 : (del.contains(mouseX, mouseY) ? 2 : 0);
				Draw.fill(g, r.x(), r.y(), r.right(), r.bottom(), Draw.alpha(0x30FFF2CF, a));
			}
			Draw.sprite(g, Theme.icon(e.icon), r.x() + 2, r.y() + 2, 12, 12, Draw.alpha(Theme.IVORY, a));
			String text = this.font.plainSubstrByWidth(e.label, r.w() - 54);
			Draw.text(g, this.font, Component.literal(text), r.x() + 18, r.y() + 4, Draw.alpha(e.pinned ? Theme.TEXT_GOLD : Theme.TEXT, a), false);
			int pinColor = e.pinned ? 0xFFF2C76A : (hover && this.hoveredPart == 1 ? Theme.IVORY : 0xFF7E7566);
			Draw.sprite(g, Theme.icon("pin"), pin.x(), pin.y(), 12, 12, Draw.alpha(pinColor, a));
			int delColor = hover && this.hoveredPart == 2 ? 0xFFE0465E : 0xFF7E7566;
			Draw.sprite(g, Theme.icon("close"), del.x(), del.y(), 12, 12, Draw.alpha(delColor, a));
		}
		if (total > MAX_ROWS) {
			int trackH = p.h() - 16;
			int barH = Math.max(8, trackH * MAX_ROWS / total);
			int barY = p.y() + 8 + (trackH - barH) * this.scroll / Math.max(1, total - MAX_ROWS);
			Draw.fill(g, p.right() - 4, barY, p.right() - 2, barY + barH, Draw.alpha(Theme.GOLD, a * 0.8f));
		}
	}

	private void drawAddRow(GuiGraphics g, Rect r, float a, boolean hover, int mouseX, int mouseY, float partialTick) {
		if (this.input != null) {
			Draw.fill(g, r.x(), r.y() + 1, r.right(), r.bottom() - 1, Draw.alpha(0xC00A080E, a));
			Draw.fill(g, r.x(), r.bottom() - 2, r.right(), r.bottom() - 1, Draw.alpha(Theme.GOLD, a * 0.8f));
			Draw.text(g, this.font, Component.literal("/"), r.x() + 4, r.y() + 4, Draw.alpha(Theme.TEXT_GOLD, a), false);
			this.input.setX(r.x() + 11);
			this.input.setY(r.y() + 4);
			this.input.setWidth(r.w() - 14);
			this.input.render(g, mouseX, mouseY, partialTick);
			return;
		}
		if (hover) Draw.fill(g, r.x(), r.y(), r.right(), r.bottom(), Draw.alpha(0x30FFF2CF, a));
		int c = hover ? Theme.TEXT_GOLD : Theme.TEXT_DIM;
		Draw.sprite(g, Theme.icon("plus"), r.x() + 2, r.y() + 2, 12, 12, Draw.alpha(c, a));
		Draw.text(g, this.font, Component.translatable("isekai_ui.commands.add"), r.x() + 18, r.y() + 4, Draw.alpha(c, a), false);
	}

	/** Clique dentro da janela da lista. Devolve true se usou o clique. */
	public boolean click(Rect p, double mouseX, double mouseY, int button) {
		List<CommandMemory.Entry> list = CommandMemory.entries();
		int total = list.size() + 1;
		for (int row = 0; row < rowsShown(); row++) {
			int idx = row + this.scroll;
			if (idx >= total) break;
			Rect r = rowRect(p, row);
			if (!r.contains(mouseX, mouseY)) continue;
			if (idx == list.size()) {
				if (this.input == null) startTyping();
				return true;
			}
			CommandMemory.Entry e = list.get(idx);
			if (button == 1) {
				CommandMemory.cycleIcon(e);
				UiSounds.toggle();
				this.host.commandsChanged();
				return true;
			}
			if (new Rect(r.right() - 30, r.y() + 2, 12, 12).contains(mouseX, mouseY)) {
				CommandMemory.togglePin(e);
				UiSounds.toggle();
				this.host.commandsChanged();
			} else if (new Rect(r.right() - 14, r.y() + 2, 12, 12).contains(mouseX, mouseY)) {
				CommandMemory.remove(e);
				UiSounds.click();
				this.host.commandsChanged();
			} else {
				UiSounds.click();
				CommandMemory.run(e);
			}
			return true;
		}
		return false;
	}

	private void startTyping() {
		this.input = new EditBox(this.font, 0, 0, WIDTH - 26, 10, Component.translatable("isekai_ui.commands.add"));
		this.input.setBordered(false);
		this.input.setMaxLength(256);
		this.input.setTextColor(Theme.TEXT);
		this.input.setHint(Component.translatable("isekai_ui.commands.add_hint"));
		this.host.addInput(this.input);
		this.input.setFocused(true);
		UiSounds.click();
	}

	public void stopTyping(boolean save) {
		if (this.input == null) return;
		EditBox box = this.input;
		this.input = null;
		this.host.removeInput(box);
		if (save && CommandMemory.addManual(box.getValue()) != null) {
			UiSounds.toggle();
			this.host.commandsChanged();
		}
	}

	/** Teclas enquanto anota um comando: Enter salva, Esc cancela, o resto vai para o campo. */
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
		int total = CommandMemory.entries().size() + 1;
		this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, Math.max(0, total - MAX_ROWS));
		return true;
	}

	/** Dica para a linha embaixo do mouse (ou null). */
	public Component tooltip() {
		List<CommandMemory.Entry> list = CommandMemory.entries();
		if (this.hoveredRow < 0) return null;
		if (this.hoveredRow == list.size()) return this.input == null ? Component.translatable("isekai_ui.commands.add_tip") : null;
		if (this.hoveredRow > list.size()) return null;
		CommandMemory.Entry e = list.get(this.hoveredRow);
		String key = this.hoveredPart == 1 ? (e.pinned ? "isekai_ui.commands.unpin" : "isekai_ui.commands.pin")
				: this.hoveredPart == 2 ? "isekai_ui.commands.delete" : "isekai_ui.commands.run";
		return Component.translatable(key, "/" + e.command);
	}

	/** Usado pelos testes automaticos. */
	public Rect debugPinRect(Rect p, int row) {
		Rect r = rowRect(p, row);
		return new Rect(r.right() - 30, r.y() + 2, 12, 12);
	}

	/** Usado pelos testes automaticos: anota um comando como se tivesse digitado. */
	public void debugType(String text) {
		if (this.input == null) startTyping();
		if (this.input != null) this.input.setValue(text);
	}
}
