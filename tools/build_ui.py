#!/usr/bin/env python3
"""Java UI pack: Cobblemon's battle GUI art as font glyphs, so the client draws it inside vanilla dialog buttons.

usage: build_ui.py COBBLEMON_DIR OUT_ZIP
Font cobblemonlink:ui (codepoints must match Ui.java / Battle.java), all at Cobblemon's sizes:
  U+E000      move tile (grayscale, tinted by type)   U+E001-E004  fight/switch/bag/run tiles   U+E005  party tile
  U+E006      back button
  U+E010+i    type icon i (order of TYPES)            U+E100/E101  overlay info tile / flipped   U+E102 portrait ring
  U+E103      battle log frame                        U+E110+k     overlay HP bar, k of 97 px, pre-coloured
  cursor screen, idle then hover (+1): U+E200+2i menu tile i   U+E210+2t move tile, type t   U+E240 party tile
  U+E242 back button   U+E244 cursor   U+E250+t type icon, 18 px (after the HP bars, U+E111-E171)
  U+E270      Poké Ball inventory icon, 16 px (Hotbar.java draws it in the battle overlay)   U+E271 starter toast box
  U+E280+i    starter screen art (STARTER_GLYPHS, StarterScreen.java)
  U+F000+n / U+F400+n  advance -n / +n px
Also Cobblemon's Poké Ball item (item_model cobblemon:poke_ball): its flat icon, and its 3D model once thrown.
Portraits (build_all.py, OUT_ZIP's folder/portraits): font cobblemonlink:portrait, species i (sorted by name) at
U+F0000+2i facing right and +1 mirrored (Ui.java reads the same list back from this zip), 28 px; and item models
cobblemonlink:portrait/<name> for the menus.
Font cobblemonlink:small: vanilla ascii.png at half size. Also overrides the menu blur/background, the dialog
warning button and the GUI text shader (see TEXT_VSH) to keep the world visible and pin the overlay to the corners.
"""
import io, json, os, sys, zipfile
import numpy as np
from PIL import Image

TYPES = ["normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
         "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"]  # types_small.png order
TYPE_RGB = [0xA8A878, 0xF08030, 0x6890F0, 0x78C850, 0xF8D030, 0x98D8D8, 0xC03028, 0xA040A0, 0xE0C068,
            0xA890F0, 0xF85888, 0xA8B820, 0xB8A038, 0x705898, 0x7038F8, 0x705848, 0xB8B8D0, 0xEE99AC]  # Ui.TYPE_COLOR order
