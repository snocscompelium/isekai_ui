package com.compelium.isekaiui.client;

import com.compelium.isekaiui.IsekaiUI;
import com.compelium.isekaiui.mixin.KeyMappingAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.util.function.Function;

/**
 * Atalho para o Customizable Player Models (CPM), se ele estiver instalado: abre o menu do CPM (o mesmo da
 * tecla G, com roupas/gestos, modelos e configuracoes). Nada aqui depende do CPM para compilar.
 */
public final class CpmBridge {
	private static final String GESTURE_KEY = "key.cpm.gestureMenu";

	private CpmBridge() {
	}

	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded("cpm");
	}

	/** Abre o menu do CPM. "parent" e a tela para onde voltar quando o menu fechar. */
	public static boolean open(Minecraft mc, Screen parent) {
		if (!installed()) return false;
		try {
			ClassLoader cl = CpmBridge.class.getClassLoader();
			Class<?> guiImpl = Class.forName("com.tom.cpm.client.GuiImpl", true, cl);
			Class<?> gesture = Class.forName("com.tom.cpm.shared.gui.GestureGui", true, cl);
			Class<?> iGui = Class.forName("com.tom.cpl.gui.IGui", true, cl);
			Constructor<?> gestureCtor = gesture.getConstructor(iGui);
			Constructor<?> implCtor = guiImpl.getConstructor(Function.class, Screen.class);
			Function<Object, Object> creator = gui -> {
				try {
					return gestureCtor.newInstance(gui);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			};
			mc.setScreen((Screen) implCtor.newInstance(creator, parent));
			return true;
		} catch (Throwable t) {
			IsekaiUI.LOG.warn("Nao consegui abrir o menu do CPM direto; tentando pela tecla dele", t);
		}
		// plano B: "aperta" a tecla do menu do CPM; ele mesmo abre a tela no proximo tick
		KeyMapping key = KeyMapping.get(GESTURE_KEY);
		if (key == null) return false;
		KeyMappingAccessor acc = (KeyMappingAccessor) key;
		acc.isekai$setClickCount(acc.isekai$getClickCount() + 1);
		mc.setScreen(null);
		return true;
	}
}
