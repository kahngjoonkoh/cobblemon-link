"""Cobblemon's species looks: which model + texture a Pokémon with a given set of aspects is drawn with.

Cobblemon (VaryingModelRepository) takes every resolver of a species in `order`, and every variation whose aspects
are all on the Pokémon overrides the model / texture so far. Each distinct (model, texture) pair is one converted
model here, named `<species>` for the base look and `<species>__<aspect>__...` (aspects that produced it) otherwise.
Used by build_all.py (what to convert) and build_data.py (the table the plugin resolves with at runtime).
Render layers (Charmander's flame, glows, patterns: layers() below) are painted onto the texture, so they show on
the converted models and pictures; Cobblemon draws them emissive and animated, here they're plain and still.
ponytail: cosmetic items (player-applied) and alpha eyes are not looks; animated textures use their first frame.
"""
import glob, itertools, json, os, re

SKIP = re.compile(r'^(cosmetic_item-.*|alpha_eyes)$')


def resolvers(res):
    """{species: [resolver, ...]} in Cobblemon's order."""
    by = {}
    for p in sorted(glob.glob(f'{res}/assets/cobblemon/bedrock/pokemon/resolvers/**/*.json', recursive=True)):
        d = json.load(open(p))
        by.setdefault(d['species'].split(':')[1], []).append(d)
    for rs in by.values():
        rs.sort(key=lambda d: d.get('order', 0))
    return by


def features(res):
    """({feature name: definition}, {species: [feature names]}) from species_features + assignments + species files."""
    defs = {os.path.basename(p)[:-5]: json.load(open(p))
            for p in glob.glob(f'{res}/data/cobblemon/species_features/*.json')}
    assigned = {}
    for p in glob.glob(f'{res}/data/cobblemon/species_feature_assignments/*.json'):
        d = json.load(open(p))
        for sp in d.get('pokemon', []):
            assigned.setdefault(sp.split()[0], []).extend(d.get('features', []))
    for p in glob.glob(f'{res}/data/cobblemon/species/**/*.json', recursive=True):
        for f in json.load(open(p)).get('features', []):
            assigned.setdefault(os.path.basename(p)[:-5], []).append(f)
    return defs, {k: sorted(set(v)) for k, v in assigned.items()}


def aspect_of(defn, value):
    """The aspect a feature value gives (None if it gives none)."""
    if not defn.get('isAspect', defn.get('type') == 'flag'):
        return None
    if defn.get('type') == 'flag':
        return defn['keys'][0] if value is True else None
    fmt = defn.get('aspectFormat', '{{choice}}')
    return fmt.replace('{{choice}}', str(value))


def resolve(rs, aspects):
    """(model, texture) for a set of aspects, the way Cobblemon layers the variations."""
    model = texture = None
    for d in rs:
        for v in d['variations']:
            if set(v.get('aspects', [])) <= aspects:
                model = v.get('model', model)
                texture = v.get('texture', texture)
    return model, texture


def layers(rs, aspects):
    """The render layers' textures (first frames) for a set of aspects: a matching variation's layers replace the ones
    so far, as in Cobblemon; alpha eyes only show on alphas, which aren't looks here."""
    out = []
    for d in rs:
        for v in d['variations']:
            if set(v.get('aspects', [])) <= aspects and v.get('layers') is not None:
                out = [first_frame(l['texture']) for l in v['layers'] if l.get('name') != 'alpha_eyes' and l.get('texture')]
    return out


def poser(rs, aspects):
    """The poser a set of aspects uses (layered like the model)."""
    out = None
    for d in rs:
        for v in d['variations']:
            if set(v.get('aspects', [])) <= aspects:
                out = v.get('poser', out)
    return out


def key(model, texture):
    return f'{model}|{json.dumps(texture, sort_keys=True)}'


def looks(rs, groups):
    """[(name, aspects, model, texture)] for one species: one per distinct (model, texture).
    groups: lists of mutually exclusive aspects (a choice feature's values, or one flag) the resolvers use."""
    out, seen = [], set()
    for pick in itertools.product(*[[None] + g for g in groups]):
        aspects = {a for a in pick if a}
        model, texture = resolve(rs, aspects)
        if not model or not texture or key(model, texture) in seen:
            continue
        seen.add(key(model, texture))
        out.append((aspects, model, texture))
    out.sort(key=lambda x: (len(x[0]), sorted(x[0])))  # the base look first, then the simplest aspect sets
    species = rs[0]['species'].split(':')[1]
    return [('__'.join([species] + [slug(a) for a in sorted(aspects)]), sorted(aspects), model, texture)
            for aspects, model, texture in out]


def slug(aspect):
    """An aspect as part of a model name (BetterModel and Bedrock ids): a-z, 0-9 and _ (Unown's ! and ? spelled out)."""
    aspect = aspect.replace('!', 'exclamation').replace('?', 'question')
    return re.sub(r'[^a-z0-9]+', '_', aspect).strip('_')