H = 20       # dialog buttons are 20 px tall
# menu blur pass that copies the image unchanged (vanilla's box_blur.fsh interface, 26.x)
NO_BLUR = """#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() { fragColor = texture(InSampler, texCoord); }
"""
# GUI text coloured (254, g, b) [(252, g, b) red, (251, g, b) dark grey] is moved to a screen corner: g bit 0 = right edge, bit 1 = bottom edge, bit 2 = shadow,
# bit 3 = stay horizontally centred, bit 4 = draw at twice the size (Hotbar.java's item sprites), bits 5-7 = n: drawn
# at (8 - n) / 8 opacity (Cobblemon's faded overlay), b = px from that top/bottom edge. (253, g, b): moved b - 64 px down
# in place, drawn at grey level g (the starter screen's chest title, laid out from the title's origin). Ui.overlay() lays it out from the action bar's origin (centre, H - 72).
TEXT_VSH = """#version 330
#extension GL_ARB_separate_shader_objects : require
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#endif
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 3) in ivec2 UV2;
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
void main() {
    vec3 pos = Position;
    vec4 color = Color;
#ifdef IS_GUI
    ivec3 c = ivec3(round(Color.rgb * 255.0));
    if (c.r == 253) { // a chest title piece (CobblemonLink's starter screen): b - 64 px down, grey level g
        pos.y += float(c.b) - 64.0;
        color = vec4(vec3(float(c.g) / 255.0), Color.a);
    } else if (c.r >= 251 && c.r <= 254 && c.r != 253) {
        vec2 screen = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
        if ((c.g & 8) == 0) pos.x += (c.g & 1) == 1 ? screen.x * 0.5 : -screen.x * 0.5;
        if ((c.g & 16) == 16) { // an 8 px atlas sprite at 16 px, grown from its top left (vertices TL, BL, BR, TR)
            int v = gl_VertexIndex % 4;
            if (v == 1 || v == 2) pos.y += 8.0;
            if (v >= 2) pos.x += 8.0;
        }
        pos.y += (c.g & 2) == 2 ? 72.0 - float(c.b) : float(c.b) - screen.y + 72.0;
        vec3 rgb = c.r == 252 ? vec3(1.0, 0.333, 0.333) : c.r == 251 ? vec3(0.333) : vec3(1.0); // red, dark grey, white
        color = vec4(rgb * ((c.g & 4) == 4 ? 0.25 : 1.0), Color.a * float(8 - (c.g >> 5)) / 8.0);
    }
#endif
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = color;
#endif
    texCoord0 = UV0;
}
"""
# vanilla core/gui.vsh, but the dark gradient every in-world screen lays over the world (Screen.extractTransparentBackground:
# 0xC0101010 to 0xD0101010) is made clear, so the world shows behind StarterScreen's chest as behind Cobblemon's screens
GUI_VSH = """#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 0) out vec4 vertexColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    ivec4 c = ivec4(round(Color * 255.0));
    bool dim = c.rgb == ivec3(16) && (c.a == 192 || c.a == 208);
    vertexColor = dim ? vec4(0.0) : Color;
}
"""
HP_FULL = 97  # Cobblemon's battle tile HP bar width
ASCII_ROWS = [  # vanilla include/default.json ascii.png rows, for the half-size copy
    "\0" * 16, "\0" * 16, " !\"#$%&'()*+,-./", "0123456789:;<=>?", "@ABCDEFGHIJKLMNO", "PQRSTUVWXYZ[\\]^_",
    "`abcdefghijklmno", "pqrstuvwxyz{|}~\0"] + ["\0" * 16] * 8


def hp_colour(ratio):
    """Cobblemon's getDepletableRedGreen, times the 0.8/0.8/0.27 it draws the tile bar with."""
    r = -2 * ratio + 2 if ratio > 0.2 else 1.0
    g = 1.0 if ratio > 0.5 else ratio / 0.5 if ratio > 0.2 else 0.0
    return int(min(1, r) * 0.8 * 255), int(g * 0.8 * 255), int(0.27 * 255)


def tint(img, rgb):
    """Multiply by a colour, like Cobblemon's blitk red/green/blue on the grey move tile."""
    r, g, b = rgb >> 16, (rgb >> 8) & 255, rgb & 255
    out = img.copy()
    px = out.load()
    for x in range(out.width):
        for y in range(out.height):
            p = px[x, y]
            px[x, y] = (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, p[3])
    return out


ARROW = ["#.......", "##......", "#o#.....", "#oo#....", "#ooo#...", "#oooo#..", "#ooooo#.", "#oooooo#",
         "#ooo####", "#o##o#..", "##..#o#.", "....##.."]  # the cursor: white with a black edge, tip at the top left


def arrow():
    img = Image.new("RGBA", (8, 12))
    for y, row in enumerate(ARROW):
        for x, c in enumerate(row):
            if c != ".":
                img.putpixel((x, y), (0, 0, 0, 255) if c == "#" else (255, 255, 255, 255))
    return img


def nine(img, w, h, b):
    """img stretched to w x h keeping a b px border (nearest-neighbour middle)."""
    out = Image.new("RGBA", (w, h))
    xs = [(0, b, 0, b), (b, img.width - b, b, w - b), (img.width - b, img.width, w - b, w)]
    ys = [(0, b, 0, b), (b, img.height - b, b, h - b), (img.height - b, img.height, h - b, h)]
    for sx0, sx1, dx0, dx1 in xs:
        for sy0, sy1, dy0, dy1 in ys:
            out.paste(img.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST), (dx0, dy0))
    return out


