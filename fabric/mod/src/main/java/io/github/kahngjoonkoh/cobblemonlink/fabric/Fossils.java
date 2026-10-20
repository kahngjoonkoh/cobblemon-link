package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.block.entity.FossilMultiblockEntity;
import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What Cobblemon's client draws in its fossil machine (RestorationTankRenderer, FossilAnalyzerRenderer), from the
 * server's state: in the tank, an embryo growing through its three stages and then the fossil's own fetus
 * (tools/npc_models.py: "fossil_<geometry>"); on the analyzer, the fossils put in. Furniture draws them.
 */
final class Fossils {
    private Fossils() {}

    /** A model in the tank and its size. */
    record Look(String model, float scale) {}

    /** What grows in the tank whose lower half is at pos; null for nothing. */
    static Look tank(Level level, BlockPos pos) {
        FossilMultiblockStructure s = structure(level, pos);
        if (s == null || !pos.equals(s.getTankBasePos())) return null;
        var fossil = s.getResultingFossil() != null ? s.getResultingFossil()
                : s.getTimeRemaining() > 0 ? com.cobblemon.mod.common.api.fossil.Fossils.INSTANCE.getFossilByItemStacks(s.getFossilInventory()) : null;
        if (fossil == null) return null;
        float done = s.getTimeRemaining() <= 0 ? 1 : Math.max(0, 1 - (float) s.getTimeRemaining() / FossilMultiblockStructure.TIME_TO_TAKE);
        String fetus = "fossil_" + fossil.getIdentifier().getPath() + "_fetus";
        return look(done, Models.has(fetus) ? fetus : "fossil_substitute_fetus");
    }

    /** The stage at progress done (0..1): three embryo stages, then the fetus, each growing a little. */
    static Look look(float done, String fetus) {
        int stage = Math.min(3, (int) (done * 4));
        float within = done * 4 - stage; // 0..1 through this stage
        String model = stage < 3 ? "fossil_embryo_stage" + (stage + 1) : fetus;
        return new Look(model, 0.5f + 0.3f * Math.min(1, within)); // ponytail: a straight growth, not Cobblemon's curves
    }

    /** The fossils put into the analyzer at pos (none for other blocks). */
    static List<ItemStack> analyzer(Level level, BlockPos pos) {
        FossilMultiblockStructure s = structure(level, pos);
        return s == null || !pos.equals(s.getAnalyzerPos()) ? List.of() : s.getFossilInventory();
    }

    private static FossilMultiblockStructure structure(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FossilMultiblockEntity e && e.getMultiblockStructure() instanceof FossilMultiblockStructure s ? s : null;
    }
}
