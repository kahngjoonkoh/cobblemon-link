#!/usr/bin/env python3
"""Cobblemon's own blocks as real blocks for players without the mod (docs/custom-blocks.md): each one takes a block
state vanilla never shows once the server holds it still, and both clients are told to draw that state as Cobblemon's
block.
- Full cubes (the evolution-stone ores) take note block states: every real note block is kept at
  instrument=harp,note=0 (CustomBlocks.java), which frees the other 574 (instrument, note) pairs.
- Plants (apricorns hanging on leaves) take tripwire states: every real string is kept with all seven properties
  false, which frees the other 127.
Paper's block-updates.disable-noteblock-updates / disable-tripwire-updates (server/setup.sh) stop the world changing
those states on its own; the plugin does the rest.

Allocation is append-only: a world stores the states, so a block, once given a state, keeps it. New blocks go at the
end of their list.

usage: build_custom_blocks.py COBBLEMON_RESOURCES OUT_DIR
  OUT_DIR/blocks-pack.zip       Java resource pack (blockstates, Cobblemon's models and textures, ore item models)
  OUT_DIR/blocks.mcpack         Bedrock textures and geometry
  OUT_DIR/geyser-blocks.json    Geyser custom_mappings: those states -> Bedrock custom blocks
  OUT_DIR/custom-blocks.json    the plugin's table: block -> state, ore drops and veins
"""
import hashlib, io, itertools, json, os, sys, uuid, zipfile

sys.path.insert(0, os.path.dirname(__file__))
import bedrock_blocks

# Minecraft 26.3's note block instruments, in its order (the plugin checks each state parses at startup)
INSTRUMENTS = ['harp', 'basedrum', 'snare', 'hat', 'bass', 'flute', 'bell', 'guitar', 'chime', 'xylophone',
               'iron_xylophone', 'cow_bell', 'didgeridoo', 'bit', 'banjo', 'pling', 'zombie', 'skeleton', 'creeper',
               'dragon', 'wither_skeleton', 'piglin', 'custom_head']
TRIPWIRE = ['attached', 'disarmed', 'east', 'north', 'powered', 'south', 'west']  # alphabetical, as Minecraft writes them

STONES = ['dawn', 'dusk', 'fire', 'ice', 'leaf', 'moon', 'shiny', 'sun', 'thunder', 'water']
# append-only (see above)
ORES = [f'{s}_stone_ore' for s in STONES] + [f'deepslate_{s}_stone_ore' for s in STONES] + \
       ['dripstone_moon_stone_ore', 'nether_fire_stone_ore', 'terracotta_sun_stone_ore']
COLOURS = ['black', 'blue', 'green', 'pink', 'red', 'white', 'yellow']
FACINGS = {'south': 0, 'west': 90, 'north': 180, 'east': 270}  # Cobblemon's blockstate y rotation per facing
APRICORNS = [(c, age, f) for c in COLOURS for age in range(4) for f in FACINGS]  # append-only


def note_states():
    """Every note block state but the one real note blocks are kept at, powered left to the plugin (always false)."""
    return [f'instrument={i},note={n}' for i in INSTRUMENTS for n in range(25) if (i, n) != ('harp', 0)]


def tripwire_states():
    """Every tripwire state but all-false (real string)."""
    return [','.join(f'{p}={str(v).lower()}' for p, v in zip(TRIPWIRE, vs))
            for vs in itertools.product([False, True], repeat=len(TRIPWIRE)) if any(vs)]


def allocate(blocks, states):
    assert len(blocks) <= len(states), f'{len(blocks)} blocks, {len(states)} free states'
    return dict(zip(blocks, states))


def apricorn_key(c, age, f): return f'{c}_apricorn[age={age},facing={f}]'


def apricorn_model(c, age): return f'cobblemon:block/{c}_apricorn' if age == 3 else f'cobblemon:block/apricorn_stage_{age}'


