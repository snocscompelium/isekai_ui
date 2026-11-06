package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.anim.Ease;
import com.compelium.isekaiui.client.IsekaiKeys;
import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.lands.LandsTracker;
import com.compelium.isekaiui.map.ChatWaypoints;
import com.compelium.isekaiui.map.DimMap;
import com.compelium.isekaiui.map.Emblems;
import com.compelium.isekaiui.map.MapIcons;
import com.compelium.isekaiui.map.MapManager;
import com.compelium.isekaiui.map.MapRenderer;
import com.compelium.isekaiui.map.MapView;
import com.compelium.isekaiui.map.Waypoints;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Mapa grande. Nao ocupa a tela toda: "desenrola" a partir do minimapa (ou do canto da tela,
 * quando aberto pela tecla) e volta para o mesmo lugar ao fechar.
 * Clique direito no mapa: criar/editar marcador ou escolher o simbolo de um reino.
 */
public class IsekaiMapScreen extends Screen {
	private static final float OPEN_SECONDS = 0.46f;
	private static final float CLOSE_SECONDS = 0.30f;
	private static final float MIN_ZOOM = 0.0625f;
	private static final float MAX_ZOOM = 8f;
	private static final int CELL = 18;
	private static final int WAYPOINT_COLS = 9;
	private static final int EMBLEM_COLS = 11;

	private enum Popup { NONE, CONTEXT, WAYPOINT, EMBLEM }

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

	// ---- popups
	private Popup popup = Popup.NONE;
	private Rect popupRect;
	private int ctxX;
	private int ctxZ;
	private String ctxLand;
	private Waypoints.Waypoint ctxWaypoint;
	private final List<ContextOption> contextOptions = new ArrayList<>();
	private Waypoints.Waypoint editing;
	/** Marcador vindo do chat (botao [+ Marcador]) que abre o editor assim que o mapa abrir. */
	private Waypoints.Waypoint pendingShared;
	private boolean editingNew;
	private EditBox nameBox;
	private String selIcon = "glyph:pin";
	private int selColor = Waypoints.COLORS[0];
	private String emblemLand;
	private String selEmblem;

	private record MapButton(String icon, Component tooltip, Runnable action, BooleanSupplier on) {
	}

	private record ContextOption(Component label, Runnable action) {
	}

	private IsekaiMapScreen(Rect from, boolean fromInventory) {
		super(Component.translatable("isekai_ui.map.title"));
		this.from = from;
		this.fromInventory = fromInventory;
	}

	public static IsekaiMapScreen fromHotkey() {
		return new IsekaiMapScreen(null, false);
	}

	/** Abre o mapa ja com o editor de um marcador novo (recebido no chat), centralizado nele. */
	public static IsekaiMapScreen editShared(Waypoints.Waypoint w) {
		IsekaiMapScreen screen = new IsekaiMapScreen(null, false);
		screen.pendingShared = w;
		return screen;
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
		this.buttons.add(new MapButton("pin", Component.translatable("isekai_ui.button.waypoints"), () -> toggle(() -> ms.showWaypoints = !ms.showWaypoints), () -> ms.showWaypoints));
		this.buttons.add(new MapButton("grid", Component.translatable("isekai_ui.button.grid"), () -> toggle(() -> ms.showGrid = !ms.showGrid), () -> ms.showGrid));
		if (this.popup == Popup.WAYPOINT) layoutWaypointEditor(this.nameBox == null ? "" : this.nameBox.getValue());
		if (this.pendingShared != null) {
			Waypoints.Waypoint w = this.pendingShared;
			this.pendingShared = null;
			this.follow = false;
			this.view.cx = w.x + 0.5;
			this.view.cz = w.z + 0.5;
			this.ctxX = w.x;
			this.ctxZ = w.z;
			openWaypointEditor(w, true);
		}
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

		Draw.gradient(g, 0, 0, this.width, this.height / 3, Draw.alpha(0x55000000, dim), 0x00000000);
		Draw.gradient(g, 0, this.height * 2 / 3, this.width, this.height, 0x00000000, Draw.alpha(0x55000000, dim));

		Draw.fill(g, r.x() - 3, r.y() + 3, r.right() + 7, r.bottom() + 10, Draw.alpha(Theme.SHADOW, a));
		DimMap map = MapManager.current();
		MapRenderer.draw(g, map, this.view, r, a, partialTick, false);
		Draw.sprite(g, Theme.MAP_FRAME, r.x() - 6, r.y() - 6, r.w() + 12, r.h() + 12, Math.max(a, chrome));

		updateHover(mouseX, mouseY);
		if (chrome > 0.01f) drawChrome(g, r, chrome, mouseX, mouseY, map);
		if (this.popup != Popup.NONE && this.closeNanos < 0L) drawPopup(g, mouseX, mouseY, partialTick);
		super.render(g, mouseX, mouseY, partialTick);
		if (this.closeNanos < 0L && this.popup == Popup.NONE && this.hoveredButton >= 0) {
			this.setTooltipForNextRenderPass(this.buttons.get(this.hoveredButton).tooltip());
		}
	}

