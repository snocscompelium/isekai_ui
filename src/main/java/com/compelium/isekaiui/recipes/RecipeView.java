package com.compelium.isekaiui.recipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.List;

/**
 * Uma receita como o livro mostra: anotada (funcionou de verdade) ou vinda do servidor e ainda nao testada.
 * inputs: uma lista de opcoes por posicao (receitas do servidor aceitam varios itens, ex.: qualquer tabua).
 *
 * @param learned  a receita anotada (null se ainda nao foi testada)
 * @param serverId id da receita no livro do servidor (para colocar os itens pelo proprio jogo), ou null
 * @param signature texto que identifica a receita nao testada (para lembrar que ela nao funciona)
 */
public record RecipeView(RecipeJournal.Station station, int w, int h, List<List<ItemStack>> inputs, ItemStack result,
						 RecipeJournal.Entry learned, RecipeDisplayId serverId, String signature) {

	public boolean tested() {
		return this.learned != null;
	}

	/** Item mostrado numa posicao (troca de opcao a cada segundo quando ha varias). */
	public ItemStack shown(int index, long timeMillis) {
		if (index < 0 || index >= this.inputs.size()) return ItemStack.EMPTY;
		List<ItemStack> opts = this.inputs.get(index);
		if (opts.isEmpty()) return ItemStack.EMPTY;
		return opts.get((int) ((timeMillis / 1000L) % opts.size()));
	}
}
