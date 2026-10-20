package io.github.kahngjoonkoh.cobblemonlink.fabric;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.List;
import java.util.Map;
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

    public static net.minecraft.network.protocol.Packet<?> particles(ServerPlayer p, CustomPacketPayload payload) { return Particles.of(p, payload); }

    private static final Map<Integer, java.util.UUID> NPCS = new java.util.concurrent.ConcurrentHashMap<>();

    /** An NPC for a client without the mod: an unlisted player entry (its name, ≤16 letters) and a player entity. */
    public static net.minecraft.network.protocol.Packet<?> npcSpawn(net.minecraft.network.protocol.game.ClientboundAddEntityPacket a,
            com.cobblemon.mod.common.entity.npc.NPCEntity npc, ServerPlayer p) {
        String raw = npc.getName().getString(); // Cobblemon may keep a lang key as plain text
        String name = (Lang.has(raw) ? Lang.get(p, raw) : Lang.resolve(p, npc.getName()).getString()).replaceAll("[^A-Za-z0-9_]", "");
        if (name.length() > 16) name = name.substring(0, 16);
        if (name.isEmpty()) name = "NPC";
        var info = eu.pb4.polymer.core.api.entity.PolymerEntityUtils.createMutablePlayerListPacket(java.util.EnumSet.of(
                net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER));
        info.entries().add(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry(a.getUUID(),
                new com.mojang.authlib.GameProfile(a.getUUID(), name), false, 0, net.minecraft.world.level.GameType.SURVIVAL, null, null));
        NPCS.put(a.getId(), a.getUUID());
        var body = new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(a.getId(), a.getUUID(), a.getX(), a.getY(), a.getZ(),
                a.getXRot(), a.getYRot(), net.minecraft.world.entity.EntityType.PLAYER, 0, net.minecraft.world.phys.Vec3.ZERO, a.getYHeadRot());
        var layers = new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(a.getId(), List.of(
                net.minecraft.network.syncher.SynchedEntityData.DataValue.create(new net.minecraft.network.syncher.EntityDataAccessor<>(17,
                        net.minecraft.network.syncher.EntityDataSerializers.BYTE), (byte) 0x7f)));
        return new net.minecraft.network.protocol.game.ClientboundBundlePacket(List.of(info, body, layers));
    }

    /** The player entries of NPCs leaving a client's view, or null. */
    public static net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket npcGone(net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket r) {
        List<java.util.UUID> ids = new java.util.ArrayList<>();
        for (int id : r.getEntityIds()) { var u = NPCS.get(id); if (u != null) ids.add(u); }
        return ids.isEmpty() ? null : new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(ids);
    }

    /** A Cobblemon sign's block-entity data as Minecraft's sign (or hanging sign) data: the same text tags. */
    public static net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket signData(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket b) {
        boolean hanging = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(b.getType()).getPath().contains("hanging");
        try {
            var ctor = net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(net.minecraft.core.BlockPos.class,
                    net.minecraft.world.level.block.entity.BlockEntityType.class, net.minecraft.nbt.CompoundTag.class);
            ctor.setAccessible(true);
            return ctor.newInstance(b.getPos(), hanging ? net.minecraft.world.level.block.entity.BlockEntityType.HANGING_SIGN
                    : net.minecraft.world.level.block.entity.BlockEntityType.SIGN, b.getTag());
        } catch (ReflectiveOperationException e) {
            return b;
        }
    }

    /** After a chunk reaches a player without the mod: each Cobblemon sign in it as Minecraft's sign data. */
    public static void chunkSigns(ServerPlayer p, int x, int z) {
        Tasks.next(() -> {
            if (p.hasDisconnected() || !p.level().hasChunk(x, z)) return;
            for (var be : p.level().getChunk(x, z).getBlockEntities().values()) {
                var type = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                if (type.getNamespace().equals("cobblemon") && type.getPath().contains("sign"))
                    p.connection.send(signData(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(be)));
            }
        });
    }

    /** The lang key of a bare translatable text of ours or Cobblemon's (a text display's), else null. */
    public static String langKey(Object value) {
        return value instanceof net.minecraft.network.chat.Component c && c.getSiblings().isEmpty()
                && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && Lang.has(t.getKey()) ? t.getKey() : null;
    }
}
