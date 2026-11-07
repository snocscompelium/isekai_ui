package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.anim.Ease;
import com.compelium.isekaiui.client.CpmBridge;
import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.commands.CommandMemory;
import com.compelium.isekaiui.config.IsekaiConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Barra de botoes hexagonais (inventario e criativo). A ordem e os botoes escondidos vem das configuracoes;
 * quando ha mais botoes do que cabem, os ultimos vao para a "gaveta" (botao "...") que abre por cima da barra.
 */
public final class ButtonBar {
	public static final String MORE = "more";
	public static final String SETTINGS = "settings";
	private static final int STEP = InventoryLayout.BUTTON_STEP;
	private static final int DRAWER_COLS = 4;
	private static final int DRAWER_STEP_Y = 23;

	/** Um botao: id fixo (para a ordem salva), icone, dica, acao e se esta "aceso". */
	public record Entry(String id, String icon, Component tooltip, Runnable action, BooleanSupplier on) {
	}

	/** Gaveta aberta (vale para o inventario e o criativo). */
	static boolean drawerOpen;

	private final List<Entry> all = new ArrayList<>();
	private final List<Entry> bar = new ArrayList<>();
	private final List<Entry> drawer = new ArrayList<>();
	private int hoveredBar = -1;
	private int hoveredDrawer = -1;

	public void clear() {
		this.all.clear();
		this.bar.clear();
		this.drawer.clear();
	}

	public void add(String id, String icon, Component tooltip, Runnable action, boolean on) {
		this.all.add(new Entry(id, icon, tooltip, action, () -> on));
	}

	/** Botao cujo "aceso" muda sozinho (ex.: lixeira armada para limpar tudo). */
	public void add(String id, String icon, Component tooltip, Runnable action, BooleanSupplier on) {
		this.all.add(new Entry(id, icon, tooltip, action, on));
	}

	/** Aplica a ordem e os escondidos das configuracoes e separa o que fica na barra e o que vai para a gaveta. */
	public void arrange(Runnable toggleDrawer) {
		IsekaiConfig.BarSettings cfg = IsekaiConfig.get().bar;
		Map<String, Integer> pos = new HashMap<>();
		for (int i = 0; i < cfg.order.size(); i++) pos.putIfAbsent(cfg.order.get(i), i);
		List<Entry> sorted = new ArrayList<>(this.all);
		int base = cfg.order.size();
		sorted.sort(Comparator.comparingInt(e -> pos.getOrDefault(e.id(), base + this.all.indexOf(e))));
		sorted.removeIf(e -> cfg.hidden.contains(e.id()) && !SETTINGS.equals(e.id()));
		this.bar.clear();
		this.drawer.clear();
		int max = Math.max(3, cfg.maxVisible);
		if (sorted.size() <= max) {
			this.bar.addAll(sorted);
		} else {
			// barra compacta: os primeiros ficam, o resto vai para a gaveta "..."; a engrenagem nunca vai para a gaveta
			Entry gear = null;
			for (Entry e : sorted) if (SETTINGS.equals(e.id())) gear = e;
			int gearPos = gear == null ? -1 : sorted.indexOf(gear);
			List<Entry> rest = new ArrayList<>(sorted);
			if (gear != null) rest.remove(gear);
			int room = Math.max(1, max - 1 - (gear != null ? 1 : 0));
			this.bar.addAll(rest.subList(0, Math.min(room, rest.size())));
			if (room < rest.size()) this.drawer.addAll(rest.subList(room, rest.size()));
			if (!this.drawer.isEmpty()) {
				this.bar.add(new Entry(MORE, "more", Component.translatable("isekai_ui.button.more", this.drawer.size()), toggleDrawer, () -> drawerOpen));
			}
			if (gear != null) this.bar.add(gearPos >= 0 && gearPos < room ? gearPos : this.bar.size(), gear);
		}
		if (this.drawer.isEmpty()) drawerOpen = false;
	}

	public int barCount() {
		return Math.max(1, this.bar.size());
	}

	public boolean hasDrawer() {
		return !this.drawer.isEmpty();
	}

