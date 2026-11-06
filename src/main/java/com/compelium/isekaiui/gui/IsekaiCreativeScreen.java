package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.Instruments;
import com.compelium.isekaiui.client.IsekaiKeys;
import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.commands.CommandMemory;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.creative.PersonalChest;
import com.compelium.isekaiui.map.MapManager;
import com.compelium.isekaiui.map.MapRenderer;
import com.compelium.isekaiui.map.MapView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeInventoryListener;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
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
import java.util.Map;
import java.util.Optional;

/**
 * Inventario do modo criativo no estilo isekai. A janela grande ("biblioteca") tem dois modos, escolhidos
 * nas abas do titulo: o Catalogo do jogo (categorias + busca) e o Bau pessoal (copias de itens salvas no seu
 * computador, separadas em abas com nome e icone). Em volta ficam o equipamento, o minimapa e a bolsa.
 *
 * Funciona igual ao criativo do jogo: as mudancas no inventario sao feitas aqui e cada slot alterado e
 * enviado ao servidor pelo mesmo pacote que o inventario criativo vanilla usa.
 */
public class IsekaiCreativeScreen extends IsekaiMenuScreen<InventoryMenu> {
	private static final float TO_MAP_SECONDS = 0.14f;
	private static final int SLOT = InventoryLayout.SLOT;
	private static final int TAB = InventoryLayout.TAB;
	private static final int ROWS = 6;
	private static final int TAB_S = 17;
	private static final int TAB_ROWS = 2;
	private static final int LIB_H = 8 + TAB_ROWS * TAB_S + 4 + 12 + 4 + ROWS * SLOT + 8;
	private static final int BAG_W = IsekaiStationScreen.BAG_W;
	private static final int BAG_H = IsekaiStationScreen.BAG_H;
	private static final int EQUIP_W = 36;
	private static final int EQUIP_H = 7 + 4 * 22 - 2 + 12 + InventoryLayout.HEX_H + 7;
	private static final int EDITOR_H = 74;
	private static final int MIN_HEIGHT = 222;
	/** Abas pequenas: -1 = busca / todos os itens, -2 = "+" (nova aba), 0 em diante = categoria ou aba do bau. */
	private static final int CELL_SEARCH = -1;
	private static final int CELL_PLUS = -2;
	private static final int CELL_NONE = Integer.MIN_VALUE;
	private static final long CONFIRM_NANOS = 3_000_000_000L;
	private static final long TOAST_NANOS = 3_000_000_000L;

	private static boolean vanillaBypass;
	/** Catalogo: null = busca (todos os itens). */
	private static CreativeModeTab selectedTab;
	private static String lastSearch = "";
	private static String lastPersonalSearch = "";
	/** true = mostrando o bau pessoal; false = o catalogo. */
	private static boolean personalMode;
	/** Aba aberta do bau pessoal; -1 = todos os itens / resultado da busca. */
	private static int personalTab;
	/** Ultima aba de verdade que foi aberta (para onde vai um item solto na lista de busca). */
	private static int lastRealTab;

	private final LocalPlayer player;
	private final List<BarButton> buttons = new ArrayList<>();
	private final MapView miniView = new MapView();
	private CreativeInventoryListener listener;
	private List<CreativeModeTab> tabs = List.of();
	private List<ItemStack> shown = List.of();
	private List<int[]> refs = List.of();
	private int scrollRow;
	private int tabRowOffset;
	private Layout layout;
	private EditBox search;
	private CommandList commandList;
	private ArmorSetList armorList;
	private float commandsAppearAt;
	private float armorAppearAt;
	private float editorAppearAt;

	private Panel equip;
	private Panel library;
	private Panel mini;
	private Panel bag;
	private Panel bar;
	private Panel commands;
	private Panel armor;
	private Panel editor;

	private int hoveredCell = -1;
	private int hoveredTabCell = CELL_NONE;
	private int hoveredMode = -1;
	private int hoveredButton = -1;
	private int hoveredEditorPart;
	private boolean miniHovered;
	/** Tecla ja tratada: ignora o caractere que o sistema manda logo depois (ex.: "1" nao vira busca). */
	private boolean ignoreTextInput;

	/** Editor de uma aba do bau pessoal (nome, icone, posicao, apagar). -1 = fechado. */
	private int editingTab = -1;
	private EditBox tabName;
	private int confirmDeleteTab = -1;
	private long confirmNanos;

	private Component toast;
	private long toastNanos;

	private record BarButton(String icon, Component tooltip, Runnable action, boolean on) {
	}

	/** Posicoes padrao das janelas para um tamanho de tela. */
	private record Layout(int cols, Rect equip, Rect library, Rect bar, Rect mini, Rect bag) {
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

	private static int libWidth(int cols) {
		return 9 + cols * SLOT + 4 + 4 + 9;
	}

	private static int groupWidth(int cols) {
		return EQUIP_W + 8 + libWidth(cols) + 8 + BAG_W;
	}

	/** 9 colunas quando cabe (uma aba do bau pessoal = 6 linhas certinhas); senao 8 ou 7. */
	private static int columnsFor(int width) {
		for (int c = 9; c > 7; c--) if (width >= groupWidth(c) + 8) return c;
		return 7;
	}

	private static Layout compute(int W, int H, int buttons) {
		int cols = columnsFor(W);
		int x0 = Math.max(4, (W - groupWidth(cols)) / 2);
		Rect equipR = new Rect(x0, (H - EQUIP_H) / 2 + 4, EQUIP_W, EQUIP_H);
		int libY = Math.max(TAB + 4, (H - (TAB + LIB_H + 6 + InventoryLayout.HEX_H)) / 2 + TAB);
		Rect libR = new Rect(equipR.right() + 8, libY, libWidth(cols), LIB_H);
		int barW = Math.max(1, buttons) * InventoryLayout.BUTTON_STEP;
		Rect barR = new Rect(libR.cx() - barW / 2, libR.bottom() + 6, barW, InventoryLayout.HEX_H);
		int miniH = Mth.clamp(H - 168, 50, 96);
		int rightH = TAB + miniH + 12 + 8 + TAB + BAG_H;
		int top = Math.max(TAB + 4, (H - rightH) / 2 + TAB);
		Rect miniR = new Rect(libR.right() + 8, top, BAG_W, miniH);
		Rect bagR = new Rect(miniR.x(), miniR.bottom() + 12 + 8 + TAB, BAG_W, BAG_H);
		return new Layout(cols, equipR, libR, barR, miniR, bagR);
	}

	private static int expectedButtonCount() {
		return 5 + CommandMemory.pinned().size();
	}

	/** Onde o minimapa fica na tela (respeitando o layout salvo) — usado pelo mapa grande para voltar. */
	public static Rect minimapRect(int width, int height) {
		Rect def = compute(width, height, expectedButtonCount()).mini();
		Map<String, float[]> saved = IsekaiConfig.get().layouts.get("creative");
		float[] f = saved == null ? null : saved.get("minimap");
		if (f == null || f.length != 2) return def.grow(-5);
		int top = TAB + 1;
		int x = Mth.clamp(Math.round(f[0] * Math.max(0, width - def.w())), 1, Math.max(1, width - def.w() - 1));
		int y = Mth.clamp(Math.round(f[1] * Math.max(0, height - def.h())), top, Math.max(top, height - def.h() - 1));
		return new Rect(x, y, def.w(), def.h()).grow(-5);
	}

