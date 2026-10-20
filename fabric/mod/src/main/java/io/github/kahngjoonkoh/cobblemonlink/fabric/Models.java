package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * cobblemon-link's models for vanilla Java (fabric/tools/build_models.py): every bone an item model, registered with
 * Polymer at startup (its pack carries them), the bones and keyframes read the first time a look is seen.
 * ponytail: data stays loaded once read (a few hundred KB a look); an LRU if a server sees every look.
 */
public final class Models {
    /** Carrier items for the bone models: ones PolyMc doesn't use, so ~1,600 overrides each, not 87,000 on one. */
    private static final List<Item> CARRIERS = List.of(Items.LEATHER_HORSE_ARMOR, Items.IRON_HORSE_ARMOR,
            Items.GOLDEN_HORSE_ARMOR, Items.DIAMOND_HORSE_ARMOR, Items.ANGLER_POTTERY_SHERD, Items.ARCHER_POTTERY_SHERD,
            Items.ARMS_UP_POTTERY_SHERD, Items.BLADE_POTTERY_SHERD, Items.BREWER_POTTERY_SHERD, Items.BURN_POTTERY_SHERD,
            Items.DANGER_POTTERY_SHERD, Items.EXPLORER_POTTERY_SHERD, Items.FLOW_POTTERY_SHERD, Items.FRIEND_POTTERY_SHERD,
            Items.GUSTER_POTTERY_SHERD, Items.HEART_POTTERY_SHERD, Items.HEARTBREAK_POTTERY_SHERD, Items.HOWL_POTTERY_SHERD,
            Items.MINER_POTTERY_SHERD, Items.MOURNER_POTTERY_SHERD, Items.PLENTY_POTTERY_SHERD, Items.PRIZE_POTTERY_SHERD,
            Items.SCRAPE_POTTERY_SHERD, Items.SHEAF_POTTERY_SHERD, Items.SHELTER_POTTERY_SHERD, Items.SKULL_POTTERY_SHERD,
            Items.SNORT_POTTERY_SHERD, Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS,
            Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL, Items.MUSIC_DISC_MELLOHI,
            Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD, Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11,
            Items.MUSIC_DISC_WAIT, Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_5, Items.MUSIC_DISC_PIGSTEP,
            Items.MUSIC_DISC_RELIC, Items.MUSIC_DISC_CREATOR, Items.MUSIC_DISC_CREATOR_MUSIC_BOX,
            Items.MUSIC_DISC_PRECIPICE, Items.DISC_FRAGMENT_5, Items.TURTLE_HELMET, Items.WOLF_ARMOR,
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_BOOTS);

    public record Bone(String name, int parent, Vector3f local, Quaternionf rest, float fit, ItemStack item) {}

    /** keys: per channel [t, x, y, z, ...]; loop: 0 once, 1 loop, 2 hold the last frame. */
    public record Anim(float length, int loop, Map<Integer, float[][]> channels) {}

    public record Model(String name, Bone[] bones, Map<String, Anim> anims) {}

    private static Path dir;
    private static final Map<String, Map<Integer, PolymerModelData>> PARTS = new HashMap<>();
    private static final Map<String, Model> LOADED = new ConcurrentHashMap<>();

    private Models() {}

