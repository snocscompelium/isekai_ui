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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Simbolo (icone) escolhido para cada reino/terreno do Lands, por servidor. */
public final class Emblems {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static Map<String, String> byLand = new HashMap<>();
	private static Path file;

	private Emblems() {
	}

	static void load(Path serverDir) {
		file = serverDir.resolve("emblems.json");
		byLand = new HashMap<>();
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Map<String, String> m = GSON.fromJson(r, new TypeToken<Map<String, String>>() {
				}.getType());
				if (m != null) byLand.putAll(m);
			} catch (Exception e) {
				IsekaiUI.LOG.warn("Nao consegui ler {}", file, e);
			}
		}
	}

	private static String key(String land) {
		return land.strip().toLowerCase(Locale.ROOT);
	}

	/** Icone do reino (ou null se nao tiver). */
	public static String get(String land) {
		return land == null ? null : byLand.get(key(land));
	}

	public static void set(String land, String iconKey) {
		if (land == null) return;
		if (iconKey == null) byLand.remove(key(land));
		else byLand.put(key(land), iconKey);
		save();
	}

	private static void save() {
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(byLand, w);
			}
		} catch (Exception e) {
			IsekaiUI.LOG.warn("Nao consegui salvar {}", file, e);
		}
	}
}
