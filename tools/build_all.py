"""Convert every Cobblemon species (base form) into BetterModel + GeyserModelEngine inputs.

usage: build_all.py COBBLEMON_RESOURCES OUT_DIR
  COBBLEMON_RESOURCES: Cobblemon's common/src/main/resources (see fetch_cobblemon.sh)
  OUT_DIR gets java/<name>.bbmodel, bedrock/<name>/..., portraits/<name>.png (+ _r, the wild side), species.tsv
"""
import base64, glob, io, json, os, re, sys
from multiprocessing import Pool
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import geo2bb
import looks
import portrait

BEDROCK_ANIM = re.compile(r"q\.bedrock(?:_primary|_stateful)?\('([^']+)',\s*'([^']+)'")
# PokemonEntity.playAnimation's names in a poser's "animations" (BattleAnimations: a move by category, hit, faint)
BATTLE = ('cry', 'recoil', 'faint', 'physical', 'special')
# Cobblemon's Poké Ball (PokeBallModel, EmptyPokeBallEntity): its idle is shut_idle; 'throw' loops in flight
BALL = ('throw', 'open', 'shut', 'bounce', 'bob1', 'bob2', 'bob3', 'capture', 'break')
KT_SCALE = re.compile(r"portraitScale\s*=\s*([-\d.]+)F?")
KT_TRANSLATION = re.compile(r"portraitTranslation\s*=\s*Vec3\(\s*([-\d.]+),\s*([-\d.]+),\s*([-\d.]+)\s*\)")


def kotlin_portraits(res):
    """{poser name: (portraitScale, portraitTranslation)} from Kotlin models (GengarModel.kt -> gengar)."""
    out = {}
    for p in glob.glob(f'{res}/com/**/*Model.kt', recursive=True):
        src = open(p).read()
        s, t = KT_SCALE.search(src), KT_TRANSLATION.search(src)
        if s or t:
            out[os.path.basename(p)[:-8].lower()] = (float(s.group(1)) if s else 1.0,
                                                     [float(x) for x in t.groups()] if t else [0.0, 0.0, 0.0])
    return out


def index(pattern, key):
    return {key(p): p for p in glob.glob(pattern, recursive=True)}


def pose_anims(poser, want, avoid=('battle', 'surface', 'water', 'swim', 'fly', 'hover', 'sleep', 'shoulder', 'ride'), need=''):
    """First q.bedrock(...) of the first pose with poseType `want` whose name avoids special states (and has `need`)."""
    poses = sorted(poser.get('poses', {}).items(), key=lambda kv: 'NONE' not in kv[1].get('poseTypes', []))
    for pname, p in poses:
        if want in p.get('poseTypes', []) and need in pname.lower() and not any(w in pname.lower() for w in avoid):
            for a in p.get('animations', []):
                m = BEDROCK_ANIM.search(a if isinstance(a, str) else a.get('animation', ''))
                if m:
                    return m.groups()
    return None


def plan(res):
    """[(name, geo_path, [texture, layer, ...], scale, {dst: (anim_file, anim_name)}, portrait)] for every look of every species
    (looks.py: base, shiny, gender, regional and other forms)."""
    A = f'{res}/assets/cobblemon'
    models = index(f'{A}/bedrock/pokemon/models/**/*.geo.json', lambda p: os.path.basename(p)[:-5])
    anim_files = index(f'{A}/bedrock/pokemon/animations/**/*.animation.json', lambda p: os.path.basename(p)[:-15])
    posers = index(f'{A}/bedrock/pokemon/posers/**/*.json', lambda p: os.path.basename(p)[:-5])
    scales = {os.path.basename(p)[:-5]: json.load(open(p)).get('baseScale', 1.0)
              for p in glob.glob(f'{res}/data/cobblemon/species/**/*.json', recursive=True)}
    kotlin = kotlin_portraits(res)
    rs_by = looks.resolvers(res)
    jobs = []
    for species, found in sorted(looks.all_looks(res).items()):
        for name, aspects, model, texture in found:
            geo = model.split(':')[1]
            if geo not in models:
                continue
            poser_name = (looks.poser(rs_by[species], set(aspects)) or f'cobblemon:{species}').split(':')[1]
            poser = json.load(open(posers[poser_name])) if poser_name in posers else {}
            anims = {}
            for dst, want, fallback in (('idle', 'STAND', 'ground_idle'), ('walk', 'WALK', 'ground_walk')):
                anims[dst] = pose_anims(poser, want) or (poser_name, fallback)
            anims['battle_idle'] = pose_anims(poser, 'STAND', avoid=('water', 'fly', 'hover'), need='battle') or (poser_name, 'battle_idle')
            for dst in BATTLE:
                m = BEDROCK_ANIM.search(poser.get('animations', {}).get(dst, ''))
                anims[dst] = m.groups() if m else (poser_name, dst)
            anims = {k: (anim_files[f], a) for k, (f, a) in anims.items() if f in anim_files}
            framing = (poser['portraitScale'], poser.get('portraitTranslation', [0, 0, 0])) if 'portraitScale' in poser \
                else kotlin.get(poser_name, (1.0, [0.0, 0.0, 0.0]))
            tex = [f"{A}/{t.split(':')[1]}" for t in [looks.first_frame(texture)] + looks.layers(rs_by[species], set(aspects))]
            jobs.append((name, models[geo], tex, scales.get(species, 1.0), anims, framing))
    B = f'{A}/bedrock/poke_balls'
    if os.path.exists(f'{B}/models/poke_ball.geo.json'):  # no portraits: it isn't a Pokémon
        anims = {dst: (f'{B}/animations/poke_ball.animation.json', dst) for dst in BALL} | \
                {'idle': (f'{B}/animations/poke_ball.animation.json', 'shut_idle')}
        jobs.append(('poke_ball', f'{B}/models/poke_ball.geo.json', [f'{A}/textures/poke_balls/poke_ball.png'], 1.0, anims, None))
    return jobs


