package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.anim.Ease;
import com.compelium.isekaiui.client.IsekaiKeys;
import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.lands.LandsTracker;
import com.compelium.isekaiui.map.DimMap;
import com.compelium.isekaiui.map.MapManager;
import com.compelium.isekaiui.map.MapRenderer;
import com.compelium.isekaiui.map.MapView;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Mapa grande. Nao ocupa a tela toda: "desenrola" a partir do minimapa (ou do canto da tela,
 * quando aberto pela tecla) e volta para o mesmo lugar ao fechar.
 */
public class IsekaiMapScreen extends Screen {
	private static final float OPEN_SECONDS = 0.46f;
	private static final float CLOSE_SECONDS = 0.30f;
	private static final float MIN_ZOOM = 0.0625f;
	private static final float MAX_ZOOM = 8f;

	private final Rect from;
	private final boolean fromInventory;
	private final MapView view = new MapView();
	private final List<MapButton> buttons = new ArrayList<>();

	private Rect target;
	private long openNanos;
	private long closeNanos = -1L;
	private Rect closeTarget;
	private Runnable afterClose;
	private boolean follow = true;
	private boolean dragging;
	private int hoveredButton = -1;

	private record MapButton(String icon, Component tooltip, Runnable action, BooleanSupplier on) {
	}

	private IsekaiMapScreen(Rect from, boolean fromInventory) {
		super(Component.translatable("isekai_ui.map.title"));
		this.from = from;
		this.fromInventory = fromInventory;
	}

	public static IsekaiMapScreen fromHotkey() {
		return new IsekaiMapScreen(null, false);
	}

	public static IsekaiMapScreen fromInventory(Rect miniRect) {
		return new IsekaiMapScreen(miniRect, true);
	}

	@Override
	protected void init() {
		int w = Math.min(this.width - 48, Math.round(this.width * 0.84f));
		int h = Math.min(this.height - 60, Math.round(this.height * 0.78f));
		this.target = new Rect((this.width - w) / 2, (this.height - h) / 2 + 6, w, h);
		if (this.openNanos == 0L) {
			this.openNanos = System.nanoTime();
			if (!this.fromInventory) UiSounds.mapOpen();
			LocalPlayer p = this.minecraft.player;
			if (p != null) {
				this.view.cx = p.getX();
				this.view.cz = p.getZ();
			}
			this.view.zoom = IsekaiConfig.get().map.mapZoom;
		}
		IsekaiConfig.MapSettings ms = IsekaiConfig.get().map;
		this.buttons.clear();
		this.buttons.add(new MapButton("inventory", Component.translatable("isekai_ui.button.back"), this::backToInventory, () -> false));
		this.buttons.add(new MapButton("center", Component.translatable("isekai_ui.button.center"), () -> this.follow = true, () -> this.follow));
		this.buttons.add(new MapButton("entities", Component.translatable("isekai_ui.button.entities"), () -> toggle(() -> ms.showEntities = !ms.showEntities), () -> ms.showEntities));
		this.buttons.add(new MapButton("claims", Component.translatable("isekai_ui.button.claims"), () -> toggle(() -> ms.showClaims = !ms.showClaims), () -> ms.showClaims));
		this.buttons.add(new MapButton("grid", Component.translatable("isekai_ui.button.grid"), () -> toggle(() -> ms.showGrid = !ms.showGrid), () -> ms.showGrid));
	}

	private static void toggle(Runnable change) {
		change.run();
		IsekaiConfig.save();
		UiSounds.toggle();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// sem desfoque: o mundo continua visivel em volta do mapa
	}

	// ------------------------------------------------------------------ animacao

	private static float secondsSince(long nanos) {
		return (System.nanoTime() - nanos) / 1.0e9f;
	}

	private float openTime() {
		return IsekaiConfig.get().animations ? secondsSince(this.openNanos) : 10f;
	}

	private float closeProgress() {
		if (this.closeNanos < 0L) return 0f;
		return Ease.window(secondsSince(this.closeNanos), 0f, CLOSE_SECONDS);
	}

	private Rect corner() {
		return new Rect(this.width - 36, 10, 26, 22);
	}

	/** Retangulo atual: largura abre primeiro e a altura logo depois (efeito de pergaminho desenrolando). */
	private Rect current() {
		if (this.closeNanos >= 0L) {
			float c = closeProgress();
			float pw = Ease.inCubic(Ease.window(c, 0.15f, 0.85f));
			float ph = Ease.inCubic(Ease.window(c, 0f, 0.8f));
			return mix(this.target, this.closeTarget, pw, ph);
		}
		float t = openTime() / OPEN_SECONDS;
		float pw = Ease.outCubic(Ease.window(t, 0f, 0.78f));
		float ph = Ease.outBack(Ease.window(t, 0.14f, 0.86f));
		Rect src = this.from != null ? this.from : corner();
		return mix(src, this.target, pw, ph);
	}

