#!/usr/bin/env python3
"""1.21.1 item models only allow cube rotations of 0/±22.5/±45 on one axis. Cobblemon's cubes use any angle, so a
cube with any other rotation moves into a bone of its own carrying that rotation (BIL draws each bone as a display
entity, whose transform can rotate freely). usage: split_rotations.py FILE.bbmodel... (in place)"""
import json, sys, uuid

OK = (0, 22.5, -22.5, 45, -45)


def legal(r):
    nz = [a for a in r if a]
    return len(nz) <= 1 and all(a in OK for a in r)


def split(d):
    by_id = {e['uuid']: e for e in d['elements']}
    moved = 0

    def walk(children):
        nonlocal moved
        for i, c in enumerate(children):
            if isinstance(c, dict):
                walk(c['children'])
            elif c in by_id and not legal(by_id[c].get('rotation', [0, 0, 0])):
                e = by_id[c]
                g = {'name': 'r_' + e['name'], 'origin': e.get('origin', [0, 0, 0]), 'rotation': e['rotation'],
                     'uuid': str(uuid.uuid4()), 'export': True, 'visibility': True, 'children': [c]}
                e['rotation'] = [0, 0, 0]
                children[i] = g
                moved += 1
    walk(d['outliner'])
    return moved


def demo():
    d = {'elements': [{'uuid': 'a', 'name': 'x', 'rotation': [0, -15, 0], 'origin': [1, 2, 3]},
                      {'uuid': 'b', 'name': 'y', 'rotation': [0, 22.5, 0]}],
         'outliner': [{'children': ['a', 'b']}]}
    assert split(d) == 1 and d['outliner'][0]['children'][0]['rotation'] == [0, -15, 0]
    assert d['elements'][0]['rotation'] == [0, 0, 0] and d['outliner'][0]['children'][1] == 'b'


if __name__ == '__main__':
    demo()
    for p in sys.argv[1:]:
        d = json.load(open(p))
        print(p.split('/')[-1], 'cubes moved into their own bone:', split(d))
        json.dump(d, open(p, 'w'))
