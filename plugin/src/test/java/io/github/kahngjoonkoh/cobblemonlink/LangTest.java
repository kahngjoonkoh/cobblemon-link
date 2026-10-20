package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import org.junit.jupiter.api.Test;

class LangTest {
    @Test
    void lookupAndFallback() {
        Lang l = Lang.load(null); // only the plugin's own files
        l.add("en_us", new StringReader("{\"cobblemon.battle.used_move\": \"%1$s used %2$s!\", \"a.b\": \"%s and %s\"}"));
        l.add("ko_kr", new StringReader("{\"cobblemon.battle.used_move\": \"%1$s의 %2$s!\"}"));
        assertEquals("피카츄의 10만볼트!", l.get("ko_kr", "cobblemon.battle.used_move", "피카츄", "10만볼트"));
        assertEquals("x and y", l.get("ko_kr", "a.b", "x", "y"));                 // missing in ko_kr: en_us
        assertEquals("Pikachu used Tackle!", l.get("fr_fr", "cobblemon.battle.used_move", "Pikachu", "Tackle")); // no fr_fr at all
        assertEquals("no.such.key", l.get("en_us", "no.such.key"));
        assertEquals("Fallback", l.getOr("ko_kr", "no.such.key", "Fallback"));
        assertEquals("ko_kr", Lang.locale(java.util.Locale.of("ko", "KR")));
        assertTrue(l.has("en_us", "cobblemonlink.trade.cancelled")); // the plugin's own keys ship in the jar
    }

    /** The Java overlay lays text out by width: a Korean name must measure as unifont's full-width glyphs. */
    @Test
    void wideGlyphs() {
        assertEquals(24, Ui.width("피카츄"));
        assertEquals(25.5, Ui.uniWidth("피카츄", true), 1e-9);
    }

    /** Bedrock texture paths: the same shortening as tools/build_bedrock_ui.py's short(). */
    @Test
    void bedrockNames() {
        assertEquals("pikachu", Ui.bedrockName("pikachu"));
        assertEquals("magikarp__female__magikarp_jump_ac391226", Ui.bedrockName("magikarp__female__magikarp_jump_calico_orange_white_black"));
    }
}
