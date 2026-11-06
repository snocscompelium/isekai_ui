package com.compelium.isekaiui.camera;

import com.compelium.isekaiui.config.IsekaiConfig;

/** Estado da pose "mexendo na janela" do personagem local. */
public final class PoseState {
	private static final float TAP_SECONDS = 0.32f;
	private static long tapNanos;

	private PoseState() {
	}

	/** 0 = pose normal, 1 = bracos totalmente levantados. Acompanha a camera. */
	public static float weight() {
		return IsekaiConfig.get().poseCharacter ? CinematicCamera.eased() : 0f;
	}

	/** Pequeno "toque" do braco quando o jogador clica num slot. */
	public static void tap() {
		tapNanos = System.nanoTime();
	}

	public static float tapAmount() {
		float t = (System.nanoTime() - tapNanos) / 1.0e9f;
		if (tapNanos == 0L || t < 0f || t > TAP_SECONDS) return 0f;
		return (float) Math.sin(t / TAP_SECONDS * Math.PI);
	}
}
