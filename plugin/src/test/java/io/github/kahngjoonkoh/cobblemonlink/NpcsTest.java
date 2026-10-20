package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NpcsTest {
    /** Cobblemon's pool party: min..max Pokémon, no entry more than its selectableTimes, levels in its range. */
    @Test
    void poolParty() {
        Npcs.Entry e = new Npcs.Entry();
        e.species = "charmander";
        e.times = 2;
        e.levels = List.of(3, 6);
        Npcs.Npc n = new Npcs.Npc();
        n.min = 4;
        n.max = 6;
        n.pool = List.of(e);
        for (int seed = 0; seed < 20; seed++) {
            List<Mon> party = Npcs.party(MonTest.dex(), n, new Random(seed));
            assertEquals(2, party.size()); // the pool runs out first
            assertTrue(party.stream().allMatch(m -> m.level >= 3 && m.level <= 6));
        }
    }
}
