#!/usr/bin/env python3
"""Cobblemon's blocks on Bedrock for the Fabric product. PolyMc shows each Cobblemon block state to players without
the mod as some vanilla state; where it borrows a state that looks like something else (note block tunes, infested
stone, waxed copper, spare leaves, tripwire, kelp ages...), its Java pack redraws that state as Cobblemon's block, but
Geyser shows Bedrock the vanilla block (a column of note blocks where apricorn logs should be). This makes those
states Geyser custom blocks drawn with Cobblemon's own models, as build_custom_blocks.py does for the Paper plugin.
Stand-ins that already look right (an apricorn door as an acacia door, a sign as a sign) and fallbacks (stone,
structure void, barriers under GeyserModelEngine furniture) are left alone.

usage: [VANILLA_JAR=client-1.21.1.jar] build_poly_blocks.py POLY_BLOCKS_JSON COBBLEMON_RES OUT_DIR
  POLY_BLOCKS_JSON  config/cobblemonlink/poly-blocks.json the mod writes at start (Cobblemon state -> client state)
  OUT_DIR/cobblemon-link-blocks.mcpack   Bedrock textures and geometry
  OUT_DIR/cobblemon-link-blocks.json     Geyser custom block mappings
"""
import hashlib, io, json, os, sys, tempfile, uuid, zipfile
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import bedrock_blocks
from build_custom_blocks import rotate

FALLBACKS = {'structure_void', 'stone', 'barrier', 'campfire', 'soul_campfire', 'chest'}
LOOKALIKE = ('infested_', 'waxed_', 'petrified_')  # vanilla blocks that copy another's look: always borrowed
WALK_THROUGH = ('tripwire', 'sugar_cane', 'kelp', 'cave_vines', 'small_dripleaf', 'sapling', 'pressure_plate')
# vanilla states worldgen places (oceans' kelp, growing cane, lush caves, snowy podzol, trees' leaves): PolyMc borrows
# some of them, but on Bedrock a vanilla world must look vanilla, so they keep their own look (Java sees PolyMc's)
NATURAL = ('kelp', 'sugar_cane', 'cave_vines', 'small_dripleaf', 'mycelium', 'podzol')


def natural(client):
    c, p = split(client)
    return c in NATURAL or c.endswith('_leaves') and 'persistent=false' in p


def split(state):
    """'ns:block[a=b]' -> ('block', 'a=b')."""
    b, _, p = state.partition('[')
    return b.split(':')[1], p.rstrip(']')


def borrowed(cob, client):
    """True when PolyMc's Java pack redraws this client state (so Bedrock needs it too)."""
    c, _ = split(client)
    if c in FALLBACKS: return False
    if c.startswith(LOOKALIKE) or c.endswith('_leaves'): return True  # PolyMc's spare leaves states
    suffix = split(cob)[0].split('_', 1)[-1]  # apricorn_fence_gate -> fence_gate: an acacia_fence_gate is a stand-in
    return not c.endswith('_' + suffix)


def applies(res, block, props):
    """The models (model, x, y) Cobblemon's blockstate file draws for a state."""
    p = f'{res}/assets/cobblemon/blockstates/{block}.json'
    if not os.path.exists(p): return []
    d = json.load(open(p))
    have = dict(kv.split('=') for kv in props.split(',')) if props else {}
    one = lambda a: (a[0] if isinstance(a, list) else a)
    if 'variants' in d:
        for key, v in d['variants'].items():
            want = dict(kv.split('=') for kv in key.split(',')) if key else {}
            if all(have.get(k) == str(x) for k, x in want.items()):
                v = one(v)
                return [(v['model'], v.get('x', 0), v.get('y', 0))]
        return []
    out = []
    def ok(w):
        if 'OR' in w: return any(ok(x) for x in w['OR'])
        if 'AND' in w: return all(ok(x) for x in w['AND'])
        return all(have.get(k) in str(v).lower().split('|') for k, v in w.items())
    for part in d.get('multipart', []):
        if ok(part.get('when', {})):
            a = one(part['apply'])
            out.append((a['model'], a.get('x', 0), a.get('y', 0)))
    return out


def full_cube(m):
    els = m['elements']
    return len(els) == 1 and els[0]['from'] == [0, 0, 0] and els[0]['to'] == [16, 16, 16] and not els[0].get('rotation')


