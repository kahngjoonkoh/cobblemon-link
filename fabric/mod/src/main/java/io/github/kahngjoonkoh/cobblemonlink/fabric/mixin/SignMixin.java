package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Signs;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A sign we showed only to ask for text (Signs): its answer is ours, not a sign edit. */
@Mixin(ServerGamePacketListenerImpl.class)
public class SignMixin {
    @Inject(method = "handleSignUpdate", at = @At("HEAD"), cancellable = true)
    private void cobblemonlink$sign(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        if (Signs.answered(((ServerGamePacketListenerImpl) (Object) this).player, packet.getPos(), packet.getLines())) ci.cancel();
    }
}
