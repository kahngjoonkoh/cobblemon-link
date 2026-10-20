#!/bin/bash
# Fetch the pinned plugin set into ./data and install the converted models.
# Safe to re-run. Run once before the first boot and once after it (to patch generated configs).
set -euo pipefail
cd "$(dirname "$0")"
D=data
mkdir -p "$D/plugins/Geyser-Spigot/extensions" "$D/plugins/BetterModel/models" \
         "$D/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input"

# name | url | sha256   (ponytail: pinned by hash; bump all three together when upgrading)
# GeyserModelEngine/GeyserUtils only publish a moving "latest" tag, so the tested builds (MIT)
# are mirrored on this repo's deps-2026-10-15 release. GeyserModelEngine's Paper plugin comes from our fork
# (kahngjoonkoh/GeyserModelEngine, branch cobblemon-link-perf): upstream 1.0.9 + its open PR #76
# (patches/geysermodelengine-1.0.9-pr76.patch), until upstream merges it.
while IFS='|' read -r name url sha; do
  [ -z "$name" ] && continue
  dest="$D/plugins/$name"
  if [ ! -f "$dest" ] || ! echo "$sha  $dest" | sha256sum -c --quiet 2>/dev/null; then
    echo "downloading $name"
    curl -sfLo "$dest" "$url"
    echo "$sha  $dest" | sha256sum -c --quiet || { echo "HASH MISMATCH: $name" >&2; rm -f "$dest"; exit 1; }
  fi
done <<'EOF'
Geyser-Spigot.jar|https://download.geysermc.org/v2/projects/geyser/versions/2.12.0/builds/1256/downloads/spigot|c61720bbea714a704d9b76a2f1e9a7ac91271e48101ec745ccd668fda9514015
floodgate-spigot.jar|https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/141/downloads/spigot|21570aff9ce17d6983928e8552777760e1ede5050026b04c686b0ae112e6fd7e
bettermodel-3.5.1-SNAPSHOT-527-paper.jar|https://cdn.modrinth.com/data/4h8rX3rt/versions/f2UpZnG8/bettermodel-3.5.1-SNAPSHOT-527-paper.jar|8443cddb7e9b9372eab7c77cb0545b4526f995162e022530694bf96d97ad475f
packetevents-spigot-2.14.0.jar|https://cdn.modrinth.com/data/HYKaKraK/versions/m78nFxYg/packetevents-spigot-2.14.0.jar|060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7
GeyserModelEngine-1.0.9.jar|https://github.com/kahngjoonkoh/GeyserModelEngine/releases/download/1.0.9-cl.2/GeyserModelEngine-1.0.9-cl.2.jar|05ef67be2957c1be5872f31a3c8702fe9956bf20101f8b950e159049a6f0b3b6
geyserutils-spigot.jar|https://github.com/kahngjoonkoh/cobblemon-link/releases/download/deps-2026-10-15/geyserutils-spigot-1.0-SNAPSHOT.jar|b39677308d320c00be2a5bf8058296f39789b836cfb448d0c13437b2ecbdec43
Geyser-Spigot/extensions/GeyserModelEngineExtension-1.0.9.jar|https://github.com/kahngjoonkoh/cobblemon-link/releases/download/deps-2026-10-15/GeyserModelEngineExtension-1.0.9.jar|9b5e6c9b6e20f5f5aa7516cd32eb635f1a99d4458bd6327b828859cbb2aa4f18
Geyser-Spigot/extensions/geyserutils-geyser.jar|https://github.com/kahngjoonkoh/cobblemon-link/releases/download/deps-2026-10-15/geyserutils-geyser-1.0-SNAPSHOT.jar|50120094bd46fefbb1672737540e3a66a483391e062625676805b4bb08e79ea9
EOF

