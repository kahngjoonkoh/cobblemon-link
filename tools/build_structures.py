"""Cobblemon's structures (ruins, shipwreck coves, fishing boats, habitats) for a vanilla server: its worldgen JSON
and structure NBT go into the datapack, with Cobblemon's own blocks swapped for vanilla look-alikes (BLOCKS) and its
own processor types (height_range, random_pooled_states) left out. The server generates them in new chunks.
  python3 tools/build_structures.py RES_DIR OUT_DIR   ->  OUT_DIR/datapack/cobblemon-link/data/cobblemon/{structure,worldgen}
Blocks the plugin draws as Cobblemon's (build_custom_blocks.py: ores, stone blocks, apricorn wood) go in as their
states; berry bushes as markers the plugin makes into bushes; gilded chests as chests the plugin makes into gilded
chests, filled from their loot table (CustomBlocks.java).
ponytail: Cobblemon's village houses (added to vanilla villages by code) are left out.
"""
import glob
import gzip
import io
import json
import os
import re
import struct
import sys


TABLE = {}  # build_custom_blocks.table(): Cobblemon block -> the state that draws it (main() fills it)


def custom(block, props):
    """A Cobblemon block's custom state as (name, {property: value}), or None."""
    b = block.split(':', 1)[1]
    s = TABLE.get(b) or TABLE.get(f"{b}[axis={props.get('axis', 'y')}]") or TABLE.get(f'marker/{b}')
    if not s: return None
    name, _, p = s.partition('[')
    return 'minecraft:' + name.split(':')[-1], dict(kv.split('=') for kv in p.rstrip(']').split(',')) if p else {}


def vanilla(block):
    """A Cobblemon block id -> its vanilla stand-in ("minecraft:air" when nothing fits)."""
    b = block.split(':', 1)[1]
    for pat, to in BLOCKS:
        if re.fullmatch(pat, b):
            return to(b) if callable(to) else to
    return 'minecraft:air'


BLOCKS = [
    (r'.*(gilded|gimmighoul)_chest', 'minecraft:chest'),
    (r'(stripped_)?(\w+_)?apricorn_(log|wood)', lambda b: f"minecraft:{'stripped_' if b.startswith('stripped') else ''}oak_{'wood' if b.endswith('wood') else 'log'}"),
    (r'(stripped_)?saccharine_(log|wood)', lambda b: f"minecraft:{'stripped_' if b.startswith('stripped') else ''}birch_{b.rsplit('_', 1)[1]}"),
    (r'(\w+_)?apricorn_leaves', 'minecraft:oak_leaves'),
    (r'saccharine_leaves', 'minecraft:birch_leaves'),
    (r'apricorn_(planks|slab|stairs|fence|fence_gate|door|trapdoor|button|pressure_plate)', lambda b: 'minecraft:oak_' + b.split('apricorn_')[1]),
    (r'saccharine_(planks|slab|stairs|fence|fence_gate|door|trapdoor|button|pressure_plate)', lambda b: 'minecraft:birch_' + b.split('saccharine_')[1]),
    (r'deepslate_\w+_ore', 'minecraft:deepslate'),
    (r'\w+_ore', 'minecraft:stone'),
    (r'\w+_berry', 'minecraft:sweet_berry_bush'),
    (r'.*(mint|leek|root|herb|grains|vivichoke|galarica).*', 'minecraft:fern'),
]

RENAMED = {'minecraft:chain': 'minecraft:iron_chain'}  # vanilla blocks renamed since Cobblemon's 1.21.1
C_FALLBACK = {'is_snowy_plains': ['minecraft:snowy_plains'], 'is_stony_shores': ['minecraft:stony_shore']}  # when c: is missing
VANILLA_PROCESSORS = re.compile(r'^(minecraft:)?(rule|block_rot|block_ignore|gravity|capped|protected_blocks|lava_submerged_block|block_age|jigsaw_replacement|nop)$')


def fix_json(x):
    """Swaps Cobblemon block ids in a worldgen JSON tree; drops processors Minecraft doesn't have."""
    if isinstance(x, dict):
        if 'processor_type' in x and not VANILLA_PROCESSORS.match(x['processor_type']):
            return None
        if isinstance(x.get('Name'), str):  # a block state: 26.x writes {"id", "properties"} (or just the id)
            c = custom(x['Name'], x.get('Properties') or {}) if x['Name'].startswith('cobblemon:') else None
            if c: return {'id': c[0], 'properties': c[1]}
            name = vanilla(x['Name']) if x['Name'].startswith('cobblemon:') else RENAMED.get(x['Name'], x['Name'])
            return {'id': name, 'properties': x['Properties']} if x.get('Properties') else name
        out = {}
        for k, v in x.items():
            if k == 'block' and isinstance(v, str) and v.startswith('cobblemon:'):
                v = vanilla(v)
            elif k == 'block' and isinstance(v, str):
                v = RENAMED.get(v, v)
            else:
                v = fix_json(v)
            if v is not None:
                out[k] = v
        return out
    if isinstance(x, list):
        return [v for v in (fix_json(i) for i in x) if v is not None]
    return x


