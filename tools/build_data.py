"""Cobblemon gameplay data -> what the cobblemon-link plugin reads.

usage: build_data.py COBBLEMON_RESOURCES OUT_DIR
  OUT_DIR/gamedata.json            species (stats, learnsets, evolutions, catch rate, ...), wild spawns, display names
  OUT_DIR/datapack/cobblemon-link  Cobblemon's biome tags, so Paper resolves #cobblemon:is_forest natively
Only species that have a converted model (OUT_DIR/java/<name>.bbmodel) are included.
"""
import glob, json, os, re, shutil, sys

sys.path.insert(0, os.path.dirname(__file__))
import looks

STATS = {'hp': 'hp', 'attack': 'atk', 'defence': 'def', 'special_attack': 'spa', 'special_defence': 'spd', 'speed': 'spe'}
# spawn conditions Spawner.java checks, as Cobblemon writes them; entries needing anything else (Poké Snacks, bait) are
# skipped
KNOWN_CONDITIONS = {'biomes', 'minSkyLight', 'maxSkyLight', 'canSeeSky', 'timeRange', 'minY', 'maxY', 'isRaining',
                    'isThundering', 'moonPhase', 'minLight', 'maxLight', 'structures', 'neededNearbyBlocks',
                    'neededBaseBlocks', 'isSlimeChunk', 'minX', 'maxX', 'minLureLevel', 'maxLureLevel', 'rodType'}
POSITIONS = {'grounded', 'submerged', 'surface', 'seafloor', 'fishing'}  # fishing: Poké Rods (Fishing.java)
PACK_FORMAT = 121  # Minecraft 26.3 data pack format


