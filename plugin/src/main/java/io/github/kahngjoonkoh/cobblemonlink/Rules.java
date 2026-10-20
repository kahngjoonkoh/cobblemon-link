package io.github.kahngjoonkoh.cobblemonlink;

/** Main-series formulas Cobblemon also follows. Pure functions, unit-tested. */
final class Rules {
    private Rules() {}

    /** Total experience needed to reach level n in the given Cobblemon experience group. */
    static long expFor(String group, int n) {
        long c = (long) n * n * n;
        long v = switch (group) {
            case "fast" -> 4 * c / 5;
            case "medium_slow" -> 6 * c / 5 - 15L * n * n + 100L * n - 140;
            case "slow" -> 5 * c / 4;
            case "erratic" -> n < 50 ? c * (100 - n) / 50 : n < 68 ? c * (150 - n) / 100
                    : n < 98 ? c * ((1911 - 10L * n) / 3) / 500 : c * (160 - n) / 100;
            case "fluctuating" -> n < 15 ? c * ((n + 1) / 3 + 24) / 50 : n < 36 ? c * (n + 14) / 50
                    : c * (n / 2 + 32) / 50;
            default -> c; // medium_fast
        };
        return Math.max(0, v);
    }

    static final double EXP_MULTIPLIER = 2; // Cobblemon's experienceMultiplier
    static final double LUCKY_EGG = 1.5, EXP_SHARE = 0.5; // luckyEggMultiplier, experienceShareMultiplier

    /**
     * Cobblemon's StandardExperienceCalculator: gen 5 scaled EXP times its multiplier. participation: 1 if it faced the
     * foe, EXP_SHARE for an Exp. Share holder that didn't; bonus: traded (1.5) x Lucky Egg x can evolve by level (1.2)
     * x friendship 220+ (1.2).
     */
    static int expGain(int baseExp, int foeLevel, int ownLevel, double participation, double bonus) {
        double scale = Math.pow((2.0 * foeLevel + 10) / (foeLevel + ownLevel + 10), 2.5);
        return (int) Math.round((baseExp * foeLevel / 5.0 * participation * scale + 1) * bonus * EXP_MULTIPLIER);
    }

    /** One capture: how many of the four checks passed (the ball shakes that many, at most 3), and the result. */
    record Capture(int shakes, boolean caught, boolean critical) {}

    /**
     * Cobblemon's CobblemonCaptureCalculator: gen 9 with gen 8's low-level bonus, x0.5 outside battle, critical captures
     * from the Pokédex caught count, and four shake checks (counted, not stopped at the first fail).
     * ballBonus: the ball's catch rate multiplier; foeTop: the highest level on the player's side in battle (0 = none).
     */
    static Capture capture(int catchRate, int hp, int maxHp, String status, int level, boolean inBattle, double ballBonus,
                           int foeTop, int caughtCount, java.util.random.RandomGenerator rnd) {
        double rate = (3.0 * maxHp - 2.0 * hp) * catchRate * (inBattle ? 1 : 0.5) * ballBonus / (3.0 * maxHp);
        rate *= switch (status) {
            case "slp", "frz" -> 2.5;
            case "par", "brn", "psn", "tox" -> 1.5;
            default -> 1.0;
        };
        rate *= level < 13 ? Math.max((36 - 2 * level) / 10, 1) : 1;
        if (foeTop > 0 && foeTop < level) rate *= Math.max(0.1, Math.min(1, 1 - (level - foeTop) / 50)); // Kotlin Int division, as Cobblemon
        double dex = caughtCount <= 30 ? 0 : caughtCount <= 150 ? 0.5 : caughtCount <= 300 ? 1 : caughtCount <= 450 ? 1.5
                : caughtCount <= 600 ? 2 : 2.5;
        boolean critical = rnd.nextInt(256) < Math.round(rate * dex / 6);
        long p = Math.round(65536 / Math.pow(255 / rate, 0.1875));
        int shakes = 0;
        for (int i = 0; i < 4; i++) {
            if (rnd.nextInt(65537) < p) shakes++;
            if (i == 0 && critical) return new Capture(1, shakes == 1, true);
        }
        return new Capture(shakes, shakes == 4, false);
    }

    /** HP stat. */
    static int maxHp(int base, int iv, int ev, int level) {
        return (2 * base + iv + ev / 4) * level / 100 + level + 10;
    }

    /** Non-HP stat, before nature. */
    static int stat(int base, int iv, int ev, int level) {
        return (2 * base + iv + ev / 4) * level / 100 + 5;
    }

    static final int EV_STAT_MAX = 252, EV_TOTAL_MAX = 510;

    /** Adds EVs the way Cobblemon's EVs.add does: each stat up to 252, all of them up to 510. Returns what was added. */
    static int addEv(int[] evs, int stat, int amount) {
        int total = 0;
        for (int e : evs) total += e;
        int add = Math.max(0, Math.min(amount, Math.min(EV_STAT_MAX - evs[stat], EV_TOTAL_MAX - total)));
        evs[stat] += add;
        return add;
    }

    /** Cobblemon's SWORD_AND_SHIELD_LEVEL_UP friendship gain. */
    static int levelUpFriendship(int friendship) {
        return friendship <= 99 ? 3 : friendship <= 199 ? 2 : 0;
    }

    /**
     * Cobblemon's PlayerLevelRangeInfluence: the party's highest level (`top`, 0 without a party) +-5, at least up to
     * 10, inside the spawn's [min, max]; no overlap -> the near quarter of the spawn's range.
     */
    static int[] levelRange(int top, int min, int max) {
        int lo = top == 0 ? 1 : Math.max(top - 5, 1), hi = top == 0 ? 10 : Math.min(100, Math.max(top + 5, 10));
        int a = Math.max(lo, min), b = Math.min(hi, max), width = max - min;
        if (a <= b) return new int[] {a, b};
        return min > hi ? new int[] {min, (int) (min + width / 4F)} : new int[] {(int) (min + 3 * width / 4F), max};
    }

    /** Cobblemon's MoonPhaseRange: "1-3", 4, or a list of those (0 is a full moon). */
    static boolean moonPhase(Object range, int phase) {
        if (range instanceof java.util.List<?> l) return l.stream().anyMatch(r -> moonPhase(r, phase));
        String r = range instanceof Number n ? String.valueOf(n.intValue()) : String.valueOf(range);
        int dash = r.indexOf('-');
        if (dash < 0) return Integer.parseInt(r.trim()) == phase;
        return phase >= Integer.parseInt(r.substring(0, dash).trim()) && phase <= Integer.parseInt(r.substring(dash + 1).trim());
    }


    /** The species a set of fossils revives into (Cobblemon's fossils: one, or a pair in any order), or null. */
    static String fossil(java.util.Map<String, java.util.List<String>> recipes, java.util.List<String> fossils) {
        for (var e : recipes.entrySet())
            if (e.getValue().size() == fossils.size() && new java.util.HashSet<>(e.getValue()).equals(new java.util.HashSet<>(fossils))) return e.getKey();
        return null;
    }
}
