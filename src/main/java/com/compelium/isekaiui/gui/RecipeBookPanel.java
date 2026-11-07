package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.recipes.RecipeJournal;
import com.compelium.isekaiui.recipes.RecipeJournal.Station;
import com.compelium.isekaiui.recipes.RecipePlacer;
import com.compelium.isekaiui.recipes.RecipeView;
import com.compelium.isekaiui.recipes.ServerRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Janela do grimorio de receitas: abas por estacao, busca, lista de resultados e os detalhes da receita
 * escolhida com o botao "Colocar" (ou "Testar", para as receitas do servidor que ainda nao foram testadas).
 */
public class RecipeBookPanel implements IsekaiMenuScreen.PanelContent {
	public static final int H = 160;
	/** Largura da parte de detalhes (grade da receita, botao e dica). */
	private static final int DETAIL_W = 98;
	public static final int MAX_COLS = 6;
	public static final int MIN_COLS = 4;
	private static final int ROWS = 5;
	private static final int CELL = 18;
	private static final long STATUS_NANOS = 3_500_000_000L;

	/** Abas do grimorio (uma por tipo de estacao). */
	public enum Tab {
		CRAFTING(Items.CRAFTING_TABLE),
		FURNACES(Items.FURNACE),
		BREWING(Items.BREWING_STAND),
		SMITHING(Items.SMITHING_TABLE),
		STONECUTTING(Items.STONECUTTER);

		final Item icon;

		Tab(Item icon) {
			this.icon = icon;
		}

		boolean has(Station st) {
			return switch (this) {
				case CRAFTING -> st == Station.CRAFTING;
				case FURNACES -> st.isFurnace();
				case BREWING -> st == Station.BREWING;
				case SMITHING -> st == Station.SMITHING;
				case STONECUTTING -> st == Station.STONECUTTING;
			};
		}

		Component title() {
			return Component.translatable("isekai_ui.recipes.tab." + name().toLowerCase(Locale.ROOT));
		}
	}

	/** O que a tela que mostra o grimorio precisa oferecer. */
	public interface Host {
		AbstractContainerMenu menu();

		void addInput(EditBox box);

		void focusInput(EditBox box);

		void blurInput();
	}

	private final Font font;
	private final Host host;
	private final EditBox search;
	/** Colunas da lista de receitas (menos colunas em telas pequenas, para o grimorio caber ao lado da estacao). */
	private int cols = MAX_COLS;
	private Tab tab;
	private List<RecipeView> views = List.of();
	private int learnedCount;
	private int builtVersion = -1;
	private String builtQuery;
	private Tab builtTab;
	private boolean builtUntested;
	private long builtAt;
	private int scroll;
	private RecipeView selected;

	private int hoveredTab = -1;
	private boolean hoverUntested;
	private boolean hoverPlace;
	private ItemStack hoveredItem = ItemStack.EMPTY;
	private RecipeView hoveredView;
	private Component status;
	private long statusNanos;

	public RecipeBookPanel(Font font, Host host, Tab initial) {
		this.font = font;
		this.host = host;
		this.tab = initial;
		this.search = new EditBox(font, 0, 0, 100, 10, Component.translatable("isekai_ui.recipes.search"));
		this.search.setBordered(false);
		this.search.setMaxLength(40);
		this.search.setTextColor(Theme.TEXT);
		this.search.setHint(Component.translatable("isekai_ui.recipes.search_hint"));
		this.search.setResponder(t -> this.scroll = 0);
	}

	/** Largura da janela com "cols" colunas de receitas. */
	public static int width(int cols) {
		return 7 + cols * CELL + 13 + DETAIL_W + 7;
	}

	public int width() {
		return width(this.cols);
	}

	public void setColumns(int c) {
		this.cols = Mth.clamp(c, 3, MAX_COLS);
		this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
	}

	/** Chamado a cada init da tela: devolve a caixa de busca para a lista de widgets da tela. */
	public void attach() {
		this.host.addInput(this.search);
	}

	public void setTab(Tab t) {
		if (t == null || t == this.tab) return;
		this.tab = t;
		this.scroll = 0;
		this.selected = null;
	}

	// ------------------------------------------------------------------ lista

