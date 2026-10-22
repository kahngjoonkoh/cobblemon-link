package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PartyHudTest {
    @Test
    void payloadPieces() {
        assertEquals(83, java.util.Arrays.stream(PartyHud.FIELDS).sum()); // build_bedrock_ui.py HUD_SLOT less "Z<i>"
        assertEquals("a?b??", PartyHud.ascii("a~b|#"));
        assertEquals("Flabebe", PartyHud.ascii("Flabébé"));
        assertEquals("NidoranF", PartyHud.ascii("Nidoran♀"));
        assertEquals("a?b?", PartyHud.ascii("a~b|"));
        assertEquals("Zubat z9?", PartyHud.ascii("Zubat Z9$")); // a slot marker ("Z<digit>") and '$' stay out of names
        assertArrayEquals(new double[]{0, 1}, PartyHud.fade(10, 3, 40, 3));
    }
}
