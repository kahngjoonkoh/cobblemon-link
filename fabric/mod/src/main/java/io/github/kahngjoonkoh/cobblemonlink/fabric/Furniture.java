package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

/**
 * Cobblemon's machine blocks for players without the mod: PolyMc stands a barrier in for them (PolyEntry), and this
 * draws Cobblemon's own block model over it, following the block's state: vanilla Java an item display with the
 * model (from the pack), Bedrock a GeyserModelEngine entity (fabric/tools/bedrock_blocks.py, "clb_<model>").
 * ponytail: each chunk scanned once as it loads (a palette check first), so a world full of chunks costs little.
 */
public final class Furniture {
    static final List<String> BLOCKS = List.of("healing_machine", "pc", "pasture", "display_case", "fossil_analyzer",
            "restoration_tank", "monitor", "tm_machine", "campfire_pot_red", "campfire_pot_blue", "campfire_pot_green",
            "campfire_pot_white", "campfire_pot_yellow", "campfire_pot_pink", "campfire_pot_black", "campfire", "soul_campfire");

    /** Cobblemon's campfires: Minecraft's campfire stands in (PolyEntry), the pot on it is ours. */
    static final List<String> CAMPFIRES = List.of("campfire", "soul_campfire");

    /** A blockstate variant: its properties (all must match), model and turn. */
    record Variant(Map<String, String> when, String model, int y) {}

    private static final Map<Block, List<Variant>> VARIANTS = new HashMap<>();
    private static final Map<String, PolymerModelData> ITEMS = new HashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, Part>> DRAWN = new HashMap<>();

    private Furniture() {}

