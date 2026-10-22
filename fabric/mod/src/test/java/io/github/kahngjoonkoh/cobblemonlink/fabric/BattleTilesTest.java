package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BattleTilesTest {
    @Test
    void payloadLayout() {
        assertEquals(107, java.util.Arrays.stream(BattleTiles.FIELDS).sum()); // build_bedrock_ui.py BT_SLOT
        String[][] tiles = new String[6][];
        tiles[3] = new String[]{"a", "items/portrait/rattata", "Rattata", "L3", "m", "b48", "100%", "---"};
        String p = BattleTiles.payload(tiles);
        assertEquals(3 + 6 * 107, p.length());
        assertEquals("a", p.substring(3 + 3 * 107, 3 + 3 * 107 + 1)); // bt_field(3, "st")
        assertEquals("~", p.substring(3, 4)); // tile 0 unused
        // build_bedrock_ui.py short(): names over 40 characters
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa_4642fe68", PartyHud.bedrockName("a".repeat(45)));
        assertEquals("pikachu", PartyHud.bedrockName("pikachu"));
    }
}