STARTER_GLYPHS = ["base", "block", "selected", "thumb", "up", "down", "ball"]  # StarterScreen.java's order
STARTER_CHEST = (13, 15, 176, 186)  # where StarterScreen's 4-row chest sits on the panel: x, y, w, h (13 px out each side)
# base.png cut to fit the chest (each list in base.png's own numbering): columns out of the left half, seamless outside
# the card window (build_cards.py draws the window narrower to match), and out of the list; rows out of the type
# strip (3 px of padding round the icons) and of the list, so both panels end 3 px above the buttons, as in Cobblemon
STARTER_LEFT_CUTS = [(16, 3), (22, 6), (108, 9)]
STARTER_LIST_CUT = (176, 19)
STARTER_LEFT_ROWS = [118, 121, 138, 139]  # the type strip shorter (the description box keeps Cobblemon's size)...
STARTER_LEFT_DOWN = 4  # ...so the left panel sits this much lower; it ends level with the list
STARTER_LIST_GROW = 4  # rows the list gains: room for 4 regions
STARTER_LIST_TOP = 5  # the list's top (its title tab and frame) this much lower: its first region sits 4 px under it
STARTER_FOOT_EDGE = 237  # the list footer's plate ends here (base.png's x): the whole width, the arrows on it


def cut_rows(img, rows):
    """img without the given rows."""
    a = np.asarray(img)
    return Image.fromarray(np.ascontiguousarray(np.delete(a, rows, 0)))


def cut_columns(img, cuts):
    """img without the columns of each (x, n) in cuts (x in img's own numbering)."""
    a = np.asarray(img)
    for x, n in sorted(cuts, reverse=True):
        a = np.concatenate([a[:, :x], a[:, x + n:]], 1)
    return Image.fromarray(np.ascontiguousarray(a))


def stretch(img, rows):
    """img with each (row, n) of rows repeated n more times (rows in img's own numbering)."""
    out, y0 = [], 0
    for row, n in sorted(rows):
        out.append(img.crop((0, y0, img.width, row + 1)))
        out += [img.crop((0, row, img.width, row + 1))] * n
        y0 = row + 1
    out.append(img.crop((0, y0, img.width, img.height)))
    res = Image.new("RGBA", (img.width, sum(i.height for i in out)))
    y = 0
    for i in out:
        res.paste(i, (0, y))
        y += i.height
    return res