def fields(d):
    """The battle-relevant fields a species (or a form, which overrides only what it lists) defines."""
    out = {}
    if 'primaryType' in d:
        out['types'] = [t for t in (d.get('primaryType'), d.get('secondaryType')) if t]
    if 'baseStats' in d:
        out['baseStats'] = {STATS[k]: v for k, v in d['baseStats'].items()}
    if 'abilities' in d:
        out['abilities'] = [a for a in d['abilities'] if not a.startswith('h:')] or ['noability']
        out['hidden'] = next((a[2:] for a in d['abilities'] if a.startswith('h:')), None)  # the Ability Patch's
    if 'moves' in d:
        out['moves'] = sorted([int(lvl), move] for lvl, _, move in (m.partition(':') for m in d['moves']) if lvl.isdigit())
        for kind in ('tm', 'egg', 'tutor'):  # TMs teach these; egg and tutor: data only (Cobblemon 1.8 has no breeding or tutors)
            ms = sorted({m.split(':', 1)[1] for m in d['moves'] if m.startswith(kind + ':')})
            if ms:
                out[kind + 'Moves'] = ms
    if 'evolutions' in d:
        # ponytail: plain level-ups only; stones, trades, friendship etc. need items/UI that don't exist yet.
        # The result keeps its own form through the Pokémon's aspects (raichu alolan -> the evolution's alolan form)
        out['evolutions'] = [[e['requirements'][0]['minLevel'], e['result'].split()[0]] for e in d['evolutions']
                             if e.get('variant') == 'level_up' and len(e.get('requirements', [])) == 1
                             and e['requirements'][0].get('variant') == 'level']
        # every other evolution as Cobblemon wrote it (Evolutions.java checks the requirements): stones, friendship,
        # time, held items, trades...
        evos = [{'variant': e['variant'], 'result': e['result'], 'requirements': e.get('requirements', []),
                 **({'item': e['requiredContext'].split(':', 1)[-1]} if e.get('requiredContext') else {})}
                for e in d['evolutions'] if not (e.get('variant') == 'level_up' and len(e.get('requirements', [])) == 1
                                                 and e['requirements'][0].get('variant') == 'level')]
        if evos:
            out['evos'] = evos
    if 'evYield' in d:  # Gen 8 EV gain for defeating it
        out['evYield'] = {STATS[k]: v for k, v in d['evYield'].items() if v}
    if 'hitbox' in d:  # Cobblemon's alpha size: from the larger hitbox side times baseScale
        out['size'] = round(max(d['hitbox'].get('width', 1), d['hitbox'].get('height', 1)) * d.get('baseScale', 1), 3)
    r = d.get('riding')
    if r:  # Cobblemon 1.7+ riding: the ways it moves (LAND, AIR, LIQUID) and its SPEED stat's middle (Companions.ride)
        bs = r.get('behaviours') or ({'X': r['behaviour']} if r.get('behaviour') else {})
        kinds = sorted({k if k != 'X' else {'land': 'LAND', 'air': 'AIR', 'liquid': 'LIQUID'}.get(
            (v or {}).get('key', 'cobblemon:land/x').split(':')[-1].split('/')[0], 'LAND') for k, v in bs.items()})
        stats = r.get('stats') or next((v.get('stats') for v in bs.values() if v and v.get('stats')), {})
        sp = stats.get('SPEED', {})
        sp = sp.get('ranges', '40-60') if isinstance(sp, dict) else sp
        if isinstance(sp, dict):  # per behaviour: {"LAND": "25-40", ...}
            sp = next(iter(sp.values()), '40-60')
        lo, _, hi = str(sp).partition('-')
        if kinds:
            out['ride'] = {'kinds': kinds, 'speed': (int(float(lo)) + int(float(hi or lo))) // 2}
    if d.get('shoulderMountable'):
        out['shoulder'] = True  # Companions.shoulder
    if 'ultra_beast' in d.get('labels', []):
        out['ultraBeast'] = True  # Beast Ball
    for k, to in (('catchRate', 'catchRate'), ('maleRatio', 'maleRatio'), ('baseFriendship', 'baseFriendship'),
                  ('weight', 'weight')):  # weight in hectograms (Heavy Ball)
        if k in d:
            out[to] = d[k]
    return out


def species(res, modeled):
    out = {}
    for f in glob.glob(f'{res}/data/cobblemon/species/**/*.json', recursive=True):
        sid = os.path.basename(f)[:-5]
        d = json.load(open(f))
        if not d.get('implemented') or sid not in modeled:
            continue
        out[sid] = {'name': d['name'], 'catchRate': 45, 'maleRatio': 0.5, 'evolutions': [], 'moves': [],
                    'dex': d.get('nationalPokedexNumber', 0), 'pokedex': (d.get('pokedex') or [None])[0],
                    'baseExp': d.get('baseExperienceYield', 50), 'expGroup': d.get('experienceGroup', 'medium_fast'),
                    **fields(d)}
        # forms (regional and others): picked by the Pokémon's aspects; battle as Showdown's "<species><form>" id
        forms = [{'name': fd['name'], 'aspects': fd.get('aspects', []),
                  'showdown': sid + re.sub(r'[^a-z0-9]', '', fd['name'].lower()), **fields(fd)}
                 for fd in d.get('forms', []) if fd.get('aspects') and not fd.get('battleOnly')]
        if forms:
            out[sid]['forms'] = forms
    return out


def features(res, dex):
    """Species features (choice/flag/weighted_choice) that give aspects, and which species have which."""
    defs, assigned = looks.features(res)
    used = {sid: [f for f in assigned.get(sid, []) if defs.get(f, {}).get('type') in ('choice', 'weighted_choice', 'flag')]
            for sid in dex}
    for sid, fs in used.items():
        if fs:
            dex[sid]['features'] = fs
    keep = {f for fs in used.values() for f in fs}
    return {f: {k: defs[f][k] for k in ('type', 'keys', 'default', 'choices', 'isAspect', 'aspectFormat') if k in defs[f]}
            for f in sorted(keep)}


def species_looks(res, dex, modeled):
    """Per species, Cobblemon's resolver variations (aspects -> model / texture override, in order) and the converted
    model name for every (model, texture) pair: the plugin layers them exactly like VaryingModelRepository."""
    textures = []
    for sid, rs in looks.resolvers(res).items():
        if sid not in dex:
            continue
        var = []
        for d in rs:
            for v in d['variations']:
                if 'model' not in v and 'texture' not in v:
                    continue
                t = None
                if 'texture' in v:
                    tj = json.dumps(v['texture'], sort_keys=True)
                    if tj not in textures:
                        textures.append(tj)
                    t = textures.index(tj)
                var.append([v.get('aspects', []), v.get('model'), t])
        names, worn = {}, {}
        for name, aspects, model, texture in looks.all_looks_for(res, sid):
            if name not in modeled:
                continue
            c = [a for a in aspects if a != 'shiny' and a in looks.cosmetics_for(res, sid)]
            if c:  # a cosmetic look: worn -> {aspect: {look without it: look with it}}
                plain = '__'.join([sid] + [looks.slug(a) for a in aspects if a not in c])
                worn.setdefault(c[0], {})[plain] = name
                continue
            if json.dumps(texture, sort_keys=True) in textures:
                names[f'{model}|{textures.index(json.dumps(texture, sort_keys=True))}'] = name
        dex[sid]['looks'] = {'variations': var, 'models': names, **({'cosmetics': worn} if worn else {})}


def spawns(res, dex):
    out = []
    def rng(r, default='5-30'):
        lo, _, hi = str(r or default).partition('-')
        return [int(lo), int(hi or lo)]
    for f in sorted(glob.glob(f'{res}/data/cobblemon/spawn_pool_world/**/*.json', recursive=True)):  # herds/ too
        for s in json.load(open(f)).get('spawns', []):
            cond, anti = s.get('condition', {}), s.get('anticondition', {})
            herd = None
            if s.get('type') == 'pokemon-herd':  # Cobblemon's herds: a leader (an alpha in herds/) and followers
                herd = [{'species': h['pokemon'].split()[0], 'props': h['pokemon'].split()[1:], 'weight': h.get('weight', 1),
                         'level': rng(h.get('levelRange'), s.get('levelRange')), 'max': h.get('maxTimes', 1),
                         'leader': bool(h.get('isLeader'))} for h in s.get('herdablePokemon', []) if h['pokemon'].split()[0] in dex]
                leaders = [h for h in herd if h['leader']]
                words = [leaders[0]['species']] if leaders else []
            else:
                words = (s.get('pokemon') or '').split()
            sid = words[0] if words else ''
            if (s.get('type') not in ('pokemon', 'pokemon-herd') or s.get('spawnablePositionType') not in POSITIONS or sid not in dex
                    or set(cond) - KNOWN_CONDITIONS or set(anti) - KNOWN_CONDITIONS):
                continue
            props = [w for w in words[1:] if w != sid]  # form/feature properties: alolan, flower=white, region_bias=hisui
            out.append({'species': sid, 'bucket': s['bucket'], 'weight': s['weight'], **({'props': props} if props else {}),
                        'level': rng(s.get('level') or s.get('levelRange')), 'type': s['spawnablePositionType'], 'cond': cond,
                        **({'anti': anti} if anti else {}), **({'herd': herd, 'maxHerd': s.get('maxHerdSize', 4)} if herd else {})})
    return out


def pokedex_text(res, dex):
    """The species' first Pokédex entry in English (the starter screen shows it), replacing its lang key."""
    lang = json.load(open(f'{res}/assets/cobblemon/lang/en_us.json', encoding='utf-8'))
    for s in dex.values():
        key = s.pop('pokedex', None)
        if key:
            s['desc'] = lang.get(key, '')


def names(res, dex):
    """English display names for the move/ability/type ids the plugin shows (Cobblemon's en_us lang file)."""
    lang = json.load(open(f'{res}/assets/cobblemon/lang/en_us.json', encoding='utf-8'))
    used = {'move': {m for s in dex.values() for _, m in s['moves']},
            'ability': {a for s in dex.values() for a in s['abilities'] + [s.get('hidden')] if a},
            'type': {t for s in dex.values() for t in s['types']}}
    return {kind: {i: lang.get(f'cobblemon.{kind}.{i}', i) for i in sorted(ids)} for kind, ids in used.items()}


def datapack(res, out):
    dp = f'{out}/datapack/cobblemon-link'
    shutil.rmtree(dp, ignore_errors=True)
    shutil.copytree(f'{res}/data/cobblemon/tags/worldgen/biome', f'{dp}/data/cobblemon/tags/worldgen/biome')
    # the "Pokémon" dialog: Java's Quick Actions key (G) and a pause-menu button open it; its buttons are custom
    # clicks the plugin handles (CobblemonLink.onQuickAction), so nobody has to type a command
    button = lambda label, i: {'label': label, 'action': {'type': 'minecraft:custom', 'id': f'cobblemonlink:{i}'}}
    os.makedirs(f'{dp}/data/cobblemonlink/dialog', exist_ok=True)
    json.dump({'type': 'minecraft:multi_action', 'title': 'Pokémon', 'columns': 2, 'can_close_with_escape': True,
               'actions': [button('Send out / Recall lead', 'lead'), button('Party', 'party'),
                           button('PC', 'pc'), button('Back to battle', 'battle')]},
              open(f'{dp}/data/cobblemonlink/dialog/pokemon.json', 'w'), indent=1)
    for tag in ('quick_actions', 'pause_screen_additions'):
        os.makedirs(f'{dp}/data/minecraft/tags/dialog', exist_ok=True)
        json.dump({'values': ['cobblemonlink:pokemon']}, open(f'{dp}/data/minecraft/tags/dialog/{tag}.json', 'w'))
    json.dump({'pack': {'description': 'cobblemon-link: Cobblemon biome tags (MPL-2.0)',
                        'min_format': PACK_FORMAT, 'max_format': PACK_FORMAT + 100}},
              open(f'{dp}/pack.mcmeta', 'w'), indent=1)


def npcs(res, dex):
    """Cobblemon's NPC classes that battle (their own fields over their presets'): name, skill, party pool (Npcs.java)."""
    def load(kind, i):
        p = f'{res}/data/cobblemon/{kind}/{i.split(":")[-1]}.json'
        return json.load(open(p)) if os.path.exists(p) else {}
    out = {}
    for f in sorted(glob.glob(f'{res}/data/cobblemon/npcs/*.json')):
        d = {}
        for pr in json.load(open(f)).get('presets', []):
            d.update(load('npc_presets', pr))
        d.update(json.load(open(f)))
        party = d.get('party') or {}
        dialogue = (d.get('interaction') or {}).get('dialogue', '').split(':')[-1] or None
        if party.get('type') != 'pool' and not dialogue:
            continue  # ponytail: pool parties and dialogues (Cobblemon's own NPCs use these); simple/script parties later
        lv = lambda e: [int(x) for x in str(e.get('npcLevels') or e.get('level') or '5').split('-')][:2]
        pool = [{'species': e['pokemon'].split()[0], 'weight': e.get('weight', 1), 'times': e.get('selectableTimes', 1),
                 'levels': (lv(e) * 2)[:2]} for e in party.get('pool', []) if e['pokemon'].split()[0] in dex]
        out[os.path.basename(f)[:-5]] = {'name': (d.get('names') or [os.path.basename(f)[:-5]])[0], 'skill': d.get('skill', 0),
                                         'min': party.get('minPokemon', 1), 'max': party.get('maxPokemon', 6), 'pool': pool,
                                         **({'dialogue': dialogue} if dialogue else {})}
    return out


def cosmetic_items(res):
    """Cobblemon's cosmetic items: [{species: [...], items: {item or #tag: aspect}}] (Companions.cosmetic)."""
    out = []
    for f in sorted(glob.glob(f'{res}/data/cobblemon/cosmetic_items/*.json')):
        d = json.load(open(f))
        items = {}
        for c in d.get('cosmeticItems', []):
            i = c['consumedItem']
            items[i[10:] if i.startswith('cobblemon:') else i] = c['aspects'][0]
        out.append({'species': [s.split()[0] for s in d.get('pokemon', [])], 'items': items})
    return out


def baits(res):
    """Cobblemon's bait: item ("aguav_berry", "minecraft:golden_apple") -> its effects [{type, chance, value, sub}]."""
    out = {}
    for f in sorted(glob.glob(f'{res}/data/cobblemon/spawn_bait_effects/**/*.json', recursive=True)):
        d = json.load(open(f))
        item = d['item'][10:] if d['item'].startswith('cobblemon:') else d['item']
        out[item] = [{'type': e['type'].split(':')[-1], 'chance': e.get('chance', 1), 'value': e.get('value', 0),
                      **({'sub': e['subcategory'].split(':')[-1]} if e.get('subcategory') else {})} for e in d['effects']]
    return out


def marks(res):
    """Cobblemon's marks a wild Pokémon can carry: id -> {chance, group} (Rules.mark picks one)."""
    out = {}
    for f in sorted(glob.glob(f'{res}/data/cobblemon/marks/*.json')):
        d = json.load(open(f))
        if 'chance' in d:
            out[os.path.basename(f)[:-5]] = {'chance': d['chance'], 'group': d.get('group')}
    return out


def main(a):
    if len(a) != 2:
        sys.exit(__doc__)
    res, out = a
    modeled = {os.path.basename(p)[:-8] for p in glob.glob(f'{out}/java/*.bbmodel') + glob.glob(f'{out}/cosmetic/java/*.bbmodel')}
    dex = species(res, modeled)
    pokedex_text(res, dex)
    feats = features(res, dex)
    species_looks(res, dex, modeled)
    sp = spawns(res, dex)
    fossils = {json.load(open(f))['result']: [i.split(':')[1] for i in json.load(open(f))['fossils']]
               for f in glob.glob(f'{res}/data/cobblemon/fossils/*.json')}  # species -> fossils (Machines.java)
    json.dump({'species': dex, 'spawns': sp, 'features': feats, 'names': names(res, dex), 'fossils': fossils,
               'npcs': npcs(res, dex), 'dialogues': {os.path.basename(f)[:-5]: json.load(open(f))
                                                     for f in glob.glob(f'{res}/data/cobblemon/dialogues/*.json')},
               'marks': marks(res), 'markIcons': sorted(os.path.basename(p)[:-4] for p in glob.glob(f'{res}/assets/cobblemon/textures/gui/mark/*.png')), 'baits': baits(res), 'cosmetics': cosmetic_items(res)},
              open(f'{out}/gamedata.json', 'w'), separators=(',', ':'))
    datapack(res, out)
    print(len(dex), 'species,', len(sp), 'spawn entries,', len({s['species'] for s in sp}), 'spawnable species')


def demo():
    import tempfile
    with tempfile.TemporaryDirectory() as t:
        os.makedirs(f'{t}/data/cobblemon/species/g1'); os.makedirs(f'{t}/data/cobblemon/spawn_pool_world')
        json.dump({'implemented': True, 'name': 'Bulbasaur', 'primaryType': 'grass',
                   'baseStats': {k: 1 for k in STATS}, 'abilities': ['overgrow', 'h:chlorophyll'],
                   'moves': ['1:tackle', 'tm:cut', '3:vinewhip'],
                   'evolutions': [{'variant': 'level_up', 'result': 'ivysaur', 'requirements': [{'variant': 'level', 'minLevel': 16}]},
                                  {'variant': 'item_interact', 'result': 'x', 'requirements': []}]},
                  open(f'{t}/data/cobblemon/species/g1/bulbasaur.json', 'w'))
        json.dump({'spawns': [
            {'type': 'pokemon', 'pokemon': 'bulbasaur', 'spawnablePositionType': 'grounded', 'bucket': 'common',
             'weight': 2, 'level': '3-9', 'condition': {'biomes': ['#cobblemon:is_forest'], 'timeRange': 'day'}},
            {'type': 'pokemon', 'pokemon': 'bulbasaur', 'spawnablePositionType': 'grounded', 'bucket': 'common',
             'weight': 2, 'level': '3-9', 'condition': {'biomes': ['#x'], 'structures': ['y']}}]},
            open(f'{t}/data/cobblemon/spawn_pool_world/0001.json', 'w'))
        dex = species(t, {'bulbasaur'})
        b = dex['bulbasaur']
        assert b['abilities'] == ['overgrow'] and b['hidden'] == 'chlorophyll' and b['moves'] == [[1, 'tackle'], [3, 'vinewhip']]
        assert b['evolutions'] == [[16, 'ivysaur']] and b['baseStats']['spa'] == 1
        sp = spawns(t, dex)
        assert len(sp) == 2 and sp[0]['level'] == [3, 9] and sp[0]['cond']['timeRange'] == 'day'
        assert sp[1]['cond']['structures'] == ['y'] and sp[0]['type'] == 'grounded'
        os.makedirs(f'{t}/data/cobblemon/npcs'); os.makedirs(f'{t}/data/cobblemon/npc_presets')
        json.dump({'names': ['AI Man'], 'presets': ['cobblemon:p']}, open(f'{t}/data/cobblemon/npcs/ai.json', 'w'))
        json.dump({'skill': 5, 'party': {'type': 'pool', 'minPokemon': 2, 'pool': [{'pokemon': 'bulbasaur', 'npcLevels': '1-8'}, {'pokemon': 'nope'}]}},
                  open(f'{t}/data/cobblemon/npc_presets/p.json', 'w'))
        assert npcs(t, dex) == {'ai': {'name': 'AI Man', 'skill': 5, 'min': 2, 'max': 6,
                                       'pool': [{'species': 'bulbasaur', 'weight': 1, 'times': 1, 'levels': [1, 8]}]}}, npcs(t, dex)


if __name__ == '__main__':
    demo()
    main(sys.argv[1:])
