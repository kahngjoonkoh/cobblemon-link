"""Cobblemon's PC for PcScreen.java: a 5-row chest whose title draws the screen (build_ui.py calls pc()).

Panel 182 x 222 (PC_W x PC_H), the chest's top left at (PC_CHEST) on it. Art from Cobblemon's pc/ textures, cut to the
chest's slots: the box (6 x 5) on columns 0-5, the party (2 x 3) on columns 7-8, the box's ◀ name ▶ bar on the first
inventory row, the info strip (the selected Pokémon) under it all, 7 px past the chest.
Font cobblemonlink:ui, U+E2A0 on (PC_GLYPHS order, then wallpapers at U+E2D0+i, filter type icons at U+E2B0+t, info
type icons at U+E320+t, cell type icons at U+E340+t); cobblemonlink:pc_lv: "Lv<n>" at U+E000+n; pc_label / pc_bold /
pc_text: Unifont (tools/unifont_ascii.hex, GNU Unifont 17, SIL OFL 1.1) shrunk to 0.75 / 0.625 of Cobblemon's
DEFAULT_LARGE, its shadow baked in, at half a GUI px a pixel; cobblemonlink:body / body_big: build_bodies.py's pictures
at 16 / 44 px, U+F9000+i (sorted by name). Every glyph's advance goes to assets/cobblemonlink/pc_widths.json.
"""
import json, os
import numpy as np
from PIL import Image, ImageChops, ImageDraw

PC_W, PC_H = 182, 222
PC_CHEST = (3, 10)  # the chest's top left on the panel
PC_GLYPHS = ["base", "ring", "release", "half", "sort_name", "sort_level", "sort_type", "sort_pokedex_number",
             "sort_gender", "filter_all", "ball", "male", "female"]  # PcScreen.java's order, U+E2A0 on
WALLPAPERS = [f"basic/wallpaper_basic_{i:02d}" for i in range(1, 12)] + [
    f"biome/wallpaper_biome_{b}" for b in ("cave", "forest", "nether", "ocean", "the_end")] + ["misc/wallpaper_pokemon_alpha"]
TYPES = ["normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
         "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"]
slot_x = lambda c: PC_CHEST[0] + 8 + 18 * c
slot_y = lambda r: PC_CHEST[1] + 18 + 18 * r if r < 5 else PC_CHEST[1] + 121 + 18 * (r - 5)  # 5 = the first inventory row
BOX = (slot_x(0) - 3, slot_y(0) - 3, slot_x(5) + 19, slot_y(4) + 19)  # the box screen's outline
SY = slot_y(4) + 22  # the ◀ name ▶ bar's top
IY0, IY1 = SY + 30, PC_H - 6  # the info strip
FX, FY = 5, IY0 - 6  # the name card (its Lv tab rises 6 px above the strip)


