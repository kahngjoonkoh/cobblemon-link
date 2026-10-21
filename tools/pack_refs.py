#!/usr/bin/env python3
"""Check that every client entity in a Bedrock pack points at things the pack defines: geometry, animations,
animation controllers, render controllers and textures. GeyserModelEngine shares identical animations and geometry
between models (server/patches), so an entity can point at another model's ids; this catches a dangling one.

usage: pack_refs.py <unpacked pack dir or .zip>   (prints a summary, exits 1 on a missing reference)
       pack_refs.py demo                           (self-check)
"""
import json, sys, zipfile, tempfile, os
from pathlib import Path

COMMON = {"animation.common.look_at_target"}  # Geyser's built-in look-at animation


def check(root: Path):
    defined = {"geometry": set(), "animation": set(), "controller": set(), "render": set()}
    for f in root.glob("models/**/*.json"):
        for g in json.loads(f.read_text()).get("minecraft:geometry", []):
            defined["geometry"].add(g["description"]["identifier"])
    for f in root.glob("animations/**/*.json"):
        defined["animation"] |= json.loads(f.read_text()).get("animations", {}).keys()
    for f in root.glob("animation_controllers/**/*.json"):
        defined["controller"] |= json.loads(f.read_text()).get("animation_controllers", {}).keys()
    for f in root.glob("render_controllers/**/*.json"):
        defined["render"] |= json.loads(f.read_text()).get("render_controllers", {}).keys()

    missing, entities = [], 0
    for f in root.glob("entity/**/*.json"):
        d = json.loads(f.read_text())["minecraft:client_entity"]["description"]
        entities += 1
        for g in d.get("geometry", {}).values():
            if g not in defined["geometry"]: missing.append((d["identifier"], g))
        for a in d.get("animations", {}).values():
            kind = "controller" if a.startswith("controller.") else "animation"
            if a not in defined[kind] and a not in COMMON: missing.append((d["identifier"], a))
        for r in d.get("render_controllers", []):
            if (r if isinstance(r, str) else next(iter(r))) not in defined["render"]: missing.append((d["identifier"], r))
        for t in d.get("textures", {}).values():
            if not (root / (t + ".png")).exists() and not (root / (t + ".tga")).exists(): missing.append((d["identifier"], t))
    return entities, missing


def main(arg):
    if arg.endswith(".zip"):
        tmp = tempfile.mkdtemp()
        zipfile.ZipFile(arg).extractall(tmp)
        arg = tmp
    entities, missing = check(Path(arg))
    for m in missing[:20]: print("missing:", *m)
    print(f"{entities} entities, {len(missing)} missing references")
    return 1 if missing else 0


def demo():
    with tempfile.TemporaryDirectory() as t:
        r = Path(t)
        for sub in ("models/entity", "animations", "animation_controllers", "render_controllers", "entity", "textures/entity"):
            (r / sub).mkdir(parents=True)
        (r / "models/entity/a.json").write_text(json.dumps({"minecraft:geometry": [{"description": {"identifier": "geometry.a"}}]}))
        (r / "animations/a.json").write_text(json.dumps({"animations": {"animation.a.idle": {}}}))
        (r / "animation_controllers/a.json").write_text(json.dumps({"animation_controllers": {"controller.animation.a.idle": {}}}))
        (r / "render_controllers/a.json").write_text(json.dumps({"render_controllers": {"controller.render.meg_a": {}, "controller.render.meg_b": {}}}))
        (r / "textures/entity/a.png").write_bytes(b"")
        (r / "textures/entity/b.png").write_bytes(b"")
        ent = lambda name, tex: {"minecraft:client_entity": {"description": {"identifier": "ns:" + name,
            "geometry": {"x": "geometry.a"}, "textures": {"default": "textures/entity/" + tex},
            "animations": {"idle": "animation.a.idle", "idle_control": "controller.animation.a.idle",
                           "look_at_target": "animation.common.look_at_target"},
            "render_controllers": ["controller.render.meg_" + name]}}}
        (r / "entity/a.json").write_text(json.dumps(ent("a", "a")))
        (r / "entity/b.json").write_text(json.dumps(ent("b", "b")))  # b shares a's geometry and animations
        assert check(r) == (2, [])
        (r / "entity/c.json").write_text(json.dumps(ent("c", "c")))  # no render controller, no texture
        assert len(check(r)[1]) == 2
    print("pack_refs demo ok")


if __name__ == "__main__":
    if len(sys.argv) != 2: sys.exit(__doc__)
    sys.exit(demo() if sys.argv[1] == "demo" else main(sys.argv[1]))
