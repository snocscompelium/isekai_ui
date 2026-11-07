package com.compelium.isekaiui.test;

import com.compelium.isekaiui.client.ReturnToContainer;
import com.compelium.isekaiui.commands.CommandMemory;
import com.compelium.isekaiui.client.Instruments;
import com.compelium.isekaiui.creative.ArmorSets;
import com.compelium.isekaiui.creative.PersonalChest;
import com.compelium.isekaiui.lands.LandsTracker;
import com.compelium.isekaiui.gui.IsekaiAnvilScreen;
import com.compelium.isekaiui.gui.IsekaiBrewingScreen;
import com.compelium.isekaiui.gui.IsekaiCraftingScreen;
import com.compelium.isekaiui.gui.IsekaiCreativeScreen;
import com.compelium.isekaiui.gui.IsekaiEnchantingScreen;
import com.compelium.isekaiui.gui.IsekaiFurnaceScreen;
import com.compelium.isekaiui.gui.IsekaiGrindstoneScreen;
import com.compelium.isekaiui.gui.IsekaiSmithingScreen;
import com.compelium.isekaiui.gui.IsekaiStonecutterScreen;
import com.compelium.isekaiui.map.ChatWaypoints;
import com.compelium.isekaiui.mixin.MinecraftAccessor;
import com.compelium.isekaiui.gui.IsekaiChestScreen;
import com.compelium.isekaiui.gui.IsekaiInventoryScreen;
import com.compelium.isekaiui.gui.IsekaiMapScreen;
import com.compelium.isekaiui.gui.Rect;
import com.compelium.isekaiui.gui.HudLayoutScreen;
import com.compelium.isekaiui.gui.IsekaiMenuScreen;
import com.compelium.isekaiui.gui.RecipeBookPanel;
import com.compelium.isekaiui.gui.SettingsScreen;
import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.recipes.RecipeJournal;
import com.compelium.isekaiui.recipes.RecipePlacer;
import com.compelium.isekaiui.map.DimMap;
import com.compelium.isekaiui.map.Emblems;
import com.compelium.isekaiui.map.MapManager;
import com.compelium.isekaiui.map.Waypoints;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
			mc.options.renderDistance().set(6);
			mc.options.simulationDistance().set(5);
			mc.resizeDisplay();
		});
		// em portugues (como o servidor joga): confere se os textos cabem nas janelas
		ctx.runOnClient(mc -> {
			mc.options.languageCode = "pt_br";
			mc.getLanguageManager().setSelected("pt_br");
			mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
		});
		fontScan(ctx);
		// mundo plano (rapido de gerar no GitHub); o cenario de teste e montado com comandos
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			// garante o jogador em cima do chao (no mundo plano do teste ele nasce afundado 1 bloco)
			server.runCommand("execute as @a at @s positioned over world_surface run tp @s ~ ~ ~");
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
			server.runCommand("give @a minecraft:light_blue_shulker_box[minecraft:container=["
					+ "{slot:0,item:{id:\"minecraft:diamond\",count:3}},{slot:1,item:{id:\"minecraft:gold_ingot\",count:16}},"
					+ "{slot:4,item:{id:\"minecraft:ender_pearl\",count:8}},{slot:9,item:{id:\"minecraft:golden_apple\",count:2}},"
					+ "{slot:13,item:{id:\"minecraft:enchanted_book\",count:1}},{slot:22,item:{id:\"minecraft:cake\",count:1}}]]");
			server.runCommand("give @a minecraft:bundle[minecraft:bundle_contents=[{id:\"minecraft:arrow\",count:16},{id:\"minecraft:bread\",count:4},{id:\"minecraft:feather\",count:3}]]");
			server.runCommand("item replace entity @a armor.head with minecraft:iron_helmet");
			server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
			server.runCommand("item replace entity @a armor.legs with minecraft:iron_leggings");
			server.runCommand("item replace entity @a armor.feet with minecraft:iron_boots");
			server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
			buildScene(server);
			server.runCommand("execute at @a run summon minecraft:cow ~6 ~ ~4 {NoAI:1b}");
			server.runCommand("execute at @a run summon minecraft:pig ~-5 ~ ~7 {NoAI:1b}");
			server.runCommand("execute at @a run summon minecraft:sheep ~9 ~ ~-6 {NoAI:1b}");
			server.runCommand("execute at @a run summon minecraft:husk ~-8 ~ ~-9 {NoAI:1b}");

			sp.getClientWorld().waitForChunksRender(true, 20 * 60 * 4);
			ctx.waitTicks(60);

			// simula o titulo do plugin Lands e anda por alguns chunks dentro do "terreno"
			ctx.runOnClient(mc -> mc.gui.setTitle(Component.literal("Reino de Aurora").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)));
			for (String step : new String[]{"~ ~ ~-16", "~-16 ~ ~", "~ ~ ~-16", "~16 ~ ~"}) {
				server.runCommand("execute as @a at @s run tp @s " + step);
				ctx.waitTicks(4);
			}
			ctx.runOnClient(mc -> mc.gui.setTitle(Component.literal("Wilderness").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)));
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~32");
			ctx.runOnClient(mc -> mc.gui.clearTitles());
			ctx.waitTicks(40);
			ctx.takeScreenshot("isekai-00-mundo");

			// inventario
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(3);
			ctx.takeScreenshot("isekai-01-inventario-abrindo");
			ctx.waitTicks(60);
			String camInfo = ctx.computeOnClient(mc -> {
				var c = mc.gameRenderer.getMainCamera();
				var p = mc.player;
				return "progresso=" + com.compelium.isekaiui.camera.CinematicCamera.progress()
						+ " camera=" + c.getPosition() + " olho=" + p.getEyePosition()
						+ " dist=" + c.getPosition().distanceTo(p.getEyePosition())
						+ " camYaw=" + c.getYRot() + " camPitch=" + c.getXRot()
						+ " playerYaw=" + p.getYRot() + " pes=" + p.position()
						+ " fov=" + mc.options.fov().get() + " tipo=" + mc.options.getCameraType()
						+ " detached=" + c.isDetached();
			});
			System.out.println("[isekai-test] " + camInfo);
			ctx.takeScreenshot("isekai-02-inventario");
			ctx.runOnClient(mc -> com.compelium.isekaiui.gui.IsekaiMenuScreen.debugHidePanels = true);
			ctx.waitTicks(4);
			ctx.takeScreenshot("isekai-02b-so-personagem");
			ctx.runOnClient(mc -> com.compelium.isekaiui.gui.IsekaiMenuScreen.debugHidePanels = false);
			boolean isekai = ctx.computeOnClient(mc -> mc.screen instanceof IsekaiInventoryScreen);
			if (!isekai) throw new AssertionError("O inventario isekai nao abriu");

			// v0.2: arrastar a janela do inventario pela aba do titulo (escala 2: sobra espaco na tela)
			setGuiScale(ctx, 2);
			double scale = 2.0;
			Rect before = panelRect(ctx, "main");
			dragPanel(ctx, before, -60, -30, scale, "isekai-02c-arrastando-janela");
			ctx.takeScreenshot("isekai-02d-janela-movida");
			Rect moved = panelRect(ctx, "main");
			System.out.println("[isekai-test] janela antes=" + before + " depois=" + moved);
			if (moved.x() == before.x() && moved.y() == before.y()) throw new AssertionError("A janela nao foi arrastada");
			doubleClickTab(ctx, moved, scale);
			Rect reset = panelRect(ctx, "main");
			if (!reset.equals(before)) {
				throw new AssertionError("Duplo clique na aba nao devolveu a janela ao lugar padrao: " + reset + " (esperado " + before + ")");
			}

			// soltar uma janela em cima de outra: ela se encaixa no lugar livre mais perto
			setGuiScale(ctx, 3);
			scale = 3.0;
			Rect before3 = panelRect(ctx, "main");
			dragPanel(ctx, before3, 72, 36, scale, "isekai-02e-arrastando-por-cima");
			ctx.takeScreenshot("isekai-02f-encaixada-no-lugar-livre");
			Rect main3 = panelRect(ctx, "main");
			Rect mini3 = panelRect(ctx, "minimap");
			Rect craft3 = panelRect(ctx, "craft");
			System.out.println("[isekai-test] encaixe: main=" + main3 + " minimapa=" + mini3 + " sintese=" + craft3);
			if (intersects(main3, mini3) || intersects(main3, craft3)) throw new AssertionError("A janela ficou por cima de outra: " + main3);
			doubleClickTab(ctx, main3, scale);
			if (!panelRect(ctx, "main").equals(before3)) throw new AssertionError("Duplo clique nao restaurou a janela");

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

			// v0.2: marcadores e simbolo do reino
			ctx.runOnClient(mc -> {
				DimMap dim = MapManager.current();
				int px = mc.player.getBlockX();
				int py = mc.player.getBlockY();
				int pz = mc.player.getBlockZ();
				Waypoints.Waypoint mine = new Waypoints.Waypoint();
				mine.name = "Mina";
				mine.x = px + 27;
				mine.y = py + 4;
				mine.z = pz + 6;
				mine.icon = "glyph:pickaxe";
				mine.color = Waypoints.COLORS[1];
				dim.waypoints().add(mine);
				Waypoints.Waypoint far = new Waypoints.Waypoint();
				far.name = "Torre distante";
				far.x = px + 900;
				far.y = py;
				far.z = pz - 420;
				far.icon = "glyph:tower";
				far.color = Waypoints.COLORS[5];
				dim.waypoints().add(far);
				((IsekaiMapScreen) mc.screen).debugOpenNewWaypoint(px - 3, pz + 26, "Vila do Teste");
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("isekai-11b-novo-marcador");
			ctx.runOnClient(mc -> {
				((IsekaiMapScreen) mc.screen).debugSaveWaypoint();
				Emblems.set("Reino de Aurora", "emblem:crown");
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("isekai-11c-mapa-com-marcadores");
			int waypointCount = ctx.computeOnClient(mc -> MapManager.current().waypoints().all().size());
			if (waypointCount != 3) throw new AssertionError("Esperava 3 marcadores, tem " + waypointCount);
			ctx.runOnClient(mc -> ((IsekaiMapScreen) mc.screen).debugOpenEmblem("Reino de Aurora"));
			ctx.waitTicks(5);
			ctx.takeScreenshot("isekai-11d-simbolo-do-reino");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(3);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);

			// v0.2: bau de verdade (estilo isekai) com uma shulker cheia dentro
			server.runCommand("execute at @p run setblock ~2 ~ ~ minecraft:chest[facing=west]{Items:["
					+ "{Slot:0b,id:\"minecraft:diamond\",count:5},{Slot:1b,id:\"minecraft:golden_apple\",count:3},"
					+ "{Slot:4b,id:\"minecraft:red_shulker_box\",count:1,components:{\"minecraft:container\":["
					+ "{slot:0,item:{id:\"minecraft:iron_ingot\",count:32}},{slot:2,item:{id:\"minecraft:redstone\",count:20}},"
					+ "{slot:10,item:{id:\"minecraft:experience_bottle\",count:6}},{slot:26,item:{id:\"minecraft:totem_of_undying\",count:1}}]}},"
					+ "{Slot:13b,id:\"minecraft:oak_log\",count:32},{Slot:22b,id:\"minecraft:iron_ingot\",count:20}]} replace");
			// bau com nome (como os menus de plugin): tem que continuar vanilla
			server.runCommand("execute at @p run setblock ~-2 ~ ~ minecraft:chest[facing=east]{CustomName:'\"Loja do Servidor\"',Items:["
					+ "{Slot:10b,id:\"minecraft:emerald\",count:1},{Slot:12b,id:\"minecraft:diamond_sword\",count:1},"
					+ "{Slot:14b,id:\"minecraft:golden_apple\",count:1},{Slot:16b,id:\"minecraft:experience_bottle\",count:1}]} replace");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> useBlock(mc, 2));
			ctx.waitTicks(40);
			ctx.takeScreenshot("isekai-13-bau-isekai");
			boolean chestIsekai = ctx.computeOnClient(mc -> mc.screen instanceof IsekaiChestScreen);
			if (!chestIsekai) throw new AssertionError("O bau nao abriu no estilo isekai: " + ctx.computeOnClient(mc -> String.valueOf(mc.screen)));
			double[] shulkerSlot = ctx.computeOnClient(mc -> slotCenter(mc, 4));
			ctx.getInput().setCursorPos(shulkerSlot[0], shulkerSlot[1]);
			ctx.waitTicks(4);
			ctx.takeScreenshot("isekai-14-bau-shulker-por-dentro");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(25);

			ctx.runOnClient(mc -> useBlock(mc, -2));
			ctx.waitTicks(15);
			ctx.takeScreenshot("isekai-15-menu-de-plugin-intacto");
			boolean pluginVanilla = ctx.computeOnClient(mc -> mc.screen != null && mc.screen.getClass() == ContainerScreen.class);
			if (!pluginVanilla) throw new AssertionError("O bau com nome devia continuar vanilla: " + ctx.computeOnClient(mc -> String.valueOf(mc.screen)));
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(50);

			// v0.2: comando que abre menu vira comando rapido
			ctx.runOnClient(mc -> mc.player.connection.sendCommand("pets"));
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> mc.setScreen(new ContainerScreen(ChestMenu.threeRows(91, mc.player.getInventory()),
					mc.player.getInventory(), Component.literal("Menu de Pets"))));
			ctx.waitTicks(5);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(10);
			boolean learned = ctx.computeOnClient(mc -> CommandMemory.entries().stream().anyMatch(e -> e.command.equals("pets")));
			if (!learned) throw new AssertionError("O comando /pets nao foi aprendido");

			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(40);
			double[] shulkerInv = ctx.computeOnClient(mc -> slotCenter(mc, 44));
			ctx.getInput().setCursorPos(shulkerInv[0], shulkerInv[1]);
			ctx.waitTicks(4);
			ctx.takeScreenshot("isekai-16-inventario-shulker");
			double[] bundleInv = ctx.computeOnClient(mc -> slotCenter(mc, 9));
			ctx.getInput().setCursorPos(bundleInv[0], bundleInv[1]);
			ctx.waitTicks(4);
			ctx.takeScreenshot("isekai-17-inventario-sacola");
			Rect listButton = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("commands"));
			click(ctx, listButton, scale);
			ctx.waitTicks(15);
			ctx.takeScreenshot("isekai-18-comandos-rapidos");
			Rect pin = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugCommandPinRect(0));
			click(ctx, pin, scale);
			ctx.waitTicks(15);
			ctx.takeScreenshot("isekai-19-comando-fixado");
			boolean pinnedOnBar = ctx.computeOnClient(mc -> {
				IsekaiInventoryScreen inv = (IsekaiInventoryScreen) mc.screen;
				return inv.debugButtonRect("pin:pets") != null || inv.debugInDrawer("pin:pets");
			});
			int buttons = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonCount());
			System.out.println("[isekai-test] botoes na barra: " + buttons);
			if (!pinnedOnBar) throw new AssertionError("O comando fixado nao apareceu na barra (botoes: " + buttons + ")");
			// fecha a lista e o inventario
			Rect listAgain = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("commands"));
			click(ctx, listAgain, scale);
			ctx.waitTicks(5);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);

			// ================================================================== v0.3
			server.runCommand("item replace entity @a container.18 with minecraft:netherite_upgrade_smithing_template");
			server.runCommand("item replace entity @a container.19 with minecraft:diamond_chestplate");
			server.runCommand("item replace entity @a container.20 with minecraft:netherite_ingot");
			server.runCommand("item replace entity @a container.21 with minecraft:lapis_lazuli 16");
			server.runCommand("item replace entity @a container.22 with minecraft:stone 32");
			server.runCommand("item replace entity @a container.23 with minecraft:diamond_sword[minecraft:custom_name='{\"text\":\"Lamina do Rei\",\"color\":\"gold\",\"italic\":false}',"
					+ "minecraft:lore=['{\"text\":\"Arma lendaria do servidor\",\"color\":\"gray\"}'],minecraft:enchantments={levels:{\"minecraft:sharpness\":5}}]");
			server.runCommand("item replace entity @a container.24 with minecraft:raw_iron 12");
			server.runCommand("item replace entity @a container.25 with minecraft:coal 6");
			server.runCommand("xp add @a 30 levels");

			// bau -> craft no inventario -> volta ao bau (o jogador olhando para o bau, como de verdade)
			ctx.runOnClient(mc -> {
				mc.player.setYRot(-90f);
				mc.player.setXRot(29f);
				mc.player.yRotO = -90f;
				mc.player.xRotO = 29f;
			});
			ctx.waitTicks(4);
			boolean aiming = ctx.computeOnClient(mc -> ReturnToContainer.lookedAtContainer() != null);
			System.out.println("[isekai-test] mirando o bau: " + aiming);
			ctx.runOnClient(mc -> ((MinecraftAccessor) mc).isekai$startUseItem());
			ctx.waitTicks(30);
			assertScreen(ctx, IsekaiChestScreen.class, "bau pela mira");
			ctx.takeScreenshot("isekai-40-bau-com-botao-craft");
			click(ctx, ctx.computeOnClient(mc -> ((IsekaiChestScreen) mc.screen).debugSideButtonRect("craft")), scale);
			ctx.waitTicks(40);
			assertScreen(ctx, IsekaiInventoryScreen.class, "inventario depois do botao craft");
			ctx.takeScreenshot("isekai-41-craft-com-voltar-ao-bau");
			Rect backButton = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("chest"));
			if (backButton == null) throw new AssertionError("Sem o botao 'voltar ao bau'");
			click(ctx, backButton, scale);
			ctx.waitTicks(40);
			assertScreen(ctx, IsekaiChestScreen.class, "de volta ao bau");
			ctx.takeScreenshot("isekai-42-de-volta-ao-bau");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);

			// estacoes de trabalho
			server.runCommand("execute at @p run setblock ~-2 ~ ~2 minecraft:crafting_table replace");
			server.runCommand("execute at @p run setblock ~-1 ~ ~2 minecraft:furnace[facing=north] replace");
			server.runCommand("execute at @p run setblock ~ ~ ~2 minecraft:anvil[facing=north] replace");
			server.runCommand("execute at @p run setblock ~1 ~ ~2 minecraft:smithing_table replace");
			server.runCommand("execute at @p run setblock ~2 ~ ~2 minecraft:grindstone[face=floor,facing=north] replace");
			server.runCommand("execute at @p run setblock ~-2 ~ ~-2 minecraft:stonecutter[facing=south] replace");
			server.runCommand("execute at @p run setblock ~-1 ~ ~-2 minecraft:brewing_stand{Items:["
					+ "{Slot:0b,id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:water\"}}},"
					+ "{Slot:1b,id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:water\"}}},"
					+ "{Slot:3b,id:\"minecraft:nether_wart\",count:4},{Slot:4b,id:\"minecraft:blaze_powder\",count:8}]} replace");
			server.runCommand("execute at @p run setblock ~ ~ ~-2 minecraft:enchanting_table replace");
			server.runCommand("execute at @p run setblock ~1 ~ ~-2 minecraft:hopper{Items:[{Slot:0b,id:\"minecraft:apple\",count:5},{Slot:2b,id:\"minecraft:gold_nugget\",count:9}]} replace");
			server.runCommand("execute at @p run setblock ~2 ~ ~-2 minecraft:blast_furnace[facing=south] replace");
			ctx.waitTicks(10);

			openStation(ctx, -2, 2, IsekaiCraftingScreen.class, "mesa de trabalho");
			ctx.runOnClient(mc -> {
				int id = mc.player.containerMenu.containerId;
				mc.gameMode.handleInventoryMouseClick(id, 40, 0, ClickType.PICKUP, mc.player);
				mc.gameMode.handleInventoryMouseClick(id, 1, 1, ClickType.PICKUP, mc.player);
				mc.gameMode.handleInventoryMouseClick(id, 40, 0, ClickType.PICKUP, mc.player);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("isekai-43-mesa-de-trabalho");
			closeStation(ctx);

			openStation(ctx, -1, 2, IsekaiFurnaceScreen.class, "fornalha");
			quickMove(ctx, 18);
			quickMove(ctx, 19);
			ctx.waitTicks(60);
			ctx.takeScreenshot("isekai-44-fornalha");
			closeStation(ctx);

			openStation(ctx, 0, 2, IsekaiAnvilScreen.class, "bigorna");
			quickMove(ctx, 30);
			ctx.waitTicks(10);
			ctx.takeScreenshot("isekai-45-bigorna");
			closeStation(ctx);

			openStation(ctx, 1, 2, IsekaiSmithingScreen.class, "ferraria");
			quickMove(ctx, 13);
			quickMove(ctx, 14);
			quickMove(ctx, 15);
			ctx.waitTicks(15);
			ctx.takeScreenshot("isekai-46-ferraria-provador");
			closeStation(ctx);

			openStation(ctx, 2, 2, IsekaiGrindstoneScreen.class, "rebolo");
			ctx.takeScreenshot("isekai-47-rebolo");
			closeStation(ctx);

			openStation(ctx, -2, -2, IsekaiStonecutterScreen.class, "cortador de pedras");
			quickMove(ctx, 15);
			ctx.waitTicks(10);
			ctx.takeScreenshot("isekai-48-cortador");
			closeStation(ctx);

			openStation(ctx, -1, -2, IsekaiBrewingScreen.class, "suporte de pocoes");
			ctx.waitTicks(30);
			ctx.takeScreenshot("isekai-49-pocoes");
			closeStation(ctx);

			openStation(ctx, 0, -2, IsekaiEnchantingScreen.class, "mesa de encantamento");
			quickMove(ctx, 30);
			quickMove(ctx, 14);
			ctx.waitTicks(15);
			ctx.takeScreenshot("isekai-50-encantamento");
			closeStation(ctx);

			openStation(ctx, 1, -2, IsekaiChestScreen.class, "funil");
			ctx.takeScreenshot("isekai-51-funil");
			closeStation(ctx);

			openStation(ctx, 2, -2, IsekaiFurnaceScreen.class, "alto-forno");
			ctx.takeScreenshot("isekai-52-alto-forno");
			closeStation(ctx);

			// comando anotado a mao na lista de comandos rapidos
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(40);
			Rect listBtn = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("commands"));
			click(ctx, listBtn, scale);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugAddCommand("/tp @s 0 100 0"));
			ctx.waitTicks(10);
			ctx.takeScreenshot("isekai-53-comando-anotado");
			boolean noted = ctx.computeOnClient(mc -> CommandMemory.entries().stream().anyMatch(e -> e.command.equals("tp @s 0 100 0")));
			if (!noted) throw new AssertionError("O comando anotado nao entrou na lista");
			Rect listBtn2 = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("commands"));
			click(ctx, listBtn2, scale);
			ctx.waitTicks(5);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);

			// marcadores compartilhados no chat por outros mods de mapa
			ctx.runOnClient(mc -> {
				mc.gui.getChat().addMessage(Component.literal("<Aldric> galera, a base nova: xaero-waypoint:Base do Cla:B:-60:70:-40:9:false:0:Internal-overworld-waypoints"));
				mc.gui.getChat().addMessage(Component.literal("<Mirela> [name:\"Mina de Ouro\", x:40, y:52, z:-30, dim:minecraft:overworld]"));
				mc.gui.getChat().addMessage(Component.literal("<Tobias> to aqui ó X: 18 Y: 64 Z: 25"));
			});
			ctx.getInput().pressKey(GLFW.GLFW_KEY_T);
			ctx.waitTicks(10);
			ctx.takeScreenshot("isekai-54-chat-com-marcadores");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(5);
			int lastId = ctx.computeOnClient(mc -> ChatWaypoints.debugLastId());
			System.out.println("[isekai-test] ultimo botao de marcador do chat: " + lastId);
			if (lastId < 3) throw new AssertionError("Os marcadores do chat nao foram reconhecidos (" + lastId + ")");
			ctx.runOnClient(mc -> mc.player.connection.sendUnsignedCommand(ChatWaypoints.COMMAND + " waypoint " + (lastId - 2)));
			ctx.waitTicks(30);
			assertScreen(ctx, IsekaiMapScreen.class, "editor do marcador recebido");
			ctx.takeScreenshot("isekai-55-marcador-do-chat");
			ctx.runOnClient(mc -> ((IsekaiMapScreen) mc.screen).debugSaveWaypoint());
			ctx.waitTicks(5);
			ctx.takeScreenshot("isekai-56-mapa-com-marcador-do-chat");
			ctx.runOnClient(mc -> {
				int px = mc.player.getBlockX();
				int pz = mc.player.getBlockZ();
				((IsekaiMapScreen) mc.screen).debugOpenNewWaypoint(px + 5, pz + 5, "Fogueira");
				((IsekaiMapScreen) mc.screen).debugShare();
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("isekai-57-compartilhar-no-chat");
			String shared = ctx.computeOnClient(mc -> String.valueOf(mc.screen));
			System.out.println("[isekai-test] tela de compartilhar: " + shared);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(10);

			// ================================================================== v0.4
			// territorios marcados a mao no mapa grande (o chunk do jogador fica dentro do Reino de Aurora)
			ctx.getInput().pressKey(GLFW.GLFW_KEY_M);
			ctx.waitTicks(30);
			assertScreen(ctx, IsekaiMapScreen.class, "mapa para marcar territorios");
			ctx.runOnClient(mc -> {
				int pcx = mc.player.getBlockX() >> 4;
				int pcz = mc.player.getBlockZ() >> 4;
				IsekaiMapScreen ms = (IsekaiMapScreen) mc.screen;
				ms.debugPaintTerritory("Reino de Aurora", pcx - 1, pcz - 2, pcx + 2, pcz);
				ms.debugPaintTerritory("Vale Sombrio", pcx + 3, pcz + 1, pcx + 5, pcz + 3);
			});
			double[] mapMid = ctx.computeOnClient(mc -> new double[]{mc.getWindow().getWidth() * 0.62, mc.getWindow().getHeight() * 0.62});
			ctx.getInput().setCursorPos(mapMid[0], mapMid[1]);
			ctx.waitTicks(10);
			ctx.takeScreenshot("isekai-62-territorios-pintando");
			ctx.runOnClient(mc -> ((IsekaiMapScreen) mc.screen).debugTerritoryMode(false));
			ctx.waitTicks(10);
			ctx.takeScreenshot("isekai-63-mapa-com-territorios");
			String landHere = ctx.computeOnClient(mc -> String.valueOf(LandsTracker.currentLand()));
			System.out.println("[isekai-test] territorio onde o jogador esta: " + landHere);
			if (!"Reino de Aurora".equals(landHere)) throw new AssertionError("O chunk pintado nao virou territorio: " + landHere);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(25);

			// conjuntos de armadura no sobrevivencia: salva o de ferro, veste diamante (o ferro vai para a bolsa)
			ctx.runOnClient(mc -> ArmorSets.saveCurrent(mc.player));
			String[][] pieces = {{"head", "27", "diamond_helmet"}, {"chest", "28", "diamond_chestplate"},
					{"legs", "29", "diamond_leggings"}, {"feet", "30", "diamond_boots"}};
			for (String[] pc : pieces) {
				server.runCommand("item replace entity @a container." + pc[1] + " from entity @p armor." + pc[0]);
				server.runCommand("item replace entity @a armor." + pc[0] + " with minecraft:" + pc[2]);
			}
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> ArmorSets.saveCurrent(mc.player));
			int sets = ctx.computeOnClient(mc -> ArmorSets.all().size());
			if (sets != 2) throw new AssertionError("Esperava 2 conjuntos salvos, tem " + sets);
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(40);
			assertScreen(ctx, IsekaiInventoryScreen.class, "inventario para os conjuntos");
			click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("armor")), scale);
			ctx.waitTicks(15);
			ctx.takeScreenshot("isekai-64-conjuntos-de-armadura");
			int missingSurvival = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugEquipSet(0));
			ctx.waitTicks(15);
			ctx.takeScreenshot("isekai-65-conjunto-vestido");
			String headSurvival = wornOnServer(server, EquipmentSlot.HEAD);
			System.out.println("[isekai-test] sobrevivencia: faltaram " + missingSurvival + ", cabeca=" + headSurvival);
			if (missingSurvival != 0 || !headSurvival.equals("minecraft:iron_helmet")) {
				throw new AssertionError("O conjunto de ferro nao foi vestido pela bolsa (faltaram " + missingSurvival + ", cabeca " + headSurvival + ")");
			}
			click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("armor")), scale);
			ctx.waitTicks(5);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);

			// criativo: catalogo, minimapa, bau pessoal com abas, conjuntos de armadura
			server.runCommand("gamemode creative @a");
			ctx.waitTicks(10);
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(40);
			assertScreen(ctx, IsekaiCreativeScreen.class, "criativo isekai");
			ctx.takeScreenshot("isekai-58-criativo");
			Rect creativeMini = panelRect(ctx, "minimap");
			int creativeCols = ctx.computeOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugColumns());
			System.out.println("[isekai-test] criativo: minimapa=" + creativeMini + " colunas=" + creativeCols);
			if (creativeMini == null) throw new AssertionError("O criativo nao tem minimapa");
			ctx.runOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugSearch("diamond"));
			ctx.waitTicks(5);
			ctx.takeScreenshot("isekai-59-criativo-busca");
			double[] swordSlot = ctx.computeOnClient(mc -> slotCenter(mc, 23));
			ctx.getInput().setCursorPos(swordSlot[0], swordSlot[1]);
			ctx.waitTicks(2);
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> {
				IsekaiCreativeScreen cs = (IsekaiCreativeScreen) mc.screen;
				cs.debugStoreCarried(0);
				cs.debugTakeFromCatalog(0, true);
				cs.debugStoreCarried(1);
				cs.debugNewTab("Armas");
				for (int i = 0; i < 6; i++) {
					cs.debugTakeFromCatalog(4 + i, false);
					cs.debugStoreCarried(i);
				}
				cs.debugNewTab("Blocos");
				cs.debugTakeFromCatalog(0, true);
				cs.debugStoreCarried(0);
				cs.debugSelectTab(1);
			});
			ctx.getInput().setCursorPos(10, 10);
			ctx.waitTicks(5);
			ctx.takeScreenshot("isekai-60-bau-pessoal-abas");
			int tabCount = ctx.computeOnClient(mc -> PersonalChest.tabCount());
			String tab1 = ctx.computeOnClient(mc -> PersonalChest.tabName(1));
			boolean stored = ctx.computeOnClient(mc -> PersonalChest.get(0, 0).has(DataComponents.CUSTOM_NAME));
			System.out.println("[isekai-test] bau pessoal: abas=" + tabCount + " aba1=" + tab1 + " personalizado=" + stored);
			if (!stored) throw new AssertionError("O item personalizado nao foi guardado no bau pessoal");
			if (tabCount != 3 || !"Armas".equals(tab1)) throw new AssertionError("As abas do bau pessoal nao foram criadas: " + tabCount + " / " + tab1);
			ctx.runOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugOpenTabEditor(1));
			ctx.waitTicks(10);
			ctx.takeScreenshot("isekai-60b-editar-aba");
			ctx.runOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugCloseTabEditor());
			ctx.runOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugSearch("lamina"));
			ctx.waitTicks(5);
			ctx.takeScreenshot("isekai-60c-busca-no-bau");
			int found = ctx.computeOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugCellCount());
			if (found != 1) throw new AssertionError("A busca no bau pessoal devia achar 1 item, achou " + found);
			ctx.runOnClient(mc -> {
				IsekaiCreativeScreen cs = (IsekaiCreativeScreen) mc.screen;
				cs.debugSearch("");
				cs.debugSelectTab(0);
				cs.debugTakeCopy(0);
			});
			ctx.waitTicks(10);
			int copiesOnServer = server.computeOnServer(s -> {
				int n = 0;
				for (net.minecraft.world.item.ItemStack st : s.getPlayerList().getPlayers().get(0).getInventory().items) {
					if (st.has(DataComponents.CUSTOM_NAME)) n++;
				}
				return n;
			});
			System.out.println("[isekai-test] copias do item personalizado no servidor: " + copiesOnServer);
			if (copiesOnServer < 1) throw new AssertionError("A copia do bau pessoal nao chegou ao servidor");
			ctx.takeScreenshot("isekai-61-copia-no-inventario");
			// conjuntos no criativo: veste o de diamante como copia
			int missingCreative = ctx.computeOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugEquipSet(1));
			ctx.waitTicks(15);
			ctx.takeScreenshot("isekai-61b-criativo-conjuntos");
			String headCreative = wornOnServer(server, EquipmentSlot.HEAD);
			System.out.println("[isekai-test] criativo: cabeca=" + headCreative);
			if (missingCreative != 0 || !headCreative.equals("minecraft:diamond_helmet")) {
				throw new AssertionError("O conjunto de diamante nao foi vestido no criativo (cabeca " + headCreative + ")");
			}
			ctx.runOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugSetPersonal(false));
			// minimapa do criativo abre o mapa grande, e a tecla do inventario volta para o criativo
			click(ctx, panelRect(ctx, "minimap"), scale);
			ctx.waitTicks(30);
			assertScreen(ctx, IsekaiMapScreen.class, "mapa pelo minimapa do criativo");
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(40);
			assertScreen(ctx, IsekaiCreativeScreen.class, "de volta ao criativo");
			ctx.takeScreenshot("isekai-61c-de-volta-ao-criativo");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);
			server.runCommand("gamemode survival @a");
			ctx.waitTicks(10);

			// bussola e relogio: com bussola na bolsa o minimapa aparece no jogo; relogio dentro de uma sacola vale
			boolean before04 = ctx.computeOnClient(mc -> Instruments.hasCompass() || Instruments.hasClock());
			if (before04) throw new AssertionError("Sem bussola/relogio o jogador nao devia ter os instrumentos");
			server.runCommand("item replace entity @a container.31 with minecraft:compass");
			server.runCommand("item replace entity @a container.32 with minecraft:bundle[minecraft:bundle_contents=[{id:\"minecraft:clock\",count:1}]]");
			ctx.runOnClient(mc -> {
				mc.player.setYRot(-90f);
				mc.player.setXRot(0f);
				mc.player.yRotO = -90f;
				mc.player.xRotO = 0f;
			});
			ctx.waitTicks(15);
			ctx.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
			int armorValue = ctx.computeOnClient(mc -> mc.player.getArmorValue());
			System.out.println("[isekai-test] armadura no cliente depois do criativo: " + armorValue);
			boolean instruments = ctx.computeOnClient(mc -> Instruments.hasCompass() && Instruments.hasClock() && !Instruments.holdingCompass());
			if (!instruments) throw new AssertionError("A bussola na bolsa / relogio na sacola nao foram reconhecidos");
			ctx.takeScreenshot("isekai-66-hud-com-bussola-e-relogio");
			// segurando a bussola: marcadores aparecem no horizonte
			server.runCommand("item replace entity @a weapon.offhand with minecraft:compass");
			ctx.waitTicks(15);
			boolean holding = ctx.computeOnClient(mc -> Instruments.holdingCompass());
			if (!holding) throw new AssertionError("Segurar a bussola nao foi reconhecido");
			ctx.takeScreenshot("isekai-67-marcadores-no-horizonte");
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(40);
			ctx.takeScreenshot("isekai-68-inventario-com-relogio");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);

			// ================================================================== v0.5
			testV05(ctx, server, scale);

			// tela maior (1080p, escala 4)
			ctx.getInput().resizeWindow(1920, 1080);
			ctx.runOnClient(mc -> {
				mc.options.guiScale().set(4);
				mc.resizeDisplay();
			});
			ctx.waitTicks(5);
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(60);
			ctx.takeScreenshot("isekai-30-inventario-1080p");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> useBlock(mc, 2));
			ctx.waitTicks(40);
			ctx.takeScreenshot("isekai-31-bau-1080p");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);
			// grimorio ao lado da mesa de trabalho (em 1080p cabe ao lado)
			openStation(ctx, -2, 2, IsekaiCraftingScreen.class, "mesa de trabalho 1080p");
			ctx.runOnClient(mc -> ((IsekaiCraftingScreen) mc.screen).debugGrimoire());
			ctx.waitTicks(20);
			ctx.takeScreenshot("isekai-32-grimorio-ao-lado-1080p");
			boolean beside = ctx.computeOnClient(mc -> {
				IsekaiCraftingScreen cs = (IsekaiCraftingScreen) mc.screen;
				Rect g = cs.debugPanelRect("grimoire");
				Rect st = cs.debugPanelRect("station");
				return g != null && st != null && g.right() <= st.x();
			});
			System.out.println("[isekai-test] grimorio ao lado da mesa em 1080p: " + beside);
			closeStation(ctx);

			// criativo: Shift + clique duas vezes na lixeira limpa a bolsa e a barra (armadura fica)
			server.runCommand("gamemode creative @a");
			ctx.waitTicks(10);
			ctx.getInput().pressKey(options -> options.keyInventory);
			ctx.waitTicks(40);
			assertScreen(ctx, IsekaiCreativeScreen.class, "criativo para a lixeira");
			int itemsBefore = itemsOnServer(server);
			ctx.runOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugTrash(true));
			ctx.waitTicks(5);
			ctx.takeScreenshot("isekai-90-lixeira-armada");
			int itemsArmed = itemsOnServer(server);
			if (itemsArmed != itemsBefore) throw new AssertionError("O primeiro Shift+clique na lixeira ja apagou itens");
			ctx.runOnClient(mc -> ((IsekaiCreativeScreen) mc.screen).debugTrash(true));
			ctx.waitTicks(15);
			ctx.takeScreenshot("isekai-91-criativo-limpo");
			int itemsAfter = itemsOnServer(server);
			String headAfter = wornOnServer(server, EquipmentSlot.HEAD);
			System.out.println("[isekai-test] lixeira: antes=" + itemsBefore + " depois=" + itemsAfter + " cabeca=" + headAfter);
			if (itemsBefore == 0 || itemsAfter != 0) throw new AssertionError("A lixeira nao limpou a bolsa e a barra (" + itemsBefore + " -> " + itemsAfter + ")");
			if (headAfter.equals("minecraft:air")) throw new AssertionError("A lixeira nao devia tirar a armadura");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(20);
		}
	}

	/** Pesquisa: quais caracteres a fonte do jogo desenha sem nada (para esconder dados no chat e nos livros). */
	private static void fontScan(ClientGameTestContext ctx) {
		java.util.List<Integer> thin = new java.util.ArrayList<>();
		ctx.runOnClient(mc -> {
			net.minecraft.client.gui.Font f = mc.font;
			StringBuilder w0 = new StringBuilder();
			StringBuilder w1 = new StringBuilder();
			StringBuilder w2 = new StringBuilder();
			int[][] ranges = {{0x20, 0xFFFF}, {0x10000, 0x1FFFF}, {0xE0000, 0xE01EF}};
			for (int[] r : ranges) {
				for (int cp = r[0]; cp <= r[1]; cp++) {
					if (cp >= 0xD800 && cp <= 0xDFFF) continue;
					if (cp == 0xA7 || cp == 0x7F) continue;
					int w = f.width(new String(Character.toChars(cp)));
					if (w == 0) w0.append(Integer.toHexString(cp)).append(' ');
					else if (w == 1) {
						w1.append(Integer.toHexString(cp)).append(' ');
						thin.add(cp);
					} else if (w == 2 && cp < 0x3000) w2.append(Integer.toHexString(cp)).append(' ');
				}
			}
			System.out.println("[isekai-font] largura0: " + w0);
			System.out.println("[isekai-font] largura1 (" + thin.size() + "): " + w1);
			System.out.println("[isekai-font] largura2 (<3000): " + w2);
			for (String path : new String[]{"font/include/space.json", "font/default.json", "font/uniform.json", "font/include/default.json"}) {
				mc.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.withDefaultNamespace(path)).ifPresent(res -> {
					try (java.io.InputStream in = res.open()) {
						String txt = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ");
						System.out.println("[isekai-font] " + path + ": " + (txt.length() > 3000 ? txt.substring(0, 3000) + "..." : txt));
					} catch (java.io.IOException e) {
						System.out.println("[isekai-font] " + path + ": erro " + e);
					}
				});
			}
		});
		// mostra os de largura 1 bem grandes, entre barras, para ver quais sao vazios
		for (int page = 0; page * 108 < Math.min(thin.size(), 324); page++) {
			final int first = page * 108;
			ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.Screen(Component.literal("fonte")) {
				@Override
				public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
					g.fill(0, 0, this.width, this.height, 0xFF202020);
					for (int i = first; i < Math.min(thin.size(), first + 108); i++) {
						int k = i - first;
						int x = 4 + (k % 12) * 35;
						int y = 4 + (k / 12) * 26;
						g.fill(x, y, x + 32, y + 24, 0xFF404040);
						g.pose().pushPose();
						g.pose().translate(x + 2, y + 1, 0);
						g.pose().scale(2f, 2f, 1f);
						g.drawString(this.font, "[" + new String(Character.toChars(thin.get(i))) + "]", 0, 0, 0xFFFFFFFF, false);
						g.pose().popPose();
						g.drawString(this.font, Integer.toHexString(thin.get(i)), x + 1, y + 16, 0xFFFFE070, false);
					}
				}
			}));
			ctx.waitTicks(3);
			ctx.takeScreenshot("isekai-00-fonte-" + page);
		}
		ctx.runOnClient(mc -> mc.setScreen(null));
	}

	/** Quantos itens o jogador tem na bolsa e na barra, segundo o servidor. */
	private static int itemsOnServer(TestServerContext server) {
		return server.computeOnServer(s -> {
			int n = 0;
			for (net.minecraft.world.item.ItemStack st : s.getPlayerList().getPlayers().get(0).getInventory().items) if (!st.isEmpty()) n++;
			return n;
		});
	}

	/** v0.5: conjuntos crescendo na hora, grimorio, barra compacta, configuracoes e minimapa movel. */
	private static void testV05(ClientGameTestContext ctx, TestServerContext server, double scale) {
		// conjunto novo: a janela cresce na hora (antes so crescia depois de fechar e abrir)
		ctx.getInput().pressKey(options -> options.keyInventory);
		ctx.waitTicks(40);
		assertScreen(ctx, IsekaiInventoryScreen.class, "inventario v0.5");
		click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("armor")), scale);
		ctx.waitTicks(15);
		int armorBefore = panelRect(ctx, "armor_sets").h();
		ctx.runOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugSaveArmorSet());
		ctx.waitTicks(10);
		int armorAfter = panelRect(ctx, "armor_sets").h();
		ctx.takeScreenshot("isekai-70-conjunto-novo-janela-cresceu");
		System.out.println("[isekai-test] janela de conjuntos: " + armorBefore + " -> " + armorAfter);
		if (armorAfter <= armorBefore) throw new AssertionError("A janela de conjuntos nao cresceu ao salvar um conjunto (" + armorBefore + " -> " + armorAfter + ")");
		click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("armor")), scale);
		ctx.waitTicks(5);

		// grimorio: o que ja foi aprendido sozinho ate aqui (so com resultados de verdade)
		String learnedSoFar = ctx.computeOnClient(mc -> {
			StringBuilder sb = new StringBuilder();
			for (RecipeJournal.Entry e : RecipeJournal.all()) sb.append(e.station).append(':').append(BuiltInRegistries.ITEM.getKey(e.result.getItem())).append('x').append(e.result.getCount()).append(' ');
			return sb.toString();
		});
		System.out.println("[isekai-test] grimorio antes do teste: " + learnedSoFar);
		// do zero: 1 tronco na grade 2x2 -> o servidor mostra 4 tabuas -> a receita e anotada
		ctx.runOnClient(mc -> RecipeJournal.debugForgetAll());
		ctx.runOnClient(mc -> {
			int id = mc.player.containerMenu.containerId;
			int logs = findSlot(mc, "minecraft:oak_log");
			mc.gameMode.handleInventoryMouseClick(id, logs, 0, ClickType.PICKUP, mc.player);
			mc.gameMode.handleInventoryMouseClick(id, 1, 1, ClickType.PICKUP, mc.player);
			mc.gameMode.handleInventoryMouseClick(id, logs, 0, ClickType.PICKUP, mc.player);
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("isekai-71-receita-anotada");
		String learned = ctx.computeOnClient(mc -> {
			if (RecipeJournal.size() != 1) return "receitas=" + RecipeJournal.size();
			RecipeJournal.Entry e = RecipeJournal.all().get(0);
			return e.station + ":" + BuiltInRegistries.ITEM.getKey(e.result.getItem()) + "x" + e.result.getCount() + " " + e.w + "x" + e.h;
		});
		System.out.println("[isekai-test] receita anotada: " + learned);
		if (!learned.equals("CRAFTING:minecraft:oak_planksx4 1x1")) throw new AssertionError("A receita das tabuas nao foi anotada: " + learned);
		// tira o tronco da grade e coloca de novo pelo grimorio
		quickMove(ctx, 1);
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugGrimoire());
		ctx.waitTicks(20);
		ctx.takeScreenshot("isekai-72-grimorio");
		String placed = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugGrimoire().debugPlace(0).getString());
		ctx.waitTicks(20);
		String gridNow = ctx.computeOnClient(mc -> BuiltInRegistries.ITEM.getKey(mc.player.containerMenu.getSlot(1).getItem().getItem())
				+ " -> " + BuiltInRegistries.ITEM.getKey(mc.player.containerMenu.getSlot(0).getItem().getItem()));
		System.out.println("[isekai-test] grimorio colocou (" + placed + "): " + gridNow);
		ctx.takeScreenshot("isekai-73-grimorio-colocou");
		if (!gridNow.contains("oak_planks")) throw new AssertionError("O grimorio nao colocou a receita na grade: " + gridNow);
		// receitas do servidor sem testar (com "?")
		ctx.runOnClient(mc -> IsekaiConfig.get().recipes.showUntested = true);
		ctx.waitTicks(10);
		ctx.takeScreenshot("isekai-74-grimorio-receitas-do-servidor");
		int shownWithServer = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugGrimoire().debugViewCount());
		System.out.println("[isekai-test] grimorio com receitas do servidor: " + shownWithServer);
		ctx.runOnClient(mc -> IsekaiConfig.get().recipes.showUntested = false);
		// fecha o grimorio pelo botao da barra e sai
		click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("grimoire")), scale);
		ctx.waitTicks(5);
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(25);

		// mesa de trabalho: grimorio por cima da bolsa (tela pequena) e colocando item por item (cliques com calma)
		openStation(ctx, -2, 2, IsekaiCraftingScreen.class, "mesa de trabalho com grimorio");
		ctx.runOnClient(mc -> {
			RecipePlacer.debugManualOnly = true;
			((IsekaiCraftingScreen) mc.screen).debugGrimoire();
		});
		ctx.waitTicks(20);
		String manual = ctx.computeOnClient(mc -> ((IsekaiCraftingScreen) mc.screen).debugGrimoire().debugPlace(0).getString());
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> RecipePlacer.debugManualOnly = false);
		String tableNow = ctx.computeOnClient(mc -> BuiltInRegistries.ITEM.getKey(mc.player.containerMenu.getSlot(1).getItem().getItem())
				+ " -> " + BuiltInRegistries.ITEM.getKey(mc.player.containerMenu.getSlot(0).getItem().getItem()));
		System.out.println("[isekai-test] mesa, colocando um por um (" + manual + "): " + tableNow);
		ctx.takeScreenshot("isekai-75-mesa-com-grimorio");
		if (!tableNow.contains("oak_planks")) throw new AssertionError("Os cliques do grimorio nao montaram a receita na mesa: " + tableNow);
		closeStation(ctx);
		// fornalha e suporte de pocoes tambem tem o grimorio
		openStation(ctx, -1, 2, IsekaiFurnaceScreen.class, "fornalha com grimorio");
		ctx.waitTicks(10);
		ctx.takeScreenshot("isekai-76-fornalha-com-grimorio");
		closeStation(ctx);

		// barra compacta: com limite de 4, os outros botoes vao para a gaveta "..."
		ctx.runOnClient(mc -> IsekaiConfig.get().bar.maxVisible = 4);
		ctx.getInput().pressKey(options -> options.keyInventory);
		ctx.waitTicks(40);
		assertScreen(ctx, IsekaiInventoryScreen.class, "inventario com barra compacta");
		ctx.takeScreenshot("isekai-77-barra-compacta");
		boolean armorInDrawer = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugInDrawer("armor"));
		boolean gearOnBar = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("settings") != null);
		if (!armorInDrawer || !gearOnBar) throw new AssertionError("Barra compacta errada: armadura na gaveta=" + armorInDrawer + " engrenagem na barra=" + gearOnBar);
		click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("more")), scale);
		ctx.waitTicks(12);
		ctx.takeScreenshot("isekai-78-gaveta-aberta");
		click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("armor")), scale);
		ctx.waitTicks(15);
		boolean armorOpened = ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugPanelVisible("armor_sets")
				&& !((IsekaiInventoryScreen) mc.screen).debugPanelVisible("bar_drawer"));
		ctx.takeScreenshot("isekai-79-botao-da-gaveta");
		if (!armorOpened) throw new AssertionError("O botao da gaveta nao abriu os conjuntos");
		click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("more")), scale);
		ctx.waitTicks(5);
		click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("armor")), scale);
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> IsekaiConfig.get().bar.maxVisible = 8);

		// engrenagem: configuracoes do mod
		click(ctx, ctx.computeOnClient(mc -> ((IsekaiInventoryScreen) mc.screen).debugButtonRect("settings")), scale);
		ctx.waitTicks(15);
		assertScreen(ctx, SettingsScreen.class, "configuracoes pela engrenagem");
		ctx.takeScreenshot("isekai-80-configuracoes");
		ctx.runOnClient(mc -> ((SettingsScreen) mc.screen).debugSelect(1));
		ctx.waitTicks(5);
		ctx.takeScreenshot("isekai-81-configuracoes-sons");
		ctx.runOnClient(mc -> ((SettingsScreen) mc.screen).debugSelect(4));
		ctx.waitTicks(5);
		ctx.takeScreenshot("isekai-82-configuracoes-barra");
		ctx.runOnClient(mc -> ((SettingsScreen) mc.screen).debugSelect(5));
		ctx.waitTicks(5);
		ctx.takeScreenshot("isekai-83-configuracoes-grimorio");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(40);
		assertScreen(ctx, IsekaiInventoryScreen.class, "de volta das configuracoes");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(25);

		// minimapa da tela (com bussola) em outro lugar, e a tela de arrastar
		ctx.runOnClient(mc -> {
			IsekaiConfig.InstrumentSettings c = IsekaiConfig.get().instruments;
			c.hudX = 0.86f;
			c.hudY = 0.62f;
			c.hudSize = 96;
		});
		ctx.waitTicks(10);
		ctx.takeScreenshot("isekai-84-minimapa-movido");
		ctx.runOnClient(mc -> mc.setScreen(new HudLayoutScreen(null)));
		ctx.waitTicks(10);
		ctx.takeScreenshot("isekai-85-arrastar-minimapa");
		Rect hud = ctx.computeOnClient(mc -> com.compelium.isekaiui.client.HudOverlay.minimapRect(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()));
		ctx.getInput().setCursorPos(hud.cx() * scale, hud.cy() * scale);
		ctx.waitTicks(2);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(1);
		for (int i = 0; i < 4; i++) {
			ctx.getInput().moveCursor(-40 * scale, -15 * scale);
			ctx.waitTicks(1);
		}
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		Rect hudMoved = ctx.computeOnClient(mc -> com.compelium.isekaiui.client.HudOverlay.minimapRect(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()));
		ctx.takeScreenshot("isekai-86-minimapa-arrastado");
		System.out.println("[isekai-test] minimapa: " + hud + " -> " + hudMoved);
		if (hudMoved.x() >= hud.x()) throw new AssertionError("Arrastar o minimapa nao funcionou: " + hud + " -> " + hudMoved);
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> {
			IsekaiConfig.InstrumentSettings c = IsekaiConfig.get().instruments;
			c.hudX = -1f;
			c.hudY = -1f;
			c.hudSize = 84;
		});
	}

	/** Slot do menu aberto (bolsa/barra) com o item, ou -1. */
	private static int findSlot(Minecraft mc, String itemId) {
		var menu = mc.player.containerMenu;
		for (Slot s : menu.slots) {
			if (s.container == mc.player.getInventory() && s.getContainerSlot() < 36 && s.hasItem()
					&& BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).toString().equals(itemId)) return s.index;
		}
		return -1;
	}

	/** Id do item que o jogador veste no servidor naquele lugar do corpo. */
	private static String wornOnServer(TestServerContext server, EquipmentSlot slot) {
		return server.computeOnServer(s -> BuiltInRegistries.ITEM.getKey(s.getPlayerList().getPlayers().get(0).getItemBySlot(slot).getItem()).toString());
	}

	private static void assertScreen(ClientGameTestContext ctx, Class<?> type, String what) {
		boolean ok = ctx.computeOnClient(mc -> type.isInstance(mc.screen));
		if (!ok) throw new AssertionError("Esperava " + type.getSimpleName() + " (" + what + "), mas a tela e " + ctx.computeOnClient(mc -> String.valueOf(mc.screen)));
	}

	/** Abre a estacao no bloco (dx, dz) relativo ao jogador e confere a tela isekai certa. */
	private static void openStation(ClientGameTestContext ctx, int dx, int dz, Class<?> type, String what) {
		ctx.runOnClient(mc -> {
			BlockPos pos = BlockPos.containing(mc.player.getX() + dx, mc.player.getY(), mc.player.getZ() + dz);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
		});
		ctx.waitTicks(30);
		assertScreen(ctx, type, what);
	}

	private static void closeStation(ClientGameTestContext ctx) {
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(20);
	}

	/** Shift+clique num slot do menu aberto. */
	private static void quickMove(ClientGameTestContext ctx, int slot) {
		ctx.runOnClient(mc -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player));
		ctx.waitTicks(3);
	}

	/** Clique direito (usar) no bloco a "dx" blocos do jogador no eixo X. */
	private static void useBlock(Minecraft mc, int dx) {
		BlockPos pos = BlockPos.containing(mc.player.getX() + dx, mc.player.getY(), mc.player.getZ());
		Direction face = dx > 0 ? Direction.WEST : Direction.EAST;
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
	}

	/** Centro de um slot da tela aberta, em pixels da janela. */
	private static double[] slotCenter(Minecraft mc, int index) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
		Slot slot = screen.getMenu().slots.get(index);
		double sc = mc.getWindow().getGuiScale();
		return new double[]{(slot.x + 8) * sc, (slot.y + 8) * sc};
	}

	private static void setGuiScale(ClientGameTestContext ctx, int guiScale) {
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(guiScale);
			mc.resizeDisplay();
		});
		ctx.waitTicks(5);
	}

	private static Rect panelRect(ClientGameTestContext ctx, String id) {
		return ctx.computeOnClient(mc -> ((com.compelium.isekaiui.gui.IsekaiMenuScreen<?>) mc.screen).debugPanelRect(id));
	}

	/** Segura a aba do titulo, arrasta em 4 passos (foto no meio) e solta. */
	private static void dragPanel(ClientGameTestContext ctx, Rect panel, int dx, int dy, double scale, String midShot) {
		ctx.getInput().setCursorPos((panel.x() + 14) * scale, (panel.y() - 5) * scale);
		ctx.waitTicks(2);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(1);
		for (int i = 0; i < 4; i++) {
			ctx.getInput().moveCursor(dx / 4.0 * scale, dy / 4.0 * scale);
			ctx.waitTicks(1);
		}
		ctx.takeScreenshot(midShot);
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
	}

	private static void doubleClickTab(ClientGameTestContext ctx, Rect panel, double scale) {
		ctx.getInput().setCursorPos((panel.x() + 14) * scale, (panel.y() - 5) * scale);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
	}

	private static boolean intersects(Rect a, Rect b) {
		return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
	}

	private static void click(ClientGameTestContext ctx, Rect r, double scale) {
		if (r == null) throw new AssertionError("Botao nao encontrado");
		ctx.getInput().setCursorPos(r.cx() * scale, r.cy() * scale);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	/** Monta um pequeno cenario (morro em degraus, lago, vila, arvores) para o mapa ter o que mostrar. */
	private static void buildScene(TestServerContext server) {
		String[] cmds = {
				// morro em degraus (testa as curvas de nivel)
				"fill ~12 ~-1 ~-14 ~40 ~2 ~14 minecraft:stone",
				"fill ~16 ~3 ~-10 ~36 ~6 ~10 minecraft:stone",
				"fill ~20 ~7 ~-6 ~32 ~10 ~6 minecraft:andesite",
				"fill ~23 ~11 ~-3 ~29 ~14 ~3 minecraft:stone",
				"fill ~23 ~15 ~-3 ~29 ~15 ~3 minecraft:snow_block",
				"fill ~12 ~3 ~-14 ~40 ~3 ~14 minecraft:grass_block replace minecraft:air",
				// lago com margem de areia
				"fill ~-34 ~-1 ~-16 ~-10 ~-1 ~16 minecraft:sand",
				"fill ~-31 ~-3 ~-13 ~-13 ~-1 ~13 minecraft:water",
				"fill ~-27 ~-3 ~-8 ~-17 ~-3 ~8 minecraft:air",
				"fill ~-27 ~-3 ~-8 ~-17 ~-3 ~8 minecraft:water",
				// estrada e vila ao sul
				"fill ~-1 ~-1 ~5 ~1 ~-1 ~44 minecraft:dirt_path",
				"fill ~-9 ~ ~14 ~-3 ~3 ~20 minecraft:oak_planks hollow",
				"fill ~-10 ~4 ~13 ~-2 ~4 ~21 minecraft:red_terracotta",
				"fill ~3 ~ ~18 ~9 ~3 ~24 minecraft:cobblestone hollow",
				"fill ~2 ~4 ~17 ~10 ~4 ~25 minecraft:dark_oak_planks",
				"fill ~-9 ~ ~28 ~-3 ~3 ~35 minecraft:stone_bricks hollow",
				"fill ~-10 ~4 ~27 ~-2 ~4 ~36 minecraft:blue_terracotta",
				"fill ~3 ~ ~32 ~10 ~2 ~38 minecraft:spruce_planks hollow",
				"fill ~2 ~3 ~31 ~11 ~3 ~39 minecraft:spruce_slab",
				"fill ~-2 ~-1 ~24 ~2 ~-1 ~28 minecraft:cobblestone",
				// arvores ao norte
				"fill ~-6 ~ ~-22 ~-6 ~4 ~-22 minecraft:oak_log",
				"fill ~-8 ~3 ~-24 ~-4 ~6 ~-20 minecraft:oak_leaves replace minecraft:air",
				"fill ~2 ~ ~-28 ~2 ~5 ~-28 minecraft:oak_log",
				"fill ~0 ~4 ~-30 ~4 ~7 ~-26 minecraft:oak_leaves replace minecraft:air",
				"fill ~-14 ~ ~-30 ~-14 ~4 ~-30 minecraft:birch_log",
				"fill ~-16 ~3 ~-32 ~-12 ~6 ~-28 minecraft:birch_leaves replace minecraft:air",
				"fill ~8 ~ ~-36 ~8 ~4 ~-36 minecraft:spruce_log",
				"fill ~6 ~3 ~-38 ~10 ~7 ~-34 minecraft:spruce_leaves replace minecraft:air",
		};
		for (String c : cmds) {
			server.runCommand("execute at @p run " + c);
		}
	}

	private static double[] miniCenter(Minecraft mc) {
		Rect mini = ((IsekaiInventoryScreen) mc.screen).debugPanelRect("minimap");
		double scale = mc.getWindow().getGuiScale();
		return new double[]{mini.cx() * scale, mini.cy() * scale};
	}
}