# ---- NBT (the structure files): read, swap palette blocks, write ----
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)
FMT = {BYTE: '>b', SHORT: '>h', INT: '>i', LONG: '>q', FLOAT: '>f', DOUBLE: '>d'}


def read(f, t):
    if t in FMT:
        return struct.unpack(FMT[t], f.read(struct.calcsize(FMT[t])))[0]
    if t == STRING:
        return f.read(struct.unpack('>H', f.read(2))[0]).decode('utf-8', 'surrogatepass')
    if t in (BYTES, INTS, LONGS):
        n = struct.unpack('>i', f.read(4))[0]
        size = {BYTES: 'b', INTS: 'i', LONGS: 'q'}[t]
        return (t, list(struct.unpack(f'>{n}{size}', f.read(n * struct.calcsize(size)))))
    if t == LIST:
        et, n = struct.unpack('>bi', f.read(5))
        return (LIST, et, [read(f, et) for _ in range(n)])
    if t == COMPOUND:
        d = {}
        while (ct := f.read(1)[0]) != END:
            k = read(f, STRING)  # (not inline: Python reads the right-hand side first)
            d[k] = (ct, read(f, ct))
        return d


def write(f, t, v):
    if t in FMT:
        f.write(struct.pack(FMT[t], v))
    elif t == STRING:
        b = v.encode('utf-8', 'surrogatepass')
        f.write(struct.pack('>H', len(b)) + b)
    elif t in (BYTES, INTS, LONGS):
        size = {BYTES: 'b', INTS: 'i', LONGS: 'q'}[t]
        f.write(struct.pack(f'>i{len(v[1])}{size}', len(v[1]), *v[1]))
    elif t == LIST:
        f.write(struct.pack('>bi', v[1], len(v[2])))
        for i in v[2]:
            write(f, v[1], i)
    elif t == COMPOUND:
        for k, (ct, cv) in v.items():
            f.write(bytes([ct]))
            write(f, STRING, k)
            write(f, ct, cv)
        f.write(bytes([END]))


def fix_nbt(data):
    """Structure NBT bytes -> the same with Cobblemon's palette blocks swapped and its entities and block entity data dropped."""
    f = io.BytesIO(gzip.decompress(data))
    assert f.read(1)[0] == COMPOUND
    name = read(f, STRING)
    root = read(f, COMPOUND)
    swapped, gilded = set(), {}
    palettes = [root['palette'][1][2]] if 'palette' in root else [p[2] for p in root.get('palettes', (0, (0, 0, [])))[1][2]]
    for pal in palettes:
        for i, st in enumerate(pal):
            if st['Name'][1] in RENAMED:
                st['Name'] = (STRING, RENAMED[st['Name'][1]])
            if not st['Name'][1].startswith('cobblemon:'): continue
            block = st['Name'][1]
            props = {k: v[1] for k, v in st.get('Properties', (COMPOUND, {}))[1].items()}
            c = custom(block, props)
            if c:  # drawn as Cobblemon's by the plugin
                st['Name'] = (STRING, c[0])
                st['Properties'] = (COMPOUND, {k: (STRING, v) for k, v in c[1].items()})
            elif re.fullmatch(r'.*(gilded|gimmighoul)_chest', block):  # a chest the plugin makes a gilded chest
                st['Name'] = (STRING, 'minecraft:chest')
                st['Properties'] = (COMPOUND, {'facing': (STRING, props.get('facing', 'north')), 'type': (STRING, 'single'),
                                               'waterlogged': (STRING, props.get('waterlogged', 'false'))})
                gilded[i] = block.split(':')[1]
            else:
                st['Name'] = (STRING, vanilla(block))
            swapped.add(i)
    if 'blocks' in root:  # a swapped block keeps no block entity data (a Cobblemon block's isn't the stand-in's)
        for b in root['blocks'][1][2]:
            if b['state'][1] in gilded:  # the plugin reads which chest and its loot from here (PublicBukkitValues)
                loot = b.get('nbt', (COMPOUND, {}))[1].get('LootTable', (STRING, ''))[1]
                b['nbt'] = (COMPOUND, {'id': (STRING, 'minecraft:chest'), 'PublicBukkitValues': (COMPOUND, {
                    'cobblemonlink:gilded': (STRING, gilded[b['state'][1]] + '|' + loot)})})
            elif b['state'][1] in swapped:
                b.pop('nbt', None)
    if 'entities' in root:
        ents = root['entities'][1]
        root['entities'] = (LIST, (LIST, ents[1], [e for e in ents[2] if not e.get('nbt', (0, {}))[1].get('id', (0, ''))[1].startswith('cobblemon:')]))
    out = io.BytesIO()
    out.write(bytes([COMPOUND]))
    write(out, STRING, name)
    write(out, COMPOUND, root)
    return gzip.compress(out.getvalue(), mtime=0)


