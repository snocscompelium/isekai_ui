package com.compelium.isekaiui.commands;

import com.compelium.isekaiui.IsekaiUI;
import com.compelium.isekaiui.map.MapManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.InteractionResult;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Comandos rapidos aprendidos: quando o jogador digita um comando (ex.: /pets) e logo em seguida
 * o servidor abre um menu, o comando vira um botao na lista de comandos rapidos daquele servidor.
 */
public final class CommandMemory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final long LEARN_WINDOW_NANOS = 4_000_000_000L;
	/** Se o jogador clicou num bloco/criatura logo antes, o menu foi aberto por ele (bau, aldeao...), nao pelo comando. */
	private static final long INTERACT_WINDOW_NANOS = 1_500_000_000L;
	private static final String[] ICON_CYCLE = {"star", "paw", "sword", "scroll", "coin", "house", "book", "gear", "people", "flag", "compass", "potion"};

	public static final class Entry {
		public String command = "";
		public String label = "";
		public String icon = "star";
		public boolean pinned;
		public int uses;
		public long lastUsed;
	}

	private static List<Entry> entries = new ArrayList<>();
	private static String loadedFor;
	private static String pendingCommand;
	private static long pendingNanos;
	private static long lastInteractNanos;

	private CommandMemory() {
	}

	public static void init() {
		ClientSendMessageEvents.COMMAND.register(CommandMemory::onCommandSent);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) lastInteractNanos = System.nanoTime();
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) lastInteractNanos = System.nanoTime();
			return InteractionResult.PASS;
		});
	}

	private static void onCommandSent(String command) {
		if (command == null || command.isBlank()) return;
		pendingCommand = command.strip();
		pendingNanos = System.nanoTime();
	}

	/** Chamado sempre que uma tela vai abrir. Se for um menu logo depois de um comando, aprende o comando. */
	public static void onScreenOpening(Screen screen) {
		if (pendingCommand == null || screen == null) return;
		if (System.nanoTime() - pendingNanos > LEARN_WINDOW_NANOS) {
			pendingCommand = null;
			return;
		}
		if (!(screen instanceof AbstractContainerScreen<?>)) return;
		if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return;
		if (screen.getClass().getName().startsWith("com.compelium.isekaiui")) return;
		if (lastInteractNanos != 0L && System.nanoTime() - lastInteractNanos < INTERACT_WINDOW_NANOS) return;
		String cmd = pendingCommand;
		pendingCommand = null;
		learn(cmd);
	}

	private static void learn(String command) {
		ensureLoaded();
		Entry e = find(command);
		if (e == null) {
			e = new Entry();
			e.command = command;
			e.label = prettify(command);
			e.icon = guessIcon(command);
			entries.add(e);
			IsekaiUI.LOG.info("Novo comando rapido aprendido: /{}", command);
		}
		e.uses++;
		e.lastUsed = System.currentTimeMillis();
		save();
	}

	public static List<Entry> entries() {
		ensureLoaded();
		List<Entry> list = new ArrayList<>(entries);
		list.sort(Comparator.<Entry, Boolean>comparing(e -> !e.pinned).thenComparing(e -> -e.uses).thenComparing(e -> e.label.toLowerCase(Locale.ROOT)));
		return list;
	}

	public static List<Entry> pinned() {
		List<Entry> list = new ArrayList<>();
		for (Entry e : entries()) if (e.pinned) list.add(e);
		return list;
	}

	public static void togglePin(Entry e) {
		e.pinned = !e.pinned;
		save();
	}

	public static void remove(Entry e) {
		ensureLoaded();
		entries.remove(e);
		save();
	}

	public static void cycleIcon(Entry e) {
		int i = 0;
		for (int k = 0; k < ICON_CYCLE.length; k++) if (ICON_CYCLE[k].equals(e.icon)) i = k;
		e.icon = ICON_CYCLE[(i + 1) % ICON_CYCLE.length];
		save();
	}

	public static void run(Entry e) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		e.uses++;
		e.lastUsed = System.currentTimeMillis();
		save();
		mc.player.connection.sendCommand(e.command);
	}

	/** Comando anotado a mao (lista de comandos rapidos, botao "+"). Ja entra fixado na barra se pedir. */
	public static Entry addManual(String text) {
		if (text == null) return null;
		String cmd = text.strip();
		while (cmd.startsWith("/")) cmd = cmd.substring(1).strip();
		if (cmd.isEmpty()) return null;
		ensureLoaded();
		Entry e = find(cmd);
		if (e == null) {
			e = new Entry();
			e.command = cmd;
			e.label = prettify(cmd);
			e.icon = guessIcon(cmd);
			entries.add(e);
		}
		e.lastUsed = System.currentTimeMillis();
		save();
		return e;
	}

	private static Entry find(String command) {
		for (Entry e : entries) if (e.command.equalsIgnoreCase(command)) return e;
		return null;
	}

	static String prettify(String command) {
		String c = command.startsWith("/") ? command.substring(1) : command;
		if (c.isEmpty()) return c;
		return Character.toUpperCase(c.charAt(0)) + c.substring(1);
	}

	static String guessIcon(String command) {
		String c = command.toLowerCase(Locale.ROOT);
		if (c.matches(".*(pet|mascote|montaria|mount).*")) return "paw";
		if (c.matches(".*(skill|habilidade|talent|talento|classe|class|stats|atributo).*")) return "star";
		if (c.matches(".*(shop|loja|market|mercado|leilao|auction|\\bah\\b|bank|banco|money|coins|moeda).*")) return "coin";
		if (c.matches(".*(home|casa|base).*")) return "house";
		if (c.matches(".*(warp|spawn|tp|viagem|travel|portal|rtp).*")) return "compass";
		if (c.matches(".*(quest|missao|missão|mission|diario|journal).*")) return "scroll";
		if (c.matches(".*(guild|guilda|clan|cla|clã|party|grupo|friends|amigos).*")) return "people";
		if (c.matches(".*(land|lands|terreno|reino|kingdom|nation|nacao|nação|claim).*")) return "flag";
		if (c.matches(".*(kit|potion|pocao|poção|alquimia).*")) return "potion";
		if (c.matches(".*(craft|forja|forge|blacksmith|ferreiro|config|settings|opcoes|opções).*")) return "gear";
		if (c.matches(".*(arena|pvp|duel|duelo|boss|dungeon|masmorra).*")) return "sword";
		if (c.matches(".*(menu|help|ajuda|wiki|guia|book|livro).*")) return "book";
		return "star";
	}

	// ------------------------------------------------------------------ arquivo por servidor

	private static void ensureLoaded() {
		String key = MapManager.serverKey(Minecraft.getInstance());
		if (key.equals(loadedFor)) return;
		loadedFor = key;
		entries = new ArrayList<>();
		Path file = file(key);
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				List<Entry> list = GSON.fromJson(r, new TypeToken<List<Entry>>() {
				}.getType());
				if (list != null) {
					for (Entry e : list) if (e != null && e.command != null && !e.command.isBlank()) entries.add(e);
				}
			} catch (Exception ex) {
				IsekaiUI.LOG.warn("Nao consegui ler {}", file, ex);
			}
		}
	}

	private static void save() {
		if (loadedFor == null) return;
		Path file = file(loadedFor);
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(entries, w);
			}
		} catch (Exception ex) {
			IsekaiUI.LOG.warn("Nao consegui salvar {}", file, ex);
		}
	}

	private static Path file(String serverKey) {
		return MapManager.serverDir(serverKey).resolve("commands.json");
	}
}
