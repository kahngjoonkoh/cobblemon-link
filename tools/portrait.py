"""Cobblemon's battle-tile portrait of a model, pre-rendered (vanilla clients can't draw a model inside the GUI).

render(bb, png, scale, translation, base, reversed) -> RGBA image, PX px per GUI px, of the 28 px portrait circle's square.
Follows api/gui/GuiUtils.drawPosablePortrait: scale 18, the poser's portraitScale / portraitTranslation, then
Y -32 deg (mirrored when reversed) and X 5 deg, lit by its two shader lights. Blockbench geometry (geo2bb's output)
in the first frame of its idle clip (the PORTRAIT pose is the standing one). ponytail: keyframe values only (no
molang between keys, no look-at), no emissive/overlay layers; cutout textures, nearest sampling.
"""
import base64, io, math
import numpy as np
from PIL import Image

PX = 3          # texture pixels per GUI pixel (Cobblemon draws it at the GUI scale; 3 is 1080p's)
SIZE = 28       # BattleOverlay.PORTRAIT_DIAMETER
FACES = {  # corners seen from outside: top-left, top-right, bottom-right, bottom-left (Blockbench's UV order)
    'north': lambda f, t: [(t[0], t[1], f[2]), (f[0], t[1], f[2]), (f[0], f[1], f[2]), (t[0], f[1], f[2])],
    'south': lambda f, t: [(f[0], t[1], t[2]), (t[0], t[1], t[2]), (t[0], f[1], t[2]), (f[0], f[1], t[2])],
    'east': lambda f, t: [(t[0], t[1], t[2]), (t[0], t[1], f[2]), (t[0], f[1], f[2]), (t[0], f[1], t[2])],
    'west': lambda f, t: [(f[0], t[1], f[2]), (f[0], t[1], t[2]), (f[0], f[1], t[2]), (f[0], f[1], f[2])],
    'up': lambda f, t: [(f[0], t[1], f[2]), (t[0], t[1], f[2]), (t[0], t[1], t[2]), (f[0], t[1], t[2])],
    'down': lambda f, t: [(f[0], f[1], t[2]), (t[0], f[1], t[2]), (t[0], f[1], f[2]), (f[0], f[1], f[2])],
}


def rot(deg):
    """Blockbench (three.js 'ZYX') euler rotation in degrees -> 3x3 matrix, x applied first."""
    x, y, z = (math.radians(d) for d in deg)
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def first_frame(bb, clip='idle'):
    """{bone name: (rotation, position)} at the clip's earliest keys, as Bedrock values (geo2bb keeps them raw)."""
    pose = {}
    for a in bb.get('animations', []):
        if a['name'] != clip:
            continue
        for an in a['animators'].values():
            first = {}
            for k in sorted(an['keyframes'], key=lambda k: k['time']):
                first.setdefault(k['channel'], k['data_points'][0])
            v = lambda ch: [float(first[ch][c]) for c in 'xyz'] if ch in first else [0.0, 0.0, 0.0]
            pose[an['name']] = (v('rotation'), v('position'))
    return pose


