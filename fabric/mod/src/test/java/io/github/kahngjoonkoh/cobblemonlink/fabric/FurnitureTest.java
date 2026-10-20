package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FurnitureTest {
    @Test
    void firstVariantWhoseKeysAllMatch() {
        var vs = List.of(new Furniture.Variant(Furniture.matching(Furniture.props("facing=east,charge=6")), "active", 270),
                new Furniture.Variant(Furniture.matching(Furniture.props("facing=east,charge=0")), "idle", 270),
                new Furniture.Variant(Furniture.matching(Furniture.props("")), "any", 0));
        assertEquals("active", Furniture.variant(vs, Furniture.props("facing=east,natural=true,charge=6")).model());
        assertEquals("idle", Furniture.variant(vs, Furniture.props("facing=east,natural=false,charge=0")).model());
        assertEquals("any", Furniture.variant(vs, Furniture.props("facing=north,charge=6")).model());
    }
}
