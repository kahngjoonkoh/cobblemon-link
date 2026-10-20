# Fabric PoC: real Cobblemon, joinable by vanilla Java, Bedrock and Cobblemon players

**Question:** can cobblemon-link move onto a real Cobblemon server (Fabric) and still let vanilla Java and Bedrock
players join with nothing installed, while Cobblemon-mod players join normally?
**Answer:** yes. Each of the three risky questions works end to end on a test server (evidence below). The risks
are scale and upkeep, not a hard wall.

## Setup (`docker-compose.yml`, data in `~/.cache/cl-fabric-poc`)
- `fabric`: Minecraft 1.21.1, Fabric loader 0.19.5, Fabric API 0.116.17, Fabric Language Kotlin, **Cobblemon 1.8.2**
  (unchanged). Mods in `mods-extra/` (not committed):
  - **PolyMc** 5.7.0, built from TheEpicBlock/PolyMc master (1.21) with its datagen. LGPL-3.0.
  - **Polymer** 0.9.19+1.21.1: resource-pack auto-host and virtual entities. LGPL-3.0.
  - **blockbench-import-library (BIL)** 1.2.7+1.21: draws `.bbmodel` files with display entities. LGPL-3.0.
  - **`bridge/`**, our server-side mod (`cl-bridge`, about 250 lines).
- `viaproxy`: ViaProxy 3.4.14, Java 26.3 → 1.21.1.
- `geyser`: Geyser standalone 2.12.0-b1265 (current, Bedrock 26.30–26.52), plus GeyserUtils and
  GeyserModelEngineExtension, the same extensions cobblemon-link runs today.
- Models: three cobblemon-link `.bbmodel`s (Pikachu, Charmander, Bulbasaur) for Java, and their GeyserModelEngine
  input folders for Bedrock.

## Q1: a vanilla 1.21.1 client: **works**
- **Baseline:** "Network Protocol Error" at login. The client can't parse Cobblemon's synced registries
  (`trim_pattern ... cobblemon:automaton_armor_trim_smithing_template`). See `evidence/q1-baseline-protocol-error.png`.
- **PolyMc** fixes the login. It filters and disguises modded registries, items and blocks per player.
- **The bridge mod** (`SendMixin`, `Bridge`) covers what PolyMc doesn't. For vanilla clients only, it:
  - drops Cobblemon entities (by their server-side type, because PolyMc may already have disguised the packet);
  - drops their entity data, and any entity-data field whose type isn't one of Minecraft's 31 own types;
  - shows each Pokémon as a cobblemon-link model, drawn by BIL and following the entity, with Polymer serving the
    pack.
- **Evidence:** `evidence/q1-vanilla-sees-pokemon-fixed.png` and `q1-vanilla-final.png`: Pikachu, Charmander (with
  its tail flame) and Bulbasaur on a vanilla client, which stays connected.
- **Model fix needed:**
  - **The problem:** 1.21.1 item models only allow cube rotations of 0, ±22.5 or ±45 degrees on one axis. Cobblemon
    uses any angle: Charmander has 17 such cubes and Bulbasaur 11, and those parts fail to load.
  - **The fix:** `tools/split_rotations.py` moves each such cube into its own bone carrying the rotation. Display
    entities rotate freely, so they all render (`q1-vanilla-sees-pokemon.png` is before the fix, `-fixed` after).
  - **Also needed:** BIL wants a `frame_time` on each texture. Both belong in geo2bb if this goes ahead.

## Q2: Bedrock through Geyser standalone and ViaProxy: **works (protocol level)**
- **Chain:** `bedrock-protocol` bot (Bedrock 26.52) → Geyser standalone (latest) → ViaProxy (26.3 → 1.21.1) →
  Fabric 1.21.1. The bot joins the world and stays connected.
- **Pokémon:** the bridge sends GeyserUtils' `CustomEntityPacket(entityId, "modelengine:<species>")` on
  `geyserutils:main` (JSON), plus a pig as the carrier. This is exactly what GeyserModelEngine does on Paper.
- **Evidence:** `evidence/q2-bedrock-bot.log` shows `add_entity modelengine:pikachu` and `modelengine:bulbasaur` at
  the Pokémon's positions, and the model pack is offered ("packs offered: 2").
