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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	/** Usado pelos testes automaticos: posicao atual de uma janela. */
	public Rect debugPanelRect(String id) {
		Panel p = panel(id);
		return p == null ? null : p.now;
	}

	private void loadLayout() {
		Map<String, float[]> saved = IsekaiConfig.get().layouts.get(layoutKey());
		for (Panel p : this.panels) {
			float[] f = saved == null ? null : saved.get(p.id);
			if (f != null && f.length == 2) {
				int x = Math.round(f[0] * Math.max(0, this.width - p.def.w()));
				int y = Math.round(f[1] * Math.max(0, this.height - p.def.h()));
				p.rect = clampToScreen(p, new Rect(x, y, p.def.w(), p.def.h()));
			} else {
				p.rect = clampToScreen(p, p.def);
			}
		}
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
		super.render(g, mouseX, mouseY, partialTick);
		float time = time();
		for (Panel p : this.panels) {
			if (!p.overlay || !p.visible || p.alpha <= 0.01f) continue;
			Draw.push(g);
			Draw.layer(g, 320f);
			if (p.framed) Panels.panel(g, this.font, p.now, p.alpha, p.progress, p.title, time);
			drawPanel(g, p, partialTick, mouseX, mouseY);
			if (p == this.pressPanel && this.dragging) {
				Rect a = p.grabArea();
				Draw.sprite(g, Theme.MAP_FRAME_GLOW, a.x() - 3, a.y() - 3, a.w() + 6, a.h() + 6, 0.8f);
			}
			Draw.pop(g);
		}
		renderOverlay(g, mouseX, mouseY, partialTick);
		if (this.closeNanos < 0L && this.pressPanel == null && overlayAt(mouseX, mouseY) == null) this.renderTooltip(g, mouseX, mouseY);
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
			if (p.framed) Panels.panel(g, this.font, p.now, p.alpha, p.progress, p.title, time);
			drawPanel(g, p, partialTick, mouseX, mouseY);
			if (p == this.pressPanel && this.dragging) {
				Rect a = p.grabArea();
				Draw.sprite(g, Theme.MAP_FRAME_GLOW, a.x() - 3, a.y() - 3, a.w() + 6, a.h() + 6, 0.8f);
			}
		}
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
				if (onPanelClick(p, mouseX, mouseY, button)) return true;
			}
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if (this.hoveredSlot != null) PoseState.tap();
		return handled;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (this.pressPanel != null) {
			if (!this.dragging && (Math.abs(mouseX - this.pressX) > DRAG_THRESHOLD || Math.abs(mouseY - this.pressY) > DRAG_THRESHOLD)) {
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
				saveLayout();
				return true;
			}
			boolean onTab = p.title != null && mouseY < p.now.y();
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
			onPanelClick(p, mouseX, mouseY, button);
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
		return panelAt(mouseX, mouseY) == null;
	}
}