def aspect_groups(rs, sp_features, defs):
    """The resolvers' aspects that change the model or texture, grouped: a choice feature's values exclude each other;
    shiny, gender and the species' flag features stand alone. Other aspects (paint and items a player applies) and
    layer-only ones (Spinda's spots) are skipped."""
    looks_aspects = {a for d in rs for v in d['variations'] if 'model' in v or 'texture' in v
                     for a in v.get('aspects', []) if not SKIP.match(a)}
    groups, placed = [], set()
    for f in sp_features:
        d = defs.get(f, {})
        if d.get('type') in ('choice', 'weighted_choice'):
            g = [a for a in (aspect_of(d, c) for c in d.get('choices', [])) if a in looks_aspects and a not in placed]
        elif d.get('type') == 'flag':
            g = [a for a in [aspect_of(d, True)] if a in looks_aspects and a not in placed]
        else:
            g = []
        if g:
            groups.append(g)
            placed.update(g)
    for g in (['male', 'female'], ['shiny']):
        g = [a for a in g if a in looks_aspects and a not in placed]
        if g:
            groups.append(g)
    return groups


def first_frame(texture):
    """Animated textures ({"frames": [...], "fps": ...}) -> their first frame."""
    return texture['frames'][0] if isinstance(texture, dict) else texture


_cache = {}


def cosmetics_for(res, species):
    if ('c', res) not in _cache:
        _cache[('c', res)] = cosmetics(res)
    return _cache[('c', res)].get(species, [])


def all_looks_for(res, species):
    if res not in _cache:
        _cache[res] = all_looks(res)
    return _cache[res].get(species, [])


def cosmetics(res):
    """{species: [aspect, ...]}: Cobblemon's cosmetic items (data/cobblemon/cosmetic_items) a species can wear."""
    out = {}
    for p in sorted(glob.glob(f'{res}/data/cobblemon/cosmetic_items/*.json')):
        d = json.load(open(p))
        for sp in d.get('pokemon', []):
            for c in d.get('cosmeticItems', []):
                for a in c.get('aspects', [])[:1]:  # color-white / colour-white: the same look
                    if a not in out.setdefault(sp.split()[0], []):
                        out[sp.split()[0]].append(a)
    return out


def cosmetic_looks(rs, wearable):
    """[(name, aspects, model, texture)] for a cosmetic worn on the base and shiny looks, where it changes the model,
    the texture or the layers."""
    species, out = rs[0]['species'].split(':')[1], []
    for a in wearable:
        for base in (set(), {'shiny'}):
            asp = base | {a}
            if resolve(rs, asp) == resolve(rs, base) and layers(rs, asp) == layers(rs, base):
                continue
            model, texture = resolve(rs, asp)
            if model and texture:
                out.append(('__'.join([species] + [slug(x) for x in sorted(asp)]), sorted(asp), model, texture))
    return out


def all_looks(res, with_cosmetics=True):
    """{species: [(name, aspects, model, texture)]} for every species with a model (cosmetic looks last)."""
    defs, assigned = features(res)
    wear = cosmetics(res) if with_cosmetics else {}
    out = {}
    for species, rs in resolvers(res).items():
        found = looks(rs, aspect_groups(rs, assigned.get(species, []), defs)) + cosmetic_looks(rs, wear.get(species, []))
        if found:
            out[species] = found
    return out


def demo():
    rs = [{'species': 'cobblemon:x', 'order': 0, 'variations': [
              {'aspects': [], 'model': 'm', 'texture': 't'}, {'aspects': ['shiny'], 'texture': 'ts'},
              {'aspects': ['female'], 'model': 'mf'}]},
          {'species': 'cobblemon:x', 'order': 1, 'variations': [
              {'aspects': ['alolan'], 'model': 'ma', 'texture': 'ta'}, {'aspects': ['alolan', 'shiny'], 'texture': 'tas'}]}]
    names = {n: (m, t) for n, _, m, t in looks(rs, [['female'], ['shiny'], ['alolan']])}
    assert names['x'] == ('m', 't') and names['x__shiny'] == ('m', 'ts')
    assert names['x__female__shiny'] == ('mf', 'ts') and names['x__alolan__shiny'] == ('ma', 'tas')
    assert resolve(rs, {'alolan', 'female'}) == ('ma', 'ta')  # the regional form's model wins (later order)
    rl = [{'species': 'cobblemon:c', 'variations': [
        {'aspects': [], 'layers': [{'name': 'flame', 'texture': {'frames': ['f1', 'f2']}}]},
        {'aspects': ['shiny'], 'layers': [{'name': 'flame', 'texture': 'sf'}, {'name': 'alpha_eyes', 'texture': 'a'}]}]}]
    assert layers(rl, set()) == ['f1'] and layers(rl, {'shiny'}) == ['sf']  # replaced, first frame, no alpha eyes
    assert aspect_of({'type': 'choice', 'isAspect': True, 'aspectFormat': '{{choice}}-sea'}, 'west') == 'west-sea'
    assert aspect_of({'type': 'flag', 'keys': ['alolan'], 'isAspect': True}, True) == 'alolan'
    rc = [{'species': 'cobblemon:sq', 'variations': [{'aspects': [], 'model': 'm', 'texture': 't'},
          {'aspects': ['cosmetic_item-black_glasses'], 'layers': [{'texture': 'g'}]}]}]
    assert [n for n, *_ in cosmetic_looks(rc, ['cosmetic_item-black_glasses', 'cosmetic_item-x'])] == \
        ['sq__cosmetic_item_black_glasses', 'sq__cosmetic_item_black_glasses__shiny']


if __name__ == '__main__':
    demo()
    print('ok')
