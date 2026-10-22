package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PartyPanelTest {
    @Test
    void bedrockSlotsAreFixedWidthAscii() {
        assertEquals(83, java.util.Arrays.stream(PartyPanel.FIELDS).sum(), "build_bedrock_ui.py HUD_SLOT less \"Z<i>\"");
        assertEquals("a?b??", PartyPanel.ascii("a~b|#"));
        assertEquals("Flabebe", PartyPanel.ascii("Flabébé"));
        assertEquals("NidoranF", PartyPanel.ascii("Nidoran♀"));
        assertEquals("a?b?", PartyPanel.ascii("a~b|"), "the pack's padding and marker stay out of names");
        assertEquals("Zubat z9?", PartyPanel.ascii("Zubat Z9$")); // a slot marker ("Z<digit>") and '$' stay out of names
    }
}
