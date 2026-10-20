"""Cobblemon's items for the plugin: what each one does, and its art on both clients.

usage: build_items.py COBBLEMON_RESOURCES OUT_DIR
  OUT_DIR/items.json         id -> {name, kind, ...}: Items.java gives and uses them
  OUT_DIR/items-pack.zip     Java: assets/cobblemon/items/<id>.json with Cobblemon's own item models and textures
  OUT_DIR/items.mcpack       Bedrock: the icons
  OUT_DIR/recipes.json       Cobblemon's recipes the plugin can register (Recipes.java)
  OUT_DIR/geyser-items.json  Geyser custom mappings: a Java item with item_model cobblemon:<id> -> cobblemonlink:<id>
The Poké Ball itself stays in the UI packs (build_ui.py, build_bedrock_ui.py), which also give it its 3D model.
"""
import glob, hashlib, io, json, os, re, sys, uuid, zipfile

from PIL import Image

STATS = {'hp': 'hp', 'attack': 'atk', 'defence': 'def', 'special_attack': 'spa', 'special_defence': 'spd', 'speed': 'spe'}
# PokeBalls.kt, in order; the plugin's Items.ballBonus knows each one's catch modifier
BALLS = ['slate_ball', 'azure_ball', 'verdant_ball', 'roseate_ball', 'citrine_ball', 'great_ball', 'ultra_ball',
         'master_ball', 'safari_ball', 'fast_ball', 'level_ball', 'lure_ball', 'heavy_ball', 'love_ball', 'friend_ball',
         'moon_ball', 'sport_ball', 'net_ball', 'dive_ball', 'nest_ball', 'repeat_ball', 'timer_ball', 'luxury_ball',
         'premier_ball', 'dusk_ball', 'heal_ball', 'quick_ball', 'cherish_ball', 'park_ball', 'dream_ball', 'beast_ball',
         'ancient_poke_ball', 'ancient_citrine_ball', 'ancient_verdant_ball', 'ancient_azure_ball', 'ancient_roseate_ball',
         'ancient_slate_ball', 'ancient_ivory_ball', 'ancient_great_ball', 'ancient_ultra_ball', 'ancient_heavy_ball',
         'ancient_leaden_ball', 'ancient_gigaton_ball', 'ancient_feather_ball', 'ancient_wing_ball', 'ancient_jet_ball',
         'ancient_origin_ball']
