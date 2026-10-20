#!/usr/bin/env python3
"""cobblemon-link's Java models (assets/java, from tools/geo2bb.py) for blockbench-import-library on 1.21.1: cubes at
angles 1.21.1 can't draw move into their own bone (split_rotations.py) and every texture gets BIL's frame_time.
usage: bil_models.py SRC_DIR OUT_DIR [NAME...]   (only files newer than their output are redone)"""
import json, os, sys
from concurrent.futures import ProcessPoolExecutor
from split_rotations import split


def convert(src, out):
    d = json.load(open(src))
    split(d)
    for t in d.get('textures', []):
        t.setdefault('frame_time', 1)
    json.dump(d, open(out, 'w'), separators=(',', ':'))
    return os.path.basename(src)


def demo():
    import tempfile
    with tempfile.TemporaryDirectory() as t:
        p = os.path.join(t, 'a.bbmodel')
        json.dump({'elements': [{'uuid': 'a', 'name': 'x', 'rotation': [0, -15, 0]}], 'outliner': [{'children': ['a']}],
                   'textures': [{'name': 'a.png'}]}, open(p, 'w'))
        convert(p, p)
        d = json.load(open(p))
        assert d['textures'][0]['frame_time'] == 1 and d['outliner'][0]['children'][0]['rotation'] == [0, -15, 0]


if __name__ == '__main__':
    demo()
    src, out, names = sys.argv[1], sys.argv[2], set(sys.argv[3:])
    os.makedirs(out, exist_ok=True)
    jobs = []
    for f in sorted(os.listdir(src)):
        if not f.endswith('.bbmodel') or names and f[:-8] not in names: continue
        s, o = os.path.join(src, f), os.path.join(out, f)
        if not os.path.exists(o) or os.path.getmtime(o) < os.path.getmtime(s): jobs.append((s, o))
    with ProcessPoolExecutor(4) as ex:
        n = sum(1 for _ in ex.map(convert, *zip(*jobs))) if jobs else 0
    print(n, 'converted,', len(os.listdir(out)), 'in', out)
