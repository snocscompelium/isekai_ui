package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.gui.IsekaiInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Troca o inventario vanilla (sobrevivencia/aventura) pelo inventario isekai. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	@Nullable
	public LocalPlayer player;
	@Shadow
	@Nullable
	public MultiPlayerGameMode gameMode;

	@Shadow
	public abstract void setScreen(@Nullable Screen screen);

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void isekai$swapInventory(@Nullable Screen screen, CallbackInfo ci) {
		if (screen == null || screen.getClass() != InventoryScreen.class) return;
		if (IsekaiInventoryScreen.consumeVanillaBypass()) return;
		if (!IsekaiConfig.get().enableInventory || this.player == null || this.gameMode == null) return;
		if (this.gameMode.hasInfiniteItems()) return; // criativo continua vanilla
		ci.cancel();
		this.setScreen(new IsekaiInventoryScreen(this.player));
	}
}
