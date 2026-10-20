# Fabric parity with the Paper plugin

Every user-facing feature of `plugin/` (the Paper product, including the uncommitted work in the main checkout on
2026-10-20: party HUD, Bedrock form fixes, 3D Poké Balls, battle animations), and where the Fabric product stands.

On Fabric the game itself is **real Cobblemon 1.8.2**: spawning, battles (Showdown inside Cobblemon), catching,
EXP, evolution, storage and the Pokédex are Cobblemon's own. The Fabric work is the bridge that shows all of it to
players without the mod. Players with the Cobblemon mod see Cobblemon itself.

**Priorities (2026-10-20):** Bedrock comes first and should be as close to real Cobblemon as possible. Vanilla Java
is a taster: how much it gets is the server's choice, `vanillaJava` in `config/cobblemonlink/config.json`.

✅ done (evidence in `evidence/`) · 🟡 partly · ⬜ not yet · ➖ not needed on Fabric (Cobblemon does it, or Paper-only)

## Vanilla Java levels

The server draws everything a vanilla Java client sees, so each level costs CPU and bandwidth. Bedrock players
always get everything (their client animates the models itself); Cobblemon-mod players cost nothing extra.

| Level | What vanilla Java players get | Cost per Java player |
|---|---|---|
| `off` | A disconnect message: install Cobblemon or join on Bedrock | none |
| `basic` | Join and play. Pokémon are still models (turned once a second), screens are plain chest menus, battles a chest menu | spawn packets only (~31 displays a Pokémon, once); no per-tick work |
| `standard` | Animated models (idle, walk, battle idle, faint, 5 poses a second); the screens with Cobblemon's art (starter, Summary, PC, Pokédex) | ~31 display updates × 5/s per visible Pokémon (~10 KB/s each); bone maths per Pokémon on the server thread |
| `full` (default) | Everything, as the Paper version: 10 poses a second, Cobblemon's move animations (cry, physical, special, recoil), the battle screen (held camera, cursor, tiles, redrawn hotbar) and the party HUD | ~31 × 10/s per visible Pokémon (~20 KB/s each), plus a HUD/battle overlay every tick |

Gating lives in `Config.Level` (tested in `ConfigTest`). Evidence: `evidence/levels-off-disconnect.png`.

## 1. Bedrock (first)

| Feature (Paper) | Fabric | Status |
|---|---|---|
| Bedrock joins (Geyser + Floodgate) | Geyser standalone → ViaProxy (handshake kept) → Fabric with Floodgate-Fabric 2.2.4-b38 | ✅ `m1-bedrock-floodgate-bot.log` |
| Bedrock players told apart | Floodgate API (`Clients`) | ✅ |
| Cobblemon's text in English (Geyser only knows Minecraft's keys) | Chat, action bar, titles and item names written out on the server (`Lang`) | ✅ `m1-bedrock-text.log` |
| Models: every look (forms, shinies, genders, features) | GeyserModelEngine custom entity `modelengine:<look>` on a pig, look from Cobblemon's aspects | ✅ `m2-bedrock-models-anims.log` (protocol) |
| Species scale | GeyserUtils `CustomEntityDataPacket.scale` | ✅ (protocol) |
| idle / walk / battle_idle / faint | GeyserModelEngine `anim` int properties, from the server's state | ✅ (protocol) |
| Move animations: cry, physical, special, recoil; battle_idle in battle | Cobblemon's `PlayPosableAnimationPacket` → the same properties | ✅ `b8-bedrock-battle-animations-bot.log` (protocol: Charmander's physical bit on Slash) |
| Tap or hit a wild Pokémon starts a battle | pig click/hit → Cobblemon `BattleBuilder.pve` (`InteractMixin`) | ✅ `b1-bedrock-battle-bot.log` |
| 3D Poké Ball (thrown, shakes, capture/break) | GME `poke_ball` on Cobblemon's `EmptyPokeBallEntity`, animation from its capture state | 🟡 same code as Java (`m3-java-pokeball-capture.png`); not seen on a phone |
| Bedrock UI pack (forms art, HUD) pushed by Geyser | `bedrock-ui.mcpack` from `tools/build_bedrock_ui.py` in Geyser's `packs/` | ✅ ("packs offered: 3") |
| Pack versions ≤ 65535 | Reuses the main checkout's fix (uncommitted there); our items pack keeps it too | 🟡 |
| Cobblemon item icons in the Bedrock inventory (Paper: the Poké Ball) | `tools/bedrock_items.py`: Geyser custom item mappings for PolyMc's stand-ins (595 items) + an icon pack | ✅ `b7-bedrock-item-icons-bot.log` |
| Battle forms (menu, moves, switch, catch, run) + HP boss bars, prompt, `/battle` | Floodgate forms with the Paper titles (MARK 1-3), answers sent as Cobblemon's Showdown responses | ✅ `b1-bedrock-battle-bot.log` (a won battle) |
| Starter form, `/starter` | Cobblemon's StarterHandler (its starter packet opens the form, `chooseStarter` takes the pick) | ✅ `b3-bedrock-starter-bot.log` |
| Summary form (tabs, nickname, move order + benched moves, send out, lead, to PC, Evolve), `/party` | Cobblemon Pokémon API | ✅ `b4-bedrock-summary-bot.log` |
| PC form + box settings (move/swap, sort, filter, release, name, wallpaper) | Cobblemon `PCStore`, opened by Cobblemon (PC block or `/pc`) | ✅ `b5-bedrock-pc-bot.log` (opened with `/pc`; the PC block path not tried) |
| Pokédex form, `/pokedex` | Cobblemon Pokédex records | ✅ `b6-bedrock-pokedex-bot.log` |
| Party HUD (title payload `|CL…`), EXP / level-up / move / evolution notices, `/partyhud` | Same payload from Cobblemon's party; Cobblemon's EXP chat lines dropped | ✅ `b2-bedrock-party-hud-bot.log` |
| Starter prompt (action bar) | Until Cobblemon's `starterSelected` | ✅ |

