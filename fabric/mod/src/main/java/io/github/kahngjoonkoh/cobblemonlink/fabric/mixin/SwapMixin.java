package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Mixins;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sneak + swap-hands (F) for players without the mod: Cobblemon's send out / recall key. */
@Mixin(ServerGamePacketListenerImpl.class)
public class SwapMixin {
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void cobblemonlink$swap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        var p = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND && p.server.isSameThread() && Mixins.sendOutKey(p)) {
            p.inventoryMenu.sendAllDataToRemote(); // the client already swapped its hands
            ci.cancel();
        }
    }
}
