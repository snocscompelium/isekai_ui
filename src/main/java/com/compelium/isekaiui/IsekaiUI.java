package com.compelium.isekaiui;

import com.compelium.isekaiui.client.IsekaiKeys;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.lands.LandsTracker;
import com.compelium.isekaiui.map.MapManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compelium's Isekai UI - inventario e mapa no estilo "janela de status" de isekai.
 * Mod 100% do lado do cliente: todo slot desenhado corresponde a um slot real do inventario.
 */
public final class IsekaiUI implements ClientModInitializer {
	public static final String MOD_ID = "isekai_ui";
	public static final Logger LOG = LoggerFactory.getLogger("Isekai UI");

	/** Ponto unico de criacao de IDs (facilita portar para versoes onde o nome da classe muda). */
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		IsekaiConfig.load();
		IsekaiKeys.register();
		MapManager.init();
		LandsTracker.init();
		ClientTickEvents.END_CLIENT_TICK.register(IsekaiKeys::tick);
		LOG.info("Compelium's Isekai UI pronto.");
	}
}
