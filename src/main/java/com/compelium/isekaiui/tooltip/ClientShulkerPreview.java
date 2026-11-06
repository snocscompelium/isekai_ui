package com.compelium.isekaiui.tooltip;

import com.compelium.isekaiui.gui.Draw;
import com.compelium.isekaiui.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Desenha a grade 9x3 com os itens da shulker dentro do tooltip. */
public final class ClientShulkerPreview implements ClientTooltipComponent {
	private static final int CELL = 18;
	private final ShulkerPreview data;

	public ClientShulkerPreview(ShulkerPreview data) {
		this.data = data;
	}

	@Override
	public int getHeight(Font font) {
		return 4 + 3 * CELL + 3;
	}

	@Override
	public int getWidth(Font font) {
		return 9 * CELL + 2;
	}

	@Override
	public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
		int color = 0xFF000000 | this.data.color();
		Draw.fill(g, x + 1, y + 1, x + 9 * CELL + 1, y + 3, color);
		Draw.fill(g, x + 1, y + 3, x + 9 * CELL + 1, y + 4, Draw.alpha(0xFF000000, 0.35f));
		for (int i = 0; i < 27 && i < this.data.items().size(); i++) {
			int sx = x + 1 + (i % 9) * CELL;
			int sy = y + 5 + (i / 9) * CELL;
			Draw.sprite(g, Theme.SLOT, sx, sy, CELL, CELL, 1f);
			ItemStack stack = this.data.items().get(i);
			if (!stack.isEmpty()) {
				g.renderItem(stack, sx + 1, sy + 1);
				g.renderItemDecorations(font, stack, sx + 1, sy + 1);
			}
		}
	}
}
