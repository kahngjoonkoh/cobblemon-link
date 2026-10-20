#!/usr/bin/env python3
"""The Paper version's Java UI art (assets/ui-pack.zip from tools/build_ui.py and friends, and the picture packs in
assets/pics) as one pack for the 1.21.1 client, merged into Polymer's pack by the mod (Ui.java). The fonts and textures
are the same; 26.3's shaders and item definitions are left out, and the text shader is rewritten for 1.21.1
(rendertype_text: the GUI is told apart by its orthographic projection, and the drop shadow of a coded colour is
hidden, as 1.21.1 text can't turn its shadow off).
usage: ui_pack.py UI_PACK.zip PICS_DIR OUT_DIR"""
import glob, json, os, shutil, sys, zipfile

# the Paper version's TEXT_VSH (build_ui.py) for 1.21.1: the same colour codes, see Ui.Overlay / Ui.Title
TEXT_VSH = r"""#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 pos = Position;
    vec4 color = Color;
    if (ProjMat[2][3] == 0.0) { // the GUI (orthographic)
        ivec3 c = ivec3(round(Color.rgb * 255.0));
        if (c.r == 253) { // a chest title piece (Ui.Title): b - 64 px down, grey level g
            pos.y += float(c.b) - 64.0;
            color = vec4(vec3(float(c.g) / 255.0), Color.a);
        } else if (c.r == 250) { // the party panel (PartyHud): left edge, b - 128 px from the screen's middle
            vec2 screen = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
            pos.x -= screen.x * 0.5;
            pos.y += float(c.b) - 128.0 - screen.y * 0.5 + 72.0;
            color = vec4(vec3((c.g & 4) == 4 ? 0.25 : 1.0), Color.a * float(8 - (c.g >> 5)) / 8.0);
        } else if (c.r >= 251 && c.r <= 254) { // Ui.Overlay: corners
            vec2 screen = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
            if ((c.g & 8) == 0) pos.x += (c.g & 1) == 1 ? screen.x * 0.5 : -screen.x * 0.5;
            if ((c.g & 16) == 16) { // an 8 px atlas sprite at 16 px, grown from its top left (vertices TL, BL, BR, TR)
                int v = gl_VertexID % 4;
                if (v == 1 || v == 2) pos.y += 8.0;
                if (v >= 2) pos.x += 8.0;
            }
            pos.y += (c.g & 2) == 2 ? 72.0 - float(c.b) : float(c.b) - screen.y + 72.0;
            vec3 rgb = c.r == 252 ? vec3(1.0, 0.333, 0.333) : c.r == 251 ? vec3(0.333) : vec3(1.0); // red, dark grey, white
            color = vec4(rgb * ((c.g & 4) == 4 ? 0.25 : 1.0), Color.a * float(8 - (c.g >> 5)) / 8.0);
        } else if ((c.r == 62 || c.r == 63) && !(c.g == 63 && (c.b == 63 || c.b == 21)) && !(c.g == 21 && (c.b == 21 || c.b == 63))
                   && !(c.g == 42 && c.b == 0)) {
            color.a = 0.0; // the drop shadow of a coded piece (vanilla's white, yellow, red, pink and gold shadows stay)
        }
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
"""

# no dimmed world behind a screen (Screen.renderTransparentBackground's 0xC0101010 / 0xD0101010)
POSITION_COLOR_VSH = r"""#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    ivec4 c = ivec4(round(Color * 255.0));
    vertexColor = c.rgb == ivec3(16) && (c.a == 192 || c.a == 208) ? vec4(0.0) : Color;
}
"""


def build(ui, pics, out):
    shutil.rmtree(out, ignore_errors=True)
    fonts = {}
    for z in [ui] + sorted(glob.glob(os.path.join(pics, '*.zip'))):
        with zipfile.ZipFile(z) as zf:
            for n in zf.namelist():
                if n.endswith('/') or n == 'pack.mcmeta' or n.startswith('assets/minecraft/shaders/') \
                        or '/items/' in n or n.startswith('assets/cobblemon/') or 'sprites/container/slot_highlight' in n:
                    continue
                if '/font/' in n and n.endswith('.json'):  # the picture packs split a font by region: merge them
                    fonts.setdefault(n, []).extend(json.loads(zf.read(n))['providers'])
                    continue
                p = os.path.join(out, n)
                os.makedirs(os.path.dirname(p), exist_ok=True)
                open(p, 'wb').write(zf.read(n))
    for n, providers in fonts.items():
        p = os.path.join(out, n)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump({'providers': providers}, open(p, 'w'), separators=(',', ':'))
    core = os.path.join(out, 'assets/minecraft/shaders/core')
    os.makedirs(core, exist_ok=True)
    open(os.path.join(core, 'rendertype_text.vsh'), 'w').write(TEXT_VSH)
    open(os.path.join(core, 'position_color.vsh'), 'w').write(POSITION_COLOR_VSH)
    json.dump({'pack': {'pack_format': 34, 'description': 'cobblemon-link UI (Cobblemon GUI art, CC BY-NC 3.0)'}},
              open(os.path.join(out, 'pack.mcmeta'), 'w'))
    return len(fonts)


def demo():
    import tempfile
    with tempfile.TemporaryDirectory() as t:
        ui, pics = os.path.join(t, 'ui.zip'), os.path.join(t, 'pics')
        os.makedirs(pics)
        with zipfile.ZipFile(ui, 'w') as w:
            w.writestr('assets/cobblemonlink/font/ui.json', json.dumps({'providers': [{'type': 'space'}]}))
            w.writestr('assets/minecraft/shaders/core/text.vsh', '26.3')
        for r in ('a', 'b'):
            with zipfile.ZipFile(os.path.join(pics, r + '.zip'), 'w') as w:
                w.writestr('assets/cobblemonlink/font/portrait.json', json.dumps({'providers': [{'type': 'bitmap', 'file': r}]}))
        build(ui, pics, os.path.join(t, 'out'))
        merged = json.load(open(os.path.join(t, 'out/assets/cobblemonlink/font/portrait.json')))
        assert [p['file'] for p in merged['providers']] == ['a', 'b']
        assert not os.path.exists(os.path.join(t, 'out/assets/minecraft/shaders/core/text.vsh'))
        assert 'ProjMat[2][3] == 0.0' in open(os.path.join(t, 'out/assets/minecraft/shaders/core/rendertype_text.vsh')).read()


if __name__ == '__main__':
    demo()
    print(build(sys.argv[1], sys.argv[2], sys.argv[3]), 'fonts')
