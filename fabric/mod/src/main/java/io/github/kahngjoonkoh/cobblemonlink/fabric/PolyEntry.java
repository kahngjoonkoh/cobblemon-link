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
        // a thrown Poké Ball keeps its own id (not PolyMc's flying item stand-in), so we can draw it: Java's display
        // model, Bedrock's GeyserModelEngine ball (Mons.ball)
        registry.registerEntityPoly(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "empty_pokeball")),
                new io.github.theepicblock.polymc.impl.poly.entity.DefaultedEntityPoly<>(net.minecraft.world.entity.EntityType.ARMOR_STAND));
    }
}