PERSISTENT = ['par', 'brn', 'psn', 'tox', 'slp', 'frz']
# CobblemonItems.kt: the medicine, battle items, candies, vitamins and feathers, with the numbers their classes use
KINDS = {
    'potion': {'kind': 'potion', 'heal': 20}, 'super_potion': {'kind': 'potion', 'heal': 60},
    'hyper_potion': {'kind': 'potion', 'heal': 120}, 'max_potion': {'kind': 'potion', 'heal': -1},
    'full_restore': {'kind': 'potion', 'heal': -1, 'cure': PERSISTENT},
    'revive': {'kind': 'revive', 'ratio': 0.5}, 'max_revive': {'kind': 'revive', 'ratio': 1.0},
    'full_heal': {'kind': 'cure', 'cure': PERSISTENT}, 'antidote': {'kind': 'cure', 'cure': ['psn', 'tox']},
    'awakening': {'kind': 'cure', 'cure': ['slp']}, 'burn_heal': {'kind': 'cure', 'cure': ['brn']},
    'ice_heal': {'kind': 'cure', 'cure': ['frz']}, 'paralyze_heal': {'kind': 'cure', 'cure': ['par']},
    'ether': {'kind': 'ether', 'pp': 10}, 'max_ether': {'kind': 'ether', 'pp': -1},
    'elixir': {'kind': 'elixir', 'pp': 10}, 'max_elixir': {'kind': 'elixir', 'pp': -1},
    'x_attack': {'kind': 'x_stat', 'stat': 'atk'}, 'x_defence': {'kind': 'x_stat', 'stat': 'def'},
    'x_special_attack': {'kind': 'x_stat', 'stat': 'spa'}, 'x_special_defence': {'kind': 'x_stat', 'stat': 'spd'},
    'x_speed': {'kind': 'x_stat', 'stat': 'spe'}, 'x_accuracy': {'kind': 'x_stat', 'stat': 'accuracy'},
    'dire_hit': {'kind': 'dire_hit'}, 'guard_spec': {'kind': 'guard_spec'},
    'rare_candy': {'kind': 'candy', 'exp': -1}, 'exp_candy_xs': {'kind': 'candy', 'exp': 100},
    'exp_candy_s': {'kind': 'candy', 'exp': 800}, 'exp_candy_m': {'kind': 'candy', 'exp': 3000},
    'exp_candy_l': {'kind': 'candy', 'exp': 10000}, 'exp_candy_xl': {'kind': 'candy', 'exp': 30000},
    **{v: {'kind': 'ev', 'stat': s, 'amount': 10} for v, s in (('hp_up', 'hp'), ('protein', 'atk'), ('iron', 'def'),
                                                              ('calcium', 'spa'), ('zinc', 'spd'), ('carbos', 'spe'))},
    **{f: {'kind': 'ev', 'stat': s, 'amount': 1} for f, s in (('health_feather', 'hp'), ('muscle_feather', 'atk'),
       ('resist_feather', 'def'), ('genius_feather', 'spa'), ('clever_feather', 'spd'), ('swift_feather', 'spe'))},
    'ability_capsule': {'kind': 'ability_capsule'}, 'ability_patch': {'kind': 'ability_patch'},
    # berries fed out of battle (Cobblemon's berry items); held otherwise, like any held item
    'oran_berry': {'kind': 'berry', 'heal': 10}, 'berry_juice': {'kind': 'berry', 'heal': 20},
    'sitrus_berry': {'kind': 'berry', 'ratio': 0.25},
    'cheri_berry': {'kind': 'berry', 'cure': ['par']}, 'chesto_berry': {'kind': 'berry', 'cure': ['slp']},
    'pecha_berry': {'kind': 'berry', 'cure': ['psn', 'tox']}, 'rawst_berry': {'kind': 'berry', 'cure': ['brn']},
    'aspear_berry': {'kind': 'berry', 'cure': ['frz']}, 'lum_berry': {'kind': 'berry', 'cure': PERSISTENT},
    'leppa_berry': {'kind': 'berry', 'pp': 10},
    **{b: {'kind': 'berry', 'stat': s, 'amount': -10} for b, s in (('pomeg_berry', 'hp'), ('kelpsy_berry', 'atk'),
       ('qualot_berry', 'def'), ('hondew_berry', 'spa'), ('grepa_berry', 'spd'), ('tamato_berry', 'spe'))},
}
MACHINES = ['healing_machine', 'pc', 'pasture', 'restoration_tank', 'fossil_analyzer', 'tm_machine', 'display_case']  # Machines.java's blocks
NATURES = ['hardy', 'lonely', 'brave', 'adamant', 'naughty', 'bold', 'docile', 'relaxed', 'impish', 'lax', 'timid',
           'hasty', 'serious', 'jolly', 'naive', 'modest', 'mild', 'quiet', 'bashful', 'rash', 'calm', 'gentle', 'sassy',
           'careful', 'quirky']


def evolution_items(res):
    """Items an item_interact evolution asks for (stones, Link Cable, apples...)."""
    out = set()
    for f in glob.glob(f'{res}/data/cobblemon/species/**/*.json', recursive=True):
        for e in json.load(open(f)).get('evolutions', []):
            ctx = e.get('requiredContext') or ''
            if e.get('variant') == 'item_interact' and ctx.startswith('cobblemon:'):
                out.add(ctx.split(':', 1)[1])
    return out


def showdown_items(res):
    """Item ids Cobblemon's Showdown knows (data/items.js in showdown.zip): those work when a Pokémon holds them."""
    path = f'{res}/data/cobblemon/showdown.zip'
    if not os.path.exists(path):
        return set()
    with zipfile.ZipFile(path) as z:
        return set(re.findall(r'^  ([a-z0-9]+): \{', z.read('data/items.js').decode(), re.M))


