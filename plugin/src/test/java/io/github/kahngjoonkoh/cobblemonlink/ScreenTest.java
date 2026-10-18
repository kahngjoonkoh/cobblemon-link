package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenTest {
    @Test
    void tileHitBoxCountsUpFromTheBottom() {
        var t = new Screen.Tile(12, 85, 90, 26, 'a', 'b', o -> {}, () -> {}); // Cobblemon's Fight tile
        assertTrue(t.contains(57, 72));
        assertFalse(t.contains(57, 50)); // below it
        assertFalse(t.contains(5, 72));  // left of it
    }
}
