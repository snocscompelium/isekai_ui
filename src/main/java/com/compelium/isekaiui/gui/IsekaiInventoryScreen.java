package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.anim.Ease;
import com.compelium.isekaiui.camera.PoseState;
import com.compelium.isekaiui.client.IsekaiKeys;
import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.lands.LandsTracker;
import com.compelium.isekaiui.map.MapManager;
import com.compelium.isekaiui.map.MapRenderer;
import com.compelium.isekaiui.map.MapView;
import com.compelium.isekaiui.mixin.SlotAccessor;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventario estilo "janela de status" de isekai. Usa o mesmo InventoryMenu do vanilla:
 * os slots so mudam de lugar na tela, entao o servidor continua recebendo cliques normais.
 */
public class IsekaiInventoryScreen extends AbstractContainerScreen<InventoryMenu> {
	private static final float GROUP_SECONDS = 0.34f;
	private static final float CLOSE_SECONDS = 0.18f;
	private static final float TO_MAP_SECONDS = 0.14f;
	private static boolean vanillaBypass;

	private final Player owner;
	private final int[] originalX;
	private final int[] originalY;
	private final List<BarButton> buttons = new ArrayList<>();
	private final MapView miniView = new MapView();

	private InventoryLayout layout;
	private long openNanos;
	private long closeNanos = -1L;
	private boolean closingToMap;
	private Runnable afterClose;
	private int hoveredButton = -1;
	private boolean miniHovered;
	private boolean restored;

	private record BarButton(String icon, Component tooltip, Runnable action, boolean on) {
	}

	public IsekaiInventoryScreen(Player player) {
		super(player.inventoryMenu, player.getInventory(), Component.translatable("isekai_ui.inventory.title"));
		this.owner = player;
		int n = this.menu.slots.size();
		this.originalX = new int[n];
		this.originalY = new int[n];
		for (int i = 0; i < n; i++) {
			Slot s = this.menu.slots.get(i);
			this.originalX[i] = s.x;
			this.originalY[i] = s.y;
		}
	}

	/** Usado quando a tela e pequena demais: deixa o inventario vanilla abrir uma vez. */
	public static boolean consumeVanillaBypass() {
		boolean b = vanillaBypass;
		vanillaBypass = false;
		return b;
	}

	public static int buttonCount() {
		return 2 + IsekaiConfig.get().menuButtons.size();
	}

	public boolean wantsCinematicCamera() {
		return IsekaiConfig.get().thirdPersonCamera && this.closeNanos < 0L;
	}

	// ------------------------------------------------------------------ ciclo de vida

	@Override
	protected void init() {
		if (!InventoryLayout.fits(this.width, this.height) || this.menu.slots.size() < 46) {
			vanillaBypass = true;
			this.minecraft.setScreen(new InventoryScreen(this.owner));
			return;
		}
		this.imageWidth = this.width;
		this.imageHeight = this.height;
		super.init();
		this.leftPos = 0;
		this.topPos = 0;
		if (this.openNanos == 0L) {
			this.openNanos = System.nanoTime();
			UiSounds.windowOpen();
		}
		buildButtons();
		this.layout = InventoryLayout.compute(this.width, this.height, this.buttons.size());
		placeSlots();
	}