def table(res):
    lang = json.load(open(f'{res}/assets/cobblemon/lang/en_us.json', encoding='utf-8'))
    items = {b: {'kind': 'ball'} for b in BALLS}
    items.update(KINDS)
    items.update({f'{n}_mint': {'kind': 'mint', 'nature': n} for n in NATURES if os.path.exists(
        f'{res}/assets/cobblemon/models/item/{n}_mint.json')})
    for f in glob.glob(f'{res}/assets/cobblemon/models/item/*_rod.json'):  # Poké Rods (fishing rods with a ball)
        items[os.path.basename(f)[:-5]] = {'kind': 'rod'}
    for i in evolution_items(res):
        items.setdefault(i, {'kind': 'evolution'})
    for m in MACHINES:  # placed as Machines.java's blocks
        items[m] = {'kind': 'machine'}
    for f in glob.glob(f'{res}/data/cobblemon/fossils/*.json'):  # into the restoration tank
        for i in json.load(open(f))['fossils']:
            items[i.split(':')[1]] = {'kind': 'fossil'}
    # held items: anything else Showdown knows (Leftovers, Choice Band, berries, gems...); fossils wait for their machine
    sd = showdown_items(res) | {'luckyegg', 'expshare', 'everstone', 'soothebell', 'amuletcoin', 'smokeball',
                                'cleansetag'}  # Cobblemon's own held items (EXP, evolution, friendship)
    for f in glob.glob(f'{res}/assets/cobblemon/models/item/*.json'):
        i = os.path.basename(f)[:-5]
        if i.replace('_', '') in sd and 'fossil' not in i:  # fossils have their own kind
            items.setdefault(i, {'kind': 'held'})
    global RECIPES
    RECIPES = recipes(res, items)
    out = {}
    for i, d in sorted(items.items()):
        if not os.path.exists(f'{res}/assets/cobblemon/models/item/{i}.json'):
            continue
        out[i] = {'name': lang.get(f'item.cobblemon.{i}', lang.get(f'block.cobblemon.{i}', i)), **d}
    return out


RECIPE_TYPES = {'minecraft:crafting_shaped': 'shaped', 'minecraft:crafting_shapeless': 'shapeless',
                'minecraft:smelting': 'smelting', 'minecraft:blasting': 'blasting',
                'minecraft:smithing_transform': 'smithing', 'cobblemon:brewing_stand': 'brewing'}


