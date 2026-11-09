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

    @Test
    void doubleTapForwardSprints() {
        int[] s = {0, 99};
        s = Riding.sprint(s, true);  // held: no sprint
        assertEquals(0, s[0]);
        s = Riding.sprint(s, false); // let go...
        s = Riding.sprint(s, false);
        s = Riding.sprint(s, true);  // ...and pressed again 2 ticks later: sprint
        assertEquals(1, s[0]);
        for (int i = 0; i < 10; i++) s = Riding.sprint(s, false);
        s = Riding.sprint(s, true);  // after 10 ticks: not a double tap
        assertEquals(0, s[0]);
    }

    @Test
    void staminaRunsOutAndComesBack() {
        float s = 1;
        for (int i = 0; i < 100; i++) s = Riding.stamina(s, true);
        assertEquals(0, s, 1e-4);
        for (int i = 0; i < 200; i++) s = Riding.stamina(s, false);
        assertEquals(1, s, 1e-4);
    }

    @Test
    void oggLength() { // a 2 s Vorbis stream at 44100 Hz: identification header rate, last page granule 88200
        byte[] b = new byte[64];
        byte[] id = {1, 'v', 'o', 'r', 'b', 'i', 's', 0, 0, 0, 0, 2, 0x44, (byte) 0xAC, 0, 0};
        System.arraycopy("OggS".getBytes(), 0, b, 0, 4);
        System.arraycopy(id, 0, b, 10, id.length);
        System.arraycopy("OggS".getBytes(), 0, b, 40, 4);
        b[46] = (byte) 0x88; b[47] = 0x58; b[48] = 0x01; // 88200 little-endian
        assertEquals(40, RideSounds.Ogg.oggTicks(b));
    }
}
