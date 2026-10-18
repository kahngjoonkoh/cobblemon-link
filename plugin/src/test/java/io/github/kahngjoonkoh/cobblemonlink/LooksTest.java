package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LooksTest {
    /** gamedata.json shape (build_data.py): a species with an Alolan form, a sea feature, shiny and female looks. */
    static final String JSON = """
            {"species": {"raichu": {"name": "Raichu", "types": ["electric"], "baseStats": {"hp": 60}, "abilities": ["static"],
              "catchRate": 75, "baseExp": 218, "expGroup": "medium_fast", "maleRatio": 0.5, "moves": [], "evolutions": [],
              "features": ["alolan", "sea"],
              "forms": [{"name": "Alola", "aspects": ["alolan"], "showdown": "raichualola", "types": ["electric", "psychic"]}],
              "looks": {"variations": [[[], "raichu_male.geo", 0], [["shiny"], null, 1], [["female"], "raichu_female.geo", null],
                                       [["alolan"], "raichu_alolan.geo", 2], [["shiny", "alolan"], null, 3]],
                        "models": {"raichu_male.geo|0": "raichu", "raichu_male.geo|1": "raichu__shiny",
                                   "raichu_female.geo|0": "raichu__female", "raichu_alolan.geo|2": "raichu__alolan",
                                   "raichu_alolan.geo|3": "raichu__alolan__shiny"}}}},
             "features": {"alolan": {"type": "flag", "keys": ["alolan"], "isAspect": true, "default": false},
                          "sea": {"type": "choice", "keys": ["sea"], "default": "random", "choices": ["west", "east"],
                                  "isAspect": true, "aspectFormat": "{{choice}}-sea"}},
             "spawns": []}""";

    @Test
    void looksAndFormsFollowAspects() {
        Dex dex = new Gson().fromJson(JSON, Dex.class);
        assertEquals("raichu", dex.model("raichu", Set.of("male")));
        assertEquals("raichu__shiny", dex.model("raichu", Set.of("shiny", "male")));
        assertEquals("raichu__female", dex.model("raichu", Set.of("female")));
        assertEquals("raichu__alolan__shiny", dex.model("raichu", Set.of("alolan", "shiny", "female"))); // form model wins

        Mon m = Mon.create(dex, "raichu", 30, List.of("alolan", "sea=east"));
        assertTrue(m.aspects.containsAll(List.of("alolan", "east-sea")), m.aspects.toString());
        assertEquals(List.of("electric", "psychic"), dex.of(m).types);
        assertEquals("raichualola", m.set(dex).get("species"));
        assertEquals(75, dex.of(m).catchRate); // not in the form: the species' own

        Mon plain = Mon.create(dex, "raichu", 30);
        assertFalse(plain.aspects.contains("alolan"));
        assertTrue(plain.aspects.contains("west-sea") || plain.aspects.contains("east-sea")); // "random" picks one
        assertEquals("raichu", plain.set(dex).get("species"));
        assertTrue(Mon.create(dex, "raichu", 30, List.of("shiny")).shiny);
    }
}
