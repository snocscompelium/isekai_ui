package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.anim.Ease;
import com.compelium.isekaiui.client.IsekaiKeys;
import com.compelium.isekaiui.client.ReturnToContainer;
import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.commands.CommandMemory;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.lands.LandsTracker;
import com.compelium.isekaiui.map.MapManager;
import com.compelium.isekaiui.map.MapRenderer;
import com.compelium.isekaiui.map.MapView;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventario estilo "janela de status" de isekai. Usa o mesmo InventoryMenu do vanilla:
 * os slots so mudam de lugar na tela, entao o servidor continua recebendo cliques normais.
 * Todas as janelas podem ser arrastadas pelo titulo ou pelas bordas.
 */
public class IsekaiInventoryScreen extends IsekaiMenuScreen<InventoryMenu> {
	private static final float TO_MAP_SECONDS = 0.14f;
	private static final int COMMANDS_W = CommandList.WIDTH;
	private static boolean vanillaBypass;
	static boolean commandsOpen;

	private final List<BarButton> buttons = new ArrayList<>();
	private final MapView miniView = new MapView();
	private InventoryLayout layout;
	private Panel equip;
	private Panel main;
	private Panel mini;
	private Panel craft;
	private Panel bar;
	private Panel commands;
	private int hoveredButton = -1;
	private boolean miniHovered;
	private CommandList commandList;
	/** Momento (segundos desde que a tela abriu) em que a lista de comandos foi aberta, para animar a entrada. */
	private float commandsAppearAt;

	private record BarButton(String icon, Component tooltip, Runnable action, boolean on) {
	}

	public IsekaiInventoryScreen(Player player) {
		super(player.inventoryMenu, player.getInventory(), Component.translatable("isekai_ui.inventory.title"));
	}

	/** Usado quando a tela e pequena demais: deixa o inventario vanilla abrir uma vez. */
	public static boolean consumeVanillaBypass() {
		boolean b = vanillaBypass;
		vanillaBypass = false;
		return b;
	}

	// ------------------------------------------------------------------ janelas

	@Override
	protected String layoutKey() {
		return "inventory";
	}

	@Override
	protected boolean screenFits() {
		return super.screenFits() && this.menu.slots.size() >= 46;
	}

	@Override
	protected void openVanillaFallback() {
		vanillaBypass = true;
		this.minecraft.setScreen(new InventoryScreen(this.owner));
	}

	@Override
	protected void createPanels() {
		buildButtons();
		this.layout = InventoryLayout.compute(this.width, this.height, this.buttons.size());
		InventoryLayout L = this.layout;
		this.equip = addPanel("equip", 0.05f, -26, 0, true, this.owner.getName(), L.equip);
		this.main = addPanel("main", 0f, 28, 0, true, Component.translatable("isekai_ui.panel.inventory"), L.main);
		this.craft = addPanel("craft", 0.13f, 0, 18, true, Component.translatable("isekai_ui.panel.crafting"), L.craft);
		this.mini = addPanel("minimap", 0.09f, 0, -18, false, regionName(), L.mini.grow(5));
		this.mini.padBottom = 12;
		this.bar = addPanel("bar", 0.17f, 0, 16, false, null, L.bar);
		if (this.commandList == null) this.commandList = new CommandList(this.font, new CommandList.Host() {
			@Override
			public void addInput(net.minecraft.client.gui.components.EditBox box) {
				addWidget(box);
				setFocused(box);
			}

			@Override
			public void removeInput(net.minecraft.client.gui.components.EditBox box) {
				removeWidget(box);
				setFocused(null);
			}

			@Override
			public void commandsChanged() {
				rebuildWidgets();
			}
		});
		else this.commandList.stopTyping(false);
		int h = CommandList.height();
		Rect cmdDef = new Rect(L.bar.cx() - COMMANDS_W / 2, L.bar.y() - 10 - h, COMMANDS_W, h);
		this.commands = addPanel("commands", this.commandsAppearAt, 0, 10, true, Component.translatable("isekai_ui.panel.commands"), cmdDef);
		this.commands.overlay = true;
		this.commands.visible = commandsOpen;
	}

