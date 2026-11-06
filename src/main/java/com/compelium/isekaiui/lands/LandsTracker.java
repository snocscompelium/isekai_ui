package com.compelium.isekaiui.lands;

import com.compelium.isekaiui.IsekaiUI;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.map.DimMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Integracao com o plugin Lands sem precisar de nada no servidor: o Lands mostra um titulo no
 * meio da tela quando o jogador entra num terreno (ou volta para area sem dono). A gente le esse
 * titulo e, enquanto o jogador anda dentro do terreno, marca no mapa os chunks por onde ele passa.
 */
public final class LandsTracker {
	private enum State { UNKNOWN, IN_LAND, WILD }

	private static State state = State.UNKNOWN;
	private static String land;
	private static Pattern landPattern;
	private static final List<Pattern> wildPatterns = new ArrayList<>();
	private static Integer requiredRgb;

	private LandsTracker() {
	}

	public static void init() {
		reloadPatterns();
	}

	public static void reloadPatterns() {
		IsekaiConfig.LandsSettings cfg = IsekaiConfig.get().lands;
		try {
			landPattern = Pattern.compile(cfg.landPattern);
		} catch (PatternSyntaxException e) {
			IsekaiUI.LOG.warn("landPattern invalido no config, usando o padrao", e);
			landPattern = Pattern.compile("^\\s*(?<land>.+?)\\s*$");
		}
		wildPatterns.clear();
		for (String p : cfg.wildernessPatterns) {
			try {
				wildPatterns.add(Pattern.compile(p));
			} catch (PatternSyntaxException e) {
				IsekaiUI.LOG.warn("wildernessPatterns tem um padrao invalido: {}", p);
			}
		}
		requiredRgb = parseColor(cfg.requiredColor);
	}

	public static void reset() {
		state = State.UNKNOWN;
		land = null;
	}

	/** Nome do terreno onde o jogador esta (ou null se nao souber / area sem dono). */
	public static String currentLand() {
		if (!IsekaiConfig.get().lands.autoDetect) {
			// territorios marcados a mao: o dono e o do chunk onde o jogador esta
			Minecraft mc = Minecraft.getInstance();
			com.compelium.isekaiui.map.DimMap map = com.compelium.isekaiui.map.MapManager.current();
			if (map == null || mc.player == null) return null;
			return map.claim(mc.player.chunkPosition().x, mc.player.chunkPosition().z);
		}
		return state == State.IN_LAND ? land : null;
	}

	private static boolean auto() {
		IsekaiConfig.LandsSettings cfg = IsekaiConfig.get().lands;
		return cfg.enabled && cfg.autoDetect;
	}

	public static void onTitle(Component c) {
		IsekaiConfig.LandsSettings cfg = IsekaiConfig.get().lands;
		if (auto() && cfg.readTitle) handle(c, "titulo");
	}

	public static void onSubtitle(Component c) {
		IsekaiConfig.LandsSettings cfg = IsekaiConfig.get().lands;
		if (auto() && cfg.readSubtitle) handle(c, "subtitulo");
	}

	public static void onActionBar(Component c) {
		IsekaiConfig.LandsSettings cfg = IsekaiConfig.get().lands;
		if (auto() && cfg.readActionBar) handle(c, "actionbar");
	}

	private static void handle(Component c, String source) {
		if (c == null) return;
		String text = c.getString().replaceAll("§.", "").strip();
		if (text.isEmpty()) return;
		IsekaiConfig.LandsSettings cfg = IsekaiConfig.get().lands;
		if (cfg.debug) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				TextColor color = firstColor(c);
				String colorName = color == null ? "sem cor" : color.serialize();
				mc.player.displayClientMessage(Component.literal("[Isekai UI] " + source + " (" + colorName + "): \"" + text + "\"")
						.withStyle(ChatFormatting.GRAY), false);
			}
		}
		for (Pattern p : wildPatterns) {
			if (p.matcher(text).find()) {
				state = State.WILD;
				land = null;
				return;
			}
		}
		if (requiredRgb != null) {
			TextColor color = firstColor(c);
			if (color == null || (color.getValue() & 0xFFFFFF) != requiredRgb) return;
		}
		Matcher m = landPattern.matcher(text);
		if (!m.find()) return;
		String name;
		try {
			name = m.group("land");
		} catch (IllegalArgumentException e) {
			name = m.group();
		}
		if (name == null || name.isBlank()) return;
		state = State.IN_LAND;
		land = name.strip();
	}

	/** Chamado todo tick: pinta o chunk atual com o dono conhecido. */
	public static void tick(Minecraft mc, DimMap map) {
		if (map == null || mc.player == null || !auto()) return;
		int cx = mc.player.chunkPosition().x;
		int cz = mc.player.chunkPosition().z;
		if (state == State.IN_LAND && land != null) {
			map.setClaim(cx, cz, land);
		} else if (state == State.WILD) {
			map.setClaim(cx, cz, null);
		}
	}

	private static TextColor firstColor(Component c) {
		Optional<TextColor> found = c.visit((Style style, String s) -> {
			if (!s.isBlank() && style.getColor() != null) return Optional.of(style.getColor());
			return Optional.empty();
		}, Style.EMPTY);
		return found.orElse(null);
	}

	private static Integer parseColor(String s) {
		if (s == null || s.isBlank()) return null;
		String v = s.strip();
		try {
			if (v.startsWith("#")) return Integer.parseInt(v.substring(1), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return null;
		}
		ChatFormatting f = ChatFormatting.getByName(v.toLowerCase(java.util.Locale.ROOT));
		if (f != null && f.getColor() != null) return f.getColor() & 0xFFFFFF;
		return null;
	}
}
