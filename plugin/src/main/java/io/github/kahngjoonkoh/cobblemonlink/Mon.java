package io.github.kahngjoonkoh.cobblemonlink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** One Pokémon. Saved as JSON (party/PC files, wild entity tags). */
final class Mon {
    static final String[] NATURES = {"Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed",
            "Impish", "Lax", "Timid", "Hasty", "Serious", "Jolly", "Naive", "Modest", "Mild", "Quiet", "Bashful",
            "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky"};

    String id = UUID.randomUUID().toString();
    String species, nature, ability, gender;
    int level;
    long exp;
    int[] ivs = new int[6];
    List<String> moves = new ArrayList<>();
    int hp = -1; // -1 = full
    List<String> aspects = new ArrayList<>(); // from species features: alolan, flower-white, west-sea, ...
    boolean shiny;
    List<String> learnable = new ArrayList<>(); // level-up moves it wanted with 4 known: MoveLearn offers them
    int[] evs = new int[6];
    String status = "";  // Showdown id (par, brn, slp, frz, psn, tox), kept after battle like Cobblemon
    int statusSecs;      // seconds out of battle until the status wears off (Cobblemon: 180-300)
    List<Integer> pp;    // current PP per move, null = full
    int friendship = -1; // -1: the species' base friendship (saves from before friendship)
    String ball = "poke_ball";
    String trainer;      // original trainer's UUID (Cobblemon's 1.5x EXP for traded Pokémon)
    int faintSecs = -1, healSecs; // Cobblemon's faint and passive-heal timers (out of battle)
    List<Integer> ppMax; // max PP per move, as the last battle reported it (Ether, Elixir)
    String mint;         // a mint's nature: Cobblemon's effective nature for stats and battles
    String held;         // held item id (Cobblemon's), or null
    boolean fished;      // reeled in with a Poké Rod (Cobblemon's "fished" aspect: the Lure Ball)

    static int shinyRate = 8192; // Cobblemon's shinyRate (config.yml)

    static Mon create(Dex dex, String species, int level) { return create(dex, species, level, List.of()); }

    /**
     * A new Pokémon. props: Cobblemon's spawn properties after the species (alolan, flower=white, shiny, female,
     * region_bias=hisui); every species feature they don't set takes its default ("random" picks one).
     */
    static Mon create(Dex dex, String species, int level, List<String> props) {
        var r = ThreadLocalRandom.current();
        Mon m = new Mon();
        m.species = species;
        m.shiny = props.contains("shiny") || r.nextInt(shinyRate) == 0;
        m.aspects = featureAspects(dex, species, props);
        Dex.Species s = dex.of(m);
        m.level = Math.max(1, Math.min(100, level));
        m.exp = Rules.expFor(s.expGroup, m.level);
        m.nature = NATURES[r.nextInt(NATURES.length)];
        m.ability = s.abilities.get(r.nextInt(s.abilities.size()));
        m.gender = props.contains("male") ? "M" : props.contains("female") ? "F"
                : s.maleRatio < 0 ? "" : r.nextDouble() < s.maleRatio ? "M" : "F";
        for (int i = 0; i < 6; i++) m.ivs[i] = r.nextInt(32);
        m.friendship = s.baseFriendship;
        // the four most recent level-up moves, like a wild Pokémon in the main games
        for (List<String> lm : s.moves) {
            if (Integer.parseInt(lm.get(0)) > m.level || m.moves.contains(lm.get(1))) continue;
            m.moves.add(lm.get(1));
            if (m.moves.size() > 4) m.moves.removeFirst();
        }
        return m;
    }

