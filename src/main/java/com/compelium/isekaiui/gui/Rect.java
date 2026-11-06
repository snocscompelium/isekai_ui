package com.compelium.isekaiui.gui;

public record Rect(int x, int y, int w, int h) {
	public int right() {
		return x + w;
	}

	public int bottom() {
		return y + h;
	}

	public int cx() {
		return x + w / 2;
	}

	public int cy() {
		return y + h / 2;
	}

	public boolean contains(double px, double py) {
		return px >= x && py >= y && px < x + w && py < y + h;
	}

	public Rect offset(int dx, int dy) {
		return (dx == 0 && dy == 0) ? this : new Rect(x + dx, y + dy, w, h);
	}

	public Rect grow(int amount) {
		return new Rect(x - amount, y - amount, w + amount * 2, h + amount * 2);
	}

	public static Rect lerp(Rect a, Rect b, float t) {
		return new Rect(
				Math.round(a.x + (b.x - a.x) * t),
				Math.round(a.y + (b.y - a.y) * t),
				Math.round(a.w + (b.w - a.w) * t),
				Math.round(a.h + (b.h - a.h) * t));
	}
}
