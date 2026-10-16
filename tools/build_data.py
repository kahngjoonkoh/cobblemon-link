"""Cobblemon gameplay data -> what the cobblemon-link plugin reads.

usage: build_data.py COBBLEMON_RESOURCES OUT_DIR
  OUT_DIR/gamedata.json            species (stats, learnsets, evolutions, catch rate, ...), wild spawns, display names
  OUT_DIR/datapack/cobblemon-link  Cobblemon's biome tags, so Paper resolves #cobblemon:is_forest natively
Only species that have a converted model (OUT_DIR/java/<name>.bbmodel) are included.
"""
import glob, json, os, shutil, sys

STATS = {'hp': 'hp', 'attack': 'atk', 'defence': 'def', 'special_attack': 'spa', 'special_defence': 'spd', 'speed': 'spe'}
# spawn conditions the plugin checks; entries needing anything else (structures, snacks, fishing...) are skipped
KNOWN_CONDITIONS = {'biomes', 'minSkyLight', 'maxSkyLight', 'canSeeSky', 'timeRange', 'minY', 'maxY', 'isRaining'}
PACK_FORMAT = 121  # Minecraft 26.3 data pack format


def species(res, modeled):
    out = {}
    for f in glob.glob(f'{res}/data/cobblemon/species/**/*.json', recursive=True):
        sid = os.path.basename(f)[:-5]
        d = json.load(open(f))
        if not d.get('implemented') or sid not in modeled:
            continue
        moves = []
        for m in d.get('moves', []):
            lvl, _, move = m.partition(':')
            if lvl.isdigit():
                moves.append([int(lvl), move])
        evos = []
        for e in d.get('evolutions', []):
            req = e.get('requirements', [])
            # ponytail: plain level-ups only; stones, trades, friendship etc. need items/UI that don't exist yet
            if e.get('variant') == 'level_up' and len(req) == 1 and req[0].get('variant') == 'level':
                evos.append([req[0]['minLevel'], e['result'].split()[0]])
        out[sid] = {
            'name': d['name'],
            'types': [t for t in (d.get('primaryType'), d.get('secondaryType')) if t],
            'baseStats': {STATS[k]: v for k, v in d['baseStats'].items()},
            'abilities': [a for a in d.get('abilities', []) if not a.startswith('h:')] or ['noability'],
            'catchRate': d.get('catchRate', 45),
            'baseExp': d.get('baseExperienceYield', 50),
            'expGroup': d.get('experienceGroup', 'medium_fast'),
            'maleRatio': d.get('maleRatio', 0.5),
            'moves': sorted(moves),
            'evolutions': evos,
        }
    return out


def spawns(res, dex):
    out = []
    for f in sorted(glob.glob(f'{res}/data/cobblemon/spawn_pool_world/*.json')):
        for s in json.load(open(f)).get('spawns', []):
            sid = ''.join((s.get('pokemon') or '').split()[:1])  # drop form aspects (region_bias=alola)
            cond, anti = s.get('condition', {}), s.get('anticondition', {})
            if (s.get('type') != 'pokemon' or s.get('spawnablePositionType') != 'grounded' or sid not in dex
                    or set(cond) - KNOWN_CONDITIONS or set(anti) - {'biomes'} or not cond.get('biomes')):
                continue
            lo, _, hi = str(s.get('level', '5-30')).partition('-')
            out.append({'species': sid, 'bucket': s['bucket'], 'weight': s['weight'],
                        'level': [int(lo), int(hi or lo)], 'biomes': cond['biomes'],
                        'antiBiomes': anti.get('biomes', []),
                        **{k: cond[k] for k in KNOWN_CONDITIONS - {'biomes'} if k in cond}})
    return out


def names(res, dex):
    """English display names for the move/ability/type ids the plugin shows (Cobblemon's en_us lang file)."""
    lang = json.load(open(f'{res}/assets/cobblemon/lang/en_us.json', encoding='utf-8'))
    used = {'move': {m for s in dex.values() for _, m in s['moves']},
            'ability': {a for s in dex.values() for a in s['abilities']},
            'type': {t for s in dex.values() for t in s['types']}}
    return {kind: {i: lang.get(f'cobblemon.{kind}.{i}', i) for i in sorted(ids)} for kind, ids in used.items()}


def datapack(res, out):
    dp = f'{out}/datapack/cobblemon-link'
    shutil.rmtree(dp, ignore_errors=True)
    shutil.copytree(f'{res}/data/cobblemon/tags/worldgen/biome', f'{dp}/data/cobblemon/tags/worldgen/biome')
    json.dump({'pack': {'description': 'cobblemon-link: Cobblemon biome tags (MPL-2.0)',
                        'min_format': PACK_FORMAT, 'max_format': PACK_FORMAT + 100}},
              open(f'{dp}/pack.mcmeta', 'w'), indent=1)


def main(a):
    if len(a) != 2:
        sys.exit(__doc__)
    res, out = a
    modeled = {os.path.basename(p)[:-8] for p in glob.glob(f'{out}/java/*.bbmodel')}
    dex = species(res, modeled)
    sp = spawns(res, dex)
    json.dump({'species': dex, 'spawns': sp, 'names': names(res, dex)}, open(f'{out}/gamedata.json', 'w'),
              separators=(',', ':'))
    datapack(res, out)
    print(len(dex), 'species,', len(sp), 'grounded spawn entries,', len({s['species'] for s in sp}), 'spawnable species')


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
        assert b['abilities'] == ['overgrow'] and b['moves'] == [[1, 'tackle'], [3, 'vinewhip']]
        assert b['evolutions'] == [[16, 'ivysaur']] and b['baseStats']['spa'] == 1
        sp = spawns(t, dex)
        assert len(sp) == 1 and sp[0]['level'] == [3, 9] and sp[0]['timeRange'] == 'day'  # structure entry skipped


if __name__ == '__main__':
    demo()
    main(sys.argv[1:])
