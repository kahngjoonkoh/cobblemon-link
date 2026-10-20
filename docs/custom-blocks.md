# Custom blocks on Paper (vanilla Java + Bedrock)

The Paper product shows Cobblemon's blocks with vanilla stand-ins: ores are stone, apricorns drop from leaves, and
Gilded Chests in structures are plain chests. The players' clients have no Cobblemon blocks, so this page covers what a
plugin can do instead, and a working proof of concept on branch `paper-custom-blocks`.

## The techniques

A Paper plugin can't register a block the client knows. Every method shows the player something the client already has
and changes how it looks with the resource pack (Java) and Geyser's mappings (Bedrock).

| Technique | Blocks it allows | Shape and transparency | Light, collision, mining | Cost | Java with only a pack | Bedrock (Geyser) |
|---|---|---|---|---|---|---|
| **Note block states** (instrument × note, minus one kept for real note blocks) | 574 | full opaque cubes only | full collision; mining is the note block's (0.8 s, axe) unless the server adjusts it (below) | none: a normal block | yes: `blockstates/note_block.json` | yes: `custom_mappings` `state_overrides` on `minecraft:note_block` |
| **Tripwire states** (7 booleans, minus all-false for real string) | 127 | any model, transparent, non-full | no collision, instant break, no light | none | yes: `blockstates/tripwire.json` | yes, any geometry |
| **Mushroom block faces** (red, brown, stem: 6 booleans each) | ~190 | full cubes | as mushroom blocks (axe, 0.3 s) | none, but the world's own mushroom blocks show differently unless held to one state | yes | yes |
| **Chorus plant / other spare states** (chorus, leaves `distance`, `waterlogged`…) | a handful to ~60 | chorus: non-full shapes | vanilla's of that block | none | yes | yes |
| **Display entities + barrier or interaction hitbox** ("furniture") | unlimited | any model, any size | collision from a barrier; mining by events | one entity or more a block, sent to every nearby player: fine for machines, too heavy for ores or plants in thousands | yes: item model | yes, through GeyserModelEngine (what our machines use now) |
| **Real server blocks + packet remap** (CraftEngine) | unlimited on the server, still limited by spare client states | as the state it's sent as | the server knows the real block | NMS internals, breaks between Minecraft versions | yes | its listing doesn't say |

Paper helps the state techniques: `paper-global.yml` has `block-updates.disable-noteblock-updates` and
`disable-tripwire-updates`, so the world stops changing those states on its own (neighbours, redstone, tripwire hooks).
The plugin then only stops players from changing them by hand (tuning a note block, stepping on string).

### Libraries

