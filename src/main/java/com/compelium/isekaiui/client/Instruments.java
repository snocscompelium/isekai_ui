package com.compelium.isekaiui.client;

import com.compelium.isekaiui.config.IsekaiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Relogio e bussola funcionais: ter um relogio (no inventario ou dentro de shulker/sacola) mostra a hora certa;
 * ter uma bussola libera as coordenadas e o minimapa na tela; segurar a bussola mostra os marcadores no horizonte.
 */
public final class Instruments {
	private static boolean clock;
	private static boolean compass;
	private static boolean holdingCompass;

	private Instruments() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			clock = compass = holdingCompass = false;
			return;
		}
		int found = 0;
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && found != 3; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty()) found |= scan(s, 0);
		}
		ItemStack carried = p.containerMenu.getCarried();
		if (!carried.isEmpty()) found |= scan(carried, 0);
		clock = (found & 1) != 0;
		compass = (found & 2) != 0;
		holdingCompass = isCompass(p.getMainHandItem()) || isCompass(p.getOffhandItem());
	}

	/** 1 = relogio, 2 = bussola (olha dentro de shulker boxes e sacolas tambem). */
	private static int scan(ItemStack s, int depth) {
		int f = 0;
		if (s.is(Items.CLOCK)) f |= 1;
		if (isCompass(s)) f |= 2;
		if (depth < 2 && f != 3) {
			ItemContainerContents contents = s.get(DataComponents.CONTAINER);
			if (contents != null) {
				for (ItemStack in : contents.nonEmptyItems()) {
					f |= scan(in, depth + 1);
					if (f == 3) return f;
				}
			}
			BundleContents bundle = s.get(DataComponents.BUNDLE_CONTENTS);
			if (bundle != null) {
				for (ItemStack in : bundle.items()) {
					f |= scan(in, depth + 1);
					if (f == 3) return f;
				}
			}
		}
		return f;
	}

	private static boolean isCompass(ItemStack s) {
		return s.is(Items.COMPASS) || s.is(Items.RECOVERY_COMPASS);
	}

	public static boolean hasClock() {
		return clock || !IsekaiConfig.get().instruments.requireClock;
	}

	public static boolean hasCompass() {
		return compass || !IsekaiConfig.get().instruments.requireCompass;
	}

	/** Coordenadas nos mapas: so com bussola. */
	public static boolean showCoordinates() {
		return hasCompass();
	}

	public static boolean holdingCompass() {
		return holdingCompass;
	}
}
