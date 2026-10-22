package io.github.kahngjoonkoh.cobblemonlink.fabric;

import io.github.kahngjoonkoh.cobblemonlink.fabric.Lang;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;

/** Text rewrites for Bedrock players (Lang): chat, action bar, titles and item names. */
public final class BedrockText {
    private BedrockText() {}

    public static Packet<?> text(Packet<?> pk, net.minecraft.server.level.ServerPlayer p) {
        if (pk instanceof ClientboundSystemChatPacket c && !c.overlay() && c.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().startsWith("cobblemon.experience."))
            return null; // as Cobblemon: EXP, level-ups and new moves show on the party panel, not in chat
        if (pk instanceof ClientboundSystemChatPacket c) {
            var r = Lang.resolve(p, c.content());
            return r == c.content() ? pk : new ClientboundSystemChatPacket(r, c.overlay());
        }
        if (pk instanceof ClientboundDisguisedChatPacket d) { // /say and the like: the sender's name may be a mod's key
            var r = Lang.resolve(p, d.message());
            var name = Lang.resolve(p, d.chatType().name());
            return r == d.message() && name == d.chatType().name() ? pk : new ClientboundDisguisedChatPacket(r,
                    new net.minecraft.network.chat.ChatType.Bound(d.chatType().chatType(), name, d.chatType().targetName().map(Lang::resolve)));
        }
        if (pk instanceof ClientboundSetActionBarTextPacket a) {
            var r = Lang.resolve(p, a.text());
            return r == a.text() ? pk : new ClientboundSetActionBarTextPacket(r);
        }
        if (pk instanceof ClientboundSetTitleTextPacket t) {
            var r = Lang.resolve(p, t.text());
            return r == t.text() ? pk : new ClientboundSetTitleTextPacket(r);
        }
        if (pk instanceof ClientboundSetSubtitleTextPacket t) {
            var r = Lang.resolve(p, t.text());
            return r == t.text() ? pk : new ClientboundSetSubtitleTextPacket(r);
        }
        if (pk instanceof ClientboundUpdateAdvancementsPacket u) { // the toast's title and text: Cobblemon's advancements
            java.util.List<net.minecraft.advancements.AdvancementHolder> added = new java.util.ArrayList<>();
            for (var h : u.getAdded()) {
                var a = h.value();
                var d = a.display().map(x -> {
                    var nd = new net.minecraft.advancements.DisplayInfo(x.getIcon(), Lang.resolve(p, x.getTitle()), Lang.resolve(p, x.getDescription()),
                            x.getBackground(), x.getType(), x.shouldShowToast(), x.shouldAnnounceChat(), x.isHidden());
                    nd.setLocation(x.getX(), x.getY());
                    return nd;
                });
                added.add(new net.minecraft.advancements.AdvancementHolder(h.id(), new net.minecraft.advancements.Advancement(a.parent(), d,
                        a.rewards(), a.criteria(), a.requirements(), a.sendsTelemetryEvent(), a.name().map(n -> Lang.resolve(p, n)))));
            }
            return new ClientboundUpdateAdvancementsPacket(u.shouldReset(), added, u.getRemoved(), u.getProgress());
        }
        if (pk instanceof ClientboundContainerSetContentPacket c)
            return new ClientboundContainerSetContentPacket(c.getContainerId(), c.getStateId(),
                    net.minecraft.core.NonNullList.of(net.minecraft.world.item.ItemStack.EMPTY, c.getItems().stream().map(Lang::item).toArray(net.minecraft.world.item.ItemStack[]::new)),
                    Lang.item(p, c.getCarriedItem()));
        if (pk instanceof ClientboundContainerSetSlotPacket s)
            return new ClientboundContainerSetSlotPacket(s.getContainerId(), s.getStateId(), s.getSlot(), Lang.item(p, s.getItem()));
        return pk;
    }
}
