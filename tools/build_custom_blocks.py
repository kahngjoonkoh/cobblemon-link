#!/usr/bin/env python3
"""Cobblemon's own blocks as real blocks for players without the mod (docs/custom-blocks.md). Each one takes a block
state the plugin holds back from vanilla, and both clients are told to draw that state as Cobblemon's block:
- full cubes (ores, evolution-stone blocks, apricorn wood and leaves) take mushroom block states with the bottom face
  shown (down=true) but not all six: vanilla's huge mushrooms never generate those, and CustomBlocks.java keeps players
  from building them (Paper's block-updates.disable-mushroom-block-updates; the plugin works out mushroom faces itself);
- plants (apricorns, apricorn saplings, berry bushes, mints) take tripwire states with disarmed=true: vanilla string
  only holds that while a shears cut is breaking it, and the plugin does the tripwire's work for real string (Paper's
  disable-tripwire-updates). Berry bushes and mints draw nothing on Java themselves: CustomBlocks puts the bush or mint
  of its kind and age on an item display (Bedrock gets one look per age).
Real note blocks aren't touched.

Allocation is append-only: a world stores the states, so a block keeps its state for good. New blocks go at the end of
their list. Structure markers (a berry bush a structure places, made into the real plant when its chunk is generated)
take mushroom states from the other end, so they never move a block.

usage: [VANILLA_JAR=client.jar] build_custom_blocks.py COBBLEMON_RESOURCES OUT_DIR
  OUT_DIR/blocks-pack.zip          Java resource pack
  OUT_DIR/blocks.mcpack            Bedrock textures, geometry and item icons
  OUT_DIR/geyser-blocks.json       Geyser custom block mappings (those states -> Bedrock custom blocks)
  OUT_DIR/geyser-blocks-items.json Geyser custom item mappings (the blocks' and seeds' items)
  OUT_DIR/custom-blocks.json       the plugin's table
"""
import glob, gzip, hashlib, io, itertools, json, os, sys, tempfile, uuid, zipfile
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import bedrock_blocks

MUSHROOMS = ['brown_mushroom_block', 'red_mushroom_block', 'mushroom_stem']
FACES = ['down', 'east', 'north', 'south', 'up', 'west']  # alphabetical, as Minecraft writes them
TRIPWIRE = ['attached', 'disarmed', 'east', 'north', 'powered', 'south', 'west']

STONES = ['dawn', 'dusk', 'fire', 'ice', 'leaf', 'moon', 'shiny', 'sun', 'thunder', 'water']
ORES = [f'{s}_stone_ore' for s in STONES] + [f'deepslate_{s}_stone_ore' for s in STONES] + \
       ['dripstone_moon_stone_ore', 'nether_fire_stone_ore', 'terracotta_sun_stone_ore']
WOOD = [f'{w}[axis={a}]' for w in ('apricorn_log', 'apricorn_wood', 'stripped_apricorn_log', 'stripped_apricorn_wood')
        for a in 'xyz']
CUBES = ORES + [f'{s}_stone_block' for s in STONES] + WOOD + ['apricorn_planks', 'apricorn_leaves']  # append-only

COLOURS = ['black', 'blue', 'green', 'pink', 'red', 'white', 'yellow']
MINTS = ['red', 'blue', 'cyan', 'pink', 'green', 'white']
FACINGS = ['north', 'east', 'south', 'west']
PLANTS = ([f'apricorn[age={a},facing={f}]' for a in range(3) for f in FACINGS] +       # unripe: one look for every colour
          [f'{c}_apricorn[age=3,facing={f}]' for c in COLOURS for f in FACINGS] +
          [f'{c}_apricorn_sapling' for c in COLOURS] +
          [f'berry[age={a}]' for a in range(6)] +
          [f'mint[age={a}]' for a in range(8)])  # append-only

