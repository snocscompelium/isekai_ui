package com.compelium.isekaiui.creative;

import com.compelium.isekaiui.IsekaiUI;
import com.compelium.isekaiui.map.MapManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Conjuntos de armadura salvos (por servidor). No criativo, trocar de conjunto coloca copias das pecas;
 * no sobrevivencia, as pecas vem do proprio inventario com cliques normais (como se o jogador arrastasse).
 */
public final class ArmorSets {
	/** Pecas: 0 capacete, 1 peitoral, 2 calcas, 3 botas, 4 mao secundaria. */
	public static final int PIECES = 5;
	public static final int MAX_SETS = 12;
	private static final int[] MENU_SLOT = {InventoryMenu.ARMOR_SLOT_START, InventoryMenu.ARMOR_SLOT_START + 1,
			InventoryMenu.ARMOR_SLOT_START + 2, InventoryMenu.ARMOR_SLOT_START + 3, InventoryMenu.SHIELD_SLOT};
	private static final EquipmentSlot[] EQUIP = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

	public static final class ArmorSet {
		public String name;
		public final ItemStack[] pieces = new ItemStack[PIECES];

		ArmorSet(String name) {
			this.name = name;
			Arrays.fill(this.pieces, ItemStack.EMPTY);
		}

		public boolean isEmpty() {
			for (ItemStack s : this.pieces) if (!s.isEmpty()) return false;
			return true;
		}
	}

	private static final List<ArmorSet> sets = new ArrayList<>();
	private static String loadedFor;
	/** Trocas que faltam fazer no sobrevivencia: {id do menu, slot de onde vem, slot do corpo}. */
	private static final ArrayDeque<int[]> PENDING = new ArrayDeque<>();
	private static int cooldown;

	private ArmorSets() {
	}

	public static List<ArmorSet> all() {
		ensureLoaded();
		return sets;
	}

	/** Salva o que o jogador esta vestindo agora. Devolve o novo conjunto ou null. */
	public static ArmorSet saveCurrent(LocalPlayer player) {
		ensureLoaded();
		if (player == null || sets.size() >= MAX_SETS) return null;
		ArmorSet set = new ArmorSet(Component.translatable("isekai_ui.armor.default_name", sets.size() + 1).getString());
		for (int i = 0; i < PIECES; i++) set.pieces[i] = player.getItemBySlot(EQUIP[i]).copy();
		if (set.isEmpty()) return null;
		sets.add(set);
		save();
		return set;
	}

	public static void rename(ArmorSet set, String name) {
		if (set == null || name == null || name.isBlank()) return;
		set.name = name.strip();
		save();
	}

	public static void remove(ArmorSet set) {
		if (sets.remove(set)) save();
	}

	/**
	 * Veste o conjunto. Criativo: coloca copias (o slot da mao secundaria so muda se o conjunto tiver algo nela).
	 * Sobrevivencia: procura cada peca no inventario e troca de lugar com a que esta vestida.
	 * Devolve quantas pecas nao foram encontradas.
	 */
	public static int equip(ArmorSet set, InventoryMenu menu, LocalPlayer player, boolean creative) {
		Minecraft mc = Minecraft.getInstance();
		MultiPlayerGameMode gm = mc.gameMode;
		if (set == null || gm == null || player == null) return 0;
		if (creative) {
			for (int i = 0; i < PIECES; i++) {
				ItemStack piece = set.pieces[i];
				if (i == 4 && piece.isEmpty()) continue;
				ItemStack worn = menu.getSlot(MENU_SLOT[i]).getItem();
				// peca especial (nome, encantamento...) que nao esta salva em nenhum conjunto: vai para a bolsa, nao some
				if (!worn.isEmpty() && !ItemStack.isSameItemSameComponents(worn, piece) && isSpecial(worn) && !isSaved(worn)) {
					if (!player.getInventory().add(worn.copy())) gm.handleCreativeModeItemDrop(worn.copy());
				}
				menu.getSlot(MENU_SLOT[i]).set(piece.copy());
			}
			menu.broadcastChanges();
			return 0;
		}
		if (!menu.getCarried().isEmpty() || player.containerMenu != menu) return 0;
		int missing = 0;
		boolean[] used = new boolean[menu.slots.size()];
		PENDING.clear();
		for (int i = 0; i < PIECES; i++) {
			ItemStack piece = set.pieces[i];
			if (piece.isEmpty()) continue;
			int target = MENU_SLOT[i];
			if (matches(menu.getSlot(target).getItem(), piece) > 0) continue;
			int src = findBest(menu, piece, used);
			if (src < 0) {
				missing++;
				continue;
			}
			used[src] = true;
			PENDING.add(new int[]{menu.containerId, src, target});
		}
		cooldown = 0;
		tick();
		return missing;
	}

