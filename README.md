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

**Free and non-commercial, for everyone.** cobblemon-link is free and open source, and it will stay that way. Anyone can play, on vanilla Java, Java with the optional mod, or Bedrock, and anyone can host it. There are no paid perks, stores or premium versions. Cobblemon's art is licensed for non-commercial use only (see [License](#license)), so servers running cobblemon-link must not sell anything that uses it.

## Current state: playable core

Built from Cobblemon `main` at commit [`910551e`](https://gitlab.com/cable-mc/cobblemon/-/commit/910551ee70bd2853532ea782a20420fef475ce4b) (2026-10-15, after 1.8.2), pinned in [`COBBLEMON_COMMIT`](COBBLEMON_COMMIT).

### Gameplay (v0.3)

How this compares with Cobblemon 1.8.2, feature by feature: [docs/COVERAGE.md](docs/COVERAGE.md).

Everything below was played through on a vanilla Java 26.3 client. Every screen is Cobblemon's: the battle screen, starter, PC, Summary and Pokédex. On vanilla Java the server draws them from a small resource pack (`assets/ui-pack.zip`) over chests, dialogs and the HUD. Bedrock players get the same screens as restyled forms through Geyser. Java players can also install the optional client mod (`client/`), which draws the screens itself, like Cobblemon does. See [Screens compared](#screens-compared). **The gameplay hasn't been tested on a Bedrock device yet.**

| Feature | How it works |
|---|---|
| **Starter** | New players get Cobblemon's "Check out the starters!" toast; `/starter` opens Cobblemon's starter screen with all 27 starters (Gen 1–9). You get the starter at Lv.10, once per player (as Cobblemon: no items with it; `starter.balls` can add Poké Balls). |
| **Wild spawns** | Built from Cobblemon's spawn pools: 1,066 entries for 579 species. They are checked against biome (Cobblemon's biome tags, shipped as a datapack), time of day, rain, sky light and height. A rarity bucket is picked (94.4/5/0.5/0.1, close to Cobblemon's 94.05/5/0.5/0.2), and wild Pokémon are capped per player (default 8). |
| **Battles** | Right-click (or tap, on Bedrock) a wild Pokémon. Cobblemon's flow: Fight / Switch / Catch / Run, then type-coloured move tiles with PP, or the party. Both Pokémon's tiles (portrait, HP), the battle log and your hotbar stay on screen; your own Pokémon is sent out beside you. Wild Pokémon pick random moves. |
| **Catching** | Throw a Poké Ball at a wild Pokémon, in or out of battle (Catch in the battle menu steps aside so you can throw). Uses the main-series catch formula: catch rate, HP left, and status bonuses. |
| **Party and PC** | `/party` opens Cobblemon's Summary, which holds the party, and the PC: 40 boxes of 30 (`pc.boxes`), opened with the Summary's **Open PC** button; `/pc` is for admins, as in Cobblemon. Move and swap between boxes and the party, sort a box, filter by type, release (with a confirm; never your last Pokémon), rename a box and pick its wallpaper. HP carries over between battles. |
| **EXP and growth** | Gen 5+ EXP formula and all six growth curves. Level-up moves are learned while there's room; with four moves, you pick one to forget (or don't learn it). As in Cobblemon, EXP, level-ups, new moves and "ready to evolve" show on the party panel on the left (`/partyhud` hides it, like Cobblemon's O key), not in chat. Level-based evolutions (310 species): as in Cobblemon, the Pokémon says it's ready and you press Evolve in its Summary. |
| **Summary** | Opened from the party: Info, Moves and Stats tabs, nickname (click the name card), Send out / Lead / To PC, and the party to switch between. On the Moves tab, click two moves to swap their order. A Pokémon that's ready to evolve gets an **Evolve** button, as in Cobblemon. |
| **Pokédex** | `/pokedex`: seen (met in battle) and caught counts, every species in national order, and entries for caught ones. On vanilla Java: search, a filter, gender / shiny / form buttons for the ones you've seen, and Abilities, Base stats, Moves and Evolutions tabs (click a tab again for its next part). |
| **Healing** | Lie down in a bed. |
| **Poké Balls** | Crafted vertically from red dye, a stone button and an iron ingot (makes 4). |

Not yet: trainers and gyms, items and held items, stone, trade and friendship evolutions, water, flying and cave spawns, and PvP battles. The full list of gaps is in [docs/COVERAGE.md](docs/COVERAGE.md).

| Battle menu | Fight |
|---|---|
| ![battle menu](docs/screenshots/gameplay-battle-menu.png) | ![fight](docs/screenshots/gameplay-battle-log.png) |
| **Party** | **Catching** |
| ![party](docs/screenshots/gameplay-party.png) | ![catch](docs/screenshots/gameplay-catch.png) |

### Screens compared

Each screen in four clients:
- **Cobblemon:** Cobblemon 1.8.2 itself (Fabric 1.21.1).
- **Java:** cobblemon-link on a vanilla Java client (resource pack only).
- **Java + mod:** cobblemon-link on Java with the optional client mod (`client/`).
- **Bedrock:** cobblemon-link on Bedrock, through Geyser and Floodgate.

Click an image for full size.

| Screen | Cobblemon | Java | Java + mod | Bedrock |
|---|---|---|---|---|
| Starter | ![](docs/screenshots/compare/starter-cobblemon.png) | ![](docs/screenshots/compare/starter-java.png) | ![](docs/screenshots/compare/starter-java-mod.png) | ![](docs/screenshots/compare/starter-bedrock.png) |
| Battle | ![](docs/screenshots/compare/battle-cobblemon.png) | ![](docs/screenshots/compare/battle-java.png) | ![](docs/screenshots/compare/battle-java-mod.png) | ![](docs/screenshots/compare/battle-bedrock.png) |
| Moves | ![](docs/screenshots/compare/moves-cobblemon.png) | ![](docs/screenshots/compare/moves-java.png) | ![](docs/screenshots/compare/moves-java-mod.png) | ![](docs/screenshots/compare/moves-bedrock.png) |
| PC | ![](docs/screenshots/compare/pc-cobblemon.png) | ![](docs/screenshots/compare/pc-java.png) | ![](docs/screenshots/compare/pc-java-mod.png) | ![](docs/screenshots/compare/pc-bedrock.png) |
| Summary | ![](docs/screenshots/compare/summary-cobblemon.png) | ![](docs/screenshots/compare/summary-java.png) | ![](docs/screenshots/compare/summary-java-mod.png) | ![](docs/screenshots/compare/summary-bedrock.png) |
| Move learning¹ | ![](docs/screenshots/compare/move-learning-cobblemon.png) | ![](docs/screenshots/compare/move-learning-java.png) | ![](docs/screenshots/compare/move-learning-java-mod.png) | ![](docs/screenshots/compare/move-learning-bedrock.png) |
| Evolve | ![](docs/screenshots/compare/evolve-cobblemon.png) | ![](docs/screenshots/compare/evolve-java.png) | ![](docs/screenshots/compare/evolve-java-mod.png) | ![](docs/screenshots/compare/evolve-bedrock.png) |
| Pokédex | ![](docs/screenshots/compare/pokedex-cobblemon.png) | ![](docs/screenshots/compare/pokedex-java.png) | ![](docs/screenshots/compare/pokedex-java-mod.png) | ![](docs/screenshots/compare/pokedex-bedrock.png) |
| Party bar | ![](docs/screenshots/compare/party-bar-cobblemon.png) | ![](docs/screenshots/compare/party-bar-java.png) | ![](docs/screenshots/compare/party-bar-java-mod.png) | Same panel, drawn by the Bedrock UI pack (no screenshot yet; ¾ size from the top on touch screens) |

¹ Cobblemon has no prompt for this. A new move that doesn't fit goes to the benched moves, and you swap it in from the Summary's Moves tab (shown). cobblemon-link asks which move to forget instead, because it has no benched moves yet.

### How it differs from Cobblemon

Vanilla clients can't run Cobblemon's own screens or models, so some things work differently. Gameplay gaps (missing features) are listed in [docs/COVERAGE.md](docs/COVERAGE.md); this list is about things that exist but look or behave differently.

**Battle screen and controls**

| | Cobblemon | cobblemon-link |
|---|---|---|
| Battle screen (Java) | Its own GUI, mouse cursor | Cobblemon's art drawn by the server through the resource pack. The camera is held still and your mouse moves a cursor the server draws, so the cursor lags by your ping. Left click selects. |
| Battle screen (Bedrock) | — | A Bedrock form styled with Cobblemon's tiles. HP shows in boss bars. |
| Hide the battle | **R** ("Press R to move around.") | Java: **Shift** ("Press Shift to move around."). Bedrock: the **"Tap here to move around."** button, or the back gesture. |
| Back to the battle | **R** ("You need to choose an action. Press R.") | Java: **double-tap Shift** ("…Double-tap Shift."). Bedrock: action bar "You need to choose an action. Tap the Pokémon." Both: tap or right-click the Pokémon, or `/battle`. Only while standing on the ground. |
| Catch tile | Steps aside for a thrown ball | Same. A failed throw doesn't use your turn yet. |
| Battle log | Right-hand pane | Same on Java. On Bedrock, the last 4 lines sit in the battle form; nothing goes to chat. |
| Hotbar and hearts during a battle (Java) | The game's own | Redrawn by the server, because the client hides them while the camera is held (no durability bars or enchantment glint). |
| Portraits | The live 3D model, animated | Pre-rendered pictures from the model's first idle frame, without eye/blink layers. |
| Starter prompt | Toast: "Check out the starters! Press the "M" key" | The same toast with "Type /starter" (Bedrock: action bar text). It stays until you have a Pokémon. |
| Starter screen (Java) | Its own screen; every pixel takes the mouse | Cobblemon's screen drawn by the resource pack over a 4-row chest. A vanilla client only takes clicks on a chest's 18 px slot grid, so Cobblemon's art is reshaped to sit on it: tiles are a slot's 16 px (Cobblemon's are 25) with a close-up portrait, a tile can span two slot rows, and the region boxes are 72 px. 4 regions in view, with ▲/▼ on the list's footer instead of a scroll wheel. The panel is 202 × 202 (Cobblemon's 239 × 197), centred on the chest: the card window and description box are 18 px narrower and the list 19; both panels sit lower so their bottoms meet just above the buttons. "I choose you!" is 4 slots (72 px) wide. No back button: Esc or E closes it. "Starter Selection" is regular weight (the narrower tab can't fit bold); the half-size text is spaced as Cobblemon's. The model and background are still pictures. Empty slots light up under the cursor, and items in your own inventory would show through its lower half. An exact copy would need clicks anywhere (the battle screen's server-drawn cursor, which lags by your ping) or a client mod. |
| Starter screen (Bedrock) | — | Cobblemon's layout as a restyled form (no slot grid on Bedrock): its 25 px tiles, card, positions and back button; three regions a page with ▲/▼ instead of a scroll wheel; no scrollbar thumb. Not yet seen on a device. |
| In-world screens (Java) | The world shows behind Cobblemon's screens | On this server no screen dims the world behind it, and chests have no "Inventory" label in English (both so the starter screen looks like Cobblemon's). |
| Party | Cobblemon's screen | A chest menu with portrait icons. |
| PC (Java) | Its own screen: drag Pokémon with the cursor, an animated model, Filter and sort under the box | Cobblemon's PC art over a 5-row chest, every button on a chest slot. Click a Pokémon to select it, then click a slot to move it there (or a Pokémon to swap), across boxes too. ◀ box name ▶ sits under the box, sort and type filter under the party; the selected Pokémon shows in a strip under it all (still pictures; held items show an empty slot). Text uses a smaller copy of Unifont, since the client can't scale text. Your own inventory is hidden from your screen while the PC is open. |
| PC (Bedrock) | — | The same slots as a chest of item icons (no PC art yet). |
| Box name and wallpaper | Click the name to type; a wallpaper button with a scroll list | Clicking the name opens a dialog (a form on Bedrock) with the name and a wallpaper choice. Biome wallpapers unlock as in Cobblemon, by standing in a cave, forest, ocean, Nether or End biome; locked ones show as ???. The alpha wallpaper can't unlock yet (nothing spawns alpha). |
| Sending out your Pokémon | Thrown out in a ball, animated | Appears beside you (2.5 blocks toward the opponent, 1.5 to the right) and stands still. |
| Poké Ball | Cobblemon's item and 3D ball | Same art on Java. On Bedrock the item has Cobblemon's icon but a thrown ball looks like a snowball. |