[ -d ../assets/java ] || { echo "no models in ../assets: run tools/build.sh, or use a release zip" >&2; exit 1; }
# gameplay plugin: prebuilt in release zips, or built by tools/build.sh from plugin/
jar=
for f in ../plugin/cobblemon-link.jar ../plugin/build/libs/cobblemon-link-*.jar; do [ -f "$f" ] && { jar=$f; break; }; done
[ -n "$jar" ] || { echo "no plugin jar: run tools/build.sh, or use a release zip" >&2; exit 1; }
rm -f "$D"/plugins/cobblemon-link*.jar
cp "$jar" "$D/plugins/cobblemon-link.jar"
mkdir -p "$D/plugins/CobblemonLink"
cp ../assets/gamedata.json ../assets/items.json ../assets/recipes.json ../assets/tms.json "$D/plugins/CobblemonLink/"
rm -rf "$D/plugins/CobblemonLink/lang" && cp -r ../assets/lang "$D/plugins/CobblemonLink/lang"  # Cobblemon's 29 languages
# Java battle-menu art; the pack server serves it next to BetterModel's build.zip
cp ../assets/ui-pack.zip "$D/plugins/BetterModel/cobblemon-link-ui.zip"
rm -f "$D"/plugins/BetterModel/cobblemon-link-pics-*.zip  # the Pokémon pictures, a pack a region
cp ../assets/pics/cobblemon-link-pics-*.zip "$D/plugins/BetterModel/"
mkdir -p "$D/plugins/Geyser-Spigot/packs" && cp ../assets/bedrock-ui.mcpack "$D/plugins/Geyser-Spigot/packs/cobblemon-link-ui.mcpack"
mkdir -p "$D/plugins/Geyser-Spigot/custom_mappings" && cp ../assets/geyser-mappings.json "$D/plugins/Geyser-Spigot/custom_mappings/cobblemon-link.json"
# Cobblemon's items (tools/build_items.py): Java art next to the UI pack, Bedrock icons and Geyser's item mappings
cp ../assets/items-pack.zip "$D/plugins/BetterModel/cobblemon-link-items.zip"
cp ../assets/items.mcpack "$D/plugins/Geyser-Spigot/packs/cobblemon-link-items.mcpack"
# Cobblemon's sounds (tools/build_sounds.py): cries, battle music, Poké Balls, machines
cp ../assets/sounds-pack.zip "$D/plugins/BetterModel/cobblemon-link-sounds.zip"
cp ../assets/sounds.mcpack "$D/plugins/Geyser-Spigot/packs/cobblemon-link-sounds.mcpack"
cp ../assets/geyser-items.json "$D/plugins/Geyser-Spigot/custom_mappings/cobblemon-link-items.json"
# models: Java side (BetterModel) and Bedrock side (GeyserModelEngine extension)
cp ../assets/java/*.bbmodel "$D/plugins/BetterModel/models/"
cp -r ../assets/bedrock/* "$D/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/"
# every Poké Ball's own model (tools/build_balls.py) and Cobblemon's machine blocks (tools/build_blocks.py)
cp ../assets/balls/java/*.bbmodel "$D/plugins/BetterModel/models/"
cp -r ../assets/balls/bedrock/* "$D/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/"
cp ../assets/blocks/java/*.bbmodel "$D/plugins/BetterModel/models/"
cp -r ../assets/blocks/bedrock/* "$D/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/"

# configs only exist after the first boot
G="$D/plugins/Geyser-Spigot/config.yml"
if [ -f "$G" ]; then
  sed -i 's/^\(\s*\)auth-type: online/\1auth-type: floodgate/' "$G"
  sed -i 's/^\(\s*\)max-players: .*/\1max-players: 12/' "$G"   # what Bedrock's server list shows
  echo "patched Geyser auth-type -> floodgate"
else
  echo "first run: start the server once, stop it, then run setup.sh again"
fi
# CPU (see README "Performance"): nothing a player can see changes
set_key() { [ -f "$1" ] && sed -i "s|^\(\s*\)$2:.*|\1$2: $3|" "$1"; }
B="$D/plugins/BetterModel/config.yml"
set_key "$B" lerp-frame-time 0          # converter keyframes are already linear; no frames to insert
set_key "$B" packet-bundling-size 0     # one bundle per model update instead of one per 4 bones
set_key "$B" player-animation false     # no player models here
set_key "$B" player-armor false
# Pokémon (pig carriers) tracked to 80 blocks: Cobblemon's own range; untracked models stop animating
[ -f "$D/spigot.yml" ] && sed -i '/entity-tracking-range:/,/display:/ s/^\(\s*\)animals: .*/\1animals: 80/' "$D/spigot.yml"
[ -f "$D/server.properties" ] && sed -i 's/^pause-when-empty-seconds=.*/pause-when-empty-seconds=60/' "$D/server.properties"
# GeyserModelEngine (PR #76 build): per-model loop at the server tick instead of every 20 ms
G2="$D/plugins/GeyserModelEngine/config.yml"
[ -f "$G2" ] && ! grep -q "entity-update-period" "$G2" && sed -i 's/^\(\s*\)entity-position-update-period: .*/&\n\1entity-update-period: 50 # ms/' "$G2"
