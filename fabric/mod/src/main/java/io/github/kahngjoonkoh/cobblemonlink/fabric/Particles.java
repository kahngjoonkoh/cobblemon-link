package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Cobblemon's particle effects (Snowstorm, drawn by its client) as Minecraft's particles for players without the mod:
 * the closest vanilla particle by the effect's name, a small burst where Cobblemon would draw it.
 * ponytail: a name table, not Snowstorm; good enough to see sparkles, evolutions, hits and heals.
 */
final class Particles {
    private Particles() {}

    private static int tick;

    /** Shiny Pokémon sparkle now and then, as Cobblemon's client makes them (it starts that effect itself). */
    static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tick % 40 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!Clients.bridged(p.connection)) continue;
                for (var pe : p.serverLevel().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, p.getBoundingBox().inflate(24),
                        e -> e.getPokemon().getShiny() && !e.isInvisible()))
                    p.connection.send(new ClientboundLevelParticlesPacket(ParticleTypes.END_ROD, false, pe.getX(), pe.getY() + pe.getBbHeight() * 0.6, pe.getZ(),
                            pe.getBbWidth() * 0.4f, pe.getBbHeight() * 0.3f, pe.getBbWidth() * 0.4f, 0.01f, 4));
            }
        });
    }

    /** The vanilla particle for a Cobblemon effect id (path), and how many; null to show nothing. */
    record Look(ParticleOptions type, int count, float spread) {}

    static Look look(String effect) {
        if (effect.contains("shiny") || effect.contains("sparkle")) return new Look(ParticleTypes.END_ROD, 6, 0.4f);
        if (effect.startsWith("evo") || effect.contains("evolution")) return new Look(ParticleTypes.END_ROD, 24, 0.8f);
        if (effect.contains("heal") || effect.contains("love") || effect.contains("heart")) return new Look(ParticleTypes.HEART, 5, 0.5f);
        if (effect.contains("fire") || effect.contains("flame") || effect.contains("ember") || effect.contains("lava")) return new Look(ParticleTypes.FLAME, 10, 0.4f);
        if (effect.contains("water") || effect.contains("bubble") || effect.contains("splash")) return new Look(ParticleTypes.SPLASH, 12, 0.5f);
        if (effect.contains("electric") || effect.contains("thunder") || effect.contains("spark")) return new Look(ParticleTypes.ELECTRIC_SPARK, 12, 0.5f);
        if (effect.contains("smoke") || effect.contains("poof") || effect.contains("ball")) return new Look(ParticleTypes.POOF, 8, 0.3f);
        if (effect.contains("leaf") || effect.contains("leaves") || effect.contains("grass")) return new Look(ParticleTypes.CHERRY_LEAVES, 8, 0.6f);
        if (effect.contains("tailflame")) return new Look(ParticleTypes.SMALL_FLAME, 1, 0.05f);
        if (effect.startsWith("moves") || effect.contains("hit") || effect.contains("impact")) return new Look(ParticleTypes.CRIT, 10, 0.4f);
        return null;
    }

    /** Cobblemon's particle payload as a vanilla particle packet, or null when there's nothing to show. */
    static Packet<?> of(ServerPlayer p, CustomPacketPayload payload) {
        Vec3 at;
        String effect;
        if (payload instanceof SpawnSnowstormParticlePacket s) {
            at = s.getPosition();
            effect = s.getEffectId().getPath();
        } else if (payload instanceof SpawnSnowstormEntityParticlePacket s) {
            var e = p.level().getEntity(s.getSourceEntityId());
            if (e == null) return null;
            at = e.position().add(0, e.getBbHeight() / 2, 0);
            effect = s.getEffectId().getPath();
        } else return null;
        Look l = look(effect.substring(effect.lastIndexOf('/') + 1));
        return l == null ? null : new ClientboundLevelParticlesPacket(l.type(), false, at.x, at.y, at.z, l.spread(), l.spread(), l.spread(), 0.02f, l.count());
    }
}
