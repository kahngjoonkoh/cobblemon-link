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
        assertEquals("charmander", m.species); // as Cobblemon: ready to evolve, evolved from the Summary
        assertEquals("charmeleon", m.evolution);
        assertEquals(4, m.moves.size()); // already knew 4: fire fang waits in learnable
        assertEquals(List.of("firefang"), m.learnable);
        assertTrue(said.stream().anyMatch(s -> s.contains("wants to learn firefang")));
        assertTrue(said.stream().anyMatch(s -> s.contains("seems ready to evolve")));
        assertTrue(m.evolve(dex).contains("evolved into Charmeleon"));
        assertEquals("charmeleon", m.species);
        assertNull(m.evolution);
        assertEquals(10 + (Rules.maxHp(39, m.ivs[0], 0, 16) - before), m.hp);
    }

    /** Cobblemon's benched moves: a forgotten move can be swapped back in, the one it replaces goes to the bench. */
    @Test
    void benchedMoves() {
        Dex dex = dex();
        Mon m = Mon.create(dex, "charmander", 12); // growl, ember, smokescreen, dragonbreath; scratch dropped off
        m.bench("vinewhip");
        m.bench("ember"); // known: not benched
        assertEquals(List.of("vinewhip"), m.benched);
        assertEquals(List.of("vinewhip", "scratch"), m.relearnable(dex)); // benched, then level-up moves up to 12
        m.relearn(dex, "vinewhip", 0);
        assertEquals(List.of("vinewhip", "ember", "smokescreen", "dragonbreath"), m.moves);
        assertEquals(List.of("growl"), m.benched);
        m.relearn(dex, "firefang", 1); // level 16: not yet
        assertEquals("ember", m.moves.get(1));
    }

    /** Cobblemon's sizes: 0.9 at level 1 growing to 1 at level 9, the intrinsic 0.95-1.05, alphas by their size. */
    @Test
    void sizes() {
        Dex dex = dex();
        Mon m = Mon.create(dex, "charmander", 1);
        assertTrue(m.scale >= 0.95f && m.scale <= 1.05f);
        m.scale = 1;
        assertEquals(0.9f, m.drawScale(dex), 1e-6);
        m.level = 9;
        assertEquals(1f, m.drawScale(dex), 1e-6);
        m.alpha = true;
        dex.of(m).size = 1;
        assertEquals(1.5f, m.drawScale(dex), 1e-6); // 1.1 + 0.8 x 0.5
    }

    /** Cobblemon's bait on a fished Pokémon: an Aguav-style nature bait and an IV bait. */
    @Test
    void bait() {
        Dex dex = dex();
        Mon m = Mon.create(dex, "charmander", 10);
        Dex.Bait nature = new Dex.Bait(), iv = new Dex.Bait();
        nature.type = "nature";
        nature.sub = "spd";
        iv.type = "iv";
        iv.sub = "atk";
        iv.value = 40;
        Rules.bait(dex, m, List.of(nature, iv), new java.util.Random(3));
        assertEquals(1, SummaryScreen.nature(m.nature, 4)); // raises Sp. Def
        assertEquals(31, m.ivs[1]);
    }
}
