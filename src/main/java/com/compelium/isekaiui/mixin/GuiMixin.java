package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.lands.LandsTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Le os titulos do centro da tela (usados pelo plugin Lands ao entrar em terrenos). */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "setTitle", at = @At("HEAD"))
	private void isekai$title(Component title, CallbackInfo ci) {
		LandsTracker.onTitle(title);
	}

	@Inject(method = "setSubtitle", at = @At("HEAD"))
	private void isekai$subtitle(Component subtitle, CallbackInfo ci) {
		LandsTracker.onSubtitle(subtitle);
	}

	@Inject(method = "setOverlayMessage", at = @At("HEAD"))
	private void isekai$actionBar(Component message, boolean animateColor, CallbackInfo ci) {
		LandsTracker.onActionBar(message);
	}
}
