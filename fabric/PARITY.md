# Fabric parity with the Paper plugin

Every user-facing feature of `plugin/` (the Paper product, including the uncommitted work in the main checkout on
2026-10-20: party HUD, Bedrock form fixes, 3D Poké Balls, battle animations), and where the Fabric product stands.

On Fabric the game itself is **real Cobblemon 1.8.2**: spawning, battles (Showdown inside Cobblemon), catching,
EXP, evolution, storage and the Pokédex are Cobblemon's own. So the Fabric work is the bridge that shows all of it to
players without the mod: vanilla Java (1.21.1, resource pack only) and Bedrock (Geyser + Floodgate). Players with
the Cobblemon mod see Cobblemon itself.

✅ done (evidence in `evidence/`) · 🟡 partly · ⬜ not yet · ➖ not needed on Fabric (Cobblemon does it, or Paper-only)

## Joining and packs

| Feature (Paper) | Fabric | Status |
|---|---|---|
| Vanilla Java joins with no mods | PolyMc + `SendMixin` filters Cobblemon's registries, entities and entity data | ✅ PoC |
| Bedrock joins (Geyser + Floodgate) | Geyser standalone → ViaProxy → Fabric, Floodgate-Fabric on the server | ⬜ |
| Bedrock players told apart (Paper: Floodgate UUID) | Floodgate API | ⬜ |
| Java resource packs sent on join, required | Polymer auto-host; only to vanilla Java (none for Cobblemon-mod or Bedrock players) | ⬜ |
| Bedrock packs (models, UI) pushed by Geyser | Same packs in Geyser standalone's `packs/` | 🟡 PoC (models) |
| Pack manifest versions ≤ 65535 (Bedrock refuses larger) | Reuses `tools/build_bedrock_ui.py` (fixed in the main checkout, uncommitted) | ⬜ |
| Cobblemon-mod players play normal Cobblemon | PolyMc's no-op map + no bridge filtering for them | ✅ PoC |

## Pokémon in the world

| Feature (Paper) | Fabric | Status |
|---|---|---|
| All 2,391 looks (forms, shinies, genders, features) as models, Java | BIL `.bbmodel` per look, from `tools/geo2bb.py` + rotation split | ⬜ |
| Same on Bedrock (GeyserModelEngine) | GeyserUtils `CustomEntityPacket` on a pig carrier | 🟡 PoC (3 species) |
| Species scale (Cobblemon baseScale) | BIL holder scale; Bedrock `CustomEntityDataPacket.scale` | ⬜ |
| Facing (yaw) | Bones follow the entity's body yaw | ⬜ |
| idle / walk animations | From Cobblemon's pose (moving or not) | ⬜ |
| Battle animations: battle_idle, cry, physical, special, recoil, faint | From Cobblemon's battle state and its `PlayPosableAnimationPacket` | ⬜ |
| Bedrock animations | GeyserModelEngine `anim` int properties (sorted animation bits) | ⬜ |
| Right-click or hit a wild Pokémon starts a battle | Interaction hitbox (Java) / pig (Bedrock) → Cobblemon `BattleBuilder.pve` | ⬜ |
| 3D Poké Ball: thrown, capture shakes, send-out arc | Cobblemon's own `EmptyPokeBallEntity` drawn as an item display (Java) / GME `poke_ball` (Bedrock) | ⬜ |
| Wild spawning (Cobblemon's rules) | Cobblemon's spawner | ➖ |
| Sending out the lead (Ctrl while standing still) | Sprint start while still → Cobblemon send-out / recall | ⬜ |
| Companions follow, recalled for battles | Cobblemon | ➖ |

## Screens: vanilla Java (UI pack over chests, action bar and titles)

| Screen (Paper) | Fabric | Status |
|---|---|---|
| UI pack (fonts, art, text shader) | Same art; shaders rewritten for the 1.21.1 client | ⬜ |
| Starter toast + `/starter` screen (27 starters) | Cobblemon's starter list → `StarterHandler.chooseStarter` | ⬜ |
| Battle screen (camera, cursor, Fight / Switch / Catch / Run, move tiles, party, log, hotbar) | Cobblemon battle request → `PlayerBattleActor` responses | ⬜ |
| Hide / reveal battle (Shift, double Shift, `/battle`) | Same controls | ⬜ |
| Party panel (HUD, EXP/level-up/new move/evolve pop-ups, `/partyhud`) | From Cobblemon's party each tick | ⬜ |
| `/party` → Summary (Info / Moves / Stats, nickname, move order, send out / lead / to PC, Evolve) | Cobblemon Pokémon API | ⬜ |
| PC (40 boxes, move/swap, sort, type filter, release, box name and wallpaper, wallpaper unlocks) | Cobblemon `PCStore` | ⬜ |
| Pokédex (seen/caught, entries, search, filter, tabs) | Cobblemon Pokédex records | ⬜ |
| Move learning prompt | ➖ Cobblemon benches the move; swap it in from the Summary's Moves tab | ➖ |
| Evolution (ready notice, Evolve button) | Cobblemon `evolutionProxy` | ⬜ |
| Chat-free EXP (shown on the party panel) | Cobblemon's messages for vanilla players go to the panel | ⬜ |

## Screens: Bedrock (forms drawn by the Bedrock UI pack)

| Screen (Paper) | Fabric | Status |
|---|---|---|
| Battle forms (menu, moves, switch) + HP boss bars | Floodgate forms (same titles/MARK kinds) | ⬜ |
| Starter form | | ⬜ |
| Summary form | | ⬜ |
| PC form + box settings form | | ⬜ |
| Pokédex form | | ⬜ |
| Party HUD (title payload `|CL…`) | Same payload from Cobblemon's party | ⬜ |
| Starter prompt in the action bar | | ⬜ |

## Commands

| Command (Paper) | Fabric | Status |
|---|---|---|
| `/starter`, `/party`, `/pokedex`, `/battle`, `/partyhud` | Same names | ⬜ |
| `/pc` (admin) | Cobblemon's `/pc` for mod players; ours for the rest | ⬜ |
| `/pokegive`, `/pokespawn`, `/pokeheal`, `/pokeballs` (admin) | Aliases over Cobblemon's `PokemonProperties` | ⬜ |

## Other

| Feature (Paper) | Fabric | Status |
|---|---|---|
| Beds heal the party | Kept (Cobblemon's healing machine also works) | ⬜ |
| Poké Ball recipe (dye, button, iron) | ➖ Cobblemon's own recipes | ➖ |
| Optional client mod (`client/`) | ➖ Java players with mods use Cobblemon itself | ➖ |
| Plugin API events (`StarterPromptEvent`, `WildBattleEndEvent`) | ➖ Cobblemon's event API | ➖ |
| Quick-action dialog (26.3 dialogs, G key) | ➖ 1.21.1 has no dialogs | ➖ |

## Deployment

| | Fabric | Status |
|---|---|---|
| docker compose: Fabric + Geyser standalone + ViaProxy | `fabric/docker-compose.yml` | 🟡 PoC |
| Gradle build of the mod | `fabric/mod` | 🟡 PoC |
| README section, versions note | `fabric/README.md` | ⬜ |
