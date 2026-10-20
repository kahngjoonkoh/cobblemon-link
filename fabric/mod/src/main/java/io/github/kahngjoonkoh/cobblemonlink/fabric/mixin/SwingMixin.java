package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Mixins;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A left click on Java's battle screen arrives as an arm swing. */
@Mixin(ServerGamePacketListenerImpl.class)
public class SwingMixin {
    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void cobblemonlink$swing(ServerboundSwingPacket packet, CallbackInfo ci) {
        Mixins.swing(((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
