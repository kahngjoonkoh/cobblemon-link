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
    }

    /** Cobblemon's CobblemonCaptureCalculator, against its own numbers. */
    @Test
    void capture() {
        var rnd = new java.util.Random(7);
        // full-HP Pikachu (190) at L20 in battle: (3-2)/3 * 190 = 63.3 -> each check passes 77%
        int caught = 0;
        for (int i = 0; i < 20000; i++) if (Rules.capture(190, 35, 35, "", 20, true, 1, 0, 0, rnd).caught()) caught++;
        assertEquals(Math.pow(Math.round(65536 / Math.pow(255 / (190 / 3.0), 0.1875)) / 65537.0, 4), caught / 20000.0, 0.02);
        // outside battle x0.5, so harder
        int wild = 0;
        for (int i = 0; i < 20000; i++) if (Rules.capture(190, 35, 35, "", 20, false, 1, 0, 0, rnd).caught()) wild++;
        assertTrue(wild < caught - 1000);
        // 1 HP, asleep, low level: certain
        assertTrue(Rules.capture(190, 1, 35, "slp", 5, true, 1, 0, 0, rnd).caught());
        assertEquals(4, Rules.capture(190, 1, 35, "slp", 5, true, 1, 0, 0, rnd).shakes());
        // a legendary-tier rate at full HP stays hard, and fails short of four checks
        var hard = Rules.capture(3, 100, 100, "", 50, true, 1, 0, 0, new java.util.Random(1));
        assertFalse(hard.caught());
        // critical captures need 31+ caught, and are one check
        boolean sawCritical = false;
        for (int i = 0; i < 2000 && !sawCritical; i++) {
            var c = Rules.capture(255, 1, 35, "slp", 30, true, 1, 0, 700, rnd);
            if (c.critical()) { sawCritical = true; assertEquals(1, c.shakes()); }
        }
        assertTrue(sawCritical);
        for (int i = 0; i < 2000; i++) assertFalse(Rules.capture(255, 1, 35, "slp", 30, true, 1, 0, 30, rnd).critical());
    }

    @Test
    void statsAndExp() {
        assertEquals(34, Rules.maxHp(35, 31, 0, 12)); // Showdown reports Pikachu L12, 31 IVs, as 34 HP
        assertEquals(41, Rules.maxHp(35, 31, 252, 12)); // (70+31+63)*12/100 + 22
        // Rattata L5 beaten by a L12: (51*5/5*(20/27)^2.5 + 1) x Cobblemon's 2 = 50.2
        assertEquals(50, Rules.expGain(51, 5, 12, 1, 1));
        assertEquals(75, Rules.expGain(51, 5, 12, 1, 1.5)); // traded
        int[] evs = new int[6];
        assertEquals(252, Rules.addEv(evs, 5, 300));
        assertEquals(252, Rules.addEv(evs, 1, 255));
        assertEquals(6, Rules.addEv(evs, 2, 10)); // 510 total
        assertEquals(3, Rules.levelUpFriendship(70));
        assertEquals(0, Rules.levelUpFriendship(200));
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