	private void drawChrome(GuiGraphics g, Rect r, float a, int mouseX, int mouseY, DimMap map) {
		Component region = Panels.upper(regionName());
		int tabW = Math.min(r.w() - 40, Math.max(90, this.font.width(region) + 30));
		Panels.tab(g, this.font, r.cx() - tabW / 2, r.y() - 6 - InventoryLayout.TAB, tabW, region, a);

		if (r.contains(mouseX, mouseY) && this.closeNanos < 0L && this.popup == Popup.NONE) {
			int wx = Mth.floor(MapRenderer.toWorldX(this.view, r, mouseX));
			int wz = Mth.floor(MapRenderer.toWorldZ(this.view, r, mouseY));
			Component line = Component.literal("X " + wx + "   Z " + wz);
			Waypoints.Waypoint hovered = map == null ? null : waypointAt(map, r, mouseX, mouseY);
			String owner = map == null ? null : map.claim(wx >> 4, wz >> 4);
			if (hovered != null) {
				net.minecraft.network.chat.MutableComponent name = hovered.name == null || hovered.name.isBlank()
						? Component.translatable("isekai_ui.map.waypoint_default") : Component.literal(hovered.name);
				line = name.append(Component.literal("   X " + hovered.x + "   Y " + hovered.y + "   Z " + hovered.z));
			} else if (owner != null) {
				line = Component.translatable("isekai_ui.map.claim", owner).append(Component.literal("   X " + wx + "   Z " + wz));
			}
			int w = this.font.width(line) + 12;
			Draw.sprite(g, Theme.PILL, r.x() + 6, r.bottom() - 18, w, 13, a * 0.92f);
			Draw.text(g, this.font, line, r.x() + 12, r.bottom() - 15, Draw.alpha(Theme.TEXT, a), false);
			if (map != null && map.waypoints().all().size() < 3) {
				Component hint = Component.translatable("isekai_ui.map.right_click_hint");
				Draw.text(g, this.font, hint, r.x() + 8, r.y() + 6, Draw.alpha(Theme.MAP_INK, a * 0.75f), false);
			}
		}

		Component zoom = Component.literal(String.format(java.util.Locale.ROOT, "x%.2f", this.view.zoom));
		Draw.text(g, this.font, zoom, r.right() - 8 - this.font.width(zoom), r.bottom() - 15 - InventoryLayout.HEX_H - 4,
				Draw.alpha(Theme.MAP_INK, a), false);
		for (int i = 0; i < this.buttons.size(); i++) {
			MapButton b = this.buttons.get(i);
			Panels.hexButton(g, buttonRect(r, i), b.icon(), i == this.hoveredButton, b.on().getAsBoolean(), a);
		}
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
		if (this.closeNanos >= 0L || chromeAlpha() < 0.5f || this.popup != Popup.NONE) return;
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

	private Waypoints.Waypoint waypointAt(DimMap map, Rect r, double mx, double my) {
		if (!IsekaiConfig.get().map.showWaypoints) return null;
		Waypoints.Waypoint best = null;
		double bestD = 49;
		for (Waypoints.Waypoint w : map.waypoints().all()) {
			int sx = MapRenderer.toScreenX(this.view, r, w.x + 0.5);
			int sy = MapRenderer.toScreenY(this.view, r, w.z + 0.5);
			double d = (sx - mx) * (sx - mx) + (sy - my) * (sy - my);
			if (d < bestD) {
				bestD = d;
				best = w;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ popups

	private void openContext(double mx, double my) {
		DimMap map = MapManager.current();
		if (map == null) return;
		Rect r = current();
		this.ctxX = Mth.floor(MapRenderer.toWorldX(this.view, r, mx));
		this.ctxZ = Mth.floor(MapRenderer.toWorldZ(this.view, r, my));
		this.ctxWaypoint = waypointAt(map, r, mx, my);
		this.ctxLand = map.claim(this.ctxX >> 4, this.ctxZ >> 4);
		this.contextOptions.clear();
		if (this.ctxWaypoint != null) {
			Waypoints.Waypoint w = this.ctxWaypoint;
			this.contextOptions.add(new ContextOption(Component.translatable("isekai_ui.map.edit_waypoint"), () -> openWaypointEditor(w, false)));
		} else {
			this.contextOptions.add(new ContextOption(Component.translatable("isekai_ui.map.new_waypoint"), this::openNewWaypoint));
		}
		if (this.ctxLand != null) {
			String land = this.ctxLand;
			this.contextOptions.add(new ContextOption(Component.translatable("isekai_ui.map.set_emblem", land), () -> openEmblemEditor(land)));
		}
		if (this.contextOptions.size() == 1) {
			this.contextOptions.get(0).action().run();
			return;
		}
		int w = 0;
		for (ContextOption o : this.contextOptions) w = Math.max(w, this.font.width(o.label()));
		w += 16;
		int h = 6 + this.contextOptions.size() * 14 + 4;
		int x = Mth.clamp((int) mx, 4, this.width - w - 4);
		int y = Mth.clamp((int) my, 4, this.height - h - 4);
		this.popupRect = new Rect(x, y, w, h);
		this.popup = Popup.CONTEXT;
		UiSounds.click();
	}

	private void openNewWaypoint() {
		DimMap map = MapManager.current();
		Waypoints.Waypoint w = new Waypoints.Waypoint();
		w.x = this.ctxX;
		w.z = this.ctxZ;
		Integer y = map == null ? null : map.surfaceY(this.ctxX, this.ctxZ);
		w.y = y != null ? y + 1 : (this.minecraft.player == null ? 64 : this.minecraft.player.getBlockY());
		w.name = "";
		openWaypointEditor(w, true);
	}

	private void openWaypointEditor(Waypoints.Waypoint w, boolean isNew) {
		this.editing = w;
		this.editingNew = isNew;
		this.selIcon = w.icon;
		this.selColor = w.color;
		this.popup = Popup.WAYPOINT;
		layoutWaypointEditor(w.name);
		UiSounds.click();
	}

	private List<MapIcons.Icon> waypointIcons() {
		List<MapIcons.Icon> list = new ArrayList<>(MapIcons.glyphs());
		list.addAll(MapIcons.customIcons());
		return list;
	}

	private void layoutWaypointEditor(String name) {
		int iconRows = (waypointIcons().size() + WAYPOINT_COLS - 1) / WAYPOINT_COLS;
		int w = WAYPOINT_COLS * CELL + 16;
		int h = 8 + 16 + 6 + iconRows * CELL + 6 + 14 + 6 + 10 + 6 + 14 + 8 + 4;
		this.popupRect = new Rect((this.width - w) / 2, Math.max(InventoryLayout.TAB + 4, (this.height - h) / 2), w, h);
		if (this.nameBox != null) removeWidget(this.nameBox);
		this.nameBox = new EditBox(this.font, this.popupRect.x() + 12, this.popupRect.y() + 12, w - 24, 10, Component.translatable("isekai_ui.map.waypoint_name"));
		this.nameBox.setBordered(false);
		this.nameBox.setMaxLength(32);
		this.nameBox.setTextColor(Theme.TEXT);
		this.nameBox.setHint(Component.translatable("isekai_ui.map.waypoint_name_hint"));
		this.nameBox.setValue(name == null ? "" : name);
		// so recebe teclado/mouse por aqui; e desenhado dentro do popup (camada de cima) em drawPopup
		addWidget(this.nameBox);
		setFocused(this.nameBox);
		this.nameBox.setFocused(true);
	}

	private void openEmblemEditor(String land) {
		this.emblemLand = land;
		this.selEmblem = Emblems.get(land);
		int count = 1 + emblemChoices().size();
		int rows = (count + EMBLEM_COLS - 1) / EMBLEM_COLS;
		int label = 0;
		for (String k : new String[]{"isekai_ui.popup.open_folder", "isekai_ui.popup.reload", "isekai_ui.popup.done"}) {
			label = Math.max(label, this.font.width(Component.translatable(k)));
		}
		int w = Math.max(EMBLEM_COLS * CELL + 16, 3 * (label + 12) + 8 + 16);
		int hintLines = this.font.split(Component.translatable("isekai_ui.map.emblem_hint"), w - 18).size();
		int h = 8 + rows * CELL + 6 + hintLines * 10 + 6 + 14 + 8;
		this.popupRect = new Rect((this.width - w) / 2, Math.max(InventoryLayout.TAB + 4, (this.height - h) / 2), w, h);
		this.popup = Popup.EMBLEM;
		UiSounds.click();
	}

	private void closePopup() {
		if (this.nameBox != null) {
			removeWidget(this.nameBox);
			this.nameBox = null;
		}
		this.popup = Popup.NONE;
		this.editing = null;
		this.emblemLand = null;
	}

	private void saveWaypoint() {
		DimMap map = MapManager.current();
		if (map == null || this.editing == null) {
			closePopup();
			return;
		}
		String name = this.nameBox == null ? "" : this.nameBox.getValue().strip();
		this.editing.name = name.isEmpty() ? Component.translatable("isekai_ui.map.waypoint_default").getString() : name;
		this.editing.icon = this.selIcon;
		this.editing.color = this.selColor;
		if (this.editingNew) map.waypoints().add(this.editing);
		else map.waypoints().save();
		UiSounds.toggle();
		closePopup();
	}

	private void deleteWaypoint() {
		DimMap map = MapManager.current();
		if (map != null && this.editing != null && !this.editingNew) map.waypoints().remove(this.editing);
		UiSounds.click();
		closePopup();
	}

	/** Botao "compartilhar no chat" na linha das coordenadas do editor de marcador. */
	private static Rect shareRect(Rect pr, int colorsY) {
		return new Rect(pr.right() - 8 - 22, colorsY + 14 + 3, 22, 14);
	}

	/** Fecha o mapa e abre o chat com o marcador escrito num formato que outros mods de mapa entendem. */
	private void shareWaypoint() {
		if (this.editing == null) return;
		Waypoints.Waypoint copy = new Waypoints.Waypoint();
		String name = this.nameBox == null ? this.editing.name : this.nameBox.getValue().strip();
		copy.name = name == null || name.isEmpty() ? Component.translatable("isekai_ui.map.waypoint_default").getString() : name;
		copy.x = this.editing.x;
		copy.y = this.editing.y;
		copy.z = this.editing.z;
		copy.color = this.selColor;
		copy.icon = this.selIcon;
		String dim = MapManager.currentDimension();
		String text = ChatWaypoints.shareText(copy, dim == null ? "minecraft:overworld" : dim);
		UiSounds.click();
		closePopup();
		this.minecraft.setScreen(new ChatScreen(text));
	}

	/** Botoes de texto dos popups (pilula escura com texto). */
	private List<Rect> popupButtons(int count) {
		List<Rect> list = new ArrayList<>();
		int bw = (this.popupRect.w() - 16 - (count - 1) * 4) / count;
		for (int i = 0; i < count; i++) {
			list.add(new Rect(this.popupRect.x() + 8 + i * (bw + 4), this.popupRect.bottom() - 8 - 14, bw, 14));
		}
		return list;
	}

	private void drawTextButton(GuiGraphics g, Rect r, Component label, boolean hover) {
		Draw.sprite(g, hover ? Theme.TAB : Theme.PILL, r.x(), r.y(), r.w(), r.h(), 1f);
		Draw.textCentered(g, this.font, label, r.cx(), r.y() + 3, hover ? Theme.TEXT_INK : Theme.TEXT, false);
	}

	private void drawPopup(GuiGraphics g, int mx, int my, float partialTick) {
		Rect pr = this.popupRect;
		if (pr == null) return;
		Draw.push(g);
		// camada acima do mapa: textos do mapa (nomes de reinos) ficam um pouco "a frente" e vazariam pelo popup
		Draw.layer(g, 100f);
		// o vidro das janelas e translucido: um fundo quase opaco evita que o mapa apareca atraves do popup
		Draw.fill(g, pr.x() + 2, pr.y() + 2, pr.right() - 2, pr.bottom() - 2, IsekaiMenuScreen.OVERLAY_BACKDROP);
		if (this.popup == Popup.CONTEXT) {
			Draw.sprite(g, Theme.PANEL, pr.x(), pr.y(), pr.w(), pr.h(), 1f);
			for (int i = 0; i < this.contextOptions.size(); i++) {
				Rect row = new Rect(pr.x() + 4, pr.y() + 5 + i * 14, pr.w() - 8, 14);
				boolean hover = row.contains(mx, my);
				if (hover) Draw.fill(g, row.x(), row.y(), row.right(), row.bottom(), 0x30FFF2CF);
				Draw.text(g, this.font, this.contextOptions.get(i).label(), row.x() + 4, row.y() + 3, hover ? Theme.TEXT_GOLD : Theme.TEXT, false);
			}
		} else if (this.popup == Popup.WAYPOINT) {
			Panels.panel(g, this.font, pr, 1f, 1f, Component.translatable(this.editingNew ? "isekai_ui.map.new_waypoint" : "isekai_ui.map.edit_waypoint"), 0f);
			Rect field = new Rect(pr.x() + 8, pr.y() + 8, pr.w() - 16, 16);
			Draw.fill(g, field.x(), field.y(), field.right(), field.bottom(), 0xC00A080E);
			Draw.fill(g, field.x(), field.bottom() - 1, field.right(), field.bottom(), Draw.alpha(Theme.GOLD, 0.8f));
			if (this.nameBox != null) this.nameBox.render(g, mx, my, partialTick);
			List<MapIcons.Icon> icons = waypointIcons();
			int gy = pr.y() + 8 + 16 + 6;
			for (int i = 0; i < icons.size(); i++) {
				Rect cell = waypointCell(pr, i);
				MapIcons.Icon icon = icons.get(i);
				boolean sel = icon.key().equals(this.selIcon);
				boolean hover = cell.contains(mx, my);
				Draw.sprite(g, Theme.SLOT, cell.x(), cell.y(), CELL, CELL, 1f);
				if (sel || hover) Draw.fill(g, cell.x() + 1, cell.y() + 1, cell.right() - 1, cell.bottom() - 1, sel ? 0x55E6CB82 : 0x30FFF2CF);
				icon.draw(g, cell.cx(), cell.cy(), 12, this.selColor, 1f);
			}
			int iconRows = (icons.size() + WAYPOINT_COLS - 1) / WAYPOINT_COLS;
			int cy = gy + iconRows * CELL + 6;
			for (int i = 0; i < Waypoints.COLORS.length; i++) {
				Rect sw = new Rect(pr.x() + 8 + i * 16, cy, 14, 14);
				int c = Waypoints.COLORS[i];
				boolean sel = c == this.selColor;
				Draw.fill(g, sw.x(), sw.y(), sw.right(), sw.bottom(), sel ? 0xFFFFF2CF : 0xFF1B160F);
				Draw.fill(g, sw.x() + 2, sw.y() + 2, sw.right() - 2, sw.bottom() - 2, 0xFF000000 | c);
			}
			Rect share = shareRect(pr, cy);
			boolean shareHover = share.contains(mx, my);
			Draw.sprite(g, shareHover ? Theme.TAB : Theme.PILL, share.x(), share.y(), share.w(), share.h(), 1f);
			Draw.sprite(g, Theme.icon("chat"), share.cx() - 5, share.cy() - 5, 10, 10, shareHover ? 0xFF3A2B17 : 0xFFE6CB82);
			if (shareHover) this.setTooltipForNextRenderPass(Component.translatable("isekai_ui.map.share"));
			Component coords = Component.literal("X " + this.editing.x + "   Y " + this.editing.y + "   Z " + this.editing.z);
			Draw.text(g, this.font, coords, pr.x() + 10, cy + 14 + 6, Theme.TEXT_DIM, false);
			List<Rect> btns = popupButtons(this.editingNew ? 2 : 3);
			drawTextButton(g, btns.get(0), Component.translatable("isekai_ui.popup.save"), btns.get(0).contains(mx, my));
			if (this.editingNew) {
				drawTextButton(g, btns.get(1), Component.translatable("isekai_ui.popup.cancel"), btns.get(1).contains(mx, my));
			} else {
				drawTextButton(g, btns.get(1), Component.translatable("isekai_ui.popup.delete"), btns.get(1).contains(mx, my));
				drawTextButton(g, btns.get(2), Component.translatable("isekai_ui.popup.cancel"), btns.get(2).contains(mx, my));
			}
		} else if (this.popup == Popup.EMBLEM) {
			Panels.panel(g, this.font, pr, 1f, 1f, Component.translatable("isekai_ui.map.emblem_title", this.emblemLand), 0f);
			List<MapIcons.Icon> icons = emblemChoices();
			int gy = pr.y() + 8;
			for (int i = 0; i <= icons.size(); i++) {
				Rect cell = emblemCell(pr, i);
				boolean hover = cell.contains(mx, my);
				boolean sel = i == 0 ? this.selEmblem == null : icons.get(i - 1).key().equals(this.selEmblem);
				Draw.sprite(g, Theme.SLOT, cell.x(), cell.y(), CELL, CELL, 1f);
				if (sel || hover) Draw.fill(g, cell.x() + 1, cell.y() + 1, cell.right() - 1, cell.bottom() - 1, sel ? 0x55E6CB82 : 0x30FFF2CF);
				if (i == 0) Draw.sprite(g, Theme.icon("close"), cell.cx() - 6, cell.cy() - 6, 12, 12, 0xFF7E7566);
				else icons.get(i - 1).draw(g, cell.cx(), cell.cy(), 16, 0xFFFFFF, 1f);
			}
			int rows = (icons.size() + EMBLEM_COLS) / EMBLEM_COLS;
			int ty = gy + rows * CELL + 6;
			for (net.minecraft.util.FormattedCharSequence line : this.font.split(Component.translatable("isekai_ui.map.emblem_hint"), pr.w() - 18)) {
				g.drawString(this.font, line, pr.x() + 9, ty, Theme.TEXT_DIM, false);
				ty += 10;
			}
			List<Rect> btns = popupButtons(3);
			drawTextButton(g, btns.get(0), Component.translatable("isekai_ui.popup.open_folder"), btns.get(0).contains(mx, my));
			drawTextButton(g, btns.get(1), Component.translatable("isekai_ui.popup.reload"), btns.get(1).contains(mx, my));
			drawTextButton(g, btns.get(2), Component.translatable("isekai_ui.popup.done"), btns.get(2).contains(mx, my));
		}
		Draw.pop(g);
	}

	private static Rect emblemCell(Rect pr, int i) {
		return new Rect(pr.x() + 8 + (i % EMBLEM_COLS) * CELL, pr.y() + 8 + (i / EMBLEM_COLS) * CELL, CELL, CELL);
	}

	private static Rect waypointCell(Rect pr, int i) {
		return new Rect(pr.x() + 8 + (i % WAYPOINT_COLS) * CELL, pr.y() + 8 + 16 + 6 + (i / WAYPOINT_COLS) * CELL, CELL, CELL);
	}

	private List<MapIcons.Icon> emblemChoices() {
		List<MapIcons.Icon> list = new ArrayList<>(MapIcons.emblems());
		list.addAll(MapIcons.customIcons());
		return list;
	}

	private boolean popupClick(double mx, double my, int button) {
		Rect pr = this.popupRect;
		if (pr == null || !pr.grow(2).contains(mx, my)) {
			closePopup();
			return true;
		}
		if (this.popup == Popup.CONTEXT) {
			for (int i = 0; i < this.contextOptions.size(); i++) {
				Rect row = new Rect(pr.x() + 4, pr.y() + 5 + i * 14, pr.w() - 8, 14);
				if (row.contains(mx, my)) {
					this.popup = Popup.NONE;
					this.contextOptions.get(i).action().run();
					return true;
				}
			}
			return true;
		}
		if (this.popup == Popup.WAYPOINT) {
			if (this.nameBox != null && this.nameBox.isMouseOver(mx, my)) return false;
			List<MapIcons.Icon> icons = waypointIcons();
			int gy = pr.y() + 8 + 16 + 6;
			for (int i = 0; i < icons.size(); i++) {
				if (waypointCell(pr, i).contains(mx, my)) {
					this.selIcon = icons.get(i).key();
					UiSounds.hover();
					return true;
				}
			}
			int iconRows = (icons.size() + WAYPOINT_COLS - 1) / WAYPOINT_COLS;
			int cy = gy + iconRows * CELL + 6;
			for (int i = 0; i < Waypoints.COLORS.length; i++) {
				if (new Rect(pr.x() + 8 + i * 16, cy, 14, 14).contains(mx, my)) {
					this.selColor = Waypoints.COLORS[i];
					UiSounds.hover();
					return true;
				}
			}
			if (shareRect(pr, cy).contains(mx, my)) {
				shareWaypoint();
				return true;
			}
			List<Rect> btns = popupButtons(this.editingNew ? 2 : 3);
			if (btns.get(0).contains(mx, my)) saveWaypoint();
			else if (!this.editingNew && btns.get(1).contains(mx, my)) deleteWaypoint();
			else if (btns.get(btns.size() - 1).contains(mx, my)) closePopup();
			return true;
		}
		if (this.popup == Popup.EMBLEM) {
			List<MapIcons.Icon> icons = emblemChoices();
			for (int i = 0; i <= icons.size(); i++) {
				Rect cell = emblemCell(pr, i);
				if (cell.contains(mx, my)) {
					this.selEmblem = i == 0 ? null : icons.get(i - 1).key();
					Emblems.set(this.emblemLand, this.selEmblem);
					UiSounds.toggle();
					return true;
				}
			}
			List<Rect> btns = popupButtons(3);
			if (btns.get(0).contains(mx, my)) {
				Util.getPlatform().openUri(MapIcons.folder().toUri());
			} else if (btns.get(1).contains(mx, my)) {
				MapIcons.reloadCustom();
				openEmblemEditor(this.emblemLand);
			} else if (btns.get(2).contains(mx, my)) {
				closePopup();
			}
			return true;
		}
		return true;
	}

	// ------------------------------------------------------------------ entrada

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.closeNanos >= 0L) return true;
		if (this.popup != Popup.NONE) {
			if (popupClick(mouseX, mouseY, button)) return true;
			return super.mouseClicked(mouseX, mouseY, button);
		}
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
		if (button == 1 && r.contains(mouseX, mouseY) && chromeAlpha() > 0.5f) {
			openContext(mouseX, mouseY);
			return true;
		}
		if (button == 0 && r.contains(mouseX, mouseY)) {
			this.dragging = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (this.dragging && button == 0 && this.closeNanos < 0L && this.popup == Popup.NONE) {
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
		if (this.closeNanos >= 0L || scrollY == 0 || this.popup != Popup.NONE) return true;
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
		if (this.popup != Popup.NONE) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				closePopup();
				return true;
			}
			if (this.popup == Popup.WAYPOINT && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
				saveWaypoint();
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers) || true;
		}
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
		closePopup();
		UiSounds.mapClose();
		startClose(corner(), null);
	}

	private void backToInventory() {
		if (this.closeNanos >= 0L) return;
		closePopup();
		UiSounds.click();
		startClose(IsekaiInventoryScreen.minimapRect(this.width, this.height), () -> {
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

	/** Usado pelos testes automaticos. */
	public void debugOpenNewWaypoint(int x, int z, String name) {
		this.ctxX = x;
		this.ctxZ = z;
		openNewWaypoint();
		if (this.nameBox != null) this.nameBox.setValue(name);
	}

	/** Usado pelos testes automaticos. */
	public void debugSaveWaypoint() {
		saveWaypoint();
	}

	/** Usado pelos testes automaticos. */
	public void debugOpenEmblem(String land) {
		openEmblemEditor(land);
	}
}
