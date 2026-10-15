#!/bin/bash
# Build assets/ from the Cobblemon commit pinned in COBBLEMON_COMMIT.
#   tools/build.sh                 -> assets/java, assets/bedrock, assets/species.tsv
#   tools/build.sh --zip v0.1.0    -> also dist/cobblemon-link-v0.1.0.zip (ready-to-run server bundle)
set -euo pipefail
cd "$(dirname "$0")/.."
sha=$(cat COBBLEMON_COMMIT)
src=$(mktemp -d); trap 'rm -rf "$src"' EXIT
tools/fetch_cobblemon.sh "$sha" "$src"
rm -rf assets/java assets/bedrock assets/species.tsv
python3 tools/build_all.py "$src" assets

if [ "${1:-}" = --zip ]; then
  v=$2 name=cobblemon-link-$2
  rm -rf "dist/$name" && mkdir -p "dist/$name"
  cp -r README.md LICENSE COBBLEMON_COMMIT assets server "dist/$name/"
  rm -rf "dist/$name/server/data"
  (cd dist && rm -f "$name.zip" && zip -qr "$name.zip" "$name")
  echo "dist/$name.zip ($(du -h "dist/$name.zip" | cut -f1))"
fi
