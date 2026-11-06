package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.Instruments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Medalhao do ceu no canto do minimapa: sem relogio mostra so o sol (que vira lua de noite);
 * com relogio no inventario vira um relogio com a hora certa.
 */
public final class SkyDial {
	public static final int SIZE = 22;
	private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);

	private SkyDial() {
	}

	public static void draw(GuiGraphics g, Font font, int cx, int cy, float alpha) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || alpha <= 0.02f) return;
		Draw.sprite(g, Theme.DIAL, cx - SIZE / 2, cy - SIZE / 2, SIZE, SIZE, alpha);
		boolean fixed = mc.level.dimensionType().hasFixedTime();
		long day = Math.floorMod(mc.level.getDayTime(), 24000L);
		if (Instruments.hasClock()) {
			if (alpha > 0.5f) g.renderItem(CLOCK, cx - 8, cy - 8);
			Component time = Component.literal(fixed ? "??:??" : clock(day));
			int w = font.width(time) + 8;
			// a plaquinha da hora nunca sai da tela (o minimapa pode estar encostado na borda)
			int px = Mth.clamp(cx - w / 2, 1, Math.max(1, g.guiWidth() - w - 1));
			Draw.sprite(g, Theme.PILL, px, cy + SIZE / 2 - 2, w, 11, alpha);
			Draw.textCentered(g, font, time, px + w / 2, cy + SIZE / 2, Draw.alpha(Theme.TEXT_GOLD, alpha), false);
			return;
		}
		if (fixed) {
			Draw.textCentered(g, font, Component.literal("?"), cx, cy - 4, Draw.alpha(Theme.TEXT_DIM, alpha), false);
			return;
		}
		boolean night = day >= 12800 && day < 23200;
		boolean dusk = (day >= 11800 && day < 12800) || day >= 23200 || day < 600;
		int tint = night ? 0xFFCFD8F0 : (dusk ? 0xFFE8873A : 0xFFF2C94C);
		Draw.sprite(g, Theme.icon(night ? "moon" : "sun"), cx - 6, cy - 6, 12, 12, Draw.alpha(tint, alpha));
	}

	/** Hora do jogo no formato 24h (o dia do Minecraft comeca as 6:00). */
	public static String clock(long dayTime) {
		int hours = (int) ((dayTime / 1000 + 6) % 24);
		int minutes = (int) (dayTime % 1000 * 60 / 1000);
		return String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes);
	}
}
