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
}
