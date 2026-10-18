package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.object.ObjectContents;

/**
 * Item icons for the battle hotbar (Hotbar.java), made from the vanilla client's own models: Mojang's client jar is
 * downloaded once (checked against Mojang's SHA-1) and nothing from it is shipped with this project.
 * Flat items are their texture in the client's atlas, as text objects. Block items are rendered here the way the
 * client draws them in a slot (FaceBakery's faces and UVs, the model's gui transform, Lighting's two GUI lights), into
 * a font that a generated pack carries, at 4x so they stay as sharp as the client's own.
 * ponytail: of the entity-drawn items only the shield (see entity()); chests, heads, banners... show nothing; no glint,
 * tints only for constant/grass/foliage/dye defaults.
 */
final class Icons {
    static final Key FONT = Key.key("cobblemonlink", "icons");
    static final int PX = 64, COLS = 32; // cell size (a 16 px slot at 4x), cells per sheet row
    static final char FIRST = '';
    // per face: its 4 vertices (FaceInfo order) as min/max of x, y, z, and the normal
    private static final Map<String, String[]> FACES = Map.of(
            "down", new String[]{"001", "000", "100", "101"}, "up", new String[]{"010", "011", "111", "110"},
            "north", new String[]{"110", "100", "000", "010"}, "south", new String[]{"011", "001", "101", "111"},
            "west", new String[]{"010", "000", "001", "011"}, "east", new String[]{"111", "101", "100", "110"});
    private static final Map<String, double[]> NORMALS = Map.of("down", new double[]{0, -1, 0}, "up", new double[]{0, 1, 0},
            "north", new double[]{0, 0, -1}, "south", new double[]{0, 0, 1}, "west", new double[]{-1, 0, 0}, "east", new double[]{1, 0, 0});
    // Lighting.ITEMS_3D (gui_light "side") and ITEMS_FLAT ("front"), in the same space as the transformed normals
    private static final double[] LIGHT0, LIGHT1, FLAT0, FLAT1;

    static {
        double[] d0 = unit(new double[]{0.2, 1, -0.7}), d1 = unit(new double[]{-0.2, 1, 0.7});
        double[][] flat = mul(rotY(-Math.PI / 8), rotX(Math.PI * 3 / 4));
        double[][] pose = mul(mul(rotY(1.0821041), rotX(3.2375858)), flat);
        LIGHT0 = apply(pose, d0);
        LIGHT1 = apply(pose, d1);
        FLAT0 = apply(flat, d0);
        FLAT1 = apply(flat, d1);
    }

    /** A HUD glyph and its exact advance. */
    record Sprite(Component glyph, int advance) {}

    static final char HUD_FIRST = '\ue000';
    private static final String HUD_DIR = "assets/minecraft/textures/gui/sprites/hud/";
    // what Hud.extractItemHotbar / extractPlayerHealth draw (vehicle hearts never apply: the battle seat has no health)
    private static final java.util.regex.Pattern HUD = java.util.regex.Pattern.compile(
            "hotbar|hotbar_selection|hotbar_offhand_(left|right)|heart/(?!vehicle).*|food_.*|armor_.*|air.*");

    final Map<String, Component> icons = new HashMap<>(); // item id -> its icon
    final Map<String, Sprite> hud = new HashMap<>();      // vanilla HUD sprite name ("heart/full") -> glyph
    byte[] pack;                                          // the font pack: item icons and HUD sprites
    private final ZipFile jar;
    private final Map<String, BufferedImage> textures = new HashMap<>();

    private Icons(ZipFile jar) { this.jar = jar; }