    /** Cobblemon's species features as aspects (spawn properties first, then each feature's default). */
    static List<String> featureAspects(Dex dex, String species, List<String> props) {
        var r = ThreadLocalRandom.current();
        Map<String, String> given = new LinkedHashMap<>();
        for (String p : props) {
            int eq = p.indexOf('=');
            if (eq > 0) given.put(p.substring(0, eq), p.substring(eq + 1));
            else given.put(p, "true"); // bare flag (alolan); shiny/male/female are not features
        }
        List<String> out = new ArrayList<>();
        for (String name : dex.get(species).features) {
            Dex.Feature f = dex.features.get(name);
            if (f == null || Boolean.FALSE.equals(f.isAspect) && !"flag".equals(f.type)) continue;
            String value = f.keys.stream().map(given::get).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            if (value == null) value = f.def == null ? null : String.valueOf(f.def);
            if ("flag".equals(f.type)) {
                if ("true".equals(value)) out.add(f.keys.getFirst());
                continue;
            }
            if ("random".equals(value)) {
                if (f.choices instanceof List<?> l) value = String.valueOf(l.get(r.nextInt(l.size())));
                else if (f.choices instanceof Map<?, ?> w) { // weighted_choice
                    double roll = r.nextDouble(w.values().stream().mapToDouble(x -> ((Number) x).doubleValue()).sum());
                    for (var e : w.entrySet()) if ((roll -= ((Number) e.getValue()).doubleValue()) <= 0) { value = String.valueOf(e.getKey()); break; }
                }
            }
            if (value == null || "none".equals(value) || "random".equals(value)) continue;
            out.add((f.aspectFormat == null ? "{{choice}}" : f.aspectFormat).replace("{{choice}}", value));
        }
        return out;
    }

    /** Every aspect Cobblemon would see: features, shiny, and the gender. */
    java.util.Set<String> allAspects() {
        java.util.Set<String> all = new java.util.HashSet<>(aspects == null ? List.of() : aspects);
        if (shiny) all.add("shiny");
        if ("M".equals(gender)) all.add("male");
        if ("F".equals(gender)) all.add("female");
        return all;
    }

    /** The converted model (looks.py) this Pokémon is drawn with. */
    String model(Dex dex) { return dex.model(species, allAspects()); }

    int maxHp(Dex dex) { return Rules.maxHp(dex.of(this).base(0), ivs[0], ev(0), level); }

    int ev(int stat) { return evs == null ? 0 : evs[stat]; }

    int friendship(Dex dex) { return friendship < 0 ? dex.of(this).baseFriendship : friendship; }

    /** Cobblemon's incrementFriendship / decrementFriendship: kept within 0-255. */
    void addFriendship(Dex dex, int amount) { friendship = Math.clamp(friendship(dex) + amount, 0, 255); }

    int hp(Dex dex) {
        int max = maxHp(dex);
        return hp < 0 || hp > max ? max : hp;
    }

    boolean fainted(Dex dex) { return hp(dex) == 0; }

    /** A full heal (Healing Machine, bed): HP, status and PP. */
    void heal() {
        hp = -1;
        status = "";
        pp = null;
        faintSecs = -1;
    }

    /**
     * Cobblemon's PlayerPartyStore.onSecondPassed, out of battle: a fainted Pokémon wakes with 20% HP after 300 s;
     * otherwise 5% of max HP (at least 1) every 60 s; a status wears off after its 180-300 s. Returns a message or null.
     */
    String second(Dex dex) {
        if (fainted(dex)) {
            if (faintSecs < 0) faintSecs = 300;
            if (--faintSecs > -1) return null;
            hp = (int) Math.ceil(maxHp(dex) * 0.2);
            return name(dex) + " has recovered from fainting.";
        }
        faintSecs = -1;
        if (hp >= 0 && --healSecs <= -1) {
            healSecs = 60;
            hp = Math.min(maxHp(dex), hp + (int) Math.round(Math.max(1.0, maxHp(dex) * 0.05)));
            if (hp >= maxHp(dex)) hp = -1;
        }
        if (status != null && !status.isEmpty() && --statusSecs <= 0) status = "";
        return null;
    }

    String nickname; // Cobblemon's nickname (SummaryScreen), null for the species' name

    String name(Dex dex) { return nickname != null && !nickname.isBlank() ? nickname : dex.name(species); }

