package com.compelium.isekaiui.map;

import com.compelium.isekaiui.IsekaiUI;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Mapa de uma dimensao de um servidor. Guarda os chunks explorados em arquivos de regiao
 * (32x32 chunks) em .minecraft/isekai_ui/maps/&lt;servidor&gt;/&lt;dimensao&gt;/.
 */
public final class DimMap {
	private static final int MAGIC = 0x15EC41A1;
	private static final int VERSION = 1;
	private static final int CLAIMS_MAGIC = 0x15EC41C1;
	private static final int MAX_LOADED_REGIONS = 48;

	private final Path dir;
	private final Long2ObjectOpenHashMap<ChunkSlice> chunks = new Long2ObjectOpenHashMap<>();
	private final LongOpenHashSet explored = new LongOpenHashSet();
	private final Long2ObjectOpenHashMap<String> claims = new Long2ObjectOpenHashMap<>();
	private final LongLinkedOpenHashSet loadedRegions = new LongLinkedOpenHashSet();
	private final LongOpenHashSet dirtyRegions = new LongOpenHashSet();
	private final LongOpenHashSet regionsWithData = new LongOpenHashSet();
	private final TileCache tiles = new TileCache(this);
	private final Waypoints waypoints;
	private boolean claimsDirty;
	private int claimsVersion;

