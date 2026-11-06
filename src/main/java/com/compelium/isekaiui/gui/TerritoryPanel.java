package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.map.DimMap;
import com.compelium.isekaiui.map.Kingdoms;
import com.compelium.isekaiui.map.MapManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Painel "Territorios" do mapa grande: escolher o reino que o pincel pinta, criar, renomear, trocar a cor
 * e excluir reinos. A pintura em si e feita pela tela do mapa.
 */
public class TerritoryPanel {
	public static final int WIDTH = 132;
	private static final int ROW = 14;
	private static final long CONFIRM_NANOS = 3_000_000_000L;

	public interface Host {
		void addInput(EditBox box);

		void removeInput(EditBox box);
	}

	private final Font font;
	private final Host host;
	/** Reino que o pincel pinta; null = borracha. */
	private String selected;
	private EditBox input;
	/** Reino sendo renomeado (null = criando um novo). */
	private String renaming;
	private String confirmDelete;
	private long confirmNanos;
	private int scroll;
	private Rect rect = new Rect(0, 0, WIDTH, 40);

	public TerritoryPanel(Font font, Host host) {
		this.font = font;
		this.host = host;
	}

	public String selected() {
		return this.selected;
	}

	public boolean erasing() {
		return this.selected == null;
	}

	public void select(String name) {
		this.selected = name;
	}

	public Rect rect() {
		return this.rect;
	}

	public boolean typing() {
		return this.input != null;
	}

	private List<String> names() {
		return Kingdoms.names(MapManager.current());
	}

	private int hintLines() {
		return this.font.split(Component.translatable("isekai_ui.territory.hint"), WIDTH - 16).size();
	}

