package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Party (max 6) and PC (Cobblemon's boxes of 30) per player, one JSON file each under plugins/CobblemonLink/players. */
final class Store {
    static final int PARTY = 6, BOX = 30;
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** A PC box: its name (null = "Box n"), its wallpaper (an index of PcScreen.WALLPAPERS, -1 = Cobblemon's teal), 30 slots. */
    static final class Box {
        String name;
        int wallpaper = -1;
        Mon[] slots = new Mon[BOX];
    }

    static final class Data {
        List<Mon> party = new ArrayList<>();
        List<Box> boxes = new ArrayList<>();
        Set<String> wallpapers = new HashSet<>(); // unlocked ones (Cobblemon's ids: biome_cave, pokemon_alpha, ...)
        Set<String> seen = new HashSet<>(), caught = new HashSet<>(); // Pokédex: species ids
        Set<String> looks = new HashSet<>(); // Pokédex: each look seen, look(): its genders, shinies and forms
        boolean hideParty; // /partyhud: no party panel on the left (Cobblemon's O key)
        List<Mon> pc; // saves from before boxes: moved into them on load

        int pcCount() {
            int n = 0;
            for (Box b : boxes) for (Mon m : b.slots) if (m != null) n++;
            return n;
        }

        /** Into the first free box slot; false if every box is full. */
        boolean toPc(Mon mon) {
            for (Box b : boxes)
                for (int i = 0; i < BOX; i++)
                    if (b.slots[i] == null) {
                        b.slots[i] = mon;
                        return true;
                    }
            return false;
        }

        boolean empty() { return party.isEmpty() && pcCount() == 0; }

        /** The Pokédex has seen this Pokémon, as it looks. */
        void see(Mon m) {
            seen.add(m.species);
            looks.add(look(m));
        }
    }

    private final Path dir;
    private final int boxes;
    private final Map<UUID, Data> cache = new HashMap<>();

    /** boxes: Cobblemon's defaultBoxCount (config pc.boxes). */
    Store(Path dir, int boxes) {
        this.dir = dir;
        this.boxes = boxes;
    }

    Data get(UUID player) {
        return cache.computeIfAbsent(player, u -> {
            Path f = dir.resolve(u + ".json");
            try {
                return fill(Files.exists(f) ? GSON.fromJson(Files.readString(f), Data.class) : new Data(), boxes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** At least n boxes; an old save's PC list moved into them. */
    static Data fill(Data d, int n) {
        while (d.boxes.size() < n) d.boxes.add(new Box());
        if (d.seen == null) d.seen = new HashSet<>();
        if (d.caught == null) d.caught = new HashSet<>();
        if (d.looks == null) d.looks = new HashSet<>();
        if (d.pc != null) {
            d.pc.forEach(m -> { if (!d.toPc(m)) spill(d, m); });
            d.pc = null;
        }
        d.party.forEach(m -> { d.caught.add(m.species); d.looks.add(look(m)); }); // saves from before the Pokédex
        for (Box b : d.boxes) for (Mon m : b.slots) if (m != null) { d.caught.add(m.species); d.looks.add(look(m)); }
        d.seen.addAll(d.caught);
        return d;
    }

    /** "species:aspect,aspect" (sorted): what the Pokédex remembers of a Pokémon's looks. */
    static String look(Mon m) {
        return m.species + ":" + String.join(",", new java.util.TreeSet<>(m.allAspects()));
    }

    /** ponytail: a full PC gets one more box rather than losing the Pokémon; Cobblemon refuses the catch instead. */
    private static void spill(Data d, Mon m) {
        Box extra = new Box();
        extra.slots[0] = m;
        d.boxes.add(extra);
    }

    /** Adds to the party, or the PC when the party is full. Returns true if it went to the party. */
    boolean give(UUID player, Mon mon) {
        Data d = get(player);
        d.see(mon);
        d.caught.add(mon.species);
        boolean toParty = d.party.size() < PARTY;
        if (toParty) d.party.add(mon);
        else if (!d.toPc(mon)) spill(d, mon);
        save(player);
        return toParty;
    }

    /** ponytail: synchronous write of a few KB per change; batch/async if it ever shows in timings. */
    void save(UUID player) {
        Data d = cache.get(player);
        if (d == null) return;
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(player + ".json.tmp");
            Files.writeString(tmp, GSON.toJson(d));
            Files.move(tmp, dir.resolve(player + ".json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void unload(UUID player) {
        save(player);
        cache.remove(player);
    }
}