def main(res, out):
    import build_custom_blocks
    TABLE.update(build_custom_blocks.table(res)[0])
    src, dst = f'{res}/data/cobblemon', f'{out}/datapack/cobblemon-link/data/cobblemon'
    n = 0
    for p in glob.glob(f'{src}/structure/**/*.nbt', recursive=True):
        rel = os.path.relpath(p, src)
        if rel.startswith('structure/village'):
            continue  # ponytail: villages need Cobblemon's code to join vanilla ones
        os.makedirs(os.path.dirname(f'{dst}/{rel}'), exist_ok=True)
        open(f'{dst}/{rel}', 'wb').write(fix_nbt(open(p, 'rb').read()))
        n += 1
    for kind in ('structure', 'structure_set', 'template_pool', 'processor_list'):
        for p in glob.glob(f'{src}/worldgen/{kind}/**/*.json', recursive=True):
            rel = os.path.relpath(p, src)
            if 'village' in rel:
                continue
            os.makedirs(os.path.dirname(f'{dst}/{rel}'), exist_ok=True)
            d = fix_json(json.load(open(p)))
            if isinstance(d.get('biomes'), str) and d['biomes'].startswith('#c:'):  # a c: tag the server may not have
                d['biomes'] = '#cobblemon:c_' + d['biomes'][3:].replace('/', '_')
                tag = f"{dst}/tags/worldgen/biome/{d['biomes'][11:]}.json"
                os.makedirs(os.path.dirname(tag), exist_ok=True)
                json.dump({'values': [{'id': '#c:' + d['biomes'][13:], 'required': False}] +
                           C_FALLBACK.get(d['biomes'][13:], [])}, open(tag, 'w'))
            json.dump(d, open(f'{dst}/{rel}', 'w'), indent=1)
    for p in glob.glob(f'{src}/tags/worldgen/structure/**/*.json', recursive=True):
        rel = os.path.relpath(p, src)
        os.makedirs(os.path.dirname(f'{dst}/{rel}'), exist_ok=True)
        open(f'{dst}/{rel}', 'w').write(open(p).read())
    print(n, 'structure pieces ->', dst)


def demo():
    assert vanilla('cobblemon:gilded_chest') == 'minecraft:chest'
    assert vanilla('cobblemon:stripped_red_apricorn_log') == 'minecraft:stripped_oak_log'
    assert vanilla('cobblemon:deepslate_moon_stone_ore') == 'minecraft:deepslate'
    assert vanilla('cobblemon:healing_machine') == 'minecraft:air'
    assert fix_json({'processors': [{'processor_type': 'cobblemon:height_range'}, {'processor_type': 'minecraft:rule', 'rules': [
        {'output_state': {'Name': 'cobblemon:oran_berry'}}]}]}) == {'processors': [{'processor_type': 'minecraft:rule', 'rules': [
            {'output_state': 'minecraft:sweet_berry_bush'}]}]}
    assert fix_json({'Name': 'minecraft:oak_log', 'Properties': {'axis': 'y'}}) == {'id': 'minecraft:oak_log', 'properties': {'axis': 'y'}}
    root = {'palette': (LIST, (LIST, COMPOUND, [{'Name': (STRING, 'cobblemon:gilded_chest')}, {'Name': (STRING, 'minecraft:stone')}])),
            'blocks': (LIST, (LIST, COMPOUND, [{'state': (INT, 0), 'nbt': (COMPOUND, {})}, {'state': (INT, 1)}]))}
    raw = io.BytesIO()
    raw.write(bytes([COMPOUND]))
    write(raw, STRING, '')
    write(raw, COMPOUND, {k: v[1] if False else v for k, v in root.items()} | {})
    fixed = io.BytesIO(gzip.decompress(fix_nbt(gzip.compress(raw.getvalue()))))
    fixed.read(1)
    read(fixed, STRING)
    back = read(fixed, COMPOUND)
    assert back['palette'][1][2][0]['Name'][1] == 'minecraft:chest'
    assert back['blocks'][1][2][0]['nbt'][1]['PublicBukkitValues'][1]['cobblemonlink:gilded'][1] == 'gilded_chest|'
    TABLE['moon_stone_ore'] = 'brown_mushroom_block[down=true,east=false]'
    assert custom('cobblemon:moon_stone_ore', {}) == ('minecraft:brown_mushroom_block', {'down': 'true', 'east': 'false'})
    assert fix_json({'Name': 'cobblemon:moon_stone_ore'}) == {'id': 'minecraft:brown_mushroom_block', 'properties': {'down': 'true', 'east': 'false'}}
    TABLE.clear()


if __name__ == '__main__':
    demo()
    if len(sys.argv) == 3:
        main(*sys.argv[1:])
