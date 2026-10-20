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
            cs.add(new Choice(Lang.t(p, "cobblemon.ui.interact.battle") + " (" + Lang.t(p, "cobblemonlink.format.multi") + ")", Items.NETHERITE_SWORD,
                    () -> challenge(p, target, BattleFormat.Companion.getGEN_9_MULTI())));
            cs.add(new Choice(Lang.t(p, "cobblemon.ui.interact.battle") + " (" + Lang.t(p, "cobblemonlink.format.triples") + ")", Items.DIAMOND_SWORD, () -> challenge(p, target, BattleFormat.Companion.getGEN_9_TRIPLES())));
        }
        if (com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(target) != null)
            cs.add(new Choice(Lang.t(p, "cobblemon.ui.interact.spectate"), Items.SPYGLASS, () -> com.cobblemon.mod.common.net.serverhandling.battle.SpectateBattleHandler.INSTANCE.handle(
                    new com.cobblemon.mod.common.net.messages.server.battle.SpectateBattlePacket(target.getUUID()), p.server, p)));
        else cs.add(new Choice(Lang.t(p, "cobblemon.ui.interact.team_request"), Items.LEAD, () -> com.cobblemon.mod.common.net.serverhandling.battle.TeamRequestHandler.INSTANCE.handle(
                new com.cobblemon.mod.common.net.messages.server.battle.BattleTeamRequestPacket(target.getId()), p.server, p)));
        cs.add(new Choice(Lang.t(p, "cobblemon.ui.interact.trade"), Items.EMERALD, () -> OfferTradeHandler.INSTANCE.handle(new OfferTradePacket(target.getUUID()), p.server, p)));
        String title = target.getScoreboardName();
        // Cobblemon checks the asker looks at the target too: they may have turned while the menu was open
        cs.replaceAll(c -> new Choice(c.name, c.icon, facing(p, target, c.run)));
        if (Clients.bedrock(p)) {
            List<Forms.Opt> opts = cs.stream().map(c -> new Forms.Opt(c.name, "none", c.run)).toList();
            Forms.send(p, title, "", opts, i -> { if (i >= 0) opts.get(i).action().run(); });
        } else {
            Chest c = new Chest(1, title);
            for (int i = 0; i < cs.size(); i++) {
                Runnable r = cs.get(i).run;
                c.set(i, Chest.icon(cs.get(i).icon, cs.get(i).name, List.of()), () -> { p.closeContainer(); r.run(); });
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
            // a multi battle's challenge comes from a team: one of its players
            if (from == null && n.getChallengerIDs() != null)
                for (var id : n.getChallengerIDs()) if (from == null) from = p.server.getPlayerList().getPlayer(id);
            if (from == null) return;
            ServerPlayer sender = from;
            String type = n.getBattleFormat().getBattleType().getName();
            ask(p, sender, Lang.t(p, "cobblemonlink.challenge.received", sender.getScoreboardName(), type), () -> {
                Pokemon lead = lead(p);
                if (lead != null) ChallengeResponseHandler.INSTANCE.handle(new BattleChallengeResponsePacket(sender.getId(), n.getChallengeID(), lead.getUuid(), true), p.server, p);
            }, () -> ChallengeResponseHandler.INSTANCE.handle(new BattleChallengeResponsePacket(sender.getId(), n.getChallengeID(), p.getUUID(), false), p.server, p));
        });
        else if (payload instanceof com.cobblemon.mod.common.net.messages.client.battle.TeamRequestNotificationPacket n) p.server.execute(() -> {
            ServerPlayer from = p.server.getPlayerList().getPlayer(n.getSenderID());
            if (from == null) return;
            ask(p, from, Lang.t(p, "cobblemonlink.team.received", from.getScoreboardName()),
                    () -> com.cobblemon.mod.common.net.serverhandling.battle.TeamRequestResponseHandler.INSTANCE.handle(
                            new com.cobblemon.mod.common.net.messages.server.battle.BattleTeamResponsePacket(from.getId(), n.getRequestID(), true), p.server, p),
                    () -> com.cobblemon.mod.common.net.serverhandling.battle.TeamRequestResponseHandler.INSTANCE.handle(
                            new com.cobblemon.mod.common.net.messages.server.battle.BattleTeamResponsePacket(from.getId(), n.getRequestID(), false), p.server, p));
        });
        else if (payload instanceof TradeOfferNotificationPacket n) p.server.execute(() -> {
            ServerPlayer from = p.server.getPlayerList().getPlayer(n.getSenderID());
            ask(p, from, Lang.t(p, "cobblemonlink.trade.received", from == null ? "?" : from.getScoreboardName()),
                    () -> AcceptTradeRequestHandler.INSTANCE.handle(new AcceptTradeRequestPacket(n.getRequestID()), p.server, p), () -> {});
        });
        else if (payload instanceof TradeStartedPacket || payload instanceof TradeUpdatedPacket || payload instanceof TradeAcceptanceChangedPacket)
            p.server.execute(() -> trade(p, false));
        // both accepted: Cobblemon's client plays its animation, then asks to go ahead
        else if (payload instanceof TradeProcessStartedPacket) p.server.execute(() -> {
            ActiveTrade t = TradeManager.INSTANCE.getActiveTrade(p.getUUID());
            if (t == null) return;
            var theirs = t.getOpposingOffer(t.getTradeParticipant(p.getUUID())).getPokemon();
            if (theirs != null) PerformTradeHandler.INSTANCE.handle(new PerformTradePacket(theirs.getUuid()), p.server, p);
        });
        else if (payload instanceof TradeCompletedPacket || payload instanceof TradeCancelledPacket) p.server.execute(() -> {
            SHOWN.remove(p.getUUID());
            if (!Clients.bedrock(p)) p.closeContainer();
        });
    }

    /** Turn p to face from: Cobblemon only lets you ask or answer someone you're looking at. */
    private static Runnable facing(ServerPlayer p, ServerPlayer from, Runnable then) {
        return () -> {
            if (from != null && !from.hasDisconnected())
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, from.getEyePosition());
            then.run();
        };
    }

    private static void ask(ServerPlayer p, ServerPlayer from, String question, Runnable yesAnswer, Runnable noAnswer) {
        Runnable yes = facing(p, from, yesAnswer), no = facing(p, from, noAnswer);
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

    /** The trade as last shown to each player: Cobblemon re-sends updates that change nothing (a re-offer). */
    private static final java.util.Map<java.util.UUID, String> SHOWN = new java.util.concurrent.ConcurrentHashMap<>();

    /** The trade screen, as Cobblemon has the trade now (again only if it changed, unless asked). */
    static void trade(ServerPlayer p, boolean always) {
        ActiveTrade t = TradeManager.INSTANCE.getActiveTrade(p.getUUID());
        if (t == null) return;
        var me = t.getTradeParticipant(p.getUUID());
        var them = t.getOppositePlayer(me);
        var mine = t.getOffer(me);
        var theirs = t.getOpposingOffer(me);
        String key = (mine.getPokemon() == null ? "-" : mine.getPokemon().getUuid()) + "/" + mine.getAccepted() + "/"
                + (theirs.getPokemon() == null ? "-" : theirs.getPokemon().getUuid()) + "/" + theirs.getAccepted();
        if (!always && key.equals(SHOWN.put(p.getUUID(), key))) return;
        SHOWN.put(p.getUUID(), key);
        String name = Lang.resolve(p, them.getName()).getString();
        // Cobblemon marks an offer accepted when the other side accepts it: my acceptance is on their offer
        boolean iAccepted = theirs.getAccepted(), theyAccepted = mine.getAccepted();
        String acc = "  (" + Lang.t(p, "cobblemonlink.trade.accepted") + ")";
        String status = Lang.t(p, "cobblemonlink.trade.you_offer", describe(p, mine.getPokemon())) + (iAccepted ? acc : "")
                + "\n" + Lang.t(p, "cobblemonlink.trade.they_offer", name, describe(p, theirs.getPokemon())) + (theyAccepted ? acc : "");
        List<Forms.Opt> opts = new ArrayList<>();
        opts.add(new Forms.Opt(Lang.t(p, "cobblemonlink.trade.offer"), "none", () -> offer(p)));
        if (mine.getPokemon() != null && theirs.getPokemon() != null)
            opts.add(new Forms.Opt(Lang.t(p, iAccepted ? "cobblemonlink.trade.unaccept" : "cobblemonlink.trade.accept"), "none", () ->
                    ChangeTradeAcceptanceHandler.INSTANCE.handle(new ChangeTradeAcceptancePacket(theirs.getPokemon().getUuid(), !iAccepted), p.server, p)));
        opts.add(new Forms.Opt(Lang.t(p, "cobblemonlink.trade.cancel"), "none", () -> CancelTradeHandler.INSTANCE.handle(new CancelTradePacket(), p.server, p)));
        if (Clients.bedrock(p)) {
            Forms.send(p, Lang.t(p, "cobblemonlink.trade.title", name), status, opts, i -> { if (i >= 0) opts.get(i).action().run(); });
            return;
        }
        Chest c = new Chest(3, Lang.t(p, "cobblemonlink.trade.title", name));
        c.set(10, mine.getPokemon() == null ? Chest.icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, Lang.t(p, "cobblemonlink.trade.you_offer", "-"), List.of()) : JavaScreens.mon(p, mine.getPokemon(), iAccepted ? Lang.t(p, "cobblemonlink.trade.accepted") : ""), null);
        c.set(16, theirs.getPokemon() == null ? Chest.icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, Lang.t(p, "cobblemonlink.trade.they_offer", name, "-"), List.of()) : JavaScreens.mon(p, theirs.getPokemon(), theyAccepted ? Lang.t(p, "cobblemonlink.trade.accepted") : ""), null);
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
            Forms.send(p, Lang.t(p, "cobblemonlink.trade.offer"), "", opts, i -> { if (i >= 0) opts.get(i).action().run(); else trade(p, true); });
            return;
        }
        Chest c = new Chest(1, Lang.t(p, "cobblemonlink.trade.offer"));
        for (int i = 0; i < opts.size(); i++) c.set(i, JavaScreens.mon(p, mons.get(i), Lang.t(p, "cobblemonlink.trade.click_offer")), opts.get(i).action());
        c.open(p);
    }
}