def recipes(res, items):
    """Cobblemon's crafting, furnace, smithing and brewing recipes whose ingredients are vanilla or Cobblemon items
    with a model; their Cobblemon ingredients join the table as materials. The plugin registers them (Recipes)."""
    def tag(t, seen=()):  # a Cobblemon item tag, flattened (vanilla and c: entries stay as they are)
        path = f'{res}/data/cobblemon/tags/item/{t[11:]}.json'
        if not os.path.exists(path) or t in seen:
            return [None]
        vals = [v if isinstance(v, str) else v.get('id') for v in json.load(open(path))['values']]
        return [i for v in vals for i in (tag(v, seen + (t,)) if str(v).startswith('#cobblemon:') else ids(v))]

    def common(t):  # Fabric/NeoForge "c:" tags the recipes use, as the vanilla item (Paper has no c: tags)
        t = t[2:]  # less "c:"
        kind, _, what = t.partition('/')
        special = {'gems/amethyst': 'amethyst_shard', 'gems/lapis': 'lapis_lazuli', 'dusts/redstone': 'redstone',
                   'strings': 'string', 'leathers': 'leather', 'feathers': 'feather', 'slime_balls': 'slime_ball',
                   'crops/wheat': 'wheat', 'chests/wooden': 'chest', 'drinks/milk': 'milk_bucket',
                   'fertilizers': 'bone_meal', 'chains': 'iron_chain'}
        if t in special:
            return 'minecraft:' + special[t]
        suffix = {'ingots': '_ingot', 'nuggets': '_nugget', 'gems': '', 'dyes': '_dye'}.get(kind)
        return None if suffix is None or not what else f'minecraft:{what}{suffix}'

    def ids(x):
        if isinstance(x, list):
            return [i for y in x for i in ids(y)]
        if isinstance(x, dict):
            x = x.get('item') or x.get('id') or ('#' + x['tag'] if 'tag' in x else None)
        if isinstance(x, str) and x.startswith(('#c:', 'c:')):
            return [common(x.lstrip('#'))]
        if isinstance(x, str) and x.startswith('#cobblemon:'):
            return tag(x)
        return [x] if isinstance(x, str) else [None]

    def has_model(i):  # an item, not a block (blocks would need placing) or a boat/sign/door
        path = f'{res}/assets/cobblemon/models/item/{i}.json'
        if i in MACHINES:
            return os.path.exists(path)
        if not os.path.exists(path) or re.search(r'(boat|sign|door|fence|button|pressure_plate|_block|tm_machine|display_case)$', i):
            return False
        return not json.load(open(path)).get('parent', '').startswith('cobblemon:block')
    out = []
    for f in sorted(glob.glob(f'{res}/data/cobblemon/recipe/*.json') + glob.glob(f'{res}/data/cobblemon/recipe/brewing_stand/*.json')):
        d = json.load(open(f))
        kind = RECIPE_TYPES.get(d.get('type'))
        r = d.get('result')
        rid, count = (r.get('id') or r.get('item'), r.get('count', 1)) if isinstance(r, dict) else (r, 1)
        if not kind or not isinstance(rid, str):
            continue
        parts = {'key': d.get('key', {}), 'ingredients': d.get('ingredients', []),
                 **{k: d[k] for k in ('ingredient', 'base', 'addition', 'template', 'input', 'bottle') if k in d}}
        used = [i for v in parts.values() for i in (ids(list(v.values())) if isinstance(v, dict) else ids(v))] + [rid]
        if any(i is None or i.startswith('#c:') or i.startswith('#cobblemon') or not i.startswith(('minecraft:', '#minecraft:', 'cobblemon:'))
               or i.startswith('cobblemon:') and not has_model(i[10:]) for i in used):
            continue
        for i in used:
            if i.startswith('cobblemon:') and i[10:] != 'poke_ball':
                items.setdefault(i[10:], {'kind': 'material'})
        norm = lambda v: [x for x in ids(v)]
        out.append({'id': os.path.basename(f)[:-5], 'type': kind, 'result': rid, 'count': count,
                    **({'pattern': d['pattern']} if 'pattern' in d else {}),
                    **({'key': {k: norm(v) for k, v in d['key'].items()}} if 'key' in d else {}),
                    **({'ingredients': [norm(v) for v in d['ingredients']]} if 'ingredients' in d else {}),
                    **{k: norm(d[k]) for k in ('ingredient', 'base', 'addition', 'template', 'input', 'bottle') if k in d},
                    **({'time': d['cookingtime']} if 'cookingtime' in d else {}), **({'xp': d['experience']} if 'experience' in d else {})})
    return out


def model_files(res, mid, files):
    """Copy model cobblemon:item/<mid> and, through its parents, every Cobblemon texture it draws. Returns layer0."""
    path = f'{res}/assets/cobblemon/models/{mid}.json'
    if not os.path.exists(path):
        return None
    m = json.load(open(path))
    if 'particle' not in m.get('textures', {}) and 'layer0' in m.get('textures', {}):
        m.setdefault('textures', {})['particle'] = m['textures']['layer0']  # the client warns without one
    files[f'assets/cobblemon/models/{mid}.json'] = json.dumps(m).encode()
    first = None
    for t in m.get('textures', {}).values():
        if isinstance(t, str) and t.startswith('cobblemon:'):
            p = f'{res}/assets/cobblemon/textures/{t[10:]}.png'
            if os.path.exists(p):
                files[f'assets/cobblemon/textures/{t[10:]}.png'] = open(p, 'rb').read()
                if os.path.exists(p + '.mcmeta'):
                    files[f'assets/cobblemon/textures/{t[10:]}.png.mcmeta'] = open(p + '.mcmeta', 'rb').read()
                first = first or p
    parent = m.get('parent', '')
    if parent.startswith('cobblemon:'):
        first = model_files(res, parent[10:], files) or first
    return first


