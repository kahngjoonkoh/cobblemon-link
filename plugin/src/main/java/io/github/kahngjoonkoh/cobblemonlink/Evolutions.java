package io.github.kahngjoonkoh.cobblemonlink;

import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Cobblemon's evolutions beyond plain level-ups (Dex.Species.evos, as its species files write them): item_interact
 * (stones and other items, evolving at once), level_up with more requirements (friendship, time, biome, moves...),
 * and trade. A level_up one, as in Cobblemon, only becomes the pending evolution the player confirms in the Summary.
 */
final class Evolutions {
    private final CobblemonLink pl;

    Evolutions(CobblemonLink pl) { this.pl = pl; }

    /** The item_interact evolution this item starts for m, or null. */
    Dex.Evo byItem(Mon m, String item) {
        return best(m, null, "item_interact", item);
    }

    /** Uses an evolution item: Cobblemon evolves right away. Returns the message, or null if nothing happened. */
    String evolveWithItem(Mon m, String item) { return evolveWithItem(m, item, null); }

    String evolveWithItem(Mon m, String item, Player owner) {
        Dex.Evo e = byItem(m, item);
        if (e == null) return null;
        m.evolution = e.result;
        return m.evolve(pl.dex, owner == null ? Lang.FALLBACK : CobblemonLink.loc(owner));
    }

    /**
     * After EXP (a battle, a candy): a level_up evolution whose requirements all pass becomes the pending one, as
     * Cobblemon's "seems ready to evolve". Plain level ones are Mon.gainExp's; this adds friendship, time, biome...
     */
    void update(Mon m, Player owner, java.util.function.Consumer<String> say) {
        if (m.evolution != null) return;
        Dex.Evo e = best(m, owner, "level_up", null);
        if (e == null) return;
        m.evolution = e.result;
        String loc = CobblemonLink.loc(owner);
        say.accept(pl.lang.get(loc, "cobblemonlink.evolve.ready", m.name(pl.dex, loc)));
    }

    /** A trade: Cobblemon's trade evolutions happen as the Pokémon arrives. */
    String traded(Mon m, Player newOwner) {
        Dex.Evo e = best(m, newOwner, "trade", null);
        if (e == null) return null;
        m.evolution = e.result;
        return m.evolve(pl.dex, CobblemonLink.loc(newOwner));
    }

    /** Of the evolutions that pass, the most specific (most requirements): Pikachu in an Alolan biome is Alolan Raichu. */
    private Dex.Evo best(Mon m, Player p, String variant, String item) {
        Dex.Species s = pl.dex.of(m);
        if (s == null || s.evos == null || "everstone".equals(m.held)) return null; // Cobblemon: the Everstone stops it
        Dex.Evo best = null;
        for (Dex.Evo e : s.evos) {
            if (!e.variant.equals(variant) || item != null && !item.equals(e.item) || pl.dex.get(e.species()) == null) continue;
            if (e.requirements.stream().allMatch(r -> check(r, m, p)) && (best == null || e.requirements.size() > best.requirements.size()))
                best = e;
        }
        return best;
    }