	private void rebuildIfNeeded() {
		String q = this.search.getValue().strip().toLowerCase(Locale.ROOT);
		boolean untested = IsekaiConfig.get().recipes.showUntested;
		long now = System.currentTimeMillis();
		if (this.builtVersion == RecipeJournal.version() && q.equals(this.builtQuery) && this.tab == this.builtTab
				&& untested == this.builtUntested && (!untested || now - this.builtAt < 5000L)) return;
		this.builtVersion = RecipeJournal.version();
		this.builtQuery = q;
		this.builtTab = this.tab;
		this.builtUntested = untested;
		this.builtAt = now;
		List<RecipeView> learned = new ArrayList<>();
		for (RecipeJournal.Entry e : RecipeJournal.all()) {
			if (this.tab.has(e.station) && matches(e.result, q)) learned.add(view(e));
		}
		Comparator<RecipeView> byName = Comparator.comparing(v -> v.result().getHoverName().getString().toLowerCase(Locale.ROOT));
		learned.sort(byName);
		this.learnedCount = learned.size();
		List<RecipeView> out = new ArrayList<>(learned);
		if (untested) {
			List<RecipeView> others = new ArrayList<>();
			for (RecipeView v : ServerRecipes.untested()) if (this.tab.has(v.station()) && matches(v.result(), q)) others.add(v);
			others.sort(byName);
			out.addAll(others);
		}
		this.views = out;
		if (this.selected != null && !this.views.contains(this.selected)) {
			// a receita escolhida pode ter virado "anotada": procura a mesma pelo resultado
			RecipeView again = null;
			for (RecipeView v : this.views) {
				if (ItemStack.isSameItemSameComponents(v.result(), this.selected.result()) && v.station() == this.selected.station()) {
					again = v;
					break;
				}
			}
			this.selected = again;
		}
		this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
	}

