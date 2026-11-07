package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.UiSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * Base das "estacoes" (baus, mesa de trabalho, fornalha, bigorna...): uma janela da estacao e a janela
 * do inventario do jogador (27 + barra hexagonal). Os slots sao os do menu original, so mudam de lugar.
 * Em todo menu vanilla os 36 ultimos slots sao o inventario do jogador.
 */
public abstract class IsekaiStationScreen<T extends AbstractContainerMenu> extends IsekaiMenuScreen<T> {
	protected static final int SLOT = InventoryLayout.SLOT;
	protected static final int BAG_W = 9 * SLOT + InventoryLayout.PAD * 2;
	protected static final int BAG_H = 8 + 3 * SLOT + 9 + 30 + 7;
	private static final int SIDE_BUTTONS_W = InventoryLayout.HEX_W + 4;
	private static final int GRIMOIRE_GAP = 8;
	private static boolean vanillaBypass;
	/** Grimorio aberto nas estacoes (lembrado entre uma estacao e outra). */
	private static boolean grimoireOpen;

	protected final Screen vanilla;
	private final String key;
	protected Panel station;
	protected Panel bag;
	protected Panel grimoirePanel;
	private RecipeBookPanel grimoire;
	private float grimoireAppearAt;

	protected IsekaiStationScreen(T menu, Inventory inventory, Component title, Screen vanilla, String key) {
		super(menu, inventory, title);
		this.vanilla = vanilla;
		this.key = key;
	}

	/** True uma vez depois que a tela isekai pediu para abrir a vanilla (o mixin nao troca de novo). */
	public static boolean consumeVanillaBypass() {
		boolean b = vanillaBypass;
		vanillaBypass = false;
		return b;
	}

	// ------------------------------------------------------------------ o que cada estacao define

	protected abstract int stationW();

	protected abstract int stationH();

	/** Quantos slots do menu sao da estacao (os outros 36 sao o inventario do jogador). */
	protected abstract int stationSlots();

	/** Posiciona os slots da estacao dentro de r (retangulo atual da janela). */
	protected abstract void placeStation(Rect r);

	protected abstract void drawStation(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick);

	/** Clique numa parte da estacao que nao e slot (botoes desenhados, listas...). */
	protected boolean clickStation(Rect r, double mouseX, double mouseY, int button) {
		return false;
	}

	/** Dica (varias linhas) para o mouse em cima de algo da estacao; null = nada. */
	protected List<Component> stationTooltip(Rect r, int mouseX, int mouseY) {
		return null;
	}

	/** Botoes hexagonais ao lado da estacao (livro de receitas, ir para o craft...). */
	protected void addStationButtons() {
	}

	protected Component stationTitle() {
		return this.title;
	}