	private static Rect mix(Rect a, Rect b, float pw, float ph) {
		float cxA = a.x() + a.w() / 2f, cyA = a.y() + a.h() / 2f;
		float cxB = b.x() + b.w() / 2f, cyB = b.y() + b.h() / 2f;
		float w = a.w() + (b.w() - a.w()) * pw;
		float h = a.h() + (b.h() - a.h()) * ph;
		float cx = cxA + (cxB - cxA) * pw;
		float cy = cyA + (cyB - cyA) * ph;
		return new Rect(Math.round(cx - w / 2f), Math.round(cy - h / 2f), Math.max(4, Math.round(w)), Math.max(4, Math.round(h)));
	}

	private float contentAlpha() {
		if (this.closeNanos >= 0L) return 1f - Ease.window(closeProgress(), 0.55f, 0.45f);
		return Ease.outCubic(Math.min(1f, openTime() / OPEN_SECONDS * 3f));
	}

	private float chromeAlpha() {
		if (this.closeNanos >= 0L) return 1f - Ease.window(closeProgress(), 0f, 0.4f);
		return Ease.window(openTime() / OPEN_SECONDS, 0.7f, 0.3f);
	}

	// ------------------------------------------------------------------ desenho

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (this.closeNanos >= 0L && closeProgress() >= 1f) {
			finishClose();
			return;
		}
		LocalPlayer p = this.minecraft.player;
		if (p == null) return;
		if (this.follow) {
			this.view.cx = Mth.lerp(partialTick, p.xo, p.getX());
			this.view.cz = Mth.lerp(partialTick, p.zo, p.getZ());
		}
		Rect r = current();
		float a = contentAlpha();
		float chrome = chromeAlpha();
		float dim = this.closeNanos >= 0L ? 1f - closeProgress() : Ease.window(openTime(), 0f, OPEN_SECONDS);

		// leve vinheta para destacar o mapa sem esconder o jogo
		Draw.gradient(g, 0, 0, this.width, this.height / 3, Draw.alpha(0x55000000, dim), 0x00000000);
		Draw.gradient(g, 0, this.height * 2 / 3, this.width, this.height, 0x00000000, Draw.alpha(0x55000000, dim));

		Draw.fill(g, r.x() - 3, r.y() + 3, r.right() + 7, r.bottom() + 10, Draw.alpha(Theme.SHADOW, a));
		DimMap map = MapManager.current();
		MapRenderer.draw(g, map, this.view, r, a, partialTick, false);
		Draw.sprite(g, Theme.MAP_FRAME, r.x() - 6, r.y() - 6, r.w() + 12, r.h() + 12, Math.max(a, chrome));

