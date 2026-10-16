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
# are mirrored on this repo's deps-2026-10-15 release.
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
GeyserModelEngine-1.0.9.jar|https://github.com/kahngjoonkoh/cobblemon-link/releases/download/deps-2026-10-15/GeyserModelEngine-1.0.9.jar|6e777d569d7f7c2b8583ba21ba8ac7c451c265089bd34aefdb7f00c2fe8957a2
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
cp ../assets/gamedata.json "$D/plugins/CobblemonLink/"
# models: Java side (BetterModel) and Bedrock side (GeyserModelEngine extension)
cp ../assets/java/*.bbmodel "$D/plugins/BetterModel/models/"
cp -r ../assets/bedrock/* "$D/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/"

# configs only exist after the first boot
G="$D/plugins/Geyser-Spigot/config.yml"
if [ -f "$G" ]; then
  sed -i 's/^\(\s*\)auth-type: online/\1auth-type: floodgate/' "$G"
  echo "patched Geyser auth-type -> floodgate"
else
  echo "first run: start the server once, stop it, then run setup.sh again"
fi
