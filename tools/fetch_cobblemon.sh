#!/bin/bash
# Download the Cobblemon resources build_all.py and build_data.py need (models, animations, posers,
# resolvers, textures, battle GUI art, Poké Ball items, species and their features, spawn pools, biome tags, lang) at one commit, without cloning the whole repo.
# usage: fetch_cobblemon.sh COMMIT_SHA DEST
set -euo pipefail
sha=$1 dest=$2
api=https://gitlab.com/api/v4/projects/31496946/repository/archive.tar.gz   # cable-mc/cobblemon
base=common/src/main/resources
mkdir -p "$dest"
for p in assets/cobblemon/bedrock/pokemon assets/cobblemon/textures/pokemon assets/cobblemon/textures/gui \
         assets/cobblemon/textures/item/poke_balls assets/cobblemon/models/item data/cobblemon/species \
         data/cobblemon/species_features data/cobblemon/species_feature_assignments \
         data/cobblemon/spawn_pool_world data/cobblemon/tags/worldgen/biome assets/cobblemon/lang; do
  echo "fetching $p"
  curl -sfL "$api?sha=$sha&path=$base/$p" | tar xz -C "$dest" --strip-components=5
done
# Kotlin-defined Pokémon models: the portrait framing of species that have no JSON poser (build_all.py)
k=common/src/main/kotlin/com/cobblemon/mod/common/client/render/models/blockbench/pokemon
echo "fetching $k"
curl -sfL "$api?sha=$sha&path=$k" | tar xz -C "$dest" --strip-components=5
echo "$sha" > "$dest/COBBLEMON_COMMIT"
