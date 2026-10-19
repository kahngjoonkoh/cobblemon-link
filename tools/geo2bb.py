"""Cobblemon Bedrock geo + animation -> Blockbench .bbmodel (for BetterModel)
and a matching Bedrock input folder (for GeyserModelEngine).

usage: geo2bb.py GEO ANIM TEXTURE NAME OUT_JAVA_DIR OUT_BEDROCK_DIR ANIM_MAP [SCALE]
  ANIM_MAP: comma list src=dst, e.g. ground_idle=idle,ground_walk=walk
  SCALE: Cobblemon species baseScale (default 1)
For every species at once see build_all.py.
"""
import base64, json, math, os, random, re, sys, uuid, zlib

# ponytail: mirrors Blockbench's own bedrock importer (x negated, rx/ry negated);
# keyframes are copied raw, same as Blockbench does for bedrock files.


SAFE = re.compile(r'^[0-9.+\-*/() ,<>=!&|?:a-z_]*$')
FUNCS = {'sin', 'cos', 'abs', 'clamp', 'min', 'max', 'mod', 'pow', 'sqrt', 'round', 'ceil', 'floor', 'pi', 'anim_t',
         'exp', 'random', 'random_integer'}
# riding/sound/temp queries are 0 when a Pokémon just stands or walks
OTHER_QUERY = re.compile(r'\b(?:q|query|v|variable|t|temp|c|context)\.(?!anim_time\b)[a-z_.]+(?:\([^()]*\))?')


BARE_NAME = re.compile(r'(?<![.\w])[a-z_]\w*\b(?!\s*[.(])')


def decimal_commas(e):
    """'4,46' outside any function call is a typo for 4.46 (grafaiai: sibling bone says 4.46)."""
    out, depth = [], 0
    for i, ch in enumerate(e):
        depth += (ch == '(') - (ch == ')')
        if ch == ',' and depth == 0 and 0 < i < len(e) - 1 and e[i - 1].isdigit() and e[i + 1].isdigit():
            ch = '.'
        out.append(ch)
    return ''.join(out)


def clean(expr):
    """Lower-case, canonical q.anim_time, every other query/variable -> 0. Shared by Java bake and Bedrock copy."""
    e = decimal_commas(expr.lower().replace('query.anim_time', 'q.anim_time'))
    e = OTHER_QUERY.sub('0', e)
    # bare names like NaN (Blockbench export bug) or an undefined `speed` are 0 in molang, as in the mod
    return BARE_NAME.sub('0', e)


def molang(expr, t):
    """Evaluate Cobblemon's anim_time molang. Bedrock trig is in degrees.
    ponytail: math functions + q.anim_time only; anything else raises (caller drops that channel)."""
    e = clean(expr).replace('q.anim_time', 'anim_t').replace('math.', '')
    if not SAFE.match(e) or set(re.findall(r'[a-z_]+', e)) - FUNCS:
        raise ValueError('unsupported molang: ' + expr)
    rad = math.radians
    env = {'sin': lambda d: math.sin(rad(d)), 'cos': lambda d: math.cos(rad(d)), 'abs': abs,
           'clamp': lambda v, lo, hi: max(lo, min(hi, v)), 'min': min, 'max': max, 'mod': math.fmod,
           'pow': pow, 'sqrt': math.sqrt, 'round': round, 'ceil': math.ceil, 'floor': math.floor,
           'pi': math.pi, 'anim_t': t, 'exp': math.exp, '__builtins__': {}}
    r = random.Random(zlib.crc32(f'{expr}@{t}'.encode()))  # reproducible builds
    env['random'] = r.uniform
    env['random_integer'] = r.randint
    return eval(e, env)  # input validated above: digits, operators, whitelisted names only


def num(x, t):
    return molang(x, t) if isinstance(x, str) else x


def uid():
    return str(uuid.uuid4())


def neg_x(v):
    return [-v[0], v[1], v[2]]


