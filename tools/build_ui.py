#!/usr/bin/env python3
"""Java UI pack: Cobblemon's battle GUI art as font glyphs, so the client draws it inside vanilla dialog buttons.

usage: build_ui.py COBBLEMON_DIR OUT_ZIP
Font cobblemonlink:ui (codepoints must match Ui.java):
  U+E000      move tile (grayscale, tinted by the move's type colour)   U+E001-E004  fight/switch/bag/run tiles   U+E005  party tile
  U+E010+i    type icon i (order of TYPES)                              U+E040       HP bar background
  U+E041+k    HP bar fill k px wide (1..HP_W)                           U+F000+n / U+F400+n  advance -n / +n px
"""
import io, json, sys, zipfile
from PIL import Image

TYPES = ["normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
         "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"]  # types_small.png order
H = 20       # dialog buttons are 20 px tall
HP_W = 48    # HP bar fill width


def squash(img, h):
    """Cut the rows most like the row above until the image is h tall: shrinks pixel art without resampling."""
    rows = [[img.getpixel((x, y)) for x in range(img.width)] for y in range(img.height)]
    keep = list(range(img.height))
    while len(keep) > h:
        diff = lambda i: sum(sum(abs(a - b) for a, b in zip(p, q)) for p, q in zip(rows[keep[i]], rows[keep[i - 1]]))
        keep.pop(min(range(1, len(keep)), key=diff))
    out = Image.new("RGBA", (img.width, h))
    for y, src in enumerate(keep):
        out.paste(img.crop((0, src, img.width, src + 1)), (0, y))
    return out


def widen(img, w, left, right):
    """Stretch the columns between left and img.width-right to reach width w (3-slice)."""
    mid = img.crop((left, 0, img.width - right, img.height)).resize((w - left - right, img.height), Image.NEAREST)
    out = Image.new("RGBA", (w, img.height))
    out.paste(img.crop((0, 0, left, img.height)), (0, 0))
    out.paste(mid, (left, 0))
    out.paste(img.crop((img.width - right, 0, img.width, img.height)), (w - right, 0))
    return out


def build(gui, out_zip):
    files, providers = {}, []

    def glyph(name, img, char, height, ascent):
        buf = io.BytesIO()
        img.save(buf, "PNG")
        files[f"assets/cobblemonlink/textures/font/{name}.png"] = buf.getvalue()
        providers.append({"type": "bitmap", "file": f"cobblemonlink:font/{name}.png",
                          "height": height, "ascent": ascent, "chars": [char]})

    # a 20 px glyph with ascent 13 fills a button: button text sits 6 px below the top, glyph tops at 7 - ascent
    move = Image.open(f"{gui}/battle/battle_move.png").convert("RGBA").crop((0, 0, 92, 24))
    glyph("move", widen(squash(move, H), 140, 4, 4), "", H, 13)
    party = Image.open(f"{gui}/battle/party_select.png").convert("RGBA").crop((0, 0, 94, 23))
    glyph("party", widen(squash(party, H), 140, 4, 4), "\ue005", H, 13)
    for i, k in enumerate(["fight", "switch", "bag", "run"]):
        tile = Image.open(f"{gui}/battle/battle_menu_{k}.png").convert("RGBA").crop((0, 26, 90, 52))
        glyph(k, widen(squash(tile, H), 110, 4, 30), chr(0xE001 + i), H, 13)
    icons = Image.open(f"{gui}/types_small.png").convert("RGBA")
    for i, t in enumerate(TYPES):
        glyph(f"type_{t}", icons.crop((i * 18, 0, i * 18 + 18, 18)), chr(0xE010 + i), 9, 8)
    bg = Image.new("RGBA", (HP_W + 2, 5), (32, 32, 32, 255))
    glyph("hp_bg", bg, "", 5, 5)
    for k in range(1, HP_W + 1):
        fill = Image.new("RGBA", (k, 5))  # 3 px bar inside the background's 5 (ascent may not exceed height)
        fill.paste((255, 255, 255, 255), (0, 1, k, 4))
        glyph(f"hp_{k}", fill, chr(0xE040 + k), 5, 5)
    adv = {chr(0xF000 + n): -n for n in range(1, 513)} | {chr(0xF400 + n): n for n in range(1, 513)}
    providers.append({"type": "space", "advances": adv})

    files["assets/cobblemonlink/font/ui.json"] = json.dumps({"providers": providers}).encode()
    files["pack.mcmeta"] = json.dumps({"pack": {
        "description": "cobblemon-link UI (Cobblemon GUI art, CC BY-NC 3.0)",
        "pack_format": 97, "supported_formats": [46, 999], "min_format": 46, "max_format": 999}}).encode()
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):  # fixed order and timestamps: same input, same zip, same SHA-1
            z.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), files[name])
    return files


def demo():
    img = Image.new("RGBA", (4, 6))
    for y, v in enumerate([0, 9, 9, 9, 50, 0]):
        img.paste((v, v, v, 255), (0, y, 4, y + 1))
    s = squash(img, 4)
    assert [s.getpixel((0, y))[0] for y in range(4)] == [0, 9, 50, 0], "squash should drop the repeated rows"
    assert widen(img, 10, 1, 1).size == (10, 6)


if __name__ == "__main__":
    demo()
    files = build(sys.argv[1], sys.argv[2])
    print(f"ui pack: {len(files)} files -> {sys.argv[2]}")
