package com.compelium.isekaiui.tooltip;

import com.compelium.isekaiui.gui.Draw;
import com.compelium.isekaiui.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Previa de shulker box / sacola desenhada como uma mochila de couro (tingida com a cor do item):
 * alca, tampa com duas correias e fivelas, bolsos laterais e um bolso para cada item.
 */
public class ClientBackpackTooltip implements ClientTooltipComponent {
	private static final int SIDE = 5;
	private static final int FLAP_H = 18;
	private static final int BODY_TOP = 12;
	private final BackpackPreview data;
	private final int cols;
	private final int rows;
	private final int bodyW;
	private final int bodyH;
	private final int tint;

	public ClientBackpackTooltip(BackpackPreview data) {
		this.data = data;
		this.cols = data.columns();
		int pockets = Math.max(data.bundle() ? 4 : 0, data.items().size() + (data.extra() > 0 ? 1 : 0));
		this.rows = Math.max(1, (pockets + this.cols - 1) / this.cols);
		this.bodyW = this.cols * 18 + 12;
		int extraLines = data.bundle() ? 6 + (data.selectedStack().isEmpty() ? 0 : 11) : 0;
		this.bodyH = 10 + this.rows * 18 + 6 + extraLines;
		this.tint = leather(data.color());
	}

	/** Deixa a cor com cara de couro: um pouco menos saturada e nunca preta demais. */
	private static int leather(int rgb) {
		int r = (rgb >> 16) & 255;
		int g = (rgb >> 8) & 255;
		int b = rgb & 255;
		int gray = (r + g + b) / 3;
		r = (r * 85 + gray * 15) / 100;
		g = (g * 85 + gray * 15) / 100;
		b = (b * 85 + gray * 15) / 100;
		int min = 58;
		r = Math.max(min, r);
		g = Math.max(min, g);
		b = Math.max(min, b);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	@Override
	public int getHeight(Font font) {
		return BODY_TOP + this.bodyH + 3;
	}

	@Override
	public int getWidth(Font font) {
		return this.bodyW + SIDE * 2;
	}

	@Override
	public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
		int bx = x + SIDE;
		int by = y + BODY_TOP;
		// bolsos laterais (atras do corpo)
		int sideH = Math.min(30, this.bodyH - 14);
		Draw.sprite(g, Theme.BACKPACK_SIDE, bx - SIDE, by + 8, 8, sideH, this.tint);
		Draw.sprite(g, Theme.BACKPACK_SIDE, bx + this.bodyW - 3, by + 8, 8, sideH, this.tint);
		// alca, corpo e bolsos dos itens
		Draw.sprite(g, Theme.BACKPACK_HANDLE, bx + this.bodyW / 2 - 8, y, 16, 6, this.tint);
		Draw.sprite(g, Theme.BACKPACK_BODY, bx, by, this.bodyW, this.bodyH, this.tint);
		int px = bx + 6;
		int py = by + 10;
		int total = this.rows * this.cols;
		for (int i = 0; i < total; i++) {
			int sx = px + (i % this.cols) * 18;
			int sy = py + (i / this.cols) * 18;
			Draw.sprite(g, Theme.BACKPACK_POCKET, sx, sy, 18, 18, 1f);
			if (i == this.data.selected()) {
				Draw.fill(g, sx + 1, sy + 1, sx + 17, sy + 17, 0x60FFF2CF);
				Draw.fill(g, sx, sy, sx + 18, sy + 1, 0xFFF2C76A);
				Draw.fill(g, sx, sy + 17, sx + 18, sy + 18, 0xFFF2C76A);
				Draw.fill(g, sx, sy, sx + 1, sy + 18, 0xFFF2C76A);
				Draw.fill(g, sx + 17, sy, sx + 18, sy + 18, 0xFFF2C76A);
			}
			if (i < this.data.items().size()) {
				ItemStack s = this.data.items().get(i);
				if (!s.isEmpty()) {
					g.renderItem(s, sx + 1, sy + 1);
					g.renderItemDecorations(font, s, sx + 1, sy + 1);
				}
			} else if (i == this.data.items().size() && this.data.extra() > 0) {
				String more = "+" + this.data.extra();
				g.drawString(font, more, sx + 9 - font.width(more) / 2, sy + 5, 0xFFFFF4D6, true);
			}
		}
		if (this.data.bundle()) drawBundleInfo(font, g, bx, py + this.rows * 18);
		// tampa com correias e fivelas
		Draw.sprite(g, Theme.BACKPACK_FLAP, bx, y + 4, this.bodyW, FLAP_H, this.tint);
		for (int k = 1; k <= 3; k += 2) {
			int cx = bx + this.bodyW * k / 4;
			Draw.sprite(g, Theme.BACKPACK_STRAP, cx - 3, y + 5, 6, FLAP_H - 3, this.tint);
			Draw.sprite(g, Theme.BACKPACK_BUCKLE, cx - 5, y + 4 + FLAP_H - 6, 10, 8, 1f);
		}
	}

	private void drawBundleInfo(Font font, GuiGraphics g, int bx, int top) {
		int barX = bx + 6;
		int barW = this.bodyW - 12;
		int barY = top + 2;
		Draw.fill(g, barX, barY, barX + barW, barY + 3, 0xFF2A1F14);
		float f = Math.min(1f, this.data.fullness());
		if (f > 0f) Draw.fill(g, barX, barY, barX + Math.max(1, Math.round(barW * f)), barY + 3, f >= 1f ? 0xFFE0465E : 0xFFE6B04A);
		if (this.data.items().isEmpty()) {
			Component empty = Component.translatable("isekai_ui.backpack.empty");
			g.drawString(font, empty, bx + this.bodyW / 2 - font.width(empty) / 2, top - 13, 0xFFFFF4D6, true);
		}
		ItemStack sel = this.data.selectedStack();
		if (!sel.isEmpty()) {
			String name = font.plainSubstrByWidth(sel.getHoverName().getString(), this.bodyW - 8);
			g.drawString(font, name, bx + this.bodyW / 2 - font.width(name) / 2, barY + 6, 0xFFFFF4D6, true);
		}
	}
}