## 2. Shared (all players without the mod)

| Feature (Paper) | Fabric | Status |
|---|---|---|
| Vanilla Java joins with no mods | PolyMc + `SendMixin` | ✅ PoC |
| Cobblemon-mod players play normal Cobblemon, no pack | PolyMc's no-op map; pack only for vanilla Java | ✅ `m1-mod-client-no-pack.log` |
| Packet audit: Cobblemon entities, entity data, block entities, menus, payloads | Filtered for players without the mod (`SendMixin`) | ✅ (effects, sounds, villager professions checked: fine) |
| Wild spawning, catching, EXP, evolution, storage | Cobblemon | ➖ |
| Commands `/starter`, `/party`, `/pokedex`, `/battle`, `/partyhud` | | ✅ (Bedrock; Java by level below) |
| Admin `/pokegive`, `/pokespawn`, `/pokeheal`, `/pokeballs`, `/pc` | Cobblemon's own (same names); `/pokeballs` added | ✅ |
| Beds heal the party | `bedsHeal` in the config (default on) | ✅ |

## 3. Vanilla Java, by level

| Feature (Paper) | Level | Fabric | Status |
|---|---|---|---|
| `off`: friendly disconnect | off | `Bridge` join check | ✅ `levels-off-disconnect.png` |
| Resource pack sent on join, required | basic | Polymer auto-host, in game (8 s in: a 1.21.1 toast race can kill the download) | ✅ `m1-java-pack-in-game.png` |
| Models of every look | basic | Own renderer: one item model per bone (`tools/build_models.py`), Polymer virtual entities | ✅ `m2-java-gengar.png` |
| Facing (yaw) | basic | | ✅ |
| Click a Pokémon (hitbox) | basic | Interaction element redirected to the entity; with a Poké Ball in hand it throws it | ✅ |
| idle / walk / battle_idle / faint | standard | Keyframes posed on the server | ✅ `m2-java-charizard-walking.png` |
| Move animations (cry, physical, special, recoil) | full | From Cobblemon's animation packet | 🟡 wired |
| 3D Poké Ball | standard | Own renderer on Cobblemon's `EmptyPokeBallEntity` | ✅ `m3-java-pokeball-capture.png` |
| Starter (`/starter`, toast) | basic: chest · standard: art | Cobblemon's StarterHandler; chest a row a region | 🟡 chest ✅ `j2-java-basic-starter-party-dex.png`; art ⬜ |
| Party / Summary / PC / Pokédex | basic: chest · standard: art | The Paper version's chest menus on Cobblemon's data (party + options, PC with sort/filter/release/wallpaper, Pokédex) | 🟡 chests ✅ `j2-java-basic-starter-party-dex.png`; art ⬜ |
| Battle | basic/standard: chest menu · full: battle screen | BattleUi's menus as a chest, HP boss bars | 🟡 chest ✅ `j1-java-basic-battle.png` (all levels for now); battle screen ⬜ |
| Party HUD, chat-free EXP | full | The Paper version's overlay through the 1.21.1 text shader, from Cobblemon's party | ✅ `j4-java-full-party-hud.png` |
| UI pack for the 1.21.1 client (fonts, pictures, text shader) | standard | `tools/ui_pack.py`, merged into Polymer's pack | ✅ `j3-java-ui-pack-toast.png` (Cobblemon's starter toast) |

## Not needed on Fabric

| Paper feature | Why |
|---|---|
| Move learning prompt | Cobblemon benches the move; swap it in from the Summary's Moves tab |
| Poké Ball recipe | Cobblemon's own recipes |
| Optional client mod (`client/`) | Java players with mods use Cobblemon itself |
| Plugin API events | Cobblemon's event API |
| Quick-action dialog (26.3 dialogs) | 1.21.1 has no dialogs |

## Deployment

| | Fabric | Status |
|---|---|---|
| docker compose: Fabric + Geyser standalone + ViaProxy, settings in `.env` | `fabric/docker-compose.yml` | ✅ (the toby test servers run it) |
| Pinned, hash-checked downloads; PolyMc built from source; models and packs | `fabric/tools/setup.sh` | ✅ (run against a scratch data dir) |
| Gradle build of the mod, unit tests | `fabric/tools/build.sh` (`ConfigTest`, `PartyHudTest`) | ✅ |
| README, versions | `fabric/README.md` | ✅ |
