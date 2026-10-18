package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PcScreenTest {
    private static Mon mon(String species) {
        Mon m = new Mon();
        m.species = species;
        return m;
    }

    @Test
    void movesSwapsAndKeepsAPartyPokemon() {
        Store.Data d = Store.fill(new Store.Data(), 2);
        Mon a = mon("a"), b = mon("b"), c = mon("c");
        d.party.add(a);
        d.boxes.get(0).slots[0] = b;
        assertNotNull(PcScreen.move(d, a, new PcScreen.Loc(0, 5)), "the last party Pokémon stays");
        assertNull(PcScreen.move(d, b, new PcScreen.Loc(1, 3)), "box to another box");
        assertSame(b, d.boxes.get(1).slots[3]);
        assertNull(d.boxes.get(0).slots[0]);
        assertNull(PcScreen.move(d, b, new PcScreen.Loc(-1, 4)), "into the party's next free cell");
        assertEquals(List.of(a, b), d.party);
        d.boxes.get(0).slots[7] = c;
        assertNull(PcScreen.move(d, a, new PcScreen.Loc(0, 7)), "a swap keeps the party's size");
        assertEquals(List.of(c, b), d.party);
        assertSame(a, d.boxes.get(0).slots[7]);
    }

    @Test
    void oldPcListMovesIntoBoxes() {
        Store.Data d = new Store.Data();
        d.pc = new ArrayList<>();
        for (int i = 0; i < 31; i++) d.pc.add(mon("m" + i));
        Store.fill(d, 40);
        assertNull(d.pc);
        assertEquals(40, d.boxes.size());
        assertEquals(31, d.pcCount());
        assertEquals("m30", d.boxes.get(1).slots[0].species);
    }

    @Test
    void fontsAreAsciiWithGenderSigns() {
        assertEquals("Pokemon Flabebe ♀", PcScreen.plain("Pokémon Flabébé ♀").replace("é", "e"));
        assertEquals("Pokémon", PcScreen.plain("Pokémon"));
    }
}