def rotate(m, deg):
    """A block model turned like a blockstate's y (clockwise seen from above), so Bedrock gets one geometry a facing.
    ponytail: only y-axis, 0° element rotations (what the apricorn models use); up/down UVs aren't turned."""
    m = json.loads(json.dumps(m))
    for _ in range(deg // 90):
        for e in m['elements']:
            (x1, _, z1), (x2, _, z2) = e['from'], e['to']
            e['from'][0], e['from'][2], e['to'][0], e['to'][2] = 16 - z2, x1, 16 - z1, x2
            ren = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
            e['faces'] = {ren.get(k, k): v for k, v in e.get('faces', {}).items()}
    return m


def cube_faces(src, name):
    """A cube model's texture per Bedrock face: {'*': side, 'up': top, 'down': bottom}."""
    t = bedrock_blocks.model(src, name)['textures']
    side = t.get('all') or t.get('side')
    out = {'*': side}
    if t.get('end'): out['up'] = out['down'] = t['end']
    if t.get('top'): out['up'] = t['top']
    if t.get('bottom'): out['down'] = t['bottom']
    return out


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
            if pl['type'] == 'minecraft:rarity_filter': v['chance'] = pl['chance']
            if pl['type'] == 'minecraft:height_range':
                h = pl['height']
                v['shape'] = h['type'].split(':')[1]
                v['min'], v['max'] = anchor(h['min_inclusive']), anchor(h['max_inclusive'])
        stone, kind = f[:-5].rsplit('_', 1)[0], f[:-5].rsplit('_', 1)[1]  # moon_stone_upper -> moon_stone, upper
        if kind in ('upper', 'lower'): tag = f'ore_{stone}_normal'
        elif kind == 'rare': tag = f"ore_{f[:-5].rsplit('_', 2)[0]}_rare"
        else: tag = f'ore_{stone}_{kind}'
        v['biomes'] = f'cobblemon:has_ore/{tag}'
        out.append(v)
    return out


def png(src, tex):
    p = bedrock_blocks.ref(tex, 'textures') + '.png'
    return src.read(p) if src.has(p) else None


def build(res, out):
    src = bedrock_blocks.Src(res)
    notes = allocate(ORES, note_states())
    wires = allocate([apricorn_key(*a) for a in APRICORNS], tripwire_states())
    files = {}  # Java pack path -> bytes

    def add_model(name):
        """Cobblemon's model, its parents (cobblemon ones) and textures, into the Java pack."""
        path = bedrock_blocks.ref(name, 'models') + '.json'
        if path in files or not src.has(path): return
        files[path] = src.read(path)
        m = json.loads(files[path])
        if m.get('parent', '').startswith('cobblemon:'): add_model(m['parent'])
        for t in m.get('textures', {}).values():
            if t.startswith('#'): continue
            p = bedrock_blocks.ref(t, 'textures')
            for ext in ('.png', '.png.mcmeta'):
                if src.has(p + ext): files[p + ext] = src.read(p + ext)

    # Java: the note block and tripwire blockstates, every state named (one we don't use keeps vanilla's model)
    nb = {}
    for i in INSTRUMENTS:
        for n in range(25):
            k = f'instrument={i},note={n}'
            ore = next((o for o, s in notes.items() if s == k), None)
            nb[k] = {'model': f'cobblemon:block/{ore}' if ore else 'minecraft:block/note_block'}
            if ore: add_model(f'cobblemon:block/{ore}')
    files['assets/minecraft/blockstates/note_block.json'] = json.dumps({'variants': nb}).encode()
    tw = {}
    by_state = {s: k for k, s in wires.items()}
    for vs in itertools.product([False, True], repeat=len(TRIPWIRE)):
        k = ','.join(f'{p}={str(v).lower()}' for p, v in zip(TRIPWIRE, vs))
        if k in by_state:
            c, age, f = next(a for a in APRICORNS if apricorn_key(*a) == by_state[k])
            tw[k] = {'model': apricorn_model(c, age), 'y': FACINGS[f]} if FACINGS[f] else {'model': apricorn_model(c, age)}
            add_model(apricorn_model(c, age))
        else:
            tw[k] = {'model': 'minecraft:block/tripwire_ns'}  # real string: no hooks, so never attached
    files['assets/minecraft/blockstates/tripwire.json'] = json.dumps({'variants': tw}).encode()
    for o in ORES:  # an ore you hold shows as the block (placed with /clblock)
        files[f'assets/cobblemon/items/{o}.json'] = json.dumps({'model': {'type': 'minecraft:model', 'model': f'cobblemon:block/{o}'}}).encode()
    files['pack.mcmeta'] = json.dumps({'pack': {'description': "Cobblemon's blocks (Cobblemon art, CC BY-NC 3.0)",
                                                'pack_format': 97, 'supported_formats': [46, 999], 'min_format': 46, 'max_format': 999}}).encode()
    os.makedirs(out, exist_ok=True)
    with zipfile.ZipFile(f'{out}/blocks-pack.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for p in sorted(files): z.writestr(zipfile.ZipInfo(p, (1980, 1, 1, 0, 0, 0)), files[p])

    # Bedrock: textures and geometry, and Geyser's mappings from those states to custom blocks
    terrain, bed, overrides_nb, overrides_tw = {}, {}, {}, {}
    lp = f'{res}/assets/cobblemon/lang/en_us.json'
    names = json.load(open(lp)) if os.path.exists(lp) else {}

    def tex_key(t):
        k = 'cl_' + t.split('/')[-1]
        if k not in terrain and (data := png(src, t)):
            terrain[k] = {'textures': f'textures/blocks/cobblemonlink/{k}'}
            bed[f'textures/blocks/cobblemonlink/{k}.png'] = data
        return k

    for o, s in notes.items():
        mats = {face: {'texture': tex_key(t), 'render_method': 'opaque'} for face, t in cube_faces(src, f'cobblemon:block/{o}').items() if t}
        deep = o.startswith(('deepslate_', 'nether_'))
        o_ = {'display_name': names.get(f'block.cobblemon.{o}', o), 'material_instances': mats,
              'destructible_by_mining': 4.5 if deep else 3.0}  # Minecraft's ore hardness
        for powered in ('false', 'true'):
            overrides_nb[f'{s},powered={powered}'] = o_
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        geos = {}
        for c, age, f in APRICORNS:
            name = apricorn_model(c, age)
            gid = f"cl_{name.split('/')[-1]}_{f}"
            if gid not in geos:  # Bedrock has one geometry a facing: the Java model turned, then converted
                m = rotate(bedrock_blocks.model(src, name), FACINGS[f])
                p = f'{tmp}/assets/cobblemon/models/block/{gid}.json'
                os.makedirs(os.path.dirname(p), exist_ok=True)
                json.dump(m, open(p, 'w'))
                both = bedrock_blocks.Srcs(bedrock_blocks.Src(tmp), src)
                assert bedrock_blocks.convert(both, f'cobblemon:block/{gid}', f'{tmp}/geo'), gid
                d = f'{tmp}/geo/clb_{gid}'
                g = json.load(open(f'{d}/clb_{gid}.geo.json'))
                g['format_version'] = '1.16.0'
                g['minecraft:geometry'][0]['description']['identifier'] = f'geometry.{gid}'
                bed[f'models/blocks/{gid}.geo.json'] = json.dumps(g).encode()
                tk = 'cl_' + name.split('/')[-1]  # the atlas is the same for every facing
                terrain[tk] = {'textures': f'textures/blocks/cobblemonlink/{tk}'}
                bed[f'textures/blocks/cobblemonlink/{tk}.png'] = open(f'{d}/clb_{gid}.png', 'rb').read()
                geos[gid] = tk
            overrides_tw[wires[apricorn_key(c, age, f)]] = {
                'display_name': names.get(f'block.cobblemon.{c}_apricorn', f'{c} apricorn'),
                'geometry': f'geometry.{gid}',
                'material_instances': {'*': {'texture': geos[gid], 'render_method': 'alpha_test'}},
                'collision_box': {'origin': [0, 0, 0], 'size': [0, 0, 0]},  # Java's tripwire: walk through
                'selection_box': {'origin': [-8, 0, -8], 'size': [16, 16, 16]},  # the whole cell: easy to tap
                'destructible_by_mining': 0.0}
    bed['textures/terrain_texture.json'] = json.dumps({'resource_pack_name': 'cobblemonlink', 'texture_name': 'atlas.terrain',
                                                       'padding': 8, 'num_mip_levels': 4, 'texture_data': terrain}).encode()
    digest = hashlib.sha256(b''.join(bed[k] for k in sorted(bed))).digest()
    ver = [1, 0, int.from_bytes(digest[:3]) % 32768]  # Bedrock refuses a version part over 65535
    bed['manifest.json'] = json.dumps({'format_version': 2, 'header': {
        'name': 'cobblemon-link blocks', 'description': "Cobblemon's blocks (CC BY-NC 3.0)",
        'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link/blocks')), 'version': ver, 'min_engine_version': [1, 21, 0]},
        'modules': [{'type': 'resources', 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link/blocks/res')), 'version': ver}]}).encode()
    with zipfile.ZipFile(f'{out}/blocks.mcpack', 'w', zipfile.ZIP_DEFLATED) as z:
        for p in sorted(bed): z.writestr(zipfile.ZipInfo(p, (1980, 1, 1, 0, 0, 0)), bed[p])
    json.dump({'format_version': 1, 'blocks': {
        'minecraft:note_block': {'name': 'cl_note_block', 'only_override_states': True, 'state_overrides': overrides_nb},
        'minecraft:tripwire': {'name': 'cl_tripwire', 'only_override_states': True, 'state_overrides': overrides_tw}}},
        open(f'{out}/geyser-blocks.json', 'w'), indent=1)

    # the plugin's table
    drops = {o: o.split('_ore')[0].split('_', 1)[1] if o.split('_')[0] in ('deepslate', 'dripstone', 'nether', 'terracotta') else o[:-4] for o in ORES}
    json.dump({'blocks': {**{o: f'minecraft:note_block[{s},powered=false]' for o, s in notes.items()},
                          **{k: f'minecraft:tripwire[{s}]' for k, s in wires.items()}},
               'drops': drops, 'veins': veins(res)}, open(f'{out}/custom-blocks.json', 'w'), indent=1)
    return len(notes), len(wires)


def demo():
    ns, ts = note_states(), tripwire_states()
    assert len(ns) == 23 * 25 - 1 and 'instrument=harp,note=0' not in ns and len(set(ns)) == len(ns)
    assert len(ts) == 127 and all('true' in s for s in ts) and len(set(ts)) == len(ts)
    a = allocate(ORES, ns)
    assert a['dawn_stone_ore'] == 'instrument=harp,note=1'  # append-only: the first ore's state never moves
    assert len(set(a.values())) == len(ORES)
    assert len(allocate([apricorn_key(*x) for x in APRICORNS], ts)) == 112
    m = {'elements': [{'from': [0, 0, 0], 'to': [4, 4, 2], 'faces': {'north': {}}}]}
    r = rotate(m, 90)['elements'][0]
    assert (r['from'], r['to'], list(r['faces'])) == ([14, 0, 0], [16, 4, 4], ['east']), r  # north face turns east
    try:
        allocate(list(range(128)), ts); raise SystemExit('over-allocation not caught')
    except AssertionError: pass
    print('ok')


if __name__ == '__main__':
    if len(sys.argv) == 2 and sys.argv[1] == 'demo': demo()
    elif len(sys.argv) == 3: print(build(*sys.argv[1:]), '(ores, apricorn states) ->', sys.argv[2])
    else: sys.exit(__doc__)