def box_faces(u, v, size, mirror):
    w, h, d = (math.floor(s + 1e-6) for s in size)
    f = {
        'north': [u + d, v + d, u + d + w, v + d + h],
        'east': [u, v + d, u + d, v + d + h],
        'south': [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        'west': [u + d + w, v + d, u + 2 * d + w, v + d + h],
        'up': [u + d + w, v + d, u + d, v],
        'down': [u + d + 2 * w, v, u + d + w, v + d],
    }
    if mirror:
        f['east'], f['west'] = f['west'], f['east']
        for k in f:
            a = f[k]
            f[k] = [a[2], a[1], a[0], a[3]]
    return f


def per_face(uvd):
    out = {}
    for k, fd in uvd.items():
        (u, v), (su, sv) = fd['uv'], fd.get('uv_size', [0, 0])
        out[k] = [u, v, u + su, v + sv]
    return out


KEEP_BONE = re.compile(r'^(head|hitbox|shadow|mount|locator.*|seat.*|(h|hi|b|ob|p)_.*)$')


def merge_still(g, moving):
    """Java only: a group that no animation moves and that has no rotation of its own carries its cubes exactly where
    its parent does, so its cubes join the parent's display. Halves displays (and packets) per model on average.
    Bedrock keeps every bone (GeyserModelEngine's disable_part_visibility makes the bone lists independent)."""
    kids = []
    for c in g['children']:
        if isinstance(c, str):
            kids.append(c)
            continue
        c = merge_still(c, moving)
        if c['name'] in moving or any(c.get('rotation', [0, 0, 0])) or KEEP_BONE.match(c['name']):
            kids.append(c)
        else:
            kids += c['children']
    g['children'] = kids
    return g


STEP = 0.05                                       # sample the true curves at 20 Hz (the server tick)
TOLERANCE = {'rotation': 1.0, 'position': 0.05, 'scale': 0.01}  # degrees, model px, scale factor


def curve(val, mul=1):
    """One Bedrock channel as f(t) -> [x, y, z]: keyframes (linear or catmull-rom, pre/post) or one molang vector."""
    def vec(kv, t):
        kv = kv if isinstance(kv, list) else [kv] * 3
        out = [num(x, t) * mul for x in kv]
        if not all(isinstance(x, (int, float)) and math.isfinite(x) for x in out):
            raise ValueError(kv)  # e.g. grafaiai has a list nested inside a vector
        return out
    frames = val if isinstance(val, dict) and not {'post', 'pre'} & set(val) else {'0.0': val}
    keys = []
    for t, kv in sorted(frames.items(), key=lambda kv: float(kv[0])):
        mode = kv.get('lerp_mode', 'linear') if isinstance(kv, dict) else 'linear'
        pre = kv.get('pre', kv.get('post')) if isinstance(kv, dict) else kv
        post = kv.get('post', kv.get('pre')) if isinstance(kv, dict) else kv
        keys.append((float(t), pre, post, mode))
    if len(keys) == 1:  # a single value: constant, or molang of q.anim_time evaluated as time runs
        kv = keys[0][2]
        vec(kv, 0.0)
        return lambda t: vec(kv, t)
    vals = [(t, vec(pre, t), vec(post, t), mode) for t, pre, post, mode in keys]  # keyed molang: at its key time

    def f(t):
        if t <= vals[0][0]:
            return vals[0][2]
        if t >= vals[-1][0]:
            return vals[-1][1]
        i = max(j for j in range(len(vals)) if vals[j][0] <= t)
        (t1, _, p1, m1), (t2, p2, _, m2) = vals[i], vals[i + 1]
        u = (t - t1) / (t2 - t1)
        if 'catmullrom' not in (m1, m2):
            return [a + (b - a) * u for a, b in zip(p1, p2)]
        p0 = vals[i - 1][2] if i > 0 else p1
        p3 = vals[i + 2][1] if i + 2 < len(vals) else p2
        return [0.5 * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u + (-a + 3 * b - 3 * c + d) * u ** 3)
                for a, b, c, d in zip(p0, p1, p2, p3)]
    return f


