package com.compelium.isekaiui.creative;

import com.compelium.isekaiui.IsekaiUI;
import com.compelium.isekaiui.map.MapManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Bau pessoal do criativo: copias de itens (inclusive os personalizados do servidor, com nome, lore e
 * encantamentos) guardadas no SEU computador, uma lista por servidor, separadas em abas com nome e icone.
 * Tirar um item do bau entrega uma copia pelo mesmo mecanismo do inventario criativo do jogo.
 */
public final class PersonalChest {
	public static final int SLOTS = 54;
	public static final int MAX_TABS = 15;
	private static final ItemStack DEFAULT_ICON = new ItemStack(Items.CHEST);

	public static final class Tab {
		public String name;
		public ItemStack icon = ItemStack.EMPTY;
		public final ItemStack[] items = new ItemStack[SLOTS];

		Tab(String name) {
			this.name = name;
			Arrays.fill(this.items, ItemStack.EMPTY);
		}
	}

	private static final List<Tab> tabs = new ArrayList<>();
	private static String loadedFor;

	private PersonalChest() {
	}

	private static List<Tab> tabs() {
		ensureLoaded();
		if (tabs.isEmpty()) tabs.add(new Tab(Component.translatable("isekai_ui.personal.default_tab").getString()));
		return tabs;
	}

	public static int tabCount() {
		return tabs().size();
	}

	public static String tabName(int t) {
		List<Tab> list = tabs();
		return t >= 0 && t < list.size() ? list.get(t).name : "";
	}

	/** Icone da aba: o escolhido, ou o primeiro item guardado nela, ou um bau. */
	public static ItemStack tabIcon(int t) {
		List<Tab> list = tabs();
		if (t < 0 || t >= list.size()) return DEFAULT_ICON;
		Tab tab = list.get(t);
		if (!tab.icon.isEmpty()) return tab.icon;
		for (ItemStack s : tab.items) if (!s.isEmpty()) return s;
		return DEFAULT_ICON;
	}

	public static boolean tabEmpty(int t) {
		List<Tab> list = tabs();
		if (t < 0 || t >= list.size()) return true;
		for (ItemStack s : list.get(t).items) if (!s.isEmpty()) return false;
		return true;
	}

	public static ItemStack get(int t, int slot) {
		List<Tab> list = tabs();
		if (t < 0 || t >= list.size() || slot < 0 || slot >= SLOTS) return ItemStack.EMPTY;
		return list.get(t).items[slot];
	}

	public static void set(int t, int slot, ItemStack stack) {
		List<Tab> list = tabs();
		if (t < 0 || t >= list.size() || slot < 0 || slot >= SLOTS) return;
		list.get(t).items[slot] = stack == null ? ItemStack.EMPTY : stack;
		save();
	}

	/** Nova aba no fim; devolve o numero dela ou -1 se ja tiver o maximo. */
	public static int addTab(String name) {
		List<Tab> list = tabs();
		if (list.size() >= MAX_TABS) return -1;
		String n = name == null || name.isBlank() ? Component.translatable("isekai_ui.personal.new_tab", list.size() + 1).getString() : name.strip();
		list.add(new Tab(n));
		save();
		return list.size() - 1;
	}

	public static void renameTab(int t, String name) {
		List<Tab> list = tabs();
		if (t < 0 || t >= list.size() || name == null || name.isBlank()) return;
		list.get(t).name = name.strip();
		save();
	}

	public static void setTabIcon(int t, ItemStack icon) {
		List<Tab> list = tabs();
		if (t < 0 || t >= list.size()) return;
		list.get(t).icon = icon == null || icon.isEmpty() ? ItemStack.EMPTY : icon.copyWithCount(1);
		save();
	}

	public static void removeTab(int t) {
		List<Tab> list = tabs();
		if (t < 0 || t >= list.size()) return;
		list.remove(t);
		save();
	}

	/** Troca a aba de lugar com a vizinha (delta -1 ou +1). Devolve a nova posicao. */
	public static int moveTab(int t, int delta) {
		List<Tab> list = tabs();
		int to = t + delta;
		if (t < 0 || t >= list.size() || to < 0 || to >= list.size()) return t;
		Tab tab = list.remove(t);
		list.add(to, tab);
		save();
		return to;
	}

	/** Guarda uma copia no primeiro espaco livre, comecando pela aba indicada. Devolve a aba usada ou -1. */
	public static int addCopy(ItemStack stack, int fromTab) {
		List<Tab> list = tabs();
		if (stack == null || stack.isEmpty()) return -1;
		for (int k = 0; k < list.size(); k++) {
			int t = Math.floorMod(fromTab + k, list.size());
			ItemStack[] items = list.get(t).items;
			for (int i = 0; i < SLOTS; i++) {
				if (items[i].isEmpty()) {
					items[i] = stack.copy();
					save();
					return t;
				}
			}
		}
		return -1;
	}

	/** Guarda uma copia no primeiro espaco livre DESTA aba. Devolve o slot usado ou -1 se a aba estiver cheia. */
	public static int addTo(int t, ItemStack stack) {
		List<Tab> list = tabs();
		if (stack == null || stack.isEmpty() || t < 0 || t >= list.size()) return -1;
		ItemStack[] items = list.get(t).items;
		for (int i = 0; i < SLOTS; i++) {
			if (items[i].isEmpty()) {
				items[i] = stack.copy();
				save();
				return i;
			}
		}
		return -1;
	}

