# Fabric parity with the Paper plugin

Every user-facing feature of `plugin/` (the Paper product, including the uncommitted work in the main checkout on
2026-10-20: party HUD, Bedrock form fixes, 3D Poké Balls, battle animations), and where the Fabric product stands.

On Fabric the game itself is **real Cobblemon 1.8.4** (1.8.2 until 2026-11-08): spawning, battles (Showdown inside Cobblemon), catching,
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
| Pack versions ≤ 65535 | Every pack we build takes its version from a hash, kept under 32768 (blocks, items, sounds, text, UI) | ✅ checked 2026-11-09: all five served packs at 1.0.<32768, and the 26.60 client loads them (`r26-bedrock-join-hud-models.png`) |
| Cobblemon item icons in the Bedrock inventory (Paper: the Poké Ball) | `tools/bedrock_items.py`: Geyser custom item mappings for PolyMc's stand-ins (595 items) + an icon pack | ✅ `b7-bedrock-item-icons-bot.log` |
| Battle forms (menu, moves, switch, catch, run) + HP boss bars, prompt, `/battle` | Floodgate forms with the Paper titles (MARK 1-3), answers sent as Cobblemon's Showdown responses | ✅ `b1-bedrock-battle-bot.log` (a won battle) |
| Starter form, `/starter` | ✅ pages, Special (Pikachu, Eevee), pick; the chosen starter stays shown on every page and its name and number fit their tabs (owner's choice, as Cobblemon) | `r26-bedrock-starter-page1.png`, `r26-bedrock-starter-kept-across-pages.png`, `r26-bedrock-starter-title-fits.png`, `-chosen` |
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
| Cobblemon's particles (Snowstorm: moves, evolutions, healing) | ✅ | ✅ | `Particles`: the effects the server sends (move keyframes, Poké Balls, pastures, MoLang `particle` calls) become the nearest Minecraft particle by name, and effects the names don't place (most moves) dust in the effect's own Snowstorm tint (`ParticlesTest`, `c16-bedrock-particles.log`). Effects Cobblemon's client starts itself, from the server: each species' ambient effect from its animations' `particle_effects` (Magcargo/Slugma lava, Gastly's gas, Chinchou, Magnezone…, shiny variants too; `c25-java-ambient-particles.png`), the shiny sparkle in its tint, and the evolution scene in `evo_buildup` / `evo_sparkleburst` colours. ponytail: Minecraft's particle shapes and a fixed burst, not Snowstorm's emitters and curves; drawn at the body, not the locator |
| Items in hand and inventory | ✅ | ✅ | PolyMc stand-ins; Bedrock icons via `tools/bedrock_items.py` (`b7`) |
| 3D Poké Balls in hand | ➖ | 🟡 | Held Poké Balls are 2D in hand for vanilla Java: 1.21.1 item models can't switch by display context. Switch to 3D-in-hand / 2D-in-GUI (`minecraft:select` on `display_context`, with Cobblemon's `item/<ball>_model`) once Cobblemon runs on 1.21.4+. |
| Using items on a Pokémon (potions, candies, stones, Ethers, PP Up) | ✅ | ✅ | Cobblemon's party / move pickers (`Callbacks`, `c1-bedrock-potion-party-select-bot.log`) |
| Evolution | ✅ | ✅ | Summary's Evolve (`c9-java-held-item-evolution.log`); a Pokémon whose look changes (evolution, form, shiny) gets a short evolution scene: Cobblemon's evolution sound, 3 s of light gathering round the old look, a flash, the new look with a burst (`c17-java-evolution-scene.png`, `c17-bedrock-look-change.log`); on Java the owner's view moves to in front of the Pokémon for the scene and comes back after the new look (`Mons.camera`, `c22-java-evolution-camera.png`, `c22-java-evolution-camera-back.png`); ⬜ the camera on Bedrock (Geyser doesn't carry it) and Cobblemon's Snowstorm effects |
| Move learning, benched moves, move relearning | ✅ | ✅ | Summary swaps benched moves; TM machine as a chest (`Menus`) with its widgets as buttons: choose the TM (cycles your unlocked TMs through Cobblemon's SetActiveTM handler, `c5-java-tm-machine-choose.png`) and Start — a TM printed end to end (`c5-java-tm-printing.log`, `c5-java-tm-printed.png`); Bedrock gets the same chest through Geyser. teaching a TM: use it on your Pokémon (Cobblemon's item interaction, `c5-java-tm-teach.log`) |
| Held items (give / take) | ✅ | ✅ | `Wheel`: Shift + right-click (Bedrock: sneak + tap) your Pokémon → Change / Take held item through Cobblemon's InteractPokemonHandler (`c9-java-held-item-evolution.log`, `c9-bedrock-wheel-held-item.log`) |
| Pokémon interaction wheel (shoulder, ride, held item, cosmetic) | ✅ | ✅ | `Wheel` (form / chest) → Cobblemon's handler; opens on Java (`c9-java-interaction-wheel.png`) and Bedrock (`c9-bedrock-wheel-held-item.log`); shoulder and ride work (rows below) |
| Riding Pokémon (1.7+) | ✅ | ✅ | Wheel → Ride (Cobblemon's mount); Java sits on an invisible armor stand scaled to the Pokémon under its id; Cobblemon moves rides on the rider's client, so `Riding` moves it on the server from Minecraft's player input (walk where you look, jump; up/down for flyers and swimmers) — `c11-java-riding.log`, `c11-java-riding-arcanine.png`, `RidingTest`. Bedrock: the same server movement steered by its forward input (`c11-bedrock-riding.log`, `r-bedrock-04-sendout-wheel-ride.log`). Sneak gets off. On Java the head bones are hidden from the rider alone (the first-person camera would sit inside them; the body stays, so F5 shows the mount; `r-java-16-riding-first-person.png`, third person `r-java-17-riding-third-person.png`). ⬜ Cobblemon's per-behaviour handling (stamina, sprint, gliding) |
| Pokémon on your shoulder | ✅ | ✅ | Wheel → Shoulder (Cobblemon's); `Shoulders` draws it from Minecraft's shoulder data: Java a half-size model on the shoulder (`c10-java-shoulder-pidgey.png`), Bedrock the GeyserModelEngine model on a carrier following the shoulder (`c10-bedrock-shoulder-bot.log`); ⬜ Cobblemon's shoulder pose animations |
| Sending out / recalling (R key) | ✅ | ✅ | Summary's Send out / Recall (Bedrock: `r-bedrock-04-sendout-wheel-ride.log`); Sneak + swap hands (F) as Cobblemon's R key through its SendOutPokemonHandler (`c13-java-sendout-key.log`, `c13-java-sendout-key.png`); Bedrock players without a swap-hands control use the Summary |
| PvP battle challenges, multi battles, spectating | ✅ | ✅ | `Social`: right-click (tap) a player → singles / doubles / triples challenge; the challenge form / chest; Cobblemon's challenge handlers; a Java ↔ Bedrock singles battle done (`c7-pvp-java-bedrock.log`, `c7-java-pvp-battle.png`, `c6-java-player-menu.png`). the player menu also offers Spectate (target in a battle: Bedrock HP bars and the log in the action bar `c19-bedrock-spectating.log`, Java the full battle overlay `c19-java-spectating.png`) and Team up (multi battles) with the team request as a form/chest (`c20-bedrock-team-up.log`) and Battle (multi, 2 vs 2): a Java + Bedrock team against two Bedrock players (`c20-multi-battle.log`; Java's panels show every Pokémon on each side, `c20-java-multi-battle.png`); a move with several targets asks which (`c20-java-multi-target.png`); asking and answering turn you to face the other player (Cobblemon only accepts requests and answers to someone you look at; `c22-requests-face-target.log`); Catch hidden outside wild battles |
| Trading | ✅ | ✅ | `Social`: right-click (tap) a player → Trade; the request, the trade screen read from Cobblemon's ActiveTrade (offer, accept, cancel), Cobblemon's trade handlers; Java ↔ Bedrock trade done end to end (`c6-trade-java-bedrock.log`, `c6-java-trade-screen.png`); ⬜ Cobblemon's trade animation |
| NPCs (Professor, trainers) and their dialogues | ✅ | ✅ | NPCs drawn like Pokémon with Cobblemon's own NPC models and skins (`tools/npc_models.py`; Java item displays + name, Bedrock GME: `c12-java-npc-models.png` vs `c12-cobblemon-client-npc-reference.png`, `c12-bedrock-npc-models.log`), clicks reach the NPC; without a model, a player-shaped stand-in; `Dialogues`: Cobblemon's dialogue pages, choices and text answers through its dialogue handlers — Bedrock forms (`c12-bedrock-npc-dialogue.log`), Java chests (`c12-java-npc-dialogue.png`, `c12-java-npc-villager.png`). Java types text answers on a sign editor shown only to them (`Signs`, `c12-java-dialogue-text.log`). ⬜ player-chosen NPC skins ("variable" textures), NPC walk poses (Cobblemon's are procedural) |
| Fishing with Poké Rods (bobber, bites, reeling) | ✅ | ✅ | Cobblemon's fishing runs; its bobber sent as Minecraft's hook owned by the angler (the client drops a hook without one), no name tag (`c8-java-poke-rod-bobber.png`, `c8-bedrock-bobber-bot.log`); ⬜ the Poké Ball-coloured bobber model |
| Healing machine, PC, pasture, display case, fossil analyzer, restoration tank, monitor, TM machine, campfire pot blocks | ✅ | ✅ | `Furniture`: Cobblemon's block models over a barrier stand-in (`PolyEntry`), following the block state; Java an item display (`f1-java-machine-blocks.png`, `f1-java-pasture-two-halves.png`, vs `f1-cobblemon-client-reference.png`), Bedrock a GeyserModelEngine model from `tools/bedrock_blocks.py` (`f2-bedrock-machine-blocks.log`); clicking the Bedrock model uses the block (`f3-bedrock-click-pc-model.log`). ⬜ the block-entity extras (healing machine's balls, display case item, tank fluid) |
| Pasture block screen | ✅ | ✅ | `Pasture`: on Cobblemon's `OpenPasturePacket`, the pastured Pokémon, recall one / all, put one out from the linked PC, through Cobblemon's pasture handlers; Bedrock forms (`c3-bedrock-pasture-bot.log`: out and back), Java chests (`c3-java-pasture-chest.png`) |
| Fossils and the resurrection machine | ✅ built by hand (analyzer, monitor on top, tank beside), fossil in, apples in the tank, the Pokémon grows in the tank, monitor green (timer shortened server-side for the test) | `r26-bedrock-fossil-machine-formed.png`, `r26-bedrock-fossil-running.png`, `r26-bedrock-fossil-revived.png` |
| Berries, apricorns, mints, saplings, gem clusters (blocks and growth) | ✅ | ✅ | Berry bushes, apricorns and mints drawn with Cobblemon's own models by growth stage (`Furniture`, multipart blockstates too) over a walk-through stand-in, with a click box for Java (`c14-java-berry-bushes.png`, `c14-bedrock-plants-bot.log`); growth and harvesting are Cobblemon's. flowers and fruit on a grown bush at Cobblemon's growth points, from its berry geometries (`Berries`: Java item models made at startup, `c14-java-berry-fruit.png`; Bedrock `clberry_*` models, `c14-bedrock-berry-fruit.log`); no berry has a leaf tint in 1.8.2. saplings (and potted), gem and tumblestone clusters and buds the same way (`c14-java-clusters.png`, `c14-bedrock-saplings-clusters.log`) |
| Campfire pot cooking, Poké Snacks, seasonings | ✅ | ✅ | `Menus`: Cobblemon's cooking pot menu shown as a chest with its slots renumbered both ways (grid, seasonings, result, a lid button through Cobblemon's lid handler); the pot drawn on Minecraft's campfire (`Furniture`). Java `c4-java-cooking-pot-*.png`, `c4-java-pot-on-campfire.png`; Bedrock `c4-bedrock-cooking-pot-bot.log`. ⬜ cooking progress bar, Poké Snack block visuals |
| Apricorn boats, signs, gilded chests | ✅ | ✅ | Apricorn boats (and chest boats) shown and ridden as Minecraft's (oak) boats — `c15-java-apricorn-boats.png`, `c15-java-boat-ride.log`; Bedrock through Geyser the same packets. Apricorn / saccharine signs as Minecraft's jungle / cherry signs keeping their text, gilded chests in Cobblemon's own look: its Bedrock chest geometry in each of the 7 colours, turned by facing, as an item display on Java and a `clb_<colour>_gilded_chest` model on Bedrock, still opening as the Gilded Chest (`Furniture`, `Berries.model`, `c21-java-gilded-chests.png`, `c21-java-gilded-chest-open.png`, `c21-bedrock-gilded-chests.log`). Signs and boats keep the nearest Minecraft wood: the client draws them from its own wood textures, and retexturing those would change every real jungle / cherry / oak sign and boat too. Sign text also right after its chunk loads (`c15-java-sign-after-join.png`) |
| Pokédex (all of it: forms, genders, scanner) | ✅ | ✅ | ours shows seen/caught and entries; the scanner: right-click (tap) a Pokémon holding a Pokédex → Cobblemon's scan, then our Pokédex at its entry (`c18-bedrock-pokedex-scan.log`); Cobblemon's regional Pokédexes (Bedrock: the page counter opens a dropdown, `c18-bedrock-regional-dex.log`; Java art: the button left of the tabs cycles them); `/dex` as well as `/pokedex` (Cobblemon's own /pokedex is operator-only and hides ours). form and gender views: the entry's picture cycles the species' forms and female look (Bedrock: tap it, `c18-bedrock-dex-forms.log`; Java art: click it) |
| Toasts (new Pokédex entries, ...) | ✅ | ✅ | Bedrock: the action bar. Java: real toasts, a one-off hidden advancement with Cobblemon's icon and text, removed the next tick (`Toasts`, `c23-java-toast.png`); the header reads Minecraft's "Advancement Made!" |
| Starter selection | ✅ | ✅ | Cobblemon's StarterHandler |
| Battles vs wild Pokémon, catching, EXP, levels | ✅ | ✅ | see above |
| Mega, Z, Dynamax, Tera gimmicks | ➖ | ➖ | no items unlock them in 1.8.2 |

## Regression pass (fresh flat world, 2026-10-20)

Java (vanilla 1.21.1 client) and Bedrock (bot through Geyser), evidence `evidence/r-*`:

| Check | Java | Bedrock |
|---|---|---|
| Join, resource pack, party HUD | ✅ `r-java-01` | ✅ `r-bedrock-01` |
| Starter | ✅ `r-java-02`, `r-java-03` | ✅ `r-bedrock-01` |
| Wild battle (screen / forms), a move, EXP | ✅ `r-java-04` – `06` | ✅ `r-bedrock-01` |
| Catch (battle UI gone on capture) | ✅ `r-java-07-catch-sequence.png` | ✅ `r-bedrock-01` |
| Summary, moves, PC, Pokédex | ✅ `r-java-09` – `12` | ✅ `r-bedrock-02` |
| Player menu, trade | ✅ `r-java-13`, `14` | ✅ `r-bedrock-03` |
| Wheel, ride, get off | ✅ `r-java-15` – `18` | ✅ `r-bedrock-04` |
| Fishing | ✅ `r-java-19` | ✅ `r-bedrock-07` |
| NPC model and dialogue | ✅ `r-java-20`, `21` | ✅ `r-bedrock-05` |
| Healing machine, pasture, cooking pot, TM machine | ✅ `r-java-22` – `26` | ✅ `r-bedrock-06` |

Fixed on the way: the wheel was unreachable with a Poké Ball in hand; the battle UI lingered ~2 s after a catch; the
Java rider saw the inside of their mount's head (now only the head is hidden from them); Sneak gets off a ride;
requests and answers face the other player; the vanilla test client needed a bigger heap (1600 MB).

## Real Bedrock client (26.60.4, Cobblemon 1.8.4, 2026-11-08)

The unmodified Bedrock 26.60 client (mcpelauncher on toby, signed in, no add-ons) on the Fabric test server; every
check is a screenshot of that client, `evidence/r26-*`. The protocol bot passed rows this client showed broken, so a
row counts here only with a screenshot.

| Row | Status | Screenshot (`evidence/`) |
|---|---|---|
| Bedrock joins (Geyser + Floodgate) | ✅ RakNet (NetherNet finds no path from toby and falls back) | `r26-bedrock-join-hud-models.png` |
| Models: every look, species scale | ✅ Mudkip, Pikachu, Zubat, Rattata, Arcanine, Charmeleon | `r26-bedrock-send-out-model.png`, `r26-bedrock-battle-menu.png` |
| idle / walk / battle_idle | ✅ | `r26-bedrock-walk-animation.png`, `r26-bedrock-battle-zubat-anim-a.png` |
| Tap or hit a wild Pokémon starts a battle | ✅ | `r26-bedrock-battle-menu.png` |
| Battle: menu, moves, a turn, switch, run | ✅ | `r26-bedrock-battle-moves.png`, `-turn`, `-switch`, `-switched`, `-run` |
| Catch (prompt, ball, caught, HUD) | ✅ as Cobblemon's Catch (owner's choice): no bag list; the menu closes with Cobblemon's "Throw a Poké Ball at your opponent to capture it." and the player throws a ball themselves. One ball, one throw: Bedrock sends a tap and a use-item, and the second ball was refused with "It's not your turn to act."; a short cooldown after the tap's throw stops it | `r26-bedrock-catch-prompt.png`, `r26-bedrock-catch-thrown.png`, `r26-bedrock-catch-caught.png`, `r26-bedrock-caught-party-hud.png` |
| Battle items and medicine in battle | ✅ as Cobblemon: holding one, tap your own Pokémon in the battle (its attemptItemInteraction): X Attack "Attack rose sharply", the turn taken, one used. Our tap-to-reopen-the-menu no longer catches that tap | `r26-bedrock-battle-item-x-attack.png` |
| Cobblemon item icons | ✅ | `r26-bedrock-item-icon-ultra-ball.png` |
| Starter form, `/starter` | ✅ pages, Special (Pikachu, Eevee), pick | `r26-bedrock-starter-page1.png`, `-select`, `-special-page`, `-eevee`, `-chosen` |
| Summary: Info, Moves, Stats | ✅; the party as Cobblemon 1.8.4's cards (owner's choice): the caught ball, the Pokémon, "Lv. 50" bottom right, name, gender and HP bar, the viewed one in the selected art; a big Pokémon no longer covers its level | `r26-bedrock-summary-info.png`, `-moves`, `-stats`, `r26-bedrock-summary-party-cards.png`, `-switch.png`, vs `r26-java-cobblemon-summary-party.png` |
| Summary: send out, lead, to PC, nickname | ✅ | `r26-bedrock-send-out-model.png`, `r26-bedrock-summary-lead.png`, `-to-pc.png`, `-nickname-form.png`, `-nicknamed.png` |
| Summary: Evolve | ✅ Charmander → Charmeleon | `r26-bedrock-summary-evolve-button.png`, `r26-bedrock-evolved.png` |
| Learning moves on level-up, move swap | ✅ fixed after v1.2.0: Cobblemon 1.8.4 doesn't bench a level-up move when 4 are known, it becomes relearnable; the Summary's Moves tab now lists benched and relearnable moves (as Cobblemon's), and its rows are tappable (they were text only, so a tap did nothing): Eevee 10 → 15 by Rare Candy, Baby-Doll Eyes swapped in for Covet | `r26-bedrock-summary-relearnable-moves.png`, `r26-bedrock-summary-move-swapped.png` |
| TM machine | ✅ opens as a chest with its controls; choosing cycles your unlocked TMs (Cobblemon's handler); crafting: blank TM in chest slot 1, ingredients in 2–6 (Agility: 6 Psychic Gems, 2 Green Mint Leaves), Start, and the TM comes out. Fixed after v1.2.0: the chest kept showing the ingredients after Start (the open menu never shows the result Cobblemon puts in the block), so the machine reopens once it's there | `r26-bedrock-tm-machine.png`, `r26-bedrock-tm-machine-choose.png`, `r26-bedrock-tm-craft-ingredients.png`, `r26-bedrock-tm-crafted.png` |
| Using items on a Pokémon | ✅ Rare Candy, party picker | `r26-bedrock-item-use-party-picker.png`, `r26-bedrock-rare-candy-level-up.png` |
| PC: select, move to a box, release (Yes / No) | ✅ | `r26-bedrock-pc-selected.png`, `-moved-to-box`, `-release-button`, `-release-confirm`, `-released` |
| PC block opens the PC | ✅ | `r26-bedrock-pc-block.png` |
| Pokédex form, `/pokedex` | ✅ | `r26-bedrock-pokedex.png` |
| Party HUD (portraits, names) | ✅ | `r26-bedrock-caught-party-hud.png`, `r26-bedrock-summary-nicknamed.png` |
| Interaction wheel (only Cobblemon's choices) | ✅ Pidgey: Shoulder; Arcanine: Ride | `r26-bedrock-wheel-pidgey.png`, `r26-bedrock-wheel-arcanine-ride.png` |
| Pokémon on your shoulder | ✅ follows the player | `r26-bedrock-shoulder.png`, `r26-bedrock-shoulder-turned.png` |
| Riding | ✅ moves; the rider's view: row below | `r26-bedrock-ride-mounted.png`, `r26-bedrock-ride-moved.png` |
| Shiny looks and sparkles | ✅ | `r26-bedrock-shiny-sparkles.png` |
| NPCs: model and dialogue | ✅ | `r26-bedrock-npc-model.png`, `r26-bedrock-npc-dialogue.png`, `-dialogue-2` |
| Machine blocks drawn (healing machine, PC, TM machine) | ✅ | `r26-bedrock-machine-blocks.png` |
| Healing machine heals the party | ✅ | `r26-bedrock-fainted-hud.png`, `r26-bedrock-healing-machine.png`, `r26-bedrock-healed-hud.png` |
| Fainted Pokémon: forced switch, HUD | ✅ | `r26-bedrock-battle-fainted-switch-move-anim.png`, `r26-bedrock-fainted-hud.png` |
| 3D Poké Ball: thrown, the Pokémon drawn in, breaks free | ✅ | `r26-bedrock-pokeball-throw-sheet.png`, `-thrown`, `-broke-free` |
| Fishing: Poké Rod cast, bobber, bite starts a battle | ✅ | `r26-bedrock-fishing-bobber.png`, `r26-bedrock-fishing-goldeen-battle.png` |
| PC: sort, filter, box name and wallpaper | ✅ | `r26-bedrock-pc-sorted.png`, `-filter-form`, `-filtered`, `-box-settings`, `-box-renamed-wallpaper` |
| Pokédex scanner (tap a Pokémon holding a Pokédex) | ✅ | `r26-bedrock-pokedex-scanner.png` |
| Berries, apricorns, mints, saplings (models) | ✅ | `r26-bedrock-berries-apricorns-pot-chat-fixed.png` |
| Campfire pot cooking menu | ✅ (chest layout, as decided) | `r26-bedrock-cooking-pot.png` |
| Pasture: block, form, put out from the PC | ✅ | `r26-bedrock-pasture-form.png`, `-pick`, `-block` |
| Starter prompt (action bar) | ✅ | `r26-bedrock-starter-prompt.png` |
| Cobblemon's languages per player | ✅ Cobblemon's text in German; our own strings too, since after v1.2.0 (`r26-bedrock-our-strings-german.png`) | `r26-bedrock-language-german.png` |
| Apricorn boats (ride, paddle) | ✅ ridden as a boat; placing from the item didn't on this try | `r26-bedrock-apricorn-boat.png` |
| Toasts / advancements | ✅ "Advancement Made! Consult the Fossil" | `r26-bedrock-fossil-machine-toast.png` |
| Vanilla chat with numbers | ✅ fixed (was "Changed the block at (0), (1), (2)") | `r26-bedrock-chat-numbers-before-fix.png`, `r26-bedrock-berries-apricorns-pot-chat-fixed.png` |
| Display case: put an item in, show it | ✅ tapping it puts the held item in, takes it out, or swaps, by Cobblemon's own handler; the item shows inside. The case's model carrier now has a tiny box (GeyserUtils size, safe with the patched GeyserUtils), so the tap reaches the case. The item is a dropped-item entity that can't be picked up (Geyser draws no item displays): no gravity, since Geyser runs an item's gravity itself. Bedrock spins it, where Cobblemon holds it still (`r26-java-cobblemon-display-case-diamond.png`). Found on the way: Bedrock's spawn delay for thrown Poké Balls also delayed every virtual entity past its data; now only Cobblemon's types | `r26-bedrock-display-case-apple.png`, `r26-bedrock-display-case-taken-out.png`, `r26-bedrock-display-case-diamond.png` |
| Apricorn trees (no more note blocks) | ✅ leaves, logs and coloured apricorns (red, blue); leaf colour matches Java Cobblemon (`r26-java-cobblemon-apricorn-leaves.png`) | `r26-bedrock-apricorn-trees.png`, `r26-bedrock-apricorn-fruit-machines.png` |
| PvP challenge, Bedrock vs Java Cobblemon | ✅ request (Java sees Cobblemon's own Battle Request), battle on both clients, win and advancement; Bedrock also accepts Java's challenge, and has Cobblemon's Forfeit with its confirmation (fixed: it showed Catch / Run) | `r26-bedrock-challenge-from-java.png`, `r26-bedrock-pvp-forfeit-tile.png`, `r26-bedrock-pvp-forfeit-confirm.png`, `r26-bedrock-pvp-forfeited.png`, `r26-bedrock-player-menu-java.png`, `r26-java-battle-request-from-bedrock.png`, `r26-bedrock-pvp-vs-java.png`, `r26-java-pvp-vs-bedrock.png`, `r26-bedrock-pvp-won.png` |
| Trading, Bedrock vs Java | ✅ with the patched GeyserUtils (its packets on the session's event loop): Bedrock asks, Java accepts in Cobblemon's own screen, both offer (Caterpie ↔ Arcanine), both accept, the Pokémon swap and both get Take Good Care!; no kick. After a swap the trade stays open, as Cobblemon's screen does. One trade form at a time: Bedrock stacked each update's form over the open one, and the server can't close a form. So an update waits for the open form's answer, with a line above the hotbar saying what changed; closing or answering the form shows the current trade (`r26-bedrock-trade-update-notice.png`, `r26-bedrock-trade-refreshed.png`, `r26-bedrock-trade-after-swap.png`) | `r26-bedrock-trade-offers.png`, `r26-java-trade-offers-from-bedrock.png`, `r26-bedrock-trade-done.png`, `r26-java-trade-done.png` |
| Riding: rider's view | ✅ seated where Cobblemon seats a Java rider: on the model's `locator_seat_1`, a player's riding offset (0.6) below it. Geyser seats a pig's rider at 0.75 × its box height, so while ridden the box height is set to put the seat there, and the real box comes back on dismount (tapping works again). First person now matches real Cobblemon on Java: the mane fills the middle, the sky and trees above. ⬜ Cobblemon's riding stamina bar Mounting showed the raw key `action.hint.exit.modelengine:arcanine` (Bedrock names the hint by the ridden entity's id); a small text pack (`fabric/tools/bedrock_lang.py`, built by setup) gives every model Bedrock's own "Tap sneak to dismount" (English) | `r26-bedrock-ride-hint.png`, `r26-bedrock-ride-seat-first-person.png`, `r26-bedrock-ride-seat-third-person.png`, `r26-bedrock-ride-dismounted-tap.png`, vs `r26-java-cobblemon-ride-first-person.png` |
| Sounds | ✅ recorded from the 26.60 client's own audio output (2026-11-09, after v1.2.0; its audio had come up silent before, it works since a client restart): a Cobblemon cry sent by name plays, a made-up sound name stays silent, and a battle start plays the send-out, Eevee's cry and Armarouge's battle cry (our server-played cries and animation sounds) | `r26-bedrock-sounds-recorded.png` (screenshot + waveforms), `r26-bedrock-sound-cry-vs-missing.wav`, `r26-bedrock-sound-battle-start.wav` |
| Wild spawning (Cobblemon's spawner) | ➖ many species around spawn | `r26-bedrock-send-out-model.png` |

Fixed for this client: every form field read button 0 ("Info*" everywhere: a `panel` ignores `collection_name`, now
`collection_panel`); every form button answered "closed" (`collection_details` belongs in the button's own bindings);
the starter drew all its pages at once (a page panel follows its page toggle) and had no Pikachu / Eevee tiles
(Cobblemon 1.8.4's Special starters); Pokédex numbers were read as numbers ("0258" → 258: they start with `§r`); the
Fabric compose file mounted models and UI from `./data` whatever `DATA` said (no models at all); the Summary's small
buttons cut their text; the wheel offered Shoulder and Ride to every Pokémon (now only Cobblemon's choices); the
owner never saw the Pokémon on their own shoulder, and its 0.9-wide pig carrier swallowed every tap (an allay carrier
now, shown to the owner too); a Pokémon or NPC only answered a tap at its feet (its carrier now takes the Java
entity's box, sent again after each entity data, which resets it); a stale Polymer pack after the Cobblemon upgrade
(setup.sh makes a new one when the Cobblemon jar is newer).

Fixed after v1.2.0: Geyser couldn't read the server's command list ("Index 63 out of bounds for length 62"): Cobblemon's
own argument types went out raw; they're now plain strings the server suggests for (`CommandsMixin`). A rare kick "Index -1 out of bounds for length 33" from Geyser's Java side (seen 3 times: an NPC
teleport, a fishing reel, a join); Its trace: `GeyserEntityDataManager.put` (Index -1 of 33). Sending GeyserUtils box sizes (height/width) for furniture carriers made it happen on every join, so the Pokémon/NPC box size sent since the NPC fix is the likely cause of the earlier rare kicks too; to settle before release. Root cause: GeyserUtils handles our `geyserutils:main` packets on the downstream network thread (`packetReceived` → `handleCustomPacket`), racing Geyser's session-thread entity data (an unsynchronised fastutil map); fix: wrap it in `session.ensureInEventLoop(...)` (one line, patch + mirror like the extension's). Earlier guess: an entity property update naming a property Geyser has not registered for that entity, so likely one of our GeyserModelEngine `anim<n>` bundles reaching an entity whose model has fewer properties, or a carrier before its custom entity is set.

Owner's UI decisions (2026-11-09): Catch as Cobblemon (built, ✅); starter details kept across pages and the title
fitted (built, ✅); Summary party slots as Cobblemon 1.8.4's cards (built, ✅; its HP gradient too, after v1.2.0).
## Final analysis (2026-11-09, v1.2.0; updated for v1.2.1)

Every row of the real-client table above is ✅ on the unmodified Bedrock 26.60 client against Cobblemon 1.8.4, with
its screenshots in `evidence/r26-*`; Sounds is recorded from the client's audio output (`r26-bedrock-sounds-recorded.png`). Also checked against
Cobblemon 1.8.4 itself (its screens, blocks and items), these were in no row (updated after v1.2.0; ❌ = can't work on Bedrock, with the reason):

| Gap | Bedrock | Vanilla Java | Note |
|---|---|---|---|
| Summary **Marks** tab (Cobblemon 1.8.4's 4th tab: a Pokémon's marks, 168 icons) | ✅ built after v1.2.0 as the owner approved (mock-up of Cobblemon's own tab, 2026-11-10) | ⬜ | Cobblemon's panel, slots and icons at its own positions (measured on its screen, `r26-java-cobblemon-summary-marks.png`): the shown mark's icon and description, "Eevee the Alpha" with the title in its colour, the marks it has in a 6 × 5 grid; a tap shows that mark (Cobblemon's setActiveMark). `r26-bedrock-summary-marks.png`, `-marks-chosen-markings.png`, `-marks-page2.png`. ponytail: a tap on the scroll strip turns the page (1/6 → 2/6) where Cobblemon scrolls |
| Summary **markings** (●▲■♥★◆ under the model, tap to mark) | ✅ built after v1.2.0 (same OK) | ⬜ | Cobblemon's six marking icons under the Pokémon on every tab; a tap cycles none → blue → red as Cobblemon's (setMarkings): circle blue, triangle red in `r26-bedrock-summary-marks-chosen-markings.png` |
| Riding stamina bar | ❌ can't work | ❌ can't work | Cobblemon's bar is Minecraft's jump bar showing the ride's stamina, which sprinting spends. Riders without the mod are moved by the server (Cobblemon moves rides on its own client), and no sprint reaches it from them: tried on 26.60 with sprint held while riding, `isSprinting` stayed false (Bedrock sends no sprint while mounted; a vanilla Java client only sprints on a vehicle it can sprint). With nothing to spend it, a bar would never move, so none is drawn |
| Animation sounds Cobblemon's client plays itself | ✅ / ❌ ride loops | 🟡 | after v1.2.0: the sounds a Pokémon's own one-shot animations carry (`sound_effects`: battle cries, quirks, a Duraludon's bonk; read from Cobblemon's animation files) are played by the server at their keyframe times; in battle a cry is the battle cry where the Pokémon has one, as Cobblemon's posers pick it (Armarouge's battle cry, recorded from the client: `r26-bedrock-sounds-recorded.png`). Move effects' sounds already come from the server. Looping animations' sounds (3 species' air idles) never trigger: our models carry no air idle. ❌ riding sound loops: Cobblemon's client plays them from its ride behaviour's state (speed, style), which riders without the mod don't run (Riding stamina bar row) |
| TM machine and the move-learning prompt on the 26.60 client | ✅ | ✅ | re-run after v1.2.0: see the two real-client rows above; TM crafting done too (`r26-bedrock-tm-crafted.png`) |
| Decorative and building blocks | ✅ | 🟡 | fixed after v1.2.0, checked on 26.60: the shaped ones draw with Cobblemon's models (lectern, disc shelf, 16 plaques, eject button, magnet, coin pouch and sack; `r26-bedrock-decorative-shaped.png`), as do the plants and clusters; the cubes (gem blocks, tumblestone and its bricks and slabs, hearty grain bale, slathered saccharine log, the ores) draw with Cobblemon's textures (`r26-bedrock-building-blocks.png`; before: dirt, `r26-bedrock-decorative-blocks.png`). Cause: Geyser's state overrides for those cubes had material instances but no geometry, which Bedrock drew as dirt; each now has `minecraft:geometry.full_block`. PolyMc also only puts them on note-block states with notes 0–15 (Bedrock caps a block property at 16 values). Where PolyMc had no spare state left it showed a stone cube, on Java too: now Cobblemon 1.8's placed items (potions, held items, up to 4), teacups, pots, orbs and the like (83 blocks, `cobblemonlink-decor.txt`) are drawn with Cobblemon's models like the plants, and the 21 stairs, slabs, walls and fences left over stand in as a vanilla block of the same shape and nearest colour (tumblestone: cut copper / granite, sky: prismarine, black: polished deepslate, saccharine: cherry, apricorn: jungle; as the signs do) (`r26-bedrock-placed-items-stairs-walls.png`). Their exact texture can't reach Bedrock: Geyser only knows the vanilla state the server sends, and every free state of a stairs/wall block is taken, so drawing Cobblemon's texture would repaint real vanilla blocks too |
| Command suggestions | ✅ | ✅ | fixed after v1.2.0: Cobblemon's own argument types (`cobblemon:pokemon`, `party_slot`, `pokemon_properties`... ids 54–68) went raw to players without the mod; ViaVersion passed the unknown ids through and Geyser dropped the whole list ("Index 63 out of bounds for length 62"). They now go as a string argument the server suggests for (`CommandsMixin`); Bedrock lists `/pokespawn`, `/pokeheal`... (`r26-bedrock-command-suggestions.png`) |
| Our own 116 strings | ✅ | ✅ | after v1.2.0: in all 29 of Cobblemon's languages (`assets/cobblemonlink/lang/*.json`), using Cobblemon's own words in each for Pokémon, PC, party, battle formats and its lines; German on 26.60: "Senden", "In den PC" (`r26-bedrock-our-strings-german.png`). ponytail: machine-written; worth a native speaker's look |
| Display case | ✅ | ⬜ | vanilla Java's drawing is new and untested; a Java Cobblemon player's click on a case was ignored once, unexplained |
| HP bar colours on the Summary cards | ✅ | — | after v1.2.0: Cobblemon's gradient (RenderHelper.getDepletableRedGreen as its party slot calls it: red 2 − 2·hp, green 1 / hp ÷ 0.5 / 0, both ×0.8, blue 0.27, read from its bytecode); one bar picture a width, the width being the HP ratio. Eevee at ~40% orange, full green (`r26-bedrock-summary-hp-gradient.png`) |
| Display case item | ❌ can't match exactly | — | the item shows inside the case, but Bedrock spins and bobs it: Geyser draws no item displays, so it's a dropped item (Bedrock animates those itself); an armor stand's hand holds it tilted off-centre, which is further from Cobblemon's still item |
| Creative / admin editors (NPC, behaviour, habitat, config screens) | ➖ | ➖ | tools for mod players; not needed |