def pc_base(gui, cut_columns, cut_rows, stretch, nine):
    """The screen without anything that changes: frames, the teal box screen and its cells, the party's cells, the
    arrows, the filter icon and button plates, the panel's foot, the info strip's plates, name card, portrait window
    and empty held-item slot."""
    o = lambda n: Image.open(f"{gui}/pc/{n}.png").convert("RGBA")
    a = np.asarray(cut_columns(o("pc_base"), [(150, 67), (272, 3), (300, 23)]))  # box 6 columns of 18; party panel 55 wide
    L, R, G3, H = 77, 201, 3, PC_H
    col = lambda x0, x1, y0, y1: Image.fromarray(np.ascontiguousarray(a[y0:y1, x0:x1]))
    centre = Image.new("RGBA", (R - L, H))
    centre.alpha_composite(col(L, R, 0, 26))
    centre.alpha_composite(stretch(col(L, R, 100, 101), [(0, SY - 27)]), (0, 26))
    centre.alpha_composite(col(L, R, 182, 205), (0, SY))
    right = stretch(col(R, a.shape[1], 0, 205), [(110, H - 205)])
    p = Image.new("RGBA", (PC_W, H))
    p.alpha_composite(centre)
    p.alpha_composite(right, (R - L + G3, 0))  # the gutter 3 px wider: the party has 8 px either side
    for i in range(G3):
        p.alpha_composite(centre.crop((R - L - 1, 0, R - L, H)), (R - L + i, 0))
    d = ImageDraw.Draw(p)
    d.rectangle(BOX, fill=(52, 172, 176, 255), outline=(150, 230, 230, 255))
    for r in range(5):
        for c in range(6):
            d.rectangle([slot_x(c) - 1, slot_y(r) - 1, slot_x(c) + 16, slot_y(r) + 16], fill=(42, 150, 156, 255))
    for i in range(6):
        c, r = 7 + i % 2, i // 2
        d.rectangle([slot_x(c) - 1, slot_y(r) - 1, slot_x(c) + 16, slot_y(r) + 16], fill=(60, 60, 60, 255), outline=(30, 30, 30, 255))
    ay = SY + 11
    for cx, left in ((slot_x(0) + 8, True), (slot_x(5) + 8, False)):
        for i in range(4):
            xx = cx + (i if left else -i) - (2 if left else -2)
            d.line([(xx, ay - i), (xx, ay + i)], fill=(255, 255, 255, 255))
    plate = nine(o("pc_release_button").crop((0, 0, 58, 16)), 14, 14, 3)
    for c in (7, 8):
        p.alpha_composite(plate, (slot_x(c) + 1, slot_y(4) + 2))  # sort and type filter, at their cells' foot
    p.alpha_composite(o("pc_icon_filter").crop((0, 0, 16, 16)).resize((10, 10), Image.NEAREST), (slot_x(7) + 12, slot_y(4) - 11))
    DV, PX0, PX1 = slot_y(4) + 22, R - L + G3, R - L + G3 + 53  # the party panel ends under the filters
    d.rectangle([PX0, DV - 1, PX1, SY + 29], fill=(47, 47, 47, 255))  # Release sits on the dark, as Cobblemon's
    d.line([(PX0 + 2, DV - 2), (PX1 - 2, DV - 2)], fill=(80, 80, 80, 255))
    for x, dx in ((PX0, 1), (PX1, -1)):  # round corners
        d.point([(x, DV - 2), (x, DV - 3), (x + dx, DV - 2)], fill=(47, 47, 47, 255))
        d.point([(x + dx, DV - 3)], fill=(80, 80, 80, 255))
    d.rectangle([3, SY + 23, PC_W - 2, H - 4], fill=(47, 47, 47, 255))  # no chest showing round the strip
    d.rectangle([R - L - 3, SY + 18, R - L + G3, SY + 23], fill=(47, 47, 47, 255))
    d.rounded_rectangle([3, IY0, PC_W - 4, IY1], 3, fill=(103, 103, 103, 255), outline=(47, 47, 47, 255))
    nf = np.array(cut_rows(cut_columns(o("pc_base").crop((0, 0, 77, 23)), [(15, 8), (42, 20)]), [3, 4, 12, 13, 14]))
    nf[:6, 27:] = 0  # Cobblemon's Lv tab and name frame, cut to 49 px; only the tab above the bar
    p.alpha_composite(Image.fromarray(nf), (FX, FY))
    d.rectangle([FX + 1, FY + 19, FX + 48, IY1 - 3], fill=(47, 47, 47, 255))  # the portrait window
    p.alpha_composite(o("portrait_background").resize((44, 44), Image.NEAREST), (FX + 3, FY + 20))
    p.alpha_composite(o("pc_base").crop((2, 97, 18, 113)).resize((10, 10), Image.NEAREST), (FX + 37, IY1 - 14))  # held item
    for i in range(4):  # Type, Nature, Ability, Moves: a header band and a value band
        y = IY0 + 3 + 15 * i
        d.rectangle([58, y, PC_W - 9, y + 5], fill=(47, 47, 47, 255))
        d.rectangle([58, y + 6, PC_W - 9, y + 13], fill=(80, 80, 80, 255))
    a = np.asarray(p).copy()  # no chest grey through a see-through pixel inside the chest
    cx, cy = PC_CHEST
    for y in range(cy + 204 - 1, cy - 1, -1):
        hole = a[y, cx:cx + 176, 3] < 255
        a[y, cx:cx + 176][hole] = a[min(y + 1, PC_H - 1), cx:cx + 176][hole]
    return Image.fromarray(a)