	/** Aba do grimorio de receitas desta estacao (null = estacao sem grimorio). */
	protected RecipeBookPanel.Tab grimoireTab() {
		return null;
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected String layoutKey() {
		return "station." + this.key;
	}

	@Override
	protected boolean screenFits() {
		return this.menu.slots.size() >= stationSlots() + 36 && layout(this.width, this.height, 0) != null;
	}

	/**
	 * Estacao em cima e inventario embaixo (ou lado a lado em telas baixas); null se nao couber.
	 * "reserve" = espaco livre a esquerda (grimorio aberto ao lado, como o livro de receitas do jogo).
	 */
	private Rect[] layout(int W, int H, int reserve) {
		int sw = stationW();
		int sh = stationH();
		int tab = InventoryLayout.TAB;
		int colW = Math.max(sw, BAG_W);
		int stacked = tab + sh + 10 + tab + BAG_H;
		if (stacked <= H - 10 && reserve + colW + SIDE_BUTTONS_W + 12 <= W) {
			int x = reserve > 0 ? Math.max(6 + reserve, (W - (reserve + colW + SIDE_BUTTONS_W)) / 2 + reserve)
					: InventoryLayout.mainColumnX(W, colW, colW + SIDE_BUTTONS_W);
			int top = (H - stacked) / 2 + tab;
			Rect s = new Rect(x + (colW - sw) / 2, top, sw, sh);
			Rect b = new Rect(x + (colW - BAG_W) / 2, top + sh + 10 + tab, BAG_W, BAG_H);
			return new Rect[]{s, b};
		}
		int pairW = sw + SIDE_BUTTONS_W + 8 + BAG_W;
		if (reserve + pairW + 12 <= W && Math.max(sh, BAG_H) + tab + 8 <= H) {
			int x = reserve > 0 ? Math.max(6 + reserve, (W - (reserve + pairW)) / 2 + reserve)
					: Mth.clamp(InventoryLayout.mainColumnX(W, pairW, pairW), 6, W - 6 - pairW);
			Rect s = new Rect(x, Math.max(tab + 4, (H - sh) / 2), sw, sh);
			Rect b = new Rect(x + sw + SIDE_BUTTONS_W + 8, Math.max(tab + 4, (H - BAG_H) / 2), BAG_W, BAG_H);
			return new Rect[]{s, b};
		}
		return null;
	}

	@Override
	protected void openVanillaFallback() {
		vanillaBypass = true;
		this.minecraft.setScreen(this.vanilla);
	}

	@Override
	protected void createPanels() {
		RecipeBookPanel.Tab tab = grimoireTab();
		boolean book = tab != null && grimoireOpen;
		// grimorio ao lado da estacao (como o livro do jogo); em telas menores ele perde colunas para caber
		Rect[] l = null;
		int cols = RecipeBookPanel.MAX_COLS;
		for (int c = RecipeBookPanel.MAX_COLS; book && l == null && c >= RecipeBookPanel.MIN_COLS; c--) {
			l = layout(this.width, this.height, RecipeBookPanel.width(c) + GRIMOIRE_GAP);
			cols = c;
		}
		boolean beside = l != null;
		if (l == null) {
			cols = RecipeBookPanel.MAX_COLS;
			l = layout(this.width, this.height, 0);
		}
		if (l == null) return;
		this.station = addPanel("station", 0f, 0, -20, true, stationTitle(), l[0]);
		this.bag = addPanel("bag", 0.08f, 0, 22, true, Component.translatable("isekai_ui.panel.inventory"), l[1]);
		this.grimoirePanel = null;
		if (tab != null) {
			addSideButton(this.station, "book", Component.translatable("isekai_ui.button.grimoire"), this::toggleGrimoire, () -> grimoireOpen);
			if (book) createGrimoire(tab, l, beside, cols);
		}
		addStationButtons();
	}

	/** Grimorio ao lado da estacao quando cabe; senao por cima da bolsa (os ingredientes sao colocados sozinhos). */
	private void createGrimoire(RecipeBookPanel.Tab tab, Rect[] l, boolean beside, int cols) {
		if (this.grimoire == null) this.grimoire = new RecipeBookPanel(this.font, new RecipeBookPanel.Host() {
			@Override
			public AbstractContainerMenu menu() {
				return IsekaiStationScreen.this.menu;
			}

			@Override
			public void addInput(EditBox box) {
				addWidget(box);
			}

			@Override
			public void focusInput(EditBox box) {
				setFocused(box);
			}

			@Override
			public void blurInput() {
				setFocused(null);
			}
		}, tab);
		this.grimoire.attach();
		this.grimoire.setColumns(cols);
		int gw = this.grimoire.width();
		int gh = RecipeBookPanel.H;
		Rect r;
		if (beside) {
			int left = Math.min(l[0].x(), l[1].x());
			r = new Rect(left - GRIMOIRE_GAP - gw, l[0].y(), gw, gh);
		} else {
			// tela pequena demais: por cima da bolsa, sem cobrir a estacao quando der
			Rect b = l[1];
			int top = Math.max(InventoryLayout.TAB + 1, l[0].bottom() + 4 + InventoryLayout.TAB);
			r = new Rect(Mth.clamp(b.cx() - gw / 2, 4, Math.max(4, this.width - gw - 4)),
					Mth.clamp(b.y(), InventoryLayout.TAB + 1, Math.max(InventoryLayout.TAB + 1, Math.min(this.height - gh - 1, Math.max(top, b.y())))), gw, gh);
		}
		this.grimoirePanel = addPanel("grimoire", this.grimoireAppearAt, beside ? -20 : 0, beside ? 0 : 10, true,
				Component.translatable("isekai_ui.panel.grimoire"), r);
		this.grimoirePanel.overlay = !beside;
		this.grimoirePanel.content = this.grimoire;
	}

	private void toggleGrimoire() {
		grimoireOpen = !grimoireOpen;
		this.grimoireAppearAt = time();
		UiSounds.toggle();
		rebuildWidgets();
	}

	/** Usado pelos testes automaticos: abre o grimorio (se preciso) e devolve a janela dele. */
	public RecipeBookPanel debugGrimoire() {
		if (grimoireTab() == null) return null;
		if (!grimoireOpen) toggleGrimoire();
		return this.grimoire;
	}

	@Override
	protected void placeSlots() {
		if (this.station == null) return;
		placeStation(this.station.now);
		int n = stationSlots();
		Rect b = this.bag.now;
		placeGrid(n, 27, 9, b.x() + InventoryLayout.PAD + 1, b.y() + 9);
		placeHexHotbar(n + 27, hotbarX(b), hotbarY(b));
	}

	private static int hotbarX(Rect b) {
		return b.x() + (BAG_W - (8 * 18 + InventoryLayout.HEX_W)) / 2;
	}

	private static int hotbarY(Rect b) {
		return b.y() + 8 + 3 * SLOT + 9;
	}

	@Override
	protected Panel slotOwner(int slotIndex) {
		return slotIndex < stationSlots() ? this.station : this.bag;
	}

	@Override
	protected void drawPanel(GuiGraphics g, Panel p, float partialTick, int mouseX, int mouseY) {
		if (p == this.station) {
			drawStation(g, p.now, p.alpha, mouseX, mouseY, partialTick);
		} else if (p == this.bag) {
			Rect b = p.now;
			drawGrid(g, b.x() + InventoryLayout.PAD + 1, b.y() + 9, 27, 9, p.alpha);
			Panels.ornament(g, b.x() + 24, hotbarY(b) - 7, b.w() - 48, p.alpha * 0.9f);
			drawHexHotbar(g, hotbarX(b), hotbarY(b), p.alpha);
		}
	}

	@Override
	protected boolean onPanelClick(Panel p, double mouseX, double mouseY, int button) {
		if (p == this.station) return clickStation(p.now, mouseX, mouseY, button);
		return false;
	}

	@Override
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (isClosing() || isDraggingPanel() || this.station == null || this.hoveredSlot != null) return;
		if (!this.station.grabArea().contains(mouseX, mouseY) || overlayAt(mouseX, mouseY) != null) return;
		List<Component> lines = stationTooltip(this.station.now, mouseX, mouseY);
		if (lines != null && !lines.isEmpty()) g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
	}

