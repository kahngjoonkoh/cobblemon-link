#!/bin/bash
# cobblemon-link (Fabric): fetches the pinned server-side mods and Bedrock tools, lays out $DATA (default
# fabric/data), builds the models and packs from the repo's assets/ (tools/build.sh makes them), configures Geyser
# and Floodgate. Run it, start once (docker compose up -d; the first boot writes configs), stop, run it again.
set -e
cd "$(dirname "$0")/.."
[ -f .env ] && set -a && . ./.env && set +a
DATA=${DATA:-./data}; MODELS=${MODELS:-$DATA/models}; ASSETS=${ASSETS:-../assets}
mkdir -p mods-extra "$DATA/fabric/config/floodgate" "$DATA/fabric/config/cobblemonlink" "$DATA/geyser/extensions/geysermodelengineextension/input" \
    "$DATA/geyser/packs" "$DATA/geyser/custom_mappings" "$DATA/viaproxy" "$MODELS"

# name | url | sha256 (pinned; bump together). GeyserUtils is the build the Paper version tested, mirrored on the
# repo's deps-2026-10-15 release; the GeyserModelEngine extension writes compact JSON
# (../server/patches/geysermodelengineextension-1.0.9-compact-json.patch, deps-2026-10-21).
while IFS='|' read -r dest url sha; do
  [ -z "$dest" ] && continue
  if [ ! -f "$dest" ] || ! echo "$sha  $dest" | sha256sum -c --quiet 2>/dev/null; then
    echo "downloading $dest"; curl -fsSL -o "$dest" "$url"
    echo "$sha  $dest" | sha256sum -c --quiet || { echo "HASH MISMATCH: $dest" >&2; rm -f "$dest"; exit 1; }
  fi
done <<EOF2
mods-extra/Floodgate-Fabric-2.2.4-b38.jar|https://cdn.modrinth.com/data/bWrNNfkb/versions/Mf2wV7re/Floodgate-Fabric-2.2.4-b38.jar|8a8494e9579ecb5e5a8d74f43122816a26a8460ec2b8954cf89575b360351fa9
mods-extra/polymer-bundled-0.9.19+1.21.1.jar|https://maven.nucleoid.xyz/eu/pb4/polymer-bundled/0.9.19+1.21.1/polymer-bundled-0.9.19+1.21.1.jar|8c6bb7897bf53e3207a2ca4fc26db55471990b605855f494c2d80bfcabd1ed3b
$DATA/geyser/Geyser-Standalone.jar|https://download.geysermc.org/v2/projects/geyser/versions/2.12.0/builds/1265/downloads/standalone|3e235b5112110665a53ed3b42c0b8d514021c50184c01380aa5b01d52ee1f636
$DATA/viaproxy/ViaProxy.jar|https://github.com/ViaVersion/ViaProxy/releases/download/v3.4.14/ViaProxy-3.4.14.jar|2894bbfb2f4342f8fde887af6c689313efd5b0e46a8006775eea0e21e2948ab5
$DATA/geyser/extensions/GeyserModelEngineExtension-1.0.9.jar|https://github.com/kahngjoonkoh/cobblemon-link/releases/download/deps-2026-10-21/GeyserModelEngineExtension-1.0.9-cl.1.jar|54b6004bbb2ec8a50127e68a0f48488b4b89128093ee7782c2f4a140086b5fc5
$DATA/geyser/extensions/geyserutils-geyser.jar|https://github.com/kahngjoonkoh/cobblemon-link/releases/download/deps-2026-10-15/geyserutils-geyser-1.0-SNAPSHOT.jar|50120094bd46fefbb1672737540e3a66a483391e062625676805b4bb08e79ea9
EOF2

# PolyMc has no 1.21.1 release: built from TheEpicBlock/PolyMc master at a pinned commit (its datagen first);
# release zips ship the built jar
POLYMC=mods-extra/PolyMc-5.7.0-rev.a3eaae6+1.21.jar
if [ ! -f "$POLYMC" ]; then
  src=~/.cache/cl-fabric/src/PolyMc
  [ -d "$src" ] || git clone -q https://github.com/TheEpicBlock/PolyMc.git "$src"
  mkdir -p ~/.cache/cl-fabric/gradle  # else Docker creates it as root
  git -C "$src" checkout -q a3eaae6
  docker run --rm -u "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/g -e HOME=/tmp -v ~/.cache/cl-fabric/gradle:/g -v "$src":/w -w /w \
      eclipse-temurin:21-jdk bash -c "./gradlew -q --no-daemon runDatagen && ./gradlew -q --no-daemon build -x test"
  cp "$src/build/libs/PolyMc-5.7.0-rev.a3eaae6+1.21.jar" "$POLYMC"