    /** One of Cobblemon's evolution requirements. Unknown or untracked ones (defeat, use_move, structure...) fail. */
    boolean check(Map<String, Object> r, Mon m, Player p) {
        Dex dex = pl.dex;
        Location at = p != null ? p.getLocation() : null;
        World w = at != null ? at.getWorld() : null;
        return switch (String.valueOf(r.get("variant"))) {
            case "level" -> m.level >= num(r, "minLevel", 1) && m.level <= num(r, "maxLevel", 100);
            case "friendship" -> m.friendship(dex) >= num(r, "amount", 0);
            case "held_item" -> m.held != null && String.valueOf(r.get("itemCondition")).endsWith(":" + m.held);
            case "time_range" -> w != null && inTime(String.valueOf(r.get("range")), w.getTime() % 24000);
            case "biome" -> at != null && pl.spawner.inBiome(String.valueOf(r.get("biomeCondition")), at);
            case "has_move" -> m.moves.contains(String.valueOf(r.get("move")));
            case "properties" -> properties(String.valueOf(r.get("target")), m);
            case "party_member" -> p != null && pl.store.get(p.getUniqueId()).party.stream()
                    .anyMatch(x -> x != m && x.species.equals(String.valueOf(r.get("target")).split(" ")[0]))
                    == !Boolean.FALSE.equals(r.get("contains"));
            case "stat_compare" -> stat(m, String.valueOf(r.get("highStat"))) > stat(m, String.valueOf(r.get("lowStat")));
            case "stat_equal" -> stat(m, String.valueOf(r.get("statOne"))) == stat(m, String.valueOf(r.get("statTwo")));
            case "moon_phase" -> w != null && moonPhase(w).equals(String.valueOf(r.get("moonPhase")));
            case "weather" -> w != null && (r.get("isRaining") == null || w.hasStorm() == Boolean.TRUE.equals(r.get("isRaining")))
                    && (r.get("isThundering") == null || w.isThundering() == Boolean.TRUE.equals(r.get("isThundering")));
            case "negate" -> r.get("inner") instanceof Map<?, ?> in && !check(cast(in), m, p);
            default -> false; // ponytail: defeat, use_move, blocks_traveled, structure, advancement, property_range, has_move_type
        };
    }

    /** Cobblemon's TimeRange.timeRanges (ticks of the day). */
    static boolean inTime(String range, long t) {
        return switch (range) {
            case "day" -> t >= 23460 || t <= 12541;
            case "night" -> t >= 12542 && t <= 23459;
            case "morning" -> t >= 23000 || t <= 4999;
            case "noon" -> t >= 5000 && t <= 6999;
            case "afternoon" -> t >= 7000 && t <= 12999;
            case "evening" -> t >= 13000 && t <= 16999;
            case "midnight" -> t >= 17000 && t <= 18999;
            case "predawn" -> t >= 19000 && t <= 22999;
            case "dawn" -> t >= 22300 || t <= 166;
            case "dusk" -> t >= 11834 && t <= 13701;
            case "twilight" -> t >= 11834 && t <= 13701 || t >= 22300 || t <= 166;
            default -> false;
        };
    }

    private static final String[] PHASES = {"FULL_MOON", "WANING_GIBBOUS", "THIRD_QUARTER", "WANING_CRESCENT", "NEW_MOON",
            "WAXING_CRESCENT", "FIRST_QUARTER", "WAXING_GIBBOUS"};

    static String moonPhase(World w) { return PHASES[(int) (w.getFullTime() / 24000 % 8)]; }

    /** Cobblemon's PokemonProperties target, as these files use it: "gender=female", "form=...", or bare aspects. */
    static boolean properties(String target, Mon m) {
        for (String part : target.trim().split("\\s+")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq), v = eq < 0 ? "true" : part.substring(eq + 1);
            boolean ok = switch (k) {
                case "gender" -> v.equalsIgnoreCase("female") ? "F".equals(m.gender) : v.equalsIgnoreCase("male") ? "M".equals(m.gender)
                        : m.gender == null || m.gender.isEmpty();
                case "shiny" -> m.shiny == Boolean.parseBoolean(v);
                default -> m.allAspects().contains(eq < 0 ? k : v) || m.allAspects().contains(k + "-" + v) || m.allAspects().contains(v + "-" + k);
            };
            if (!ok) return false;
        }
        return true;
    }

    private int stat(Mon m, String name) {
        int k = switch (name) {
            case "hp" -> 0; case "attack" -> 1; case "defence" -> 2; case "special_attack" -> 3; case "special_defence" -> 4;
            default -> 5;
        };
        return k == 0 ? m.maxHp(pl.dex) : SummaryScreen.stats(pl.dex, m)[k];
    }

    private static int num(Map<String, Object> r, String k, int def) {
        return r.get(k) instanceof Number n ? n.intValue() : def;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) { return (Map<String, Object>) m; }
}