	public List<Entry> barEntries() {
		return this.bar;
	}

	public List<Entry> drawerEntries() {
		return this.drawer;
	}

	public static Rect buttonRect(Rect barRect, int i) {
		return new Rect(barRect.x() + i * STEP, barRect.y() + 1, InventoryLayout.HEX_W, InventoryLayout.HEX_H);
	}

	/** Tamanho da janela da gaveta para n botoes. */
	public static int drawerWidth(int n) {
		return Math.min(DRAWER_COLS, Math.max(1, n)) * STEP + 10;
	}

	public static int drawerHeight(int n) {
		int rows = (Math.max(1, n) + DRAWER_COLS - 1) / DRAWER_COLS;
		return 8 + rows * DRAWER_STEP_Y + 4;
	}

	/** Onde a gaveta abre: em cima do botao "...". */
	public Rect drawerRect(Rect barRect) {
		int n = this.drawer.size();
		int w = drawerWidth(n);
		int h = drawerHeight(n);
		Rect more = buttonRect(barRect, Math.max(0, this.bar.size() - 1));
		return new Rect(more.cx() - w / 2, barRect.y() - 10 - h, w, h);
	}

	private static Rect drawerButton(Rect d, int i) {
		int cols = Math.min(DRAWER_COLS, 99);
		return new Rect(d.x() + 6 + (i % cols) * STEP, d.y() + 6 + (i / cols) * DRAWER_STEP_Y, InventoryLayout.HEX_W, InventoryLayout.HEX_H);
	}

	/** Desenha a barra; "progress" anima a entrada dos botoes (um depois do outro). */
	public void drawBar(GuiGraphics g, Rect barRect, float alpha, float progress, int mouseX, int mouseY, boolean canHover) {
		int before = this.hoveredBar;
		this.hoveredBar = -1;
		int n = this.bar.size();
		// um botao depois do outro, e todos terminam juntos quando a janela termina de abrir
		float stagger = Math.min(0.12f, 0.5f / Math.max(1, n - 1));
		float span = Math.max(0.2f, 1f - (n - 1) * stagger);
		for (int i = 0; i < n; i++) {
			float pi = Ease.clamp01((progress - i * stagger) / span);
			Rect r = buttonRect(barRect, i);
			Rect shown = r.offset(0, Math.round((1f - Ease.outCubic(pi)) * 10f));
			boolean hover = canHover && r.contains(mouseX, mouseY);
			if (hover) this.hoveredBar = i;
			Entry e = this.bar.get(i);
			boolean on = e.on().getAsBoolean();
			Panels.hexButton(g, shown, e.icon(), hover, on, alpha * Ease.outCubic(pi));
		}
		if (this.hoveredBar >= 0 && this.hoveredBar != before) UiSounds.hover();
	}

	public void drawDrawer(GuiGraphics g, Rect d, float alpha, int mouseX, int mouseY, boolean canHover) {
		int before = this.hoveredDrawer;
		this.hoveredDrawer = -1;
		for (int i = 0; i < this.drawer.size(); i++) {
			Rect r = drawerButton(d, i);
			boolean hover = canHover && r.contains(mouseX, mouseY);
			if (hover) this.hoveredDrawer = i;
			Entry e = this.drawer.get(i);
			Panels.hexButton(g, r, e.icon(), hover, e.on().getAsBoolean(), alpha);
		}
		if (this.hoveredDrawer >= 0 && this.hoveredDrawer != before) UiSounds.hover();
	}

	/** Clique na barra: roda a acao do botao. */
	public boolean clickBar(Rect barRect, double mouseX, double mouseY) {
		for (int i = 0; i < this.bar.size(); i++) {
			if (buttonRect(barRect, i).contains(mouseX, mouseY)) {
				UiSounds.click();
				this.bar.get(i).action().run();
				return true;
			}
		}
		return false;
	}

