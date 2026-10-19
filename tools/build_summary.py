#!/usr/bin/env python3
"""SummaryScreen.java's art for vanilla Java: Cobblemon's Summary, stacked to fit over a 5-row chest (the PC's
geometry: panel 182 x 212, the chest's top left at (3, 10)). Font cobblemonlink:ui from U+E480 (SUM_GLYPHS order; build_dex.py has U+E400-E46F).
Cells (column, row; rows 0-4 the chest, 5-7 the inventory, 8 the hotbar): name card 0-2 x 0, the tabs on 3-8 x 0
(two cells each), the picture 0-2 x 1-3, held item 0 x 4, Evolve 1-2 x 4, the tab's page 3-8 x 1-7 (Moves: a move a
row, 1-4), Party 0-2 x 5 (its title), the party 0-2 x 6-7, on the hotbar Send out / Lead / To PC, three cells each.
"""
import numpy as np
from PIL import Image, ImageDraw

from build_dex import slot_x, slot_y, cells

SUM_W, SUM_H = 182, 212
SUM_GLYPHS = ["base", "tab_on", "tab_info", "tab_moves", "tab_stats", "page_info", "page_frame", "page_stats",
              "move", "move_sel", "evolve", "plate", "shiny"]  # SummaryScreen.java's order, U+E480 on
DARK, MID, LIGHT, BAND = (47, 47, 47, 255), (103, 103, 103, 255), (141, 141, 141, 255), (75, 75, 75, 255)
CARD = (slot_x(0) - 3, slot_y(0) - 9)  # the name card's top left: its Lv tab rises above the chest's first row
PAGE = (slot_x(3) - 1, slot_y(1) - 2, slot_x(8) + 17, slot_y(7) + 17)  # x0, y0, x1, y1
TAB = lambda k: cells(3 + 2 * k, 0, 4 + 2 * k, 0)


def cut(a, axis, at, n):
    """a without n rows (axis 0) or columns (axis 1) from at."""
    return np.ascontiguousarray(np.delete(a, range(at, at + n), axis))


def tab_art(o, on):
    x0, y0, x1, y1 = TAB(0)
    t = o("summary_tab").resize((x1 - x0 + 1, 14), Image.NEAREST)
    if not on:
        t = Image.merge("RGBA", [b.point(lambda v: v * 7 // 10) for b in t.split()[:3]] + [t.split()[3]])
    return t


def icon(o, name, on):
    ic = o(f"summary_tab_icon_{name}").resize((12, 12), Image.NEAREST)
    if not on:
        ic = Image.merge("RGBA", [b.point(lambda v: v * 6 // 10) for b in ic.split()[:3]] + [ic.split()[3]])
    return ic


def sum_base(gui):
    o = lambda n: Image.open(f"{gui}/summary/{n}.png").convert("RGBA")
    p = Image.new("RGBA", (SUM_W, SUM_H))
    d = ImageDraw.Draw(p)
    d.rounded_rectangle([0, 6, SUM_W - 1, SUM_H - 3], 4, fill=DARK)
    for k, n in enumerate(["info", "moves", "stats"]):  # every tab unlit; tab_on lights the open one
        x0, y0, x1, y1 = TAB(k)
        p.alpha_composite(tab_art(o, False), (x0, y1 - 12))
        p.alpha_composite(icon(o, n, False), ((x0 + x1) // 2 - 6, y1 - 10))
    w = slot_x(2) + 16 - CARD[0] + 1  # the name card, cut to the three cells' width
    card = np.asarray(o("summary_base").crop((0, 0, 77, 25)))
    p.alpha_composite(Image.fromarray(cut(card, 1, 30, 77 - w)), CARD)
    x0, y0, x1, y1 = cells(0, 1, 2, 3, 2)  # the picture: square
    d.rectangle([x0, y0, x1, y1], fill=MID)  # Cobblemon's grey frame round the dark window
    d.rectangle([x0 + 2, y0 + 2, x1 - 2, y1 - 2], fill=DARK)
    p.alpha_composite(o("portrait_background").resize((x1 - x0 - 5, y1 - y0 - 5), Image.NEAREST), (x0 + 3, y0 + 3))
    x0, y0, x1, y1 = cells(0, 4, 0, 4)  # held item (shown, always empty)
    d.rectangle([x0, y0, x1, y1], fill=(60, 60, 60, 255), outline=(30, 30, 30, 255))
    p.alpha_composite(o("icon_item_held").crop((0, 0, 12, 12)), (x0 + 3, y0 + 3))
    x0, y0, x1, y1 = cells(0, 5, 2, 7, 3)  # the party: its title row, then 2 x 3 cells
    d.rectangle([x0, y0, x1 - 2, y1], fill=MID, outline=LIGHT)
    for i in range(6):
        d.rectangle(cells(i % 3, 6 + i // 3, i % 3, 6 + i // 3), fill=(60, 60, 60, 255), outline=(30, 30, 30, 255))
    return p


def sum_glyphs(gui, pc_plate):
    """[(name, image, char, height, ascent)] for font cobblemonlink:ui; pc_plate: the PC's button plate (58 x 16)."""
    o = lambda n: Image.open(f"{gui}/summary/{n}.png").convert("RGBA")
    x0, y0, x1, y1 = PAGE
    w, h = x1 - x0 + 1, y1 - y0 + 1
    # Cobblemon's Info page, 134 x 148: 10 columns less of the labels, 15 of the values (the Exp. box keeps its width),
    # fewer description rows
    info = np.asarray(o("summary_info_base"))
    assert 134 - w == 25
    info = cut(cut(cut(info, 1, 55, 15), 1, 20, 10), 0, 95, 148 - h)
    frame = Image.new("RGBA", (w, h))
    ImageDraw.Draw(frame).rectangle([0, 0, w - 1, h - 1], fill=MID, outline=LIGHT)
    stats = frame.copy()
    ds = ImageDraw.Draw(stats)
    for i in range(6):
        y = 5 + 17 * i
        ds.rectangle([4, y, w - 5, y + 12], fill=BAND)
        ds.rectangle([4, y, 40, y + 12], fill=DARK)
    move = o("summary_move").crop((0, 0, 108, 22)).resize((w, 18), Image.NEAREST)
    sel = Image.new("RGBA", (w, 18))
    ImageDraw.Draw(sel).rectangle([0, 0, w - 1, 17], outline=(80, 170, 255, 255))
    tab_on = Image.new("RGBA", (TAB(0)[2] - TAB(0)[0] + 1, 14))
    tab_on.alpha_composite(tab_art(o, True))
    plate = pc_plate.resize((55, 18), Image.NEAREST)
    imgs = {"base": sum_base(gui), "tab_on": tab_on, "tab_info": icon(o, "info", True), "tab_moves": icon(o, "moves", True),
            "tab_stats": icon(o, "stats", True), "page_info": Image.fromarray(info), "page_frame": frame, "page_stats": stats,
            "move": move, "move_sel": sel, "evolve": o("summary_evolve_button").crop((0, 0, 54, 15)).resize((37, 18), Image.NEAREST),
            "plate": plate, "shiny": o("icon_shiny").resize((7, 7), Image.NEAREST)}
    return [(f"sum_{n}", imgs[n], chr(0xE480 + i), imgs[n].height, min(7, imgs[n].height)) for i, n in enumerate(SUM_GLYPHS)]


def demo():
    assert PAGE == (64, 44, 172, 184)
    assert TAB(2) == [136, 27, 171, 44]


if __name__ == "__main__":
    demo()
