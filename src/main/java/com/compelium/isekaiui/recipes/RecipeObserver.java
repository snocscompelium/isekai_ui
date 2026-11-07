package com.compelium.isekaiui.recipes;

import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.gui.IsekaiMenuScreen;
import com.compelium.isekaiui.gui.VanillaTitles;
import com.compelium.isekaiui.recipes.RecipeJournal.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Olha as estacoes abertas e anota no grimorio cada receita cujo resultado o SERVIDOR mostrou. So conta
 * quando a grade ficou parada um pouco e o servidor ja respondeu depois da ultima mudanca (assim um resultado
 * antigo, que ainda nao foi atualizado, nunca vira receita falsa).
 */
public final class RecipeObserver {
	private static final int STABLE_TICKS = 3;
	private static final int TEST_TICKS = 40;

	private static AbstractContainerMenu lastMenu;
	private static long tick;
	// grade de criacao / entradas (ferraria, cortador)
	private static ItemStack[] lastInputs = new ItemStack[0];
	private static ItemStack lastResult = ItemStack.EMPTY;
	private static long inputsChangedAt;
	private static long resultChangedAt;
	private static String lastLearnedKey;
	// fornalha
	private static ItemStack furnaceIn = ItemStack.EMPTY;
	private static ItemStack furnaceOut = ItemStack.EMPTY;
	private static long furnaceKindChangedAt;
	// suporte de pocoes
	private static final ItemStack[] bottles = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
	private static ItemStack ingredient = ItemStack.EMPTY;
	// teste de uma receita do servidor
	private static String testSignature;
	private static int testContainer = -1;
	private static int testState;
	private static int testTicks;

	private RecipeObserver() {
	}

