# cobblemon-link (Fabric)

**Real Cobblemon on a Fabric server, playable from Bedrock and vanilla Java too.** Players with the Cobblemon mod play
Cobblemon as usual; Bedrock players (phones, consoles, PC) join through Geyser with nothing installed; vanilla Java
players get a server-drawn taster of it. Free and non-commercial, like the Paper version (see the root README).

This is one of cobblemon-link's two products:
- **cobblemon-link** (this folder): a server-side Fabric mod next to the real Cobblemon. The game is Cobblemon's own
  (spawning, battles, catching, EXP, evolution, storage, Pokédex); the mod shows it to players without the mod.
- **cobblemon-link-plugin** (`plugin/`): the Paper plugin that recreates Cobblemon's gameplay for vanilla clients.

Feature by feature, against the Paper version: [PARITY.md](PARITY.md). How it was proven: [POC.md](POC.md).

## Versions

| | Version | Why |
|---|---|---|
| Minecraft (server, Cobblemon players, vanilla Java players) | **1.21.1** | Every Cobblemon release since December 2024 is 1.21.1 only |
| Cobblemon | **1.8.2** (Fabric) | the latest release; Modrinth version `GJ2a1b8O` |
| Fabric loader / Fabric API / Fabric Language Kotlin | 0.19.5 / 0.116.17+1.21.1 / latest | as Cobblemon needs |
| PolyMc | 5.7.0 master `a3eaae6` (built by `tools/setup.sh`) | shows Cobblemon's items, blocks and registries to clients without the mod; no 1.21.1 release exists |
| Polymer | 0.9.19+1.21.1 | resource pack hosting and virtual entities (the Java models) |
| Floodgate (Fabric) | 2.2.4-b38 | the last 1.21.1 build; Bedrock players without Java accounts |
| Geyser standalone | 2.12.0-b1265 | Bedrock 26.30–26.52; it speaks the latest Java protocol |
| ViaProxy | 3.4.14 | Geyser's Java version → 1.21.1 |
| GeyserUtils + GeyserModelEngine extension | the Paper version's builds | Cobblemon's models on Bedrock |

Bedrock's NetherNet transport (from 2026-10-27) needs a newer Geyser and its NetherNet port: update the Geyser pin
then.

## Who sees what

| Player | Models | Screens |
|---|---|---|
| Cobblemon mod (Java 1.21.1) | Cobblemon's | Cobblemon's |
| Bedrock | Cobblemon's models as native Bedrock entities, animated by the client | The Paper version's Bedrock screens (battle, starter, Summary, PC, Pokédex, party HUD), drawn by its UI pack |
| Vanilla Java 1.21.1 | Drawn by the server with display entities | Chest menus (the `vanillaJava` level) |

### Vanilla Java levels

Everything a vanilla Java player sees is drawn by the server, so how much they get is a setting:
`config/cobblemonlink/config.json` in the server's data folder.

```json
{ "vanillaJava": "full", "bedsHeal": true }
```

| `vanillaJava` | Vanilla Java players get | Cost per Java player |
|---|---|---|
| `off` | A message: install Cobblemon, or join on Bedrock | none |
| `basic` | Still models (turned once a second), chest menus | spawn packets only |
| `standard` | Animated models (idle, walk, battle, faint, 5 poses a second) | ~10 KB/s per Pokémon in view |
| `full` (default) | Also Cobblemon's move animations at 10 poses a second | ~20 KB/s per Pokémon in view |

Bedrock players cost nothing extra: their client animates the models. `bedsHeal` makes lying in a bed heal the
party (the Paper version's healing; Cobblemon's healing machine works too).

## Run it

You need Docker, Python 3 and the repo's converted assets (`tools/build.sh` at the repo root). Accept the
[Minecraft EULA](https://aka.ms/MinecraftEULA).

```bash
cd fabric
tools/setup.sh                 # pinned downloads (hash-checked), PolyMc built from source, the mod, models, packs
docker compose up -d           # first boot: Cobblemon, configs, the Floodgate key (a few minutes)
docker compose down && tools/setup.sh && docker compose up -d   # Geyser through Floodgate, Bedrock item icons
```

- Restart the server with `docker compose restart` (all three): ViaProxy keeps the server's address from its start.
- Java: `<host>:25565` (bound to localhost; set `JAVA_BIND=0.0.0.0` in `fabric/.env` to expose it).
- Bedrock: `<host>:19132` (UDP).
- Other `.env` settings: `JAVA_PORT`, `BEDROCK_BIND`, `BEDROCK_PORT`, `MEMORY` (4G), `MAX_PLAYERS` (12),
  `ONLINE_MODE` (TRUE), `MODE`, `LEVEL_TYPE`, `RCON_PASSWORD`, `DATA` (./data), `MODELS`, `PREFIX` (container names).

Commands for players without the mod: `/starter`, `/party`, `/pokedex`, `/battle` (reopen the battle menu),
`/partyhud`. Cobblemon's own commands work as usual (`/pokegive`, `/pokespawn`, `/pokeheal`, `/pc` for ops), plus
`/pokeballs [n]`. Players without the mod start a battle by right-clicking, tapping or hitting a wild Pokémon
(Cobblemon's R key needs the mod); the PC opens from Cobblemon's PC block.

## How it works

- **Joining**: PolyMc rewrites Cobblemon's registries, items and blocks per player. `mixin/SendMixin` removes what a
  client without the mod can't read: Cobblemon's entities and entity data, block entities, menus and payloads.
- **Players** (`Clients`): a Cobblemon client registered Cobblemon's channels; a Bedrock player is a Floodgate
  player (Geyser → ViaProxy keeps Floodgate's handshake); everyone else is vanilla Java.
- **Packs**: Polymer's pack (Cobblemon's assets for PolyMc, our models) goes to vanilla Java players only, in game.
  Bedrock players get Geyser's packs: the GeyserModelEngine models, the UI pack, Cobblemon's item icons.
- **Models** (`Models`, `ModelHolder`, `Mons`): `tools/build_models.py` makes one item model a bone from the
  converted `.bbmodel`s; the server poses the bones from the keyframes. Bedrock players get GeyserModelEngine's custom
  entity with its scale and animation properties. Cobblemon's thrown Poké Ball gets the ball model.
- **Screens**: driven by Cobblemon itself. Its battle prompt (`BattleMakeChoicePacket`) opens the battle menu and a
  choice goes back as the Showdown response Cobblemon's client would send; its starter and PC packets open ours;
  Summary, Pokédex and the party HUD read Cobblemon's Pokémon, storage and Pokédex records.

## Layout

```
mod/          the Fabric mod (Gradle; tools/build.sh builds it in Docker). Tests: mod/src/test
tools/        setup.sh, build.sh, build_models.py, bil_models.py + split_rotations.py, bedrock_items.py;
              test helpers: client.sh, modclient.sh, bedbot.js, bedbattle.js, snap.py, xkey98.py, restart.sh, deploy.sh
evidence/     screenshots and logs behind PARITY.md
```
