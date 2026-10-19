package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Work on the server thread: once a second until it says it's done. */
public final class Tasks {
    private static final List<BooleanSupplier> PENDING = new ArrayList<>();
    private static int tick;

    private Tasks() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (++tick % 20 == 0) PENDING.removeIf(BooleanSupplier::getAsBoolean);
        });
    }

    public static void until(BooleanSupplier done) { if (!done.getAsBoolean()) PENDING.add(done); }
}
