package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RulesTest {
    @Test
    void expCurvesMatchPublishedTables() {
        // level 100 totals from the main-series tables
        assertEquals(800_000, Rules.expFor("fast", 100));
        assertEquals(1_000_000, Rules.expFor("medium_fast", 100));
        assertEquals(1_059_860, Rules.expFor("medium_slow", 100));
        assertEquals(1_250_000, Rules.expFor("slow", 100));
        assertEquals(600_000, Rules.expFor("erratic", 100));
        assertEquals(1_640_000, Rules.expFor("fluctuating", 100));
        assertEquals(0, Rules.expFor("medium_slow", 1)); // formula goes negative at 1
        assertEquals(135, Rules.expFor("medium_slow", 5)); // a level 5 starter
        var rnd = new java.util.Random(7);
        assertEquals(4, Rules.shakes(1.0, rnd));
        assertEquals(0, Rules.shakes(0.0, rnd));
        int caught = 0;
        for (int i = 0; i < 20000; i++) if (Rules.shakes(0.3, rnd) == 4) caught++;
        assertEquals(0.3, caught / 20000.0, 0.02, "four checks keep the single roll's odds");
    }

    @Test
    void catchChance() {
        // full HP Pikachu (rate 190): a = 190/3, about 25%
        assertEquals(190 / 3.0 / 255, Rules.catchChance(190, 35, 35, ""), 1e-9);
        // 1 HP and asleep is far better, capped at 100%
        assertEquals(1.0, Rules.catchChance(190, 1, 35, "slp"));
        assertTrue(Rules.catchChance(3, 1, 100, "") < 0.02); // legendary-tier rate stays hard
    }

    @Test
    void statsAndExp() {
        assertEquals(34, Rules.maxHp(35, 31, 12)); // Showdown reports Pikachu L12, 31 IVs, as 34 HP
        assertEquals(25, Rules.expGain(51, 5, 12)); // Rattata L5 beaten by a L12: 51*5/5*(20/27)^2.5 + 1
    }

    /** Cobblemon's PlayerLevelRangeInfluence, case by case. */
    @Test
    void spawnLevelsFollowTheParty() {
        assertArrayEquals(new int[] {15, 25}, Rules.levelRange(20, 1, 50));   // party 20: 15..25
        assertArrayEquals(new int[] {1, 10}, Rules.levelRange(3, 1, 50));     // low party: still up to 10
        assertArrayEquals(new int[] {1, 10}, Rules.levelRange(0, 1, 50));     // no party: 1..10
        assertArrayEquals(new int[] {30, 35}, Rules.levelRange(5, 30, 50));   // all above: bottom quarter
        assertArrayEquals(new int[] {16, 20}, Rules.levelRange(60, 5, 20));   // all below: top quarter (5 + 15 * 3/4)
    }
}