	/**
	 * Troca uma peca de cada vez, com uma pausa curtinha entre elas (como uma pessoa rapida faria), para nao
	 * parecer um "auto-armor" para o anti-cheat do servidor. Para tudo se o inventario fechar ou a mao ocupar.
	 */
	public static void tick() {
		if (PENDING.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		MultiPlayerGameMode gm = mc.gameMode;
		if (player == null || gm == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)
				|| screen.getMenu() != player.containerMenu || !(player.containerMenu instanceof InventoryMenu menu)
				|| !menu.getCarried().isEmpty() || menu.containerId != PENDING.peek()[0]) {
			PENDING.clear();
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		int[] job = PENDING.poll();
		int src = job[1];
		int target = job[2];
		if (menu.getSlot(src).getItem().isEmpty()) return;
		gm.handleInventoryMouseClick(menu.containerId, src, 0, ClickType.PICKUP, player);
		gm.handleInventoryMouseClick(menu.containerId, target, 0, ClickType.PICKUP, player);
		if (!menu.getCarried().isEmpty()) gm.handleInventoryMouseClick(menu.containerId, src, 0, ClickType.PICKUP, player);
		cooldown = 2;
	}

	/** Item com algo alem do normal (nome, lore, encantamento, dados do servidor...). Desgaste nao conta. */
	private static boolean isSpecial(ItemStack s) {
		ItemStack c = s.copyWithCount(1);
		DataComponentMap proto = c.getItem().components();
		c.set(DataComponents.DAMAGE, proto.get(DataComponents.DAMAGE));
		c.set(DataComponents.REPAIR_COST, proto.get(DataComponents.REPAIR_COST));
		return !c.getComponentsPatch().isEmpty();
	}

	private static boolean isSaved(ItemStack s) {
		for (ArmorSet set : sets) {
			for (ItemStack p : set.pieces) if (!p.isEmpty() && ItemStack.isSameItemSameComponents(p, s)) return true;
		}
		return false;
	}

	/** Procura no inventario (slots 9-44) a peca mais parecida: igual > mesmo nome > mesmo item. */
	private static int findBest(InventoryMenu menu, ItemStack piece, boolean[] used) {
		int best = -1;
		int bestScore = 0;
		for (int s = InventoryMenu.INV_SLOT_START; s < InventoryMenu.USE_ROW_SLOT_END; s++) {
			if (used[s]) continue;
			int score = matches(menu.getSlot(s).getItem(), piece);
			if (score > bestScore) {
				bestScore = score;
				best = s;
			}
		}
		return best;
	}

	private static int matches(ItemStack candidate, ItemStack piece) {
		if (candidate.isEmpty() || !candidate.is(piece.getItem())) return 0;
		if (ItemStack.isSameItemSameComponents(candidate, piece)) return 3;
		if (Objects.equals(candidate.get(DataComponents.CUSTOM_NAME), piece.get(DataComponents.CUSTOM_NAME))) return 2;
		return 1;
	}

	// ------------------------------------------------------------------ arquivo

	private static HolderLookup.Provider registries() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.registryAccess() : null;
	}

	private static Path file(String serverKey) {
		return MapManager.serverDir(serverKey).resolve("conjuntos_armadura.nbt");
	}

	private static void ensureLoaded() {
		String key = MapManager.serverKey(Minecraft.getInstance());
		if (key.equals(loadedFor)) return;
		HolderLookup.Provider regs = registries();
		if (regs == null) return;
		loadedFor = key;
		sets.clear();
		Path f = file(key);
		if (!Files.exists(f)) return;
		try {
			CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
			ListTag list = root.getList("Sets", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size() && sets.size() < MAX_SETS; i++) {
				CompoundTag t = list.getCompound(i);
				ArmorSet set = new ArmorSet(t.getString("Name"));
				ListTag items = t.getList("Pieces", Tag.TAG_COMPOUND);
				for (int k = 0; k < items.size(); k++) {
					CompoundTag e = items.getCompound(k);
					int slot = e.getInt("Slot");
					if (slot < 0 || slot >= PIECES) continue;
					ItemStack.parse(regs, e.getCompound("Item")).ifPresent(s -> set.pieces[slot] = s);
				}
				sets.add(set);
			}
		} catch (Exception ex) {
			IsekaiUI.LOG.warn("Nao consegui ler os conjuntos de armadura {}", f, ex);
		}
	}

	private static void save() {
		HolderLookup.Provider regs = registries();
		if (loadedFor == null || regs == null) return;
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		ListTag list = new ListTag();
		for (ArmorSet set : sets) {
			CompoundTag t = new CompoundTag();
			t.putString("Name", set.name);
			ListTag items = new ListTag();
			for (int i = 0; i < PIECES; i++) {
				if (set.pieces[i].isEmpty()) continue;
				CompoundTag e = new CompoundTag();
				e.putInt("Slot", i);
				e.put("Item", set.pieces[i].save(regs));
				items.add(e);
			}
			t.put("Pieces", items);
			list.add(t);
		}
		root.put("Sets", list);
		Path f = file(loadedFor);
		try {
			Files.createDirectories(f.getParent());
			NbtIo.writeCompressed(root, f);
		} catch (Exception ex) {
			IsekaiUI.LOG.warn("Nao consegui salvar os conjuntos de armadura {}", f, ex);
		}
	}
}
