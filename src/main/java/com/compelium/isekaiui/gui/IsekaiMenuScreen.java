package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.anim.Ease;
import com.compelium.isekaiui.camera.PoseState;
import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.mixin.SlotAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Base das telas isekai com slots (inventario, baus...). Cuida das janelas (paineis), das animacoes,
 * de arrastar as janelas pelo titulo/bordas e de lembrar onde o jogador as deixou.
 * Os slots sao os do menu original: so a posicao na tela muda.
 */
public abstract class IsekaiMenuScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
	/** So para testes automaticos: esconde as janelas para fotografar o personagem. */
	public static boolean debugHidePanels;
	protected static final float GROUP_SECONDS = 0.34f;
	protected static final float CLOSE_SECONDS = 0.18f;
	private static final double DRAG_THRESHOLD = 3.0;
	/** Fundo quase opaco atras de janelas que ficam por cima de outras (o vidro normal e translucido). */
	public static final int OVERLAY_BACKDROP = 0xEE16131D;
	private static final float TOP_LAYER = 320f;
	/** Cada janela por cima fica numa camada propria (os itens de uma nao vazam na outra). */
	private static final float OVERLAY_STEP = 300f;
	/** Dicas (tooltips) ficam acima de todas as janelas e dos itens delas. */
	private static final float TIP_LAYER = 1700f;

	/** Botao hexagonal preso no lado direito de uma janela (anda junto quando ela e arrastada). */
	public record SideButton(Panel panel, String icon, Component tooltip, Runnable action, BooleanSupplier on) {
	}

	protected final List<SideButton> sideButtons = new ArrayList<>();
	private SideButton hoveredSide;

	/** Conteudo de uma janela que se desenha e trata cliques sozinho (ex.: grimorio de receitas). */
	public interface PanelContent {
		void draw(GuiGraphics g, Rect r, float alpha, int mouseX, int mouseY, float partialTick, boolean canHover);

		default boolean click(Rect r, double mouseX, double mouseY, int button) {
			return false;
		}

		default boolean scroll(Rect r, double mouseX, double mouseY, double amount) {
			return false;
		}

		default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			return false;
		}

		default boolean isTyping() {
			return false;
		}

		/** O jogador clicou fora desta janela (ex.: tirar o foco da busca). */
		default void clickedOutside() {
		}

		/** Desenha a dica do que esta embaixo do mouse (por cima de tudo). Devolve true se desenhou. */
		default boolean renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
			return false;
		}
	}

	/** Avisinho no alto da tela (ex.: "Receita anotada no grimorio"). */
	private static Component notice;
	private static long noticeNanos;
	/** Dica pedida neste quadro (desenhada no fim, por cima de tudo). */
	private Component pendingTip;

	/** Uma janela da tela. */
	public static final class Panel {
		public final String id;
		public final float delay;
		public final int slideX;
		public final int slideY;
		/** Janela comum (vidro + moldura). Se false, a subclasse desenha tudo. */
		public final boolean framed;
		public Component title;
		public Rect def = new Rect(0, 0, 10, 10);
		public Rect rect = def;
		public Rect now = def;
		public float progress;
		public float alpha;
		public boolean visible = true;
		/** Desenhada por cima dos itens (janelas tipo popup). */
		public boolean overlay;
		/** Espaco extra embaixo que tambem pertence a janela (ex.: coordenadas sob o minimapa). */
		public int padBottom;
		/** false: a subclasse desenha a(s) aba(s) do titulo (ex.: abas "Catalogo" e "Bau pessoal"). */
		public boolean drawTitle = true;
		/** Se tiver, a janela usa este conteudo em vez de drawPanel/onPanelClick. */
		public PanelContent content;
		/** Janela presa a outra (ex.: gaveta da barra): nao pode ser arrastada. */
		public boolean fixed;

		public Panel(String id, float delay, int slideX, int slideY, boolean framed) {
			this.id = id;
			this.delay = delay;
			this.slideX = slideX;
			this.slideY = slideY;
			this.framed = framed;
		}

		/** Area que pode ser agarrada (janela + aba de titulo). */
		public Rect grabArea() {
			int tab = this.title != null ? InventoryLayout.TAB : 0;
			return new Rect(this.now.x(), this.now.y() - tab, this.now.w(), this.now.h() + tab);
		}

		public int dx() {
			return this.now.x() - this.def.x();
		}

		public int dy() {
			return this.now.y() - this.def.y();
		}
	}

	protected final Player owner;
	protected final List<Panel> panels = new ArrayList<>();
	private final Map<Slot, int[]> originalPositions = new HashMap<>();

	private long openNanos;
	protected long closeNanos = -1L;
	private float closeDuration = CLOSE_SECONDS;
	private Panel keepWhileClosing;
	private Runnable afterClose;
	private boolean restored;

	private Panel pressPanel;
	private double pressX;
	private double pressY;
	private int pressPanelX;
	private int pressPanelY;
	private boolean dragging;
	private Panel lastClickPanel;
	private long lastClickNanos;

	protected IsekaiMenuScreen(T menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.owner = inventory.player;
		for (Slot s : menu.slots) this.originalPositions.put(s, new int[]{s.x, s.y});
	}

	// ------------------------------------------------------------------ o que cada tela define

	/** Nome usado para salvar o layout (ex.: "inventory", "container"). */
	protected abstract String layoutKey();

	/** Cria as janelas com a posicao padrao (chamado a cada init/redimensionamento). */
	protected abstract void createPanels();

	/** Posiciona os slots usando panel.now (posicao animada). */
	protected abstract void placeSlots();

	/** Conteudo de cada janela (molduras de slot, mapa, botoes...). */
	protected abstract void drawPanel(GuiGraphics g, Panel p, float partialTick, int mouseX, int mouseY);

	/** Clique (sem arrastar) numa area da janela que nao e slot. */
	protected boolean onPanelClick(Panel p, double mouseX, double mouseY, int button) {
		return false;
	}

	/** Mostra um avisinho no alto da tela por alguns segundos (em qualquer tela isekai aberta). */
	public static void notice(Component text) {
		notice = text;
		noticeNanos = System.nanoTime();
	}

	/** Conteudo de janela que esta digitando agora (ele recebe as teclas antes de tudo). */
	protected PanelContent typingContent() {
		for (Panel p : this.panels) {
			if (p.content != null && p.visible && p.content.isTyping()) return p.content;
		}
		return null;
	}

	private boolean panelClick(Panel p, double mouseX, double mouseY, int button) {
		if (p.content != null) {
			p.content.click(p.now, mouseX, mouseY, button);
			return true;
		}
		return onPanelClick(p, mouseX, mouseY, button);
	}

	/** Ponto da faixa do titulo que e um botao (ex.: aba de modo): clique duplo ali nao reseta a janela. */
	protected boolean isTitleButton(Panel p, double mouseX, double mouseY) {
		return false;
	}

	protected void onOpened() {
		UiSounds.windowOpen();
	}

	protected boolean screenFits() {
		return InventoryLayout.fits(this.width, this.height);
	}

	/** Chamado quando a tela nao cabe: a subclasse abre a versao vanilla. */
	protected abstract void openVanillaFallback();

	public boolean wantsCinematicCamera() {
		return IsekaiConfig.get().thirdPersonCamera && this.closeNanos < 0L;
	}

	// ------------------------------------------------------------------ ciclo de vida

	@Override
	protected void init() {
		if (!screenFits()) {
			openVanillaFallback();
			return;
		}
		this.imageWidth = this.width;
		this.imageHeight = this.height;
		super.init();
		this.leftPos = 0;
		this.topPos = 0;
		if (this.openNanos == 0L) {
			this.openNanos = System.nanoTime();
			onOpened();
		}
		this.panels.clear();
		this.sideButtons.clear();
		createPanels();
		loadLayout();
		updatePanels();
		placeSlots();
		this.restored = false;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (this.closeNanos >= 0L && secondsSince(this.closeNanos) > this.closeDuration + 0.25f) finishClose();
	}

	@Override
	public void removed() {
		restoreSlots();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (this.closeNanos >= 0L) return;
		if (!IsekaiConfig.get().animations) {
			super.onClose();
			return;
		}
		startClosing(CLOSE_SECONDS, null, null);
		UiSounds.windowClose();
	}

	/** Comeca a animacao de saida. "keep" fica parada (ex.: minimapa virando o mapa grande). */
	protected void startClosing(float seconds, Panel keep, Runnable after) {
		this.closeNanos = System.nanoTime();
		this.closeDuration = seconds;
		this.keepWhileClosing = keep;
		this.afterClose = after;
		this.pressPanel = null;
		this.dragging = false;
		if (!IsekaiConfig.get().animations) finishClose();
	}

	private void finishClose() {
		Runnable after = this.afterClose;
		this.afterClose = null;
		if (this.minecraft == null || this.minecraft.screen != this) return;
		if (after != null) after.run();
		else super.onClose();
	}

	protected boolean isClosing() {
		return this.closeNanos >= 0L;
	}

	protected float time() {
		return secondsSince(this.openNanos);
	}

	protected static float secondsSince(long nanos) {
		return (System.nanoTime() - nanos) / 1.0e9f;
	}

	// ------------------------------------------------------------------ botoes laterais

	protected SideButton addSideButton(Panel panel, String icon, Component tooltip, Runnable action, BooleanSupplier on) {
		SideButton b = new SideButton(panel, icon, tooltip, action, on);
		this.sideButtons.add(b);
		return b;
	}

	protected Rect sideButtonRect(SideButton b) {
		int i = 0;
		for (SideButton o : this.sideButtons) {
			if (o == b) break;
			if (o.panel() == b.panel()) i++;
		}
		Rect r = b.panel().now;
		return new Rect(r.right() + 2, r.y() + 1 + i * (InventoryLayout.HEX_H + 3), InventoryLayout.HEX_W, InventoryLayout.HEX_H);
	}

	protected SideButton sideButtonAt(double mx, double my) {
		for (SideButton b : this.sideButtons) {
			Panel p = b.panel();
			if (!p.visible || p.alpha < 0.5f) continue;
			if (sideButtonRect(b).contains(mx, my)) return b;
		}
		return null;
	}

	private void drawSideButtons(GuiGraphics g, Panel p) {
		for (SideButton b : this.sideButtons) {
			if (b.panel() != p) continue;
			Panels.hexButton(g, sideButtonRect(b), b.icon(), b == this.hoveredSide, b.on().getAsBoolean(), p.alpha);
		}
	}

		// ------------------------------------------------------------------ layout salvo

	protected Panel addPanel(String id, float delay, int slideX, int slideY, boolean framed, Component title, Rect def) {
		Panel p = new Panel(id, delay, slideX, slideY, framed);
		p.title = title;
		p.def = def;
		p.rect = def;
		p.now = def;
		this.panels.add(p);
		return p;
	}

	protected Panel panel(String id) {
		for (Panel p : this.panels) if (p.id.equals(id)) return p;
		return null;
	}

	/** Usado pelos testes automaticos: botao lateral pelo nome do icone. */
	public Rect debugSideButtonRect(String icon) {
		for (SideButton b : this.sideButtons) if (b.icon().equals(icon)) return sideButtonRect(b);
		return null;
	}

	/** Usado pelos testes automaticos: posicao atual de uma janela. */
	public Rect debugPanelRect(String id) {
		Panel p = panel(id);
		return p == null ? null : p.now;
	}

	/** Usado pelos testes automaticos: a janela esta aberta? */
	public boolean debugPanelVisible(String id) {
		Panel p = panel(id);
		return p != null && p.visible;
	}

	private void loadLayout() {
		Map<String, float[]> saved = IsekaiConfig.get().layouts.get(layoutKey());
		List<Panel> moved = new ArrayList<>();
		for (Panel p : this.panels) {
			float[] f = saved == null ? null : saved.get(p.id);
			if (f != null && f.length == 2) {
				int x = Math.round(f[0] * Math.max(0, this.width - p.def.w()));
				int y = Math.round(f[1] * Math.max(0, this.height - p.def.h()));
				p.rect = clampToScreen(p, new Rect(x, y, p.def.w(), p.def.h()));
				moved.add(p);
			} else {
				p.rect = clampToScreen(p, p.def);
			}
		}
		// numa tela de outro tamanho as janelas salvas podem se encostar: empurra para o lugar livre mais proximo
		for (Panel p : moved) p.rect = resolveOverlap(p, p.rect, p.rect);
	}

	// ------------------------------------------------------------------ janelas nao se sobrepoem

	private static Rect area(Panel p, Rect r) {
		int top = p.title != null ? InventoryLayout.TAB : 0;
		return new Rect(r.x() - 2, r.y() - top - 2, r.w() + 4, r.h() + top + p.padBottom + 4);
	}

	private static boolean intersects(Rect a, Rect b) {
		return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
	}

	private boolean overlapsOthers(Panel moving, Rect r) {
		Rect a = area(moving, r);
		for (Panel o : this.panels) {
			if (o == moving || o.overlay || !o.visible) continue;
			if (intersects(a, area(o, o.rect))) return true;
		}
		return false;
	}

	/** Se a janela ficou em cima de outra, procura o lugar livre mais perto (em aneis cada vez maiores). */
	private Rect resolveOverlap(Panel p, Rect r, Rect fallback) {
		if (p.overlay || !overlapsOthers(p, r)) return r;
		int maxRadius = Math.max(this.width, this.height);
		for (int radius = 4; radius <= maxRadius; radius += 4) {
			Rect best = null;
			long bestD = Long.MAX_VALUE;
			for (int i = -radius; i <= radius; i += 4) {
				Rect[] candidates = {r.offset(i, -radius), r.offset(i, radius), r.offset(-radius, i), r.offset(radius, i)};
				for (Rect c0 : candidates) {
					Rect c = clampToScreen(p, c0);
					if (overlapsOthers(p, c)) continue;
					long dx = c.x() - r.x();
					long dy = c.y() - r.y();
					long d = dx * dx + dy * dy;
					if (d < bestD) {
						bestD = d;
						best = c;
					}
				}
			}
			if (best != null) return best;
		}
		return fallback;
	}

	private void saveLayout() {
		Map<String, float[]> saved = new HashMap<>();
		for (Panel p : this.panels) {
			if (p.rect.x() == p.def.x() && p.rect.y() == p.def.y()) continue;
			float fx = this.width - p.rect.w() <= 0 ? 0f : (float) p.rect.x() / (this.width - p.rect.w());
			float fy = this.height - p.rect.h() <= 0 ? 0f : (float) p.rect.y() / (this.height - p.rect.h());
			saved.put(p.id, new float[]{Mth.clamp(fx, 0f, 1f), Mth.clamp(fy, 0f, 1f)});
		}
		if (saved.isEmpty()) IsekaiConfig.get().layouts.remove(layoutKey());
		else IsekaiConfig.get().layouts.put(layoutKey(), saved);
		IsekaiConfig.save();
	}

	private Rect clampToScreen(Panel p, Rect r) {
		int top = p.title != null ? InventoryLayout.TAB + 1 : 1;
		int x = Mth.clamp(r.x(), 1, Math.max(1, this.width - r.w() - 1));
		int y = Mth.clamp(r.y(), top, Math.max(top, this.height - r.h() - 1));
		return new Rect(x, y, r.w(), r.h());
	}

	// ------------------------------------------------------------------ animacao

	private float progress(Panel p) {
		if (!IsekaiConfig.get().animations) return this.closeNanos >= 0L && p != this.keepWhileClosing ? 0f : 1f;
		float v = Ease.window(time(), p.delay, GROUP_SECONDS);
		if (this.closeNanos >= 0L && p != this.keepWhileClosing) {
			v = Math.min(v, 1f - Ease.window(secondsSince(this.closeNanos), 0f, this.closeDuration));
		}
		return v;
	}

	protected static int slide(float p, int distance) {
		return Math.round((1f - Ease.outBack(p)) * distance);
	}

	protected static float fade(float p) {
		return Ease.outCubic(Math.min(1f, p * 1.7f));
	}

	private void updatePanels() {
		for (Panel p : this.panels) {
			p.progress = progress(p);
			p.alpha = p.visible ? fade(p.progress) : 0f;
			p.now = p.rect.offset(slide(p.progress, p.slideX), slide(p.progress, p.slideY));
		}
	}

	// ------------------------------------------------------------------ slots

	protected void setSlot(int index, int x, int y) {
		if (index < 0 || index >= this.menu.slots.size()) return;
		Slot s = this.menu.slots.get(index);
		((SlotAccessor) s).isekai$setX(x);
		((SlotAccessor) s).isekai$setY(y);
	}

	/** Esconde um slot fora da tela (ex.: janela fechada). */
	protected void hideSlot(int index) {
		setSlot(index, -10000, -10000);
	}

	private void restoreSlots() {
		if (this.restored) return;
		for (Map.Entry<Slot, int[]> e : this.originalPositions.entrySet()) {
			((SlotAccessor) e.getKey()).isekai$setX(e.getValue()[0]);
			((SlotAccessor) e.getKey()).isekai$setY(e.getValue()[1]);
		}
		this.restored = true;
	}

	/** Slots de uma grade (colunas x linhas) a partir do canto do primeiro item. */
	protected void placeGrid(int firstSlot, int count, int cols, int itemX, int itemY) {
		for (int i = 0; i < count; i++) {
			setSlot(firstSlot + i, itemX + (i % cols) * InventoryLayout.SLOT, itemY + (i / cols) * InventoryLayout.SLOT);
		}
	}

	/** Barra de atalhos em hexagonos alternados (cima/baixo). */
	protected void placeHexHotbar(int firstSlot, int hexX, int hexY) {
		for (int k = 0; k < 9; k++) {
			setSlot(firstSlot + k, hexX + k * 18 + 4, hexY + ((k & 1) == 1 ? 10 : 0) + 2);
		}
	}

	protected static void drawGrid(GuiGraphics g, int itemX, int itemY, int count, int cols, float alpha) {
		for (int i = 0; i < count; i++) {
			Draw.sprite(g, Theme.SLOT, itemX - 1 + (i % cols) * InventoryLayout.SLOT, itemY - 1 + (i / cols) * InventoryLayout.SLOT, 18, 18, alpha);
		}
	}

	protected void drawHexHotbar(GuiGraphics g, int hexX, int hexY, float alpha) {
		for (int k = 0; k < 9; k++) {
			Draw.sprite(g, Theme.SLOT_HEX, hexX + k * 18, hexY + ((k & 1) == 1 ? 10 : 0), InventoryLayout.HEX_W, InventoryLayout.HEX_H, alpha);
		}
		int selected = this.owner.getInventory().selected;
		if (selected >= 0 && selected < 9) {
			float pulse = 0.55f + 0.25f * Mth.sin(time() * 3.2f);
			Draw.sprite(g, Theme.SLOT_HEX_SELECTED, hexX + selected * 18, hexY + ((selected & 1) == 1 ? 10 : 0),
					InventoryLayout.HEX_W, InventoryLayout.HEX_H, alpha * pulse);
		}
	}

	// ------------------------------------------------------------------ desenho

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (this.panels.isEmpty() || debugHidePanels) return;
		if (this.closeNanos >= 0L && secondsSince(this.closeNanos) >= this.closeDuration) {
			finishClose();
			return;
		}
		updatePanels();
		placeSlots();
		SideButton hoverBefore = this.hoveredSide;
		this.hoveredSide = this.closeNanos < 0L && this.pressPanel == null ? sideButtonAt(mouseX, mouseY) : null;
		if (this.hoveredSide != null && this.hoveredSide != hoverBefore) UiSounds.hover();
		super.render(g, mouseX, mouseY, partialTick);
		float time = time();
		// a janela sendo arrastada fica por cima de todas (com os itens dela)
		Panel dragged = this.dragging ? this.pressPanel : null;
		if (dragged != null && !dragged.overlay) {
			Draw.push(g);
			Draw.layer(g, TOP_LAYER);
			drawPanelFull(g, dragged, partialTick, mouseX, mouseY, time, true);
			renderPanelItems(g, dragged);
			Draw.pop(g);
		}
		int layer = 0;
		for (Panel p : this.panels) {
			if (!p.overlay || !p.visible || p.alpha <= 0.01f) continue;
			Draw.push(g);
			Draw.layer(g, TOP_LAYER + layer * OVERLAY_STEP);
			drawPanelFull(g, p, partialTick, mouseX, mouseY, time, true);
			Draw.pop(g);
			layer++;
		}
		// dicas por cima de tudo (inclusive dos itens desenhados dentro das janelas de cima)
		Draw.push(g);
		Draw.layer(g, TIP_LAYER);
		renderOverlay(g, mouseX, mouseY, partialTick);
		Panel over = overlayAt(mouseX, mouseY);
		Panel under = over != null ? over : panelAt(mouseX, mouseY);
		boolean contentTip = false;
		if (under != null && under.content != null && this.closeNanos < 0L && this.pressPanel == null) {
			contentTip = under.content.renderTooltip(g, mouseX, mouseY);
		}
		if (this.hoveredSide != null) this.pendingTip = this.hoveredSide.tooltip();
		if (this.closeNanos < 0L && this.pressPanel == null && over == null && !contentTip) this.renderTooltip(g, mouseX, mouseY);
		if (this.pendingTip != null && !contentTip) g.renderTooltip(this.font, this.pendingTip, mouseX, mouseY);
		this.pendingTip = null;
		Draw.pop(g);
		drawNotice(g);
	}

	/** As dicas das telas isekai sao desenhadas no fim do quadro, acima das janelas (ver render). */
	@Override
	public void setTooltipForNextRenderPass(Component tooltip) {
		this.pendingTip = tooltip;
	}

	private void drawNotice(GuiGraphics g) {
		if (notice == null) return;
		float age = (System.nanoTime() - noticeNanos) / 1_000_000_000f;
		if (age > 3.5f) {
			notice = null;
			return;
		}
		float a = Math.min(Math.min(1f, age / 0.15f), Math.min(1f, (3.5f - age) / 0.5f));
		int w = Math.min(this.width - 20, this.font.width(notice) + 24);
		int x = (this.width - w) / 2;
		int y = 3;
		Draw.push(g);
		Draw.layer(g, TIP_LAYER + 600f);
		Draw.sprite(g, Theme.PILL, x, y, w, 14, a);
		Draw.sprite(g, Theme.icon("book"), x + 4, y + 1, 12, 12, Draw.alpha(Theme.GOLD, a));
		Draw.textFit(g, this.font, notice, x + w / 2 + 6, y + 3, w - 22, Draw.alpha(Theme.TEXT_GOLD, a), false);
		Draw.pop(g);
	}

	/** Coisas por cima de tudo (dicas). */
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// sem desfoque e sem escurecer: o mundo e o personagem ficam visiveis atras das janelas
		this.renderBg(g, partialTick, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		float time = time();
		for (Panel p : this.panels) {
			if (p.overlay || !p.visible || p.alpha <= 0.01f) continue;
			if (p == this.pressPanel && this.dragging) continue; // desenhada por cima, em render()
			drawPanelFull(g, p, partialTick, mouseX, mouseY, time, false);
		}
	}

	/** Moldura + conteudo de uma janela. "onTop": janela por cima de outras, ganha fundo opaco. */
	private void drawPanelFull(GuiGraphics g, Panel p, float partialTick, int mouseX, int mouseY, float time, boolean onTop) {
		if (p.framed) {
			if (onTop) {
				Rect n = p.now;
				Draw.fill(g, n.x() + 2, n.y() + 2, n.right() - 2, n.bottom() - 2, Draw.alpha(OVERLAY_BACKDROP, p.alpha));
			}
			Panels.panel(g, this.font, p.now, p.alpha, p.progress, p.drawTitle ? p.title : null, time);
		}
		if (p.content != null) {
			boolean canHover = this.closeNanos < 0L && !this.dragging
					&& (p.overlay ? overlayAt(mouseX, mouseY) == p : overlayAt(mouseX, mouseY) == null);
			p.content.draw(g, p.now, p.alpha, mouseX, mouseY, partialTick, canHover);
		} else {
			drawPanel(g, p, partialTick, mouseX, mouseY);
		}
		drawSideButtons(g, p);
		if (p == this.pressPanel && this.dragging) {
			Rect a = p.grabArea();
			Draw.sprite(g, Theme.MAP_FRAME_GLOW, a.x() - 3, a.y() - 3, a.w() + 6, a.h() + 6, 0.8f);
		}
	}

	/** Itens dos slots da janela (usado quando ela e desenhada por cima das outras). */
	private void renderPanelItems(GuiGraphics g, Panel p) {
		for (int i = 0; i < this.menu.slots.size(); i++) {
			Slot s = this.menu.slots.get(i);
			ItemStack stack = s.getItem();
			if (stack.isEmpty()) continue;
			Panel owner = slotOwner(i);
			if (owner != null ? owner != p : !p.now.contains(s.x + 8, s.y + 8)) continue;
			g.renderItem(stack, s.x, s.y);
			g.renderItemDecorations(this.font, stack, s.x, s.y);
		}
	}

	/** Janela a que o slot pertence (null = descobrir pela posicao). */
	protected Panel slotOwner(int slotIndex) {
		return null;
	}

	// ------------------------------------------------------------------ entrada

	protected Panel overlayAt(double mx, double my) {
		for (int i = this.panels.size() - 1; i >= 0; i--) {
			Panel p = this.panels.get(i);
			if (p.overlay && p.visible && p.alpha > 0.5f && p.grabArea().contains(mx, my)) return p;
		}
		return null;
	}

	protected Panel panelAt(double mx, double my) {
		for (int i = this.panels.size() - 1; i >= 0; i--) {
			Panel p = this.panels.get(i);
			if (p.visible && p.alpha > 0.5f && p.grabArea().contains(mx, my)) return p;
		}
		return null;
	}

	protected boolean isDraggingPanel() {
		return this.dragging;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.closeNanos >= 0L) return true;
		for (Panel p : this.panels) {
			if (p.content != null && p.visible && !p.grabArea().contains(mouseX, mouseY)) p.content.clickedOutside();
		}
		if (button == 0) {
			SideButton side = sideButtonAt(mouseX, mouseY);
			if (side != null) {
				UiSounds.click();
				side.action().run();
				return true;
			}
		}
		Panel over = overlayAt(mouseX, mouseY);
		if (this.hoveredSlot == null || over != null) {
			Panel p = over != null ? over : panelAt(mouseX, mouseY);
			if (p != null) {
				if (button == 0) {
					this.pressPanel = p;
					this.pressX = mouseX;
					this.pressY = mouseY;
					this.pressPanelX = p.rect.x();
					this.pressPanelY = p.rect.y();
					this.dragging = false;
					return true;
				}
				if (panelClick(p, mouseX, mouseY, button)) return true;
			}
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if (this.hoveredSlot != null) PoseState.tap();
		return handled;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (this.pressPanel != null) {
			if (!this.dragging && !this.pressPanel.fixed
					&& (Math.abs(mouseX - this.pressX) > DRAG_THRESHOLD || Math.abs(mouseY - this.pressY) > DRAG_THRESHOLD)) {
				this.dragging = true;
			}
			if (this.dragging) {
				Panel p = this.pressPanel;
				int nx = this.pressPanelX + (int) Math.round(mouseX - this.pressX);
				int ny = this.pressPanelY + (int) Math.round(mouseY - this.pressY);
				p.rect = clampToScreen(p, new Rect(nx, ny, p.rect.w(), p.rect.h()));
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (this.pressPanel != null && button == 0) {
			Panel p = this.pressPanel;
			this.pressPanel = null;
			if (this.dragging) {
				this.dragging = false;
				Rect before = new Rect(this.pressPanelX, this.pressPanelY, p.rect.w(), p.rect.h());
				p.rect = resolveOverlap(p, p.rect, before);
				saveLayout();
				UiSounds.click();
				return true;
			}
			boolean onTab = p.title != null && !p.fixed && mouseY < p.now.y() && !isTitleButton(p, mouseX, mouseY);
			long now = System.nanoTime();
			if (onTab && p == this.lastClickPanel && now - this.lastClickNanos < 350_000_000L) {
				p.rect = clampToScreen(p, p.def);
				saveLayout();
				UiSounds.toggle();
				this.lastClickPanel = null;
				return true;
			}
			this.lastClickPanel = p;
			this.lastClickNanos = now;
			panelClick(p, mouseX, mouseY, button);
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Panel over = overlayAt(mouseX, mouseY);
		Panel under = over != null ? over : panelAt(mouseX, mouseY);
		if (under != null && under.content != null) {
			under.content.scroll(under.now, mouseX, mouseY, scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		PanelContent typing = typingContent();
		if (typing != null) return typing.keyPressed(keyCode, scanCode, modifiers);
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
		return panelAt(mouseX, mouseY) == null && sideButtonAt(mouseX, mouseY) == null;
	}
}
