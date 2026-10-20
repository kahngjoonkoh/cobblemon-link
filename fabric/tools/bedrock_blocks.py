#!/usr/bin/env python3
"""Cobblemon's machine blocks (healing machine, PC, pasture...) for Bedrock players: each block model a block state
uses becomes a GeyserModelEngine model "clb_<model>" (geometry from the Java model's cubes, its textures packed into
one atlas), which the server shows on a carrier at the block (Furniture.java). Java models are Blockbench's: the
same conversion as geo2bb.py, backwards (x mirrored, rotations' x and y negated).
ponytail: no per-face UV rotation; animated textures show their first frame.
usage: [VANILLA_JAR=client.jar] bedrock_blocks.py COBBLEMON_JAR_OR_DIR OUT_DIR BLOCK... ('plants': every berry bush, apricorn and mint; 'berries': their fruit and flowers)"""
import io, json, os, sys, zipfile
from PIL import Image


class Src:
    def __init__(self, path):
        self.z = None if os.path.isdir(path) else zipfile.ZipFile(path)
        self.dir = path

    def read(self, n):
        return self.z.read(n) if self.z else open(os.path.join(self.dir, n), 'rb').read()

    def has(self, n):
        if self.z:
            try: self.z.getinfo(n); return True
            except KeyError: return False
        return os.path.exists(os.path.join(self.dir, n))


class Srcs:
    """Several sources, the first that has a file wins (Cobblemon's jar, then Minecraft's for its parent models)."""
    def __init__(self, *srcs): self.srcs = srcs
    def read(self, n): return next(s for s in self.srcs if s.has(n)).read(n)
    def has(self, n): return any(s.has(n) for s in self.srcs)
    @property
    def z(self): return self.srcs[0].z
    @property
    def dir(self): return self.srcs[0].dir


def ref(r, kind):
    ns, p = r.split(':') if ':' in r else ('minecraft', r)
    return f'assets/{ns}/{kind}/{p}'


def model(src, name):
    """Elements and textures with the parents' filled in."""
    if not src.has(ref(name, 'models') + '.json'): return {'textures': {}, 'elements': []}
    m = json.loads(src.read(ref(name, 'models') + '.json'))
    tex, els = dict(m.get('textures', {})), m.get('elements')
    if m.get('parent') and not m['parent'].startswith('minecraft:block/block') and src.has(ref(m['parent'], 'models') + '.json'):
        p = model(src, m['parent'])
        tex = {**p['textures'], **tex}
        if els is None: els = p['elements']
    return {'textures': tex, 'elements': els or []}


def resolve(tex, key):
    for _ in range(8):
        if not key.startswith('#'): return key
        key = tex.get(key[1:], key)
    return None