# vanilla parents Cobblemon's plant models use (for the Bedrock geometry; the Java client has its own)
VANILLA_PARENTS = {
    'cross': {'textures': {'particle': '#cross'}, 'elements': [
        {'from': [0.8, 0, 8], 'to': [15.2, 16, 8], 'rotation': {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45},
         'faces': {'north': {'uv': [0, 0, 16, 16], 'texture': '#cross'}, 'south': {'uv': [0, 0, 16, 16], 'texture': '#cross'}}},
        {'from': [8, 0, 0.8], 'to': [8, 16, 15.2], 'rotation': {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45},
         'faces': {'west': {'uv': [0, 0, 16, 16], 'texture': '#cross'}, 'east': {'uv': [0, 0, 16, 16], 'texture': '#cross'}}}]},
}
# vanilla's tripwire.json: (north, south, east, west) -> (model, y); "_attached" models when attached
TRIPWIRE_MODELS = {
    (0, 0, 0, 0): ('ns', 0), (1, 0, 0, 0): ('n', 0), (0, 1, 0, 0): ('n', 180), (0, 0, 1, 0): ('n', 90), (0, 0, 0, 1): ('n', 270),
    (1, 1, 0, 0): ('ns', 0), (0, 0, 1, 1): ('ns', 90), (1, 0, 1, 0): ('ne', 0), (0, 1, 1, 0): ('ne', 90), (0, 1, 0, 1): ('ne', 180),
    (1, 0, 0, 1): ('ne', 270), (1, 1, 1, 0): ('nse', 0), (0, 1, 1, 1): ('nse', 90), (1, 1, 0, 1): ('nse', 180),
    (1, 0, 1, 1): ('nse', 270), (1, 1, 1, 1): ('nsew', 0)}


def custom_items():
    """The items CustomBlocks makes (the blocks you can place, the seeds): build_items.py lets recipes use them."""
    return sorted({n.partition('[')[0] for n in CUBES} | {f'{c}_apricorn_seed' for c in COLOURS} | {f'{c}_mint_seeds' for c in MINTS})


def mushroom_states():
    """Every mushroom block state with its bottom face shown, but not the all-six item state."""
    out = []
    for b in MUSHROOMS:
        for vs in itertools.product(['false', 'true'], repeat=5):
            if all(v == 'true' for v in vs): continue
            p = dict(zip(['east', 'north', 'south', 'up', 'west'], vs), down='true')
            out.append(f"minecraft:{b}[{','.join(f'{k}={p[k]}' for k in FACES)}]")
    return out


def tripwire_states():
    """Every tripwire state with disarmed=true."""
    return [f"minecraft:tripwire[{','.join(f'{p}={v}' for p, v in zip(TRIPWIRE, (a, 'true', e, n, pw, s, w)))}]"
            for a, e, n, pw, s, w in itertools.product(['false', 'true'], repeat=6)]


def allocate(names, states, markers=()):
    assert len(names) + len(markers) <= len(states), f'{len(names)} + {len(markers)} blocks, {len(states)} free states'
    out = dict(zip(names, states))
    out.update(zip(markers, reversed(states)))
    return out


def props(state):
    """'minecraft:x[a=b,c=d]' -> ('x', 'a=b,c=d')."""
    b, _, p = state.partition('[')
    return b.split(':')[1], p.rstrip(']')


def vanilla_tripwire(d):
    """Vanilla's model for a tripwire state."""
    m, y = TRIPWIRE_MODELS[tuple(int(d[f] == 'true') for f in ('north', 'south', 'east', 'west'))]
    return {'model': f"minecraft:block/tripwire{'_attached' if d['attached'] == 'true' else ''}_{m}", **({'y': y} if y else {})}