	private static boolean matches(ItemStack s, String q) {
		if (q.isEmpty()) return true;
		if (s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
		return BuiltInRegistries.ITEM.getKey(s.getItem()).toString().contains(q);
	}

	private static RecipeView view(RecipeJournal.Entry e) {
		List<List<ItemStack>> in = new ArrayList<>();
		for (ItemStack s : e.inputs) in.add(s.isEmpty() ? List.of() : List.of(s));
		return new RecipeView(e.station, e.w, e.h, in, e.result, e, null, null);
	}

	private int maxScroll() {
		int rows = (this.views.size() + this.cols - 1) / this.cols;
		return Math.max(0, rows - ROWS);
	}

	// ------------------------------------------------------------------ posicoes

	private static Rect tabRect(Rect r, int i) {
		return new Rect(r.x() + 7 + i * 20, r.y() + 7, 18, 18);
	}

	private static Rect untestedRect(Rect r) {
		return new Rect(r.right() - 7 - 18, r.y() + 7, 18, 18);
	}

	private Rect searchRect(Rect r) {
		return new Rect(r.x() + 7, r.y() + 29, this.cols * CELL + 6, 12);
	}

	private static int gridX(Rect r) {
		return r.x() + 8;
	}

	private static int gridY(Rect r) {
		return r.y() + 45;
	}

	private Rect detail(Rect r) {
		int x = r.x() + 7 + this.cols * CELL + 13;
		return new Rect(x, r.y() + 29, r.right() - 7 - x, 106);
	}

	private Rect placeRect(Rect r) {
		Rect d = detail(r);
		return new Rect(d.x(), d.y() + 72, d.w(), 16);
	}

	// ------------------------------------------------------------------ desenho

	@Override
	public void draw(GuiGraphics g, Rect r, float a, int mouseX, int mouseY, float partialTick, boolean canHover) {
		rebuildIfNeeded();
		this.hoveredTab = -1;
		this.hoverUntested = false;
		this.hoverPlace = false;
		this.hoveredItem = ItemStack.EMPTY;
		this.hoveredView = null;
		boolean items = a > 0.5f;

		// abas
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			Rect t = tabRect(r, i);
			boolean sel = tabs[i] == this.tab;
			boolean hover = canHover && t.contains(mouseX, mouseY);
			if (hover) this.hoveredTab = i;
			Draw.fill(g, t.x(), t.y(), t.right(), t.bottom(), Draw.alpha(sel ? 0xC0473A63 : 0x900A080E, a));
			Draw.fill(g, t.x(), t.bottom() - 1, t.right(), t.bottom(), Draw.alpha(sel ? 0xFFF2C76A : (hover ? 0xFFB89552 : 0xFF3A3346), a));
			if (items) g.renderItem(new ItemStack(tabs[i].icon), t.x() + 1, t.y() + 1);
		}
		Rect u = untestedRect(r);
		boolean untested = IsekaiConfig.get().recipes.showUntested;
		this.hoverUntested = canHover && u.contains(mouseX, mouseY);
		Draw.fill(g, u.x(), u.y(), u.right(), u.bottom(), Draw.alpha(untested ? 0xC0473A63 : 0x900A080E, a));
		Draw.fill(g, u.x(), u.bottom() - 1, u.right(), u.bottom(), Draw.alpha(untested ? 0xFFF2C76A : (this.hoverUntested ? 0xFFB89552 : 0xFF3A3346), a));
		Draw.textCentered(g, this.font, Component.literal("?"), u.cx(), u.y() + 5, Draw.alpha(untested ? Theme.TEXT_GOLD : 0xFF9A90A4, a), false);

		// busca
		Rect s = searchRect(r);
		Draw.fill(g, s.x(), s.y(), s.right(), s.bottom(), Draw.alpha(0xC00A080E, a));
		Draw.fill(g, s.x(), s.bottom() - 1, s.right(), s.bottom(), Draw.alpha(Theme.GOLD, a * (this.search.isFocused() ? 1f : 0.5f)));
		Draw.sprite(g, Theme.icon("search"), s.x() + 2, s.y(), 12, 12, Draw.alpha(0xFF9A90A4, a));
		this.search.setX(s.x() + 15);
		this.search.setY(s.y() + 2);
		this.search.setWidth(s.w() - 18);
		this.search.render(g, mouseX, mouseY, partialTick);

		// resultados
		int gx = gridX(r);
		int gy = gridY(r);
		this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
		long now = System.currentTimeMillis();
		for (int i = 0; i < this.cols * ROWS; i++) {
			int idx = this.scroll * this.cols + i;
			int x = gx + (i % this.cols) * CELL;
			int y = gy + (i / this.cols) * CELL;
			Draw.sprite(g, Theme.SLOT, x - 1, y - 1, 18, 18, a);
			if (idx >= this.views.size()) continue;
			RecipeView v = this.views.get(idx);
			boolean hover = canHover && mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
			if (hover) {
				this.hoveredView = v;
				this.hoveredItem = v.result();
				Draw.fill(g, x, y, x + 16, y + 16, Draw.alpha(0x30FFF2CF, a));
			}
			if (items) {
				g.renderItem(v.result(), x, y);
				g.renderItemDecorations(this.font, v.result(), x, y);
			}
			if (v == this.selected || !v.tested()) {
				Draw.push(g);
				Draw.layer(g, 200f);
				if (!v.tested()) {
					Draw.fill(g, x, y, x + 16, y + 16, Draw.alpha(0x700A080E, a));
					Draw.text(g, this.font, Component.literal("?"), x + 11, y, Draw.alpha(Theme.TEXT_GOLD, a), true);
				}
				if (v == this.selected) frame(g, x - 1, y - 1, 18, 18, Draw.alpha(Theme.GOLD, a));
				Draw.pop(g);
			}
		}
		if (maxScroll() > 0) {
			int trackX = gx + this.cols * CELL + 2;
			int trackH = ROWS * CELL - 2;
			Draw.fill(g, trackX, gy - 1, trackX + 3, gy - 1 + trackH, Draw.alpha(0xFF1A1620, a));
			int barH = Math.max(8, trackH * ROWS / (ROWS + maxScroll()));
			int barY = gy - 1 + (trackH - barH) * this.scroll / maxScroll();
			Draw.fill(g, trackX, barY, trackX + 3, barY + barH, Draw.alpha(Theme.GOLD, a));
		}
		if (this.views.isEmpty()) {
			Component msg = Component.translatable(this.search.getValue().isBlank() ? "isekai_ui.recipes.empty" : "isekai_ui.recipes.nothing");
			List<FormattedCharSequence> lines = this.font.split(msg, r.w() - 30);
			int ty = gy + 30 - lines.size() * 5;
			Draw.fill(g, r.x() + 7, ty - 4, r.right() - 7, ty + lines.size() * 10 + 3, Draw.alpha(0xD00A080E, a));
			for (int i = 0; i < lines.size(); i++) {
				g.drawString(this.font, lines.get(i), r.cx() - this.font.width(lines.get(i)) / 2, ty + i * 10, Draw.alpha(Theme.TEXT_DIM, a), false);
			}
		} else if (this.selected == null) {
			this.selected = this.views.get(0);
		}

		// detalhes da receita escolhida
		if (this.selected != null) drawDetail(g, r, this.selected, a, mouseX, mouseY, canHover, now);

		// linha de baixo: aviso ou contagem
		Component line;
		int color = Theme.TEXT_DIM;
		if (this.status != null && System.nanoTime() - this.statusNanos < STATUS_NANOS) {
			line = this.status;
			color = Theme.TEXT_GOLD;
		} else {
			int others = this.views.size() - this.learnedCount;
			line = others > 0 ? Component.translatable("isekai_ui.recipes.count_untested", this.learnedCount, others)
					: Component.translatable(this.learnedCount == 1 ? "isekai_ui.recipes.count_one" : "isekai_ui.recipes.count", this.learnedCount);
		}
		Draw.textFit(g, this.font, line, r.cx(), r.bottom() - 14, r.w() - 14, Draw.alpha(color, a), false);
	}