def convert(src, name, out):
    m = model(src, name)
    used = []
    for e in m['elements']:
        for f in e.get('faces', {}).values():
            t = resolve(m['textures'], f['texture'])
            if t and t not in used: used.append(t)
    imgs = {}
    for t in used:
        p = ref(t, 'textures') + '.png'
        if not src.has(p): continue
        im = Image.open(io.BytesIO(src.read(p))).convert('RGBA')
        if im.height > im.width and im.height % im.width == 0: im = im.crop((0, 0, im.width, im.width))  # first frame
        imgs[t] = im
    if not imgs: return False
    W, H = sum(i.width for i in imgs.values()), max(i.height for i in imgs.values())
    atlas, at, x = Image.new('RGBA', (W, H)), {}, 0
    for t, im in imgs.items():
        atlas.paste(im, (x, 0))
        at[t] = (x, 0, im.width, im.height)
        x += im.width
    cubes = []
    for e in m['elements']:
        f, t = e['from'], e['to']
        c = {'origin': [8 - t[0], f[1], f[2] - 8], 'size': [t[0] - f[0], t[1] - f[1], t[2] - f[2]], 'uv': {}}
        for side, face in e.get('faces', {}).items():
            tx = resolve(m['textures'], face['texture'])
            if tx not in at: continue
            ox, oy, tw, th = at[tx]
            u1, v1, u2, v2 = face.get('uv', [0, 0, 16, 16])
            c['uv'][side] = {'uv': [ox + u1 / 16 * tw, oy + v1 / 16 * th], 'uv_size': [(u2 - u1) / 16 * tw, (v2 - v1) / 16 * th]}
        r = e.get('rotation')
        if r and r.get('angle'):
            o = r['origin']
            rot = {'x': [-r['angle'], 0, 0], 'y': [0, -r['angle'], 0], 'z': [0, 0, r['angle']]}[r['axis']]
            c['pivot'], c['rotation'] = [-(o[0] - 8), o[1], o[2] - 8], rot
        cubes.append(c)
    ident = 'clb_' + name.split('/')[-1]
    d = os.path.join(out, ident)
    os.makedirs(d, exist_ok=True)
    atlas.save(os.path.join(d, ident + '.png'))
    json.dump({'format_version': '1.12.0', 'minecraft:geometry': [{'description': {'identifier': 'geometry.' + ident,
               'texture_width': W, 'texture_height': H, 'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1, 0]},
               'bones': [{'name': 'block', 'pivot': [0, 0, 0], 'cubes': cubes}]}]}, open(os.path.join(d, ident + '.geo.json'), 'w'))
    json.dump({'format_version': '1.8.0', 'animations': {'idle': {'loop': True, 'animation_length': 1, 'bones': {}}}},
              open(os.path.join(d, ident + '.animation.json'), 'w'))
    return True


def models_of(src, block):
    bs = json.loads(src.read(f'assets/cobblemon/blockstates/{block}.json'))
    out = set()
    for v in bs.get('variants', {}).values():
        for x in (v if isinstance(v, list) else [v]): out.add(x['model'])
    for part in bs.get('multipart', []):
        a = part['apply']
        for x in (a if isinstance(a, list) else [a]): out.add(x['model'])
    return sorted(out)


def berries(src, out):
    """The fruit and flower each berry bush shows (Cobblemon draws them on the grown bush): its Bedrock geometry as
    it is, as GeyserModelEngine model "clberry_<geometry>" with its texture."""
    base = 'assets/cobblemon/'
    names = src.z.namelist() if src.z else [os.path.relpath(os.path.join(r, f), src.dir) for r, _, fs in os.walk(os.path.join(src.dir, 'data/cobblemon/berries')) for f in fs]
    pairs = set()
    for n in names:
        if n.startswith('data/cobblemon/berries/') and n.endswith('.json'):
            d = json.loads(src.read(n))
            for m, t in ((d.get('fruitModel'), d.get('fruitTexture')), (d.get('flowerModel'), d.get('flowerTexture'))):
                if m and t: pairs.add((m.split(':')[1], t.split(':')[1]))
    n = 0
    for geo, tex in sorted(pairs):
        g = base + 'bedrock/berries/' + geo + '.json'
        t = base + 'textures/berries/' + tex + '.png'
        if not (src.has(g) and src.has(t)): continue
        name = 'clberry_' + geo.replace('.geo', '')
        d = json.loads(src.read(g))
        d['minecraft:geometry'][0]['description']['identifier'] = 'geometry.' + name
        os.makedirs(os.path.join(out, name), exist_ok=True)
        json.dump(d, open(os.path.join(out, name, name + '.geo.json'), 'w'))
        open(os.path.join(out, name, name + '.png'), 'wb').write(src.read(t))
        json.dump({"format_version": "1.8.0", "animations": {"idle": {"loop": True, "animation_length": 1, "bones": {}}}},
                  open(os.path.join(out, name, name + '.animation.json'), 'w'))
        n += 1
    return n


def plants(src):
    """Cobblemon's plants and crystals (Furniture.plant): berry bushes, apricorns, mints, saplings, clusters."""
    base = 'assets/cobblemon/blockstates/'
    names = src.z.namelist() if src.z else [base + f for f in os.listdir(os.path.join(src.dir, base))]
    plant = lambda b: b.endswith(('_berry', '_apricorn', '_mint', '_cluster')) or 'sapling' in b or 'budding_' in b
    return sorted(n[len(base):-5] for n in names if n.startswith(base) and n.endswith('.json') and plant(n[len(base):-5]))


def demo():
    import tempfile
    with tempfile.TemporaryDirectory() as t:
        os.makedirs(t + '/assets/cobblemon/models/block'); os.makedirs(t + '/assets/cobblemon/textures/block'); os.makedirs(t + '/assets/cobblemon/blockstates')
        Image.new('RGBA', (16, 16), (255, 0, 0, 255)).save(t + '/assets/cobblemon/textures/block/a.png')
        json.dump({'textures': {'all': 'cobblemon:block/a'}, 'elements': [{'from': [0, 0, 0], 'to': [4, 16, 16],
                   'faces': {'north': {'uv': [0, 0, 8, 16], 'texture': '#all'}}}]}, open(t + '/assets/cobblemon/models/block/m.json', 'w'))
        json.dump({'variants': {'': {'model': 'cobblemon:block/m'}}}, open(t + '/assets/cobblemon/blockstates/x.json', 'w'))
        src = Src(t)
        assert models_of(src, 'x') == ['cobblemon:block/m'] and convert(src, 'cobblemon:block/m', t + '/out')
        g = json.load(open(t + '/out/clb_m/clb_m.geo.json'))['minecraft:geometry'][0]['bones'][0]['cubes'][0]
        assert g['origin'] == [4, 0, -8] and g['uv']['north'] == {'uv': [0, 0], 'uv_size': [8, 16]}


if __name__ == '__main__':
    demo()
    # VANILLA_JAR: Minecraft's client jar, for parents such as block/cross (saplings) and amethyst_cluster
    src, out = Srcs(*[Src(p) for p in (sys.argv[1], os.environ.get('VANILLA_JAR')) if p]), sys.argv[2]
    n = 0
    for b in sys.argv[3:]:
        if b == 'berries':
            print(berries(src, out), 'berry models')
            continue
        for name in plants(src) if b == 'plants' else [b]:
            for m in models_of(src, name): n += convert(src, m, out)
    print(n, 'block models')
