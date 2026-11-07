package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.recipes.RecipeJournal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Configuracoes do mod no mesmo estilo das janelas (marfim e dourado). Abre pelo botao de engrenagem da
 * barra do inventario ou pelo Mod Menu. Tudo muda na hora e e salvo em config/isekai_ui.json.
 */
public class SettingsScreen extends Screen {
	private static final int NAV_W = 100;
	private static final int ROW_H = 18;
	private static int lastCategory;

	private final Screen parent;
	private final List<Category> categories = new ArrayList<>();
	private List<Row> rows = List.of();
	private int category;
	private int scroll;
	private Row dragging;
	private Row hoveredRow;
	private int hoveredNav = -1;
	private boolean hoverDone;
	private Component status;
	private long statusNanos;

	private record Category(String icon, Component name, Supplier<List<Row>> rows) {
	}

	public SettingsScreen(Screen parent) {
		super(Component.translatable("isekai_ui.settings.title"));
		this.parent = parent;
	}

	private static Component tr(String key, Object... args) {
		return Component.translatable(key, args);
	}

	@Override
	protected void init() {
		this.categories.clear();
		this.categories.add(new Category("gear", tr("isekai_ui.settings.cat.general"), this::generalRows));
		this.categories.add(new Category("sound", tr("isekai_ui.settings.cat.sounds"), this::soundRows));
		this.categories.add(new Category("compass", tr("isekai_ui.settings.cat.instruments"), this::instrumentRows));
		this.categories.add(new Category("map", tr("isekai_ui.settings.cat.map"), this::mapRows));
		this.categories.add(new Category("list", tr("isekai_ui.settings.cat.bar"), this::barRows));
		this.categories.add(new Category("book", tr("isekai_ui.settings.cat.recipes"), this::recipeRows));
		this.category = Mth.clamp(lastCategory, 0, this.categories.size() - 1);
		rebuildRows();
	}

	private void rebuildRows() {
		this.rows = this.categories.get(this.category).rows().get();
		this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
	}

	private void select(int c) {
		if (c == this.category) return;
		this.category = c;
		lastCategory = c;
		this.scroll = 0;
		rebuildRows();
		UiSounds.click();
	}

	private void setStatus(Component c) {
		this.status = c;
		this.statusNanos = System.nanoTime();
	}

	// ------------------------------------------------------------------ o que tem em cada aba

	private List<Row> generalRows() {
		IsekaiConfig c = IsekaiConfig.get();
		List<Row> r = new ArrayList<>();
		r.add(new Header(tr("isekai_ui.settings.h.windows")));
		r.add(new Toggle("inventory", () -> c.enableInventory, v -> c.enableInventory = v));
		r.add(new Toggle("containers", () -> c.enableContainers, v -> c.enableContainers = v));
		r.add(new Toggle("stations", () -> c.enableStations, v -> c.enableStations = v));
		r.add(new Toggle("creative", () -> c.enableCreative, v -> c.enableCreative = v));
		r.add(new Toggle("shulker_preview", () -> c.shulkerPreview, v -> c.shulkerPreview = v));
		r.add(new Toggle("animations", () -> c.animations, v -> c.animations = v));
		r.add(new Header(tr("isekai_ui.settings.h.character")));
		r.add(new Toggle("third_person", () -> c.thirdPersonCamera, v -> c.thirdPersonCamera = v));
		r.add(new Toggle("pose", () -> c.poseCharacter, v -> c.poseCharacter = v));
		r.add(new Toggle("hide_held", () -> c.hideHeldItemsWhileBrowsing, v -> c.hideHeldItemsWhileBrowsing = v));
		r.add(new Action("reset_layouts", () -> {
			c.layouts.clear();
			IsekaiConfig.save();
			setStatus(tr("isekai_ui.settings.done_reset_layouts"));
		}));
		return r;
	}