	private static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
		Draw.fill(g, x, y, x + w, y + 1, color);
		Draw.fill(g, x, y + h - 1, x + w, y + h, color);
		Draw.fill(g, x, y, x + 1, y + h, color);
		Draw.fill(g, x + w - 1, y, x + w, y + h, color);
	}

	/** Um slot de ingrediente no detalhe (com dica ao passar o mouse). */
	private void ingredient(GuiGraphics g, int x, int y, ItemStack s, float a, int mouseX, int mouseY, boolean canHover) {
		Draw.sprite(g, Theme.SLOT, x - 1, y - 1, 18, 18, a);
		if (!s.isEmpty() && a > 0.5f) g.renderItem(s, x, y);
		if (canHover && !s.isEmpty() && mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) this.hoveredItem = s;
	}

	private void drawDetail(GuiGraphics g, Rect r, RecipeView v, float a, int mouseX, int mouseY, boolean canHover, long now) {
		Rect d = detail(r);
		Draw.textFit(g, this.font, v.result().getHoverName(), d.cx(), d.y() + 1, d.w(), Draw.alpha(Theme.TEXT_GOLD, a), false);
		int top = d.y() + 13;
		int resultX;
		int resultY;
		switch (v.station()) {
			case CRAFTING -> {
				int size = 3;
				int x0 = d.x() + 1;
				for (int row = 0; row < size; row++) {
					for (int col = 0; col < size; col++) {
						ItemStack s = row < v.h() && col < v.w() ? v.shown(row * v.w() + col, now) : ItemStack.EMPTY;
						ingredient(g, x0 + col * 17, top + row * 17, s, a, mouseX, mouseY, canHover);
					}
				}
				resultX = x0 + 3 * 17 + 22;
				resultY = top + 17;
				Draw.sprite(g, Theme.CRAFT_ARROW, x0 + 3 * 17 + 5, resultY + 4, 12, 9, a);
			}
			case BREWING -> {
				int x0 = d.x() + 8;
				ingredient(g, x0 + 9, top, v.shown(0, now), a, mouseX, mouseY, canHover);
				ingredient(g, x0, top + 26, v.shown(1, now), a, mouseX, mouseY, canHover);
				Draw.sprite(g, Theme.icon("potion"), x0 + 9 + 2, top + 18, 12, 12, Draw.alpha(0xFF9A90A4, a));
				resultX = x0 + 50;
				resultY = top + 26;
				Draw.sprite(g, Theme.CRAFT_ARROW, x0 + 25, resultY + 4, 12, 9, a);
			}
			case SMITHING -> {
				int x0 = d.x() + 1;
				for (int i = 0; i < 3; i++) ingredient(g, x0 + i * 18, top + 17, v.shown(i, now), a, mouseX, mouseY, canHover);
				resultX = x0 + 3 * 18 + 20;
				resultY = top + 17;
				Draw.sprite(g, Theme.CRAFT_ARROW, x0 + 3 * 18 + 3, resultY + 4, 12, 9, a);
			}
			default -> {
				// fornalhas e cortador: uma entrada -> resultado
				int x0 = d.x() + 14;
				ingredient(g, x0, top + 17, v.shown(0, now), a, mouseX, mouseY, canHover);
				if (v.station().isFurnace()) {
					Draw.sprite(g, Theme.icon("flame"), x0 + 2, top + 36, 12, 12, Draw.alpha(0xFFE8873A, a));
				}
				resultX = x0 + 46;
				resultY = top + 17;
				Draw.sprite(g, Theme.CRAFT_ARROW, x0 + 23, resultY + 4, 12, 9, a);
			}
		}
		Draw.sprite(g, Theme.SLOT_RESULT, resultX - 5, resultY - 5, 26, 26, a);
		if (a > 0.5f) {
			g.renderItem(v.result(), resultX, resultY);
			g.renderItemDecorations(this.font, v.result(), resultX, resultY);
		}
		if (canHover && mouseX >= resultX - 4 && mouseX < resultX + 20 && mouseY >= resultY - 4 && mouseY < resultY + 20) this.hoveredItem = v.result();

		// botao colocar/testar + por que nao da
		RecipePlacer.Ready ready = RecipePlacer.check(v, this.host.menu());
		Rect p = placeRect(r);
		boolean enabled = ready == RecipePlacer.Ready.OK;
		this.hoverPlace = canHover && p.contains(mouseX, mouseY);
		Component label = Component.translatable(v.tested() ? "isekai_ui.recipes.place" : "isekai_ui.recipes.test");
		button(g, p, label, this.hoverPlace && enabled, enabled, a);
		Component hint = enabled ? (v.tested() ? stationName(v.station()) : Component.translatable("isekai_ui.recipes.untested_hint"))
				: RecipePlacer.reason(ready, v);
		Draw.textFit(g, this.font, hint, d.cx(), p.bottom() + 4, d.w(), Draw.alpha(enabled ? 0xFF8E8472 : 0xFFD7A15A, a), false);
	}

	/** Botao em forma de pilula (marfim/dourado), apagado quando nao da para usar. */
	private void button(GuiGraphics g, Rect b, Component text, boolean hover, boolean enabled, float a) {
		int bg = !enabled ? 0x900A080E : (hover ? 0xF0473A63 : 0xE0322943);
		Draw.fill(g, b.x() + 1, b.y(), b.right() - 1, b.bottom(), Draw.alpha(bg, a));
		int edge = Draw.alpha(!enabled ? 0xFF3A3346 : (hover ? Theme.GOLD : 0xFFB89552), a);
		Draw.fill(g, b.x() + 1, b.y(), b.right() - 1, b.y() + 1, edge);
		Draw.fill(g, b.x() + 1, b.bottom() - 1, b.right() - 1, b.bottom(), edge);
		Draw.fill(g, b.x(), b.y() + 1, b.x() + 1, b.bottom() - 1, edge);
		Draw.fill(g, b.right() - 1, b.y() + 1, b.right(), b.bottom() - 1, edge);
		int color = !enabled ? 0xFF5E5868 : (hover ? Theme.TEXT_GOLD : Theme.TEXT);
		Draw.textFit(g, this.font, text, b.cx(), b.y() + (b.h() - 8) / 2, b.w() - 8, Draw.alpha(color, a), false);
	}

	private static Component stationName(Station st) {
		return new ItemStack(st.icon).getHoverName();
	}

	// ------------------------------------------------------------------ entrada

	private void setStatus(Component c) {
		this.status = c;
		this.statusNanos = System.nanoTime();
	}

	@Override
	public boolean click(Rect r, double mouseX, double mouseY, int button) {
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			if (tabRect(r, i).contains(mouseX, mouseY)) {
				setTab(tabs[i]);
				UiSounds.click();
				return true;
			}
		}
		if (untestedRect(r).contains(mouseX, mouseY)) {
			IsekaiConfig.RecipeSettings cfg = IsekaiConfig.get().recipes;
			cfg.showUntested = !cfg.showUntested;
			IsekaiConfig.save();
			this.scroll = 0;
			UiSounds.toggle();
			return true;
		}
		if (searchRect(r).contains(mouseX, mouseY)) {
			this.host.focusInput(this.search);
			this.search.setFocused(true);
			if (button == 1) this.search.setValue("");
			return true;
		}
		if (this.search.isFocused()) {
			this.search.setFocused(false);
			this.host.blurInput();
		}
		int gx = gridX(r) - 1;
		int gy = gridY(r) - 1;
		if (mouseX >= gx && mouseY >= gy && mouseX < gx + this.cols * CELL && mouseY < gy + ROWS * CELL) {
			int idx = (this.scroll + (int) ((mouseY - gy) / CELL)) * this.cols + (int) ((mouseX - gx) / CELL);
			if (idx >= 0 && idx < this.views.size()) {
				this.selected = this.views.get(idx);
				UiSounds.click();
				// shift + clique: ja coloca os ingredientes
				if (Screen.hasShiftDown() && RecipePlacer.check(this.selected, this.host.menu()) == RecipePlacer.Ready.OK) place(false);
			}
			return true;
		}
		if (this.selected != null && placeRect(r).contains(mouseX, mouseY)) {
			if (RecipePlacer.check(this.selected, this.host.menu()) == RecipePlacer.Ready.OK) {
				UiSounds.click();
				place(Screen.hasShiftDown());
			}
			return true;
		}
		return true;
	}

	private void place(boolean max) {
		if (this.selected == null) return;
		setStatus(RecipePlacer.place(this.selected, this.host.menu(), max));
	}

	@Override
	public boolean scroll(Rect r, double mouseX, double mouseY, double amount) {
		this.scroll = Mth.clamp(this.scroll - (int) Math.signum(amount), 0, maxScroll());
		return true;
	}

	@Override
	public boolean isTyping() {
		return this.search.isFocused();
	}

	@Override
	public void clickedOutside() {
		if (!this.search.isFocused()) return;
		this.search.setFocused(false);
		this.host.blurInput();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			this.search.setFocused(false);
			this.host.blurInput();
			return true;
		}
		return this.search.keyPressed(keyCode, scanCode, modifiers) || this.search.canConsumeInput();
	}

	@Override
	public boolean renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		if (!this.hoveredItem.isEmpty()) {
			List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, this.hoveredItem));
			if (this.hoveredView != null && !this.hoveredView.tested()) {
				lines.add(Component.translatable("isekai_ui.recipes.untested_tip").withStyle(ChatFormatting.GOLD));
			}
			g.renderTooltip(this.font, lines, this.hoveredItem.getTooltipImage(), mouseX, mouseY);
			return true;
		}
		if (this.hoveredTab >= 0) {
			g.renderTooltip(this.font, Tab.values()[this.hoveredTab].title(), mouseX, mouseY);
			return true;
		}
		if (this.hoverUntested) {
			g.renderComponentTooltip(this.font, List.of(Component.translatable("isekai_ui.recipes.untested_toggle"),
					Component.translatable("isekai_ui.recipes.untested_toggle_tip").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
			return true;
		}
		if (this.hoverPlace && this.selected != null) {
			g.renderTooltip(this.font, Component.translatable(this.selected.tested() ? "isekai_ui.recipes.place_tip" : "isekai_ui.recipes.test_tip"), mouseX, mouseY);
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ testes automaticos

	public int debugViewCount() {
		rebuildIfNeeded();
		return this.views.size();
	}

	public Component debugPlace(int index) {
		rebuildIfNeeded();
		if (index < 0 || index >= this.views.size()) return Component.empty();
		this.selected = this.views.get(index);
		Component c = RecipePlacer.place(this.selected, this.host.menu(), false);
		setStatus(c);
		return c;
	}

	public void debugSearch(String text) {
		this.search.setValue(text);
	}
}
