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
import java.util.List;
import java.util.UUID;

/** Marcadores (waypoints) de uma dimensao, salvos em waypoints.json na pasta do mapa. */
public final class Waypoints {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Cores dos marcadores (sem transparencia). */
	public static final int[] COLORS = {0xE0465E, 0xE8A23A, 0xE8D35A, 0x5FBF5A, 0x4FB5C9, 0x5A7FE0, 0xA96AE0, 0xF2EEE2};

	public static final class Waypoint {
		public String id = UUID.randomUUID().toString();
		public String name = "";
		public int x;
		public int y;
		public int z;
		public String icon = "glyph:pin";
		public int color = COLORS[0];
	}

	private final Path file;
	private final List<Waypoint> list = new ArrayList<>();

	Waypoints(Path dir) {
		this.file = dir.resolve("waypoints.json");
		if (Files.exists(this.file)) {
			try (Reader r = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
				List<Waypoint> loaded = GSON.fromJson(r, new TypeToken<List<Waypoint>>() {
				}.getType());
				if (loaded != null) for (Waypoint w : loaded) if (w != null) this.list.add(w);
			} catch (Exception e) {
				IsekaiUI.LOG.warn("Nao consegui ler {}", this.file, e);
			}
		}
	}

	public List<Waypoint> all() {
		return this.list;
	}

	public void add(Waypoint w) {
		this.list.add(w);
		save();
	}

	public void remove(Waypoint w) {
		this.list.remove(w);
		save();
	}

	public void save() {
		try {
			Files.createDirectories(this.file.getParent());
			try (Writer w = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8)) {
				GSON.toJson(this.list, w);
			}
		} catch (Exception e) {
			IsekaiUI.LOG.warn("Nao consegui salvar {}", this.file, e);
		}
	}
}
