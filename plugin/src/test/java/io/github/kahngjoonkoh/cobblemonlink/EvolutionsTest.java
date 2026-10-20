package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvolutionsTest {
    /** Cobblemon's TimeRanges, at their edges. */
    @Test
    void timeRanges() {
        assertTrue(Evolutions.inTime("day", 0));
        assertTrue(Evolutions.inTime("day", 23460));
        assertFalse(Evolutions.inTime("day", 12542));
        assertTrue(Evolutions.inTime("night", 18000));
        assertTrue(Evolutions.inTime("dusk", 12000));
        assertFalse(Evolutions.inTime("noon", 7000));
        assertFalse(Evolutions.inTime("unknown", 1000));
    }

    @Test
    void properties() {
        Mon m = new Mon();
        m.gender = "F";
        m.aspects = List.of("alolan");
        assertTrue(Evolutions.properties("gender=female", m));
        assertFalse(Evolutions.properties("gender=male", m));
        assertTrue(Evolutions.properties("alolan", m));
        assertFalse(Evolutions.properties("galarian", m));
    }
}
