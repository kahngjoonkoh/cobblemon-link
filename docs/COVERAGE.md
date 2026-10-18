# Coverage: cobblemon-link vs Cobblemon 1.8.2

How much of Cobblemon's gameplay cobblemon-link reproduces, checked against Cobblemon **1.8.2** (2026-10-11, the latest tag) by reading its source at that tag. cobblemon-link's data comes from Cobblemon `main` at the commit in [`COBBLEMON_COMMIT`](../COBBLEMON_COMMIT) (2026-10-15).

✅ same as Cobblemon · 🟡 partly, or done differently · ❌ missing · ➖ not possible without a client mod

This page covers the core first: spawning, battling, capture, Pokémon data and commands. Blocks, items and structures are listed at the end for a later pass.

## Spawning

| Feature | Cobblemon 1.8.2 | cobblemon-link | |
|---|---|---|---|
| Spawn data | 3,044 entries in `spawn_pool_world`, 874 species | Uses Cobblemon's own files: 1,066 entries, 579 species | 🟡 |
| Grounded spawns | 2,007 entries | Surface only: entries needing `canSeeSky: false` (caves) are skipped | 🟡 |
| Water spawns (submerged, surface, seafloor) | 434 entries | — | ❌ |
| Fishing (Poké Rods, bait, Lure level) | 603 entries | — | ❌ |
| Biomes and biome tags | ✓ | Checked through Cobblemon's biome tags, shipped as a datapack | ✅ |
| Time of day, rain, Y level, sky light | ✓ | Checked | ✅ |
| Structures, moon phase, nearby or base blocks, slime chunks, block light, thunder | ✓ | Entries with these conditions are skipped (`structures` alone is 314 entries) | ❌ |
| Rarity buckets | common 94.05, uncommon 5, rare 0.5, ultra-rare 0.2, boss 0.25; one bucket per pass | Same (`config.yml`) | ✅ |
| Spawn level | Party's highest level ±5, limited to the spawn's level range | Same rule (`Rules.levelRange`, tested) | ✅ |
| Caps | 1 per chunk, up to 8 per pass, every 20 ticks, 8 blocks apart, 16–64 blocks from the player | Same: an 8×16×8 zone 16–64 blocks out (ahead of a moving player), 1 per chunk over the 3×3 chunks around it, up to 8 per pass, 8 blocks apart | ✅ |
| Despawn | 32/96 near/far distance, 600–3600 tick age | Same rule (CobblemonAgingDespawner), checked every 10 s | ✅ |
| Herds (`pokemon-herd`, alpha leads) | ✓ | — | ❌ |
| Alpha Pokémon (1.8.0) | ✓ | — | ❌ |
| Shinies | 1/8192 | 1/8192 (`Mon.shinyRate`), with Cobblemon's shiny textures and portraits | ✅ |
| Regional forms and other aspects | ✓ | Spawn properties (alolan, flower=white, region_bias=hisui), species features with their defaults, gender; forms battle with their own types, stats, abilities and moves | ✅ |
| Gender | ✓ | From the species' male ratio | ✅ |
| Influences (Sweet Incense, Poké Snacks, Saccharine log, Habitat Block, bait) | ✓ | — | ❌ |
| Wild Pokémon model and animations | ✓ | Cobblemon's models: BetterModel on Java, GeyserModelEngine on Bedrock | ✅ |

## Battling

