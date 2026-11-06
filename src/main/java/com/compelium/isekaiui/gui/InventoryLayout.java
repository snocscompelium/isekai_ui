package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.config.IsekaiConfig;

/**
 * Posicoes das janelas do inventario. O personagem fica a esquerda (camera em 3a pessoa),
 * o equipamento na borda esquerda, a bolsa no meio e o minimapa + sintese a direita.
 */
public final class InventoryLayout {
	public static final int SLOT = 18;
	public static final int PAD = 8;
	public static final int TAB = 12;
	public static final int HEX_W = 24;
	public static final int HEX_H = 20;
	public static final int BUTTON_STEP = 26;
	public static final int MIN_WIDTH = 330;
	public static final int MIN_HEIGHT = 200;

	public final Rect equip;
	public final Rect main;
	public final Rect mini;
	public final Rect craft;
	public final Rect bar;
	/** Canto do primeiro item da bolsa (slot 9). */
	public final int storageX;
	public final int storageY;
	/** Canto do primeiro hexagono da barra de atalhos. */
	public final int hotbarX;
	public final int hotbarY;
	/** Canto do hexagono do capacete; os outros descem de 22 em 22. */
	public final int equipX;
	public final int equipY;
	public final int offhandY;
	public final int craftX;
	public final int craftY;
	public final int resultX;
	public final int resultY;

	private InventoryLayout(int W, int H, int buttons) {
		int mainW = 9 * SLOT + PAD * 2;
		int mainH = 8 + 3 * SLOT + 9 + 30 + 7;
		int equipW = 36;
		int equipH = 7 + 4 * 22 - 2 + 12 + HEX_H + 7;
		int miniS = 92;
		int craftH = 7 + 2 * SLOT + 7;
		int gapMain = 12;
		int groupW = mainW + gapMain + miniS;

		float charX = W * IsekaiConfig.get().characterScreenX;
		int mainX = Math.round(charX + 46);
		mainX = Math.min(mainX, W - 8 - groupW);
		mainX = Math.max(mainX, 52);

		int mainColumnH = TAB + mainH + 8 + 22;
		int mainY = (H - mainColumnH) / 2 + TAB;
		this.main = new Rect(mainX, mainY, mainW, mainH);

		int equipX0 = Math.max(6, Math.min(Math.round(W * 0.035f), mainX - equipW - 60));
		this.equip = new Rect(equipX0, (H - equipH) / 2 + 4, equipW, equipH);

		int rightX = main.right() + gapMain;
		int rightColumnH = TAB + 5 + miniS + 5 + 14 + 8 + TAB + craftH;
		int top = Math.max(TAB + 6, (H - rightColumnH) / 2 + TAB + 5);
		this.mini = new Rect(rightX, top, miniS, miniS);
		this.craft = new Rect(rightX, mini.bottom() + 5 + 14 + 8 + TAB, miniS, craftH);

		int barW = Math.max(1, buttons) * BUTTON_STEP - 2;
		this.bar = new Rect(main.cx() - barW / 2, main.bottom() + 8, barW, 22);

		this.storageX = main.x() + PAD + 1;
		this.storageY = main.y() + 8 + 1;
		int hotbarW = 8 * 18 + HEX_W;
		this.hotbarX = main.x() + (mainW - hotbarW) / 2;
		this.hotbarY = main.y() + 8 + 3 * SLOT + 9;

		this.equipX = equip.x() + (equipW - HEX_W) / 2;
		this.equipY = equip.y() + 7;
		this.offhandY = equipY + 4 * 22 - 2 + 12;

		this.craftX = craft.x() + 8 + 1;
		this.craftY = craft.y() + 7 + 1;
		this.resultX = craft.right() - 8 - 26 + 5;
		this.resultY = craft.y() + 7 + SLOT - 8;
	}

	public static InventoryLayout compute(int width, int height, int buttons) {
		return new InventoryLayout(width, height, buttons);
	}

	public static boolean fits(int width, int height) {
		return width >= MIN_WIDTH && height >= MIN_HEIGHT;
	}
}
