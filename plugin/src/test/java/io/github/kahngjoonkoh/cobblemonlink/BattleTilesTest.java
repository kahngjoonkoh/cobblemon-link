package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BattleTilesTest {
    @Test
    void payloadLayout() {
        assertEquals(72, java.util.Arrays.stream(Battle.TILE_FIELDS).sum()); // build_bedrock_ui.py BT_SLOT less "Z<i>"
        String[][] t = new String[6][];
        t[3] = new String[]{"a", "rattata", "Rattata", "L3", "m", "b48", "100%", "---"};
        String p = Battle.tilePayload(t);
        assertEquals(3 + 6 * 74, p.length());
        assertEquals("Z3a", p.substring(3 + 3 * 74, 3 + 3 * 74 + 3)); // tile 3: its marker, then st (bt_field(3, "st") = (3, 2, 1))
        assertTrue(p.startsWith("|CBZ0~"));
    }
}
