#!/usr/bin/env python3
"""Bedrock UI pack (sent by Geyser): Cobblemon's battle tiles and starter screen for the forms sent through Floodgate.

usage: build_bedrock_ui.py COBBLEMON_GUI_DIR OUT_MCPACK
A battle form's title ends in MARK + kind (1 menu, 2 moves, 3 party; 4 is StarterScreen's starter form); every button's image names its art under
textures/cobblemonlink/ (Battle.java). ui/server_form.json redraws those forms without the dialog box: the buttons
become Cobblemon's tiles at its positions, anchored to the bottom left like its GUI, with the hover art Cobblemon
shows under the mouse (on a touch screen: while pressed). Other forms keep the vanilla layout.
ponytail: written from the vanilla server_form.json (Mojang/bedrock-samples); only a device shows if Bedrock agrees.
"""
import hashlib, io, json, os, sys, zipfile
from PIL import Image
from build_ui import TYPES, TYPE_RGB, tint, hp_colour


def short(name):
    """A look's name in a Bedrock texture path: Bedrock warns about paths of 80+ characters (some platforms fail), so
    long ones are cut to 31 characters plus 8 of their SHA-1 (Ui.bedrockName does the same)."""
    return name if len(name) <= 40 else name[:31] + "_" + hashlib.sha1(name.encode()).hexdigest()[:8]

MARK = "§r§l§"
TEX = "textures/cobblemonlink/"
UUIDS = ("1e1a2f23-3752-4900-890c-329d1964bb52", "1c47e405-4544-41e2-bcd1-c94a6b420207")  # pack, module


def tex_binding(name="#tex"):
    return {"binding_type": "collection", "binding_collection_name": "form_buttons",
            "binding_name": "#form_button_texture", "binding_name_override": name}


def tile(size, offset, label_offset, visible):
    """One button: its form image is the idle art, image + '_hover' the hovered one, the text on top."""
    def art(suffix):
        return {"type": "image", "size": ["100%", "100%"], "layer": 1, "bindings": [
            tex_binding(), {"binding_type": "view", "source_property_name": f"(#tex + '{suffix}')", "target_property_name": "#texture"}]}
    label = {"type": "label", "layer": 3, "text": "#form_button_text", "shadow": True, "color": [1, 1, 1],
             "anchor_from": "top_left", "anchor_to": "top_left", "offset": label_offset, "size": ["default", "default"],
             "bindings": [{"binding_type": "collection", "binding_collection_name": "form_buttons", "binding_name": "#form_button_text"}]}
    return {"type": "panel", "size": size, "offset": offset, "anchor_from": "top_left", "anchor_to": "top_left",
            "bindings": [tex_binding(), {"binding_type": "view", "source_property_name": visible, "target_property_name": "#visible"}],
            "controls": [{"button@common.button": {
                "size": ["100%", "100%"],
                "$pressed_button_name": "button.form_button_click",
                "$button_bindings": [{"binding_type": "collection_details", "binding_collection_name": "form_buttons"}],
                "controls": [{"default": art("")}, {"hover": art("_hover")}, {"pressed": art("_hover")}, {"label": label}]}}]}


IS_BACK = f"(#tex = '{TEX}back')"
IS_TILE = f"(not ((#tex = '{TEX}back') or (#tex = '{TEX}none') or (#tex = '')))"


def cell(size, tile_size, label_offset, back_offset=None):
    """A grid cell: the tile, or (for the last button) Cobblemon's back arrow moved to (9, H-22)."""
    controls = [{"tile": tile(tile_size, [0, 0], label_offset, IS_TILE)}]
    if back_offset:
        controls.append({"back": tile([29, 17], back_offset, [0, 0], IS_BACK)})
    return {"type": "panel", "size": size, "controls": controls}


def grid(kind, dims, cell_size, offset):
    """Cobblemon positions are from the screen's bottom-left corner, so the grid is anchored there."""
    return {"type": "grid", "anchor_from": "bottom_left", "anchor_to": "bottom_left", "offset": offset,
            "size": [dims[0] * cell_size[0], dims[1] * cell_size[1]], "grid_dimensions": dims,
            "grid_item_template": f"server_form.cl_cell_{kind}", "collection_name": "form_buttons",
            "bindings": [{"binding_name": "#title_text"}, {"binding_type": "view", "target_property_name": "#visible",
                         "source_property_name": f"(not ((#title_text - '{MARK}{kind}') = #title_text))"}]}


def is_battle(yes):
    test = f"((#title_text - '{MARK}') = #title_text)"
    battle = f"((not {test}) and ((#title_text - '{MARK}4') = #title_text) and ((#title_text - '{MARK}5') = #title_text) and ((#title_text - '{MARK}6') = #title_text) and ((#title_text - '{MARK}7') = #title_text) and ((#title_text - '{MARK}8') = #title_text))"  # 4 starter, 5 PC, 6 summary, 7 move learning, 8 Pokédex
    return [{"binding_name": "#title_text"}, {"binding_type": "view", "target_property_name": "#visible",
                                              "source_property_name": battle if yes else test}]


def at(i, control):
    """control bound to form button i (outside a grid, which numbers its own items)."""
    return dict(control, collection_index=i)


def button(size, offset, pressed, controls, index=None):
    b = {"size": size, "offset": offset, "anchor_from": "top_left", "anchor_to": "top_left", "$pressed_button_name": pressed,
         "controls": controls}
    if index is not None:
        b = at(index, dict(b, **{"$button_bindings": [{"binding_type": "collection_details", "binding_collection_name": "form_buttons"}]}))
    return {"button@common.button": b}


def image(tex, size, offset=(0, 0), layer=1, bindings=None):
    c = {"type": "image", "size": list(size), "offset": list(offset), "anchor_from": "top_left", "anchor_to": "top_left", "layer": layer}
    if tex:
        c["texture"] = TEX + tex
    if bindings:
        c["bindings"] = bindings
    return c


def label(text, offset, size=("default", "default"), scale=1.0, align="left", bindings=None, layer=3):
    c = {"type": "label", "text": text, "color": [1, 1, 1], "shadow": True, "layer": layer, "offset": list(offset),
         "size": list(size), "anchor_from": "top_left", "anchor_to": "top_left", "text_alignment": align}
    if scale != 1.0:
        c["font_scale_factor"] = scale
    if bindings:
        c["bindings"] = bindings
    return c


BUTTON_TEXT = [{"binding_type": "collection", "binding_collection_name": "form_buttons", "binding_name": "#form_button_text"}]
BUTTON_IMAGE = [{"binding_type": "collection", "binding_collection_name": "form_buttons", "binding_name": "#form_button_texture",
                 "binding_name_override": "#texture"},
                {"binding_type": "collection", "binding_collection_name": "form_buttons",
                 "binding_name": "#form_button_texture_file_system", "binding_name_override": "#texture_file_system"}]


def visible(expr):
    return {"binding_type": "view", "source_property_name": expr, "target_property_name": "#visible"}


STARTER_MAX = 36  # starter slots the form can hold: 12 regions of 3 (Cobblemon 1.8.2 has 9)
STARTER_BUTTONS = 4  # per starter: 0 "I choose you!" (card, "#0001"), 1 its tile (text: the region on a region's first), 2 name, 3 entry
STARTER_PAGE = 3  # regions shown at once (Cobblemon's list height)


def sibling(control, prop="#toggle_state", target="#visible"):
    """A sibling control's property (a toggle's state) into this one's (the dropdown's own pattern)."""
    return {"binding_type": "view", "source_control_name": control, "source_property_name": prop,
            "target_property_name": target, "resolve_sibling_scope": True}


def radio(name, group, index, size, offset, off, on, shown):
    """A radio toggle (one of group checked at a time, none of it a form button: picking never submits). shown:
    bindings for #visible; off / on: (idle, hover) textures."""
    states = {"unchecked": off[0], "unchecked_hover": off[1], "checked": on[0], "checked_hover": on[1]}
    ctl = {k: image(t, size) for k, t in states.items()}
    return {f"{name}@common.toggle": {
        "size": list(size), "offset": list(offset), "anchor_from": "top_left", "anchor_to": "top_left", "layer": 2,
        "$toggle_name": group, "$toggle_view_binding_name": name, "$radio_toggle_group": True,
        "$toggle_group_forced_index": index, "$toggle_group_default_selected": 0, "$toggle_binding_type": "none",
        "$toggle_property_bag": {"#toggle_state": index == 0},
        "$unchecked_control": f"server_form.cl_st_{name}_unchecked", "$checked_control": f"server_form.cl_st_{name}_checked",
        "$unchecked_hover_control": f"server_form.cl_st_{name}_unchecked_hover", "$checked_hover_control": f"server_form.cl_st_{name}_checked_hover",
        "$unchecked_locked_control": "common.empty_panel", "$checked_locked_control": "common.empty_panel",
        "$unchecked_locked_hover_control": "common.empty_panel", "$checked_locked_hover_control": "common.empty_panel",
        "bindings": shown}}, {f"cl_st_{name}_{k}": c for k, c in ctl.items()}


