package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.commands.CommandMemory;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.gui.IsekaiContainerScreen;
import com.compelium.isekaiui.gui.IsekaiInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ChestMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Troca as telas vanilla pelas telas isekai: inventario (sobrevivencia/aventura) e baus/shulkers
 * com o nome padrao do jogo. Tambem avisa o "aprendiz" de comandos quando um menu abre.
 */
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
	private void isekai$swapScreens(@Nullable Screen screen, CallbackInfo ci) {
		if (screen == null) return;
		CommandMemory.onScreenOpening(screen);
		if (this.player == null) return;

		if (screen.getClass() == InventoryScreen.class) {
			if (IsekaiInventoryScreen.consumeVanillaBypass()) return;
			if (!IsekaiConfig.get().enableInventory || this.gameMode == null) return;
			if (this.gameMode.hasInfiniteItems()) return; // criativo continua vanilla
			ci.cancel();
			this.setScreen(new IsekaiInventoryScreen(this.player));
			return;
		}

		if (screen.getClass() == ContainerScreen.class && screen instanceof ContainerScreen chest) {
			if (IsekaiContainerScreen.consumeVanillaBypass()) return;
			if (!IsekaiContainerScreen.isVanillaStorage(chest.getTitle())) return;
			if (!(chest.getMenu() instanceof ChestMenu menu)) return;
			ci.cancel();
			this.setScreen(new IsekaiContainerScreen(menu, this.player.getInventory(), chest.getTitle(), menu.getRowCount(), chest));
			return;
		}

		if (screen.getClass() == ShulkerBoxScreen.class && screen instanceof ShulkerBoxScreen shulker) {
			if (IsekaiContainerScreen.consumeVanillaBypass()) return;
			if (!IsekaiContainerScreen.isVanillaStorage(shulker.getTitle())) return;
			ci.cancel();
			this.setScreen(new IsekaiContainerScreen(shulker.getMenu(), this.player.getInventory(), shulker.getTitle(), 3, shulker));
		}
	}
}
