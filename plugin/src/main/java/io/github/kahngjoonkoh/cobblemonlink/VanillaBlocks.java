package io.github.kahngjoonkoh.cobblemonlink;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.block.data.type.TripwireHook;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real mushroom blocks and string keep working as vanilla while some of their states are Cobblemon's blocks. Paper's
 * block-updates.disable-mushroom-block-updates and disable-tripwire-updates stop the game changing those states (so
 * it can't change ours); this class does that work for the real ones, the way vanilla's HugeMushroomBlock and
 * TripWireBlock do it. Tripwire hooks are vanilla's own: they're told to look again (Block.tick) when their string
 * changes, and they work out their power and attachment themselves; this class writes the string's part back.
 */
final class VanillaBlocks implements Listener {
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final BlockFace[] ALL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    private final CustomBlocks cb;
    /** Attached real string that entities can set off, per world (position keys). */
    private final Map<World, Set<Long>> armed = new HashMap<>();
    /** When a powered string looks for entities again (game tick). */
    private final Map<Block, Long> recheck = new HashMap<>();

    VanillaBlocks(CustomBlocks cb) {
        this.cb = cb;
        for (World w : Bukkit.getWorlds()) for (Chunk c : w.getLoadedChunks()) scanArmed(c);
        Bukkit.getScheduler().runTaskTimer(cb.pl, this::tick, 1, 1);
    }

    // ---- mushroom blocks (HugeMushroomBlock) ----

    static boolean isMushroom(Material m) {
        return m == Material.BROWN_MUSHROOM_BLOCK || m == Material.RED_MUSHROOM_BLOCK || m == Material.MUSHROOM_STEM;
    }

    /** A real mushroom block (not one of ours). */
    private boolean real(Block b, Material type) { return b.getType() == type && cb.name(b) == null; }

    /**
     * The faces a state can't have: ours are the states showing the bottom face but not all six. A real block that
     * would have one keeps its bottom face hidden instead (the face that's usually against the ground).
     */
    static void coerce(MultipleFacing d) {
        if (d.hasFace(BlockFace.DOWN) && d.getFaces().size() < 6) d.setFace(BlockFace.DOWN, false);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (cb.name(b) != null) return;
        Material t = b.getType();
        if (isMushroom(t)) {
            // getStateForPlacement: a face shows unless the same block is beside it; and updateShape for the neighbours
            MultipleFacing d = (MultipleFacing) b.getBlockData();
            for (BlockFace f : ALL) d.setFace(f, !real(b.getRelative(f), t));
            coerce(d);
            b.setBlockData(d, true);
            for (BlockFace f : ALL) hide(b.getRelative(f), f.getOppositeFace(), t);
        } else if (t == Material.TRIPWIRE) {
            Tripwire d = (Tripwire) b.getBlockData();
            for (BlockFace f : SIDES) d.setFace(f, connects(b.getRelative(f), f));
            b.setBlockData(d, true);
            for (BlockFace f : SIDES) connect(b.getRelative(f), f.getOppositeFace());
            Bukkit.getScheduler().runTask(cb.pl, () -> { if (b.getType() == Material.TRIPWIRE) source(b); });
        } else if (t == Material.TRIPWIRE_HOOK) {
            for (BlockFace f : SIDES) connect(b.getRelative(f), f.getOppositeFace());
            Bukkit.getScheduler().runTask(cb.pl, () -> sync(b));
        }
    }

    /** A real mushroom block of this type beside one: its face toward it stops showing. */
    private void hide(Block n, BlockFace toward, Material t) {
        if (!real(n, t)) return;
        MultipleFacing d = (MultipleFacing) n.getBlockData();
        if (!d.hasFace(toward)) return;
        d.setFace(toward, false);
        coerce(d);
        n.setBlockData(d, true);
    }

    /** After a piston moved a block here: a real mushroom block and its new neighbours hide the faces between them. */
    void mushroomMoved(Block b) {
        Material t = b.getType();
        if (!isMushroom(t) || cb.name(b) != null) return;
        for (BlockFace f : ALL) if (real(b.getRelative(f), t)) {
            hide(b, f, t);
            hide(b.getRelative(f), f.getOppositeFace(), t);
        }
    }

    // ---- string (TripWireBlock) ----

    private boolean realString(Block b) { return b.getType() == Material.TRIPWIRE && cb.name(b) == null; }

    /** shouldConnectTo: real string, or a hook facing it. */
    private boolean connects(Block n, BlockFace dir) {
        if (n.getType() == Material.TRIPWIRE_HOOK) return ((TripwireHook) n.getBlockData()).getFacing() == dir.getOppositeFace();
        return realString(n);
    }

    /** updateShape: a real string's connection toward a neighbour that changed. */
    private void connect(Block s, BlockFace toward) {
        if (!realString(s)) return;
        Tripwire d = (Tripwire) s.getBlockData();
        boolean c = connects(s.getRelative(toward), toward);
        if (d.hasFace(toward) == c) return;
        d.setFace(toward, c);
        s.setBlockData(d, true);
    }

    /** updateSource: the hooks south and west along the string work out their state again. */
    private List<Block> source(Block s) {
        List<Block> hooks = new ArrayList<>();
        for (BlockFace dir : new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST}) {
            for (int i = 1; i < 42; i++) {
                Block x = s.getRelative(dir, i);
                if (x.getType() == Material.TRIPWIRE_HOOK) {
                    if (((TripwireHook) x.getBlockData()).getFacing() == dir.getOppositeFace()) {
                        x.tick(); // TripWireHookBlock.calculateState, vanilla's own
                        hooks.add(x);
                        Bukkit.getScheduler().runTask(cb.pl, () -> sync(x));
                    }
                    break;
                }
                if (x.getType() != Material.TRIPWIRE) break;
            }
        }
        return hooks;
    }

    /** The hook's line: each real string on it attached as the hook is (vanilla's hook writes this; Paper's switch stops it). */
    void sync(Block hook) {
        if (hook.getType() != Material.TRIPWIRE_HOOK) return;
        TripwireHook h = (TripwireHook) hook.getBlockData();
        BlockFace f = h.getFacing();
        List<Block> line = new ArrayList<>(); // as calculateState goes: every string up to the hook facing back
        for (int i = 1; i < 42; i++) {
            Block x = hook.getRelative(f, i);
            if (x.getType() == Material.TRIPWIRE_HOOK) break;
            if (realString(x)) line.add(x);
        }
        for (Block s : line) {
            Tripwire d = (Tripwire) s.getBlockData();
            if (d.isAttached() != h.isAttached()) {
                d.setAttached(h.isAttached());
                s.setBlockData(d, true);
            }
            if (h.isAttached()) armed(s).add(s.getBlockKey());
            else armed(s).remove(s.getBlockKey());
        }
    }

    private Set<Long> armed(Block b) { return armed.computeIfAbsent(b.getWorld(), w -> new HashSet<>()); }

    /** entityInside / checkPressed, every tick: attached string an entity is in powers, and its hooks fire. */
    private void tick() {
        for (var e : armed.entrySet()) {
            World w = e.getKey();
            for (Long k : new ArrayList<>(e.getValue())) {
                Block s = w.getBlockAt(Block.getBlockKeyX(k), Block.getBlockKeyY(k), Block.getBlockKeyZ(k));
                if (!realString(s) || !((Tripwire) s.getBlockData()).isAttached()) { e.getValue().remove(k); continue; }
                Tripwire d = (Tripwire) s.getBlockData();
                if (d.isPowered()) {
                    Long at = recheck.get(s);
                    if (at == null) recheck.put(s, (long) Bukkit.getCurrentTick() + 10);
                    else if (Bukkit.getCurrentTick() >= at) {
                        if (inside(s).isEmpty()) { recheck.remove(s); setPowered(s, false); }
                        else recheck.put(s, (long) Bukkit.getCurrentTick() + 10);
                    }
                } else {
                    List<Entity> in = inside(s);
                    if (!in.isEmpty() && allowed(s, in)) {
                        setPowered(s, true);
                        recheck.put(s, (long) Bukkit.getCurrentTick() + 10);
                    }
                }
            }
        }
    }

    private List<Entity> inside(Block s) {
        // SHAPE_ATTACHED: the full width, 1 to 2.5 pixels up
        BoundingBox box = new BoundingBox(s.getX(), s.getY() + 1 / 16.0, s.getZ(), s.getX() + 1, s.getY() + 2.5 / 16, s.getZ() + 1);
        return new ArrayList<>(s.getWorld().getNearbyEntities(box, x -> !(x instanceof Player p && p.getGameMode() == GameMode.SPECTATOR)));
    }

    /** Vanilla's events for it: a player's PHYSICAL interaction, another entity's EntityInteractEvent. */
    private static boolean allowed(Block s, List<Entity> in) {
        for (Entity x : in) {
            var ev = x instanceof Player p ? new PlayerInteractEvent(p, Action.PHYSICAL, null, s, BlockFace.SELF)
                    : new EntityInteractEvent(x, s);
            Bukkit.getPluginManager().callEvent(ev);
            if (!(ev instanceof PlayerInteractEvent pe ? pe.useInteractedBlock() == org.bukkit.event.Event.Result.DENY : ((EntityInteractEvent) ev).isCancelled())) return true;
        }
        return false;
    }

    private void setPowered(Block s, boolean on) {
        Tripwire d = (Tripwire) s.getBlockData();
        d.setPowered(on);
        s.setBlockData(d, true);
        source(s);
    }

    /**
     * affectNeighborsAfterRemoval: real string about to go. Unless shears cut it, its hooks see it powered first (they
     * fire, then let go when they look again); the strings beside it stop connecting.
     */
    private void stringGoes(Block s, boolean shears) {
        if (!realString(s)) return;
        armed(s).remove(s.getBlockKey());
        recheck.remove(s);
        if (!shears) { // shears: vanilla disarms it first, so its hooks let go without firing
            Tripwire d = (Tripwire) s.getBlockData();
            d.setPowered(true);
            s.setBlockData(d, false);
        }
        List<Block> hooks = source(s);
        List<Block> later = hooks;
        Bukkit.getScheduler().runTask(cb.pl, () -> { for (BlockFace f : SIDES) connect(s.getRelative(f), f.getOppositeFace()); });
        // vanilla's hook looks again 10 ticks after its string went (the scheduled tick calculateState asks for)
        Bukkit.getScheduler().runTaskLater(cb.pl, () -> later.forEach(h -> {
            if (h.getType() == Material.TRIPWIRE_HOOK) h.tick();
        }), 10);
        Bukkit.getScheduler().runTaskLater(cb.pl, () -> later.forEach(this::sync), 11);
    }

    /** A hook going: its line's string lets go once the other hook has worked it out (vanilla's own onRemove). */
    private void hookGoes(Block hook) {
        if (hook.getType() != Material.TRIPWIRE_HOOK) return;
        BlockFace f = ((TripwireHook) hook.getBlockData()).getFacing();
        Bukkit.getScheduler().runTask(cb.pl, () -> {
            for (BlockFace s : SIDES) connect(hook.getRelative(s), s.getOppositeFace());
            for (int i = 1; i < 42; i++) {
                Block x = hook.getRelative(f, i);
                if (x.getType() == Material.TRIPWIRE_HOOK) { sync(x); return; }
                if (realString(x)) {
                    Tripwire d = (Tripwire) x.getBlockData();
                    if (d.isAttached()) { d.setAttached(false); x.setBlockData(d, true); }
                    armed(x).remove(x.getBlockKey());
                } else if (x.getType() != Material.TRIPWIRE) return;
            }
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        var tool = e.getPlayer().getInventory().getItemInMainHand();
        if (realString(b)) stringGoes(b, tool.getType() == Material.SHEARS);
        else if (b.getType() == Material.TRIPWIRE_HOOK) hookGoes(b);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onExplode(EntityExplodeEvent e) { e.blockList().forEach(this::goes); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onExplode(BlockExplodeEvent e) { e.blockList().forEach(this::goes); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPiston(BlockPistonExtendEvent e) { e.getBlocks().forEach(this::goes); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPiston(BlockPistonRetractEvent e) { e.getBlocks().forEach(this::goes); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onFlow(BlockFromToEvent e) { goes(e.getToBlock()); }

    private void goes(Block b) {
        if (realString(b)) stringGoes(b, false);
        else if (b.getType() == Material.TRIPWIRE_HOOK) hookGoes(b);
    }

    @EventHandler
    void onChunk(ChunkLoadEvent e) { scanArmed(e.getChunk()); }

    @EventHandler
    void onUnload(ChunkUnloadEvent e) {
        Set<Long> a = armed.get(e.getWorld());
        if (a == null) return;
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        a.removeIf(k -> Block.getBlockKeyX(k) >> 4 == cx && Block.getBlockKeyZ(k) >> 4 == cz);
        recheck.keySet().removeIf(b -> b.getWorld().equals(e.getWorld()) && b.getX() >> 4 == cx && b.getZ() >> 4 == cz);
    }

    /** A chunk's attached real string (jungle temple traps, players' traps) joins the watch. */
    private void scanArmed(Chunk c) {
        var snap = c.getChunkSnapshot(false, false, false);
        boolean any = false;
        for (BlockData d : attachedStates()) if (snap.contains(d)) { any = true; break; }
        if (!any) return;
        World w = c.getWorld();
        for (int y = w.getMinHeight(); y < w.getMaxHeight(); y++)
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    if (snap.getBlockType(x, y, z) != Material.TRIPWIRE) continue;
                    BlockData d = snap.getBlockData(x, y, z);
                    if (cb.name(d) == null && ((Tripwire) d).isAttached()) armed(c.getBlock(x, y, z)).add(c.getBlock(x, y, z).getBlockKey());
                }
    }

    private List<BlockData> attached;

    /** Every attached, not disarmed string state (16 connections, powered or not). */
    private List<BlockData> attachedStates() {
        if (attached == null) {
            attached = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                Tripwire d = (Tripwire) Material.TRIPWIRE.createBlockData();
                d.setAttached(true);
                d.setPowered((i & 16) != 0);
                for (int f = 0; f < 4; f++) d.setFace(SIDES[f], (i >> f & 1) != 0);
                attached.add(d);
            }
        }
        return attached;
    }
}
