"""Starter preview cards: the left half of Cobblemon's starter screen (StarterSelectionScreen), one per starter.

usage: build_cards.py COBBLEMON_RESOURCES OUT_DIR   (OUT_DIR/java/*.bbmodel + species.tsv in; out: OUT_DIR/cards/<name>.png
(Java's: StarterScreen's narrower panel), cards_full/<name>.png (Bedrock's: Cobblemon's own) and tiles/<name>.png (the
list's tile picture, CategoryList's framing))
Drawn in Cobblemon's order and at its positions: window background, the Poké Ball pattern (first frame), the white
and the type platforms, the model (portrait.profile: the poser's profile framing), the panel frame, the type icons.
Cropped to the window and the type row, at K px per GUI pixel. build_ui.py turns them into font glyphs.
ponytail: still pictures (the screen animates the model and scrolls the pattern); names and the Pokédex text are
drawn by the client as text (StarterScreen.java).
"""
import base64, glob, json, os, re, sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import looks
import portrait
from build_ui import STARTER_LEFT_CUTS, STARTER_LEFT_ROWS, cut_columns, cut_rows, nine

# CobblemonLink.STARTERS, in the same order (Cobblemon's starter categories, Kanto to Paldea)
STARTERS = ['bulbasaur', 'charmander', 'squirtle', 'chikorita', 'cyndaquil', 'totodile', 'treecko', 'torchic', 'mudkip',
            'turtwig', 'chimchar', 'piplup', 'snivy', 'tepig', 'oshawott', 'chespin', 'fennekin', 'froakie', 'rowlet',
            'litten', 'popplio', 'grookey', 'scorbunny', 'sobble', 'sprigatito', 'fuecoco', 'quaxly']
TYPES = ['normal', 'fire', 'water', 'grass', 'electric', 'ice', 'fighting', 'poison', 'ground', 'flying', 'psychic',
         'bug', 'rock', 'ghost', 'dragon', 'dark', 'steel', 'fairy']  # types.png order (textureXMultiplier)
CROP = (1, 13, 129, 138)  # x0, y0, x1, y1 of the 239x197 panel: the window, its frame and the type row
K = 2  # px per GUI px: 256 x 250, the most a font glyph may be (the client packs glyphs on 256 px pages)
KT_SCALE = re.compile(r"profileScale\s*=\s*([-\d.]+)F?")
KT_TRANSLATION = re.compile(r"profileTranslation\s*=\s*Vec3\(\s*([-\d.]+),\s*([-\d.]+),\s*([-\d.]+)\s*\)")


def profile_framing(res, poser_name):
    p = glob.glob(f'{res}/assets/cobblemon/bedrock/pokemon/posers/**/{poser_name}.json', recursive=True)
    if p:
        d = json.load(open(p[0]))
        return d.get('profileScale', 1.0), d.get('profileTranslation', [0.0, 0.0, 0.0])
    for kt in glob.glob(f'{res}/com/**/*Model.kt', recursive=True):
        if os.path.basename(kt)[:-8].lower() == poser_name:
            src = open(kt).read()
            s, t = KT_SCALE.search(src), KT_TRANSLATION.search(src)
            return float(s.group(1)) if s else 1.0, [float(x) for x in t.groups()] if t else [0.0, 0.0, 0.0]
    return 1.0, [0.0, 0.0, 0.0]


