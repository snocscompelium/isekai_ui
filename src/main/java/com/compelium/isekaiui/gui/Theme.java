package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.IsekaiUI;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Identidade visual: marfim e dourado sobre vidro escuro. */
public final class Theme {
	private Theme() {
	}

	// Cores (ARGB)
	public static final int TEXT = 0xFFF3ECD8;
	public static final int TEXT_DIM = 0xFFB9AE96;
	public static final int TEXT_GOLD = 0xFFE6CB82;
	public static final int TEXT_INK = 0xFF3A2F1E;
	public static final int IVORY = 0xFFEDE3C8;
	public static final int GOLD = 0xFFC9A35A;
	public static final int GLOW = 0xFFFFF2CF;
	public static final int SHADOW = 0x66000000;
	public static final int PARCHMENT = 0xFFD8C8A2;
	public static final int MAP_INK = 0xFF5E4E35;
	public static final int ENTITY_DOT = 0xFFE0465E;

	// Sprites (atlas de GUI: assets/isekai_ui/textures/gui/sprites/*.png)
	public static final ResourceLocation PANEL = IsekaiUI.id("panel");
	public static final ResourceLocation MAP_FRAME = IsekaiUI.id("map_frame");
	public static final ResourceLocation DIAL = IsekaiUI.id("dial");
	public static final ResourceLocation MAP_FRAME_GLOW = IsekaiUI.id("map_frame_glow");
	public static final ResourceLocation TAB = IsekaiUI.id("tab");
	public static final ResourceLocation SLOT = IsekaiUI.id("slot");
	public static final ResourceLocation SLOT_HEX = IsekaiUI.id("slot_hex");
	public static final ResourceLocation SLOT_HEX_SELECTED = IsekaiUI.id("slot_hex_selected");
	public static final ResourceLocation SLOT_RESULT = IsekaiUI.id("slot_result");
	public static final ResourceLocation SLOT_RESULT_GLOW = IsekaiUI.id("slot_result_glow");
	public static final ResourceLocation HEX_BUTTON = IsekaiUI.id("hex_button");
	public static final ResourceLocation HEX_BUTTON_HOVER = IsekaiUI.id("hex_button_hover");
	public static final ResourceLocation HEX_BUTTON_ON = IsekaiUI.id("hex_button_on");
	public static final ResourceLocation CRAFT_ARROW = IsekaiUI.id("craft_arrow");
	public static final ResourceLocation PROGRESS_ARROW = IsekaiUI.id("progress_arrow");
	public static final ResourceLocation PROGRESS_ARROW_FULL = IsekaiUI.id("progress_arrow_full");
	public static final ResourceLocation BACKPACK_BODY = IsekaiUI.id("backpack_body");
	public static final ResourceLocation BACKPACK_FLAP = IsekaiUI.id("backpack_flap");
	public static final ResourceLocation BACKPACK_HANDLE = IsekaiUI.id("backpack_handle");
	public static final ResourceLocation BACKPACK_BUCKLE = IsekaiUI.id("backpack_buckle");
	public static final ResourceLocation BACKPACK_POCKET = IsekaiUI.id("backpack_pocket");
	public static final ResourceLocation BACKPACK_STRAP = IsekaiUI.id("backpack_strap");
	public static final ResourceLocation BACKPACK_SIDE = IsekaiUI.id("backpack_side");
	public static final ResourceLocation PILL = IsekaiUI.id("pill");

	// Texturas soltas (assets/isekai_ui/textures/gui/plain/*.png)
	public static final ResourceLocation GLINT = IsekaiUI.id("textures/gui/plain/glint.png");
	public static final ResourceLocation MAP_ARROW = IsekaiUI.id("textures/gui/plain/map_arrow.png");
	public static final ResourceLocation MAP_DOT = IsekaiUI.id("textures/gui/plain/map_dot.png");

	public static final Set<String> ICONS = Set.of(
			"map", "camera_third", "camera_first", "inventory", "center", "entities", "claims", "grid", "close", "back",
			"star", "sword", "paw", "scroll", "coin", "house", "book", "gear", "people", "flag", "compass", "potion",
			"list", "pin", "gem", "skull", "tree", "pickaxe", "anchor", "portal", "tower",
			"craft", "chest", "flame", "trash", "search", "plus", "catalog", "chat", "next", "prev", "potion_rack",
			"sun", "moon", "armor", "brush");

	public static ResourceLocation icon(String name) {
		return IsekaiUI.id("icon/" + (ICONS.contains(name) ? name : "star"));
	}

	/** Cores "heraldicas" suaves para terrenos do Lands. */
	private static final int[] LAND_COLORS = {
			0xB8423C, 0x3E62A8, 0x2F8A5A, 0xC28E2F, 0x7D52A8, 0x2E8C8C, 0xB0507A, 0x7C8A30, 0xA8643A, 0x4C7A9E
	};

	public static int landColor(String name) {
		if (name == null) return 0x888888;
		Integer chosen = com.compelium.isekaiui.map.Kingdoms.color(name);
		if (chosen != null) return chosen;
		int h = name.toLowerCase(java.util.Locale.ROOT).hashCode();
		h ^= (h >>> 16);
		return LAND_COLORS[Math.floorMod(h, LAND_COLORS.length)];
	}
}