fi
# the mod builds against these (tools/build.sh); a release zip ships the built mod instead of mod/
if [ -d mod/src ]; then
  mkdir -p mod/libs
  cp "$POLYMC" mods-extra/Floodgate-Fabric-2.2.4-b38.jar mod/libs/
  unzip -o -q -j mods-extra/Floodgate-Fabric-2.2.4-b38.jar META-INF/jars/cumulus-1.1.2.jar META-INF/jars/common-2.2.1-SNAPSHOT-unshaded.jar -d mod/libs
  tools/build.sh
fi

# models: one item model a bone for vanilla Java, GeyserModelEngine's input for Bedrock (both from tools/build_all.py)
if [ -d "$ASSETS/java" ]; then
  python3 tools/bil_models.py "$ASSETS/java" ~/.cache/cl-fabric/models >/dev/null
  [ -f "$MODELS/index.json" ] && [ "$MODELS/index.json" -nt "$ASSETS/java" ] || \
      python3 tools/build_models.py ~/.cache/cl-fabric/models "$MODELS" "$ASSETS/gamedata.json"
  cp -r "$ASSETS"/bedrock/* "$DATA/geyser/extensions/geysermodelengineextension/input/"
  # GeyserModelEngine only writes pack files that are missing: clear its output so the next start regenerates it
  rm -rf "$DATA/geyser/extensions/geysermodelengineextension/ResourcePack/generated_pack"
  cp "$ASSETS/bedrock-ui.mcpack" "$DATA/geyser/packs/cobblemon-link-ui.mcpack"
  # Cobblemon's sounds (cries, the Poké Ball) for Bedrock, from the Cobblemon jar the server downloaded
  J=$(ls "$DATA"/fabric/mods/Cobblemon-fabric-*.jar 2>/dev/null | head -1)
  [ -n "$J" ] && python3 ../tools/bedrock_sounds.py "$J" "$DATA/geyser/packs/cobblemon-link-sounds.mcpack" >/dev/null
  # Cobblemon's machine blocks (healing machine, PC...) as GeyserModelEngine models; keep in step with Furniture.BLOCKS
  [ -n "$J" ] && VANILLA_JAR=$(ls "$DATA"/fabric/polymer/cached_client_jars/*.jar 2>/dev/null | head -1) python3 ../tools/bedrock_blocks.py "$J" "$DATA/geyser/extensions/geysermodelengineextension/input" healing_machine pc \
      pasture display_case fossil_analyzer restoration_tank monitor tm_machine campfire_pot_{red,blue,green,white,yellow,pink,black} plants berries gilded >/dev/null
  # Cobblemon's NPCs as models (their skins), from the Cobblemon jar unpacked
  if [ -n "$J" ]; then rm -rf ~/.cache/cl-fabric/cobj && mkdir -p ~/.cache/cl-fabric/cobj && unzip -q -o "$J" 'assets/*' -d ~/.cache/cl-fabric/cobj
    python3 tools/npc_models.py ~/.cache/cl-fabric/cobj "$MODELS" "$DATA/geyser/extensions/geysermodelengineextension/input" >/dev/null; fi
  python3 tools/ui_pack.py "$ASSETS/ui-pack.zip" "$ASSETS/pics" "${UI:-$DATA/ui}" >/dev/null   # vanilla Java's UI art
else
  echo "no $ASSETS/java: run tools/build.sh at the repo root first (it converts Cobblemon's models)"
fi

# configs only exist after the first boot
G="$DATA/geyser/config.yml"
if [ -f "$G" ] && [ -f "$DATA/fabric/config/floodgate/key.pem" ]; then
  sed -i 's/^\(\s*\)auth-type: .*/\1auth-type: floodgate/; s/^\(\s*\)address: 127.0.0.1/\1address: viaproxy/; s/^\(\s*\)port: 25565/\1port: 25568/' "$G"
  cp "$DATA/fabric/config/floodgate/key.pem" "$DATA/geyser/key.pem"
  # Cobblemon's item icons: Geyser mappings for PolyMc's stand-ins, from the pack Polymer built at the last start
  P="$DATA/fabric/polymer/resource_pack.zip"
  if [ -f "$P" ]; then
    python3 tools/bedrock_items.py "$P" /tmp/cl-items.$$ >/dev/null
    cp /tmp/cl-items.$$/cobblemon-link-items.json "$DATA/geyser/custom_mappings/"
    cp /tmp/cl-items.$$/cobblemon-link-items.mcpack "$DATA/geyser/packs/"
    rm -rf /tmp/cl-items.$$
  fi
  echo "configured Geyser (Floodgate, through ViaProxy) and Bedrock item icons"
else
  echo "first run: docker compose up -d, wait for the server to start, docker compose down, then run setup.sh again"
fi
