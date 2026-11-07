package com.compelium.isekaiui.recipes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Fila de cliques no inventario, um a cada 2 ticks (como uma pessoa rapida faria), para colocar os
 * ingredientes de uma receita sem parecer "hack" para o anti-cheat do servidor. Para tudo se a tela mudar.
 */
public final class ClickQueue {
	/** Um passo da fila (no maximo um clique). Devolve false para cancelar o resto. */
	public interface Step {
		boolean run(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu);
	}

	private static final ArrayDeque<Step> STEPS = new ArrayDeque<>();
	private static int containerId = -1;
	private static int cooldown;

	private ClickQueue() {
	}

	public static void start(AbstractContainerMenu menu, List<Step> steps) {
		STEPS.clear();
		STEPS.addAll(steps);
		containerId = menu.containerId;
		cooldown = 0;
	}

	/** Coloca um passo no comeco da fila (ex.: tentar de novo depois de pegar outra pilha). */
	public static void pushFront(Step step) {
		STEPS.addFirst(step);
	}

	public static boolean busy() {
		return !STEPS.isEmpty();
	}

	public static void cancel() {
		STEPS.clear();
	}

	public static void tick(Minecraft mc) {
		if (STEPS.isEmpty()) return;
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen == null || p.containerMenu.containerId != containerId) {
			STEPS.clear();
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		Step s = STEPS.poll();
		if (s != null && !s.run(mc, p, p.containerMenu)) STEPS.clear();
		cooldown = 1;
	}
}
