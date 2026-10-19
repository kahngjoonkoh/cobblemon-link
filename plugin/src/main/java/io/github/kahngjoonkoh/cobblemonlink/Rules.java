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

    /** Gen 5+ experience for defeating a wild Pokémon (no trainer/item bonuses). */
    static long expGain(int baseExp, int foeLevel, int ownLevel) {
        double scale = Math.pow((2.0 * foeLevel + 10) / (foeLevel + ownLevel + 10), 2.5);
        return (long) Math.floor(baseExp * foeLevel / 5.0 * scale) + 1;
    }

    /**
     * Chance one Poké Ball catches: the gen 3-4 modified catch rate, as a single roll.
     * ponytail: single roll instead of four shake checks; same expected odds within a few percent.
     */
    static double catchChance(int catchRate, int hp, int maxHp, String status) {
        double bonus = switch (status) {
            case "slp", "frz" -> 2.5;
            case "par", "brn", "psn", "tox" -> 1.5;
            default -> 1.0;
        };
        double a = (3.0 * maxHp - 2.0 * hp) * catchRate * bonus / (3.0 * maxHp);
        return Math.min(1.0, a / 255.0);
    }

    /**
     * The main games' four shake checks for a catch of chance `chance`, each passed with chance^(1/4) (so all four,
     * a catch, keep the same odds): how many pass before the first fail, 4 = caught. The ball shakes that many times.
     */
    static int shakes(double chance, java.util.random.RandomGenerator rnd) {
        double each = Math.pow(chance, 0.25);
        int n = 0;
        while (n < 4 && rnd.nextDouble() < each) n++;
        return n;
    }

    /** HP stat (no EVs, which this plugin doesn't award yet). */
    static int maxHp(int base, int iv, int level) {
        return (2 * base + iv) * level / 100 + level + 10;
    }

    /** Non-HP stat, before nature. */
    static int stat(int base, int iv, int level) {
        return (2 * base + iv) * level / 100 + 5;
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
}