    static void register() {
        for (String id : BLOCKS) {
            Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblemon", id));
            var in = Furniture.class.getClassLoader().getResourceAsStream("assets/cobblemon/blockstates/" + id + ".json");
            if (in == null) continue;
            JsonObject vs = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("variants");
            List<Variant> list = new ArrayList<>();
            for (var e : vs.entrySet()) {
                JsonObject v = e.getValue().isJsonArray() ? e.getValue().getAsJsonArray().get(0).getAsJsonObject() : e.getValue().getAsJsonObject();
                list.add(new Variant(props(e.getKey()), v.get("model").getAsString(), v.has("y") ? v.get("y").getAsInt() : 0));
            }
            VARIANTS.put(b, list);
            for (Variant v : list)
                ITEMS.computeIfAbsent(v.model, m -> PolymerResourcePackUtils.requestModel(
                        Models.CARRIERS.get(Math.floorMod(m.hashCode(), Models.CARRIERS.size())), ResourceLocation.parse(m)));
        }
        // PolyMc's pack carries only the block models items use: these, their parents and textures too
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(b -> {
            java.util.Set<String> done = new java.util.HashSet<>();
            for (String m : ITEMS.keySet()) addModel(b, m, done);
        });
        // found when their chunk loads and whenever a block is set (BlockMixin); attached a tick later, the chunk may
        // still be loading
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            var sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                if (sections[i].hasOnlyAir() || !sections[i].maybeHas(st -> VARIANTS.containsKey(st.getBlock()))) continue;
                int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                    if (VARIANTS.containsKey(sections[i].getBlockState(x, y, z).getBlock()))
                        placed(level, chunk.getPos().getBlockAt(x, y0 + y, z));
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            var map = DRAWN.get(level);
            if (map != null) map.values().removeIf(p -> { boolean in = new net.minecraft.world.level.ChunkPos(p.pos).equals(chunk.getPos()); if (in) p.destroy(); return in; });
        });
        System.out.println("[cobblemonlink] " + VARIANTS.size() + " furniture blocks, " + ITEMS.size() + " models");
    }

    private static void addModel(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder b, String id, java.util.Set<String> done) {
        ResourceLocation r = ResourceLocation.parse(id);
        if (!r.getNamespace().equals("cobblemon") || !done.add(id)) return;
        byte[] json = resource("assets/cobblemon/models/" + r.getPath() + ".json");
        if (json == null) return;
        b.addData("assets/cobblemon/models/" + r.getPath() + ".json", json);
        JsonObject o = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        if (o.has("parent")) addModel(b, o.get("parent").getAsString(), done);
        if (o.has("textures")) for (var t : o.getAsJsonObject("textures").entrySet()) {
            String v = t.getValue().getAsString();
            if (v.startsWith("#") || !done.add(v)) continue;
            ResourceLocation tr = ResourceLocation.parse(v);
            if (!tr.getNamespace().equals("cobblemon")) continue;
            for (String ext : new String[]{".png", ".png.mcmeta"}) {
                byte[] d = resource("assets/cobblemon/textures/" + tr.getPath() + ext);
                if (d != null) b.addData("assets/cobblemon/textures/" + tr.getPath() + ext, d);
            }
        }
    }

    private static byte[] resource(String path) {
        try (var in = Furniture.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    static Map<String, String> props(String key) {
        Map<String, String> m = new HashMap<>();
        if (!key.isEmpty()) for (String kv : key.split(",")) { String[] p = kv.split("="); m.put(p[0], p[1]); }
        return m;
    }

    /** The first variant whose properties the state all has (Minecraft's rule); null if none. */
    static Variant variant(List<Variant> vs, Map<String, String> state) {
        for (Variant v : vs) if (state.entrySet().containsAll(v.when.entrySet())) return v;
        return null;
    }

    static Variant variant(BlockState s) {
        List<Variant> vs = VARIANTS.get(s.getBlock());
        if (vs == null) return null;
        Map<String, String> m = new HashMap<>();
        s.getValues().forEach((p, v) -> m.put(p.getName(), net.minecraft.Util.getPropertyName(p, v)));
        return variant(vs, m);
    }

    /** A machine block may be at pos (a chunk loaded, or a block was set there): drawn from next tick. */
    public static void placed(ServerLevel level, BlockPos at) {
        BlockPos pos = at.immutable();
        if (DRAWN.containsKey(level) && DRAWN.get(level).containsKey(pos)) return;
        Tasks.next(() -> {
            var map = DRAWN.computeIfAbsent(level, l -> new HashMap<>());
            if (map.containsKey(pos) || !level.isLoaded(pos) || !VARIANTS.containsKey(level.getBlockState(pos).getBlock())) return;
            map.put(pos, new Part(level, pos));
        });
    }

    public static boolean furniture(BlockState s) { return VARIANTS.containsKey(s.getBlock()); }

    /**
     * The models a block shows (null: not a machine block any more): its blockstate's, but for a campfire (PolyMc shows
     * Minecraft's) only its pot, open or with its lid on.
     */
    static List<Variant> models(BlockState s, ServerLevel level, BlockPos pos) {
        Variant v = variant(s);
        if (v == null) return null;
        List<Variant> out = new ArrayList<>();
        if (!CAMPFIRES.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath())) out.add(v);
        if (level.getBlockEntity(pos) instanceof com.cobblemon.mod.common.block.entity.CampfireBlockEntity be && !be.getPotItem().isEmpty()) {
            boolean lid = s.getValues().entrySet().stream().anyMatch(e -> e.getKey().getName().equals("lid") && Boolean.TRUE.equals(e.getValue()));
            String m = "cobblemon:block/" + BuiltInRegistries.ITEM.getKey(be.getPotItem().getItem()).getPath() + (lid ? "" : "_open");
            if (ITEMS.containsKey(m)) out.add(new Variant(Map.of(), m, v.y));
        }
        return out;
    }

    /** One machine block: a Java holder and a Bedrock holder, checking the block every half second. */
    static final class Part {
        final ServerLevel level;
        final BlockPos pos;
        final ElementHolder java, bedrock;
        final List<ItemDisplayElement> items = new ArrayList<>();
        final List<SimpleEntityElement> pigs = new ArrayList<>();
        final Map<Integer, String> gme = new HashMap<>(); // pig id -> GeyserModelEngine model
        String shown;
        int ticks;

        Part(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
            java = new ElementHolder() {
                @Override public boolean startWatching(ServerGamePacketListenerImpl h) { return Clients.java(h) && super.startWatching(h); }
                @Override protected void onTick() { check(); }
            };
            bedrock = new ElementHolder() {
                @Override public boolean startWatching(ServerGamePacketListenerImpl h) {
                    if (!Clients.bedrock(h.player)) return false;
                    gme.forEach((id, m) -> h.send(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.customEntity(id, "modelengine:" + m))));
                    return super.startWatching(h);
                }
            };
            ChunkAttachment.ofTicking(java, level, net.minecraft.world.phys.Vec3.atCenterOf(pos));
            ChunkAttachment.of(bedrock, level, net.minecraft.world.phys.Vec3.atBottomCenterOf(pos));
            check();
        }

        void check() {
            if (ticks++ % 10 != 0) return;
            List<Variant> ms = models(level.getBlockState(pos), level, pos);
            if (ms == null) { // broken
                DRAWN.get(level).remove(pos);
                destroy();
                return;
            }
            String key = ms.stream().map(v -> v.model + "@" + v.y).reduce("", String::concat);
            if (key.equals(shown)) return;
            shown = key;
            items.forEach(java::removeElement);
            pigs.forEach(bedrock::removeElement);
            items.clear();
            pigs.clear();
            gme.clear();
            for (Variant v : ms) {
                ItemDisplayElement item = new ItemDisplayElement(ITEMS.get(v.model).asStack());
                item.setModelTransformation(ItemDisplayContext.NONE);
                item.setViewRange(2);
                // an item display shows the model turned half round from the placed block
                item.setLeftRotation(new Quaternionf().rotateY((float) Math.toRadians(180 - v.y)));
                items.add(java.addElement(item));
                SimpleEntityElement pig = new SimpleEntityElement(EntityType.PIG);
                pig.setNoGravity(true);
                pig.setSilent(true);
                pig.setYaw(v.y + 180);
                pig.setInteractionHandler(click);
                String m = "clb_" + v.model.substring(v.model.lastIndexOf('/') + 1);
                gme.put(pig.getEntityId(), m);
                for (var h : bedrock.getWatchingPlayers())
                    h.send(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.customEntity(pig.getEntityId(), "modelengine:" + m)));
                pigs.add(bedrock.addElement(pig));
            }
        }

        /** A Bedrock player clicking the model clicks the block under it. */
        final eu.pb4.polymer.virtualentity.api.elements.VirtualElement.InteractionHandler click = new eu.pb4.polymer.virtualentity.api.elements.VirtualElement.InteractionHandler() {
            @Override public void interact(net.minecraft.server.level.ServerPlayer p, net.minecraft.world.InteractionHand hand) {
                p.gameMode.useItemOn(p, level, p.getItemInHand(hand), hand,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
            }

            // ponytail: creative breaks it; survival digging needs the block itself (Bedrock aims through the model rarely)
            @Override public void attack(net.minecraft.server.level.ServerPlayer p) {
                if (p.isCreative()) p.gameMode.destroyBlock(pos);
            }
        };

        void destroy() { java.destroy(); bedrock.destroy(); }
    }
}