	// ------------------------------------------------------------------ ajudantes de desenho

	/** Slot de resultado (26x26) com brilho quando tem item. itemX/itemY = canto do item (16x16). */
	protected void drawResultSlot(GuiGraphics g, int itemX, int itemY, int slotIndex, float alpha) {
		Draw.sprite(g, Theme.SLOT_RESULT, itemX - 5, itemY - 5, 26, 26, alpha);
		if (slotIndex >= 0 && slotIndex < this.menu.slots.size() && !this.menu.slots.get(slotIndex).getItem().isEmpty()) {
			float glow = 0.45f + 0.35f * Mth.sin(time() * 4f);
			Draw.sprite(g, Theme.SLOT_RESULT_GLOW, itemX - 5, itemY - 5, 26, 26, alpha * glow);
		}
	}

	/** Fundo de um slot comum (18x18) para um item em itemX/itemY. */
	protected static void drawSlot(GuiGraphics g, int itemX, int itemY, float alpha) {
		Draw.sprite(g, Theme.SLOT, itemX - 1, itemY - 1, 18, 18, alpha);
	}

	/** Seta de progresso: fundo apagado e parte dourada ate "progress" (0..1). */
	protected static void drawProgressArrow(GuiGraphics g, int x, int y, float progress, float alpha) {
		Draw.sprite(g, Theme.PROGRESS_ARROW, x, y, 22, 15, alpha);
		if (progress > 0f) {
			int w = Math.max(1, Math.round(22 * Math.min(1f, progress)));
			g.enableScissor(x, y, x + w, y + 15);
			Draw.sprite(g, Theme.PROGRESS_ARROW_FULL, x, y, 22, 15, alpha);
			g.disableScissor();
		}
	}

	/** Icone que "enche" de baixo para cima (chama do combustivel, por exemplo). */
	protected static void drawFillingIcon(GuiGraphics g, String icon, int x, int y, int size, float fill, int dimColor, int fullColor, float alpha) {
		Draw.sprite(g, Theme.icon(icon), x, y, size, size, Draw.alpha(dimColor, alpha));
		if (fill > 0f) {
			int h = Math.max(1, Math.round(size * Math.min(1f, fill)));
			g.enableScissor(x, y + size - h, x + size, y + size);
			Draw.sprite(g, Theme.icon(icon), x, y, size, size, Draw.alpha(fullColor, alpha));
			g.disableScissor();
		}
	}

	/** Linha de informacao centralizada no pe da estacao; se nao couber, encolhe (e corta no ultimo caso). */
	protected void infoLine(GuiGraphics g, Component text, Rect r, int color, float alpha) {
		int max = r.w() - 12;
		int w = this.font.width(text);
		int y = r.bottom() - 15;
		if (w <= max) {
			smallText(g, text, r.cx() - w / 2, y, color, alpha);
			return;
		}
		float scale = Math.max(0.6f, (float) max / w);
		Draw.push(g);
		Draw.translate(g, r.cx(), y + 1);
		Draw.scale(g, scale);
		net.minecraft.util.FormattedCharSequence line = text.getVisualOrderText();
		if (w * scale > max) line = net.minecraft.locale.Language.getInstance().getVisualOrder(this.font.substrByWidth(text, (int) (max / scale)));
		int lw = this.font.width(line);
		g.drawString(this.font, line, -lw / 2, 0, Draw.alpha(color, alpha), false);
		Draw.pop(g);
	}

	protected void smallText(GuiGraphics g, Component text, int x, int y, int color, float alpha) {
		Draw.text(g, this.font, text, x, y, Draw.alpha(color, alpha), false);
	}

	protected static String clock(int ticks) {
		int s = Math.max(0, (ticks + 19) / 20);
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}
}
