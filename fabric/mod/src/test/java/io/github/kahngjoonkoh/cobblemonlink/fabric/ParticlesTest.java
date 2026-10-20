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
}