def wallpaper(gui, name):
    """Cobblemon's wallpaper, cropped to fill the box screen, each slot lightly shaded so the cells still read."""
    x0, y0, x1, y1 = BOX
    w, h = x1 - x0 - 1, y1 - y0 - 1
    wp = Image.open(f"{gui}/pc/wallpaper/{name}.png").convert("RGBA")
    sc = max(w / wp.width, h / wp.height)
    out = wp.resize((round(wp.width * sc), round(wp.height * sc)), Image.LANCZOS).crop((0, 0, w, h))
    shade = Image.new("RGBA", (18, 18), (0, 0, 0, 50))
    for r in range(5):
        for c in range(6):
            out.alpha_composite(shade, (slot_x(c) - 1 - x0 - 1, slot_y(r) - 1 - y0 - 1))
    return out


def pc_glyphs(gui, cob, cut_columns, cut_rows, stretch, nine):
    """[(name, image, char, height, ascent)] for font cobblemonlink:ui."""
    o = lambda n: Image.open(f"{gui}/pc/{n}.png").convert("RGBA")
    ring = Image.new("RGBA", (18, 18))
    ImageDraw.Draw(ring).rectangle([0, 0, 17, 17], outline=(80, 170, 255, 255))
    plate = o("pc_release_button").crop((0, 0, 58, 16))
    icon = lambda img: img.resize((10, 10), Image.NEAREST)
    imgs = {"base": pc_base(gui, cut_columns, cut_rows, stretch, nine), "ring": ring,
            "release": nine(plate, 34, 14, 3), "half": nine(plate, 16, 14, 3),
            "filter_all": icon(o("pc_icon_filter").crop((0, 0, 16, 16))),
            "ball": Image.open(f"{cob}/textures/item/poke_balls/poke_ball.png").convert("RGBA").resize((6, 6), Image.NEAREST),
            "male": o("gender_icon_male"), "female": o("gender_icon_female")}
    for s in ("name", "level", "type", "pokedex_number", "gender"):
        imgs[f"sort_{s}"] = icon(o(f"pc_button_sort_{s}").crop((0, 0, 20, 20)))
    heights = {"male": 4, "female": 4}  # Cobblemon draws them at half size
    out = []
    for i, n in enumerate(PC_GLYPHS):
        h = heights.get(n, imgs[n].height)
        out.append((f"pc_{n}", imgs[n], chr(0xE2A0 + i), h, min(7, h)))
    for i, n in enumerate(WALLPAPERS):
        img = wallpaper(gui, n)
        out.append((f"pc_wall_{i}", img, chr(0xE2D0 + i), img.height, 7))
    types = Image.open(f"{gui}/types.png").convert("RGBA")
    for t, name in enumerate(TYPES):
        ic = types.crop((36 * t, 0, 36 * t + 36, 36))
        out.append((f"pc_type_filter_{name}", icon(ic), chr(0xE2B0 + t), 10, 7))
        out.append((f"pc_type_info_{name}", ic.resize((14, 14), Image.LANCZOS), chr(0xE320 + t), 7, 7))
        cell = Image.new("RGBA", (6, 8))  # 3 px, half a px down: Cobblemon-small, beside the level
        cell.alpha_composite(ic.resize((6, 6), Image.LANCZOS), (0, 1))
        out.append((f"pc_type_cell_{name}", cell, chr(0xE340 + t), 4, 4))
    return out


