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

    static Mon create(Dex dex, String species, int level) {
        var r = ThreadLocalRandom.current();
        Dex.Species s = dex.get(species);
        Mon m = new Mon();
        m.species = species;
        m.level = Math.max(1, Math.min(100, level));
        m.exp = Rules.expFor(s.expGroup, m.level);
        m.nature = NATURES[r.nextInt(NATURES.length)];
        m.ability = s.abilities.get(r.nextInt(s.abilities.size()));
        m.gender = s.maleRatio < 0 ? "" : r.nextDouble() < s.maleRatio ? "M" : "F";
        for (int i = 0; i < 6; i++) m.ivs[i] = r.nextInt(32);
        // the four most recent level-up moves, like a wild Pokémon in the main games
        for (List<String> lm : s.moves) {
            if (Integer.parseInt(lm.get(0)) > m.level || m.moves.contains(lm.get(1))) continue;
            m.moves.add(lm.get(1));
            if (m.moves.size() > 4) m.moves.removeFirst();
        }
        return m;
    }

    int maxHp(Dex dex) { return Rules.maxHp(dex.get(species).base(0), ivs[0], level); }

    int hp(Dex dex) {
        int max = maxHp(dex);
        return hp < 0 || hp > max ? max : hp;
    }

    boolean fainted(Dex dex) { return hp(dex) == 0; }

    String name(Dex dex) { return dex.name(species); }

    /** Showdown PokemonSet. */
    Map<String, Object> set(Dex dex) {
        Map<String, Object> ivMap = new LinkedHashMap<>(), evMap = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            ivMap.put(Dex.STATS[i], ivs[i]);
            evMap.put(Dex.STATS[i], 0);
        }
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("name", name(dex));
        set.put("species", species);
        set.put("level", level);
        set.put("moves", moves);
        set.put("ability", ability);
        set.put("nature", nature);
        set.put("gender", gender);
        set.put("ivs", ivMap);
        set.put("evs", evMap);
        return set;
    }

    /**
     * Adds experience; levels up, learns level-up moves and evolves. Messages go to {@code say}.
     * ponytail: a 5th move is announced but not learned; a move-replace menu is the upgrade.
     */
    void gainExp(Dex dex, long amount, Consumer<String> say) {
        exp += amount;
        say.accept(name(dex) + " gained " + amount + " EXP.");
        Dex.Species s = dex.get(species);
        while (level < 100 && exp >= Rules.expFor(s.expGroup, level + 1)) {
            int oldMax = maxHp(dex);
            level++;
            if (hp >= 0) hp += maxHp(dex) - oldMax;
            say.accept(name(dex) + " grew to level " + level + "!");
            for (List<String> lm : s.moves) {
                if (Integer.parseInt(lm.get(0)) != level || moves.contains(lm.get(1))) continue;
                if (moves.size() < 4) {
                    moves.add(lm.get(1));
                    say.accept(name(dex) + " learned " + dex.label("move", lm.get(1)) + "!");
                } else {
                    say.accept(name(dex) + " wants to learn " + dex.label("move", lm.get(1)) + ", but already knows 4 moves.");
                }
            }
        }
        for (List<String> evo : s.evolutions) {
            String into = evo.get(1);
            if (level >= Integer.parseInt(evo.get(0)) && dex.get(into) != null) {
                String before = name(dex);
                int oldMax = maxHp(dex);
                species = into;
                if (hp >= 0) hp += maxHp(dex) - oldMax;
                say.accept("What? " + before + " evolved into " + name(dex) + "!");
                break;
            }
        }
    }
}
