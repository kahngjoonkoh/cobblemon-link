package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Cobblemon's ride sound loops (a species' riding.behaviours.*.rideSounds: wind, plumage...), which its client plays
 * itself, for players without the mod: each loop again as it ends, at Cobblemon's volume for the ride's speed
 * (min(v / 1.5, 1)², pitch max(1, 0.2 + that)), to the rider (playForPassengers) and to others near (playForNonPassengers).
 */
final class RideSounds {
    private RideSounds() {}

    record Loop(String event, boolean riders, boolean others, int ticks) {}

    private static final Map<String, Map<String, List<Loop>>> SPECIES = new HashMap<>();
    private static final Map<PokemonEntity, Map<String, Long>> NEXT = new java.util.WeakHashMap<>();
    private static Map<String, Path> files;
    private static JsonObject sounds;

    static void tick(PokemonEntity pe, ServerPlayer rider) {
        if (!(pe.level() instanceof ServerLevel level)) return;
        var all = loops(pe.getPokemon().getSpecies().getResourceIdentifier().getPath());
        if (all.isEmpty()) return;
        String key = pe.isInWater() ? "LIQUID" : pe.canFly() && !pe.onGround() ? "AIR" : "LAND";
        List<Loop> list = all.getOrDefault(key, all.values().iterator().next());
        double v = Math.min(pe.getDeltaMovement().length() / 1.5, 1), volume = v * v;
        float pitch = (float) Math.max(1, 0.2 + volume);
        long now = level.getGameTime();
        var next = NEXT.computeIfAbsent(pe, k -> new HashMap<>());
        for (Loop l : list) {
            if (now < next.getOrDefault(l.event(), 0L)) continue;
            if (volume < 0.005) { next.put(l.event(), now + 5); continue; } // standing still: silent, look again soon
            next.put(l.event(), now + l.ticks());
            var sound = Holder.direct(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon", l.event())));
            var packet = new ClientboundSoundEntityPacket(sound, SoundSource.NEUTRAL, pe, (float) volume, pitch, level.random.nextLong());
            for (ServerPlayer p : level.players())
                if (Clients.bridged(p.connection) && (p == rider ? l.riders() : l.others() && p.distanceToSqr(pe) < 32 * 32)) p.connection.send(packet);
        }
    }

    /** behaviour (LAND, AIR, LIQUID) -> its loops, from the species' file in Cobblemon's data. */
    static Map<String, List<Loop>> loops(String species) {
        return SPECIES.computeIfAbsent(species, s -> {
            Map<String, List<Loop>> out = new HashMap<>();
            Path f = files().get(s + ".json");
            if (f == null) return out;
            try {
                var riding = JsonParser.parseString(Files.readString(f)).getAsJsonObject().getAsJsonObject("riding");
                if (riding == null || !riding.has("behaviours")) return out;
                for (var b : riding.getAsJsonObject("behaviours").entrySet()) {
                    var rs = b.getValue().getAsJsonObject().getAsJsonArray("rideSounds");
                    if (rs == null) continue;
                    List<Loop> loops = new ArrayList<>();
                    for (var e : rs) {
                        var o = e.getAsJsonObject();
                        String event = o.get("soundLocation").getAsString().replace("cobblemon:", "");
                        int ticks = ticks(event);
                        if (ticks > 0) loops.add(new Loop(event, !o.has("playForPassengers") || o.get("playForPassengers").getAsBoolean(),
                                !o.has("playForNonPassengers") || o.get("playForNonPassengers").getAsBoolean(), ticks));
                    }
                    out.put(b.getKey(), loops);
                }
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger("cobblemonlink").warn("ride sounds of {}: {}", s, ex.toString());
            }
            return out;
        });
    }

    /** A sound event's length in ticks: its first file's Ogg Vorbis length (last granule position / sample rate). */
    static int ticks(String event) {
        try {
            if (sounds == null) sounds = JsonParser.parseString(Files.readString(cobblemon("assets/cobblemon/sounds.json"))).getAsJsonObject();
            var first = sounds.getAsJsonObject(event).getAsJsonArray("sounds").get(0);
            String name = (first.isJsonObject() ? first.getAsJsonObject().get("name").getAsString() : first.getAsString()).replace("cobblemon:", "");
            return Ogg.oggTicks(Files.readAllBytes(cobblemon("assets/cobblemon/sounds/" + name + ".ogg")));
        } catch (Exception ex) {
            return 0;
        }
    }

    /** Ogg Vorbis parsing on its own (no Cobblemon classes: testable). */
    static final class Ogg {
        static int oggTicks(byte[] b) {
            int id = indexOf(b, new byte[]{1, 'v', 'o', 'r', 'b', 'i', 's'}, 0);
            int last = -1;
            for (int i = indexOf(b, "OggS".getBytes(), 0); i >= 0; i = indexOf(b, "OggS".getBytes(), i + 4)) last = i;
            if (id < 0 || last < 0) return 0;
            long rate = le(b, id + 12, 4), granule = le(b, last + 6, 8);
            return rate <= 0 ? 0 : (int) Math.ceil(granule * 20.0 / rate);
        }

        private static long le(byte[] b, int at, int n) {
            long v = 0;
            for (int k = n - 1; k >= 0; k--) v = v << 8 | (b[at + k] & 0xFF);
            return v;
        }

        private static int indexOf(byte[] b, byte[] w, int from) {
            outer:
            for (int i = from; i <= b.length - w.length; i++) {
                for (int k = 0; k < w.length; k++) if (b[i + k] != w[k]) continue outer;
                return i;
            }
            return -1;
        }
    }

    private static Path cobblemon(String path) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cobblemon").flatMap(c -> c.findPath(path)).orElseThrow();
    }

    private static Map<String, Path> files() {
        if (files != null) return files;
        files = new HashMap<>();
        try (var walk = Files.walk(cobblemon("data/cobblemon/species"))) {
            walk.filter(Files::isRegularFile).forEach(f -> files.put(f.getFileName().toString(), f));
        } catch (Exception ignored) {}
        return files;
    }
}
