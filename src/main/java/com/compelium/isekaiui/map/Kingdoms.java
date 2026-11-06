package com.compelium.isekaiui.map;

import com.compelium.isekaiui.IsekaiUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reinos marcados a mao no mapa (por servidor): nome e cor escolhida. Os chunks de cada reino ficam no
 * arquivo de chunks de cada dimensao (DimMap); aqui ficam so os nomes e as cores.
 */
public final class Kingdoms {
	/** Cores para escolher (o primeiro clique na amostra troca para a proxima). */
	public static final int[] COLORS = {0xC9463D, 0xE0883A, 0xD9C24A, 0x5DA64F, 0x3E9C8F, 0x4A78C9, 0x8E5BC9, 0xC95B9A, 0x8A6A4A, 0xD8D2C4};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** nome (como o jogador escreveu) -> cor RGB, ou -1 para a cor automatica. */
	private static Map<String, Integer> known = new LinkedHashMap<>();
	private static Path file;

	private Kingdoms() {
	}

	static void load(Path serverDir) {
		file = serverDir.resolve("kingdoms.json");
		known = new LinkedHashMap<>();
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Map<String, Integer> m = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, Integer>>() {
				}.getType());
				if (m != null) known.putAll(m);
			} catch (Exception e) {
				IsekaiUI.LOG.warn("Nao consegui ler {}", file, e);
			}
		}
	}

	private static String find(String name) {
		if (name == null) return null;
		String k = name.strip().toLowerCase(Locale.ROOT);
		for (String n : known.keySet()) if (n.toLowerCase(Locale.ROOT).equals(k)) return n;
		return null;
	}

	/** Cor escolhida pelo jogador (ou null para usar a cor automatica). */
	public static Integer color(String name) {
		String n = find(name);
		if (n == null) return null;
		Integer c = known.get(n);
		return c == null || c < 0 ? null : c;
	}

	/** Todos os reinos conhecidos: os criados aqui e os que aparecem nos chunks do mapa atual (ordem alfabetica). */
	public static List<String> names(DimMap map) {
		TreeMap<String, String> all = new TreeMap<>();
		for (String n : known.keySet()) all.put(n.toLowerCase(Locale.ROOT), n);
		if (map != null) {
			for (String n : map.claims().values()) all.putIfAbsent(n.toLowerCase(Locale.ROOT), n);
		}
		return new ArrayList<>(all.values());
	}

	public static void add(String name) {
		if (name == null || name.isBlank() || find(name) != null) return;
		known.put(name.strip(), -1);
		save();
	}

	/** Troca para a proxima cor da lista. */
	public static int cycleColor(String name, int currentColor) {
		String n = find(name);
		if (n == null) {
			n = name.strip();
		}
		int next = COLORS[0];
		for (int i = 0; i < COLORS.length; i++) {
			if (COLORS[i] == currentColor) {
				next = COLORS[(i + 1) % COLORS.length];
				break;
			}
		}
		known.put(n, next);
		save();
		return next;
	}

	public static void rename(DimMap map, String oldName, String newName) {
		if (oldName == null || newName == null || newName.isBlank()) return;
		String clean = newName.strip();
		String n = find(oldName);
		Integer color = n == null ? Integer.valueOf(-1) : known.remove(n);
		known.put(clean, color == null ? -1 : color);
		String emblem = Emblems.get(oldName);
		if (emblem != null) {
			Emblems.set(oldName, null);
			Emblems.set(clean, emblem);
		}
		if (map != null) map.renameClaims(oldName, clean);
		save();
	}

	public static void remove(DimMap map, String name) {
		String n = find(name);
		if (n != null) known.remove(n);
		Emblems.set(name, null);
		if (map != null) map.removeClaims(name);
		save();
	}

	private static void save() {
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(known, w);
			}
		} catch (Exception e) {
			IsekaiUI.LOG.warn("Nao consegui salvar {}", file, e);
		}
	}
}
