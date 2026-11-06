package com.compelium.isekaiui.creative;

import com.compelium.isekaiui.IsekaiUI;
import com.compelium.isekaiui.map.MapManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bau pessoal do criativo: copias de itens (inclusive os personalizados do servidor, com nome, lore e
 * encantamentos) guardadas no SEU computador, uma lista por servidor. Tirar um item do bau entrega uma
 * copia pelo mesmo mecanismo do inventario criativo do jogo (so funciona no modo criativo).
 */
public final class PersonalChest {
	public static final int SLOTS = 27;
	public static final int PAGES = 20;
	private static ItemStack[][] pages = empty();
	private static String loadedFor;

	private PersonalChest() {
	}

	private static ItemStack[][] empty() {
		ItemStack[][] p = new ItemStack[PAGES][SLOTS];
		for (ItemStack[] page : p) java.util.Arrays.fill(page, ItemStack.EMPTY);
		return p;
	}

	public static ItemStack get(int page, int slot) {
		ensureLoaded();
		if (page < 0 || page >= PAGES || slot < 0 || slot >= SLOTS) return ItemStack.EMPTY;
		return pages[page][slot];
	}

	public static void set(int page, int slot, ItemStack stack) {
		ensureLoaded();
		if (page < 0 || page >= PAGES || slot < 0 || slot >= SLOTS) return;
		pages[page][slot] = stack == null ? ItemStack.EMPTY : stack;
		save();
	}

	/** Guarda uma copia no primeiro espaco livre a partir da pagina indicada. Devolve a pagina usada ou -1. */
	public static int addCopy(ItemStack stack, int fromPage) {
		ensureLoaded();
		if (stack == null || stack.isEmpty()) return -1;
		for (int k = 0; k < PAGES; k++) {
			int page = (fromPage + k) % PAGES;
			for (int i = 0; i < SLOTS; i++) {
				if (pages[page][i].isEmpty()) {
					pages[page][i] = stack.copy();
					save();
					return page;
				}
			}
		}
		return -1;
	}

	/** Quantas paginas tem algo (minimo 1). */
	public static int usedPages() {
		ensureLoaded();
		int last = 0;
		for (int p = 0; p < PAGES; p++) {
			for (ItemStack s : pages[p]) {
				if (!s.isEmpty()) {
					last = p;
					break;
				}
			}
		}
		return last + 1;
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
		pages = empty();
		Path f = file(key);
		if (!Files.exists(f)) return;
		try {
			CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
			ListTag items = root.getList("Items", Tag.TAG_COMPOUND);
			for (int i = 0; i < items.size(); i++) {
				CompoundTag e = items.getCompound(i);
				int page = e.getInt("Page");
				int slot = e.getInt("Slot");
				if (page < 0 || page >= PAGES || slot < 0 || slot >= SLOTS) continue;
				ItemStack.parse(regs, e.getCompound("Item")).ifPresent(s -> pages[page][slot] = s);
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
		ListTag items = new ListTag();
		for (int p = 0; p < PAGES; p++) {
			for (int i = 0; i < SLOTS; i++) {
				ItemStack s = pages[p][i];
				if (s.isEmpty()) continue;
				CompoundTag e = new CompoundTag();
				e.putInt("Page", p);
				e.putInt("Slot", i);
				e.put("Item", s.save(regs));
				items.add(e);
			}
		}
		root.put("Items", items);
		Path f = file(loadedFor);
		try {
			Files.createDirectories(f.getParent());
			NbtIo.writeCompressed(root, f);
		} catch (Exception ex) {
			IsekaiUI.LOG.warn("Nao consegui salvar o bau pessoal {}", f, ex);
		}
	}
}