	private void buildButtons() {
		this.buttons.clear();
		IsekaiConfig cfg = IsekaiConfig.get();
		this.buttons.add(new BarButton("map",
				Component.translatable("isekai_ui.button.map", IsekaiKeys.mapKeyName()), this::openMap, false));
		boolean third = cfg.thirdPersonCamera;
		this.buttons.add(new BarButton(third ? "camera_third" : "camera_first",
				Component.translatable(third ? "isekai_ui.button.camera.third" : "isekai_ui.button.camera.first"),
				this::toggleCamera, third));
		for (IsekaiConfig.MenuButton b : cfg.menuButtons) {
			if (b == null || b.command == null || b.command.isBlank()) continue;
			String cmd = b.command.strip();
			String label = b.label == null || b.label.isBlank() ? "/" + cmd : b.label;
			this.buttons.add(new BarButton(b.icon == null ? "star" : b.icon, Component.literal(label), () -> runCommand(cmd), false));
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (this.closeNanos >= 0L && secondsSince(this.closeNanos) > closeDuration() + 0.25f) {
			finishClose();
		}
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
		startClosing(false, null);
		UiSounds.windowClose();
	}

	private void startClosing(boolean toMap, Runnable after) {
		this.closeNanos = System.nanoTime();
		this.closingToMap = toMap;
		this.afterClose = after;
	}

	private float closeDuration() {
		return this.closingToMap ? TO_MAP_SECONDS : CLOSE_SECONDS;
	}

	private void finishClose() {
		Runnable after = this.afterClose;
		this.afterClose = null;
		if (this.minecraft == null || this.minecraft.screen != this) return;
		if (after != null) after.run();
		else super.onClose();
	}

	// ------------------------------------------------------------------ acoes

	private void openMap() {
		if (this.closeNanos >= 0L) return;
		Rect from = this.layout.mini;
		UiSounds.mapOpen();
		Runnable go = () -> {
			if (this.minecraft.player != null) this.minecraft.player.closeContainer();
			this.minecraft.setScreen(IsekaiMapScreen.fromInventory(from));
		};
		if (!IsekaiConfig.get().animations) {
			go.run();
			return;
		}
		startClosing(true, go);
	}

	private void toggleCamera() {
		IsekaiConfig cfg = IsekaiConfig.get();
		cfg.thirdPersonCamera = !cfg.thirdPersonCamera;
		IsekaiConfig.save();
		UiSounds.toggle();
		buildButtons();
	}

	private void runCommand(String command) {
		if (this.minecraft.player == null) return;
		String cmd = command.startsWith("/") ? command.substring(1) : command;
		this.minecraft.player.connection.sendCommand(cmd);
	}

	// ------------------------------------------------------------------ animacao

	private static float secondsSince(long nanos) {
		return (System.nanoTime() - nanos) / 1.0e9f;
	}

	/** 0 = fora da tela, 1 = no lugar. */
	private float group(float delay, boolean isMini) {
		if (!IsekaiConfig.get().animations) return this.closeNanos >= 0L ? 0f : 1f;
		float p = Ease.window(secondsSince(this.openNanos), delay, GROUP_SECONDS);
		if (this.closeNanos >= 0L) {
			if (this.closingToMap && isMini) return p;
			float c = 1f - Ease.window(secondsSince(this.closeNanos), 0f, closeDuration());
			p = Math.min(p, c);
		}
		return p;
	}

	private static int slide(float p, int distance) {
		return Math.round((1f - Ease.outBack(p)) * distance);
	}

	private static float fade(float p) {
		return Ease.outCubic(Math.min(1f, p * 1.7f));
	}

	private float pEquip() {
		return group(0.05f, false);
	}

	private float pMain() {
		return group(0f, false);
	}

	private float pMini() {
		return group(0.09f, true);
	}

	private float pCraft() {
		return group(0.13f, false);
	}

	private float pBar() {
		return group(0.17f, false);
	}

	private Rect equipNow() {
		return this.layout.equip.offset(slide(pEquip(), -26), 0);
	}

	private Rect mainNow() {
		return this.layout.main.offset(slide(pMain(), 28), 0);
	}

	private Rect miniNow() {
		return this.layout.mini.offset(0, slide(pMini(), -18));
	}

	private Rect craftNow() {
		return this.layout.craft.offset(0, slide(pCraft(), 18));
	}

	private Rect barNow() {
		return this.layout.bar.offset(0, slide(pBar(), 16));
	}

	// ------------------------------------------------------------------ slots

	private void placeSlots() {
		if (this.layout == null) return;
		InventoryLayout L = this.layout;
		int mdx = mainNow().x() - L.main.x();
		int edx = equipNow().x() - L.equip.x();
		int cdy = craftNow().y() - L.craft.y();
		for (int i = 9; i < 36; i++) {
			int idx = i - 9;
			set(i, L.storageX + (idx % 9) * InventoryLayout.SLOT + mdx, L.storageY + (idx / 9) * InventoryLayout.SLOT);
		}
		for (int k = 0; k < 9; k++) {
			set(36 + k, L.hotbarX + k * 18 + 4 + mdx, L.hotbarY + ((k & 1) == 1 ? 10 : 0) + 2);
		}
		for (int a = 0; a < 4; a++) {
			set(5 + a, L.equipX + 4 + edx, L.equipY + a * 22 + 2);
		}
		set(45, L.equipX + 4 + edx, L.offhandY + 2);
		for (int c = 0; c < 4; c++) {
			set(1 + c, L.craftX + (c % 2) * InventoryLayout.SLOT, L.craftY + (c / 2) * InventoryLayout.SLOT + cdy);
		}
		set(0, L.resultX, L.resultY + cdy);
		this.restored = false;
	}

	private void set(int index, int x, int y) {
		Slot s = this.menu.slots.get(index);
		((SlotAccessor) s).isekai$setX(x);
		((SlotAccessor) s).isekai$setY(y);
	}

	private void restoreSlots() {
		if (this.restored) return;
		int n = Math.min(this.originalX.length, this.menu.slots.size());
		for (int i = 0; i < n; i++) set(i, this.originalX[i], this.originalY[i]);
		this.restored = true;
	}

	// ------------------------------------------------------------------ desenho

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (this.layout == null) return;
		if (this.closeNanos >= 0L && secondsSince(this.closeNanos) >= closeDuration()) {
			finishClose();
			return;
		}
		placeSlots();
		updateHover(mouseX, mouseY);
		super.render(g, mouseX, mouseY, partialTick);
		if (this.closeNanos < 0L) {
			if (this.hoveredButton >= 0) {
				this.setTooltipForNextRenderPass(this.buttons.get(this.hoveredButton).tooltip());
			} else if (this.miniHovered) {
				this.setTooltipForNextRenderPass(Component.translatable("isekai_ui.map.open_hint", IsekaiKeys.mapKeyName()));
			}
			this.renderTooltip(g, mouseX, mouseY);
		}
	}

