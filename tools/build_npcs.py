"""Cobblemon's NPC models (bedrock/npcs: the trainer and Professor Sacchi) converted like the Pokémon (build_all.build),
with trainer_generic's idle, battle and win animations. Npcs.java puts them on the NPC's Mannequin.
  python3 tools/build_npcs.py RES_DIR OUT_DIR   ->  OUT_DIR/npcs/{java,bedrock}/npc_<name>.*
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import build_all  # noqa: E402

NPCS = {'trainer': ('trainer', 'standard/trainer'), 'sacchi': ('sacchi', 'sacchi/sacchi')}  # name -> (geo, texture)


def jobs(res):
    A = f'{res}/assets/cobblemon'
    anim = f'{A}/bedrock/npcs/animations/trainer_generic.animation.json'
    anims = {'idle': (anim, 'idle'), 'battle_idle': (anim, 'idle_battle'), 'walk': (anim, 'idle'),
             'physical': (anim, 'command'), 'special': (anim, 'command'), 'win': (anim, 'win'), 'faint': (anim, 'lose')}
    return [(f'npc_{n}', f'{A}/bedrock/npcs/models/{geo}.geo.json', [f'{A}/textures/npcs/{tex}.png'], 1.0, anims, None)
            for n, (geo, tex) in NPCS.items()]


if __name__ == '__main__':
    res, out = sys.argv[1:]
    for j in jobs(res):
        print(build_all.build(j, f'{out}/npcs'))
