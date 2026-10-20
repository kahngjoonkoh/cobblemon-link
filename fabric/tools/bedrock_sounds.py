#!/usr/bin/env python3
"""Cobblemon's sounds for Bedrock players: a pack with Cobblemon's .ogg files and sound_definitions.json, so the
sounds the server plays by name (Sounds.java: cries, the Poké Ball's) reach them through Geyser. Both spellings of a
name are defined, "cobblemon:pokemon.pikachu.cry" and "pokemon.pikachu.cry", as Geyser may pass either.
ponytail: cries and the ball, evolution, shiny, fishing and PC sounds (moves' sounds are client-side animations).
usage: bedrock_sounds.py COBBLEMON_JAR_OR_DIR OUT.mcpack"""
import hashlib, io, json, os, sys, uuid, zipfile

KEEP = ('pokemon.', 'poke_ball.', 'evolution.', 'shiny', 'fishing.', 'pc.', 'gui.', 'battle.')


def build(src, out):
    if os.path.isdir(src):
        read = lambda n: open(os.path.join(src, n), 'rb').read()
        names = set()
        for root, _, files in os.walk(src):
            for f in files: names.add(os.path.relpath(os.path.join(root, f), src))
    else:
        z = zipfile.ZipFile(src)
        read, names = z.read, set(z.namelist())
    events = json.loads(read('assets/cobblemon/sounds.json'))
    defs, files = {}, {}
    for name, ev in events.items():
        if not name.startswith(KEEP): continue
        sounds = []
        for s in ev.get('sounds', []):
            ref = s if isinstance(s, str) else s['name']
            ns, p = ref.split(':') if ':' in ref else ('minecraft', ref)
            f = f'assets/{ns}/sounds/{p}.ogg'
            if f not in names: continue
            files[f'sounds/cobblemon/{p}.ogg'] = read(f)
            sounds.append({'name': f'sounds/cobblemon/{p}', 'volume': s.get('volume', 1.0) if isinstance(s, dict) else 1.0})
        if sounds:
            d = {'category': 'neutral', 'sounds': sounds}
            defs[name] = d
            defs['cobblemon:' + name] = d
    digest = hashlib.sha1(json.dumps(sorted(defs)).encode()).digest()
    ver = [1, 0, int.from_bytes(digest[:3]) % 32768]  # a version part over 65535 makes Bedrock refuse the pack
    manifest = {'format_version': 2, 'header': {'name': 'cobblemon-link sounds', 'description': "Cobblemon's sounds (CC BY-NC 3.0)",
                'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link-sounds')), 'version': ver, 'min_engine_version': [1, 20, 0]},
                'modules': [{'type': 'resources', 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link-sounds-res')), 'version': ver}]}
    files['manifest.json'] = json.dumps(manifest, indent=1).encode()
    files['sounds/sound_definitions.json'] = json.dumps({'format_version': '1.14.0', 'sound_definitions': defs}).encode()
    b = io.BytesIO()
    with zipfile.ZipFile(b, 'w', zipfile.ZIP_DEFLATED) as w:
        for n, d in sorted(files.items()): w.writestr(n, d)
    open(out, 'wb').write(b.getvalue())
    return len(defs) // 2, len(b.getvalue())


def demo():
    import tempfile
    with tempfile.TemporaryDirectory() as t:
        os.makedirs(t + '/assets/cobblemon/sounds/pokemon/pikachu')
        open(t + '/assets/cobblemon/sounds/pokemon/pikachu/pikachu_cry.ogg', 'wb').write(b'ogg')
        json.dump({'pokemon.pikachu.cry': {'sounds': ['cobblemon:pokemon/pikachu/pikachu_cry']}, 'block.x': {'sounds': ['a']}},
                  open(t + '/assets/cobblemon/sounds.json', 'w'))
        n, size = build(t, t + '/s.mcpack')
        z = zipfile.ZipFile(t + '/s.mcpack')
        d = json.loads(z.read('sounds/sound_definitions.json'))['sound_definitions']
        assert n == 1 and 'cobblemon:pokemon.pikachu.cry' in d and d['pokemon.pikachu.cry']['sounds'][0]['name'] == 'sounds/cobblemon/pokemon/pikachu/pikachu_cry'


if __name__ == '__main__':
    demo()
    n, size = build(sys.argv[1], sys.argv[2])
    print(n, 'sounds,', size // 1048576, 'MB')