# ---- fonts ----
DIG5 = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111",
        "4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001001001001",
        "8": "111101111101111", "9": "111101111001111", "L": "100100100100111", "v": "000000101101010"}
DIG4 = {"0": "111101101111", "1": "010110010111", "L": "100100100111", "v": "000101101010"}  # Lv100 only


def level(n):
    """'Lv<n>' at half a GUI px a pixel (3 x 5 letters, 3 x 4 for Lv100 so it fits by the type icons), its shadow
    1 px down-right, half a px in from the cell's corner: 8 rows, height 4."""
    glyphs = [(3, 4, DIG4[c]) for c in f"Lv{n}"] if n >= 100 else [(3, 5, DIG5[c]) for c in f"Lv{n}"]
    img = Image.new("RGBA", (sum(w + 1 for w, _, _ in glyphs) + 2, 8))
    for dx, rgb in ((1, (30, 30, 30)), (0, (255, 255, 255))):
        x = 1
        for w, h, bits in glyphs:
            for i, b in enumerate(bits):
                if b == "1":
                    img.putpixel((x + i % w + dx, 1 + 5 - h + i // w + dx), rgb + (255,))
            x += w + 1
    return img


def unifont(path):
    hexes = {}
    for line in open(path):
        cp, h = line.strip().split(":")
        hexes[int(cp, 16)] = h
    def bitmap(c):
        h = hexes.get(c, hexes[0x3F])
        w = len(h) // 4
        img = Image.new("L", (w, 16))
        for r in range(16):
            v = int(h[r * w // 4:(r + 1) * w // 4], 16)
            for x in range(w):
                if v >> (w - 1 - x) & 1:
                    img.putpixel((x, r), 255)
        cols = [x for x in range(w) if any(img.getpixel((x, r)) for r in range(16))]
        return img.crop((cols[0], 0, cols[-1] + 1, 16)) if cols else None
    return bitmap


CHARS = [chr(c) for c in range(0x21, 0x7F)] + ["♀", "♂", "é"]
ROWS = (len(CHARS) + 15) // 16  # rows of 16 in a font sheet


def text_font(bitmap, rows, bold):
    """Unifont shrunk to rows px (its 16 -> rows, box filter then a threshold), bold as Minecraft does it (a copy
    1 px right), a dark shadow 1 px down-right. Cells of rows + 2, half a GUI px a pixel. Returns (sheet, cell w, cell h)."""
    cells = {}
    for ch in CHARS:
        g = bitmap(ord(ch))
        if bold:
            wide = Image.new("L", (g.width + 1, 16))
            wide.paste(g, (0, 0))
            shifted = Image.new("L", (g.width + 1, 16))
            shifted.paste(g, (1, 0))
            g = ImageChops.lighter(wide, shifted)
        s = g.resize((max(1, round(g.width * rows / 16)), rows), Image.BOX).point(lambda v: 255 if v >= 110 else 0)
        img = Image.new("RGBA", (s.width + 1, rows + 2))
        for dx, rgb in ((1, (63, 63, 63)), (0, (255, 255, 255))):
            img.paste(Image.new("RGBA", s.size, rgb + (255,)), (dx, dx), s)
        cells[ch] = img
    cw, ch_ = max(i.width for i in cells.values()), rows + 2
    sheet = Image.new("RGBA", (cw * 16, ch_ * ROWS))
    for i, c in enumerate(CHARS):
        sheet.paste(cells[c], (cw * (i % 16), ch_ * (i // 16)))
    return sheet, cw, ch_


def advance(img, scale):
    """The client's bitmap advance: ink width (to the last column with any alpha) times scale, rounded, plus 1."""
    a = np.asarray(img.convert("RGBA"))[:, :, 3]
    cols = np.nonzero(a.any(0))[0]
    w = int(cols[-1]) + 1 if len(cols) else 0
    return int(0.5 + w * scale) + 1


def pc(files, gui, cob, glyph, png_bytes, helpers, out_dir):
    """Adds the PC's glyphs (through glyph()) and fonts to files; writes pc_widths.json (every advance PcScreen needs)."""
    widths = {"ui": {}}
    for name, img, char, height, ascent in pc_glyphs(gui, cob, *helpers):
        img = img.copy()
        p = img.getpixel((img.width - 1, 0))
        img.putpixel((img.width - 1, 0), p[:3] + (max(p[3], 1),))  # advance = full width (scaled) + 1
        glyph(name, img, char, height, ascent)
        widths["ui"][char] = advance(img, height / img.height)
    lv = []
    widths["pc_lv"] = {}
    for n in range(1, 101):
        img = level(n)
        files[f"assets/cobblemonlink/textures/font/pc_lv/{n}.png"] = png_bytes(img)
        lv.append({"type": "bitmap", "file": f"cobblemonlink:font/pc_lv/{n}.png", "height": 4, "ascent": 4,
                   "chars": [chr(0xE000 + n)]})
        widths["pc_lv"][chr(0xE000 + n)] = advance(img, 0.5)
    files["assets/cobblemonlink/font/pc_lv.json"] = json.dumps({"providers": lv}).encode()
    bitmap = unifont(os.path.join(os.path.dirname(__file__), "unifont_ascii.hex"))
    for font, rows, bold, space in (("pc_label", 12, True, 4), ("pc_bold", 10, True, 4), ("pc_text", 10, False, 3)):
        sheet, cw, chh = text_font(bitmap, rows, bold)
        files[f"assets/cobblemonlink/textures/font/{font}.png"] = png_bytes(sheet)
        grid = ["".join(CHARS[r * 16:(r + 1) * 16]).ljust(16, "\u0000") for r in range(ROWS)]
        files[f"assets/cobblemonlink/font/{font}.json"] = json.dumps({"providers": [
            {"type": "space", "advances": {" ": space, "\ue300": -1}},  # U+E300: PcScreen.text's tighter spacing
            {"type": "bitmap", "file": f"cobblemonlink:font/{font}.png", "height": chh // 2, "ascent": chh // 2,
             "chars": grid}]}).encode()
        widths[font] = {" ": space}
        for i, c in enumerate(CHARS):
            widths[font][c] = advance(sheet.crop((cw * (i % 16), chh * (i // 16), cw * (i % 16 + 1), chh * (i // 16 + 1))), 0.5)
    src = os.path.join(out_dir, "bodies")
    names = sorted(n[:-4] for n in os.listdir(src) if n.endswith(".png")) if os.path.isdir(src) else []
    small, big = [], []
    for i, name in enumerate(names):
        img = Image.open(f"{src}/{name}.png").convert("RGBA")
        img.putpixel((img.width - 1, 0), img.getpixel((img.width - 1, 0))[:3] + (max(1, img.getpixel((img.width - 1, 0))[3]),))
        files[f"assets/cobblemonlink/textures/font/body/{name}.png"] = png_bytes(img)
        for lst, h in ((small, 16), (big, 44)):
            lst.append({"type": "bitmap", "file": f"cobblemonlink:font/body/{name}.png", "height": h, "ascent": 7,
                        "chars": [chr(0xF9000 + i)]})
    files["assets/cobblemonlink/font/body.json"] = json.dumps({"providers": small}).encode()
    files["assets/cobblemonlink/font/body_big.json"] = json.dumps({"providers": big}).encode()
    widths["body"], widths["body_big"] = 17, 45  # advance of every body glyph (16 / 44 px, last column inked)
    files["assets/cobblemonlink/pc_widths.json"] = json.dumps(widths, ensure_ascii=False).encode()


def demo():
    assert slot_y(5) == 131 and slot_x(7) == 137 and IY0 == 152
    assert advance(Image.new("RGBA", (5, 5), (255, 255, 255, 255)), 0.5) == 4  # int(0.5 + 2.5) + 1
    img = level(5)
    assert img.size == (14, 8)  # L v 5, each 3 wide + 1, plus 2


if __name__ == "__main__":
    demo()
