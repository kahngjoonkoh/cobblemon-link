package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MoonPhaseTest {
    /** Cobblemon's MoonPhaseRange as its spawn files write it. */
    @Test
    void moonPhases() {
        assertTrue(Rules.moonPhase("1-3", 2));
        assertFalse(Rules.moonPhase("1-3", 0));
        assertTrue(Rules.moonPhase(0, 0));
        assertTrue(Rules.moonPhase(List.of("0", "4-5"), 5));
        assertFalse(Rules.moonPhase(List.of("0", "4-5"), 6));
    }

    @Test
    void fossils() {
        var r = java.util.Map.of("aerodactyl", List.of("old_amber_fossil"), "dracozolt", List.of("fossilized_bird", "fossilized_drake"));
        assertEquals("aerodactyl", Rules.fossil(r, List.of("old_amber_fossil")));
        assertEquals("dracozolt", Rules.fossil(r, List.of("fossilized_drake", "fossilized_bird")));
        assertNull(Rules.fossil(r, List.of("fossilized_bird")));
    }
}
