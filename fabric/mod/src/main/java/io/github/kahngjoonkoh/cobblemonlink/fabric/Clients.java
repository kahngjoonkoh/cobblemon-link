package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The three kinds of player. A Cobblemon client registered Cobblemon's play channels and gets Cobblemon untouched;
 * a Bedrock player is a Floodgate player (Geyser, through ViaProxy); everyone else is a vanilla Java client.
 * Looked at on every packet sent, so it is worked out once a player has joined and remembered.
 */
public final class Clients {
    public enum Kind { MODDED, BEDROCK, JAVA }

    private static final boolean FLOODGATE = FabricLoader.getInstance().isModLoaded("floodgate");
    private static final Map<UUID, Kind> KNOWN = new ConcurrentHashMap<>();

    private Clients() {}

    static void register() {
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> KNOWN.put(h.player.getUUID(), look(h)));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> KNOWN.remove(h.player.getUUID()));
    }

    public static Kind kind(ServerGamePacketListenerImpl h) {
        Kind k = KNOWN.get(h.player.getUUID());
        return k != null ? k : look(h);
    }

    private static Kind look(ServerGamePacketListenerImpl h) {
        if (ServerPlayNetworking.getSendable(h).stream().anyMatch(id -> id.getNamespace().equals("cobblemon"))) return Kind.MODDED;
        return FLOODGATE && org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(h.player.getUUID()) ? Kind.BEDROCK : Kind.JAVA;
    }

    public static boolean modded(ServerGamePacketListenerImpl h) { return kind(h) == Kind.MODDED; }

    /** Needs the bridge: no Cobblemon on the client (vanilla Java or Bedrock). */
    public static boolean bridged(ServerGamePacketListenerImpl h) { return kind(h) != Kind.MODDED; }

    public static boolean bedrock(ServerPlayer p) { return p.connection != null && kind(p.connection) == Kind.BEDROCK; }

    /** A vanilla Java client: the resource pack, chest screens and display-entity models. */
    public static boolean java(ServerGamePacketListenerImpl h) { return kind(h) == Kind.JAVA; }

    public static boolean java(ServerPlayer p) { return p.connection != null && java(p.connection); }
}