	/** Quantos itens a aba tem guardados. */
	public static int count(int t) {
		List<Tab> list = tabs();
		if (t < 0 || t >= list.size()) return 0;
		int n = 0;
		for (ItemStack s : list.get(t).items) if (!s.isEmpty()) n++;
		return n;
	}

	/** Todos os itens guardados, aba por aba. Cada resultado e {aba, slot}. */
	public static List<int[]> all() {
		List<int[]> out = new ArrayList<>();
		List<Tab> list = tabs();
		for (int t = 0; t < list.size(); t++) {
			ItemStack[] items = list.get(t).items;
			for (int i = 0; i < SLOTS; i++) if (!items[i].isEmpty()) out.add(new int[]{t, i});
		}
		return out;
	}

	/** Busca em todas as abas pelo nome do item (ou id). Cada resultado e {aba, slot}. */
	public static List<int[]> search(String query) {
		List<int[]> out = new ArrayList<>();
		String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return out;
		List<Tab> list = tabs();
		for (int t = 0; t < list.size(); t++) {
			ItemStack[] items = list.get(t).items;
			for (int i = 0; i < SLOTS; i++) {
				ItemStack s = items[i];
				if (s.isEmpty()) continue;
				if (s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
						|| BuiltInRegistries.ITEM.getKey(s.getItem()).toString().contains(q)
						|| list.get(t).name.toLowerCase(Locale.ROOT).contains(q)) {
					out.add(new int[]{t, i});
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ arquivo

	private static HolderLookup.Provider registries() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) return mc.level.registryAccess();
		return null;
	}

	private static Path file(String serverKey) {
		return MapManager.serverDir(serverKey).resolve("bau_pessoal.nbt");
	}

	private static void ensureLoaded() {
		Minecraft mc = Minecraft.getInstance();
		String key = MapManager.serverKey(mc);
		if (key.equals(loadedFor)) return;
		HolderLookup.Provider regs = registries();
		if (regs == null) return;
		loadedFor = key;
		tabs.clear();
		Path f = file(key);
		if (!Files.exists(f)) return;
		try {
			CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
			if (root.contains("Tabs", Tag.TAG_LIST)) {
				ListTag list = root.getList("Tabs", Tag.TAG_COMPOUND);
				for (int i = 0; i < list.size() && tabs.size() < MAX_TABS; i++) {
					CompoundTag t = list.getCompound(i);
					Tab tab = new Tab(t.getString("Name"));
					if (t.contains("Icon", Tag.TAG_COMPOUND)) ItemStack.parse(regs, t.getCompound("Icon")).ifPresent(s -> tab.icon = s);
					ListTag items = t.getList("Items", Tag.TAG_COMPOUND);
					for (int k = 0; k < items.size(); k++) {
						CompoundTag e = items.getCompound(k);
						int slot = e.getInt("Slot");
						if (slot < 0 || slot >= SLOTS) continue;
						ItemStack.parse(regs, e.getCompound("Item")).ifPresent(s -> tab.items[slot] = s);
					}
					tabs.add(tab);
				}
			} else {
				// formato antigo (v0.3): 20 paginas de 27 -> cada 2 paginas viram uma aba de 54
				ListTag items = root.getList("Items", Tag.TAG_COMPOUND);
				for (int k = 0; k < items.size(); k++) {
					CompoundTag e = items.getCompound(k);
					int page = e.getInt("Page");
					int slot = e.getInt("Slot");
					if (page < 0 || page >= 40 || slot < 0 || slot >= 27) continue;
					int t = page / 2;
					while (tabs.size() <= t && tabs.size() < MAX_TABS) {
						tabs.add(new Tab(Component.translatable("isekai_ui.personal.new_tab", tabs.size() + 1).getString()));
					}
					if (t >= tabs.size()) continue;
					int s = (page % 2) * 27 + slot;
					ItemStack.parse(regs, e.getCompound("Item")).ifPresent(st -> tabs.get(t).items[s] = st);
				}
			}
		} catch (Exception ex) {
			IsekaiUI.LOG.warn("Nao consegui ler o bau pessoal {}", f, ex);
		}
	}

	private static void save() {
		HolderLookup.Provider regs = registries();
		if (loadedFor == null || regs == null) return;
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		root.putInt("Format", 2);
		ListTag list = new ListTag();
		for (Tab tab : tabs) {
			CompoundTag t = new CompoundTag();
			t.putString("Name", tab.name);
			if (!tab.icon.isEmpty()) t.put("Icon", tab.icon.save(regs));
			ListTag items = new ListTag();
			for (int i = 0; i < SLOTS; i++) {
				ItemStack s = tab.items[i];
				if (s.isEmpty()) continue;
				CompoundTag e = new CompoundTag();
				e.putInt("Slot", i);
				e.put("Item", s.save(regs));
				items.add(e);
			}
			t.put("Items", items);
			list.add(t);
		}
		root.put("Tabs", list);
		Path f = file(loadedFor);
		try {
			Files.createDirectories(f.getParent());
			NbtIo.writeCompressed(root, f);
		} catch (Exception ex) {
			IsekaiUI.LOG.warn("Nao consegui salvar o bau pessoal {}", f, ex);
		}
	}
}
