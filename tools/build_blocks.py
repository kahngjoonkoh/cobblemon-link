"""Cobblemon's machine blocks as models the plugin can stand in the world (Machines.java): each block's Java block
model (both halves for the two-block-tall ones) becomes a BetterModel model for Java and a GeyserModelEngine model for
Bedrock, the same way Pokémon are drawn. bedrock_blocks.py (shared with the Fabric product) turns the Java model into
Bedrock geometry; geo2bb.py makes the .bbmodel.

usage: build_blocks.py COBBLEMON_RESOURCES OUT_DIR
  OUT_DIR/blocks/java/clb_<block>.bbmodel     -> plugins/BetterModel/models
  OUT_DIR/blocks/bedrock/clb_<block>/          -> GeyserModelEngine's input
"""
import io, json, os, sys, tempfile

sys.path.insert(0, os.path.dirname(__file__))
import bedrock_blocks, geo2bb

# block -> the blockstate variants drawn (Cobblemon's "north", idle states); a second one is the upper half
MACHINES = {
    'healing_machine': ['facing=north,natural=false,charge=0'],
    'pc': ['facing=north,part=bottom,on=false', 'facing=north,part=top,on=false'],
    'pasture': ['facing=north,part=bottom,on=false', 'facing=north,part=top,on=false'],
    'restoration_tank': ['facing=north,part=bottom,on=false', 'facing=north,part=top,on=false'],
    'fossil_analyzer': ['facing=north,on=false'],
    'tm_machine': ['facing=north,empty=false,open=false,active=false,dispensed=false'],
    'display_case': None,
    'campfire_pot_red': ['facing=north,open=false,occupied=false'],
}


def variant_model(res, block, key):
    bs = json.load(open(f'{res}/assets/cobblemon/blockstates/{block}.json'))
    v = bs.get('variants', {})
    m = v.get(key) if key is not None else next(iter(v.values()))
    m = m[0] if isinstance(m, list) else m
    return m['model'], m.get('y', 0)


def build(res, out):
    src = bedrock_blocks.Src(res)
    n = 0
    with tempfile.TemporaryDirectory() as tmp:
        for block, keys in MACHINES.items():
            if not os.path.exists(f'{res}/assets/cobblemon/blockstates/{block}.json'):
                continue
            elements, textures = [], {}
            for half, key in enumerate(keys or [None]):
                name, _ = variant_model(res, block, key)
                m = bedrock_blocks.model(src, name)
                for e in m['elements']:  # the upper half one block up, its texture names kept apart
                    e = json.loads(json.dumps(e))
                    e['from'][1] += 16 * half
                    e['to'][1] += 16 * half
                    if e.get('rotation'):
                        e['rotation']['origin'][1] += 16 * half
                    for f in e.get('faces', {}).values():
                        f['texture'] = f['texture'].replace('#', f'#h{half}_') if f['texture'].startswith('#') else f['texture']
                    elements.append(e)
                textures.update({f'h{half}_{k}': (v.replace('#', f'#h{half}_') if v.startswith('#') else v)
                                 for k, v in m['textures'].items()})
            merged = f'cobblemon:block/clb_{block}'
            path = f'{tmp}/assets/cobblemon/models/block/clb_{block}.json'
            os.makedirs(os.path.dirname(path), exist_ok=True)
            json.dump({'textures': textures, 'elements': elements}, open(path, 'w'))
            both = bedrock_blocks.Src(tmp)
            both.read = lambda p, a=both.read: a(p) if os.path.exists(os.path.join(tmp, p)) else src.read(p)
            both.has = lambda p: os.path.exists(os.path.join(tmp, p)) or src.has(p)
            if not bedrock_blocks.convert(both, merged, f'{tmp}/geo'):
                continue
            ident = f'clb_clb_{block}'
            geo = json.load(open(f'{tmp}/geo/{ident}/{ident}.geo.json'))['minecraft:geometry'][0]
            png = open(f'{tmp}/geo/{ident}/{ident}.png', 'rb').read()
            name = f'clb_{block}'
            bb, g, an, _ = geo2bb.convert(geo, {'idle': {'loop': True, 'animation_length': 1, 'bones': {}}}, png, name)
            for e in bb['elements']:  # a block model may leave faces out; BetterModel wants all six (an empty one hides)
                for side in ('north', 'east', 'south', 'west', 'up', 'down'):
                    e.setdefault('faces', {}).setdefault(side, {'uv': [0, 0, 0, 0]})
            geo2bb.write(name, bb, g, an, png, f'{out}/blocks/java', f'{out}/blocks/bedrock')
            n += 1
    return n


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    print(build(*sys.argv[1:]), 'machine models ->', sys.argv[2] + '/blocks')
