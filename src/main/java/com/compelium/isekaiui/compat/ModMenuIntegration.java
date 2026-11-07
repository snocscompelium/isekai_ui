package com.compelium.isekaiui.compat;

import com.compelium.isekaiui.gui.SettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Botao "Configurar" do mod na lista do Mod Menu (so e usado se o Mod Menu estiver instalado). */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