def starter_screen(gui, ball):
    """Cobblemon's StarterSelectionScreen art for StarterScreen.java, in STARTER_GLYPHS order, changed only where the
    chest needs it. The panel (202 x 203): base.png cut as STARTER_* says, its list footer 24 px shorter (back right of
    its slant) and long enough at the bottom to hide the chest; "I choose you!" 88 px wide over its 5 slots, back at
    Cobblemon's 24 px over 2. A region of the list (CategoryList: header, container 72 px, 3 tiles at a slot's 16 px,
    2 px apart: the chest's pitch). The selected tile's rim; the scrollbar thumb (3 regions of 9); the page arrows
    (ours); the tab's Poké Ball at half size."""
    sel = f"{gui}/starterselection"
    o = lambda n: Image.open(f"{sel}/{n}.png").convert("RGBA")
    x0, y0, w, h = STARTER_CHEST
    H = y0 + h + 1
    base = Image.new("RGBA", (239, 197))
    base.alpha_composite(o("background"), (6, 17))  # the window, for a Pokémon without a card (the random one)
    base.alpha_composite(o("base"))
    base.paste((0, 0, 0, 255), (231, 27, 234, 172))  # the scrollbar's track
    a = np.asarray(cut_columns(base, [STARTER_LIST_CUT])).copy()
    lx, ln = STARTER_LIST_CUT
    rec = 220 - ln  # a column of the footer's dark recess
    e = STARTER_FOOT_EDGE - ln  # the footer, below its top strip: a plain plate (the arrows on it) up to a square
    a[177:189, 134:e] = (103, 103, 103, 255)  # edge instead of the slant, then the dark recess (back in it); it ends
    a[177:189, e] = (141, 141, 141, 255)  # with the back button (base.png 189, 192 once the list grows)
    a[177, e + 1:rec + 1] = (141, 141, 141, 255)
    a[178:189, e + 1:rec + 1] = (47, 47, 47, 255)
    a[189:191, 129:e + 1] = a[195:197, 129:e + 1]  # its bottom edge, from the plate's own...
    a[189, 134:e] = a[195, 150]  # ...the whole way across (Cobblemon's only ran under its slanted part)
    a[191:197, 129:rec + 1] = (47, 47, 47, 255)  # the dark strip under it all
    full = Image.fromarray(a)
    left = cut_columns(cut_rows(full.crop((0, 0, 129, 197)), STARTER_LEFT_ROWS), STARTER_LEFT_CUTS)
    left = Image.fromarray(np.concatenate([np.zeros((STARTER_LEFT_DOWN, left.width, 4), np.uint8), np.asarray(left)]))
    right = cut_rows(full.crop((129, 0, full.width, 197)), list(range(28, 28 + STARTER_LIST_TOP)))
    right = Image.fromarray(np.concatenate([np.zeros((STARTER_LIST_TOP, right.width, 4), np.uint8), np.asarray(right)]))
    right = stretch(right, [(160, STARTER_LIST_GROW)])
    left = stretch(left, [(185, H - left.height)])  # dark, under the left frame (which ends level with the list's)
    right = stretch(right, [(196 + STARTER_LIST_GROW, H - right.height)])  # the dark strip
    base = Image.new("RGBA", (left.width + right.width, H))
    base.paste(left, (0, 0))
    base.paste(right, (left.width, 0))
    a = np.asarray(base).copy()  # no chest grey through a see-through pixel: fill each from the one below it
    for y in range(min(y0 + h, a.shape[0] - 1) - 1, y0 - 1, -1):
        hole = a[y, x0:x0 + w, 3] < 255
        a[y, x0:x0 + w][hole] = a[y + 1, x0:x0 + w][hole]
    base = Image.fromarray(a)
    # the bottom row, at the bottom of its slots: "I choose you!" (4 slots); the arrows sit on the list's footer
    base.alpha_composite(nine(o("choose_button").crop((0, 0, 106, 14)), 72, 14, 3), (20, 179))
    # no back button: Esc or E closes the chest
    tile = lambda n: nine(o(n).crop((0, 0, 25, 25)), 16, 16, 3)
    wide = lambda n: Image.fromarray(np.concatenate([np.asarray(o(n))[:, :36], np.asarray(o(n))[:, -36:]], 1))  # 89 -> 72 px
    block = Image.new("RGBA", (72, 29))
    block.alpha_composite(wide("selection_container_header"))
    block.alpha_composite(wide("selection_container_edge"), (0, 7))
    for y in range(8, 28):
        block.alpha_composite(wide("selection_container"), (0, y))
    block.alpha_composite(wide("selection_container_footer"), (0, 28))
    for i in range(3):  # 10 px of box each side of the tiles
        block.alpha_composite(tile("starter_selection"), (10 + 18 * i, 9))
    ring = tile("starter_selection_active")  # drawn over the face, which fills the tile: only its 2 px rim
    ring.paste((0, 0, 0, 0), (2, 2, 14, 14))
    thumb = Image.new("RGBA", (3, 64), (192, 192, 192, 255))  # 4 regions of 9 in the 144 px track
    thumb.paste((128, 128, 128, 255), (0, 63, 3, 64))
    up = Image.new("RGBA", (7, 7))  # 7 tall: a glyph's ascent (7) can't exceed its height
    for y in range(4):
        up.paste((255, 255, 255, 255), (3 - y, y, 4 + y, y + 1))
    small_ball = ball.resize((8, 8), Image.NEAREST)
    return [("base", base), ("block", block), ("selected", ring), ("thumb", thumb),
            ("up", up), ("down", up.crop((0, 0, 7, 4)).transpose(Image.FLIP_TOP_BOTTOM).crop((0, 0, 7, 7))), ("ball", small_ball)]


