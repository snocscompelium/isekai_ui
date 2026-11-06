package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.commands.CommandMemory;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.creative.PersonalChest;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeInventoryListener;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Inventario do modo criativo no estilo isekai: catalogo de itens (abas + busca), equipamento,
 * inventario, bau pessoal (copias de itens salvas no seu computador) e comandos rapidos anotados.
 *
 * Funciona igual ao criativo do jogo: as mudancas no inventario sao feitas aqui e cada slot alterado e
 * enviado ao servidor pelo mesmo pacote que o inventario criativo vanilla usa.
 */
public class IsekaiCreativeScreen extends IsekaiMenuScreen<InventoryMenu> {
	private static final int COLS = 8;
	private static final int ROWS = 5;
	private static final int TAB_S = 17;
	private static final int CAT_W = 9 + COLS * InventoryLayout.SLOT + 4 + 4 + 9;
	private static final int CAT_H = 8 + 2 * TAB_S + 4 + 12 + 4 + ROWS * InventoryLayout.SLOT + 8;
	private static final int BAG_W = IsekaiStationScreen.BAG_W;
	private static final int BAG_H = IsekaiStationScreen.BAG_H;
	private static final int CHEST_H = 8 + 3 * InventoryLayout.SLOT + 8;
	private static final int EQUIP_W = 36;
	private static final int EQUIP_H = 7 + 4 * 22 - 2 + 12 + InventoryLayout.HEX_H + 7;
	private static final int GROUP_W = EQUIP_W + 8 + CAT_W + 8 + BAG_W;

	private static boolean vanillaBypass;
	/** null = busca (todos os itens). */
	private static CreativeModeTab selectedTab;
	private static String lastSearch = "";
	private static int personalPage;

	private final LocalPlayer player;
	private final List<BarButton> buttons = new ArrayList<>();
	private CreativeInventoryListener listener;
	private List<CreativeModeTab> tabs = List.of();
	private List<ItemStack> shown = List.of();
	private int scrollRow;
	private EditBox search;
	private CommandList commandList;
	private float commandsAppearAt;

	private Panel equip;
	private Panel catalog;
	private Panel personal;
	private Panel bag;
	private Panel bar;
	private Panel commands;

	private int hoveredItem = -1;
	private int hoveredPersonal = -1;
	private int hoveredTab = -2;
	private int hoveredButton = -1;
	/** Tecla ja tratada: ignora o caractere que o sistema manda logo depois (ex.: "1" nao vira busca). */
	private boolean ignoreTextInput;

	private record BarButton(String icon, Component tooltip, Runnable action, boolean on) {
	}

	public IsekaiCreativeScreen(LocalPlayer player) {
		super(player.inventoryMenu, player.getInventory(), Component.translatable("isekai_ui.creative.title"));
		this.player = player;
	}

	public static boolean consumeVanillaBypass() {
		boolean b = vanillaBypass;
		vanillaBypass = false;
		return b;
	}

	// ------------------------------------------------------------------ janelas

	@Override
	protected String layoutKey() {
		return "creative";
	}

	@Override
	protected boolean screenFits() {
		return this.width >= GROUP_W + 16 && this.height >= 222 && this.menu.slots.size() >= 46;
	}

	@Override
	protected void openVanillaFallback() {
		openClassic();
	}

	private void openClassic() {
		vanillaBypass = true;
		boolean op = this.minecraft.options.operatorItemsTab().get() && this.player.canUseGameMasterBlocks();
		this.minecraft.setScreen(new CreativeModeInventoryScreen(this.player, this.player.connection.enabledFeatures(), op));
	}

	@Override
	protected void init() {
		boolean op = this.minecraft.options.operatorItemsTab().get() && this.player.canUseGameMasterBlocks();
		CreativeModeTabs.tryRebuildTabContents(this.player.connection.enabledFeatures(), op, this.player.level().registryAccess());
		List<CreativeModeTab> list = new ArrayList<>();
		for (CreativeModeTab t : CreativeModeTabs.tabs()) {
			if (t.getType() == CreativeModeTab.Type.CATEGORY && t.shouldDisplay()) list.add(t);
		}
		this.tabs = list;
		if (selectedTab != null && !this.tabs.contains(selectedTab)) selectedTab = null;
		super.init();
		if (this.minecraft.screen != this) return; // tela pequena: abriu o criativo vanilla no lugar
		if (this.listener == null) {
			this.listener = new CreativeInventoryListener(this.minecraft);
			this.menu.addSlotListener(this.listener);
		}
		refresh();
	}

