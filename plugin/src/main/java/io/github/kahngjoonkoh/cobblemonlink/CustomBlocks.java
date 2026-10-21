package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundGroup;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cobblemon's own blocks as real blocks for players without the mod (docs/custom-blocks.md, tools/build_custom_blocks.py).
 * Each is a mushroom block state (full cubes) or a tripwire state (plants) that the plugin holds back from vanilla; our
 * Java pack and Geyser's custom block mappings draw it as Cobblemon's block. VanillaBlocks keeps real mushroom blocks
 * and string working as vanilla; Plants grows the plants; this class places, breaks and generates them.
 */
final class CustomBlocks implements Listener {
    private static final NamespacedKey FAST = new NamespacedKey("cobblemonlink", "block_mining");

    final CobblemonLink pl;
    final NamespacedKey itemKey;
    final NamespacedKey gildedKey = new NamespacedKey("cobblemonlink", "gilded");
    final Map<String, BlockData> byName = new HashMap<>();
    final Map<String, String> byState = new HashMap<>(); // BlockData.getAsString() -> name
    final Map<String, String> drops = new HashMap<>();
    final Map<String, JsonObject> itemDefs = new HashMap<>();
    final Map<String, JsonObject> loot = new HashMap<>();
    final List<Vein> veins = new ArrayList<>();
    final Plants plants;
    final VanillaBlocks vanilla;
    /** The interaction this class handled (CobblemonLink.onUse leaves it alone). */
    PlayerInteractEvent handled;

    record Vein(String feature, int count, int size, int min, int max, boolean trapezoid, Set<Key> biomes, Map<String, String> ores) {}

    CustomBlocks(CobblemonLink pl, Path table) throws IOException {
        this.pl = pl;
        itemKey = new NamespacedKey(pl, "item"); // Items.java's: its id() reads ours too
        JsonObject t = JsonParser.parseString(Files.readString(table)).getAsJsonObject();
        int bad = 0;
        for (var e : t.getAsJsonObject("blocks").entrySet()) {
            try {
                BlockData d = Bukkit.createBlockData(e.getValue().getAsString());
                byName.put(e.getKey(), d);
                byState.put(d.getAsString(), e.getKey());
            } catch (IllegalArgumentException ex) {
                bad++; // a state this Minecraft doesn't have
            }
        }
        if (bad > 0) pl.getLogger().warning(bad + " custom block states don't exist in this Minecraft version");
        t.getAsJsonObject("drops").entrySet().forEach(e -> drops.put(e.getKey(), e.getValue().getAsString()));
        t.getAsJsonObject("items").entrySet().forEach(e -> itemDefs.put(e.getKey(), e.getValue().getAsJsonObject()));
        if (t.has("loot")) t.getAsJsonObject("loot").entrySet().forEach(e -> loot.put(e.getKey(), e.getValue().getAsJsonObject()));
        for (var el : t.getAsJsonArray("veins")) {
            JsonObject v = el.getAsJsonObject();
            Map<String, String> ores = new HashMap<>();
            v.getAsJsonObject("ores").entrySet().forEach(e -> ores.put(e.getKey(), e.getValue().getAsString()));
            veins.add(new Vein(v.get("feature").getAsString(), v.get("count").getAsInt(), v.get("size").getAsInt(), v.get("min").getAsInt(),
                    v.get("max").getAsInt(), v.get("shape").getAsString().equals("trapezoid"), biomes(v.get("biomes").getAsString()), ores));
        }
        plants = new Plants(this, t);
        vanilla = new VanillaBlocks(this);
        Bukkit.getWorlds().forEach(this::addPopulator);
        Bukkit.getScheduler().runTaskTimer(pl, this::aim, 1, 2);
        var pm = pl.getServer().getPluginManager();
        pm.registerEvents(this, pl);
        pm.registerEvents(plants, pl);
        pm.registerEvents(vanilla, pl);
        pl.getLogger().info(byName.size() + " custom block states, " + veins.size() + " ore features, " + plants.berries.size() + " berries");
    }

    /** The biomes in a biome tag (empty when the server doesn't have it). */
    static Set<Key> biomes(String tag) {
        var reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        Set<Key> in = new HashSet<>();
        TagKey<Biome> key = TagKey.create(RegistryKey.BIOME, Key.key(tag.replace("#", "")));
        if (reg.hasTag(key)) reg.getTagValues(key).forEach(b -> in.add(b.key()));
        return in;
    }

