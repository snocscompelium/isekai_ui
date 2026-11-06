package com.compelium.isekaiui.map;

import com.compelium.isekaiui.IsekaiUI;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Texturas do mapa em blocos de 128x128 pixels. LOD 0 = 1 pixel por bloco,
 * LOD 1 = 1 pixel a cada 2 blocos, ... ate LOD 3 (1 pixel a cada 8 blocos).
 */
public final class TileCache {
	public static final int SIZE = 128;
	public static final int MAX_LOD = 3;
	private static final int MAX_TILES = 200;

	private static int counter;
	private static ResourceLocation fogId;
	private static DynamicTexture fogTexture;

	private final DimMap map;
	private final Long2ObjectLinkedOpenHashMap<Tile> tiles = new Long2ObjectLinkedOpenHashMap<>();
	private final List<Tile> pending = new ArrayList<>();
	private long frame;

	private static final class Tile {
		final int lod;
		final int tx;
		final int tz;
		DynamicTexture texture;
		ResourceLocation id;
		boolean dirty = true;
		boolean built;
		long lastFrame;

		Tile(int lod, int tx, int tz) {
			this.lod = lod;
			this.tx = tx;
			this.tz = tz;
		}
	}

	TileCache(DimMap map) {
		this.map = map;
	}

	private static long tileKey(int lod, int tx, int tz) {
		return ((long) lod << 58) | ((tx & 0x1FFFFFFFL) << 29) | (tz & 0x1FFFFFFFL);
	}

	public void beginFrame() {
		frame++;
		pending.clear();
	}

	/** Textura a desenhar para o tile, ou null se ainda nao existe (desenhar nevoa no lugar). */
	public ResourceLocation request(int lod, int tx, int tz) {
		if (!hasAnyData(lod, tx, tz)) return null;
		long k = tileKey(lod, tx, tz);
		Tile t = tiles.getAndMoveToLast(k);
		if (t == null) {
			t = new Tile(lod, tx, tz);
			tiles.putAndMoveToLast(k, t);
			evict();
		}
		t.lastFrame = frame;
		if (t.dirty && !pending.contains(t)) pending.add(t);
		return t.built ? t.id : null;
	}

	/** Reconstroi tiles pendentes ate acabar o tempo (pelo menos 1 por frame). */
	public void buildPending(long budgetNanos) {
		long start = System.nanoTime();
		for (Tile t : pending) {
			if (!t.dirty) continue;
			build(t);
			if (System.nanoTime() - start > budgetNanos) break;
		}
		pending.clear();
	}

	public void invalidateChunk(int cx, int cz) {
		for (int lod = 0; lod <= MAX_LOD; lod++) {
			int chunksPerTile = (SIZE << lod) >> 4;
			int tx0 = Math.floorDiv(cx - 1, chunksPerTile);
			int tx1 = Math.floorDiv(cx + 1, chunksPerTile);
			int tz0 = Math.floorDiv(cz - 1, chunksPerTile);
			int tz1 = Math.floorDiv(cz + 1, chunksPerTile);
			for (int tz = tz0; tz <= tz1; tz++) {
				for (int tx = tx0; tx <= tx1; tx++) {
					Tile t = tiles.get(tileKey(lod, tx, tz));
					if (t != null) t.dirty = true;
				}
			}
		}
	}

	public void clear() {
		for (Tile t : tiles.values()) release(t);
		tiles.clear();
		pending.clear();
	}

