package com.compelium.isekaiui.recipes;

import com.compelium.isekaiui.IsekaiUI;
import com.compelium.isekaiui.map.MapManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grimorio de receitas: so guarda receitas que o servidor realmente aceitou (o resultado apareceu de verdade
 * na bancada, fornalha, suporte de pocoes, ferraria ou cortador). Um arquivo por servidor/mundo.
 * Tambem lembra as receitas "do servidor" que foram testadas e nao funcionaram (para esconder).
 */
public final class RecipeJournal {
	private static final int MAX_ENTRIES = 4000;

	/** Onde a receita e feita. */
	public enum Station {
		CRAFTING(Items.CRAFTING_TABLE),
		SMELTING(Items.FURNACE),
		BLASTING(Items.BLAST_FURNACE),
		SMOKING(Items.SMOKER),
		BREWING(Items.BREWING_STAND),
		SMITHING(Items.SMITHING_TABLE),
		STONECUTTING(Items.STONECUTTER);

		public final Item icon;

		Station(Item icon) {
			this.icon = icon;
		}

		public boolean isFurnace() {
			return this == SMELTING || this == BLASTING || this == SMOKING;
		}

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Uma receita que funcionou. inputs: crafting = grade w x h (vazios permitidos); fornalha/cortador = [entrada];
	 * pocao = [ingrediente, pocao base]; ferraria = [molde, base, adicional]. */
	public static final class Entry {
		public final Station station;
		public final int w;
		public final int h;
		public final ItemStack[] inputs;
		public final ItemStack result;
		public long seen;
		final String key;

		Entry(Station station, int w, int h, ItemStack[] inputs, ItemStack result, long seen, String key) {
			this.station = station;
			this.w = w;
			this.h = h;
			this.inputs = inputs;
			this.result = result;
			this.seen = seen;
			this.key = key;
		}
	}

	private static final List<Entry> entries = new ArrayList<>();
	private static final Map<String, Entry> byKey = new HashMap<>();
	private static final Set<String> fakes = new HashSet<>();
	private static String loadedFor;
	private static long dirtySince = -1L;
	/** Muda sempre que o grimorio muda (a tela do livro sabe quando refazer a lista). */
	private static int version;

	private RecipeJournal() {
	}

	public static List<Entry> all() {
		ensureLoaded();
		return entries;
	}

	public static int size() {
		ensureLoaded();
		return entries.size();
	}

	public static int version() {
		return version;
	}

	public static int fakeCount() {
		ensureLoaded();
		return fakes.size();
	}

	public static boolean isFake(String signature) {
		ensureLoaded();
		return fakes.contains(signature);
	}

	public static void markFake(String signature) {
		ensureLoaded();
		if (fakes.add(signature)) changed();
	}

	public static void clearFakes() {
		ensureLoaded();
		if (fakes.isEmpty()) return;
		fakes.clear();
		changed();
	}

	public static boolean knows(Station station, ItemStack[] inputs, ItemStack result) {
		ensureLoaded();
		return byKey.containsKey(key(station, inputs, result));
	}

	/**
	 * Anota uma receita que acabou de funcionar. Devolve a receita se ela era nova (null se ja era conhecida).
	 * Os itens ja devem estar "limpos" (quantidade 1 nas entradas, sem desgaste).
	 */
	public static Entry learn(Station station, int w, int h, ItemStack[] inputs, ItemStack result) {
		ensureLoaded();
		if (loadedFor == null || result.isEmpty()) return null;
		String key = key(station, inputs, result);
		Entry known = byKey.get(key);
		if (known != null) {
			known.seen = System.currentTimeMillis();
			return null;
		}
		if (entries.size() >= MAX_ENTRIES) return null;
		ItemStack[] copy = new ItemStack[inputs.length];
		for (int i = 0; i < inputs.length; i++) copy[i] = inputs[i] == null ? ItemStack.EMPTY : inputs[i].copy();
		Entry e = new Entry(station, w, h, copy, result.copy(), System.currentTimeMillis(), key);
		entries.add(e);
		byKey.put(key, e);
		changed();
		return e;
	}

	/** Copia "limpa" de um ingrediente: 1 unidade e sem desgaste/custo de reparo (o resto e mantido). */
	public static ItemStack clean(ItemStack s) {
		if (s == null || s.isEmpty()) return ItemStack.EMPTY;
		ItemStack c = s.copyWithCount(1);
		DataComponentMap proto = c.getItem().components();
		c.set(DataComponents.DAMAGE, proto.get(DataComponents.DAMAGE));
		c.set(DataComponents.REPAIR_COST, proto.get(DataComponents.REPAIR_COST));
		return c;
	}

