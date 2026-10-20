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
        String hidden; // hidden ability (the Ability Patch's), or null
        int catchRate, baseExp, baseFriendship = 50;
        double weight;     // hectograms
        boolean ultraBeast, shoulder; // shoulder: Cobblemon's shoulderMountable
        double size = 1;              // max(hitbox width, height) x baseScale (Mon.drawScale)
        Map<String, Integer> evYield = Map.of(); // EVs for defeating it (Gen 8), stat -> amount
        List<String> tmMoves = List.of(), eggMoves = List.of(), tutorMoves = List.of();
        String expGroup, desc; // desc: the first Pokédex entry
        double maleRatio;
        int dex;               // national Pokédex number
        List<List<String>> moves;      // [level, move id], sorted by level (Gson reads the numbers as strings)
        List<List<String>> evolutions; // [min level, species id]
        List<Evo> evos = List.of();    // every other evolution, as Cobblemon's species files write it (Evolutions)
        List<Form> forms = List.of();  // regional and other forms, picked by aspects
        List<String> features = List.of();
        Looks looks;

        int base(int i) { return baseStats.getOrDefault(STATS[i], 50); }
    }

    /** A form overrides only the fields it lists (null = the species' own). */
    static final class Form {
        String name, showdown;
        List<String> aspects, types, abilities;
        String hidden;
        Map<String, Integer> baseStats;
        List<List<String>> moves, evolutions;
        List<Evo> evos;
        Integer catchRate;
        Double maleRatio;
        Map<String, Integer> evYield;
    }

    /** One Cobblemon evolution: variant (level_up, item_interact, trade), result ("raichu alolan"), item, requirements. */
    static final class Evo {
        String variant, result, item;
        List<Map<String, Object>> requirements = List.of();

        String species() { return result.split(" ")[0]; }
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
        Map<String, Map<String, String>> cosmetics = Map.of(); // cosmetic aspect -> {look without it: look with it}
    }

    /** One of Cobblemon's cosmetic_items files: the species that can wear them, item (or #tag) -> aspect. */
    static final class Cosmetic {
        List<String> species = List.of();
        Map<String, String> items = Map.of();
    }

    static final class Spawn {
        String species, bucket, type = "grounded"; // type: Cobblemon's spawnablePositionType (grounded, submerged, surface, seafloor)
        double weight;
        int[] level;
        Cond cond = new Cond(), anti;               // Cobblemon's condition and anticondition, as written
        List<String> props = List.of(); // Cobblemon spawn properties: alolan, flower=white, region_bias=hisui
        List<HerdMember> herd;          // a herd spawn: its leader and followers (Spawner.herd)
        int maxHerd;
    }

    /** One of a herd's Pokémon: Cobblemon's herdablePokemon. */
    static final class HerdMember {
        String species;
        List<String> props = List.of();
        double weight;
        int[] level;
        int max = 1;
        boolean leader;
    }

    /** One of Cobblemon's spawning conditions; a null field isn't checked. */
    static final class Cond {
        List<String> biomes, structures, neededNearbyBlocks, neededBaseBlocks;
        Integer minSkyLight, maxSkyLight, minY, maxY, minLight, maxLight, minX, maxX, minLureLevel, maxLureLevel;
        String rodType; // "cobblemon:love_rod"
        Boolean canSeeSky, isRaining, isThundering, isSlimeChunk;
        String timeRange;
        Object moonPhase; // "1-3", 0, or a list of either
    }

    Map<String, Species> species;
    Map<String, Feature> features = Map.of();
    private final Map<String, Species> merged = new java.util.concurrent.ConcurrentHashMap<>();
    List<Spawn> spawns;
    Map<String, List<String>> fossils = Map.of(); // species -> the fossils it's revived from
    Map<String, Npcs.Npc> npcs = Map.of();          // Cobblemon's NPC classes that battle or talk
    Map<String, Map<String, Object>> dialogues = Map.of(); // Cobblemon's dialogues (Dialogues.java)
    Map<String, Mark> marks = Map.of();             // Cobblemon's marks with a chance on wild Pokémon
    Map<String, List<Bait>> baits = Map.of();       // Cobblemon's bait (spawn_bait_effects): item -> effects
    List<Cosmetic> cosmetics = List.of();

    /** The cosmetic aspect this item gives this species, or null (an item id, vanilla "minecraft:x", or one of its tags). */
    String cosmeticFor(String species, String item, org.bukkit.Material vanilla) {
        for (Cosmetic c : cosmetics) {
            if (!c.species.contains(species)) continue;
            for (var e : c.items.entrySet()) {
                String k = e.getKey();
                if (k.equals(item)) return e.getValue();
                if (vanilla != null && k.startsWith("#")) {
                    var tag = org.bukkit.Bukkit.getTag(org.bukkit.Tag.REGISTRY_ITEMS, org.bukkit.NamespacedKey.fromString(k.substring(1)), org.bukkit.Material.class);
                    if (tag != null && tag.isTagged(vanilla)) return e.getValue();
                }
            }
        }
        return null;
    }

    static final class Bait {
        String type, sub;
        double chance = 1, value;
    }

    static final class Mark {
        double chance;
        String group;
    }
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
            x.hidden = f.abilities != null ? f.hidden : s.hidden;
            x.moves = f.moves != null ? f.moves : s.moves;
            x.evolutions = f.evolutions != null ? f.evolutions : s.evolutions;
            x.evos = f.evos != null ? f.evos : s.evos;
            x.catchRate = f.catchRate != null ? f.catchRate : s.catchRate;
            x.maleRatio = f.maleRatio != null ? f.maleRatio : s.maleRatio;
            x.baseExp = s.baseExp;
            x.baseFriendship = s.baseFriendship;
            x.weight = s.weight;
            x.ultraBeast = s.ultraBeast;
            x.shoulder = s.shoulder;
            x.size = s.size;
            x.evYield = f.evYield != null ? f.evYield : s.evYield;
            x.tmMoves = s.tmMoves;
            x.eggMoves = s.eggMoves;
            x.tutorMoves = s.tutorMoves;
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

    transient Lang lang = Lang.load(null); // the plugin swaps in the one with Cobblemon's languages

    /** A species, move, ability or type name in a language (Cobblemon's lang keys), else the English one. */
    String name(String id, String locale) {
        return lang.getOr(locale, "cobblemon.species." + id + ".name", name(id));
    }

    String label(String kind, String id, String locale) {
        return lang.getOr(locale, "cobblemon." + kind + "." + id, label(kind, id));
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
