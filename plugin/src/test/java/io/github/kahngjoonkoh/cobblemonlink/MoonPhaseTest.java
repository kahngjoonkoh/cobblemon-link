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
}