| Feature | Cobblemon 1.8.2 | cobblemon-link | |
|---|---|---|---|
| Engine | Pokémon Showdown | Pokémon Showdown 0.11.11 in its own container | ✅ |
| Starting a wild battle | Send out a party Pokémon at it (R key) | Right-click or hit it; there are no keybinds without a mod | 🟡 |
| Wild AI | `RandomBattleAI` | Showdown's random AI | ✅ |
| Singles vs wild | ✓ | ✓ | ✅ |
| Doubles, triples, multi, free-for-all | ✓ | — | ❌ |
| PvP challenges | ✓ (32 blocks) | — | ❌ |
| NPC trainers (`StrongBattleAI`) | ✓ | — | ❌ |
| Spectating | ✓ (64 blocks) | — | ❌ |
| Fleeing | Walk 32 blocks away | Run button, or walk 24 blocks away (`battle.max-distance`) | 🟡 |
| Battle UI | Mod screen: portraits, tiles, bag | Vanilla dialog with Cobblemon's tile art on Java, plain form on Bedrock, HP boss bars | 🟡 ➖ |
| Pokémon visible in the battle | Your sent-out Pokémon stands facing the wild one | Only the wild one; yours isn't in the world | ❌ |
| Bag items (potions, revives, ethers, status cures, X items) | ✓ | Poké Ball only | ❌ |
| Held items | About 80 work in battle | — | ❌ |
| Weather, terrain, abilities, status | Showdown | Showdown | ✅ |
| Mega, Z-Moves, Dynamax, Tera | In the engine, but no items unlock them | — | ✅ (none in either) |
| EXP formula | Gen 5 scaled × `experienceMultiplier` **2**, plus Lucky Egg / traded / can-evolve / friendship bonuses, Exp Share 0.5 | Gen 5 scaled; no ×2 multiplier and no bonuses | 🟡 |
| Who gets EXP | Participants, plus the Exp Share | Participants that didn't faint | 🟡 |
| EVs | Gen 8 EV gain | Not awarded (EVs stay 0) | ❌ |
| Friendship | ✓ | — | ❌ |
| HP and status persist after battle | ✓ | HP persists; status is cleared | 🟡 |

## Capture

| Feature | Cobblemon 1.8.2 | cobblemon-link | |
|---|---|---|---|
| Formula | Gen 9 with Gen 8 level modifiers: low-level bonus below Lv13, −2% per level above your Pokémon | Gen 3–4 modified catch rate, without level modifiers | 🟡 |
| Out-of-battle throw | Direct capture at ×0.5 | Direct capture, without the ×0.5 | 🟡 |
| Shakes / critical capture | 4 shake rolls; critical capture scales with Pokédex count | One roll, no shake animation, no critical capture | 🟡 |
| Status bonus | ×2.5 sleep or freeze, ×1.5 others | Same | ✅ |
| Ball in battle | Only when its last Pokémon is left; uses your turn | Any time; a miss doesn't use your turn | 🟡 |
| Failed out-of-battle throw | Ball used up, Pokémon wakes and flees | Ball used up, Pokémon stays | 🟡 |
| Ball types | 30+ (Great, Ultra, Quick, Dusk, Net, Heal…) plus ancient balls | One Poké Ball (×1), crafted from red dye, button and iron ingot | ❌ |
| Apricorn ball crafting | ✓ | — | ❌ |
| Can't catch owned or busy Pokémon | ✓ | ✓ (wild only, not while it's in someone's battle) | ✅ |
| Pokédex (seen / caught) | ✓ | `/pokedex`: seen (met in battle) and caught, Cobblemon's Pokédex screen (Bedrock, client mod; a chest on vanilla Java) with picture, types and entry; no search, forms or info tabs | 🟡 |

## Pokémon data and progression

