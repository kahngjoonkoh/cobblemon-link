package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
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
        String expGroup, desc; // desc: the first Pokédex entry
        double maleRatio;
        int dex;               // national Pokédex number
        List<List<String>> moves;      // [level, move id], sorted by level (Gson reads the numbers as strings)
        List<List<String>> evolutions; // [min level, species id]
        List<Form> forms = List.of();  // regional and other forms, picked by aspects
        List<String> features = List.of();
        Looks looks;

        int base(int i) { return baseStats.getOrDefault(STATS[i], 50); }
    }

    /** A form overrides only the fields it lists (null = the species' own). */
    static final class Form {
        String name, showdown;
        List<String> aspects, types, abilities;
        Map<String, Integer> baseStats;
        List<List<String>> moves, evolutions;
        Integer catchRate;
        Double maleRatio;
    }

    /** Cobblemon species feature (choice, weighted_choice, flag): a value per Pokémon, shown as an aspect. */
    static final class Feature {
        String type, aspectFormat;
        List<String> keys;
        @SerializedName("default") Object def;
        Object choices;     // choice: [values]; weighted_choice: {value: weight}
        Boolean isAspect;
    }

    /** Cobblemon's resolver variations: [aspects, model or null, texture index or null], layered in order. */
    static final class Looks {
        List<List<Object>> variations;
        Map<String, String> models; // "model|texture index" -> converted model name
    }

    static final class Spawn {
        String species, bucket, timeRange;
        double weight;
        int[] level;
        List<String> biomes, antiBiomes;
        Integer minSkyLight, maxSkyLight, minY, maxY;
        Boolean canSeeSky, isRaining;
        List<String> props = List.of(); // Cobblemon spawn properties: alolan, flower=white, region_bias=hisui
    }

    Map<String, Species> species;
    Map<String, Feature> features = Map.of();
    private final Map<String, Species> merged = new java.util.concurrent.ConcurrentHashMap<>();
    List<Spawn> spawns;
    Map<String, Map<String, String>> names; // kind ("move", "ability", "type") -> id -> English name

    static Dex load(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file)) {
            return new Gson().fromJson(r, Dex.class);
        }
    }

    Species get(String id) { return species.get(id); }

    /** Cobblemon's form for these aspects: the first whose aspects the Pokémon all has; null = the base form. */
    Form form(String id, java.util.Set<String> aspects) {
        Species s = species.get(id);
        if (s == null) return null;
        for (Form f : s.forms) if (!f.aspects.isEmpty() && aspects.containsAll(f.aspects)) return f;
        return null;
    }

    /** The species data a Pokémon battles and grows with: its form's, where the form defines them. */
    Species of(Mon m) {
        Species s = species.get(m.species);
        Form f = form(m.species, m.allAspects());
        if (f == null) return s;
        return merged.computeIfAbsent(m.species + "/" + f.name, k -> {
            Species x = new Species();
            x.name = s.name;
            x.types = f.types != null ? f.types : s.types;
            x.baseStats = f.baseStats != null ? f.baseStats : s.baseStats;
            x.abilities = f.abilities != null ? f.abilities : s.abilities;
            x.moves = f.moves != null ? f.moves : s.moves;
            x.evolutions = f.evolutions != null ? f.evolutions : s.evolutions;
            x.catchRate = f.catchRate != null ? f.catchRate : s.catchRate;
            x.maleRatio = f.maleRatio != null ? f.maleRatio : s.maleRatio;
            x.baseExp = s.baseExp;
            x.expGroup = s.expGroup;
            x.forms = s.forms;
            x.features = s.features;
            x.looks = s.looks;
            return x;
        });
    }

    /** The converted model a Pokémon is drawn with: Cobblemon's VaryingModelRepository layering, else the base. */
    String model(String id, java.util.Set<String> aspects) {
        Species s = species.get(id);
        if (s == null || s.looks == null) return id;
        Object model = null, texture = null;
        for (List<Object> v : s.looks.variations) {
            @SuppressWarnings("unchecked") List<String> need = (List<String>) v.get(0);
            if (!aspects.containsAll(need)) continue;
            if (v.get(1) != null) model = v.get(1);
            if (v.get(2) != null) texture = ((Number) v.get(2)).intValue();
        }
        return s.looks.models.getOrDefault(model + "|" + texture, id);
    }

    String label(String kind, String id) {
        Map<String, String> m = names == null ? null : names.get(kind);
        return m == null ? id : m.getOrDefault(id, id);
    }

    String name(String id) {
        Species s = species.get(id);
        return s == null ? id : s.name;
    }
}