| Library | Licence | Free and open? | How it does blocks | Bedrock |
|---|---|---|---|---|
| Oraxen | its own paid licence: source viewable, no redistribution | no | note block, string, chorus states | through addons |
| Nexo (Oraxen's successor) | paid | no | note block, string, chorus states; disables their vanilla behaviour | Scaffolding addon (separate purchase, early access) |
| ItemsAdder | paid, closed | no | note block, tripwire, mushroom, "real" blocks | its own converter |
| CraftEngine | GPL-3.0 community edition, paid premium | open (GPL), premium extras paid | registers real blocks on the server, sent to clients as vanilla states | not stated |

None of them fits a project that's MPL-2.0 and free: three are paid, and CraftEngine's GPL would pull a large
dependency with its own config system into a plugin that already generates everything from Cobblemon's files. The
technique itself is simple enough to do in ~400 lines, so the PoC does it directly.

## Recommendation, by Cobblemon block family

| Family | Examples | Technique |
|---|---|---|
| Full cubes | the 23 evolution-stone ores, apricorn planks and logs, gilded chests' wood | **note block states** (574 free: enough for every Cobblemon full cube) |
| Plants and fruit | apricorns (7 colours × 4 ages × 4 facings = 112), berry bushes, mints, revival herbs, medicinal leeks | **tripwire states** (127 free, so only the apricorns fit; the rest need a second source of states) |
| Crops on farmland | mints, leeks, berry bushes' growth stages | the spare states of vanilla crops that never occur (e.g. `beetroots` ages with a hidden marker) or more tripwire-like states; a follow-up |
| Containers | Gilded Chests (7) | a vanilla chest with the item model swapped: the block stays a real chest (contents, hoppers); its look comes from a display entity, as now |
| Machines | healing machine, PC, pasture, fossil machines, TM machine, campfire pot | **display entities**, as the plugin already does: they need animation and states no block state gives |

## The proof of concept (branch `paper-custom-blocks`)

- `tools/build_custom_blocks.py` reads Cobblemon's own blockstates, models, textures, lang and ore features and writes:
  - `blocks-pack.zip`: the Java pack (note block and tripwire blockstates pointing at Cobblemon's models and textures);
  - `blocks.mcpack` and `geyser-blocks.json`: Bedrock textures and geometry, and Geyser's state overrides;
  - `custom-blocks.json`: the plugin's table (block → state, drops, ore veins).

  State allocation is append-only, so a block keeps its state across releases. `python3 tools/build_custom_blocks.py demo`
  is the check.
- `CustomBlocks.java`:
  - **Keeps the states still:** real note blocks are placed at harp/0 and can't be tuned; real string stays all-false.
  - **Mining:** an ore breaks only as fast as Minecraft's iron ore would. A `BLOCK_BREAK_SPEED` attribute scales the
    client's own break time from the note block's to the ore's, so the crack animation matches.
  - **Drops:** Cobblemon's loot (its evolution stone with Fortune, the ore itself with Silk Touch, nothing without a pickaxe).
  - **Apricorns:** planted on the side of leaves; bone meal grows them; right-click picks a ripe one.
  - **Generation:** Cobblemon's 42 ore features (count, height, vein size, biome tags) generate in new chunks.
- `server/setup.sh`: installs the packs and mappings, and sets Paper's two update switches.

### What was checked

On toby, Paper 26.3 with the vanilla 26.3 Java client and the Bedrock protocol bot:

| Check | Result | Evidence |
|---|---|---|
| Ores generate in new chunks | about 1,000 ores in a 7×7-chunk area, in stone and deepslate | `evidence/cb-java-ore-generated.png` |
| All 23 ores and 7 apricorn colours × 4 stages show with Cobblemon's textures on Java | yes | `cb-java-ores-and-apricorns.png`, `cb-java-lineup.png`, `cb-java-apricorns.png` |
| Place an ore from its item | shows the ore, in hand and placed | `cb-java-ore-placed-from-item.png` |
| Mining feel | by hand 1.5 s and with an axe 1 s: not broken; iron pickaxe: broken in under a second, a Moon Stone dropped | `cb-java-ore-*.png`, `cb-java-checks.log` |
| Apricorn: plant, bone meal to ripe, pick | grows, picks back to the first stage, drops `cobblemon:red_apricorn` | `cb-java-apricorn-*.png`, `cb-java-checks.log` |
| Real note blocks and string unaffected | a note block on gold stays harp/0 through 3 right-clicks and a redstone block; string with a pig standing in it stays all-false | `cb-java-checks.log` |
| Chunk unload and reload, server restart | states unchanged | `cb-java-after-chunk-reload.png`, `cb-java-checks.log` |
| Bedrock | Geyser registers `cl_note_block` and `cl_tripwire`; the client is offered the blocks pack; an ore and an apricorn reach the client as custom block runtime ids (a real note block as vanilla's) | `cb-bedrock-bot.log` |

### Limits

- **Bedrock is proven at protocol level only.** A bot can't render, so how the textures and the apricorn geometry look on a
  real device is unchecked. Geyser's wiki says Bedrock block components can break between Bedrock versions.
- **Real note blocks can't be tuned** (every note is a Cobblemon block). A per-block note stored in chunk data would bring
  tuning back. Real string has no tripwire hooks either.
- **Ore veins are a random walk of Cobblemon's vein size,** close to vanilla's small blobs but not the same shape. They come
  from a `BlockPopulator`, so only in chunks generated after the plugin is installed.
- **Mining:** the hardness comes from vanilla's iron, deepslate and nether ores. Efficiency and Haste work, because the
  scale is computed from the held tool. Bedrock gets Minecraft's ore time from the mapping, but the server still decides.
- **Apricorns don't grow on their own yet,** only with bone meal. They need apricorn trees and saplings to appear naturally;
  for now they're planted by hand on any leaves.
- **Running out of states:** 127 tripwire states hold only the apricorns. Berry bushes, mints and other plants need
  another source of states.

## Rolling out to all of Cobblemon's blocks

| Work | Estimate | PARITY.md rows it moves |
|---|---|---|
| Ores: done here; add `/pokeitem` for ore items, ore datapack tags (`c:ores`), Fortune per Cobblemon's formula | ½ day | Evolution stones and ores 🟡 → ✅ |
| Apricorn trees: saplings, logs, planks, leaves (note block states) and tree growth that hangs apricorns; generation from Cobblemon's tree features | 3–4 days | Apricorns, Apricorn trees and ball crafting 🟡 → ✅ |
| Berry bushes and mints (more states: crops' spare states or note block states for their stages) | 3–5 days | Berries and berry trees 🟡 → ✅, Recipes (the plant ones) |
| Structures: swap the vanilla look-alikes for these blocks in `build_structures.py` | 1 day | Structures (fewer stand-ins) |
| Gilded Chests as chests with their model on a display entity (already drawn for Machines) | 1 day | Display Case / Gilded Chests |
| Real-device Bedrock check and fixes | 1 day | — |
| **Total** | **about 2 weeks** | ~6 rows 🟡 → ✅ |

## Sources

- [Geyser wiki: custom blocks](https://geysermc.org/wiki/geyser/custom-blocks)
- [CraftEngine (GitHub)](https://github.com/Xiao-MoMi/craft-engine), [CraftEngine (BuiltByBit)](https://builtbybit.com/resources/craftengine.82674/), [CraftEngine (Modrinth)](https://modrinth.com/project/DCuZeqP4)
- [Nexo FAQ](https://docs.nexomc.com/faq), [Nexo API: NexoBlocks](https://jd.nexomc.com/1.15/com/nexomc/nexo/api/NexoBlocks.html), [Nexo Scaffolding](https://docs.nexomc.com/addons/scaffolding)
- [Oraxen (GitHub)](https://www.github.com/oraxen/oraxen)
- [ItemsAdder vs Oraxen (2026)](https://mineguard.pro/en/blog/itemsadder-vs-oraxen-custom-items-comparison-2026)
- [Rainbow: Geyser block mappings from block state overrides](https://www.modpackindex.com/mod/152846/rainbow)
