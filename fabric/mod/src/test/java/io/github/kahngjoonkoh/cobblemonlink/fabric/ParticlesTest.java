package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.particles.ParticleTypes;
import org.junit.jupiter.api.Test;

class ParticlesTest {
    @org.junit.jupiter.api.BeforeAll
    static void minecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void namesPickAParticle() {
        assertEquals(ParticleTypes.END_ROD, Particles.look("shiny_sparkle_ambient").type());
        assertEquals(24, Particles.look("evo_buildup").count());
        assertEquals(ParticleTypes.HEART, Particles.look("healing_machine_heal").type());
        assertNull(Particles.look("activates"));
    }

    @Test
    void tintsGiveAColour() {
        var g = com.google.gson.JsonParser.parseString("{\"gradient\": {\"0.0\": \"#ffffffff\", \"0.65\": \"#FFCA00FF\"}}");
        assertEquals(0xCA00FF, Particles.colour(g));
        assertEquals(0xFF0000, Particles.colour(com.google.gson.JsonParser.parseString("[1, 0, 0, 1]")));
        assertNull(Particles.colour(com.google.gson.JsonParser.parseString("[\"v.r\", 0, 0]")));
    }
}
