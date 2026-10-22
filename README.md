# cobblemon-link

**Play Cobblemon together, whatever you play on.** Bedrock players on phones, consoles and PC, Java players without
any mods, and Java players with the Cobblemon mod, all on one server.

> Unofficial fan project. Not affiliated with or endorsed by the Cobblemon team, PokeDemo, Nintendo, Game Freak,
> Creatures Inc. or The Pokémon Company. Pokémon and its characters are trademarks of their respective owners.

## Why

[Cobblemon](https://cobblemon.com) is the best Pokémon experience in Minecraft, but it is a Java mod: every player has
to install it, and Bedrock players (phones, consoles) can't join at all. cobblemon-link lets them in. A friend on a
phone, a friend on plain Java and a friend with the Cobblemon mod can catch, battle and trade on the same server.

**Free and non-commercial, for everyone.**
- cobblemon-link is free and open source ([MPL-2.0](LICENSE)), and it will stay that way.
- Anyone can play, and anyone can host it. There are no stores, paid ranks, premium versions or closed components.
- Cobblemon's art is licensed for non-commercial use only ([CC BY-NC 3.0](assets/LICENSE-CC-BY-NC-3.0.txt)), so a
  server running cobblemon-link must not sell anything that uses it.
- Open projects are welcome as reference and credited below. We don't take code we aren't allowed to.

## Two products

| | **cobblemon-link** (Fabric) | **cobblemon-link-plugin** (Paper) |
|---|---|---|
| What runs the game | The real Cobblemon 1.8.2, with our server-side mod beside it | Our Paper plugin, rebuilding Cobblemon's gameplay from its own data and battle engine |
| Cobblemon-mod players (Java 1.21.1) | ✅ the full Cobblemon experience | ➖ |
| Vanilla Java | ✅ Java 1.21.1, a server-drawn "taster", from `off` to `full` (a server setting) | ✅ Java 26.3, the main way to play |
| Bedrock | ✅ through Geyser + ViaProxy, as close to Cobblemon as we can make it | ✅ through Geyser |
| Server | Minecraft 1.21.1 · Fabric · Cobblemon 1.8.2 · PolyMc · Polymer · Floodgate · Geyser standalone · ViaProxy | Paper 26.3 (build 190) · Cobblemon data from `main` at [`910551e`](https://gitlab.com/cable-mc/cobblemon/-/commit/910551ee70bd2853532ea782a20420fef475ce4b) (after 1.8.2) · Geyser · Floodgate · BetterModel |
| Pick it if | you want real Cobblemon, and mod players with everyone else | your players won't install mods, or you want the newest Minecraft |
| Feature by feature | [fabric/PARITY.md](fabric/PARITY.md) | [PARITY.md](PARITY.md) |
| Folder | [`fabric/`](fabric) | [`plugin/`](plugin), [`server/`](server), [`sim/`](sim) |

Both use the same converted Cobblemon models, Bedrock screens and party HUD, built from Cobblemon's own files by
[`tools/`](tools).

### What you can do

On both products, from Bedrock and vanilla Java:
- **Catching and battles:**
  - choose a starter and catch wild Pokémon (land, water, caves, fishing, herds and alphas);
  - battle them with Cobblemon's battle screen, and fight other players and NPC trainers, in singles, doubles or
    triples.
- **Your Pokémon:**
  - the party HUD, Summary, PC and Pokédex;
  - EXP, evolutions of every kind, items, held items, TMs and benched moves;
  - trading.
- **Out in the world:**
  - ride Pokémon, carry them on your shoulder, fish with Poké Rods;
  - cook in the Campfire Pot; use the healing machine, pasture, fossils and the TM machine; talk to NPCs.
- **Language:** each player sees the game in their own language (Cobblemon's 29).

**Paper: Cobblemon's blocks borrow some vanilla block states.** Cobblemon's ores, apricorn wood, apricorns, berry
bushes and mints are drawn on states of mushroom blocks and string that the plugin keeps for them
([docs/custom-blocks.md](docs/custom-blocks.md)). Normal mushroom blocks, string and tripwire hooks still work as usual,
but a mushroom block can't show its bottom face unless all six faces show (those face combinations are reserved).

What's still partial, row by row, is in the two PARITY files. The Fabric product shows Cobblemon itself, so its gaps
are about looks; the Paper product rebuilds Cobblemon, so some rules are simplified.

## Quick start

You need Docker. Accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) before running. Download the zip for your
product from [Releases](https://github.com/kahngjoonkoh/cobblemon-link/releases).

**cobblemon-link (Fabric):** `cobblemon-link-<version>.zip`
```bash
cd cobblemon-link-<version>/fabric
tools/setup.sh                 # pinned, hash-checked downloads; models and packs for this server
docker compose up -d           # first boot: Cobblemon, configs, the Floodgate key (a few minutes)
docker compose down && tools/setup.sh && docker compose up -d
```
- **Java:** `<host>:25565`. Use Minecraft 1.21.1, with or without the Cobblemon 1.8.2 mod.
- **Bedrock:** `<host>:19132`.
- **Settings:** the vanilla Java level and others, in [fabric/README.md](fabric/README.md).

**cobblemon-link-plugin (Paper):** `cobblemon-link-plugin-<version>.zip`
```bash
cd cobblemon-link-plugin-<version>/server
./setup.sh                     # pinned, hash-checked plugins; installs the plugin, models and data
docker compose up -d --build   # first boot generates configs and builds the battle sidecar
docker compose down && ./setup.sh && docker compose up -d
```
- **Java:** `<host>:25566` (Minecraft 26.3).
- **Bedrock:** `<host>:19133`.
- Java's port is bound to localhost by default. Set `JAVA_BIND=0.0.0.0` (or a Tailscale IP) in `server/.env` to
  expose it.
- Java players from outside need the resource pack served publicly: set `PACK_URL=` or `PACK_BIND=0.0.0.0`.
- Settings are in `server/data/plugins/CobblemonLink/config.yml`.

**Then, on either:** run `/starter`, then right-click (tap on Bedrock) a wild Pokémon.
- **Bedrock names:** Bedrock players join with a `.` prefix, so op them by that name (for example `.Steve`) while
  they're online.
- **Stopping:** use `docker compose stop`, which saves the world. Not `docker kill`.

## Build from source

```bash
tools/build.sh                     # fetch the pinned Cobblemon, convert models/data/packs into assets/, build the plugin
fabric/tools/setup.sh              # build the Fabric mod and PolyMc, and its models and packs
tools/build.sh --zip-only v1.1.4   # the two release zips in dist/
```

Needs `bash`, `curl`, `python3` (Pillow, NumPy), `zip` and Docker. Gradle and the JDKs run inside Docker images. To move
the Paper product to a newer Cobblemon, put its commit in [`COBBLEMON_COMMIT`](COBBLEMON_COMMIT) and rebuild.

## Repo layout

```
fabric/    cobblemon-link: the server-side Fabric mod (fabric/mod), its compose file and tools
plugin/    cobblemon-link-plugin: the Paper plugin (Java 25). Tests: plugin/src/test
sim/       the Paper product's battle sidecar: Cobblemon's own Showdown build (Node), self-checks in sim/test*.js
server/    the Paper product's compose file and setup.sh
tools/     shared: fetch + convert Cobblemon's models, data, sounds, items, UI and Bedrock packs; build.sh; test clients
assets/    built, not committed: models and packs (CC BY-NC 3.0), game data (MPL-2.0)
client/    a frozen, optional Fabric client mod for the Paper product (not part of the 1.0 releases)
bench/     load benchmarks          docs/     screenshots, evidence, the Fabric proof of concept
```

## Upgrade notes

- **Cobblemon on Minecraft 1.21.4+:** the Fabric product is on 1.21.1 because Cobblemon is. When Cobblemon moves to
  1.21.4+, held Poké Balls for vanilla Java can become 3D in the hand and stay flat in the inventory, as on Paper.
- **Bedrock NetherNet (from 2026-10-27):** newer Bedrock versions need a newer Geyser and its NetherNet port. Update the
  Geyser pins in `fabric/tools/setup.sh` and `server/setup.sh`.

## Credits

- **[Cobblemon](https://gitlab.com/cable-mc/cobblemon)** by the Cobblemon team and contributors.
  - Models, animations, textures, sounds and GUI art (everything built into `assets/`) are adapted from Cobblemon under
    **CC BY-NC 3.0**. The changes are listed in [assets/README.md](assets/README.md).
  - Game data (`gamedata.json`, the datapack, items, recipes, dialogues, structures) is derived from Cobblemon's data
    files under **MPL-2.0**.
  - On Fabric, Cobblemon itself runs on the server, downloaded from Modrinth.
- **[cobblemon-showdown](https://gitlab.com/cable-mc/cobblemon-showdown)** (MIT), Cobblemon's fork of Pokémon Showdown,
  runs the Paper product's battles. It is unpacked from Cobblemon's resources with Cobblemon's bag item scripts
  (MPL-2.0).
- **[Geyser and Floodgate](https://geysermc.org)** (MIT): Bedrock players on both products.
- **[ViaProxy](https://github.com/ViaVersion/ViaProxy)** (GPL-3.0): lets Geyser reach the 1.21.1 Fabric server. It runs
  as its own process and isn't modified.
- **[PolyMc](https://github.com/TheEpicBlock/PolyMc)** (LGPL-3.0) and **[Polymer](https://github.com/Patbox/polymer)**
  (LGPL-3.0): Cobblemon's content for vanilla clients on Fabric. PolyMc is built unmodified from its source at commit
  `a3eaae6`.
- **blockbench-import-library (BIL)** (LGPL-3.0): the Fabric proof of concept drew models with it, and the Fabric
  product's model maths follow it. It isn't shipped.
- **[BetterModel](https://github.com/toxicity188/BetterModel)** (Java models) and
  **[packetevents](https://github.com/retrooper/packetevents)**: Paper runtime dependencies.
- **[GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine)** and
  **[GeyserUtils](https://github.com/GeyserExtensionists/GeyserUtils)** (MIT, © zimzaza4): Cobblemon's models on
  Bedrock, on both products.
  - The tested builds are mirrored with their licences on the
    [`deps-2026-10-15`](https://github.com/kahngjoonkoh/cobblemon-link/releases/tag/deps-2026-10-15) release.
  - The Paper plugin comes from our public fork
    ([`1.0.9-cl.2`](https://github.com/kahngjoonkoh/GeyserModelEngine/releases/tag/1.0.9-cl.2)): upstream plus its open
    [PR #76](https://github.com/GeyserExtensionists/GeyserModelEngine/pull/76).
  - The Geyser extension is patched so phones load a much smaller Bedrock pack: compact JSON, identical animations and
    geometry shared between models, and no bone-tint layers
    ([`deps-2026-10-22`](https://github.com/kahngjoonkoh/cobblemon-link/releases/tag/deps-2026-10-22),
    `server/patches/geysermodelengineextension-1.0.9-cl.patch`).
- **[PokeDemo](https://modrinth.com/plugin/pokedemo)** ([source](https://github.com/nn2048/PokedemoProject),
  GPL-3.0) inspired the Paper approach and was studied for ideas only. No PokeDemo code or assets are included.
- **[Paper](https://papermc.io)**, **[Fabric](https://fabricmc.net)** and
  **[itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server)** run the servers.

Runtime dependencies are downloaded by the setup scripts, pinned by hash, and not redistributed. The exceptions are
PolyMc (built from source; its source is at the link above) and the mirrored GeyserModelEngine/GeyserUtils builds.

## License

- **Code** (`fabric/`, `plugin/`, `sim/`, `tools/`, `server/`, `client/`, `bench/`): [MPL-2.0](LICENSE), chosen to
  stay compatible with Cobblemon's code licence.
- **Models, animations, textures, sounds and GUI art** in `assets/` and the release zips:
  [CC BY-NC 3.0](assets/LICENSE-CC-BY-NC-3.0.txt), from Cobblemon. **Non-commercial use only.**
- **Game data** in `assets/`: MPL-2.0, derived from Cobblemon's data.
- Screenshots in `docs/`, `fabric/evidence/` and `bench/` show third-party content and are included for documentation
  only.