def simplify(curves, length):
    """Fewest keyframe times (shared by every bone, as BetterModel merges them) whose linear interpolation stays
    within TOLERANCE of every channel's true curve at every tick: Douglas-Peucker over all channels at once.
    BetterModel then sends a bone update only at these times and the client interpolates in between."""
    n = max(1, round(length / STEP))
    ts = [round(min(i * STEP, length), 4) for i in range(n + 1)]
    samples = {k: {t: f(t) for t in ts} for k, f in curves.items()}
    keep = {0, n}

    def worst(a, b):
        best, at = 1.0, None
        for i in range(a + 1, b):
            u = (ts[i] - ts[a]) / (ts[b] - ts[a])
            for (bone, ch), sm in samples.items():
                va, vb, vi = sm[ts[a]], sm[ts[b]], sm[ts[i]]
                e = max(abs(x - (p + (q - p) * u)) for x, p, q in zip(vi, va, vb)) / TOLERANCE[ch]
                if e > best:
                    best, at = e, i
        return at
    stack = [(0, n)]
    while stack:
        a, b = stack.pop()
        i = worst(a, b)
        if i is not None:
            keep.add(i)
            stack += [(a, i), (i, b)]
    return [ts[i] for i in sorted(keep)], samples


def convert(geo, anims, png, name, scale=1.0):
    """geo: one minecraft:geometry entry. anims: {dst_name: bedrock animation dict}.
    Returns (bbmodel, bedrock geo file, bedrock animation file, dropped channels)."""
    tw, th = geo['description']['texture_width'], geo['description']['texture_height']
    sc = lambda v: [x * scale for x in v]
    elements, groups, children = [], {}, {}
    for b in geo['bones']:
        g = {'name': b['name'], 'origin': sc(neg_x(b.get('pivot', [0, 0, 0]))),
             'rotation': [-r if i < 2 else r for i, r in enumerate(b.get('rotation', [0, 0, 0]))],
             'uuid': uid(), 'export': True, 'visibility': True, 'children': []}
        groups[b['name']] = g
        children.setdefault(b.get('parent'), []).append(b['name'])
        for c in b.get('cubes', []):
            o, s = c['origin'], c['size']
            frm = [-(o[0] + s[0]), o[1], o[2]]
            box = isinstance(c.get('uv', [0, 0]), list)
            mirror = c.get('mirror', b.get('mirror', False))
            # UVs come from the unscaled size; geometry is scaled after (Java side only)
            faces = box_faces(*c.get('uv', [0, 0]), s, mirror) if box else per_face(c['uv'])
            rot = c.get('rotation', [0, 0, 0])
            el = {'name': b['name'], 'type': 'cube', 'uuid': uid(), 'box_uv': False,
                  'from': sc(frm), 'to': sc([frm[0] + s[0], frm[1] + s[1], frm[2] + s[2]]),
                  'origin': sc(neg_x(c.get('pivot', [0, 0, 0]))),
                  'rotation': [-rot[0], -rot[1], rot[2]], 'inflate': c.get('inflate', 0) * scale,
                  'faces': {k: {'uv': v, 'texture': 0} for k, v in faces.items()},
                  'export': True, 'visibility': True}
            elements.append(el)
            g['children'].append(el['uuid'])

    def tree(n):
        g = groups[n]
        g['children'] += [tree(c) for c in children.get(n, [])]
        return g
    outliner = [tree(n) for n in children.get(None, [])]
    moving = {bone for an in anims.values() for bone in an.get('bones', {})}
    outliner = [merge_still(g, moving) for g in outliner]

    bb_anims, br_anims, dropped = [], {}, []
    for dst, a in anims.items():
        length = a.get('animation_length', 1.0)
        curves = {}  # (bone, channel) -> f(t) -> [x, y, z]
        for bone, chans in a.get('bones', {}).items():
            if bone not in groups:
                continue
            for ch, val in chans.items():
                if ch not in ('rotation', 'position', 'scale'):
                    continue
                try:
                    curves[(bone, ch)] = curve(val, scale if ch == 'position' else 1)
                except Exception:  # ponytail: odd molang -> channel dropped on Java, still exact on Bedrock
                    dropped.append(f'{dst}:{bone}.{ch}')
        times, samples = simplify(curves, length)
        animators = {}
        for (bone, ch) in curves:
            kfs = [{'channel': ch, 'uuid': uid(), 'time': t, 'interpolation': 'linear',
                    'data_points': [dict(zip('xyz', samples[(bone, ch)][t]))]} for t in times]
            animators.setdefault(groups[bone]['uuid'], {'name': bone, 'type': 'bone', 'keyframes': []})['keyframes'] += kfs
        loop = {True: 'loop', 'hold_on_last_frame': 'hold'}.get(a.get('loop'), 'once')
        bb_anims.append({'uuid': uid(), 'name': dst, 'loop': loop,
                         'override': False, 'length': length, 'snapping': 24, 'animators': animators})
        # Bedrock plays the original molang natively; strip cobblemon-only queries, sounds, particles
        br = {k: a[k] for k in ('loop', 'animation_length') if k in a}
        br['bones'] = json.loads(json.dumps(a.get('bones', {})),
                                 object_hook=lambda d: {k: clean(v) if isinstance(v, str) else
                                                        [clean(x) if isinstance(x, str) else x for x in v]
                                                        if isinstance(v, list) else v for k, v in d.items()})
        br_anims[dst] = br

    bb = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False},
          'name': name, 'resolution': {'width': tw, 'height': th},
          'elements': elements, 'outliner': outliner,
          'textures': [{'name': name + '.png', 'id': '0', 'uuid': uid(), 'width': tw, 'height': th,
                        'uv_width': tw, 'uv_height': th, 'render_mode': 'default',
                        'source': 'data:image/png;base64,' + base64.b64encode(png).decode()}],
          'animations': bb_anims}

    # Bedrock: box UV depends on cube size, so scale through a wrapper bone instead of resizing cubes
    geo = json.loads(json.dumps(geo))
    geo['description']['identifier'] = 'geometry.' + name
    if scale != 1:
        for b in geo['bones']:
            b.setdefault('parent', 'cl_scale')
        geo['bones'].insert(0, {'name': 'cl_scale', 'pivot': [0, 0, 0]})
        for br in br_anims.values():
            br['bones']['cl_scale'] = {'scale': scale}
    return (bb, {'format_version': '1.12.0', 'minecraft:geometry': [geo]},
            {'format_version': '1.8.0', 'animations': br_anims},  # plain keys: what GeyserModelEngine reads
            dropped)


