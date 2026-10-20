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
 * Effects the name table doesn't place (most move effects) are dust in the effect's own colour, read from its
 * Snowstorm tint. ponytail: a name table and one colour, not Snowstorm; good enough to see sparkles, evolutions,
 * hits and heals, and which move it was.
 */
final class Particles {
    private Particles() {}

    private static int tick;

    /** Shiny Pokémon sparkle now and then, as Cobblemon's client makes them (it starts that effect itself). */
    static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tick % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!Clients.bridged(p.connection)) continue;
                // effects a species' idle starts on Cobblemon's client (Magcargo's lava bubbles, Gastly's gas...), every second
                for (var pe : p.serverLevel().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, p.getBoundingBox().inflate(24), e -> !e.isInvisible())) {
                    String sp = pe.getPokemon().getSpecies().getResourceIdentifier().getPath();
                    var fx = ambient().get(pe.getPokemon().getShiny() && ambient().containsKey(sp + "_shiny") ? sp + "_shiny" : sp);
                    Packet<?> pk = fx == null ? null : packet(fx, pe.position().add(0, pe.getBbHeight() * 0.6, 0)); // ponytail: the body, not the locator
                    if (pk != null) p.connection.send(pk);
                }
                if (tick % 40 != 0) continue;
                for (var pe : p.serverLevel().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, p.getBoundingBox().inflate(24),
                        e -> e.getPokemon().getShiny() && !e.isInvisible())) {
                    p.connection.send(new ClientboundLevelParticlesPacket(ParticleTypes.END_ROD, false, pe.getX(), pe.getY() + pe.getBbHeight() * 0.6, pe.getZ(),
                            pe.getBbWidth() * 0.4f, pe.getBbHeight() * 0.3f, pe.getBbWidth() * 0.4f, 0.01f, 4));
                    p.connection.send(new ClientboundLevelParticlesPacket(dust("shiny_sparkle_ambient"), false, pe.getX(), pe.getY() + pe.getBbHeight() * 0.6, pe.getZ(),
                            pe.getBbWidth() * 0.4f, pe.getBbHeight() * 0.3f, pe.getBbWidth() * 0.4f, 0, 3));
                }
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

    /** Effect id ("cobblemon:...") -> its tint as 0xRRGGBB, from Cobblemon's Snowstorm files; read once. */
    private static java.util.Map<String, Integer> colours;

    private static synchronized java.util.Map<String, Integer> colours() {
        if (colours != null) return colours;
        colours = new java.util.HashMap<>();
        var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cobblemon").flatMap(c -> c.findPath("assets/cobblemon/bedrock/particles"));
        if (dir.isPresent()) try (var files = java.nio.file.Files.walk(dir.get())) {
            files.filter(f -> f.toString().endsWith(".json")).forEach(f -> {
                try {
                    var pe = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject().getAsJsonObject("particle_effect");
                    var tint = pe.getAsJsonObject("components").getAsJsonObject("minecraft:particle_appearance_tinting");
                    Integer c = tint == null ? null : colour(tint.get("color"));
                    if (c != null) colours.put(pe.getAsJsonObject("description").get("identifier").getAsString(), c);
                } catch (Exception ignored) { // ponytail: a few of Cobblemon's files aren't strict JSON; those keep the name table
                }
            });
        } catch (java.io.IOException ignored) {
        }
        return colours;
    }

    /**
     * A Snowstorm tint as 0xRRGGBB: [r, g, b(, a)] in 0..1, "#AARRGGBB", or a gradient of those (its most colourful
     * key, since white is mostly the highlight); null for MoLang or nothing.
     */
    static Integer colour(com.google.gson.JsonElement t) {
        if (t == null) return null;
        if (t.isJsonArray()) {
            var a = t.getAsJsonArray();
            if (a.size() < 3 || !a.get(0).isJsonPrimitive() || !a.get(0).getAsJsonPrimitive().isNumber()) return null;
            int rgb = 0;
            for (int i = 0; i < 3; i++) rgb = rgb << 8 | Math.round(Math.max(0, Math.min(1, a.get(i).getAsFloat())) * 255);
            return rgb;
        }
        if (t.isJsonPrimitive() && t.getAsString().startsWith("#")) {
            String h = t.getAsString().substring(1);
            try { return (int) (Long.parseLong(h, 16) & 0xFFFFFF); } catch (NumberFormatException e) { return null; }
        }
        if (t.isJsonObject() && t.getAsJsonObject().has("gradient") && t.getAsJsonObject().get("gradient").isJsonObject()) {
            Integer best = null;
            int most = -1;
            for (var e : t.getAsJsonObject().getAsJsonObject("gradient").entrySet()) {
                Integer c = colour(e.getValue());
                if (c == null) continue;
                int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255, sat = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                if (sat > most) { most = sat; best = c; }
            }
            return best;
        }
        return null;
    }

    /** Cobblemon's particle payload as a vanilla particle packet, or null when there's nothing to show. */
    static Packet<?> of(ServerPlayer p, CustomPacketPayload payload) {
        Vec3 at;
        String effect;
        net.minecraft.resources.ResourceLocation id;
        if (payload instanceof SpawnSnowstormParticlePacket s) {
            at = s.getPosition();
            id = s.getEffectId();
            effect = id.getPath();
        } else if (payload instanceof SpawnSnowstormEntityParticlePacket s) {
            var e = p.level().getEntity(s.getSourceEntityId());
            if (e == null) return null;
            at = e.position().add(0, e.getBbHeight() / 2, 0);
            id = s.getEffectId();
            effect = id.getPath();
        } else return null;
        return packet(id, at);
    }

    /** The vanilla particles for Cobblemon effect id at a point, or null when there's nothing to show. */
    static Packet<?> packet(net.minecraft.resources.ResourceLocation id, Vec3 at) {
        String effect = id.getPath();
        Look l = look(effect.substring(effect.lastIndexOf('/') + 1));
        Integer c = colours().get(id.toString());
        if (c != null && (l == null || l.type() == ParticleTypes.CRIT)) // move effects: dust in their own colour
            l = new Look(dust(c), l == null ? 8 : l.count(), l == null ? 0.4f : l.spread());
        return l == null ? null : new ClientboundLevelParticlesPacket(l.type(), false, at.x, at.y, at.z, l.spread(), l.spread(), l.spread(), 0.02f, l.count());
    }

    /** Dust in an effect's own colour (white when it has none), for effects the name table draws otherwise too. */
    static ParticleOptions dust(String effect) {
        Integer c = colours().get("cobblemon:" + effect);
        return dust(c == null ? 0xFFFFFF : c);
    }

    private static ParticleOptions dust(int c) {
        return new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f), 1.2f);
    }

    /** Species (and species_shiny) -> the effect its idle animation starts on Cobblemon's client (lava bubbles...). */
    private static java.util.Map<String, net.minecraft.resources.ResourceLocation> ambient;

    static synchronized java.util.Map<String, net.minecraft.resources.ResourceLocation> ambient() {
        if (ambient != null) return ambient;
        ambient = new java.util.HashMap<>();
        var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cobblemon").flatMap(c -> c.findPath("assets/cobblemon/bedrock/pokemon/animations"));
        if (dir.isPresent()) try (var files = java.nio.file.Files.walk(dir.get())) {
            files.filter(f -> f.toString().endsWith(".animation.json")).forEach(f -> {
                try {
                    String folder = f.getParent().getFileName().toString(), species = folder.substring(folder.indexOf('_') + 1);
                    if (f.getFileName().toString().contains("_shiny")) species += "_shiny";
                    var anims = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject().getAsJsonObject("animations");
                    String effect = null;
                    for (var a : anims.entrySet()) { // the idle's effect first, else any
                        var fx = a.getValue().getAsJsonObject().getAsJsonObject("particle_effects");
                        if (fx == null) continue;
                        for (var k : fx.entrySet()) {
                            var v = k.getValue().isJsonArray() ? k.getValue().getAsJsonArray().get(0) : k.getValue();
                            if (!v.isJsonObject() || !v.getAsJsonObject().has("effect")) continue;
                            if (effect == null || a.getKey().endsWith("idle")) effect = v.getAsJsonObject().get("effect").getAsString();
                        }
                    }
                    if (effect != null) ambient.put(species, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", effect.replace("cobblemon:", "")));
                } catch (Exception ignored) { // ponytail: not strict JSON; that species keeps no ambient effect
                }
            });
        } catch (java.io.IOException ignored) {
        }
        return ambient;
    }
}
