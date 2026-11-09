#!/usr/bin/env bash
# Envoie un jar sur CurseForge via l'API d'upload officielle.
# Usage : CF_TOKEN=... CF_PROJECT=123456 bash curseforge/upload.sh build/libs/fateflip-1.0.0.jar 1.0.0
set -euo pipefail

JAR="$1"
VERSION="$2"
API="https://minecraft.curseforge.com/api"
MC_VERSION="1.21.1"

[ -f "$JAR" ] || { echo "Jar introuvable : $JAR"; exit 1; }

# Les versions de jeu sont identifiées par des ID numériques : on les résout par leur nom.
curl -sSf -H "X-Api-Token: ${CF_TOKEN}" "$API/game/version-types" > /tmp/cf-types.json
curl -sSf -H "X-Api-Token: ${CF_TOKEN}" "$API/game/versions" > /tmp/cf-versions.json

type_id() { jq -r --arg s "$1" '.[] | select(.slug == $s) | .id' /tmp/cf-types.json | head -n1; }
version_id() { # $1 = id du type, $2 = nom exact
  jq -r --argjson t "$1" --arg n "$2" '.[] | select(.gameVersionTypeID == $t and .name == $n) | .id' /tmp/cf-versions.json | head -n1
}

MC_TYPE=$(type_id "minecraft-1-21")
LOADER_TYPE=$(type_id "modloader")
ENV_TYPE=$(type_id "environment")
JAVA_TYPE=$(type_id "java")

IDS=()
for pair in "$MC_TYPE:$MC_VERSION" "$LOADER_TYPE:Forge" "$ENV_TYPE:Client" "$JAVA_TYPE:Java 21"; do
  t="${pair%%:*}"; n="${pair#*:}"
  if [ -n "$t" ]; then
    id=$(version_id "$t" "$n")
    if [ -n "$id" ]; then IDS+=("$id"); echo "Version « $n » -> $id"; else echo "::warning::Version CurseForge « $n » introuvable"; fi
  fi
done
[ "${#IDS[@]}" -gt 0 ] || { echo "Aucune version de jeu résolue"; exit 1; }
GV=$(printf '%s\n' "${IDS[@]}" | jq -s '.')

# Notes de version : la section du CHANGELOG correspondant à cette version.
NOTES=$(awk -v v="## ${VERSION}" 'index($0,v)==1{f=1;next} /^## /{if(f)exit} f' CHANGELOG.md)
[ -n "$NOTES" ] || NOTES="Fate Flip ${VERSION}"

META=$(jq -n --arg cl "$NOTES" --arg name "Fate Flip ${VERSION}" --argjson gv "$GV" \
  '{changelog: $cl, changelogType: "markdown", displayName: $name, gameVersions: $gv, releaseType: "release"}')

HTTP=$(curl -sS -o /tmp/cf-response.json -w "%{http_code}" \
  -H "X-Api-Token: ${CF_TOKEN}" \
  -F "metadata=${META}" \
  -F "file=@${JAR}" \
  "$API/projects/${CF_PROJECT}/upload-file")
cat /tmp/cf-response.json; echo
if [ "$HTTP" != "200" ]; then echo "::error::Envoi CurseForge refusé (HTTP $HTTP)"; exit 1; fi
echo "::notice::Fate Flip ${VERSION} envoyé sur CurseForge (projet ${CF_PROJECT})."
