"""Minimal Anvil reader: block states of chunks. usage: mca.py WORLD_DIR X Z RADIUS_CHUNKS"""
import struct, zlib, sys, io, os, collections
def nbt(b):
    f = io.BytesIO(b)
    def rd(t):
        if t == 1: return struct.unpack('>b', f.read(1))[0]
        if t == 2: return struct.unpack('>h', f.read(2))[0]
        if t == 3: return struct.unpack('>i', f.read(4))[0]
        if t == 4: return struct.unpack('>q', f.read(8))[0]
        if t == 5: return struct.unpack('>f', f.read(4))[0]
        if t == 6: return struct.unpack('>d', f.read(8))[0]
        if t == 7: n = struct.unpack('>i', f.read(4))[0]; return f.read(n)
        if t == 8: n = struct.unpack('>H', f.read(2))[0]; return f.read(n).decode('utf-8', 'replace')
        if t == 9:
            et, n = struct.unpack('>bi', f.read(5)); return [rd(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = f.read(1)[0]
                if tt == 0: return d
                name = f.read(struct.unpack('>H', f.read(2))[0]).decode('utf-8', 'replace'); d[name] = rd(tt)
        if t == 11: n = struct.unpack('>i', f.read(4))[0]; return list(struct.unpack(f'>{n}i', f.read(4 * n)))
        if t == 12: n = struct.unpack('>i', f.read(4))[0]; return list(struct.unpack(f'>{n}q', f.read(8 * n)))
    tt = f.read(1)[0]; f.read(struct.unpack('>H', f.read(2))[0]); return rd(tt)
def chunk(world, cx, cz):
    p = f'{world}/region/r.{cx >> 5}.{cz >> 5}.mca'
    if not os.path.exists(p): return None
    with open(p, 'rb') as f:
        h = f.read(4096); i = 4 * ((cx & 31) + (cz & 31) * 32)
        off = int.from_bytes(h[i:i + 3], 'big')
        if not off: return None
        f.seek(off * 4096); ln, comp = struct.unpack('>ib', f.read(5)); data = f.read(ln - 1)
    return nbt(zlib.decompress(data))
def blocks(c):
    """yields (x, y, z, state) for non-air blocks"""
    for s in c.get('sections', []):
        bs = s.get('block_states'); 
        if not bs: continue
        pal = bs['palette']
        if len(pal) == 1:
            if pal[0]['Name'] != 'minecraft:air':
                for i in range(4096): yield i & 15, s['Y'] * 16 + (i >> 8), (i >> 4) & 15, pal[0]
            continue
        bits = max(4, (len(pal) - 1).bit_length()); per = 64 // bits; mask = (1 << bits) - 1
        for i in range(4096):
            v = (bs['data'][i // per] & 0xFFFFFFFFFFFFFFFF) >> ((i % per) * bits) & mask
            st = pal[v]
            if st['Name'] != 'minecraft:air': yield i & 15, s['Y'] * 16 + (i >> 8), (i >> 4) & 15, st
def name(st):
    p = st.get('Properties', {})
    return st['Name'] + ('[' + ','.join(f'{k}={v}' for k, v in sorted(p.items())) + ']' if p else '')
if __name__ == '__main__':
    w, X, Z, R = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    cnt = collections.Counter()
    for cx in range((X >> 4) - R, (X >> 4) + R + 1):
        for cz in range((Z >> 4) - R, (Z >> 4) + R + 1):
            c = chunk(w, cx, cz)
            if c:
                for x, y, z, st in blocks(c):
                    n = name(st)
                    if any(k in n for k in ('leaves', 'log', 'wood', 'cobblemon:')): cnt[n] += 1
    for n, k in cnt.most_common(60): print(k, n)
