package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.net.messages.client.battle.BattleChallengeNotificationPacket;
import com.cobblemon.mod.common.net.messages.client.trade.TradeAcceptanceChangedPacket;
import com.cobblemon.mod.common.net.messages.client.trade.TradeCancelledPacket;
import com.cobblemon.mod.common.net.messages.client.trade.TradeCompletedPacket;
import com.cobblemon.mod.common.net.messages.client.trade.TradeOfferNotificationPacket;
import com.cobblemon.mod.common.net.messages.client.trade.TradeProcessStartedPacket;
import com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket;
import com.cobblemon.mod.common.net.messages.client.trade.TradeUpdatedPacket;
import com.cobblemon.mod.common.net.messages.server.BattleChallengePacket;
import com.cobblemon.mod.common.net.messages.server.BattleChallengeResponsePacket;
import com.cobblemon.mod.common.net.messages.server.trade.AcceptTradeRequestPacket;
import com.cobblemon.mod.common.net.messages.server.trade.CancelTradePacket;
import com.cobblemon.mod.common.net.messages.server.trade.ChangeTradeAcceptancePacket;
import com.cobblemon.mod.common.net.messages.server.trade.OfferTradePacket;
import com.cobblemon.mod.common.net.messages.server.trade.PerformTradePacket;
import com.cobblemon.mod.common.net.messages.server.trade.UpdateTradeOfferPacket;
import com.cobblemon.mod.common.net.serverhandling.ChallengeHandler;
import com.cobblemon.mod.common.net.serverhandling.ChallengeResponseHandler;
import com.cobblemon.mod.common.net.serverhandling.trade.AcceptTradeRequestHandler;
import com.cobblemon.mod.common.net.serverhandling.trade.CancelTradeHandler;
import com.cobblemon.mod.common.net.serverhandling.trade.ChangeTradeAcceptanceHandler;
import com.cobblemon.mod.common.net.serverhandling.trade.OfferTradeHandler;
import com.cobblemon.mod.common.net.serverhandling.trade.PerformTradeHandler;
import com.cobblemon.mod.common.net.serverhandling.trade.UpdateTradeOfferHandler;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.trade.ActiveTrade;
import com.cobblemon.mod.common.trade.TradeManager;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Cobblemon's player-to-player play for players without the mod: right-click (tap) a player to challenge them or
 * offer a trade (Cobblemon's R-key interaction menu), and the replies to challenges, trade offers and the trade
 * screen itself. Everything goes through Cobblemon's own handlers; the trade screen is read from Cobblemon's
 * ActiveTrade each time. Bedrock: forms; vanilla Java: chests.
 */
final class Social {
    private Social() {}

    /** Right-click on another player. */
    static boolean interact(ServerPlayer p, ServerPlayer target) {
        if (!Clients.bridged(p.connection)) return false;
        Pokemon lead = lead(p);
        record Choice(String name, net.minecraft.world.item.Item icon, Runnable run) {}
        List<Choice> cs = new ArrayList<>();
        if (lead != null) {
            cs.add(new Choice(Lang.t(p, "cobblemon.ui.interact.battle") + " (" + Lang.t(p, "cobblemonlink.format.singles") + ")", Items.IRON_SWORD, () -> challenge(p, target, BattleFormat.Companion.getGEN_9_SINGLES())));
            cs.add(new Choice(Lang.t(p, "cobblemon.ui.interact.battle") + " (" + Lang.t(p, "cobblemonlink.format.doubles") + ")", Items.GOLDEN_SWORD, () -> challenge(p, target, BattleFormat.Companion.getGEN_9_DOUBLES())));
            cs.add(new Choice(Lang.t(p, "cobblemon.ui.interact.battle") + " (" + Lang.t(p, "cobblemonlink.format.triples") + ")", Items.DIAMOND_SWORD, () -> challenge(p, target, BattleFormat.Companion.getGEN_9_TRIPLES())));
        }
        cs.add(new Choice(Lang.t(p, "cobblemon.ui.interact.trade"), Items.EMERALD, () -> OfferTradeHandler.INSTANCE.handle(new OfferTradePacket(target.getUUID()), p.server, p)));
        String title = target.getScoreboardName();
        if (Clients.bedrock(p)) {
            List<Forms.Opt> opts = cs.stream().map(c -> new Forms.Opt(c.name, "none", c.run)).toList();
            Forms.send(p, title, "", opts, i -> { if (i >= 0) opts.get(i).action().run(); });
        } else {
            Chest c = new Chest(1, title);
            for (int i = 0; i < cs.size(); i++) {
                Runnable r = cs.get(i).run;
                c.set(2 * i, Chest.icon(cs.get(i).icon, cs.get(i).name, List.of()), () -> { p.closeContainer(); r.run(); });
            }
            c.open(p);
        }
        return true;
    }

    private static Pokemon lead(ServerPlayer p) {
        for (Pokemon m : PlayerExtensionsKt.party(p)) if (!m.isFainted()) return m;
        return null;
    }

    private static void challenge(ServerPlayer p, ServerPlayer target, BattleFormat format) {
        Pokemon lead = lead(p);
        if (lead != null) ChallengeHandler.INSTANCE.handle(new BattleChallengePacket(target.getId(), lead.getUuid(), format), p.server, p);
    }

    /** Cobblemon's messages to a player without the mod (any thread). */
    static void packet(ServerPlayer p, CustomPacketPayload payload) {
        if (payload instanceof BattleChallengeNotificationPacket n) p.server.execute(() -> {
            ServerPlayer from = p.server.getPlayerList().getPlayer(n.getSenderID());
            if (from == null) return;
            String type = n.getBattleFormat().getBattleType().getName();
            ask(p, Lang.t(p, "cobblemonlink.challenge.received", from.getScoreboardName(), type), () -> {
                Pokemon lead = lead(p);
                if (lead != null) ChallengeResponseHandler.INSTANCE.handle(new BattleChallengeResponsePacket(from.getId(), n.getChallengeID(), lead.getUuid(), true), p.server, p);
            }, () -> ChallengeResponseHandler.INSTANCE.handle(new BattleChallengeResponsePacket(from.getId(), n.getChallengeID(), p.getUUID(), false), p.server, p));
        });
        else if (payload instanceof TradeOfferNotificationPacket n) p.server.execute(() -> {
            ServerPlayer from = p.server.getPlayerList().getPlayer(n.getSenderID());
            ask(p, Lang.t(p, "cobblemonlink.trade.received", from == null ? "?" : from.getScoreboardName()),
                    () -> AcceptTradeRequestHandler.INSTANCE.handle(new AcceptTradeRequestPacket(n.getRequestID()), p.server, p), () -> {});
        });
        else if (payload instanceof TradeStartedPacket || payload instanceof TradeUpdatedPacket || payload instanceof TradeAcceptanceChangedPacket)
            p.server.execute(() -> trade(p));
        // both accepted: Cobblemon's client plays its animation, then asks to go ahead
        else if (payload instanceof TradeProcessStartedPacket) p.server.execute(() -> {
            ActiveTrade t = TradeManager.INSTANCE.getActiveTrade(p.getUUID());
            if (t == null) return;
            var theirs = t.getOpposingOffer(t.getTradeParticipant(p.getUUID())).getPokemon();
            if (theirs != null) PerformTradeHandler.INSTANCE.handle(new PerformTradePacket(theirs.getUuid()), p.server, p);
        });
        else if (payload instanceof TradeCompletedPacket || payload instanceof TradeCancelledPacket) p.server.execute(() -> {
            if (!Clients.bedrock(p)) p.closeContainer();
        });
    }

    private static void ask(ServerPlayer p, String question, Runnable yes, Runnable no) {
        if (Clients.bedrock(p)) {
            Forms.send(p, question, "", List.of(new Forms.Opt(Lang.t(p, "cobblemon.ui.interact.accept"), "none", yes), new Forms.Opt(Lang.t(p, "cobblemon.ui.interact.decline"), "none", no)),
                    i -> (i == 0 ? yes : no).run());
            return;
        }
        Chest c = new Chest(1, question);
        c.set(2, Chest.icon(Items.LIME_WOOL, Lang.t(p, "cobblemon.ui.interact.accept"), List.of()), () -> { p.closeContainer(); yes.run(); });
        c.set(6, Chest.icon(Items.RED_WOOL, Lang.t(p, "cobblemon.ui.interact.decline"), List.of()), () -> { p.closeContainer(); no.run(); });
        c.open(p);
    }

    /** The trade screen, as Cobblemon has the trade now. */
    static void trade(ServerPlayer p) {
        ActiveTrade t = TradeManager.INSTANCE.getActiveTrade(p.getUUID());
        if (t == null) return;
        var me = t.getTradeParticipant(p.getUUID());
        var them = t.getOppositePlayer(me);
        var mine = t.getOffer(me);
        var theirs = t.getOpposingOffer(me);
        String name = Lang.resolve(p, them.getName()).getString();
        String acc = "  (" + Lang.t(p, "cobblemonlink.trade.accepted") + ")";
        String status = Lang.t(p, "cobblemonlink.trade.you_offer", describe(p, mine.getPokemon())) + (mine.getAccepted() ? acc : "")
                + "\n" + Lang.t(p, "cobblemonlink.trade.they_offer", name, describe(p, theirs.getPokemon())) + (theirs.getAccepted() ? acc : "");
        List<Forms.Opt> opts = new ArrayList<>();
        opts.add(new Forms.Opt(Lang.t(p, "cobblemonlink.trade.offer"), "none", () -> offer(p)));
        if (mine.getPokemon() != null && theirs.getPokemon() != null)
            opts.add(new Forms.Opt(Lang.t(p, mine.getAccepted() ? "cobblemonlink.trade.unaccept" : "cobblemonlink.trade.accept"), "none", () ->
                    ChangeTradeAcceptanceHandler.INSTANCE.handle(new ChangeTradeAcceptancePacket(theirs.getPokemon().getUuid(), !mine.getAccepted()), p.server, p)));
        opts.add(new Forms.Opt(Lang.t(p, "cobblemonlink.trade.cancel"), "none", () -> CancelTradeHandler.INSTANCE.handle(new CancelTradePacket(), p.server, p)));
        if (Clients.bedrock(p)) {
            Forms.send(p, Lang.t(p, "cobblemonlink.trade.title", name), status, opts, i -> { if (i >= 0) opts.get(i).action().run(); });
            return;
        }
        Chest c = new Chest(3, Lang.t(p, "cobblemonlink.trade.title", name));
        c.set(10, mine.getPokemon() == null ? Chest.icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, Lang.t(p, "cobblemonlink.trade.you_offer", "-"), List.of()) : JavaScreens.mon(mine.getPokemon(), mine.getAccepted() ? Lang.t(p, "cobblemonlink.trade.accepted") : ""), null);
        c.set(16, theirs.getPokemon() == null ? Chest.icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, Lang.t(p, "cobblemonlink.trade.they_offer", name, "-"), List.of()) : JavaScreens.mon(theirs.getPokemon(), theirs.getAccepted() ? Lang.t(p, "cobblemonlink.trade.accepted") : ""), null);
        net.minecraft.world.item.Item[] icons = {Items.CHEST, Items.LIME_WOOL, Items.BARRIER};
        for (int i = 0; i < opts.size(); i++) {
            Forms.Opt o = opts.get(i);
            net.minecraft.world.item.Item icon = i == 0 ? icons[0] : i == opts.size() - 1 ? icons[2] : icons[1];
            c.set(12 + i, Chest.icon(icon, o.text(), List.of()), o.action());
        }
        c.open(p);
    }

    private static String describe(ServerPlayer p, Pokemon m) { return m == null ? "-" : BattleUi.name(m) + " " + Lang.t(p, "cobblemon.ui.lv") + " " + m.getLevel(); }

    /** Pick one of your party to offer (Cobblemon checks it may be traded). */
    private static void offer(ServerPlayer p) {
        var party = PlayerExtensionsKt.party(p);
        List<Forms.Opt> opts = new ArrayList<>();
        List<Pokemon> mons = new ArrayList<>();
        for (int slot = 0; slot < 6; slot++) {
            Pokemon m = party.get(new PartyPosition(slot));
            if (m == null) continue;
            int s = slot;
            mons.add(m);
            opts.add(new Forms.Opt(describe(p, m), Models.has(Summary.look(m)) ? "body/" + Summary.look(m) : "none",
                    () -> UpdateTradeOfferHandler.INSTANCE.handle(new UpdateTradeOfferPacket(new kotlin.Pair<>(m.getUuid(), new PartyPosition(s))), p.server, p)));
        }
        if (Clients.bedrock(p)) {
            Forms.send(p, Lang.t(p, "cobblemonlink.trade.offer"), "", opts, i -> { if (i >= 0) opts.get(i).action().run(); else trade(p); });
            return;
        }
        Chest c = new Chest(1, Lang.t(p, "cobblemonlink.trade.offer"));
        for (int i = 0; i < opts.size(); i++) c.set(i, JavaScreens.mon(mons.get(i), Lang.t(p, "cobblemonlink.trade.click_offer")), opts.get(i).action());
        c.open(p);
    }
}
