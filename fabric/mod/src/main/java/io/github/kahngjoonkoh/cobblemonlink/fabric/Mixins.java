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

    public static void callbacks(ServerPlayer p, CustomPacketPayload payload) { Callbacks.packet(p, payload); Social.packet(p, payload); }

    public static void pasture(net.minecraft.server.level.ServerPlayer p) { Pasture.open(p); }

    public static net.minecraft.network.protocol.Packet<?> menuOpen(net.minecraft.network.protocol.game.ClientboundOpenScreenPacket o, ServerPlayer p) { return Menus.open(o, p); }

    public static net.minecraft.network.protocol.Packet<?> menuOut(net.minecraft.network.protocol.Packet<?> packet, ServerPlayer p) { return Menus.out(packet, p); }

    public static void menuClosed(ServerPlayer p) { Menus.closed(p); }

    public static boolean moddedMenu(net.minecraft.world.inventory.MenuType<?> t) { return Menus.modded(t); }

    public static boolean playerClicked(ServerPlayer p, ServerPlayer target) { return Social.interact(p, target); }

    /** Steering from a client without the mod, as Cobblemon's client would send it (x strafe, y up/down, z forward). */
    public static void rideInput(ServerPlayer p, float xxa, float zza, boolean jump, boolean sneak) {
        if (!Clients.bridged(p.connection) || !(p.getVehicle() instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity)) return;
        ((com.cobblemon.mod.common.duck.PlayerDuck) p).setDriverInput(new org.joml.Vector3f(Math.signum(xxa), jump ? 1 : sneak ? -1 : 0, Math.signum(zza)));
    }
}
