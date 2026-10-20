package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Cobblemon's machine blocks on a vanilla server: a barrier holds the space (two for the tall ones), and Cobblemon's
 * own block model stands in it (tools/build_blocks.py: BetterModel for Java, GeyserModelEngine for Bedrock).
 * Right-click uses it; Shift + left-click picks it up. Placed machines are kept in machines.json.
 * - Healing Machine: heals the party (ponytail: no charge to wait for).
 * - PC: opens the PC.
 * - Restoration Tank: takes fossils; when they make a Pokémon (Cobblemon's fossils), it is revived after 12 minutes
 *   and comes out wild beside the tank, to be caught. The Fossil Analyzer is decoration beside it.
 * - Pasture: party Pokémon left to roam around it, taken back from its menu.
 * - TM Machine: makes TMs from a Blank TM and Cobblemon's TM ingredients.
 */
final class Machines {
    static final List<String> TALL = List.of("pc", "pasture", "restoration_tank");
    static final long REVIVE_MS = 12 * 60_000L; // FossilMultiblockStructure.TIME_TO_TAKE: 12 minutes

    static final class Machine {
        String type, world;
        int x, y, z;
        float yaw;
        List<String> fossils = new ArrayList<>();
        long reviveAt;          // tank: when the fossil's Pokémon comes out (0 = idle)
        String owner;           // who placed it (the pasture's Pokémon belong to their owners)
        List<Mon> pastured = new ArrayList<>();
        List<String> pasturedBy = new ArrayList<>();
        transient Entity carrier;
        transient List<Entity> roaming = new ArrayList<>();

        String key() { return world + "/" + x + "/" + y + "/" + z; }

        Location at(World w) { return new Location(w, x + 0.5, y, z + 0.5, yaw, 0); }
    }

    private final CobblemonLink pl;
    private final Path file;
    private final Map<String, Machine> machines = new HashMap<>();
    private final Map<String, List<String>> fossilRecipes; // species -> the fossils it takes

    Machines(CobblemonLink pl, Map<String, List<String>> fossilRecipes) {
        this.pl = pl;
        this.file = pl.getDataFolder().toPath().resolve("machines.json");
        this.fossilRecipes = fossilRecipes;
        try {
            if (Files.exists(file)) {
                List<Machine> list = Store.GSON.fromJson(Files.readString(file), new TypeToken<List<Machine>>() {}.getType());
                for (Machine m : list) {
                    m.roaming = new ArrayList<>();
                    machines.put(m.key(), m);
                }
            }
        } catch (IOException e) {
            pl.getLogger().warning("machines.json unreadable: " + e);
        }
    }

    void save() {
        try {
            Files.writeString(file, Store.GSON.toJson(new ArrayList<>(machines.values())));
        } catch (IOException e) {
            pl.getLogger().warning("machines.json not saved: " + e);
        }
    }

    /** Draws every machine whose chunk is loaded (and forgets models that went with their chunk). */
    void tick() {
        long now = System.currentTimeMillis();
        for (Machine m : machines.values()) {
            World w = Bukkit.getWorld(m.world);
            if (w == null || !w.isChunkLoaded(m.x >> 4, m.z >> 4)) continue;
            if (m.carrier == null || !m.carrier.isValid()) m.carrier = carrier(m.at(w), "clb_" + m.type);
            if (m.type.equals("restoration_tank") && m.reviveAt > 0 && now >= m.reviveAt) revive(m, w);
            if (m.type.equals("pasture")) roam(m, w);
        }
    }

    private Entity carrier(Location at, String model) {
        Pig pig = at.getWorld().spawn(at, Pig.class, e -> {
            e.setPersistent(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setAware(false);
            e.setGravity(false);
            e.setCollidable(false);
            e.setRotation(at.getYaw(), 0);
            e.getPersistentDataContainer().set(pl.companionKey, org.bukkit.persistence.PersistentDataType.STRING, "machine");
        });
        BetterModel.model(model).ifPresent(r -> r.create(BukkitAdapter.adapt(pig)));
        return pig;
    }

    void removeAll() {
        for (Machine m : machines.values()) {
            if (m.carrier != null) m.carrier.remove();
            m.roaming.forEach(Entity::remove);
        }
    }

    Machine at(Block b) {
        Machine m = machines.get(b.getWorld().getName() + "/" + b.getX() + "/" + b.getY() + "/" + b.getZ());
        if (m == null) m = machines.get(b.getWorld().getName() + "/" + b.getX() + "/" + (b.getY() - 1) + "/" + b.getZ());
        return m != null && TALL.contains(m.type) || m != null && m.y == b.getY() ? m : null;
    }

    /** Right-click with a machine item on a block face: place it there, facing the player. */
    boolean place(Player p, String type, Block clicked, BlockFace face) {
        Block b = clicked.getRelative(face);
        if (!b.getType().isAir() || TALL.contains(type) && !b.getRelative(BlockFace.UP).getType().isAir()) return false;
        Machine m = new Machine();
        m.type = type;
        m.world = b.getWorld().getName();
        m.x = b.getX();
        m.y = b.getY();
        m.z = b.getZ();
        m.yaw = Math.round((p.getLocation().getYaw() + 180) / 90f) * 90f; // facing the player, square to the world
        m.owner = p.getUniqueId().toString();
        b.setType(Material.BARRIER);
        if (TALL.contains(type)) b.getRelative(BlockFace.UP).setType(Material.BARRIER);
        machines.put(m.key(), m);
        save();
        return true;
    }

    /** Shift + left-click: the machine comes back as its item (a pasture's Pokémon go home to their owners first). */
    void pickUp(Player p, Machine m) {
        World w = Bukkit.getWorld(m.world);
        if (w == null) return;
        for (int i = 0; i < m.pastured.size(); i++) returnHome(m, i);
        machines.remove(m.key());
        if (m.carrier != null) m.carrier.remove();
        m.roaming.forEach(Entity::remove);
        Block b = w.getBlockAt(m.x, m.y, m.z);
        b.setType(Material.AIR);
        if (TALL.contains(m.type)) b.getRelative(BlockFace.UP).setType(Material.AIR);
        ItemStack it = pl.items.create(m.type, 1);
        if (it != null) w.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), it);
        save();
    }

    /** Right-click on a machine. */
    void use(Player p, Machine m, ItemStack hand) {
        switch (m.type) {
            case "healing_machine" -> {
                if (pl.battles.containsKey(p.getUniqueId())) {
                    p.sendMessage(Component.text(pl.t(p, "cobblemon.healingmachine.inbattle"), NamedTextColor.RED));
                    return;
                }
                Store.Data d = pl.store.get(p.getUniqueId());
                if (d.party.isEmpty()) {
                    p.sendMessage(Component.text(pl.t(p, "cobblemon.healingmachine.nopokemon"), NamedTextColor.RED));
                    return;
                }
                d.party.forEach(Mon::heal);
                pl.store.save(p.getUniqueId());
                p.sendMessage(Component.text(pl.t(p, "cobblemon.healingmachine.healed"), NamedTextColor.GREEN));
                Sounds.play(m.carrier, "block.healing_machine.active");
                Spawner.animate(m.carrier, "idle", kr.toxicity.model.api.animation.AnimationIterator.Type.PLAY_ONCE);
            }
            case "pc" -> {
                Sounds.play(m.carrier, "pc.on");
                pl.pc.open(p);
            }
            case "restoration_tank" -> tank(p, m, hand);
            case "fossil_analyzer" -> p.sendMessage(Component.text(pl.t(p, "cobblemonlink.machine.analyzer"), NamedTextColor.GRAY));
            case "pasture" -> pastureMenu(p, m);
            case "tm_machine" -> tmMenu(p, 0);
            default -> {}
        }
    }

    // ---- restoration tank ----

    private void tank(Player p, Machine m, ItemStack hand) {
        if (m.reviveAt > 0) {
            long left = Math.max(0, m.reviveAt - System.currentTimeMillis()) / 1000;
            p.sendMessage(Component.text(pl.t(p, "cobblemonlink.machine.reviving", left / 60, left % 60), NamedTextColor.GRAY));
            return;
        }
        String id = pl.items.id(hand);
        Items.Def d = id == null ? null : pl.items.defs.get(id);
        if (d == null || !d.kind.equals("fossil") || m.fossils.size() >= 2) {
            p.sendMessage(Component.text(pl.t(p, "cobblemonlink.machine.tank_hint"), NamedTextColor.GRAY));
            return;
        }
        m.fossils.add(id);
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.subtract();
        String result = result(m.fossils);
        if (result != null) {
            m.reviveAt = System.currentTimeMillis() + REVIVE_MS;
            p.sendMessage(Component.text(pl.t(p, "cobblemonlink.machine.revive_start"), NamedTextColor.GREEN));
        } else p.sendMessage(Component.text(pl.t(p, "cobblemonlink.machine.fossil_in"), NamedTextColor.GRAY));
        save();
    }

    /** The species these fossils make (Cobblemon's data/cobblemon/fossils), or null. */
    String result(List<String> fossils) { return Rules.fossil(fossilRecipes, fossils); }

    private void revive(Machine m, World w) {
        String species = result(m.fossils);
        m.fossils.clear();
        m.reviveAt = 0;
        save();
        if (species == null || pl.dex.get(species) == null) return;
        Location at = m.at(w).add(m.at(w).getDirection().multiply(1.5));
        pl.spawner.spawn(at, Mon.create(pl.dex, species, 1), true); // FossilMultiblockStructure: a wild Pokémon to catch
        Player owner = m.owner == null ? null : Bukkit.getPlayer(UUID.fromString(m.owner));
        if (owner != null) owner.sendMessage(Component.text(pl.t(owner, "cobblemonlink.machine.revived", pl.dex.name(species, CobblemonLink.loc(owner))), NamedTextColor.GREEN));
    }

    // ---- TM machine ----

    /**
     * Cobblemon's TM Machine: the TMs whose ingredients (its tms/*.json recipes: type gems and the like) you carry,
     * each made from a Blank TM. ponytail: every TM is available (Cobblemon unlocks them as your Pokémon learn moves).
     */
    void tmMenu(Player p, int page) {
        List<String> can = new ArrayList<>();
        for (var e : pl.items.tms.entrySet()) if (has(p, e.getValue().recipe)) can.add(e.getKey());
        java.util.Collections.sort(can);
        Menu menu = new Menu(54, pl.t(p, "block.cobblemon.tm_machine"));
        if (can.isEmpty()) menu.set(22, Menu.icon(Material.PAPER, pl.t(p, "cobblemonlink.machine.tm_none"), List.of()), null);
        for (int i = 0; i < 45 && page * 45 + i < can.size(); i++) {
            String move = can.get(page * 45 + i);
            List<String> lore = pl.items.tms.get(move).recipe.stream().map(r -> r.get(1) + " × " + r.get(0)).toList();
            menu.set(i, Menu.icon(pl.items.tm(move), "TM " + pl.dex.label("move", move, CobblemonLink.loc(p)), lore), e -> {
                ItemStack blank = pl.items.create("blank_tm", 1);
                if (!has(p, pl.items.tms.get(move).recipe) || blank == null || !p.getInventory().containsAtLeast(blank, 1)) {
                    p.sendMessage(Component.text(pl.t(p, "cobblemonlink.machine.tm_missing"), NamedTextColor.RED));
                    return;
                }
                take(p, pl.items.tms.get(move).recipe);
                p.getInventory().removeItem(blank);
                p.getInventory().addItem(pl.items.tm(move)).values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
                tmMenu(p, page);
            });
        }
        if (page > 0) menu.set(45, Menu.icon(Material.ARROW, "←", List.of()), e -> tmMenu(p, page - 1));
        if ((page + 1) * 45 < can.size()) menu.set(53, Menu.icon(Material.ARROW, "→", List.of()), e -> tmMenu(p, page + 1));
        p.openInventory(menu.getInventory());
    }

    /** The stacks a recipe entry stands for ("cobblemon:x", "minecraft:x" or "#minecraft:tag"). */
    private boolean matches(String id, ItemStack it) {
        if (it == null) return false;
        if (id.startsWith("cobblemon:")) return id.substring(10).equals(pl.items.id(it));
        if (pl.items.id(it) != null) return false;
        if (id.startsWith("#")) {
            var tag = Bukkit.getTag(org.bukkit.Tag.REGISTRY_ITEMS, org.bukkit.NamespacedKey.fromString(id.substring(1)), Material.class);
            return tag != null && tag.isTagged(it.getType());
        }
        return it.getType().getKey().toString().equals(id);
    }

    private boolean has(Player p, List<List<Object>> recipe) {
        for (List<Object> r : recipe) {
            int need = ((Number) r.get(1)).intValue();
            for (ItemStack it : p.getInventory().getContents()) if (matches((String) r.get(0), it)) need -= it.getAmount();
            if (need > 0) return false;
        }
        return true;
    }

    private void take(Player p, List<List<Object>> recipe) {
        for (List<Object> r : recipe) {
            int need = ((Number) r.get(1)).intValue();
            for (ItemStack it : p.getInventory().getContents()) {
                if (need <= 0 || !matches((String) r.get(0), it)) continue;
                int n = Math.min(need, it.getAmount());
                it.subtract(n);
                need -= n;
            }
        }
    }

    // ---- pasture ----

    /** Cobblemon's pasture: one chest row of your party to put out, one of the Pokémon roaming here to take back. */
    private void pastureMenu(Player p, Machine m) {
        Menu menu = new Menu(27, pl.t(p, "block.cobblemon.pasture"));
        List<Mon> party = pl.store.get(p.getUniqueId()).party;
        for (int i = 0; i < party.size(); i++) {
            Mon mon = party.get(i);
            menu.set(i, Menu.monIcon(pl.dex, mon, pl.t(p, "cobblemonlink.machine.pasture_put")), e -> {
                if (party.size() <= 1 || m.pastured.size() >= 16) return; // keep one; Cobblemon's pasture holds 16
                party.remove(mon);
                m.pastured.add(mon);
                m.pasturedBy.add(p.getUniqueId().toString());
                pl.store.save(p.getUniqueId());
                save();
                pastureMenu(p, m);
            });
        }
        for (int i = 0; i < m.pastured.size() && i < 9; i++) {
            int idx = i;
            boolean mine = p.getUniqueId().toString().equals(m.pasturedBy.get(i));
            menu.set(18 + i, Menu.monIcon(pl.dex, m.pastured.get(i), mine ? pl.t(p, "cobblemonlink.machine.pasture_take") : ""),
                    mine ? e -> {
                        Mon mon = m.pastured.get(idx);
                        if (!pl.store.give(p.getUniqueId(), mon)) p.sendMessage(Component.text(pl.t(p, "cobblemonlink.capture.to_pc", mon.name(pl.dex, CobblemonLink.loc(p)))));
                        m.pastured.remove(idx);
                        m.pasturedBy.remove(idx);
                        save();
                        pastureMenu(p, m);
                    } : null);
        }
        p.openInventory(menu.getInventory());
    }

    private void returnHome(Machine m, int i) {
        UUID owner = UUID.fromString(m.pasturedBy.get(i));
        pl.store.give(owner, m.pastured.get(i));
    }

    /** The pastured Pokémon wander within 8 blocks, as companions do (Spawner.companion, wandering kept). */
    private void roam(Machine m, World w) {
        m.roaming.removeIf(e -> !e.isValid());
        if (m.roaming.size() == m.pastured.size()) {
            for (Entity e : m.roaming)
                if (e.getLocation().distanceSquared(m.at(w)) > 64) e.teleport(m.at(w).add(1, 0, 1));
            return;
        }
        m.roaming.forEach(Entity::remove);
        m.roaming.clear();
        for (Mon mon : m.pastured) {
            Entity e = pl.spawner.roamer(m.at(w).add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2), mon.model(pl.dex));
            m.roaming.add(e);
        }
    }
}
