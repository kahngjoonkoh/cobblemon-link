#!/usr/bin/env python3
"""PokedexScreen.java's art for vanilla Java: Cobblemon's Pokédex, stacked to fit over a 5-row chest (build_pc.py's
geometry: panel 182 x 212, the chest's top left at (3, 10)). Font cobblemonlink:ui from U+E400 (DEX_GLYPHS order),
platforms at U+E420 + type (TYPES, then the unknown one), tab icons lit at U+E410 + tab, stat bars at U+E440 + px - 1;
font cobblemonlink:dex_no, Cobblemon's tile numbers (3 x 5 digits at half a GUI px).
Cells (column, row; rows 0-4 the chest, 5-7 the inventory, 8 the hotbar): picture 0-3 x 0-2 (gender and shiny
buttons on column 3, form arrows on row 2), info 4-8 x 0-2, the list 0-8 x 3-7, on the hotbar ◀, filter, search,
the 5 tabs, ▶.
"""
from PIL import Image, ImageDraw

DEX_W, DEX_H = 182, 212
DEX_GLYPHS = ["base", "select", "unknown", "caught", "unknown_big", "tab_on", "male", "female", "shiny", "shiny_on",
              "form_left", "form_right"]  # PokedexScreen.java's order, U+E400 on
TABS = ["info", "abilities", "stats", "moves", "evolution_tree"]  # Cobblemon's tab_* art, hotbar columns 3-7
TYPES = ["normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
         "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"]
BAR = 48  # longest stat bar (base 255)
DEEP, MIDC, PALE, WHITE, CASE = (58, 150, 186, 255), (84, 196, 210, 255), (125, 225, 232, 255), (236, 252, 255, 255), (43, 43, 43, 255)
slot_x = lambda c: 11 + 18 * c
slot_y = lambda r: 28 + 18 * r if r < 5 else 131 + 18 * (r - 5) if r < 8 else 189
cells = lambda c0, r0, c1, r1, pad=1: [slot_x(c0) - pad, slot_y(r0) - pad, slot_x(c1) + 15 + pad, slot_y(r1) + 15 + pad]
DIG = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111",
       "4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001001001001",
       "8": "111101111101111", "9": "111101111001111"}


def dex_base(gui, nine):
    o = lambda n: Image.open(f"{gui}/pokedex/{n}.png").convert("RGBA")
    p = Image.new("RGBA", (DEX_W, DEX_H))
    p.alpha_composite(nine(o("pokedex_base_red"), DEX_W * 2, DEX_H * 2, 22).resize((DEX_W, DEX_H), Image.NEAREST))
    d = ImageDraw.Draw(p)
    d.rectangle([3, 3, DEX_W - 4, DEX_H - 4], fill=CASE)  # the case's inside: nothing see-through round the screen
    d.rectangle([7, 7, DEX_W - 8, DEX_H - 8], fill=MIDC, outline=PALE)
    d.rectangle([8, 8, DEX_W - 9, 25], fill=DEEP)  # header: National, seen, caught
    p.alpha_composite(o("globe_icon").resize((7, 7), Image.NEAREST), (10, 13))
    p.alpha_composite(o("caught_seen_icon").crop((0, 0, 14, 14)).resize((7, 7), Image.NEAREST), (116, 13))
    p.alpha_composite(o("caught_seen_icon").crop((0, 14, 14, 28)).resize((7, 7), Image.NEAREST), (146, 13))
    d.rectangle(cells(0, 0, 3, 2), fill=PALE, outline=WHITE)  # the picture
    x0, y0, x1, y1 = cells(4, 0, 8, 2)
    d.rectangle([x0, y0, x1, y0 + 10], fill=DEEP)  # number and name
    d.rectangle([x0, y0 + 11, x1, y1], fill=WHITE, outline=DEEP)  # the tab's page
    tile = o("pokedex_slot").resize((18, 18), Image.NEAREST)
    for r in range(3, 8):
        for c in range(9):
            p.alpha_composite(tile, (slot_x(c) - 1, slot_y(r) - 1))
    d.rectangle([8, slot_y(4) + 18, DEX_W - 9, slot_y(5) - 3], fill=DEEP)  # region, range, filter, page
    for c in range(9):
        d.rectangle(cells(c, 8, c, 8), fill=DEEP, outline=PALE)
    for c, n in ((0, "info_arrow_left"), (8, "info_arrow_right")):
        p.alpha_composite(o(n).crop((0, 0, 7, 10)), (slot_x(c) + 4, slot_y(8) + 3))
    p.alpha_composite(o("filter_icon").resize((10, 10), Image.NEAREST), (slot_x(1) + 3, slot_y(8) + 3))
    p.alpha_composite(o("search_icon").resize((10, 10), Image.NEAREST), (slot_x(2) + 3, slot_y(8) + 3))
    for k, n in enumerate(TABS):
        p.alpha_composite(dim(o(f"tab_{n}").crop((0, 0, 16, 16))), (slot_x(3 + k), slot_y(8)))
    return p