def starter_form():
    """Cobblemon's StarterSelectionScreen (StarterScreen.bedrock) in one form that stays open while browsing: its
    239 x 197 panel, the regions three at a time (page toggles on the scrollbar's track), each starter a radio toggle
    whose card, name, number and entry show while it is checked. Only "I choose you!" is a form button (button
    STARTER_BUTTONS * k), so browsing never picks. A region is 3 starters (padded with "none" tiles).
    Returns (the form, the toggles' state controls for the namespace).
    ponytail: the model is still Cobblemon's card picture."""
    n, per = STARTER_MAX, STARTER_BUTTONS
    pages = -(-n // 3 // STARTER_PAGE)
    tex = {"binding_type": "collection", "binding_collection_name": "form_buttons", "binding_name": "#form_button_texture",
           "binding_name_override": "#cl_t"}
    text = {"binding_type": "collection", "binding_collection_name": "form_buttons", "binding_name": "#form_button_text",
            "binding_name_override": "#text"}
    exists = f"(not ((#cl_t = '') or (#cl_t = '{TEX}none')))"
    def ix(i, control, bag=None):  # bound to form button i
        control = dict(control, collection_index=i)
        if bag:
            control["property_bag"] = bag
        return control
    c = [{"base": image("starter_base", (239, 197))},
         {"ball": image("starter_ball", (8, 8), (4, 3), 2)},
         {"heading": label("Starter Selection", (134, 11), (77, 10), align="center")},
         {"exit@common.button": button([16, 12], [210, 181], "button.menu_exit", [
             {s: image(t, (16, 12))} for s, t in (("default", "starter_exit"), ("hover", "starter_exit_hover"), ("pressed", "starter_exit_hover"))])["button@common.button"]}]
    states = {}
    track = 145 // pages
    for p in range(pages):  # the scrollbar's track (231, 27)-(234, 172): a page per segment, the checked one the thumb
        tog, st = radio(f"pg{p}", "cl_starter_page", p, (5, track), (230, 27 + p * track), ("none", "starter_track_hover"),
                        ("starter_thumb", "starter_thumb"), [tex, view(exists, "#visible")])
        key = f"pg{p}@common.toggle"
        tog[key] = dict(ix(p * STARTER_PAGE * 3 * per + 1, tog[key]), **{"$toggle_property_bag": {"#cl_t": "", "#toggle_state": p == 0}})  # its first region's first tile
        c.append(tog); states |= st
    for k in range(n):
        r, j = divmod(k, 3)
        page, row = divmod(r, STARTER_PAGE)
        y = 27 + 44 * row
        tile = k * per + 1
        shown = [tex, sibling(f"pg{page}", target="#cl_p"), view(f"(#cl_p and {exists})", "#visible")]
        bag = {"#cl_t": "", "#cl_p": False}
        if j == 0:  # the region's container and its name (the first tile's text)
            c.append({f"rb{r}": ix(tile, image("starter_block", (89, 40), (138, y), 1, shown), bag)})
            c.append({f"rh{r}": ix(tile, label("#text", (138, y + 1), (89, 7), 0.5, "center", [text] + shown, layer=3), bag)})
        tog, st = radio(f"st{k}", "cl_starter", k, (25, 25), (143 + 27 * j, y + 11), ("starter_tile", "starter_tile_hover"),
                        ("starter_tile_active", "starter_tile_active_hover"), shown)
        key = f"st{k}@common.toggle"
        tog[key] = dict(ix(tile, tog[key]), **{"$toggle_property_bag": bag | {"#toggle_state": k == 0}})
        c.append(tog); states |= st
        face = image(None, (23, 23), (144 + 27 * j, y + 12), 4, [dict(tex, binding_name_override="#texture")] + shown)
        c.append({f"sf{k}": ix(tile, face, bag)})
        on = sibling(f"st{k}")  # the details while this starter is checked
        c.append({f"sc{k}": ix(k * per, image(None, (128, 125), (1, 13), 2, [dict(tex, binding_name_override="#texture"), on]))})
        c.append({f"sn{k}": ix(k * per + 2, label("#text", (14, 2), bindings=[text, on]))})
        c.append({f"sd{k}": ix(k * per, label("#text", (79, 2), bindings=[text, on]))})
        c.append({f"se{k}": ix(k * per + 3, label("#text", (8, 143), (114, 28), 0.5, bindings=[text, on]))})
        choose = button([106, 14], [13, 180], "button.form_button_click",
                        [{"default": image("starter_choose", (106, 14))}, {"hover": image("starter_choose_hover", (106, 14))},
                         {"pressed": image("starter_choose_hover", (106, 14))},
                         {"label": label("I choose you!", (0, 3), (106, 10), align="center")}], index=k * per)["button@common.button"]
        choose["bindings"] = [on]
        c.append({f"sb{k}@common.button": choose})
    form = {"type": "panel", "size": [239, 197], "anchor_from": "center", "anchor_to": "center", "collection_name": "form_buttons",
            "bindings": [{"binding_name": "#title_text"}, visible(f"(not ((#title_text - '{MARK}4') = #title_text))")],
            "controls": c}
    return form, states


# ---- the PC (PcScreen.bedrock): Cobblemon's PCGUI at its own positions (from its 1.8.2 classes) ----
PC_TYPES = TYPES
PC_HOLDERS = {"prev": 0, "next": 1, "name": 2, "filter": 3, "sort": 4, "release": 5, "yes": 6, "no": 7, "wallpaper": 8,
              "body": 9, "nature": 10, "ability": 11, "move0": 12, "gender": 16, "type0": 17, "type1": 18}
PC_BOX0, PC_PARTY0 = 19, 49  # PcScreen.java's button order


def pc_party_xy(i):
    """StorageWidget (85, 27) + party slot (193, 8): two staggered columns 31 apart, the right one 8 px lower."""
    return 278 + i % 2 * 31, 35 + 31 * (i // 2) + i % 2 * 8


def pc_cell():
    """server_form.cl_pc_cell, a box or party slot (StorageSlot, 25 px): the whole Pokémon (the button's image), "Lv5" top left, type icons
    top right, Cobblemon's selection pointer. The text is "Lv5" + "~<type><1|2>" tokens + "*" if selected."""
    t = [{"binding_type": "collection", "binding_collection_name": "form_buttons", "binding_name": "#form_button_text",
          "binding_name_override": "#t"}]
    tokens = [f"~{ty}{n}" for ty in PC_TYPES for n in (1, 2)]
    lv = "(#t" + "".join(f" - '{k}'" for k in tokens) + " - '*')"
    has = lambda k: visible(f"(not ((#t - '{k}') = #t))")
    controls = [{"default": image(None, (25, 25))}, {"hover": image("pc/hover", (25, 25), layer=4)},
                {"pressed": image("pc/hover", (25, 25), layer=4)},
                {"body": image(None, (25, 25), (0, 0), 2, BUTTON_IMAGE + [visible("(not (#texture = 'textures/cobblemonlink/none'))")])},
                {"lv": label("#text", (1, 1), scale=0.5, bindings=t + [
                    {"binding_type": "view", "source_property_name": lv, "target_property_name": "#text"}], layer=5)},
                {"ring": image("pc/pointer", (11, 8), (7, -6), 6, t + [has("*")])}]
    for ty in PC_TYPES:
        for n in (1, 2):
            controls.append({f"t_{ty}{n}": image(f"pc/tcell/{ty}", (4, 4), (20 - 5 * (n - 1), 1), 5, t + [has(f"~{ty}{n}")])})
    return {"size": [25, 25], "anchor_from": "top_left", "anchor_to": "top_left",
            "$pressed_button_name": "button.form_button_click",
            "$button_bindings": [{"binding_type": "collection_details", "binding_collection_name": "form_buttons"}],
            "bindings": t + [visible("(not (#t = ''))")], "controls": controls}


def pc_button(i, xy, size, tex, text=None, text_xy=(0, 0), states=True):
    """A form button i at xy, its art tex (+ '_hover'), an optional label; hidden while its text is empty."""
    t = [{"binding_type": "collection", "binding_collection_name": "form_buttons", "binding_name": "#form_button_text",
          "binding_name_override": "#t"}]
    art = [{"default": image(tex, size)}, {"hover": image(tex + ("_hover" if states else ""), size)},
           {"pressed": image(tex + ("_hover" if states else ""), size)}] if tex else [
        {"default": image(None, size)}, {"hover": image("pc/hover", size, layer=4)}, {"pressed": image("pc/hover", size, layer=4)}]
    if text:
        art.append({"text": label(text, text_xy, (size[0], 10), align="center", layer=5,
                                  bindings=t + [{"binding_type": "view", "source_property_name": "#t", "target_property_name": "#text"}] if text == "#t" else None)})
    return at(i, {"button@common.button": {
        "size": list(size), "offset": list(xy), "anchor_from": "top_left", "anchor_to": "top_left",
        "$pressed_button_name": "button.form_button_click",
        "$button_bindings": [{"binding_type": "collection_details", "binding_collection_name": "form_buttons"}],
        "bindings": t + [visible("(not (#t = ''))")], "controls": art}})


def pc_text(i, xy, size, scale=0.5, align="center"):
    """Holder button i's text as a label (not a button)."""
    return at(i, label("#form_button_text", xy, size, scale, align, BUTTON_TEXT, layer=5))


def pc_image(i, xy, size, layer=2):
    """Holder button i's image (not a button); hidden while it is 'none'."""
    return at(i, image(None, size, xy, layer, BUTTON_IMAGE + [visible("(not (#texture = 'textures/cobblemonlink/none'))")]))


def pc_form():
    """Cobblemon's PC (PcScreen.bedrock): its 349 x 205 panel in the middle of the screen. Buttons in PC_HOLDERS
    order, then the box's 30 slots and the party's 6; the title (less MARK 5) is the selected Pokémon's name.
    ponytail: still pictures; Filter and the sort icon open / cycle instead of Cobblemon's text search and sort row."""
    H = PC_HOLDERS
    title = [{"binding_name": "#title_text"}]
    c = [{"base": image("pc/base", (349, 205))},
         pc_image(H["wallpaper"], (85, 27), (174, 155), 2),
         {"grid": image("pc/grid", (160, 133), (92, 38), 3)}, {"overlay": image("pc/overlay", (174, 155), (85, 27), 3)},
         {"party": label("Party", (267, 9), (82, 10), align="center")},
         {"held": label("Held Item", (24, 107), (40, 6), 0.5, "left")},
         {"h_nature": label("Nature", (9, 128), (63, 6), 0.5)}, {"h_ability": label("Ability", (9, 145), (63, 6), 0.5)},
         {"h_moves": label("Moves", (9, 162), (63, 6), 0.5)},
         pc_text(H["nature"], (9, 136), (63, 6)), pc_text(H["ability"], (9, 153), (63, 6)),
         pc_image(H["body"], (9, 30), (60, 60), 3),
         pc_text(H["body"], (6, 2), (40, 9), 1.0, "left"),  # "Lv. 5" in the tab
         pc_text(H["gender"], (69, 11), (8, 9), 1.0, "left"),
         pc_image(H["type0"], (62, 28), (8, 8), 4), pc_image(H["type1"], (53, 28), (8, 8), 4),
         {"name": label("#text", (12, 11), (56, 9), bindings=title + [
             {"binding_type": "view", "source_property_name": f"(#title_text - '{MARK}5')", "target_property_name": "#text"}])},
         {"ball": image("starter_ball", (8, 8), (3, 12), 2, title + [visible(f"(not ((#title_text - '{MARK}5') = ''))")])},
         pc_button(H["prev"], (113, 13), (12, 12), None), {"prev_art": image("pc/arrow_previous", (7, 7), (117, 16), 4)},
         pc_button(H["next"], (216, 13), (12, 12), None), {"next_art": image("pc/arrow_next", (7, 7), (220, 16), 4)},
         pc_button(H["name"], (126, 11), (90, 12), None, "#t", (0, 2)),
         pc_button(H["filter"], (126, 182), (88, 14), None, "#t", (4, 3)), {"filter_art": image("pc/filter", (8, 8), (130, 185), 4)},
         pc_button(H["sort"], (216, 184), (12, 12), None), pc_image(H["sort"], (218, 186), (8, 8), 5),
         pc_button(H["release"], (279, 151), (58, 16), "pc/release", "Release", (0, 4)),
         pc_button(H["yes"], (277, 152), (30, 13), "pc/half", "Yes", (0, 3)),
         pc_button(H["no"], (308, 152), (30, 13), "pc/half", "No", (0, 3)),
         button([26, 13], [320, 186], "button.menu_exit",
                [{s: image(t, (26, 13))} for s, t in (("default", "pc/exit"), ("hover", "pc/exit_hover"), ("pressed", "pc/exit_hover"))])]
    for k in range(4):
        c.append(pc_text(H["move0"] + k, (9, 170 + 7 * k), (63, 6)))
    slot = lambda i, x, y: {f"slot{i}@server_form.cl_pc_cell": {"collection_index": i, "offset": [x, y]}}
    for s in range(30):
        c.append(slot(PC_BOX0 + s, 92 + 27 * (s % 6), 38 + 27 * (s // 6)))
    for i in range(6):
        c.append(slot(PC_PARTY0 + i, *pc_party_xy(i)))
    return {"type": "panel", "size": [349, 205], "anchor_from": "center", "anchor_to": "center",
            "collection_name": "form_buttons",  # at() items read the form's buttons by index
            "bindings": title + [visible(f"(not ((#title_text - '{MARK}5') = #title_text))")], "controls": c}


def pc_art(gui, png, out):
    """The PC's textures under TEX + pc/, and every look's whole body (build_bodies.py) under TEX + body/."""
    o = lambda n: Image.open(f"{gui}/{n}.png").convert("RGBA")
    base = o("pc/pc_base")
    base.alpha_composite(o("pc/portrait_background"), (6, 27))
    base.alpha_composite(o("pc/info_box"), (9, 128))
    base.alpha_composite(o("pc/pc_screen_glow"), (68, 10))
    base.alpha_composite(o("pc/party_panel"), (267, 8))
    png("pc/base", base)
    png("pc/grid", o("pc/pc_screen_grid"))
    png("pc/overlay", o("pc/pc_screen_overlay"))
    png("pc/pointer", o("pc/pc_pointer"))
    png("pc/hover", Image.new("RGBA", (25, 25), (255, 255, 255, 60)))
    for side in ("next", "previous"):
        png(f"pc/arrow_{side}", o(f"pc/pc_arrow_{side}").crop((0, 0, 14, 14)))
    png("pc/filter", o("pc/pc_icon_filter").crop((0, 0, 16, 16)))
    for k, v in (("", 0), ("_hover", 1)):
        png(f"pc/release{k}", o("pc/pc_release_button").crop((0, 16 * v, 58, 16 * v + 16)))
        png(f"pc/half{k}", o("pc/pc_release_button_confirm").crop((0, 13 * v, 30, 13 * v + 13)))
        b = o("common/back_button").crop((0, 13 * v, 26, 13 * v + 13))
        icon = o("common/back_button_icon")
        b.alpha_composite(icon.resize((icon.width // 2, icon.height // 2), Image.NEAREST), (8, 3))
        png(f"pc/exit{k}", b)
    for s in ("name", "level", "type", "pokedex_number", "gender"):
        png(f"pc/sort_{s}", o(f"pc/pc_button_sort_{s}").crop((0, 0, 20, 20)))
    png("pc/sort_options", o("pc/pc_icon_options").crop((0, 0, 16, 16)))
    types = o("types")
    for i, t in enumerate(TYPES):
        png(f"pc/type/{t}", types.crop((36 * i, 0, 36 * i + 36, 36)))
        png(f"pc/tcell/{t}", types.crop((36 * i, 0, 36 * i + 36, 36)).resize((8, 8), Image.LANCZOS))
    import glob as _g
    for f in sorted(_g.glob(f"{gui}/pc/wallpaper/*/wallpaper_*.png")):
        png("pc/wall/" + os.path.basename(f)[len("wallpaper_"):-4], Image.open(f).convert("RGBA"))
    d = os.path.join(os.path.dirname(out), "bodies")
    for n in sorted(os.listdir(d)) if os.path.isdir(d) else []:
        png("body/" + short(n[:-4]), Image.open(f"{d}/{n}").convert("RGBA"))


# ---- the Summary (SummaryScreen.java): Cobblemon's Summary at its own positions (331 x 161) ----
SUM_ROWS = 17  # first row holder: label, value, image (three buttons a row)


def sum_row(r, tab):
    """Row r's label and value as Cobblemon's Info rows (15 px apart), the Moves tab's move slots (25 px), or Stats."""
    if tab == "moves":
        y = 12 + 6 + 25 * r
        return (77 + 10, y + 7), (77 + 70, y + 7)
    y = 12 + 7 + 15 * r if r < 5 else (12 + 84, 12 + 124, 12 + 136)[r - 5]
    return (77 + 5, y), (77 + 50, y)


def summary_form():
    """SummaryScreen: buttons 0-2 the tabs ('*' on the open one), 3-5 send out / lead / to PC, 6 the Pokémon
    (image, 'Lv. n'), 7 gender, 8-9 types, 11-16 the party, 17 on: rows of label, value, image."""
    title = [{"binding_name": "#title_text"}]
    c = [{"base": image("summary/base", (331, 161))},
         {"main": image("summary/info_base", (134, 148), (77, 12), 2)},
         {"party_bg": image("summary/party_bg", (114, 113), (216, 24), 2)},
         pc_image(6, (9, 35), (60, 60), 3), pc_text(6, (6, 4), (40, 9), 1.0, "left"),
         pc_text(7, (69, 14), (8, 9), 1.0, "left"),
         pc_image(8, (27, 119), (12, 12), 3), pc_image(9, (41, 119), (12, 12), 3),
         {"held": label("Held Item", (24, 113), (40, 6), 0.5, "left")},
         {"ball": image("starter_ball", (8, 8), (3, 15), 2)},
         {"name": label("#text", (12, 14), (56, 9), bindings=title + [
             {"binding_type": "view", "source_property_name": f"(#title_text - '{MARK}6')", "target_property_name": "#text"}])},
         button([26, 13], [302, 145], "button.menu_exit",
                [{s_: image(t_, (26, 13))} for s_, t_ in (("default", "pc/exit"), ("hover", "pc/exit_hover"), ("pressed", "pc/exit_hover"))])]
    for k, name in enumerate(("info", "moves", "stats")):
        c.append(pc_button(k, (78 + 31 * k, -1), (39, 13), f"summary/tab_{name}", states=False))
    for k, (x, w) in enumerate(((217, 30), (249, 24), (275, 24))):
        c.append(pc_button(3 + k, (x, 139), (w, 11), None, "#t", (0, 2)))
    c.append(pc_button(10, (12, 13), (56, 11), None))  # the name: a tap renames, as Cobblemon's nickname field
    c.append(pc_button(17 + 3 * 8, (12, 145), (54, 15), "summary/evolve", "Evolve", (0, 4)))  # Cobblemon's Evolve button
    for i in range(6):
        x, y = 216 + 6 + i % 2 * 51, 24 + 8 + 32 * (i // 2) + i % 2 * 8
        c.append({f"ps{i}@server_form.cl_pc_cell": {"collection_index": 11 + i, "offset": [x + 9, y]}})
    for r in range(8):  # every tab on the Info rows (15 px apart, then the Ability block and the EXP rows)
        (lx, ly), (vx, vy) = sum_row(r, "info")
        c.append(at(SUM_ROWS + 3 * r, label("#form_button_text", (lx, ly), (45, 7), 0.5, "left", BUTTON_TEXT, layer=5)))
        c.append(at(SUM_ROWS + 3 * r + 1, label("#form_button_text", (vx, vy), (80, 7), 0.5, "left", BUTTON_TEXT, layer=5)))
    return {"type": "panel", "size": [331, 161], "anchor_from": "center", "anchor_to": "center",
            "collection_name": "form_buttons",  # at() items read the form's buttons by index
            "bindings": title + [visible(f"(not ((#title_text - '{MARK}6') = #title_text))")], "controls": c}


def summary_art(gui, png):
    o = lambda n: Image.open(f"{gui}/summary/{n}.png").convert("RGBA")
    base = o("summary_base")
    base.alpha_composite(o("portrait_background"), (6, 32))
    png("summary/base", base)
    png("summary/info_base", o("summary_info_base"))
    png("summary/moves_base", o("summary_moves_base"))
    png("summary/party_bg", o("summary_party_background"))
    png("summary/evolve", o("summary_evolve_button").crop((0, 0, 54, 15)))
    png("summary/evolve_hover", o("summary_evolve_button").crop((0, 15, 54, 30)))
    for name in ("info", "moves", "stats"):
        tab = o("summary_tab")
        tab.alpha_composite(o(f"summary_tab_icon_{name}").resize((9, 9), Image.NEAREST), (15, 2))
        png(f"summary/tab_{name}", tab)


def learn_form():
    """MoveLearn: Cobblemon's moves panel (134 x 148) under the question (the title, less MARK 7); buttons 0-3 the
    known moves (tap one to forget it), 4 "Don't learn"."""
    title = [{"binding_name": "#title_text"}]
    c = [{"q": label("#text", (0, 0), (134, 10), align="center", bindings=title + [
            {"binding_type": "view", "source_property_name": f"(#title_text - '{MARK}7')", "target_property_name": "#text"}])},
         {"base": image("summary/moves_base", (134, 148), (0, 12))},
         {"text": label("#form_text", (5, 136), (58, 20), 0.5)}]
    for k in range(4):
        c.append(pc_button(k, (12, 12 + 6 + 25 * k), (110, 22), None, "#t", (0, 7)))
    c.append(pc_button(4, (68, 12 + 113), (60, 28), None, "#t", (0, 10)))
    return {"type": "panel", "size": [134, 160], "anchor_from": "center", "anchor_to": "center",
            "collection_name": "form_buttons",  # at() items read the form's buttons by index
            "bindings": title + [visible(f"(not ((#title_text - '{MARK}7') = #title_text))")], "controls": c}


def dex_slot():
    """server_form.cl_dex_slot: a Pokédex entry (25 px): its slot, picture, number, caught mark, the chosen ring."""
    t = [{"binding_type": "collection", "binding_collection_name": "form_buttons", "binding_name": "#form_button_text",
          "binding_name_override": "#t"}]
    has = lambda k: visible(f"(not ((#t - '{k}') = #t))")
    return {"size": [25, 25], "anchor_from": "top_left", "anchor_to": "top_left",
            "$pressed_button_name": "button.form_button_click",
            "$button_bindings": [{"binding_type": "collection_details", "binding_collection_name": "form_buttons"}],
            "bindings": t + [visible("(not (#t = ''))")],
            "controls": [{"default": image("pokedex/slot", (25, 25))}, {"hover": image("pokedex/slot_select", (25, 25), layer=4)},
                         {"pressed": image("pokedex/slot_select", (25, 25), layer=4)},
                         {"pic": image(None, (23, 23), (1, 1), 2, BUTTON_IMAGE)},
                         {"no": label("#text", (2, 1), scale=0.5, layer=5, bindings=t + [
                             {"binding_type": "view", "source_property_name": "(#t - 'c' - '*')", "target_property_name": "#text"}])},
                         {"caught": image("pokedex/caught", (6, 6), (18, 18), 5, t + [has("c")])},
                         {"sel": image("pokedex/slot_select", (25, 25), layer=6, bindings=t + [has("*")])}]}


def dex_form():
    """PokedexScreen: buttons 0/1 the page arrows, 2 the page, 3 the chosen one, 4-5 its types, 6 seen, 7 owned,
    8 on the 25 slots; the title (less MARK 8) is its name, the form's text its entry."""
    title = [{"binding_name": "#title_text"}]
    c = [{"base": image("pokedex/base", (345, 207))},
         {"head": label("Pok\u00e9dex", (36, 14), (60, 9))},
         {"seen_i": image("pokedex/seen", (7, 7), (252, 15), 3)}, pc_text(6, (262, 14), (24, 9), 1.0, "left"),
         {"own_i": image("pokedex/caught", (7, 7), (290, 15), 3)}, pc_text(7, (300, 14), (24, 9), 1.0, "left"),
         pc_button(0, (26, 179), (12, 12), None), {"pl": label("<", (29, 181), (8, 9))},
         pc_text(2, (40, 181), (94, 9), 1.0, "center"),
         pc_button(1, (134, 179), (12, 12), None), {"pr": label(">", (137, 181), (8, 9))},
         {"view": image("pokedex/viewport", (139, 70), (180, 39), 2)},
         pc_text(3, (183, 30), (50, 9), 1.0, "left"),
         {"name": label("#text", (235, 30), (80, 9), bindings=title + [
             {"binding_type": "view", "source_property_name": f"(#title_text - '{MARK}8')", "target_property_name": "#text"}])},
         pc_image(3, (220, 44), (60, 60), 3), pc_image(4, (232, 112), (12, 12), 3), pc_image(5, (246, 112), (12, 12), 3),
         {"entry": label("#form_text", (189, 135), (121, 42), 0.5)},
         button([26, 13], [312, 186], "button.menu_exit",
                [{s_: image(t_, (26, 13))} for s_, t_ in (("default", "pc/exit"), ("hover", "pc/exit_hover"), ("pressed", "pc/exit_hover"))])]
    for k in range(25):
        c.append({f"s{k}@server_form.cl_dex_slot": {"collection_index": 8 + k, "offset": [26 + 27 * (k % 5), 39 + 27 * (k // 5)]}})
    return {"type": "panel", "size": [345, 207], "anchor_from": "center", "anchor_to": "center",
            "collection_name": "form_buttons",  # at() items read the form's buttons by index
            "bindings": title + [visible(f"(not ((#title_text - '{MARK}8') = #title_text))")], "controls": c}


def dex_art(gui, png):
    o = lambda n: Image.open(f"{gui}/pokedex/{n}.png").convert("RGBA")
    base = o("pokedex_base_red")
    base.alpha_composite(o("pokedex_screen"))
    png("pokedex/base", base)
    png("pokedex/viewport", o("pokedex_screen_info_viewport"))
    png("pokedex/slot", o("pokedex_slot").crop((0, 0, 25, 25)))
    png("pokedex/slot_select", o("slot_select").crop((0, 0, 25, 25)))
    q = Image.new("RGBA", (25, 25))  # Cobblemon's "?" for an unseen one, in the middle of the slot
    q.alpha_composite(o("pokedex_slot_unknown"), (8, 7))
    png("pokedex/unknown", q)
    png("pokedex/caught", o("caught_icon_small"))
    png("pokedex/seen", o("caught_seen_icon").crop((0, 0, 14, 14)))

def tidy(node, n=None):
    """Every entry of a "controls" list as Bedrock needs it: {name: control}. at() on a wrapper ({"x@y": {...}} plus
    collection_index) or a bare control (pc_text, pc_image) made entries Bedrock drops silently (the Summary and parts
    of the PC and Pokédex came out empty on a phone): the extra keys go into the control, a bare one gets a name."""
    n = n if n is not None else [0]
    if isinstance(node, list):
        return [tidy(x, n) for x in node]
    if not isinstance(node, dict):
        return node
    out = {}
    for k, v in node.items():
        if k == "controls" and isinstance(v, list):
            fixed = []
            for x in v:
                # one key holding a dict is a named control whatever its name ("text", "size"... are fine names)
                single = len(x) == 1 and isinstance(next(iter(x.values())), dict)
                named = list(x) if single else [key for key, val in x.items()
                                                 if isinstance(val, dict) and ("@" in key or key not in CONTROL_KEYS)]
                if len(named) == 1 and len(x) > 1:  # a wrapper with the control's keys beside it
                    key = named[0]
                    x = {key: dict(x[key], **{a: b for a, b in x.items() if a != key})}
                elif not named:  # a bare control
                    n[0] += 1
                    x = {f"cl_c{n[0]}": x}
                (key, val), = x.items()
                if key.startswith("button@"):  # several unnamed buttons in one panel: give each its own name
                    n[0] += 1
                    key = f"cl_b{n[0]}@" + key.split("@", 1)[1]
                fixed.append({key: tidy(val, n)})
            out[k] = fixed
        else:
            out[k] = tidy(v, n)
    return out


CONTROL_KEYS = {"bindings", "property_bag", "size", "offset", "anchor_from", "anchor_to", "layer", "texture", "text", "type",
                "collection_index", "collection_name", "color", "shadow", "font_scale_factor", "text_alignment", "alpha",
                "visible", "ignored", "localize", "grid_dimensions", "grid_item_template", "button_mappings", "modifications"}


def malformed(doc):
    """Entries of "controls" lists that aren't {name: control} (Bedrock drops them)."""
    bad = []
    def walk(c):
        if isinstance(c, dict):
            for k, v in c.items():
                if k == "controls" and isinstance(v, list):
                    for x in v:
                        if not (isinstance(x, dict) and len(x) == 1 and isinstance(next(iter(x.values())), dict)): bad.append(x)
                        elif "@" not in next(iter(x)) and "type" not in next(iter(x.values())): bad.append(x)  # wrapped twice
                        else: walk(next(iter(x.values())))
                elif isinstance(v, (dict, list)): walk(v)
        elif isinstance(c, list):
            for x in c: walk(x)
    walk(doc)
    return bad


def server_form():
    return tidy(_server_form())


def _server_form():
    STARTER = starter_form()
    vanilla_long_form = {  # Mojang's long_form, unchanged, for every other form
        "$title_panel": "common_dialogs.standard_title_label", "$title_size": ["100% - 15px", 10],
        "$title_max_size": ["100% - 15px", 10], "size": [225, 200], "$text_name": "#title_text",
        "$title_text_binding_type": "none", "$child_control": "server_form.long_form_panel", "layer": 2,
        "bindings": is_battle(False)}
    log = {"type": "image", "texture": TEX + "log", "size": [169, 55], "layer": 1,  # BattleMessagePane, (W-181, H-85)
           "anchor_from": "bottom_right", "anchor_to": "bottom_right", "offset": [-12, -30],
           "controls": [{"text": {"type": "label", "text": "#form_text", "color": [1, 1, 1], "layer": 2,
                                  "anchor_from": "top_left", "anchor_to": "top_left", "offset": [8, 5], "size": [153, 46]}}]}
    def hint(state):
        return {state: {"type": "label", "text": "Tap here to move around.", "color": [1, 1, 1], "alpha": 0.75,
                        "layer": 3, "size": ["default", "default"]}}
    # BattleGUI hide_label at (W/2, H/5), here a button: closing the form is how a Bedrock player stands up
    hide = {
        "size": [140, 12], "anchor_from": "top_middle", "anchor_to": "top_middle", "offset": [0, "20%"],
        "$pressed_button_name": "button.menu_exit",
        "controls": [hint("default"), hint("hover"), hint("pressed")],
        "bindings": [{"binding_name": "#title_text"}, {"binding_type": "view", "target_property_name": "#visible",
                      "source_property_name": f"(not ((#title_text - '{MARK}1') = #title_text))"}]}
    battle = {"type": "panel", "size": ["100%", "100%"], "bindings": is_battle(True), "controls": [
        {"log": log}, {"hide@common.button": hide},
        # BattleGeneralActionSelection: 90x26 tiles 3 px apart, top left at (12, H-85)
        {"menu": grid(1, [2, 2], [93, 29], [12, -27])},
        # BattleMoveSelection: 92x24 tiles 13/5 px apart from (20, H-84); Battle.java pads to 4 moves, back is 5th
        {"moves": grid(2, [2, 3], [105, 29], [20, 3])},
        # BattleSwitchPokemonSelection: 94x29 tiles 4/2 px apart; left-aligned like the Java screen; back is 6th
        {"party": grid(3, [2, 4], [98, 31], [12, -68])}]}
    return {
        "namespace": "server_form",
        # Mojang's screen plus force_render_below: the HUD (hotbar, hearts, HP bars) stays up under the battle form
        "third_party_server_screen@common.base_screen": {
            "$screen_content": "server_form.main_screen_content", "force_render_below": True,
            "button_mappings": [{"from_button_id": "button.menu_cancel", "to_button_id": "button.menu_exit",
                                 "mapping_type": "global"}]},
        "main_screen_content": {"type": "panel", "size": ["100%", "100%"], "controls": [{"server_form_factory": {
            "type": "factory", "control_ids": {"long_form": "@server_form.long_form", "custom_form": "@server_form.custom_form"}}}]},
        "long_form": {"type": "panel", "size": ["100%", "100%"], "controls": [
            {"vanilla@server_form.cl_vanilla_long_form": {}}, {"battle@server_form.cl_battle": {}},
            {"starter@server_form.cl_starter": {}}, {"pc@server_form.cl_pc": {}}, {"summary@server_form.cl_summary": {}}, {"learn@server_form.cl_learn": {}}, {"dex@server_form.cl_dex": {}}]},
        "cl_starter": STARTER[0], **STARTER[1],
        "cl_pc": pc_form(),
        "cl_summary": summary_form(),
        "cl_learn": learn_form(),
        "cl_dex": dex_form(),
        "cl_dex_slot@common.button": dex_slot(),
        "cl_pc_cell@common.button": pc_cell(),
        "cl_vanilla_long_form@common_dialogs.main_panel_no_buttons": vanilla_long_form,
        "cl_battle": battle,
        "cl_cell_1": cell([93, 29], [90, 26], [6, 8]),
        "cl_cell_2": cell([105, 29], [92, 24], [17, 2], back_offset=[-11, 4]),     # back: (9, H-22) from cell (0, 58)
        "cl_cell_3": cell([98, 31], [94, 29], [5, 4], back_offset=[-101, 108]),    # back: from cell (98, 62)
    }


# PartyPanel.java's Bedrock party: a title (its display hidden; a subtitle alone never reaches the HUD) of "|CL", the party's size, then six
# slots of plain ASCII (Bedrock cuts strings by bytes), each "Z<i>" then fixed-width fields padded with '~'. Must match
# PartyPanel.FIELDS. Bedrock's '%.Ns' cut only reads two digits of N, so nothing is cut 100+ characters in: a slot is
# reached by cutting whole slots off one at a time (each starts with its unique "Z<i>", so "-" removes only it).
# No field is all digits: Bedrock turns those into numbers, which a picture's name or a comparison can't use, so a
# number field starts with a letter (n party size, a-d slot, L level, h HP, x EXP, s scroll; pop-up fades a-h).
HUD_FIELDS = [("st", 1), ("lv", 4), ("nm", 12), ("g", 1), ("hp", 3), ("xp", 3), ("sc", 3), ("ev", 2), ("mv", 2),
              ("ea", 1), ("et", 11), ("pt", 40)]  # pt: the portrait's name under textures/items/portrait/
HUD_HEAD = 5  # "|CL", then n and the party's size
HUD_SLOT = 2 + sum(w for _, w in HUD_FIELDS)  # "Z<i>" and the fields
HUD_T = "#hud_title_text_string"
# Bedrock folds whatever follows a '%.Ns' cut into the cut (a cut then "- '~'" strips first, cuts after), so every
# field is a chain of one-step view bindings through a control's own properties: the title less Geyser's colour code,
# less the field's start, cut to its width, then used.


def view(src, dst):
    return {"binding_type": "view", "source_property_name": src, "target_property_name": dst}


def hud_chain(fields, head=HUD_HEAD, seg=HUD_SLOT):
    """Bindings putting each field (slot, offset in it, width) in #cl_f0, #cl_f1, ... (and a property bag for them);
    slot None: the offset is from the title's start. #cl_s0 is the title less its header, #cl_s<k+1> less k+1 slots."""
    b = [{"binding_name": HUD_T}, view(f"(({HUD_T} - '§r') - '§f')", "#cl_t")]
    top = max([i for i, _, _ in fields if i is not None], default=-1)
    if top >= 0:
        b.append(view(f"(#cl_t - ('%.{head}s' * #cl_t))", "#cl_s0"))
        for k in range(top):
            b.append(view(f"(#cl_s{k} - ('%.{seg}s' * #cl_s{k}))", f"#cl_s{k + 1}"))
    for j, (i, k, n) in enumerate(fields):
        src = "#cl_t" if i is None else f"#cl_s{i}"
        assert k < 100 and n < 100, "Bedrock's cut reads two digits"
        b += [view(f"({src} - ('%.{k}s' * {src}))", f"#cl_a{j}"), view(f"('%.{n}s' * #cl_a{j})", f"#cl_f{j}")]
    bag = {"#cl_t": ""} | {f"#cl_s{k}": "" for k in range(top + 1)} | {f"#cl_{x}{j}": "" for j in range(len(fields)) for x in "af"}
    return b, bag


def hud_field(i, name, part=None):
    """(slot, offset in it, width) of slot i's field (or its part-th character)."""
    k = 2
    for f, w in HUD_FIELDS:
        if f == name:
            return (i, k + part, 1) if part is not None else (i, k, w)
        k += w


def hud_slot(i, y, s, anchor):
    """Slot i's controls at y (from the anchor) at scale s, as PartyPanel.draw lays them out."""
    sel = 6 if i == 0 else 0
    f = lambda n, part=None: hud_field(i, n, part)

    def put(c, x, yy, w, h, layer, fields=(), finals=()):
        """finals: (source using #cl_f0..., target property), after the fields' chain."""
        c.update({"size": [w * s, h * s], "offset": [x * s, (yy + y) * s], "anchor_from": anchor, "anchor_to": "top_left",
                  "layer": layer})
        if fields:
            b, bag = hud_chain(fields)
            c["bindings"] = b + [view(src, dst) for src, dst in finals]
            c["property_bag"] = bag
        return c

    def img(tex, x, yy, w, h, layer, fields=(), tex_expr=None, show=None):
        c = {"type": "image"}
        if tex:
            c["texture"] = TEX + tex
        finals = ([(f"('{TEX}' + {tex_expr})", "#texture")] if tex_expr else []) + ([(show, "#visible")] if show else [])
        return put(c, x, yy, w, h, layer, fields, finals)

    def text(x, yy, w, align, fields=(), src=None, show=None, static=None):
        c = {"type": "label", "text": static or "#text", "color": [1, 1, 1], "shadow": True, "localize": False,
             "font_scale_factor": 0.5 * s, "text_alignment": align}
        finals = ([(src, "#text")] if src else []) + ([(show, "#visible")] if show else [])
        return put(c, x, yy, w, 5, 5, fields, finals)

    tex = lambda name, prefix: ([f(name)], f"'{prefix}' + #cl_f0")
    c = [
        img("hud/portrait_bg", 22 + sel, 2, 21, 21, 1),
        put({"type": "image"}, 22 + sel, 2, 21, 21, 2, [f("pt")], [("('textures/items/portrait/' + (#cl_f0 - '~'))", "#texture"),
                                                               ("(not ((#cl_f0 - '~') = ''))", "#visible")]),
        img(None, 0, 0, 62, 30, 3, *tex("st", "hud/slot_")),
        img(None, 24 + sel, 4, 17, 17, 4, *tex("sc", "hud/scroll_"), "(not (#cl_f0 = 's00'))"),
        text(sel - 3, 13, 20, "center", static="Lv."),
        # "L19~" less '~' then 'L' leaves "19", which Bedrock makes a number a label won't show: '§r' + it is text
        text(sel - 3, 18, 20, "center", [f("lv")], "('§r' + ((#cl_f0 - '~') - 'L'))"),
        text(sel + 2, 25, 40, "left", [f("nm")], "(#cl_f0 - '~')"),
        img(None, sel + 40, 22, 2.5, 3.5, 5, *tex("g", "hud/g_"), "(not (#cl_f0 = 'n'))"),
        img("hud/ball", sel + 43, 22, 9, 11, 5),
        img(None, sel + 46, 5, 2, 18, 4, *tex("hp", "hud/hp_"), "(not (#cl_f0 = 'h00'))"),
        img(None, sel + 49, 5, 1, 18, 4, *tex("xp", "hud/exp_"), "(not (#cl_f0 = 'x00'))"),
        text(sel + 57, 17, 40, "left", [f("et"), f("ea")], "(#cl_f0 - '~')", "(not (#cl_f1 = '-'))"),
    ]
    for kind, spots in (("ev", ((56, 4, "a"), (78, 4, "b"))), ("mv", ((56, 4, "a"), (56, 17, "b")))):
        for x, yy, pos in spots:  # PartyPanel.draw moves a pop-up aside for the other one or "+N EXP"
            c.append(img(None, sel + x, yy, 18.5, 10, 6, [f(kind, 0), f(kind, 1)], f"'hud/{kind}_' + #cl_f0",
                         f"((not (#cl_f0 = '-')) and (#cl_f1 = '{pos}'))"))
    return [{f"s{i}_{k}": x} for k, x in enumerate(c)]


# Battle.java / BattleUi.java's Bedrock battle: a title "|CB" then six fixed-width tiles (0-2 your side, top left;
# 3-5 the other side, top right), Cobblemon's BattleOverlay tiles. Must match BattleTiles.FIELDS (both products).
# st: 'a' if the tile is used; pt: portrait path; nm name; lv "L19"; g m/f/n; hb HP bar step "b00".."b48";
# ht "32/32" (yours) or "100%" (theirs); su status "brn"... or "---".
BT_FIELDS = [("st", 1), ("pt", 40), ("nm", 12), ("lv", 4), ("g", 1), ("hb", 3), ("ht", 7), ("su", 3)]
BT_HEAD = 3
BT_SLOT = 2 + sum(w for _, w in BT_FIELDS)  # "Z<i>" and the fields, as the party's slots
BT_STEPS = 48
BT_STATUS = ("brn", "frz", "par", "psn", "slp", "tox")


def bt_field(i, name):
    k = 2
    for f, w in BT_FIELDS:
        if f == name:
            return i, k, w
        k += w


def bt_tile(i, s, top):
    """Tile i at scale s, top px down: the positions of the Java overlay (BattleUi.panel / Battle.panel)."""
    own = i < 3
    row = i % 3
    anchor = "top_left" if own else "top_right"
    x0 = 12 if own else -152
    y0 = top + 45 * row
    f = lambda n: bt_field(i, n)

    def put(c, x, y, w, h, layer, fields=(), finals=()):
        c.update({"size": [w * s, h * s], "offset": [(x0 + x) * s, (y0 + y) * s], "anchor_from": anchor, "anchor_to": "top_left",
                  "layer": layer})
        if fields:
            b, bag = hud_chain(fields, BT_HEAD, BT_SLOT)
            c["bindings"] = b + [view(src, dst) for src, dst in finals]
            c["property_bag"] = bag
        return c

    def text(x, y, w, src, field, scale=1.0, align="left"):
        """w None: sized to its text (no ellipsis)."""
        c = {"type": "label", "text": "#text", "color": [1, 1, 1], "shadow": True, "localize": False,
             "font_scale_factor": scale * s, "text_alignment": align}
        c = put(c, x, y, w or 1, 9 * scale, 6, [f(field)], [(src, "#text")])
        if w is None:
            c["size"] = ["default", "default"]
        return c

    px = 5 if own else 107
    ib = 40 if own else 7
    c = [
        put({"type": "image", "texture": TEX + "battle/underlay"}, px, 8, 28, 28, 1),
        put({"type": "image"}, px, 8, 28, 28, 2, [f("pt")], [("('textures/items/portrait/' + (#cl_f0 - '~'))", "#texture"),
                                                              ("(not ((#cl_f0 - '~') = ''))", "#visible")]),
        put({"type": "image", "texture": TEX + ("battle/info" if own else "battle/info_flipped")}, 0, 0, 140, 40, 3),
        text(ib, 7, 60, "(#cl_f0 - '~')", "nm"),
        # "L10~" less '~' and 'L' is "10", a number a label won't show: 'Lv.' + it is text, sized to fit (no ellipsis)
        text(ib + 66, 7, None, "('Lv.' + ((#cl_f0 - '~') - 'L'))", "lv"),
        put({"type": "image"}, ib + 60, 8, 5, 7, 6, [f("g")], [(f"('{TEX}hud/g_' + #cl_f0)", "#texture"), ("(not (#cl_f0 = 'n'))", "#visible")]),
        put({"type": "image"}, ib - 2, 19, 97, 4, 5, [f("hb")],
            [(f"('{TEX}battle/hp{'o' if own else 'f'}_' + #cl_f0)", "#texture"), ("(not (#cl_f0 = 'b00'))", "#visible")]),
        text(ib + (39 if own else 44), 24, None, "('§r' + (#cl_f0 - '~'))", "ht", 0.5),
        put({"type": "image"}, ib - 2, 25, 37, 3.5, 6, [f("su")], [(f"('{TEX}battle/status_' + #cl_f0)", "#texture"),
                                                                    ("(not ((#cl_f0 = '---') or (#cl_f0 = '')))", "#visible")]),
    ]
    return [{f"b{i}_{k}": x} for k, x in enumerate(c)]


def bt_screen():
    """The battle tiles, shown while the title carries "|CB"; a tile while its st is 'a'. Touch screens: 3/4 size,
    below the hearts."""
    def tiles(s, top):
        return [{f"bt{i}": hud_panel([bt_field(i, "st")], "(#cl_f0 = 'a')", bt_tile(i, s, top(i)), BT_HEAD, BT_SLOT)} for i in range(6)]
    on = [{"binding_name": HUD_T}, view(f"(not ({HUD_T} = ({HUD_T} - '|CB')))", "#visible")]
    return {"type": "panel", "size": ["100%", "100%"], "layer": 1, "bindings": on, "controls": [
        {"desktop": {"type": "panel", "size": ["100%", "100%"], "ignored": "$touch", "controls": tiles(1, lambda i: 10)}},
        # touch: yours below the hearts and the coordinates line ("Position:"), theirs below the hunger bar
        {"touch": {"type": "panel", "size": ["100%", "100%"], "ignored": "(not $touch)",
                   "controls": tiles(0.75, lambda i: (50 if i < 3 else 22) / 0.75)}}]}


def hud_panel(fields, show, controls, head=HUD_HEAD, seg=HUD_SLOT):
    b, bag = hud_chain(fields, head, seg)
    return {"type": "panel", "size": ["100%", "100%"], "bindings": b + [view(show, "#visible")], "property_bag": bag,
            "controls": controls}


def hud_screen():
    """The party down the left edge (PartyOverlay: centred, so a column per party size); on a touch screen at 3/4 size
    from the top, clear of the D-pad. The title that carries it isn't shown."""
    desktop = [{f"n{n}": hud_panel([(None, 3, 2)], f"(#cl_f0 = 'n{n}')", [c for i in range(n) for c in hud_slot(i, -n * 15 - 10 + 34 * i, 1, "left_middle")])}
               for n in range(1, 7)]
    # a touch screen's slot shows while it holds a Pokémon: its first field is a-d (padding is '~', past the end '')
    touch = [{f"t{i}": hud_panel([hud_field(i, "st")], "(not ((#cl_f0 = '~') or (#cl_f0 = '')))", hud_slot(i, 8 / 0.75 + 34 * i, 0.75, "top_left"))}
             for i in range(6)]
    b, bag = hud_chain([(None, 3, 2)])
    party = {"type": "panel", "size": ["100%", "100%"], "layer": 1, "property_bag": bag,  # "|CLn0": hidden
             "bindings": b + [view(f"(not ({HUD_T} = ({HUD_T} - '|CL')))", "#cl_on"), view("(#cl_on and (not (#cl_f0 = 'n0')))", "#visible")],
             "controls": [{"desktop": {"type": "panel", "size": ["100%", "100%"], "ignored": "$touch", "controls": desktop}},
                          {"touch": {"type": "panel", "size": ["100%", "100%"], "ignored": "(not $touch)", "controls": touch}}]}
    debug = []
    if os.environ.get("CL_HUD_DEBUG"):  # a line at the top: the pack loaded, touch or not, the title's start
        lab = lambda t, ig, x: {"type": "label", "text": t, "color": [1, 1, 0], "layer": 40, "anchor_from": "top_middle",
                                "anchor_to": "top_middle", "offset": [x, 24], "size": ["default", "default"], **({"ignored": ig} if ig else {})}
        raw = lab("#text", None, 40)
        raw["bindings"] = [{"binding_name": "#hud_title_text_string"}, {"binding_type": "view",
                           "source_property_name": "('[' + ('%.24s' * #hud_title_text_string) + ']')", "target_property_name": "#text"}]
        debug = [{"cl_dbg_t": lab("CL touch", "(not $touch)", -60)}, {"cl_dbg_d": lab("CL desk", "$touch", -60)}, {"cl_dbg_raw": raw}]
    return {"namespace": "hud",
            "cl_party": party,
            "cl_battle_tiles": bt_screen(),
            "root_panel": {"modifications": [{"array_name": "controls", "operation": "insert_back",
                                              "value": [{"cl_party@hud.cl_party": {}}, {"cl_battle_tiles@hud.cl_battle_tiles": {}}] + debug}]},
            # Mojang's title, its bindings only (the rest stays Mojang's): not shown when it carries the party or a battle
            "hud_title_text": {"bindings": [{"binding_name": HUD_T},
                                            view(f"(({HUD_T} = ({HUD_T} - '|CL')) and ({HUD_T} = ({HUD_T} - '|CB')))", "#visible")]}}


def hud_art(gui, png, hp_colour):
    o = lambda n: Image.open(f"{gui}/party/{n}.png").convert("RGBA")
    for k, n in enumerate(("party_slot", "party_slot_active", "party_slot_fainted", "party_slot_fainted_active")):
        png(f"hud/slot_{'abcd'[k]}", o(n))
    png("hud/portrait_bg", o("party_slot_portrait_background"))
    png("hud/g_m", o("party_gender_male"))
    png("hud/g_f", o("party_gender_female"))
    png("hud/ball", Image.open(f"{gui}/ball/poke_ball.png").convert("RGBA").crop((0, 0, 18, 22)))
    strip = o("party_slot_portrait_level_up")
    from build_hud import scroll, BAR
    for step in range(1, 15):
        png(f"hud/scroll_s{step:02d}", scroll(strip, step))
    exp = (int(0.2 * 255), int(0.65 * 255), int(0.84 * 255), 255)
    for h in range(1, BAR + 1):
        for kind, w, rgba in (("hp", 2, hp_colour(h / BAR) + (255,)), ("exp", 1, exp)):
            bar = Image.new("RGBA", (w, BAR))
            bar.paste(rgba, (0, BAR - h, w, BAR))
            png(f"hud/{kind}_{kind[0] if kind == 'hp' else 'x'}{h:02d}", bar)
    for kind, n in (("ev", "party_slot_notification_evolution"), ("mv", "party_slot_notification_new_move")):
        img = o(n)
        for fade in range(8):  # PartyPanel's fade steps: 0 opaque, 7 nearly gone
            a = img.getchannel("A").point(lambda v: v * (8 - fade) // 8)
            png(f"hud/{kind}_{'abcdefgh'[fade]}", Image.merge("RGBA", img.split()[:3] + (a,)))


def bt_art(gui, png, hp_colour):
    o = lambda n: Image.open(f"{gui}/battle/{n}.png").convert("RGBA")
    png("battle/info", o("battle_info_base"))
    png("battle/info_flipped", o("battle_info_base_flipped"))
    png("battle/underlay", o("battle_info_underlay"))
    for st in BT_STATUS:
        png(f"battle/status_{st}", o(f"battle_status_{st}"))
    for k in range(1, BT_STEPS + 1):  # 97 px bars, yours filling from the left, theirs from the right
        w = round(97 * k / BT_STEPS)
        for side, x in (("o", 0), ("f", 97 - w)):
            bar = Image.new("RGBA", (97, 4))
            bar.paste(hp_colour(k / BT_STEPS) + (255,), (x, 0, x + w, 4))
            png(f"battle/hp{side}_b{k:02d}", bar)


def portrait_names(out):
    src = os.path.join(os.path.dirname(out), "portraits")
    return sorted(n[:-4] for n in os.listdir(src) if not n.endswith("_r.png")) if os.path.isdir(src) else []


def build(gui, out):
    files = {}

    def png(name, img):
        buf = io.BytesIO()
        img.save(buf, "PNG")
        files[f"{TEX}{name}.png"] = buf.getvalue()

    for k in ["fight", "switch", "bag", "run"]:
        t = Image.open(f"{gui}/battle/battle_menu_{k}.png").convert("RGBA")
        png(f"menu_{k}", t.crop((0, 0, 90, 26)))
        png(f"menu_{k}_hover", t.crop((0, 26, 90, 52)))
    move, over = (Image.open(f"{gui}/battle/{n}.png").convert("RGBA") for n in ("battle_move", "battle_move_overlay"))
    for t, rgb in zip(TYPES, TYPE_RGB):
        for v, suffix in ((0, ""), (1, "_hover")):
            img = tint(move.crop((0, 24 * v, 92, 24 * v + 24)), rgb)
            img.alpha_composite(over)
            png(f"move_{t}{suffix}", img)
    party = Image.open(f"{gui}/battle/party_select.png").convert("RGBA")
    png("party", party.crop((0, 0, 94, 29)))
    png("party_hover", party.crop((0, 29, 94, 58)))
    back = Image.open(f"{gui}/battle/battle_back.png").convert("RGBA")
    png("back", back.crop((0, 0, 58, 34)))
    png("back_hover", back.crop((0, 34, 58, 68)))
    png("none", Image.new("RGBA", (1, 1)))
    png("log", Image.open(f"{gui}/battle/battle_log.png").convert("RGBA"))
    # the starter screen (starter_form): Cobblemon's art at its own sizes; the cards are build_cards.py's
    sel = f"{gui}/starterselection"
    o = lambda n: Image.open(f"{sel}/{n}.png").convert("RGBA")
    base = Image.new("RGBA", (239, 197))
    base.alpha_composite(o("background"), (6, 17))
    base.alpha_composite(o("base"))
    base.paste((0, 0, 0, 255), (231, 27, 234, 172))  # the scrollbar's track
    png("starter_base", base)
    png("starter_choose", o("choose_button").crop((0, 0, 106, 14)))
    png("starter_choose_hover", o("choose_button").crop((0, 14, 106, 28)))
    for k, v in (("", 0), ("_hover", 1)):
        b = Image.open(f"{gui}/common/back_button.png").convert("RGBA").crop((0, 13 * v, 26, 13 * v + 13)).resize((16, 12), Image.NEAREST)
        icon = Image.open(f"{gui}/common/back_button_icon.png").convert("RGBA")
        b.alpha_composite(icon.resize((icon.width // 2, icon.height // 2), Image.NEAREST), (3, 3))
        png(f"starter_exit{k}", b)
    for n, k in (("starter_selection", "starter_tile"), ("starter_selection_active", "starter_tile_active")):
        png(k, o(n).crop((0, 0, 25, 25)))
        png(k + "_hover", o(n).crop((0, 25, 25, 50)))
    block = Image.new("RGBA", (89, 40))
    block.alpha_composite(o("selection_container_header"))
    block.alpha_composite(o("selection_container_edge"), (0, 7))
    for y in range(8, 39):
        block.alpha_composite(o("selection_container"), (0, y))
    block.alpha_composite(o("selection_container_footer"), (0, 39))
    png("starter_block", block)
    up = Image.new("RGBA", (7, 4))
    for y in range(4):
        up.paste((255, 255, 255, 255), (3 - y, y, 4 + y, y + 1))
    png("starter_up", up)
    thumb = Image.new("RGBA", (5, 1))
    thumb.paste((198, 198, 198, 255), (1, 0, 4, 1))
    png("starter_thumb", thumb)  # the scrollbar thumb on the checked page's segment
    png("starter_track_hover", Image.new("RGBA", (5, 1), (255, 255, 255, 60)))
    png("starter_down", up.transpose(Image.FLIP_TOP_BOTTOM))
    png("starter_ball", Image.open(f"{gui}/../item/poke_balls/poke_ball.png").convert("RGBA").resize((8, 8), Image.NEAREST))
    pc_art(gui, png, out)  # PcScreen.bedrock
    summary_art(gui, png)  # SummaryScreen
    dex_art(gui, png)  # PokedexScreen
    hud_art(gui, png, hp_colour)  # PartyPanel
    bt_art(gui, png, hp_colour)  # the battle tiles (bt_screen)
    for n in ("party_slot", "party_slot_active", "party_slot_fainted", "party_slot_fainted_active",
              "party_slot_portrait_background", "party_gender_male", "party_gender_female"):  # the client mod's party HUD
        png("party/" + n, Image.open(f"{gui}/party/{n}.png").convert("RGBA"))
    for kind, src in (("card", "cards_full"), ("tile", "tiles")):  # Cobblemon's own card, not Java's narrower one
        d = os.path.join(os.path.dirname(out), src)
        for n in sorted(os.listdir(d)) if os.path.isdir(d) else []:
            files[f"{TEX}{kind}/{n}"] = open(f"{d}/{n}", "rb").read()

    # Poké Ball: Geyser maps the Java item (item_model cobblemon:poke_ball) to a Bedrock item with Cobblemon's icon
    files["textures/items/poke_ball.png"] = open(f"{gui}/../item/poke_balls/poke_ball.png", "rb").read()
    textures = {"cobblemonlink:poke_ball": {"textures": ["textures/items/poke_ball"]}}
    for name in portrait_names(out):  # menu icons (build_all.py portraits), Geyser items on minecraft:paper
        icon = Image.open(f"{os.path.dirname(out)}/portraits/{name}.png").convert("RGBA").resize((28, 28), Image.NEAREST)
        buf = io.BytesIO()
        icon.save(buf, "PNG", optimize=True)
        files[f"textures/items/portrait/{short(name)}.png"] = buf.getvalue()  # 28 px, as on Java: the item atlas stays small
        textures[f"cobblemonlink:portrait_{name}"] = {"textures": [f"textures/items/portrait/{short(name)}"]}
    files["textures/item_texture.json"] = json.dumps({"resource_pack_name": "cobblemon-link UI", "texture_name": "atlas.items",
        "texture_data": textures}, indent=1).encode()

    form, hud = server_form(), hud_screen()
    assert not malformed(form) and not malformed(hud), "every control entry is {name: control}"
    hud_check(hud)
    import re  # Bedrock's '%.Ns' cut reads two digits of N: nothing longer than 99 anywhere
    for doc in (form, hud):
        assert not re.search(r"%\.\d{3,}s", json.dumps(doc)), "a 3-digit cut"
    files["ui/server_form.json"] = json.dumps(form, indent=1, ensure_ascii=False).encode()
    files["ui/hud_screen.json"] = json.dumps(hud, indent=1, ensure_ascii=False).encode()
    # Bedrock keeps a pack by uuid + version: a content-derived patch number makes every change a new download
    digest = hashlib.sha1(b"".join(k.encode() + files[k] for k in sorted(files))).digest()
    ver = [1, 0, int.from_bytes(digest[:3]) % 32768]  # Bedrock refuses a pack whose version part is over 65535 (81108: "Block")
    files["manifest.json"] = json.dumps({"format_version": 2, "header": {
        "name": "cobblemon-link UI", "description": "Cobblemon battle tiles (Cobblemon GUI art, CC BY-NC 3.0)",
        "uuid": UUIDS[0], "version": ver, "min_engine_version": [1, 21, 0]},
        "modules": [{"type": "resources", "uuid": UUIDS[1], "version": ver}]}, indent=1).encode()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)), files[name])
    return files


def payload(head, fields, slots):
    """BattleTiles/PartyHud's Bedrock title as Geyser sends it: head, then six "Z<i>" slots of '~'-padded fields."""
    out = "§r" + head
    for i in range(6):
        v = slots[i] if i < len(slots) and slots[i] else [""] * len(fields)
        out += f"Z{i}" + "".join(x[:w] + "~" * (w - len(x[:w])) for x, (_, w) in zip(v, fields))
    return out


def hud_check(hud):
    """Renders the battle tiles and the party from sample payloads through the pack's bindings (testclient/hudeval:
    Bedrock's '-' and two-digit cut) and fails on an empty field or a step value Bedrock may take for a binding."""
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "testclient"))
    import hudeval
    def run(panel, title):
        seen = {}
        def walk(c, name):
            v = hudeval.control(c, title)
            for k, x in v.items():
                assert not (isinstance(x, str) and x.startswith(("#", "$"))), f"{name} {k} = {x[:12]!r}...: '#' or '$' first"
            if v.get("#visible") is False: return
            seen[name] = v
            for e in c.get("controls", []):
                (n, cc), = e.items()
                walk(cc, n)
        walk(panel, "root")
        return seen
    mine = ["a", "mudkip", "Mudkip", "L10", "f", "b48", "33/33", "---"]
    foe = ["a", "mareep", "Mareep", "L14", "m", "b24", "50%", "par"]
    t = run(hud["cl_battle_tiles"]["controls"][0]["desktop"], payload("|CB", BT_FIELDS, [mine, [], [], foe]))
    assert set(n for n in t if n.startswith("bt")) == {"bt0", "bt3"}, sorted(t)
    for i, v in ((0, mine), (3, foe)):
        got = {x.get("#texture") for x in t.values()} | {x.get("#text") for x in t.values()}
        assert f"textures/items/portrait/{v[1]}" in got and v[2] in got and f"Lv.{v[3][1:]}" in got, (i, got)
        assert f"§r{v[6]}" in got and any(str(g).endswith(f"_{v[5]}") for g in got) and (v[7] == "---" or f"{TEX}battle/status_{v[7]}" in got), (i, got)
    slot = ["b", "L12", "Torchic", "m", "h20", "x10", "s00", "-", "-", "-", "", "torchic"]
    party = run(hud["cl_party"], payload("|CLn2", HUD_FIELDS, [slot, slot]))
    assert "n2" in party and "torchic" in str(party) and "Torchic" in str(party), sorted(party)


if __name__ == "__main__":
    f = server_form()
    assert f["cl_cell_2"]["controls"][1]["back"]["offset"] == [-11, 4], "fight back arrow lands at (9, H-22)"
    assert not malformed(f), malformed(f)[:2]
    assert BT_SLOT == 73 and bt_field(3, "st") == (3, 2, 1), "BattleTiles.FIELDS"
    form, _ = starter_form()
    names = [next(iter(c)) for c in form["controls"]]
    assert len(names) == len(set(names)), "control names are unique (toggle state is looked up by name)"
    assert sum(1 for n in names if n.startswith("sb")) == STARTER_MAX, "one I-choose-you button per starter"
    assert HUD_SLOT == 85 and hud_field(1, "lv") == (1, 3, 4), "PartyPanel.FIELDS' layout"
    assert all(len(short(n)) <= 40 for n in portrait_names(sys.argv[2])), "a portrait's name fits its field"
    import re  # nothing is cut 100+ characters in (Bedrock reads two digits of N)
    for doc in (hud_screen(),):
        assert not re.search(r"%\.\d{3,}s", json.dumps(doc)), "a 3-digit cut"
    assert short("a" * 40) == "a" * 40 and len(short("a" * 41)) == 40
    files = build(sys.argv[1], sys.argv[2])
    print(f"bedrock ui pack: {len(files)} files -> {sys.argv[2]}")
    mappings = os.path.join(os.path.dirname(sys.argv[2]), "geyser-mappings.json")  # -> Geyser's custom_mappings/
    with open(mappings, "w") as f:
        json.dump({"format_version": 2, "items": {"minecraft:snowball": [{
            "type": "definition", "model": "cobblemon:poke_ball", "bedrock_identifier": "cobblemonlink:poke_ball",
            "display_name": "Poké Ball", "bedrock_options": {"icon": "cobblemonlink:poke_ball"}}],
            "minecraft:paper": [{"type": "definition", "model": f"cobblemonlink:portrait/{n}",
                                 "bedrock_identifier": f"cobblemonlink:portrait_{n}",
                                 "bedrock_options": {"icon": f"cobblemonlink:portrait_{n}"}}
                                for n in portrait_names(sys.argv[2])]}}, f, indent=1)
    print(f"geyser item mappings -> {mappings}")
