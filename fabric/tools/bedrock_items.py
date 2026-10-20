#!/usr/bin/env python3
"""Cobblemon's item icons for Bedrock players. PolyMc shows each Cobblemon item to clients without the mod as a
vanilla item with a custom_model_data number (in Polymer's pack: models/item/<vanilla>.json overrides). This writes
Geyser custom item mappings (format 2) for those numbers and a Bedrock pack with the items' flat textures, so the
Bedrock inventory shows a Poké Ball instead of PolyMc's stand-in (a stick, a feather...).
ponytail: flat items only (a layer0 texture); block items keep the stand-in's icon.
usage: bedrock_items.py POLYMER_PACK.zip OUT_DIR   -> OUT_DIR/cobblemon-link-items.mcpack, OUT_DIR/cobblemon-link-items.json"""
import hashlib, io, json, os, sys, uuid, zipfile


def model(z, names, ref):
    ns, p = ref.split(':') if ':' in ref else ('minecraft', ref)
    f = f'assets/{ns}/models/{p}.json'
    return json.loads(z.read(f)) if f in names else None


def layer0(z, names, ref):
    for _ in range(6):
        m = model(z, names, ref)
        if not m: return None
        t = m.get('textures', {}).get('layer0')
        if t: return t
        ref = m.get('parent')
        if not ref or not ref.startswith('cobblemon'): return None
    return None


def build(pack, out):
    z = zipfile.ZipFile(pack)
    names = set(z.namelist())
    items, textures, files, seen = {}, {}, {}, set()
    for f in sorted(names):
        if not (f.startswith('assets/minecraft/models/item/') and f.endswith('.json')): continue
        vanilla = 'minecraft:' + f.split('/')[-1][:-5]
        for o in json.loads(z.read(f)).get('overrides', []):
            ref, cmd = o['model'], o.get('predicate', {}).get('custom_model_data')
            if not ref.startswith('cobblemon:') or cmd is None or (vanilla, cmd) in seen: continue
            seen.add((vanilla, cmd))  # an item's variants share its number (other predicates): its first look
            tex = layer0(z, names, ref)
            if not tex: continue
            ns, p = tex.split(':') if ':' in tex else ('minecraft', tex)
            src = f'assets/{ns}/textures/{p}.png'
            if src not in names: continue
            key = 'cl_' + ref.split('/')[-1]
            files[f'textures/items/cl/{key}.png'] = z.read(src)
            textures[key] = {'textures': [f'textures/items/cl/{key}']}
            items.setdefault(vanilla, []).append({'type': 'definition', 'model': vanilla, 'bedrock_identifier': 'cobblemonlink:' + key,
                'predicate': [{'type': 'range_dispatch', 'property': 'custom_model_data', 'threshold': int(cmd), 'index': 0}],
                'bedrock_options': {'icon': key}})
    digest = hashlib.sha1(json.dumps(textures, sort_keys=True).encode()).digest()
    ver = [1, 0, int.from_bytes(digest[:3]) % 32768]  # a version part over 65535 makes Bedrock refuse the pack
    manifest = {'format_version': 2, 'header': {'name': 'cobblemon-link items', 'description': "Cobblemon's item icons (CC BY-NC 3.0)",
                'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link-items')), 'version': ver, 'min_engine_version': [1, 20, 0]},
                'modules': [{'type': 'resources', 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cobblemon-link-items-res')), 'version': ver}]}
    files['manifest.json'] = json.dumps(manifest, indent=1).encode()
    files['textures/item_texture.json'] = json.dumps({'resource_pack_name': 'cobblemon-link items', 'texture_name': 'atlas.items',
                                                     'texture_data': textures}).encode()
    os.makedirs(out, exist_ok=True)
    b = io.BytesIO()
    with zipfile.ZipFile(b, 'w', zipfile.ZIP_DEFLATED) as w:
        for n, d in sorted(files.items()): w.writestr(n, d)
    open(f'{out}/cobblemon-link-items.mcpack', 'wb').write(b.getvalue())
    json.dump({'format_version': 2, 'items': items}, open(f'{out}/cobblemon-link-items.json', 'w'), indent=1)
    return sum(map(len, items.values())), ver


def demo():
    import tempfile
    with tempfile.TemporaryDirectory() as t:
        p = os.path.join(t, 'p.zip')
        with zipfile.ZipFile(p, 'w') as w:
            w.writestr('assets/minecraft/models/item/stick.json', json.dumps({'overrides': [
                {'predicate': {'custom_model_data': 7}, 'model': 'cobblemon:item/poke_ball'},
                {'predicate': {'custom_model_data': 8}, 'model': 'cobblemonlink:item/m/x/0'}]}))
            w.writestr('assets/cobblemon/models/item/poke_ball.json', json.dumps({'textures': {'layer0': 'cobblemon:item/poke_balls/poke_ball'}}))
            w.writestr('assets/cobblemon/textures/item/poke_balls/poke_ball.png', b'png')
        n, ver = build(p, t)
        m = json.load(open(t + '/cobblemon-link-items.json'))['items']['minecraft:stick'][0]
        assert n == 1 and m['predicate'][0]['threshold'] == 7 and ver[2] < 65536 and m['bedrock_options']['icon'] == 'cl_poke_ball'


if __name__ == '__main__':
    demo()
    n, ver = build(sys.argv[1], sys.argv[2])
    print(n, 'items, pack version', ver)