    /** The custom block a block is ("moon_stone_ore", "apricorn_log[axis=y]", "red_apricorn[age=3,facing=north]"), or null. */
    String name(Block b) { return name(b.getBlockData()); }

    String name(BlockData d) {
        Material m = d.getMaterial();
        return m == Material.BROWN_MUSHROOM_BLOCK || m == Material.RED_MUSHROOM_BLOCK || m == Material.MUSHROOM_STEM || m == Material.TRIPWIRE
                ? byState.get(d.getAsString()) : null;
    }

    static String base(String name) { return name == null ? null : name.contains("[") ? name.substring(0, name.indexOf('[')) : name; }

    boolean isPlant(String name) { return name != null && byName.containsKey(name) && byName.get(name).getMaterial() == Material.TRIPWIRE; }

    /** One of our items: a placeable block or a seed (Items.create falls back here). */
    ItemStack item(String id, int amount) {
        JsonObject d = itemDefs.get(id);
        if (d == null) return null;
        ItemStack it = new ItemStack(Material.PAPER, amount);
        it.editMeta(m -> {
            String kind = d.get("kind").getAsString();
            m.customName(Component.translatable((kind.equals("block") ? "block" : "item") + ".cobblemon." + id, d.get("name").getAsString())
                    .decoration(TextDecoration.ITALIC, false));
            m.setItemModel(new NamespacedKey("cobblemon", id));
            m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        });
        return it;
    }

    /** The block an item places, or null. */
    String placeable(ItemStack it) {
        String id = it == null || !it.hasItemMeta() ? null : it.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        JsonObject d = id == null ? null : itemDefs.get(id);
        return d != null && d.get("kind").getAsString().equals("block") ? id : null;
    }

    // ---- what a block is like: hardness, tool, sounds ----

    /** The vanilla block a custom block mines and sounds like. */
    static Material like(String name) {
        String b = base(name);
        if (b.startsWith("deepslate_")) return Material.DEEPSLATE_IRON_ORE;
        if (b.startsWith("nether_")) return Material.NETHER_QUARTZ_ORE;
        if (b.endsWith("_ore")) return Material.IRON_ORE;
        if (b.endsWith("_stone_block")) return Material.AMETHYST_BLOCK;
        if (b.endsWith("leaves")) return Material.OAK_LEAVES;
        if (b.endsWith("planks")) return Material.OAK_PLANKS;
        if (b.contains("apricorn_log") || b.contains("apricorn_wood")) return Material.OAK_LOG;
        if (b.contains("sapling")) return Material.OAK_SAPLING;
        if (b.endsWith("apricorn")) return Material.COCOA;
        if (b.equals("berry")) return Material.SWEET_BERRY_BUSH;
        return Material.WHEAT;
    }

    /** The block's own sound (the client plays the mushroom block's or string's for the player who did it). */
    void sound(Block b, String name, boolean place) {
        SoundGroup g = like(name).createBlockData().getSoundGroup();
        b.getWorld().playSound(b.getLocation().add(.5, .5, .5), place ? g.getPlaceSound() : g.getBreakSound(), (g.getVolume() + 1) / 2, g.getPitch() * 0.8f);
    }

    /** Fraction of a block broken a tick, vanilla's formula without the player's own factors (they cancel out). */
    static double progress(BlockData d, ItemStack tool) {
        float hard = d.getMaterial().getHardness();
        if (hard <= 0) return 1;
        boolean harvest = !d.requiresCorrectToolForDrops() || d.isPreferredTool(tool);
        return d.getDestroySpeed(tool, true) / hard / (harvest ? 30 : 100);
    }

    // ---- placing ----

