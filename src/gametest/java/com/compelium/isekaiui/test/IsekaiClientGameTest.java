package com.compelium.isekaiui.test;

import com.compelium.isekaiui.gui.InventoryLayout;
import com.compelium.isekaiui.gui.IsekaiInventoryScreen;
import com.compelium.isekaiui.gui.IsekaiMapScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Teste automatico rodado no GitHub: cria um mundo, abre o inventario e o mapa e tira prints.
 * Os prints servem para conferir o visual sem precisar abrir o jogo.
 */
public class IsekaiClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(3);
			mc.options.renderDistance().set(7);
			mc.options.simulationDistance().set(5);
			mc.resizeDisplay();
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(creator -> creator.setSeed("isekai-ui"))
				.create()) {
			TestServerContext server = sp.getServer();
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("time set 1000");
			server.runCommand("weather clear");
			server.runCommand("gamemode survival @a");
			server.runCommand("give @a minecraft:diamond_sword");
			server.runCommand("give @a minecraft:iron_pickaxe");
			server.runCommand("give @a minecraft:bread 24");
			server.runCommand("give @a minecraft:oak_log 32");
			server.runCommand("give @a minecraft:torch 48");
			server.runCommand("give @a minecraft:potion");
			server.runCommand("give @a minecraft:map");
			server.runCommand("give @a minecraft:emerald 12");
			server.runCommand("item replace entity @a armor.head with minecraft:iron_helmet");
			server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
			server.runCommand("item replace entity @a armor.legs with minecraft:iron_leggings");
			server.runCommand("item replace entity @a armor.feet with minecraft:iron_boots");
			server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
			server.runCommand("execute at @a run summon minecraft:cow ~6 ~ ~4 {NoAI:1b}");
			server.runCommand("execute at @a run summon minecraft:pig ~-5 ~ ~7 {NoAI:1b}");
			server.runCommand("execute at @a run summon minecraft:sheep ~9 ~ ~-6 {NoAI:1b}");
			server.runCommand("execute at @a run summon minecraft:husk ~-8 ~ ~-9 {NoAI:1b}");

			// renderizacao por software no GitHub e lenta: espera ate 5 minutos
			sp.getClientWorld().waitForChunksRender(true, 20 * 60 * 5);
			ctx.waitTicks(80);

			// simula o titulo do plugin Lands e anda por alguns chunks dentro do "terreno"
			ctx.runOnClient(mc -> mc.gui.setTitle(Component.literal("Reino de Aurora").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)));
			for (String step : new String[]{"~16 ~ ~", "~16 ~ ~", "~ ~ ~16", "~-16 ~ ~", "~-16 ~ ~"}) {
				server.runCommand("execute as @a at @s run tp @s " + step);
				ctx.waitTicks(4);
			}
			ctx.runOnClient(mc -> mc.gui.setTitle(Component.literal("Wilderness").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)));
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~-16");
			ctx.runOnClient(mc -> mc.gui.clearTitles());
			ctx.waitTicks(40);
			ctx.takeScreenshot("isekai-00-mundo");

			// inventario
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(3);
			ctx.takeScreenshot("isekai-01-inventario-abrindo");
			ctx.waitTicks(25);
			ctx.takeScreenshot("isekai-02-inventario");
			boolean isekai = ctx.computeOnClient(mc -> mc.screen instanceof IsekaiInventoryScreen);
			if (!isekai) throw new AssertionError("O inventario isekai nao abriu");

			double[] mini = ctx.computeOnClient(IsekaiClientGameTest::miniCenter);
			ctx.getInput().setCursorPos(mini[0], mini[1]);
			ctx.waitTicks(6);
			ctx.takeScreenshot("isekai-03-inventario-minimapa-hover");

			// clica no minimapa -> mapa grande
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			ctx.waitTicks(5);
			ctx.takeScreenshot("isekai-04-mapa-abrindo");
			ctx.waitTicks(25);
			ctx.takeScreenshot("isekai-05-mapa");
			boolean map = ctx.computeOnClient(mc -> mc.screen instanceof IsekaiMapScreen);
			if (!map) throw new AssertionError("O mapa nao abriu");
			ctx.getInput().scroll(-4);
			ctx.waitTicks(30);
			ctx.takeScreenshot("isekai-06-mapa-zoom-longe");
			ctx.getInput().scroll(8);
			ctx.waitTicks(20);
			ctx.takeScreenshot("isekai-07-mapa-zoom-perto");

			// E volta para o inventario
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(30);
			ctx.takeScreenshot("isekai-08-de-volta-ao-inventario");

			// F5 dentro do inventario: primeira pessoa
			ctx.getInput().pressKey(GLFW.GLFW_KEY_F5);
			ctx.waitTicks(20);
			ctx.takeScreenshot("isekai-09-primeira-pessoa");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_F5);
			ctx.waitTicks(3);

			// fecha
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(25);
			ctx.takeScreenshot("isekai-10-fechado");

			// mapa pela tecla M
			ctx.getInput().pressKey(GLFW.GLFW_KEY_M);
			ctx.waitTicks(30);
			ctx.takeScreenshot("isekai-11-mapa-pela-tecla");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);

			// tela maior (1080p, escala 4)
			ctx.getInput().resizeWindow(1920, 1080);
			ctx.runOnClient(mc -> {
				mc.options.guiScale().set(4);
				mc.resizeDisplay();
			});
			ctx.waitTicks(5);
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(30);
			ctx.takeScreenshot("isekai-12-inventario-1080p");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);
		}
	}

	private static double[] miniCenter(Minecraft mc) {
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		InventoryLayout layout = InventoryLayout.compute(w, h, IsekaiInventoryScreen.buttonCount());
		double scale = mc.getWindow().getGuiScale();
		return new double[]{layout.mini.cx() * scale, layout.mini.cy() * scale};
	}
}
