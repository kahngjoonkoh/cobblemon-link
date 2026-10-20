package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PartyHudTest {
    @Test
    void payloadPieces() {
        assertEquals(119, java.util.Arrays.stream(PartyHud.FIELDS).sum()); // build_bedrock_ui.py HUD_SLOT
        assertEquals("Flabebe", PartyHud.ascii("Flabébé"));
        assertEquals("NidoranF", PartyHud.ascii("Nidoran♀"));
        assertEquals("a?b?", PartyHud.ascii("a~b|"));
        assertArrayEquals(new double[]{0, 1}, PartyHud.fade(10, 3, 40, 3));
    }
}
