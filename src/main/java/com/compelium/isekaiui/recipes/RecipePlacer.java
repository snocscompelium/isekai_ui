package com.compelium.isekaiui.recipes;

import com.compelium.isekaiui.recipes.RecipeJournal.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coloca os ingredientes de uma receita do grimorio na estacao aberta. Quando o servidor tem a mesma receita
 * no livro dele, usa o pedido normal do jogo (igual ao livro vanilla, instantaneo); senao clica item por item
 * pela fila de cliques. Receitas "nao testadas" sao testadas pelo pedido do jogo.
 */
public final class RecipePlacer {
	/** O que impede de colocar a receita nesta tela. */
	public enum Ready {
		OK, NEED_TABLE, WRONG_STATION, NO_SERVER_ID
	}

	private RecipePlacer() {
	}

	public static Ready check(RecipeView v, AbstractContainerMenu menu) {
		if (menu == null) return Ready.WRONG_STATION;
		Station st = v.station();
		boolean right = switch (st) {
			case CRAFTING -> menu instanceof AbstractCraftingMenu;
			case SMELTING -> menu instanceof FurnaceMenu;
			case BLASTING -> menu instanceof BlastFurnaceMenu;
			case SMOKING -> menu instanceof SmokerMenu;
			case BREWING -> menu instanceof BrewingStandMenu;
			case SMITHING -> menu instanceof SmithingMenu;
			case STONECUTTING -> menu instanceof StonecutterMenu;
		};
		if (!right) return Ready.WRONG_STATION;
		if (menu instanceof AbstractCraftingMenu cm && (v.w() > cm.getGridWidth() || v.h() > cm.getGridHeight())) return Ready.NEED_TABLE;
		if (!v.tested() && v.serverId() == null && (st == Station.CRAFTING || st.isFurnace())) return Ready.NO_SERVER_ID;
		return Ready.OK;
	}

	/** Mensagem curta explicando por que nao da para colocar aqui. */
	public static Component reason(Ready r, RecipeView v) {
		return switch (r) {
			case NEED_TABLE -> Component.translatable("isekai_ui.recipes.need_table");
			case WRONG_STATION -> Component.translatable("isekai_ui.recipes.open_station", new ItemStack(v.station().icon).getHoverName());
			case NO_SERVER_ID -> Component.translatable("isekai_ui.recipes.cannot_test");
			case OK -> Component.empty();
		};
	}