**World**

| | Cobblemon | cobblemon-link |
|---|---|---|
| Wild Pokémon | Cobblemon's entity and AI | A silent pig carries the model: pig-sized hitbox, pig wandering (without following food or breeding). Like other animals, they rest when no player is within 32 blocks. |
| Spawning | Cobblemon's spawner | The same algorithm and defaults (a pass per player every second, 1 Pokémon per chunk, levels from your party, Cobblemon's despawning), for surface land spawns only. |

**Performance changes (not visible)**

Java players see each Pokémon as many small display entities that the server moves, so the server does the animating work that Cobblemon's clients do themselves. To keep that cheap:
- **Fewer keyframes:** animations are converted with only the keyframes needed to stay within 1°, 0.05 px and 1% scale of Cobblemon's curve at every tick (median 21 → 3 per second). The client interpolates between them.
- **Fewer parts:** model parts that never move on their own are merged into their parent (median 41 → 25 displays per Pokémon).
- **No duplicate stream for Bedrock:** Bedrock players get no Java animation stream. Their client animates the native model itself, exactly as authored.
- **Server tuning:** BetterModel sends each update as one bundle. Pokémon are tracked to 80 blocks, Cobblemon's own range. The JVM uses Aikar's flags and compact object headers, and the server pauses when nobody is online.