    /** At startup, before Polymer builds its pack: every bone model, and our pack's assets. */
    static void register(Path dir) {
        Models.dir = dir;
        Path index = dir.resolve("index.json");
        if (!Files.exists(index)) {
            System.out.println("[cobblemonlink] no models in " + dir + " (fabric/tools/build_models.py)");
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(index)).getAsJsonObject();
            for (var e : o.entrySet()) {
                Item carrier = CARRIERS.get(Math.floorMod(e.getKey().hashCode(), CARRIERS.size()));
                Map<Integer, PolymerModelData> parts = new HashMap<>();
                for (var b : e.getValue().getAsJsonArray())
                    parts.put(b.getAsInt(), PolymerResourcePackUtils.requestModel(carrier,
                            ResourceLocation.fromNamespaceAndPath("cobblemonlink", "item/m/" + e.getKey() + "/" + b.getAsInt())));
                PARTS.put(e.getKey(), parts);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            if (Files.exists(dir.resolve("looks.json"))) looks = JsonParser.parseString(Files.readString(dir.resolve("looks.json"))).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(b -> b.copyFromPath(dir.resolve("pack")));
        System.out.println("[cobblemonlink] " + PARTS.size() + " models, "
                + PARTS.values().stream().mapToInt(Map::size).sum() + " bone models");
    }

    public static boolean has(String look) { return PARTS.containsKey(look); }

    private static JsonObject looks = new JsonObject();

    /**
     * The look a Pokémon is drawn with (Cobblemon's VaryingModelRepository, as tools/looks.py tables it): every
     * variation whose aspects it all has overrides the model and texture so far; the pair names the look.
     */
    public static String look(String species, java.util.Set<String> aspects) {
        JsonObject s = looks.getAsJsonObject(species);
        if (s == null) return species;
        String model = null, texture = null;
        for (var v : s.getAsJsonArray("variations")) {
            JsonArray a = v.getAsJsonArray();
            boolean all = true;
            for (var need : a.get(0).getAsJsonArray()) all &= aspects.contains(need.getAsString());
            if (!all) continue;
            if (!a.get(1).isJsonNull()) model = a.get(1).getAsString();
            if (!a.get(2).isJsonNull()) texture = String.valueOf(a.get(2).getAsInt());
        }
        var m = s.getAsJsonObject("models").get(model + "|" + texture);
        return m == null ? species : m.getAsString();
    }

    /** The look's bones and animations, read on first use; null if there is no such model. */
    public static Model get(String look) {
        if (!PARTS.containsKey(look)) return null;
        return LOADED.computeIfAbsent(look, Models::read);
    }

    private static Model read(String look) {
        try {
            JsonObject o = JsonParser.parseString(Files.readString(dir.resolve("data/" + look + ".json"))).getAsJsonObject();
            JsonArray bs = o.getAsJsonArray("bones");
            Bone[] bones = new Bone[bs.size()];
            Map<Integer, PolymerModelData> parts = PARTS.get(look);
            for (int i = 0; i < bones.length; i++) {
                JsonArray b = bs.get(i).getAsJsonArray(); // name, parent, local x y z, rest x y z, fit, has model
                Vector3f local = new Vector3f(b.get(2).getAsFloat(), b.get(3).getAsFloat(), b.get(4).getAsFloat()).div(16);
                Quaternionf rest = euler(b.get(5).getAsFloat(), b.get(6).getAsFloat(), b.get(7).getAsFloat());
                ItemStack item = null;
                PolymerModelData d = parts.get(i);
                if (d != null) item = d.asStack();
                bones[i] = new Bone(b.get(0).getAsString(), b.get(1).getAsInt(), local, rest, b.get(8).getAsFloat(), item);
            }
            Map<String, Anim> anims = new HashMap<>();
            for (var e : o.getAsJsonObject("anims").entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                Map<Integer, float[][]> ch = new HashMap<>();
                for (var c : a.get(2).getAsJsonObject().entrySet()) {
                    JsonObject co = c.getValue().getAsJsonObject();
                    ch.put(Integer.parseInt(c.getKey()), new float[][]{floats(co.get("p")), floats(co.get("r")), floats(co.get("s"))});
                }
                anims.put(e.getKey(), new Anim(a.get(0).getAsFloat(), a.get(1).getAsInt(), ch));
            }
            return new Model(look, bones, anims);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static float[] floats(com.google.gson.JsonElement e) {
        if (e == null) return null;
        JsonArray a = e.getAsJsonArray();
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    /** Blockbench's bone rotation (degrees): Z, then Y, then X, as blockbench-import-library builds it. */
    static Quaternionf euler(float x, float y, float z) {
        return new Quaternionf().rotateZ((float) Math.toRadians(z)).rotateY((float) Math.toRadians(y)).rotateX((float) Math.toRadians(x));
    }

    /** A channel's value at time t (linear keyframes [t, x, y, z, ...]); null when the channel is absent. */
    static Vector3f sample(float[] k, float t, Vector3f out) {
        if (k == null || k.length == 0) return null;
        int n = k.length / 4;
        if (t <= k[0] || n == 1) return out.set(k[1], k[2], k[3]);
        for (int i = 1; i < n; i++) {
            float t1 = k[4 * i];
            if (t < t1) {
                float t0 = k[4 * (i - 1)], f = t1 > t0 ? (t - t0) / (t1 - t0) : 1;
                int a = 4 * (i - 1) + 1, b = 4 * i + 1;
                return out.set(k[a] + (k[b] - k[a]) * f, k[a + 1] + (k[b + 1] - k[a + 1]) * f, k[a + 2] + (k[b + 2] - k[a + 2]) * f);
            }
        }
        return out.set(k[4 * n - 3], k[4 * n - 2], k[4 * n - 1]);
    }
}
