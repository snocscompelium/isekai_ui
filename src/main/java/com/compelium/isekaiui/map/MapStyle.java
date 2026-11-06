package com.compelium.isekaiui.map;

import net.minecraft.world.level.material.MapColor;

import java.util.Random;

/**
 * Estilo de pergaminho: cores dos blocos desbotadas sobre papel, relevo em degraus
 * (curvas de nivel) e nevoa nas areas ainda nao exploradas.
 */
public final class MapStyle {
	private MapStyle() {
	}

	public static final int PARCH = 0xD8C8A2;
	private static final int PARCH_HIGH = 0xECE2C4;
	private static final int PARCH_LOW = 0xC2AF87;
	private static final int WATER_SHALLOW = 0xA9BFB5;
	private static final int WATER_DEEP = 0x6E8C92;
	public static final int INK = 0x5E4E35;
	private static final int FOG_LIGHT = 0xCFC2A7;
	private static final int FOG_DARK = 0xB0A48C;
	private static final int BAND = 8;

	private static final int TILE = 128;
	private static final int[] FOG_PIXELS = new int[TILE * TILE];
	private static final float[] LATTICE = new float[64 * 64];

	static {
		Random r = new Random(0x15EC41L);
		for (int i = 0; i < LATTICE.length; i++) LATTICE[i] = r.nextFloat();
		for (int z = 0; z < TILE; z++) {
			for (int x = 0; x < TILE; x++) {
				float n = 0.55f * valueNoise(x / 32f, z / 32f, 4, 0)
						+ 0.30f * valueNoise(x / 16f, z / 16f, 8, 17)
						+ 0.15f * valueNoise(x / 8f, z / 8f, 16, 33);
				float t = smooth((n - 0.25f) / 0.6f);
				int c = mix(FOG_LIGHT, FOG_DARK, t);
				// leve tom violeta nas manchas mais escuras (lembra as nuvens da referencia)
				c = mix(c, 0xA79FB0, t * 0.18f);
				FOG_PIXELS[z * TILE + x] = c;
			}
		}
	}

	/** Cor da nevoa no pixel (px,pz) de um tile (repete a cada 128 px, sem emendas). */
	public static int fog(int px, int pz) {
		return FOG_PIXELS[(pz & (TILE - 1)) * TILE + (px & (TILE - 1))];
	}

	public static int land(int colorId, int h, int hN, int hW, int hNW2, int bx, int bz) {
		float elev = clamp((h - 64) / 110f, -0.5f, 1f);
		int base = elev >= 0f ? mix(PARCH, PARCH_HIGH, elev) : mix(PARCH, PARCH_LOW, -elev);
		int c = base;
		if (colorId > 0) {
			int col = MapColor.byId(colorId).col;
			c = mix(base, desaturate(col, 0.5f), 0.42f);
		}
		float slope = (h - hN) * 0.6f + (h - hW) * 0.4f;
		c = shade(c, 1f + clamp(slope * 0.045f, -0.15f, 0.12f));
		int band = Math.floorDiv(h, BAND);
		if (band != Math.floorDiv(hN, BAND) || band != Math.floorDiv(hW, BAND)) {
			c = mix(c, INK, 0.17f);
		} else if (Math.floorDiv(hNW2, BAND) > band) {
			c = shade(c, 0.93f);
		}
		return shade(c, 1f + grain(bx, bz) * 0.03f);
	}

	public static int water(int depth, boolean shore, int bx, int bz) {
		float t = clamp((depth - 1) / 10f, 0f, 1f);
		int c = mix(WATER_SHALLOW, WATER_DEEP, t);
		if (t > 0.2f && Math.floorMod(bx + bz * 3, 9) == 0) c = shade(c, 0.97f);
		if (shore) c = mix(c, INK, 0.30f);
		return shade(c, 1f + grain(bx, bz) * 0.02f);
	}

	/** Mistura o terreno com a nevoa (0 = explorado, 1 = desconhecido) e desenha a borda "a tinta". */
	public static int withFog(int terrain, float fog, int px, int pz) {
		if (fog <= 0.001f) return terrain;
		int c = fog >= 0.999f ? fog(px, pz) : mix(terrain, fog(px, pz), fog);
		if (fog > 0.22f && fog < 0.78f) {
			float edge = 1f - Math.abs(fog - 0.5f) / 0.28f;
			c = mix(c, INK, edge * 0.20f);
		}
		return c;
	}

	// ---------- utilitarios de cor ----------

	public static int mix(int a, int b, float t) {
		if (t <= 0f) return a & 0xFFFFFF;
		if (t >= 1f) return b & 0xFFFFFF;
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int r = Math.round(ar + (br - ar) * t);
		int g = Math.round(ag + (bg - ag) * t);
		int bl = Math.round(ab + (bb - ab) * t);
		return (r << 16) | (g << 8) | bl;
	}

	public static int shade(int c, float f) {
		int r = Math.min(255, Math.max(0, Math.round(((c >> 16) & 0xFF) * f)));
		int g = Math.min(255, Math.max(0, Math.round(((c >> 8) & 0xFF) * f)));
		int b = Math.min(255, Math.max(0, Math.round((c & 0xFF) * f)));
		return (r << 16) | (g << 8) | b;
	}

	public static int desaturate(int c, float amount) {
		int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		int l = (r * 30 + g * 59 + b * 11) / 100;
		int gray = (l << 16) | (l << 8) | l;
		return mix(c, gray, amount);
	}

	private static float grain(int x, int z) {
		int h = x * 374761393 + z * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= (h >>> 16);
		return ((h & 0xFFFF) / 32767.5f) - 1f;
	}

	private static float valueNoise(float x, float y, int period, int offset) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		float fx = smooth(x - x0);
		float fy = smooth(y - y0);
		float a = lattice(x0, y0, period, offset);
		float b = lattice(x0 + 1, y0, period, offset);
		float c = lattice(x0, y0 + 1, period, offset);
		float d = lattice(x0 + 1, y0 + 1, period, offset);
		float top = a + (b - a) * fx;
		float bottom = c + (d - c) * fx;
		return top + (bottom - top) * fy;
	}

	private static float lattice(int x, int y, int period, int offset) {
		int ix = (Math.floorMod(x, period) + offset) & 63;
		int iy = (Math.floorMod(y, period) + offset * 3) & 63;
		return LATTICE[iy * 64 + ix];
	}

	private static float smooth(float t) {
		t = clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static float clamp(float v, float min, float max) {
		return v < min ? min : (v > max ? max : v);
	}
}