    @EventHandler(priority = EventPriority.LOW)
    void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        Block clicked = e.getClickedBlock();
        if (clicked.getType().isInteractable() && !p.isSneaking() && name(clicked) == null) return; // a chest, a door: use it
        ItemStack hand = p.getInventory().getItemInMainHand();
        String block = placeable(hand);
        if (block != null) {
            handled = e;
            e.setCancelled(true);
            Block at = (clicked.getType().isAir() || clicked.isReplaceable()) && name(clicked) == null ? clicked : clicked.getRelative(e.getBlockFace());
            if (!(at.getType().isAir() || at.isReplaceable() && name(at) == null)) return;
            if (!at.getWorld().getNearbyEntities(BoundingBox.of(at), x -> x instanceof LivingEntity && !(x instanceof Player q && q.getGameMode() == GameMode.SPECTATOR)).isEmpty()) return;
            String name = byName.containsKey(block) ? block : block + "[axis=" + switch (e.getBlockFace()) {
                case EAST, WEST -> "x";
                case NORTH, SOUTH -> "z";
                default -> "y";
            } + "]";
            if (place(p, at, clicked, hand, name)) {
                if (name.equals("apricorn_leaves")) plants.setPersistent(at, true); // placed leaves never decay
                if (p.getGameMode() != GameMode.CREATIVE) hand.subtract();
            }
            return;
        }
        if (plants.onInteract(e, aimed(clicked, e.getBlockFace()), hand)) {
            handled = e;
            e.setCancelled(true);
        }
    }

    /**
     * The block a player means: Java draws a plant over string's small outline, so a click on it lands on the block
     * behind (the leaves an apricorn hangs from, the ground under a bush); a plant on the clicked face is the one.
     */
    Block aimed(Block clicked, BlockFace face) {
        if (isPlant(name(clicked)) || face == null) return clicked;
        Block in = clicked.getRelative(face);
        return isPlant(name(in)) ? in : clicked;
    }

    /** Puts a custom block down for a player (a BlockPlaceEvent first, so protection plugins can say no). */
    boolean place(Player p, Block at, Block against, ItemStack hand, String name) {
        BlockData d = byName.get(name);
        if (d == null) return false;
        BlockState before = at.getState();
        at.setBlockData(d, true);
        BlockPlaceEvent ev = new BlockPlaceEvent(at, before, against, hand, p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) {
            before.update(true, false);
            return false;
        }
        sound(at, name, true);
        return true;
    }

    // ---- breaking: mining time, drops, sounds ----

    /**
     * The client times a break from the mushroom block's (0.2, axe: an iron axe breaks it at once); a break-speed
     * modifier makes it the block's own. It's set while the player looks at one of ours, so it's there before the
     * first hit (a block broken at once never sends a damage event to set it).
     */
    private void aim() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            Block b = p.getTargetBlockExact(6);
            String name = b == null ? null : name(b);
            if (name == null || isPlant(name)) { clearSpeed(p); continue; }
            ItemStack tool = p.getInventory().getItemInMainHand();
            speed(p, progress(like(name).createBlockData(), tool) / progress(b.getBlockData(), tool));
        }
    }

    private static void speed(Player p, double ratio) {
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a == null) return;
        AttributeModifier old = a.getModifier(FAST);
        if (old != null && Math.abs(old.getAmount() - (ratio - 1)) < 1e-9) return;
        if (old != null) a.removeModifier(FAST);
        a.addTransientModifier(new AttributeModifier(FAST, ratio - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    @EventHandler(ignoreCancelled = true)
    void onDamage(BlockDamageEvent e) {
        Block aimed = aimed(e.getBlock(), e.getBlockFace());
        if (aimed != e.getBlock()) { // a hit on the plant drawn there breaks the plant (they break at once)
            e.setCancelled(true);
            BlockBreakEvent ev = new BlockBreakEvent(aimed, e.getPlayer()); // onBreak below does the drops
            Bukkit.getPluginManager().callEvent(ev);
            if (!ev.isCancelled()) aimed.setType(Material.AIR, true);
            return;
        }
        String name = name(e.getBlock());
        if (name == null || isPlant(name)) { clearSpeed(e.getPlayer()); return; }
        ItemStack tool = e.getItemInHand();
        speed(e.getPlayer(), progress(like(name).createBlockData(), tool) / progress(e.getBlock().getBlockData(), tool));
        if (e.getInstaBreak() && progress(like(name).createBlockData(), tool) < 1) e.setCancelled(true); // the first hit came before aim()
    }

    private static void clearSpeed(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a != null && a.getModifier(FAST) != null) a.removeModifier(FAST);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        String name = name(b);
        if (name == null) {
            Bukkit.getScheduler().runTask(pl, () -> supports(b));
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        sound(b, name, false);
        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        List<ItemStack> out = e.getPlayer().getGameMode() == GameMode.CREATIVE ? List.of() : drops(b, name, tool);
        removed(b, name);
        Bukkit.getScheduler().runTask(pl, () -> {
            out.forEach(it -> b.getWorld().dropItemNaturally(b.getLocation().add(.5, .5, .5), it));
            supports(b);
        });
    }

    /** What a custom block drops when broken with a tool (Cobblemon's loot tables). */
    List<ItemStack> drops(Block b, String name, ItemStack tool) {
        if (isPlant(name)) return plants.drops(b, name, tool);
        String base = base(name);
        List<ItemStack> out = new ArrayList<>();
        var r = ThreadLocalRandom.current();
        boolean silk = tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH);
        if (base.endsWith("_ore")) {
            if (!like(name).createBlockData().isPreferredTool(tool)) return out; // a pickaxe or nothing
            if (silk) add(out, item(base, 1));
            else { // one stone, minecraft:ore_drops with Fortune
                int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE), bonus = r.nextInt(fortune + 2) - 1;
                add(out, pl.items == null ? null : pl.items.create(drops.get(base), 1 + Math.max(0, bonus)));
            }
        } else if (base.equals("apricorn_leaves")) {
            if (silk || tool != null && tool.getType() == Material.SHEARS) add(out, item(base, 1));
            else if (r.nextDouble() < new double[]{0.02, 0.022222223, 0.025, 0.033333335, 0.1}[Math.min(4, tool == null ? 0 : tool.getEnchantmentLevel(Enchantment.FORTUNE))])
                out.add(new ItemStack(Material.STICK, 1 + r.nextInt(2)));
        } else if (base.endsWith("_stone_block")) {
            if (like(name).createBlockData().isPreferredTool(tool)) add(out, item(base, 1));
        } else add(out, item(base, 1)); // wood
        return out;
    }

    private static void add(List<ItemStack> out, ItemStack it) { if (it != null) out.add(it); }

    /** A custom block is gone: its plant data and models; the leaves around a log or leaf may now decay. */
    void removed(Block b, String name) {
        if (isPlant(name)) plants.remove(b);
        String base = base(name);
        if (base.contains("apricorn_log") || base.contains("apricorn_wood") || base.equals("apricorn_leaves")) plants.leavesMayDecay(b);
        if (base.equals("apricorn_leaves")) plants.setPersistent(b, false);
    }

    /** After a block went: the plants it held (a bush on top, apricorns on its sides) go with it. */
    void supports(Block gone) {
        plants.checkSupport(gone.getRelative(BlockFace.UP));
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) plants.checkSupport(gone.getRelative(f));
    }

    /** Breaks a custom block as the world would: its drops (each kept with this chance), its sound. */
    void breakNaturally(Block b, String name, ItemStack tool, double chance) {
        List<ItemStack> out = drops(b, name, tool);
        sound(b, name, false);
        removed(b, name);
        b.setType(Material.AIR, true);
        for (ItemStack it : out) if (ThreadLocalRandom.current().nextDouble() < chance) b.getWorld().dropItemNaturally(b.getLocation().add(.5, .5, .5), it);
        supports(b);
    }

    // ---- creative pick-block: the Cobblemon item ----

    @EventHandler(ignoreCancelled = true)
    void onPick(PlayerPickBlockEvent e) {
        String name = name(e.getBlock());
        if (name == null) return;
        e.setCancelled(true);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) return;
        String id = isPlant(name) ? plants.seedOf(e.getBlock(), name) : base(name);
        ItemStack it = id == null ? null : pl.items != null ? pl.items.create(id, 1) : item(id, 1);
        if (it != null) e.getPlayer().getInventory().setItemInMainHand(it);
    }

    // ---- the world: explosions, pistons, water, endermen ----

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onExplode(EntityExplodeEvent e) { explode(e.blockList(), e.getYield()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onExplode(BlockExplodeEvent e) { explode(e.blockList(), e.getYield()); }

    private void explode(List<Block> blocks, float yield) {
        for (var it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            String name = name(b);
            if (name != null) {
                it.remove();
                Bukkit.getScheduler().runTask(pl, () -> { if (name.equals(name(b))) breakNaturally(b, name, null, yield); });
            } else Bukkit.getScheduler().runTask(pl, () -> supports(b));
        }
    }

    /** Pistons push cubes as they are (their state is what they are); a plant in the way breaks, as a crop does. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onPiston(BlockPistonExtendEvent e) { pistoned(e.getBlocks(), e.getDirection()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    void onPiston(BlockPistonRetractEvent e) { pistoned(e.getBlocks(), e.getDirection()); }

    private void pistoned(List<Block> blocks, BlockFace dir) {
        List<Block> moved = new ArrayList<>();
        for (Block b : blocks) {
            String name = name(b);
            if (name != null && isPlant(name)) breakNaturally(b, name, null, 1);
            else {
                if (name != null && name.equals("apricorn_leaves")) plants.setPersistent(b, false);
                moved.add(b.getRelative(dir));
            }
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            for (Block b : blocks) supports(b);
            for (Block to : moved) {
                vanilla.mushroomMoved(to);
                if ("apricorn_leaves".equals(name(to))) plants.leavesMayDecay(to);
            }
        }, 3);
    }

    @EventHandler(ignoreCancelled = true)
    void onFlow(BlockFromToEvent e) {
        String name = name(e.getToBlock());
        if (name != null && isPlant(name)) {
            e.setCancelled(true);
            breakNaturally(e.getToBlock(), name, null, 1); // washed away, as crops are
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onEntityChange(EntityChangeBlockEvent e) {
        if (name(e.getBlock()) != null) e.setCancelled(true); // endermen, ravagers
    }

    // ---- world generation: Cobblemon's ores, trees, berry groves and mints in new chunks ----

    @EventHandler
    void onWorld(WorldInitEvent e) { addPopulator(e.getWorld()); }

    private void addPopulator(World w) {
        if (w.getPopulators().stream().noneMatch(p -> p instanceof Generator)) w.getPopulators().add(new Generator());
    }

    private final class Generator extends BlockPopulator {
        @Override
        public void populate(WorldInfo info, Random r, int cx, int cz, LimitedRegion region) {
            for (Vein v : veins) {
                for (int i = 0; i < v.count; i++) {
                    int x = (cx << 4) + r.nextInt(16), z = (cz << 4) + r.nextInt(16);
                    int span = v.max - v.min;
                    int y = v.trapezoid ? v.min + r.nextInt(span / 2 + 1) + r.nextInt(span - span / 2 + 1) : v.min + r.nextInt(span + 1);
                    if (y < info.getMinHeight() || y >= info.getMaxHeight() || !region.isInRegion(x, y, z)) continue;
                    if (!v.biomes.isEmpty() && !v.biomes.contains(region.getBiome(x, y, z).key())) continue;
                    vein(v, r, x, y, z, region);
                }
            }
            plants.populate(info, r, cx, cz, region);
        }

        /** ponytail: a random walk of `size` steps, close to vanilla's OreFeature blob for its small sizes (3-5). */
        private void vein(Vein v, Random r, int x, int y, int z, LimitedRegion region) {
            for (int k = 0; k < v.size; k++) {
                if (region.isInRegion(x, y, z)) {
                    String ore = target(v, region.getType(x, y, z));
                    BlockData d = ore == null ? null : byName.get(ore);
                    if (d != null) region.setBlockData(x, y, z, d);
                }
                x += r.nextInt(3) - 1; y += r.nextInt(3) - 1; z += r.nextInt(3) - 1;
            }
        }
    }

    /** Which of the vein's ores replaces this block (Cobblemon's targets), or null. */
    static String target(Vein v, Material m) {
        for (var e : v.ores.entrySet()) {
            boolean hit = switch (e.getKey()) {
                case "minecraft:stone_ore_replaceables" -> Tag.STONE_ORE_REPLACEABLES.isTagged(m);
                case "minecraft:deepslate_ore_replaceables" -> Tag.DEEPSLATE_ORE_REPLACEABLES.isTagged(m);
                case "cobblemon:dripstone_replaceables" -> m == Material.DRIPSTONE_BLOCK;
                case "minecraft:netherrack", "?" -> m == Material.NETHERRACK;
                case "minecraft:terracotta" -> Tag.TERRACOTTA.isTagged(m);
                default -> false;
            };
            if (hit) return e.getValue();
        }
        return null;
    }

    /** A new chunk: its structures' markers become berry bushes, their chests gilded chests (build_structures.py). */
    @EventHandler
    void onChunk(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        Chunk c = e.getChunk();
        var snap = c.getChunkSnapshot(false, false, false);
        if (byName.entrySet().stream().anyMatch(x -> x.getKey().startsWith("marker/") && snap.contains(x.getValue()))) {
            World w = c.getWorld();
            for (int y = w.getMinHeight(); y < w.getMaxHeight(); y++)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        String n = name(snap.getBlockData(x, y, z));
                        if (n != null && n.startsWith("marker/"))
                            plants.plantBerry(c.getBlock(x, y, z), n.substring(7), 3 + ThreadLocalRandom.current().nextInt(3));
                    }
        }
        for (BlockState s : c.getTileEntities()) {
            if (!(s instanceof org.bukkit.block.Chest chest)) continue;
            String g = chest.getPersistentDataContainer().get(gildedKey, PersistentDataType.STRING);
            if (g == null || pl.machines == null) continue;
            String[] kind = g.split("\\|", 2);
            String type = kind[0].equals("gimmighoul_chest") ? "gilded_chest" : kind[0];
            float yaw = switch (((org.bukkit.block.data.Directional) s.getBlockData()).getFacing()) {
                case EAST -> 270;
                case NORTH -> 180;
                case WEST -> 90;
                default -> 0;
            };
            chest.getInventory().clear();
            pl.machines.placeGilded(s.getBlock(), type, yaw, roll(kind.length > 1 ? kind[1] : ""));
        }
    }

    /** A Cobblemon loot table rolled (pools, uniform or constant rolls, weighted item entries, set_count). */
    List<ItemStack> roll(String table) {
        List<ItemStack> out = new ArrayList<>();
        JsonObject t = loot.get(table);
        if (t == null) return out;
        var r = ThreadLocalRandom.current();
        for (var pe : t.getAsJsonArray("pools")) {
            JsonObject pool = pe.getAsJsonObject();
            int rolls = number(pool.get("rolls"), r);
            var entries = pool.getAsJsonArray("entries");
            int total = 0;
            for (var en : entries) total += weight(en.getAsJsonObject());
            for (int i = 0; i < rolls && total > 0; i++) {
                int pick = r.nextInt(total);
                for (var en : entries) {
                    JsonObject entry = en.getAsJsonObject();
                    pick -= weight(entry);
                    if (pick >= 0) continue;
                    if (!entry.has("name")) break;
                    int count = 1;
                    if (entry.has("functions")) for (var f : entry.getAsJsonArray("functions"))
                        if (f.getAsJsonObject().get("function").getAsString().endsWith("set_count")) count = number(f.getAsJsonObject().get("count"), r);
                    String id = entry.get("name").getAsString();
                    Material m = id.startsWith("cobblemon:") ? null : Material.matchMaterial(id);
                    ItemStack it = id.startsWith("cobblemon:") ? (pl.items == null ? null : pl.items.create(id.substring(10), count))
                            : m == null ? null : new ItemStack(m, count);
                    if (it != null && count > 0) out.add(it);
                    break;
                }
            }
        }
        return out;
    }

    private static int weight(JsonObject entry) { return entry.has("weight") ? entry.get("weight").getAsInt() : 1; }

    static int number(JsonElement n, Random r) {
        if (n == null) return 1;
        if (n.isJsonPrimitive()) return (int) n.getAsDouble();
        JsonObject o = n.getAsJsonObject();
        if (o.has("min")) {
            int lo = (int) Math.floor(o.get("min").getAsDouble()), hi = (int) Math.floor(o.get("max").getAsDouble());
            return lo + r.nextInt(Math.max(1, hi - lo + 1));
        }
        return o.has("value") ? o.get("value").getAsInt() : 1;
    }

    /** Admin check: how many of each custom block the chunks around a spot hold, and the nearest one (whose name has `only`). */
    String scan(Location at, int radius, String only) {
        Map<String, Integer> n = new java.util.TreeMap<>();
        Block near = null;
        World w = at.getWorld();
        for (int cx = (at.getBlockX() >> 4) - radius; cx <= (at.getBlockX() >> 4) + radius; cx++)
            for (int cz = (at.getBlockZ() >> 4) - radius; cz <= (at.getBlockZ() >> 4) + radius; cz++) {
                var snap = w.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                for (int y = w.getMinHeight(); y < w.getMaxHeight(); y++)
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++) {
                            String name = name(snap.getBlockData(x, y, z));
                            if (name == null) continue;
                            n.merge(base(name), 1, Integer::sum);
                            if (only != null && !name.contains(only)) continue;
                            Block b = w.getBlockAt((cx << 4) + x, y, (cz << 4) + z);
                            if (near == null || b.getLocation().distanceSquared(at) < near.getLocation().distanceSquared(at)) near = b;
                        }
            }
        return n + (near == null ? "" : " nearest: " + name(near) + " at " + near.getX() + " " + near.getY() + " " + near.getZ());
    }
}
