package io.github.kahngjoonkoh.cobblemonlink.fabric;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Cobblemon's sounds that its client plays on its own (cries, the Poké Ball's throw, shakes and capture), played by
 * the server for players without the mod. Java hears them from the pack (PolyMc carries Cobblemon's sounds), Bedrock
 * from tools/bedrock_sounds.py's pack (Geyser passes the name through).
 */
public final class Sounds {
    private Sounds() {}

    /** A Cobblemon sound at the entity, for the players without the mod within 32 blocks. */
    public static void play(Entity at, String event) {
        if (!(at.level() instanceof ServerLevel level)) return;
        var sound = Holder.direct(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon", event)));
        var packet = new ClientboundSoundEntityPacket(sound, SoundSource.NEUTRAL, at, 1f, 1f, level.random.nextLong());
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(at) < 32 * 32 && Clients.bridged(p.connection)) p.connection.send(packet);
    }

    /**
     * The sounds an animation of the Pokémon's own carries (Cobblemon's client plays an animation's sound_effects:
     * battle cries, quirks, a Duraludon's bonk...), at their times. The cry animation's cry stays with cry(); effects that aren't
     * Cobblemon sounds (a few Bedrock leftovers) are skipped. ponytail: one-shot animations only, not loops' sounds.
     */
    public static void animation(com.cobblemon.mod.common.entity.pokemon.PokemonEntity p, String anim) {
        String group = p.getPokemon().getSpecies().getResourceIdentifier().getPath();
        for (var e : effects(group, anim).entrySet()) {
            String effect = e.getValue();
            if (anim.equals("cry") && effect.endsWith(".cry")) continue; // cry() plays it
            Tasks.after(Math.round(e.getKey() * 20), () -> { if (p.isAlive()) play(p, effect); });
        }
    }

    private static final java.util.Map<String, java.util.Map<String, java.util.TreeMap<Float, String>>> ANIMS = new java.util.HashMap<>();
    private static java.util.Map<String, java.nio.file.Path> files;

    /** time (s) -> Cobblemon sound of "animation.<group>.<anim>", read from Cobblemon's own files on first use. */
    static java.util.Map<Float, String> effects(String group, String anim) {
        return ANIMS.computeIfAbsent(group, g -> {
            java.util.Map<String, java.util.TreeMap<Float, String>> out = new java.util.HashMap<>();
            var f = files().get(g + ".animation.json");
            if (f == null) return out;
            try {
                var all = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject().getAsJsonObject("animations");
                for (var e : all.entrySet()) {
                    var se = e.getValue().getAsJsonObject().get("sound_effects");
                    if (se == null || !se.isJsonObject()) continue;
                    var times = new java.util.TreeMap<Float, String>();
                    for (var k : se.getAsJsonObject().entrySet()) {
                        var v = k.getValue().isJsonArray() ? k.getValue().getAsJsonArray().get(0) : k.getValue();
                        if (!v.isJsonObject() || !v.getAsJsonObject().has("effect")) continue;
                        String effect = v.getAsJsonObject().get("effect").getAsString();
                        if (known().contains(effect))
                            times.put(Float.parseFloat(k.getKey()), effect);
                    }
                    out.put(e.getKey().substring(e.getKey().lastIndexOf('.') + 1), times);
                }
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger("cobblemonlink").warn("animation sounds of {}: {}", g, ex.toString());
            }
            return out;
        }).getOrDefault(anim, new java.util.TreeMap<>());
    }

    private static java.util.Set<String> known;

    /** Cobblemon's sound events (its sounds.json: the cries aren't in the server's registry). */
    private static java.util.Set<String> known() {
        if (known != null) return known;
        known = new java.util.HashSet<>();
        var f = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cobblemon").flatMap(c -> c.findPath("assets/cobblemon/sounds.json"));
        if (f.isPresent()) try {
            known.addAll(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f.get())).getAsJsonObject().keySet());
        } catch (Exception ignored) {}
        return known;
    }

    private static java.util.Map<String, java.nio.file.Path> files() {
        if (files != null) return files;
        files = new java.util.HashMap<>();
        var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cobblemon").flatMap(c -> c.findPath("assets/cobblemon/bedrock/pokemon/animations"));
        if (dir.isPresent()) try (var walk = java.nio.file.Files.walk(dir.get())) {
            walk.filter(java.nio.file.Files::isRegularFile).forEach(f -> files.put(f.getFileName().toString(), f));
        } catch (java.io.IOException ignored) {}
        return files;
    }

    public static void cry(com.cobblemon.mod.common.entity.pokemon.PokemonEntity p) {
        play(p, "pokemon." + p.getPokemon().getSpecies().getResourceIdentifier().getPath() + ".cry");
    }
}
