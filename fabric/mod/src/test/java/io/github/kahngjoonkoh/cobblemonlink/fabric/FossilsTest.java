package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FossilsTest {
    @Test
    void stagesThenFetus() {
        assertEquals("fossil_embryo_stage1", Fossils.look(0, "fossil_kabuto_fetus").model());
        assertEquals("fossil_embryo_stage3", Fossils.look(0.6f, "fossil_kabuto_fetus").model());
        assertEquals("fossil_kabuto_fetus", Fossils.look(1, "fossil_kabuto_fetus").model());
        assertEquals(0.8f, Fossils.look(1, "fossil_kabuto_fetus").scale());
    }
}
