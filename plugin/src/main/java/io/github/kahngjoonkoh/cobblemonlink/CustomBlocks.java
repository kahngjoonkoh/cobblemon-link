package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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

/**
 * Cobblemon's own blocks as real blocks for players without the mod (docs/custom-blocks.md, tools/build_custom_blocks.py):
 * each is a note block or tripwire state that vanilla never shows here, drawn as Cobblemon's block by our Java pack and,
 * through Geyser's custom block mappings, on Bedrock. Paper's block-updates flags keep the world from changing those
 * states on its own; this class keeps players from doing it (real note blocks stay at harp/0, real string all-false).
 */
final class CustomBlocks implements Listener {
    static final BlockData NOTE = Bukkit.createBlockData("minecraft:note_block[instrument=harp,note=0,powered=false]");
    static final BlockData STRING = Bukkit.createBlockData("minecraft:tripwire[attached=false,disarmed=false,east=false,north=false,powered=false,south=false,west=false]");
    private static final NamespacedKey FAST = new NamespacedKey("cobblemonlink", "ore_mining");

    private final CobblemonLink pl;
    private final NamespacedKey itemKey;
    final Map<String, BlockData> byName = new HashMap<>();
    final Map<String, String> byState = new HashMap<>(); // BlockData.getAsString() -> name
    private final Map<String, String> drops = new HashMap<>();
    private final List<Vein> veins = new ArrayList<>();

    record Vein(String feature, int count, int size, int min, int max, boolean trapezoid, Set<Key> biomes, Map<String, String> ores) {}

    CustomBlocks(CobblemonLink pl, Path table) throws IOException {
        this.pl = pl;
        itemKey = new NamespacedKey(pl, "block");
        JsonObject t = JsonParser.parseString(Files.readString(table)).getAsJsonObject();
        int bad = 0;
        for (var e : t.getAsJsonObject("blocks").entrySet()) {
            try {
                BlockData d = Bukkit.createBlockData(e.getValue().getAsString());
                byName.put(e.getKey(), d);
                byState.put(d.getAsString(), e.getKey());
            } catch (IllegalArgumentException ex) {
                bad++; // a state this Minecraft doesn't have (build_custom_blocks.py's instrument list is out of date)
            }
        }
        if (bad > 0) pl.getLogger().warning(bad + " custom block states don't exist in this Minecraft version");
        t.getAsJsonObject("drops").entrySet().forEach(e -> drops.put(e.getKey(), e.getValue().getAsString()));
        var biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        for (var el : t.getAsJsonArray("veins")) {
            JsonObject v = el.getAsJsonObject();
            TagKey<Biome> tag = TagKey.create(RegistryKey.BIOME, Key.key(v.get("biomes").getAsString()));
            Set<Key> in = new HashSet<>();
            if (biomes.hasTag(tag)) biomes.getTagValues(tag).forEach(b -> in.add(b.key()));
            Map<String, String> ores = new HashMap<>();
            v.getAsJsonObject("ores").entrySet().forEach(e -> ores.put(e.getKey(), e.getValue().getAsString()));
            veins.add(new Vein(v.get("feature").getAsString(), v.get("count").getAsInt(), v.get("size").getAsInt(),
                    v.get("min").getAsInt(), v.get("max").getAsInt(), v.get("shape").getAsString().equals("trapezoid"), in, ores));
        }
        Bukkit.getWorlds().forEach(this::addPopulator);
        pl.getServer().getPluginManager().registerEvents(this, pl);
        pl.getLogger().info(byName.size() + " custom block states, " + veins.size() + " ore features");
    }

    /** The custom block a block is, or null. */
    String name(Block b) {
        Material m = b.getType();
        return m == Material.NOTE_BLOCK || m == Material.TRIPWIRE ? byState.get(b.getBlockData().getAsString()) : null;
    }