### Models

| | Status |
|---|---|
| **All 2,391 looks of Cobblemon's 905 modelled species** converted, 0 failures: base forms, shinies, female models, regional forms (Alolan, Galarian, Hisuian, Paldean, Valencian) and feature looks (Unown letters, Vivillon wings, Shellos seas, Alcremie, …) | ✅ (3 species lose one or two animation channels to typos in Cobblemon's own files; see [assets/README](assets/README.md)) |
| Rendered on **vanilla Java 26.3** (resource pack, one "Proceed" click) | ✅ verified: Pikachu, Bulbasaur, Charizard, Gengar, Snorlax, Wailord, plus the repaired Grafaiai, Milotic, Corviknight, Clamperl, Ampharos and Incineroar |
| Rendered on **Bedrock** (Galaxy S25, Bedrock 1.26.51, pack pushed by Geyser) | ✅ verified: Pikachu |
| Species scale (Cobblemon `baseScale`) and idle/walk animations | ✅ |
| Look-at, blink, battle animations, animated textures (first frame shown) and glow/overlay layers | ❌ not yet |

![six species on vanilla Java](docs/screenshots/java-vanilla-lineup.png)

*Wailord, Snorlax, Gengar, Charizard, Bulbasaur and Pikachu on an unmodded Java 26.3 client.*

