"""Cobblemon Bedrock geo + animation -> Blockbench .bbmodel (for BetterModel)
and a matching Bedrock input folder (for GeyserModelEngine).

usage: geo2bb.py GEO ANIM TEXTURE NAME OUT_BBMODEL OUT_BEDROCK_DIR ANIM_MAP
  ANIM_MAP: comma list src=dst, e.g. ground_idle=idle,ground_walk=walk
"""
import base64, json, math, os, re, sys, uuid

# ponytail: mirrors Blockbench's own bedrock importer (x negated, rx/ry negated);
# keyframes are copied raw, same as Blockbench does for bedrock files.


SAFE = re.compile(r'^[0-9.+\-*/() ,a-z_]*$')


def molang(expr, t):
    """Evaluate Cobblemon's sin/cos-of-anim_time molang. Bedrock trig is in degrees.
    ponytail: only q.anim_time + math.sin/cos/abs; anything else raises, add when a model needs it."""
    e = expr.lower().replace('query.', 'q.').replace('q.anim_time', 't').replace('math.', '')
    if not SAFE.match(e) or set(re.findall(r'[a-z_]+', e)) - {'sin', 'cos', 'abs', 't'}:
        raise ValueError('unsupported molang: ' + expr)
    env = {'sin': lambda d: math.sin(math.radians(d)), 'cos': lambda d: math.cos(math.radians(d)),
           'abs': abs, 't': t, '__builtins__': {}}
    return eval(e, env)  # input validated above: digits, operators, sin/cos/abs/t only


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


def convert(geo_path, anim_path, tex_path, name, anim_map):
    geo = json.load(open(geo_path))['minecraft:geometry'][0]
    tw, th = geo['description']['texture_width'], geo['description']['texture_height']
    elements, groups, children = [], {}, {}
    for b in geo['bones']:
        g = {'name': b['name'], 'origin': neg_x(b.get('pivot', [0, 0, 0])),
             'rotation': [-r if i < 2 else r for i, r in enumerate(b.get('rotation', [0, 0, 0]))],
             'uuid': uid(), 'export': True, 'visibility': True, 'children': []}
        groups[b['name']] = g
        children.setdefault(b.get('parent'), []).append(b['name'])
        for c in b.get('cubes', []):
            o, s = c['origin'], c['size']
            frm = [-(o[0] + s[0]), o[1], o[2]]
            box = isinstance(c.get('uv', [0, 0]), list)
            mirror = c.get('mirror', b.get('mirror', False))
            faces = box_faces(*c.get('uv', [0, 0]), s, mirror) if box else per_face(c['uv'])
            rot = c.get('rotation', [0, 0, 0])
            el = {'name': b['name'], 'type': 'cube', 'uuid': uid(), 'box_uv': box,
                  'from': frm, 'to': [frm[0] + s[0], frm[1] + s[1], frm[2] + s[2]],
                  'origin': neg_x(c.get('pivot', [0, 0, 0])),
                  'rotation': [-rot[0], -rot[1], rot[2]], 'inflate': c.get('inflate', 0),
                  'faces': {k: {'uv': v, 'texture': 0} for k, v in faces.items()},
                  'export': True, 'visibility': True}
            if box:
                el['uv_offset'] = c.get('uv', [0, 0])
            elements.append(el)
            g['children'].append(el['uuid'])

    def tree(n):
        g = groups[n]
        g['children'] += [tree(c) for c in children.get(n, [])]
        return g
    outliner = [tree(n) for n in children.get(None, [])]

    anims_src = json.load(open(anim_path))['animations']
    bb_anims, br_anims = [], {}
    for src, dst in anim_map.items():
        key = next(k for k in anims_src if k.endswith('.' + src))
        a = anims_src[key]
        br_anims[dst] = a
        animators = {}
        for bone, chans in a.get('bones', {}).items():
            if bone not in groups:
                continue
            kfs = []
            for ch, val in chans.items():
                if ch not in ('rotation', 'position', 'scale'):
                    continue
                frames = val if isinstance(val, dict) and not {'post', 'pre'} & set(val) else {'0.0': val}
                length = a.get('animation_length', 1.0)
                if len(frames) == 1 and 'q.' in json.dumps(frames).replace('query.', 'q.'):
                    # constant molang channel -> bake 20 samples/s over the clip
                    kv = next(iter(frames.values()))
                    frames = {str(i / 20): kv for i in range(int(length * 20) + 1)}
                for t, kv in frames.items():
                    interp = 'linear'
                    if isinstance(kv, dict):
                        interp = kv.get('lerp_mode', 'linear')
                        kv = kv.get('post', kv.get('pre'))
                    if not isinstance(kv, list):
                        kv = [kv] * 3
                    kv = [num(x, float(t)) for x in kv]
                    kfs.append({'channel': ch, 'uuid': uid(), 'time': float(t), 'interpolation': interp,
                                'data_points': [dict(zip('xyz', kv))]})
            animators[groups[bone]['uuid']] = {'name': bone, 'type': 'bone', 'keyframes': kfs}
        bb_anims.append({'uuid': uid(), 'name': dst, 'loop': 'loop' if a.get('loop') else 'once',
                         'override': False, 'length': a.get('animation_length', 1.0), 'snapping': 24,
                         'animators': animators})

    png = open(tex_path, 'rb').read()
    bb = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': True},
          'name': name, 'resolution': {'width': tw, 'height': th},
          'elements': elements, 'outliner': outliner,
          'textures': [{'name': name + '.png', 'id': '0', 'uuid': uid(), 'width': tw, 'height': th,
                        'uv_width': tw, 'uv_height': th, 'render_mode': 'default',
                        'source': 'data:image/png;base64,' + base64.b64encode(png).decode()}],
          'animations': bb_anims}
    return bb, geo, br_anims, png


def main(a):
    if len(a) != 7:
        sys.exit(__doc__)
    geo_p, anim_p, tex_p, name, out_bb, out_br, amap = a
    anim_map = dict(p.split('=') for p in amap.split(','))
    bb, geo, br_anims, png = convert(geo_p, anim_p, tex_p, name, anim_map)
    json.dump(bb, open(out_bb, 'w'))
    os.makedirs(out_br, exist_ok=True)
    geo['description']['identifier'] = 'geometry.' + name
    json.dump({'format_version': '1.12.0', 'minecraft:geometry': [geo]}, open(f'{out_br}/{name}.geo.json', 'w'))
    json.dump({'format_version': '1.8.0', 'animations': br_anims}, open(f'{out_br}/{name}.animation.json', 'w'))
    open(f'{out_br}/{name}.png', 'wb').write(png)
    print(len(bb['elements']), 'cubes,', len(geo['bones']), 'bones,', [x['name'] for x in bb['animations']])


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


if __name__ == '__main__':
    demo()
    main(sys.argv[1:])
