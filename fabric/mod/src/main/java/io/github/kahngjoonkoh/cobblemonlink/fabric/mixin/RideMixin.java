package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Mixins;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A vanilla client's steering while it rides (WASD, jump, sneak): Cobblemon's driver input for a ridden Pokémon. */
@Mixin(ServerGamePacketListenerImpl.class)
public class RideMixin {
    @Inject(method = "handlePlayerInput", at = @At("TAIL"))
    private void cobblemonlink$input(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        Mixins.rideInput(((ServerGamePacketListenerImpl) (Object) this).player, packet.getXxa(), packet.getZza(), packet.isJumping(), packet.isShiftKeyDown());
    }
}
