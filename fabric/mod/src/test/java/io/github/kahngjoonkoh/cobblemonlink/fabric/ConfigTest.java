package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.*;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Config.Level;
import org.junit.jupiter.api.Test;

class ConfigTest {
    @Test
    void levels() {
        assertEquals(Level.STANDARD, Level.parse(" Standard "));
        assertEquals(Level.FULL, Level.parse("nonsense")); // a typo keeps the default
        assertEquals(Level.FULL, Level.parse(null));
        assertFalse(Level.BASIC.screenArt());
        assertTrue(Level.STANDARD.screenArt());
        assertFalse(Level.STANDARD.battleScreen());
        assertTrue(Level.FULL.battleScreen() && Level.FULL.moveAnimations());
        assertEquals(0, Level.BASIC.poseEvery());
        assertTrue(Level.FULL.poseEvery() < Level.STANDARD.poseEvery());
        assertTrue(Level.BASIC.atLeast(Level.BASIC) && !Level.OFF.atLeast(Level.BASIC));
    }
}
