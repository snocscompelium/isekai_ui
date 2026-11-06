package com.compelium.isekaiui.camera;

/** Marca no "render state" de uma entidade dizendo se e o jogador local mexendo na janela. */
public interface PoseFlag {
	boolean isekai$isLocalBrowsing();

	void isekai$setLocalBrowsing(boolean value);
}
