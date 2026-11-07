package com.compelium.isekaiui.client;

import com.compelium.isekaiui.IsekaiUI;
import com.compelium.isekaiui.config.IsekaiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * Sons de interface proprios (estilo "janela magica"). Os arquivos ficam em
 * assets/isekai_ui/sounds/ui/*.ogg e podem ser trocados por um resource pack.
 */
public final class UiSounds {
	private static final SoundEvent WINDOW_OPEN = event("ui.window_open");
	private static final SoundEvent WINDOW_CLOSE = event("ui.window_close");
	private static final SoundEvent MAP_OPEN = event("ui.map_open");
	private static final SoundEvent MAP_CLOSE = event("ui.map_close");
	private static final SoundEvent CLICK = event("ui.click");
	private static final SoundEvent HOVER = event("ui.hover");
	private static final SoundEvent TOGGLE = event("ui.toggle");

	private UiSounds() {
	}

	private static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(IsekaiUI.id(name));
	}

	public static void windowOpen() {
		play(WINDOW_OPEN, 1.0f, 0.9f, true);
	}

	public static void windowClose() {
		play(WINDOW_CLOSE, 1.0f, 0.8f, true);
	}

	public static void mapOpen() {
		play(MAP_OPEN, 1.0f, 0.9f, true);
	}

	public static void mapClose() {
		play(MAP_CLOSE, 1.0f, 0.8f, true);
	}

	public static void click() {
		play(CLICK, 1.0f, 0.7f, false);
	}

	public static void hover() {
		play(HOVER, 1.0f, 0.6f, false);
	}

	public static void toggle() {
		play(TOGGLE, 1.0f, 0.75f, false);
	}

	/** Som de exemplo para quem esta mexendo no volume nas configuracoes. */
	public static void preview(boolean window) {
		if (window) windowOpen();
		else click();
	}

	private static void play(SoundEvent event, float pitch, float volume, boolean window) {
		IsekaiConfig cfg = IsekaiConfig.get();
		float v = volume * cfg.uiSoundVolume * (window ? cfg.windowSoundVolume : cfg.clickSoundVolume);
		if (!cfg.uiSounds || v <= 0.001f) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, v));
	}
}
