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
| Full cubes | the 23 evolution-stone ores, stone blocks, apricorn logs, planks and leaves | **mushroom block states**: 93 kept (the bottom face shown, not all six), so note blocks stay vanilla |
| Plants and fruit | apricorns, saplings, berry bushes, mints | **tripwire states**: the 64 with `disarmed=true`; one look per stage, with what a state can't say (colour, which berry) in chunk data |
| Containers | Gilded Chests (7) | a vanilla chest with the item model swapped: the block stays a real chest (contents, hoppers); its look comes from a display entity, as now |
| Machines | healing machine, PC, pasture, fossil machines, TM machine, campfire pot | **display entities**, as the plugin already does: they need animation and states no block state gives |

## What shipped (v1.1.0)

Way A: real vanilla blocks keep working; the plugin does their part for the states it keeps.

| Pool | States kept for Cobblemon | Used for | What vanilla loses |
|---|---|---|---|
| Mushroom blocks (brown, red, stem) | the 93 with the bottom face shown but not all six | 47 full cubes: 23 ores, 10 evolution-stone blocks, apricorn logs, wood (each axis), planks, leaves; structure berry markers | a real mushroom block shows its bottom face only when all six show (huge mushrooms never generate the kept states) |
| String | the 64 with `disarmed=true` | 61 plants: apricorns (stages 0-2 one look, ripe one look a colour, 4 facings), 7 saplings, berry bushes (6 ages), mints (8 ages) | nothing a player sees: vanilla string only holds `disarmed` for the tick before shears cut it |
| Note blocks | none | — | none: note blocks, their tuning, music and observers are untouched |

Paper's `block-updates.disable-mushroom-block-updates` and `disable-tripwire-updates` stop the game changing those
states. `VanillaBlocks.java` then does vanilla's part for real mushroom blocks (faces hide against their own kind when
placed or pushed) and real string:
- hooks attach along the line;
- entities going in set it off (players through the PHYSICAL interaction event, others through EntityInteractEvent);
- it releases 10 ticks after the last one leaves;
- breaking it fires the hooks, then they let go;
- shears cut it quietly.

Hooks are vanilla's own: the plugin only asks them to look again. Every state change goes through the world with
neighbour updates, so observers see it.

`CustomBlocks.java` places and breaks Cobblemon's blocks:
- **Mining time:** a break-speed attribute makes the client's mining time the block's own (ores mine like iron ore).
- **Drops:** Cobblemon's loot tables, explosions included.
- **World:** pistons move cubes as they are and break plants; water washes plants away; endermen can't take them.
- **Creative pick-block:** gives the Cobblemon item.
- **Sounds:** their own, played from the server.

`Plants.java` does Cobblemon's growing, following its blocks' code:
- **ApricornBlock:** 1-in-5 random ticks, bone meal, picking with a 10% seed.
- **Apricorn saplings:** two stages, then ApricornTreeFeature's tree.
- **MintBlock:** light 9+, 1-in-8 random ticks.
- **BerryBlock/BerryBlockEntity:** growth time and refresh rate ×1.4 minutes over the stages, yield at flowering, the
  fruit drawn at its growth points.

New chunks get Cobblemon's features:
- ores, by their placed features;
- apricorn trees, by biome rate;
- berry groves, by biome preference and weight;
- mints, by altitude.

Cobblemon's structures use the blocks directly. Their berry bushes are markers that become bushes when the chunk
loads; their gilded chests carry their loot table to the plugin's gilded chests.

What a block's state can't say lives in the chunk's own data:
- an unripe apricorn's colour;
- which berry a bush is, its timers and fruit;
- a mint's colour;
- leaves a player placed (they never decay).

Berry bushes and mints draw on Java with item displays. Java aims at string's flat outline, so every plant also gets an
invisible interaction box its size, for clicks and hits. Bedrock draws all of them as Geyser custom blocks
(`blocks.mcpack`, `geyser-blocks.json`).

### Checked (Paper 26.3, toby, a fresh world; `docs/evidence/cb-*`)