def dim(img):
    r, g, b, a = img.split()
    return Image.merge("RGBA", [c.point(lambda v: v * 7 // 10) for c in (r, g, b)] + [a])


def dex_glyphs(gui, nine):
    """[(name, image, char, height, ascent)] for font cobblemonlink:ui."""
    o = lambda n: Image.open(f"{gui}/pokedex/{n}.png").convert("RGBA")
    lit = Image.new("RGBA", (18, 18))
    ImageDraw.Draw(lit).rectangle([0, 0, 17, 17], fill=PALE, outline=WHITE)
    button = lambda n, y: o(n).crop((0, y, 20, y + 20)).resize((16, 16), Image.NEAREST)
    imgs = {"base": dex_base(gui, nine), "select": o("slot_select").crop((0, 0, 25, 25)).resize((18, 18), Image.NEAREST),
            "unknown": o("pokedex_slot_unknown"), "caught": o("caught_icon_small").resize((5, 5), Image.NEAREST),
            "unknown_big": o("pokedex_slot_unknown").resize((16, 20), Image.NEAREST), "tab_on": lit,
            "male": button("button_male", 0), "female": button("button_female", 0),
            "shiny": button("button_shiny", 0), "shiny_on": button("button_shiny", 20),
            "form_left": o("forms_arrow_left").crop((0, 0, 10, 16)), "form_right": o("forms_arrow_right").crop((0, 0, 10, 16))}
    out = [(f"dex_{n}", imgs[n], chr(0xE400 + i), imgs[n].height, min(7, imgs[n].height)) for i, n in enumerate(DEX_GLYPHS)]
    for k, n in enumerate(TABS):
        out.append((f"dex_tab_{n}", o(f"tab_{n}").crop((0, 16, 16, 32)), chr(0xE410 + k), 16, 7))
    for t, n in enumerate(TYPES + ["unknown"]):
        img = o(f"platform_base_{n}" if n != "unknown" else "platform_base").resize((68, 18), Image.NEAREST)
        out.append((f"dex_platform_{n}", img, chr(0xE420 + t), 18, 7))
    for w in range(1, BAR + 1):
        out.append((f"dex_bar_{w}", Image.new("RGBA", (w, 3), (90, 205, 120, 255)), chr(0xE440 + w - 1), 3, 3))
    return out


def dex_no_font(files, png_bytes, widths):
    """Font cobblemonlink:dex_no: digits 3 x 5 at half a GUI px with a shadow 1 px down-right (Cobblemon's tile numbers)."""
    sheet = Image.new("RGBA", (5 * 10, 8))
    for i, c in enumerate("0123456789"):
        for dx, rgb in ((1, (40, 90, 110)), (0, (255, 255, 255))):
            for k, b in enumerate(DIG[c]):
                if b == "1":
                    sheet.putpixel((5 * i + k % 3 + dx, 1 + k // 3 + dx), rgb + (255,))
    files["assets/cobblemonlink/textures/font/dex_no.png"] = png_bytes(sheet)
    files["assets/cobblemonlink/font/dex_no.json"] = __import__("json").dumps({"providers": [
        {"type": "bitmap", "file": "cobblemonlink:font/dex_no.png", "height": 4, "ascent": 4, "chars": ["0123456789"]}]}).encode()
    widths["dex_no"] = {c: 3 for c in "0123456789"}  # ink 4 half px (digit + shadow) -> 2 + 1


def demo():
    assert slot_y(8) == 189 and slot_y(5) == 131 and slot_x(8) == 155
    assert cells(0, 0, 3, 2) == [10, 27, 81, 80]


if __name__ == "__main__":
    demo()