    /** The client jar for this server's version, downloaded into dir once. */
    static Path clientJar(Path dir, String version) throws IOException {
        Path out = dir.resolve("client-" + version + ".jar");
        if (Files.exists(out)) return out;
        JsonObject manifest = json(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));
        for (JsonElement v : manifest.getAsJsonArray("versions")) {
            if (!v.getAsJsonObject().get("id").getAsString().equals(version)) continue;
            JsonObject client = json(URI.create(v.getAsJsonObject().get("url").getAsString()))
                    .getAsJsonObject("downloads").getAsJsonObject("client");
            byte[] bytes;
            try (InputStream in = URI.create(client.get("url").getAsString()).toURL().openStream()) {
                bytes = in.readAllBytes();
            }
            String sha1 = HexFormat.of().formatHex(sha1(bytes));
            if (!sha1.equals(client.get("sha1").getAsString())) throw new IOException("client jar SHA-1 mismatch");
            Files.write(out, bytes);
            return out;
        }
        throw new IOException("Mojang lists no client " + version);
    }

    static Icons build(Path clientJar) throws IOException {
        try (ZipFile z = new ZipFile(clientJar.toFile())) {
            Icons ic = new Icons(z);
            Map<String, BufferedImage> rendered = new TreeMap<>();
            z.stream().map(ZipEntry::getName).filter(n -> n.startsWith("assets/minecraft/items/") && n.endsWith(".json")).forEach(n -> {
                String id = n.substring(23, n.length() - 5);
                try {
                    JsonObject root = ic.read(n).getAsJsonObject("model"), node = ic.modelNode(root);
                    if (node != null) ic.item(id, root, node, rendered);
                } catch (RuntimeException | IOException e) {
                    // ponytail: an item this renderer doesn't understand just has no icon
                }
            });
            ic.pack = ic.pack(rendered);
            return ic;
        }
    }

    /** The "minecraft:model" (or "minecraft:special") node a client shows by default: select/condition/range fallbacks. */
    private JsonObject modelNode(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        String type = o.has("type") ? o.get("type").getAsString().replace("minecraft:", "") : "";
        if (type.equals("model") || type.equals("special")) return o;
        for (String k : List.of("fallback", "on_false"))
            if (o.has(k)) return modelNode(o.get(k));
        for (String k : List.of("entries", "cases", "models"))
            if (o.has(k) && !o.getAsJsonArray(k).isEmpty()) {
                JsonObject first = o.getAsJsonArray(k).get(0).getAsJsonObject();
                return modelNode(first.has("model") ? first.get("model") : first);
            }
        return null;
    }

    private void item(String id, JsonObject root, JsonObject node, Map<String, BufferedImage> rendered) throws IOException {
        JsonObject textures = new JsonObject();
        JsonArray elements = null;
        JsonObject gui = null;
        boolean generated = false, front = false;
        boolean special = node.get("type").getAsString().endsWith("special");
        String model = node.get(special ? "base" : "model").getAsString();
        for (int depth = 0; model != null && depth < 20; depth++) {
            String path = model.replace("minecraft:", "");
            if (path.equals("builtin/generated") || path.equals("item/generated")) generated = true;
            JsonObject m;
            try {
                m = read("assets/minecraft/models/" + path + ".json");
            } catch (IOException e) {
                break;
            }
            if (m.has("textures"))
                for (var t : m.getAsJsonObject("textures").entrySet()) if (!textures.has(t.getKey())) textures.add(t.getKey(), t.getValue());
            if (elements == null && m.has("elements")) elements = m.getAsJsonArray("elements");
            if (gui == null && m.has("display") && m.getAsJsonObject("display").has("gui")) gui = m.getAsJsonObject("display").getAsJsonObject("gui");
            if (m.has("gui_light") && depth == 0) front = m.get("gui_light").getAsString().equals("front");
            model = m.has("parent") ? m.get("parent").getAsString() : null;
        }
        View view = new View(gui, front);
        if (special) {
            List<Quad> quads = entity(node.getAsJsonObject("model").get("type").getAsString().replace("minecraft:", ""),
                    view, root.has("transformation") ? vec(root.getAsJsonObject("transformation").getAsJsonArray("scale")) : new double[]{1, 1, 1});
            if (quads != null) rendered.put(id, paint(quads));
            return;
        }
        if (generated || elements == null) { // flat: the client's own sprite, as a text object
            for (String layer : List.of("layer0", "layer1")) {
                String t = texture(textures, layer);
                if (t == null || t.contains("overlay")) continue; // a tinted overlay layer is blank without its tint
                String atlas = t.startsWith("item/") ? "items" : "blocks";
                icons.put(id, Component.object(ObjectContents.sprite(Key.key("minecraft", atlas), Key.key("minecraft", t))));
                return;
            }
            return;
        }
        rendered.put(id, paint(block(textures, elements, view, node.has("tints") ? node.getAsJsonArray("tints") : new JsonArray())));
    }

    /** One face on screen: PX-px slot coordinates of its 4 vertices, the texture px at each, and its depth. */
    private record Quad(double[][] screen, double depth, BufferedImage tex, double[][] uv) {}

    /** The model's gui transform (ItemTransform.apply) into the slot (scale 16, -16, 16), and the GUI lights for it. */
    private record View(double[][] r, double[] tr, double[] sc, double[] l0, double[] l1) {
        View(JsonObject gui, boolean front) {
            this(rotation(gui), gui != null && gui.has("translation") ? vec(gui.getAsJsonArray("translation")) : new double[3],
                    gui != null && gui.has("scale") ? vec(gui.getAsJsonArray("scale")) : new double[]{1, 1, 1},
                    front ? FLAT0 : LIGHT0, front ? FLAT1 : LIGHT1);
        }

        private static double[][] rotation(JsonObject gui) {
            double[] rot = gui != null && gui.has("rotation") ? vec(gui.getAsJsonArray("rotation")) : new double[3];
            return mul(mul(rotX(Math.toRadians(rot[0])), rotY(Math.toRadians(rot[1]))), rotZ(Math.toRadians(rot[2]))); // rotationXYZ
        }

        /** p in block units (0..1 across the model) -> {screen x, screen y, depth}. */
        double[] project(double[] p) {
            double[] v = new double[3];
            for (int k = 0; k < 3; k++) v[k] = (p[k] - 0.5) * sc[k]; // translate(-.5) is applied first
            v = apply(r, v);
            for (int k = 0; k < 3; k++) v[k] += tr[k] / 16;
            return new double[]{PX / 2.0 + PX * v[0], PX / 2.0 - PX * v[1], v[2]};
        }

        double[] normal(double[] n) { return apply(r, unit(n)); }

        double light(double[] n) { return Math.min(1, (Math.max(0, dot(l0, n)) + Math.max(0, dot(l1, n))) * 0.6 + 0.4); }
    }

    /** A block model's faces (FaceBakery: FaceInfo vertices, default UVs, UV rotation; element rotation). */
    private List<Quad> block(JsonObject textures, JsonArray elements, View view, JsonArray tints) throws IOException {
        List<Quad> quads = new ArrayList<>();
        for (JsonElement el : elements) {
            JsonObject e = el.getAsJsonObject();
            double[] from = vec(e.getAsJsonArray("from")), to = vec(e.getAsJsonArray("to"));
            double[][] er = identity();
            double[] origin = {8, 8, 8};
            if (e.has("rotation")) {
                JsonObject ro = e.getAsJsonObject("rotation");
                origin = vec(ro.getAsJsonArray("origin"));
                if (ro.has("axis")) {
                    double a = Math.toRadians(ro.get("angle").getAsDouble());
                    er = switch (ro.get("axis").getAsString()) { case "x" -> rotX(a); case "y" -> rotY(a); default -> rotZ(a); };
                } else { // rotationZYX(z, y, x)
                    er = mul(mul(rotZ(Math.toRadians(num(ro, "z"))), rotY(Math.toRadians(num(ro, "y")))), rotX(Math.toRadians(num(ro, "x"))));
                }
                if (ro.has("rescale") && ro.get("rescale").getAsBoolean()) {
                    for (int axis = 0; axis < 3; axis++) { // CuboidRotation.computeRescale: 1 / largest component of the turned axis
                        double max = Math.max(Math.abs(er[0][axis]), Math.max(Math.abs(er[1][axis]), Math.abs(er[2][axis])));
                        for (int row = 0; row < 3; row++) er[row][axis] /= max;
                    }
                }
            }
            boolean shade = !e.has("shade") || e.get("shade").getAsBoolean();
            for (var f : e.getAsJsonObject("faces").entrySet()) {
                String dir = f.getKey();
                JsonObject face = f.getValue().getAsJsonObject();
                double[] n = view.normal(apply(er, NORMALS.get(dir)));
                if (n[2] <= 1e-4) continue; // faces away from the viewer
                String texName = texture(textures, face.get("texture").getAsString().substring(1));
                if (texName == null) continue;
                int tintIndex = face.has("tintindex") ? face.get("tintindex").getAsInt() : -1;
                BufferedImage tex = shaded(texName, tint(tints, tintIndex), shade ? view.light(n) : 1);
                double[] uvs = defaultUv(dir, from, to);
                if (face.has("uv")) for (int k = 0; k < 4; k++) uvs[k] = face.getAsJsonArray("uv").get(k).getAsDouble();
                int shift = (face.has("rotation") ? face.get("rotation").getAsInt() / 90 : 0) & 3;
                double[][] screen = new double[4][], uv = new double[4][];
                double depth = 0;
                for (int i = 0; i < 4; i++) {
                    String c = FACES.get(dir)[i];
                    double[] p = new double[3];
                    for (int k = 0; k < 3; k++) p[k] = c.charAt(k) == '1' ? to[k] : from[k];
                    double[] q = apply(er, new double[]{p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]});
                    for (int k = 0; k < 3; k++) q[k] = (q[k] + origin[k]) / 16;
                    screen[i] = view.project(q);
                    depth += screen[i][2];
                    int idx = (i + shift) % 4; // CuboidFace.UVs.getVertexU/V
                    uv[i] = new double[]{(idx == 0 || idx == 1 ? uvs[0] : uvs[2]) * tex.getWidth() / 16,
                            (idx == 0 || idx == 3 ? uvs[1] : uvs[3]) * tex.getHeight() / 16};
                }
                quads.add(new Quad(screen, depth, tex, uv));
            }
        }
        return quads;
    }

    /**
     * Items the client draws with an entity model (minecraft:special): their boxes, copied from the client's model code.
     * Each box: texture offset u, v, then addBox(x, y, z, width, height, depth), in the texture's px; scale is the item's
     * "transformation" scale. ponytail: only the shield (ShieldModel, no banner pattern); add others the same way.
     */
    private List<Quad> entity(String type, View view, double[] scale) throws IOException {
        record Box(int u, int v, double x, double y, double z, double w, double h, double d) {}
        String texName;
        List<Box> boxes;
        switch (type) {
            case "shield" -> {
                texName = "entity/shield/shield_base_nopattern";
                boxes = List.of(new Box(0, 0, -6, -11, -2, 12, 22, 1), new Box(26, 0, -1, -3, -1, 2, 6, 6));
            }
            default -> { return null; }
        }
        BufferedImage plain = image("assets/minecraft/textures/" + texName + ".png");
        List<Quad> quads = new ArrayList<>();
        for (Box b : boxes) {
            double x0 = b.x, y0 = b.y, z0 = b.z, x1 = b.x + b.w, y1 = b.y + b.h, z1 = b.z + b.d;
            double[][] t = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}}, l = {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            double u0 = b.u, u1 = b.u + b.d, u2 = u1 + b.w, u22 = u2 + b.w, u3 = u2 + b.d, u4 = u3 + b.w;
            double v0 = b.v, v1 = b.v + b.d, v2 = v1 + b.h;
            // ModelPart.Cube: per face its vertices, uv rectangle (u0, v0, u1, v1) and normal
            Object[][] faces = {
                    {new double[][]{l[1], l[0], t[0], t[1]}, new double[]{u1, v0, u2, v1}, new double[]{0, -1, 0}},
                    {new double[][]{t[2], t[3], l[3], l[2]}, new double[]{u2, v1, u22, v0}, new double[]{0, 1, 0}},
                    {new double[][]{t[0], l[0], l[3], t[3]}, new double[]{u0, v1, u1, v2}, new double[]{-1, 0, 0}},
                    {new double[][]{t[1], t[0], t[3], t[2]}, new double[]{u1, v1, u2, v2}, new double[]{0, 0, -1}},
                    {new double[][]{l[1], t[1], t[2], l[2]}, new double[]{u2, v1, u3, v2}, new double[]{1, 0, 0}},
                    {new double[][]{l[0], l[1], l[2], l[3]}, new double[]{u3, v1, u4, v2}, new double[]{0, 0, 1}}};
            for (Object[] f : faces) {
                double[][] vs = (double[][]) f[0];
                double[] r = (double[]) f[1], nm = (double[]) f[2];
                double[] n = view.normal(new double[]{nm[0] * scale[0], nm[1] * scale[1], nm[2] * scale[2]});
                if (n[2] <= 1e-4) continue;
                BufferedImage tex = shaded(texName, 0xFFFFFF, view.light(n));
                double k = (double) tex.getWidth() / plain.getWidth();
                // ModelPart.Polygon: vertex 0 gets (u1, v0), 1 (u0, v0), 2 (u0, v1), 3 (u1, v1)
                double[][] uv = {{r[2] * k, r[1] * k}, {r[0] * k, r[1] * k}, {r[0] * k, r[3] * k}, {r[2] * k, r[3] * k}};
                double[][] screen = new double[4][];
                double depth = 0;
                for (int i = 0; i < 4; i++) {
                    double[] p = new double[3];
                    for (int a = 0; a < 3; a++) p[a] = vs[i][a] / 16 * scale[a];
                    screen[i] = view.project(p);
                    depth += screen[i][2];
                }
                quads.add(new Quad(screen, depth, tex, uv));
            }
        }
        return quads;
    }

    /** Faces back to front, each texture mapped onto its parallelogram (orthographic, so affine is exact). */
    private static BufferedImage paint(List<Quad> quads) {
        quads.sort(Comparator.comparingDouble(Quad::depth));
        BufferedImage img = new BufferedImage(PX, PX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (Quad q : quads) {
            // affine map texture px -> screen px through vertices 0, 1, 3
            double du1 = q.uv[1][0] - q.uv[0][0], dv1 = q.uv[1][1] - q.uv[0][1], du3 = q.uv[3][0] - q.uv[0][0], dv3 = q.uv[3][1] - q.uv[0][1];
            double det = du1 * dv3 - du3 * dv1;
            if (Math.abs(det) < 1e-9) continue;
            double ex1 = q.screen[1][0] - q.screen[0][0], ey1 = q.screen[1][1] - q.screen[0][1];
            double ex3 = q.screen[3][0] - q.screen[0][0], ey3 = q.screen[3][1] - q.screen[0][1];
            double m00 = (ex1 * dv3 - ex3 * dv1) / det, m01 = (ex3 * du1 - ex1 * du3) / det;
            double m10 = (ey1 * dv3 - ey3 * dv1) / det, m11 = (ey3 * du1 - ey1 * du3) / det;
            AffineTransform at = new AffineTransform(m00, m10, m01, m11,
                    q.screen[0][0] - m00 * q.uv[0][0] - m01 * q.uv[0][1], q.screen[0][1] - m10 * q.uv[0][0] - m11 * q.uv[0][1]);
            Path2D.Double clip = new Path2D.Double();
            clip.moveTo(q.screen[0][0], q.screen[0][1]);
            for (int i = 1; i < 4; i++) clip.lineTo(q.screen[i][0], q.screen[i][1]);
            clip.closePath();
            g.setClip(clip);
            g.drawImage(q.tex, at, null);
        }
        g.dispose();
        return img;
    }

    /** FaceBakery.defaultFaceUV */
    private static double[] defaultUv(String dir, double[] f, double[] t) {
        return switch (dir) {
            case "down" -> new double[]{f[0], 16 - t[2], t[0], 16 - f[2]};
            case "up" -> new double[]{f[0], f[2], t[0], t[2]};
            case "north" -> new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case "south" -> new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
            case "west" -> new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
            default -> new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        };
    }

    /** ItemTintSource defaults: constant, dye, grass and foliage colour maps; others untinted. */
    private int tint(JsonArray tints, int index) throws IOException {
        if (index < 0 || index >= tints.size()) return 0xFFFFFF;
        JsonObject t = tints.get(index).getAsJsonObject();
        String type = t.get("type").getAsString().replace("minecraft:", "");
        if (t.has("value")) return t.get("value").getAsInt() & 0xFFFFFF;
        if (t.has("default")) return t.get("default").getAsInt() & 0xFFFFFF;
        if (type.equals("grass") || type.equals("foliage")) {
            double temp = Math.clamp(num(t, "temperature"), 0, 1), down = Math.clamp(num(t, "downfall"), 0, 1) * temp;
            BufferedImage map = image("assets/minecraft/textures/colormap/" + type + ".png");
            return map.getRGB((int) ((1 - temp) * 255), (int) ((1 - down) * 255)) & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    private BufferedImage shaded(String tex, int tint, double light) throws IOException {
        String key = tex + "/" + tint + "/" + Math.round(light * 1000);
        BufferedImage done = textures.get(key);
        if (done != null) return done;
        BufferedImage src = image("assets/minecraft/textures/" + tex + ".png");
        int w = src.getWidth(), h = Math.min(src.getHeight(), w); // animated textures: the first frame
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double r = (tint >> 16 & 255) / 255.0 * light, gr = (tint >> 8 & 255) / 255.0 * light, b = (tint & 255) / 255.0 * light;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                out.setRGB(x, y, p & 0xFF000000 | (int) ((p >> 16 & 255) * r) << 16 | (int) ((p >> 8 & 255) * gr) << 8 | (int) ((p & 255) * b));
            }
        textures.put(key, out);
        return out;
    }

    private BufferedImage image(String path) throws IOException {
        ZipEntry e = jar.getEntry(path);
        if (e == null) throw new IOException("missing " + path);
        BufferedImage img;
        try (InputStream in = jar.getInputStream(e)) {
            img = ImageIO.read(in);
        }
        if (img.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_GRAY) return img;
        // ImageIO treats grey PNGs as linear light and brightens them on getRGB; the client uses the stored values
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var raster = img.getRaster();
        int bands = raster.getNumBands(), shift = raster.getSampleModel().getSampleSize(0) - 8;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int v = raster.getSample(x, y, 0) >> shift, a = bands > 1 ? raster.getSample(x, y, 1) >> shift : 255;
                out.setRGB(x, y, a << 24 | v << 16 | v << 8 | v);
            }
        return out;
    }

    /** A texture variable, following #references, without its namespace. */
    private static String texture(JsonObject textures, String name) {
        for (int i = 0; i < 10 && name != null; i++) {
            JsonElement t = textures.get(name);
            if (t == null) return null;
            String s = t.isJsonObject() ? t.getAsJsonObject().get("sprite").getAsString() : t.getAsString();
            if (!s.startsWith("#")) return s.replace("minecraft:", "");
            name = s.substring(1);
        }
        return null;
    }

    /**
     * The font pack: rendered items on one sheet of PX cells shown 16 px tall, then the HUD sprites Hotbar.java draws,
     * copied 1:1. Each glyph gets a faint pixel in its last column, so its advance is exactly its width + 1.
     */
    private byte[] pack(Map<String, BufferedImage> rendered) throws IOException {
        int rows = Math.max(1, (rendered.size() + COLS - 1) / COLS), i = 0;
        BufferedImage sheet = new BufferedImage(COLS * PX, rows * PX, BufferedImage.TYPE_INT_ARGB);
        StringBuilder[] chars = new StringBuilder[rows];
        for (int r = 0; r < rows; r++) chars[r] = new StringBuilder("\0".repeat(COLS));
        for (var e : rendered.entrySet()) {
            int x = i % COLS * PX, y = i / COLS * PX;
            sheet.createGraphics().drawImage(e.getValue(), x, y, null);
            edge(sheet, x + PX - 1, y);
            char c = (char) (FIRST + i);
            chars[i / COLS].setCharAt(i % COLS, c);
            icons.put(e.getKey(), glyph(c));
            i++;
        }
        JsonArray rowsJson = new JsonArray();
        for (StringBuilder r : chars) rowsJson.add(r.toString());
        JsonArray providers = new JsonArray();
        providers.add(provider("cobblemonlink:font/icons.png", 16, rowsJson));
        Map<String, byte[]> files = new TreeMap<>();
        files.put("assets/cobblemonlink/textures/font/icons.png", png(sheet));
        char c = HUD_FIRST;
        for (String n : jar.stream().map(ZipEntry::getName).sorted().toList()) {
            if (!n.startsWith(HUD_DIR) || !n.endsWith(".png")) continue;
            String name = n.substring(HUD_DIR.length(), n.length() - 4);
            if (!HUD.matcher(name).matches()) continue;
            BufferedImage img = image(n);
            edge(img, img.getWidth() - 1, 0);
            String file = "hud/" + name.replace('/', '_');
            files.put("assets/cobblemonlink/textures/font/" + file + ".png", png(img));
            JsonArray row = new JsonArray();
            row.add(String.valueOf(c));
            providers.add(provider("cobblemonlink:font/" + file + ".png", img.getHeight(), row));
            hud.put(name, new Sprite(glyph(c), img.getWidth() + 1));
            c++;
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(zip)) {
            put(z, "pack.mcmeta", ("{\"pack\":{\"description\":\"cobblemon-link item icons (rendered from your Minecraft client's models)\","
                    + "\"pack_format\":97,\"supported_formats\":[46,999],\"min_format\":46,\"max_format\":999}}").getBytes(StandardCharsets.UTF_8));
            put(z, "assets/cobblemonlink/font/icons.json", font.toString().getBytes(StandardCharsets.UTF_8));
            for (var f : files.entrySet()) put(z, f.getKey(), f.getValue());
        }
        return zip.toByteArray();
    }

    private static Component glyph(char c) { return Component.text(c).font(FONT).shadowColor(ShadowColor.none()); }

    /** A pixel of alpha 1 where there is none: the client measures a glyph to its last non-transparent column. */
    private static void edge(BufferedImage img, int x, int y) {
        if (img.getRGB(x, y) >>> 24 == 0) img.setRGB(x, y, 0x01000000);
    }

    private static JsonObject provider(String file, int height, JsonArray chars) {
        JsonObject p = new JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", file);
        p.addProperty("height", height);
        p.addProperty("ascent", 7); // the glyph's top is the text's top, like the UI pack's overlay glyphs
        p.add("chars", chars);
        return p;
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0); // same icons, same bytes, same SHA-1: clients keep their cached copy
        z.putNextEntry(e);
        z.write(data);
    }

    private JsonObject read(String path) throws IOException {
        ZipEntry e = jar.getEntry(path);
        if (e == null) throw new IOException("missing " + path);
        try (var r = new InputStreamReader(jar.getInputStream(e), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    private static JsonObject json(URI uri) throws IOException {
        try (var r = new InputStreamReader(uri.toURL().openStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    static byte[] sha1(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(b);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double num(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsDouble() : 0; }
    private static double[] vec(JsonArray a) { return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()}; }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double[] unit(double[] v) { double l = Math.sqrt(dot(v, v)); return new double[]{v[0] / l, v[1] / l, v[2] / l}; }
    private static double[][] identity() { return new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}; }
    private static double[][] rotX(double a) { return new double[][]{{1, 0, 0}, {0, Math.cos(a), -Math.sin(a)}, {0, Math.sin(a), Math.cos(a)}}; }
    private static double[][] rotY(double a) { return new double[][]{{Math.cos(a), 0, Math.sin(a)}, {0, 1, 0}, {-Math.sin(a), 0, Math.cos(a)}}; }
    private static double[][] rotZ(double a) { return new double[][]{{Math.cos(a), -Math.sin(a), 0}, {Math.sin(a), Math.cos(a), 0}, {0, 0, 1}}; }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] c = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) c[i][j] += a[i][k] * b[k][j];
        return c;
    }

    private static double[] apply(double[][] m, double[] v) {
        return new double[]{dot(m[0], v), dot(m[1], v), dot(m[2], v)};
    }
}
