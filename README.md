# cobblemon-link

**Cobblemon-style Pokémon on a plain Paper server, playable from vanilla Java *and* Bedrock, with no client mods.**

> Unofficial fan project. Not affiliated with or endorsed by the Cobblemon team, PokeDemo, Nintendo, Game Freak, Creatures Inc. or The Pokémon Company. Pokémon and its characters are trademarks of their respective owners.

## Purpose

[Cobblemon](https://cobblemon.com) is the best Pokémon experience in Minecraft, but it is a Fabric/NeoForge **mod**. Every player must install it, and **Bedrock players (phones, consoles) can't join at all.**

[PokeDemo](https://modrinth.com/plugin/pokedemo) shows that the gameplay can run as a Paper **plugin**. But its models and menus need its own Fabric client mod. Without that mod, players see invisible Pokémon with floating name tags (see [docs/screenshots](docs/screenshots)), and Bedrock players can never install the mod.

This repo tests a third approach:

- **Gameplay** runs as a Paper plugin.
- **Visuals** reach players as **server-sent resource packs**, which vanilla clients accept with one "Proceed" click.
- **Java:** [BetterModel](https://modrinth.com/plugin/bettermodel) renders Cobblemon's models with display entities and a generated resource pack.
- **Bedrock:** [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine) turns the same models into native Bedrock custom entities. Geyser pushes the Bedrock pack automatically.
- **Models** are Cobblemon's own. They are already in Bedrock geometry format and are converted for Java by [`tools/geo2bb.py`](tools/geo2bb.py).

## Current state: all species, visuals only

Built from Cobblemon `main` at commit [`910551e`](https://gitlab.com/cable-mc/cobblemon/-/commit/910551ee70bd2853532ea782a20420fef475ce4b) (2026-10-15, after 1.8.2), pinned in [`COBBLEMON_COMMIT`](COBBLEMON_COMMIT).

| | Status |
|---|---|
| **901 species** (every base form Cobblemon has a model for) converted, 0 failures | ✅ (3 species lose one or two animation channels to typos in Cobblemon's own files; see [assets/README](assets/README.md)) |
| Rendered on **vanilla Java 26.3** (resource pack, one "Proceed" click) | ✅ verified: Pikachu, Bulbasaur, Charizard, Gengar, Snorlax, Wailord, plus the repaired Grafaiai, Milotic, Corviknight, Clamperl, Ampharos and Incineroar |
| Rendered on **Bedrock** (Galaxy S25, Bedrock 1.26.51, pack pushed by Geyser) | ✅ verified: Pikachu |
| Species scale (Cobblemon `baseScale`) | ✅ applied |
| Idle and walk animations | ✅ taken from each species' Cobblemon poser |
| Regional forms, shinies, look-at, blink and battle animations | ❌ not yet |
| Gameplay (spawning, catching, battles, party, PC) | ❌ not started. `/bm spawn <species>` only |

![six species on vanilla Java](docs/screenshots/java-vanilla-lineup.png)

*Wailord, Snorlax, Gengar, Charizard, Bulbasaur and Pikachu on an unmodded Java 26.3 client.*

| Cobblemon mod (Fabric client) | This repo (vanilla Java client) |
|---|---|
| ![Cobblemon mod](docs/screenshots/cobblemon-mod-reference.png) | ![cobblemon-link](docs/screenshots/java-vanilla-pikachu-front.png) |

The Pikachu comparison was taken before the species scale was applied.

**Download sizes for players** (once, then cached): Java resource pack about 35 MB, Bedrock pack about 16 MB.

## Server load

Measured with 100 animated Pikachu and one viewer. Full details are in [bench/RESULTS.md](bench/RESULTS.md).

| | Tick time (50 ms = lag) | Total server CPU |
|---|---|---|
| This approach (Paper 26.3 + BetterModel) | 4.6 ms | 284% |
| Cobblemon mod (Fabric 1.21.1) | 17.6 ms | 43% |

The main thread does well. The cost is moved to async threads that stream bone animation to every viewer, so per-player Pokémon caps are needed.

## Try it

You need Docker. Accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) before running.

1. Download `cobblemon-link-<version>.zip` from [Releases](https://github.com/kahngjoonkoh/cobblemon-link/releases) and unzip it.
2. Start the server:
   ```bash
   cd cobblemon-link-<version>/server
   ./setup.sh                       # downloads the pinned, hash-checked plugins and installs the models
   docker compose up -d             # first boot generates configs (takes about a minute)
   docker compose down && ./setup.sh && docker compose up -d
   ```
3. Connect. Java: `<host>:25566` (bound to localhost by default; edit `ports` to expose it). Bedrock: `<host>:19133`.
4. Make yourself op (`docker exec cobblemon-link-mc rcon-cli op <name>`), then run `/bm spawn charizard`. Any name from `assets/species.tsv` works.
   `/bm spawn` puts the model on a vanilla husk, which is hostile, and the model is lost on restart. It is a viewer, not gameplay.

**Java players outside this machine** need a public URL for the resource pack. Put `build.zip` (in `server/data/plugins/BetterModel/`) somewhere public and set `PACK_URL=https://.../build.zip` in `server/.env`. Or set `PACK_BIND=0.0.0.0` to expose the bundled pack server on port 8099. Bedrock players need nothing: Geyser sends their pack.

Bedrock players join through Floodgate with a `.` prefix, so op them by that name (for example `.Steve`) **while they are online**. Opping them before their first join saves the wrong ID.

**Stopping:** use `docker compose stop` or `docker compose down`. The server saves the world and exits within seconds. Don't use `docker kill`, which skips the save.

### Build from source

```bash
tools/build.sh                    # fetch the pinned Cobblemon commit, convert into assets/
tools/build.sh --zip v0.1.0       # also write dist/cobblemon-link-v0.1.0.zip
```

Needs `bash`, `curl`, `python3` and `zip`. To move to a newer Cobblemon, put its commit SHA in `COBBLEMON_COMMIT` and rebuild. `assets/species.tsv` lists each species with its scale, animations and status.

## Repo layout

```
assets/    converted Cobblemon models (built, not committed; CC BY-NC 3.0, see assets/README.md)
tools/     build.sh (fetch + convert + zip), fetch_cobblemon.sh, build_all.py (all species), geo2bb.py (one model)
server/    docker-compose + setup.sh for the prototype server (Paper 26.3)
bench/     load benchmark scripts, raw results and screenshots
docs/      screenshots, including PokeDemo on vanilla clients for comparison
```

Convert a single model by hand:

```bash
python3 tools/geo2bb.py <geo.json> <animation.json> <texture.png> <name> \
  <java_out_dir> <bedrock_out_dir> ground_idle=idle,ground_walk=walk [scale]
```

## Roadmap

1. Gameplay plugin: spawning with per-player caps (about 8–10, see Server load), catching, party and PC. Battles on [Pokémon Showdown](https://github.com/smogon/pokemon-showdown) (MIT), which is what Cobblemon uses.
2. Regional forms, shinies and the look, blink and quirk animations.
3. Measure network bandwidth per viewer, since bone streaming goes through tunnels and mobile data.

## Attribution

- **[Cobblemon](https://gitlab.com/cable-mc/cobblemon)** by the Cobblemon team and contributors.
  - All Pokémon models, animations and textures in `assets/` (and in release zips) are adapted from Cobblemon under **CC BY-NC 3.0**.
  - Cobblemon's code is **MPL-2.0**. No Cobblemon code is included yet. Any MPL file copied in future keeps its license header.
- **[PokeDemo](https://modrinth.com/plugin/pokedemo)** ([source](https://github.com/nn2048/PokedemoProject), **GPL-3.0**) by PtrMC / nn2048.
  - It inspired the Paper-plugin approach and was studied as a reference.
  - No PokeDemo code or assets are included. Its screenshots in `docs/` only document how it looks on vanilla clients.
- **[BetterModel](https://github.com/toxicity188/BetterModel)** (Java model rendering) is a runtime dependency, downloaded by `server/setup.sh` and not redistributed.
- **[GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine)** and **[GeyserUtils](https://github.com/GeyserExtensionists/GeyserUtils)** (Bedrock bridge, both MIT, © zimzaza4) only publish a moving `latest` build. The exact tested jars are mirrored unmodified, with their licenses, on the [`deps-2026-10-15`](https://github.com/kahngjoonkoh/cobblemon-link/releases/tag/deps-2026-10-15) release.
- **[Geyser and Floodgate](https://geysermc.org)**, **[packetevents](https://github.com/retrooper/packetevents)** and **[Paper](https://papermc.io)** are also runtime dependencies and are not redistributed.

## License

- **Code** (`tools/`, `server/`, `bench/`): [MPL-2.0](LICENSE). It was chosen to stay compatible with Cobblemon's code license.
- **Assets** (`assets/`): [CC BY-NC 3.0](assets/LICENSE-CC-BY-NC-3.0.txt), inherited from Cobblemon. **Non-commercial use only.**
- Screenshots in `docs/` and `bench/` show third-party content and are included for documentation only.
