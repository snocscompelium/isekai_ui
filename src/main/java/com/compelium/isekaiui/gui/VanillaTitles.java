package com.compelium.isekaiui.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Set;

/**
 * Diz se o titulo de uma tela e o nome padrao do jogo (ex.: "Bau", "Fornalha"). Menus de plugin usam
 * titulos proprios (texto, cores, fonte de resource pack), entao eles nunca passam neste teste.
 */
public final class VanillaTitles {
	public static final Set<String> STORAGE = Set.of(
			"container.chest", "container.chestDouble", "container.shulkerBox", "container.enderchest", "container.barrel");

	private VanillaTitles() {
	}

	public static boolean is(Component title, Set<String> keys) {
		String key = key(title);
		return key != null && keys.contains(key);
	}

	public static boolean is(Component title, String... keys) {
		String key = key(title);
		if (key == null) return false;
		for (String k : keys) if (k.equals(key)) return true;
		return false;
	}

	/** Chave de traducao do titulo, ou null se nao for um titulo "puro" do jogo. */
	public static String key(Component title) {
		if (title == null || !title.getSiblings().isEmpty()) return null;
		if (!(title.getContents() instanceof TranslatableContents tc)) return null;
		if (tc.getArgs().length > 0) return null;
		Style style = title.getStyle();
		if (style.getFont() != null && !style.getFont().equals(Style.DEFAULT_FONT)) return null;
		return tc.getKey();
	}
}
