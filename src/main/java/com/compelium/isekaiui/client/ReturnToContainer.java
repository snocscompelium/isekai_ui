package com.compelium.isekaiui.client;

import com.compelium.isekaiui.mixin.MinecraftAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * "Ir para o craft" / "Voltar ao bau". Nao existe craft com o bau aberto (o servidor ignora a grade 2x2
 * nesse caso), entao o mod so faz o que o jogador faria a mao: fecha o bau, abre o inventario e, para
 * voltar, fecha o inventario e da um clique direito normal no mesmo bau (que ainda esta na mira).
 */
public final class ReturnToContainer {
	private static final long WINDOW_NANOS = 120_000_000_000L;
	private static BlockPos target;
	private static long armedNanos;
	private static int useInTicks = -1;

	private ReturnToContainer() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ReturnToContainer::tick);
	}

	/** Bloco de armazenamento que esta na mira agora (bau, barril, shulker, bau do ender...), ou null. */
	public static BlockPos lookedAtContainer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
		BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
		if (be instanceof BaseContainerBlockEntity || be instanceof EnderChestBlockEntity) return hit.getBlockPos();
		return null;
	}

	public static void arm(BlockPos pos) {
		target = pos;
		armedNanos = System.nanoTime();
	}

	/** O bau de onde o jogador veio ainda esta na mira (e foi ha pouco tempo)? */
	public static boolean available() {
		if (target == null || System.nanoTime() - armedNanos > WINDOW_NANOS) return false;
		return target.equals(lookedAtContainer());
	}

	/** Chamado depois que o inventario fechou: em 2 ticks da o clique direito no bau. */
	public static void scheduleReopen() {
		useInTicks = 2;
	}

	private static void tick(Minecraft mc) {
		if (useInTicks < 0) return;
		if (mc.player == null || mc.screen != null) {
			useInTicks = -1;
			return;
		}
		if (useInTicks-- > 0) return;
		useInTicks = -1;
		if (available()) {
			target = null;
			((MinecraftAccessor) mc).isekai$startUseItem();
		}
	}
}
