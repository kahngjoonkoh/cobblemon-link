# Regression pass, fresh world (2026-10-21)

World, player data and machines wiped on toby; plugin at the branch head.

| Check | Java (client on toby) | Bedrock (bot) |
|---|---|---|
| Join, starter toast, `/starter` | ✓ [screen](m28-java-fresh-rg02.png) | ✓ starter form, "You chose Bulbasaur!" |
| Send out (R) | ✓ [Charmander](m28-java-fresh-rg04.png) | — |
| Natural spawns in the fresh world | ✓ (Sigilyph, Maschiff, Fidough, Meditite… seen) | ✓ (add_entity of modelengine:*) |
| Wild battle: Fight / moves / log | ✓ [battle](m28-java-fresh-rg07.png) | ✓ [forms, boss bars](m28-bedrock-fresh-world-regression.log) |
| Capture with a thrown Poké Ball | ✓ broke free, then caught ([throw](m28-java-fresh-rg09.png)) | — |
| PC, Pokédex | ✓ [PC](m28-java-fresh-rg11.png), [Pokédex](m28-java-fresh-rg12.png) | — |
| Rare Candy → level 16, "ready to evolve", Evolve | ✓ [level](m28-java-fresh-rg14.png), [Charmeleon](m28-java-fresh-rg17.png) | — |
| Sim self-checks | `test.js`, `test-pvp.js`, `test-trainer.js`, `test-switch.js`, `test-doubles.js` (doubles + triples) pass | |
| `gradle test` | green | |

Found and fixed during the pass: a right-click into the air with a Poké Ball opened "Give Poké Ball to…" (it was
classed as a held item); Cobblemon's Poké Ball recipes now give the throwable ball.

Bot note: the Bedrock bot must hit the carrier (minecraft:pig) within reach; GeyserModelEngine's model entity is
display-only (taps on a real phone land on the carrier's hitbox).