	private void buildButtons() {
		this.buttons.clear();
		IsekaiConfig cfg = IsekaiConfig.get();
		this.buttons.add(new BarButton("map", Component.translatable("isekai_ui.button.map", IsekaiKeys.mapKeyName()), this::openMap, false));
		boolean third = cfg.thirdPersonCamera;
		this.buttons.add(new BarButton(third ? "camera_third" : "camera_first",
				Component.translatable(third ? "isekai_ui.button.camera.third" : "isekai_ui.button.camera.first"),
				this::toggleCamera, third));
		this.buttons.add(new BarButton("list", Component.translatable("isekai_ui.button.commands"), this::toggleCommands, commandsOpen));
		if (ReturnToContainer.available()) {
			this.buttons.add(new BarButton("chest", Component.translatable("isekai_ui.button.back_to_chest"), this::returnToChest, false));
		}
		for (CommandMemory.Entry e : CommandMemory.pinned()) {
			this.buttons.add(new BarButton(e.icon, Component.literal(e.label + "  (/" + e.command + ")"), () -> CommandMemory.run(e), false));
		}
		for (IsekaiConfig.MenuButton b : cfg.menuButtons) {
			if (b == null || b.command == null || b.command.isBlank()) continue;
			String cmd = b.command.strip().startsWith("/") ? b.command.strip().substring(1) : b.command.strip();
			String label = b.label == null || b.label.isBlank() ? "/" + cmd : b.label;
			this.buttons.add(new BarButton(b.icon == null ? "star" : b.icon, Component.literal(label), () -> {
				if (this.minecraft.player != null) this.minecraft.player.connection.sendCommand(cmd);
			}, false));
		}
	}

	@Override
	protected void placeSlots() {
		InventoryLayout L = this.layout;
		if (L == null) return;
		int mdx = this.main.dx(), mdy = this.main.dy();
		placeGrid(9, 27, 9, L.storageX + mdx, L.storageY + mdy);
		placeHexHotbar(36, L.hotbarX + mdx, L.hotbarY + mdy);
		int edx = this.equip.dx(), edy = this.equip.dy();
		for (int a = 0; a < 4; a++) setSlot(5 + a, L.equipX + 4 + edx, L.equipY + a * 22 + 2 + edy);
		setSlot(45, L.equipX + 4 + edx, L.offhandY + 2 + edy);
		int cdx = this.craft.dx(), cdy = this.craft.dy();
		for (int c = 0; c < 4; c++) {
			setSlot(1 + c, L.craftX + (c % 2) * InventoryLayout.SLOT + cdx, L.craftY + (c / 2) * InventoryLayout.SLOT + cdy);
		}
		setSlot(0, L.resultX + cdx, L.resultY + cdy);
	}

	/** Quantos botoes a barra vai ter (fixos + comandos com pin + botoes do arquivo de configuracao). */
	private static int expectedButtonCount() {
		int n = 3 + CommandMemory.pinned().size() + (ReturnToContainer.available() ? 1 : 0);
		for (IsekaiConfig.MenuButton b : IsekaiConfig.get().menuButtons) {
			if (b != null && b.command != null && !b.command.isBlank()) n++;
		}
		return n;
	}

	@Override
	protected Panel slotOwner(int slotIndex) {
		if (slotIndex >= 9 && slotIndex <= 44) return this.main;
		if ((slotIndex >= 5 && slotIndex <= 8) || slotIndex == 45) return this.equip;
		if (slotIndex >= 0 && slotIndex <= 4) return this.craft;
		return null;
	}

	/** Onde o minimapa fica na tela (respeitando o layout salvo) — usado pelo mapa para voltar. */
	public static Rect minimapRect(int width, int height) {
		InventoryLayout L = InventoryLayout.compute(width, height, expectedButtonCount());
		Rect def = L.mini.grow(5);
		Map<String, float[]> saved = IsekaiConfig.get().layouts.get("inventory");
		float[] f = saved == null ? null : saved.get("minimap");
		if (f == null || f.length != 2) return L.mini;
		int top = InventoryLayout.TAB + 1;
		int x = Mth.clamp(Math.round(f[0] * Math.max(0, width - def.w())), 1, Math.max(1, width - def.w() - 1));
		int y = Mth.clamp(Math.round(f[1] * Math.max(0, height - def.h())), top, Math.max(top, height - def.h() - 1));
		return new Rect(x + 5, y + 5, L.mini.w(), L.mini.h());
	}

	// ------------------------------------------------------------------ testes automaticos

	/** Retangulo de um botao da barra (pelo nome do icone), ou null. */
	public Rect debugButtonRect(String icon) {
		for (int i = 0; i < this.buttons.size(); i++) {
			if (this.buttons.get(i).icon().equals(icon)) return buttonRect(this.bar.now, i);
		}
		return null;
	}

