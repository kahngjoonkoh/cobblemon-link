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
        for (String id : Furniture.BLOCKS)
            registry.registerBlockPoly(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblemon", id)),
                    new SimpleReplacementPoly(id.equals("campfire") ? Blocks.CAMPFIRE : id.equals("soul_campfire") ? Blocks.SOUL_CAMPFIRE : Blocks.BARRIER));
    }
}
