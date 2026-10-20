package io.github.kahngjoonkoh.cobblemonlink.fabric;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Lang;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;

/** Text rewrites for Bedrock players (Lang): chat, action bar, titles and item names. */
public final class BedrockText {
    private BedrockText() {}

    public static Packet<?> text(Packet<?> p) {
        if (p instanceof ClientboundSystemChatPacket c && !c.overlay() && c.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().startsWith("cobblemon.experience."))
            return null; // as Cobblemon: EXP, level-ups and new moves show on the party panel, not in chat
        if (p instanceof ClientboundSystemChatPacket c) {
            var r = Lang.resolve(c.content());
            return r == c.content() ? p : new ClientboundSystemChatPacket(r, c.overlay());
        }
        if (p instanceof ClientboundSetActionBarTextPacket a) {
            var r = Lang.resolve(a.text());
            return r == a.text() ? p : new ClientboundSetActionBarTextPacket(r);
        }
        if (p instanceof ClientboundSetTitleTextPacket t) {
            var r = Lang.resolve(t.text());
            return r == t.text() ? p : new ClientboundSetTitleTextPacket(r);
        }
        if (p instanceof ClientboundSetSubtitleTextPacket t) {
            var r = Lang.resolve(t.text());
            return r == t.text() ? p : new ClientboundSetSubtitleTextPacket(r);
        }
        if (p instanceof ClientboundContainerSetContentPacket c)
            return new ClientboundContainerSetContentPacket(c.getContainerId(), c.getStateId(),
                    net.minecraft.core.NonNullList.of(net.minecraft.world.item.ItemStack.EMPTY, c.getItems().stream().map(Lang::item).toArray(net.minecraft.world.item.ItemStack[]::new)),
                    Lang.item(c.getCarriedItem()));
        if (p instanceof ClientboundContainerSetSlotPacket s)
            return new ClientboundContainerSetSlotPacket(s.getContainerId(), s.getStateId(), s.getSlot(), Lang.item(s.getItem()));
        return p;
    }
}