	/** Clique na gaveta: roda a acao e fecha a gaveta (devolve true se usou o clique). */
	public boolean clickDrawer(Rect d, double mouseX, double mouseY, Runnable closeDrawer) {
		for (int i = 0; i < this.drawer.size(); i++) {
			if (drawerButton(d, i).contains(mouseX, mouseY)) {
				UiSounds.click();
				Entry e = this.drawer.get(i);
				closeDrawer.run();
				e.action().run();
				return true;
			}
		}
		return true;
	}

	/** Dica do botao embaixo do mouse (barra ou gaveta), ou null. */
	public Component tooltip(boolean drawerVisible) {
		if (this.hoveredBar >= 0 && this.hoveredBar < this.bar.size()) return this.bar.get(this.hoveredBar).tooltip();
		if (drawerVisible && this.hoveredDrawer >= 0 && this.hoveredDrawer < this.drawer.size()) return this.drawer.get(this.hoveredDrawer).tooltip();
		return null;
	}

	public boolean hoveringBar() {
		return this.hoveredBar >= 0;
	}

	/** Retangulo de um botao pelo id (barra ou gaveta aberta), para os testes automaticos. */
	public Rect find(String id, Rect barRect, Rect drawerRect) {
		for (int i = 0; i < this.bar.size(); i++) if (this.bar.get(i).id().equals(id)) return buttonRect(barRect, i);
		if (drawerRect != null) {
			for (int i = 0; i < this.drawer.size(); i++) if (this.drawer.get(i).id().equals(id)) return drawerButton(drawerRect, i);
		}
		return null;
	}

	public boolean inDrawer(String id) {
		for (Entry e : this.drawer) if (e.id().equals(id)) return true;
		return false;
	}

	public void resetHover() {
		this.hoveredBar = -1;
		this.hoveredDrawer = -1;
	}

	// ------------------------------------------------------------------ lista para as configuracoes

	/** Um botao que pode aparecer na barra (para a tela de configuracoes). */
	public record CatalogItem(String id, String icon, Component name) {
	}

	/** Todos os botoes conhecidos, na ordem atual da barra. */
	public static List<CatalogItem> catalog() {
		List<CatalogItem> out = new ArrayList<>();
		out.add(item("map", "map"));
		out.add(item("camera", "camera_third"));
		out.add(item("commands", "list"));
		out.add(item("armor", "armor"));
		out.add(item("grimoire", "book"));
		out.add(item("chest", "chest"));
		if (CpmBridge.installed()) out.add(item("cpm", "hanger"));
		out.add(item("classic", "catalog"));
		out.add(item("trash", "trash"));
		try {
			for (CommandMemory.Entry e : CommandMemory.pinned()) {
				out.add(new CatalogItem(pinId(e), e.icon, Component.literal(e.label + "  (/" + e.command + ")")));
			}
		} catch (RuntimeException ignored) {
			// fora de um mundo a lista de comandos nao existe
		}
		List<IsekaiConfig.MenuButton> cfgButtons = IsekaiConfig.get().menuButtons;
		for (int i = 0; i < cfgButtons.size(); i++) {
			IsekaiConfig.MenuButton b = cfgButtons.get(i);
			if (b == null || b.command == null || b.command.isBlank()) continue;
			String label = b.label == null || b.label.isBlank() ? "/" + b.command.strip() : b.label;
			out.add(new CatalogItem("cfg:" + i, b.icon == null ? "star" : b.icon, Component.literal(label)));
		}
		out.add(item(SETTINGS, "gear"));
		IsekaiConfig.BarSettings cfg = IsekaiConfig.get().bar;
		Map<String, Integer> pos = new HashMap<>();
		for (int i = 0; i < cfg.order.size(); i++) pos.putIfAbsent(cfg.order.get(i), i);
		List<CatalogItem> base = new ArrayList<>(out);
		out.sort(Comparator.comparingInt(c -> pos.getOrDefault(c.id(), cfg.order.size() + base.indexOf(c))));
		return out;
	}

	private static CatalogItem item(String id, String icon) {
		return new CatalogItem(id, icon, Component.translatable("isekai_ui.bar." + id));
	}

	public static String pinId(CommandMemory.Entry e) {
		return "pin:" + e.command;
	}
}