def quads(bb, pose=None):
    """(4 corner positions in model px, 4 uvs, texture index) for every visible cube face, bones posed."""
    pose = pose or {}
    parent = {}

    def walk(node, chain):
        for c in node.get('children', []):
            if isinstance(c, str):
                parent[c] = chain
            else:
                walk(c, chain + [c])
    for root in bb['outliner']:
        walk(root, [root])
    out = []
    for el in bb['elements']:
        inf = el.get('inflate', 0)
        f = [a - inf for a in el['from']]
        t = [a + inf for a in el['to']]
        chain = [(el.get('origin', [0, 0, 0]), el.get('rotation', [0, 0, 0]), [0, 0, 0])]
        for g in reversed(parent.get(el['uuid'], [])):
            # Bedrock key -> this space (Blockbench: x negated): rotation (-x, -y, z) on top of the bone's,
            # position (-x, y, z) moving the bone and its children (BedrockAnimation adds them to the ModelPart)
            r, d = pose.get(g['name'], ([0, 0, 0], [0, 0, 0]))
            rb = g.get('rotation', [0, 0, 0])
            chain.append((g['origin'], [rb[0] - r[0], rb[1] - r[1], rb[2] + r[2]], [-d[0], d[1], d[2]]))
        for name, face in el['faces'].items():
            if face.get('texture') is None or name not in FACES:
                continue
            u1, v1, u2, v2 = face['uv']
            uv = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            k = face.get('rotation', 0) // 90
            uv = uv[-k:] + uv[:-k] if k else uv
            p = np.array(FACES[name](f, t), float)
            for o, r, d in chain:
                if any(r):
                    p = (p - o) @ rot(r).T + o
                p = p + d
            out.append((p, uv, face['texture']))
    return out


def render(bb, png, scale, translation, base=1.0, reversed=False):
    """The battle tile's 28 px portrait (GuiUtils.drawPosablePortrait)."""
    sx = -1 if reversed else 1
    view = rot([0, -32 * sx, 0]) @ rot([5, 0, 0])  # mulPose(Y -32) then mulPose(X 5): x applied first
    # PoseStack: scale(18, 18, -18), translate(t.x, t.y + 1.5 ps, t.z - 4), scale(ps, ps, 1 / ps), view
    return draw(bb, png, base, view, (SIZE, SIZE), SIZE / 2 + 18 * translation[0] * sx, -3 + 18 * (translation[1] + 1.5 * scale),
                18 * scale, [[0.2, 1.0, -1.0], [0.1, 0.0, 8.0]])


def profile(bb, png, scale, translation, base=1.0, px=PX, size=(118, 100), origin=(59, -12), k=2.7 * 20):
    """drawProfilePokemon (rotation X 13, Y 35, the poser's profileScale / profileTranslation) into a size box, its
    origin at origin, k GUI px a block. Defaults: the starter screen's model (StarterSelectionScreen ModelWidget: a
    118x100 window, baseScale 2.7, offsetY -12, scale 20). TILE: a starter tile (CategoryList: 25 px, the model
    at (12.5, -6), scale 3.5 x 4.5)."""
    view = rot([13, 0, 0]) @ rot([0, 35, 0])  # fromEulerXYZDegrees(13, 35, 0): y applied first, then x
    return draw(bb, png, base, view, size, origin[0] + k * translation[0], origin[1] + k * (translation[1] + 1.5 * scale),
                k * scale, [[-1.0, 1.0, 1.0], [1.3, -1.0, 1.0]], px)


TILE = dict(size=(25, 25), origin=(12.5, -6), k=3.5 * 4.5)


def draw(bb, png, base, view, size, ox, oy, k, lights, px=PX):
    """Rasterise the model at PX per GUI px: GUI point = (ox, oy) + k * view(model point, blocks, y down)."""
    tex = np.asarray(Image.open(io.BytesIO(png)).convert('RGBA'), float) / 255
    tw, th = bb['resolution']['width'], bb['resolution']['height']
    color = np.zeros((size[1] * px, size[0] * px, 4))
    depth = np.full((size[1] * px, size[0] * px), -np.inf)
    lights = [np.array(v) / np.linalg.norm(v) for v in lights]
    for p, uv, _ in quads(bb, first_frame(bb)):
        # entity model space (TexturedModel: Bedrock's x and z, y flipped; geo2bb negated x): blocks, feet at 0.
        # geo2bb baked baseScale in; Cobblemon's GUI draws the raw model
        w = (p * [-1, -1, 1] / 16 / base) @ view.T
        gx, gy = ox + k * w[:, 0], oy + k * w[:, 1]
        if (gx[1] - gx[0]) * (gy[2] - gy[0]) - (gx[2] - gx[0]) * (gy[1] - gy[0]) <= 0:
            continue  # back face (entityCutout culls): front faces run clockwise on the y-down screen
        nv = np.cross(w[3] - w[0], w[1] - w[0])  # outward: corners run TL, TR, BR, BL seen from outside
        nv = nv / (np.linalg.norm(nv) or 1) * [1, 1, -1]  # GUI space, as the shader lights see it
        shade = min(1.0, 0.4 + 0.6 * sum(max(0.0, float(nv @ l)) for l in lights))  # light.glsl
        pts = np.stack([gx * px, gy * px, -w[:, 2]], 1)  # depth: toward the viewer is -z before the flip
        box = (min(u for u, _ in uv), min(v for _, v in uv), max(u for u, _ in uv), max(v for _, v in uv))
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(color, depth, pts[list(tri)], [uv[i] for i in tri], tex, tw, th, shade, box)
    return Image.fromarray((color * 255).round().astype(np.uint8), 'RGBA')