		updateHover(mouseX, mouseY);
		if (chrome > 0.01f) drawChrome(g, r, chrome, mouseX, mouseY, map);
		super.render(g, mouseX, mouseY, partialTick);
		if (this.closeNanos < 0L && this.hoveredButton >= 0) {
			this.setTooltipForNextRenderPass(this.buttons.get(this.hoveredButton).tooltip());
		}
	}

	private void drawChrome(GuiGraphics g, Rect r, float a, int mouseX, int mouseY, DimMap map) {
		// aba com o nome da regiao no topo (como na referencia)
		Component region = Panels.upper(regionName());
		int tabW = Math.min(r.w() - 40, Math.max(90, this.font.width(region) + 30));
		int tabX = r.cx() - tabW / 2;
		int tabY = r.y() - 6 - InventoryLayout.TAB;
		Panels.tab(g, this.font, tabX, tabY, tabW, region, a);

		// informacoes do cursor (canto inferior esquerdo)
		if (r.contains(mouseX, mouseY) && this.closeNanos < 0L) {
			int wx = Mth.floor(MapRenderer.toWorldX(this.view, r, mouseX));
			int wz = Mth.floor(MapRenderer.toWorldZ(this.view, r, mouseY));
			Component line = Component.literal("X " + wx + "   Z " + wz);
			String owner = map == null ? null : map.claim(wx >> 4, wz >> 4);
			if (owner != null) {
				line = Component.translatable("isekai_ui.map.claim", owner).append(Component.literal("   X " + wx + "   Z " + wz));
			}
			int w = this.font.width(line) + 12;
			Draw.sprite(g, Theme.PILL, r.x() + 6, r.bottom() - 18, w, 13, a * 0.92f);
			Draw.text(g, this.font, line, r.x() + 12, r.bottom() - 15, Draw.alpha(Theme.TEXT, a), false);
		}

		// zoom
		Component zoom = Component.literal(String.format(java.util.Locale.ROOT, "x%.2f", this.view.zoom));
		Draw.text(g, this.font, zoom, r.right() - 8 - this.font.width(zoom), r.bottom() - 15 - InventoryLayout.HEX_H - 4,
				Draw.alpha(Theme.MAP_INK, a), false);

		// botoes hexagonais (canto inferior direito)
		for (int i = 0; i < this.buttons.size(); i++) {
			MapButton b = this.buttons.get(i);
			Panels.hexButton(g, buttonRect(r, i), b.icon(), i == this.hoveredButton, b.on().getAsBoolean(), a);
		}
		// fechar (no fim da aba)
		Panels.hexButton(g, closeRect(r), "close", this.hoveredButton == -2, false, a);
	}

	private Rect buttonRect(Rect r, int i) {
		int n = this.buttons.size();
		int x = r.right() - 8 - n * InventoryLayout.BUTTON_STEP + 2 + i * InventoryLayout.BUTTON_STEP;
		return new Rect(x, r.bottom() - 8 - InventoryLayout.HEX_H, InventoryLayout.HEX_W, InventoryLayout.HEX_H);
	}

	private Rect closeRect(Rect r) {
		return new Rect(r.right() - InventoryLayout.HEX_W + 4, r.y() - 6 - InventoryLayout.HEX_H + 2, InventoryLayout.HEX_W, InventoryLayout.HEX_H);
	}

	private void updateHover(int mx, int my) {
		int before = this.hoveredButton;
		this.hoveredButton = -1;
		if (this.closeNanos >= 0L || chromeAlpha() < 0.5f) return;
		Rect r = current();
		for (int i = 0; i < this.buttons.size(); i++) {
			if (buttonRect(r, i).contains(mx, my)) this.hoveredButton = i;
		}
		if (closeRect(r).contains(mx, my)) this.hoveredButton = -2;
		if (this.hoveredButton != -1 && this.hoveredButton != before) UiSounds.hover();
	}

	private Component regionName() {
		String land = LandsTracker.currentLand();
		if (land != null) return Component.literal(land);
		if (this.minecraft != null && this.minecraft.level != null && this.minecraft.player != null) {
			return this.minecraft.level.getBiome(this.minecraft.player.blockPosition()).unwrapKey()
					.map(k -> (Component) Component.translatable(Util.makeDescriptionId("biome", k.location())))
					.orElse(Component.translatable("isekai_ui.map.unknown_region"));
		}
		return Component.translatable("isekai_ui.map.unknown_region");
	}

	// ------------------------------------------------------------------ entrada

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.closeNanos >= 0L) return true;
		Rect r = current();
		if (chromeAlpha() > 0.5f) {
			if (closeRect(r).contains(mouseX, mouseY)) {
				UiSounds.click();
				onClose();
				return true;
			}
			for (int i = 0; i < this.buttons.size(); i++) {
				if (buttonRect(r, i).contains(mouseX, mouseY)) {
					UiSounds.click();
					this.buttons.get(i).action().run();
					return true;
				}
			}
		}
		if (button == 0 && r.contains(mouseX, mouseY)) {
			this.dragging = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (this.dragging && button == 0 && this.closeNanos < 0L) {
			this.view.cx -= dragX / this.view.zoom;
			this.view.cz -= dragY / this.view.zoom;
			this.follow = false;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		this.dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.closeNanos >= 0L || scrollY == 0) return true;
		Rect r = current();
		float newZoom = Mth.clamp(this.view.zoom * (float) Math.pow(1.2, scrollY), MIN_ZOOM, MAX_ZOOM);
		double offX = mouseX - (r.x() + r.w() / 2.0);
		double offY = mouseY - (r.y() + r.h() / 2.0);
		boolean atCenter = Math.abs(offX) < 14 && Math.abs(offY) < 14;
		if (r.contains(mouseX, mouseY) && !(this.follow && atCenter)) {
			double wx = MapRenderer.toWorldX(this.view, r, mouseX);
			double wz = MapRenderer.toWorldZ(this.view, r, mouseY);
			this.view.cx = wx - offX / newZoom;
			this.view.cz = wz - offY / newZoom;
			this.follow = false;
		}
		this.view.zoom = newZoom;
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.closeNanos >= 0L) return true;
		if (IsekaiKeys.isMapKey(keyCode, scanCode)) {
			onClose();
			return true;
		}
		if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
			backToInventory();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// ------------------------------------------------------------------ fechar

	@Override
	public void onClose() {
		if (this.closeNanos >= 0L) return;
		UiSounds.mapClose();
		startClose(corner(), null);
	}

	private void backToInventory() {
		if (this.closeNanos >= 0L) return;
		UiSounds.click();
		InventoryLayout L = InventoryLayout.compute(this.width, this.height, IsekaiInventoryScreen.buttonCount());
		startClose(L.mini, () -> {
			if (this.minecraft.player != null) this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
		});
	}

	private void startClose(Rect to, Runnable after) {
		this.closeNanos = System.nanoTime();
		this.closeTarget = to;
		this.afterClose = after;
		this.dragging = false;
		if (!IsekaiConfig.get().animations) finishClose();
	}

	private void finishClose() {
		Runnable after = this.afterClose;
		this.afterClose = null;
		if (this.minecraft == null || this.minecraft.screen != this) return;
		if (after != null) after.run();
		else this.minecraft.setScreen(null);
	}
}
