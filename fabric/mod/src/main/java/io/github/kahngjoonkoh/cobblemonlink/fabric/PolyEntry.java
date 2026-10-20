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
        for (String id : Furniture.blocks())
            registry.registerBlockPoly(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblemon", id)),
                    new SimpleReplacementPoly(id.equals("campfire") ? Blocks.CAMPFIRE : id.equals("soul_campfire") ? Blocks.SOUL_CAMPFIRE
                            : Furniture.plant(id) ? Blocks.STRUCTURE_VOID : Blocks.BARRIER));
        // Cobblemon's signs and chests as Minecraft's (same properties: facing, rotation...): signs keep their text
        // (SendMixin hands their block-entity data over as Minecraft's sign's), chests open as chests (Menus)
        for (var k : BuiltInRegistries.BLOCK.keySet()) {
            if (!k.getNamespace().equals("cobblemon")) continue;
            String id = k.getPath();
            net.minecraft.world.level.block.Block to = id.endsWith("gilded_chest") || id.equals("gimmighoul_chest") ? Blocks.CHEST
                    : id.endsWith("_wall_hanging_sign") ? (id.startsWith("saccharine") ? Blocks.CHERRY_WALL_HANGING_SIGN : Blocks.JUNGLE_WALL_HANGING_SIGN)
                    : id.endsWith("_hanging_sign") ? (id.startsWith("saccharine") ? Blocks.CHERRY_HANGING_SIGN : Blocks.JUNGLE_HANGING_SIGN)
                    : id.endsWith("_wall_sign") ? (id.startsWith("saccharine") ? Blocks.CHERRY_WALL_SIGN : Blocks.JUNGLE_WALL_SIGN)
                    : id.endsWith("_sign") ? (id.startsWith("saccharine") ? Blocks.CHERRY_SIGN : Blocks.JUNGLE_SIGN) : null;
            if (to != null) registry.registerBlockPoly(BuiltInRegistries.BLOCK.get(k), new SameState(to));
        }
        // a thrown Poké Ball keeps its own id (not PolyMc's flying item stand-in), so we can draw it: Java's display
        // model, Bedrock's GeyserModelEngine ball (Mons.ball)
        registry.registerEntityPoly(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "empty_pokeball")),
                new io.github.theepicblock.polymc.impl.poly.entity.DefaultedEntityPoly<>(net.minecraft.world.entity.EntityType.ARMOR_STAND));
    }

    /** A stand-in block taking over every property the two share (facing, rotation, waterlogged...). */
    record SameState(net.minecraft.world.level.block.Block to) implements io.github.theepicblock.polymc.api.block.BlockPoly {
        @Override public net.minecraft.world.level.block.state.BlockState getClientBlock(net.minecraft.world.level.block.state.BlockState s) { return to.withPropertiesOf(s); }

        @Override public String getDebugInfo(net.minecraft.world.level.block.Block b) { return "same state as " + to; }
    }
}
