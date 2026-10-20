#!/usr/bin/env python3
"""cobblemon-link's models for the Fabric product's own renderer (Java clients, 1.21.1): one item model per bone,
drawn by an item display each, animated by the server from the keyframes.

usage: build_models.py SRC_DIR OUT_DIR [GAMEDATA]
  SRC_DIR  .bbmodel files after bil_models.py (rotations 1.21.1 can draw)
  OUT_DIR  pack/ (assets merged into Polymer's pack) and data/<model>.json (bones and animations, read by
           mod/.../Models.java), index.json (model -> bones that have an item model), and with GAMEDATA
           (assets/gamedata.json) looks.json: species -> its looks table (which look a set of aspects draws)

Bone maths follow blockbench-import-library (BbModelImporter): cubes are moved into the bone's space, shrunk to fit
item models' -16..32 box (the display scales them back), and offset to the model's centre (8, 8, 8)."""
import base64, json, os, sys
from concurrent.futures import ProcessPoolExecutor

NS = 'cobblemonlink'


def groups(outliner, parent=-1, out=None, by_elem=None):
    """Depth-first bones: (group, parent index); by_elem: element uuid -> bone index."""
    if out is None: out, by_elem = [], {}
    for c in outliner:
        if isinstance(c, dict):
            i = len(out)
            out.append((c, parent))
            for e in c['children']:
                if not isinstance(e, dict): by_elem[e] = i
            groups(c['children'], i, out, by_elem)
    return out, by_elem


def keys(frames):
    """[t, x, y, z, ...] sorted by time; numbers only (geo2bb writes numbers)."""
    out = []
    for k in sorted(frames, key=lambda k: k['time']):
        p = k['data_points'][0]
        out += [round(k['time'], 4)] + [round(float(p[a]), 4) for a in 'xyz']
    return out


def build(src, out):
    name = os.path.basename(src)[:-8]
    d = json.load(open(src))
    res = d.get('resolution', {'width': 16, 'height': 16})
    texs = d.get('textures', [])
    tex_names = []
    for t in texs:
        n = os.path.splitext(t['name'].lower())[0].replace(' ', '_')
        tex_names.append(n)
        png = base64.b64decode(t['source'].split(',', 1)[1])
        tp = f"{out}/pack/assets/{NS}/textures/item/m/{name}/{n}.png"
        os.makedirs(os.path.dirname(tp), exist_ok=True)
        open(tp, 'wb').write(png)
        w, h = t.get('width', 0), t.get('height', 0)
        if w and h > w and h % w == 0:  # an animated texture: frames stacked downwards
            json.dump({'animation': {'frametime': t.get('frame_time', 1)}}, open(tp + '.mcmeta', 'w'))
    bones, by_elem = groups(d['outliner'])
    cubes = {}
    for e in d['elements']:
        if e.get('type', 'cube') == 'cube' and e['uuid'] in by_elem: cubes.setdefault(by_elem[e['uuid']], []).append(e)
    data, models = [], []
    for i, (g, parent) in enumerate(bones):
        po = bones[parent][0]['origin'] if parent >= 0 else [0, 0, 0]
        local = [g['origin'][a] - po[a] for a in range(3)]
        fit = 1.0
        es = cubes.get(i, [])
        if es and g.get('export', True) and g['name'] != 'hitbox':
            o = g['origin']
            boxes = []
            for e in es:
                inf = e.get('inflate', 0)
                f = [e['from'][a] - inf - o[a] for a in range(3)]
                t = [e['to'][a] + inf - o[a] for a in range(3)]
                boxes.append((e, f, t))
            m = max(max(abs(v) for v in f + t) for _, f, t in boxes) or 1
            s = min(1.0, 24.0 / m)
            fit = 1 / s
            elements, used = [], set()
            for e, f, t in boxes:
                faces = {}
                for side, face in e.get('faces', {}).items():
                    ti = face.get('texture')
                    if ti is None: continue
                    tx = texs[ti] if ti < len(texs) else {}
                    uw, uh = tx.get('uv_width') or res['width'], tx.get('uv_height') or res['height']
                    uv = [face['uv'][k] * 16 / (uw if k % 2 == 0 else uh) for k in range(4)]
                    fo = {'texture': f'#{ti}', 'tintindex': 0, 'uv': [round(v, 4) for v in uv]}
                    if face.get('rotation'): fo['rotation'] = face['rotation']
                    faces[side] = fo
                    used.add(ti)
                if not faces: continue
                el = {'from': [round(v * s + 8, 4) for v in f], 'to': [round(v * s + 8, 4) for v in t], 'faces': faces}
                r = e.get('rotation', [0, 0, 0])
                eo = e.get('origin', [0, 0, 0])
                if any(r) or any(eo):
                    ax = next((k for k in range(3) if r[k]), 1)
                    el['rotation'] = {'axis': 'xyz'[ax], 'angle': r[ax],
                                      'origin': [round((eo[a] - o[a]) * s + 8, 4) for a in range(3)]}
                elements.append(el)
            if elements:
                tm = {str(k): f'{NS}:item/m/{name}/{tex_names[k]}' for k in sorted(used)}
                tm['particle'] = next(iter(tm.values()))
                mp = f"{out}/pack/assets/{NS}/models/item/m/{name}/{i}.json"
                os.makedirs(os.path.dirname(mp), exist_ok=True)
                json.dump({'textures': tm, 'elements': elements, 'display': {'head': {'rotation': [0, 180, 0]}}},
                          open(mp, 'w'), separators=(',', ':'))
                models.append(i)
        data.append([g['name'], parent] + [round(v, 4) for v in local] + [round(v, 4) for v in g.get('rotation', [0, 0, 0])]
                    + [round(fit, 5), 1 if i in models else 0])
    anims = {}
    uuid_to_bone = {g['uuid']: i for i, (g, _) in enumerate(bones)}
    for a in d.get('animations', []):
        chans = {}
        for uid, an in a.get('animators', {}).items():
            if uid not in uuid_to_bone: continue
            c = {}
            for ch, short in (('position', 'p'), ('rotation', 'r'), ('scale', 's')):
                fr = [k for k in an.get('keyframes', []) if k['channel'] == ch]
                if fr: c[short] = keys(fr)
            if c: chans[uuid_to_bone[uid]] = c
        anims[a['name']] = [a.get('length', 0), {'once': 0, 'loop': 1, 'hold': 2}.get(a.get('loop', 'once'), 0), chans]
    os.makedirs(f"{out}/data", exist_ok=True)
    json.dump({'bones': data, 'anims': anims}, open(f"{out}/data/{name}.json", 'w'), separators=(',', ':'))
    return name, models


