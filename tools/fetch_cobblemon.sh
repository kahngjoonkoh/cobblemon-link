#!/bin/bash
# Download the Cobblemon resources build_all.py needs (models, animations, posers, resolvers,
# textures, species data) at one commit, without cloning the whole repo.
# usage: fetch_cobblemon.sh COMMIT_SHA DEST
set -euo pipefail
sha=$1 dest=$2
api=https://gitlab.com/api/v4/projects/31496946/repository/archive.tar.gz   # cable-mc/cobblemon
base=common/src/main/resources
mkdir -p "$dest"
for p in assets/cobblemon/bedrock/pokemon assets/cobblemon/textures/pokemon data/cobblemon/species; do
  echo "fetching $p"
  curl -sfL "$api?sha=$sha&path=$base/$p" | tar xz -C "$dest" --strip-components=5
done
echo "$sha" > "$dest/COBBLEMON_COMMIT"
