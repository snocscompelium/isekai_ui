# Compelium's Isekai UI

Mod **só do cliente** (Fabric) que transforma o inventário e o mapa em janelas de status no estilo isekai.
Não precisa instalar nada no servidor e quem não tiver o mod joga normalmente.

## O que faz

- **Inventário isekai**: ao abrir o inventário, a câmera vai para terceira pessoa e o personagem levanta as mãos
  como se mexesse numa janela flutuante. As janelas (equipamento, bolsa, atalhos em hexágono, síntese e minimapa)
  entram com animação e podem ser **arrastadas** pela aba do título (clique duplo na aba devolve ao lugar).
  **F5** dentro do inventário alterna para primeira pessoa. Todo slot desenhado é um slot real do inventário
  vanilla, então o servidor recebe os cliques normalmente.
- **Minimapa** ao lado do inventário (também no criativo). Clicar nele (ou apertar **M**) abre o mapa grande, que
  "desenrola" a partir do minimapa. **E** no mapa volta para o inventário.
- **Mapa de pergaminho**: relevo com curvas de nível, cores desbotadas e névoa nas áreas ainda não exploradas.
  Criaturas aparecem como pontos vermelhos. **Jogadores nunca aparecem no mapa.**
- **Marcadores e brasões de reino** no mapa, inclusive marcadores que outros jogadores mandam no chat com
  Xaero's Minimap, JourneyMap ou VoxelMap (botão **[+ Marcador]** na mensagem).
- **Territórios marcados à mão**: no mapa grande, o botão do pincel abre o painel de territórios. Escolha (ou crie)
  um reino e pinte os chunks clicando/arrastando; botão direito apaga. O nome do reino aparece no minimapa quando
  você entra nele. (A leitura automática dos títulos do Lands ainda existe, desligada: `lands.autoDetect`.)
- **Bússola e relógio de verdade**:
  - sem relógio, o medalhão do minimapa mostra só o sol (que vira lua à noite); com um **relógio** no inventário
    (ou dentro de uma shulker/sacola) ele mostra a hora;
  - com uma **bússola** você vê as coordenadas e o minimapa aparece no canto da tela mesmo com o inventário fechado;
  - **segurando** a bússola, os marcadores aparecem no horizonte, com nome e distância.
- **Conjuntos de armadura** (botão da camiseta na barra): salve a armadura vestida e troque de conjunto com um
  clique. No sobrevivência as peças vêm da bolsa (uma de cada vez, como uma pessoa faria); no criativo são cópias.
- **Baús, shulkers e estações** (bancada, fornalhas, bigorna, ferraria com provador, rebolo, cortador, poções,
  encantamento, funil, ejetor) no mesmo estilo. Menus de plugin com nome próprio continuam vanilla.
- **Criativo isekai**: catálogo com categorias e busca, e o **Baú pessoal** — cópias de itens (inclusive os
  personalizados do servidor) salvas no seu computador, separadas em **abas** com nome e ícone
  (botão direito numa aba edita; **+** cria outra; Shift+clique num item da bolsa guarda uma cópia).
- **Comandos rápidos**: comandos que abrem menus são aprendidos sozinhos; dá para anotar outros e fixar na barra.

## Instalação

1. Fabric Loader + Fabric API para Minecraft **1.21.4**.
2. Baixe o `.jar` em **Releases → Versão de teste** e coloque na pasta `mods`.

## Configuração

Arquivo `config/isekai_ui.json` (criado na primeira vez que o jogo abre):

- `thirdPersonCamera`, `characterScreenX`, `cameraDistance`: enquadramento da câmera.
- `map.exploreRadiusChunks`: raio revelado ao redor do jogador.
- `instruments`: `requireClock` / `requireCompass` (false = sempre mostrar hora / coordenadas),
  `hudMinimap`, `hudCorner` (`top_left`, `top_right`, `bottom_left`, `bottom_right`), `hudSize`, `horizonMarkers`.
- `lands`: `autoDetect` liga a leitura automática dos títulos do Lands; `debug` mostra no chat cada título recebido.
- `menuButtons`: atalhos de comando na barra hexagonal, por exemplo
  `{"label": "Habilidades", "command": "skills", "icon": "star"}`.

Os mapas, marcadores, reinos, o baú pessoal e os conjuntos de armadura ficam em `.minecraft/isekai_ui/maps/`
(uma pasta por servidor).

## Texturas

As texturas ficam em `src/main/resources/assets/isekai_ui/textures/gui/` e podem ser trocadas por resource pack.
`tools/gen_textures.py` gera a versão base.

## Compilação

Cada envio para o branch `main` é compilado pelo GitHub Actions, que também abre o jogo numa tela virtual,
tira prints da interface e publica tudo no branch `ci-output`.
