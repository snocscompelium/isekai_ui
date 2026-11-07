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
	/** Mesa de trabalho, fornalhas, bigorna, ferraria, rebolo, cortador, alquimia e encantamento no estilo isekai. */
	public boolean enableStations = true;
	/** Inventario do modo criativo no estilo isekai (catalogo, bau pessoal e comandos anotados). */
	public boolean enableCreative = true;
	public InstrumentSettings instruments = new InstrumentSettings();
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
	/** Volume geral dos sons da interface (0 a 1). */
	public float uiSoundVolume = 0.7f;
	/** Volume do som de abrir/fechar janelas e o mapa (0 a 1), multiplicado pelo volume geral. */
	public float windowSoundVolume = 1.0f;
	/** Volume dos cliques, passar o mouse e botoes (0 a 1), multiplicado pelo volume geral. */
	public float clickSoundVolume = 1.0f;

	public BarSettings bar = new BarSettings();
	public RecipeSettings recipes = new RecipeSettings();

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
		/** Botao [+ Marcador] nas mensagens do chat com marcadores de outros mods de mapa. */
		public boolean chatWaypoints = true;
		/** Formato do "Compartilhar no chat": "xaero" (Xaero's Minimap) ou "journeymap" (JourneyMap/VoxelMap). */
		public String shareFormat = "xaero";
		/** Pixels de interface por bloco no minimapa. */
		public float minimapZoom = 1.0f;
		/** Pixels de interface por bloco ao abrir o mapa grande. */
		public float mapZoom = 2.0f;
	}

	public static final class LandsSettings {
		public String _info = "Le os titulos que o plugin Lands mostra ao entrar/sair de terrenos e marca os chunks no mapa. Ligue 'debug' para ver no chat cada titulo recebido e ajustar os padroes.";
		public boolean enabled = true;
		/** Ler os titulos do Lands sozinho. Desligado: os territorios sao marcados a mao no mapa. */
		public boolean autoDetect = false;
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

	public static final class InstrumentSettings {
		public String _info = "Relogio e bussola: sem relogio o minimapa mostra so o sol/lua, com relogio mostra a hora. A bussola libera as coordenadas e o minimapa durante o jogo; segurando a bussola os marcadores aparecem no horizonte. hudCorner: top_left, top_right, bottom_left ou bottom_right. hudX/hudY: posicao escolhida arrastando (0 a 1; -1 = usar o canto).";
		public boolean requireClock = true;
		public boolean requireCompass = true;
		public boolean hudMinimap = true;
		public String hudCorner = "top_left";
		public float hudX = -1f;
		public float hudY = -1f;
		public int hudSize = 84;
		public boolean horizonMarkers = true;
	}

	public static final class BarSettings {
		public String _info = "Barra de botoes do inventario: ordem, botoes escondidos e quantos cabem antes de ir para a gaveta (...). Ids: map, camera, commands, armor, grimoire, cpm, chest, settings, trash, classic, pin:<comando>, cfg:<numero>.";
		/** Quantos botoes ficam na barra; o resto vai para a gaveta "...". */
		public int maxVisible = 8;
		public List<String> order = new ArrayList<>();
		public List<String> hidden = new ArrayList<>();
	}

	public static final class RecipeSettings {
		/** Mostra tambem as receitas que o servidor manda e que ainda nao foram testadas (com "?"). */
		public boolean showUntested = false;
		/** Avisinho na tela quando uma receita nova e anotada. */
		public boolean notifyLearned = true;
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
		if (map.shareFormat == null || map.shareFormat.isBlank()) map.shareFormat = "xaero";
		if (instruments == null) instruments = new InstrumentSettings();
		if (instruments.hudCorner == null) instruments.hudCorner = "top_left";
		if (bar == null) bar = new BarSettings();
		if (bar.order == null) bar.order = new ArrayList<>();
		if (bar.hidden == null) bar.hidden = new ArrayList<>();
		bar.maxVisible = clamp(bar.maxVisible, 3, 14);
		if (recipes == null) recipes = new RecipeSettings();
		instruments.hudSize = clamp(instruments.hudSize, 48, 160);
		if (instruments.hudX > 1f) instruments.hudX = 1f;
		if (instruments.hudY > 1f) instruments.hudY = 1f;
		windowSoundVolume = clamp(windowSoundVolume, 0f, 1f);
		clickSoundVolume = clamp(clickSoundVolume, 0f, 1f);
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
