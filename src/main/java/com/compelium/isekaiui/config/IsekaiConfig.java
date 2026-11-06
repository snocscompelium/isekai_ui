package com.compelium.isekaiui.config;

import com.compelium.isekaiui.IsekaiUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuracao em config/isekai_ui.json. Campos que comecam com "_" sao so explicacoes.
 */
public final class IsekaiConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static IsekaiConfig instance = new IsekaiConfig();

	public String _info = "Compelium's Isekai UI. Apague este arquivo para voltar ao padrao.";

	/** Substitui o inventario vanilla (modo sobrevivencia/aventura) pelo inventario isekai. */
	public boolean enableInventory = true;
	/** Bau, bau grande, barril, bau do ender e shulker box no estilo isekai (so os com nome padrao do jogo). */
	public boolean enableContainers = true;
	/** Mostra os itens de dentro da shulker box ao passar o mouse sobre ela. */
	public boolean shulkerPreview = true;
	/** Camera em terceira pessoa ao abrir o inventario (F5 dentro do inventario alterna). */
	public boolean thirdPersonCamera = true;
	/** Personagem levanta os bracos "mexendo" na janela. */
	public boolean poseCharacter = true;
	/** Esconde o item da mao enquanto o personagem mexe na janela. */
	public boolean hideHeldItemsWhileBrowsing = true;
	/** Quanto a camera fica atras do personagem (blocos). */
	public float cameraDistance = 1.2f;
	/** Quanto a camera fica para a direita do personagem (blocos). Negativo = esquerda. */
	public float cameraSide = 3.0f;
	public float cameraHeight = -0.15f;
	/** Posicao horizontal do personagem na tela com a camera em 3a pessoa (0 = esquerda, 0.5 = centro). */
	public float characterScreenX = 0.24f;
	public boolean animations = true;
	public boolean uiSounds = true;
	public float uiSoundVolume = 0.7f;

	public MapSettings map = new MapSettings();
	public String _layoutsInfo = "Posicoes das janelas arrastadas (0 = esquerda/topo, 1 = direita/baixo). Apague para voltar ao padrao.";
	public Map<String, Map<String, float[]>> layouts = new HashMap<>();
	public LandsSettings lands = new LandsSettings();

	public String _menuButtonsInfo = "Atalhos de comando na barra hexagonal. Exemplo: {\"label\": \"Habilidades\", \"command\": \"skills\", \"icon\": \"star\"}. Icones: star, sword, paw, scroll, coin, house, book, gear, people, flag, compass, potion";
	public List<MenuButton> menuButtons = new ArrayList<>();

	public static final class MapSettings {
		/** Raio (em chunks) revelado ao redor do jogador. */
		public int exploreRadiusChunks = 5;
		public boolean showEntities = true;
		public boolean showClaims = true;
		public boolean showGrid = true;
		public boolean showWaypoints = true;
		/** Pixels de interface por bloco no minimapa. */
		public float minimapZoom = 1.0f;
		/** Pixels de interface por bloco ao abrir o mapa grande. */
		public float mapZoom = 2.0f;
	}

	public static final class LandsSettings {
		public String _info = "Le os titulos que o plugin Lands mostra ao entrar/sair de terrenos e marca os chunks no mapa. Ligue 'debug' para ver no chat cada titulo recebido e ajustar os padroes.";
		public boolean enabled = true;
		public boolean readTitle = true;
		public boolean readSubtitle = false;
		public boolean readActionBar = false;
		/** Cor que o titulo do Lands usa (nome como dark_green, gold... ou #RRGGBB). Vazio = qualquer cor. */
		public String requiredColor = "dark_green";
		/** Regex aplicada ao texto do titulo. O grupo "land" vira o nome do terreno. */
		public String landPattern = "^\\s*(?<land>.+?)\\s*$";
		/** Textos que significam "saiu para area sem dono". */
		public List<String> wildernessPatterns = new ArrayList<>(List.of(
				"(?i)^\\s*wilderness\\s*$",
				"(?i)^\\s*natureza\\s*$",
				"(?i)^\\s*(terras?\\s+)?selvage(m|ns)\\s*$",
				"(?i)^\\s*terra\\s+sem\\s+dono\\s*$",
				"(?i)^\\s*zona\\s+livre\\s*$"));
		public boolean debug = false;
	}

	public static final class MenuButton {
		public String label = "";
		public String command = "";
		public String icon = "star";
	}

	public static IsekaiConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("isekai_ui.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				IsekaiConfig c = GSON.fromJson(r, IsekaiConfig.class);
				if (c != null) instance = c;
			} catch (Exception e) {
				IsekaiUI.LOG.warn("Nao consegui ler config/isekai_ui.json, usando o padrao", e);
				instance = new IsekaiConfig();
			}
		}
		instance.sanitize();
		save();
	}

	public static void save() {
		try {
			Path p = path();
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			IsekaiUI.LOG.warn("Nao consegui salvar config/isekai_ui.json", e);
		}
	}

	private void sanitize() {
		if (map == null) map = new MapSettings();
		if (lands == null) lands = new LandsSettings();
		if (menuButtons == null) menuButtons = new ArrayList<>();
		if (layouts == null) layouts = new HashMap<>();
		if (lands.wildernessPatterns == null) lands.wildernessPatterns = new ArrayList<>();
		if (lands.landPattern == null || lands.landPattern.isBlank()) lands.landPattern = "^\\s*(?<land>.+?)\\s*$";
		if (lands.requiredColor == null) lands.requiredColor = "";
		map.exploreRadiusChunks = clamp(map.exploreRadiusChunks, 1, 16);
		map.minimapZoom = clamp(map.minimapZoom, 0.25f, 4f);
		map.mapZoom = clamp(map.mapZoom, 0.0625f, 6f);
		cameraDistance = clamp(cameraDistance, -4f, 6f);
		cameraSide = clamp(cameraSide, -6f, 6f);
		cameraHeight = clamp(cameraHeight, -1f, 2f);
		characterScreenX = clamp(characterScreenX, 0.05f, 0.95f);
		uiSoundVolume = clamp(uiSoundVolume, 0f, 1f);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static float clamp(float v, float min, float max) {
		if (Float.isNaN(v)) return min;
		return Math.max(min, Math.min(max, v));
	}
}
