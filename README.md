# cobblemon-link

**Cobblemon-style Pokémon on a plain Paper server, playable from vanilla Java *and* Bedrock, with no client mods.**

> Unofficial fan project. Not affiliated with or endorsed by the Cobblemon team, PokeDemo, Nintendo, Game Freak, Creatures Inc. or The Pokémon Company. Pokémon and its characters are trademarks of their respective owners.

## Purpose

[Cobblemon](https://cobblemon.com) is the best Pokémon experience in Minecraft, but it is a Fabric/NeoForge **mod**. Every player must install it, and **Bedrock players (phones, consoles) can't join at all.**

[PokeDemo](https://modrinth.com/plugin/pokedemo) shows that the gameplay can run as a Paper **plugin**. But its models and menus need its own Fabric client mod. Without that mod, players see invisible Pokémon with floating name tags (see [docs/screenshots](docs/screenshots)), and Bedrock players can never install the mod.

This repo takes a third approach:

- **Gameplay** runs as a Paper plugin ([`plugin/`](plugin)), driven by Cobblemon's own data: species, stats, learnsets, evolutions and spawn pools.
- **Battles** run on [Pokémon Showdown](https://github.com/smogon/pokemon-showdown), the same engine Cobblemon uses, in a small sidecar container ([`sim/`](sim)).
- **Visuals** reach players as **server-sent resource packs**, which vanilla clients accept with one "Proceed" click.
- **Java:** [BetterModel](https://modrinth.com/plugin/bettermodel) renders Cobblemon's models with display entities and a generated resource pack.
- **Bedrock:** [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine) turns the same models into native Bedrock custom entities. Geyser pushes the Bedrock pack automatically.
- **Models** are Cobblemon's own. They are already in Bedrock geometry format and are converted for Java by [`tools/geo2bb.py`](tools/geo2bb.py).

## Current state: playable core

Built from Cobblemon `main` at commit [`910551e`](https://gitlab.com/cable-mc/cobblemon/-/commit/910551ee70bd2853532ea782a20420fef475ce4b) (2026-10-15, after 1.8.2), pinned in [`COBBLEMON_COMMIT`](COBBLEMON_COMMIT).

### Gameplay (v0.2)

Everything below was played through on a vanilla Java 26.3 client. The menus are chest menus, which Geyser shows to Bedrock players, but **the gameplay hasn't been tested on a Bedrock device yet**.

| Feature | How it works |
|---|---|
| **Starter** | `/starter` opens a menu of all 27 starters (Gen 1–9). You get the starter at Lv.5 plus 5 Poké Balls. |
| **Wild spawns** | Built from Cobblemon's spawn pools: 1,066 entries for 579 species. They are checked against biome (Cobblemon's biome tags, shipped as a datapack), time of day, rain, sky light and height. A rarity bucket is picked with Cobblemon's weights, and wild Pokémon are capped per player (default 8). |
| **Battles** | Right-click (or tap, on Bedrock) a wild Pokémon. The menu offers moves with PP, switching, Poké Ball and Run. Turns are narrated in chat. Wild Pokémon pick random moves. |
| **Catching** | Throw a Poké Ball at a wild Pokémon, or use one from the battle menu. Uses the main-series catch formula: catch rate, HP left, and status bonuses. |
| **Party and PC** | `/party` (6 slots; click to make lead, shift-click to send to PC) and `/pc` (pages of 45). HP carries over between battles. |
| **EXP and growth** | Gen 5+ EXP formula and all six growth curves. Level-up moves are learned automatically while there's room. Level-based evolutions (310 species) happen at the right level. |
| **Healing** | Lie down in a bed. |
| **Poké Balls** | Crafted vertically from red dye, a stone button and an iron ingot (makes 4). |

Not yet: trainers and gyms, items and held items, stone, trade and friendship evolutions, choosing which move to forget, water, flying and cave spawns, regional forms and shinies, sending out your own Pokémon in the world, and PvP battles.

| Battle menu | Battle log |
|---|---|
| ![battle menu](docs/screenshots/gameplay-battle-menu.png) | ![battle log](docs/screenshots/gameplay-battle-log.png) |
| **Party** | **Catching** |
| ![party](docs/screenshots/gameplay-party.png) | ![catch](docs/screenshots/gameplay-catch.png) |

### Models

| | Status |
|---|---|
| **901 species** (every base form Cobblemon has a model for) converted, 0 failures | ✅ (3 species lose one or two animation channels to typos in Cobblemon's own files; see [assets/README](assets/README.md)) |
| Rendered on **vanilla Java 26.3** (resource pack, one "Proceed" click) | ✅ verified: Pikachu, Bulbasaur, Charizard, Gengar, Snorlax, Wailord, plus the repaired Grafaiai, Milotic, Corviknight, Clamperl, Ampharos and Incineroar |
| Rendered on **Bedrock** (Galaxy S25, Bedrock 1.26.51, pack pushed by Geyser) | ✅ verified: Pikachu |
| Species scale (Cobblemon `baseScale`) and idle/walk animations | ✅ |
| Regional forms, shinies, look-at, blink and battle animations | ❌ not yet |

![six species on vanilla Java](docs/screenshots/java-vanilla-lineup.png)

*Wailord, Snorlax, Gengar, Charizard, Bulbasaur and Pikachu on an unmodded Java 26.3 client.*

**Download sizes for players** (once, then cached): Java resource pack about 35 MB, Bedrock pack about 16 MB.

## Server load

Measured with 100 animated Pikachu and one viewer. Full details are in [bench/RESULTS.md](bench/RESULTS.md).

| | Tick time (50 ms = lag) | Total server CPU |
|---|---|---|
| This approach (Paper 26.3 + BetterModel) | 4.6 ms | 284% |
| Cobblemon mod (Fabric 1.21.1) | 17.6 ms | 43% |

The main thread does well. The cost is moved to async threads that stream bone animation to every viewer, so per-player Pokémon caps are needed.

With the gameplay plugin and natural spawning (7 wild Pokémon around one player), the tick took 2.8 ms and the server used about 38% of one core. The Showdown sidecar idles at about 55 MB of RAM.

## Try it

You need Docker. Accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) before running.

1. Download `cobblemon-link-<version>.zip` from [Releases](https://github.com/kahngjoonkoh/cobblemon-link/releases) and unzip it.
2. Start the server:
   ```bash
   cd cobblemon-link-<version>/server
   ./setup.sh                       # downloads the pinned, hash-checked plugins; installs plugin, models and data
   docker compose up -d --build     # first boot generates configs (about a minute) and builds the battle sidecar
   docker compose down && ./setup.sh && docker compose up -d
   ```
3. Connect. Java: `<host>:25566` (bound to localhost by default; edit `ports` to expose it). Bedrock: `<host>:19133`.
4. Run `/starter`, then right-click (or tap) a wild Pokémon.

| Command | Who | |
|---|---|---|
| `/starter`, `/party`, `/pc`, `/battle` | everyone | `/battle` reopens the battle menu if you closed it |
| `/pokegive <species> [level]`, `/pokespawn <species> [level]`, `/pokeheal`, `/pokeballs [n]` | ops | for testing |

Settings (spawn cap, distances, rarity weights, sidecar address) are in `server/data/plugins/CobblemonLink/config.yml`. Party and PC data is saved per player under `players/` in the same folder.

**Java players outside this machine** need a public URL for the resource pack. Put `build.zip` (in `server/data/plugins/BetterModel/`) somewhere public and set `PACK_URL=https://.../build.zip` in `server/.env`. Or set `PACK_BIND=0.0.0.0` to expose the bundled pack server on port 8099. Bedrock players need nothing: Geyser sends their pack.

Bedrock players join through Floodgate with a `.` prefix, so op them by that name (for example `.Steve`) **while they are online**. Opping them before their first join saves the wrong ID.

**Stopping:** use `docker compose stop` or `docker compose down`. The server saves the world and exits within seconds. Don't use `docker kill`, which skips the save.

### Build from source

```bash
tools/build.sh                    # fetch the pinned Cobblemon commit, convert into assets/, build the plugin
tools/build.sh --zip v0.2.0       # also write dist/cobblemon-link-v0.2.0.zip
```

Needs `bash`, `curl`, `python3`, `zip` and Docker (the plugin builds with Gradle and JDK 25 inside the `gradle:9.1-jdk25` image). To move to a newer Cobblemon, put its commit SHA in `COBBLEMON_COMMIT` and rebuild. `assets/species.tsv` lists each species with its scale, animations and status.

## Repo layout

```
plugin/    the Paper plugin (Java 25): spawning, battles, catching, party/PC, EXP. Tests: plugin/src/test
sim/       Pokémon Showdown battle sidecar (Node), with a self-check in sim/test.js
assets/    built, not committed: models (CC BY-NC 3.0), gamedata.json and biome-tag datapack (MPL-2.0)
tools/     build.sh (fetch + convert + build + zip), fetch_cobblemon.sh, build_all.py (models),
           build_data.py (gameplay data), geo2bb.py (one model)
server/    docker-compose + setup.sh (Paper 26.3 + plugins + sidecar)
bench/     load benchmark scripts, raw results and screenshots
docs/      screenshots, including PokeDemo on vanilla clients for comparison
```

Convert a single model by hand:

```bash
python3 tools/geo2bb.py <geo.json> <animation.json> <texture.png> <name> \
  <java_out_dir> <bedrock_out_dir> ground_idle=idle,ground_walk=walk [scale]
```

## Roadmap

1. Bedrock device test of the gameplay (menus, tapping to battle, throwing balls).
2. Trainers, gyms and badges; items and stone evolutions; a move-replace menu.
3. Water, flying and cave spawns; regional forms and shinies.
4. Measure network bandwidth per viewer, since bone streaming goes through tunnels and mobile data.

## Attribution

- **[Cobblemon](https://gitlab.com/cable-mc/cobblemon)** by the Cobblemon team and contributors.
  - All Pokémon models, animations and textures in `assets/` (and in release zips) are adapted from Cobblemon under **CC BY-NC 3.0**.
  - Cobblemon's repository is **MPL-2.0**. `assets/gamedata.json` (species stats, learnsets, evolutions, spawn pools, English names) and the biome-tag datapack are derived from Cobblemon's data files under MPL-2.0. No Cobblemon code is included.
- **[PokeDemo](https://modrinth.com/plugin/pokedemo)** ([source](https://github.com/nn2048/PokedemoProject), **GPL-3.0**) by PtrMC / nn2048.
  - It inspired the Paper-plugin approach and was studied as a reference.
  - No PokeDemo code or assets are included. Its screenshots in `docs/` only document how it looks on vanilla clients.
- **[BetterModel](https://github.com/toxicity188/BetterModel)** (Java model rendering) is a runtime dependency, downloaded by `server/setup.sh` and not redistributed.
- **[GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine)** and **[GeyserUtils](https://github.com/GeyserExtensionists/GeyserUtils)** (Bedrock bridge, both MIT, © zimzaza4) only publish a moving `latest` build. The exact tested jars are mirrored unmodified, with their licenses, on the [`deps-2026-10-15`](https://github.com/kahngjoonkoh/cobblemon-link/releases/tag/deps-2026-10-15) release.
- **[Pokémon Showdown](https://github.com/smogon/pokemon-showdown)** (MIT) runs every battle. It is installed from npm into the sidecar image and not redistributed.
- **[Geyser and Floodgate](https://geysermc.org)**, **[packetevents](https://github.com/retrooper/packetevents)** and **[Paper](https://papermc.io)** are also runtime dependencies and are not redistributed.

## License

- **Code** (`plugin/`, `sim/`, `tools/`, `server/`, `bench/`): [MPL-2.0](LICENSE). It was chosen to stay compatible with Cobblemon's code license.
- **Models, animations and textures** (`assets/java`, `assets/bedrock`): [CC BY-NC 3.0](assets/LICENSE-CC-BY-NC-3.0.txt), inherited from Cobblemon. **Non-commercial use only.**
- **Game data** (`assets/gamedata.json`, `assets/datapack`): MPL-2.0, derived from Cobblemon's data.
- Screenshots in `docs/` and `bench/` show third-party content and are included for documentation only.
