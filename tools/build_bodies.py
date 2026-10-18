"""Whole-Pokémon pictures for the PC (PcScreen.java): every look (looks.py) in Cobblemon's tile framing.

usage: build_bodies.py COBBLEMON_RESOURCES OUT_DIR   (OUT_DIR/java/*.bbmodel + species.tsv in; out: OUT_DIR/bodies/<name>.png)
Each is the starter list's tile framing (portrait.TILE: drawProfilePokemon at 25 px), inside its 1 px frame (23 px),
at 2 px a GUI px: 46 x 46. build_ui.py makes them font glyphs, drawn at 16 px in a box slot and 46 px in the info strip.
"""
import base64, json, os, sys
from multiprocessing import Pool

sys.path.insert(0, os.path.dirname(__file__))
import looks
import portrait
from build_cards import profile_framing

PX = 2


def render(job):
    name, poser, base, res, out = job
    try:
        bb = json.load(open(f'{out}/java/{name}.bbmodel'))
        png = base64.b64decode(bb['textures'][0]['source'].split(',', 1)[1])
        img = portrait.profile(bb, png, *profile_framing(res, poser), base, PX, **portrait.TILE)
        img.crop((PX, PX, img.width - PX, img.height - PX)).save(f'{out}/bodies/{name}.png')
        return None
    except Exception as e:  # one broken look must not stop the rest
        return f'{name}: {type(e).__name__}: {e}'


def main(res, out):
    bases = {l.split('\t')[0]: float(l.split('\t')[1]) for l in open(f'{out}/species.tsv').read().splitlines()[1:]}
    rs = looks.resolvers(res)
    jobs = []
    for species, found in sorted(looks.all_looks(res).items()):
        for name, aspects, _model, _texture in found:
            if os.path.exists(f'{out}/java/{name}.bbmodel'):
                poser = (looks.poser(rs[species], set(aspects)) or f'cobblemon:{species}').split(':')[1]
                jobs.append((name, poser, bases.get(name, 1.0), res, out))
    os.makedirs(f'{out}/bodies', exist_ok=True)
    with Pool() as p:
        bad = [r for r in p.map(render, jobs) if r]
    print(len(jobs) - len(bad), 'bodies,', len(bad), 'failed')
    for b in bad:
        print(' ', b)


if __name__ == '__main__':
    main(*sys.argv[1:3])
