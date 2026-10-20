package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Menus;
import io.github.kahngjoonkoh.cobblemonlink.fabric.Mixins;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clicks on a chest standing in for a Cobblemon menu, renumbered to the menu's slots (Menus). */
@Mixin(ServerGamePacketListenerImpl.class)
public class ClickMixin {
    @ModifyVariable(method = "handleContainerClick", at = @At("HEAD"), argsOnly = true)
    private ServerboundContainerClickPacket cobblemonlink$click(ServerboundContainerClickPacket packet) {
        return Menus.click(packet, ((ServerGamePacketListenerImpl) (Object) this).player);
    }

    @Inject(method = "handleContainerClose", at = @At("HEAD"))
    private void cobblemonlink$close(ServerboundContainerClosePacket packet, CallbackInfo ci) {
        Mixins.menuClosed(((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
