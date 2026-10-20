package io.github.kahngjoonkoh.cobblemonlink.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RidingTest {
    @Test
    void forwardIsWhereTheRiderLooks() {
        Vec3 south = Riding.step(1, 0, 0, 0.3); // yaw 0 faces +z
        assertEquals(0, south.x, 1e-9);
        assertEquals(0.3, south.z, 1e-9);
        Vec3 west = Riding.step(1, 0, 90, 0.3); // yaw 90 faces -x
        assertEquals(-0.3, west.x, 1e-9);
        Vec3 diag = Riding.step(1, 1, 0, 0.3); // never faster on a diagonal
        assertEquals(0.3, Math.hypot(diag.x, diag.z), 1e-9);
    }
}