	private void updateHover(int mx, int my) {
		int before = this.hoveredButton;
		boolean miniBefore = this.miniHovered;
		this.hoveredButton = -1;
		this.miniHovered = false;
		if (this.closeNanos >= 0L) return;
		Rect bar = barNow();
		for (int i = 0; i < this.buttons.size(); i++) {
			if (buttonRect(bar, i).contains(mx, my)) this.hoveredButton = i;
		}
		this.miniHovered = miniNow().grow(4).contains(mx, my);
		if ((this.hoveredButton >= 0 && this.hoveredButton != before) || (this.miniHovered && !miniBefore)) {
			UiSounds.hover();
		}
	}

	private static Rect buttonRect(Rect bar, int i) {
		return new Rect(bar.x() + i * InventoryLayout.BUTTON_STEP, bar.y() + 1, InventoryLayout.HEX_W, InventoryLayout.HEX_H);
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
		InventoryLayout L = this.layout;
		float time = secondsSince(this.openNanos);

		// ---- equipamento (com o nome do personagem)
		float pe = pEquip();
		float ae = fade(pe);
		Rect er = equipNow();
		int edx = er.x() - L.equip.x();
		Panels.panel(g, this.font, er, ae, pe, this.owner.getName(), time);
		for (int a = 0; a < 4; a++) {
			Draw.sprite(g, Theme.SLOT_HEX, L.equipX + edx, L.equipY + a * 22, InventoryLayout.HEX_W, InventoryLayout.HEX_H, ae);
		}
		Panels.ornament(g, er.x() + 4, L.offhandY - 9, er.w() - 8, ae * 0.9f);
		Draw.sprite(g, Theme.SLOT_HEX, L.equipX + edx, L.offhandY, InventoryLayout.HEX_W, InventoryLayout.HEX_H, ae);

		// ---- bolsa + atalhos
		float pm = pMain();
		float am = fade(pm);
		Rect mr = mainNow();
		int mdx = mr.x() - L.main.x();
		Panels.panel(g, this.font, mr, am, pm, Component.translatable("isekai_ui.panel.inventory"), time);
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 9; c++) {
				Draw.sprite(g, Theme.SLOT, L.storageX - 1 + c * InventoryLayout.SLOT + mdx, L.storageY - 1 + r * InventoryLayout.SLOT, 18, 18, am);
			}
		}
		Panels.ornament(g, mr.x() + 24, L.hotbarY - 7, mr.w() - 48, am * 0.9f);
		for (int k = 0; k < 9; k++) {
			Draw.sprite(g, Theme.SLOT_HEX, L.hotbarX + k * 18 + mdx, L.hotbarY + ((k & 1) == 1 ? 10 : 0),
					InventoryLayout.HEX_W, InventoryLayout.HEX_H, am);
		}
		int selected = this.owner.getInventory().selected;
		if (selected >= 0 && selected < 9) {
			int hx = L.hotbarX + selected * 18 + mdx;
			int hy = L.hotbarY + ((selected & 1) == 1 ? 10 : 0);
			float pulse = 0.55f + 0.25f * Mth.sin(time * 3.2f);
			Draw.sprite(g, Theme.SLOT_HEX_SELECTED, hx, hy, InventoryLayout.HEX_W, InventoryLayout.HEX_H, am * pulse);
		}

		// ---- sintese (crafting 2x2)
		float pc = pCraft();
		float ac = fade(pc);
		Rect cr = craftNow();
		int cdy = cr.y() - L.craft.y();
		Panels.panel(g, this.font, cr, ac, pc, Component.translatable("isekai_ui.panel.crafting"), time);
		for (int c = 0; c < 4; c++) {
			Draw.sprite(g, Theme.SLOT, L.craftX - 1 + (c % 2) * InventoryLayout.SLOT, L.craftY - 1 + (c / 2) * InventoryLayout.SLOT + cdy, 18, 18, ac);
		}
		Draw.sprite(g, Theme.CRAFT_ARROW, L.craftX + 37, L.craftY + 13 + cdy, 12, 9, ac);
		boolean hasResult = !this.menu.slots.get(0).getItem().isEmpty();
		Draw.sprite(g, Theme.SLOT_RESULT, L.resultX - 5, L.resultY - 5 + cdy, 26, 26, ac);
		if (hasResult) {
			float glow = 0.45f + 0.35f * Mth.sin(time * 4f);
			Draw.sprite(g, Theme.SLOT_RESULT_GLOW, L.resultX - 5, L.resultY - 5 + cdy, 26, 26, ac * glow);
		}

		// ---- minimapa
		float pn = pMini();
		float an = fade(pn);
		Rect nr = miniNow();
		Player p = this.owner;
		this.miniView.cx = Mth.lerp(partialTick, p.xo, p.getX());
		this.miniView.cz = Mth.lerp(partialTick, p.zo, p.getZ());
		this.miniView.zoom = IsekaiConfig.get().map.minimapZoom;
		Draw.fill(g, nr.x() - 2, nr.y() + 3, nr.right() + 6, nr.bottom() + 9, Draw.alpha(Theme.SHADOW, an));
		MapRenderer.draw(g, MapManager.current(), this.miniView, nr, an, partialTick, true);
		Draw.sprite(g, Theme.MAP_FRAME, nr.x() - 5, nr.y() - 5, nr.w() + 10, nr.h() + 10, an);
		if (this.miniHovered) {
			float pulse = 0.6f + 0.4f * Mth.sin(time * 5f);
			Draw.sprite(g, Theme.MAP_FRAME_GLOW, nr.x() - 5, nr.y() - 5, nr.w() + 10, nr.h() + 10, an * pulse);
		}
		Panels.tab(g, this.font, nr.x() - 1, nr.y() - 5 - InventoryLayout.TAB, nr.w() + 2, regionName(), an);
		BlockPos bp = p.blockPosition();
		Component coords = Component.literal("X " + bp.getX() + "   Y " + bp.getY() + "   Z " + bp.getZ());
		Draw.textCentered(g, this.font, coords, nr.cx(), nr.bottom() + 8, Draw.alpha(Theme.TEXT_DIM, an), true);

		// ---- barra hexagonal
		float pb = pBar();
		Rect br = barNow();
		for (int i = 0; i < this.buttons.size(); i++) {
			float pi = Ease.clamp01(pb * 1.4f - i * 0.12f);
			float ai = fade(pi);
			Rect b = buttonRect(br, i).offset(0, slide(pi, 10));
			BarButton bb = this.buttons.get(i);
			boolean hover = i == this.hoveredButton;
			Panels.hexButton(g, b, bb.icon(), hover, bb.on(), ai);
		}
	}

	private Component regionName() {
		String land = LandsTracker.currentLand();
		if (land != null) return Component.literal(land);
		if (this.minecraft != null && this.minecraft.level != null) {
			return this.minecraft.level.getBiome(this.owner.blockPosition()).unwrapKey()
					.map(k -> (Component) Component.translatable(Util.makeDescriptionId("biome", k.location())))
					.orElse(Component.translatable("isekai_ui.map.unknown_region"));
		}
		return Component.translatable("isekai_ui.map.unknown_region");
	}

	// ------------------------------------------------------------------ entrada

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.closeNanos >= 0L) return true;
		if (button == 0 && miniNow().grow(4).contains(mouseX, mouseY)) {
			openMap();
			return true;
		}
		Rect bar = barNow();
		for (int i = 0; i < this.buttons.size(); i++) {
			if (buttonRect(bar, i).contains(mouseX, mouseY)) {
				UiSounds.click();
				this.buttons.get(i).action().run();
				return true;
			}
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if (this.hoveredSlot != null) PoseState.tap();
		return handled;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.closeNanos >= 0L) return true;
		if (IsekaiKeys.isMapKey(keyCode, scanCode)) {
			openMap();
			return true;
		}
		if (this.minecraft != null && this.minecraft.options.keyTogglePerspective.matches(keyCode, scanCode)) {
			toggleCamera();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
		if (this.layout == null) return false;
		InventoryLayout L = this.layout;
		boolean inside = L.main.grow(2).contains(mouseX, mouseY)
				|| L.equip.grow(2).contains(mouseX, mouseY)
				|| L.craft.grow(2).contains(mouseX, mouseY)
				|| L.mini.grow(6).contains(mouseX, mouseY)
				|| L.bar.grow(2).contains(mouseX, mouseY)
				|| new Rect(L.main.x(), L.main.y() - InventoryLayout.TAB, L.main.w(), InventoryLayout.TAB).contains(mouseX, mouseY);
		return !inside;
	}
}
