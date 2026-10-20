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
 * - Display Case: shows one item (right-click with it); right-click with an empty hand takes it back.
 * - Campfire Pot: put on a campfire; its menu takes a 3x3 grid and up to three seasonings and cooks Cobblemon's pot
 *   recipes while the campfire is lit. Seasonings with bait effects go into a Poké Snack or Poké Bait.
 * - Gilded Chest: a chest of 27 slots (kept in machines.json).
 * - Poké Snack: draws a wild Pokémon within 8 blocks about once a minute, with its seasonings' bait effects; eaten
 *   after 8 bites (Cobblemon's MAX_BITES).
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
        String shown;           // display case: the item inside (ItemStack bytes, Base64)
        List<String> stored;    // gilded chest: its 27 slots (ItemStack bytes, Base64; "" = empty)
        String bait;            // Poké Snack: its seasonings (Cobblemon bait items, comma separated)
        int bites;              // Poké Snack: Pokémon drawn so far
        long nextBite;          // Poké Snack: when the next one comes (ms)
        transient Entity carrier, display;
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
        for (Machine m : new ArrayList<>(machines.values())) { // a Poké Snack can be eaten up meanwhile
            World w = Bukkit.getWorld(m.world);
            if (w == null || !w.isChunkLoaded(m.x >> 4, m.z >> 4)) continue;
            if (m.carrier == null || !m.carrier.isValid()) m.carrier = carrier(m.at(w), "clb_" + m.type);
            if (m.type.equals("restoration_tank") && m.reviveAt > 0 && now >= m.reviveAt) revive(m, w);
            if (m.type.equals("pasture")) roam(m, w);
            if (m.shown != null && (m.display == null || !m.display.isValid())) m.display = showcase(m, w);
            if (m.type.equals("poke_snack") && now >= m.nextBite) snack(m, w, now);
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

    /** A Poké Snack's bite: a Pokémon comes (when one fits), and after 8 the snack is gone. */
    private void snack(Machine m, World w, long now) {
        m.nextBite = now + java.util.concurrent.ThreadLocalRandom.current().nextLong(40_000, 80_000);
        List<Dex.Bait> bait = new ArrayList<>();
        if (m.bait != null) for (String id : m.bait.split(",")) bait.addAll(pl.dex.baits.getOrDefault(id, List.of()));
        if (pl.spawner.snack(m.at(w), bait) == null) return;
        if (++m.bites >= 8) {
            machines.remove(m.key());
            if (m.carrier != null) m.carrier.remove();
            w.getBlockAt(m.x, m.y, m.z).setType(Material.AIR);
        }
        save();
    }

    /**
     * The display case's item, worn by a small invisible marker armour stand inside the glass: Geyser draws armour
     * stands for Bedrock (not item displays), and a dropped item would be pushed out of the barrier.
     */
    private Entity showcase(Machine m, World w) {
        ItemStack it = ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(m.shown));
        return w.spawn(m.at(w).add(0, -0.72, 0), org.bukkit.entity.ArmorStand.class, d -> {
            d.setPersistent(false);
            d.setInvisible(true);
            d.setMarker(true);
            d.setSmall(true);
            d.setGravity(false);
            d.getEquipment().setHelmet(it);
        });
    }

    /** Right-click a display case: put the held item in, or take what's inside. */
    private void displayCase(Player p, Machine m, ItemStack hand) {
        World w = Bukkit.getWorld(m.world);
        if (m.shown != null) {
            p.getInventory().addItem(ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(m.shown)))
                    .values().forEach(x -> w.dropItemNaturally(m.at(w).add(0, 0.5, 0), x));
            m.shown = null;
            if (m.display != null) m.display.remove();
        } else if (hand != null && !hand.isEmpty()) {
            m.shown = java.util.Base64.getEncoder().encodeToString(hand.asOne().serializeAsBytes());
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.subtract();
        }
        save();
    }

    /** A Gilded Chest's 27 slots, saved back when closed. */
    final class Chest implements org.bukkit.inventory.InventoryHolder {
        final Machine m;
        final org.bukkit.inventory.Inventory inv;

        Chest(Machine m, Player p) {
            this.m = m;
            inv = Bukkit.createInventory(this, 27, Component.text(pl.items.name(p, m.type)));
            if (m.stored != null)
                for (int i = 0; i < m.stored.size() && i < 27; i++)
                    if (!m.stored.get(i).isEmpty()) inv.setItem(i, ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(m.stored.get(i))));
            Sounds.play(m.carrier, "block.gilded_chest.open");
        }

        @Override
        public org.bukkit.inventory.Inventory getInventory() { return inv; }

        void close() {
            List<String> out = new ArrayList<>();
            for (ItemStack s : inv.getContents()) out.add(s == null || s.isEmpty() ? "" : java.util.Base64.getEncoder().encodeToString(s.serializeAsBytes()));
            m.stored = out;
            save();
        }
    }

    /** The Campfire Pot's menu: a 3x3 grid, three seasonings, Cook, the result. */
    final class Pot implements org.bukkit.inventory.InventoryHolder {
        static final int[] GRID = {1, 2, 3, 10, 11, 12, 19, 20, 21}, SEASONING = {5, 14, 23};
        static final int COOK = 15, RESULT = 16;
        final Machine m;
        final org.bukkit.inventory.Inventory inv;

        Pot(Machine m, Player p) {
            this.m = m;
            inv = Bukkit.createInventory(this, 27, Component.text(pl.items.name(p, m.type)));
            ItemStack pane = Menu.icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
            for (int i = 0; i < 27; i++) inv.setItem(i, pane);
            for (int i : GRID) inv.setItem(i, null);
            for (int i : SEASONING) inv.setItem(i, null);
            inv.setItem(RESULT, null);
            inv.setItem(COOK, Menu.icon(Material.CAMPFIRE, pl.t(p, "cobblemonlink.pot.cook"), List.of(pl.t(p, "cobblemonlink.pot.seasoning"))));
        }

        @Override
        public org.bukkit.inventory.Inventory getInventory() { return inv; }

        boolean free(int slot) {
            return slot == RESULT || java.util.Arrays.stream(GRID).anyMatch(x -> x == slot) || java.util.Arrays.stream(SEASONING).anyMatch(x -> x == slot);
        }

        void click(org.bukkit.event.inventory.InventoryClickEvent e) {
            int slot = e.getRawSlot();
            if (slot >= 27) { if (e.isShiftClick()) e.setCancelled(true); return; } // ponytail: no shift-moving into the pot
            if (!free(slot) || slot == RESULT && !e.getCursor().isEmpty()) e.setCancelled(true);
            if (slot == COOK) cook((Player) e.getWhoClicked());
        }

        /** Cobblemon's cooking, at once: the campfire must be lit; one of each grid item and seasoning is used. */
        void cook(Player p) {
            World w = Bukkit.getWorld(m.world);
            Block fire = w.getBlockAt(m.x, m.y, m.z);
            if (!(fire.getBlockData() instanceof org.bukkit.block.data.type.Campfire c) || !c.isLit()) {
                p.sendMessage(Component.text(pl.t(p, "cobblemonlink.pot.unlit"), NamedTextColor.RED));
                return;
            }
            ItemStack[] grid = new ItemStack[9];
            for (int i = 0; i < 9; i++) grid[i] = inv.getItem(GRID[i]);
            Recipes.Def d = Recipes.cook(pl, grid);
            ItemStack out = d == null ? null : Recipes.result(pl, d), have = inv.getItem(RESULT);
            if (out == null || !Recipes.empty(have) && (!have.isSimilar(out) || have.getAmount() + out.getAmount() > have.getMaxStackSize())) return;
            List<String> bait = new ArrayList<>();
            for (int i : SEASONING) {
                ItemStack s = inv.getItem(i);
                String id = s == null || s.isEmpty() ? null : java.util.Objects.requireNonNullElse(pl.items.id(s), s.getType().getKey().toString());
                if (id == null || !pl.dex.baits.containsKey(id)) continue;
                bait.add(id);
                s.subtract();
            }
            if (!bait.isEmpty() && (d.result.equals("cobblemon:poke_snack") || d.result.equals("cobblemon:poke_bait")))
                out.editPersistentDataContainer(pdc -> pdc.set(new org.bukkit.NamespacedKey(pl, "bait"), org.bukkit.persistence.PersistentDataType.STRING, String.join(",", bait)));
            for (int i : GRID) { ItemStack s = inv.getItem(i); if (s != null) s.subtract(); }
            if (Recipes.empty(have)) inv.setItem(RESULT, out); else have.add(out.getAmount());
            Sounds.play(p, "block.campfire_pot.cook");
        }

        /** Closing gives back what's left in it. */
        void close(Player p) {
            for (int i = 0; i < 27; i++) {
                if (!free(i)) continue;
                ItemStack s = inv.getItem(i);
                if (s != null && !s.isEmpty()) p.getInventory().addItem(s).values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
                inv.setItem(i, null);
            }
        }
    }

    /** A machine of this type within r blocks (Cobblemon's find_nearby_block, for dialogues). */
    boolean near(Location at, String type, int r) {
        for (Machine m : machines.values())
            if (m.type.equals(type) && m.world.equals(at.getWorld().getName()) && m.at(at.getWorld()).distance(at) <= r) return true;
        return false;
    }

    void removeAll() {
        for (Machine m : machines.values()) {
            if (m.display != null) m.display.remove();
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
        if (type.startsWith("campfire_pot_")) { // on the campfire itself, which stays
            if (!org.bukkit.Tag.CAMPFIRES.isTagged(clicked.getType()) || at(clicked) != null) return false;
            Machine m = new Machine();
            m.type = type;
            m.world = clicked.getWorld().getName();
            m.x = clicked.getX();
            m.y = clicked.getY();
            m.z = clicked.getZ();
            m.yaw = Math.round((p.getLocation().getYaw() + 180) / 90f) * 90f;
            m.owner = p.getUniqueId().toString();
            machines.put(m.key(), m);
            save();
            return true;
        }
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
        if (type.equals("poke_snack")) { // its seasonings, from the Campfire Pot
            m.bait = p.getInventory().getItemInMainHand().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(pl, "bait"), org.bukkit.persistence.PersistentDataType.STRING);
            m.nextBite = System.currentTimeMillis() + 20_000;
        }
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
        if (m.shown != null) w.dropItemNaturally(m.at(w).add(0, 0.5, 0), ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(m.shown)));
        if (m.stored != null) for (String st : m.stored) // a gilded chest spills what it held
            if (!st.isEmpty()) w.dropItemNaturally(m.at(w).add(0, 0.5, 0), ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(st)));
        if (m.display != null) m.display.remove();
        m.roaming.forEach(Entity::remove);
        Block b = w.getBlockAt(m.x, m.y, m.z);
        if (!m.type.startsWith("campfire_pot_")) b.setType(Material.AIR); // a pot leaves its campfire
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
            case "display_case" -> displayCase(p, m, hand);
            default -> {
                if (m.type.startsWith("campfire_pot_")) p.openInventory(new Pot(m, p).inv);
                if (m.type.endsWith("gilded_chest")) p.openInventory(new Chest(m, p).inv);
            }
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
            Entity e = pl.spawner.roamer(m.at(w).add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2), mon.worn(pl.dex), mon.drawScale(pl.dex));
            m.roaming.add(e);
        }
    }
}
