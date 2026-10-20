package io.github.kahngjoonkoh.cobblemonlink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Cobblemon's trading between two players: a request the other accepts, then both pick a party Pokémon to offer and
 * confirm; when both are ready the Pokémon swap, and trade evolutions happen on arrival (Evolutions.traded).
 * A chest menu (docs/mockups/trade.md), so Java and Bedrock (through Geyser) both draw it.
 * ponytail: party Pokémon only; Cobblemon's trade screen also browses the PC.
 */
final class Trades {
    private final CobblemonLink pl;
    private final Map<UUID, UUID> requests = new HashMap<>(); // asked -> asker
    private final Map<UUID, Trade> open = new HashMap<>();

    Trades(CobblemonLink pl) { this.pl = pl; }

    private final class Trade {
        final Player[] who;
        final int[] offer = {-1, -1};
        final boolean[] ready = new boolean[2];
        final Menu[] menus = new Menu[2];
        boolean done;

        Trade(Player a, Player b) { who = new Player[] {a, b}; }

        int side(Player p) { return who[0].equals(p) ? 0 : 1; }
    }

    void command(Player p, String[] a) {
        if (a.length == 2 && a[0].equalsIgnoreCase("accept")) {
            Player asker = Bukkit.getPlayerExact(a[1]);
            if (asker == null || !asker.getUniqueId().equals(requests.remove(p.getUniqueId()))) {
                p.sendMessage(Component.text(pl.t(p, "cobblemonlink.trade.none", a[1]), NamedTextColor.RED));
                return;
            }
            start(asker, p);
            return;
        }
        Player other = a.length == 1 ? Bukkit.getPlayerExact(a[0]) : null;
        if (other == null || other.equals(p)) {
            p.sendMessage(Component.text("/trade <player>", NamedTextColor.RED));
            return;
        }
        if (!other.getWorld().equals(p.getWorld()) || other.getLocation().distance(p.getLocation()) > 32) {
            p.sendMessage(Component.text(pl.t(p, "cobblemonlink.trade.too_far", other.getName()), NamedTextColor.RED));
            return;
        }
        requests.put(other.getUniqueId(), p.getUniqueId());
        p.sendMessage(Component.text(pl.t(p, "cobblemon.trade.sent", other.getName()), NamedTextColor.GRAY));
        other.sendMessage(Component.text(pl.t(other, "cobblemon.trade.received", p.getName()) + " ", NamedTextColor.YELLOW)
                .append(Component.text(pl.t(other, "cobblemonlink.trade.accept"), NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/trade accept " + p.getName()))));
    }

    private void start(Player a, Player b) {
        for (Player p : List.of(a, b))
            if (pl.battles.containsKey(p.getUniqueId()) || open.containsKey(p.getUniqueId())) {
                a.sendMessage(Component.text(pl.t(a, "cobblemonlink.trade.cannot"), NamedTextColor.RED));
                b.sendMessage(Component.text(pl.t(b, "cobblemonlink.trade.cannot"), NamedTextColor.RED));
                return;
            }
        Trade t = new Trade(a, b);
        open.put(a.getUniqueId(), t);
        open.put(b.getUniqueId(), t);
        for (int s = 0; s < 2; s++) {
            Player p = t.who[s];
            t.menus[s] = new Menu(54, pl.t(p, "cobblemonlink.trade.title", t.who[1 - s].getName()));
            t.menus[s].onClose = () -> cancel(t, p);
            p.openInventory(t.menus[s].getInventory());
        }
        render(t);
    }

    /** Rows: your party (top), the two offers (middle), their party (bottom); Ready and Cancel on the right. */
    private void render(Trade t) {
        for (int s = 0; s < 2; s++) {
            Menu m = t.menus[s];
            m.getInventory().clear();
            List<Mon> mine = pl.store.get(t.who[s].getUniqueId()).party, theirs = pl.store.get(t.who[1 - s].getUniqueId()).party;
            int side = s;
            for (int i = 0; i < mine.size(); i++) {
                int idx = i;
                m.set(i, Menu.monIcon(pl.dex, mine.get(i), pl.t(t.who[side], t.offer[s] == i ? "cobblemonlink.trade.offered" : "cobblemonlink.trade.offer")), e -> offer(t, side, idx));
            }
            for (int i = 0; i < theirs.size(); i++) m.set(45 + i, Menu.monIcon(pl.dex, theirs.get(i)), null);
            m.set(20, t.offer[s] < 0 ? Menu.icon(Material.GRAY_STAINED_GLASS_PANE, pl.t(t.who[side], "cobblemonlink.trade.your_offer"), List.of())
                    : Menu.monIcon(pl.dex, mine.get(t.offer[s]), pl.t(t.who[side], "cobblemonlink.trade.your_offer")), null);
            m.set(24, t.offer[1 - s] < 0 ? Menu.icon(Material.GRAY_STAINED_GLASS_PANE, pl.t(t.who[side], "cobblemonlink.trade.their_offer", t.who[1 - s].getName()), List.of())
                    : Menu.monIcon(pl.dex, theirs.get(t.offer[1 - s]), pl.t(t.who[side], "cobblemonlink.trade.their_offer", t.who[1 - s].getName())), null);
            m.set(22, Menu.icon(Material.ARROW, "⇄", List.of()), null);
            m.set(8, Menu.icon(t.ready[s] ? Material.LIME_CONCRETE : Material.RED_CONCRETE, pl.t(t.who[side], t.ready[s] ? "cobblemonlink.trade.ready" : "cobblemonlink.trade.ready_q"),
                    List.of(t.ready[1 - s] ? t.who[1 - s].getName() + " is ready" : "Waiting for both offers")), e -> ready(t, side));
            m.set(17, Menu.icon(Material.BARRIER, pl.t(t.who[side], "cobblemonlink.trade.cancel"), List.of()), e -> t.who[side].closeInventory());
        }
    }

    private void offer(Trade t, int s, int idx) {
        if (t.done) return;
        t.offer[s] = idx;
        t.ready[0] = t.ready[1] = false; // a changed offer needs both to confirm again
        render(t);
    }

    private void ready(Trade t, int s) {
        if (t.done || t.offer[0] < 0 || t.offer[1] < 0) return;
        t.ready[s] = !t.ready[s];
        if (t.ready[0] && t.ready[1]) finish(t);
        else render(t);
    }

    private void finish(Trade t) {
        t.done = true;
        Store.Data a = pl.store.get(t.who[0].getUniqueId()), b = pl.store.get(t.who[1].getUniqueId());
        Mon ma = a.party.get(t.offer[0]), mb = b.party.get(t.offer[1]);
        a.party.set(t.offer[0], mb);
        b.party.set(t.offer[1], ma);
        for (int s = 0; s < 2; s++) {
            Player p = t.who[s];
            Mon got = s == 0 ? mb : ma;
            Store.Data d = s == 0 ? a : b;
            d.see(got);
            d.caught.add(got.species);
            String gotName = got.name(pl.dex);
            p.sendMessage(Component.text(pl.t(p, "cobblemonlink.trade.traded_for", got.name(pl.dex, CobblemonLink.loc(p))), NamedTextColor.GREEN));
            String evolved = pl.evolutions.traded(got, p); // Cobblemon: trade evolutions happen on arrival
            if (evolved != null) p.sendMessage(Component.text(evolved, NamedTextColor.AQUA));
            pl.store.save(p.getUniqueId());
            open.remove(p.getUniqueId());
            t.menus[s].onClose = null;
            p.closeInventory();
        }
    }

    private void cancel(Trade t, Player by) {
        if (t.done) return;
        t.done = true;
        for (int s = 0; s < 2; s++) {
            open.remove(t.who[s].getUniqueId());
            t.menus[s].onClose = null;
            if (!t.who[s].equals(by)) t.who[s].closeInventory();
            t.who[s].sendMessage(Component.text(pl.t(t.who[s], "cobblemonlink.trade.cancelled"), NamedTextColor.GRAY));
        }
    }
}