def write_zip(path, files):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):  # fixed order and timestamps: same input, same zip, same SHA-1
            z.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), files[name])


def build(res, out):
    items = table(res)
    java, bedrock, textures, icons = {}, {}, {}, {}
    model = lambda m: {'type': 'minecraft:model', 'model': m}
    for i in items:
        icons[i] = model_files(res, f'item/{i}', java)
        d = model('cobblemon:item/' + i)
        if items[i]['kind'] == 'ball' and model_files(res, f'item/{i}_model', java):
            # in flight (a thrown item renders as "ground"): Cobblemon's 3D ball, like the Poké Ball's
            d = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                 'cases': [{'when': 'ground', 'model': model(f'cobblemon:item/{i}_model')}], 'fallback': d}
        if items[i]['kind'] == 'rod' and model_files(res, f'item/{i}_cast', java):  # the line out: Cobblemon's cast model
            d = {'type': 'minecraft:condition', 'property': 'minecraft:fishing_rod/cast',
                 'on_true': model(f'cobblemon:item/{i}_cast'), 'on_false': d}
        java[f'assets/cobblemon/items/{i}.json'] = json.dumps({'model': d}).encode()
        if icons[i]:
            buf = io.BytesIO()
            Image.open(icons[i]).convert('RGBA').save(buf, 'PNG', optimize=True)
            bedrock[f'textures/items/cobblemon/{i}.png'] = buf.getvalue()
            textures[f'cobblemonlink:{i}'] = {'textures': [f'textures/items/cobblemon/{i}']}
    java['pack.mcmeta'] = json.dumps({'pack': {'description': "Cobblemon's items (Cobblemon art, CC BY-NC 3.0)",
                                               'pack_format': 97, 'supported_formats': [46, 999],
                                               'min_format': 46, 'max_format': 999}}).encode()
    # TMs (Cobblemon's tms/*.json): one item model per type, the move is on the item (Items.tm)
    tms = {}
    for f in sorted(glob.glob(f'{res}/data/cobblemon/tms/*.json')):
        d = json.load(open(f))
        tms[d['moveName']] = {'type': d['type'], 'recipe': [[r['item'] if 'item' in r else '#' + r['tag'], r.get('count', 1)] for r in d.get('recipe', [])]}
    for t in sorted({v['type'] for v in tms.values()}):
        tex = f'{res}/assets/cobblemon/textures/item/tms/{t}_tm.png'
        if not os.path.exists(tex):
            continue
        java[f'assets/cobblemon/textures/item/tms/{t}_tm.png'] = open(tex, 'rb').read()
        java[f'assets/cobblemon/models/item/tm_{t}.json'] = json.dumps({'parent': 'minecraft:item/generated',
                                                                         'textures': {'layer0': f'cobblemon:item/tms/{t}_tm'}}).encode()
        java[f'assets/cobblemon/items/tm_{t}.json'] = json.dumps({'model': model(f'cobblemon:item/tm_{t}')}).encode()
        bedrock[f'textures/items/cobblemon/tm_{t}.png'] = open(tex, 'rb').read()
        textures[f'cobblemonlink:tm_{t}'] = {'textures': [f'textures/items/cobblemon/tm_{t}']}
        icons[f'tm_{t}'] = tex
    json.dump(tms, open(f'{out}/tms.json', 'w'), indent=1)
    # the chest's "Inventory" label is drawn over the plugin's screens (build_ui.py blanks it in en_us): in every
    # language Cobblemon has, too
    for f in glob.glob(f'{res}/assets/cobblemon/lang/*.json'):
        java[f'assets/minecraft/lang/{os.path.basename(f)}'] = json.dumps({'container.inventory': ''}).encode()
    write_zip(f'{out}/items-pack.zip', java)

    bedrock['textures/item_texture.json'] = json.dumps({'resource_pack_name': 'cobblemon-link items',
                                                        'texture_name': 'atlas.items', 'texture_data': textures}).encode()
    digest = hashlib.sha1(b''.join(k.encode() + bedrock[k] for k in sorted(bedrock))).digest()
    ver = [1, 0, int.from_bytes(digest[:3]) % 32768]  # Bedrock refuses a version part over 65535
    ids = [str(uuid.uuid5(uuid.NAMESPACE_URL, f'cobblemon-link/items/{n}')) for n in ('header', 'resources')]
    bedrock['manifest.json'] = json.dumps({'format_version': 2, 'header': {
        'name': 'cobblemon-link items', 'description': "Cobblemon's item icons (CC BY-NC 3.0)", 'uuid': ids[0],
        'version': ver, 'min_engine_version': [1, 21, 0]},
        'modules': [{'type': 'resources', 'uuid': ids[1], 'version': ver}]}).encode()
    write_zip(f'{out}/items.mcpack', bedrock)

    mapping = lambda i: {'type': 'definition', 'model': f'cobblemon:{i}', 'bedrock_identifier': f'cobblemonlink:{i}',
                         'display_name': items[i]['name'], 'bedrock_options': {'icon': f'cobblemonlink:{i}'}}
    base = lambda i: {'ball': 'minecraft:snowball', 'rod': 'minecraft:fishing_rod'}.get(items[i]['kind'], 'minecraft:paper')  # Items.java's materials
    maps = {}
    for i in items:
        if icons[i]:
            maps.setdefault(base(i), []).append(mapping(i))
    for i in icons:  # the TM models
        if i.startswith('tm_'):
            maps.setdefault('minecraft:paper', []).append({'type': 'definition', 'model': f'cobblemon:{i}', 'bedrock_identifier': f'cobblemonlink:{i}',
                                                           'display_name': 'TM', 'bedrock_options': {'icon': f'cobblemonlink:{i}'}})
    json.dump({'format_version': 2, 'items': maps}, open(f'{out}/geyser-items.json', 'w'), indent=1)
    json.dump(items, open(f'{out}/items.json', 'w'), indent=1, ensure_ascii=False)
    json.dump(RECIPES, open(f'{out}/recipes.json', 'w'), indent=1)
    return items