	public DimMap(Path dir) {
		this.dir = dir;
		try {
			Files.createDirectories(dir);
			try (Stream<Path> files = Files.list(dir)) {
				files.forEach(p -> {
					String n = p.getFileName().toString();
					if (n.startsWith("r.") && n.endsWith(".dat")) {
						String[] parts = n.split("\\.");
						if (parts.length == 4) {
							try {
								regionsWithData.add(key(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
							} catch (NumberFormatException ignored) {
							}
						}
					}
				});
			}
		} catch (IOException e) {
			IsekaiUI.LOG.warn("Nao consegui abrir a pasta do mapa {}", dir, e);
		}
		loadClaims();
		this.waypoints = new Waypoints(dir);
	}

	public Waypoints waypoints() {
		return this.waypoints;
	}

	/** Altura do chao conhecida no mapa (ou null se o chunk nunca foi lido). */
	public Integer surfaceY(int bx, int bz) {
		ChunkSlice s = chunk(bx >> 4, bz >> 4);
		if (s == null) return null;
		return (int) s.height[ChunkSlice.index(bx & 15, bz & 15)];
	}

	public static long key(int x, int z) {
		return ChunkPos.asLong(x, z);
	}

	public TileCache tiles() {
		return tiles;
	}

	// ------------------------------------------------------------------ acesso

	public boolean regionHasData(int rx, int rz) {
		return regionsWithData.contains(key(rx, rz));
	}

	public void ensureRegion(int rx, int rz) {
		long rk = key(rx, rz);
		if (loadedRegions.contains(rk)) {
			loadedRegions.addAndMoveToLast(rk);
			return;
		}
		loadedRegions.add(rk);
		loadRegion(rx, rz);
		while (loadedRegions.size() > MAX_LOADED_REGIONS) {
			long old = loadedRegions.firstLong();
			if (old == rk) break;
			if (dirtyRegions.contains(old)) saveRegion(old);
			unloadRegion(old);
			loadedRegions.removeFirstLong();
		}
	}

	public ChunkSlice chunk(int cx, int cz) {
		ensureRegion(cx >> 5, cz >> 5);
		return chunks.get(key(cx, cz));
	}

	/** Versao sem carregar regiao (use depois de ensureRegion). */
	public ChunkSlice chunkLoaded(int cx, int cz) {
		return chunks.get(key(cx, cz));
	}

	public boolean isExplored(int cx, int cz) {
		ensureRegion(cx >> 5, cz >> 5);
		return explored.contains(key(cx, cz));
	}

	public boolean isExploredLoaded(int cx, int cz) {
		return explored.contains(key(cx, cz));
	}

	public void putScan(int cx, int cz, ChunkSlice slice) {
		ensureRegion(cx >> 5, cz >> 5);
		long k = key(cx, cz);
		if (slice.sameAs(chunks.get(k))) return;
		chunks.put(k, slice);
		markDirty(cx, cz);
		tiles.invalidateChunk(cx, cz);
	}

	/** Marca o chunk como explorado. Retorna true se ele acabou de ser revelado. */
	public boolean explore(int cx, int cz) {
		ensureRegion(cx >> 5, cz >> 5);
		if (explored.add(key(cx, cz))) {
			markDirty(cx, cz);
			tiles.invalidateChunk(cx, cz);
			return true;
		}
		return false;
	}

	private void markDirty(int cx, int cz) {
		long rk = key(cx >> 5, cz >> 5);
		dirtyRegions.add(rk);
		regionsWithData.add(rk);
	}

	// ------------------------------------------------------------------ terrenos (Lands)

	public String claim(int cx, int cz) {
		return claims.get(key(cx, cz));
	}

	public void setClaim(int cx, int cz, String owner) {
		long k = key(cx, cz);
		if (Objects.equals(claims.get(k), owner)) return;
		if (owner == null) claims.remove(k);
		else claims.put(k, owner);
		claimsDirty = true;
		claimsVersion++;
	}

	public Long2ObjectOpenHashMap<String> claims() {
		return claims;
	}

	public int claimsVersion() {
		return claimsVersion;
	}

	// ------------------------------------------------------------------ arquivos

	public void saveAll() {
		for (long rk : dirtyRegions.toLongArray()) saveRegion(rk);
		if (claimsDirty) saveClaims();
	}

	public void close() {
		saveAll();
		tiles.clear();
	}

	private Path regionFile(int rx, int rz) {
		return dir.resolve("r." + rx + "." + rz + ".dat");
	}

	private void loadRegion(int rx, int rz) {
		Path file = regionFile(rx, rz);
		if (!Files.exists(file)) return;
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
			if (in.readInt() != MAGIC) return;
			int version = in.readInt();
			if (version != VERSION) return;
			int count = in.readInt();
			for (int i = 0; i < count; i++) {
				int cx = in.readInt();
				int cz = in.readInt();
				ChunkSlice s = new ChunkSlice();
				in.readFully(s.color);
				for (int j = 0; j < 256; j++) s.height[j] = in.readShort();
				in.readFully(s.depth);
				chunks.putIfAbsent(key(cx, cz), s);
			}
			int exploredCount = in.readInt();
			for (int i = 0; i < exploredCount; i++) {
				explored.add(key(in.readInt(), in.readInt()));
			}
		} catch (IOException e) {
			IsekaiUI.LOG.warn("Arquivo de mapa corrompido: {}", file, e);
		}
	}

	private void saveRegion(long rk) {
		int rx = ChunkPos.getX(rk);
		int rz = ChunkPos.getZ(rk);
		List<Long> chunkKeys = new ArrayList<>();
		List<Long> exploredKeys = new ArrayList<>();
		for (int dz = 0; dz < 32; dz++) {
			for (int dx = 0; dx < 32; dx++) {
				long k = key((rx << 5) + dx, (rz << 5) + dz);
				if (chunks.containsKey(k)) chunkKeys.add(k);
				if (explored.contains(k)) exploredKeys.add(k);
			}
		}
		Path file = regionFile(rx, rz);
		Path tmp = dir.resolve("r." + rx + "." + rz + ".tmp");
		try {
			Files.createDirectories(dir);
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
				out.writeInt(MAGIC);
				out.writeInt(VERSION);
				out.writeInt(chunkKeys.size());
				for (long k : chunkKeys) {
					ChunkSlice s = chunks.get(k);
					out.writeInt(ChunkPos.getX(k));
					out.writeInt(ChunkPos.getZ(k));
					out.write(s.color);
					for (int j = 0; j < 256; j++) out.writeShort(s.height[j]);
					out.write(s.depth);
				}
				out.writeInt(exploredKeys.size());
				for (long k : exploredKeys) {
					out.writeInt(ChunkPos.getX(k));
					out.writeInt(ChunkPos.getZ(k));
				}
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			dirtyRegions.remove(rk);
		} catch (IOException e) {
			IsekaiUI.LOG.warn("Nao consegui salvar o mapa {}", file, e);
		}
	}

	private void unloadRegion(long rk) {
		int rx = ChunkPos.getX(rk);
		int rz = ChunkPos.getZ(rk);
		for (int dz = 0; dz < 32; dz++) {
			for (int dx = 0; dx < 32; dx++) {
				long k = key((rx << 5) + dx, (rz << 5) + dz);
				chunks.remove(k);
				explored.remove(k);
			}
		}
	}

	private void loadClaims() {
		Path file = dir.resolve("claims.dat");
		if (!Files.exists(file)) return;
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
			if (in.readInt() != CLAIMS_MAGIC) return;
			int count = in.readInt();
			for (int i = 0; i < count; i++) {
				long k = in.readLong();
				claims.put(k, in.readUTF());
			}
			claimsVersion++;
		} catch (IOException e) {
			IsekaiUI.LOG.warn("Arquivo de terrenos corrompido: {}", file, e);
		}
	}

	private void saveClaims() {
		Path file = dir.resolve("claims.dat");
		Path tmp = dir.resolve("claims.tmp");
		try {
			Files.createDirectories(dir);
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
				out.writeInt(CLAIMS_MAGIC);
				out.writeInt(claims.size());
				for (Long2ObjectMap.Entry<String> e : claims.long2ObjectEntrySet()) {
					out.writeLong(e.getLongKey());
					out.writeUTF(e.getValue());
				}
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			claimsDirty = false;
		} catch (IOException e) {
			IsekaiUI.LOG.warn("Nao consegui salvar os terrenos {}", file, e);
		}
	}
}
