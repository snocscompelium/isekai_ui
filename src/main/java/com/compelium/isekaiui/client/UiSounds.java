package com.compelium.isekaiui.client;

import com.compelium.isekaiui.config.IsekaiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Sons de interface (estilo "janela magica"). Usa apenas sons do proprio jogo. */
public final class UiSounds {
	private UiSounds() {
	}

	public static void windowOpen() {
		play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.75f, 0.9f);
		play(SoundEvents.ENCHANTMENT_TABLE_USE, 2.0f, 0.22f);
	}

	public static void windowClose() {
		play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.55f);
	}

	public static void mapOpen() {
		play(SoundEvents.BOOK_PAGE_TURN, 0.85f, 1.0f);
		play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.45f, 0.45f);
	}

	public static void mapClose() {
		play(SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.8f);
	}

	public static void click() {
		play(SoundEvents.UI_BUTTON_CLICK.value(), 1.7f, 0.3f);
	}

	public static void hover() {
		play(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.15f);
	}

	public static void toggle() {
		play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.85f, 0.35f);
	}

	private static void play(SoundEvent event, float pitch, float volume) {
		IsekaiConfig cfg = IsekaiConfig.get();
		if (!cfg.uiSounds || cfg.uiSoundVolume <= 0f) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume * cfg.uiSoundVolume));
	}
}
