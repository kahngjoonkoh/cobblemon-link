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

    public static void callbacks(ServerPlayer p, CustomPacketPayload payload) { Callbacks.packet(p, payload); Social.packet(p, payload); Dialogues.packet(p, payload); }

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

    /** Cobblemon's send-out key (R): the first Pokémon able to go out, or recall it; sneaking only. */
    public static boolean sendOutKey(ServerPlayer p) {
        if (!Clients.bridged(p.connection) || !p.isShiftKeyDown()) return false;
        var party = com.cobblemon.mod.common.util.PlayerExtensionsKt.party(p);
        for (int i = 0; i < 6; i++) {
            var m = party.get(new com.cobblemon.mod.common.api.storage.party.PartyPosition(i));
            if (m != null && !m.isFainted()) {
                com.cobblemon.mod.common.net.serverhandling.storage.SendOutPokemonHandler.INSTANCE.handle(
                        new com.cobblemon.mod.common.net.messages.server.SendOutPokemonPacket(i), p.server, p);
                return true;
            }
        }
        return true;
    }

    private static final java.util.Set<Integer> DEFERRED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** True the first time an entity's spawn is put off a tick (then it goes as it is). */
    public static boolean deferSpawn(int id) {
        if (DEFERRED.remove(id)) return false;
        DEFERRED.add(id);
        return true;
    }
}
