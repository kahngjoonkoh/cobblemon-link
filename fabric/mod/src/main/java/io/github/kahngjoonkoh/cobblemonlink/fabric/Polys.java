package io.github.kahngjoonkoh.cobblemonlink.fabric;

import io.github.theepicblock.polymc.api.misc.PolyMapProvider;
import io.github.theepicblock.polymc.impl.NOPPolyMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** PolyMc only for players without Cobblemon: a Cobblemon client gets PolyMc's no-op map, so Cobblemon's own content. */
final class Polys {
    static void register() {
        PolyMapProvider.EVENT.register(connection ->
                connection.getPacketListener() instanceof ServerGamePacketListenerImpl h && Clients.modded(h) ? new NOPPolyMap() : null);
        // ponytail: a client's play channels are known once it has joined, so its map is picked again then (PolyMc
        // already filtered its configuration-phase registry sync; a Cobblemon client got through that fine here)
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> PolyMapProvider.get(h).refreshUsedPolyMap());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> dump());
    }

    /**
     * Which vanilla state PolyMc shows each Cobblemon block state as, for tools/build_poly_blocks.py (Geyser's custom
     * block mappings: Bedrock then draws those borrowed states as Cobblemon's blocks too). config/cobblemonlink/poly-blocks.json.
     */
    static void dump() {
        var map = io.github.theepicblock.polymc.PolyMc.getMainMap();
        var out = new java.util.TreeMap<String, String>();
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals("cobblemon")) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var client = map.getClientState(state, null);
                if (client != null && client != state) out.put(name(state), name(client));
            }
        }
        try {
            var file = java.nio.file.Path.of("config", "cobblemonlink", "poly-blocks.json");
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger("cobblemonlink").warn("poly-blocks.json: {}", e.toString());
        }
    }

    /** "ns:block[a=b,c=d]", properties sorted (as Minecraft's blockstate files and Geyser's mappings write them). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String name(net.minecraft.world.level.block.state.BlockState s) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        String props = s.getValues().entrySet().stream()
                .map(e -> e.getKey().getName() + "=" + ((net.minecraft.world.level.block.state.properties.Property) e.getKey()).getName((Comparable) e.getValue()))
                .sorted().collect(java.util.stream.Collectors.joining(","));
        return props.isEmpty() ? id : id + "[" + props + "]";
    }
}
