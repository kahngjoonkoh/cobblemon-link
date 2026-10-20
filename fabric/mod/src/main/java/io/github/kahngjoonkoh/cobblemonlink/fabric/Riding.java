package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Riding a Pokémon for players without the mod. Cobblemon moves a ridden Pokémon on the rider's client (its riding
 * behaviours keep their velocity client-side), so here the server moves it from the rider's steering (Mixins.rideInput
 * via Minecraft's player input): walk where the rider looks, jump on land, up/down with jump/sneak when it flies or
 * swims. ponytail: one speed per Pokémon (its movement speed), no stamina or sprint; Cobblemon's behaviours
 * (horse, bird, boat...) would replace this one day.
 */
final class Riding {
    private Riding() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.getVehicle() instanceof PokemonEntity pe && pe.getControllingPassenger() == p && Clients.bridged(p.connection)) move(p, pe);
                // the model hidden from its rider (ModelHolder.startWatching), back when they get off
                Integer was = RIDDEN.get(p.getUUID()), now = p.getVehicle() instanceof PokemonEntity pe && Clients.java(p.connection) ? pe.getId() : null;
                if (java.util.Objects.equals(was, now)) continue;
                if (was != null && Mons.holder(was) != null) Mons.holder(was).startWatching(p.connection);
                if (now != null && Mons.holder(now) != null) Mons.holder(now).stopWatching(p.connection);
                if (now == null) RIDDEN.remove(p.getUUID()); else RIDDEN.put(p.getUUID(), now);
            }
        });
    }

    private static final java.util.Map<java.util.UUID, Integer> RIDDEN = new java.util.HashMap<>();

    /** Ground speed in blocks a tick: Cobblemon's walk speed attribute, scaled the way a horse's is. */
    static double speed(double attribute) { return Math.min(0.6, attribute * 0.55); }

    /** The step for forward/strafe input (-1..1) facing yaw (degrees), at speed. */
    static Vec3 step(float forward, float strafe, float yaw, double speed) {
        double r = Math.toRadians(yaw), sin = Math.sin(r), cos = Math.cos(r);
        double len = Math.max(1, Math.hypot(forward, strafe));
        double f = forward / len * speed, s = strafe / len * speed;
        return new Vec3(s * cos - f * sin, 0, f * cos + s * sin);
    }

    private static void move(ServerPlayer p, PokemonEntity pe) {
        float yaw = p.getYRot();
        pe.setYRot(yaw);
        pe.setYBodyRot(yaw);
        pe.setYHeadRot(yaw);
        Vec3 flat = step(p.zza, p.xxa, yaw, speed(pe.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
        Vec3 was = pe.getDeltaMovement();
        boolean free = pe.isInWater() || pe.canFly(); // up and down at will
        if (p.isShiftKeyDown() && pe.onGround() && !pe.isInWater()) { // Sneak gets off (flyers and swimmers first come down)
            p.stopRiding();
            return;
        }
        double y;
        if (free) y = p.jumping ? 0.25 : p.isShiftKeyDown() ? -0.25 : 0;
        else y = pe.onGround() ? (p.jumping ? 0.55 : 0) : was.y - 0.08;
        Vec3 v = new Vec3(flat.x, y, flat.z);
        pe.setDeltaMovement(v);
        pe.move(MoverType.SELF, v);
        pe.hasImpulse = true;
    }
}
