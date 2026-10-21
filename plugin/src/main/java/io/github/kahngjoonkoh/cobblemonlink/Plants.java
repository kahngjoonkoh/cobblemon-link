package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cobblemon's plants as CustomBlocks: apricorns on apricorn trees, apricorn saplings, berry bushes, mints. What a
 * block's state can't say (an unripe apricorn's colour, which berry a bush is, its timers and fruit) lives in its
 * chunk's data; berry bushes and mints are drawn on Java by an item display (Bedrock draws one look per age).
 * Growth follows Cobblemon's blocks: ApricornBlock, ApricornSaplingBlock (SaplingBlock), MintBlock, BerryBlock(Entity).
 */
final class Plants implements Listener {
    static final String[] COLOURS = {"black", "blue", "green", "pink", "red", "white", "yellow"};
    static final String[] MINTS = {"red", "blue", "cyan", "pink", "green", "white"};
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final double RANDOM_TICK = 3 / 4096.0; // randomTickSpeed 3, per block per game tick
    private static final Gson GSON = new Gson();

    /** One plant's own data (chunk PDC, as JSON). */
    static final class Plant {
        String k;        // apricorn, sapling, berry, mint, leaves (a player's: never decays)
        String t;        // colour, or berry
        int s;           // sapling stage
        long n;          // berry: the game tick it grows next
        List<Integer> p; // berry: its growth points with fruit
        transient List<Entity> shown;
    }

    static final class Berry {
        List<String> ages;
        String fruit, flower;
        List<List<Double>> points;
        List<Integer> yield, growth, refresh, grove;
        double boneMeal;
        int weight;
        List<String> biomes;
        boolean anywhere;
        transient Set<Key> in;
    }

    private final CustomBlocks cb;
    private final NamespacedKey key = new NamespacedKey("cobblemonlink", "plants");
    final Map<String, Berry> berries;
    final Map<String, List<String>> mints;
    /** Loaded plants: world -> chunk key -> position key -> plant. */
    private final Map<World, Map<Long, Map<Long, Plant>>> loaded = new HashMap<>();
    /** Generated plants waiting for their chunk to load (generation runs off the main thread). */
    private final Map<String, Map<Long, Map<Long, Plant>>> pending = new ConcurrentHashMap<>();
    private final Set<Key> sparse, normal, dense;

    Plants(CustomBlocks cb, JsonObject table) {
        this.cb = cb;
        berries = GSON.fromJson(table.get("berries"), new TypeToken<Map<String, Berry>>() {}.getType());
        mints = GSON.fromJson(table.get("mints"), new TypeToken<Map<String, List<String>>>() {}.getType());
        for (Berry b : berries.values()) {
            b.in = new java.util.HashSet<>();
            for (String t : b.biomes) b.in.addAll(CustomBlocks.biomes(t));
        }
        sparse = CustomBlocks.biomes("cobblemon:has_feature/apricorns_sparse");
        normal = CustomBlocks.biomes("cobblemon:has_feature/apricorns_normal");
        dense = CustomBlocks.biomes("cobblemon:has_feature/apricorns_dense");
        for (World w : Bukkit.getWorlds()) for (Chunk c : w.getLoadedChunks()) load(c);
        Bukkit.getScheduler().runTaskTimer(cb.pl, this::tick, 20, 20);
    }

    // ---- the data: chunk PDC, loaded with the chunk ----

    static long chunkKey(int cx, int cz) { return (long) cx << 32 ^ (cz & 0xffffffffL); }

    private Map<Long, Plant> chunk(Block b) {
        return loaded.computeIfAbsent(b.getWorld(), w -> new HashMap<>()).computeIfAbsent(chunkKey(b.getX() >> 4, b.getZ() >> 4), k -> new HashMap<>());
    }

    Plant get(Block b) { return chunk(b).get(b.getBlockKey()); }

    private void put(Block b, Plant p) {
        chunk(b).put(b.getBlockKey(), p);
        save(b.getChunk());
    }

    void remove(Block b) {
        Plant p = chunk(b).remove(b.getBlockKey());
        if (p != null) {
            hide(p);
            save(b.getChunk());
        }
    }

    private void save(Chunk c) {
        Map<Long, Plant> m = loaded.getOrDefault(c.getWorld(), Map.of()).get(chunkKey(c.getX(), c.getZ()));
        if (m == null || m.isEmpty()) { c.getPersistentDataContainer().remove(key); return; }
        Map<String, Plant> out = new HashMap<>();
        m.forEach((k, p) -> out.put(Long.toString(k), p));
        c.getPersistentDataContainer().set(key, PersistentDataType.STRING, GSON.toJson(out));
    }

    private void load(Chunk c) {
        Map<Long, Plant> m = new HashMap<>();
        String s = c.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (s != null) {
            Map<String, Plant> in = GSON.fromJson(s, new TypeToken<Map<String, Plant>>() {}.getType());
            in.forEach((k, p) -> m.put(Long.parseLong(k), p));
        }
        loaded.computeIfAbsent(c.getWorld(), w -> new HashMap<>()).put(chunkKey(c.getX(), c.getZ()), m);
        Map<Long, Map<Long, Plant>> forWorld = pending.get(c.getWorld().getName());
        Map<Long, Plant> waiting = forWorld == null ? null : forWorld.remove(chunkKey(c.getX(), c.getZ()));
        if (waiting != null) {
            m.putAll(waiting);
            save(c);
        }
        boolean changed = false;
        for (var e : m.entrySet()) {
            Block b = c.getWorld().getBlockAt(Block.getBlockKeyX(e.getKey()), Block.getBlockKeyY(e.getKey()), Block.getBlockKeyZ(e.getKey()));
            Plant p = e.getValue();
            if ("berry".equals(p.k) && p.n == 0 && cb.name(b) != null) { timers(b, p, age(cb.name(b))); changed = true; } // a generated bush
            show(b, p);
        }
        if (changed) save(c);
    }

    @EventHandler
    void onLoad(ChunkLoadEvent e) { load(e.getChunk()); }

    @EventHandler
    void onUnload(ChunkUnloadEvent e) {
        Map<Long, Plant> m = loaded.getOrDefault(e.getWorld(), new HashMap<>()).remove(chunkKey(e.getChunk().getX(), e.getChunk().getZ()));
        if (m != null) m.values().forEach(this::hide);
    }

    /** Placed apricorn leaves never decay (vanilla's persistent=true). */
    void setPersistent(Block b, boolean on) {
        if (on) {
            Plant p = new Plant();
            p.k = "leaves";
            put(b, p);
        } else if (get(b) != null && "leaves".equals(get(b).k)) remove(b);
    }

    // ---- drawing berry bushes and mints on Java ----

    private void hide(Plant p) {
        if (p.shown != null) p.shown.forEach(Entity::remove);
        p.shown = null;
    }

    /** The item displays for a bush or a mint (its model for its kind and age; flowers or fruit at its growth points). */
    void show(Block b, Plant p) {
        hide(p);
        if ("leaves".equals(p.k)) return;
        String name = cb.name(b);
        if (name == null) return;
        int age = age(name);
        List<Entity> out = new ArrayList<>();
        out.add(clickBox(b, name, p));
        p.shown = out;
        Location centre = b.getLocation().add(.5, .5, .5);
        if (p.k.equals("apricorn") || p.k.equals("sapling")) return;
        if (p.k.equals("mint")) {
            List<String> m = mints.get(p.t);
            if (m != null) out.add(display(centre, m.get(age), new Quaternionf()));
        } else {
            Berry br = berries.get(p.t);
            if (br == null) return;
            out.add(display(centre, br.ages.get(age), new Quaternionf()));
            String part = age == 4 ? br.flower : age == 5 ? br.fruit : null;
            if (part != null && p.p != null)
                for (int i : p.p) {
                    if (i >= br.points.size()) continue;
                    List<Double> g = br.points.get(i); // block pixels, then Cobblemon's turn (z, y, x)
                    Quaternionf q = new Quaternionf().rotateZ((float) Math.toRadians(g.get(5))).rotateY((float) Math.toRadians(g.get(4))).rotateX((float) Math.toRadians(g.get(3)));
                    out.add(display(b.getLocation().add(g.get(0) / 16, g.get(1) / 16, g.get(2) / 16), part, q));
                }
        }
    }

    static final NamespacedKey CLICK = new NamespacedKey("cobblemonlink", "plant_at");

    /**
     * Java draws a plant over string's flat outline, which a player can't aim at: an interaction box the size of the
     * plant takes the clicks and hits instead (Bedrock aims at the custom block's own selection box).
     */
    private Entity clickBox(Block b, String name, Plant p) {
        float w = 0.8f, h = 0.8f;
        Location at = b.getLocation().add(.5, 0, .5);
        if (p.k.equals("apricorn")) { // hanging beside its leaves, Cobblemon's AABB a stage
            BlockFace f = BlockFace.valueOf(facing(name).toUpperCase());
            float size = new float[]{.25f, .35f, .45f, .45f}[age(name)];
            w = size;
            h = size + .15f;
            at.add(f.getModX() * (.5 - size / 2), .85 - h, f.getModZ() * (.5 - size / 2));
        } else if (p.k.equals("mint")) h = .2f + age(name) * .08f;
        else if (p.k.equals("berry")) h = new float[]{.15f, .45f, .7f, 1, 1, 1}[age(name)];
        else h = .8f; // sapling
        final float fw = w, fh = h;
        return b.getWorld().spawn(at, org.bukkit.entity.Interaction.class, e -> {
            e.setPersistent(false);
            e.setInteractionWidth(fw);
            e.setInteractionHeight(fh);
            e.getPersistentDataContainer().set(CLICK, PersistentDataType.LONG, b.getBlockKey());
        });
    }

    /** The plant a click box stands for, or null. */
    Block clicked(Entity e) {
        Long k = e instanceof org.bukkit.entity.Interaction ? e.getPersistentDataContainer().get(CLICK, PersistentDataType.LONG) : null;
        return k == null ? null : e.getWorld().getBlockAt(Block.getBlockKeyX(k), Block.getBlockKeyY(k), Block.getBlockKeyZ(k));
    }

    @EventHandler(ignoreCancelled = true)
    void onUse(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        Block b = clicked(e.getRightClicked());
        if (b == null) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        var pi = new PlayerInteractEvent(e.getPlayer(), org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                e.getPlayer().getInventory().getItemInMainHand(), b, BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
        if (onInteract(pi, b, e.getPlayer().getInventory().getItemInMainHand())) cb.handled = pi;
    }

    @EventHandler(ignoreCancelled = true)
    void onHit(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        Block b = clicked(e.getAttacked());
        if (b == null) return;
        e.setCancelled(true);
        String name = cb.name(b);
        if (name == null) return;
        org.bukkit.event.block.BlockBreakEvent ev = new org.bukkit.event.block.BlockBreakEvent(b, e.getPlayer()); // CustomBlocks.onBreak drops it
        Bukkit.getPluginManager().callEvent(ev);
        if (!ev.isCancelled()) b.setType(Material.AIR, true);
    }

    private ItemDisplay display(Location at, String model, Quaternionf rot) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(m -> m.setItemModel(NamespacedKey.fromString(model)));
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(it);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(1, 1, 1), new Quaternionf()));
        });
    }

    // ---- what a plant is ----

    private BlockData state(String name) { return cb.byName.get(name); }

    private static int age(String name) { return name.contains("age=") ? name.charAt(name.indexOf("age=") + 4) - '0' : 0; }

    private static String facing(String name) { return name.substring(name.indexOf("facing=") + 7, name.indexOf(']')); }

    private String colourOf(Block b, String name) {
        Plant p = get(b);
        if (p != null && p.t != null) return p.t;
        String base = CustomBlocks.base(name);
        return base.endsWith("_apricorn") || base.endsWith("_sapling") ? base.substring(0, base.indexOf('_')) : "red";
    }

    /** The item a plant picks as (creative pick-block). */
    String seedOf(Block b, String name) {
        String base = CustomBlocks.base(name);
        return switch (base) {
            case "berry" -> get(b) == null ? null : get(b).t;
            case "mint" -> get(b) == null ? null : get(b).t + "_mint_seeds";
            default -> base.endsWith("_sapling") ? colourOf(b, name) + "_apricorn_seed" : colourOf(b, name) + "_apricorn";
        };
    }

    private ItemStack item(String id, int n) { return cb.pl.items == null ? cb.item(id, n) : cb.pl.items.create(id, n); }

    /** What a plant drops when broken (Cobblemon's loot tables). */
    List<ItemStack> drops(Block b, String name, ItemStack tool) {
        List<ItemStack> out = new ArrayList<>();
        var r = ThreadLocalRandom.current();
        String base = CustomBlocks.base(name);
        Plant p = get(b);
        switch (base) {
            case "berry" -> { if (p != null) add(out, item(p.t, 1)); }
            case "mint" -> {
                if (p == null) break;
                int fortune = tool == null ? 0 : tool.getEnchantmentLevel(Enchantment.FORTUNE);
                add(out, item(p.t + "_mint_seeds", 1 + (age(name) == 7 && r.nextDouble() < 0.125 ? 1 : 0)));
                if (age(name) == 7) add(out, item(p.t + "_mint_leaf", 1 + (r.nextDouble() < 0.5 + 0.1 * fortune ? 1 : 0)));
            }
            default -> {
                if (base.endsWith("_sapling")) add(out, item(colourOf(b, name) + "_apricorn_seed", 1));
                else if (age(name) == 3) {
                    add(out, item(colourOf(b, name) + "_apricorn", 1));
                    if (r.nextDouble() < 0.1) add(out, item(colourOf(b, name) + "_apricorn_seed", 1));
                }
            }
        }
        return out;
    }

    private static void add(List<ItemStack> out, ItemStack it) { if (it != null) out.add(it); }

    // ---- planting, picking, bone meal ----

    /** A right-click on a block: plant a seed or berry, pick a ripe apricorn or bush, bone-meal a plant. */
    boolean onInteract(PlayerInteractEvent e, Block clicked, ItemStack hand) {
        var pl = e.getPlayer();
        String name = cb.name(clicked);
        String held = cb.pl.items == null ? null : cb.pl.items.id(hand);
        if (cb.isPlant(name)) {
            if (hand.getType() == Material.BONE_MEAL) {
                if (boneMeal(clicked, name) && pl.getGameMode() != GameMode.CREATIVE) hand.subtract();
                return true;
            }
            if (CustomBlocks.base(name).endsWith("_apricorn") && age(name) == 3) { pickApricorn(clicked, name); return true; }
            if (CustomBlocks.base(name).equals("berry") && age(name) == 5) { harvestBerry(clicked); return true; }
            return false;
        }
        if (held == null) return false;
        // on top of a block, or in place of grass you clicked (as vanilla plants go)
        Block at = clicked.isReplaceable() ? clicked : e.getBlockFace() == BlockFace.UP ? clicked.getRelative(BlockFace.UP) : null;
        if (at == null || !(at.getType().isAir() || at.isReplaceable()) || cb.name(at) != null) return false;
        Block ground = at.getRelative(BlockFace.DOWN);
        Material soil = ground.getType();
        boolean done = false;
        if (held.endsWith("_apricorn_seed") && (isDirt(soil) || soil == Material.FARMLAND)) {
            done = cb.place(pl, at, ground, hand, held.replace("_seed", "_sapling"));
            if (done) { record(at, "sapling", held.substring(0, held.indexOf('_'))); show(at, get(at)); }
        } else if (held.endsWith("_mint_seeds") && soil == Material.FARMLAND) {
            done = cb.place(pl, at, ground, hand, "mint[age=0]");
            if (done) { record(at, "mint", held.substring(0, held.indexOf('_'))); show(at, get(at)); }
        } else if (berries.containsKey(held) && soil == Material.FARMLAND) {
            done = cb.place(pl, at, ground, hand, "berry[age=0]");
            if (done) plantBerryData(at, held, 0);
        }
        if (done && pl.getGameMode() != GameMode.CREATIVE) hand.subtract();
        return done || held.endsWith("_apricorn_seed") || held.endsWith("_mint_seeds");
    }

    private void record(Block b, String kind, String type) {
        Plant p = new Plant();
        p.k = kind;
        p.t = type;
        put(b, p);
    }

    private boolean boneMeal(Block b, String name) {
        var r = ThreadLocalRandom.current();
        String base = CustomBlocks.base(name);
        Plant p = get(b);
        boolean used = false;
        if (base.endsWith("apricorn") && age(name) < 3) { grow(b, name); used = true; }
        else if (base.endsWith("_sapling")) {
            used = true;
            if (r.nextFloat() < 0.45) saplingStep(b, name);
        } else if (base.equals("mint") && age(name) < 7) { setAge(b, "mint", age(name) + 1); used = true; }
        else if (base.equals("berry") && age(name) < 5 && p != null) {
            used = true;
            Berry br = berries.get(p.t);
            if (br != null && r.nextFloat() <= br.boneMeal) berryGrow(b, p, age(name));
        }
        if (used) {
            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(.5, .5, .5), 8, .3, .3, .3);
            b.getWorld().playSound(b.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1, 1);
        }
        return used;
    }

    /** ApricornBlock.harvest: the apricorn (and maybe a seed), back to its first stage. */
    private void pickApricorn(Block b, String name) {
        String colour = colourOf(b, name);
        b.setBlockData(state("apricorn[age=0,facing=" + facing(name) + "]"), true);
        Plant p = get(b) == null ? new Plant() : get(b);
        p.k = "apricorn";
        p.t = colour;
        put(b, p);
        show(b, p);
        Location at = b.getLocation().add(.5, .5, .5);
        add2(at, item(colour + "_apricorn", 1));
        if (ThreadLocalRandom.current().nextDouble() < 0.1) add2(at, item(colour + "_apricorn_seed", 1));
        b.getWorld().playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1, 1);
    }

    private static void add2(Location at, ItemStack it) { if (it != null) at.getWorld().dropItemNaturally(at, it); }

    /** BerryBlockEntity.harvest: its berries (one per growth point), then back to mature to fruit again. */
    private void harvestBerry(Block b) {
        Plant p = get(b);
        if (p == null) return;
        int n = p.p == null ? 1 : p.p.size();
        add2(b.getLocation().add(.5, .5, .5), item(p.t, n));
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1, 1);
        p.p = null;
        b.setBlockData(state("berry[age=3]"), true);
        timers(b, p, 3);
        put(b, p);
        show(b, p);
    }

    // ---- growing ----

    /** Every second: random ticks (apricorns, saplings, mints) and berry timers, for loaded plants. */
    private void tick() {
        var r = ThreadLocalRandom.current();
        double chance = 20 * RANDOM_TICK;
        for (var w : loaded.entrySet())
            for (var c : new ArrayList<>(w.getValue().values()))
                for (var e : new ArrayList<>(c.entrySet())) {
                    Plant p = e.getValue();
                    if (p.k.equals("leaves")) continue;
                    Block b = w.getKey().getBlockAt(Block.getBlockKeyX(e.getKey()), Block.getBlockKeyY(e.getKey()), Block.getBlockKeyZ(e.getKey()));
                    String name = cb.name(b);
                    if (name == null) { remove(b); continue; }
                    switch (p.k) {
                        case "berry" -> {
                            if (age(name) < 5 && w.getKey().getGameTime() >= p.n) berryGrow(b, p, age(name));
                        }
                        case "apricorn" -> { if (age(name) < 3 && r.nextDouble() < chance && r.nextInt(5) == 0) grow(b, name); }
                        case "sapling" -> { if (r.nextDouble() < chance && b.getLightLevel() >= 9 && r.nextInt(7) == 0) saplingStep(b, name); }
                        case "mint" -> { if (age(name) < 7 && r.nextDouble() < chance && b.getLightLevel() >= 9 && r.nextInt(8) == 0) setAge(b, "mint", age(name) + 1); }
                        default -> { }
                    }
                }
    }

    /** An apricorn a stage on (its colour shows at the last). */
    private void grow(Block b, String name) {
        int a = age(name) + 1;
        String colour = colourOf(b, name);
        b.setBlockData(state(a == 3 ? colour + "_apricorn[age=3,facing=" + facing(name) + "]" : "apricorn[age=" + a + ",facing=" + facing(name) + "]"), true);
        if (get(b) != null) show(b, get(b));
    }

    private void setAge(Block b, String kind, int age) {
        b.setBlockData(state(kind + "[age=" + age + "]"), true);
        Plant p = get(b);
        if (p != null) show(b, p);
    }

    /** SaplingBlock.advanceTree: stage 0 -> 1, then a tree. */
    private void saplingStep(Block b, String name) {
        Plant p = get(b);
        if (p == null) { record(b, "sapling", colourOf(b, name)); p = get(b); }
        if (p.s == 0) { p.s = 1; put(b, p); return; }
        String colour = colourOf(b, name);
        remove(b);
        b.setType(Material.AIR, false);
        if (!tree(new WorldGrid(b.getWorld()), b.getX(), b.getY(), b.getZ(), colour, new Random(), false)) {
            b.setBlockData(state(colour + "_apricorn_sapling"), false);
            record(b, "sapling", colour);
            get(b).s = 1;
        }
    }

    /** BerryBlock.growHelper: a stage on; at flowering its growth points (its yield) are chosen. */
    private void berryGrow(Block b, Plant p, int age) {
        Berry br = berries.get(p.t);
        if (br == null || age >= 5) return;
        int next = age + 1;
        if (age == 3) {
            int n = br.yield.get(0) + ThreadLocalRandom.current().nextInt(br.yield.get(1) - br.yield.get(0) + 1);
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < br.points.size(); i++) idx.add(i);
            Collections.shuffle(idx);
            p.p = new ArrayList<>(idx.subList(0, Math.min(n, idx.size())));
        }
        b.setBlockData(state("berry[age=" + next + "]"), true);
        timers(b, p, next);
        put(b, p);
        show(b, p);
    }

    /**
     * BerryBlockEntity.resetGrowTimers / goToNextStageTimer: from planted or from mature, the whole wait is Cobblemon's
     * growth time (or refresh rate) x1.4 minutes; each stage takes 80-100% of what's left over the stages left.
     */
    private void timers(Block b, Plant p, int age) {
        Berry br = berries.get(p.t);
        if (br == null || age >= 5) return;
        var r = ThreadLocalRandom.current();
        List<Integer> range = age < 3 ? br.growth : br.refresh;
        long total = (long) ((range.get(0) + r.nextInt(range.get(1) - range.get(0) + 1)) * 1.4 * 1200);
        int left = age < 3 ? 3 - age : 5 - age;
        p.n = b.getWorld().getGameTime() + (long) (total / left * (0.8 + 0.2 * r.nextDouble()));
    }

    /** A berry bush of this kind at this age (a structure's, a grove's): its data and timers. */
    void plantBerry(Block b, String berry, int age) {
        b.setBlockData(state("berry[age=" + age + "]"), false);
        plantBerryData(b, berry, age);
    }

    private void plantBerryData(Block b, String berry, int age) {
        Plant p = new Plant();
        p.k = "berry";
        p.t = berry;
        if (age >= 4) {
            Berry br = berries.get(berry);
            if (br != null) {
                int n = br.yield.get(0);
                p.p = new ArrayList<>();
                for (int i = 0; i < Math.min(n, br.points.size()); i++) p.p.add(i);
            }
        }
        timers(b, p, age);
        put(b, p);
        show(b, p);
    }

    // ---- what holds a plant up ----

    /** A plant whose support went (leaves beside an apricorn, the ground under the rest) breaks. */
    void checkSupport(Block b) {
        String name = cb.name(b);
        if (!cb.isPlant(name)) return;
        String base = CustomBlocks.base(name);
        boolean ok;
        if (base.endsWith("apricorn")) ok = "apricorn_leaves".equals(cb.name(b.getRelative(BlockFace.valueOf(facing(name).toUpperCase()))));
        else ok = b.getRelative(BlockFace.DOWN).getType().isSolid();
        if (!ok) cb.breakNaturally(b, name, null, 1);
    }

    /** Leaves near a gone log or leaf decay if no apricorn log is within 6 steps through leaves (LeavesBlock). */
    void leavesMayDecay(Block from) {
        Bukkit.getScheduler().runTaskLater(cb.pl, () -> {
            for (int dx = -4; dx <= 4; dx++)
                for (int dy = -4; dy <= 4; dy++)
                    for (int dz = -4; dz <= 4; dz++) {
                        Block b = from.getRelative(dx, dy, dz);
                        if (!"apricorn_leaves".equals(cb.name(b))) continue;
                        Plant p = get(b);
                        if (p != null && "leaves".equals(p.k)) continue;
                        if (!nearLog(b)) {
                            long delay = 10 + ThreadLocalRandom.current().nextInt(60);
                            Bukkit.getScheduler().runTaskLater(cb.pl, () -> {
                                if ("apricorn_leaves".equals(cb.name(b)) && !nearLog(b)) cb.breakNaturally(b, "apricorn_leaves", null, 1);
                            }, delay);
                        }
                    }
        }, 2);
    }

    private boolean nearLog(Block start) {
        java.util.ArrayDeque<Block> q = new java.util.ArrayDeque<>(List.of(start));
        Map<Block, Integer> dist = new HashMap<>(Map.of(start, 0));
        while (!q.isEmpty()) {
            Block b = q.poll();
            int d = dist.get(b);
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                Block n = b.getRelative(f);
                String name = cb.name(n);
                if (name == null || dist.containsKey(n)) continue;
                if (name.contains("apricorn_log") || name.contains("apricorn_wood")) return true;
                if (name.equals("apricorn_leaves") && d + 1 < 6) {
                    dist.put(n, d + 1);
                    q.add(n);
                }
            }
        }
        return false;
    }

    // ---- world generation (off the main thread: plant data waits in `pending`) ----

    /** Writes for a tree: the live world, or a generating region. */
    interface Grid {
        BlockData get(int x, int y, int z);
        void set(int x, int y, int z, BlockData d);
        boolean in(int x, int y, int z);
        void plant(int x, int y, int z, Plant p);
    }

    final class WorldGrid implements Grid {
        final World w;
        WorldGrid(World w) { this.w = w; }
        public BlockData get(int x, int y, int z) { return w.getBlockAt(x, y, z).getBlockData(); }
        public void set(int x, int y, int z, BlockData d) { w.getBlockAt(x, y, z).setBlockData(d, false); }
        public boolean in(int x, int y, int z) { return w.isChunkLoaded(x >> 4, z >> 4); }
        public void plant(int x, int y, int z, Plant p) { put(w.getBlockAt(x, y, z), p); show(w.getBlockAt(x, y, z), p); }
    }

    final class RegionGrid implements Grid {
        final LimitedRegion r;
        final String world;
        RegionGrid(LimitedRegion r, String world) { this.r = r; this.world = world; }
        public BlockData get(int x, int y, int z) { return r.getBlockData(x, y, z); }
        public void set(int x, int y, int z, BlockData d) { r.setBlockData(x, y, z, d); }
        public boolean in(int x, int y, int z) { return r.isInRegion(x, y, z); }
        public void plant(int x, int y, int z, Plant p) {
            pending.computeIfAbsent(world, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new ConcurrentHashMap<>()).put(Block.getBlockKey(x, y, z), p);
        }
    }

    /** Generation: Cobblemon's apricorn trees, berry groves and mints (their placed features' rules). */
    void populate(WorldInfo info, Random r, int cx, int cz, LimitedRegion region) {
        if (info.getEnvironment() != World.Environment.NORMAL) return;
        RegionGrid g = new RegionGrid(region, info.getName());
        int x = (cx << 4) + r.nextInt(16), z = (cz << 4) + r.nextInt(16);
        if (r.nextInt(8) == 0) { // apricorn_trees: rarity 1/8, then ApricornTreeFeature's chance for the biome
            int y = ground(region, x, z);
            Key biome = region.getBiome(x, y, z).key();
            double m = sparse.contains(biome) ? 0.1 : dense.contains(biome) ? 10 : normal.contains(biome) ? 1 : 0;
            if (m > 0 && r.nextDouble() < m * 0.1) tree(g, x, y, z, COLOURS[r.nextInt(COLOURS.length)], r, true);
        }
        if (r.nextInt(25) == 0) grove(g, region, r, (cx << 4) + r.nextInt(16), (cz << 4) + r.nextInt(16));
        mints(g, region, r, (cx << 4) + r.nextInt(16), (cz << 4) + r.nextInt(16));
        Bukkit.getScheduler().runTask(cb.pl, this::flush);
    }

    /** Plants generated into chunks that had already loaded (a tree reaching over a chunk edge). */
    private void flush() {
        for (var w : pending.entrySet()) {
            World world = Bukkit.getWorld(w.getKey());
            if (world == null) continue;
            for (Long k : new ArrayList<>(w.getValue().keySet())) {
                int cx = (int) (k >> 32), cz = (int) (long) k;
                if (!world.isChunkLoaded(cx, cz) || !loaded.getOrDefault(world, Map.of()).containsKey(k)) continue;
                Map<Long, Plant> waiting = w.getValue().remove(k);
                if (waiting == null) continue;
                loaded.get(world).get(k).putAll(waiting);
                Chunk c = world.getChunkAt(cx, cz);
                save(c);
                waiting.forEach((pk, p) -> show(world.getBlockAt(Block.getBlockKeyX(pk), Block.getBlockKeyY(pk), Block.getBlockKeyZ(pk)), p));
            }
        }
    }

    private boolean airOrLeaves(Grid g, int x, int y, int z) {
        if (!g.in(x, y, z)) return false;
        BlockData d = g.get(x, y, z);
        return d.getMaterial().isAir() || Tag.LEAVES.isTagged(d.getMaterial()) || "apricorn_leaves".equals(cb.name(d));
    }

    private void setIfClear(Grid g, int x, int y, int z, BlockData d) { if (airOrLeaves(g, x, y, z)) g.set(x, y, z, d); }

    /** ApricornTreeFeature: a 5-log trunk, Cobblemon's leaf layers, up to 8 apricorns hanging on the leaves. */
    boolean tree(Grid g, int ox, int oy, int oz, String colour, Random r, boolean generating) {
        if (!g.in(ox, oy - 1, oz) || !isDirt(g.get(ox, oy - 1, oz).getMaterial())) return false;
        for (int y = 0; y <= 5; y++) if (!airOrLeaves(g, ox, oy + y, oz)) return false;
        BlockData log = state("apricorn_log[axis=y]"), leaf = state("apricorn_leaves");
        for (int y = 0; y <= 4; y++) g.set(ox, oy + y, oz, log);
        int ly = oy + 1;
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // north, east, south, west
        for (int[] d : dirs) for (int up = 0; up <= 3; up++) setIfClear(g, ox + d[0], ly + up, oz + d[1], leaf);
        int[] v = dirs[r.nextInt(4)];
        int px = ox + v[0] * 2, pz = oz + v[1] * 2, off = r.nextBoolean() ? -1 : 1;
        setIfClear(g, px, ly, pz, leaf);
        setIfClear(g, v[0] == 0 ? px + off : px, ly, v[0] == 0 ? pz : pz + off, leaf);
        for (int[] c : new int[][]{{1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) for (int up = 0; up <= 3; up++) setIfClear(g, ox + c[0], ly + up, oz + c[1], leaf);
        List<List<int[]>> spots = new ArrayList<>(); // [x, y, z, facing index]
        int l2 = oy + 2;
        for (int i = 0; i < 4; i++) {
            int[] d = dirs[i];
            List<int[]> s = new ArrayList<>();
            int lx = ox + d[0] * 2, lz = oz + d[1] * 2;
            setIfClear(g, lx, l2, lz, leaf);
            s.add(new int[]{lx + d[0], l2, lz + d[1], (i + 2) % 4});
            setIfClear(g, lx, l2 + 1, lz, leaf);
            s.add(new int[]{lx + d[0], l2 + 1, lz + d[1], (i + 2) % 4});
            spots.add(s);
        }
        for (int[] c : new int[][]{{1, 2}, {-1, 2}, {1, -2}, {-2, 1}, {2, 1}, {-2, -1}, {-1, -2}, {2, -1}}) {
            List<int[]> s = new ArrayList<>();
            for (int up = 0; up <= 1; up++) {
                int lx = ox + c[0], lz = oz + c[1];
                setIfClear(g, lx, l2 + up, lz, leaf);
                for (int i = 0; i < 4; i++) {
                    int ax = lx + dirs[i][0], az = lz + dirs[i][1];
                    if (g.in(ax, l2 + up, az) && g.get(ax, l2 + up, az).getMaterial() == Material.AIR) s.add(new int[]{ax, l2 + up, az, (i + 2) % 4});
                }
            }
            spots.add(s);
        }
        int ty = oy + 5;
        setIfClear(g, ox, ty, oz, leaf);
        for (int[] d : dirs) setIfClear(g, ox + d[0], ty, oz + d[1], leaf);
        List<Integer> used = new ArrayList<>();
        for (int i = 0, n = 2 + r.nextInt(2); i < n; i++) { // getLayerFourVariation
            int k;
            do k = r.nextInt(4); while (used.contains(k));
            used.add(k);
            int[] d = dirs[k];
            int qx = ox + d[0] * 2, qz = oz + d[1] * 2, o = r.nextBoolean() ? -1 : 1;
            int[] two = d[0] == 0 ? new int[]{qx + o, qz} : new int[]{qx, qz + o};
            if (r.nextInt(3) == 0) { setIfClear(g, qx, oy + 4, qz, leaf); setIfClear(g, two[0], oy + 4, two[1], leaf); }
            else if (r.nextBoolean()) setIfClear(g, qx, oy + 4, qz, leaf);
            else setIfClear(g, two[0], oy + 4, two[1], leaf);
        }
        List<List<int[]>> some = new ArrayList<>(spots.stream().filter(s -> !s.isEmpty()).toList());
        Collections.shuffle(some, r);
        String[] facings = {"north", "east", "south", "west"};
        for (List<int[]> s : some.subList(0, Math.min(8, some.size()))) {
            int[] a = s.get(r.nextInt(s.size()));
            int[] toward = dirs[a[3]];
            if (!g.in(a[0] + toward[0], a[1], a[2] + toward[1]) || !"apricorn_leaves".equals(cb.name(g.get(a[0] + toward[0], a[1], a[2] + toward[1])))) continue;
            if (!airOrLeaves(g, a[0], a[1], a[2])) continue;
            int age = generating ? r.nextInt(4) : 0;
            g.set(a[0], a[1], a[2], state(age == 3 ? colour + "_apricorn[age=3,facing=" + facings[a[3]] + "]" : "apricorn[age=" + age + ",facing=" + facings[a[3]] + "]"));
            Plant p = new Plant();
            p.k = "apricorn";
            p.t = colour;
            g.plant(a[0], a[1], a[2], p);
        }
        return true;
    }

    /** #minecraft:dirt as Cobblemon means it (grass blocks too: 26.x's tag of that name no longer has them). */
    static boolean isDirt(Material m) {
        return Tag.DIRT.isTagged(m) || switch (m) {
            case DIRT, GRASS_BLOCK, PODZOL, COARSE_DIRT, ROOTED_DIRT, MYCELIUM, MOSS_BLOCK, PALE_MOSS_BLOCK, MUD, MUDDY_MANGROVE_ROOTS -> true;
            default -> false;
        };
    }

    private static boolean wildSoil(Material m) {
        return isDirt(m) || Tag.SAND.isTagged(m) || Tag.TERRACOTTA.isTagged(m) || m == Material.SNOW_BLOCK || m == Material.FARMLAND;
    }

    private static boolean replaceable(Material m) {
        return m.isAir() || Tag.LEAVES.isTagged(m) || switch (m) {
            case FERN, DEAD_BUSH, FIRE, SOUL_FIRE, SNOW, VINE, GLOW_LICHEN, LIGHT, TALL_GRASS, LARGE_FERN, STRUCTURE_VOID -> true;
            default -> false;
        };
    }

    /** BerryGroveFeature: a berry that grows in this biome (by Cobblemon's weights), its grove size of bushes around a spot. */
    private boolean grove(Grid g, LimitedRegion region, Random r, int x, int z) {
        int[] o = locate(region, r, x, z, Plants::wildSoil);
        if (o == null) return false;
        Key biome = region.getBiome(o[0], o[1], o[2]).key();
        List<Map.Entry<String, Berry>> ok = berries.entrySet().stream().filter(e -> e.getValue().anywhere || e.getValue().in.contains(biome)).toList();
        int total = ok.stream().mapToInt(e -> Math.max(1, e.getValue().weight)).sum();
        if (total == 0) return false;
        int pick = r.nextInt(total);
        Map.Entry<String, Berry> berry = null;
        for (var e : ok) if ((pick -= Math.max(1, e.getValue().weight)) < 0) { berry = e; break; }
        if (berry == null) return false;
        Berry br = berry.getValue();
        int n = br.grove.get(0) + r.nextInt(Math.max(1, br.grove.get(1) - br.grove.get(0) + 1));
        List<int[]> around = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) around.add(new int[]{o[0] + dx, o[1] + dy, o[2] + dz});
        Collections.shuffle(around, r);
        for (int[] a : around) {
            if (n <= 0) break;
            if (!region.isInRegion(a[0], a[1] - 1, a[2]) || !region.isInRegion(a[0], a[1], a[2])) continue;
            if (!replaceable(region.getType(a[0], a[1], a[2])) || !wildSoil(region.getType(a[0], a[1] - 1, a[2]))) continue;
            int age = Math.max(3, Math.min(5, (int) Math.round(4 + r.nextGaussian())));
            region.setBlockData(a[0], a[1], a[2], state("berry[age=" + age + "]"));
            Plant p = new Plant();
            p.k = "berry";
            p.t = berry.getKey();
            if (age >= 4) {
                p.p = new ArrayList<>();
                for (int i = 0; i < Math.min(br.yield.get(0), br.points.size()); i++) p.p.add(i);
            }
            p.n = 0; // its timers start when the chunk loads (age >= 3: from the refresh rate)
            g.plant(a[0], a[1], a[2], p);
            n--;
        }
        return true;
    }

    /** MintBlockFeature: above y 70, rarer lower down; a mint (age 5-7) and up to two more beside it. */
    private boolean mints(Grid g, LimitedRegion region, Random r, int x, int z) {
        int y = ground(region, x, z);
        if (y < 70) return false;
        int chance = y < 80 ? 88 : y < 90 ? 77 : y < 100 ? 55 : y < 110 ? 33 : y < 120 ? 22 : 12;
        if (r.nextInt(chance) != 0) return false;
        int[] o = locate(region, r, x, z, Plants::isDirt);
        if (o == null) return false;
        String colour = MINTS[r.nextInt(MINTS.length)];
        List<int[]> at = new ArrayList<>(List.of(o));
        List<int[]> more = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    int[] a = {o[0] + dx, o[1] + dy, o[2] + dz};
                    if ((dx != 0 || dz != 0) && region.isInRegion(a[0], a[1] - 1, a[2]) && region.isInRegion(a[0], a[1], a[2])
                            && region.getType(a[0], a[1], a[2]).isAir() && isDirt(region.getType(a[0], a[1] - 1, a[2]))) more.add(a);
                }
        if (more.isEmpty()) return false;
        Collections.shuffle(more, r);
        at.addAll(more.subList(0, Math.min(2, more.size())));
        for (int[] a : at) {
            region.setBlockData(a[0], a[1], a[2], state("mint[age=" + (5 + r.nextInt(3)) + "]"));
            Plant p = new Plant();
            p.k = "mint";
            p.t = colour;
            g.plant(a[0], a[1], a[2], p);
        }
        return true;
    }

    /**
     * MOTION_BLOCKING_NO_LEAVES while generating: only the _WG heightmaps exist yet, so from the surface down past
     * leaves and plants.
     */
    private static int ground(LimitedRegion region, int x, int z) {
        int y = region.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE_WG);
        while (y > region.getWorld().getMinHeight() && region.isInRegion(x, y, z) && replaceable(region.getType(x, y, z))) y--;
        return y + 1;
    }

    /** cobblemon:locate_predicate: up to 64 tries within 8 blocks across and 1 up or down for open ground over soil. */
    private static int[] locate(LimitedRegion region, Random r, int x, int z, java.util.function.Predicate<Material> soil) {
        int y0 = ground(region, x, z);
        for (int i = 0; i < 64; i++) {
            int ax = x + r.nextInt(17) - 8, ay = y0 + r.nextInt(3) - 1, az = z + r.nextInt(17) - 8;
            if (!region.isInRegion(ax, ay, az) || !region.isInRegion(ax, ay - 1, az)) continue;
            if (replaceable(region.getType(ax, ay, az)) && soil.test(region.getType(ax, ay - 1, az))) return new int[]{ax, ay, az};
        }
        return null;
    }
}
