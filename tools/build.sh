#!/bin/bash
# Build assets/ from the Cobblemon commit pinned in COBBLEMON_COMMIT.
#   tools/build.sh                 -> assets/ (models, gamedata.json, datapack) + plugin/build/libs/*.jar
#   tools/build.sh --zip v0.1.0    -> also dist/cobblemon-link-v0.1.0.zip (ready-to-run server bundle)
set -euo pipefail
cd "$(dirname "$0")/.."
sha=$(cat COBBLEMON_COMMIT)
src=$(mktemp -d); trap 'rm -rf "$src"' EXIT
tools/fetch_cobblemon.sh "$sha" "$src"
rm -rf assets/java assets/bedrock assets/species.tsv assets/gamedata.json assets/datapack
python3 tools/build_all.py "$src" assets
python3 tools/build_data.py "$src" assets
# plugin: Gradle in Docker, so only Docker is needed (JDK 25 + Gradle come with the image)
docker run --rm -u "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/w/.gradle-home -v "$PWD/plugin":/w -w /w \
  gradle:9.1-jdk25 gradle -q --no-daemon build

if [ "${1:-}" = --zip ]; then
  v=$2 name=cobblemon-link-$2
  rm -rf "dist/$name" && mkdir -p "dist/$name"
  cp -r README.md LICENSE COBBLEMON_COMMIT assets server sim "dist/$name/"
  rm -rf "dist/$name/server/data" "dist/$name/server/.env"
  mkdir -p "dist/$name/plugin" && cp plugin/build/libs/cobblemon-link-*.jar "dist/$name/plugin/cobblemon-link.jar"
  (cd dist && rm -f "$name.zip" && zip -qr "$name.zip" "$name")
  echo "dist/$name.zip ($(du -h "dist/$name.zip" | cut -f1))"
fi