def write(name, bb, br_geo, br_anim, png, out_java, out_bedrock):
    os.makedirs(out_java, exist_ok=True)
    json.dump(bb, open(f'{out_java}/{name}.bbmodel', 'w'))
    d = f'{out_bedrock}/{name}'
    os.makedirs(d, exist_ok=True)
    json.dump(br_geo, open(f'{d}/{name}.geo.json', 'w'))
    json.dump(br_anim, open(f'{d}/{name}.animation.json', 'w'))
    open(f'{d}/{name}.png', 'wb').write(png)


def main(a):
    if len(a) not in (7, 8):
        sys.exit(__doc__)
    geo_p, anim_p, tex_p, name, out_java, out_bedrock, amap = a[:7]
    src = json.load(open(anim_p))['animations']
    anims = {dst: next(v for k, v in src.items() if k.endswith('.' + s))
             for s, dst in (p.split('=') for p in amap.split(','))}
    geo = json.load(open(geo_p))['minecraft:geometry'][0]
    png = open(tex_p, 'rb').read()
    bb, g, an, dropped = convert(geo, anims, png, name, float(a[7]) if len(a) == 8 else 1.0)
    write(name, bb, g, an, png, out_java, out_bedrock)
    print(len(bb['elements']), 'cubes,', [x['name'] for x in bb['animations']], 'dropped:', dropped)


