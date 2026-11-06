package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.anim.Ease;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/** Pecas visuais reutilizaveis: janela, aba de titulo e botao hexagonal. */
public final class Panels {
	private Panels() {
	}

	/** Janela de vidro escuro com moldura marfim/dourada, aba de titulo e efeitos de abertura. */
	public static void panel(GuiGraphics g, Font font, Rect r, float alpha, float progress, Component title, float time) {
		if (alpha <= 0.01f) return;
		Draw.fill(g, r.x() + 2, r.y() + 3, r.right() + 3, r.bottom() + 4, Draw.alpha(Theme.SHADOW, alpha * 0.8f));
		Draw.sprite(g, Theme.PANEL, r.x(), r.y(), r.w(), r.h(), alpha);
		// brilho suave de "vidro" na metade de cima
		Draw.gradient(g, r.x() + 5, r.y() + 5, r.right() - 5, r.y() + Math.max(8, r.h() / 2),
				Draw.alpha(0x1CFFF4DC, alpha), 0x00FFF4DC);
		if (title != null) {
			Component t = upper(title);
			int w = font.width(t) + 18;
			int x = w <= r.w() - 12 ? r.x() + 8 : r.cx() - w / 2;
			x = Mth.clamp(x, 2, Math.max(2, g.guiWidth() - w - 2));
			tab(g, font, x, r.y() - InventoryLayout.TAB + 1, w, t, alpha);
		}
		// linha de "materializacao" descendo pela janela enquanto ela abre
		if (progress < 1f) {
			float k = 1f - progress;
			int y = r.y() + 3 + Math.round((r.h() - 6) * Ease.outCubic(progress));
			Draw.gradient(g, r.x() + 3, Math.max(r.y() + 3, y - 10), r.right() - 3, y, 0x00FFFFFF, Draw.alpha(Theme.GLOW, 0.30f * k * alpha));
			Draw.fill(g, r.x() + 3, y, r.right() - 3, y + 1, Draw.alpha(Theme.GLOW, 0.95f * k * alpha));
		}
		glint(g, r, alpha, time);
	}

	/** Aba marfim com texto escuro (como a aba com o nome da regiao no mapa de referencia). */
	public static void tab(GuiGraphics g, Font font, int x, int y, int w, Component title, float alpha) {
		if (alpha <= 0.01f) return;
		Draw.sprite(g, Theme.TAB, x, y, w, InventoryLayout.TAB + 1, alpha);
		Component t = title;
		int maxText = w - 14;
		if (font.width(t) > maxText) {
			t = Component.literal(font.plainSubstrByWidth(t.getString(), Math.max(0, maxText - font.width("..."))) + "...");
		}
		Draw.textCentered(g, font, t, x + w / 2, y + 3, Draw.alpha(Theme.TEXT_INK, alpha), false);
	}

	public static void hexButton(GuiGraphics g, Rect r, String icon, boolean hover, boolean on, float alpha) {
		if (alpha <= 0.01f) return;
		Draw.sprite(g, hover ? Theme.HEX_BUTTON_HOVER : (on ? Theme.HEX_BUTTON_ON : Theme.HEX_BUTTON), r.x(), r.y(), r.w(), r.h(), alpha);
		int tint = hover ? Theme.TEXT_INK : (on ? 0xFFFFF1CC : Theme.IVORY);
		Draw.sprite(g, Theme.icon(icon), r.x() + (r.w() - 12) / 2, r.y() + (r.h() - 12) / 2, 12, 12, Draw.alpha(tint, alpha));
	}

	/** Linha dourada com um losango no meio (separador decorativo, 5 px de altura). */
	public static void ornament(GuiGraphics g, int x, int y, int w, float alpha) {
		if (alpha <= 0.01f || w < 12) return;
		int cx = x + w / 2;
		int line = y + 2;
		int quarter = Math.max(1, w / 4);
		Draw.fill(g, x, line, x + quarter, line + 1, Draw.alpha(Theme.GOLD, alpha * 0.25f));
		Draw.fill(g, x + w - quarter, line, x + w, line + 1, Draw.alpha(Theme.GOLD, alpha * 0.25f));
		Draw.fill(g, x + quarter, line, x + w - quarter, line + 1, Draw.alpha(Theme.GOLD, alpha * 0.6f));
		int[] widths = {1, 3, 5, 3, 1};
		for (int i = 0; i < widths.length; i++) {
			int hw = widths[i] / 2;
			Draw.fill(g, cx - hw, y + i, cx + hw + 1, y + i + 1, Draw.alpha(0xFFECCE88, alpha));
		}
		Draw.fill(g, cx, y + 2, cx + 1, y + 3, Draw.alpha(Theme.GLOW, alpha));
	}

	/** Brilho que passa de vez em quando pela borda de cima da janela. */
	private static void glint(GuiGraphics g, Rect r, float alpha, float time) {
		float period = 6.5f;
		float phase = (time + (r.x() * 0.017f) + (r.y() * 0.011f)) % period;
		float t = phase / 0.95f;
		if (t >= 1f || time < 0.6f) return;
		int span = r.w() + 40;
		int gx = r.x() - 32 + Math.round(span * t);
		float a = alpha * Mth.sin((float) Math.PI * t) * 0.85f;
		Draw.scissor(g, new Rect(r.x() + 2, r.y(), r.w() - 4, 4));
		Draw.texture(g, Theme.GLINT, gx, r.y() + 1, 32, 3, 0f, 0f, 32, 3, 32, 3, Draw.white(a));
		Draw.endScissor(g);
	}

	public static Component upper(Component c) {
		return Component.literal(c.getString().toUpperCase(Locale.ROOT));
	}
}
