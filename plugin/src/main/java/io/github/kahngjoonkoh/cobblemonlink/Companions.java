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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/**
 * Party Pokémon sent out into the world (Cobblemon's SendOutPokemonHandler): each party slot toggles on its own, so up
 * to 6 can be out; fainted ones stay in; a recall right after a send-out is ignored (Cobblemon's throw-animation
 * guard against spam). They follow their trainer and go back in for a battle, a world change, death or logout.
 * Sending out throws a Poké Ball (PokeBalls.sendOut).
 * ponytail: no interaction wheel; following is a plain pathfind every half second.
 */
final class Companions {
    private static final int RECALL_GUARD_TICKS = 25; // THROW_DURATION + 0.5 s, as in Cobblemon
    private final CobblemonLink pl;
    private final Map<UUID, Map<String, Entity>> out = new HashMap<>(); // player -> Mon.id -> carrier
    private final Map<UUID, Entity[]> shoulders = new HashMap<>();       // player -> [left, right] carriers

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
        Entity mon = pl.spawner.companion(spot(p), m.worn(pl.dex), m.drawScale(pl.dex), p);
        pl.balls.sendOut(p, mon);
        mine.put(m.id, mon);
    }

    void recall(Player p, String monId) {
        Map<String, Entity> mine = out.get(p.getUniqueId());
        Entity e = mine == null ? null : mine.remove(monId);
        if (e != null) {
            unshoulder(p, e);
            e.remove();
        }
    }

    /**
     * Cobblemon's shoulder mounting: Shift + right-click your sent-out Pokémon (a shoulderMountable species) to put it
     * on a free shoulder, left first; it rides there until Shift + right-click with an empty hand.
     * ponytail: the carrier is moved to the shoulder every tick (no passenger offset for a model carrier).
     */
    boolean shoulder(Player p, Entity e) {
        Map<String, Entity> mine = out.getOrDefault(p.getUniqueId(), Map.of());
        String id = mine.entrySet().stream().filter(x -> x.getValue().equals(e)).map(Map.Entry::getKey).findFirst().orElse(null);
        Mon m = id == null ? null : pl.store.get(p.getUniqueId()).party.stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
        if (m == null) return false;
        Entity[] s = shoulders.computeIfAbsent(p.getUniqueId(), k -> new Entity[2]);
        if (e.equals(s[0]) || e.equals(s[1])) return true;
        if (!pl.dex.of(m).shoulder) {
            p.sendActionBar(Component.text(pl.t(p, "cobblemonlink.shoulder.cant", m.name(pl.dex, CobblemonLink.loc(p))), NamedTextColor.RED));
            return true;
        }
        int side = s[0] == null || !s[0].isValid() ? 0 : s[1] == null || !s[1].isValid() ? 1 : -1;
        if (side < 0) return true;
        s[side] = e;
        e.setGravity(false);
        return true;
    }

    /** The party Pokémon this carrier is (one of the player's sent-out ones), or null. */
    private Mon monOf(Player p, Entity e) {
        String id = out.getOrDefault(p.getUniqueId(), Map.of()).entrySet().stream().filter(x -> x.getValue().equals(e)).map(Map.Entry::getKey).findFirst().orElse(null);
        return id == null ? null : pl.store.get(p.getUniqueId()).party.stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
    }

    /**
     * Cobblemon's cosmetic items: Shift + right-click your sent-out Pokémon with one it can wear (data/cobblemon/
     * cosmetic_items) to put it on, the old one coming back; right-click with an empty hand takes it off. The model
     * is the converted look with the cosmetic (tools/looks.py cosmetic_looks).
     */
    void cosmetic(Player p, Entity e, ItemStack hand) {
        Mon m = monOf(p, e);
        if (m == null) return;
        if (hand == null) {
            if (m.cosmetic == null) return;
            giveBack(p, m.cosmetic);
            m.cosmetic = null;
        } else {
            String id = pl.items.id(hand);
            String aspect = pl.dex.cosmeticFor(m.species, id != null ? id : hand.getType().getKey().toString(), id == null ? hand.getType() : null);
            if (aspect == null) {
                p.sendActionBar(Component.text(pl.t(p, "cobblemonlink.item.no_effect"), NamedTextColor.RED));
                return;
            }
            if (m.cosmetic != null) giveBack(p, m.cosmetic);
            m.cosmetic = new Mon.Cosmetic();
            m.cosmetic.aspect = aspect;
            m.cosmetic.item = java.util.Base64.getEncoder().encodeToString(hand.asOne().serializeAsBytes());
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.subtract();
        }
        pl.store.save(p.getUniqueId());
        recall(p, m.id); // out again in its new look
        toggle(p, m);
    }

    private static void giveBack(Player p, Mon.Cosmetic c) {
        p.getInventory().addItem(ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(c.item)))
                .values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
    }

    /** Shift + right-click with an empty hand: the shoulder Pokémon hop down (the last one up first). */
    boolean unshoulderLast(Player p) {
        Entity[] s = shoulders.get(p.getUniqueId());
        if (s == null) return false;
        for (int i = 1; i >= 0; i--)
            if (s[i] != null) {
                unshoulder(p, s[i]);
                return true;
            }
        return false;
    }

    private void unshoulder(Player p, Entity e) {
        Entity[] s = shoulders.get(p.getUniqueId());
        if (s == null) return;
        for (int i = 0; i < 2; i++) if (e.equals(s[i])) s[i] = null;
        if (s[0] == null && s[1] == null) shoulders.remove(p.getUniqueId());
        e.setGravity(true);
        if (e.isValid()) e.teleport(spot(p));
    }

    /** Every tick: shoulder Pokémon sit on the trainer's shoulders, facing where the trainer faces. */
    void shoulderTick() {
        shoulders.entrySet().removeIf(en -> {
            Player p = pl.getServer().getPlayer(en.getKey());
            Entity[] s = en.getValue();
            for (int i = 0; i < 2; i++) {
                if (s[i] == null) continue;
                if (p == null || !s[i].isValid()) { s[i] = null; continue; }
                Location l = p.getLocation();
                double yaw = Math.toRadians(l.getYaw()), side = i == 0 ? 0.38 : -0.38, back = 0.12; // + left, - right of the player
                Location at = l.clone().add(Math.cos(yaw) * side + Math.sin(yaw) * back, p.isSneaking() ? 0.95 : 1.25,
                        Math.sin(yaw) * side - Math.cos(yaw) * back);
                at.setPitch(0);
                s[i].teleport(at);
            }
            return s[0] == null && s[1] == null;
        });
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