	@Override
	protected boolean screenFits() {
		return this.width >= groupWidth(7) + 8 && this.height >= MIN_HEIGHT && this.menu.slots.size() >= 46;
	}

	@Override
	protected void openVanillaFallback() {
		openClassic();
	}

	private void openClassic() {
		closeEditor(true);
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
		refreshCatalog();
		refreshPersonal();
	}

	@Override
	public void removed() {
		closeEditor(true);
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
		this.layout = compute(this.width, this.height, this.buttons.size());
		Layout L = this.layout;
		this.equip = addPanel("equip", 0.05f, -26, 0, true, this.player.getName(), L.equip());
		this.library = addPanel("catalog", 0f, 0, -20, true, modeName(personalMode), L.library());
		this.library.drawTitle = false;
		this.mini = addPanel("minimap", 0.09f, 24, 0, false, IsekaiInventoryScreen.regionName(this.minecraft, this.player), L.mini());
		this.mini.padBottom = 12;
		this.bag = addPanel("bag", 0.12f, 0, 22, true, Component.translatable("isekai_ui.panel.inventory"), L.bag());
		this.bar = addPanel("bar", 0.17f, 0, 16, false, null, L.bar());
		createOverlays(L);
		createSearch(L);
	}

	private void createOverlays(Layout L) {
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
		Rect cmdR = new Rect(L.bar().cx() - CommandList.WIDTH / 2, L.bar().y() - 8 - ch, CommandList.WIDTH, ch);
		this.commands = addPanel("commands", this.commandsAppearAt, 0, 10, true, Component.translatable("isekai_ui.panel.commands"), cmdR);
		this.commands.overlay = true;
		this.commands.visible = IsekaiInventoryScreen.commandsOpen;

		if (this.armorList == null) this.armorList = new ArmorSetList(this.font, new ArmorSetList.Host() {
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
			public boolean creative() {
				return true;
			}

			@Override
			public InventoryMenu menu() {
				return IsekaiCreativeScreen.this.menu;
			}
		});
		else this.armorList.stopTyping(false);
		int ah = ArmorSetList.height();
		Rect armorR = new Rect(L.bar().cx() - ArmorSetList.WIDTH / 2, L.bar().y() - 8 - ah, ArmorSetList.WIDTH, ah);
		this.armor = addPanel("armor_sets", this.armorAppearAt, 0, 10, true, Component.translatable("isekai_ui.panel.armor_sets"), armorR);
		this.armor.overlay = true;
		this.armor.visible = IsekaiInventoryScreen.armorOpen;

		boolean editing = personalMode && this.editingTab >= 0 && this.editingTab < PersonalChest.tabCount();
		this.editor = addPanel("tab_editor", this.editorAppearAt, 0, 8, true, Component.translatable("isekai_ui.personal.edit_tab"), editorRect(L.library()));
		this.editor.overlay = true;
		this.editor.visible = editing;
		String typed = this.tabName != null ? this.tabName.getValue() : null;
		this.tabName = null;
		if (editing) {
			EditBox box = new EditBox(this.font, 0, 0, 100, 10, Component.translatable("isekai_ui.personal.tab_name"));
			box.setBordered(false);
			box.setMaxLength(24);
			box.setTextColor(Theme.TEXT);
			box.setValue(typed != null ? typed : PersonalChest.tabName(this.editingTab));
			addWidget(box);
			this.tabName = box;
		} else {
			this.editingTab = -1;
		}
	}

	private void createSearch(Layout L) {
		this.search = new EditBox(this.font, 0, 0, L.library().w() - 18 - 14, 10, Component.translatable("isekai_ui.creative.search"));
		this.search.setBordered(false);
		this.search.setMaxLength(50);
		this.search.setTextColor(Theme.TEXT);
		this.search.setHint(searchHint());
		this.search.setValue(personalMode ? lastPersonalSearch : lastSearch);
		this.search.setResponder(this::onSearchChanged);
		addWidget(this.search);
	}

	private void buildButtons() {
		this.buttons.clear();
		boolean third = IsekaiConfig.get().thirdPersonCamera;
		this.buttons.add(new BarButton("catalog", Component.translatable("isekai_ui.creative.classic"), this::openClassic, false));
		this.buttons.add(new BarButton(third ? "camera_third" : "camera_first",
				Component.translatable(third ? "isekai_ui.button.camera.third" : "isekai_ui.button.camera.first"), this::toggleCamera, third));
		this.buttons.add(new BarButton("list", Component.translatable("isekai_ui.button.commands"), this::toggleCommands, IsekaiInventoryScreen.commandsOpen));
		this.buttons.add(new BarButton("armor", Component.translatable("isekai_ui.button.armor_sets"), this::toggleArmor, IsekaiInventoryScreen.armorOpen));
		this.buttons.add(new BarButton("trash", Component.translatable("isekai_ui.creative.trash"), this::trashCarried, false));
		for (CommandMemory.Entry e : CommandMemory.pinned()) {
			this.buttons.add(new BarButton(e.icon, Component.literal(e.label + "  (/" + e.command + ")"), () -> CommandMemory.run(e), false));
		}
	}

	private static Component modeName(boolean personal) {
		return Component.translatable(personal ? "isekai_ui.creative.mode.personal" : "isekai_ui.creative.mode.catalog");
	}

	private Component searchHint() {
		return Component.translatable(personalMode ? "isekai_ui.personal.search_hint" : "isekai_ui.creative.search_hint");
	}

	// ------------------------------------------------------------------ acoes

	private void toggleCamera() {
		IsekaiConfig cfg = IsekaiConfig.get();
		cfg.thirdPersonCamera = !cfg.thirdPersonCamera;
		IsekaiConfig.save();
		UiSounds.toggle();
		buildButtons();
	}

	private void toggleCommands() {
		closeEditor(true);
		IsekaiInventoryScreen.commandsOpen = !IsekaiInventoryScreen.commandsOpen;
		if (IsekaiInventoryScreen.commandsOpen) IsekaiInventoryScreen.armorOpen = false;
		this.commandsAppearAt = time();
		UiSounds.toggle();
		rebuildWidgets();
	}

	private void toggleArmor() {
		closeEditor(true);
		IsekaiInventoryScreen.armorOpen = !IsekaiInventoryScreen.armorOpen;
		if (IsekaiInventoryScreen.armorOpen) IsekaiInventoryScreen.commandsOpen = false;
		this.armorAppearAt = time();
		UiSounds.toggle();
		rebuildWidgets();
	}

	private void trashCarried() {
		if (this.menu.getCarried().isEmpty()) return;
		this.menu.setCarried(ItemStack.EMPTY);
		UiSounds.toggle();
	}

	private void openMap() {
		if (isClosing()) return;
		closeEditor(true);
		Rect from = this.mini.now.grow(-5);
		UiSounds.mapOpen();
		startClosing(TO_MAP_SECONDS, this.mini, () -> {
			if (this.minecraft.player != null) this.minecraft.player.closeContainer();
			this.minecraft.setScreen(IsekaiMapScreen.fromInventory(from));
		});
	}

	private void switchMode(boolean personal) {
		if (personalMode == personal) return;
		closeEditor(true);
		personalMode = personal;
		this.scrollRow = 0;
		this.tabRowOffset = 0;
		this.library.title = modeName(personal);
		this.search.setHint(searchHint());
		this.search.setValue(personal ? lastPersonalSearch : lastSearch);
		refresh();
		UiSounds.click();
	}