	@Override
	public void removed() {
		ItemStack carried = this.menu.getCarried();
		if (!carried.isEmpty() && this.minecraft.gameMode != null) {
			// devolve o que estava na mao para o inventario (ou joga no chao se estiver cheio)
			if (!this.player.getInventory().add(carried.copy())) this.minecraft.gameMode.handleCreativeModeItemDrop(carried.copy());
			this.menu.setCarried(ItemStack.EMPTY);
			this.menu.broadcastChanges();
		}
		if (this.listener != null) {
			this.menu.removeSlotListener(this.listener);
			this.listener = null;
		}
		super.removed();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (this.minecraft.gameMode != null && !this.minecraft.gameMode.hasInfiniteItems()) {
			this.minecraft.setScreen(new InventoryScreen(this.player));
		}
	}

	@Override
	protected void createPanels() {
		buildButtons();
		int W = this.width;
		int H = this.height;
		int tab = InventoryLayout.TAB;
		int x0 = Math.max(8, (W - GROUP_W) / 2);
		int rightH = tab + CHEST_H + 8 + tab + BAG_H;
		int top = Math.max(tab + 4, (H - rightH) / 2 + tab);
		Rect equipR = new Rect(x0, (H - EQUIP_H) / 2 + 4, EQUIP_W, EQUIP_H);
		int barW = this.buttons.size() * InventoryLayout.BUTTON_STEP;
		int catY = Math.max(tab + 4, (H - (tab + CAT_H + 6 + InventoryLayout.HEX_H)) / 2 + tab);
		Rect catR = new Rect(x0 + EQUIP_W + 8, catY, CAT_W, CAT_H);
		Rect barR = new Rect(catR.cx() - barW / 2, catR.bottom() + 6, barW, InventoryLayout.HEX_H);
		Rect chestR = new Rect(catR.right() + 8, top, BAG_W, CHEST_H);
		Rect bagR = new Rect(catR.right() + 8, chestR.bottom() + 8 + tab, BAG_W, BAG_H);

		this.equip = addPanel("equip", 0.05f, -26, 0, true, this.player.getName(), equipR);
		this.catalog = addPanel("catalog", 0f, 0, -20, true, catalogTitle(), catR);
		this.personal = addPanel("personal", 0.08f, 24, 0, true, personalTitle(), chestR);
		this.bag = addPanel("bag", 0.12f, 0, 22, true, Component.translatable("isekai_ui.panel.inventory"), bagR);
		this.bar = addPanel("bar", 0.17f, 0, 16, false, null, barR);

		if (this.commandList == null) this.commandList = new CommandList(this.font, new CommandList.Host() {
			@Override
			public void addInput(EditBox box) {
				addWidget(box);
				setFocused(box);
			}

			@Override
			public void removeInput(EditBox box) {
				removeWidget(box);
				setFocused(null);
			}

			@Override
			public void commandsChanged() {
				rebuildWidgets();
			}
		});
		else this.commandList.stopTyping(false);
		int ch = CommandList.height();
		Rect cmdR = new Rect(barR.cx() - CommandList.WIDTH / 2, barR.y() - 8 - ch, CommandList.WIDTH, ch);
		this.commands = addPanel("commands", this.commandsAppearAt, 0, 10, true, Component.translatable("isekai_ui.panel.commands"), cmdR);
		this.commands.overlay = true;
		this.commands.visible = IsekaiInventoryScreen.commandsOpen;

		addSideButton(this.personal, "prev", Component.translatable("isekai_ui.creative.prev_page"), () -> changePage(-1), () -> false);
		addSideButton(this.personal, "next", Component.translatable("isekai_ui.creative.next_page"), () -> changePage(1), () -> false);
		addSideButton(this.bag, "trash", Component.translatable("isekai_ui.creative.trash"), this::trashCarried, () -> false);

		String old = this.search == null ? lastSearch : this.search.getValue();
		this.search = new EditBox(this.font, 0, 0, CAT_W - 18 - 14, 10, Component.translatable("isekai_ui.creative.search"));
		this.search.setBordered(false);
		this.search.setMaxLength(50);
		this.search.setTextColor(Theme.TEXT);
		this.search.setHint(Component.translatable("isekai_ui.creative.search_hint"));
		this.search.setValue(old);
		this.search.setResponder(text -> {
			lastSearch = text;
			if (!text.isEmpty()) selectedTab = null;
			this.scrollRow = 0;
			refresh();
		});
		addWidget(this.search);
	}