def texture(paths):
    """The base texture with its render layers painted on (looks.layers), as PNG bytes; a missing layer is skipped."""
    if len(paths) == 1:
        return open(paths[0], 'rb').read()
    import numpy as np
    img = Image.open(paths[0]).convert('RGBA')
    for p in paths[1:]:
        if os.path.exists(p):
            layer = np.array(Image.open(p).convert('RGBA').resize(img.size, Image.NEAREST))
            # where the base is empty only solid layer pixels (a flame): the models are cutout, so see-through ones
            # (Chandelure's glass) would turn solid and hide what's behind them
            layer[(np.asarray(img)[:, :, 3] == 0) & (layer[:, :, 3] < 255), 3] = 0
            img.alpha_composite(Image.fromarray(layer))
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    return buf.getvalue()


def build(job, out):
    name, geo_p, tex_p, scale, anim_refs, framing = job
    try:
        anims = {}
        for dst, (f, a) in anim_refs.items():
            src = json.load(open(f))['animations']
            key = next((k for k in src if k.endswith('.' + a)), None)
            # ponytail: a battle animation over 8 s (Charizard's 40 s battle_idle) is left out: Java keeps every
            # sampled keyframe in memory; the plugin falls back to idle
            if key and (dst in ('idle', 'walk') or float(src[key].get('animation_length', 0)) <= 8):
                anims[dst] = src[key]
        geo = json.load(open(geo_p))['minecraft:geometry'][0]
        png = texture(tex_p)
        bb, g, an, dropped = geo2bb.convert(geo, anims, png, name, scale)
        geo2bb.write(name, bb, g, an, png, f'{out}/java', f'{out}/bedrock')
        os.makedirs(f'{out}/portraits', exist_ok=True)
        for suffix, rev in (('', False), ('_r', True)) if framing else ():
            portrait.render(bb, png, *framing, scale, rev).save(f'{out}/portraits/{name}{suffix}.png')
        return f'{name}\t{scale}\t{",".join(anims)}\t{len(dropped)}\tok'
    except Exception as e:  # one broken species must not stop the other thousand
        return f'{name}\t{scale}\t\t\tFAILED {type(e).__name__}: {e}'


def main(a):
    if len(a) != 2:
        sys.exit(__doc__)
    res, out = a
    jobs = plan(res)
    with Pool() as p:
        rows = p.starmap(build, [(j, out) for j in jobs])
    open(f'{out}/species.tsv', 'w').write('name\tscale\tanimations\tjava_dropped_channels\tstatus\n' + '\n'.join(rows) + '\n')
    bad = [r for r in rows if 'FAILED' in r]
    print(len(rows) - len(bad), 'converted,', len(bad), 'failed')
    for r in bad:
        print(' ', r)


def demo():
    poser = {'poses': {'battle-standing': {'poseTypes': ['STAND'], 'animations': ["q.bedrock('x', 'battle_idle')"]},
                       'standing': {'poseTypes': ['STAND', 'NONE'], 'animations': ["q.look('head')", "q.bedrock('x', 'ground_idle')"]},
                       'walk': {'poseTypes': ['WALK'], 'animations': [{'animation': "q.bedrock('x', 'ground_walk')"}]}}}
    assert pose_anims(poser, 'STAND') == ('x', 'ground_idle')
    assert pose_anims(poser, 'WALK') == ('x', 'ground_walk')
    assert pose_anims({}, 'STAND') is None
    assert pose_anims(poser, 'STAND', avoid=(), need='battle') == ('x', 'battle_idle')
    assert BEDROCK_ANIM.search("q.bedrock_primary('ivysaur', 'faint', q.curve('one'))").groups() == ('ivysaur', 'faint')


if __name__ == '__main__':
    demo()
    main(sys.argv[1:])
