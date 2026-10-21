#!/bin/bash
# Build assets/ from the Cobblemon commit pinned in COBBLEMON_COMMIT.
#   tools/build.sh                 -> assets/ (models, gamedata.json, datapack, ui-pack.zip, bedrock-ui.mcpack) + plugin jar
#   tools/build.sh --zip v1.0.0    -> also the two server bundles: dist/cobblemon-link-plugin-v1.0.0.zip (Paper) and
#                                     dist/cobblemon-link-v1.0.0.zip (Fabric; run fabric/tools/setup.sh first, it builds the mod)
#   tools/build.sh --zip-only v1.0.0  -> only the bundles, from what is already built
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${1:-}" != --zip-only ]; then  # --zip-only: package what is already built
sha=$(cat COBBLEMON_COMMIT)
src=$(mktemp -d); trap 'rm -rf "$src"' EXIT
tools/fetch_cobblemon.sh "$sha" "$src"
rm -rf assets/balls assets/sounds-pack.zip assets/sounds.mcpack assets/blocks assets/showdown assets/items.json assets/recipes.json assets/items-pack.zip assets/items.mcpack assets/geyser-items.json assets/java assets/bedrock assets/species.tsv assets/gamedata.json assets/datapack assets/ui-pack.zip assets/bedrock-ui.mcpack assets/geyser-mappings.json assets/portraits assets/cards assets/cards_full assets/tiles assets/bodies
python3 tools/build_all.py "$src" assets
python3 tools/build_data.py "$src" assets
python3 tools/build_structures.py "$src" assets  # Cobblemon's ruins, coves, boats, habitats into the datapack
# the battle engine: Cobblemon's own Showdown build and its bag item effects (sim/server.js loads them from here)
rm -rf assets/balls assets/sounds-pack.zip assets/sounds.mcpack assets/blocks assets/showdown && mkdir -p assets/showdown && unzip -q "$src/data/cobblemon/showdown.zip" -d assets/showdown
cp -r "$src/data/cobblemon/bag_items" assets/showdown/
rm -rf assets/lang && cp -r "$src/assets/cobblemon/lang" assets/lang  # Cobblemon's languages, for Lang.java
python3 tools/build_items.py "$src" assets
python3 tools/build_blocks.py "$src" assets  # Cobblemon's machine blocks as models
python3 tools/build_custom_blocks.py "$src" assets  # Cobblemon's ores, wood and plants as real blocks
python3 tools/build_balls.py "$src" assets   # every Poké Ball's own 3D model
python3 tools/build_npcs.py "$src" assets    # Cobblemon's NPC models
python3 tools/build_sounds.py "$src" assets  # Cobblemon's sounds for both clients   # Cobblemon's items: what they do, Java and Bedrock art
python3 tools/build_cards.py "$src" assets
python3 tools/build_bodies.py "$src" assets
python3 tools/build_bedrock_ui.py "$src/assets/cobblemon/textures/gui" assets/bedrock-ui.mcpack
python3 tools/build_ui.py "$src/assets/cobblemon/textures/gui" assets/ui-pack.zip  # after: the client mod's textures come from the Bedrock pack
# plugin: Gradle in Docker, so only Docker is needed (JDK 25 + Gradle come with the image)
docker run --rm -u "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/w/.gradle-home -v "$PWD/plugin":/w -w /w \
  gradle:9.1-jdk25 gradle -q --no-daemon build
fi

if [ "${1:-}" = --zip ] || [ "${1:-}" = --zip-only ]; then
  v=$2
  zipit() { (cd dist && rm -f "$1.zip" && zip -qr "$1.zip" "$1") && echo "dist/$1.zip ($(du -h "dist/$1.zip" | cut -f1))"; }
  # cobblemon-link-plugin (Paper)
  name=cobblemon-link-plugin-$v
  rm -rf "dist/$name" && mkdir -p "dist/$name/plugin"
  cp -r README.md PARITY.md LICENSE COBBLEMON_COMMIT assets server sim "dist/$name/"
  rm -rf "dist/$name/server/data" "dist/$name/server/.env"
  cp plugin/build/libs/cobblemon-link-plugin-*.jar "dist/$name/plugin/cobblemon-link-plugin.jar"
  zipit "$name"
  # cobblemon-link (Fabric): the built mod and PolyMc (fabric/tools/setup.sh makes both), the shared tools it calls
  name=cobblemon-link-$v
  ls fabric/mods-extra/cobblemon-link-[0-9]*.jar fabric/mods-extra/PolyMc-*.jar >/dev/null || { echo "run fabric/tools/setup.sh first" >&2; exit 1; }
  rm -rf "dist/$name" && mkdir -p "dist/$name/fabric/mods-extra" "dist/$name/tools"
  cp -r README.md LICENSE COBBLEMON_COMMIT assets "dist/$name/"
  cp -r fabric/README.md fabric/PARITY.md fabric/docker-compose.yml fabric/tools "dist/$name/fabric/"
  cp fabric/mods-extra/cobblemon-link-[0-9]*.jar fabric/mods-extra/PolyMc-*.jar "dist/$name/fabric/mods-extra/"
  cp tools/geo2bb.py tools/bedrock_blocks.py tools/bedrock_sounds.py "dist/$name/tools/"
  zipit "$name"
fi