- **Not shown:** a bot can't render, and no phone was available. The same Bedrock files and extension already work
  on a phone with cobblemon-link today.

## Q3: a Cobblemon screen driven from Cobblemon's server-side data: **works**
- **What it is:** `/vparty` (`PartyMenu`) opens a vanilla chest built from `Cobblemon.storage.getParty(player)`.
  Clicking a slot calls Cobblemon's `pokemon.sendOut(...)` or `recall()`.
- **Evidence:**
  - `evidence/q3-party-menu.png`: the menu;
  - `q3-party-tooltip.png`: "Pikachu Lv.12 31/31 HP";
  - `q3-sent-out.png`: Pikachu sent out by Cobblemon and shown as the model;
  - `q3-sent-out-clean.png`: Charmander, the same way.

## Q4 (implied): Cobblemon-mod players still join normally: **works**
- **The problem:** PolyMc disguises content for every client. A Cobblemon client saw no Pokémon at first.
- **The fix:** `Polys` gives clients that registered `cobblemon:` channels PolyMc's no-op map, picked again on join
  once their play channels are known.
- **Evidence:** `evidence/q4-cobblemon-client.png`: Cobblemon's own Charmander with "??? Lv. 10 / Press R to battle"
  and the 3D Poké Ball item.

## Risks and gaps found
- **Cobblemon only runs on Minecraft 1.21.1.** Every release since December 2024 is 1.21.1-only.
  - **Java:** vanilla players use 1.21.1. Newer Java clients would need ViaVersion or ViaFabric in front.
  - **Bedrock:** needs Geyser standalone and ViaProxy as two extra processes, and Bedrock's next transport change
    must be handled in Geyser standalone.
- **PolyMc for 1.21.1 is unreleased:** it's built from master, and its last release was for 1.20. PolyMc-Extra
  (GPL) only supports 1.21.6+.
  - **Startup errors:** it fails to generate some entity stand-ins (`NoSuchElementException`) and some berry block
    resources. Both are logged; neither is fatal here.
  - **What it doesn't cover:** Cobblemon's custom entity data and entities. The bridge fills that gap, but other
    modded packet types may still turn up in real play.
- **Everyone gets the Polymer pack:** it's 59 MB (PolyMc copies Cobblemon's assets plus our models) and is sent to
  all clients, Cobblemon players included. It should be skipped for Cobblemon clients.
- **Telling Bedrock players apart:** the PoC uses their source address (the ViaProxy container). A real setup needs
  Floodgate, and must forward Floodgate's data through ViaProxy. Untested.
- **Not done in the PoC:**
  - model rotation and animations: BIL plays idle only, and the holder doesn't copy the entity's yaw;
  - Bedrock entity properties: GeyserModelEngine's scale, colour and animation properties aren't sent;
  - battles, PC, Summary and Pokédex screens. Cobblemon runs these through its own client packets, so each would
    need a server-side screen like Q3's.
- **Memory:** two clients plus the Fabric server and the existing cobblemon-link server got the Fabric container
  OOM-killed twice on this 12 GB host. That's an environment limit, but real Cobblemon on Fabric is heavier than
  Paper.

## Effort estimate for the full transition
| Work | Estimate |
|---|---|
| Bridge hardening: packet audit across all Cobblemon payloads and entities, Floodgate, the per-player pack | 2–3 weeks |
| Models for vanilla on 1.21.1: geo2bb output for BIL (rotation split, `frame_time`, yaw, animations driven by Cobblemon's pose state) | 1–2 weeks |
| Bedrock entities: GeyserModelEngine properties (scale, animations) from the bridge | ~1 week |
| Screens rebuilt on Cobblemon's API (battle, party, PC, Summary, Pokédex, starter, evolution, move learning), reusing cobblemon-link's art and Bedrock forms | 4–6 weeks |
| Deployment: Fabric + Geyser standalone + ViaProxy compose, CI builds, docs | ~1 week |
| **Total** | **about 2–3 months** for one developer, with the Paper version kept alive meanwhile |

**Recommendation: go**, if real Cobblemon players are wanted. Run it as a staged migration on the `fabric-poc`
branch, and keep cobblemon-link on Paper as the stable release until the Fabric build matches its screens.
