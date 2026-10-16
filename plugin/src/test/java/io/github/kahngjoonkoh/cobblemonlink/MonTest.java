package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonTest {
    static Dex.Species species(String name, List<List<String>> moves, List<List<String>> evos) {
        Dex.Species s = new Dex.Species();
        s.name = name;
        s.types = List.of("fire");
        s.baseStats = Map.of("hp", 39, "atk", 52, "def", 43, "spa", 60, "spd", 50, "spe", 65);
        s.abilities = List.of("blaze");
        s.catchRate = 45;
        s.baseExp = 62;
        s.expGroup = "medium_slow";
        s.maleRatio = 0.875;
        s.moves = moves;
        s.evolutions = evos;
        return s;
    }

    static Dex dex() {
        Dex d = new Dex();
        d.species = Map.of(
                "charmander", species("Charmander",
                        List.of(List.of("1", "scratch"), List.of("1", "growl"), List.of("4", "ember"),
                                List.of("8", "smokescreen"), List.of("12", "dragonbreath"), List.of("16", "firefang")),
                        List.of(List.of("16", "charmeleon"))),
                "charmeleon", species("Charmeleon", List.of(), List.of()));
        return d;
    }

    @Test
    void wildMovesAreTheLatestFour() {
        Mon m = Mon.create(dex(), "charmander", 12);
        assertEquals(List.of("growl", "ember", "smokescreen", "dragonbreath"), m.moves);
        assertEquals(Rules.expFor("medium_slow", 12), m.exp);
    }

    @Test
    void levelUpLearnsAndEvolves() {
        Dex dex = dex();
        Mon m = Mon.create(dex, "charmander", 15);
        m.hp = 10; // damaged: gains the HP difference on level-up instead of healing
        int before = m.maxHp(dex);
        List<String> said = new ArrayList<>();
        m.gainExp(dex, Rules.expFor("medium_slow", 16) - m.exp, said::add);
        assertEquals(16, m.level);
        assertEquals("charmeleon", m.species);
        assertEquals(4, m.moves.size()); // already knew 4: fire fang announced, not learned
        assertTrue(said.stream().anyMatch(s -> s.contains("wants to learn firefang")));
        assertTrue(said.stream().anyMatch(s -> s.contains("evolved into Charmeleon")));
        assertEquals(10 + (Rules.maxHp(39, m.ivs[0], 16) - before), m.hp);
    }
}
