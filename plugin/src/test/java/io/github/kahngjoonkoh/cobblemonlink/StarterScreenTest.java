package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class StarterScreenTest {
    @Test
    void regionsInCobblemonsOrder() {
        var all = StarterScreen.regions(CobblemonLink.STARTERS, "eevee");
        assertEquals(10, all.size());
        assertEquals(new StarterScreen.Region("Kanto", List.of("bulbasaur", "charmander", "squirtle")), all.getFirst());
        assertEquals(new StarterScreen.Region("Random", List.of("eevee")), all.getLast());
        // another plugin's smaller choice keeps its regions; anything else goes after them
        assertEquals(List.of(new StarterScreen.Region("Johto", List.of("cyndaquil")), new StarterScreen.Region("Other", List.of("pikachu"))),
                StarterScreen.regions(List.of("pikachu", "cyndaquil"), null));
    }
}
