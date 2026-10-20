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

    @Test
    void ourTranslationsCoverEveryKey() throws Exception {
        for (String code : new String[]{"ko_kr"}) {
            var path = "/assets/cobblemonlink/lang/";
            var en = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(LangTest.class.getResourceAsStream(path + "en_us.json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().keySet();
            var other = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(LangTest.class.getResourceAsStream(path + code + ".json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().keySet();
            assertEquals(en, other, code);
        }
    }
}
