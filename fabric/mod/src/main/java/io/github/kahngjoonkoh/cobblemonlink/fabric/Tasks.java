package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Work on the server thread: once a second until it says it's done. */
public final class Tasks {
    private static final List<BooleanSupplier> PENDING = new ArrayList<>();
    private static final List<Runnable> NEXT = new ArrayList<>();
    private static int tick;

    private Tasks() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            List<Runnable> now = new ArrayList<>(NEXT);
            NEXT.clear();
            now.forEach(Runnable::run);
            if (++tick % 20 == 0) PENDING.removeIf(BooleanSupplier::getAsBoolean);
        });
    }

    /** At the end of this tick (server thread), whatever is running now. */
    public static void next(Runnable r) { NEXT.add(r); }

    public static void until(BooleanSupplier done) { if (!done.getAsBoolean()) PENDING.add(done); }
}