	/** Retangulo do alfinete (pin) de uma linha da lista de comandos. */
	public Rect debugCommandPinRect(int row) {
		return this.commandList.debugPinRect(this.commands.now, row);
	}

	/** Usado pelos testes automaticos: anota um comando pelo botao "+" e aperta Enter. */
	public void debugAddCommand(String text) {
		this.commandList.debugType(text);
		this.commandList.stopTyping(true);
	}

	public int debugButtonCount() {
		return this.buttons.size();
	}

	// ------------------------------------------------------------------ acoes

	private void openMap() {
		if (isClosing()) return;
		Rect from = this.mini.now.grow(-5);
		UiSounds.mapOpen();
		Runnable go = () -> {
			if (this.minecraft.player != null) this.minecraft.player.closeContainer();
			this.minecraft.setScreen(IsekaiMapScreen.fromInventory(from));
		};
		startClosing(TO_MAP_SECONDS, this.mini, go);
	}

	private void toggleCamera() {
		IsekaiConfig cfg = IsekaiConfig.get();
		cfg.thirdPersonCamera = !cfg.thirdPersonCamera;
		IsekaiConfig.save();
		UiSounds.toggle();
		buildButtons();
	}

	private void toggleCommands() {
		commandsOpen = !commandsOpen;
		this.commandsAppearAt = time();
		UiSounds.toggle();
		this.rebuildWidgets();
	}

	/** Fecha o inventario e da um clique direito no bau que ainda esta na mira (o mesmo que fazer a mao). */
	private void returnToChest() {
		if (isClosing()) return;
		startClosing(CLOSE_SECONDS, null, () -> {
			if (this.minecraft.player != null) this.minecraft.player.closeContainer();
			ReturnToContainer.scheduleReopen();
		});
	}

	// ------------------------------------------------------------------ desenho

	@Override
	protected void drawPanel(GuiGraphics g, Panel p, float partialTick, int mouseX, int mouseY) {
		InventoryLayout L = this.layout;
		float a = p.alpha;
		if (p == this.equip) {
			int dx = p.dx(), dy = p.dy();
			for (int i = 0; i < 4; i++) {
				Draw.sprite(g, Theme.SLOT_HEX, L.equipX + dx, L.equipY + i * 22 + dy, InventoryLayout.HEX_W, InventoryLayout.HEX_H, a);
			}
			Panels.ornament(g, p.now.x() + 4, L.offhandY - 9 + dy, p.now.w() - 8, a * 0.9f);
			Draw.sprite(g, Theme.SLOT_HEX, L.equipX + dx, L.offhandY + dy, InventoryLayout.HEX_W, InventoryLayout.HEX_H, a);
		} else if (p == this.main) {
			int dx = p.dx(), dy = p.dy();
			drawGrid(g, L.storageX + dx, L.storageY + dy, 27, 9, a);
			Panels.ornament(g, p.now.x() + 24, L.hotbarY - 7 + dy, p.now.w() - 48, a * 0.9f);
			drawHexHotbar(g, L.hotbarX + dx, L.hotbarY + dy, a);
		} else if (p == this.craft) {
			int dx = p.dx(), dy = p.dy();
			drawGrid(g, L.craftX + dx, L.craftY + dy, 4, 2, a);
			Draw.sprite(g, Theme.CRAFT_ARROW, L.craftX + 37 + dx, L.craftY + 13 + dy, 12, 9, a);
			Draw.sprite(g, Theme.SLOT_RESULT, L.resultX - 5 + dx, L.resultY - 5 + dy, 26, 26, a);
			if (!this.menu.slots.get(0).getItem().isEmpty()) {
				float glow = 0.45f + 0.35f * Mth.sin(time() * 4f);
				Draw.sprite(g, Theme.SLOT_RESULT_GLOW, L.resultX - 5 + dx, L.resultY - 5 + dy, 26, 26, a * glow);
			}
		} else if (p == this.mini) {
			drawMinimap(g, p, partialTick, mouseX, mouseY);
		} else if (p == this.bar) {
			drawBar(g, p, mouseX, mouseY);
		} else if (p == this.commands) {
			this.commandList.draw(g, p.now, p.alpha, mouseX, mouseY, partialTick, !isClosing() && !isDraggingPanel());
		}
	}

