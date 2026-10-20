package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.kahngjoonkoh.cobblemonlink.fabric.Battles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player without the mod right-clicks or hits a Pokémon: a battle (Battles.interact) instead of Cobblemon's R key. */
@Mixin(Player.class)
public class InteractMixin {
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void cobblemonlink$use(Entity e, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND && (Object) this instanceof ServerPlayer p && e instanceof PokemonEntity pe && Battles.interact(p, pe))
            cir.setReturnValue(InteractionResult.SUCCESS);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void cobblemonlink$hit(Entity e, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer p && e instanceof PokemonEntity pe && Battles.interact(p, pe)) ci.cancel();
    }
}
