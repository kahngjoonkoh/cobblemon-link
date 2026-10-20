package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The fruit and flowers on Cobblemon's grown berry bushes (its client draws them from Bedrock geometry): for vanilla
 * Java each geometry as an item model made here (its cubes as Minecraft block-model elements, box UV unfolded), drawn
 * by Furniture at the bush's growth points; Bedrock draws the geometry itself ("clberry_<geo>", tools/bedrock_blocks.py).
 */
final class Berries {
    private static final Map<String, PolymerModelData> MODELS = new HashMap<>();

    private Berries() {}

    /** At startup: every berry's fruit and flower geometry as an item model, in the pack. */
    static void register() {
        var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cobblemon").flatMap(m -> m.findPath("assets/cobblemon/bedrock/berries"));
        if (dir.isEmpty()) return;
        Map<String, byte[]> files = new HashMap<>();
        // which texture each geometry wears (the berries' fruitModel/fruitTexture, flowerModel/flowerTexture)
        Map<String, String> textures = new HashMap<>();
        var data = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cobblemon").flatMap(m -> m.findPath("data/cobblemon/berries"));
        if (data.isPresent()) try (var list = Files.list(data.get())) {
            for (var f : (Iterable<java.nio.file.Path>) list::iterator) {
                JsonObject b = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                for (String[] k : new String[][]{{"fruitModel", "fruitTexture"}, {"flowerModel", "flowerTexture"}})
                    if (b.has(k[0]) && b.has(k[1]))
                        textures.put(b.get(k[0]).getAsString().split(":")[1].replace(".geo", ""), b.get(k[1]).getAsString().split(":")[1]);
            }
        } catch (java.io.IOException e) {
            System.out.println("[cobblemonlink] berries: " + e);
        }
        try (var list = Files.list(dir.get())) {
            for (var f : (Iterable<java.nio.file.Path>) list::iterator) {
                String n = f.getFileName().toString();
                if (!n.endsWith(".geo.json")) continue;
                String geo = n.substring(0, n.length() - ".geo.json".length());
                String tex = textures.get(geo);
                byte[] png = tex == null ? null : resource("assets/cobblemon/textures/berries/" + tex + ".png");
                if (png == null) continue;
                files.put("assets/cobblemonlink/textures/item/berry/" + geo + ".png", png);
                JsonObject model = model(JsonParser.parseString(Files.readString(f)).getAsJsonObject(), "cobblemonlink:item/berry/" + geo) /* the block atlas only takes item/ and block/ textures */;
                files.put("assets/cobblemonlink/models/berry/" + geo + ".json", model.toString().getBytes(StandardCharsets.UTF_8));
                MODELS.put(geo, PolymerResourcePackUtils.requestModel(Models.CARRIERS.get(Math.floorMod(geo.hashCode(), Models.CARRIERS.size())),
                        ResourceLocation.fromNamespaceAndPath("cobblemonlink", "berry/" + geo)));
            }
        } catch (java.io.IOException e) {
            System.out.println("[cobblemonlink] berry models: " + e);
        }
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(b -> files.forEach(b::addData));
    }

    private static byte[] resource(String path) {
        try (var in = Berries.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** The item for a berry geometry ("oran_berry", "flower"), or null. */
    static ItemStack item(String geo) {
        PolymerModelData d = MODELS.get(geo);
        return d == null ? null : d.asStack();
    }

    /**
     * A Bedrock geometry's cubes as a Minecraft block model around (8, 8, 8) (x mirrored, as Bedrock's is), the
     * texture to be bound per berry ("#0"). ponytail: no bone rotations or per-face UV; berry fruit has neither (a gilded chest's two slanted belt strips stay flat).
     */
    static JsonObject model(JsonObject geo, String id) { return model(geo, id, 8); }

    /** y0: where the geometry's y = 0 goes in the block model (8: the middle, a berry's point; 0: the floor). */
    static JsonObject model(JsonObject geo, String id, float y0) {
        JsonObject g = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject desc = g.getAsJsonObject("description");
        float tw = desc.get("texture_width").getAsFloat(), th = desc.get("texture_height").getAsFloat();
        JsonArray elements = new JsonArray();
        for (var bone : g.getAsJsonArray("bones")) {
            var cubes = bone.getAsJsonObject().getAsJsonArray("cubes");
            if (cubes == null) continue;
            for (var c : cubes) {
                JsonObject cube = c.getAsJsonObject();
                float[] o = floats(cube.getAsJsonArray("origin")), sz = floats(cube.getAsJsonArray("size")), uv = floats(cube.getAsJsonArray("uv"));
                float w = sz[0], h = sz[1], d = sz[2], u = uv[0], v = uv[1];
                float in = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0; // grows the box: keeps flat belts off the faces they lie on
                boolean mir = cube.has("mirror") && cube.get("mirror").getAsBoolean();
                JsonObject e = new JsonObject();
                e.add("from", arr(8 - (o[0] + w) - in, y0 + o[1] - in, 8 + o[2] - in));
                e.add("to", arr(8 - o[0] + in, y0 + o[1] + h + in, 8 + o[2] + d + in));
                JsonObject faces = new JsonObject();
                // Bedrock's box UV row: east (u), north, west, south; x is mirrored here, so the strips keep their sides
                faces.add("up", face(u + d, v, w, d, tw, th, mir));
                faces.add("down", face(u + d + w, v, w, d, tw, th, mir));
                faces.add("north", face(u + d, v + d, w, h, tw, th, mir));
                faces.add("south", face(u + 2 * d + w, v + d, w, h, tw, th, mir));
                faces.add(mir ? "west" : "east", face(u, v + d, d, h, tw, th, mir));
                faces.add(mir ? "east" : "west", face(u + d + w, v + d, d, h, tw, th, mir));
                e.add("faces", faces);
                elements.add(e);
            }
        }
        JsonObject m = new JsonObject();
        JsonObject tex = new JsonObject();
        tex.addProperty("0", id);
        m.add("textures", tex);
        m.add("elements", elements);
        return m;
    }

    private static JsonObject face(float u, float v, float w, float h, float tw, float th, boolean mirror) {
        JsonObject f = new JsonObject();
        float a = u * 16 / tw, b = (u + w) * 16 / tw;
        f.add("uv", mirror ? arr(b, v * 16 / th, a, (v + h) * 16 / th) : arr(a, v * 16 / th, b, (v + h) * 16 / th));
        f.addProperty("texture", "#0");
        return f;
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static JsonArray arr(float... v) {
        JsonArray a = new JsonArray();
        for (float x : v) a.add(x);
        return a;
    }
}
