#!/usr/bin/env python3
"""PartyPanel.java's art: Cobblemon's party overlay (PartyOverlay) for vanilla Java, drawn by the action bar through
the text shader (build_ui.py TEXT_VSH, colour 250: left edge, centred vertically). Font cobblemonlink:ui from U+E4A0
(HUD_GLYPHS order), then the level-up scroll's 14 steps at U+E4C0 + step - 1, HP bars at U+E4D0 + height - 1 and
EXP bars at U+E4F0 + height - 1 (18 px at most). Each glyph's last column has a faint pixel, so it advances exactly its
width + 1. Half-size art (gender, ball, pop-ups) is drawn at Cobblemon's SCALE 0.5.
"""
from PIL import Image

HUD_GLYPHS = ["slot", "slot_active", "slot_fainted", "slot_fainted_active", "portrait_bg", "male", "female", "ball",
              "popup_evolution", "popup_move"]  # PartyPanel.java's order, U+E4A0 on
BAR = 18  # Cobblemon's barHeightMax


def edge(img):
    img = img.copy()
    p = img.getpixel((img.width - 1, 0))
    img.putpixel((img.width - 1, 0), p[:3] + (max(p[3], 1),))
    return img


def scroll(strip, step):
    """PartyOverlay's level-up scroll at step 1-14: a 17 px window onto the 17 x 43 strip, as its blitk calls draw it."""
    size, pos, v = 17, 0, 0
    if step <= 4:
        size = step * 4
        pos = 17 - size
    elif step <= 10:
        v = -1 + (step - 4) * 4
    else:
        size = 20 - (step - 10) * 4
        v = 23 + (step - 10) * 4
    out = Image.new("RGBA", (17, 17))
    top = max(0, v)
    piece = strip.crop((0, top, 17, min(43, v + size)))
    out.alpha_composite(piece, (0, pos + top - v))
    return out


def hud_glyphs(gui, hp_colour):
    """[(name, image, char, height, ascent)] for font cobblemonlink:ui. hp_colour: build_ui.py's Cobblemon HP colour."""
    o = lambda n: Image.open(f"{gui}/party/{n}.png").convert("RGBA")
    ball = Image.open(f"{gui}/ball/poke_ball.png").convert("RGBA").crop((0, 0, 18, 22))
    imgs = {"slot": o("party_slot"), "slot_active": o("party_slot_active"), "slot_fainted": o("party_slot_fainted"),
            "slot_fainted_active": o("party_slot_fainted_active"), "portrait_bg": o("party_slot_portrait_background"),
            "male": o("party_gender_male"), "female": o("party_gender_female"), "ball": ball,
            "popup_evolution": o("party_slot_notification_evolution"), "popup_move": o("party_slot_notification_new_move")}
    half = {"male": 4, "female": 4, "ball": 11, "popup_evolution": 10, "popup_move": 10}  # drawn at SCALE 0.5
    out = []
    for i, n in enumerate(HUD_GLYPHS):
        h = half.get(n, imgs[n].height)
        out.append((f"hud_{n}", edge(imgs[n]), chr(0xE4A0 + i), h, min(7, h)))
    strip = o("party_slot_portrait_level_up")
    for step in range(1, 15):
        out.append((f"hud_scroll_{step}", edge(scroll(strip, step)), chr(0xE4C0 + step - 1), 17, 7))
    for h in range(1, BAR + 1):
        out.append((f"hud_hp_{h}", edge(Image.new("RGBA", (2, h), hp_colour(h / BAR) + (255,))), chr(0xE4D0 + h - 1), h, min(7, h)))
        exp = (int(0.2 * 255), int(0.65 * 255), int(0.84 * 255), 255)
        out.append((f"hud_exp_{h}", edge(Image.new("RGBA", (1, h), exp)), chr(0xE4F0 + h - 1), h, min(7, h)))
    return out


def demo():
    strip = Image.new("RGBA", (17, 43), (255, 255, 255, 255))
    assert scroll(strip, 1).getbbox() == (0, 13, 17, 17)  # 4 px rising from the bottom
    assert scroll(strip, 7).getbbox() == (0, 0, 17, 17)
    assert scroll(strip, 14).getbbox() == (0, 0, 17, 4)  # 4 px left at the top


if __name__ == "__main__":
    demo()
