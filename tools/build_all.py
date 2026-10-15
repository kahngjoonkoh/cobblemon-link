"""Convert every Cobblemon species (base form) into BetterModel + GeyserModelEngine inputs.

usage: build_all.py COBBLEMON_RESOURCES OUT_DIR
  COBBLEMON_RESOURCES: Cobblemon's common/src/main/resources (see fetch_cobblemon.sh)
  OUT_DIR gets java/<name>.bbmodel, bedrock/<name>/..., species.tsv
"""
import glob, json, os, re, sys
from multiprocessing import Pool

sys.path.insert(0, os.path.dirname(__file__))
import geo2bb

BEDROCK_ANIM = re.compile(r"q\.bedrock\('([^']+)',\s*'([^']+)'")


def index(pattern, key):
    return {key(p): p for p in glob.glob(pattern, recursive=True)}


def pose_anims(poser, want, avoid=('battle', 'surface', 'water', 'swim', 'fly', 'hover', 'sleep', 'shoulder', 'ride')):
    """First q.bedrock(...) of the first pose with poseType `want` whose name avoids special states."""
    poses = sorted(poser.get('poses', {}).items(), key=lambda kv: 'NONE' not in kv[1].get('poseTypes', []))
    for pname, p in poses:
        if want in p.get('poseTypes', []) and not any(w in pname.lower() for w in avoid):
            for a in p.get('animations', []):
                m = BEDROCK_ANIM.search(a if isinstance(a, str) else a.get('animation', ''))
                if m:
                    return m.groups()
    return None


def plan(res):
    """[(name, geo_path, tex_path, scale, {dst: (anim_file, anim_name)})] for every base-form resolver."""
    A = f'{res}/assets/cobblemon'
    models = index(f'{A}/bedrock/pokemon/models/**/*.geo.json', lambda p: os.path.basename(p)[:-5])
    anim_files = index(f'{A}/bedrock/pokemon/animations/**/*.animation.json', lambda p: os.path.basename(p)[:-15])
    posers = index(f'{A}/bedrock/pokemon/posers/**/*.json', lambda p: os.path.basename(p)[:-5])
    scales = {os.path.basename(p)[:-5]: json.load(open(p)).get('baseScale', 1.0)
              for p in glob.glob(f'{res}/data/cobblemon/species/**/*.json', recursive=True)}
    jobs = []
    for r in sorted(glob.glob(f'{A}/bedrock/pokemon/resolvers/**/*.json', recursive=True)):
        d = json.load(open(r))
        if d.get('order', 0) != 0:  # ponytail: base forms only; regional forms/costumes later
            continue
        name = d['species'].split(':')[1]
        v = next((v for v in d['variations'] if not v.get('aspects') and 'model' in v), None)
        if not v or not isinstance(v.get('texture'), str) or v['model'].split(':')[1] not in models:
            continue
        poser_name = v.get('poser', d['species']).split(':')[1]
        poser = json.load(open(posers[poser_name])) if poser_name in posers else {}
        anims = {}
        for dst, want, fallback in (('idle', 'STAND', 'ground_idle'), ('walk', 'WALK', 'ground_walk')):
            anims[dst] = pose_anims(poser, want) or (poser_name, fallback)
        anims = {k: (anim_files[f], a) for k, (f, a) in anims.items() if f in anim_files}
        jobs.append((name, models[v['model'].split(':')[1]], f"{A}/{v['texture'].split(':')[1]}",
                     scales.get(name, 1.0), anims))
    return jobs


def build(job, out):
    name, geo_p, tex_p, scale, anim_refs = job
    try:
        anims = {}
        for dst, (f, a) in anim_refs.items():
            src = json.load(open(f))['animations']
            key = next((k for k in src if k.endswith('.' + a)), None)
            if key:
                anims[dst] = src[key]
        geo = json.load(open(geo_p))['minecraft:geometry'][0]
        png = open(tex_p, 'rb').read()
        bb, g, an, dropped = geo2bb.convert(geo, anims, png, name, scale)
        geo2bb.write(name, bb, g, an, png, f'{out}/java', f'{out}/bedrock')
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


if __name__ == '__main__':
    demo()
    main(sys.argv[1:])