def raster(color, depth, pts, uv, tex, tw, th, shade, box):
    h, n = color.shape[:2]
    x0, y0 = np.floor(pts[:, :2].min(0)).astype(int)
    x1, y1 = np.ceil(pts[:, :2].max(0)).astype(int)
    x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, n), min(y1, h)
    if x0 >= x1 or y0 >= y1:
        return
    ys, xs = np.mgrid[y0:y1, x0:x1] + 0.5
    (ax, ay, _), (bx, by, _), (cx, cy, _) = pts
    area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
    if abs(area) < 1e-9:
        return
    w1 = ((xs - ax) * (cy - ay) - (cx - ax) * (ys - ay)) / area
    w2 = ((bx - ax) * (ys - ay) - (xs - ax) * (by - ay)) / area
    w0 = 1 - w1 - w2
    inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
    if not inside.any():
        return
    z = w0 * pts[0, 2] + w1 * pts[1, 2] + w2 * pts[2, 2]
    u = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]
    v = w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1]
    # stay inside the face's own texels: at a mirrored (u2 < u1) edge, flooring u1 would read the next texel over
    eps = 1e-3
    u = np.clip(u, box[0] + eps, max(box[0] + eps, box[2] - eps))
    v = np.clip(v, box[1] + eps, max(box[1] + eps, box[3] - eps))
    ti = np.clip((v * tex.shape[0] / th).astype(int), 0, tex.shape[0] - 1)
    tj = np.clip((u * tex.shape[1] / tw).astype(int), 0, tex.shape[1] - 1)
    texel = tex[ti, tj]
    region = depth[y0:y1, x0:x1]
    hit = inside & (texel[..., 3] >= 0.1) & (z > region)  # cutout: alpha < 0.1 is discarded
    region[hit] = z[hit]
    c = color[y0:y1, x0:x1]
    c[hit, :3] = texel[hit, :3] * shade
    c[hit, 3] = 1


def demo():
    """A 16 px cube seen at the portrait angle: its front (south) face is lit and the back never shows."""
    png = io.BytesIO()
    Image.new('RGBA', (16, 16), (200, 200, 200, 255)).save(png, 'PNG')
    faces = {k: {'uv': [0, 0, 16, 16], 'texture': 0} for k in FACES}
    bb = {'resolution': {'width': 16, 'height': 16}, 'outliner': [],
          'elements': [{'uuid': 'a', 'from': [-8, 0, -8], 'to': [8, 16, 8], 'faces': faces}]}
    img = render(bb, png.getvalue(), 1.0, [0, -0.6, 0])
    assert img.getbbox(), "the cube should be visible"
    # outside faces face the light: the brightest face is bright, as on a lit cube (not a dim inside wall)
    rgba = np.asarray(img)
    assert rgba[..., 0][rgba[..., 3] > 0].max() > 150
    assert len(quads(bb)) == 6


if __name__ == '__main__':
    demo()
    print('ok')
