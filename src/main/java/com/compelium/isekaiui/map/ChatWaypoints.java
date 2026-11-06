package com.compelium.isekaiui.map;

import com.compelium.isekaiui.config.IsekaiConfig;
import com.compelium.isekaiui.gui.IsekaiMapScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marcadores compartilhados no chat por outros mods de mapa. Quando uma mensagem tem um marcador no formato
 * do Xaero's Minimap, do JourneyMap/VoxelMap ou coordenadas escritas como "X: 10 Y: 64 Z: -20", aparece um
 * botao [+ Marcador] no fim da mensagem; clicar abre o editor de marcador ja preenchido.
 * O clique roda um comando local (/isekai_ui ...) que nunca e enviado ao servidor.
 */
public final class ChatWaypoints {
	public static final String COMMAND = "isekai_ui";

	/** Marcador encontrado no chat. y pode ser null (desconhecido); dimension null = a atual. */
	public record Shared(String name, int x, Integer y, int z, String dimension, int color, String source) {
	}

	private static final Pattern XAERO = Pattern.compile(
			"xaero-waypoint:([^:]*):([^:]*):(-?\\d+):(~|-?\\d+):(-?\\d+):(\\d+):(?:true|false):(?:-?\\d+)(?::(\\S*))?");
	private static final Pattern BRACKET = Pattern.compile(
			"\\[\\s*(?:name\\s*:\\s*\"?([^\",\\]]*)\"?\\s*,\\s*)?x\\s*:\\s*(-?\\d+)\\s*,\\s*y\\s*:\\s*(~|-?\\d+)\\s*,\\s*z\\s*:\\s*(-?\\d+)"
					+ "(?:\\s*,\\s*dim(?:ension)?\\s*:\\s*([a-z0-9_.\\-]+(?::[a-z0-9_./\\-]+)?))?[^\\]]*\\]",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern LABELED = Pattern.compile(
			"\\bx\\s*[:=]\\s*(-?\\d{1,8})[\\s,;/|]+y\\s*[:=]\\s*(-?\\d{1,4})[\\s,;/|]+z\\s*[:=]\\s*(-?\\d{1,8})",
			Pattern.CASE_INSENSITIVE);

	private static final Map<Integer, Shared> PENDING = new LinkedHashMap<>(64, 0.75f, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, Shared> eldest) {
			return size() > 200;
		}
	};
	private static int nextId = 1;

	private ChatWaypoints() {
	}

	// ------------------------------------------------------------------ leitura

