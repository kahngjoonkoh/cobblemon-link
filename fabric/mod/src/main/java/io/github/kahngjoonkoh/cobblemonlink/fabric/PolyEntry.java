package io.github.kahngjoonkoh.cobblemonlink.fabric;

import io.github.theepicblock.polymc.api.PolyMcEntrypoint;
import io.github.theepicblock.polymc.api.PolyRegistry;
import io.github.theepicblock.polymc.impl.poly.block.SimpleReplacementPoly;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/** PolyMc's stand-ins we choose: Cobblemon's machine blocks become barriers (solid, unseen), Furniture draws them; its campfires Minecraft's. */
public class PolyEntry implements PolyMcEntrypoint {
    @Override
    public void registerPolys(PolyRegistry registry) {
        // Cobblemon's cubes go on note-block states only with notes 0-15: Geyser makes one Bedrock block of every
        // note-block state we map, and Bedrock caps a property at 16 values (Geyser warned "note contains more than 16
        // values, but BDS specifies it should not"). Taking the note block's free list now (this
        // runs before PolyMc's generator) leaves 23 instruments x 16 notes x 2 for them; the rest fall to dispensers etc.
        var mgr = registry.getBlockStateManager();
        try { mgr.requestBlockState(s -> false, new net.minecraft.world.level.block.Block[]{Blocks.NOTE_BLOCK},
                io.github.theepicblock.polymc.api.block.BlockStateProfile.NOTE_BLOCK_SUB_PROFILE.onFirstRegister);
        } catch (io.github.theepicblock.polymc.api.block.BlockStateManager.StateLimitReachedException expected) {}
        mgr.getAvailableBlockStateMap().get(Blocks.NOTE_BLOCK)
                .removeIf(s -> s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NOTE) > 15);
        for (String id : Furniture.blocks())
            registry.registerBlockPoly(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblemon", id)),
                    new SimpleReplacementPoly(id.equals("campfire") ? Blocks.CAMPFIRE : id.equals("soul_campfire") ? Blocks.SOUL_CAMPFIRE
                            : Furniture.plant(id) ? Blocks.STRUCTURE_VOID : Blocks.BARRIER));
        // Cobblemon's signs and Gimmighoul's chest as Minecraft's (gilded chests are Furniture's) (same properties: facing, rotation...): signs keep their text
        // (SendMixin hands their block-entity data over as Minecraft's sign's), chests open as chests (Menus)
        for (var k : BuiltInRegistries.BLOCK.keySet()) {
            if (!k.getNamespace().equals("cobblemon")) continue;
            String id = k.getPath();
            net.minecraft.world.level.block.Block to = id.equals("gimmighoul_chest") ? Blocks.CHEST
                    : id.endsWith("_wall_hanging_sign") ? (id.startsWith("saccharine") ? Blocks.CHERRY_WALL_HANGING_SIGN : Blocks.JUNGLE_WALL_HANGING_SIGN)
                    : id.endsWith("_hanging_sign") ? (id.startsWith("saccharine") ? Blocks.CHERRY_HANGING_SIGN : Blocks.JUNGLE_HANGING_SIGN)
                    : id.endsWith("_wall_sign") ? (id.startsWith("saccharine") ? Blocks.CHERRY_WALL_SIGN : Blocks.JUNGLE_WALL_SIGN)
                    : id.endsWith("_sign") ? (id.startsWith("saccharine") ? Blocks.CHERRY_SIGN : Blocks.JUNGLE_SIGN) : null;
            if (to == null) to = lookalike(id);
            if (to != null) registry.registerBlockPoly(BuiltInRegistries.BLOCK.get(k), new SameState(to));
        }
        // a thrown Poké Ball keeps its own id (not PolyMc's flying item stand-in), so we can draw it: Java's display
        // model, Bedrock's GeyserModelEngine ball (Mons.ball)
        registry.registerEntityPoly(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "empty_pokeball")),
                new io.github.theepicblock.polymc.impl.poly.entity.DefaultedEntityPoly<>(net.minecraft.world.entity.EntityType.ARMOR_STAND));
    }

    /** Stairs, slabs, walls and fences PolyMc has no spare state for (it showed a stone cube): a vanilla block of the same
     * shape and near colour (signs map saccharine to cherry and apricorn to jungle the same way).
     * ponytail: the ones that ran out on Cobblemon 1.8.4; check poly-blocks.json for "minecraft:stone" after an upgrade. */
    static final java.util.Set<String> OUT_OF_STATES = java.util.Set.of("apricorn_fence", "black_tumblestone_brick_wall",
            "polished_black_tumblestone_wall", "polished_sky_tumblestone_wall", "polished_tumblestone_stairs", "polished_tumblestone_wall",
            "saccharine_fence", "saccharine_slab", "saccharine_stairs", "sky_tumblestone_brick_slab", "sky_tumblestone_brick_stairs",
            "sky_tumblestone_brick_wall", "smooth_black_tumblestone_slab", "smooth_black_tumblestone_stairs", "smooth_sky_tumblestone_slab",
            "smooth_sky_tumblestone_stairs", "smooth_tumblestone_slab", "smooth_tumblestone_stairs", "tumblestone_brick_slab",
            "tumblestone_brick_stairs", "tumblestone_brick_wall");

    static net.minecraft.world.level.block.Block lookalike(String id) {
        if (!OUT_OF_STATES.contains(id)) return null;
        String shape = id.substring(id.lastIndexOf('_') + 1); // stairs, slab, wall, fence
        boolean wall = shape.equals("wall");
        String mat = id.startsWith("saccharine") ? "cherry" : id.startsWith("apricorn") ? "jungle"
                : id.contains("sky_tumblestone") ? (wall ? "prismarine" : "prismarine_brick")
                : id.contains("black_tumblestone") ? "polished_deepslate" : wall ? "granite" : "cut_copper";
        return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(mat + "_" + shape));
    }

    /** A stand-in block taking over every property the two share (facing, rotation, waterlogged...). */
    record SameState(net.minecraft.world.level.block.Block to) implements io.github.theepicblock.polymc.api.block.BlockPoly {
        @Override public net.minecraft.world.level.block.state.BlockState getClientBlock(net.minecraft.world.level.block.state.BlockState s) { return to.withPropertiesOf(s); }

        @Override public String getDebugInfo(net.minecraft.world.level.block.Block b) { return "same state as " + to; }
    }
}
