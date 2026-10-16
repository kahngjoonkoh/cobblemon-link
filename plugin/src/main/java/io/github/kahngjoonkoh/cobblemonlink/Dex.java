package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** gamedata.json, built from Cobblemon's species and spawn data by tools/build_data.py. */
final class Dex {
    static final String[] STATS = {"hp", "atk", "def", "spa", "spd", "spe"};

    static final class Species {
        String name;
        List<String> types;
        Map<String, Integer> baseStats;
        List<String> abilities;
        int catchRate, baseExp;
        String expGroup;
        double maleRatio;
        List<List<String>> moves;      // [level, move id], sorted by level (Gson reads the numbers as strings)
        List<List<String>> evolutions; // [min level, species id]

        int base(int i) { return baseStats.getOrDefault(STATS[i], 50); }
    }

    static final class Spawn {
        String species, bucket, timeRange;
        double weight;
        int[] level;
        List<String> biomes, antiBiomes;
        Integer minSkyLight, maxSkyLight, minY, maxY;
        Boolean canSeeSky, isRaining;
    }

    Map<String, Species> species;
    List<Spawn> spawns;
    Map<String, Map<String, String>> names; // kind ("move", "ability", "type") -> id -> English name

    static Dex load(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file)) {
            return new Gson().fromJson(r, Dex.class);
        }
    }

    Species get(String id) { return species.get(id); }

    String label(String kind, String id) {
        Map<String, String> m = names == null ? null : names.get(kind);
        return m == null ? id : m.getOrDefault(id, id);
    }

    String name(String id) {
        Species s = species.get(id);
        return s == null ? id : s.name;
    }
}