	public static void tick(Minecraft mc) {
		RecipeJournal.tick();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			lastMenu = null;
			testSignature = null;
			return;
		}
		tick++;
		AbstractContainerMenu m = p.containerMenu;
		if (m != lastMenu) {
			lastMenu = m;
			reset(m);
		}
		Screen screen = mc.screen;
		Component title = screen == null ? null : screen.getTitle();
		try {
			if (m instanceof InventoryMenu im) {
				if (screen instanceof AbstractContainerScreen<?>) observeCrafting(im);
			} else if (m instanceof CraftingMenu cm) {
				if (VanillaTitles.is(title, "container.crafting")) observeCrafting(cm);
			} else if (m instanceof AbstractFurnaceMenu fm) {
				if (VanillaTitles.is(title, "container.furnace", "container.smoker", "container.blast_furnace")) observeFurnace(fm);
			} else if (m instanceof BrewingStandMenu bm) {
				if (VanillaTitles.is(title, "container.brewing")) observeBrewing(bm);
			} else if (m instanceof SmithingMenu sm) {
				if (VanillaTitles.is(title, "container.upgrade")) observeSingle(sm, Station.SMITHING, 3, 3);
			} else if (m instanceof StonecutterMenu sc) {
				if (VanillaTitles.is(title, "container.stonecutter")) observeSingle(sc, Station.STONECUTTING, 1, 1);
			}
		} catch (RuntimeException ignored) {
			// nunca deixa o observador atrapalhar o jogo
		}
		testTick(m);
	}

	private static void reset(AbstractContainerMenu m) {
		lastInputs = new ItemStack[0];
		lastResult = ItemStack.EMPTY;
		inputsChangedAt = tick;
		resultChangedAt = tick;
		lastLearnedKey = null;
		furnaceKindChangedAt = tick;
		if (m instanceof AbstractFurnaceMenu) {
			furnaceIn = m.getSlot(0).getItem().copy();
			furnaceOut = m.getSlot(2).getItem().copy();
		}
		if (m instanceof BrewingStandMenu) {
			for (int i = 0; i < 3; i++) bottles[i] = m.getSlot(i).getItem().copy();
			ingredient = m.getSlot(3).getItem().copy();
		}
	}

	/** Atualiza a lembranca das entradas/resultado; devolve true quando ja da para confiar no resultado. */
	private static boolean stable(ItemStack[] inputs, ItemStack result) {
		if (!same(inputs, lastInputs)) {
			lastInputs = copy(inputs);
			inputsChangedAt = tick;
		}
		if (!ItemStack.matches(result, lastResult)) {
			lastResult = result.copy();
			resultChangedAt = tick;
		}
		// a grade precisa ficar parada o tempo de ida e volta ao servidor (com folga): assim o resultado que
		// esta na tela e com certeza a resposta para ESTA grade, e nao para uma arrumacao anterior
		return tick - resultChangedAt >= STABLE_TICKS && tick - inputsChangedAt >= waitTicks();
	}

	private static int waitTicks() {
		return Math.max(6, latencyTicks() * 2 + 4);
	}

	private static int latencyTicks() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.player == null) return 0;
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? 0 : (info.getLatency() + 49) / 50;
	}

	private static void observeCrafting(AbstractCraftingMenu cm) {
		List<Slot> grid = cm.getInputGridSlots();
		int gw = cm.getGridWidth();
		int gh = cm.getGridHeight();
		ItemStack[] now = new ItemStack[grid.size()];
		for (int i = 0; i < now.length; i++) now[i] = grid.get(i).getItem();
		ItemStack result = cm.getResultSlot().getItem();
		if (!stable(now, result) || result.isEmpty()) return;
		int minR = gh, maxR = -1, minC = gw, maxC = -1;
		for (int r = 0; r < gh; r++) {
			for (int c = 0; c < gw; c++) {
				if (now[r * gw + c].isEmpty()) continue;
				minR = Math.min(minR, r);
				maxR = Math.max(maxR, r);
				minC = Math.min(minC, c);
				maxC = Math.max(maxC, c);
			}
		}
		if (maxR < 0) return;
		int w = maxC - minC + 1;
		int h = maxR - minR + 1;
		ItemStack[] in = new ItemStack[w * h];
		for (int r = 0; r < h; r++) {
			for (int c = 0; c < w; c++) in[r * w + c] = RecipeJournal.clean(now[(r + minR) * gw + (c + minC)]);
		}
		learn(Station.CRAFTING, w, h, in, result);
	}

	/** Ferraria (3 entradas) e cortador de pedras (1 entrada): resultado mostrado pelo servidor. */
	private static void observeSingle(AbstractContainerMenu m, Station st, int inputs, int resultSlot) {
		ItemStack[] now = new ItemStack[inputs];
		for (int i = 0; i < inputs; i++) now[i] = m.getSlot(i).getItem();
		ItemStack result = m.getSlot(resultSlot).getItem();
		if (!stable(now, result) || result.isEmpty()) return;
		for (ItemStack s : now) if (st == Station.SMITHING && s.isEmpty()) return;
		// acabamentos decorativos (trims) nao entram: sao combinacoes demais
		if (st == Station.SMITHING && result.has(DataComponents.TRIM) && !now[1].has(DataComponents.TRIM)) return;
		ItemStack[] in = new ItemStack[inputs];
		for (int i = 0; i < inputs; i++) in[i] = RecipeJournal.clean(now[i]);
		learn(st, inputs, 1, in, result);
	}

	private static void observeFurnace(AbstractFurnaceMenu fm) {
		Station st = fm instanceof BlastFurnaceMenu ? Station.BLASTING : fm instanceof SmokerMenu ? Station.SMOKING : Station.SMELTING;
		ItemStack in = fm.getSlot(0).getItem();
		ItemStack out = fm.getSlot(2).getItem();
		// o resultado aumentou enquanto havia uma entrada: aquela entrada virou este resultado
		boolean grew = !out.isEmpty() && (furnaceOut.isEmpty() || (ItemStack.isSameItemSameComponents(out, furnaceOut) && out.getCount() > furnaceOut.getCount()));
		// (a entrada precisa ser a mesma ha um tempinho: trocar o item bem na hora nao confunde)
		if (grew && !furnaceIn.isEmpty() && tick - furnaceKindChangedAt >= waitTicks()) {
			int made = out.getCount() - (furnaceOut.isEmpty() ? 0 : furnaceOut.getCount());
			learn(st, 1, 1, new ItemStack[]{RecipeJournal.clean(furnaceIn)}, out.copyWithCount(Math.max(1, made)));
		}
		if (!ItemStack.isSameItemSameComponents(in, furnaceIn)) furnaceKindChangedAt = tick;
		furnaceIn = in.copy();
		furnaceOut = out.copy();
	}

	private static void observeBrewing(BrewingStandMenu bm) {
		ItemStack ing = bm.getSlot(3).getItem();
		boolean consumed = !ingredient.isEmpty()
				&& (ing.isEmpty() || (ItemStack.isSameItemSameComponents(ing, ingredient) && ing.getCount() < ingredient.getCount()));
		for (int i = 0; i < 3; i++) {
			ItemStack after = bm.getSlot(i).getItem();
			ItemStack before = bottles[i];
			if (consumed && !before.isEmpty() && !after.isEmpty() && !ItemStack.matches(before, after)) {
				learn(Station.BREWING, 2, 1, new ItemStack[]{RecipeJournal.clean(ingredient), RecipeJournal.clean(before)}, after.copyWithCount(1));
			}
			bottles[i] = after.copy();
		}
		ingredient = ing.copy();
	}

	private static void learn(Station st, int w, int h, ItemStack[] in, ItemStack result) {
		String key = RecipeJournal.key(st, in, result);
		if (key.equals(lastLearnedKey)) return;
		lastLearnedKey = key;
		RecipeJournal.Entry e = RecipeJournal.learn(st, w, h, in, result.copy());
		if (e != null && IsekaiConfig.get().recipes.notifyLearned) {
			IsekaiMenuScreen.notice(Component.translatable("isekai_ui.recipes.learned", result.getHoverName()));
		}
	}

	// ------------------------------------------------------------------ teste de receita do servidor

	/** O jogador pediu para testar uma receita que veio do servidor: veremos se o resultado aparece. */
	public static void beginTest(String signature, AbstractContainerMenu m) {
		testSignature = signature;
		testContainer = m.containerId;
		testState = m.getStateId();
		testTicks = 0;
	}

	private static void testTick(AbstractContainerMenu m) {
		if (testSignature == null) return;
		if (m.containerId != testContainer || !(m instanceof AbstractCraftingMenu cm)) {
			testSignature = null;
			return;
		}
		testTicks++;
		boolean result = !cm.getResultSlot().getItem().isEmpty();
		if (result && m.getStateId() != testState) {
			testSignature = null; // funcionou: o observador ja anotou a receita
			return;
		}
		if (testTicks < TEST_TICKS) return;
		boolean gridEmpty = true;
		for (Slot s : cm.getInputGridSlots()) if (s.hasItem()) gridEmpty = false;
		if (!gridEmpty && !result) {
			RecipeJournal.markFake(testSignature);
			IsekaiMenuScreen.notice(Component.translatable("isekai_ui.recipes.fake"));
		} else if (gridEmpty) {
			IsekaiMenuScreen.notice(Component.translatable("isekai_ui.recipes.test_missing"));
		}
		testSignature = null;
	}

	private static boolean same(ItemStack[] a, ItemStack[] b) {
		if (a.length != b.length) return false;
		for (int i = 0; i < a.length; i++) if (!ItemStack.matches(a[i], b[i])) return false;
		return true;
	}

	private static ItemStack[] copy(ItemStack[] a) {
		ItemStack[] out = new ItemStack[a.length];
		for (int i = 0; i < a.length; i++) out[i] = a[i].copy();
		return out;
	}
}
