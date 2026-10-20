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
| 3D Poké Ball (thrown, shakes, capture/break) | GME `poke_ball` on Cobblemon's `EmptyPokeBallEntity`, animation from its capture state | ✅ the ball keeps its own id (`PolyEntry`; PolyMc had swapped in a snowball), `b9-bedrock-pokeball-model.log`, Java `m3-java-pokeball-flight.png` |
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
| Move animations (cry, physical, special, recoil) | full | From Cobblemon's animation packet | ✅ (`j9`: Rattata's faint after Electro Ball; moves seen on Bedrock in `b8`) |
| 3D Poké Ball | standard | Own renderer on Cobblemon's `EmptyPokeBallEntity` | ✅ `m3-java-pokeball-capture.png` |
| Starter (`/starter`, toast) | basic: chest · standard: art | Cobblemon's StarterHandler; chest a row a region, or the Paper version's starter art | ✅ chest `j2-java-basic-starter-party-dex.png`, art `j5-java-standard-starter-art.png` |
| Party / Summary / PC / Pokédex | basic: chest · standard: art | The Paper version's chest menus on Cobblemon's data; the Paper art screens | ✅ chests `j2-java-basic-starter-party-dex.png`; art: Summary `j6-java-standard-summary-art.png`, PC `j7-java-standard-pc-art.png`, Pokédex `j8-java-standard-pokedex-art.png` (no search, form/gender/shiny toggles; box names only on Bedrock) |
| Battle | basic/standard: chest menu · full: battle screen | BattleUi's menus as a chest with HP boss bars; at full the Paper version's battle screen (held camera, cursor, Cobblemon's tiles, HP tiles, log, Shift to hide, double Shift / `/battle` back) | ✅ chest `j1-java-basic-battle.png`, screen `j9-java-full-battle-screen.png` (ponytail: no hotbar redrawn while the camera is held) |
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

## Cobblemon features beyond Paper

Everything Cobblemon 1.8.2 has with its own screen, interaction or visual (from its client packets, blocks and
entities), and whether players without the mod can see and use it. Bedrock first; the Java column follows the
`vanillaJava` level. ➖ = works already through Cobblemon plus Geyser/PolyMc, nothing to do.

| Cobblemon feature | Bedrock | Vanilla Java | Notes |
|---|---|---|---|
| Cobblemon's 29 languages, per player | ✅ | ✅ | `Lang`: each player's own client language (Geyser reports the Bedrock one), falling back to en_us; server-written text (forms, HUD, chat, names, our chests) from Cobblemon's lang file for that language; every Cobblemon lang file and ours go in the Java pack so the client translates the rest. Our own UI text has keys (`assets/cobblemonlink/lang/en_us.json`), reusing Cobblemon's keys where it has the words. `LangTest`; `l1-bedrock-german-bot.log` (Weide, Alle Zurückrufen, Glumanda), `l1-java-german-pasture.png`. Every screen's labels go through keys (Cobblemon's where they exist, ~160 strings; `l2-bedrock-german-battle.log`). our own `cobblemonlink.*` keys (~110) in English and Korean (`ko_kr.json`, `l3-bedrock-korean-pasture.log`; `LangTest` checks every key is translated), other languages fall back to English for those; 🟡 a few fixed-width labels (Pokédex art stat names, the Bedrock HUD's EXP) stay English |
| Wild spawning everywhere (water, caves, fishing pools, herds, alphas) | ➖ | ➖ | Cobblemon's own spawner |
| Shiny Pokémon (shiny looks) | ✅ | ✅ | the `__shiny` looks; sparkles every 2 s from the server (`Particles`, `c16-java-shiny-sparkle.png`) in place of Cobblemon's client-side sparkle |
| Pokémon cries and other Cobblemon sounds | ✅ | ✅ | `Sounds`: cries and the Poké Ball's sounds played by the server by name; server sounds sent by name; Bedrock pack from `tools/bedrock_sounds.py` (2,152 sounds, 20 MB) — `c2-bedrock-sounds-bot.log` |
| Cobblemon's particles (Snowstorm: moves, evolutions, healing) | 🟡 | 🟡 | `Particles`: the effects the server sends (move keyframes, Poké Balls, pastures, MoLang `particle` calls) become the nearest Minecraft particle by name (`ParticlesTest`, `c16-bedrock-particles.log`). ⬜ effects Cobblemon's client starts itself (shiny sparkle, evolution sequence) and the real Snowstorm look |
| Items in hand and inventory | ✅ | ✅ | PolyMc stand-ins; Bedrock icons via `tools/bedrock_items.py` (`b7`) |
| Using items on a Pokémon (potions, candies, stones, Ethers, PP Up) | ✅ | ✅ | Cobblemon's party / move pickers (`Callbacks`, `c1-bedrock-potion-party-select-bot.log`) |
| Evolution | ✅ | ✅ | Summary's Evolve (`c9-java-held-item-evolution.log`); a Pokémon whose look changes (evolution, form, shiny) gets a short evolution scene: Cobblemon's evolution sound, 3 s of light gathering round the old look, a flash, the new look with a burst (`c17-java-evolution-scene.png`, `c17-bedrock-look-change.log`); ⬜ Cobblemon's own cutscene camera and Snowstorm effects |
| Move learning, benched moves, move relearning | ✅ | ✅ | Summary swaps benched moves; TM machine as a chest (`Menus`) with its widgets as buttons: choose the TM (cycles your unlocked TMs through Cobblemon's SetActiveTM handler, `c5-java-tm-machine-choose.png`) and Start — a TM printed end to end (`c5-java-tm-printing.log`, `c5-java-tm-printed.png`); Bedrock gets the same chest through Geyser. teaching a TM: use it on your Pokémon (Cobblemon's item interaction, `c5-java-tm-teach.log`) |
| Held items (give / take) | ✅ | ✅ | `Wheel`: Shift + right-click (Bedrock: sneak + tap) your Pokémon → Change / Take held item through Cobblemon's InteractPokemonHandler (`c9-java-held-item-evolution.log`, `c9-bedrock-wheel-held-item.log`) |
| Pokémon interaction wheel (shoulder, ride, held item, cosmetic) | ✅ | ✅ | `Wheel` (form / chest) → Cobblemon's handler; opens on Java (`c9-java-interaction-wheel.png`) and Bedrock (`c9-bedrock-wheel-held-item.log`); shoulder and ride work (rows below) |
| Riding Pokémon (1.7+) | ✅ | ✅ | Wheel → Ride (Cobblemon's mount); Java sits on an invisible armor stand scaled to the Pokémon under its id; Cobblemon moves rides on the rider's client, so `Riding` moves it on the server from Minecraft's player input (walk where you look, jump; up/down for flyers and swimmers) — `c11-java-riding.log`, `c11-java-riding-arcanine.png`, `RidingTest`. Bedrock: the same server movement steered by its forward input (`c11-bedrock-riding.log`). ⬜ Cobblemon's per-behaviour handling (stamina, sprint, gliding) |
| Pokémon on your shoulder | ✅ | ✅ | Wheel → Shoulder (Cobblemon's); `Shoulders` draws it from Minecraft's shoulder data: Java a half-size model on the shoulder (`c10-java-shoulder-pidgey.png`), Bedrock the GeyserModelEngine model on a carrier following the shoulder (`c10-bedrock-shoulder-bot.log`); ⬜ Cobblemon's shoulder pose animations |
| Sending out / recalling (R key) | 🟡 | ✅ | Summary's Send out / Recall; Sneak + swap hands (F) as Cobblemon's R key through its SendOutPokemonHandler (`c13-java-sendout-key.log`, `c13-java-sendout-key.png`); Bedrock: the same once the swap-hands control is bound (not tried) |
| PvP battle challenges, multi battles, spectating | ✅ | ✅ | `Social`: right-click (tap) a player → singles / doubles / triples challenge; the challenge form / chest; Cobblemon's challenge handlers; a Java ↔ Bedrock singles battle done (`c7-pvp-java-bedrock.log`, `c7-java-pvp-battle.png`, `c6-java-player-menu.png`). the player menu also offers Spectate (target in a battle: Bedrock HP bars and the log in the action bar `c19-bedrock-spectating.log`, Java the full battle overlay `c19-java-spectating.png`) and Team up (multi battles) with the team request as a form/chest (`c20-bedrock-team-up.log`) and Battle (multi, 2 vs 2): a Java + Bedrock team against two Bedrock players (`c20-multi-battle.log`; Java's panels show every Pokémon on each side, `c20-java-multi-battle.png`); a move with several targets asks which (`c20-java-multi-target.png`); answering turns you to face the asker (Cobblemon only accepts answers to someone you look at); Catch hidden outside wild battles |
| Trading | ✅ | ✅ | `Social`: right-click (tap) a player → Trade; the request, the trade screen read from Cobblemon's ActiveTrade (offer, accept, cancel), Cobblemon's trade handlers; Java ↔ Bedrock trade done end to end (`c6-trade-java-bedrock.log`, `c6-java-trade-screen.png`); ⬜ Cobblemon's trade animation |
| NPCs (Professor, trainers) and their dialogues | ✅ | ✅ | NPCs drawn like Pokémon with Cobblemon's own NPC models and skins (`tools/npc_models.py`; Java item displays + name, Bedrock GME: `c12-java-npc-models.png` vs `c12-cobblemon-client-npc-reference.png`, `c12-bedrock-npc-models.log`), clicks reach the NPC; without a model, a player-shaped stand-in; `Dialogues`: Cobblemon's dialogue pages, choices and text answers through its dialogue handlers — Bedrock forms (`c12-bedrock-npc-dialogue.log`), Java chests (`c12-java-npc-dialogue.png`, `c12-java-npc-villager.png`). Java types text answers on a sign editor shown only to them (`Signs`, `c12-java-dialogue-text.log`). ⬜ player-chosen NPC skins ("variable" textures), NPC walk poses (Cobblemon's are procedural) |
| Fishing with Poké Rods (bobber, bites, reeling) | ✅ | ✅ | Cobblemon's fishing runs; its bobber sent as Minecraft's hook owned by the angler (the client drops a hook without one), no name tag (`c8-java-poke-rod-bobber.png`, `c8-bedrock-bobber-bot.log`); ⬜ the Poké Ball-coloured bobber model |
| Healing machine, PC, pasture, display case, fossil analyzer, restoration tank, monitor, TM machine, campfire pot blocks | ✅ | ✅ | `Furniture`: Cobblemon's block models over a barrier stand-in (`PolyEntry`), following the block state; Java an item display (`f1-java-machine-blocks.png`, `f1-java-pasture-two-halves.png`, vs `f1-cobblemon-client-reference.png`), Bedrock a GeyserModelEngine model from `tools/bedrock_blocks.py` (`f2-bedrock-machine-blocks.log`); clicking the Bedrock model uses the block (`f3-bedrock-click-pc-model.log`). ⬜ the block-entity extras (healing machine's balls, display case item, tank fluid) |
| Pasture block screen | ✅ | ✅ | `Pasture`: on Cobblemon's `OpenPasturePacket`, the pastured Pokémon, recall one / all, put one out from the linked PC, through Cobblemon's pasture handlers; Bedrock forms (`c3-bedrock-pasture-bot.log`: out and back), Java chests (`c3-java-pasture-chest.png`) |
| Fossils and the resurrection machine | 🟡 | 🟡 | block interactions work through PolyMc; the revived Pokémon is a normal one |
| Berries, apricorns, mints, saplings, gem clusters (blocks and growth) | ✅ | ✅ | Berry bushes, apricorns and mints drawn with Cobblemon's own models by growth stage (`Furniture`, multipart blockstates too) over a walk-through stand-in, with a click box for Java (`c14-java-berry-bushes.png`, `c14-bedrock-plants-bot.log`); growth and harvesting are Cobblemon's. flowers and fruit on a grown bush at Cobblemon's growth points, from its berry geometries (`Berries`: Java item models made at startup, `c14-java-berry-fruit.png`; Bedrock `clberry_*` models, `c14-bedrock-berry-fruit.log`); no berry has a leaf tint in 1.8.2. saplings (and potted), gem and tumblestone clusters and buds the same way (`c14-java-clusters.png`, `c14-bedrock-saplings-clusters.log`) |
| Campfire pot cooking, Poké Snacks, seasonings | ✅ | ✅ | `Menus`: Cobblemon's cooking pot menu shown as a chest with its slots renumbered both ways (grid, seasonings, result, a lid button through Cobblemon's lid handler); the pot drawn on Minecraft's campfire (`Furniture`). Java `c4-java-cooking-pot-*.png`, `c4-java-pot-on-campfire.png`; Bedrock `c4-bedrock-cooking-pot-bot.log`. ⬜ cooking progress bar, Poké Snack block visuals |
| Apricorn boats, signs, gilded chests | ✅ | ✅ | Apricorn boats (and chest boats) shown and ridden as Minecraft's (oak) boats — `c15-java-apricorn-boats.png`, `c15-java-boat-ride.log`; Bedrock through Geyser the same packets. Apricorn / saccharine signs as Minecraft's jungle / cherry signs keeping their text, gilded chests in Cobblemon's own look: its Bedrock chest geometry in each of the 7 colours, turned by facing, as an item display on Java and a `clb_<colour>_gilded_chest` model on Bedrock, still opening as the Gilded Chest (`Furniture`, `Berries.model`, `c21-java-gilded-chests.png`, `c21-java-gilded-chest-open.png`, `c21-bedrock-gilded-chests.log`). ⬜ the apricorn wood look of signs and boats. Sign text also right after its chunk loads (`c15-java-sign-after-join.png`) |
| Pokédex (all of it: forms, genders, scanner) | ✅ | ✅ | ours shows seen/caught and entries; the scanner: right-click (tap) a Pokémon holding a Pokédex → Cobblemon's scan, then our Pokédex at its entry (`c18-bedrock-pokedex-scan.log`); Cobblemon's regional Pokédexes (Bedrock: the page counter opens a dropdown, `c18-bedrock-regional-dex.log`; Java art: the button left of the tabs cycles them); `/dex` as well as `/pokedex` (Cobblemon's own /pokedex is operator-only and hides ours). form and gender views: the entry's picture cycles the species' forms and female look (Bedrock: tap it, `c18-bedrock-dex-forms.log`; Java art: click it) |
| Toasts (new Pokédex entries, ...) | ✅ | 🟡 | action bar (Java only with the UI pack) |
| Starter selection | ✅ | ✅ | Cobblemon's StarterHandler |
| Battles vs wild Pokémon, catching, EXP, levels | ✅ | ✅ | see above |
| Mega, Z, Dynamax, Tera gimmicks | ➖ | ➖ | no items unlock them in 1.8.2 |
