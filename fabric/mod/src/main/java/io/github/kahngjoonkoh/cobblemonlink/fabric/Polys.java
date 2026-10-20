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
    }
}