	private void buildButtons() {
		this.buttons.clear();
		boolean third = IsekaiConfig.get().thirdPersonCamera;
		this.buttons.add(new BarButton("catalog", Component.translatable("isekai_ui.creative.classic"), this::openClassic, false));
		this.buttons.add(new BarButton(third ? "camera_third" : "camera_first",
				Component.translatable(third ? "isekai_ui.button.camera.third" : "isekai_ui.button.camera.first"), this::toggleCamera, third));
		this.buttons.add(new BarButton("list", Component.translatable("isekai_ui.button.commands"), this::toggleCommands, IsekaiInventoryScreen.commandsOpen));
		for (CommandMemory.Entry e : CommandMemory.pinned()) {
			this.buttons.add(new BarButton(e.icon, Component.literal(e.label + "  (/" + e.command + ")"), () -> CommandMemory.run(e), false));
		}
	}

	private Component catalogTitle() {
		return selectedTab == null ? Component.translatable("isekai_ui.creative.search") : selectedTab.getDisplayName();
	}

	private Component personalTitle() {
		return Component.translatable("isekai_ui.creative.personal", personalPage + 1, Math.max(PersonalChest.usedPages(), personalPage + 1));
	}

	private void toggleCamera() {
		IsekaiConfig cfg = IsekaiConfig.get();
		cfg.thirdPersonCamera = !cfg.thirdPersonCamera;
		IsekaiConfig.save();
		UiSounds.toggle();
		buildButtons();
	}

	private void toggleCommands() {
		IsekaiInventoryScreen.commandsOpen = !IsekaiInventoryScreen.commandsOpen;
		this.commandsAppearAt = time();
		UiSounds.toggle();
		rebuildWidgets();
	}

	private void changePage(int delta) {
		personalPage = Mth.clamp(personalPage + delta, 0, PersonalChest.PAGES - 1);
		this.personal.title = personalTitle();
		UiSounds.hover();
	}

	private void trashCarried() {
		if (this.menu.getCarried().isEmpty()) return;
		this.menu.setCarried(ItemStack.EMPTY);
		UiSounds.toggle();
	}

	/** Recalcula os itens do catalogo (aba escolhida ou busca). */
	private void refresh() {
		List<ItemStack> out = new ArrayList<>();
		if (selectedTab != null) {
			out.addAll(selectedTab.getDisplayItems());
		} else {
			String q = this.search == null ? lastSearch : this.search.getValue();
			q = q.strip().toLowerCase(Locale.ROOT);
			for (ItemStack s : CreativeModeTabs.searchTab().getDisplayItems()) {
				if (q.isEmpty() || matches(s, q)) out.add(s);
			}
		}
		this.shown = out;
		this.scrollRow = Mth.clamp(this.scrollRow, 0, maxScroll());
		if (this.catalog != null) this.catalog.title = catalogTitle();
	}

