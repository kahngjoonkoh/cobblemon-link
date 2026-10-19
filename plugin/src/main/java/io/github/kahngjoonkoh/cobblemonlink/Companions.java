package io.github.kahngjoonkoh.cobblemonlink;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

/**
 * Party Pokémon sent out into the world (Cobblemon's SendOutPokemonHandler): each party slot toggles on its own, so up
 * to 6 can be out; fainted ones stay in; a recall right after a send-out is ignored (Cobblemon's throw-animation
 * guard against spam). They follow their trainer and go back in for a battle, a world change, death or logout.
 * ponytail: no throw animation or interaction wheel; following is a plain pathfind every half second.
 */
final class Companions {
    private static final int RECALL_GUARD_TICKS = 25; // THROW_DURATION + 0.5 s, as in Cobblemon
    private final CobblemonLink pl;
    private final Map<UUID, Map<String, Entity>> out = new HashMap<>(); // player -> Mon.id -> carrier

    Companions(CobblemonLink pl) { this.pl = pl; }

    boolean isOut(Player p, Mon m) { return out.getOrDefault(p.getUniqueId(), Map.of()).containsKey(m.id); }

    /** Cobblemon's R on a party slot: out if in, in if out. */
    void toggle(Player p, Mon m) {
        Map<String, Entity> mine = out.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Entity e = mine.get(m.id);
        if (e != null) {
            if (e.isValid() && e.getTicksLived() < RECALL_GUARD_TICKS) return;
            recall(p, m.id);
            return;
        }
        if (pl.battles.containsKey(p.getUniqueId())) return;
        if (m.fainted(pl.dex)) {
            pl.panel.say(p, m.name(pl.dex) + " has fainted.", true); // with the party panel, when it is shown
            return;
        }
        mine.put(m.id, pl.spawner.companion(spot(p), m.model(pl.dex), p));
    }

    void recall(Player p, String monId) {
        Map<String, Entity> mine = out.get(p.getUniqueId());
        Entity e = mine == null ? null : mine.remove(monId);
        if (e != null) e.remove();
    }

    void recallAll(Player p) {
        Map<String, Entity> mine = out.remove(p.getUniqueId());
        if (mine != null) mine.values().forEach(Entity::remove);
    }

    /** Every 10 ticks: follow the trainer; catch up by teleport when far behind or in another world. */
    void tick() {
        out.entrySet().removeIf(en -> {
            Player p = pl.getServer().getPlayer(en.getKey());
            if (p == null) {
                en.getValue().values().forEach(Entity::remove);
                return true;
            }
            en.getValue().values().removeIf(e -> !e.isValid());
            for (Entity e : en.getValue().values()) {
                Location to = p.getLocation();
                double d = e.getWorld().equals(to.getWorld()) ? e.getLocation().distance(to) : Double.MAX_VALUE;
                if (d > 24) e.teleport(spot(p));
                else if (d > 4 && e instanceof Mob mob) mob.getPathfinder().moveTo(to, 1.3);
            }
            return en.getValue().isEmpty();
        });
    }

    /** Where the player is looking, up to 12 blocks, on the ground or a water surface (Cobblemon's raycastSafeSendout), else 3 blocks ahead. */
    private static Location spot(Player p) {
        RayTraceResult hit = p.rayTraceBlocks(12, org.bukkit.FluidCollisionMode.ALWAYS); // ClipContext.Fluid.ANY
        Block b = hit != null && hit.getHitBlock() != null ? hit.getHitBlock()
                : p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3)).getBlock();
        for (int i = 0; i < 6 && !b.getType().isSolid() && !b.isLiquid(); i++) b = b.getRelative(0, -1, 0); // to the ground or water
        while (b.getRelative(0, 1, 0).getType().isSolid()) b = b.getRelative(0, 1, 0);   // up out of a wall
        Location l = b.getLocation().add(0.5, 1, 0.5);
        l.setDirection(p.getLocation().toVector().subtract(l.toVector()).setY(0));
        return l;
    }
}