	private List<Row> soundRows() {
		IsekaiConfig c = IsekaiConfig.get();
		List<Row> r = new ArrayList<>();
		r.add(new Toggle("ui_sounds", () -> c.uiSounds, v -> c.uiSounds = v));
		r.add(new Slider("volume_all", 0, 1, 0.05f, () -> c.uiSoundVolume, v -> c.uiSoundVolume = (float) v, SettingsScreen::percent,
				() -> UiSounds.preview(true)));
		r.add(new Slider("volume_windows", 0, 1, 0.05f, () -> c.windowSoundVolume, v -> c.windowSoundVolume = (float) v, SettingsScreen::percent,
				() -> UiSounds.preview(true)));
		r.add(new Slider("volume_clicks", 0, 1, 0.05f, () -> c.clickSoundVolume, v -> c.clickSoundVolume = (float) v, SettingsScreen::percent,
				() -> UiSounds.preview(false)));
		return r;
	}

	private List<Row> instrumentRows() {
		IsekaiConfig.InstrumentSettings c = IsekaiConfig.get().instruments;
		List<Row> r = new ArrayList<>();
		r.add(new Toggle("require_clock", () -> c.requireClock, v -> c.requireClock = v));
		r.add(new Toggle("require_compass", () -> c.requireCompass, v -> c.requireCompass = v));
		r.add(new Toggle("horizon", () -> c.horizonMarkers, v -> c.horizonMarkers = v));
		r.add(new Header(tr("isekai_ui.settings.h.hud_minimap")));
		r.add(new Toggle("hud_minimap", () -> c.hudMinimap, v -> c.hudMinimap = v));
		r.add(new Slider("hud_size", 48, 160, 4, () -> c.hudSize, v -> c.hudSize = (int) Math.round(v), v -> (int) Math.round(v) + "px", null));
		r.add(new Cycle("hud_corner", List.of("custom", "top_left", "top_right", "bottom_left", "bottom_right"),
				v -> tr("isekai_ui.settings.corner." + v),
				() -> c.hudX >= 0f && c.hudY >= 0f ? "custom" : c.hudCorner,
				v -> {
					if ("custom".equals(v)) {
						// "personalizado" abre a tela de arrastar
						this.minecraft.setScreen(new HudLayoutScreen(this));
						return;
					}
					c.hudCorner = v;
					c.hudX = -1f;
					c.hudY = -1f;
				}));
		r.add(new Action("hud_move", () -> this.minecraft.setScreen(new HudLayoutScreen(this))));
		return r;
	}

	private List<Row> mapRows() {
		IsekaiConfig.MapSettings c = IsekaiConfig.get().map;
		IsekaiConfig.LandsSettings lands = IsekaiConfig.get().lands;
		List<Row> r = new ArrayList<>();
		r.add(new Slider("minimap_zoom", 0.25f, 4f, 0.25f, () -> c.minimapZoom, v -> c.minimapZoom = (float) v,
				v -> String.format(Locale.ROOT, "x%.2f", v), null));
		r.add(new Toggle("show_entities", () -> c.showEntities, v -> c.showEntities = v));
		r.add(new Toggle("show_claims", () -> c.showClaims, v -> c.showClaims = v));
		r.add(new Toggle("show_grid", () -> c.showGrid, v -> c.showGrid = v));
		r.add(new Toggle("show_waypoints", () -> c.showWaypoints, v -> c.showWaypoints = v));
		r.add(new Header(tr("isekai_ui.settings.h.chat")));
		r.add(new Toggle("chat_waypoints", () -> c.chatWaypoints, v -> c.chatWaypoints = v));
		r.add(new Cycle("share_format", List.of("xaero", "journeymap"), v -> tr("isekai_ui.settings.share." + v),
				() -> c.shareFormat, v -> c.shareFormat = v));
		r.add(new Header(tr("isekai_ui.settings.h.lands")));
		r.add(new Toggle("lands_auto", () -> lands.autoDetect, v -> lands.autoDetect = v));
		return r;
	}

