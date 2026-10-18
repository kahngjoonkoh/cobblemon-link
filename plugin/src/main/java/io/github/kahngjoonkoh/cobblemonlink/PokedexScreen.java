package io.github.kahngjoonkoh.cobblemonlink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Cobblemon's Pokédex (345 x 207): every species in national order, 25 to a page in the left half (a seen one shows
 * its picture, a caught one also the Poké Ball mark), the chosen one in the right half (number, name, picture, types,
 * Pokédex entry for a caught one), seen and owned counts on top. Seen: met in battle; caught: ever owned.
 * A form (MARK + 8: Bedrock pack, client mod): buttons 0 / 1 previous / next page, 2 the page ("3/36"), 3 the chosen
 * one (image, "No. 0258"), 4-5 its types, 6 seen, 7 owned, 8-32 the page's slots ("0258", + "c" caught, + "*" chosen;
 * image the picture or "pokedex/unknown"). Vanilla Java: a chest of 45 a page.
 * ponytail: no search, filters, forms, sizes, stats, moves or evolution tabs; no cry.
 */
final class PokedexScreen {
    static final int PAGE = 25;

    private PokedexScreen() {}

    static List<String> order(Dex dex) {
        return dex.species.keySet().stream().filter(s -> dex.get(s).dex > 0)
                .sorted(Comparator.comparingInt((String s) -> dex.get(s).dex).thenComparing(s -> s)).toList();
    }

    /** page 0-based; chosen: a species, or null for the page's first. */
    static void open(CobblemonLink pl, Player p, int page, String chosen) {
        List<String> all = order(pl.dex);
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE), pg = Math.floorMod(page, pages);
        Store.Data d = pl.store.get(p.getUniqueId());
        List<String> here = all.subList(pg * PAGE, Math.min(all.size(), pg * PAGE + PAGE));
        String sel = chosen != null ? chosen : here.getFirst();
        if (!Mod.form(p)) {
            chest(pl, p, all, pg * PAGE / 45, d);
            return;
        }
        Dex.Species s = pl.dex.get(sel);
        boolean seen = d.seen.contains(sel), caught = d.caught.contains(sel);
        Runnable stay = () -> open(pl, p, pg, sel);
        List<Battle.Opt> o = new ArrayList<>();
        o.add(new Battle.Opt("<", "none", () -> open(pl, p, pg - 1, null)));
        o.add(new Battle.Opt(">", "none", () -> open(pl, p, pg + 1, null)));
        o.add(new Battle.Opt((pg + 1) + "/" + pages, "none", stay));
        o.add(new Battle.Opt(String.format("No. %04d", s.dex), seen ? picture(sel) : "pokedex/unknown", stay));
        for (int k = 0; k < 2; k++) o.add(new Battle.Opt("", seen && k < s.types.size() ? "pc/type/" + s.types.get(k) : "none", stay));
        o.add(new Battle.Opt(String.valueOf(d.seen.size()), "none", stay));
        o.add(new Battle.Opt(String.valueOf(d.caught.size()), "none", stay));
        for (int k = 0; k < PAGE; k++) {
            if (k >= here.size()) {
                o.add(new Battle.Opt("", "none", stay));
                continue;
            }
            String sp = here.get(k);
            boolean sn = d.seen.contains(sp);
            o.add(new Battle.Opt(String.format("%04d", pl.dex.get(sp).dex) + (d.caught.contains(sp) ? "c" : "") + (sp.equals(sel) ? "*" : ""),
                    sn ? picture(sp) : "pokedex/unknown", () -> open(pl, p, pg, sp)));
        }
        String entry = caught ? s.desc == null ? "" : s.desc : seen ? "Catch it to read its Pokédex entry." : "";
        Mod.send(p, (seen ? pl.dex.name(sel) : "???") + Forms.MARK + 8, entry, o,
                c -> pl.onMain(() -> { if (c >= 0 && c < o.size()) o.get(c).action().run(); }));
    }

    private static String picture(String species) {
        return Ui.bodies.containsKey(species) ? "body/" + species : "pokedex/unknown";
    }

    /** Vanilla Java: 45 a page as portraits (unseen: "???"), arrows on the last row. */
    private static void chest(CobblemonLink pl, Player p, List<String> all, int page, Store.Data d) {
        int per = 45, pages = (all.size() + per - 1) / per, pg = Math.floorMod(page, pages);
        Menu m = new Menu(54, Component.text("Pokédex  " + d.seen.size() + " seen, " + d.caught.size() + " caught  " + (pg + 1) + "/" + pages));
        for (int i = 0; i < per && pg * per + i < all.size(); i++) {
            String sp = all.get(pg * per + i);
            Dex.Species s = pl.dex.get(sp);
            String no = String.format("No. %04d  ", s.dex);
            if (!d.seen.contains(sp)) {
                m.set(i, Menu.icon(Material.GRAY_STAINED_GLASS_PANE, no + "???", List.of()), null);
                continue;
            }
            List<String> lore = new ArrayList<>(List.of(String.join(" / ", s.types.stream().map(t -> pl.dex.label("type", t)).toList())));
            if (d.caught.contains(sp) && s.desc != null) lore.addAll(Ui.wrap(s.desc, 180));
            else lore.add(d.caught.contains(sp) ? "Caught" : "Seen");
            m.set(i, Menu.icon(Menu.speciesItem(sp, Menu.typeIcon(s.types.getFirst())), no + s.name, lore), null);
        }
        if (pg > 0) m.set(45, Menu.icon(Material.ARROW, "Previous page", List.of()), e -> chest(pl, p, all, pg - 1, d));
        if (pg < pages - 1) m.set(53, Menu.icon(Material.ARROW, "Next page", List.of()), e -> chest(pl, p, all, pg + 1, d));
        p.openInventory(m.getInventory());
    }
}
