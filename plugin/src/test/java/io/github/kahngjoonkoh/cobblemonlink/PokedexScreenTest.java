package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PokedexScreenTest {
    private static Dex.Species species(String name, int no) {
        Dex.Species s = new Dex.Species();
        s.name = name;
        s.dex = no;
        return s;
    }

    @Test
    void cellsRegionsFilterSearchAndForms() {
        assertEquals(27, PokedexScreen.raw(0, PokedexScreen.tileRow(0)), "the list starts on chest row 3");
        assertEquals(45, PokedexScreen.raw(0, PokedexScreen.tileRow(18)), "then the inventory rows");
        assertEquals(80, PokedexScreen.raw(8, 8), "the hotbar's last cell");
        assertEquals("Kanto", PokedexScreen.region(151));
        assertEquals("Johto", PokedexScreen.region(152));

        Dex dex = new Dex();
        Dex.Species rattata = species("Rattata", 19);
        Dex.Form alola = new Dex.Form();
        alola.name = "Alola";
        alola.aspects = List.of("alolan");
        rattata.forms = List.of(alola);
        dex.species = Map.of("bulbasaur", species("Bulbasaur", 1), "rattata", rattata, "pikachu", species("Pikachu", 25));
        Store.Data d = Store.fill(new Store.Data(), 1);
        d.seen.addAll(List.of("bulbasaur", "rattata"));
        d.caught.add("bulbasaur");
        assertEquals(List.of("bulbasaur", "rattata", "pikachu"), PokedexScreen.list(dex, d, 0, ""));
        assertEquals(List.of("bulbasaur"), PokedexScreen.list(dex, d, 2, ""), "caught only");
        assertEquals(List.of("rattata"), PokedexScreen.list(dex, d, 0, "RAT"), "seen names");
        assertEquals(List.of(), PokedexScreen.list(dex, d, 0, "pika"), "an unseen name stays hidden");
        assertEquals(List.of("pikachu"), PokedexScreen.list(dex, d, 0, "025"), "a number finds anything");

        assertEquals(1, PokedexScreen.forms(dex, d, "rattata").size(), "only the base until an Alolan one is seen");
        d.looks.add("rattata:alolan,male");
        assertSame(alola, PokedexScreen.forms(dex, d, "rattata").get(1));
    }

    @Test
    void aBodyEndingInRKeepsItsNumber() throws Exception {
        java.nio.file.Path zip = java.nio.file.Files.createTempFile("ui", ".zip");
        try (var out = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(zip))) {
            for (String n : List.of("body/unown__character_r.png", "body/venusaur.png", "portrait/a.png", "portrait/a_r.png", "portrait/b.png")) {
                out.putNextEntry(new java.util.zip.ZipEntry(n));
                out.closeEntry();
            }
        }
        try (var z = new java.util.zip.ZipFile(zip.toFile())) {
            assertEquals(1, Ui.index(z, "body/", false).get("venusaur"));
            assertEquals(1, Ui.index(z, "portrait/", true).get("b"), "a portrait's mirror isn't counted");
        }
        java.nio.file.Files.delete(zip);
    }
}