	private List<Row> barRows() {
		IsekaiConfig.BarSettings c = IsekaiConfig.get().bar;
		List<Row> r = new ArrayList<>();
		r.add(new Slider("bar_max", 3, 14, 1, () -> c.maxVisible, v -> c.maxVisible = (int) Math.round(v), v -> String.valueOf((int) Math.round(v)), null));
		r.add(new Info(tr("isekai_ui.settings.bar_hint")));
		List<ButtonBar.CatalogItem> items = ButtonBar.catalog();
		for (int i = 0; i < items.size(); i++) r.add(new BarItem(items, i));
		r.add(new Action("bar_reset", () -> {
			c.order.clear();
			c.hidden.clear();
			c.maxVisible = 8;
			ButtonBar.drawerOpen = false;
			IsekaiConfig.save();
			rebuildRows();
			setStatus(tr("isekai_ui.settings.done_bar_reset"));
		}));
		return r;
	}

	private List<Row> recipeRows() {
		IsekaiConfig.RecipeSettings c = IsekaiConfig.get().recipes;
		List<Row> r = new ArrayList<>();
		r.add(new Info(tr("isekai_ui.settings.recipes_hint")));
		r.add(new Toggle("show_untested", () -> c.showUntested, v -> c.showUntested = v));
		r.add(new Toggle("notify_learned", () -> c.notifyLearned, v -> c.notifyLearned = v));
		if (this.minecraft != null && this.minecraft.level != null) {
			r.add(new Info(tr("isekai_ui.settings.recipes_count", RecipeJournal.size(), RecipeJournal.fakeCount())));
			r.add(new Action("forget_fakes", () -> {
				RecipeJournal.clearFakes();
				rebuildRows();
				setStatus(tr("isekai_ui.settings.done_forget_fakes"));
			}));
		}
		return r;
	}

	private static String percent(double v) {
		return Math.round(v * 100) + "%";
	}

	// ------------------------------------------------------------------ posicoes

	private Rect panel() {
		int w = Math.min(this.width - 16, 400);
		int h = Math.min(this.height - 30, 260);
		return new Rect((this.width - w) / 2, (this.height - h) / 2 + 7, w, h);
	}

	private Rect nav(int i) {
		Rect p = panel();
		return new Rect(p.x() + 8, p.y() + 10 + i * 20, NAV_W, 18);
	}

	private Rect content() {
		Rect p = panel();
		return new Rect(p.x() + NAV_W + 18, p.y() + 9, p.w() - NAV_W - 26, p.h() - 9 - 34);
	}

	private Rect done() {
		Rect p = panel();
		return new Rect(p.right() - 8 - 84, p.bottom() - 24, 84, 16);
	}

	private Rect tipArea() {
		Rect p = panel();
		Rect d = done();
		return new Rect(p.x() + 10, p.bottom() - 28, d.x() - p.x() - 18, 24);
	}

