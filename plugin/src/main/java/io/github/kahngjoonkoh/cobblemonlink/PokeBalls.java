package io.github.kahngjoonkoh.cobblemonlink;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import kr.toxicity.model.api.animation.AnimationIterator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.util.Vector;

/**
 * Cobblemon's 3D Poké Ball (PokeBallModel, EmptyPokeBallEntity): the thrown snowball is hidden and the ball's model
 * rides along it, spinning ("throw"). A hit on a wild Pokémon runs Cobblemon's capture: the ball bounces back and
 * opens (0.7 s), the Pokémon goes in (2.2 s), the ball falls, then after 1 s one shake every 1.25 s per passed check,
 * and "capture" or "break". Sending a Pokémon out throws a ball to its spot and opens it there (THROW_DURATION 0.75 s).
 * ponytail: the standard Poké Ball's model for every ball; no beam, flash or sound effects; positions move by
 * teleport each tick (the model interpolates).
 */
final class PokeBalls {
    static final int THROW_TICKS = 15, BOUNCE_TICKS = 14, BEAM_TICKS = 44, FALL_TICKS = 10, BEFORE_SHAKE = 20, SHAKE_GAP = 25;
    private static final AnimationIterator.Type ONCE = AnimationIterator.Type.PLAY_ONCE, HOLD = AnimationIterator.Type.HOLD_ON_LAST;
    private final CobblemonLink pl;
    private final Map<UUID, Entity> flying = new HashMap<>(); // snowball -> the ball's carrier
    private final Set<UUID> capturing = new HashSet<>();      // wild Pokémon inside a ball right now

    PokeBalls(CobblemonLink pl) { this.pl = pl; }

    /** A Poké Ball leaves the hand: the model follows the (hidden) snowball. */
    void launch(Snowball s) {
        s.setVisibleByDefault(false);
        Entity ball = pl.spawner.ball(s.getLocation());
        Spawner.animate(ball, "throw", AnimationIterator.Type.LOOP);
        flying.put(s.getUniqueId(), ball);
    }

    /** Every tick: the flying balls follow their snowballs; a snowball that's gone (a miss) drops its ball. */
    void tick() {
        flying.entrySet().removeIf(en -> {
            Entity s = Bukkit.getEntity(en.getKey()), ball = en.getValue();
            if (s != null && s.isValid()) {
                ball.teleport(s.getLocation().setDirection(s.getVelocity()));
                return false;
            }
            ball.remove();
            return true;
        });
    }

    /** The ball model of a snowball that just hit something (null if none). */
    Entity land(Snowball s) { return flying.remove(s.getUniqueId()); }

    boolean capturing(Entity wild) { return capturing.contains(wild.getUniqueId()); }

    /**
     * Cobblemon's capture sequence for a ball that hit `wild`, ending in done(caught). shakes: passed checks (0-4,
     * Rules.shakes); 4 is a catch, shown as 3 shakes as in Cobblemon.
     */
    void capture(Entity ball, Entity wild, int shakes, Consumer<Boolean> done) {
        if (ball == null) ball = pl.spawner.ball(wild.getLocation().add(0, 1, 0));
        Entity b = ball;
        capturing.add(wild.getUniqueId());
        boolean frozen = wild instanceof org.bukkit.entity.Mob m && !m.isAware();
        Spawner.freeze(wild, true);
        Spawner.animate(wild, "recoil", ONCE);
        Spawner.animate(b, "bounce", ONCE);
        Location hit = b.getLocation(), up = hit.clone().add(hit.toVector().subtract(wild.getLocation().toVector()).setY(0)
                .normalize().multiply(0.6)).add(0, 1, 0);
        glide(b, hit, up, BOUNCE_TICKS);
        later(BOUNCE_TICKS, () -> Spawner.animate(b, "open", HOLD));
        later(BEAM_TICKS, () -> {
            Spawner.hide(wild, true);
            Spawner.animate(b, "shut", HOLD);
            Location ground = up.clone(); // down to the first solid block (Cobblemon: it falls until it hits one)
            for (int i = 0; i < 8 && !ground.clone().add(0, -1, 0).getBlock().getType().isSolid(); i++) ground.add(0, -1, 0);
            ground.setY(Math.floor(ground.getY()));
            glide(b, up, ground, FALL_TICKS);
        });
        int land = BEAM_TICKS + FALL_TICKS + BEFORE_SHAKE, shown = Math.min(shakes, 3);
        for (int i = 0; i < shown; i++) {
            String bob = "bob" + (i + 1);
            later(land + SHAKE_GAP * i, () -> Spawner.animate(b, bob, ONCE));
        }
        int end = land + SHAKE_GAP * shown;
        later(end, () -> {
            boolean caught = shakes >= 4;
            Spawner.animate(b, caught ? "capture" : "break", ONCE);
            if (!caught) {
                Spawner.hide(wild, false);
                if (!frozen) Spawner.freeze(wild, false);
            }
        });
        later(end + 20, () -> { // Cobblemon: discard after 1 s (capture) / 1.2 s (break)
            b.remove();
            capturing.remove(wild.getUniqueId());
            done.accept(shakes >= 4);
        });
    }

    /** Throws a ball from the player to a Pokémon just sent out, which stays hidden until the ball opens. */
    void sendOut(Player p, Entity mon) {
        Spawner.hide(mon, true);
        Location from = p.getEyeLocation(), to = mon.getLocation().add(0, 0.3, 0);
        Entity b = pl.spawner.ball(from);
        Spawner.animate(b, "throw", AnimationIterator.Type.LOOP);
        for (int t = 1; t <= THROW_TICKS; t++) { // an arc 1.5 blocks high
            double u = t / (double) THROW_TICKS;
            Location at = from.clone().add(to.toVector().subtract(from.toVector()).multiply(u)).add(0, 1.5 * 4 * u * (1 - u), 0);
            later(t, () -> b.teleport(at));
        }
        later(THROW_TICKS, () -> {
            Spawner.animate(b, "open", HOLD);
            Spawner.hide(mon, false);
            Spawner.animate(mon, "cry", ONCE);
        });
        later(THROW_TICKS + 10, b::remove);
    }

    private void glide(Entity e, Location from, Location to, int ticks) {
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / ticks);
        for (int t = 1; t <= ticks; t++) {
            Location at = from.clone().add(step.clone().multiply(t));
            later(t, () -> { if (e.isValid()) e.teleport(at); });
        }
    }

    private void later(int ticks, Runnable r) { Bukkit.getScheduler().runTaskLater(pl, r, ticks); }
}
