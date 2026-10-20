package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Furniture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Cobblemon machine block placed in a loaded chunk: Furniture draws it. */
@Mixin(LevelChunk.class)
public class BlockMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void cobblemonlink$set(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        LevelChunk c = (LevelChunk) (Object) this;
        if (cir.getReturnValue() != null && c.getLevel() instanceof ServerLevel level && Furniture.furniture(state)) Furniture.placed(level, pos);
    }
}