	public static List<Shared> parse(String text) {
		List<Shared> out = new ArrayList<>();
		List<int[]> used = new ArrayList<>();
		Matcher m = XAERO.matcher(text);
		while (m.find()) {
			String name = unescapeXaero(m.group(1));
			Integer y = m.group(4).equals("~") ? null : Integer.parseInt(m.group(4));
			int color = nearestColor(chatColor(Integer.parseInt(m.group(6))));
			out.add(new Shared(name.isBlank() ? m.group(2) : name, Integer.parseInt(m.group(3)), y, Integer.parseInt(m.group(5)),
					xaeroDimension(m.group(7)), color, "Xaero's Minimap"));
			used.add(new int[]{m.start(), m.end()});
		}
		m = BRACKET.matcher(text);
		while (m.find()) {
			if (overlaps(used, m.start(), m.end())) continue;
			String name = m.group(1) == null ? "" : m.group(1).strip();
			Integer y = m.group(3).equals("~") ? null : Integer.parseInt(m.group(3));
			String dim = m.group(5) == null ? null : normalizeDimension(m.group(5));
			out.add(new Shared(name, Integer.parseInt(m.group(2)), y, Integer.parseInt(m.group(4)), dim, Waypoints.COLORS[4], "JourneyMap / VoxelMap"));
			used.add(new int[]{m.start(), m.end()});
		}
		m = LABELED.matcher(text);
		while (m.find()) {
			if (overlaps(used, m.start(), m.end())) continue;
			out.add(new Shared("", Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), null,
					Waypoints.COLORS[1], "chat"));
			used.add(new int[]{m.start(), m.end()});
		}
		return out;
	}

	private static boolean overlaps(List<int[]> used, int start, int end) {
		for (int[] u : used) if (start < u[1] && u[0] < end) return true;
		return false;
	}

	private static String unescapeXaero(String s) {
		return s.replace("^col^", ":").replace("§§", ":").strip();
	}

	private static String xaeroDimension(String container) {
		if (container == null || container.isBlank()) return null;
		String c = container.toLowerCase(Locale.ROOT);
		int dim = c.indexOf("dim%");
		if (dim >= 0) {
			String rest = c.substring(dim + 4);
			int end = rest.indexOf("-waypoints");
			if (end > 0) rest = rest.substring(0, end);
			return normalizeDimension(rest.replace('$', ':'));
		}
		if (c.contains("nether")) return "minecraft:the_nether";
		if (c.contains("end")) return "minecraft:the_end";
		if (c.contains("overworld")) return "minecraft:overworld";
		return null;
	}

	private static String normalizeDimension(String d) {
		String s = d.toLowerCase(Locale.ROOT).strip();
		if (s.equals("-1") || s.equals("nether") || s.equals("the_nether")) return "minecraft:the_nether";
		if (s.equals("1") || s.equals("end") || s.equals("the_end")) return "minecraft:the_end";
		if (s.equals("0") || s.equals("overworld")) return "minecraft:overworld";
		return s.contains(":") ? s : "minecraft:" + s;
	}

	private static int chatColor(int index) {
		ChatFormatting f = ChatFormatting.getById(index);
		Integer c = f == null ? null : f.getColor();
		return c == null ? Waypoints.COLORS[0] : c;
	}

	/** Cor mais parecida entre as 8 cores de marcador do mod. */
	public static int nearestColor(int rgb) {
		int best = Waypoints.COLORS[0];
		long bestD = Long.MAX_VALUE;
		for (int c : Waypoints.COLORS) {
			long dr = ((c >> 16) & 255) - ((rgb >> 16) & 255);
			long dg = ((c >> 8) & 255) - ((rgb >> 8) & 255);
			long db = (c & 255) - (rgb & 255);
			long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ botao no chat

	/** Acrescenta [+ Marcador] na mensagem se ela tiver um marcador. */
	public static Component decorate(Component message) {
		if (message == null || !IsekaiConfig.get().map.chatWaypoints) return message;
		String text = message.getString();
		if (text.length() < 8 || text.length() > 1200) return message;
		List<Shared> found = parse(text);
		if (found.isEmpty()) return message;
		MutableComponent out = message.copy();
		for (int i = 0; i < Math.min(3, found.size()); i++) {
			Shared s = found.get(i);
			int id = nextId++;
			PENDING.put(id, s);
			String label = s.name().isBlank() ? "X " + s.x() + " Z " + s.z() : s.name();
			Component hover = Component.translatable("isekai_ui.chatwp.hover", label, s.x(), s.y() == null ? "~" : s.y(), s.z(), s.source());
			MutableComponent button = Component.translatable("isekai_ui.chatwp.button").withStyle(style -> style
					.withColor(0xE6CB82)
					.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + " waypoint " + id))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
			out.append(Component.literal(" ")).append(button);
		}
		return out;
	}

	/** Comandos locais do mod (vindos do clique no chat). Devolve true para NAO enviar ao servidor. */
	public static boolean handleLocalCommand(String command) {
		if (command == null) return false;
		String c = command.strip();
		if (!c.equals(COMMAND) && !c.startsWith(COMMAND + " ")) return false;
		String[] parts = c.split("\\s+");
		if (parts.length >= 3 && parts[1].equals("waypoint")) {
			try {
				Shared s = PENDING.get(Integer.parseInt(parts[2]));
				if (s != null) Minecraft.getInstance().execute(() -> open(s));
			} catch (NumberFormatException ignored) {
			}
		}
		return true;
	}

	private static void open(Shared s) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		Waypoints.Waypoint w = new Waypoints.Waypoint();
		w.name = s.name().isBlank() ? Component.translatable("isekai_ui.map.waypoint_default").getString() : s.name();
		w.x = s.x();
		w.z = s.z();
		w.color = s.color();
		String current = MapManager.currentDimension();
		String dim = s.dimension() == null ? current : s.dimension();
		if (s.y() != null) {
			w.y = s.y();
		} else {
			DimMap map = MapManager.current();
			Integer surface = map == null || !dim.equals(current) ? null : map.surfaceY(s.x(), s.z());
			w.y = surface != null ? surface + 1 : mc.player.getBlockY();
		}
		if (dim.equals(current)) {
			mc.setScreen(IsekaiMapScreen.editShared(w));
		} else {
			MapManager.waypointsFor(dim).add(w);
			mc.gui.getChat().addMessage(Component.translatable("isekai_ui.chatwp.saved_other", w.name, dim).withStyle(ChatFormatting.GOLD));
		}
	}

	// ------------------------------------------------------------------ compartilhar os nossos

	/** Texto para colar no chat, no formato que outros mods de mapa entendem. */
	public static String shareText(Waypoints.Waypoint w, String dimension) {
		String name = w.name == null || w.name.isBlank() ? "Waypoint" : w.name.replace(":", " ").strip();
		if (IsekaiConfig.get().map.shareFormat.equalsIgnoreCase("journeymap")) {
			return "[name:\"" + name.replace("\"", "'") + "\", x:" + w.x + ", y:" + w.y + ", z:" + w.z + ", dim:" + dimension + "]";
		}
		String initial = name.substring(0, 1).toUpperCase(Locale.ROOT);
		String container;
		switch (dimension) {
			case "minecraft:overworld" -> container = "Internal-overworld-waypoints";
			case "minecraft:the_nether" -> container = "Internal-the-nether-waypoints";
			case "minecraft:the_end" -> container = "Internal-the-end-waypoints";
			default -> container = "Internal-dim%" + dimension.replace(':', '$') + "-waypoints";
		}
		return "xaero-waypoint:" + name + ":" + initial + ":" + w.x + ":" + w.y + ":" + w.z + ":" + xaeroColor(w.color) + ":false:0:" + container;
	}

	private static int xaeroColor(int rgb) {
		int best = 15;
		long bestD = Long.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			int c = chatColor(i);
			long dr = ((c >> 16) & 255) - ((rgb >> 16) & 255);
			long dg = ((c >> 8) & 255) - ((rgb >> 8) & 255);
			long db = (c & 255) - (rgb & 255);
			long d = dr * dr + dg * dg + db * db;
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}
}
