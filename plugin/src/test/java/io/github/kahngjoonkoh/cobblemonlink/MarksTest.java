package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MarksTest {
    static Dex.Mark mark(double chance, String group) {
        Dex.Mark m = new Dex.Mark();
        m.chance = chance;
        m.group = group;
        return m;
    }

    /** Only the time of day's and the weather's marks can hit, in the main games' order; fishing only when fished. */
    @Test
    void timeWeatherFishing() {
        Map<String, Dex.Mark> sure = Map.of("mark_time_dawn", mark(1, "time"), "mark_time_lunchtime", mark(1, "time"),
                "mark_weather_rainy", mark(1, "weather"), "mark_fishing", mark(1, null));
        Random r = new Random(1);
        assertEquals("mark_time_lunchtime", Rules.mark(sure, r, 6000, "rainy", false, false));
        assertEquals("mark_weather_rainy", Rules.mark(sure, r, 3000, "rainy", false, false)); // 9:00: no time mark
        assertEquals("mark_fishing", Rules.mark(sure, r, 3000, null, true, false));
        assertNull(Rules.mark(sure, r, 3000, "snowy", false, false));
        assertNull(Rules.mark(Map.of("mark_rare", mark(0, "rare")), r, 0, null, false, false));
    }
}