	/** Chave da receita: estacao + ingredientes (sem ordem) + resultado. Duas arrumacoes da mesma receita = uma so. */
	public static String key(Station station, ItemStack[] inputs, ItemStack result) {
		List<String> parts = new ArrayList<>();
		for (ItemStack s : inputs) if (s != null && !s.isEmpty()) parts.add(signature(s));
		String[] arr = parts.toArray(new String[0]);
		if (station == Station.CRAFTING) Arrays.sort(arr);
		return station.name() + "|" + String.join(";", arr) + "|" + signature(result) + "x" + result.getCount();
	}

	/** Texto que identifica um item com todos os detalhes (nome, encantamentos, dados do servidor...). */
	public static String signature(ItemStack s) {
		if (s == null || s.isEmpty()) return "-";
		HolderLookup.Provider regs = registries();
		if (regs == null) return s.getItem().toString();
		try {
			return s.copyWithCount(1).save(regs).toString();
		} catch (RuntimeException e) {
			return s.getItem().toString();
		}
	}

	private static void changed() {
		version++;
		if (dirtySince < 0L) dirtySince = System.nanoTime();
	}

	/** Chamado todo tick: salva o arquivo um pouquinho depois da ultima mudanca. */
	public static void tick() {
		if (dirtySince >= 0L && System.nanoTime() - dirtySince > 2_000_000_000L) {
			dirtySince = -1L;
			save();
		}
	}

	// ------------------------------------------------------------------ arquivo

	private static HolderLookup.Provider registries() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.registryAccess() : null;
	}

	private static Path file(String serverKey) {
		return MapManager.serverDir(serverKey).resolve("receitas.nbt");
	}

	private static void ensureLoaded() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		String key = MapManager.serverKey(mc);
		if (key.equals(loadedFor)) return;
		if (loadedFor != null && dirtySince >= 0L) save();
		loadedFor = key;
		entries.clear();
		byKey.clear();
		fakes.clear();
		dirtySince = -1L;
		version++;
		HolderLookup.Provider regs = registries();
		Path f = file(key);
		if (regs == null || !Files.exists(f)) return;
		try {
			CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
			ListTag list = root.getList("Recipes", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = list.getCompound(i);
				Station st;
				try {
					st = Station.valueOf(t.getString("Station"));
				} catch (IllegalArgumentException ex) {
					continue;
				}
				int w = Math.max(1, t.getInt("W"));
				int h = Math.max(1, t.getInt("H"));
				ListTag ins = t.getList("In", Tag.TAG_COMPOUND);
				int n = Math.max(t.getInt("N"), 1);
				ItemStack[] inputs = new ItemStack[n];
				Arrays.fill(inputs, ItemStack.EMPTY);
				for (int k = 0; k < ins.size(); k++) {
					CompoundTag e = ins.getCompound(k);
					int slot = e.getInt("Slot");
					if (slot < 0 || slot >= n) continue;
					ItemStack.parse(regs, e.getCompound("Item")).ifPresent(s -> inputs[slot] = s);
				}
				ItemStack result = ItemStack.parse(regs, t.getCompound("Out")).orElse(ItemStack.EMPTY);
				if (result.isEmpty()) continue;
				String k = key(st, inputs, result);
				if (byKey.containsKey(k)) continue;
				Entry entry = new Entry(st, w, h, inputs, result, t.getLong("Seen"), k);
				entries.add(entry);
				byKey.put(k, entry);
			}
			ListTag fk = root.getList("Fakes", Tag.TAG_STRING);
			for (int i = 0; i < fk.size(); i++) fakes.add(fk.getString(i));
		} catch (Exception ex) {
			IsekaiUI.LOG.warn("Nao consegui ler o grimorio {}", f, ex);
		}
	}

	private static void save() {
		HolderLookup.Provider regs = registries();
		if (loadedFor == null || regs == null) return;
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		ListTag list = new ListTag();
		for (Entry e : entries) {
			CompoundTag t = new CompoundTag();
			t.putString("Station", e.station.name());
			t.putInt("W", e.w);
			t.putInt("H", e.h);
			t.putInt("N", e.inputs.length);
			ListTag ins = new ListTag();
			for (int i = 0; i < e.inputs.length; i++) {
				if (e.inputs[i].isEmpty()) continue;
				CompoundTag in = new CompoundTag();
				in.putInt("Slot", i);
				in.put("Item", e.inputs[i].save(regs));
				ins.add(in);
			}
			t.put("In", ins);
			t.put("Out", e.result.save(regs));
			t.putLong("Seen", e.seen);
			list.add(t);
		}
		root.put("Recipes", list);
		ListTag fk = new ListTag();
		for (String s : fakes) fk.add(StringTag.valueOf(s));
		root.put("Fakes", fk);
		Path f = file(loadedFor);
		try {
			Files.createDirectories(f.getParent());
			NbtIo.writeCompressed(root, f);
		} catch (Exception ex) {
			IsekaiUI.LOG.warn("Nao consegui salvar o grimorio {}", f, ex);
		}
	}

	/** Usado pelos testes automaticos. */
	public static void debugForgetAll() {
		ensureLoaded();
		entries.clear();
		byKey.clear();
		fakes.clear();
		changed();
	}
}
