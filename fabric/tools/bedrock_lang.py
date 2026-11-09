#!/usr/bin/env python3
"""Bedrock's ride hint for our model entities. Bedrock names the hint by the ridden entity's id
("action.hint.exit.modelengine:arcanine") and had no text for it, so it showed the key; each gets Bedrock's own
pig text. ponytail: English only (the pack can't borrow Bedrock's translations).

usage: bedrock_lang.py GME_INPUT_DIR OUT.mcpack"""
import hashlib, json, os, sys, uuid, zipfile


def lang(ids):
    return ''.join(f'action.hint.exit.modelengine:{i}=Tap sneak to dismount\n' for i in sorted(ids))


def build(models, out):
    ids = [d for d in os.listdir(models) if os.path.isdir(os.path.join(models, d))]
    u = lambda s: str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link/lang' + s))
    text = lang(ids)
    v = [1, 0, int(hashlib.sha256(text.encode()).hexdigest()[:6], 16) % 32768]  # new text, new version (Bedrock caches packs)
    manifest = {'format_version': 2, 'header': {'name': 'cobblemon-link text', 'description': 'ride hints', 'uuid': u(''),
                'version': v, 'min_engine_version': [1, 21, 0]},
                'modules': [{'type': 'resources', 'uuid': u('/res'), 'version': v}]}
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('manifest.json', json.dumps(manifest))
        z.writestr('texts/languages.json', '["en_US"]')
        z.writestr('texts/en_US.lang', text)
    return len(ids)


if __name__ == '__main__':
    assert lang(['b', 'a']) == 'action.hint.exit.modelengine:a=Tap sneak to dismount\naction.hint.exit.modelengine:b=Tap sneak to dismount\n'
    if len(sys.argv) != 3: sys.exit(__doc__)
    print(build(*sys.argv[1:]), 'ride hints')
