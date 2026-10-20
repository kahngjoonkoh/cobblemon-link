package io.github.kahngjoonkoh.cobblemonlink.fabric;

import eu.pb4.polymer.autohost.impl.AutoHost;
import eu.pb4.polymer.common.impl.CommonNetworkHandlerExt;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Polymer's resource pack (Cobblemon's assets for PolyMc, the models, the UI) goes to vanilla Java clients only, once
 * they are in the game: Cobblemon clients have the assets already and Bedrock players get Geyser's packs. Polymer's
 * own send during configuration is taken out for everyone (mixin.PackMixin), since a Cobblemon client can't be told
 * apart before its play channels are known.
 */
final class Packs {
    private Packs() {}

    /** True once sent; false while the pack is still being built (try again later). */
    static boolean send(ServerGamePacketListenerImpl h) {
        if (!AutoHost.provider.isReady()) return false;
        var connection = ((CommonNetworkHandlerExt) h).polymerCommon$getConnection();
        for (var pack : AutoHost.provider.getProperties(connection))
            h.send(new ClientboundResourcePackPushPacket(pack.id(), pack.url(), pack.hash(), true,
                    Optional.of(Component.literal("Cobblemon's models and screens"))));
        return true;
    }
}
