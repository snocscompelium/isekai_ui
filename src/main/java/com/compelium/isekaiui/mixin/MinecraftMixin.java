package com.compelium.isekaiui.mixin;

import com.compelium.isekaiui.commands.CommandMemory;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.gui.IsekaiAnvilScreen;
import com.compelium.isekaiui.gui.IsekaiBrewingScreen;
import com.compelium.isekaiui.gui.IsekaiChestScreen;
import com.compelium.isekaiui.gui.IsekaiCraftingScreen;
import com.compelium.isekaiui.gui.IsekaiCreativeScreen;
import com.compelium.isekaiui.gui.IsekaiEnchantingScreen;
import com.compelium.isekaiui.gui.IsekaiFurnaceScreen;
import com.compelium.isekaiui.gui.IsekaiGrindstoneScreen;
import com.compelium.isekaiui.gui.IsekaiInventoryScreen;
import com.compelium.isekaiui.gui.IsekaiSmithingScreen;
import com.compelium.isekaiui.gui.IsekaiStationScreen;
import com.compelium.isekaiui.gui.IsekaiStonecutterScreen;
import com.compelium.isekaiui.gui.VanillaTitles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Troca as telas vanilla pelas telas isekai: inventario, criativo, baus/shulkers e estacoes de trabalho.
 * Baus e estacoes so sao trocados com o titulo padrao do jogo (menus de plugin ficam intactos).
 * Tambem avisa o "aprendiz" de comandos quando um menu abre.
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
		IsekaiConfig cfg = IsekaiConfig.get();
		Class<?> type = screen.getClass();

		if (type == InventoryScreen.class) {
			if (IsekaiInventoryScreen.consumeVanillaBypass()) return;
			if (!cfg.enableInventory || this.gameMode == null) return;
			if (this.gameMode.hasInfiniteItems()) return; // criativo: o jogo abre a tela de criativo em seguida
			ci.cancel();
			this.setScreen(new IsekaiInventoryScreen(this.player));
			return;
		}

		if (type == CreativeModeInventoryScreen.class) {
			if (IsekaiCreativeScreen.consumeVanillaBypass()) return;
			if (!cfg.enableCreative || this.gameMode == null || !this.gameMode.hasInfiniteItems()) return;
			ci.cancel();
			this.setScreen(new IsekaiCreativeScreen(this.player));
			return;
		}

		if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
		Component title = acs.getTitle();
		Inventory inv = this.player.getInventory();
		Screen replacement = null;

		if (cfg.enableContainers) {
			if (type == ContainerScreen.class && acs.getMenu() instanceof ChestMenu menu && VanillaTitles.is(title, VanillaTitles.STORAGE)) {
				replacement = new IsekaiChestScreen(menu, inv, title, 9, menu.getRowCount(), screen);
			} else if (type == ShulkerBoxScreen.class && VanillaTitles.is(title, VanillaTitles.STORAGE)) {
				replacement = new IsekaiChestScreen(acs.getMenu(), inv, title, 9, 3, screen);
			} else if (type == HopperScreen.class && VanillaTitles.is(title, "container.hopper")) {
				replacement = new IsekaiChestScreen(acs.getMenu(), inv, title, 5, 1, screen);
			} else if (type == DispenserScreen.class && VanillaTitles.is(title, "container.dispenser", "container.dropper")) {
				replacement = new IsekaiChestScreen(acs.getMenu(), inv, title, 3, 3, screen);
			}
		}
		if (replacement == null && cfg.enableStations) {
			if (type == CraftingScreen.class && screen instanceof CraftingScreen s && VanillaTitles.is(title, "container.crafting")) {
				replacement = new IsekaiCraftingScreen(s.getMenu(), inv, title, screen);
			} else if ((type == FurnaceScreen.class || type == SmokerScreen.class || type == BlastFurnaceScreen.class)
					&& acs.getMenu() instanceof AbstractFurnaceMenu menu
					&& VanillaTitles.is(title, "container.furnace", "container.smoker", "container.blast_furnace")) {
				String key = type == FurnaceScreen.class ? "furnace" : type == SmokerScreen.class ? "smoker" : "blast_furnace";
				replacement = new IsekaiFurnaceScreen(menu, inv, title, screen, key);
			} else if (type == AnvilScreen.class && screen instanceof AnvilScreen s && VanillaTitles.is(title, "container.repair")) {
				replacement = new IsekaiAnvilScreen(s.getMenu(), inv, title, screen);
			} else if (type == SmithingScreen.class && screen instanceof SmithingScreen s && VanillaTitles.is(title, "container.upgrade")) {
				replacement = new IsekaiSmithingScreen(s.getMenu(), inv, title, screen);
			} else if (type == GrindstoneScreen.class && screen instanceof GrindstoneScreen s && VanillaTitles.is(title, "container.grindstone_title")) {
				replacement = new IsekaiGrindstoneScreen(s.getMenu(), inv, title, screen);
			} else if (type == StonecutterScreen.class && screen instanceof StonecutterScreen s && VanillaTitles.is(title, "container.stonecutter")) {
				replacement = new IsekaiStonecutterScreen(s.getMenu(), inv, title, screen);
			} else if (type == BrewingStandScreen.class && screen instanceof BrewingStandScreen s && VanillaTitles.is(title, "container.brewing")) {
				replacement = new IsekaiBrewingScreen(s.getMenu(), inv, title, screen);
			} else if (type == EnchantmentScreen.class && screen instanceof EnchantmentScreen s && VanillaTitles.is(title, "container.enchant")) {
				replacement = new IsekaiEnchantingScreen(s.getMenu(), inv, title, screen);
			}
		}
		if (replacement == null) return;
		if (IsekaiStationScreen.consumeVanillaBypass()) return;
		ci.cancel();
		this.setScreen(replacement);
	}
}
