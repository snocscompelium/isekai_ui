package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.map.ChatWaypoints;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Comandos "/isekai_ui ..." (botoes do chat) sao do mod: tratados aqui e nunca enviados ao servidor. */
@Mixin(value = ClientPacketListener.class, priority = 900)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void isekai$localCommand(String command, CallbackInfo ci) {
		if (ChatWaypoints.handleLocalCommand(command)) ci.cancel();
	}

	@Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
	private void isekai$localUnsignedCommand(String command, CallbackInfoReturnable<Boolean> cir) {
		if (ChatWaypoints.handleLocalCommand(command)) cir.setReturnValue(true);
	}
}
