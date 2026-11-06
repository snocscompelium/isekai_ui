# Compelium's Isekai UI

Mod **só do cliente** (Fabric) que transforma o inventário e o mapa em janelas de status no estilo isekai.
Não precisa instalar nada no servidor e quem não tiver o mod joga normalmente.

## O que faz

- **Inventário isekai**: ao abrir o inventário, a câmera vai para terceira pessoa e o personagem levanta as mãos
  como se mexesse numa janela flutuante. As janelas (equipamento, bolsa, atalhos em hexágono, síntese e minimapa)
  entram com animação. **F5** dentro do inventário alterna para primeira pessoa.
  Todo slot desenhado é um slot real do inventário vanilla, então o servidor recebe os cliques normalmente.
- **Minimapa** ao lado do inventário. Clicar nele (ou apertar **M**) abre o mapa grande, que "desenrola" a partir
  do minimapa. **E** no mapa volta para o inventário.
- **Mapa de pergaminho**: relevo com curvas de nível, cores desbotadas e névoa nas áreas ainda não exploradas.
  Criaturas aparecem como pontos vermelhos. **Jogadores nunca aparecem no mapa.**
- **Terrenos do Lands**: o mod lê o título que o Lands mostra ao entrar num terreno e marca no mapa os chunks
  por onde o jogador passa dentro dele.
- O criativo continua com o inventário normal.

## Instalação

1. Fabric Loader + Fabric API para Minecraft **1.21.4**.
2. Baixe o `.jar` em **Releases → Versão de teste** e coloque na pasta `mods`.

## Configuração

Arquivo `config/isekai_ui.json` (criado na primeira vez que o jogo abre):

- `thirdPersonCamera`, `characterScreenX`, `cameraDistance`: enquadramento da câmera.
- `map.exploreRadiusChunks`: raio revelado ao redor do jogador.
- `lands`: padrões para reconhecer os títulos do Lands. Ligue `"debug": true` para ver no chat cada título
  recebido e ajustar `requiredColor`, `landPattern` e `wildernessPatterns`.
- `menuButtons`: atalhos de comando na barra hexagonal, por exemplo
  `{"label": "Habilidades", "command": "skills", "icon": "star"}`.

Os mapas ficam em `.minecraft/isekai_ui/maps/`.

## Texturas

As texturas ficam em `src/main/resources/assets/isekai_ui/textures/gui/` e podem ser trocadas por resource pack.
`tools/gen_textures.py` gera a versão base.

## Compilação

Cada envio para o branch `main` é compilado pelo GitHub Actions, que também abre o jogo numa tela virtual,
tira prints da interface e publica tudo no branch `ci-output`.
