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
        if (Clients.bedrock(h.player)) { // Cobblemon's text in English: Geyser only knows Minecraft's keys
            var t = io.github.kahngjoonkoh.cobblemonlink.fabric.BedrockText.text(packet);
            if (t != packet) return t;
        }
        // Bedrock: the Pokémon becomes a pig carrying GeyserModelEngine's model of its look (as on Paper)
        if (Clients.bedrock(h.player) && packet instanceof ClientboundAddEntityPacket a
                && h.player.level().getEntity(a.getId()) instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity p) {
            var holder = io.github.kahngjoonkoh.cobblemonlink.fabric.Mons.holder(p.getId());
            List<Packet<? super ClientGamePacketListener>> out = new ArrayList<>();
            out.add(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.customEntity(a.getId(), "modelengine:" + io.github.kahngjoonkoh.cobblemonlink.fabric.Mons.look(p))));
            out.add(new ClientboundAddEntityPacket(a.getId(), a.getUUID(), a.getX(), a.getY(), a.getZ(), a.getXRot(), a.getYRot(),
                    net.minecraft.world.entity.EntityType.PIG, 0, net.minecraft.world.phys.Vec3.ZERO, a.getYHeadRot()));
            out.add(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.scale(a.getId(), io.github.kahngjoonkoh.cobblemonlink.fabric.Mons.scale(p))));
            if (holder != null) out.add(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.anims(a.getId(), holder.bedrockAnims())));
            return new ClientboundBundlePacket(out);
        }
        if (packet instanceof ClientboundCustomPayloadPacket c) {
            // GeyserModelEngine's messages are for Bedrock players only
            if (c.payload() instanceof GeyserUtilsPayload && !Clients.bedrock(h.player)) return new ClientboundBundlePacket(List.of());
            // Cobblemon's move and reaction animations, drawn by the server for players without the mod
            if (c.payload() instanceof com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket pa) {
                var holder = io.github.kahngjoonkoh.cobblemonlink.fabric.Mons.holder(pa.getEntityId());
                if (holder != null) for (String n : pa.getAnimation()) if (holder.play(n)) break;
            }
            io.github.kahngjoonkoh.cobblemonlink.fabric.Battles.packet(h.player, c.payload());
            // the rest of Cobblemon's messages mean nothing to a client without it
            if (c.payload().type().id().getNamespace().equals("cobblemon")) return new ClientboundBundlePacket(List.of());
        }
        // ponytail: an empty bundle stands for "nothing"; moves etc. for an entity the client never saw are ignored by it
        if (packet instanceof ClientboundAddEntityPacket a && (modded(a.getId(), h) || !BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).getNamespace().equals("minecraft")))
            return new ClientboundBundlePacket(List.of());
        if (packet instanceof ClientboundSetEntityDataPacket d && modded(d.id(), h)) return new ClientboundBundlePacket(List.of());
        // a Cobblemon menu (cooking pot...): the client can't open its type. ponytail: the server keeps it open until Esc
        if (packet instanceof ClientboundOpenScreenPacket o && !BuiltInRegistries.MENU.getKey(o.getType()).getNamespace().equals("minecraft"))
            return new ClientboundBundlePacket(List.of());
        // a Cobblemon block entity (healing machine, PC...): the client can't decode its type
        if (packet instanceof ClientboundBlockEntityDataPacket b && !BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(b.getType()).getNamespace().equals("minecraft"))
            return new ClientboundBundlePacket(List.of());
        if (packet instanceof ClientboundSetEntityDataPacket d && d.packedItems().stream().anyMatch(v -> !Bridge.VANILLA_DATA.contains(v.serializer())))
            return new ClientboundSetEntityDataPacket(d.id(), d.packedItems().stream().filter(v -> Bridge.VANILLA_DATA.contains(v.serializer())).toList());
        return packet;
    }
}