def toast():
    """A vanilla-sized toast box as Cobblemon's starter toast draws it: 1 px black outline, 2 px grey, dark fill, round corners."""
    img = Image.new("RGBA", (160, 32))
    for inset, rgb in [(0, (0, 0, 0)), (1, (85, 85, 85)), (3, (33, 33, 33))]:
        img.paste(rgb + (255,), (inset, inset, 160 - inset, 32 - inset))
    for x, y in [(0, 0), (159, 0), (0, 31), (159, 31)]:
        img.putpixel((x, y), (0, 0, 0, 0))
        img.putpixel((1 if x == 0 else 158, 1 if y == 0 else 30), (0, 0, 0, 255))
    return img


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
    glyph("move", squash(move, H), "", H, 13)
    party = Image.open(f"{gui}/battle/party_select.png").convert("RGBA").crop((0, 0, 94, 23))
    glyph("party", squash(party, H), "\ue005", H, 13)
    for i, k in enumerate(["fight", "switch", "bag", "run"]):
        tile = Image.open(f"{gui}/battle/battle_menu_{k}.png").convert("RGBA").crop((0, 26, 90, 52))
        glyph(k, squash(tile, H), chr(0xE001 + i), H, 13)  # Cobblemon size, 90 wide
    back = Image.open(f"{gui}/battle/battle_back.png").convert("RGBA").crop((0, 0, 58, 34))  # drawn at half size
    glyph("back", back.resize((29, 17), Image.NEAREST), chr(0xE006), 17, 12)
    icons = Image.open(f"{gui}/types_small.png").convert("RGBA")
    for i, t in enumerate(TYPES):
        glyph(f"type_{t}", icons.crop((i * 18, 0, i * 18 + 18, 18)), chr(0xE010 + i), 9, 8)
    # battle overlay (Cobblemon's BattleOverlay / BattleMessagePane art), all with ascent 7 so a glyph's top is the text's top
    glyph("info", Image.open(f"{gui}/battle/battle_info_base.png").convert("RGBA"), "", 40, 7)
    glyph("info_flipped", Image.open(f"{gui}/battle/battle_info_base_flipped.png").convert("RGBA"), "", 40, 7)
    glyph("underlay", Image.open(f"{gui}/battle/battle_info_underlay.png").convert("RGBA"), "", 28, 7)
    glyph("log", Image.open(f"{gui}/battle/battle_log.png").convert("RGBA"), "", 55, 7)
    def edge(img):
        """A faint pixel in the last column: the client measures a glyph to its last visible column, so this makes
        every cursor-screen glyph advance exactly its width + 1, which Screen.java counts on."""
        img = img.copy()
        p = img.getpixel((img.width - 1, 0))
        img.putpixel((img.width - 1, 0), p[:3] + (max(p[3], 1),))
        return img

    real_glyph = glyph
    glyph = lambda name, img, char, height, ascent: real_glyph(name, edge(img), char, height, ascent)
    # cursor screen (Screen.java): full-size tiles, idle glyph then hover glyph, ascent 7 like the overlay
    for i, k in enumerate(["fight", "switch", "bag", "run"]):
        tile = Image.open(f"{gui}/battle/battle_menu_{k}.png").convert("RGBA")
        glyph(f"menu_{k}", tile.crop((0, 0, 90, 26)), chr(0xE200 + 2 * i), 26, 7)
        glyph(f"menu_{k}_hover", tile.crop((0, 26, 90, 52)), chr(0xE201 + 2 * i), 26, 7)
    move_all = Image.open(f"{gui}/battle/battle_move.png").convert("RGBA")
    move_over = Image.open(f"{gui}/battle/battle_move_overlay.png").convert("RGBA")
    for t, rgb in enumerate(TYPE_RGB):
        for v in (0, 1):
            img = tint(move_all.crop((0, 24 * v, 92, 24 * v + 24)), rgb)
            img.alpha_composite(move_over)
            glyph(f"move_{TYPES[t]}_{v}", img, chr(0xE210 + 2 * t + v), 24, 7)
    party_all = Image.open(f"{gui}/battle/party_select.png").convert("RGBA")
    glyph("party_big", party_all.crop((0, 0, 94, 29)), "\ue240", 29, 7)
    glyph("party_big_hover", party_all.crop((0, 29, 94, 58)), "\ue241", 29, 7)
    back_all = Image.open(f"{gui}/battle/battle_back.png").convert("RGBA")  # drawn at half size
    glyph("back_big", back_all.crop((0, 0, 58, 34)), "\ue242", 17, 7)
    glyph("back_big_hover", back_all.crop((0, 34, 58, 68)), "\ue243", 17, 7)
    glyph("cursor", arrow(), "\ue244", 12, 7)
    big = Image.open(f"{gui}/types.png").convert("RGBA")  # 36 px icons drawn at half size
    for i, t in enumerate(TYPES):
        glyph(f"type_big_{t}", big.crop((i * 36, 0, i * 36 + 36, 36)), chr(0xE250 + i), 18, 7)
    glyph = real_glyph
    for k in range(1, HP_FULL + 1):  # 4 px tall, top 3 px below the text's top
        glyph(f"tile_hp_{k}", Image.new("RGBA", (k, 4), hp_colour(k / HP_FULL) + (255,)), chr(0xE110 + k), 4, 4)
    adv = {chr(0xF000 + n): -n for n in range(1, 513)} | {chr(0xF400 + n): n for n in range(1, 513)}
    providers.append({"type": "space", "advances": adv})
    # ascii.png's space is blank, which the client sizes at 1 px; Ui.smallWidth counts 3 (half of vanilla's 4, rounded up)
    small = [{"type": "space", "advances": {" ": 3, "\ue300": -1}},  # U+E300: Ui.tight's half-scaled letter spacing
             {"type": "bitmap", "file": "minecraft:font/ascii.png", "height": 4, "ascent": 3, "chars": ASCII_ROWS}]
    files["assets/cobblemonlink/font/small.json"] = json.dumps({"providers": small}).encode()
    low = [small[0], dict(small[1], ascent=0)]  # same, 3 px lower: a tile's bottom row (PP, HP) under the name
    files["assets/cobblemonlink/font/small_low.json"] = json.dumps({"providers": low}).encode()
    files["assets/minecraft/shaders/core/text.vsh"] = TEXT_VSH.encode()

    # Keep the world visible behind the battle dialog, like Cobblemon's screen: no blur, no dimming, no ⚠ button.
    # ponytail: applies to every in-world menu (pause too) while on this server; vanilla has no per-screen switch.
    def clear(name, size):
        buf = io.BytesIO()
        Image.new("RGBA", size).save(buf, "PNG")
        files[f"assets/minecraft/textures/gui/{name}.png"] = buf.getvalue()
    for n in ["inworld_menu_background", "inworld_menu_list_background"]:
        clear(n, (16, 16))
    for n in ["inworld_header_separator", "inworld_footer_separator"]:
        clear(n, (32, 2))
    for n in ["warning_button", "warning_button_disabled", "warning_button_highlighted"]:
        clear(f"sprites/dialog/{n}", (20, 20))
    # No hover square over chest slots (it lights up half of a PC or starter tile) for vanilla Java. The client mod
    # draws vanilla's square again from cobblemonlink:container/slot_highlight_* (the same 16 px white square at 96 /
    # 32 alpha in a 24 px nine-slice). ponytail: every inventory on this server; vanilla has no per-screen switch.
    for n, alpha in [("back", 96), ("front", 32)]:
        clear(f"sprites/container/slot_highlight_{n}", (24, 24))
        sq = Image.new("RGBA", (24, 24))
        sq.paste((255, 255, 255, alpha), (4, 4, 20, 20))
        buf = io.BytesIO()
        sq.save(buf, "PNG")
        files[f"assets/cobblemonlink/textures/gui/sprites/container/slot_highlight_{n}.png"] = buf.getvalue()
        meta = json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 24, "height": 24, "border": 4}}}).encode()
        files[f"assets/cobblemonlink/textures/gui/sprites/container/slot_highlight_{n}.png.mcmeta"] = meta
    files["assets/minecraft/shaders/post/box_blur.fsh"] = NO_BLUR.encode()
    files["assets/minecraft/shaders/core/gui.vsh"] = GUI_VSH.encode()  # nor the dark gradient behind chests
    # The chest's "Inventory" label is drawn over the starter screen's art (StarterScreen.java), so it goes.
    # ponytail: from every chest on this server, in English; other languages keep it.
    files["assets/minecraft/lang/en_us.json"] = json.dumps({"container.inventory": ""}).encode()

    # Poké Ball: Cobblemon's flat icon everywhere, its 3D ball model in flight (a thrown item renders as "ground")
    cob = f"{gui}/../.."
    ball = Image.open(f"{cob}/textures/item/poke_balls/poke_ball.png").convert("RGBA")
    for path in ["textures/item/poke_balls/poke_ball.png", "textures/item/poke_balls/models/poke_ball.png",
                 "models/item/poke_ball.json"]:
        files[f"assets/cobblemon/{path}"] = open(f"{cob}/{path}", "rb").read()
    ball3d = json.load(open(f"{cob}/models/item/poke_ball_model.json"))
    ball3d["textures"]["particle"] = ball3d["textures"]["layer0"]  # the client warns without one
    files["assets/cobblemon/models/item/poke_ball_model.json"] = json.dumps(ball3d).encode()
    model = lambda m: {"type": "minecraft:model", "model": m}
    files["assets/cobblemon/items/poke_ball.json"] = json.dumps({"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "ground", "model": model("cobblemon:item/poke_ball_model")}],
        "fallback": model("cobblemon:item/poke_ball")}}).encode()
    glyph("poke_ball", ball, "\ue270", 16, 7)  # like Icons.java's item sheet: 16 px, ascent 7
    glyph("toast", toast(), "\ue271", 32, 7)  # Cobblemon's "Check out the starters!" toast box, 160 x 32
    for i, (name, img) in enumerate(starter_screen(gui, ball)):  # StarterScreen.java's art, U+E280 on
        glyph(f"starter_{name}", edge(img), chr(0xE280 + i), img.height, 7)
    import build_pc  # PcScreen.java's art and fonts, U+E2A0 on
    build_pc.pc(files, gui, cob, glyph, png_bytes, (cut_columns, cut_rows, stretch, nine), os.path.dirname(out_zip))

    # the client mod (client/) draws the Bedrock forms' screens from the same art: textures/cobblemonlink/<x> of the
    # Bedrock pack (built first) as cobblemonlink:textures/form/<x>; the whole-body pictures are the PC's (font/body)
    mc = os.path.join(os.path.dirname(out_zip), "bedrock-ui.mcpack")
    if os.path.exists(mc):
        with zipfile.ZipFile(mc) as z:
            for n in z.namelist():
                if n.startswith("textures/cobblemonlink/") and "/body/" not in n and n.endswith(".png"):
                    files["assets/cobblemonlink/textures/form/" + n[len("textures/cobblemonlink/"):]] = z.read(n)
    portraits(files, os.path.join(os.path.dirname(out_zip), "portraits"))
    cards(files, os.path.join(os.path.dirname(out_zip), "cards"))
    tiles(files, os.path.join(os.path.dirname(out_zip), "tiles"), os.path.join(os.path.dirname(out_zip), "portraits"))

    bad = [p["chars"] for p in providers if p.get("ascent", 0) > p.get("height", 8)]
    assert not bad, f"ascent above height breaks the whole font: {bad}"
    files["assets/cobblemonlink/font/ui.json"] = json.dumps({"providers": providers}).encode()
    files["pack.mcmeta"] = json.dumps({"pack": {
        "description": "cobblemon-link UI (Cobblemon GUI art, CC BY-NC 3.0)",
        "pack_format": 97, "supported_formats": [46, 999], "min_format": 46, "max_format": 999}}).encode()
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):  # fixed order and timestamps: same input, same zip, same SHA-1
            z.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), files[name])
    return files


