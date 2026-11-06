package com.compelium.isekaiui.map;

import com.compelium.isekaiui.IsekaiUI;
import com.compelium.isekaiui.gui.Draw;
import com.compelium.isekaiui.gui.Theme;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Icones do mapa: simbolos brancos que ganham a cor do marcador ("glyph:"), brasoes coloridos
 * do proprio mod ("emblem:") e imagens PNG que o jogador coloca em .minecraft/isekai_ui/icons ("custom:").
 */
public final class MapIcons {
	public enum Kind { GLYPH, IMAGE }

	public record Icon(String key, Kind kind, ResourceLocation id, int width, int height) {
		/** Desenha centralizado em (cx, cy) cabendo num quadrado de "size" pixels. */
		public void draw(GuiGraphics g, int cx, int cy, int size, int tint, float alpha) {
			if (this.kind == Kind.GLYPH) {
				int x = cx - size / 2;
				int y = cy - size / 2;
				Draw.sprite(g, this.id, x + 1, y + 1, size, size, Draw.alpha(0xC0201810, alpha));
				Draw.sprite(g, this.id, x, y, size, size, Draw.alpha(0xFF000000 | tint, alpha));
				return;
			}
			float scale = Math.min((float) size / this.width, (float) size / this.height);
			int w = Math.max(1, Math.round(this.width * scale));
			int h = Math.max(1, Math.round(this.height * scale));
			Draw.texture(g, this.id, cx - w / 2, cy - h / 2, w, h, 0f, 0f, this.width, this.height, this.width, this.height, Draw.white(alpha));
		}
	}

	private static final String[] GLYPHS = {"pin", "star", "house", "flag", "sword", "compass", "gem", "skull", "tree", "pickaxe", "anchor", "portal", "tower", "paw", "coin", "book"};
	private static final String[] EMBLEMS = {"crown", "lily", "eagle", "tower", "swords", "sun", "moon", "flame", "cross", "chevron", "tree", "anchor"};
	private static final int MAX_CUSTOM_SIZE = 48;

	private static final Map<String, Icon> builtin = new LinkedHashMap<>();
	private static final Map<String, Icon> custom = new LinkedHashMap<>();
	private static boolean customLoaded;

	static {
		for (String g : GLYPHS) {
			String key = "glyph:" + g;
			builtin.put(key, new Icon(key, Kind.GLYPH, Theme.icon(g), 12, 12));
		}
		for (String e : EMBLEMS) {
			String key = "emblem:" + e;
			builtin.put(key, new Icon(key, Kind.IMAGE, IsekaiUI.id("textures/gui/plain/emblem/" + e + ".png"), 16, 16));
		}
	}

	private MapIcons() {
	}

	public static Icon get(String key) {
		if (key == null) return builtin.get("glyph:pin");
		Icon i = builtin.get(key);
		if (i != null) return i;
		ensureCustom();
		i = custom.get(key);
		return i != null ? i : builtin.get("glyph:pin");
	}

	/** Como {@link #get}, mas devolve null quando o icone nao existe (ex.: PNG apagado da pasta). */
	public static Icon find(String key) {
		if (key == null) return null;
		Icon i = builtin.get(key);
		if (i != null) return i;
		ensureCustom();
		return custom.get(key);
	}

	public static List<Icon> glyphs() {
		List<Icon> list = new ArrayList<>();
		for (Icon i : builtin.values()) if (i.kind() == Kind.GLYPH) list.add(i);
		return list;
	}

	public static List<Icon> emblems() {
		List<Icon> list = new ArrayList<>();
		for (Icon i : builtin.values()) if (i.kind() == Kind.IMAGE) list.add(i);
		return list;
	}

	public static List<Icon> customIcons() {
		ensureCustom();
		return new ArrayList<>(custom.values());
	}

	public static Path folder() {
		return FabricLoader.getInstance().getGameDir().resolve("isekai_ui").resolve("icons");
	}

	/** Recarrega os PNGs da pasta de icones (botao "recarregar" no mapa). */
	public static void reloadCustom() {
		for (Icon i : custom.values()) Minecraft.getInstance().getTextureManager().release(i.id());
		custom.clear();
		customLoaded = false;
		ensureCustom();
	}

	private static void ensureCustom() {
		if (customLoaded) return;
		customLoaded = true;
		Path dir = folder();
		try {
			Files.createDirectories(dir);
			Path readme = dir.resolve("LEIA-ME.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, "Coloque aqui imagens PNG (de preferencia recortadas, com fundo transparente)\n"
						+ "para usar como icone de marcador ou simbolo de reino no mapa.\n"
						+ "Depois clique em \"recarregar icones\" no mapa.\n", StandardCharsets.UTF_8);
			}
			List<Path> files;
			try (Stream<Path> s = Files.list(dir)) {
				files = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().toList();
			}
			int n = 0;
			for (Path file : files) {
				if (n++ >= 64) break;
				try (InputStream in = Files.newInputStream(file)) {
					NativeImage img = shrink(NativeImage.read(in));
					String name = file.getFileName().toString();
					String key = "custom:" + name;
					ResourceLocation id = IsekaiUI.id("custom_icon/" + sanitize(name) + "_" + n);
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
					custom.put(key, new Icon(key, Kind.IMAGE, id, img.getWidth(), img.getHeight()));
				} catch (Exception e) {
					IsekaiUI.LOG.warn("Nao consegui carregar o icone {}", file, e);
				}
			}
		} catch (Exception e) {
			IsekaiUI.LOG.warn("Nao consegui ler a pasta de icones {}", dir, e);
		}
	}

	/** Diminui imagens grandes (media dos pixels, sem borda escura em PNG recortado). */
	private static NativeImage shrink(NativeImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		if (w <= MAX_CUSTOM_SIZE && h <= MAX_CUSTOM_SIZE) return src;
		float scale = Math.min((float) MAX_CUSTOM_SIZE / w, (float) MAX_CUSTOM_SIZE / h);
		int tw = Math.max(1, Math.round(w * scale));
		int th = Math.max(1, Math.round(h * scale));
		NativeImage out = new NativeImage(tw, th, false);
		for (int ty = 0; ty < th; ty++) {
			int y0 = ty * h / th;
			int y1 = Math.max(y0 + 1, (ty + 1) * h / th);
			for (int tx = 0; tx < tw; tx++) {
				int x0 = tx * w / tw;
				int x1 = Math.max(x0 + 1, (tx + 1) * w / tw);
				long a = 0, r = 0, gg = 0, b = 0;
				int count = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						int c = src.getPixel(x, y);
						int ca = (c >>> 24) & 0xFF;
						a += ca;
						r += (long) ((c >> 16) & 0xFF) * ca;
						gg += (long) ((c >> 8) & 0xFF) * ca;
						b += (long) (c & 0xFF) * ca;
						count++;
					}
				}
				int outA = (int) (a / count);
				int outR = a == 0 ? 0 : (int) (r / a);
				int outG = a == 0 ? 0 : (int) (gg / a);
				int outB = a == 0 ? 0 : (int) (b / a);
				out.setPixel(tx, ty, (outA << 24) | (outR << 16) | (outG << 8) | outB);
			}
		}
		src.close();
		return out;
	}

	private static String sanitize(String s) {
		String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
		return out.isEmpty() ? "icon" : out;
	}
}
