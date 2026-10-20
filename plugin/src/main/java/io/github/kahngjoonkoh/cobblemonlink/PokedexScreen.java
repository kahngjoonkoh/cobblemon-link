package io.github.kahngjoonkoh.cobblemonlink;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Cobblemon's Pokédex (345 x 207): every species in national order, 25 to a page in the left half (a seen one shows
 * its picture, a caught one also the Poké Ball mark), the chosen one in the right half (number, name, picture, types,
 * Pokédex entry for a caught one), seen and owned counts on top. Seen: met in battle; caught: ever owned.
 * A form (MARK + 8: Bedrock pack, client mod): buttons 0 / 1 previous / next page, 2 the page ("3/36"), 3 the chosen
 * one (image, "No. 0258"), 4-5 its types, 6 seen, 7 owned, 8-32 the page's slots ("0258", + "c" caught, + "*" chosen;
 * image the picture or "pokedex/unknown").
 * Vanilla Java with the UI pack: the same, stacked over a 5-row chest (build_dex.py's art; see art()). Without the
 * pack: a chest of 45 a page.
 * ponytail: no regional dexes, poses or cry.
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
        if (!Mod.form(p) && art(p)) {
            art(pl, p, views.computeIfAbsent(p.getUniqueId(), k -> new View()));
            return;
        }
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

    /** Vanilla Java without the UI pack: 45 a page as portraits (unseen: "???"), arrows on the last row. */
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

    // ---- vanilla Java with the UI pack ----

    static final int PER = 45, LINES = 6;
    static final String[] FILTERS = {"All", "Seen", "Caught"}, TABS = {"Entry", "Abilities", "Base stats", "Moves", "Evolutions"};
    private static final char BASE = '', SELECT = '', UNKNOWN = '', CAUGHT = '', UNKNOWN_BIG = '',
            TAB_ON = '', MALE = '', FEMALE = '', SHINY = '', SHINY_ON = '',
            FORM_LEFT = '', FORM_RIGHT = '', TAB_ICON = '', PLATFORM = '', BAR = ''; // build_dex.py
    private static final int BAR_MAX = 48, INK = 60; // the longest stat bar; grey of the text on the white page
    private static final String[][] REGIONS = {{"1", "Kanto"}, {"152", "Johto"}, {"252", "Hoenn"}, {"387", "Sinnoh"},
            {"494", "Unova"}, {"650", "Kalos"}, {"722", "Alola"}, {"810", "Galar"}, {"906", "Paldea"}};
    private static final Map<UUID, View> views = new HashMap<>();
    private static Map<String, String> prevo; // species -> what evolves into it

    /** What a player is looking at: page, the chosen one and how it's shown, tab (and how far scrolled), filter, search. */
    static final class View {
        int page, form, tab, scroll, filter;
        String sel, query = "";
        boolean female, shiny;
    }

    static void forget(Player p) { views.remove(p.getUniqueId()); }

    private static boolean art(Player p) {
        return !Ui.bedrock(p) && Ui.pcWidths != null && Ui.pcWidths.getAsJsonObject("ui").has(String.valueOf(BASE));
    }

    /** A cell's raw slot: rows 0-4 the chest, 5-7 the inventory, 8 the hotbar. */
    static int raw(int c, int r) { return r < 5 ? r * 9 + c : r < 8 ? 45 + (r - 5) * 9 + c : 72 + c; }

    static int x(int c) { return 11 + 18 * c; }

    static int y(int r) { return r < 5 ? 28 + 18 * r : r < 8 ? 131 + 18 * (r - 5) : 189; }

    /** The list's tile i (0-44): chest rows 3-4, then the inventory rows. */
    static int tileRow(int i) { return 3 + i / 9; }

    static String region(int dex) {
        String r = REGIONS[0][1];
        for (String[] e : REGIONS) if (dex >= Integer.parseInt(e[0])) r = e[1];
        return r;
    }

    /** The species shown, in national order: the filter (all, seen, caught), then the search (seen ones' names, or a number). */
    static List<String> list(Dex dex, Store.Data d, int filter, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        return order(dex).stream()
                .filter(s -> filter == 0 || (filter == 1 ? d.seen : d.caught).contains(s))
                .filter(s -> q.isEmpty() || d.seen.contains(s) && dex.name(s).toLowerCase(Locale.ROOT).contains(q)
                        || String.valueOf(dex.get(s).dex).equals(q.replaceFirst("^0+", "")))
                .toList();
    }

    /** The forms the player has seen this species in (null: the base form), base first. */
    static List<Dex.Form> forms(Dex dex, Store.Data d, String sp) {
        List<Dex.Form> out = new ArrayList<>();
        out.add(null);
        for (Dex.Form f : dex.get(sp).forms)
            if (!f.aspects.isEmpty() && looks(d, sp).stream().anyMatch(a -> a.containsAll(f.aspects))) out.add(f);
        return out;
    }

    static List<Set<String>> looks(Store.Data d, String sp) {
        List<Set<String>> out = new ArrayList<>();
        for (String l : d.looks)
            if (l.startsWith(sp + ":")) out.add(new HashSet<>(List.of(l.substring(sp.length() + 1).split(","))));
        return out;
    }

    private static void art(CobblemonLink pl, Player p, View v) {
        Store.Data d = pl.store.get(p.getUniqueId());
        List<String> all = list(pl.dex, d, v.filter, v.query);
        int pages = Math.max(1, (all.size() + PER - 1) / PER);
        v.page = Math.floorMod(v.page, pages);
        if (v.sel == null || pl.dex.get(v.sel) == null) v.sel = order(pl.dex).getFirst();
        List<Dex.Form> forms = forms(pl.dex, d, v.sel);
        v.form = Math.floorMod(v.form, forms.size());
        boolean female = canFemale(pl.dex, d, v.sel), shiny = looks(d, v.sel).stream().anyMatch(a -> a.contains("shiny"));
        v.female &= female;
        v.shiny &= shiny;
        Menu m = new Menu(45, title(pl, d, v, all, pages, forms, female, shiny));
        for (int i = 0; i < PER; i++) {
            int k = v.page * PER + i;
            if (k >= all.size()) break;
            String sp = all.get(k);
            m.on(raw(i % 9, tileRow(i)), e -> {
                if (!sp.equals(v.sel)) {
                    v.sel = sp;
                    v.form = v.scroll = 0;
                    v.female = v.shiny = false;
                }
                art(pl, p, v);
            });
        }
        if (female) m.on(raw(3, 0), e -> { v.female = !v.female; art(pl, p, v); });
        if (shiny) m.on(raw(3, 1), e -> { v.shiny = !v.shiny; art(pl, p, v); });
        if (forms.size() > 1) {
            m.on(raw(0, 2), e -> { v.form--; art(pl, p, v); });
            m.on(raw(3, 2), e -> { v.form++; art(pl, p, v); });
        }
        m.on(raw(0, 8), e -> { v.page--; art(pl, p, v); });
        m.on(raw(8, 8), e -> { v.page++; art(pl, p, v); });
        m.on(raw(1, 8), e -> {
            v.filter = Math.floorMod(v.filter + (e.isRightClick() ? -1 : 1), FILTERS.length);
            v.page = 0;
            art(pl, p, v);
        });
        m.on(raw(2, 8), e -> search(pl, p, v));
        for (int k = 0; k < TABS.length; k++) {
            int tab = k;
            m.on(raw(3 + k, 8), e -> { // the open tab again: its next part (Entry, Moves)
                v.scroll = tab == v.tab ? v.scroll + 1 : 0;
                v.tab = tab;
                art(pl, p, v);
            });
        }
        p.openInventory(m.getInventory());
        PcScreen.hideInventory(p, 45); // the list lies over the player's own inventory
        pl.onMain(() -> { if (p.getOpenInventory().getTopInventory().getHolder() == m) PcScreen.hideInventory(p, 45); });
    }

    /** Cobblemon's search field: a vanilla dialog's text box; empty clears it. */
    private static void search(CobblemonLink pl, Player p, View v) {
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton done = ActionButton.builder(Component.text("Search")).action(DialogAction.customClick((resp, who) -> pl.onMain(() -> {
            String q = resp.getText("q");
            v.query = q == null ? "" : q.strip();
            v.page = 0;
            art(pl, p, v);
        }), once)).build();
        ActionButton cancel = ActionButton.builder(Component.text("Cancel")).action(DialogAction.customClick((resp, who) -> pl.onMain(() -> art(pl, p, v)), once)).build();
        p.closeInventory();
        p.showDialog(Dialog.create(f -> f.empty().base(DialogBase.builder(Component.text("Search the Pokédex")).canCloseWithEscape(true)
                .inputs(List.of(DialogInput.text("q", Component.text("Name or number (empty: everything)")).initial(v.query).maxLength(20).build())).build())
                .type(DialogType.confirmation(done, cancel))));
    }

    /** It looks different as a female, and the player has seen one. */
    private static boolean canFemale(Dex dex, Store.Data d, String sp) {
        return !dex.model(sp, Set.of("female")).equals(dex.model(sp, Set.of("male")))
                && looks(d, sp).stream().anyMatch(a -> a.contains("female"));
    }

    private static Component title(CobblemonLink pl, Store.Data d, View v, List<String> all, int pages, List<Dex.Form> forms,
                                   boolean canFemale, boolean canShiny) {
        Ui.Title t = new Ui.Title();
        PcScreen.glyph(t, 0, 0, BASE, 7);
        PcScreen.text(t, "pc_label", 19, 12, "National", false);
        PcScreen.text(t, "pc_bold", 125, 13, String.format("%04d", d.seen.size()), false);
        PcScreen.text(t, "pc_bold", 155, 13, String.format("%04d", d.caught.size()), false);
        // the list
        for (int i = 0; i < PER && v.page * PER + i < all.size(); i++) {
            String sp = all.get(v.page * PER + i);
            int cx = x(i % 9), cy = y(tileRow(i));
            Integer b = Ui.bodies.get(pl.dex.model(sp, Set.of()));
            if (d.seen.contains(sp) && b != null)
                PcScreen.put(t, cx, cy, Component.text(Character.toString(0xF9000 + b)).font(Key.key("cobblemonlink", "body")), 17, 7);
            else PcScreen.glyph(t, cx + 4, cy + 4, UNKNOWN, 7);
            if (d.caught.contains(sp)) PcScreen.glyph(t, cx + 11, cy, CAUGHT, 5);
            String no = String.format("%04d", pl.dex.get(sp).dex);
            PcScreen.put(t, cx, cy, Component.text(no).font(Key.key("cobblemonlink", "dex_no")), PcScreen.width("dex_no", no) + no.length(), 4);
            if (sp.equals(v.sel)) PcScreen.glyph(t, cx - 1, cy - 1, SELECT, 7);
        }
        String strip;
        if (all.isEmpty()) strip = v.query.isEmpty() ? "Nothing " + FILTERS[v.filter].toLowerCase(Locale.ROOT) + " yet" : "No match for \"" + v.query + "\"";
        else {
            int lo = pl.dex.get(all.get(v.page * PER)).dex, hi = pl.dex.get(all.get(Math.min(all.size(), v.page * PER + PER) - 1)).dex;
            String where = v.query.isEmpty() ? (region(lo).equals(region(hi)) ? region(lo) : region(lo) + "-" + region(hi)) : "\"" + v.query + "\"";
            strip = where + "  " + String.format("%04d-%04d", lo, hi) + "  " + FILTERS[v.filter] + "  " + (v.page + 1) + "/" + pages;
        }
        PcScreen.text(t, "pc_bold", 91, 121, PcScreen.fit("pc_bold", strip, 160), true);
        PcScreen.glyph(t, x(3 + v.tab) - 1, y(8) - 1, TAB_ON, 7);
        PcScreen.glyph(t, x(3 + v.tab), y(8), (char) (TAB_ICON + v.tab), 7);
        // the chosen one
        String sp = v.sel;
        Dex.Species s = pl.dex.get(sp);
        Dex.Form form = forms.get(v.form);
        boolean seen = d.seen.contains(sp), caught = d.caught.contains(sp);
        List<String> types = form != null && form.types != null ? form.types : s.types;
        Set<String> aspects = new HashSet<>(form == null ? List.of() : form.aspects);
        aspects.add(v.female ? "female" : "male");
        if (v.shiny) aspects.add("shiny");
        Integer body = Ui.bodies.get(pl.dex.model(sp, aspects));
        if (seen) {
            PcScreen.glyph(t, 12, 62, (char) (PLATFORM + Ui.TYPES.indexOf(types.getFirst())), 7);
            if (body != null)
                PcScreen.put(t, 24, 29, Component.text(Character.toString(0xF9000 + body)).font(Key.key("cobblemonlink", "body_big")), 45, 7);
            for (int k = 0; k < types.size(); k++) PcScreen.glyph(t, 12 + 8 * k, 29, (char) ('' + Ui.TYPES.indexOf(types.get(k))), 7);
            if (canFemale) PcScreen.glyph(t, x(3), y(0), v.female ? FEMALE : MALE, 7);
            if (canShiny) PcScreen.glyph(t, x(3), y(1), v.shiny ? SHINY_ON : SHINY, 7);
            if (forms.size() > 1) {
                PcScreen.glyph(t, x(0) + 2, y(2), FORM_LEFT, 7);
                PcScreen.glyph(t, x(3) + 4, y(2), FORM_RIGHT, 7);
            }
        } else {
            PcScreen.glyph(t, 12, 62, (char) (PLATFORM + Ui.TYPES.size()), 7);
            PcScreen.glyph(t, 38, 40, UNKNOWN_BIG, 7);
        }
        String name = seen ? s.name + (form != null ? " (" + form.name + ")" : "") : "-----";
        PcScreen.text(t, "pc_bold", 84, 29, String.format("%04d", s.dex), false);
        PcScreen.text(t, "pc_bold", 104, 29, PcScreen.fit("pc_bold", name, 66), false);
        List<String> lines = page(pl.dex, d, v, sp, form, seen, caught);
        int per = v.tab == 2 ? 6 : LINES, from = lines.size() <= per ? 0 : Math.floorMod(v.scroll, (lines.size() + per - 1) / per) * per;
        for (int k = from; k < Math.min(lines.size(), from + per); k++) {
            int ly = 40 + 6 * (k - from);
            String line = lines.get(k);
            if (v.tab == 2 && line.contains("|")) { // Base stats: label, value, bar
                String[] f = line.split("\\|");
                PcScreen.text(t, "pc_bold", 85, ly, f[0], false, INK);
                PcScreen.text(t, "pc_text", 103, ly, f[1], false, INK);
                int w = Math.max(1, Math.min(BAR_MAX, Integer.parseInt(f[1]) * BAR_MAX / 255));
                PcScreen.glyph(t, 118, ly + 1, (char) (BAR + w - 1), 3);
            } else PcScreen.text(t, "pc_text", 85, ly, line, false, INK);
        }
        if (lines.size() > per) PcScreen.text(t, "pc_text", 168, 74, "v", false, INK); // more: click the tab again
        return t.build();
    }

    /** The open tab's lines (Base stats: "label|value"); nothing for one not seen yet. */
    static List<String> page(Dex dex, Store.Data d, View v, String sp, Dex.Form form, boolean seen, boolean caught) {
        Dex.Species s = dex.get(sp);
        int w = 84;
        java.util.function.ToIntFunction<String> width = x -> (int) Math.ceil(PcScreen.width("pc_text", x));
        if (!seen) return v.tab == 0 ? List.of("Not seen yet.") : List.of();
        return switch (v.tab) {
            case 0 -> caught && s.desc != null ? Ui.wrap(PcScreen.plain(s.desc), w, width) : List.of("Catch it to read its", "Pokédex entry.");
            case 1 -> (form != null && form.abilities != null ? form.abilities : s.abilities).stream().map(a -> dex.label("ability", a)).toList();
            case 2 -> {
                var stats = form != null && form.baseStats != null ? form.baseStats : s.baseStats;
                String[] label = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
                List<String> out = new ArrayList<>();
                for (int i = 0; i < 6; i++) out.add(label[i] + "|" + stats.getOrDefault(Dex.STATS[i], 0));
                yield out;
            }
            case 3 -> {
                List<String> out = new ArrayList<>();
                for (List<String> mv : form != null && form.moves != null ? form.moves : s.moves)
                    out.add("Lv" + (int) Double.parseDouble(mv.get(0)) + "  " + dex.label("move", mv.get(1)));
                yield out;
            }
            default -> evolutions(dex, d, sp);
        };
    }

    /** The whole family from its first stage, one line a stage: "Lv16 > Ivysaur"; unseen ones as ???. */
    static List<String> evolutions(Dex dex, Store.Data d, String sp) {
        if (prevo == null) {
            Map<String, String> m = new HashMap<>();
            dex.species.forEach((id, s) -> {
                if (s.evolutions != null) for (List<String> e : s.evolutions) m.putIfAbsent(e.get(1), id);
                if (s.evos != null) for (Dex.Evo e : s.evos) m.putIfAbsent(e.species(), id);
            });
            prevo = m;
        }
        String root = sp;
        for (int guard = 0; prevo.containsKey(root) && guard < 5; guard++) root = prevo.get(root);
        List<String> out = new ArrayList<>();
        family(dex, d, root, "", out);
        return out.size() == 1 ? List.of(out.getFirst(), "Doesn't evolve.") : out;
    }

    private static void family(Dex dex, Store.Data d, String sp, String how, List<String> out) {
        String name = d.seen.contains(sp) ? dex.name(sp) : "???";
        out.add(how.isEmpty() ? name : how + " > " + name);
        Dex.Species s = dex.get(sp);
        if (s == null || out.size() > 12) return;
        if (s.evolutions != null)
            for (List<String> e : s.evolutions) family(dex, d, e.get(1), "Lv" + (int) Double.parseDouble(String.valueOf(e.get(0))), out);
        if (s.evos != null) for (Dex.Evo e : s.evos) if (!e.species().equals(sp)) family(dex, d, e.species(), how(e), out);
    }

    /** "Fire Stone", "Trade", "Friendship 160", "Lv20 night"...: the evolution's trigger, shortly. */
    static String how(Dex.Evo e) {
        List<String> parts = new ArrayList<>();
        if (e.item != null) parts.add(title(e.item));
        else if (e.variant.equals("trade")) parts.add("Trade");
        for (var r : e.requirements) {
            Object v = r.get("variant");
            if ("level".equals(v) && r.get("minLevel") instanceof Number n) parts.add("Lv" + n.intValue());
            else if ("friendship".equals(v)) parts.add("Friendship");
            else if ("time_range".equals(v)) parts.add(String.valueOf(r.get("range")));
            else if ("held_item".equals(v)) parts.add("holding " + title(String.valueOf(r.get("itemCondition")).replaceFirst(".*:", "")));
            else if ("has_move".equals(v)) parts.add("knows " + r.get("move"));
        }
        return parts.isEmpty() ? "Level up" : String.join(" ", parts);
    }

    private static String title(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.split("_")) if (!w.isEmpty()) b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }
}
