package com.compelium.isekaiui.map;

import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.gui.Draw;
import com.compelium.isekaiui.gui.Rect;
import com.compelium.isekaiui.gui.Theme;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/** Desenha o mapa de pergaminho dentro de um retangulo da tela. */
public final class MapRenderer {
	/** Ligado pela tela do mapa enquanto o jogador marca territorios: grade e reinos sempre visiveis. */
	public static boolean territoryEditing;

	private MapRenderer() {
	}

	public static void draw(GuiGraphics g, DimMap map, MapView view, Rect r, float alpha, float partialTick, boolean mini) {
		Minecraft mc = Minecraft.getInstance();
		IsekaiConfig.MapSettings settings = IsekaiConfig.get().map;
		if (r.w() <= 2 || r.h() <= 2 || alpha <= 0.01f) return;
		Draw.fill(g, r.x(), r.y(), r.right(), r.bottom(), Draw.rgb(MapStyle.PARCH, alpha));
		if (map == null || mc.level == null || mc.player == null) return;

		Draw.scissor(g, r);
		float z = view.zoom;
		int lod = lodFor(z, mc.getWindow().getGuiScale());
		int tileBlocks = TileCache.SIZE << lod;
		double centerX = r.x() + r.w() / 2.0;
		double centerY = r.y() + r.h() / 2.0;
		double minX = view.cx - (r.w() / 2.0) / z;
		double maxX = view.cx + (r.w() / 2.0) / z;
		double minZ = view.cz - (r.h() / 2.0) / z;
		double maxZ = view.cz + (r.h() / 2.0) / z;
		int tx0 = Math.floorDiv((int) Math.floor(minX), tileBlocks);
		int tx1 = Math.floorDiv((int) Math.floor(maxX), tileBlocks);
		int tz0 = Math.floorDiv((int) Math.floor(minZ), tileBlocks);
		int tz1 = Math.floorDiv((int) Math.floor(maxZ), tileBlocks);

		TileCache tiles = map.tiles();
		tiles.beginFrame();
		ResourceLocation fog = TileCache.fogTexture();
		int tint = Draw.white(alpha);
		float scale = (float) (tileBlocks * (double) z / TileCache.SIZE);
		for (int tz = tz0; tz <= tz1; tz++) {
			for (int tx = tx0; tx <= tx1; tx++) {
				ResourceLocation tex = tiles.request(lod, tx, tz);
				if (tex == null) tex = fog;
				double sx = centerX + (tx * (double) tileBlocks - view.cx) * z;
				double sy = centerY + (tz * (double) tileBlocks - view.cz) * z;
				Draw.push(g);
				Draw.translate(g, sx, sy);
				Draw.scale(g, scale);
				Draw.texture(g, tex, 0, 0, TileCache.SIZE, TileCache.SIZE, 0f, 0f, TileCache.SIZE, TileCache.SIZE, TileCache.SIZE, TileCache.SIZE, tint);
				Draw.pop(g);
			}
		}
		tiles.buildPending(mini ? 3_000_000L : 7_000_000L);

		if ((settings.showGrid || territoryEditing) && !mini) drawGrid(g, view, r, alpha);
		if (settings.showClaims || territoryEditing) drawClaims(g, map, view, r, alpha, !mini, mc.font);
		if (settings.showEntities) drawEntities(g, view, r, alpha, partialTick);
		if (settings.showWaypoints) drawWaypoints(g, map, view, r, alpha, mini, mc.font);
		drawPlayer(g, view, r, alpha, partialTick);
		Draw.endScissor(g);
	}

	public static int lodFor(float zoom, double guiScale) {
		double screenPxPerBlock = zoom * guiScale;
		if (screenPxPerBlock >= 0.95) return 0;
		int lod = (int) Math.floor(Math.log(1.0 / screenPxPerBlock) / Math.log(2.0) + 0.15);
		return Mth.clamp(lod, 0, TileCache.MAX_LOD);
	}

	public static int toScreenX(MapView v, Rect r, double worldX) {
		return (int) Math.round(r.x() + r.w() / 2.0 + (worldX - v.cx) * v.zoom);
	}

	public static int toScreenY(MapView v, Rect r, double worldZ) {
		return (int) Math.round(r.y() + r.h() / 2.0 + (worldZ - v.cz) * v.zoom);
	}

	public static double toWorldX(MapView v, Rect r, double screenX) {
		return v.cx + (screenX - (r.x() + r.w() / 2.0)) / v.zoom;
	}

	public static double toWorldZ(MapView v, Rect r, double screenY) {
		return v.cz + (screenY - (r.y() + r.h() / 2.0)) / v.zoom;
	}