def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def cards(files, src):
    """Starter preview cards (build_cards.py) as font cobblemonlink:card, card i (sorted by name) at U+F7000+i,
    at 2 px a GUI px (build_cards.py K), top at the line's top (ascent 7); Ui.java reads the same list back from this zip."""
    if not os.path.isdir(src):
        return
    providers = []
    for i, name in enumerate(sorted(n[:-4] for n in os.listdir(src) if n.endswith(".png"))):
        img = Image.open(f"{src}/{name}.png").convert("RGBA")
        img.putpixel((img.width - 1, 0), (0, 0, 0, 1))  # advance = full width + 1
        files[f"assets/cobblemonlink/textures/font/card/{name}.png"] = png_bytes(img)
        providers.append({"type": "bitmap", "file": f"cobblemonlink:font/card/{name}.png", "height": img.height // 2,
                          "ascent": 7, "chars": [chr(0xF7000 + i)]})
    files["assets/cobblemonlink/font/card.json"] = json.dumps({"providers": providers}).encode()


def tiles(files, src, portraits_dir):
    """The starter list's faces for StarterScreen.java: each starter's (build_cards.py tiles/, a name per starter)
    battle portrait close-up as font cobblemonlink:tile, i (sorted by name) at U+F8000+i, 14 GUI px, inside a
    16 px tile frame on a chest slot; Ui.java reads the same list back from this zip."""
    if not os.path.isdir(src):
        return
    providers = []
    for i, name in enumerate(sorted(n[:-4] for n in os.listdir(src) if n.endswith(".png"))):
        img = Image.open(f"{portraits_dir}/{name}.png").convert("RGBA")
        img.putpixel((img.width - 1, 0), (0, 0, 0, 1))  # advance = full width + 1
        files[f"assets/cobblemonlink/textures/font/tile/{name}.png"] = png_bytes(img)
        providers.append({"type": "bitmap", "file": f"cobblemonlink:font/tile/{name}.png", "height": 14, "ascent": 7,
                          "chars": [chr(0xF8000 + i)]})
    files["assets/cobblemonlink/font/tile.json"] = json.dumps({"providers": providers}).encode()