def card(gui, bb, png, framing, base, types, narrow=False):
    """Cobblemon's card; narrow: the left half without build_ui.STARTER_LEFT_CUTS (StarterScreen.java's panel), its
    window 13 px narrower: the Poké Ball pattern cropped evenly, the platforms shortened in the middle, the model
    centred. Cropped to the window and the type row."""
    S = lambda img: img.resize((img.width * K, img.height * K), Image.NEAREST)  # GUI px -> PX detail
    sel = f'{gui}/starterselection'
    o = lambda n: Image.open(f'{sel}/{n}.png').convert('RGBA')
    cut = sum(n for _, n in STARTER_LEFT_CUTS) if narrow else 0
    ww = 118 - cut  # the window's width
    win = Image.new('RGBA', (ww * K, 100 * K))
    at = lambda img, x, y: win.alpha_composite(S(img), (round(x * K), round(y * K)))
    at(o('background').crop((cut // 2, 0, cut // 2 + ww, 100)), 0, 0)
    at(o('background_poke_ball').crop((0, 0, 109, 109)), 4.5 - cut / 2, -4.5)
    plat = lambda n: nine(o(n), 113 - cut, o(n).height, 12) if narrow else o(n)
    at(plat('platform_base'), 2.5, 71)
    at(plat(f'starter_platform_base_{types[0]}'), 2.5, 65)
    win.alpha_composite(portrait.profile(bb, png, *framing, base, K, size=(ww, 100), origin=(ww / 2, -12)))
    panel = Image.new('RGBA', (239 * K, 197 * K))
    panel.alpha_composite(win, (6 * K, 17 * K))
    if narrow:  # the frame cut as StarterScreen's panel is (its rows are all below the window)
        frame = cut_columns(cut_rows(o('base'), STARTER_LEFT_ROWS), STARTER_LEFT_CUTS)
    else:
        frame = o('base')
    panel.alpha_composite(S(frame))
    mid = 65 - (sum(n for x, n in STARTER_LEFT_CUTS if x < 65) if narrow else 0)  # the type row's middle
    top = 120 - (sum(1 for r in STARTER_LEFT_ROWS if r < 120) if narrow else 0)  # the icons' top
    sheet = Image.open(f'{gui}/types.png').convert('RGBA')  # 36 px icons at scale 0.5 (TypeIcon)
    icon = lambda t: sheet.crop((36 * TYPES.index(t), 0, 36 * TYPES.index(t) + 36, 36)).resize((18 * K, 18 * K), Image.NEAREST)
    off = 9 + (7.5 if len(types) > 1 else 0)   # centredX: half an icon, plus doubleCenteredOffset for two
    if len(types) > 1:
        panel.alpha_composite(icon(types[1]), (round((mid + 15 - off) * K), top * K))
    panel.alpha_composite(icon(types[0]), (round((mid - off) * K), top * K))
    if len(types) == 1:
        panel.alpha_composite(S(o('type_spacer_single')), (round((mid - 18) * K), (top + 3) * K))
    x0, y0, x1, y1 = CROP
    y1 -= sum(1 for r in STARTER_LEFT_ROWS if r < y1) if narrow else 0
    return panel.crop((x0 * K, y0 * K, (x1 - cut) * K, y1 * K))


def main(res, out):
    gui = f'{res}/assets/cobblemon/textures/gui'
    bases = {l.split('\t')[0]: float(l.split('\t')[1]) for l in open(f'{out}/species.tsv').read().splitlines()[1:]}
    species = {os.path.basename(p)[:-5]: json.load(open(p))
               for p in glob.glob(f'{res}/data/cobblemon/species/**/*.json', recursive=True)}
    rs = looks.resolvers(res)
    os.makedirs(f'{out}/cards', exist_ok=True)
    os.makedirs(f'{out}/cards_full', exist_ok=True)
    os.makedirs(f'{out}/tiles', exist_ok=True)
    for name in STARTERS:
        d = species[name]
        bb = json.load(open(f'{out}/java/{name}.bbmodel'))
        png = base64.b64decode(bb['textures'][0]['source'].split(',', 1)[1])
        poser = (looks.poser(rs[name], set()) or f'cobblemon:{name}').split(':')[1]
        types = [t for t in (d.get('primaryType'), d.get('secondaryType')) if t]
        framing = profile_framing(res, poser)
        card(gui, bb, png, framing, bases.get(name, 1.0), types, narrow=True).save(f'{out}/cards/{name}.png')
        card(gui, bb, png, framing, bases.get(name, 1.0), types).save(f'{out}/cards_full/{name}.png')  # Bedrock's
        # the list's tile: Cobblemon's 25 px tile framing, inside its 1 px frame (23 px), at 4 px a GUI px
        portrait.profile(bb, png, *framing, bases.get(name, 1.0), 4, **portrait.TILE).crop((4, 4, 96, 96)).save(f'{out}/tiles/{name}.png')
    print(len(STARTERS), 'starter cards and tiles')


if __name__ == '__main__':
    main(*sys.argv[1:3])
