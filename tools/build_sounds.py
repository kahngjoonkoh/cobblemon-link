"""Cobblemon's sounds for both clients: cries, battle music, Poké Ball, evolution, fishing, PC, items and machines.
The plugin plays them by name (Sounds.java); Java hears them through a resource pack, Bedrock through Geyser and a
Bedrock pack with the same names (bedrock_sounds.py, shared with the Fabric product).

usage: build_sounds.py COBBLEMON_RESOURCES OUT_DIR
  OUT_DIR/sounds-pack.zip  Java: assets/cobblemon/sounds.json (the kept events) and their .ogg files
  OUT_DIR/sounds.mcpack    Bedrock
"""
import json, os, sys, zipfile

sys.path.insert(0, os.path.dirname(__file__))
import bedrock_sounds


def java(res, out):
    events = json.load(open(f'{res}/assets/cobblemon/sounds.json'))
    keep, files = {}, {}
    for name, ev in events.items():
        if not name.startswith(bedrock_sounds.KEEP):
            continue
        keep[name] = ev
        for s in ev.get('sounds', []):
            ref = s if isinstance(s, str) else s['name']
            ns, p = ref.split(':') if ':' in ref else ('minecraft', ref)
            f = f'{res}/assets/{ns}/sounds/{p}.ogg'
            if ns == 'cobblemon' and os.path.exists(f):
                files[f'assets/cobblemon/sounds/{p}.ogg'] = open(f, 'rb').read()
    files['assets/cobblemon/sounds.json'] = json.dumps(keep).encode()
    files['pack.mcmeta'] = json.dumps({'pack': {'description': "Cobblemon's sounds (CC BY-NC 3.0)", 'pack_format': 97,
                                                'supported_formats': [46, 999], 'min_format': 46, 'max_format': 999}}).encode()
    with zipfile.ZipFile(f'{out}/sounds-pack.zip', 'w', zipfile.ZIP_STORED) as z:  # .ogg doesn't compress
        for name in sorted(files):
            z.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), files[name])
    return len(keep)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    n = java(*sys.argv[1:])
    bedrock_sounds.build(sys.argv[1], sys.argv[2] + '/sounds.mcpack')
    print(n, 'sound events ->', sys.argv[2])
