package io.github.kahngjoonkoh.cobblemonlink.fabric;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Entry points the mixins call (classes in the mixin package can't be referenced from here). */
public final class Mixins {
    private Mixins() {}

    /** An arm swing: a click on Java's battle screen. */
    public static void swing(ServerPlayer p) {
        var ui = BattleUi.get(p);
        if (ui != null) ui.swing();
    }

    public static void callbacks(ServerPlayer p, CustomPacketPayload payload) { Callbacks.packet(p, payload); }

    public static void pasture(net.minecraft.server.level.ServerPlayer p) { Pasture.open(p); }
}