**Download sizes for players** (once, then cached): Java resource packs about 85 MB (models 66 MB, UI and portraits 17 MB), Bedrock packs about 45 MB.

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
3. Connect. Java: `<host>:25566` (bound to localhost by default; set `JAVA_BIND=0.0.0.0`, or a Tailscale IP, in `server/.env` to expose it). Bedrock: `<host>:19133`.
4. Run `/starter`, then right-click (or tap) a wild Pokémon.

**Optional client mod (Java).** Players who add `cobblemon-link-client-<version>.jar` (from the same release) and [Fabric API](https://modrinth.com/mod/fabric-api) to a Fabric 26.3 client get Cobblemon's screens drawn by their own client: the battle menus, the starter screen and the PC at Cobblemon's own layout, with no cursor lag and nothing in the inventory slots. Without it everything works as above. The mod only draws: the server decides everything, and on other servers it does nothing.

| Command | Who | |
|---|---|---|
| `/starter`, `/party`, `/pokedex`, `/battle` | everyone | `/party` opens Cobblemon's Summary (a party menu on Java without the UI pack); `/battle` reopens the battle menu if you closed it |
| `/pokegive <species> [level]`, `/pokespawn <species> [level]`, `/pokeheal`, `/pokeballs [n]`, `/pc` | ops | for testing; `/pc` as Cobblemon's |

At most **12 players** (Java and Bedrock together) can be online at once, which fits a 4-core host at Cobblemon's spawn density; change it with `MAX_PLAYERS=` in `server/.env`.

Settings (spawning, which defaults to Cobblemon's own values, rarity weights, sidecar address) are in `server/data/plugins/CobblemonLink/config.yml`. Party and PC data is saved per player under `players/` in the same folder.

**Java players outside this machine** need a public URL for the resource pack. Put `build.zip` (in `server/data/plugins/BetterModel/`) somewhere public and set `PACK_URL=https://.../build.zip` in `server/.env`. Or set `PACK_BIND=0.0.0.0` to expose the bundled pack server on port 8099. Bedrock players need nothing: Geyser sends their pack.

Bedrock players join through Floodgate with a `.` prefix, so op them by that name (for example `.Steve`) **while they are online**. Opping them before their first join saves the wrong ID.

**Stopping:** use `docker compose stop` or `docker compose down`. The server saves the world and exits within seconds. Don't use `docker kill`, which skips the save.

### Build from source

```bash
tools/build.sh                    # fetch the pinned Cobblemon commit, convert into assets/, build the plugin
tools/build.sh --zip v0.3.0       # also write dist/cobblemon-link-v0.3.0.zip
```

Needs `bash`, `curl`, `python3`, `zip` and Docker (the plugin builds with Gradle and JDK 25 inside the `gradle:9.1-jdk25` image). To move to a newer Cobblemon, put its commit SHA in `COBBLEMON_COMMIT` and rebuild. `assets/species.tsv` lists each species with its scale, animations and status.

## Repo layout

```
plugin/    the Paper plugin (Java 25): spawning, battles, catching, party/PC, EXP. Tests: plugin/src/test
client/    the optional Fabric client mod: draws the server's screens (battle, starter, PC) as Cobblemon's
sim/       Pokémon Showdown battle sidecar (Node), with a self-check in sim/test.js
assets/    built, not committed: models and ui-pack.zip (CC BY-NC 3.0), gamedata.json and biome-tag datapack (MPL-2.0)
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

Gameplay order follows [docs/COVERAGE.md](docs/COVERAGE.md#suggested-order):

1. Bedrock device test of the gameplay (menus, tapping to battle, throwing balls).
2. Quick alignments with Cobblemon: EXP ×2, failed throws making the Pokémon flee, a ball in battle using a turn.
3. Cobblemon's capture formula with shakes, then the main ball types.
4. Cave and water spawns, structures and moon phase.
5. Bag and held items, EVs and friendship.
6. Stone, trade and friendship evolutions; Healing Machine and PC blocks.
7. Later: PvP, NPC trainers and gyms, TMs, fishing.
8. Measure network bandwidth per viewer, since bone streaming goes through tunnels and mobile data.

## Attribution

- **[Cobblemon](https://gitlab.com/cable-mc/cobblemon)** by the Cobblemon team and contributors.
  - All Pokémon models, animations and textures in `assets/` (and in release zips) are adapted from Cobblemon under **CC BY-NC 3.0**, as is the battle GUI art (move, menu and party tiles, type icons) in `assets/ui-pack.zip`.
  - Cobblemon's repository is **MPL-2.0**. `assets/gamedata.json` (species stats, learnsets, evolutions, spawn pools, English names) and the biome-tag datapack are derived from Cobblemon's data files under MPL-2.0. No Cobblemon code is included.
- **[PokeDemo](https://modrinth.com/plugin/pokedemo)** ([source](https://github.com/nn2048/PokedemoProject), **GPL-3.0**) by PtrMC / nn2048.
  - It inspired the Paper-plugin approach and was studied as a reference.
  - No PokeDemo code or assets are included. Its screenshots in `docs/` only document how it looks on vanilla clients.
- **[BetterModel](https://github.com/toxicity188/BetterModel)** (Java model rendering) is a runtime dependency, downloaded by `server/setup.sh` and not redistributed.
- **[GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine)** and **[GeyserUtils](https://github.com/GeyserExtensionists/GeyserUtils)** (Bedrock bridge, both MIT, © zimzaza4) only publish a moving `latest` build. The exact tested jars are mirrored unmodified, with their licenses, on the [`deps-2026-10-15`](https://github.com/kahngjoonkoh/cobblemon-link/releases/tag/deps-2026-10-15) release. GeyserModelEngine's Paper plugin comes from our public fork instead ([release `1.0.9-cl.2`](https://github.com/kahngjoonkoh/GeyserModelEngine/releases/tag/1.0.9-cl.2), branch `cobblemon-link-perf`): upstream 1.0.9 plus its open [PR #76](https://github.com/GeyserExtensionists/GeyserModelEngine/pull/76) by ScarletMC (`server/patches/`), which stops it re-sending every model's scale and colour to Bedrock players 50 times a second. It goes back to upstream once that is merged.
- **[Pokémon Showdown](https://github.com/smogon/pokemon-showdown)** (MIT) runs every battle. It is installed from npm into the sidecar image and not redistributed.
- **[Geyser and Floodgate](https://geysermc.org)**, **[packetevents](https://github.com/retrooper/packetevents)** and **[Paper](https://papermc.io)** are also runtime dependencies and are not redistributed.

## License

- **Code** (`plugin/`, `sim/`, `tools/`, `server/`, `bench/`): [MPL-2.0](LICENSE). It was chosen to stay compatible with Cobblemon's code license.
- **Models, animations and textures** (`assets/java`, `assets/bedrock`, `assets/ui-pack.zip`): [CC BY-NC 3.0](assets/LICENSE-CC-BY-NC-3.0.txt), inherited from Cobblemon. **Non-commercial use only.**
- **Game data** (`assets/gamedata.json`, `assets/datapack`): MPL-2.0, derived from Cobblemon's data.
- Screenshots in `docs/` and `bench/` show third-party content and are included for documentation only.
