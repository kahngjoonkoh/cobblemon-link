#!/bin/bash
# Build assets/ from the Cobblemon commit pinned in COBBLEMON_COMMIT.
#   tools/build.sh                 -> assets/ (models, gamedata.json, datapack, ui-pack.zip, bedrock-ui.mcpack) + plugin jar
#   tools/build.sh --zip v0.1.0    -> also dist/cobblemon-link-v0.1.0.zip (ready-to-run server bundle)
set -euo pipefail
cd "$(dirname "$0")/.."
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
# the optional client mod (client/, Fabric): attached to each release, not published elsewhere
mkdir -p "$HOME/.cache/cl-gradle-mod"
docker run --rm -u "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/g -e HOME=/tmp -v "$HOME/.cache/cl-gradle-mod":/g -v "$PWD/client":/w -w /w \
  eclipse-temurin:25-jdk ./gradlew -q --no-daemon build

if [ "${1:-}" = --zip ]; then
  v=$2 name=cobblemon-link-$2
  rm -rf "dist/$name" && mkdir -p "dist/$name"
  cp -r README.md LICENSE COBBLEMON_COMMIT assets server sim "dist/$name/"
  rm -rf "dist/$name/server/data" "dist/$name/server/.env"
  mkdir -p "dist/$name/plugin" && cp plugin/build/libs/cobblemon-link-*.jar "dist/$name/plugin/cobblemon-link.jar"
  mod=$(ls client/build/libs/cobblemon-link-client-*.jar | grep -v sources)
  cp "$mod" "dist/cobblemon-link-client-$v.jar"  # a release asset of its own, for players
  (cd dist && rm -f "$name.zip" && zip -qr "$name.zip" "$name")
  echo "dist/$name.zip ($(du -h "dist/$name.zip" | cut -f1))"
  echo "dist/cobblemon-link-client-$v.jar (attach to the release with the zip)"
fi
