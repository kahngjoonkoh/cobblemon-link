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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Party (max 6) and PC per player, one JSON file each under plugins/CobblemonLink/players. */
final class Store {
    static final int PARTY = 6;
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class Data {
        List<Mon> party = new ArrayList<>();
        List<Mon> pc = new ArrayList<>();
    }

    private final Path dir;
    private final Map<UUID, Data> cache = new HashMap<>();

    Store(Path dir) { this.dir = dir; }

    Data get(UUID player) {
        return cache.computeIfAbsent(player, u -> {
            Path f = dir.resolve(u + ".json");
            try {
                return Files.exists(f) ? GSON.fromJson(Files.readString(f), Data.class) : new Data();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Adds to the party, or the PC when the party is full. Returns true if it went to the party. */
    boolean give(UUID player, Mon mon) {
        Data d = get(player);
        boolean toParty = d.party.size() < PARTY;
        (toParty ? d.party : d.pc).add(mon);
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
