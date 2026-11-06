package com.compelium.isekaiui.map;

import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.lands.LandsTracker;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.nio.file.Path;
import java.util.Locale;

/** Liga o mapa aos eventos do jogo: escaneia chunks, revela areas exploradas e salva. */
public final class MapManager {
	private static DimMap current;
	private static String currentKey;
	private static final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
	private static final LongOpenHashSet queued = new LongOpenHashSet();
	private static int ticks;

	private MapManager() {
	}

	public static void init() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> onChunkLoad(chunk));
		ClientTickEvents.END_CLIENT_TICK.register(MapManager::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> closeCurrent());
	}

	public static DimMap current() {
		return current;
	}

	private static void onChunkLoad(LevelChunk chunk) {
		if (current == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		ChunkPos pos = chunk.getPos();
		int r = IsekaiConfig.get().map.exploreRadiusChunks + 1;
		int dx = pos.x - mc.player.chunkPosition().x;
		int dz = pos.z - mc.player.chunkPosition().z;
		boolean near = dx * dx + dz * dz <= r * r;
		if (near || current.isExplored(pos.x, pos.z)) enqueue(pos.x, pos.z);
	}

	private static void enqueue(int cx, int cz) {
		long k = ChunkPos.asLong(cx, cz);
		if (queued.add(k)) queue.enqueue(k);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			closeCurrent();
			return;
		}
		String server = serverKey(mc);
		String key = server + "/" + sanitize(level.dimension().location().toString());
		if (!key.equals(currentKey)) {
			closeCurrent();
			current = new DimMap(baseDir().resolve(key));
			currentKey = key;
			LandsTracker.reset();
			Emblems.load(serverDir(server));
			Kingdoms.load(serverDir(server));
		}
		ticks++;
		int pcx = mc.player.chunkPosition().x;
		int pcz = mc.player.chunkPosition().z;
		current.ensureRegion(pcx >> 5, pcz >> 5);

		if (ticks % 5 == 0) exploreAround(pcx, pcz);
		if (ticks % 40 == 0) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dx = -2; dx <= 2; dx++) enqueue(pcx + dx, pcz + dz);
			}
		}
		int budget = 6;
		while (budget-- > 0 && !queue.isEmpty()) {
			long k = queue.dequeueLong();
			queued.remove(k);
			scan(mc, level, ChunkPos.getX(k), ChunkPos.getZ(k));
		}
		if (ticks % 2400 == 0) current.saveAll();
		LandsTracker.tick(mc, current);
	}

	private static void exploreAround(int pcx, int pcz) {
		int r = IsekaiConfig.get().map.exploreRadiusChunks;
		int r2 = r * r + r;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				if (dx * dx + dz * dz > r2) continue;
				if (current.explore(pcx + dx, pcz + dz)) enqueue(pcx + dx, pcz + dz);
			}
		}
	}

	private static void scan(Minecraft mc, ClientLevel level, int cx, int cz) {
		LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
		if (chunk == null || chunk.isEmpty()) return;
		current.putScan(cx, cz, MapScanner.scan(level, chunk, mc.player == null ? 64 : mc.player.getBlockY()));
	}

	private static void closeCurrent() {
		if (current != null) {
			current.close();
			current = null;
			currentKey = null;
		}
		queue.clear();
		queued.clear();
	}

	private static Path baseDir() {
		return FabricLoader.getInstance().getGameDir().resolve("isekai_ui").resolve("maps");
	}

	/** Marcadores de uma dimensao deste servidor (a atual ou outra, ex.: marcador do Nether recebido no chat). */
	public static Waypoints waypointsFor(String dimensionId) {
		String key = serverKey(Minecraft.getInstance()) + "/" + sanitize(dimensionId);
		if (current != null && key.equals(currentKey)) return current.waypoints();
		return new Waypoints(baseDir().resolve(key));
	}

	/** Dimensao atual ("minecraft:overworld"...), ou null fora de um mundo. */
	public static String currentDimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? null : mc.level.dimension().location().toString();
	}

	/** Pasta com os dados de um servidor (mapas por dimensao, comandos, simbolos de reino). */
	public static Path serverDir(String serverKey) {
		return baseDir().resolve(serverKey);
	}

	public static String serverKey(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		if (server != null) return "mp_" + sanitize(server.ip);
		if (mc.getSingleplayerServer() != null) {
			return "sp_" + sanitize(mc.getSingleplayerServer().getWorldData().getLevelName());
		}
		return "desconhecido";
	}

	private static String sanitize(String s) {
		String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		return out.isEmpty() ? "_" : out;
	}
}
