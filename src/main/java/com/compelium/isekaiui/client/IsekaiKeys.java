package com.compelium.isekaiui.client;

import com.compelium.isekaiui.gui.IsekaiMapScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class IsekaiKeys {
	private static KeyMapping openMap;

	private IsekaiKeys() {
	}

	public static void register() {
		openMap = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.isekai_ui.map",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				"key.categories.isekai_ui"));
	}

	public static void tick(Minecraft mc) {
		if (openMap == null) return;
		while (openMap.consumeClick()) {
			if (mc.screen == null && mc.player != null && mc.level != null) {
				mc.setScreen(IsekaiMapScreen.fromHotkey());
			}
		}
	}

	public static boolean isMapKey(int keyCode, int scanCode) {
		return openMap != null && openMap.matches(keyCode, scanCode);
	}

	public static String mapKeyName() {
		return openMap == null ? "M" : openMap.getTranslatedKeyMessage().getString();
	}
}
