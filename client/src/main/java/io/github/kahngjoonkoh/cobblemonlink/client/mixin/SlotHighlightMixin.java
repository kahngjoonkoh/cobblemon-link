package io.github.kahngjoonkoh.cobblemonlink.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The server's pack blanks vanilla's slot hover square for vanilla Java players (it lights up half of a PC or starter
 * tile); the mod's players keep it, drawn from the pack's copy under cobblemonlink:. Outside a cobblemon-link server
 * that copy is missing, so the vanilla sprite is used.
 */
@Mixin(AbstractContainerScreen.class)
abstract class SlotHighlightMixin {
    private static final Identifier BACK = Identifier.fromNamespaceAndPath("cobblemonlink", "container/slot_highlight_back");
    private static final Identifier FRONT = Identifier.fromNamespaceAndPath("cobblemonlink", "container/slot_highlight_front");

    @Redirect(method = "extractSlotHighlightBack", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;SLOT_HIGHLIGHT_BACK_SPRITE:Lnet/minecraft/resources/Identifier;"))
    private Identifier cobblemonlink$back() { return pick(BACK, "back"); }

    @Redirect(method = "extractSlotHighlightFront", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;SLOT_HIGHLIGHT_FRONT_SPRITE:Lnet/minecraft/resources/Identifier;"))
    private Identifier cobblemonlink$front() { return pick(FRONT, "front"); }

    private static Identifier pick(Identifier ours, String n) {
        return net.minecraft.client.Minecraft.getInstance().getResourceManager()
                .getResource(Identifier.fromNamespaceAndPath("cobblemonlink", "textures/gui/sprites/container/slot_highlight_" + n + ".png"))
                .isPresent() ? ours : Identifier.withDefaultNamespace("container/slot_highlight_" + n);
    }
}
