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
 * swims. Double-tap forward to sprint (Minecraft's own gesture: neither Bedrock nor a vanilla client sends sprint while
 * riding) while there's stamina; the stamina shows where Cobblemon shows it, over the hotbar (the XP bar's place: no
 * jump bar can be sent), and the rider's XP comes back when they get off. Cobblemon's ride sound loops (the species'
 * rideSounds: wind, plumage...) play to riders without the mod, louder with speed as Cobblemon's volume expression.
 * ponytail: one speed per Pokémon (its movement speed), one stamina rate for all (5 s of sprint, 10 s to refill), not
 * Cobblemon's per-species behaviours (horse, bird, boat...) and stamina expressions; a loop's volume is set as it
 * starts (each ~2 s), not every tick.
 */
final class Riding {
    private Riding() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.getVehicle() instanceof PokemonEntity pe && pe.getControllingPassenger() == p && Clients.bridged(p.connection)) move(p, pe);
                else if (STAMINA.remove(p.getUUID()) != null) { // off: their own XP bar back, the loops stopped
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(p.experienceProgress, p.totalExperience, p.experienceLevel));
                    SPRINT.remove(p.getUUID());
                }
                if (p.getVehicle() instanceof PokemonEntity pe && pe.getControllingPassenger() == p) RideSounds.tick(pe, p);
                // the head hidden from its Java rider (their camera sits in it), back when they get off
                Integer was = RIDDEN.get(p.getUUID()), now = p.getVehicle() instanceof PokemonEntity pe && Clients.java(p.connection) ? pe.getId() : null;
                if (java.util.Objects.equals(was, now)) continue;
                if (was != null && Mons.holder(was) != null && Mons.holder(was).stopWatching(p.connection)) Mons.holder(was).startWatching(p.connection);
                if (now != null && Mons.holder(now) != null) p.connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(Mons.holder(now).headIds()));
                if (now == null) RIDDEN.remove(p.getUUID()); else RIDDEN.put(p.getUUID(), now);
            }
        });
    }

    private static final java.util.Map<java.util.UUID, Integer> RIDDEN = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Float> STAMINA = new java.util.HashMap<>();
    /** Per rider: [sprinting 0/1, ticks since forward was let go (-1: held, never let go)] */
    private static final java.util.Map<java.util.UUID, int[]> SPRINT = new java.util.HashMap<>();

    /** Stamina after a tick (0..1): sprinting spends it in 100 ticks, resting refills it in 200. */
    static float stamina(float was, boolean sprinting) {
        return Math.max(0, Math.min(1, was + (sprinting ? -0.01f : 0.005f)));
    }

    /** Sprint state after a tick, from forward held or not: a double tap (pressed again within 7 ticks) starts it. */
    static int[] sprint(int[] s, boolean forward) {
        if (!forward) return new int[]{0, s[1] < 0 ? 0 : s[1] + 1}; // let go: count from here
        if (s[1] >= 0 && s[1] <= 7) return new int[]{1, -1};          // pressed again soon after: sprint
        return new int[]{s[0], -1};
    }

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
        int[] sp = sprint(SPRINT.getOrDefault(p.getUUID(), new int[]{0, 99}), p.zza > 0);
        float was0 = STAMINA.getOrDefault(p.getUUID(), 1f);
        boolean sprinting = sp[0] == 1 && was0 > 0;
        if (!sprinting) sp[0] = 0; // run out: tap twice again once there's some back
        SPRINT.put(p.getUUID(), sp);
        float st = stamina(was0, sprinting);
        STAMINA.put(p.getUUID(), st);
        if (st != was0 || p.tickCount % 20 == 0) // the bar Cobblemon shows over the hotbar
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(st, p.totalExperience, p.experienceLevel));
        Vec3 flat = step(p.zza, p.xxa, yaw, speed(pe.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)) * (sprinting ? 1.5 : 1));
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
