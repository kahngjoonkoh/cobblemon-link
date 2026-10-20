# Parity: cobblemon-link (Paper) vs Cobblemon 1.8.2

How much of Cobblemon the Paper plugin reproduces, feature by feature, checked against Cobblemon **1.8.2**'s source and
data (`data/cobblemon/…` at the commit in [`COBBLEMON_COMMIT`](COBBLEMON_COMMIT)). The game rules are the same for both
clients; the **Java** and **Bedrock** columns say whether a player on that client can see and use the feature (Java means
vanilla Java without the optional mod).

✅ done · 🟡 partly, or done differently · ⬜ not yet · ➖ not applicable (or not possible without a client mod)

Counts: see [the summary at the end](#summary).

## Spawning

| Feature | Cobblemon 1.8.2 | cobblemon-link | Java | Bedrock |
|---|---|---|---|---|
| Spawn data | 3,044 entries in `spawn_pool_world`, 874 species | Cobblemon's own files: 1,844 entries, 662 species (all but fishing and Poké Snack ones, and species without a model) | 🟡 | 🟡 |
| Grounded spawns | 2,007 entries | Anywhere in the zone, caves included | ✅ | ✅ |
| Water spawns (submerged, surface, seafloor) | 434 entries | All three; the pig carrier swims rather than dives | 🟡 | 🟡 |
| Biomes and biome tags | ✓ | Cobblemon's biome tags as a datapack | ✅ | ✅ |
| Time of day, rain, Y level, sky light | ✓ | Checked | ✅ | ✅ |
| Moon phase, thunder, block light, slime chunks | ✓ | Checked | ✅ | ✅ |
| Structures, nearby / base blocks | ✓ | Checked (structure bounding boxes, block tags), and anticonditions of every kind | ✅ | ✅ |
| Rarity buckets | common 94.05, uncommon 5, rare 0.5, ultra-rare 0.2 | Same | ✅ | ✅ |
| Spawn level from the party | Highest level ±5 inside the spawn's range | Same (`Rules.levelRange`) | ✅ | ✅ |
| Caps, distances, despawn | 1 per chunk, 8 per pass, 16–64 blocks; aging despawner | Same | ✅ | ✅ |
| Shinies | 1/8192, shiny textures | Same | ✅ | ✅ |
| Regional forms, features, gender | ✓ | Spawn properties and species features | ✅ | ✅ |
| Herds, alphas (1.8.0) | ✓ | — | ⬜ | ⬜ |
| Influences (Sweet Incense, Poké Snacks, Saccharine log, bait) | ✓ | — | ⬜ | ⬜ |
| Wild model and animations | ✓ | Cobblemon's models: BetterModel (Java), GeyserModelEngine (Bedrock) | ✅ | ✅ |

## Battles

| Feature | Cobblemon 1.8.2 | cobblemon-link | Java | Bedrock |
|---|---|---|---|---|
| Engine | Cobblemon's Showdown fork (`showdown.zip`) | The same build, with Cobblemon's format and bag item scripts | ✅ | ✅ |
| Starting a wild battle | Send a party Pokémon at it (R) | Right-click or hit it | 🟡 | 🟡 |
| Wild AI | `RandomBattleAI` | Random legal choices | ✅ | ✅ |
| Singles vs wild | ✓ | ✓ | ✅ | ✅ |
| Doubles, triples, multi, royal | ✓ | — | ⬜ | ⬜ |
| PvP challenges, spectating | ✓ | `/challenge <player>` (32 blocks, clickable accept), singles with Fight / Switch / Forfeit, EXP for the winner; no spectating | 🟡 | 🟡 |
| NPC trainers (`StrongBattleAI`) | ✓ | — | ⬜ | ⬜ |
| Fleeing | Walk 32 blocks away | Run, or walk 24 blocks away | 🟡 | 🟡 |
| Battle UI (fight / switch / bag / run, tiles, log) | Mod screen | Cobblemon's tile art (Java), Cobblemon-styled forms (Bedrock), HP bars | 🟡 | 🟡 |
| Your Pokémon in the world during battle | ✓ | Sent out beside you from a 3D ball | ✅ | ✅ |
| Battle animations | ✓ | idle, physical/special, recoil, faint | ✅ | ✅ |
| Bag items (potions, revives, ethers, status cures, X items) | 11 bag item effects, used from the inventory on a party Pokémon | Same: right-click the item, pick the Pokémon; Cobblemon's bag item scripts run in its Showdown and take the turn | ✅ | ✅ |
| Held items in battle | ~80 | Every item Showdown knows (207, berries and gems included) plus Lucky Egg, Exp. Share, Power items, Everstone; given by right-clicking the item | ✅ | ✅ |
| Weather, terrain, abilities, status | Showdown | Showdown | ✅ | ✅ |
| Status, HP and PP kept after battle | ✓ | ✓ (packed into Showdown like Cobblemon's `packTeam`) | ✅ | ✅ |
| EXP | Gen 5 scaled × `experienceMultiplier` 2, Lucky Egg / trade / evolve / friendship bonuses, Exp. Share | Same | ✅ | ✅ |
| EVs | Gen 8 EV gain, 252 / 510 caps | Same; in stats and battles | ✅ | ✅ |
| Friendship | Base friendship, level-ups, fainting, items, walking | Base, level-ups (+3/+2), fainting (−1), X items (+1), Friend Ball; shown in the Summary; no berries or walking | 🟡 | 🟡 |

## Capture and Poké Balls

| Feature | Cobblemon 1.8.2 | cobblemon-link | Java | Bedrock |
|---|---|---|---|---|
| Formula | Gen 9 with Gen 8 level modifiers, ball and status bonuses, critical capture | Cobblemon's `CobblemonCaptureCalculator`, with critical captures from the Pokédex count | ✅ | ✅ |
| Shakes and capture animation | 3D ball, up to 3 shakes | Same timings, Cobblemon's ball model | ✅ | ✅ |
| Out-of-battle throw | Direct capture at ×0.5; a sleeping Pokémon wakes on a miss | ×0.5; it stays | ✅ | ✅ |
| Ball in battle | A miss uses your turn | Same (a forced pass) | ✅ | ✅ |
| Ball types (Great, Ultra, Quick, Dusk, Net, Heal… and ancient balls) | 48 | All 48 with `PokeBalls.kt`'s modifiers and effects; in flight they use the Poké Ball's 3D model; the Lure Ball has no fishing to boost | 🟡 | 🟡 |
| Apricorn trees and ball crafting | ✓ | Cobblemon's ball recipes; apricorns drop from leaves instead of growing on apricorn trees | 🟡 | 🟡 |
| Can't catch owned or busy Pokémon | ✓ | ✓ | ✅ | ✅ |

## Pokémon data and progression

| Feature | Cobblemon 1.8.2 | cobblemon-link | Java | Bedrock |
|---|---|---|---|---|
| Species, stats, types, catch rate, EXP groups | ✓ | From Cobblemon's species files | ✅ | ✅ |
| Level-up moves, the 5th-move prompt | ✓ | ✓ | ✅ | ✅ |
| Benched moves, relearning | ✓ | — | ⬜ | ⬜ |
| TMs, TM Machine, Type Gems | 334 TMs | All 334 TMs (type art), taught to Pokémon whose TM learnset has them; the TM Machine block makes them from a Blank TM and Cobblemon's ingredients; gems from amethyst clusters; no unlocking | 🟡 | 🟡 |
| Egg and tutor moves | ✓ | — | ⬜ | ⬜ |
| Natures, mints | ✓ | Random nature; 21 mints change the effective nature | ✅ | ✅ |
| IVs | ✓ | ✓ | ✅ | ✅ |
| Hidden abilities | From snacks / Ability Patch | — | ⬜ | ⬜ |
| Level evolutions | ✓ | ✓, evolved from the Summary like Cobblemon | ✅ | ✅ |
| Stone, trade, friendship, held-item, time, biome, move evolutions | ✓ | Items (stones, apples, Link Cable…) evolve at once; friendship, time, biome, held item, moves, party, stats, moon, weather become the pending evolution; no trades yet, nor defeat / use-move / distance / structure ones | 🟡 | 🟡 |
| Party of 6 | ✓ | ✓ | ✅ | ✅ |
| PC (40 × 30, sort, filter, release, names, wallpapers) | ✓ | ✓ | ✅ | ✅ |
| PC block | ✓ | Placed from its item (Cobblemon's model: BetterModel / GeyserModelEngine), right-click opens the PC | ✅ | ✅ |
| Healing (Healing Machine, passive) | Machine; 5% every 60 s; fainted wake at 20% after 300 s; statuses wear off | The Healing Machine block (no charge to wait for), beds too; passive healing, faint recovery and status expiry as Cobblemon | 🟡 | 🟡 |
| Starter selection | Starter screen | Cobblemon's screen, 27 starters | ✅ | ✅ |
| Party HUD (left side, EXP / level-up notices) | ✓ | ✓, `/partyhud` | ✅ | ✅ |
| Summary (info, moves, stats, nickname, evolve) | ✓ | ✓, with EVs, friendship, held item and mint; no markings | 🟡 | 🟡 |
| Pokédex (seen / caught, entries, forms) | ✓ | `/pokedex`; no regional dexes or scanner | 🟡 | 🟡 |
| Sending out / following | ✓ | Send out the lead | 🟡 | 🟡 |
| Riding (1.7+) | 13 ride settings | — | ⬜ | ⬜ |
| Shoulder mounting | ✓ | — | ⬜ | ⬜ |
| Pasture | ✓ | The Pasture block: party Pokémon put out roam around it, taken back from its chest menu (up to 16) | 🟡 | 🟡 |
| Breeding, eggs | Not in 1.8.2 | — | ➖ | ➖ |
| Size variation | ✓ | — | ⬜ | ⬜ |
| Marks (1.7+) | 168 | — | ⬜ | ⬜ |
| Cosmetic items | 30 | — | ⬜ | ⬜ |

## Interaction and world

| Feature | Cobblemon 1.8.2 | cobblemon-link | Java | Bedrock |
|---|---|---|---|---|
| Trading | ✓ | `/trade <player>`, a chest of both parties: offer, ready, swap; trade evolutions on arrival; party only (no PC) | 🟡 | 🟡 |
| Fishing (Poké Rods, bait, Lure) | 48 rods, 603 spawn entries | All 48 rods (cast model on Java); 600 fishing spawns with Lure level and rod type; 85% Pokémon per bite, lobbed in under 90 kg; Lure Ball; no bait | 🟡 | 🟡 |
| NPCs and dialogues | ✓ | — | ⬜ | ⬜ |
| Pokémon interactions (feeding, items on Pokémon) | 167 | — | ⬜ | ⬜ |
| Cries and emotes | ✓ | — | ⬜ | ⬜ |
| Fossils, Fossil Analyzer, Restoration Tank | 15 | The 15 fossils (from mining stone) revive in the Restoration Tank block after 12 minutes, one or a pair, and come out wild; the Analyzer is decoration | 🟡 | 🟡 |
| Berries and berry trees | 70 | Held berries work in battle; berries drop from sweet berry bushes; no berry trees, mulch or out-of-battle berry effects | 🟡 | 🟡 |
| Apricorns | ✓ | The 7 apricorns, from leaves | 🟡 | 🟡 |
| Campfire pot cooking, seasonings, Poké Snacks | 84 seasonings | — | ⬜ | ⬜ |
| Medicine (potions, revives, status cures, ethers) | ✓ | All of them, in and out of battle, with Cobblemon's art | ✅ | ✅ |
| Vitamins, EXP candies, Rare Candy | ✓ | Vitamins (+10 EVs), feathers (+1), EXP candies, Rare Candy | ✅ | ✅ |
| Evolution stones and ores | ✓ | The items work; no ores or other ways to find them yet (`/pokeitem`) | 🟡 | 🟡 |
| Held items | ~80 | Given by right-click (the old one comes back); no taking without a swap | 🟡 | 🟡 |
| Display Case, Gilded Chests, Pokédex items | ✓ | — | ⬜ | ⬜ |
| Recipes (crafting, furnace, smithing, brewing stand) | ~700 | 347 of them (every one whose ingredients are items: balls, medicine, held items, mints, rods); mint leaves and leeks drop from grass | 🟡 | 🟡 |
| Structures (ruins, shipwrecks, habitats, villages) | ✓ | — | ⬜ | ⬜ |

## Languages

| Feature | Cobblemon 1.8.2 | cobblemon-link | Java | Bedrock |
|---|---|---|---|---|
| Multi-language (each player's client language, en_us fallback) | 29 languages | Cobblemon's 29 lang files plus the plugin's own keys (en_us, ko_kr), rendered per player: species, moves, abilities, types, natures, stats, items, the battle, item use, trading, challenges, EXP / evolution messages, and the Summary, PC, Pokédex and move-learning screens (Java's art fonts fall back to Minecraft's font for Korean/CJK). Still English: the starter screen's art, some PC and Pokédex art labels, the Bedrock party HUD (ASCII field) | 🟡 | 🟡 |

## Commands

| Cobblemon | cobblemon-link | Java | Bedrock |
|---|---|---|---|
| `/spawnpokemon` (`pokespawn`) | `/pokespawn <species> [level]`, no properties | 🟡 | 🟡 |
| `/givepokemon` (`pokegive`) | `/pokegive <species> [level]` | 🟡 | 🟡 |
| `/healpokemon` (`pokeheal`) | `/pokeheal` (own party) | 🟡 | 🟡 |
| `/pc` | `/pc` | ✅ | ✅ |
| `/openstarterscreen` | `/starter` | 🟡 | 🟡 |
| `/pokedex` | `/pokedex` | ✅ | ✅ |
| `/stopbattle` | `/stopbattle <player>` | ✅ | ✅ |
| `/pokemonedit`, `/levelup`, `/teach`, `/held_item`, `/friendship`, `/takepokemon`, `/pctake` | All, for a named player (`/pokemonedit` takes level, shiny, nature, ability, gender, nickname, held_item, friendship) | ✅ | ✅ |
| `/pokebox`, `/boxcount`, `/renamebox`, `/clearparty`, `/clearpc`, `/pcsearch` | `/pokebox`, `/pokeboxall`, `/boxcount`, `/clearparty`, `/clearpc`; box names from the PC screen; no `/pcsearch` | 🟡 | 🟡 |
| `/givetm`, marks, NPC commands | `/pokeitem tm:<move>` gives a TM; no marks or NPCs | 🟡 | 🟡 |
| `/checkspawn` | `/checkspawn <player> [position type]`: what can spawn there now, by bucket | ✅ | ✅ |
| (`/give` of Cobblemon items) | `/pokeitem <item> [amount]` (admin) | ✅ | ✅ |

## Summary

94 rows; Java and Bedrock are level.

| | ✅ | 🟡 | ⬜ | ➖ |
|---|---|---|---|---|
| Java | 44 | 30 | 19 | 1 |
| Bedrock | 44 | 30 | 19 | 1 |

## Suggested order

1. **Battle engine:** Cobblemon's own Showdown fork, so status, PP, held items and bag items work like Cobblemon; EXP ×2, EVs, friendship.
2. **Items:** Cobblemon's items on both clients (medicine, balls, stones, candies, vitamins), the Bag in battle, items used on Pokémon.
3. **Capture:** Cobblemon's capture formula and the ball types.
4. **Evolutions:** stones, friendship, time, held items, trade.
5. **Spawning:** caves, water, moon phase, structures.
6. **Later:** PvP, NPC trainers, trading, TMs, fishing, riding, blocks and structures.