	/** Linhas: borracha, reinos..., "+ novo reino". */
	public void layout(Rect map) {
		int rows = names().size() + 2;
		int maxRows = Math.max(3, (map.h() - 40 - hintLines() * 9) / ROW);
		int visible = Math.min(rows, maxRows);
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, rows - visible));
		int h = 8 + visible * ROW + 4 + hintLines() * 9 + 6;
		this.rect = new Rect(map.x() + 8, map.y() + 8 + InventoryLayout.TAB, WIDTH, h);
		if (this.input != null) {
			Rect row = rowRect(inputRow());
			this.input.setX(row.x() + 16);
			this.input.setY(row.y() + 3);
			this.input.setWidth(row.w() - 20);
			this.input.visible = row.h() > 0;
		}
	}

	private int visibleRows() {
		return (this.rect.h() - 8 - 4 - hintLines() * 9 - 6) / ROW;
	}

	private Rect rowRect(int index) {
		int i = index - this.scroll;
		if (i < 0 || i >= visibleRows()) return new Rect(this.rect.x() + 4, -100, this.rect.w() - 8, 0);
		return new Rect(this.rect.x() + 4, this.rect.y() + 6 + i * ROW, this.rect.w() - 8, ROW);
	}

	private int inputRow() {
		List<String> names = names();
		if (this.renaming != null) {
			int i = names.indexOf(this.renaming);
			if (i >= 0) return i + 1;
		}
		return names.size() + 1;
	}

	public void render(GuiGraphics g, int mx, int my, float partialTick) {
		Rect r = this.rect;
		Draw.fill(g, r.x() + 2, r.y() + 2, r.right() - 2, r.bottom() - 2, IsekaiMenuScreen.OVERLAY_BACKDROP);
		Panels.panel(g, this.font, r, 1f, 1f, Component.translatable("isekai_ui.territory.title"), 0f);
		List<String> names = names();
		int total = names.size() + 2;
		for (int i = 0; i < total; i++) {
			Rect row = rowRect(i);
			if (row.h() == 0) continue;
			boolean hover = row.contains(mx, my);
			if (i == 0) {
				boolean sel = this.selected == null;
				if (sel || hover) Draw.fill(g, row.x(), row.y(), row.right(), row.bottom(), sel ? 0x40E6CB82 : 0x22FFF2CF);
				Draw.sprite(g, Theme.icon("trash"), row.x() + 2, row.y() + 1, 12, 12, 0xFFE6CB82);
				Draw.text(g, this.font, Component.translatable("isekai_ui.territory.eraser"), row.x() + 17, row.y() + 3, sel ? Theme.TEXT_GOLD : Theme.TEXT, false);
			} else if (i <= names.size()) {
				String name = names.get(i - 1);
				boolean sel = name.equals(this.selected);
				if (sel || hover) Draw.fill(g, row.x(), row.y(), row.right(), row.bottom(), sel ? 0x40E6CB82 : 0x22FFF2CF);
				int color = Theme.landColor(name);
				Draw.fill(g, row.x() + 3, row.y() + 3, row.x() + 11, row.y() + 11, 0xFF1B160F);
				Draw.fill(g, row.x() + 4, row.y() + 4, row.x() + 10, row.y() + 10, 0xFF000000 | color);
				if (this.input == null || !name.equals(this.renaming)) {
					FormattedCharSequence label = Language.trim(this.font, Component.literal(name), row.w() - 34);
					g.drawString(this.font, label, row.x() + 16, row.y() + 3, sel ? Theme.TEXT_GOLD : Theme.TEXT, false);
				}
				boolean armed = name.equals(this.confirmDelete) && System.nanoTime() - this.confirmNanos < CONFIRM_NANOS;
				Rect x = deleteRect(row);
				Draw.sprite(g, Theme.icon("close"), x.x(), x.y(), 10, 10, armed ? 0xFFE0465E : (x.contains(mx, my) ? 0xFFF2EEE2 : 0xFF7E7566));
			} else {
				if (hover && this.input == null) Draw.fill(g, row.x(), row.y(), row.right(), row.bottom(), 0x22FFF2CF);
				Draw.sprite(g, Theme.icon("plus"), row.x() + 2, row.y() + 1, 12, 12, 0xFFE6CB82);
				if (this.input == null || this.renaming != null) {
					Draw.text(g, this.font, Component.translatable("isekai_ui.territory.new"), row.x() + 17, row.y() + 3, Theme.TEXT_DIM, false);
				}
			}
		}
		if (this.input != null) {
			Rect row = rowRect(inputRow());
			if (row.h() > 0) {
				Draw.fill(g, row.x() + 14, row.y() + 1, row.right() - 2, row.bottom() - 1, 0xE00A080E);
				Draw.fill(g, row.x() + 14, row.bottom() - 2, row.right() - 2, row.bottom() - 1, Draw.alpha(Theme.GOLD, 0.8f));
				this.input.render(g, mx, my, partialTick);
			}
		}
		int ty = r.bottom() - 6 - hintLines() * 9;
		for (FormattedCharSequence line : this.font.split(Component.translatable("isekai_ui.territory.hint"), WIDTH - 16)) {
			g.drawString(this.font, line, r.x() + 8, ty, Theme.TEXT_DIM, false);
			ty += 9;
		}
		// dicas ao passar o mouse
		for (int i = 1; i <= names.size(); i++) {
			Rect row = rowRect(i);
			if (row.h() == 0 || !row.contains(mx, my)) continue;
			if (deleteRect(row).contains(mx, my)) {
				Component tip = names.get(i - 1).equals(this.confirmDelete)
						? Component.translatable("isekai_ui.territory.delete_confirm")
						: Component.translatable("isekai_ui.territory.delete", names.get(i - 1));
				setTooltip(tip);
			} else if (mx < row.x() + 13) {
				setTooltip(Component.translatable("isekai_ui.territory.color"));
			} else {
				setTooltip(Component.translatable("isekai_ui.territory.row_tip"));
			}
		}
	}

	private Component tooltip;

	private void setTooltip(Component c) {
		this.tooltip = c;
	}

	/** Dica para a tela mostrar neste quadro (ou null). */
	public Component takeTooltip() {
		Component c = this.tooltip;
		this.tooltip = null;
		return c;
	}

	private static Rect deleteRect(Rect row) {
		return new Rect(row.right() - 13, row.y() + 2, 10, 10);
	}

	/** true se o clique foi no painel. */
	public boolean mouseClicked(double mx, double my, int button) {
		if (!this.rect.grow(2).contains(mx, my)) return false;
		if (this.input != null && this.input.isMouseOver(mx, my)) {
			this.input.mouseClicked(mx, my, button);
			return true;
		}
		List<String> names = names();
		int total = names.size() + 2;
		for (int i = 0; i < total; i++) {
			Rect row = rowRect(i);
			if (row.h() == 0 || !row.contains(mx, my)) continue;
			if (i == 0) {
				stopTyping(false);
				this.selected = null;
				UiSounds.click();
			} else if (i <= names.size()) {
				String name = names.get(i - 1);
				if (deleteRect(row).contains(mx, my)) {
					if (name.equals(this.confirmDelete) && System.nanoTime() - this.confirmNanos < CONFIRM_NANOS) {
						Kingdoms.remove(MapManager.current(), name);
						if (name.equals(this.selected)) this.selected = null;
						this.confirmDelete = null;
						UiSounds.toggle();
					} else {
						this.confirmDelete = name;
						this.confirmNanos = System.nanoTime();
						UiSounds.click();
					}
				} else if (mx < row.x() + 13) {
					Kingdoms.cycleColor(name, Theme.landColor(name));
					UiSounds.hover();
				} else if (button == 1) {
					startTyping(name);
				} else {
					stopTyping(false);
					this.selected = name;
					UiSounds.click();
				}
			} else if (this.input == null) {
				startTyping(null);
			}
			return true;
		}
		return true;
	}

	public boolean mouseScrolled(double mx, double my, double scrollY) {
		if (!this.rect.contains(mx, my)) return false;
		this.scroll -= (int) Math.signum(scrollY);
		this.scroll = Math.max(0, this.scroll);
		return true;
	}

	private void startTyping(String renameOf) {
		stopTyping(false);
		this.renaming = renameOf;
		this.input = new EditBox(this.font, 0, 0, 80, 9, Component.translatable("isekai_ui.territory.new"));
		this.input.setBordered(false);
		this.input.setMaxLength(40);
		this.input.setTextColor(Theme.TEXT);
		this.input.setHint(Component.translatable("isekai_ui.territory.name_hint"));
		if (renameOf != null) this.input.setValue(renameOf);
		this.input.setFocused(true);
		this.host.addInput(this.input);
		int row = inputRow();
		int vis = Math.max(1, visibleRows());
		if (row < this.scroll) this.scroll = row;
		if (row >= this.scroll + vis) this.scroll = row - vis + 1;
		UiSounds.click();
	}

	/** Termina a digitacao; "apply" grava o nome (Enter). */
	public void stopTyping(boolean apply) {
		if (this.input == null) return;
		String value = this.input.getValue().strip();
		this.host.removeInput(this.input);
		EditBox old = this.input;
		this.input = null;
		if (!apply || value.isEmpty()) {
			this.renaming = null;
			return;
		}
		DimMap map = MapManager.current();
		if (this.renaming != null) {
			Kingdoms.rename(map, this.renaming, value);
			if (this.renaming.equals(this.selected)) this.selected = value;
		} else {
			Kingdoms.add(value);
			this.selected = value;
		}
		this.renaming = null;
		UiSounds.toggle();
		old.setFocused(false);
	}

	/** true se a tecla foi usada pelo campo de texto. */
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
		this.input.keyPressed(keyCode, scanCode, modifiers);
		return true;
	}

	public boolean charTyped(char c, int modifiers) {
		if (this.input == null) return false;
		this.input.charTyped(c, modifiers);
		return true;
	}

	/** Corta um texto para caber na largura (com reticencias). */
	private static final class Language {
		static FormattedCharSequence trim(Font font, Component text, int width) {
			if (font.width(text) <= width) return text.getVisualOrderText();
			String s = text.getString();
			while (!s.isEmpty() && font.width(s + "...") > width) s = s.substring(0, s.length() - 1);
			return Component.literal(s + "...").getVisualOrderText();
		}
	}
}
