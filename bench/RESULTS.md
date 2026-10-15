# Server load: plugin approach vs Cobblemon mod

Measured 2026-10-15 on a Ryzen 5 3550H laptop (4 cores / 8 threads, 12 GB RAM).

## Setup

| | Plugin approach (this repo) | Cobblemon mod |
|---|---|---|
| Server | Paper 26.3 build 190 | Fabric 1.21.1 |
| Pokémon | BetterModel 3.5.1 model attached to a husk | Cobblemon 1.8.2 entity |
| Client | vanilla Java 26.3 (resource pack) | Fabric 1.21.1 + Cobblemon |
| Heap | 3 GB | 3 GB |

Both runs used:
- The same procedure: a flat world, with Pikachu spawned on a 10×10 grid spaced 3 blocks apart.
- One player watching from the centre.
- A 60 s settle per step.
- Tick timing from vanilla `/tick query` (100-tick sample).
- CPU as the average of 5 `docker stats` samples. 100% = one core.

## Results

| Pokémon | Plugin: avg tick | Plugin: P50 / P95 / P99 | Plugin: CPU | Mod: avg tick | Mod: P50 / P95 / P99 | Mod: CPU |
|---|---|---|---|---|---|---|
| 0 | 1.9 ms | 1.8 / 2.5 / 4.9 | 18% | 10.8 ms | 10.7 / 13.1 / 14.2 | 34% |
| 25 | 2.6 ms | 2.5 / 3.9 / 4.8 | 87% | 12.1 ms | 11.8 / 14.8 / 17.6 | 37% |
| 50 | 3.2 ms | 3.0 / 5.0 / 6.9 | 153% | 13.2 ms | 13.1 / 16.8 / 21.5 | 38% |
| 100 | 4.6 ms | 4.3 / 7.9 / 12.9 | **284%** | 17.6 ms | 17.6 / 25.1 / 26.1 | 43% |

The server lags once a tick takes longer than 50 ms. Raw output is in `raw_plugin.txt` and `raw_mod.txt`. Screenshots of both 100-Pokémon fields are in `screenshots/`.

## Reading it

**Main-thread tick time: the plugin is cheaper.**
- Each Pokémon costs about 0.027 ms per tick on the plugin and about 0.068 ms on the mod.
- Neither setup is near the 50 ms limit at 100 Pokémon.
- Most of the difference at 0 Pokémon (1.9 vs 10.8 ms) comes from Paper's optimisations versus vanilla Fabric plus Cobblemon's own systems. It isn't caused by the Pokémon.

**Total CPU: the mod is far cheaper.**
- With the mod, each player's client animates its own Pokémon.
- With the plugin, the server computes every bone of every visible model and streams it to each viewer on async threads. At 100 Pokémon that is 284%, nearly 3 of 8 threads, with **one** viewer.
- The plugin's cost grows with players × Pokémon in view. A plugin server therefore needs low per-player spawn caps (about 8–10) and a short model render distance.

## Caveats

- **Not equal setups:** different Minecraft versions (26.3 vs 1.21.1), different server software, and different AI (husk vs Cobblemon's AI).
- **Same machine:** the viewer client ran on the same laptop with software rendering, so it competed for CPU in both runs.
- **Not measured:**
  - Network bandwidth. Bone streaming should use far more upload than the mod, which matters behind tunnels and for mobile players.
  - Battles.
  - More than one viewer.

## Reproducing

1. Run the server.
2. Run a bot client named `PDTest` on `Xvfb :99` (the setup used portablemc with offline mode).
3. Run the benchmark:
   ```
   C=<container> PY=<python with python-xlib> ./bench_plugin.sh
   C=<container> ./bench_mod.sh
   ```

`bm spawn` is player-only, which is why the plugin benchmark types commands through `xkey.py`. `snap.py` turns the Xvfb framebuffer into a PNG.
