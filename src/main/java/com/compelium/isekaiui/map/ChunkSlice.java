package com.compelium.isekaiui.map;

import java.util.Arrays;

/** Dados de superficie de um chunk (16x16 colunas). */
public final class ChunkSlice {
	/** Id da MapColor do bloco do topo (0 = nada). */
	public final byte[] color = new byte[256];
	/** Altura (Y) do bloco do topo. */
	public final short[] height = new short[256];
	/** Profundidade da agua (0 = seco). */
	public final byte[] depth = new byte[256];

	public static int index(int lx, int lz) {
		return (lz << 4) | lx;
	}

	public boolean sameAs(ChunkSlice other) {
		return other != null
				&& Arrays.equals(color, other.color)
				&& Arrays.equals(height, other.height)
				&& Arrays.equals(depth, other.depth);
	}
}