def demo():
    # box UV for a 2x2x2 cube at uv 0,0 must match Blockbench's layout
    f = box_faces(0, 0, [2, 2, 2], False)
    assert f['north'] == [2, 2, 4, 4] and f['up'] == [4, 2, 2, 0], f
    assert box_faces(0, 0, [2, 2, 2], True)['east'] == [6, 2, 4, 4]
    assert abs(molang("5+math.sin(q.anim_time*90*4)*2", 0.25) - 7) < 1e-9  # sin(90 deg)=1
    assert abs(molang('math.sin(q.anim_time*90)*2', 1) - 2) < 1e-9
    try:
        molang('__import__("os")', 0)
        raise AssertionError('unsafe molang accepted')
    except ValueError:
        pass
    assert molang('math.clamp(q.r.velocity_y(2)*7+q.anim_time, 0, 1)', 0.5) == 0.5  # riding query -> 0
    assert clean('0+math.sin(q.anim_time*90*3-130)*4,46') == '0+math.sin(q.anim_time*90*3-130)*4.46'
    assert clean('math.clamp(1,2,3)') == 'math.clamp(1,2,3)'  # argument commas untouched
    assert clean('NaN-math.sin(q.anim_time*90*speed)') == '0-math.sin(q.anim_time*90*0)'
    assert molang('NaN+math.exp(0)+math.random(1,1)', 0) == 2
    # scale: Java geometry shrinks, UVs keep the unscaled layout, Bedrock gets a wrapper bone
    geo = {'description': {'texture_width': 16, 'texture_height': 16},
           'bones': [{'name': 'body', 'pivot': [0, 4, 0], 'cubes': [{'origin': [0, 0, 0], 'size': [2, 2, 2], 'uv': [0, 0]}]}]}
    anim = {'loop': True, 'bones': {'body': {'position': [0, 'math.sin(q.anim_time*90)*2', 0]}}}
    bb, g, an, dropped = convert(geo, {'idle': anim}, b'', 'x', 0.5)
    el = bb['elements'][0]
    assert el['to'] == [0, 1, 1] and el['faces']['north']['uv'] == [2, 2, 4, 4] and not dropped
    kfs = bb['animations'][0]['animators'][bb['outliner'][0]['uuid']]['keyframes']
    assert len(kfs) < 21 and any(k['time'] == 1.0 and abs(k['data_points'][0]['y'] - 1.0) < 1e-9 for k in kfs)
    # a straight ramp needs only its two ends; a sine needs a few, each tick within tolerance
    times, _ = simplify({('b', 'position'): lambda t: [t, 0, 0]}, 2.0)
    assert times == [0.0, 2.0], times
    sine = lambda t: [math.sin(t * math.pi) * 10, 0, 0]
    times, _ = simplify({('b', 'rotation'): sine}, 2.0)
    for i in range(41):
        t = i * 0.05
        j = min(max(k for k in range(len(times)) if times[k] <= t + 1e-9), len(times) - 2)
        u = (t - times[j]) / (times[j + 1] - times[j])
        assert abs(sine(t)[0] - (sine(times[j])[0] + (sine(times[j + 1])[0] - sine(times[j])[0]) * u)) <= 1.0 + 1e-6
    assert 4 < len(times) < 21, times
    tree_ = {'name': 'root', 'children': [{'name': 'still', 'rotation': [0, 0, 0], 'children': ['c1']},
                                          {'name': 'arm', 'rotation': [0, 0, 0], 'children': ['c2']},
                                          {'name': 'tilted', 'rotation': [0, 10, 0], 'children': ['c3']}]}
    merged = merge_still(tree_, {'arm'})
    assert merged['children'][0] == 'c1' and [c['name'] for c in merged['children'][1:]] == ['arm', 'tilted'], merged
    bad = {'loop': True, 'bones': {'body': {'rotation': [[1, 2], 0, 0]}}}
    assert convert(geo, {'idle': bad}, b'', 'x')[3] == ['idle:body.rotation']
    assert g['minecraft:geometry'][0]['bones'][1]['parent'] == 'cl_scale' and an['animations']['idle']['bones']['cl_scale']
    held = convert(geo, {'faint': dict(anim, loop='hold_on_last_frame')}, b'', 'x')[0]['animations'][0]
    assert held['loop'] == 'hold' and bb['animations'][0]['loop'] == 'loop'


if __name__ == '__main__':
    demo()
    main(sys.argv[1:])