	private static boolean matches(ItemStack s, String q) {
		if (s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
		return BuiltInRegistries.ITEM.getKey(s.getItem()).toString().contains(q);
	}

	private int maxScroll() {
		int rows = (this.shown.size() + COLS - 1) / COLS;
		return Math.max(0, rows - ROWS);
	}

	// ------------------------------------------------------------------ posicoes

	private static int gridX(Rect c) {
		return c.x() + 10;
	}

	private static int gridY(Rect c) {
		return c.y() + 8 + 2 * TAB_S + 4 + 12 + 4 + 1;
	}

	private Rect tabRect(Rect c, int i) {
		// i = -1 e a aba de busca; as outras vem em seguida
		int k = i + 1;
		int perRow = (CAT_W - 16) / TAB_S;
		return new Rect(c.x() + 8 + (k % perRow) * TAB_S, c.y() + 8 + (k / perRow) * TAB_S, TAB_S - 1, TAB_S - 1);
	}

	private static Rect searchRect(Rect c) {
		return new Rect(c.x() + 8, c.y() + 8 + 2 * TAB_S + 4, CAT_W - 16, 12);
	}

	private static int chestX(Rect p) {
		return p.x() + InventoryLayout.PAD + 1;
	}

	private static int chestY(Rect p) {
		return p.y() + 9;
	}

	private static int hotbarX(Rect b) {
		return b.x() + (BAG_W - (8 * 18 + InventoryLayout.HEX_W)) / 2;
	}

	private static int hotbarY(Rect b) {
		return b.y() + 8 + 3 * InventoryLayout.SLOT + 9;
	}

	private static int equipX(Rect e) {
		return e.x() + (EQUIP_W - InventoryLayout.HEX_W) / 2;
	}

	@Override
	protected void placeSlots() {
		if (this.bag == null) return;
		for (int i = 0; i <= 4; i++) hideSlot(i);
		Rect e = this.equip.now;
		for (int a = 0; a < 4; a++) setSlot(5 + a, equipX(e) + 4, e.y() + 7 + a * 22 + 2);
		setSlot(45, equipX(e) + 4, e.y() + 7 + 4 * 22 - 2 + 12 + 2);
		Rect b = this.bag.now;
		placeGrid(9, 27, 9, b.x() + InventoryLayout.PAD + 1, b.y() + 9);
		placeHexHotbar(36, hotbarX(b), hotbarY(b));
		if (this.search != null) {
			Rect s = searchRect(this.catalog.now);
			this.search.setX(s.x() + 15);
			this.search.setY(s.y() + 2);
		}
	}

	@Override
	protected Panel slotOwner(int slotIndex) {
		if (slotIndex >= 9 && slotIndex <= 44) return this.bag;
		if ((slotIndex >= 5 && slotIndex <= 8) || slotIndex == 45) return this.equip;
		return null;
	}

	// ------------------------------------------------------------------ desenho

	@Override
	protected void drawPanel(GuiGraphics g, Panel p, float partialTick, int mouseX, int mouseY) {
		float a = p.alpha;
		boolean canHover = !isClosing() && !isDraggingPanel();
		if (p == this.equip) {
			Rect e = p.now;
			for (int i = 0; i < 4; i++) Draw.sprite(g, Theme.SLOT_HEX, equipX(e), e.y() + 7 + i * 22, InventoryLayout.HEX_W, InventoryLayout.HEX_H, a);
			int offY = e.y() + 7 + 4 * 22 - 2 + 12;
			Panels.ornament(g, e.x() + 4, offY - 9, e.w() - 8, a * 0.9f);
			Draw.sprite(g, Theme.SLOT_HEX, equipX(e), offY, InventoryLayout.HEX_W, InventoryLayout.HEX_H, a);
		} else if (p == this.bag) {
			Rect b = p.now;
			drawGrid(g, b.x() + InventoryLayout.PAD + 1, b.y() + 9, 27, 9, a);
			Panels.ornament(g, b.x() + 24, hotbarY(b) - 7, b.w() - 48, a * 0.9f);
			drawHexHotbar(g, hotbarX(b), hotbarY(b), a);
		} else if (p == this.catalog) {
			drawCatalog(g, p.now, a, mouseX, mouseY, partialTick, canHover);
		} else if (p == this.personal) {
			drawPersonal(g, p.now, a, mouseX, mouseY, canHover);
		} else if (p == this.bar) {
			drawBar(g, p, mouseX, mouseY, canHover);
		} else if (p == this.commands) {
			this.commandList.draw(g, p.now, a, mouseX, mouseY, partialTick, canHover);
		}
	}

	private void drawCatalog(GuiGraphics g, Rect c, float a, int mouseX, int mouseY, float partialTick, boolean canHover) {
		this.hoveredTab = -2;
		for (int i = -1; i < this.tabs.size(); i++) {
			Rect t = tabRect(c, i);
			boolean sel = i == -1 ? selectedTab == null : this.tabs.get(i) == selectedTab;
			boolean hover = canHover && t.contains(mouseX, mouseY);
			if (hover) this.hoveredTab = i;
			Draw.fill(g, t.x(), t.y(), t.right(), t.bottom(), Draw.alpha(sel ? 0xC0473A63 : 0x900A080E, a));
			int edge = sel ? 0xFFF2C76A : (hover ? 0xFFB89552 : 0xFF3A3346);
			Draw.fill(g, t.x(), t.bottom() - 1, t.right(), t.bottom(), Draw.alpha(edge, a));
			if (i == -1) {
				Draw.sprite(g, Theme.icon("search"), t.x() + 2, t.y() + 2, 12, 12, Draw.alpha(sel ? Theme.IVORY : 0xFF9A90A4, a));
			} else if (a > 0.5f) {
				g.renderItem(this.tabs.get(i).getIconItem(), t.x(), t.y());
			}
		}
		Rect s = searchRect(c);
		Draw.fill(g, s.x(), s.y(), s.right(), s.bottom(), Draw.alpha(0xC00A080E, a));
		Draw.fill(g, s.x(), s.bottom() - 1, s.right(), s.bottom(), Draw.alpha(Theme.GOLD, a * (this.search.isFocused() ? 1f : 0.5f)));
		Draw.sprite(g, Theme.icon("search"), s.x() + 2, s.y(), 12, 12, Draw.alpha(0xFF9A90A4, a));
		this.search.render(g, mouseX, mouseY, partialTick);

		int gx = gridX(c);
		int gy = gridY(c);
		this.hoveredItem = -1;
		this.scrollRow = Mth.clamp(this.scrollRow, 0, maxScroll());
		for (int i = 0; i < COLS * ROWS; i++) {
			int x = gx + (i % COLS) * InventoryLayout.SLOT;
			int y = gy + (i / COLS) * InventoryLayout.SLOT;
			Draw.sprite(g, Theme.SLOT, x - 1, y - 1, 18, 18, a);
			int idx = this.scrollRow * COLS + i;
			if (idx >= this.shown.size()) continue;
			boolean hover = canHover && mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
			if (hover) {
				this.hoveredItem = idx;
				Draw.fill(g, x, y, x + 16, y + 16, Draw.alpha(0x30FFF2CF, a));
			}
			if (a > 0.5f) g.renderItem(this.shown.get(idx), x, y);
		}
		if (maxScroll() > 0) {
			int trackX = gx + COLS * InventoryLayout.SLOT + 3;
			int trackH = ROWS * InventoryLayout.SLOT - 2;
			Draw.fill(g, trackX, gy - 1, trackX + 4, gy - 1 + trackH, Draw.alpha(0xFF1A1620, a));
			int barH = Math.max(8, trackH * ROWS / (ROWS + maxScroll()));
			int barY = gy - 1 + (trackH - barH) * this.scrollRow / maxScroll();
			Draw.fill(g, trackX, barY, trackX + 4, barY + barH, Draw.alpha(Theme.GOLD, a));
		}
		if (this.shown.isEmpty()) {
			Component none = Component.translatable("isekai_ui.creative.nothing");
			Draw.textCentered(g, this.font, none, c.cx(), gy + 30, Draw.alpha(Theme.TEXT_DIM, a), false);
		}
	}

	private void drawPersonal(GuiGraphics g, Rect p, float a, int mouseX, int mouseY, boolean canHover) {
		int x0 = chestX(p);
		int y0 = chestY(p);
		this.hoveredPersonal = -1;
		for (int i = 0; i < PersonalChest.SLOTS; i++) {
			int x = x0 + (i % 9) * InventoryLayout.SLOT;
			int y = y0 + (i / 9) * InventoryLayout.SLOT;
			Draw.sprite(g, Theme.SLOT, x - 1, y - 1, 18, 18, a);
			boolean hover = canHover && mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
			if (hover) {
				this.hoveredPersonal = i;
				Draw.fill(g, x, y, x + 16, y + 16, Draw.alpha(0x30FFF2CF, a));
			}
			ItemStack s = PersonalChest.get(personalPage, i);
			if (!s.isEmpty() && a > 0.5f) {
				g.renderItem(s, x, y);
				g.renderItemDecorations(this.font, s, x, y);
			}
		}
	}

	private Rect buttonRect(Rect bar, int i) {
		return new Rect(bar.x() + i * InventoryLayout.BUTTON_STEP + 1, bar.y(), InventoryLayout.HEX_W, InventoryLayout.HEX_H);
	}

	private void drawBar(GuiGraphics g, Panel p, int mouseX, int mouseY, boolean canHover) {
		int before = this.hoveredButton;
		this.hoveredButton = -1;
		for (int i = 0; i < this.buttons.size(); i++) {
			Rect r = buttonRect(p.now, i);
			boolean hover = canHover && r.contains(mouseX, mouseY);
			if (hover) this.hoveredButton = i;
			BarButton b = this.buttons.get(i);
			Panels.hexButton(g, r, b.icon(), hover, b.on(), p.alpha);
		}
		if (this.hoveredButton >= 0 && this.hoveredButton != before) UiSounds.hover();
	}

	@Override
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (isClosing() || isDraggingPanel()) return;
		if (this.hoveredButton >= 0 && this.hoveredButton < this.buttons.size()) {
			this.setTooltipForNextRenderPass(this.buttons.get(this.hoveredButton).tooltip());
			return;
		}
		if (this.commands.visible && this.commands.grabArea().contains(mouseX, mouseY)) {
			Component tip = this.commandList.tooltip();
			if (tip != null) this.setTooltipForNextRenderPass(tip);
			return;
		}
		if (this.hoveredTab >= -1) {
			Component name = this.hoveredTab == -1 ? Component.translatable("isekai_ui.creative.search") : this.tabs.get(this.hoveredTab).getDisplayName();
			this.setTooltipForNextRenderPass(name);
			return;
		}
		if (!this.menu.getCarried().isEmpty()) return;
		if (this.hoveredItem >= 0 && this.hoveredItem < this.shown.size()) {
			ItemStack s = this.shown.get(this.hoveredItem);
			g.renderTooltip(this.font, getTooltipFromItem(this.minecraft, s), s.getTooltipImage(), mouseX, mouseY);
		} else if (this.hoveredPersonal >= 0) {
			ItemStack s = PersonalChest.get(personalPage, this.hoveredPersonal);
			List<Component> lines = new ArrayList<>();
			if (!s.isEmpty()) lines.addAll(getTooltipFromItem(this.minecraft, s));
			lines.add(Component.translatable(s.isEmpty() ? "isekai_ui.creative.personal_empty_tip" : "isekai_ui.creative.personal_tip")
					.withStyle(ChatFormatting.DARK_GRAY));
			g.renderTooltip(this.font, lines, s.isEmpty() ? java.util.Optional.empty() : s.getTooltipImage(), mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ cliques

	@Override
	protected boolean onPanelClick(Panel p, double mouseX, double mouseY, int button) {
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
		if (p == this.catalog) return clickCatalog(p.now, mouseX, mouseY, button);
		if (p == this.personal) {
			if (this.hoveredPersonal >= 0) {
				clickPersonal(this.hoveredPersonal, button);
				return true;
			}
			return false;
		}
		return false;
	}

	private boolean clickCatalog(Rect c, double mouseX, double mouseY, int button) {
		for (int i = -1; i < this.tabs.size(); i++) {
			if (!tabRect(c, i).contains(mouseX, mouseY)) continue;
			selectedTab = i == -1 ? null : this.tabs.get(i);
			if (selectedTab != null) this.search.setValue("");
			this.scrollRow = 0;
			refresh();
			UiSounds.click();
			if (selectedTab == null) setFocused(this.search);
			return true;
		}
		if (searchRect(c).contains(mouseX, mouseY)) {
			setFocused(this.search);
			this.search.setFocused(true);
			if (button == 1) this.search.setValue("");
			return true;
		}
		if (this.hoveredItem >= 0 && this.hoveredItem < this.shown.size()) {
			clickCatalogItem(this.shown.get(this.hoveredItem), button);
			return true;
		}
		return false;
	}

	/** Igual ao catalogo vanilla: clique pega 1, shift pega a pilha, botao do meio pega a pilha. */
	private void clickCatalogItem(ItemStack stack, int button) {
		ItemStack carried = this.menu.getCarried();
		boolean shift = hasShiftDown();
		if (button == 2) {
			if (carried.isEmpty()) this.menu.setCarried(stack.copyWithCount(stack.getMaxStackSize()));
			return;
		}
		if (!carried.isEmpty()) {
			if (ItemStack.isSameItemSameComponents(carried, stack)) {
				if (button == 0) {
					if (shift) carried.setCount(carried.getMaxStackSize());
					else if (carried.getCount() < carried.getMaxStackSize()) carried.grow(1);
				} else {
					carried.shrink(1);
				}
			} else if (button == 0) {
				this.menu.setCarried(ItemStack.EMPTY); // devolve ao catalogo
			} else {
				carried.shrink(1);
			}
			return;
		}
		this.menu.setCarried(stack.copyWithCount(shift ? stack.getMaxStackSize() : 1));
	}

	/**
	 * Bau pessoal: clique com a mao vazia pega uma COPIA (o item continua guardado); botao direito tira o item
	 * do bau; shift manda uma copia direto para o inventario. Com um item na mao, clique guarda o item.
	 */
	private void clickPersonal(int slot, int button) {
		ItemStack stored = PersonalChest.get(personalPage, slot);
		ItemStack carried = this.menu.getCarried();
		if (button == 2) {
			if (carried.isEmpty() && !stored.isEmpty()) this.menu.setCarried(stored.copyWithCount(stored.getMaxStackSize()));
			return;
		}
		if (carried.isEmpty()) {
			if (stored.isEmpty()) return;
			if (button == 1) {
				this.menu.setCarried(stored.copy());
				PersonalChest.set(personalPage, slot, ItemStack.EMPTY);
			} else if (hasShiftDown()) {
				giveToInventory(stored.copy());
			} else {
				this.menu.setCarried(stored.copy());
			}
			UiSounds.hover();
			refreshPersonalTitle();
			return;
		}
		if (stored.isEmpty()) {
			PersonalChest.set(personalPage, slot, carried.copy());
			this.menu.setCarried(ItemStack.EMPTY);
		} else if (ItemStack.isSameItemSameComponents(stored, carried)) {
			int total = Math.min(stored.getMaxStackSize(), stored.getCount() + carried.getCount());
			int moved = total - stored.getCount();
			PersonalChest.set(personalPage, slot, stored.copyWithCount(total));
			carried.shrink(moved);
		} else {
			PersonalChest.set(personalPage, slot, carried.copy());
			this.menu.setCarried(stored.copy());
		}
		UiSounds.toggle();
		refreshPersonalTitle();
	}

	private void refreshPersonalTitle() {
		if (this.personal != null) this.personal.title = personalTitle();
	}

	/** Coloca no primeiro espaco livre da barra/inventario (e avisa o servidor pelo mecanismo do criativo). */
	private void giveToInventory(ItemStack stack) {
		if (!this.player.getInventory().add(stack) && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleCreativeModeItemDrop(stack);
		}
		this.menu.broadcastChanges();
	}

	/**
	 * Cliques nos slots reais: tudo e feito aqui (como no criativo vanilla) e o servidor recebe so o
	 * resultado de cada slot que mudou (pelo ouvinte de inventario criativo).
	 */
	@Override
	protected void slotClicked(Slot slot, int slotId, int button, ClickType type) {
		if (this.minecraft.gameMode == null) return;
		if (slot != null) slotId = slot.index;
		ItemStack carried = this.menu.getCarried();
		if (slotId == -999) {
			if (!carried.isEmpty()) {
				if (button == 0) {
					this.minecraft.gameMode.handleCreativeModeItemDrop(carried.copy());
					this.menu.setCarried(ItemStack.EMPTY);
				} else {
					this.minecraft.gameMode.handleCreativeModeItemDrop(carried.split(1));
				}
			}
			return;
		}
		if (slotId < 0 || slotId >= this.menu.slots.size()) return;
		if (type == ClickType.QUICK_MOVE && slot != null && slot.hasItem() && carried.isEmpty()) {
			int page = PersonalChest.addCopy(slot.getItem(), personalPage);
			if (page >= 0) {
				personalPage = page;
				refreshPersonalTitle();
				UiSounds.toggle();
			}
			return;
		}
		if (type == ClickType.THROW && slot != null && slot.hasItem()) {
			ItemStack s = slot.getItem();
			int n = button == 0 ? 1 : s.getCount();
			this.minecraft.gameMode.handleCreativeModeItemDrop(s.copyWithCount(n));
			s.shrink(n);
			slot.setChanged();
			this.menu.broadcastChanges();
			return;
		}
		this.menu.clicked(slotId, button, type, this.player);
		this.menu.broadcastChanges();
	}

	// ------------------------------------------------------------------ teclado e rolagem

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.commands.visible && this.commands.grabArea().contains(mouseX, mouseY)) return this.commandList.scroll(scrollY);
		if (this.catalog.now.contains(mouseX, mouseY)) {
			this.scrollRow = Mth.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
			return true;
		}
		if (this.personal.now.contains(mouseX, mouseY)) {
			changePage(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		this.ignoreTextInput = false;
		if (isClosing()) return true;
		if (this.commandList.isTyping()) return this.commandList.keyPressed(keyCode, scanCode, modifiers);
		boolean handled = handleKey(keyCode, scanCode, modifiers);
		if (handled && !this.search.isFocused()) this.ignoreTextInput = true;
		return handled;
	}

	private boolean handleKey(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers);
		if (this.search.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				this.search.setFocused(false);
				setFocused(null);
				return true;
			}
			return this.search.keyPressed(keyCode, scanCode, modifiers) || this.search.canConsumeInput();
		}
		if (this.minecraft.options.keyDrop.matches(keyCode, scanCode) && this.hoveredPersonal >= 0) {
			if (!PersonalChest.get(personalPage, this.hoveredPersonal).isEmpty()) {
				PersonalChest.set(personalPage, this.hoveredPersonal, ItemStack.EMPTY);
				refreshPersonalTitle();
				UiSounds.click();
			}
			return true;
		}
		// teclas 1-9 em cima de um item do catalogo/bau pessoal: pilha cheia direto naquele slot da barra
		ItemStack hovered = this.hoveredItem >= 0 && this.hoveredItem < this.shown.size() ? this.shown.get(this.hoveredItem)
				: this.hoveredPersonal >= 0 ? PersonalChest.get(personalPage, this.hoveredPersonal) : ItemStack.EMPTY;
		if (!hovered.isEmpty()) {
			for (int i = 0; i < 9; i++) {
				if (this.minecraft.options.keyHotbarSlots[i].matches(keyCode, scanCode)) {
					this.menu.getSlot(36 + i).setByPlayer(hovered.copyWithCount(hovered.getMaxStackSize()));
					this.menu.broadcastChanges();
					UiSounds.hover();
					return true;
				}
			}
		}
		if (this.minecraft.options.keyTogglePerspective.matches(keyCode, scanCode)) {
			toggleCamera();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		this.ignoreTextInput = false;
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		if (isClosing() || this.ignoreTextInput) return false;
		if (this.search.isFocused() || this.commandList.isTyping()) return super.charTyped(c, modifiers);
		// comecar a digitar com o mouse livre ja vai para a busca (como no criativo vanilla)
		if (Character.isLetter(c)) {
			selectedTab = null;
			setFocused(this.search);
			this.search.setFocused(true);
			return this.search.charTyped(c, modifiers);
		}
		return super.charTyped(c, modifiers);
	}

	// ------------------------------------------------------------------ testes automaticos

	public void debugSearch(String text) {
		this.search.setValue(text);
	}

	public void debugTakeFromCatalog(int index, boolean fullStack) {
		if (index < 0 || index >= this.shown.size()) return;
		ItemStack s = this.shown.get(index);
		this.menu.setCarried(s.copyWithCount(fullStack ? s.getMaxStackSize() : 1));
	}

	public void debugStoreCarried(int slot) {
		clickPersonal(slot, 0);
	}

	public void debugTakeCopy(int slot) {
		ItemStack stored = PersonalChest.get(personalPage, slot);
		if (!stored.isEmpty()) giveToInventory(stored.copy());
	}

	public int debugShownCount() {
		return this.shown.size();
	}

	public CommandList debugCommands() {
		return this.commandList;
	}
}