def portraits(files, src):
    if not os.path.isdir(src):
        return
    names = sorted(n[:-4] for n in os.listdir(src) if n.endswith(".png") and not n.endswith("_r.png"))
    providers, small = [], []
    for i, name in enumerate(names):
        for k, suffix in enumerate(("", "_r")):
            img = Image.open(f"{src}/{name}{suffix}.png").convert("RGBA")
            img.putpixel((img.width - 1, 0), (0, 0, 0, 1))  # advance = full width + 1, whatever the model covers
            files[f"assets/cobblemonlink/textures/font/portrait/{name}{suffix}.png"] = png_bytes(img)
            providers.append({"type": "bitmap", "file": f"cobblemonlink:font/portrait/{name}{suffix}.png",
                              "height": 28, "ascent": 7, "chars": [chr(0xF0000 + 2 * i + k)]})
        # menu icon: 28 px (an exact 1/3) in textures/item/, the only folder the item atlas takes; full size would
        # push every client's item atlas to 8192 px
        small_img = Image.open(f"{src}/{name}.png").convert("RGBA").resize((28, 28), Image.NEAREST)
        small_img.putpixel((27, 0), (0, 0, 0, 1))  # advance = full width + 1, as for the big glyphs
        files[f"assets/cobblemonlink/textures/item/portrait/{name}.png"] = png_bytes(small_img)
        # the same 28 px picture at 16 px, for text: the starter dialog's buttons (cobblemonlink:portrait_small)
        small.append({"type": "bitmap", "file": f"cobblemonlink:item/portrait/{name}.png", "height": 16, "ascent": 12,
                      "chars": [chr(0xF0000 + 2 * i)]})
        files[f"assets/cobblemonlink/items/portrait/{name}.json"] = json.dumps({"model": {
            "type": "minecraft:model", "model": f"cobblemonlink:item/portrait/{name}"}}).encode()
        files[f"assets/cobblemonlink/models/item/portrait/{name}.json"] = json.dumps({
            "parent": "minecraft:item/generated", "textures": {"layer0": f"cobblemonlink:item/portrait/{name}"}}).encode()
    files["assets/cobblemonlink/font/portrait.json"] = json.dumps({"providers": providers}).encode()
    files["assets/cobblemonlink/font/portrait_small.json"] = json.dumps({"providers": small}).encode()