	private void drawMinimap(GuiGraphics g, Panel p, float partialTick, int mouseX, int mouseY) {
		float a = p.alpha;
		Rect map = p.now.grow(-5);
		Player pl = this.owner;
		this.miniView.cx = Mth.lerp(partialTick, pl.xo, pl.getX());
		this.miniView.cz = Mth.lerp(partialTick, pl.zo, pl.getZ());
		this.miniView.zoom = IsekaiConfig.get().map.minimapZoom;
		Draw.fill(g, map.x() - 2, map.y() + 3, map.right() + 6, map.bottom() + 9, Draw.alpha(Theme.SHADOW, a));
		MapRenderer.draw(g, MapManager.current(), this.miniView, map, a, partialTick, true);
		Draw.sprite(g, Theme.MAP_FRAME, p.now.x(), p.now.y(), p.now.w(), p.now.h(), a);
		boolean hover = !isClosing() && !isDraggingPanel() && p.now.contains(mouseX, mouseY) && overlayAt(mouseX, mouseY) == null;
		if (hover && !this.miniHovered) UiSounds.hover();
		this.miniHovered = hover;
		if (hover) {
			float pulse = 0.6f + 0.4f * Mth.sin(time() * 5f);
			Draw.sprite(g, Theme.MAP_FRAME_GLOW, p.now.x(), p.now.y(), p.now.w(), p.now.h(), a * pulse);
		}
		Component region = regionName();
		p.title = region;
		Panels.tab(g, this.font, p.now.x() + 4, p.now.y() - InventoryLayout.TAB, p.now.w() - 30, region, a);
		SkyDial.draw(g, this.font, p.now.right() - 9, p.now.y() + 1, a);
		if (com.compelium.isekaiui.client.Instruments.showCoordinates()) {
			BlockPos bp = pl.blockPosition();
			Component coords = Component.literal("X " + bp.getX() + "   Y " + bp.getY() + "   Z " + bp.getZ());
			Draw.textCentered(g, this.font, coords, p.now.cx(), p.now.bottom() + 3, Draw.alpha(Theme.TEXT_DIM, a), true);
		}
	}

	private Rect buttonRect(Rect barRect, int i) {
		return new Rect(barRect.x() + i * InventoryLayout.BUTTON_STEP, barRect.y() + 1, InventoryLayout.HEX_W, InventoryLayout.HEX_H);
	}

	private void drawBar(GuiGraphics g, Panel p, int mouseX, int mouseY) {
		int before = this.hoveredButton;
		this.hoveredButton = -1;
		boolean canHover = !isClosing() && !isDraggingPanel() && overlayAt(mouseX, mouseY) == null;
		for (int i = 0; i < this.buttons.size(); i++) {
			float pi = Ease.clamp01(p.progress * 1.4f - i * 0.12f);
			Rect b = buttonRect(p.now, i).offset(0, slide(pi, 10));
			boolean hover = canHover && buttonRect(p.now, i).contains(mouseX, mouseY);
			if (hover) this.hoveredButton = i;
			BarButton bb = this.buttons.get(i);
			Panels.hexButton(g, b, bb.icon(), hover, bb.on(), fade(pi) * (p.alpha > 0 ? 1f : 0f));
		}
		if (this.hoveredButton >= 0 && this.hoveredButton != before) UiSounds.hover();
	}

	@Override
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (isClosing() || isDraggingPanel()) return;
		if (this.hoveredButton >= 0 && this.hoveredButton < this.buttons.size()) {
			this.setTooltipForNextRenderPass(this.buttons.get(this.hoveredButton).tooltip());
		} else if (this.miniHovered) {
			this.setTooltipForNextRenderPass(Component.translatable("isekai_ui.map.open_hint", IsekaiKeys.mapKeyName()));
		} else if (this.commands != null && this.commands.visible && this.commandList.tooltip() != null
				&& this.commands.grabArea().contains(mouseX, mouseY)) {
			this.setTooltipForNextRenderPass(this.commandList.tooltip());
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
	protected boolean onPanelClick(Panel p, double mouseX, double mouseY, int button) {
		if (p == this.mini && button == 0) {
			openMap();
			return true;
		}
		if (p == this.bar && button == 0) {
			for (int i = 0; i < this.buttons.size(); i++) {
				if (buttonRect(p.now, i).contains(mouseX, mouseY)) {
					UiSounds.click();
					this.buttons.get(i).action().run();
					return true;
				}
			}
			return false;
		}
		if (p == this.commands) return this.commandList.click(p.now, mouseX, mouseY, button);
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.commands != null && this.commands.visible && this.commands.grabArea().contains(mouseX, mouseY)) {
			return this.commandList.scroll(scrollY);
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (isClosing()) return true;
		if (this.commandList != null && this.commandList.isTyping()) return this.commandList.keyPressed(keyCode, scanCode, modifiers);
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
}
