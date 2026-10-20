#!/bin/bash
# Download the Cobblemon resources build_all.py and build_data.py need (models, animations, posers, the Poké Ball model,
# resolvers, textures, battle GUI art, Poké Ball items, species and their features, spawn pools, biome tags, lang, item textures,
# bag item effects, Showdown) at one commit, without cloning the whole repo.
# usage: fetch_cobblemon.sh COMMIT_SHA DEST
set -euo pipefail
sha=$1 dest=$2
api=https://gitlab.com/api/v4/projects/31496946/repository/archive.tar.gz   # cable-mc/cobblemon
base=common/src/main/resources
mkdir -p "$dest"
for p in assets/cobblemon/bedrock/pokemon assets/cobblemon/textures/pokemon assets/cobblemon/textures/gui \
         assets/cobblemon/bedrock/poke_balls assets/cobblemon/textures/poke_balls \
         assets/cobblemon/textures/item assets/cobblemon/models/item data/cobblemon/species \
         data/cobblemon/species_features data/cobblemon/species_feature_assignments \
         data/cobblemon/spawn_pool_world data/cobblemon/tags/worldgen/biome assets/cobblemon/lang \
         data/cobblemon/bag_items data/cobblemon/mechanics data/cobblemon/recipe data/cobblemon/tags/item \
         assets/cobblemon/sounds assets/cobblemon/blockstates assets/cobblemon/models/block assets/cobblemon/textures/block data/cobblemon/fossils data/cobblemon/tms \
         data/cobblemon/npcs data/cobblemon/npc_presets data/cobblemon/marks \
         data/cobblemon/spawn_bait_effects data/cobblemon/moveset_builders data/cobblemon/cosmetic_items; do
  echo "fetching $p"
  curl -sfL "$api?sha=$sha&path=$base/$p" | tar xz -C "$dest" --strip-components=5
done
# Kotlin-defined Pokémon models: the portrait framing of species that have no JSON poser (build_all.py)
k=common/src/main/kotlin/com/cobblemon/mod/common/client/render/models/blockbench/pokemon
echo "fetching $k"
curl -sfL "$api?sha=$sha&path=$k" | tar xz -C "$dest" --strip-components=5
# Cobblemon's own Showdown build (battle engine with bag items, persistent HP/status/PP): sim/server.js runs it
echo "fetching showdown.zip"
curl -sfL "https://gitlab.com/api/v4/projects/31496946/repository/files/common%2Fsrc%2Fmain%2Fresources%2Fdata%2Fcobblemon%2Fshowdown.zip/raw?ref=$sha" \
  -o "$dest/data/cobblemon/showdown.zip"
curl -sfL "https://gitlab.com/api/v4/projects/31496946/repository/files/common%2Fsrc%2Fmain%2Fresources%2Fassets%2Fcobblemon%2Fsounds.json/raw?ref=$sha" \
  -o "$dest/assets/cobblemon/sounds.json"
echo "$sha" > "$dest/COBBLEMON_COMMIT"