def demo():
    img = Image.new("RGBA", (4, 6))
    for y, v in enumerate([0, 9, 9, 9, 50, 0]):
        img.paste((v, v, v, 255), (0, y, 4, y + 1))
    s = squash(img, 4)
    assert [s.getpixel((0, y))[0] for y in range(4)] == [0, 9, 50, 0], "squash should drop the repeated rows"
    assert widen(img, 10, 1, 1).size == (10, 6)
    assert tint(Image.new("RGBA", (1, 1), (255, 255, 255, 255)), 0x102030).getpixel((0, 0)) == (16, 32, 48, 255)
    t = toast()
    assert t.getpixel((0, 0))[3] == 0 and t.getpixel((1, 1))[:3] == (0, 0, 0) and t.getpixel((80, 16))[:3] == (33, 33, 33)
    st = stretch(Image.new("RGBA", (1, 4)), [(1, 2)])
    assert st.size == (1, 6), "stretch repeats a row"
    n = nine(Image.new("RGBA", (25, 25), (9, 9, 9, 255)), 18, 18, 3)
    assert n.size == (18, 18) and n.getpixel((17, 17)) == (9, 9, 9, 255)
    assert arrow().getpixel((0, 0))[3] == 255 and arrow().getpixel((7, 0))[3] == 0, "arrow tip at the top left"


if __name__ == "__main__":
    demo()
    files = build(sys.argv[1], sys.argv[2])
    print(f"ui pack: {len(files)} files -> {sys.argv[2]}")
