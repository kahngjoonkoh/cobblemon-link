package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BattleTilesTest {
    @Test
    void payloadLayout() {
        assertEquals(72, java.util.Arrays.stream(BattleTiles.FIELDS).sum()); // build_bedrock_ui.py BT_SLOT less "Z<i>"
        String[][] tiles = new String[6][];
        tiles[3] = new String[]{"a", "rattata", "Rattata", "L3", "m", "b48", "100%", "---"};
        String p = BattleTiles.payload(tiles);
        assertEquals(3 + 6 * 74, p.length());
        assertEquals("Z3a", p.substring(3 + 3 * 74, 3 + 3 * 74 + 3)); // tile 3: its marker, then st (bt_field(3, "st") = (3, 2, 1))
        assertEquals("Z0~", p.substring(3, 6)); // tile 0 unused
        // build_bedrock_ui.py short(): names over 40 characters
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa_4642fe68", PartyHud.bedrockName("a".repeat(45)));
        assertEquals("pikachu", PartyHud.bedrockName("pikachu"));
    }
}