    /** A placeable ore (shown as the block in hand; Java pack's items/<ore>.json). */
    ItemStack item(String name, int amount) {
        if (!byName.containsKey(name) || name.contains("[")) return null;
        ItemStack it = new ItemStack(Material.NOTE_BLOCK, amount);
        it.editMeta(m -> {
            m.customName(Component.translatable("block.cobblemon." + name).decoration(TextDecoration.ITALIC, false));
            m.setItemModel(new NamespacedKey("cobblemon", name));
            m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, name);
        });
        return it;
    }

    /** Admin check: how many of each custom block the chunks around a spot hold, and the nearest one. */
    String scan(org.bukkit.Location at, int radius) {
        Map<String, Integer> n = new java.util.TreeMap<>();
        Block near = null;
        World w = at.getWorld();
        for (int cx = (at.getBlockX() >> 4) - radius; cx <= (at.getBlockX() >> 4) + radius; cx++)
            for (int cz = (at.getBlockZ() >> 4) - radius; cz <= (at.getBlockZ() >> 4) + radius; cz++) {
                var snap = w.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                for (int y = w.getMinHeight(); y < w.getMaxHeight(); y++)
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++) {
                            Material m = snap.getBlockType(x, y, z);
                            if (m != Material.NOTE_BLOCK && m != Material.TRIPWIRE) continue;
                            String name = byState.get(snap.getBlockData(x, y, z).getAsString());
                            if (name == null) continue;
                            n.merge(name.replaceAll("\\[.*", ""), 1, Integer::sum);
                            Block b = w.getBlockAt((cx << 4) + x, y, (cz << 4) + z);
                            if (near == null || b.getLocation().distanceSquared(at) < near.getLocation().distanceSquared(at)) near = b;
                        }
            }
        return n + (near == null ? "" : " nearest: " + name(near) + " at " + near.getX() + " " + near.getY() + " " + near.getZ());
    }

    // ---- keeping the states still ----

    @EventHandler(ignoreCancelled = true)
    void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        String name = e.getItemInHand().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (name != null && byName.containsKey(name)) b.setBlockData(byName.get(name), false);
        else if (b.getType() == Material.NOTE_BLOCK) b.setBlockData(NOTE, false); // a real note block
        else if (b.getType() == Material.TRIPWIRE) b.setBlockData(STRING, false); // real string
    }

    @EventHandler(priority = EventPriority.LOW)
    void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.getAction() == Action.PHYSICAL && b.getType() == Material.TRIPWIRE) { e.setCancelled(true); return; } // no powered=true
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        String name = name(b);
        Player p = e.getPlayer();
        if (b.getType() == Material.NOTE_BLOCK && !p.isSneaking()) {
            // ponytail: real note blocks can't be tuned (a new note is a custom block's state); a per-block note in
            // the chunk's PDC would bring tuning back
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            if (name == null) b.getWorld().playSound(b.getLocation().add(.5, .5, .5), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HARP, 1, 1);
            return;
        }
        if (name != null && name.contains("_apricorn[")) { pick(e, b, name); return; }
        // planting: an apricorn on the side of leaves (Cobblemon grows them on apricorn trees; leaves stand in here)
        String held = pl.items == null ? null : pl.items.id(e.getItem());
        BlockFace f = e.getBlockFace();
        if (held != null && held.endsWith("_apricorn") && Tag.LEAVES.isTagged(b.getType()) && f.getModY() == 0) {
            Block at = b.getRelative(f);
            BlockData d = byName.get(held + "[age=0,facing=" + f.getOppositeFace().name().toLowerCase() + "]");
            if (at.getType().isAir() && d != null) {
                at.setBlockData(d, false);
                if (p.getGameMode() != GameMode.CREATIVE) e.getItem().subtract();
                at.getWorld().playSound(at.getLocation(), org.bukkit.Sound.BLOCK_CROP_BREAK, 1, 1);
                e.setCancelled(true);
            }
        }
    }

    /** Right-click a ripe apricorn to pick it (back to age 0); bone meal grows it a stage, as Cobblemon's. */
    private void pick(PlayerInteractEvent e, Block b, String name) {
        e.setCancelled(true);
        int age = name.charAt(name.indexOf("age=") + 4) - '0';
        String colour = name.substring(0, name.indexOf("_apricorn"));
        String facing = name.substring(name.indexOf("facing=") + 7, name.length() - 1);
        ItemStack hand = e.getItem();
        if (hand != null && hand.getType() == Material.BONE_MEAL && age < 3) {
            b.setBlockData(byName.get(colour + "_apricorn[age=" + (age + 1) + ",facing=" + facing + "]"), false);
            if (e.getPlayer().getGameMode() != GameMode.CREATIVE) hand.subtract();
            b.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, b.getLocation().add(.5, .5, .5), 8, .3, .3, .3);
        } else if (age == 3) {
            b.setBlockData(byName.get(colour + "_apricorn[age=0,facing=" + facing + "]"), false);
            dropApricorn(b, colour);
        }
    }

    private void dropApricorn(Block b, String colour) {
        ItemStack it = pl.items == null ? null : pl.items.create(colour + "_apricorn", 1);
        if (it != null) b.getWorld().dropItemNaturally(b.getLocation().add(.5, .5, .5), it);
        b.getWorld().playSound(b.getLocation(), org.bukkit.Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1, 1);
    }

    @EventHandler(ignoreCancelled = true)
    void onNote(NotePlayEvent e) { if (name(e.getBlock()) != null) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    void onEntityStep(EntityInteractEvent e) { if (e.getBlock().getType() == Material.TRIPWIRE) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    void onFlow(BlockFromToEvent e) { if (name(e.getToBlock()) != null) e.setCancelled(true); } // water would wash string away

    // ---- breaking: an ore's own mining time and drops ----

    /** Vanilla's ore this one mines like: its hardness, its tool. */
    private static Material like(String ore) {
        return ore.startsWith("deepslate_") ? Material.DEEPSLATE_IRON_ORE : ore.startsWith("nether_") ? Material.NETHER_QUARTZ_ORE : Material.IRON_ORE;
    }

    /** Fraction of a block broken a tick, vanilla's formula without the player's own factors (they cancel out). */
    static double progress(BlockData d, ItemStack tool) {
        float hard = d.getMaterial().getHardness();
        if (hard <= 0) return 1;
        boolean harvest = !d.requiresCorrectToolForDrops() || d.isPreferredTool(tool);
        return d.getDestroySpeed(tool, true) / hard / (harvest ? 30 : 100);
    }

    @EventHandler(ignoreCancelled = true)
    void onDamage(BlockDamageEvent e) {
        String name = name(e.getBlock());
        clearSpeed(e.getPlayer());
        if (name == null || name.contains("[")) return;
        // the client times the break from the note block's (0.8, axe); this attribute makes it the ore's
        ItemStack tool = e.getItemInHand();
        double m = progress(like(name).createBlockData(), tool) / progress(NOTE, tool);
        AttributeInstance a = e.getPlayer().getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a != null) a.addTransientModifier(new AttributeModifier(FAST, m - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    @EventHandler
    void onAbort(BlockDamageAbortEvent e) { clearSpeed(e.getPlayer()); }

    private static void clearSpeed(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a != null) a.removeModifier(FAST);
    }

    @EventHandler(ignoreCancelled = true)
    void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        String name = name(b);
        clearSpeed(e.getPlayer());
        if (name == null) return;
        e.setDropItems(false);
        e.setExpToDrop(0);
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (name.contains("_apricorn[")) {
            if (name.contains("age=3")) dropApricorn(b, name.substring(0, name.indexOf("_apricorn")));
            return;
        }
        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        if (!like(name).createBlockData().isPreferredTool(tool)) return; // Cobblemon: a pickaxe or nothing
        ItemStack drop;
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) drop = item(name, 1);
        else { // Cobblemon's loot table: one stone, minecraft:ore_drops with Fortune
            int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE), bonus = new Random().nextInt(fortune + 2) - 1;
            drop = pl.items == null ? null : pl.items.create(drops.get(name), 1 + Math.max(0, bonus));
        }
        if (drop != null) b.getWorld().dropItemNaturally(b.getLocation().add(.5, .5, .5), drop);
    }

    // ---- world generation: Cobblemon's ore features, in new chunks ----

    @EventHandler
    void onWorld(WorldInitEvent e) { addPopulator(e.getWorld()); }

    private void addPopulator(World w) {
        if (w.getPopulators().stream().noneMatch(p -> p instanceof Ores)) w.getPopulators().add(new Ores());
    }

    private final class Ores extends BlockPopulator {
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
                case "minecraft:base_stone_nether", "minecraft:netherrack" -> m == Material.NETHERRACK;
                default -> m.name().endsWith("TERRACOTTA") && e.getKey().contains("terracotta");
            };
            if (hit) return e.getValue();
        }
        return null;
    }
}