| Check | Result | Evidence |
|---|---|---|
| Ores, apricorn trees, berry groves and mints generate in new chunks | e.g. 510 moon stone ores, 20 apricorn logs, 5 berry bushes around one taiga spot | `cb-java-apricorn-tree-natural.png`, `cb-java-berry-grove-natural.png` |
| Ore mining | not broken by hand (1.5 s) or iron axe (1.5 s); iron pickaxe < 1 s, Moon Stone dropped | `cb-java-ore-mined-pickaxe.png`, `cb-java-checks.log` |
| Ripe apricorn on a generated tree picked | back to its first stage, Green Apricorn dropped | `cb-java-natural-apricorn-*.png` |
| Apricorn seed → sapling → tree (bone meal), its apricorn ripened and picked | yes | `cb-java-apricorn-sapling.png`, `cb-java-apricorn-tree-grown.png` |
| Oran berry on farmland → fruiting (bone meal at Cobblemon's 56%) → harvested | 2 Oran Berries, bush back to mature | `cb-java-berry-*.png` |
| Pink mint seeds → age 7 → broken | 2 seeds and a mint leaf | `cb-java-mint-grown.png` |
| Structure blocks | a berry patch's bushes; old garden ruins' 2 gilded chests filled from Cobblemon's loot | `cb-java-structure-*.png` |
| Tripwire trap (player-placed string between two hooks, lamp behind a hook) | a pig sets it off (string, hook, lamp on); released after; a hand break fires then detaches; shears detach without firing; re-placing string re-attaches | `cb-java-checks.log` |
| Mushroom blocks placed side by side by hand | the faces between them hide | `cb-java-checks.log` |
| Note block + observer + lamp | tuning lights the lamp (note blocks untouched) | `cb-java-checks.log` |
| Pistons and TNT | an ore moves as itself; a 3×3 of ores blown up drops Moon Stones | `cb-java-checks.log` |
| Bedrock | the three custom blocks registered; ore, ripe apricorn, fruiting bush and log arrive as their own custom ids, real mushroom blocks and string as vanilla's; the blocks pack offered | `cb-bedrock-bot.log` |

### Still different from vanilla or Cobblemon

- **Bottom face:** a real mushroom block can't show its bottom face unless all six show (the README says so).
- **Bedrock looks, unchecked on a device:**
  - every berry bush shows the Oran look, and every mint the red mint;
  - a bot can't render, so how they look on a real device is unchecked.
- **Berries:** no mulch and no mutations.
- **Leaves:** apricorn leaves are drawn opaque on Java, because mushroom blocks draw no transparency. They decay like
  leaves, about 3-6 s after their last log goes.
- **Ore veins:** a random walk of Cobblemon's vein size, and only in chunks generated after the update.
- **Apricorns:** an unripe apricorn looks the same for every colour, as Cobblemon's own model does. Its colour shows when
  ripe.
- **Saccharine trees, other plants and blocks:** saccharine trees, revival herbs, medicinal leeks, big roots, hearty
  grains and Cobblemon's building blocks (tumblestone, slabs, stairs) still use stand-ins. There's room for about 46
  more full cubes, but only 3 more plant states.

## Sources

- [Geyser wiki: custom blocks](https://geysermc.org/wiki/geyser/custom-blocks)
- [CraftEngine (GitHub)](https://github.com/Xiao-MoMi/craft-engine), [CraftEngine (BuiltByBit)](https://builtbybit.com/resources/craftengine.82674/), [CraftEngine (Modrinth)](https://modrinth.com/project/DCuZeqP4)
- [Nexo FAQ](https://docs.nexomc.com/faq), [Nexo API: NexoBlocks](https://jd.nexomc.com/1.15/com/nexomc/nexo/api/NexoBlocks.html), [Nexo Scaffolding](https://docs.nexomc.com/addons/scaffolding)
- [Oraxen (GitHub)](https://www.github.com/oraxen/oraxen)
- [ItemsAdder vs Oraxen (2026)](https://mineguard.pro/en/blog/itemsadder-vs-oraxen-custom-items-comparison-2026)
- [Rainbow: Geyser block mappings from block state overrides](https://www.modpackindex.com/mod/152846/rainbow)
