# Assets

All model, animation and texture files here are **adapted from [Cobblemon](https://gitlab.com/cable-mc/cobblemon)**, taken from release 1.8.2 (`assets/cobblemon/...`). Cobblemon licenses these assets under **Creative Commons Attribution-NonCommercial 3.0**. The full text is in [`LICENSE-CC-BY-NC-3.0.txt`](LICENSE-CC-BY-NC-3.0.txt) and is copied verbatim from the Cobblemon release.

- **Attribution:** the models and textures are by the Cobblemon team and contributors.
- **NonCommercial:** these files, and servers using them, may not be used for commercial purposes. That rules out paid ranks or perks involving them.
- **Changes made:** we label them here, as the license requires.

| File | Source | Changes |
|---|---|---|
| `java/pikachu.bbmodel` | `bedrock/pokemon/models/0025_pikachu/pikachu_male.geo.json`, `animations/0025_pikachu/pikachu.animation.json`, `textures/pokemon/0025_pikachu/pikachu.png` | Converted to Blockbench `.bbmodel` by `tools/geo2bb.py`: X axis mirrored for Blockbench, box UVs expanded to per-face UVs, Molang animation expressions baked to keyframes at 20/s. `ground_idle` was renamed `idle` and `ground_walk` was renamed `walk`. Other animations were dropped. |
| `bedrock/pikachu/*` | same sources | Geometry identifier renamed to `geometry.pikachu`. Only the `idle` and `walk` animations were kept, with the same renames. The texture is unchanged. |

The species scale (Pikachu is 0.5 in Cobblemon) is **not** applied yet, so the model renders at twice Cobblemon's size.
