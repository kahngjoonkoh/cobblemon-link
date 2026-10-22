package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BattleTilesTest {
    @Test
    void payloadLayout() {
        assertEquals(107, java.util.Arrays.stream(Battle.TILE_FIELDS).sum()); // build_bedrock_ui.py BT_SLOT
        String[][] t = new String[6][];
        t[3] = new String[]{"a", "items/portrait/rattata", "Rattata", "L3", "m", "b48", "100%", "---"};
        String p = Battle.tilePayload(t);
        assertEquals(3 + 6 * 107, p.length());
        assertEquals("a", p.substring(3 + 3 * 107, 3 + 3 * 107 + 1)); // bt_field(3, "st")
        assertTrue(p.startsWith("|CB~"));
    }
}