| Feature | Cobblemon 1.8.2 | cobblemon-link | |
|---|---|---|---|
| Species, base stats, types, catch rate, EXP groups | ✓ | From Cobblemon's species files (896 species) | ✅ |
| Level-up moves | ✓ | ✓; a 5th move asks which move to forget after the battle | ✅ |
| Benched moves / relearning | ✓ | — | ❌ |
| TMs, TM Machine, Type Gems (1.8.0) | ✓ | — | ❌ |
| Natures | ✓ (mints change them) | Random nature; no mints | 🟡 |
| IVs | ✓ | Random 0–31 | ✅ |
| Abilities | Regular plus hidden (from snacks or the log) | Regular only | 🟡 |
| Level evolutions | ✓ | ✓ (310 species) | ✅ |
| Other evolutions (stones, trade, friendship, held item, time, biome, move, …) | ✓ | — | ❌ |
| Party of 6 | ✓ | ✓ | ✅ |
| PC | 40 boxes × 30, PC block | Cobblemon's PC screen (Java; a chest of icons on Bedrock): 40 boxes × 30, move/swap, sort, type filter, release, box names and wallpapers; no PC block | 🟡 |
| Healing | Healing Machine; slow passive healing; fainted Pokémon recover after a while | Sleeping in a bed heals the party | 🟡 |
| Starter selection | Starter screen | `/starter`: Cobblemon's screen (Java: over a chest; Bedrock: a form), all 27 starters, Lv10, once per player | ✅ |
| Sending out, following, riding, shoulder mounting | ✓ | — | ❌ |
| Pasture | ✓ | — | ❌ |
| Breeding / daycare | Not in 1.8.2 | — | ✅ (none in either) |
| Size variation, babies | ✓ | — | ❌ |
| Summary screen (stats, moves, friendship, held item) | Mod screen | Cobblemon's Summary (Bedrock, client mod; a dialog on vanilla Java): Info, Moves, Stats (with IVs and nature), party switching, send out / lead / to PC; no friendship, held items, nickname or move types | 🟡 |

## Commands

| Cobblemon | cobblemon-link | |
|---|---|---|
| `/spawnpokemon` (alias `pokespawn`) | `/pokespawn <species> [level]` | 🟡 no properties (shiny, form…) |
| `/givepokemon` (alias `pokegive`) | `/pokegive <species> [level]` | 🟡 |
| `/healpokemon` (alias `pokeheal`) | `/pokeheal` (your own party only) | 🟡 |
| `/pc` | `/pc` (any player) | ✅ |
| `/openstarterscreen` | `/starter` (any player, once) | 🟡 |
| `/stopbattle` | — (Run, or walking away, ends a battle) | ❌ |
| `/spawnpokemonfrompool`, `/checkspawn`, `/spawnallpokemon` | — | ❌ |
| `/pokemonedit`, `/levelup`, `/teach`, `/held_item`, `/friendship`, `/takepokemon`, `/pctake` | — | ❌ |
| `/pokebox`, `/pokeboxall`, `/boxcount`, `/renamebox`, `/clearparty`, `/clearpc`, `/pcsearch` | Shift-click in `/party` sends one to the PC | ❌ |
| `/pokedex` | `/pokedex` (any player) | ✅ |
| `/givetm`, `/technicalmachine`, marks | — | ❌ |
| NPC commands (`/spawnnpc`, `/npcedit`, `/opendialogue`, …) | — | ❌ |
| — | `/party`, `/battle` (reopen the menu), `/pokeballs` (admin) | extra |

## Blocks, items and structures (not started)

None of these exist yet in cobblemon-link apart from the Poké Ball:
- **Apricorns and balls:** apricorn trees and ball crafting, the other ball types.
- **Machines:** Healing Machine, PC block, Pasture, TM Machine, Fossil Analyzer and Restoration Tank, Display Case, Campfire Pot, Habitat Block.
- **Items:** berries (69), mints, evolution stones and ores, held items (~80), medicine, vitamins, EXP candies, Poké Bait and Snacks, Poké Rods, Pokédexes, Gilded Chests.
- **Structures:** ruins, shipwreck coves, fishing boats, 49 habitats, Pokémon village pieces.

## Suggested order

1. **Quick alignments:** EXP ×2, a failed throw making the Pokémon flee, and a ball in battle using a turn.
2. **Capture:** Cobblemon's capture formula with shakes, then the main ball types (Great, Ultra, Quick, Dusk, Net, Heal…) as custom items.
3. **Spawning:** caves (`canSeeSky: false`), water spawns, structures and moon phase.
4. **Battling:** bag items (potions, revives), held items, EVs and friendship, and sending out your own Pokémon into the battle.
5. **Progression:** stone, trade and friendship evolutions; the Healing Machine and PC blocks.
6. **Later:** PvP challenges, NPC trainers, TMs, fishing, the remaining blocks and structures.