	private void showToast(Component c) {
		this.toast = c;
		this.toastNanos = System.nanoTime();
	}

	private void onSearchChanged(String text) {
		if (personalMode) {
			lastPersonalSearch = text;
			if (!text.isEmpty()) personalTab = CELL_SEARCH;
		} else {
			lastSearch = text;
			if (!text.isEmpty()) selectedTab = null;
		}
		this.scrollRow = 0;
		refresh();
	}

	// ------------------------------------------------------------------ o que aparece na grade

	private void refresh() {
		if (personalMode) refreshPersonal();
		else refreshCatalog();
		this.scrollRow = Mth.clamp(this.scrollRow, 0, maxScroll());
		if (this.library != null) this.library.title = modeName(personalMode);
	}

	/** Itens do catalogo (categoria escolhida ou busca). */
	private void refreshCatalog() {
		List<ItemStack> out = new ArrayList<>();
		if (selectedTab != null) {
			out.addAll(selectedTab.getDisplayItems());
		} else {
			String q = lastSearch.strip().toLowerCase(Locale.ROOT);
			for (ItemStack s : CreativeModeTabs.searchTab().getDisplayItems()) {
				if (q.isEmpty() || matches(s, q)) out.add(s);
			}
		}
		this.shown = out;
	}

	/** Bau pessoal: confere a aba aberta e refaz a lista de busca / todos os itens. */
	private void refreshPersonal() {
		int count = PersonalChest.tabCount();
		personalTab = Mth.clamp(personalTab, CELL_SEARCH, count - 1);
		lastRealTab = Mth.clamp(personalTab >= 0 ? personalTab : lastRealTab, 0, Math.max(0, count - 1));
		if (personalTab < 0) {
			String q = lastPersonalSearch.strip();
			this.refs = q.isEmpty() ? PersonalChest.all() : PersonalChest.search(q);
		} else {
			this.refs = List.of();
		}
	}