def demo():
    import tempfile
    with tempfile.TemporaryDirectory() as t:
        for d in ('assets/cobblemon/models/item', 'assets/cobblemon/textures/item/medicine', 'assets/cobblemon/lang',
                  'data/cobblemon/species/g1'):
            os.makedirs(f'{t}/{d}')
        json.dump({'item.cobblemon.potion': 'Potion'}, open(f'{t}/assets/cobblemon/lang/en_us.json', 'w'))
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'cobblemon:item/medicine/potion'}},
                  open(f'{t}/assets/cobblemon/models/item/potion.json', 'w'))
        Image.new('RGBA', (16, 16)).save(f'{t}/assets/cobblemon/textures/item/medicine/potion.png')
        json.dump({'evolutions': [{'variant': 'item_interact', 'requiredContext': 'cobblemon:fire_stone'}]},
                  open(f'{t}/data/cobblemon/species/g1/vulpix.json', 'w'))
        items = build(t, t)
        assert items == {'potion': {'name': 'Potion', 'kind': 'potion', 'heal': 20}}, items  # no model: no fire stone
        z = zipfile.ZipFile(f'{t}/items-pack.zip')
        assert 'assets/cobblemon/textures/item/medicine/potion.png' in z.namelist()
        assert json.load(open(f'{t}/geyser-items.json'))['items']['minecraft:paper'][0]['model'] == 'cobblemon:potion'


if __name__ == '__main__':
    demo()
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    n = build(*sys.argv[1:])
    print(len(n), 'items ->', sys.argv[2])