	private static void drawGrid(GuiGraphics g, MapView v, Rect r, float alpha) {
		int spacing = 16;
		while (spacing * v.zoom < 28f && spacing < 8192) spacing *= 2;
		int minor = Draw.rgb(MapStyle.INK, alpha * 0.14f);
		int major = Draw.rgb(MapStyle.INK, alpha * 0.26f);
		double minX = toWorldX(v, r, r.x());
		double minZ = toWorldZ(v, r, r.y());
		long startX = (long) Math.floor(minX / spacing) * spacing;
		long startZ = (long) Math.floor(minZ / spacing) * spacing;
		for (long wx = startX; ; wx += spacing) {
			int sx = toScreenX(v, r, wx);
			if (sx > r.right()) break;
			if (sx >= r.x()) Draw.fill(g, sx, r.y(), sx + 1, r.bottom(), Math.floorMod(wx, spacing * 4L) == 0 ? major : minor);
		}
		for (long wz = startZ; ; wz += spacing) {
			int sy = toScreenY(v, r, wz);
			if (sy > r.bottom()) break;
			if (sy >= r.y()) Draw.fill(g, r.x(), sy, r.right(), sy + 1, Math.floorMod(wz, spacing * 4L) == 0 ? major : minor);
		}
	}

	private static void drawClaims(GuiGraphics g, DimMap map, MapView v, Rect r, float alpha, boolean labels, Font font) {
		if (map.claims().isEmpty()) return;
		int cxMin = Math.floorDiv((int) Math.floor(toWorldX(v, r, r.x())), 16);
		int cxMax = Math.floorDiv((int) Math.floor(toWorldX(v, r, r.right())), 16);
		int czMin = Math.floorDiv((int) Math.floor(toWorldZ(v, r, r.y())), 16);
		int czMax = Math.floorDiv((int) Math.floor(toWorldZ(v, r, r.bottom())), 16);
		Map<String, double[]> centers = new HashMap<>();

		long area = (long) (cxMax - cxMin + 1) * (czMax - czMin + 1);
		if (area <= 40_000L) {
			for (int cz = czMin; cz <= czMax; cz++) {
				for (int cx = cxMin; cx <= cxMax; cx++) {
					String owner = map.claim(cx, cz);
					if (owner != null) drawClaimChunk(g, map, v, r, cx, cz, owner, alpha, centers);
				}
			}
		} else {
			for (Long2ObjectMap.Entry<String> e : map.claims().long2ObjectEntrySet()) {
				int cx = ChunkPos.getX(e.getLongKey());
				int cz = ChunkPos.getZ(e.getLongKey());
				if (cx < cxMin || cx > cxMax || cz < czMin || cz > czMax) continue;
				drawClaimChunk(g, map, v, r, cx, cz, e.getValue(), alpha, centers);
			}
		}

		for (Map.Entry<String, double[]> e : centers.entrySet()) {
			double[] c = e.getValue();
			double areaPx = c[2] * 256.0 * v.zoom * v.zoom;
			int sx = toScreenX(v, r, c[0] / c[2]);
			int sy = toScreenY(v, r, c[1] / c[2]);
			MapIcons.Icon emblem = MapIcons.find(Emblems.get(e.getKey()));
			boolean label = labels && areaPx >= 700;
			if (emblem != null && areaPx >= 90) {
				int size = labels ? (areaPx >= 2500 ? 22 : 16) : 11;
				int ey = label ? sy - 7 - size / 2 : sy;
				emblem.draw(g, sx, ey, size, 0xFFFFFF, alpha);
			}
			if (!label) continue;
			Component name = Component.literal(e.getKey());
			int w = font.width(name);
			int col = Theme.landColor(e.getKey());
			Draw.fill(g, sx - w / 2 - 3, sy - 6, sx + w / 2 + 3, sy + 6, Draw.rgb(MapStyle.shade(col, 0.45f), alpha * 0.55f));
			Draw.textCentered(g, font, name, sx, sy - 4, Draw.alpha(Theme.TEXT, alpha), true);
		}
	}

