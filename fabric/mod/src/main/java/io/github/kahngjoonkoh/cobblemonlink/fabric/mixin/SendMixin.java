package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Bridge;
import io.github.kahngjoonkoh.cobblemonlink.fabric.Clients;
import io.github.kahngjoonkoh.cobblemonlink.fabric.GeyserUtilsPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/** For vanilla clients: no Cobblemon entities (the bridge draws them), no Cobblemon entity data types. */
@Mixin(ServerCommonPacketListenerImpl.class)
public class SendMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> clbridge$filter(Packet<?> packet) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl h) || Clients.modded(h)) return packet;
        return clean(packet, h);
    }

    /** A Cobblemon entity (by its server-side type; PolyMc may already have disguised the packet's type). */
    private static boolean modded(int id, ServerGamePacketListenerImpl h) {
        var e = h.player.level().getEntity(id);
        return e != null && !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("minecraft");
    }

    private static Packet<?> clean(Packet<?> packet, ServerGamePacketListenerImpl h) {
        if (packet instanceof ClientboundBundlePacket b) {
            List<Packet<? super ClientGamePacketListener>> out = new ArrayList<>();
            for (var p : b.subPackets()) {
                var c = clean(p, h);
                if (c != null) out.add((Packet<? super ClientGamePacketListener>) c);
            }
            return new ClientboundBundlePacket(out);
        }
        if (packet instanceof ClientboundSystemChatPacket sc && !sc.overlay() && io.github.kahngjoonkoh.cobblemonlink.fabric.PartyHud.javaPanel(h.player)
                && sc.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().startsWith("cobblemon.experience."))
            return new ClientboundBundlePacket(List.of()); // as Cobblemon: on the party panel, not in chat
        Packet<?> menu = io.github.kahngjoonkoh.cobblemonlink.fabric.Mixins.menuOut(packet, h.player); // a chest for a Cobblemon menu
        if (menu instanceof ClientboundBundlePacket) return menu;
        if (menu != null) packet = menu;
        if (Clients.bedrock(h.player)) { // Cobblemon's text in English: Geyser only knows Minecraft's keys
            var t = io.github.kahngjoonkoh.cobblemonlink.fabric.BedrockText.text(packet, h.player);
            if (t == null) return new ClientboundBundlePacket(List.of());
            if (t != packet) return t;
        }
        // Bedrock: the Pokémon becomes a pig carrying GeyserModelEngine's model of its look (as on Paper)
        if (Clients.bedrock(h.player) && packet instanceof ClientboundAddEntityPacket a && h.player.level().getEntity(a.getId()) instanceof net.minecraft.world.entity.Entity ent
                && io.github.kahngjoonkoh.cobblemonlink.fabric.Mons.ensure(ent) != null) {
            var holder = io.github.kahngjoonkoh.cobblemonlink.fabric.Mons.holder(a.getId());
            List<Packet<? super ClientGamePacketListener>> out = new ArrayList<>();
            out.add(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.customEntity(a.getId(), "modelengine:" + holder.model().name())));
            out.add(new ClientboundAddEntityPacket(a.getId(), a.getUUID(), a.getX(), a.getY(), a.getZ(), a.getXRot(), a.getYRot(),
                    net.minecraft.world.entity.EntityType.PIG, 0, net.minecraft.world.phys.Vec3.ZERO, a.getYHeadRot()));
            out.add(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.scale(a.getId(), holder.scale())));
            out.add(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.anims(a.getId(), holder.bedrockAnims())));
            return new ClientboundBundlePacket(out);
        }
        // Cobblemon's own sound events: by name (the client's sound registry hasn't got their ids)
        if (packet instanceof ClientboundSoundPacket sp && sp.getSound().unwrapKey().map(k -> !k.location().getNamespace().equals("minecraft")).orElse(false))
            return new ClientboundSoundPacket(net.minecraft.core.Holder.direct(sp.getSound().value()), sp.getSource(), sp.getX(), sp.getY(), sp.getZ(), sp.getVolume(), sp.getPitch(), sp.getSeed());
        if (packet instanceof ClientboundSoundEntityPacket se && se.getSound().unwrapKey().map(k -> !k.location().getNamespace().equals("minecraft")).orElse(false)
                && h.player.level().getEntity(se.getId()) instanceof net.minecraft.world.entity.Entity at)
            return new ClientboundSoundEntityPacket(net.minecraft.core.Holder.direct(se.getSound().value()), se.getSource(), at, se.getVolume(), se.getPitch(), se.getSeed());
        // Cobblemon's spawn packets wrap the vanilla one (with extra data only its client reads): the vanilla one
        if (packet instanceof ClientboundCustomPayloadPacket c
                && c.payload() instanceof com.cobblemon.mod.common.net.messages.client.spawn.SpawnExtraDataEntityPacket<?, ?> spawn)
            return clean(spawn.getVanillaSpawnPacket(), h);
        if (packet instanceof ClientboundCustomPayloadPacket c) {
            // GeyserModelEngine's messages are for Bedrock players only
            if (c.payload() instanceof GeyserUtilsPayload && !Clients.bedrock(h.player)) return new ClientboundBundlePacket(List.of());
            // Cobblemon's move and reaction animations, drawn by the server for players without the mod
            if (c.payload() instanceof com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket pa) {
                var holder = io.github.kahngjoonkoh.cobblemonlink.fabric.Mons.holder(pa.getEntityId());
                if (holder != null) for (String n : pa.getAnimation()) if (holder.play(n)) break;
            }
            io.github.kahngjoonkoh.cobblemonlink.fabric.Battles.packet(h.player, c.payload());
            io.github.kahngjoonkoh.cobblemonlink.fabric.Mixins.callbacks(h.player, c.payload());
            if (c.payload() instanceof com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket)
                h.player.server.execute(() -> io.github.kahngjoonkoh.cobblemonlink.fabric.Pc.open(h.player));
            if (c.payload() instanceof com.cobblemon.mod.common.net.messages.client.pasture.OpenPasturePacket)
                h.player.server.execute(() -> io.github.kahngjoonkoh.cobblemonlink.fabric.Mixins.pasture(h.player));
            if (c.payload() instanceof com.cobblemon.mod.common.net.messages.client.starter.OpenStarterUIPacket)
                h.player.server.execute(() -> io.github.kahngjoonkoh.cobblemonlink.fabric.Starters.open(h.player));
            // the rest of Cobblemon's messages mean nothing to a client without it
            if (c.payload().type().id().getNamespace().equals("cobblemon")) return new ClientboundBundlePacket(List.of());
        }
        // Cobblemon's Poké Rod bobber is a vanilla fishing hook underneath: shown as one, with its line to the player
        // someone rides a Pokémon: vanilla Java gets an invisible armor stand under the Pokémon's id to sit on (the
        // model is drawn apart), so its client rides it and sends its steering (Mixins.rideInput)
        if (packet instanceof ClientboundSetPassengersPacket sp && Clients.java(h)
                && h.player.level().getEntity(sp.getVehicle()) instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pe) {
            if (sp.getPassengers().length == 0)
                return new ClientboundBundlePacket(List.of(sp, new ClientboundRemoveEntitiesPacket(pe.getId())));
            var stand = new ClientboundAddEntityPacket(pe.getId(), pe.getUUID(), pe.getX(), pe.getY(), pe.getZ(), 0, pe.getYRot(),
                    net.minecraft.world.entity.EntityType.ARMOR_STAND, 0, net.minecraft.world.phys.Vec3.ZERO, pe.getYRot());
            var look = new ClientboundSetEntityDataPacket(pe.getId(), List.of(
                    net.minecraft.network.syncher.SynchedEntityData.DataValue.create(new net.minecraft.network.syncher.EntityDataAccessor<>(0, net.minecraft.network.syncher.EntityDataSerializers.BYTE), (byte) 0x20),
                    net.minecraft.network.syncher.SynchedEntityData.DataValue.create(new net.minecraft.network.syncher.EntityDataAccessor<>(15, net.minecraft.network.syncher.EntityDataSerializers.BYTE), (byte) 0x08)));
            // the stand scaled to the Pokémon's height, so the rider sits on top (an armor stand is 1.975 tall)
            var scale = new net.minecraft.world.entity.ai.attributes.AttributeInstance(net.minecraft.world.entity.ai.attributes.Attributes.SCALE, a -> {});
            scale.setBaseValue(Math.max(0.0625, pe.getBbHeight() * 0.9 / 1.975));
            var size = new ClientboundUpdateAttributesPacket(pe.getId(), List.of(scale));
            return new ClientboundBundlePacket(List.of(stand, look, size, sp));
        }
        // Cobblemon's NPCs (trainers, the professor...) as villagers named like them; clicks reach the NPC (its id)
        if (packet instanceof ClientboundAddEntityPacket a && h.player.level().getEntity(a.getId()) instanceof com.cobblemon.mod.common.entity.npc.NPCEntity npc) {
            var as = new ClientboundAddEntityPacket(a.getId(), a.getUUID(), a.getX(), a.getY(), a.getZ(), a.getXRot(), a.getYRot(),
                    net.minecraft.world.entity.EntityType.VILLAGER, 0, net.minecraft.world.phys.Vec3.ZERO, a.getYHeadRot());
            var named = new ClientboundSetEntityDataPacket(a.getId(), List.of(
                    net.minecraft.network.syncher.SynchedEntityData.DataValue.create(new net.minecraft.network.syncher.EntityDataAccessor<>(2, net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_COMPONENT),
                            java.util.Optional.of(io.github.kahngjoonkoh.cobblemonlink.fabric.Lang.resolve(h.player, npc.getName()))),
                    net.minecraft.network.syncher.SynchedEntityData.DataValue.create(new net.minecraft.network.syncher.EntityDataAccessor<>(3, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN), true)));
            return new ClientboundBundlePacket(List.of(as, named));
        }
        if (packet instanceof ClientboundSetEntityDataPacket d && h.player.level().getEntity(d.id()) instanceof com.cobblemon.mod.common.entity.npc.NPCEntity)
            return new ClientboundSetEntityDataPacket(d.id(), d.packedItems().stream().filter(v -> v.id() < 15 && v.id() != 2 && v.id() != 3
                    && Bridge.VANILLA_DATA.contains(v.serializer())).toList()); // a living entity's fields, our name kept
        // a Poké Rod's bobber as Minecraft's: the client keeps a hook only when its data is the angler's entity id
        if (packet instanceof ClientboundAddEntityPacket a && h.player.level().getEntity(a.getId()) instanceof net.minecraft.world.entity.projectile.FishingHook hook)
            return new ClientboundAddEntityPacket(a.getId(), a.getUUID(), a.getX(), a.getY(), a.getZ(), a.getXRot(), a.getYRot(),
                    net.minecraft.world.entity.EntityType.FISHING_BOBBER, hook.getOwner() != null ? hook.getOwner().getId() : a.getData(),
                    new net.minecraft.world.phys.Vec3(a.getXa(), a.getYa(), a.getZa()), a.getYHeadRot());
        if (packet instanceof ClientboundSetEntityDataPacket d && h.player.level().getEntity(d.id()) instanceof net.minecraft.world.entity.projectile.FishingHook)
            return new ClientboundSetEntityDataPacket(d.id(), d.packedItems().stream().filter(v -> v.id() < 10 && v.id() != 2 && v.id() != 3).toList()); // the hook's own fields, no name tag
        // ponytail: an empty bundle stands for "nothing"; moves etc. for an entity the client never saw are ignored by it
        if (packet instanceof ClientboundAddEntityPacket a && (modded(a.getId(), h) || !BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).getNamespace().equals("minecraft")))
            return new ClientboundBundlePacket(List.of());
        if (packet instanceof ClientboundSetEntityDataPacket d && modded(d.id(), h)) return new ClientboundBundlePacket(List.of());
        // a Cobblemon menu (cooking pot, TM machine): a chest stands in for it (Menus)
        if (packet instanceof ClientboundOpenScreenPacket o && io.github.kahngjoonkoh.cobblemonlink.fabric.Mixins.moddedMenu(o.getType()))
            return io.github.kahngjoonkoh.cobblemonlink.fabric.Mixins.menuOpen(o, h.player);
        // a Cobblemon block entity (healing machine, PC...): the client can't decode its type
        if (packet instanceof ClientboundBlockEntityDataPacket b && !BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(b.getType()).getNamespace().equals("minecraft"))
            return new ClientboundBundlePacket(List.of());
        if (packet instanceof ClientboundSetEntityDataPacket d && d.packedItems().stream().anyMatch(v -> !Bridge.VANILLA_DATA.contains(v.serializer())))
            return new ClientboundSetEntityDataPacket(d.id(), d.packedItems().stream().filter(v -> Bridge.VANILLA_DATA.contains(v.serializer())).toList());
        return packet;
    }
}
