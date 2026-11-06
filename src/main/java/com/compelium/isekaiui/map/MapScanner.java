package com.compelium.isekaiui.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Le a superficie de um chunk carregado no cliente (mesma logica dos mapas vanilla). */
public final class MapScanner {
	private MapScanner() {
	}

	public static ChunkSlice scan(Level level, LevelChunk chunk, int playerY) {
		ChunkSlice slice = new ChunkSlice();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		boolean ceiling = level.dimensionType().hasCeiling();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();

		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int x = baseX + lx;
				int z = baseZ + lz;
				int y;
				if (ceiling) {
					y = findFloorUnderCeiling(chunk, pos, x, z, Math.min(maxY, playerY + 2), minY);
				} else {
					y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
				}
				int i = ChunkSlice.index(lx, lz);
				if (y <= minY) {
					slice.color[i] = 0;
					slice.height[i] = (short) minY;
					continue;
				}
				pos.set(x, y, z);
				BlockState state = chunk.getBlockState(pos);
				MapColor mapColor = state.getMapColor(level, pos);
				while (mapColor == MapColor.NONE && y > minY) {
					y--;
					pos.setY(y);
					state = chunk.getBlockState(pos);
					mapColor = state.getMapColor(level, pos);
				}
				int depth = 0;
				if (!state.getFluidState().isEmpty()) {
					int yy = y - 1;
					while (yy > minY && depth < 120) {
						pos.setY(yy);
						if (chunk.getBlockState(pos).getFluidState().isEmpty()) break;
						depth++;
						yy--;
					}
				}
				slice.color[i] = (byte) mapColor.id;
				slice.height[i] = (short) y;
				slice.depth[i] = (byte) Math.min(127, depth + (state.getFluidState().isEmpty() ? 0 : 1));
			}
		}
		return slice;
	}

	/** No Nether: desce a partir da altura do jogador ate achar chao abaixo de ar. */
	private static int findFloorUnderCeiling(LevelChunk chunk, BlockPos.MutableBlockPos pos, int x, int z, int startY, int minY) {
		int y = startY;
		pos.set(x, y, z);
		// sai de dentro de blocos solidos
		while (y > minY && !chunk.getBlockState(pos).isAir()) {
			y--;
			pos.setY(y);
		}
		// desce pelo ar
		while (y > minY && chunk.getBlockState(pos).isAir()) {
			y--;
			pos.setY(y);
		}
		return y;
	}
}
