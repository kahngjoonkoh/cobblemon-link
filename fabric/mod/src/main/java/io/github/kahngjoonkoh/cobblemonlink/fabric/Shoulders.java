package io.github.kahngjoonkoh.cobblemonlink.fabric;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Pokémon on players' shoulders for clients without the mod (Cobblemon keeps them in Minecraft's shoulder data, which
 * only a Cobblemon client draws): vanilla Java a small model (ModelHolder) on the shoulder, Bedrock the
 * GeyserModelEngine model on a carrier moved with the player.
 * ponytail: others see it, the player doesn't (their first-person camera would sit inside it; the server can't tell
 * when they look in third person).
 */
final class Shoulders {
    /** How big Cobblemon draws a shoulder Pokémon against its normal size. ponytail: one factor for all. */
    static final float SIZE = 0.5f;

    private record Drawn(String key, ModelHolder java, ElementHolder bedrock) {
        void destroy() { if (java != null) java.destroy(); bedrock.destroy(); }
    }

    private static final Map<UUID, Drawn[]> DRAWN = new HashMap<>();
    private static int tick;

    private Shoulders() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tick % 5 != 0) return;
            Set<UUID> online = new HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                online.add(p.getUUID());
                Drawn[] d = DRAWN.computeIfAbsent(p.getUUID(), u -> new Drawn[2]);
                update(p, d, 0, p.getShoulderEntityLeft());
                update(p, d, 1, p.getShoulderEntityRight());
            }
            DRAWN.entrySet().removeIf(e -> {
                if (online.contains(e.getKey())) return false;
                for (Drawn d : e.getValue()) if (d != null) d.destroy();
                return true;
            });
        });
    }

    /** "species|aspects|scale" for a Pokémon on the shoulder, else null. */
    static String key(CompoundTag t) {
        if (t == null || !"cobblemon:pokemon".equals(t.getString("id")) || !t.contains("shoulder_species")) return null;
        StringBuilder a = new StringBuilder();
        for (Tag x : t.getList("shoulder_aspects", Tag.TAG_STRING)) a.append(x.getAsString()).append(',');
        return t.getString("shoulder_species") + "|" + a + "|" + (t.contains("shoulder_scale") ? t.getFloat("shoulder_scale") : 1f);
    }

    private static void update(ServerPlayer p, Drawn[] d, int side, CompoundTag tag) {
        String key = key(tag);
        if (d[side] != null && d[side].key.equals(key)) return;
        if (d[side] != null) d[side].destroy();
        d[side] = key == null ? null : draw(p, side, key);
    }

    private static Drawn draw(ServerPlayer p, int side, String key) {
        String[] k = key.split("\\|", -1);
        String species = net.minecraft.resources.ResourceLocation.parse(k[0]).getPath();
        Set<String> aspects = new HashSet<>(java.util.Arrays.asList(k[1].split(",")));
        aspects.remove("");
        String look = Models.look(species, aspects);
        float scale = Float.parseFloat(k[2]) * SIZE;
        float x = side == 0 ? 0.4f : -0.4f, y = 1.35f; // left / right shoulder, in the player's facing frame
        ModelHolder java = null;
        Models.Model m = Models.get(look);
        if (m != null) {
            java = new ModelHolder(p, m, scale, false);
            java.offset(x, y, 0);
            java.state(() -> "idle");
            EntityAttachment.ofTicking(java, p);
        }
        // Bedrock: the model on a carrier that follows the shoulder
        SimpleEntityElement pig = new SimpleEntityElement(EntityType.PIG);
        pig.setNoGravity(true);
        pig.setSilent(true);
        ElementHolder bedrock = new ElementHolder() {
            @Override public boolean startWatching(ServerGamePacketListenerImpl h) {
                if (!Clients.bedrock(h.player) || !Models.has(look)) return false;
                h.send(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.customEntity(pig.getEntityId(), "modelengine:" + look)));
                boolean r = super.startWatching(h);
                h.send(new ClientboundCustomPayloadPacket(GeyserUtilsPayload.scale(pig.getEntityId(), scale)));
                return r;
            }

            @Override protected void onTick() {
                float yaw = p.yBodyRot;
                double rad = Math.toRadians(yaw);
                // facing frame -> world: forward is (-sin, cos), left is (cos, sin)
                pig.setOffset(new Vec3(x * Math.cos(rad), y, x * Math.sin(rad)));
                pig.setYaw(yaw);
            }
        };
        bedrock.addElement(pig);
        EntityAttachment.ofTicking(bedrock, p);
        return new Drawn(key, java, bedrock);
    }
}
