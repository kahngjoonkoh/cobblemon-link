package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PartyPanelTest {
    @Test
    void bedrockSlotsAreFixedWidthAscii() {
        assertEquals(119, java.util.Arrays.stream(PartyPanel.FIELDS).sum(), "build_bedrock_ui.py HUD_SLOT");
        assertEquals("Flabebe", PartyPanel.ascii("Flabébé"));
        assertEquals("NidoranF", PartyPanel.ascii("Nidoran♀"));
        assertEquals("a?b?", PartyPanel.ascii("a~b|"), "the pack's padding and marker stay out of names");
    }
}