    /** Showdown PokemonSet plus the fields Cobblemon's Showdown reads (sim/server.js packs it). */
    Map<String, Object> set(Dex dex) {
        Map<String, Object> ivMap = new LinkedHashMap<>(), evMap = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            ivMap.put(Dex.STATS[i], ivs[i]);
            evMap.put(Dex.STATS[i], ev(i));
        }
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("name", name(dex));
        Dex.Form form = dex.form(species, allAspects());
        set.put("species", form == null ? species : form.showdown); // raichualola
        set.put("baseSpecies", species); // sim/server.js falls back to it for forms Showdown doesn't have
        set.put("shiny", shiny);
        set.put("level", level);
        set.put("moves", moves);
        set.put("ability", ability);
        set.put("nature", mint != null ? mint : nature);
        if (held != null) set.put("item", held);
        set.put("gender", gender);
        set.put("ivs", ivMap);
        set.put("evs", evMap);
        set.put("uuid", id);
        if (hp >= 0 && hp < maxHp(dex)) set.put("hp", hp);
        set.put("status", status == null ? "" : status);
        if (pp != null) set.put("pp", pp);
        set.put("happiness", friendship(dex));
        set.put("ball", ball == null ? "poke_ball" : ball);
        return set;
    }

    /**
     * Adds experience; levels up, learns level-up moves and evolves. Messages go to {@code say}. A 5th move goes to
     * learnable, which MoveLearn offers after the battle.
     */
    void gainExp(Dex dex, long amount, Consumer<String> say) {
        exp += amount;
        say.accept(name(dex) + " gained " + amount + " EXP.");
        Dex.Species s = dex.of(this);
        while (level < 100 && exp >= Rules.expFor(s.expGroup, level + 1)) {
            int oldMax = maxHp(dex);
            level++;
            if (hp >= 0) hp += maxHp(dex) - oldMax;
            addFriendship(dex, Rules.levelUpFriendship(friendship(dex)));
            say.accept(name(dex) + " grew to level " + level + "!");
            for (List<String> lm : s.moves) {
                if (Integer.parseInt(lm.get(0)) != level || moves.contains(lm.get(1))) continue;
                if (moves.size() < 4) {
                    moves.add(lm.get(1));
                    say.accept(name(dex) + " learned " + dex.label("move", lm.get(1)) + "!");
                } else {
                    say.accept(name(dex) + " wants to learn " + dex.label("move", lm.get(1)) + ", but already knows 4 moves.");
                    if (learnable == null) learnable = new ArrayList<>();
                    if (!learnable.contains(lm.get(1))) learnable.add(lm.get(1));
                }
            }
        }
        if (!"everstone".equals(held)) for (List<String> evo : s.evolutions) { // as Cobblemon: ready to evolve, evolved from the Summary
            String into = evo.get(1);
            if (level >= Integer.parseInt(evo.get(0)) && dex.get(into) != null) {
                if (!into.equals(evolution)) say.accept(name(dex) + " seems ready to evolve!");
                evolution = into;
                break;
            }
        }
    }

    String evolution; // the species it's ready to evolve into (Cobblemon's pending evolution), or null

    /** Evolves into the pending evolution; returns Cobblemon's message, or null if there's none. */
    String evolve(Dex dex) {
        if (evolution == null || dex.get(evolution.split(" ")[0]) == null) return null;
        String before = name(dex);
        int oldMax = maxHp(dex);
        boolean named = nickname != null && !nickname.isBlank();
        String[] result = evolution.split(" "); // "raichu alolan": a species and Cobblemon properties for the new form
        species = result[0]; // aspects stay: an Alolan form evolves into the Alolan form
        if (result.length > 1) aspects = featureAspects(dex, species, List.of(result).subList(1, result.length));
        evolution = null;
        if (hp >= 0) hp += maxHp(dex) - oldMax;
        return before + " evolved into " + (named ? dex.name(species) : name(dex)) + "!";
    }
}
