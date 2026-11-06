package com.compelium.isekaiui.map;

/** Centro (em blocos) e zoom (pixels de interface por bloco) de um mapa na tela. */
public final class MapView {
	public double cx;
	public double cz;
	public float zoom = 1f;

	public MapView() {
	}

	public MapView(double cx, double cz, float zoom) {
		this.cx = cx;
		this.cz = cz;
		this.zoom = zoom;
	}
}