	/** Marcadores do jogador: icone pequeno com o nome escrito a "tinta" embaixo; fora da area vira um losango na borda. */
	private static void drawWaypoints(GuiGraphics g, DimMap map, MapView v, Rect r, float alpha, boolean mini, Font font) {
		java.util.List<Waypoints.Waypoint> list = map.waypoints().all();
		if (list.isEmpty()) return;
		Rect inner = r.grow(-4);
		for (Waypoints.Waypoint w : list) {
			int sx = toScreenX(v, r, w.x + 0.5);
			int sy = toScreenY(v, r, w.z + 0.5);
			if (!inner.contains(sx, sy)) {
				int ex = Mth.clamp(sx, inner.x() + 2, inner.right() - 3);
				int ey = Mth.clamp(sy, inner.y() + 2, inner.bottom() - 3);
				diamond(g, ex, ey, Draw.rgb(w.color, alpha * 0.9f), Draw.rgb(MapStyle.INK, alpha * 0.7f));
				continue;
			}
			MapIcons.Icon icon = MapIcons.get(w.icon);
			icon.draw(g, sx, sy, mini ? 9 : 12, w.color, alpha);
			if (mini || w.name == null || w.name.isBlank() || v.zoom < 0.2f) continue;
			Component name = Component.literal(w.name);
			Draw.push(g);
			Draw.translate(g, sx, sy + 7);
			Draw.scale(g, 0.75f);
			Draw.textCentered(g, font, name, 0, 0, Draw.alpha(Theme.MAP_INK, alpha), false);
			Draw.pop(g);
		}
	}

	private static void diamond(GuiGraphics g, int cx, int cy, int color, int outline) {
		Draw.fill(g, cx - 1, cy - 3, cx + 2, cy + 4, outline);
		Draw.fill(g, cx - 3, cy - 1, cx + 4, cy + 2, outline);
		Draw.fill(g, cx - 2, cy - 2, cx + 3, cy + 3, outline);
		Draw.fill(g, cx, cy - 2, cx + 1, cy + 3, color);
		Draw.fill(g, cx - 2, cy, cx + 3, cy + 1, color);
		Draw.fill(g, cx - 1, cy - 1, cx + 2, cy + 2, color);
	}

	private static void drawClaimChunk(GuiGraphics g, DimMap map, MapView v, Rect r, int cx, int cz, String owner,
									   float alpha, Map<String, double[]> centers) {
		int col = Theme.landColor(owner);
		int x0 = toScreenX(v, r, cx * 16.0);
		int x1 = toScreenX(v, r, cx * 16.0 + 16.0);
		int y0 = toScreenY(v, r, cz * 16.0);
		int y1 = toScreenY(v, r, cz * 16.0 + 16.0);
		Draw.fill(g, x0, y0, x1, y1, Draw.rgb(col, alpha * 0.28f));
		int border = Draw.rgb(MapStyle.shade(col, 0.72f), alpha * 0.9f);
		if (!owner.equals(map.claim(cx, cz - 1))) Draw.fill(g, x0, y0, x1, y0 + 1, border);
		if (!owner.equals(map.claim(cx, cz + 1))) Draw.fill(g, x0, y1 - 1, x1, y1, border);
		if (!owner.equals(map.claim(cx - 1, cz))) Draw.fill(g, x0, y0, x0 + 1, y1, border);
		if (!owner.equals(map.claim(cx + 1, cz))) Draw.fill(g, x1 - 1, y0, x1, y1, border);
		double[] c = centers.computeIfAbsent(owner, k -> new double[3]);
		c[0] += cx * 16.0 + 8.0;
		c[1] += cz * 16.0 + 8.0;
		c[2] += 1.0;
	}

	private static void drawEntities(GuiGraphics g, MapView v, Rect r, float alpha, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		int tint = Draw.white(alpha);
		for (Entity e : mc.level.entitiesForRendering()) {
			// jogadores nunca aparecem no mapa (regra do servidor)
			if (!(e instanceof LivingEntity) || e instanceof Player || e instanceof ArmorStand || e.isInvisible()) continue;
			double x = Mth.lerp(partialTick, e.xo, e.getX());
			double zz = Mth.lerp(partialTick, e.zo, e.getZ());
			int sx = toScreenX(v, r, x);
			int sy = toScreenY(v, r, zz);
			if (!r.contains(sx, sy)) continue;
			int size = e instanceof Enemy ? 6 : 5;
			Draw.texture(g, Theme.MAP_DOT, sx - size / 2, sy - size / 2, size, size, 0f, 0f, 6, 6, 6, 6, tint);
		}
	}

	private static void drawPlayer(GuiGraphics g, MapView v, Rect r, float alpha, float partialTick) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		double x = Mth.lerp(partialTick, p.xo, p.getX());
		double zz = Mth.lerp(partialTick, p.zo, p.getZ());
		double sx = r.x() + r.w() / 2.0 + (x - v.cx) * v.zoom;
		double sy = r.y() + r.h() / 2.0 + (zz - v.cz) * v.zoom;
		if (!r.grow(6).contains(sx, sy)) return;
		Draw.push(g);
		Draw.translate(g, sx, sy);
		Draw.rotate(g, p.getViewYRot(partialTick) + 180f);
		Draw.texture(g, Theme.MAP_ARROW, -6, -6, 12, 12, 0f, 0f, 12, 12, 12, 12, Draw.white(alpha));
		Draw.pop(g);
	}
}
