#!/usr/bin/env python3
"""Cobblemon's NPCs drawn like Pokémon: each NPC variation's geometry and skin through the same pipeline as the
Pokémon (the shared tools/geo2bb.py, bil_models.py, build_models.py), added to MODELS (item models, data, index.json)
and to GeyserModelEngine's input as "npc_<geometry>". Skins are padded square first: Minecraft takes a
taller-than-wide texture for animation frames.
usage: npc_models.py COBBLEMON_DIR MODELS_DIR GME_INPUT_DIR [WORK_DIR]"""
import json, os, subprocess, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
GEO2BB = os.path.join(HERE, '..', '..', 'tools', 'geo2bb.py')
ANIMS = 'idle=idle,send_out=send_out,return=return,win=win,lose=lose,idle_battle=battle_idle'


def variations(c):
    """(geometry, skin) of each NPC variation that has both, once each."""
    out = {}
    base = os.path.join(c, 'assets/cobblemon/bedrock/npcs/variations')
    for root, _, files in os.walk(base):
        for f in files:
            for v in json.load(open(os.path.join(root, f))).get('variations', []):
                if v.get('model') and ':' in v.get('texture', ''):  # "variable": a player-chosen skin
                    out[v['model'].split(':')[1].replace('.geo', '')] = v['texture'].split(':')[1]
    return out


def square(src_png, dst_png, geo, dst_geo):
    im = Image.open(src_png).convert('RGBA')
    s = max(im.size)
    sq = Image.new('RGBA', (s, s))
    sq.paste(im, (0, 0))
    sq.save(dst_png)
    d = geo['minecraft:geometry'][0]['description']
    d['texture_width'] = d['texture_height'] = s
    json.dump(geo, open(dst_geo, 'w'))


def main(c, models, gme, work):
    a = os.path.join(c, 'assets/cobblemon/')
    for d in ('src', 'java', 'bil', 'out'): os.makedirs(os.path.join(work, d), exist_ok=True)
    for geo, tex in variations(c).items():
        g = json.load(open(a + 'bedrock/npcs/models/' + geo + '.geo.json'))
        sg, sp = os.path.join(work, 'src', geo + '.geo.json'), os.path.join(work, 'src', geo + '.png')
        square(a + tex, sp, g, sg)
        subprocess.run([sys.executable, GEO2BB, sg, a + 'bedrock/npcs/animations/trainer_generic.animation.json', sp, 'npc_' + geo,
                        os.path.join(work, 'java'), gme, ANIMS], check=True, stdout=subprocess.DEVNULL)
    subprocess.run([sys.executable, os.path.join(HERE, 'bil_models.py'), os.path.join(work, 'java'), os.path.join(work, 'bil')], check=True, stdout=subprocess.DEVNULL)
    subprocess.run([sys.executable, os.path.join(HERE, 'build_models.py'), os.path.join(work, 'bil'), os.path.join(work, 'out')], check=True, stdout=subprocess.DEVNULL)
    subprocess.run(['cp', '-r', os.path.join(work, 'out', 'pack'), os.path.join(work, 'out', 'data'), models], check=True)
    idx_p = os.path.join(models, 'index.json')
    idx = json.load(open(idx_p)) if os.path.exists(idx_p) else {}
    idx.update(json.load(open(os.path.join(work, 'out', 'index.json'))))
    json.dump(idx, open(idx_p, 'w'))
    print(len(variations(c)), 'NPC models')


if __name__ == '__main__':
    if len(sys.argv) < 4: sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else os.path.expanduser('~/.cache/cl-fabric/npcs'))