def demo():
    import tempfile
    px = base64.b64encode(bytes.fromhex('89504e470d0a1a0a')).decode()
    m = {'resolution': {'width': 16, 'height': 16}, 'textures': [{'name': 'a.png', 'source': 'data:image/png;base64,' + px}],
         'elements': [{'uuid': 'e', 'from': [-40, 0, 0], 'to': [8, 2, 2], 'faces': {'north': {'uv': [0, 0, 16, 16], 'texture': 0}}}],
         'outliner': [{'name': 'root', 'uuid': 'g0', 'origin': [0, 0, 0], 'children': [
             {'name': 'arm', 'uuid': 'g1', 'origin': [0, 4, 0], 'rotation': [0, 0, 10], 'children': ['e']}]}],
         'animations': [{'name': 'idle', 'length': 1, 'loop': 'loop', 'animators': {'g1': {'keyframes': [
             {'channel': 'rotation', 'time': 0.5, 'data_points': [{'x': 1, 'y': 0, 'z': 0}]},
             {'channel': 'rotation', 'time': 0, 'data_points': [{'x': 0, 'y': 0, 'z': 0}]}]}}}]}
    with tempfile.TemporaryDirectory() as t:
        json.dump(m, open(t + '/x.bbmodel', 'w'))
        name, models = build(t + '/x.bbmodel', t)
        assert models == [1]
        d = json.load(open(t + '/data/x.json'))
        assert d['bones'][1][:5] == ['arm', 0, 0, 4, 0] and abs(d['bones'][1][8] - 40 / 24) < 1e-3
        assert d['anims']['idle'][2]['1']['r'] == [0, 0, 0, 0, 0.5, 1, 0, 0]
        el = json.load(open(t + '/pack/assets/cobblemonlink/models/item/m/x/1.json'))['elements'][0]
        assert el['from'][0] == -16 and el['from'][1] == round(-4 * 0.6 + 8, 4)


if __name__ == '__main__':
    demo()
    src, out = sys.argv[1], sys.argv[2]
    files = sorted(os.path.join(src, f) for f in os.listdir(src) if f.endswith('.bbmodel'))
    with ProcessPoolExecutor(4) as ex:
        index = dict(ex.map(build, files, [out] * len(files), chunksize=16))
    json.dump(index, open(f"{out}/index.json", 'w'), separators=(',', ':'))
    if len(sys.argv) > 3:
        sp = json.load(open(sys.argv[3]))['species']
        json.dump({k: v['looks'] for k, v in sp.items() if v.get('looks')}, open(f"{out}/looks.json", 'w'), separators=(',', ':'))
    json.dump({'pack': {'pack_format': 34, 'description': 'cobblemon-link models'}}, open(f"{out}/pack/pack.mcmeta", 'w'))
    print(len(index), 'models,', sum(map(len, index.values())), 'bone models')
