#!/usr/bin/env bash
# Imprime assinaturas (javap) das classes listadas em ci/javap-classes.txt, usando o jar do Minecraft
# ja remapeado (nomes Mojang) que o Loom deixa no cache durante a compilacao.
set -u
MC=""
for j in $(find "$HOME/.gradle/caches" .gradle -name "*.jar" 2>/dev/null | grep -i minecraft | grep -v -- "-sources"); do
	if unzip -l "$j" 2>/dev/null | grep -q "net/minecraft/client/gui/GuiGraphics.class"; then
		MC="$j"
		break
	fi
done
echo "Minecraft jar: ${MC:-NAO ENCONTRADO}"
[ -z "$MC" ] && exit 0
while IFS= read -r cls; do
	case "$cls" in ''|\#*) continue ;; esac
	echo "=================================================================== $cls"
	javap -p -cp "$MC" "$cls" 2>&1 | head -500
done < ci/javap-classes.txt