def model_box(lo, hi):
    """A Geyser box (origin from the block's bottom centre) inside the block, at least 1 px each way (Geyser refuses
    boxes reaching out of the block)."""
    lo, hi = [max(0, min(16, v)) for v in lo], [max(0, min(16, v)) for v in hi]
    for a in range(3):
        if hi[a] - lo[a] < 1: lo[a] = max(0, hi[a] - 1); hi[a] = lo[a] + 1
    r = lambda v: round(v, 3)
    return {'origin': [r(lo[0] - 8), r(lo[1]), r(lo[2] - 8)], 'size': [r(hi[0] - lo[0]), r(hi[1] - lo[1]), r(hi[2] - lo[2])]}


def build(poly, res, out):
    table = json.load(open(poly))
    src = bedrock_blocks.Src(res)
    vanilla = [bedrock_blocks.Src(os.environ['VANILLA_JAR'])] if os.environ.get('VANILLA_JAR') else []
    both = bedrock_blocks.Srcs(src, *vanilla)
    names = json.load(open(f'{res}/assets/cobblemon/lang/en_us.json'))
    bed, terrain, overrides, done = {}, {}, {}, set()

    def tex_key(t, data=None):
        k = 'clp_' + hashlib.sha1(t.encode()).hexdigest()[:10] + '_' + t.split('/')[-1][:24]
        if k not in terrain:
            p = bedrock_blocks.ref(t, 'textures') + '.png'
            data = data or (both.read(p) if both.has(p) else None)
            if not data: return None
            im = Image.open(io.BytesIO(data))
            if im.height > im.width: im = im.crop((0, 0, im.width, im.width))  # animated: the first frame
            buf = io.BytesIO(); im.save(buf, 'PNG')
            terrain[k] = {'textures': f'textures/blocks/cobblemonlink/{k}'}
            bed[f'textures/blocks/cobblemonlink/{k}.png'] = buf.getvalue()
        return k

    with tempfile.TemporaryDirectory() as tmp:
        for cob, client in sorted(table.items()):
            if client in done or not borrowed(cob, client) or natural(client): continue
            block, props = split(cob)
            models = applies(res, block, props)
            if not models: continue
            cname, cprops = split(client)
            first = bedrock_blocks.model(both, models[0][0])
            o = {'display_name': names.get(f'block.cobblemon.{block}', block.replace('_', ' ').title()), 'destructible_by_mining': 1.0}
            if len(models) == 1 and full_cube(first) and not models[0][1] and not models[0][2]:
                faces = {}
                for side, f in first['elements'][0].get('faces', {}).items():
                    t = bedrock_blocks.resolve(first['textures'], f['texture'])
                    k = t and tex_key(t)
                    if k: faces[side] = {'texture': k, 'render_method': 'alpha_test' if cname.endswith('leaves') else 'opaque'}
                if not faces: continue
                faces['*'] = faces.get('north', next(iter(faces.values())))
                o['material_instances'] = faces
                if cname.endswith('leaves'): o['light_dampening'] = 1  # as leaves: a canopy isn't left in the dark
            else:  # merged, turned, then Bedrock geometry with its own atlas (bedrock_blocks.convert)
                els, tex = [], {}
                for i, (mname, x, y) in enumerate(models):
                    m = bedrock_blocks.model(both, mname)
                    part = {'elements': json.loads(json.dumps(m['elements'])), 'textures': {}}
                    for e in part['elements']:
                        for fc in e.get('faces', {}).values():
                            if fc['texture'].startswith('#'): fc['texture'] = f'#m{i}_' + fc['texture'][1:]
                    tex.update({f'm{i}_{k}': (f'#m{i}_' + v[1:] if v.startswith('#') else v) for k, v in m['textures'].items()})
                    els += rotate(part, y)['elements']  # ponytail: x rotations (vertical facings) aren't turned
                if not els: continue
                gid = 'clp_' + hashlib.sha1(client.encode()).hexdigest()[:12]
                p = f'{tmp}/assets/cobblemonlink/models/block/{gid}.json'
                os.makedirs(os.path.dirname(p), exist_ok=True)
                json.dump({'elements': els, 'textures': tex}, open(p, 'w'))
                srcs = bedrock_blocks.Srcs(bedrock_blocks.Src(tmp), both)
                if not bedrock_blocks.convert(srcs, f'cobblemonlink:block/{gid}', f'{tmp}/geo'): continue
                g = json.load(open(f'{tmp}/geo/clb_{gid}/clb_{gid}.geo.json'))
                g['format_version'] = '1.16.0'
                g['minecraft:geometry'][0]['description']['identifier'] = f'geometry.{gid}'
                bed[f'models/blocks/{gid}.geo.json'] = json.dumps(g, separators=(',', ':')).encode()
                k = tex_key(f'cobblemonlink:x/{gid}', open(f'{tmp}/geo/clb_{gid}/clb_{gid}.png', 'rb').read())
                lo = [min(e['from'][a] for e in els) for a in range(3)]
                hi = [max(e['to'][a] for e in els) for a in range(3)]
                box = model_box(lo, hi)
                o.update({'geometry': f'geometry.{gid}', 'material_instances': {'*': {'texture': k, 'render_method': 'alpha_test'}},
                          'selection_box': box, 'light_dampening': 0})  # a custom block blocks all light by default
                if any(w in cname for w in WALK_THROUGH): o['collision_box'] = {'origin': [0, 0, 0], 'size': [0, 0, 0]}
                else: o['collision_box'] = box
            overrides.setdefault(cname, {})[cprops] = o
            done.add(client)
    bed['textures/terrain_texture.json'] = json.dumps({'resource_pack_name': 'cobblemonlink', 'texture_name': 'atlas.terrain',
                                                       'padding': 8, 'num_mip_levels': 4, 'texture_data': terrain}).encode()
    digest = hashlib.sha256(b''.join(bed[k] for k in sorted(bed))).digest()
    ver = [1, 0, int.from_bytes(digest[:3]) % 32768]  # Bedrock refuses a version part over 65535
    bed['manifest.json'] = json.dumps({'format_version': 2, 'header': {
        'name': 'cobblemon-link blocks', 'description': "Cobblemon's blocks (CC BY-NC 3.0)",
        'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link/poly-blocks')), 'version': ver, 'min_engine_version': [1, 21, 0]},
        'modules': [{'type': 'resources', 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link/poly-blocks/res')), 'version': ver}]}).encode()
    os.makedirs(out, exist_ok=True)
    with zipfile.ZipFile(f'{out}/cobblemon-link-blocks.mcpack', 'w', zipfile.ZIP_DEFLATED) as z:
        for p in sorted(bed): z.writestr(zipfile.ZipInfo(p, (1980, 1, 1, 0, 0, 0)), bed[p])
    def mapping(b, o):  # a block without properties: its one state is the block's own components
        if list(o) == ['']: return {'name': f'clp_{b}', 'components': o['']}
        return {'name': f'clp_{b}', 'only_override_states': True, 'state_overrides': o}
    json.dump({'format_version': 1, 'blocks': {f'minecraft:{b}': mapping(b, o) for b, o in sorted(overrides.items())}},
              open(f'{out}/cobblemon-link-blocks.json', 'w'), indent=1)
    return sum(len(o) for o in overrides.values()), len(overrides)


def demo():
    assert borrowed('cobblemon:apricorn_log[axis=y]', 'minecraft:infested_stone')
    assert borrowed('cobblemon:stripped_apricorn_log[axis=x]', 'minecraft:note_block[instrument=bass,note=14,powered=false]')
    assert not borrowed('cobblemon:apricorn_door[facing=east]', 'minecraft:acacia_door[facing=east]')
    assert not borrowed('cobblemon:apricorn_fence', 'minecraft:stone')
    assert borrowed('cobblemon:apricorn_leaves[distance=1]', 'minecraft:acacia_leaves[distance=1,persistent=true,waterlogged=false]')
    assert natural('minecraft:acacia_leaves[distance=1,persistent=false,waterlogged=false]') and natural('minecraft:kelp[age=3]')
    assert not natural('minecraft:acacia_leaves[distance=1,persistent=true,waterlogged=false]') and not natural('minecraft:note_block[instrument=bass,note=1,powered=false]')
    assert borrowed('cobblemon:apricorn_slab[type=top]', 'minecraft:petrified_oak_slab[type=top]')
    assert split('minecraft:note_block[instrument=bass,note=1,powered=false]') == ('note_block', 'instrument=bass,note=1,powered=false')
    b = model_box([0, 0, 16], [16, 16, 16])  # a flat model on the block's south edge
    assert b['origin'][2] + b['size'][2] <= 8 and b['size'][2] >= 1, b
    print('ok')


if __name__ == '__main__':
    if len(sys.argv) == 2 and sys.argv[1] == 'demo': demo()
    elif len(sys.argv) == 4: print(build(*sys.argv[1:]), '(states, vanilla blocks) ->', sys.argv[3])
    else: sys.exit(__doc__)