def rotate(m, deg):
    """A block model turned like a blockstate's y (clockwise seen from above), so Bedrock gets one geometry a facing.
    ponytail: only y-axis element rotations survive (what apricorns use); up/down UVs aren't turned."""
    m = json.loads(json.dumps(m))
    for _ in range(deg // 90 % 4):
        for e in m['elements']:
            (x1, _, z1), (x2, _, z2) = e['from'], e['to']
            e['from'][0], e['from'][2], e['to'][0], e['to'][2] = 16 - z2, x1, 16 - z1, x2
            if e.get('rotation'):
                o = e['rotation']['origin']
                o[0], o[2] = 16 - o[2], o[0]
            ren = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
            e['faces'] = {ren.get(k, k): v for k, v in e.get('faces', {}).items()}
    return m


def anchor(a):
    """A vertical anchor as a y (overworld: bottom -64, top 320; the nether's 0..256 is close enough)."""
    if 'absolute' in a: return a['absolute']
    if 'above_bottom' in a: return -64 + a['above_bottom']
    return 320 - a['below_top']


def veins(res):
    """Cobblemon's ore features: count a chunk, height range, vein size, the ores it places and its biome tag."""
    out = []
    pf = f'{res}/data/cobblemon/worldgen/placed_feature/ore'
    for f in sorted(os.listdir(pf)) if os.path.isdir(pf) else []:
        p = json.load(open(f'{pf}/{f}'))
        cf = json.load(open(f"{res}/data/cobblemon/worldgen/configured_feature/{p['feature'].split(':')[1]}.json"))
        v = {'feature': f[:-5], 'size': cf['config']['size'], 'ores': {}}
        for t in cf['config']['targets']:
            v['ores'][t['target'].get('tag', t['target'].get('block', '?'))] = t['state']['Name'].split(':')[1]
        for pl in p['placement']:
            if pl['type'] == 'minecraft:count': v['count'] = pl['count']
            if pl['type'] == 'minecraft:height_range':
                h = pl['height']
                v['shape'] = h['type'].split(':')[1]
                v['min'], v['max'] = anchor(h['min_inclusive']), anchor(h['max_inclusive'])
        stone, kind = f[:-5].rsplit('_', 1)
        if kind in ('upper', 'lower'): tag = f'ore_{stone}_normal'
        elif kind == 'rare': tag = f"ore_{f[:-5].rsplit('_', 2)[0]}_rare"
        else: tag = f'ore_{stone}_{kind}'
        v['biomes'] = f'cobblemon:has_ore/{tag}'
        out.append(v)
    return out


def structure_berries(res):
    """The berry bushes Cobblemon's structures place (they get marker states)."""
    import build_structures as bs
    found = set()
    for p in glob.glob(f'{res}/data/cobblemon/structure/**/*.nbt', recursive=True):
        if '/village' in p: continue
        f = io.BytesIO(gzip.decompress(open(p, 'rb').read()))
        f.read(1); bs.read(f, bs.STRING)
        root = bs.read(f, bs.COMPOUND)
        pals = [root['palette'][1][2]] if 'palette' in root else [x[2] for x in root.get('palettes', (0, (0, 0, [])))[1][2]]
        for pal in pals:
            for st in pal:
                n = st['Name'][1]
                if n.startswith('cobblemon:') and n.endswith('_berry'): found.add(n[10:])
    return sorted(found)


def table(res):
    """name -> state for every block (markers included), and the markers."""
    markers = [f'marker/{b}' for b in structure_berries(res)]
    return {**allocate(CUBES, mushroom_states(), markers), **allocate(PLANTS, tripwire_states())}, markers


def cube_faces(src, name):
    """A cube model's texture per face: {'*': side, 'up': top, 'down': bottom}."""
    t = bedrock_blocks.model(src, name)['textures']
    side = t.get('all') or t.get('side')
    out = {'*': side}
    if t.get('end'): out['up'] = out['down'] = t['end']
    if t.get('top'): out['up'] = t['top']
    if t.get('bottom'): out['down'] = t['bottom']
    return out


def variant(res, block, key):
    """A Cobblemon blockstate's variant: (model, x, y)."""
    v = json.load(open(f'{res}/assets/cobblemon/blockstates/{block}.json'))['variants'][key]
    v = v[0] if isinstance(v, list) else v
    return v['model'], v.get('x', 0), v.get('y', 0)


def berry_models(res, berry, age):
    """The models a berry bush shows at an age (not rooted, no mulch)."""
    out = []
    for p in json.load(open(f'{res}/assets/cobblemon/blockstates/{berry}.json'))['multipart']:
        w = p.get('when', {})
        c = {k: v for d in w.get('AND', [w]) for k, v in d.items()}
        if str(c.get('age')) == str(age) and str(c.get('rooted', False)).lower() == 'false' and 'mulch' not in c:
            a = p['apply']
            out.append((a[0] if isinstance(a, list) else a)['model'])
    return out


def geo_model(geo, tex_id):
    """A Bedrock geometry's cubes as a Java block model around (8, 8, 8), box UV unfolded (Berries.java on Fabric)."""
    g = geo['minecraft:geometry'][0]
    tw, th = g['description']['texture_width'], g['description']['texture_height']
    face = lambda u, v, w, h, mir: {'uv': ([(u + w) * 16 / tw, v * 16 / th, u * 16 / tw, (v + h) * 16 / th] if mir else
                                           [u * 16 / tw, v * 16 / th, (u + w) * 16 / tw, (v + h) * 16 / th]), 'texture': '#0'}
    els = []
    for bone in g.get('bones', []):
        for c in bone.get('cubes', []):
            (ox, oy, oz), (w, h, d), (u, v) = c['origin'], c['size'], c['uv']
            i, mir = c.get('inflate', 0), c.get('mirror', False)
            els.append({'from': [8 - (ox + w) - i, 8 + oy - i, 8 + oz - i], 'to': [8 - ox + i, 8 + oy + h + i, 8 + oz + d + i],
                        'faces': {'up': face(u + d, v, w, d, mir), 'down': face(u + d + w, v, w, d, mir),
                                  'north': face(u + d, v + d, w, h, mir), 'south': face(u + 2 * d + w, v + d, w, h, mir),
                                  ('west' if mir else 'east'): face(u, v + d, d, h, mir),
                                  ('east' if mir else 'west'): face(u + d + w, v + d, d, h, mir)}})
    return {'textures': {'0': tex_id}, 'elements': els}


def opaque(png):
    """A cutout texture made solid (mushroom blocks draw no transparency): its clear pixels a darker shade of it."""
    im = Image.open(io.BytesIO(png)).convert('RGBA')
    px = [p for p in im.get_flattened_data() if p[3] > 0]
    avg = tuple(sum(p[i] for p in px) // max(1, len(px)) * 6 // 10 for i in range(3))
    out = Image.new('RGBA', im.size, avg + (255,))
    out.alpha_composite(im)
    buf = io.BytesIO()
    out.save(buf, 'PNG')
    return buf.getvalue()


def build(res, out):
    src = bedrock_blocks.Src(res)
    names = json.load(open(f'{res}/assets/cobblemon/lang/en_us.json'))
    blocks, markers = table(res)
    os.makedirs(out, exist_ok=True)
    java = {}

    def add_model(name):
        """Cobblemon's model, its cobblemon parents and textures, into the Java pack."""
        path = bedrock_blocks.ref(name, 'models') + '.json'
        if path in java or not src.has(path): return
        java[path] = src.read(path)
        m = json.loads(java[path])
        if m.get('parent', '').startswith('cobblemon:'): add_model(m['parent'])
        for t in m.get('textures', {}).values():
            if t.startswith('#'): continue
            p = bedrock_blocks.ref(t, 'textures')
            for ext in ('.png', '.png.mcmeta'):
                if src.has(p + ext): java[p + ext] = src.read(p + ext)

    def item_def(ns, item, model):
        java[f'assets/{ns}/items/{item}.json'] = json.dumps({'model': {'type': 'minecraft:model', 'model': model}}).encode()

    # ---- Java: what each state draws ----
    cube_java = {}  # name -> (model, x, y)
    for n in CUBES:
        base, _, p = n.partition('[')
        cube_java[n] = variant(res, base, p.rstrip(']')) if p else (f'cobblemon:block/{base}', 0, 0)
        add_model(cube_java[n][0])
    leaves = bedrock_blocks.model(src, 'cobblemon:block/apricorn_leaves')['textures']['all']
    java['assets/cobblemonlink/textures/block/apricorn_leaves_opaque.png'] = opaque(src.read(bedrock_blocks.ref(leaves, 'textures') + '.png'))
    java['assets/cobblemonlink/models/block/apricorn_leaves_opaque.json'] = json.dumps(
        {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'cobblemonlink:block/apricorn_leaves_opaque'}}).encode()
    cube_java['apricorn_leaves'] = ('cobblemonlink:block/apricorn_leaves_opaque', 0, 0)
    java['assets/cobblemonlink/models/block/empty.json'] = json.dumps({'textures': {'particle': 'minecraft:block/short_grass'}, 'elements': []}).encode()
    for m in markers: cube_java[m] = ('cobblemonlink:block/empty', 0, 0)

    plant_java = {}
    for n in PLANTS:
        base, _, p = n.partition('[')
        if base == 'apricorn': plant_java[n] = variant(res, 'red_apricorn', p.rstrip(']'))
        elif base.endswith('_apricorn'): plant_java[n] = variant(res, base, p.rstrip(']'))
        elif base.endswith('_sapling'): plant_java[n] = variant(res, base, '')
        else: plant_java[n] = ('cobblemonlink:block/empty', 0, 0)  # an item display draws it (berry, mint)
        add_model(plant_java[n][0])

    not_reserved = {'OR': [{'down': 'false'}, {f: 'true' for f in FACES}]}
    rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    for b in MUSHROOMS:  # vanilla's parts for every state but ours, then ours
        parts = [{'apply': {'model': f'minecraft:block/{b}', **({'uvlock': True} if rot[f] else {}), **rot[f]},
                  'when': {'AND': [{f: 'true'}, not_reserved]}} for f in rot]
        parts += [{'apply': {'model': 'minecraft:block/mushroom_block_inside', **rot[f]},
                   'when': {'AND': [{f: 'false'}, not_reserved]}} for f in rot]
        for n, s in blocks.items():
            name, p = props(s)
            if name != b: continue
            m, x, y = cube_java[n]
            parts.append({'apply': {'model': m, **({'x': x} if x else {}), **({'y': y} if y else {})},
                          'when': dict(kv.split('=') for kv in p.split(','))})
        java[f'assets/minecraft/blockstates/{b}.json'] = json.dumps({'multipart': parts}).encode()
    tw = {}
    by_state = {s: n for n, s in blocks.items() if s.startswith('minecraft:tripwire')}
    for vs in itertools.product(['false', 'true'], repeat=len(TRIPWIRE)):
        k = ','.join(f'{p}={v}' for p, v in zip(TRIPWIRE, vs))
        n = by_state.get(f'minecraft:tripwire[{k}]')
        if n:
            m, x, y = plant_java[n]
            tw[k] = {'model': m, **({'y': y} if y else {})}
        else:
            tw[k] = vanilla_tripwire(dict(zip(TRIPWIRE, vs)))
    java['assets/minecraft/blockstates/tripwire.json'] = json.dumps({'variants': tw}).encode()

    # ---- items: the blocks, the seeds, and what item displays draw ----
    items = {}
    for n in CUBES:
        base = n.partition('[')[0]
        if base in items or ('[' in n and not n.endswith('axis=y]')): continue
        item_def('cobblemon', base, cube_java[n][0] if base != 'apricorn_leaves' else 'cobblemon:block/apricorn_leaves')
        add_model('cobblemon:block/apricorn_leaves')
        items[base] = {'name': names.get(f'block.cobblemon.{base}', base), 'kind': 'block'}
    for seed in [f'{c}_apricorn_seed' for c in COLOURS] + [f'{c}_mint_seeds' for c in MINTS]:
        add_model(f'cobblemon:item/{seed}')
        item_def('cobblemon', seed, f'cobblemon:item/{seed}')
        items[seed] = {'name': names.get(f'item.cobblemon.{seed}', seed), 'kind': 'seed'}
    berries, mints = {}, {}
    for c in MINTS:
        mints[c] = []
        for a in range(8):
            m = variant(res, f'{c}_mint', f'age={a}')[0]
            add_model(m)
            item_def('cobblemonlink', f'clb_mint_{c}_{a}', m)
            mints[c].append(f'cobblemonlink:clb_mint_{c}_{a}')
    geos = {}
    for f in sorted(glob.glob(f'{res}/data/cobblemon/berries/*.json')):
        b = os.path.basename(f)[:-5]
        if not os.path.exists(f'{res}/assets/cobblemon/blockstates/{b}.json'): continue
        d = json.load(open(f))
        ages = []
        for a in range(6):
            ms = berry_models(res, b, a)
            for m in ms: add_model(m)
            model = ({'type': 'minecraft:model', 'model': ms[0]} if len(ms) == 1 else
                     {'type': 'minecraft:composite', 'models': [{'type': 'minecraft:model', 'model': m} for m in ms]})
            java[f'assets/cobblemonlink/items/clb_berry_{b}_{a}.json'] = json.dumps({'model': model}).encode()
            ages.append(f'cobblemonlink:clb_berry_{b}_{a}')
        parts = {}
        for kind in ('fruit', 'flower'):
            geo, tex = d.get(f'{kind}Model'), d.get(f'{kind}Texture')
            if not geo or not tex: continue
            geo, tex = geo.split(':')[1].replace('.geo', ''), tex.split(':')[1]
            gp, tp = f'{res}/assets/cobblemon/bedrock/berries/{geo}.geo.json', f'{res}/assets/cobblemon/textures/berries/{tex}.png'
            if not (os.path.exists(gp) and os.path.exists(tp)): continue
            key = f'{geo}__{tex}'
            if key not in geos:
                java[f'assets/cobblemonlink/textures/item/berry/{tex}.png'] = open(tp, 'rb').read()
                java[f'assets/cobblemonlink/models/berry/{key}.json'] = json.dumps(geo_model(json.load(open(gp)), f'cobblemonlink:item/berry/{tex}')).encode()
                item_def('cobblemonlink', f'clb_{key}', f'cobblemonlink:berry/{key}')
                geos[key] = f'cobblemonlink:clb_{key}'
            parts[kind] = geos[key]
        pts = [[p['position']['x'], p['position']['y'], p['position']['z'], p['rotation']['x'], p['rotation']['y'], p['rotation']['z']]
               for p in d.get('growthPoints', [])]
        cond = d.get('spawnConditions', [])
        berries[b] = {'ages': ages, **parts, 'points': pts, 'yield': [d['baseYield']['min'], d['baseYield']['max']],
                      'growth': [d['growthTime']['min'], d['growthTime']['max']], 'refresh': [d['refreshRate']['min'], d['refreshRate']['max']],
                      'boneMeal': d.get('boneMealChance', 0.5), 'weight': d.get('weight', 1),
                      'biomes': d.get('preferredBiomeTags', []) if any(c['variant'] == 'cobblemon:preferred_biome' for c in cond) else [],
                      'anywhere': any(c['variant'] == 'cobblemon:all_biome' for c in cond),
                      'grove': [min((c.get('minGroveSize', 1) for c in cond), default=1), max((c.get('maxGroveSize', 1) for c in cond), default=1)]}
    java['pack.mcmeta'] = json.dumps({'pack': {'description': "Cobblemon's blocks (Cobblemon art, CC BY-NC 3.0)",
                                               'pack_format': 97, 'supported_formats': [46, 999], 'min_format': 46, 'max_format': 999}}).encode()
    with zipfile.ZipFile(f'{out}/blocks-pack.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for p in sorted(java): z.writestr(zipfile.ZipInfo(p, (1980, 1, 1, 0, 0, 0)), java[p])

    # ---- Bedrock: the same states as custom blocks, and the items' icons ----
    bed, terrain, icons = {}, {}, {}

    def tex_key(t, data=None):
        k = 'cl_' + t.split('/')[-1]
        if k not in terrain:
            p = bedrock_blocks.ref(t, 'textures') + '.png'
            data = data or (src.read(p) if src.has(p) else None)
            if not data: return None
            im = Image.open(io.BytesIO(data))
            if im.height > im.width: im = im.crop((0, 0, im.width, im.width))  # animated: the first frame
            buf = io.BytesIO(); im.save(buf, 'PNG')
            terrain[k] = {'textures': f'textures/blocks/cobblemonlink/{k}'}
            bed[f'textures/blocks/cobblemonlink/{k}.png'] = buf.getvalue()
        return k

    def hardness(n):
        if n.startswith(('deepslate_', 'nether_')): return 4.5
        if n.endswith(('_ore', '_stone_block')): return 3.0
        return 0.2 if n.endswith('leaves') else 2.0

    overrides = {b: {} for b in MUSHROOMS + ['tripwire']}
    for n in CUBES:
        base, _, p = n.partition('[')
        faces = cube_faces(src, cube_java[n][0] if n != 'apricorn_leaves' else 'cobblemon:block/apricorn_leaves')
        if p == 'axis=x]': faces = {'*': faces['*'], 'east': faces.get('up'), 'west': faces.get('down')}
        if p == 'axis=z]': faces = {'*': faces['*'], 'north': faces.get('up'), 'south': faces.get('down')}
        rm = 'alpha_test' if base == 'apricorn_leaves' else 'opaque'
        mats = {f: {'texture': tex_key(t), 'render_method': rm} for f, t in faces.items() if t and tex_key(t)}
        name, sp = props(blocks[n])
        overrides[name][sp] = {'display_name': names.get(f'block.cobblemon.{base}', base), 'material_instances': mats,
                               'destructible_by_mining': hardness(base)}
    with tempfile.TemporaryDirectory() as tmp:
        os.makedirs(f'{tmp}/assets/minecraft/models/block', exist_ok=True)
        for k, v in VANILLA_PARENTS.items():
            json.dump(v, open(f'{tmp}/assets/minecraft/models/block/{k}.json', 'w'))
        vanilla = [bedrock_blocks.Src(os.environ['VANILLA_JAR'])] if os.environ.get('VANILLA_JAR') else []
        both = bedrock_blocks.Srcs(bedrock_blocks.Src(tmp), src, *vanilla)

        def geometry(models, deg, gid):
            """Java models (merged, turned) as Bedrock geometry 'geometry.<gid>'; returns its texture key."""
            els, tex = [], {}
            for i, mname in enumerate(models):
                m = bedrock_blocks.model(both, mname)
                for e in m['elements']:
                    e = json.loads(json.dumps(e))
                    for fc in e.get('faces', {}).values():
                        if fc['texture'].startswith('#'): fc['texture'] = f'#m{i}_' + fc['texture'][1:]
                    els.append(e)
                tex.update({f'm{i}_{k}': (f'#m{i}_' + v[1:] if v.startswith('#') else v) for k, v in m['textures'].items()})
            m = rotate({'elements': els, 'textures': tex}, deg)
            for t in tex.values():  # a vanilla texture (farmland) without VANILLA_JAR: a plain stand-in
                tp = bedrock_blocks.ref(t, 'textures') + '.png'
                if not t.startswith('#') and not both.has(tp):
                    os.makedirs(os.path.dirname(f'{tmp}/{tp}'), exist_ok=True)
                    Image.new('RGBA', (16, 16), (92, 64, 40, 255)).save(f'{tmp}/{tp}')
            p = f'{tmp}/assets/cobblemonlink/models/block/{gid}.json'
            os.makedirs(os.path.dirname(p), exist_ok=True)
            json.dump(m, open(p, 'w'))
            assert bedrock_blocks.convert(both, f'cobblemonlink:block/{gid}', f'{tmp}/geo'), gid
            d = f'{tmp}/geo/clb_{gid}'
            g = json.load(open(f'{d}/clb_{gid}.geo.json'))
            g['format_version'] = '1.16.0'
            g['minecraft:geometry'][0]['description']['identifier'] = f'geometry.{gid}'
            bed[f'models/blocks/{gid}.geo.json'] = json.dumps(g).encode()
            return tex_key(f'cobblemonlink:x/{gid}', open(f'{d}/clb_{gid}.png', 'rb').read())

        for n in PLANTS:
            base, _, p = n.partition('[')
            if base == 'berry': models, deg = berry_models(res, 'oran_berry', p[4]), 0
            elif base == 'mint': models, deg = [variant(res, 'red_mint', p.rstrip(']'))[0]], 0
            else: models, deg = [plant_java[n][0]], plant_java[n][2]
            gid = 'cl_' + n.replace('[', '_').replace(']', '').replace('=', '').replace(',', '_')
            tk = geometry(models, deg, gid)
            overrides['tripwire'][props(blocks[n])[1]] = {
                'display_name': names.get(f'block.cobblemon.{base}', base.replace('_', ' ').title()), 'geometry': f'geometry.{gid}',
                'material_instances': {'*': {'texture': tk, 'render_method': 'alpha_test'}},
                'collision_box': {'origin': [0, 0, 0], 'size': [0, 0, 0]},  # Java's tripwire: walk through
                'selection_box': {'origin': [-8, 0, -8], 'size': [16, 16, 16]}, 'destructible_by_mining': 0.0}
    for i, d in items.items():  # icons: a seed's own texture, a block's side
        if d['kind'] == 'seed':
            t = bedrock_blocks.model(src, f'cobblemon:item/{i}')['textures'].get('layer0')
        else:
            t = cube_faces(src, 'cobblemon:block/apricorn_leaves' if i == 'apricorn_leaves' else cube_java[i if i in cube_java else f'{i}[axis=y]'][0])['*']
        p = bedrock_blocks.ref(t, 'textures') + '.png' if t else None
        if p and src.has(p):
            bed[f'textures/items/cobblemonlink/{i}.png'] = src.read(p)
            icons[f'cobblemonlink:{i}'] = {'textures': [f'textures/items/cobblemonlink/{i}']}
    bed['textures/terrain_texture.json'] = json.dumps({'resource_pack_name': 'cobblemonlink', 'texture_name': 'atlas.terrain',
                                                       'padding': 8, 'num_mip_levels': 4, 'texture_data': terrain}).encode()
    bed['textures/item_texture.json'] = json.dumps({'resource_pack_name': 'cobblemonlink', 'texture_name': 'atlas.items', 'texture_data': icons}).encode()
    digest = hashlib.sha256(b''.join(bed[k] for k in sorted(bed))).digest()
    ver = [1, 0, int.from_bytes(digest[:3]) % 32768]  # Bedrock refuses a version part over 65535
    bed['manifest.json'] = json.dumps({'format_version': 2, 'header': {
        'name': 'cobblemon-link blocks', 'description': "Cobblemon's blocks (CC BY-NC 3.0)",
        'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link/blocks')), 'version': ver, 'min_engine_version': [1, 21, 0]},
        'modules': [{'type': 'resources', 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link/blocks/res')), 'version': ver}]}).encode()
    with zipfile.ZipFile(f'{out}/blocks.mcpack', 'w', zipfile.ZIP_DEFLATED) as z:
        for p in sorted(bed): z.writestr(zipfile.ZipInfo(p, (1980, 1, 1, 0, 0, 0)), bed[p])
    json.dump({'format_version': 1, 'blocks': {f'minecraft:{b}': {'name': f'cl_{b}', 'only_override_states': True, 'state_overrides': o}
                                               for b, o in overrides.items() if o}}, open(f'{out}/geyser-blocks.json', 'w'), indent=1)
    json.dump({'format_version': 2, 'items': {'minecraft:paper': [
        {'type': 'definition', 'model': f'cobblemon:{i}', 'bedrock_identifier': f'cobblemonlink:blk_{i}', 'display_name': d['name'],
         'bedrock_options': {'icon': f'cobblemonlink:{i}'}} for i, d in items.items() if f'cobblemonlink:{i}' in icons]}},
        open(f'{out}/geyser-blocks-items.json', 'w'), indent=1)

    drops = {o: o.split('_ore')[0].split('_', 1)[1] if o.split('_')[0] in ('deepslate', 'dripstone', 'nether', 'terracotta') else o[:-4] for o in ORES}
    loot = {f"cobblemon:{os.path.relpath(f, f'{res}/data/cobblemon/loot_table')[:-5]}": json.load(open(f))  # gilded chests' loot
            for f in glob.glob(f'{res}/data/cobblemon/loot_table/**/*.json', recursive=True) if '/loot_table/blocks/' not in f}
    json.dump({'blocks': blocks, 'drops': drops, 'veins': veins(res), 'items': items, 'berries': berries, 'mints': mints, 'loot': loot},
              open(f'{out}/custom-blocks.json', 'w'), indent=1)
    return len(CUBES), len(PLANTS), len(markers)


def demo():
    ms, ts = mushroom_states(), tripwire_states()
    assert len(ms) == 93 and len(set(ms)) == 93 and all('down=true' in s for s in ms)
    assert not any('east=true,north=true,south=true,up=true,west=true' in s for s in ms)  # the item state stays vanilla's
    assert len(ts) == 64 and all('disarmed=true' in s for s in ts)
    a = allocate(CUBES, ms, ['marker/a', 'marker/b'])
    assert a['dawn_stone_ore'] == ms[0] and a['marker/a'] == ms[-1] and a['marker/b'] == ms[-2]  # blocks never move
    assert len(set(a.values())) == len(a)
    assert len(allocate(PLANTS, ts)) == len(PLANTS) == 61
    try:
        allocate(list(range(65)), ts); raise SystemExit('over-allocation not caught')
    except AssertionError: pass
    r = rotate({'elements': [{'from': [0, 0, 0], 'to': [4, 4, 2], 'faces': {'north': {}}}]}, 90)['elements'][0]
    assert (r['from'], r['to'], list(r['faces'])) == ([14, 0, 0], [16, 4, 4], ['east']), r  # north face turns east
    assert vanilla_tripwire({'attached': 'true', 'north': 'true', 'south': 'false', 'east': 'true', 'west': 'false'}) == {'model': 'minecraft:block/tripwire_attached_ne'}
    g = {'minecraft:geometry': [{'description': {'texture_width': 16, 'texture_height': 16}, 'bones': [{'cubes': [{'origin': [-1, 0, -1], 'size': [2, 2, 2], 'uv': [0, 0]}]}]}]}
    assert geo_model(g, 't')['elements'][0]['from'] == [7, 8, 7]
    print('ok')


if __name__ == '__main__':
    if len(sys.argv) == 2 and sys.argv[1] == 'demo': demo()
    elif len(sys.argv) == 3: print(build(*sys.argv[1:]), '(cubes, plants, structure markers) ->', sys.argv[2])
    else: sys.exit(__doc__)