	/** Coloca os ingredientes (ou testa a receita). Devolve a mensagem para mostrar. */
	public static Component place(RecipeView v, AbstractContainerMenu menu, boolean max) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return Component.empty();
		if (ClickQueue.busy()) return Component.translatable("isekai_ui.recipes.busy");
		if (!menu.getCarried().isEmpty()) return Component.translatable("isekai_ui.recipes.hand_full");
		Ready r = check(v, menu);
		if (r != Ready.OK) return reason(r, v);
		if (!v.tested()) {
			Component missing = missingOptions(v, menu, p);
			if (missing != null) return missing;
			if (v.station() == Station.CRAFTING || v.station().isFurnace()) {
				// pedido normal do jogo: o proprio servidor confere e coloca os itens
				mc.gameMode.handlePlaceRecipe(menu.containerId, v.serverId(), max);
				if (v.station() == Station.CRAFTING) RecipeObserver.beginTest(v.signature(), menu);
				return Component.translatable("isekai_ui.recipes.testing");
			}
			// ferraria nao tem livro no jogo: coloca a mao uma opcao que o jogador tem
			ItemStack[] chosen = pickAvailable(v, menu, p);
			List<ClickQueue.Step> steps = new ArrayList<>();
			for (int i = 0; i < chosen.length && i < 3; i++) planSingle(chosen[i], i, false, menu, steps);
			if (!steps.isEmpty()) ClickQueue.start(menu, steps);
			return Component.translatable("isekai_ui.recipes.testing");
		}
		RecipeJournal.Entry e = v.learned();
		Component missing = missingExact(e, menu, p);
		if (missing != null) return missing;
		if (e.station == Station.CRAFTING || e.station.isFurnace()) {
			RecipeDisplayId id = ServerRecipes.match(e);
			if (id != null) {
				mc.gameMode.handlePlaceRecipe(menu.containerId, id, max);
				return Component.translatable("isekai_ui.recipes.placed");
			}
		}
		List<ClickQueue.Step> steps = new ArrayList<>();
		switch (e.station) {
			case CRAFTING -> planCrafting(e, (AbstractCraftingMenu) menu, steps);
			case SMELTING, BLASTING, SMOKING -> planSingle(e.inputs[0], 0, true, menu, steps);
			case BREWING -> planBrewing(e, menu, p, steps);
			case SMITHING -> {
				for (int i = 0; i < 3; i++) planSingle(e.inputs[i], i, false, menu, steps);
			}
			case STONECUTTING -> {
				planSingle(e.inputs[0], 0, true, menu, steps);
				steps.add(selectStonecutter(e.result));
			}
		}
		if (steps.isEmpty()) return Component.translatable("isekai_ui.recipes.placed");
		ClickQueue.start(menu, steps);
		return Component.translatable("isekai_ui.recipes.placing");
	}

	// ------------------------------------------------------------------ o que falta

	private static List<Slot> playerSlots(AbstractContainerMenu m, LocalPlayer p) {
		List<Slot> out = new ArrayList<>();
		for (Slot s : m.slots) if (s.container == p.getInventory() && s.getContainerSlot() < 36) out.add(s);
		return out;
	}

	/** Itens que voltam para a bolsa antes de colocar (a grade/entradas atuais) tambem contam como disponiveis. */
	private static List<ItemStack> available(AbstractContainerMenu m, LocalPlayer p) {
		List<ItemStack> out = new ArrayList<>();
		for (Slot s : playerSlots(m, p)) if (s.hasItem()) out.add(s.getItem().copy());
		if (m instanceof AbstractCraftingMenu cm) {
			for (Slot s : cm.getInputGridSlots()) if (s.hasItem()) out.add(s.getItem().copy());
		}
		return out;
	}

	private static Component missingExact(RecipeJournal.Entry e, AbstractContainerMenu m, LocalPlayer p) {
		Map<String, Integer> need = new LinkedHashMap<>();
		Map<String, ItemStack> sample = new HashMap<>();
		int count = e.station == Station.BREWING ? 1 : e.inputs.length;
		for (int i = 0; i < count; i++) {
			ItemStack s = e.inputs[i];
			if (s.isEmpty()) continue;
			String sig = RecipeJournal.signature(s);
			need.merge(sig, 1, Integer::sum);
			sample.putIfAbsent(sig, s);
		}
		if (e.station == Station.BREWING && !e.inputs[1].isEmpty()) {
			String sig = RecipeJournal.signature(e.inputs[1]);
			need.merge(sig, 1, Integer::sum);
			sample.putIfAbsent(sig, e.inputs[1]);
		}
		List<ItemStack> have = available(m, p);
		for (Map.Entry<String, Integer> n : need.entrySet()) {
			ItemStack want = sample.get(n.getKey());
			int got = 0;
			for (ItemStack h : have) if (sameExact(h, want) || h.is(want.getItem())) got += h.getCount();
			if (got < n.getValue()) {
				return Component.translatable("isekai_ui.recipes.missing", n.getValue() - got, want.getHoverName());
			}
		}
		return null;
	}

	private static Component missingOptions(RecipeView v, AbstractContainerMenu m, LocalPlayer p) {
		List<ItemStack> have = available(m, p);
		for (List<ItemStack> opts : v.inputs()) {
			if (opts.isEmpty()) continue;
			boolean ok = false;
			for (ItemStack o : opts) {
				for (ItemStack h : have) {
					if (h.is(o.getItem())) {
						ok = true;
						break;
					}
				}
				if (ok) break;
			}
			if (!ok) return Component.translatable("isekai_ui.recipes.missing", 1, opts.get(0).getHoverName());
		}
		return null;
	}

	/** Para cada posicao, a primeira opcao que o jogador tem (ou vazio). */
	private static ItemStack[] pickAvailable(RecipeView v, AbstractContainerMenu m, LocalPlayer p) {
		List<ItemStack> have = available(m, p);
		ItemStack[] out = new ItemStack[v.inputs().size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = ItemStack.EMPTY;
			for (ItemStack o : v.inputs().get(i)) {
				boolean found = false;
				for (ItemStack h : have) {
					if (h.is(o.getItem())) {
						found = true;
						break;
					}
				}
				if (found) {
					out[i] = o.copyWithCount(1);
					break;
				}
			}
		}
		return out;
	}

	private static boolean sameExact(ItemStack have, ItemStack want) {
		if (have.isEmpty() || !have.is(want.getItem())) return false;
		return RecipeJournal.signature(RecipeJournal.clean(have)).equals(RecipeJournal.signature(want));
	}

	/** Slot da bolsa com o item (igual primeiro; depois o mesmo item com outros detalhes). */
	private static Slot findSource(AbstractContainerMenu m, LocalPlayer p, ItemStack want) {
		List<Slot> slots = playerSlots(m, p);
		for (Slot s : slots) if (s.hasItem() && sameExact(s.getItem(), want)) return s;
		for (Slot s : slots) if (s.hasItem() && s.getItem().is(want.getItem())) return s;
		return null;
	}

	private static int emptyPlayerSlot(AbstractContainerMenu m, LocalPlayer p) {
		for (Slot s : playerSlots(m, p)) if (!s.hasItem()) return s.index;
		return -1;
	}

	// ------------------------------------------------------------------ passos

	private static void click(Minecraft mc, AbstractContainerMenu m, LocalPlayer p, int slot, int button, ClickType type) {
		mc.gameMode.handleInventoryMouseClick(m.containerId, slot, button, type, p);
	}

	private static ClickQueue.Step quickMove(int slot) {
		return (mc, p, m) -> {
			if (m.getSlot(slot).hasItem()) click(mc, m, p, slot, 0, ClickType.QUICK_MOVE);
			return true;
		};
	}

	private static ClickQueue.Step pickup(ItemStack want, int[] holder) {
		return (mc, p, m) -> {
			if (!m.getCarried().isEmpty()) return true;
			Slot src = findSource(m, p, want);
			if (src == null) return false;
			holder[0] = src.index;
			click(mc, m, p, src.index, 0, ClickType.PICKUP);
			return true;
		};
	}

	/** Botao direito no destino (1 item). Se a pilha da mao acabou, pega outra antes. */
	private static ClickQueue.Step placeOne(int target, ItemStack want, int[] holder) {
		return new ClickQueue.Step() {
			@Override
			public boolean run(Minecraft mc, LocalPlayer p, AbstractContainerMenu m) {
				if (m.getCarried().isEmpty()) {
					Slot src = findSource(m, p, want);
					if (src == null) return false;
					holder[0] = src.index;
					click(mc, m, p, src.index, 0, ClickType.PICKUP);
					ClickQueue.pushFront(this);
					return true;
				}
				// seguranca: so coloca o item certo, e nunca troca com outro item que ja esteja no destino
				if (!m.getCarried().is(want.getItem())) return false;
				Slot t = m.getSlot(target);
				if (t.hasItem() && !ItemStack.isSameItemSameComponents(t.getItem(), m.getCarried())) return false;
				click(mc, m, p, target, 1, ClickType.PICKUP);
				return true;
			}
		};
	}

	/** Clique esquerdo no destino (a pilha toda). */
	private static ClickQueue.Step placeAll(int target, ItemStack want) {
		return (mc, p, m) -> {
			if (m.getCarried().isEmpty()) return true;
			if (!m.getCarried().is(want.getItem())) return false;
			Slot t = m.getSlot(target);
			if (t.hasItem() && !ItemStack.isSameItemSameComponents(t.getItem(), m.getCarried())) return false;
			click(mc, m, p, target, 0, ClickType.PICKUP);
			return true;
		};
	}

	private static ClickQueue.Step putBack(int[] holder) {
		return (mc, p, m) -> {
			if (m.getCarried().isEmpty()) return true;
			int slot = holder[0];
			if (slot < 0 || (m.getSlot(slot).hasItem() && !ItemStack.isSameItemSameComponents(m.getSlot(slot).getItem(), m.getCarried()))) {
				slot = emptyPlayerSlot(m, p);
			}
			if (slot >= 0) click(mc, m, p, slot, 0, ClickType.PICKUP);
			return true;
		};
	}

	private static void planCrafting(RecipeJournal.Entry e, AbstractCraftingMenu cm, List<ClickQueue.Step> steps) {
		List<Slot> grid = cm.getInputGridSlots();
		int gw = cm.getGridWidth();
		for (Slot s : grid) if (s.hasItem()) steps.add(quickMove(s.index));
		Map<String, List<Integer>> targets = new LinkedHashMap<>();
		Map<String, ItemStack> sample = new HashMap<>();
		for (int r = 0; r < e.h; r++) {
			for (int c = 0; c < e.w; c++) {
				ItemStack s = e.inputs[r * e.w + c];
				if (s.isEmpty()) continue;
				String sig = RecipeJournal.signature(s);
				targets.computeIfAbsent(sig, k -> new ArrayList<>()).add(grid.get(r * gw + c).index);
				sample.putIfAbsent(sig, s);
			}
		}
		for (Map.Entry<String, List<Integer>> t : targets.entrySet()) {
			int[] holder = {-1};
			ItemStack want = sample.get(t.getKey());
			steps.add(pickup(want, holder));
			for (int target : t.getValue()) steps.add(placeOne(target, want, holder));
			steps.add(putBack(holder));
		}
	}

	/** Uma entrada: "all" coloca a pilha toda (fornalha, cortador); senao so 1 item (ferraria). */
	private static void planSingle(ItemStack want, int slot, boolean all, AbstractContainerMenu m, List<ClickQueue.Step> steps) {
		if (want.isEmpty()) return;
		Slot s = m.getSlot(slot);
		if (s.hasItem()) {
			if (s.getItem().is(want.getItem())) return;
			steps.add(quickMove(slot));
		}
		int[] holder = {-1};
		steps.add(pickup(want, holder));
		steps.add(all ? placeAll(slot, want) : placeOne(slot, want, holder));
		steps.add(putBack(holder));
	}

	private static void planBrewing(RecipeJournal.Entry e, AbstractContainerMenu m, LocalPlayer p, List<ClickQueue.Step> steps) {
		planSingle(e.inputs[0], 3, true, m, steps);
		ItemStack base = e.inputs[1];
		int free = 0;
		for (int b = 0; b < 3; b++) if (!m.getSlot(b).hasItem()) free++;
		int bases = 0;
		for (Slot s : playerSlots(m, p)) if (s.hasItem() && sameExact(s.getItem(), base)) bases += s.getItem().getCount();
		int n = Math.min(free, Math.max(1, bases));
		for (int b = 0; b < 3 && n > 0; b++) {
			if (m.getSlot(b).hasItem()) continue;
			int[] holder = {-1};
			steps.add(pickup(base, holder));
			steps.add(placeOne(b, base, holder));
			steps.add(putBack(holder));
			n--;
		}
	}

	/** Escolhe no cortador de pedras a opcao que da o resultado da receita. */
	private static ClickQueue.Step selectStonecutter(ItemStack result) {
		return (mc, p, m) -> {
			if (!(m instanceof StonecutterMenu sc) || mc.level == null) return false;
			List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> list = sc.getVisibleRecipes().entries();
			var ctx = SlotDisplayContext.fromLevel(mc.level);
			for (int i = 0; i < list.size(); i++) {
				ItemStack opt = list.get(i).recipe().optionDisplay().resolveForFirstStack(ctx);
				if (opt.is(result.getItem()) && opt.getCount() == result.getCount()) {
					if (sc.clickMenuButton(p, i)) mc.gameMode.handleInventoryButtonClick(sc.containerId, i);
					return true;
				}
			}
			return true;
		};
	}
}
