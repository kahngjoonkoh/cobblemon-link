package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import java.util.Queue;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No resource pack during configuration (Polymer's AutoHostTask): Packs sends it in game, to vanilla Java only. */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public class PackMixin {
    @Shadow @Final private Queue<ConfigurationTask> configurationTasks;

    @Inject(method = "startConfiguration", at = @At("TAIL"))
    private void cobblemonlink$noPackYet(CallbackInfo ci) {
        configurationTasks.removeIf(t -> t instanceof eu.pb4.polymer.autohost.impl.AutoHostTask);
    }
}