	private int contentHeight() {
		int h = 0;
		for (Row r : this.rows) h += r.height();
		return h;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - content().h());
	}

	// ------------------------------------------------------------------ desenho

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (this.minecraft != null && this.minecraft.level == null) {
			super.renderBackground(g, mouseX, mouseY, partialTick);
			return;
		}
		Draw.fill(g, 0, 0, this.width, this.height, 0x90100C16);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Rect p = panel();
		Panels.panel(g, this.font, p, 1f, 1f, this.title, 0f);

		// abas da esquerda
		this.hoveredNav = -1;
		for (int i = 0; i < this.categories.size(); i++) {
			Rect n = nav(i);
			boolean sel = i == this.category;
			boolean hover = n.contains(mouseX, mouseY);
			if (hover) this.hoveredNav = i;
			Draw.fill(g, n.x(), n.y(), n.right(), n.bottom(), sel ? 0xC0473A63 : (hover ? 0x80302840 : 0x600A080E));
			if (sel) Draw.fill(g, n.x(), n.y(), n.x() + 2, n.bottom(), Theme.GOLD);
			Category c = this.categories.get(i);
			Draw.sprite(g, Theme.icon(c.icon()), n.x() + 5, n.y() + 3, 12, 12, sel ? Theme.TEXT_GOLD : Theme.IVORY);
			String name = this.font.plainSubstrByWidth(c.name().getString(), n.w() - 26);
			Draw.text(g, this.font, Component.literal(name), n.x() + 21, n.y() + 5, sel ? Theme.TEXT_GOLD : Theme.TEXT, false);
		}
		Draw.fill(g, p.x() + NAV_W + 12, p.y() + 8, p.x() + NAV_W + 13, p.bottom() - 32, 0x806E5B3A);

		// linhas da aba escolhida
		Rect c = content();
		this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
		this.hoveredRow = null;
		g.enableScissor(c.x(), c.y(), c.right(), c.bottom());
		int y = c.y() - this.scroll;
		int rowW = c.w() - (maxScroll() > 0 ? 7 : 0);
		for (Row row : this.rows) {
			int h = row.height();
			Rect rr = new Rect(c.x(), y, rowW, h);
			if (rr.bottom() > c.y() && rr.y() < c.bottom()) {
				boolean hover = this.dragging == null ? rr.contains(mouseX, mouseY) && c.contains(mouseX, mouseY) : this.dragging == row;
				if (hover) this.hoveredRow = row;
				if (hover && row.highlight()) Draw.fill(g, rr.x(), rr.y(), rr.right(), rr.bottom(), 0x22FFF2CF);
				row.draw(g, this.font, rr, mouseX, mouseY, hover);
			}
			y += h;
		}
		g.disableScissor();
		if (maxScroll() > 0) {
			int trackX = c.right() - 4;
			Draw.fill(g, trackX, c.y(), trackX + 3, c.bottom(), 0xFF1A1620);
			int barH = Math.max(10, c.h() * c.h() / contentHeight());
			int barY = c.y() + (c.h() - barH) * this.scroll / maxScroll();
			Draw.fill(g, trackX, barY, trackX + 3, barY + barH, Theme.GOLD);
		}

		// explicacao do que esta embaixo do mouse (ou um aviso)
		Rect t = tipArea();
		Panels.ornament(g, p.x() + 10, p.bottom() - 33, p.w() - 20, 0.9f);
		Component tip = null;
		int tipColor = Theme.TEXT_DIM;
		if (this.status != null && System.nanoTime() - this.statusNanos < 3_000_000_000L) {
			tip = this.status;
			tipColor = Theme.TEXT_GOLD;
		} else if (this.hoveredRow != null) {
			tip = this.hoveredRow.tip();
		}
		if (tip != null) {
			List<FormattedCharSequence> lines = this.font.split(tip, t.w());
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				g.drawString(this.font, lines.get(i), t.x(), t.y() + 2 + i * 10, tipColor, false);
			}
		}
		Rect d = done();
		this.hoverDone = d.contains(mouseX, mouseY);
		HudLayoutScreen.pill(g, d, tr("isekai_ui.settings.done"), this.hoverDone, true);
	}

	// ------------------------------------------------------------------ entrada

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && this.hoverDone) {
			UiSounds.click();
			onClose();
			return true;
		}
		if (button == 0 && this.hoveredNav >= 0) {
			select(this.hoveredNav);
			return true;
		}
		Rect c = content();
		if (c.contains(mouseX, mouseY)) {
			int y = c.y() - this.scroll;
			int rowW = c.w() - (maxScroll() > 0 ? 7 : 0);
			for (Row row : this.rows) {
				Rect rr = new Rect(c.x(), y, rowW, row.height());
				if (rr.contains(mouseX, mouseY)) {
					if (row.click(rr, mouseX, mouseY, button)) {
						if (row instanceof Slider) this.dragging = row;
						IsekaiConfig.save();
					}
					return true;
				}
				y += row.height();
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (this.dragging instanceof Slider s) {
			s.dragTo(mouseX);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (this.dragging != null) {
			Row r = this.dragging;
			this.dragging = null;
			r.release();
			IsekaiConfig.save();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (content().contains(mouseX, mouseY)) {
			this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY) * ROW_H, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		IsekaiConfig.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ linhas

	private abstract static class Row {
		int height() {
			return ROW_H;
		}

		boolean highlight() {
			return true;
		}

		abstract void draw(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY, boolean hover);

		boolean click(Rect r, double mouseX, double mouseY, int button) {
			return false;
		}

		void release() {
		}

		Component tip() {
			return null;
		}

		static void label(GuiGraphics g, Font font, Component text, Rect r, int maxW, int color) {
			String s = font.plainSubstrByWidth(text.getString(), maxW);
			Draw.text(g, font, Component.literal(s), r.x() + 4, r.y() + 5, color, false);
		}
	}

	/** Linha com nome e explicacao vindos das chaves isekai_ui.settings.opt.<key> e .<key>.tip */
	private abstract static class Option extends Row {
		final String key;

		Option(String key) {
			this.key = key;
		}

		Component name() {
			return Component.translatable("isekai_ui.settings.opt." + this.key);
		}

		@Override
		Component tip() {
			return Component.translatable("isekai_ui.settings.opt." + this.key + ".tip");
		}
	}

	private static final class Header extends Row {
		private final Component text;

		Header(Component text) {
			this.text = text;
		}

		@Override
		int height() {
			return 16;
		}

		@Override
		boolean highlight() {
			return false;
		}

		@Override
		void draw(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY, boolean hover) {
			Component t = Panels.upper(this.text);
			Draw.text(g, font, t, r.x() + 2, r.y() + 6, Theme.TEXT_GOLD, false);
			int x = r.x() + 6 + font.width(t);
			Draw.fill(g, x, r.y() + 10, r.right() - 2, r.y() + 11, 0x806E5B3A);
		}
	}

	private static final class Info extends Row {
		private final Component text;

		Info(Component text) {
			this.text = text;
		}

		@Override
		int height() {
			return 14;
		}

		@Override
		boolean highlight() {
			return false;
		}

		@Override
		void draw(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY, boolean hover) {
			Draw.textFit(g, font, this.text, r.cx(), r.y() + 3, r.w() - 6, Theme.TEXT_DIM, false);
		}
	}

	private static final class Toggle extends Option {
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;

		Toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
			super(key);
			this.get = get;
			this.set = set;
		}

		@Override
		void draw(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY, boolean hover) {
			boolean on = this.get.getAsBoolean();
			label(g, font, name(), r, r.w() - 40, hover ? Theme.TEXT_GOLD : Theme.TEXT);
			int sx = r.right() - 28;
			int sy = r.y() + 4;
			Draw.fill(g, sx, sy, sx + 24, sy + 10, on ? 0xFF8C6A2E : 0xFF2A2433);
			Draw.fill(g, sx, sy, sx + 24, sy + 1, on ? Theme.GOLD : 0xFF4A4256);
			Draw.fill(g, sx, sy + 9, sx + 24, sy + 10, on ? 0xFF5E4720 : 0xFF17131D);
			int kx = on ? sx + 14 : sx + 1;
			Draw.fill(g, kx, sy + 1, kx + 9, sy + 9, on ? Theme.IVORY : 0xFF9A90A4);
		}

		@Override
		boolean click(Rect r, double mouseX, double mouseY, int button) {
			if (button != 0) return false;
			this.set.accept(!this.get.getAsBoolean());
			UiSounds.toggle();
			return true;
		}
	}

	private static final class Slider extends Option {
		private final float min;
		private final float max;
		private final float step;
		private final DoubleSupplier get;
		private final DoubleConsumer set;
		private final DoubleFunction<String> format;
		private final Runnable onRelease;
		private Rect lastTrack;

		Slider(String key, float min, float max, float step, DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> format, Runnable onRelease) {
			super(key);
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.format = format;
			this.onRelease = onRelease;
		}

		private Rect track(Rect r) {
			return new Rect(r.right() - 120, r.y() + 7, 82, 4);
		}

		@Override
		void draw(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY, boolean hover) {
			label(g, font, name(), r, r.w() - 130, hover ? Theme.TEXT_GOLD : Theme.TEXT);
			Rect t = track(r);
			this.lastTrack = t;
			double v = this.get.getAsDouble();
			float f = (float) Mth.clamp((v - this.min) / (this.max - this.min), 0, 1);
			Draw.fill(g, t.x(), t.y(), t.right(), t.bottom(), 0xFF2A2433);
			Draw.fill(g, t.x(), t.y(), t.x() + Math.round(t.w() * f), t.bottom(), Theme.GOLD);
			int kx = t.x() + Math.round(t.w() * f);
			Draw.fill(g, kx - 2, t.y() - 3, kx + 2, t.bottom() + 3, hover ? Theme.TEXT_GOLD : Theme.IVORY);
			String text = this.format.apply(v);
			Draw.text(g, font, Component.literal(text), r.right() - 4 - font.width(text), r.y() + 5, Theme.TEXT, false);
		}

		@Override
		boolean click(Rect r, double mouseX, double mouseY, int button) {
			if (button != 0) return false;
			this.lastTrack = track(r);
			dragTo(mouseX);
			return true;
		}

		void dragTo(double mouseX) {
			if (this.lastTrack == null) return;
			double f = Mth.clamp((mouseX - this.lastTrack.x()) / this.lastTrack.w(), 0, 1);
			double v = this.min + f * (this.max - this.min);
			v = Math.round(v / this.step) * this.step;
			this.set.accept(Mth.clamp(v, this.min, this.max));
		}

		@Override
		void release() {
			if (this.onRelease != null) this.onRelease.run();
		}
	}

	private static final class Cycle extends Option {
		private final List<String> values;
		private final Function<String, Component> names;
		private final Supplier<String> get;
		private final Consumer<String> set;

		Cycle(String key, List<String> values, Function<String, Component> names, Supplier<String> get, Consumer<String> set) {
			super(key);
			this.values = values;
			this.names = names;
			this.get = get;
			this.set = set;
		}

		private Rect area(Rect r) {
			return new Rect(r.right() - 122, r.y() + 2, 118, 14);
		}

		@Override
		void draw(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY, boolean hover) {
			label(g, font, name(), r, r.w() - 130, hover ? Theme.TEXT_GOLD : Theme.TEXT);
			Rect a = area(r);
			Draw.fill(g, a.x(), a.y(), a.right(), a.bottom(), 0xC00A080E);
			Draw.fill(g, a.x(), a.bottom() - 1, a.right(), a.bottom(), 0x80C9A35A);
			boolean left = hover && mouseX < a.cx();
			Draw.sprite(g, Theme.icon("prev"), a.x() + 1, a.y() + 1, 12, 12, hover && left ? Theme.TEXT_GOLD : 0xFF9A90A4);
			Draw.sprite(g, Theme.icon("next"), a.right() - 13, a.y() + 1, 12, 12, hover && !left ? Theme.TEXT_GOLD : 0xFF9A90A4);
			Draw.textFit(g, font, this.names.apply(this.get.get()), a.cx(), a.y() + 3, a.w() - 28, Theme.TEXT, false);
		}

		@Override
		boolean click(Rect r, double mouseX, double mouseY, int button) {
			int i = Math.max(0, this.values.indexOf(this.get.get()));
			boolean back = button == 1 || mouseX < area(r).cx();
			int n = this.values.size();
			this.set.accept(this.values.get((i + (back ? n - 1 : 1)) % n));
			UiSounds.click();
			return true;
		}
	}

	private static final class Action extends Option {
		private final Runnable run;

		Action(String key, Runnable run) {
			super(key);
			this.run = run;
		}

		@Override
		int height() {
			return 20;
		}

		@Override
		boolean highlight() {
			return false;
		}

		@Override
		void draw(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY, boolean hover) {
			HudLayoutScreen.pill(g, new Rect(r.x() + 4, r.y() + 2, r.w() - 8, 16), name(), hover, false);
		}

		@Override
		boolean click(Rect r, double mouseX, double mouseY, int button) {
			if (button != 0) return false;
			UiSounds.click();
			this.run.run();
			return true;
		}
	}

	/** Um botao da barra: icone, nome, mostrar/esconder e mover para cima/baixo. */
	private final class BarItem extends Row {
		private final List<ButtonBar.CatalogItem> items;
		private final int index;

		BarItem(List<ButtonBar.CatalogItem> items, int index) {
			this.items = items;
			this.index = index;
		}

		private ButtonBar.CatalogItem item() {
			return this.items.get(this.index);
		}

		private boolean hidden() {
			return IsekaiConfig.get().bar.hidden.contains(item().id());
		}

		private Rect eye(Rect r) {
			return new Rect(r.right() - 50, r.y() + 2, 14, 14);
		}

		private Rect up(Rect r) {
			return new Rect(r.right() - 33, r.y() + 2, 14, 14);
		}

		private Rect down(Rect r) {
			return new Rect(r.right() - 17, r.y() + 2, 14, 14);
		}

		@Override
		void draw(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY, boolean hover) {
			ButtonBar.CatalogItem it = item();
			boolean hidden = hidden();
			boolean locked = ButtonBar.SETTINGS.equals(it.id());
			Draw.sprite(g, Theme.icon(it.icon()), r.x() + 4, r.y() + 3, 12, 12, hidden ? 0xFF5E5868 : Theme.IVORY);
			String name = font.plainSubstrByWidth(it.name().getString(), r.w() - 80);
			Draw.text(g, font, Component.literal(name), r.x() + 21, r.y() + 5, hidden ? 0xFF6E6878 : (hover ? Theme.TEXT_GOLD : Theme.TEXT), false);
			button(g, eye(r), hidden ? "eye_off" : "eye", hover && eye(r).contains(mouseX, mouseY), !locked);
			button(g, up(r), "up", hover && up(r).contains(mouseX, mouseY), this.index > 0);
			button(g, down(r), "down", hover && down(r).contains(mouseX, mouseY), this.index < this.items.size() - 1);
		}

		private void button(GuiGraphics g, Rect b, String icon, boolean hover, boolean enabled) {
			if (hover && enabled) Draw.fill(g, b.x(), b.y(), b.right(), b.bottom(), 0x60473A63);
			int tint = !enabled ? 0xFF4A4452 : (hover ? Theme.TEXT_GOLD : 0xFFB8AFC2);
			Draw.sprite(g, Theme.icon(icon), b.x() + 1, b.y() + 1, 12, 12, tint);
		}

		@Override
		boolean click(Rect r, double mouseX, double mouseY, int button) {
			if (button != 0) return false;
			IsekaiConfig.BarSettings c = IsekaiConfig.get().bar;
			ButtonBar.CatalogItem it = item();
			if (eye(r).contains(mouseX, mouseY)) {
				if (ButtonBar.SETTINGS.equals(it.id())) return false;
				if (!c.hidden.remove(it.id())) c.hidden.add(it.id());
				UiSounds.toggle();
				return true;
			}
			int delta = up(r).contains(mouseX, mouseY) ? -1 : down(r).contains(mouseX, mouseY) ? 1 : 0;
			if (delta == 0) return false;
			int to = this.index + delta;
			if (to < 0 || to >= this.items.size()) return false;
			List<String> order = new ArrayList<>();
			for (ButtonBar.CatalogItem ci : this.items) order.add(ci.id());
			String moved = order.remove(this.index);
			order.add(to, moved);
			// mantem ids que nao aparecem agora (ex.: comandos de outro servidor) no fim da lista
			for (String old : c.order) if (!order.contains(old)) order.add(old);
			c.order.clear();
			c.order.addAll(order);
			UiSounds.click();
			rebuildRows();
			return true;
		}

		@Override
		Component tip() {
			if (ButtonBar.SETTINGS.equals(item().id())) return Component.translatable("isekai_ui.settings.bar_item_locked");
			return Component.translatable("isekai_ui.settings.bar_item_tip");
		}
	}
}