	private static boolean matches(ItemStack s, String q) {
		if (s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
		return BuiltInRegistries.ITEM.getKey(s.getItem()).toString().contains(q);
	}

	private boolean tabView() {
		return personalMode && personalTab >= 0;
	}

	private int cellCount() {
		if (!personalMode) return this.shown.size();
		return personalTab >= 0 ? PersonalChest.SLOTS : this.refs.size();
	}

	private ItemStack cellItem(int i) {
		if (!personalMode) return i >= 0 && i < this.shown.size() ? this.shown.get(i) : ItemStack.EMPTY;
		int[] r = cellRef(i);
		return r == null ? ItemStack.EMPTY : PersonalChest.get(r[0], r[1]);
	}

	/** {aba, slot} da celula do bau pessoal, ou null (catalogo ou celula vazia da lista de busca). */
	private int[] cellRef(int i) {
		if (!personalMode || i < 0) return null;
		if (personalTab >= 0) return i < PersonalChest.SLOTS ? new int[]{personalTab, i} : null;
		return i < this.refs.size() ? this.refs.get(i) : null;
	}

	private int cols() {
		return this.layout == null ? 9 : this.layout.cols();
	}

	private int maxScroll() {
		int rows = (cellCount() + cols() - 1) / cols();
		return Math.max(0, rows - ROWS);
	}

	/** Abas pequenas do modo atual (codigos, ver CELL_*). */
	private int[] tabCells() {
		if (!personalMode) {
			int[] out = new int[this.tabs.size() + 1];
			out[0] = CELL_SEARCH;
			for (int i = 0; i < this.tabs.size(); i++) out[i + 1] = i;
			return out;
		}
		int n = PersonalChest.tabCount();
		boolean plus = n < PersonalChest.MAX_TABS;
		int[] out = new int[n + 1 + (plus ? 1 : 0)];
		out[0] = CELL_SEARCH;
		for (int t = 0; t < n; t++) out[t + 1] = t;
		if (plus) out[n + 1] = CELL_PLUS;
		return out;
	}

	private boolean isSelectedCell(int code) {
		if (code == CELL_PLUS) return false;
		if (!personalMode) return code == CELL_SEARCH ? selectedTab == null : code < this.tabs.size() && this.tabs.get(code) == selectedTab;
		return code == personalTab;
	}

	private static int maxTabOffset(int cells, int per) {
		return Math.max(0, (cells + per - 1) / per - TAB_ROWS);
	}

	// ------------------------------------------------------------------ posicoes

	private static int gridX(Rect c) {
		return c.x() + 10;
	}

	private static int gridY(Rect c) {
		return c.y() + 8 + TAB_ROWS * TAB_S + 4 + 12 + 4 + 1;
	}

	private static int perRow(Rect c) {
		return Math.max(1, (c.w() - 16) / TAB_S);
	}

	private static Rect tabRect(Rect c, int k) {
		int per = perRow(c);
		return new Rect(c.x() + 8 + (k % per) * TAB_S, c.y() + 8 + (k / per) * TAB_S, TAB_S - 1, TAB_S - 1);
	}

	private static Rect tabArea(Rect c) {
		return new Rect(c.x() + 8, c.y() + 8, perRow(c) * TAB_S, TAB_ROWS * TAB_S);
	}

	private static Rect searchRect(Rect c) {
		return new Rect(c.x() + 8, c.y() + 8 + TAB_ROWS * TAB_S + 4, c.w() - 16, 12);
	}

	private static Rect editorRect(Rect lib) {
		int w = Math.min(lib.w() - 16, 176);
		return new Rect(lib.x() + (lib.w() - w) / 2, lib.y() + 8 + TAB_ROWS * TAB_S + 2 + TAB, w, EDITOR_H);
	}

	private static Rect editorIcon(Rect e) {
		return new Rect(e.x() + 8, e.y() + 8, 18, 18);
	}

	private static Rect editorName(Rect e) {
		return new Rect(e.x() + 32, e.y() + 10, e.w() - 40, 14);
	}

	private static Rect editorPrev(Rect e) {
		return new Rect(e.x() + 8, e.y() + 31, 16, 14);
	}

	private static Rect editorNext(Rect e) {
		return new Rect(e.x() + 26, e.y() + 31, 16, 14);
	}

	private static Rect editorDelete(Rect e) {
		return new Rect(e.right() - 24, e.y() + 31, 16, 14);
	}

	/** Abas do titulo da biblioteca: 0 = Catalogo, 1 = Bau pessoal (com nome curto se nao couber). */
	private Rect modeTabRect(Rect c, int which) {
		int avail = c.w() - 16;
		int wa = this.font.width(modeLabel(0, c)) + 18;
		int wb = this.font.width(modeLabel(1, c)) + 18;
		if (wa + 2 + wb > avail) {
			wa = Math.min(wa, (avail - 2) / 2);
			wb = Math.min(wb, avail - 2 - wa);
		}
		int y = c.y() - TAB + 1;
		return which == 0 ? new Rect(c.x() + 8, y, wa, TAB + 1) : new Rect(c.x() + 8 + wa + 2, y, wb, TAB + 1);
	}

	private Component modeLabel(int which, Rect c) {
		Component catalog = Panels.upper(modeName(false));
		if (which == 0) return catalog;
		Component full = Panels.upper(modeName(true));
		if (this.font.width(catalog) + this.font.width(full) + 36 + 2 <= c.w() - 16) return full;
		return Panels.upper(Component.translatable("isekai_ui.creative.mode.personal_short"));
	}

	/** Nome mostrado no canto da busca: categoria ou aba aberta. */
	private Component searchLabel() {
		if (personalMode) return personalTab >= 0 ? Component.literal(PersonalChest.tabName(personalTab)) : null;
		return selectedTab != null ? selectedTab.getDisplayName() : null;
	}

	private int searchLabelWidth(Rect c) {
		Component label = searchLabel();
		if (label == null) return 0;
		return Math.min(this.font.width(label), (c.w() - 16) / 2);
	}

	@Override
	protected void placeSlots() {
		if (this.bag == null) return;
		for (int i = 0; i <= 4; i++) hideSlot(i);
		Rect e = this.equip.now;
		int ex = e.x() + (EQUIP_W - InventoryLayout.HEX_W) / 2;
		for (int a = 0; a < 4; a++) setSlot(5 + a, ex + 4, e.y() + 7 + a * 22 + 2);
		setSlot(45, ex + 4, e.y() + 7 + 4 * 22 - 2 + 12 + 2);
		Rect b = this.bag.now;
		placeGrid(9, 27, 9, b.x() + InventoryLayout.PAD + 1, b.y() + 9);
		placeHexHotbar(36, hotbarX(b), hotbarY(b));
		if (this.search != null) {
			Rect s = searchRect(this.library.now);
			this.search.setX(s.x() + 15);
			this.search.setY(s.y() + 2);
			int label = searchLabelWidth(this.library.now);
			this.search.setWidth(Math.max(30, s.w() - 19 - (label > 0 ? label + 6 : 0)));
		}
		// o editor de aba acompanha a biblioteca (se ela foi arrastada)
		if (this.editor != null && this.editor.visible) this.editor.rect = editorRect(this.library.rect);
	}

	private static int hotbarX(Rect b) {
		return b.x() + (BAG_W - (8 * 18 + InventoryLayout.HEX_W)) / 2;
	}

	private static int hotbarY(Rect b) {
		return b.y() + 8 + 3 * SLOT + 9;
	}

	@Override
	protected Panel slotOwner(int slotIndex) {
		if (slotIndex >= 9 && slotIndex <= 44) return this.bag;
		if ((slotIndex >= 5 && slotIndex <= 8) || slotIndex == 45) return this.equip;
		return null;
	}

	@Override
	protected boolean isTitleButton(Panel p, double mouseX, double mouseY) {
		return p == this.library && (modeTabRect(p.now, 0).contains(mouseX, mouseY) || modeTabRect(p.now, 1).contains(mouseX, mouseY));
	}

	// ------------------------------------------------------------------ desenho

	@Override
	protected void drawPanel(GuiGraphics g, Panel p, float partialTick, int mouseX, int mouseY) {
		float a = p.alpha;
		boolean canHover = !isClosing() && !isDraggingPanel();
		boolean free = canHover && (p.overlay || overlayAt(mouseX, mouseY) == null);
		if (p == this.equip) {
			Rect e = p.now;
			int ex = e.x() + (EQUIP_W - InventoryLayout.HEX_W) / 2;
			for (int i = 0; i < 4; i++) Draw.sprite(g, Theme.SLOT_HEX, ex, e.y() + 7 + i * 22, InventoryLayout.HEX_W, InventoryLayout.HEX_H, a);
			int offY = e.y() + 7 + 4 * 22 - 2 + 12;
			Panels.ornament(g, e.x() + 4, offY - 9, e.w() - 8, a * 0.9f);
			Draw.sprite(g, Theme.SLOT_HEX, ex, offY, InventoryLayout.HEX_W, InventoryLayout.HEX_H, a);
		} else if (p == this.bag) {
			Rect b = p.now;
			drawGrid(g, b.x() + InventoryLayout.PAD + 1, b.y() + 9, 27, 9, a);
			Panels.ornament(g, b.x() + 24, hotbarY(b) - 7, b.w() - 48, a * 0.9f);
			drawHexHotbar(g, hotbarX(b), hotbarY(b), a);
		} else if (p == this.library) {
			drawLibrary(g, p.now, a, mouseX, mouseY, partialTick, free);
		} else if (p == this.mini) {
			drawMinimap(g, p, partialTick, mouseX, mouseY, free);
		} else if (p == this.bar) {
			drawBar(g, p, mouseX, mouseY, free);
		} else if (p == this.commands) {
			this.commandList.draw(g, p.now, a, mouseX, mouseY, partialTick, canHover);
		} else if (p == this.armor) {
			this.armorList.draw(g, p.now, a, mouseX, mouseY, partialTick, canHover);
		} else if (p == this.editor) {
			drawEditor(g, p.now, a, mouseX, mouseY, partialTick, canHover);
		}
	}

	private void drawLibrary(GuiGraphics g, Rect c, float a, int mouseX, int mouseY, float partialTick, boolean canHover) {
		// abas do titulo: Catalogo | Bau pessoal
		this.hoveredMode = -1;
		for (int m = 0; m < 2; m++) {
			Rect r = modeTabRect(c, m);
			boolean active = (m == 1) == personalMode;
			boolean hover = canHover && r.contains(mouseX, mouseY);
			if (hover) this.hoveredMode = m;
			Panels.tab(g, this.font, r.x(), r.y(), r.w(), modeLabel(m, c), a * (active ? 1f : (hover ? 0.8f : 0.45f)));
		}

		// abas pequenas: categorias do catalogo ou abas do bau pessoal
		int[] cells = tabCells();
		int per = perRow(c);
		this.tabRowOffset = Mth.clamp(this.tabRowOffset, 0, maxTabOffset(cells.length, per));
		int first = this.tabRowOffset * per;
		this.hoveredTabCell = CELL_NONE;
		for (int k = 0; k < TAB_ROWS * per && first + k < cells.length; k++) {
			int code = cells[first + k];
			Rect t = tabRect(c, k);
			boolean sel = isSelectedCell(code);
			boolean hover = canHover && t.contains(mouseX, mouseY);
			if (hover) this.hoveredTabCell = code;
			boolean editing = personalMode && code >= 0 && code == this.editingTab;
			Draw.fill(g, t.x(), t.y(), t.right(), t.bottom(), Draw.alpha(sel ? 0xC0473A63 : 0x900A080E, a));
			int edge = sel || editing ? 0xFFF2C76A : (hover ? 0xFFB89552 : 0xFF3A3346);
			Draw.fill(g, t.x(), t.bottom() - 1, t.right(), t.bottom(), Draw.alpha(edge, a));
			if (code == CELL_SEARCH) {
				Draw.sprite(g, Theme.icon("search"), t.x() + 2, t.y() + 2, 12, 12, Draw.alpha(sel ? Theme.IVORY : 0xFF9A90A4, a));
			} else if (code == CELL_PLUS) {
				Draw.sprite(g, Theme.icon("plus"), t.x() + 2, t.y() + 2, 12, 12, Draw.alpha(hover ? Theme.TEXT_GOLD : 0xFF9A90A4, a));
			} else if (a > 0.5f) {
				g.renderItem(personalMode ? PersonalChest.tabIcon(code) : this.tabs.get(code).getIconItem(), t.x(), t.y());
			}
		}
		int rowsTotal = (cells.length + per - 1) / per;
		if (rowsTotal > TAB_ROWS) {
			// mais abas do que cabe: um risquinho mostra onde voce esta (a rodinha do mouse em cima das abas troca a linha)
			Rect area = tabArea(c);
			int barH = Math.max(4, area.h() * TAB_ROWS / rowsTotal);
			int barY = area.y() + (area.h() - barH) * this.tabRowOffset / Math.max(1, rowsTotal - TAB_ROWS);
			Draw.fill(g, c.right() - 7, area.y(), c.right() - 5, area.bottom() - 1, Draw.alpha(0xFF1A1620, a));
			Draw.fill(g, c.right() - 7, barY, c.right() - 5, barY + barH, Draw.alpha(Theme.GOLD, a));
		}

		// busca (+ nome da categoria/aba aberta no canto)
		Rect s = searchRect(c);
		Draw.fill(g, s.x(), s.y(), s.right(), s.bottom(), Draw.alpha(0xC00A080E, a));
		Draw.fill(g, s.x(), s.bottom() - 1, s.right(), s.bottom(), Draw.alpha(Theme.GOLD, a * (this.search.isFocused() ? 1f : 0.5f)));
		Draw.sprite(g, Theme.icon("search"), s.x() + 2, s.y(), 12, 12, Draw.alpha(0xFF9A90A4, a));
		Component label = searchLabel();
		if (label != null) {
			String text = this.font.plainSubstrByWidth(label.getString(), searchLabelWidth(c));
			Draw.text(g, this.font, Component.literal(text), s.right() - 4 - this.font.width(text), s.y() + 2, Draw.alpha(Theme.TEXT_GOLD, a * 0.9f), false);
		}
		this.search.render(g, mouseX, mouseY, partialTick);

		// grade de itens
		int gx = gridX(c);
		int gy = gridY(c);
		int cols = cols();
		boolean tabView = tabView();
		int count = cellCount();
		this.hoveredCell = -1;
		this.scrollRow = Mth.clamp(this.scrollRow, 0, maxScroll());
		for (int i = 0; i < cols * ROWS; i++) {
			int idx = this.scrollRow * cols + i;
			if (tabView && idx >= PersonalChest.SLOTS) break;
			int x = gx + (i % cols) * SLOT;
			int y = gy + (i / cols) * SLOT;
			Draw.sprite(g, Theme.SLOT, x - 1, y - 1, 18, 18, a);
			boolean hover = canHover && mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
			if (hover && (personalMode || idx < count)) {
				this.hoveredCell = idx;
				Draw.fill(g, x, y, x + 16, y + 16, Draw.alpha(0x30FFF2CF, a));
			}
			ItemStack st = cellItem(idx);
			if (!st.isEmpty() && a > 0.5f) {
				g.renderItem(st, x, y);
				if (personalMode) g.renderItemDecorations(this.font, st, x, y);
			}
		}
		if (maxScroll() > 0) {
			int trackX = gx + cols * SLOT + 3;
			int trackH = ROWS * SLOT - 2;
			Draw.fill(g, trackX, gy - 1, trackX + 4, gy - 1 + trackH, Draw.alpha(0xFF1A1620, a));
			int barH = Math.max(8, trackH * ROWS / (ROWS + maxScroll()));
			int barY = gy - 1 + (trackH - barH) * this.scrollRow / maxScroll();
			Draw.fill(g, trackX, barY, trackX + 4, barY + barH, Draw.alpha(Theme.GOLD, a));
		}
		Component empty = null;
		if (!personalMode && count == 0) empty = Component.translatable("isekai_ui.creative.nothing");
		else if (personalMode && personalTab < 0 && count == 0) {
			empty = Component.translatable(lastPersonalSearch.isBlank() ? "isekai_ui.personal.empty_all" : "isekai_ui.creative.nothing");
		} else if (tabView && PersonalChest.tabEmpty(personalTab)) {
			empty = Component.translatable("isekai_ui.personal.empty_tab");
		}
		if (empty != null) {
			int ty = gy + ROWS * SLOT / 2 - 5;
			Draw.fill(g, c.x() + 6, ty - 3, c.right() - 6, ty + 11, Draw.alpha(0xB00A080E, a));
			Draw.textFit(g, this.font, empty, c.cx(), ty, c.w() - 20, Draw.alpha(Theme.TEXT_DIM, a), false);
		}
	}

	private void drawMinimap(GuiGraphics g, Panel p, float partialTick, int mouseX, int mouseY, boolean canHover) {
		float a = p.alpha;
		Rect map = p.now.grow(-5);
		this.miniView.cx = Mth.lerp(partialTick, this.player.xo, this.player.getX());
		this.miniView.cz = Mth.lerp(partialTick, this.player.zo, this.player.getZ());
		this.miniView.zoom = IsekaiConfig.get().map.minimapZoom;
		Draw.fill(g, map.x() - 2, map.y() + 3, map.right() + 6, map.bottom() + 9, Draw.alpha(Theme.SHADOW, a));
		MapRenderer.draw(g, MapManager.current(), this.miniView, map, a, partialTick, true);
		Draw.sprite(g, Theme.MAP_FRAME, p.now.x(), p.now.y(), p.now.w(), p.now.h(), a);
		boolean hover = canHover && p.now.contains(mouseX, mouseY);
		if (hover && !this.miniHovered) UiSounds.hover();
		this.miniHovered = hover;
		if (hover) {
			float pulse = 0.6f + 0.4f * Mth.sin(time() * 5f);
			Draw.sprite(g, Theme.MAP_FRAME_GLOW, p.now.x(), p.now.y(), p.now.w(), p.now.h(), a * pulse);
		}
		Component region = IsekaiInventoryScreen.regionName(this.minecraft, this.player);
		p.title = region;
		Panels.tab(g, this.font, p.now.x() + 4, p.now.y() - TAB, p.now.w() - 30, region, a);
		SkyDial.draw(g, this.font, p.now.right() - 9, p.now.y() + 1, a);
		if (Instruments.showCoordinates()) {
			BlockPos bp = this.player.blockPosition();
			Component coords = Component.literal("X " + bp.getX() + "   Y " + bp.getY() + "   Z " + bp.getZ());
			Draw.textCentered(g, this.font, coords, p.now.cx(), p.now.bottom() + 3, Draw.alpha(Theme.TEXT_DIM, a), true);
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

	private void drawEditor(GuiGraphics g, Rect e, float a, int mouseX, int mouseY, float partialTick, boolean canHover) {
		this.hoveredEditorPart = 0;
		if (this.editingTab < 0) return;
		int t = this.editingTab;
		int count = PersonalChest.tabCount();
		boolean inside = canHover && e.contains(mouseX, mouseY);
		Rect icon = editorIcon(e);
		Draw.sprite(g, Theme.SLOT, icon.x(), icon.y(), 18, 18, a);
		if (inside && icon.contains(mouseX, mouseY)) {
			this.hoveredEditorPart = 1;
			Draw.fill(g, icon.x() + 1, icon.y() + 1, icon.right() - 1, icon.bottom() - 1, Draw.alpha(0x30FFF2CF, a));
		}
		if (a > 0.5f) g.renderItem(PersonalChest.tabIcon(t), icon.x() + 1, icon.y() + 1);

		Rect name = editorName(e);
		boolean focused = this.tabName != null && this.tabName.isFocused();
		Draw.fill(g, name.x(), name.y(), name.right(), name.bottom(), Draw.alpha(0xC00A080E, a));
		Draw.fill(g, name.x(), name.bottom() - 1, name.right(), name.bottom(), Draw.alpha(Theme.GOLD, a * (focused ? 1f : 0.5f)));
		if (this.tabName != null) {
			this.tabName.setX(name.x() + 3);
			this.tabName.setY(name.y() + 3);
			this.tabName.setWidth(name.w() - 6);
			this.tabName.render(g, mouseX, mouseY, partialTick);
		}

		Rect prev = editorPrev(e);
		Rect next = editorNext(e);
		Rect del = editorDelete(e);
		boolean hp = inside && prev.contains(mouseX, mouseY);
		boolean hn = inside && next.contains(mouseX, mouseY);
		boolean hd = inside && del.contains(mouseX, mouseY);
		if (hp) this.hoveredEditorPart = 2;
		if (hn) this.hoveredEditorPart = 3;
		if (hd) this.hoveredEditorPart = 4;
		smallButton(g, prev, "prev", hp, t > 0, 0, a);
		smallButton(g, next, "next", hn, t < count - 1, 0, a);
		Draw.text(g, this.font, Component.translatable("isekai_ui.personal.move"), next.right() + 5, prev.y() + 3, Draw.alpha(Theme.TEXT_DIM, a), false);
		boolean armed = this.confirmDeleteTab == t && System.nanoTime() - this.confirmNanos < CONFIRM_NANOS;
		smallButton(g, del, "trash", hd, true, armed ? 0xFFE0465E : 0, a);

		Component line1;
		int color1 = Theme.TEXT_DIM;
		if (armed) {
			int items = PersonalChest.count(t);
			line1 = items > 0 ? Component.translatable("isekai_ui.personal.delete_confirm_items", items) : Component.translatable("isekai_ui.personal.delete_confirm");
			color1 = 0xFFE0465E;
		} else {
			line1 = Component.translatable("isekai_ui.personal.icon_hint");
		}
		Draw.textFit(g, this.font, line1, e.cx(), e.bottom() - 24, e.w() - 12, Draw.alpha(color1, a), false);
		Draw.textFit(g, this.font, Component.translatable("isekai_ui.personal.keys_hint"), e.cx(), e.bottom() - 13, e.w() - 12,
				Draw.alpha(0xFF8E8472, a), false);
	}

	private static void smallButton(GuiGraphics g, Rect r, String icon, boolean hover, boolean enabled, int tintOverride, float a) {
		boolean lit = hover && enabled;
		Draw.fill(g, r.x(), r.y(), r.right(), r.bottom(), Draw.alpha(lit ? 0xC0473A63 : 0x900A080E, a));
		Draw.fill(g, r.x(), r.bottom() - 1, r.right(), r.bottom(), Draw.alpha(lit ? 0xFFF2C76A : 0xFF3A3346, a));
		int tint = tintOverride != 0 ? tintOverride : (!enabled ? 0xFF5E5868 : (hover ? Theme.TEXT_GOLD : Theme.IVORY));
		Draw.sprite(g, Theme.icon(icon), r.x() + (r.w() - 12) / 2, r.y() + (r.h() - 12) / 2, 12, 12, Draw.alpha(tint, a));
	}

	private void drawToast(GuiGraphics g) {
		if (this.toast == null || this.library == null) return;
		long age = System.nanoTime() - this.toastNanos;
		if (age > TOAST_NANOS) {
			this.toast = null;
			return;
		}
		float a = Math.min(1f, (TOAST_NANOS - age) / 400_000_000f) * this.library.alpha;
		Rect c = this.library.now;
		int maxW = c.w() - 24;
		int w = Math.min(maxW, this.font.width(this.toast)) + 12;
		int y = c.bottom() - 21;
		Draw.push(g);
		Draw.layer(g, 300f);
		Draw.sprite(g, Theme.PILL, c.cx() - w / 2, y, w, 13, a);
		Draw.textFit(g, this.font, this.toast, c.cx(), y + 3, maxW, Draw.alpha(Theme.TEXT_GOLD, a), false);
		Draw.pop(g);
	}

	@Override
	protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		drawToast(g);
		if (isClosing() || isDraggingPanel()) return;
		if (this.hoveredButton >= 0 && this.hoveredButton < this.buttons.size()) {
			this.setTooltipForNextRenderPass(this.buttons.get(this.hoveredButton).tooltip());
			return;
		}
		Panel over = overlayAt(mouseX, mouseY);
		if (over != null) {
			Component tip = over == this.commands ? this.commandList.tooltip()
					: over == this.armor ? this.armorList.tooltip()
					: over == this.editor ? editorTooltip() : null;
			if (tip != null) this.setTooltipForNextRenderPass(tip);
			return;
		}
		if (this.miniHovered) {
			this.setTooltipForNextRenderPass(Component.translatable("isekai_ui.map.open_hint", IsekaiKeys.mapKeyName()));
			return;
		}
		if (this.hoveredMode >= 0) {
			this.setTooltipForNextRenderPass(Component.translatable(this.hoveredMode == 0 ? "isekai_ui.creative.mode.catalog_tip" : "isekai_ui.creative.mode.personal_tip"));
			return;
		}
		if (this.hoveredTabCell != CELL_NONE) {
			g.renderComponentTooltip(this.font, tabTooltip(this.hoveredTabCell), mouseX, mouseY);
			return;
		}
		if (!this.menu.getCarried().isEmpty() || this.hoveredCell < 0) return;
		ItemStack s = cellItem(this.hoveredCell);
		if (!personalMode) {
			if (!s.isEmpty()) g.renderTooltip(this.font, getTooltipFromItem(this.minecraft, s), s.getTooltipImage(), mouseX, mouseY);
			return;
		}
		List<Component> lines = new ArrayList<>();
		if (!s.isEmpty()) lines.addAll(getTooltipFromItem(this.minecraft, s));
		int[] ref = cellRef(this.hoveredCell);
		if (personalTab < 0 && ref != null) {
			lines.add(Component.translatable("isekai_ui.personal.in_tab", PersonalChest.tabName(ref[0])).withStyle(ChatFormatting.GOLD));
		}
		lines.add(Component.translatable(s.isEmpty() ? "isekai_ui.creative.personal_empty_tip" : "isekai_ui.creative.personal_tip")
				.withStyle(ChatFormatting.DARK_GRAY));
		g.renderTooltip(this.font, lines, s.isEmpty() ? Optional.empty() : s.getTooltipImage(), mouseX, mouseY);
	}

	private List<Component> tabTooltip(int code) {
		List<Component> lines = new ArrayList<>();
		if (!personalMode) {
			lines.add(code == CELL_SEARCH ? Component.translatable("isekai_ui.creative.search") : this.tabs.get(code).getDisplayName());
			return lines;
		}
		if (code == CELL_SEARCH) {
			lines.add(Component.translatable("isekai_ui.personal.all_tab"));
			return lines;
		}
		if (code == CELL_PLUS) {
			lines.add(Component.translatable("isekai_ui.personal.add_tab"));
			return lines;
		}
		lines.add(Component.literal(PersonalChest.tabName(code)).withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("isekai_ui.personal.tab_tip_count", PersonalChest.count(code), PersonalChest.SLOTS).withStyle(ChatFormatting.GRAY));
		if (!this.menu.getCarried().isEmpty()) lines.add(Component.translatable("isekai_ui.personal.tab_tip_store").withStyle(ChatFormatting.GREEN));
		lines.add(Component.translatable("isekai_ui.personal.tab_tip_edit").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private Component editorTooltip() {
		return switch (this.hoveredEditorPart) {
			case 1 -> Component.translatable("isekai_ui.personal.editor.icon");
			case 2 -> Component.translatable("isekai_ui.personal.editor.prev");
			case 3 -> Component.translatable("isekai_ui.personal.editor.next");
			case 4 -> Component.translatable("isekai_ui.personal.editor.delete");
			default -> null;
		};
	}

	// ------------------------------------------------------------------ editor de aba

	private void openEditor(int t, boolean selectAll) {
		if (t < 0 || t >= PersonalChest.tabCount()) return;
		closeEditor(true);
		IsekaiInventoryScreen.commandsOpen = false;
		IsekaiInventoryScreen.armorOpen = false;
		this.editingTab = t;
		this.editorAppearAt = time();
		personalTab = t;
		refresh();
		UiSounds.toggle();
		rebuildWidgets();
		focusTabName();
		if (selectAll && this.tabName != null) {
			this.tabName.moveCursorToEnd(false);
			this.tabName.setHighlightPos(0);
		}
	}

	private void focusTabName() {
		if (this.tabName == null) return;
		setFocused(this.tabName);
		this.tabName.setFocused(true);
	}

	/** Fecha o editor de aba; "save" grava o nome digitado. */
	private void closeEditor(boolean save) {
		if (this.editingTab < 0) return;
		int t = this.editingTab;
		EditBox box = this.tabName;
		this.editingTab = -1;
		this.tabName = null;
		this.confirmDeleteTab = -1;
		if (box != null) {
			if (save) PersonalChest.renameTab(t, box.getValue());
			removeWidget(box);
			if (getFocused() == box) setFocused(null);
		}
		if (this.editor != null) this.editor.visible = false;
	}

	private boolean clickEditor(Rect e, double mouseX, double mouseY) {
		if (this.editingTab < 0) return false;
		int t = this.editingTab;
		if (editorIcon(e).contains(mouseX, mouseY)) {
			// com um item na mao ele vira o icone; com a mao vazia o icone volta a ser automatico
			PersonalChest.setTabIcon(t, this.menu.getCarried());
			UiSounds.toggle();
			return true;
		}
		if (editorName(e).contains(mouseX, mouseY)) {
			focusTabName();
			return true;
		}
		if (editorPrev(e).contains(mouseX, mouseY) || editorNext(e).contains(mouseX, mouseY)) {
			int delta = editorPrev(e).contains(mouseX, mouseY) ? -1 : 1;
			int to = PersonalChest.moveTab(t, delta);
			if (to != t) {
				this.editingTab = to;
				personalTab = to;
				refresh();
				UiSounds.click();
			}
			return true;
		}
		if (editorDelete(e).contains(mouseX, mouseY)) {
			long now = System.nanoTime();
			if (this.confirmDeleteTab == t && now - this.confirmNanos < CONFIRM_NANOS) {
				closeEditor(false);
				PersonalChest.removeTab(t);
				personalTab = Math.min(t, PersonalChest.tabCount() - 1);
				this.scrollRow = 0;
				refresh();
				UiSounds.toggle();
			} else {
				this.confirmDeleteTab = t;
				this.confirmNanos = now;
				UiSounds.click();
			}
			return true;
		}
		return true; // clique dentro do editor nao passa para o que esta atras
	}

	// ------------------------------------------------------------------ cliques

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.editingTab >= 0 && this.editor != null && this.editor.visible && !isClosing()
				&& !this.editor.grabArea().contains(mouseX, mouseY)) {
			// pegar um item (para virar icone) nao fecha o editor; clicar em qualquer outra coisa fecha
			boolean onItem = this.hoveredSlot != null || cellAt(this.library.now, mouseX, mouseY) >= 0;
			if (!onItem) closeEditor(true);
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

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
		if (p == this.armor) return this.armorList.click(p.now, mouseX, mouseY, button);
		if (p == this.editor) return clickEditor(p.now, mouseX, mouseY);
		if (p == this.library) return clickLibrary(p.now, mouseX, mouseY, button);
		return false;
	}

	private int tabCellAt(Rect c, double mouseX, double mouseY) {
		int[] cells = tabCells();
		int per = perRow(c);
		int first = this.tabRowOffset * per;
		for (int k = 0; k < TAB_ROWS * per && first + k < cells.length; k++) {
			if (tabRect(c, k).contains(mouseX, mouseY)) return cells[first + k];
		}
		return CELL_NONE;
	}

	/** Celula da grade embaixo do mouse (indice absoluto), ou -1. */
	private int cellAt(Rect c, double mouseX, double mouseY) {
		int cols = cols();
		int gx = gridX(c) - 1;
		int gy = gridY(c) - 1;
		if (mouseX < gx || mouseY < gy) return -1;
		int col = (int) ((mouseX - gx) / SLOT);
		int row = (int) ((mouseY - gy) / SLOT);
		if (col >= cols || row >= ROWS) return -1;
		int idx = (this.scrollRow + row) * cols + col;
		if (personalMode) return tabView() && idx >= PersonalChest.SLOTS ? -1 : idx;
		return idx < this.shown.size() ? idx : -1;
	}

	private boolean clickLibrary(Rect c, double mouseX, double mouseY, int button) {
		for (int m = 0; m < 2; m++) {
			if (modeTabRect(c, m).contains(mouseX, mouseY)) {
				if (button == 0) switchMode(m == 1);
				return true;
			}
		}
		int code = tabCellAt(c, mouseX, mouseY);
		if (code != CELL_NONE) {
			if (personalMode) clickPersonalTab(code, button);
			else clickCatalogTab(code);
			return true;
		}
		if (searchRect(c).contains(mouseX, mouseY)) {
			setFocused(this.search);
			this.search.setFocused(true);
			if (button == 1) this.search.setValue("");
			return true;
		}
		int cell = cellAt(c, mouseX, mouseY);
		if (cell >= 0) {
			clickCell(cell, button);
			return true;
		}
		return false;
	}

	private void clickCatalogTab(int code) {
		selectedTab = code == CELL_SEARCH ? null : this.tabs.get(code);
		if (selectedTab != null) this.search.setValue("");
		this.scrollRow = 0;
		refresh();
		UiSounds.click();
		if (selectedTab == null) {
			setFocused(this.search);
			this.search.setFocused(true);
		}
	}

	private void clickPersonalTab(int code, int button) {
		if (code == CELL_PLUS) {
			int t = PersonalChest.addTab(null);
			if (t < 0) {
				showToast(Component.translatable("isekai_ui.personal.tabs_full"));
				return;
			}
			personalTab = t;
			this.search.setValue("");
			this.scrollRow = 0;
			openEditor(t, true);
			return;
		}
		if (code == CELL_SEARCH) {
			personalTab = CELL_SEARCH;
			this.scrollRow = 0;
			refresh();
			UiSounds.click();
			setFocused(this.search);
			this.search.setFocused(true);
			return;
		}
		if (button == 1) {
			openEditor(code, false);
			return;
		}
		ItemStack carried = this.menu.getCarried();
		if (button == 0 && !carried.isEmpty()) {
			// soltar o item da mao em cima da aba: guarda nela
			if (PersonalChest.addTo(code, carried) >= 0) {
				this.menu.setCarried(ItemStack.EMPTY);
				showToast(Component.translatable("isekai_ui.personal.stored_in", PersonalChest.tabName(code)));
				UiSounds.toggle();
			} else {
				showToast(Component.translatable("isekai_ui.personal.tab_full", PersonalChest.tabName(code)));
			}
		} else {
			UiSounds.click();
		}
		personalTab = code;
		this.scrollRow = 0;
		if (!this.search.getValue().isEmpty()) this.search.setValue("");
		refresh();
	}

	private void clickCell(int idx, int button) {
		if (!personalMode) {
			if (idx < this.shown.size()) clickCatalogItem(this.shown.get(idx), button);
			return;
		}
		int[] ref = cellRef(idx);
		if (ref != null) {
			clickPersonal(ref[0], ref[1], button);
		} else if (!this.menu.getCarried().isEmpty() && button != 2) {
			// espaco vazio da lista de busca: guarda o item na ultima aba aberta
			int t = PersonalChest.addCopy(this.menu.getCarried(), lastRealTab);
			if (t >= 0) {
				this.menu.setCarried(ItemStack.EMPTY);
				showToast(Component.translatable("isekai_ui.personal.stored_in", PersonalChest.tabName(t)));
				UiSounds.toggle();
			} else {
				showToast(Component.translatable("isekai_ui.personal.chest_full"));
			}
		}
		if (personalTab < 0) refresh();
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
	private void clickPersonal(int t, int slot, int button) {
		ItemStack stored = PersonalChest.get(t, slot);
		ItemStack carried = this.menu.getCarried();
		if (button == 2) {
			if (carried.isEmpty() && !stored.isEmpty()) this.menu.setCarried(stored.copyWithCount(stored.getMaxStackSize()));
			return;
		}
		if (carried.isEmpty()) {
			if (stored.isEmpty()) return;
			if (button == 1) {
				this.menu.setCarried(stored.copy());
				PersonalChest.set(t, slot, ItemStack.EMPTY);
			} else if (hasShiftDown()) {
				giveToInventory(stored.copy());
			} else {
				this.menu.setCarried(stored.copy());
			}
			UiSounds.hover();
			return;
		}
		if (stored.isEmpty()) {
			PersonalChest.set(t, slot, carried.copy());
			this.menu.setCarried(ItemStack.EMPTY);
		} else if (ItemStack.isSameItemSameComponents(stored, carried)) {
			int total = Math.min(stored.getMaxStackSize(), stored.getCount() + carried.getCount());
			int moved = total - stored.getCount();
			PersonalChest.set(t, slot, stored.copyWithCount(total));
			carried.shrink(moved);
		} else {
			PersonalChest.set(t, slot, carried.copy());
			this.menu.setCarried(stored.copy());
		}
		UiSounds.toggle();
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
			// shift + clique num item do inventario: guarda uma copia no bau pessoal e mostra onde ela foi parar
			int t = PersonalChest.addCopy(slot.getItem(), personalTab >= 0 ? personalTab : lastRealTab);
			if (t >= 0) {
				if (!personalMode) switchMode(true);
				personalTab = t;
				this.scrollRow = 0;
				if (!this.search.getValue().isEmpty()) this.search.setValue("");
				refresh();
				showToast(Component.translatable("isekai_ui.personal.copied_to", PersonalChest.tabName(t)));
				UiSounds.toggle();
			} else {
				showToast(Component.translatable("isekai_ui.personal.chest_full"));
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
		Panel over = overlayAt(mouseX, mouseY);
		if (over == this.commands) return this.commandList.scroll(scrollY);
		if (over == this.armor) return this.armorList.scroll(scrollY);
		if (over != null) return true;
		Rect c = this.library.now;
		if (c.contains(mouseX, mouseY)) {
			int maxOff = maxTabOffset(tabCells().length, perRow(c));
			if (maxOff > 0 && tabArea(c).contains(mouseX, mouseY)) {
				this.tabRowOffset = Mth.clamp(this.tabRowOffset - (int) Math.signum(scrollY), 0, maxOff);
				return true;
			}
			this.scrollRow = Mth.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean editorTyping() {
		return this.editingTab >= 0 && this.tabName != null && this.tabName.isFocused();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		this.ignoreTextInput = false;
		if (isClosing()) return true;
		if (this.commandList.isTyping()) return this.commandList.keyPressed(keyCode, scanCode, modifiers);
		if (this.armorList.isTyping()) return this.armorList.keyPressed(keyCode, scanCode, modifiers);
		if (this.editingTab >= 0) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				closeEditor(true);
				UiSounds.toggle();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				closeEditor(false);
				return true;
			}
			if (editorTyping()) return this.tabName.keyPressed(keyCode, scanCode, modifiers) || this.tabName.canConsumeInput();
		}
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
		int[] ref = cellRef(this.hoveredCell);
		if (this.minecraft.options.keyDrop.matches(keyCode, scanCode) && ref != null) {
			// Q em cima de um item do bau pessoal: apaga do bau
			if (!PersonalChest.get(ref[0], ref[1]).isEmpty()) {
				PersonalChest.set(ref[0], ref[1], ItemStack.EMPTY);
				if (personalTab < 0) refresh();
				UiSounds.click();
			}
			return true;
		}
		// teclas 1-9 em cima de um item do catalogo/bau pessoal: pilha cheia direto naquele slot da barra
		ItemStack hovered = cellItem(this.hoveredCell);
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
		if (IsekaiKeys.isMapKey(keyCode, scanCode)) {
			openMap();
			return true;
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
		if (this.search.isFocused() || this.commandList.isTyping() || this.armorList.isTyping() || editorTyping()) {
			return super.charTyped(c, modifiers);
		}
		// comecar a digitar com o mouse livre ja vai para a busca (como no criativo vanilla)
		if (Character.isLetter(c)) {
			if (personalMode) personalTab = CELL_SEARCH;
			else selectedTab = null;
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

	/** Guarda o item da mao no slot da aba aberta do bau pessoal (abre o bau pessoal se preciso). */
	public void debugStoreCarried(int slot) {
		switchMode(true);
		if (personalTab < 0) {
			personalTab = lastRealTab;
			refresh();
		}
		clickPersonal(personalTab, slot, 0);
	}

	public void debugTakeCopy(int slot) {
		ItemStack stored = PersonalChest.get(Math.max(0, personalTab), slot);
		if (!stored.isEmpty()) giveToInventory(stored.copy());
	}

	public int debugShownCount() {
		return this.shown.size();
	}

	public CommandList debugCommands() {
		return this.commandList;
	}

	public void debugSetPersonal(boolean personal) {
		switchMode(personal);
	}

	public void debugSelectTab(int t) {
		switchMode(true);
		personalTab = t;
		this.scrollRow = 0;
		if (!this.search.getValue().isEmpty()) this.search.setValue("");
		refresh();
	}

	/** Cria uma aba pelo botao "+" e digita o nome, como o jogador faria. Devolve o numero da aba. */
	public int debugNewTab(String name) {
		switchMode(true);
		clickPersonalTab(CELL_PLUS, 0);
		if (this.tabName != null) this.tabName.setValue(name);
		closeEditor(true);
		return personalTab;
	}

	public void debugOpenTabEditor(int t) {
		switchMode(true);
		openEditor(t, false);
	}

	public void debugCloseTabEditor() {
		closeEditor(true);
	}

	public int debugPersonalTab() {
		return personalTab;
	}

	public int debugColumns() {
		return cols();
	}

	/** Abre a janela de conjuntos e veste o conjunto pelo numero (copias, como no criativo). */
	public int debugEquipSet(int index) {
		if (!IsekaiInventoryScreen.armorOpen) toggleArmor();
		return this.armorList.debugEquip(index);
	}
}
