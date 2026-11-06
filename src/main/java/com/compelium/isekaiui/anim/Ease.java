package com.compelium.isekaiui.anim;

/** Curvas de animacao. Todas recebem t em [0,1]. */
public final class Ease {
	private Ease() {
	}

	public static float clamp01(float t) {
		return t < 0f ? 0f : (t > 1f ? 1f : t);
	}

	public static float outCubic(float t) {
		t = clamp01(t);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	public static float inCubic(float t) {
		t = clamp01(t);
		return t * t * t;
	}

	public static float inOutCubic(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	public static float outBack(float t) {
		t = clamp01(t);
		float c1 = 1.45f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	public static float smooth(float t) {
		t = clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** Progresso de uma janela de tempo [start, start+duration]. */
	public static float window(float time, float start, float duration) {
		if (duration <= 0f) return time >= start ? 1f : 0f;
		return clamp01((time - start) / duration);
	}

	public static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}
}
