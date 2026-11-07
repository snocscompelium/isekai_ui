package com.compelium.isekaiui.recipes;

import com.compelium.isekaiui.recipes.RecipeJournal.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Receitas que o servidor mandou para o livro de receitas do jogo. Elas NAO entram no grimorio sozinhas:
 * aparecem como "nao testadas" (se o jogador quiser) e so viram receita de verdade quando funcionam.
 */
public final class ServerRecipes {
	private static final int MAX_OPTIONS = 16;
	private static List<RecipeView> cache = List.of();
	private static long builtAt;
	private static int builtVersion = -1;

	private ServerRecipes() {
	}

	/** Receitas do servidor ainda nao testadas (sem as que ja falharam e sem as que ja estao anotadas). */
	public static List<RecipeView> untested() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return List.of();
		long now = System.currentTimeMillis();
		if (builtVersion == RecipeJournal.version() && now - builtAt < 5000L) return cache;
		builtAt = now;
		builtVersion = RecipeJournal.version();
		ContextMap ctx = SlotDisplayContext.fromLevel(mc.level);
		Set<String> learned = new HashSet<>();
		for (RecipeJournal.Entry e : RecipeJournal.all()) learned.add(resultKey(e.station, e.result));
		Set<String> seen = new HashSet<>();
		List<RecipeView> out = new ArrayList<>();
		for (RecipeCollection col : mc.player.getRecipeBook().getCollections()) {
			for (RecipeDisplayEntry entry : col.getRecipes()) {
				RecipeView v;
				try {
					v = toView(entry, ctx);
				} catch (RuntimeException e) {
					continue;
				}
				if (v == null || RecipeJournal.isFake(v.signature()) || !seen.add(v.signature())) continue;
				if (learned.contains(resultKey(v.station(), v.result()))) continue;
				out.add(v);
			}
		}
		cache = out;
		return out;
	}

	private static String resultKey(Station st, ItemStack result) {
		return st.name() + "|" + RecipeJournal.signature(result) + "x" + result.getCount();
	}

	static RecipeView toView(RecipeDisplayEntry entry, ContextMap ctx) {
		RecipeDisplay d = entry.display();
		ItemStack result = d.result().resolveForFirstStack(ctx);
		if (result.isEmpty()) return null;
		if (d instanceof ShapedCraftingRecipeDisplay s) {
			List<List<ItemStack>> in = new ArrayList<>();
			for (SlotDisplay sd : s.ingredients()) in.add(options(sd, ctx));
			return view(Station.CRAFTING, s.width(), s.height(), in, result, entry);
		}
		if (d instanceof ShapelessCraftingRecipeDisplay s) {
			int n = s.ingredients().size();
			if (n == 0 || n > 9) return null;
			int w = n == 1 ? 1 : (n <= 4 ? 2 : 3);
			int h = (n + w - 1) / w;
			List<List<ItemStack>> in = new ArrayList<>();
			for (SlotDisplay sd : s.ingredients()) in.add(options(sd, ctx));
			while (in.size() < w * h) in.add(List.of());
			return view(Station.CRAFTING, w, h, in, result, entry);
		}
		if (d instanceof FurnaceRecipeDisplay f) {
			ItemStack station = f.craftingStation().resolveForFirstStack(ctx);
			Station st = station.is(Items.BLAST_FURNACE) ? Station.BLASTING
					: station.is(Items.SMOKER) ? Station.SMOKING
					: station.is(Items.FURNACE) ? Station.SMELTING : null;
			if (st == null) return null;
			return view(st, 1, 1, List.of(options(f.ingredient(), ctx)), result, entry);
		}
		if (d instanceof SmithingRecipeDisplay s) {
			List<ItemStack> base = options(s.base(), ctx);
			// so as melhorias (o item muda); os acabamentos decorativos iriam encher o livro
			if (base.isEmpty() || base.get(0).is(result.getItem())) return null;
			return view(Station.SMITHING, 3, 1, List.of(options(s.template(), ctx), base, options(s.addition(), ctx)), result, entry);
		}
		return null;
	}

	private static List<ItemStack> options(SlotDisplay sd, ContextMap ctx) {
		List<ItemStack> all = sd.resolveForStacks(ctx);
		if (all.size() <= MAX_OPTIONS) return all;
		return new ArrayList<>(all.subList(0, MAX_OPTIONS));
	}

	private static RecipeView view(Station st, int w, int h, List<List<ItemStack>> in, ItemStack result, RecipeDisplayEntry entry) {
		StringBuilder sb = new StringBuilder("S|").append(st.name()).append('|').append(w).append('x').append(h).append('|');
		for (List<ItemStack> opts : in) sb.append(opts.isEmpty() ? "-" : opts.get(0).getItem().toString()).append(',');
		sb.append('|').append(RecipeJournal.signature(result)).append('x').append(result.getCount());
		return new RecipeView(st, w, h, in, result, null, entry.id(), sb.toString());
	}

	/** Procura no livro do servidor a mesma receita de uma anotada, para colocar os itens pelo proprio jogo. */
	public static RecipeDisplayId match(RecipeJournal.Entry e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return null;
		if (e.station != Station.CRAFTING && !e.station.isFurnace()) return null;
		ContextMap ctx = SlotDisplayContext.fromLevel(mc.level);
		for (RecipeCollection col : mc.player.getRecipeBook().getCollections()) {
			for (RecipeDisplayEntry entry : col.getRecipes()) {
				RecipeView v;
				try {
					v = toView(entry, ctx);
				} catch (RuntimeException ex) {
					continue;
				}
				if (v == null || v.station() != e.station) continue;
				if (!ItemStack.isSameItemSameComponents(v.result(), e.result) || v.result().getCount() != e.result.getCount()) continue;
				if (compatible(v, e)) return entry.id();
			}
		}
		return null;
	}

	/** Os ingredientes da receita anotada cabem nas opcoes da receita do servidor (sem importar a ordem). */
	private static boolean compatible(RecipeView v, RecipeJournal.Entry e) {
		List<List<ItemStack>> pool = new ArrayList<>();
		for (List<ItemStack> opts : v.inputs()) if (!opts.isEmpty()) pool.add(opts);
		List<ItemStack> need = new ArrayList<>();
		for (ItemStack s : e.inputs) if (!s.isEmpty()) need.add(s);
		if (pool.size() != need.size()) return false;
		boolean[] used = new boolean[pool.size()];
		for (ItemStack s : need) {
			boolean found = false;
			for (int i = 0; i < pool.size() && !found; i++) {
				if (used[i]) continue;
				for (ItemStack o : pool.get(i)) {
					if (o.is(s.getItem())) {
						used[i] = true;
						found = true;
						break;
					}
				}
			}
			if (!found) return false;
		}
		return true;
	}
}
