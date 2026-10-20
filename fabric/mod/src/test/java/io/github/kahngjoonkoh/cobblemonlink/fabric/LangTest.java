package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LangTest {
    @Test
    void playersLanguageThenEnglishThenKey() {
        assertEquals("de_de", Lang.code("de_DE"));
        assertEquals("en_us", Lang.code((String) null));
        assertEquals("Zurück (test)", Lang.lookup("xx_test", "cobblemonlink.back")); // the player's language
        assertEquals("Back", Lang.lookup("de_de", "cobblemonlink.back")); // no such file / key: en_us
        assertEquals("no.such.key", Lang.lookup("de_de", "no.such.key"));
    }
}