	public static ResourceLocation fogTexture() {
		if (fogId == null) {
			NativeImage img = new NativeImage(SIZE, SIZE, false);
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					putPixel(img, x, z, MapStyle.fog(x, z));
				}
			}
			fogTexture = new DynamicTexture(img);
			fogId = IsekaiUI.id("map_fog");
			Minecraft.getInstance().getTextureManager().register(fogId, fogTexture);
		}
		return fogId;
	}

	// ------------------------------------------------------------------ interno

	private boolean hasAnyData(int lod, int tx, int tz) {
		int blocks = SIZE << lod;
		int rx0 = Math.floorDiv(tx * blocks, 512);
		int rx1 = Math.floorDiv(tx * blocks + blocks - 1, 512);
		int rz0 = Math.floorDiv(tz * blocks, 512);
		int rz1 = Math.floorDiv(tz * blocks + blocks - 1, 512);
		for (int rz = rz0; rz <= rz1; rz++) {
			for (int rx = rx0; rx <= rx1; rx++) {
				if (map.regionHasData(rx, rz)) return true;
			}
		}
		return false;
	}

	private void evict() {
		while (tiles.size() > MAX_TILES) {
			long k = tiles.firstLongKey();
			Tile t = tiles.get(k);
			if (t != null && t.lastFrame >= frame - 1) break;
			tiles.removeFirst();
			if (t != null) release(t);
		}
	}

	private static void release(Tile t) {
		if (t.id != null) Minecraft.getInstance().getTextureManager().release(t.id);
		t.texture = null;
		t.id = null;
		t.built = false;
	}

	private void build(Tile t) {
		int span = 1 << t.lod;
		int originX = t.tx * SIZE * span;
		int originZ = t.tz * SIZE * span;
		int cx0 = (originX >> 4) - 1;
		int cz0 = (originZ >> 4) - 1;
		int across = ((SIZE * span) >> 4) + 2;

		for (int rz = Math.floorDiv(cz0, 32); rz <= Math.floorDiv(cz0 + across - 1, 32); rz++) {
			for (int rx = Math.floorDiv(cx0, 32); rx <= Math.floorDiv(cx0 + across - 1, 32); rx++) {
				map.ensureRegion(rx, rz);
			}
		}
		ChunkSlice[] grid = new ChunkSlice[across * across];
		boolean[] explored = new boolean[across * across];
		for (int gz = 0; gz < across; gz++) {
			for (int gx = 0; gx < across; gx++) {
				grid[gz * across + gx] = map.chunkLoaded(cx0 + gx, cz0 + gz);
				explored[gz * across + gx] = map.isExploredLoaded(cx0 + gx, cz0 + gz);
			}
		}
		Sampler sampler = new Sampler(grid, explored, cx0, cz0, across);

		int samples = Math.min(span, 2);
		int step = span / samples;
		int n = samples * samples;
		if (t.texture == null) {
			t.texture = new DynamicTexture(new NativeImage(SIZE, SIZE, false));
			t.id = IsekaiUI.id("map_tile/" + (counter++));
			Minecraft.getInstance().getTextureManager().register(t.id, t.texture);
		}
		NativeImage img = t.texture.getPixels();
		if (img == null) return;
		for (int pz = 0; pz < SIZE; pz++) {
			for (int px = 0; px < SIZE; px++) {
				int r = 0, g = 0, b = 0;
				for (int sz = 0; sz < samples; sz++) {
					for (int sx = 0; sx < samples; sx++) {
						int c = sampler.color(originX + px * span + sx * step, originZ + pz * span + sz * step, px, pz);
						r += (c >> 16) & 0xFF;
						g += (c >> 8) & 0xFF;
						b += c & 0xFF;
					}
				}
				putPixel(img, px, pz, ((r / n) << 16) | ((g / n) << 8) | (b / n));
			}
		}
		t.texture.upload();
		t.dirty = false;
		t.built = true;
	}

	static void putPixel(NativeImage img, int x, int y, int rgb) {
		img.setPixel(x, y, 0xFF000000 | rgb);
	}

	/** Le cores do terreno a partir de uma grade de chunks ja carregada. */
	private static final class Sampler {
		private final ChunkSlice[] grid;
		private final boolean[] explored;
		private final int cx0;
		private final int cz0;
		private final int across;

		Sampler(ChunkSlice[] grid, boolean[] explored, int cx0, int cz0, int across) {
			this.grid = grid;
			this.explored = explored;
			this.cx0 = cx0;
			this.cz0 = cz0;
			this.across = across;
		}

		private ChunkSlice slice(int cx, int cz) {
			int gx = cx - cx0;
			int gz = cz - cz0;
			if (gx < 0 || gz < 0 || gx >= across || gz >= across) return null;
			return grid[gz * across + gx];
		}

		private float exp(int cx, int cz) {
			int gx = cx - cx0;
			int gz = cz - cz0;
			if (gx < 0 || gz < 0 || gx >= across || gz >= across) return 0f;
			return explored[gz * across + gx] ? 1f : 0f;
		}

		private int heightAt(int bx, int bz, int fallback) {
			ChunkSlice s = slice(bx >> 4, bz >> 4);
			return s == null ? fallback : s.height[ChunkSlice.index(bx & 15, bz & 15)];
		}

		private int depthAt(int bx, int bz) {
			ChunkSlice s = slice(bx >> 4, bz >> 4);
			return s == null ? 1 : s.depth[ChunkSlice.index(bx & 15, bz & 15)];
		}

		int color(int bx, int bz, int px, int pz) {
			float fx = (bx - 7.5f) / 16f;
			float fz = (bz - 7.5f) / 16f;
			int ix = (int) Math.floor(fx);
			int iz = (int) Math.floor(fz);
			float tx = fx - ix;
			float tz = fz - iz;
			float top = exp(ix, iz) + (exp(ix + 1, iz) - exp(ix, iz)) * tx;
			float bottom = exp(ix, iz + 1) + (exp(ix + 1, iz + 1) - exp(ix, iz + 1)) * tx;
			float e = top + (bottom - top) * tz;
			float k = Math.max(0f, Math.min(1f, (e - 0.12f) / 0.76f));
			float fog = 1f - k * k * (3f - 2f * k);
			if (fog >= 0.999f) return MapStyle.fog(px, pz);

			ChunkSlice s = slice(bx >> 4, bz >> 4);
			int terrain;
			if (s == null) {
				terrain = MapStyle.PARCH;
			} else {
				int i = ChunkSlice.index(bx & 15, bz & 15);
				int h = s.height[i];
				int depth = s.depth[i];
				if (depth > 0) {
					boolean shore = depthAt(bx, bz - 1) == 0 || depthAt(bx - 1, bz) == 0;
					terrain = MapStyle.water(depth, shore, bx, bz);
				} else {
					terrain = MapStyle.land(s.color[i] & 0xFF, h,
							heightAt(bx, bz - 1, h), heightAt(bx - 1, bz, h), heightAt(bx - 2, bz - 2, h), bx, bz);
				}
			}
			return MapStyle.withFog(terrain, fog, px, pz);
		}
	}
}
