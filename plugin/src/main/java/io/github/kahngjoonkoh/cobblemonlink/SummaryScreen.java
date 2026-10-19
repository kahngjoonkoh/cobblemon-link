package io.github.kahngjoonkoh.cobblemonlink;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Cobblemon's Summary screen (331 x 161): the Pokémon on the left (level, ball, name, gender, model, types), the Info /
 * Moves / Stats tabs in the middle, the party on the right to switch between them, and our party actions (send out or
 * recall, make lead, move to PC) under it. Drawn by the client from a form (MARK + 6: Bedrock pack and client mod);
 * vanilla Java with the UI pack gets it over a 5-row chest (build_summary.py's art; see art()), without it a dialog.
 * Buttons: 0-2 the tabs ("*" on the open one), 3-5 the actions ("" when not offered), 6 the Pokémon (image, "Lv. n"),
 * 7 gender, 8-9 type icons, 10 the name (tap: nickname), 11-16 the party ("Lv5*", image), 17-40 rows of three:
 * label, value, image; 41 Evolve ("" when it isn't ready).
 * ponytail: no move types or PP outside battle (the dex has none yet), no markings or held items.
 */
final class SummaryScreen {
    static final int INFO = 0, MOVES = 1, STATS = 2;
    static final int EVOLVE = 17 + 3 * 8; // the Evolve button, after the 8 rows
    private static final String[] TABS = {"Info", "Moves", "Stats"};
    private static final String[] STAT_NAMES = {"HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"};
    private static final int[] NATURE_STATS = {1, 2, 5, 3, 4}; // the order natures raise and lower: Atk, Def, Spe, SpA, SpD

    private SummaryScreen() {}

    /** The party's Pokémon at index, on tab. */
    static void open(CobblemonLink pl, Player p, int index, int tab) {
        Store.Data d = pl.store.get(p.getUniqueId());
        if (d.party.isEmpty()) return;
        int i = Math.clamp(index, 0, d.party.size() - 1);
        Mon m = d.party.get(i);
        if (!Mod.form(p)) {
            if (art(p)) art(pl, p, m, i, tab);
            else dialog(pl, p, m, i, tab);
            return;
        }
        Runnable stay = () -> open(pl, p, i, tab);
        boolean battling = pl.battles.containsKey(p.getUniqueId());
        List<Battle.Opt> o = new ArrayList<>();
        for (int t = 0; t < TABS.length; t++) {
            int tt = t;
            o.add(new Battle.Opt(TABS[t] + (t == tab ? "*" : ""), "summary/tab_" + TABS[t].toLowerCase(), () -> open(pl, p, i, tt)));
        }
        boolean out = pl.companions.isOut(p, m);
        o.add(new Battle.Opt(battling ? "" : out ? "Recall" : "Send out", "none", () -> {
            pl.companions.toggle(p, m);
            open(pl, p, i, tab);
        }));
        o.add(new Battle.Opt(battling || i == 0 ? "" : "Lead", "none", () -> {
            d.party.addFirst(d.party.remove(i));
            pl.store.save(p.getUniqueId());
            open(pl, p, 0, tab);
        }));
        o.add(new Battle.Opt(battling || d.party.size() <= 1 ? "" : "To PC", "none", () -> {
            if (d.toPc(m)) {
                pl.companions.recall(p, m.id);
                d.party.remove(m);
                pl.store.save(p.getUniqueId());
            }
            open(pl, p, Math.max(0, i - 1), tab);
        }));
        Dex.Species s = pl.dex.of(m);
        o.add(new Battle.Opt("Lv. " + m.level, Ui.bodies.containsKey(m.model(pl.dex)) ? "body/" + m.model(pl.dex) : "none", stay));
        o.add(new Battle.Opt("M".equals(m.gender) ? "♂" : "F".equals(m.gender) ? "♀" : "", "none", stay));
        for (int k = 0; k < 2; k++)
            o.add(new Battle.Opt("", k < s.types.size() ? "pc/type/" + s.types.get(k) : "none", stay));
        o.add(new Battle.Opt("Rename", "none", () -> rename(pl, p, m, i, tab))); // a tap on the name, as Cobblemon's
        for (int k = 0; k < Store.PARTY; k++) {
            int kk = k;
            Mon pm = k < d.party.size() ? d.party.get(k) : null;
            o.add(pm == null ? new Battle.Opt("", "none", stay) : new Battle.Opt("Lv" + pm.level + (k == i ? "*" : ""),
                    Ui.bodies.containsKey(pm.model(pl.dex)) ? "body/" + pm.model(pl.dex) : "none", () -> open(pl, p, kk, tab)));
        }
        for (String[] row : rows(pl, p, m, tab)) {
            o.add(new Battle.Opt(row[0], "none", stay));
            o.add(new Battle.Opt(row[1], "none", stay));
            o.add(new Battle.Opt("", row[2], stay));
        }
        while (o.size() < EVOLVE) o.add(new Battle.Opt("", "none", stay)); // the rows are fewer on Moves and Stats
        o.add(new Battle.Opt(m.evolution == null || battling ? "" : "Evolve", "none", () -> {
            evolve(pl, p, m);
            open(pl, p, i, tab);
        }));
        String title = m.name(pl.dex) + (m.shiny ? " ★" : "") + Forms.MARK + 6;
        Mod.send(p, title, "", o, c -> pl.onMain(() -> { if (c >= 0 && c < o.size()) o.get(c).action().run(); }));
    }

    /** Cobblemon's Evolve button: evolves into the pending evolution (the Pokédex now has it as caught). */
    static void evolve(CobblemonLink pl, Player p, Mon m) {
        String said = m.evolve(pl.dex);
        if (said == null) return;
        Store.Data d = pl.store.get(p.getUniqueId());
        d.see(m);
        d.caught.add(m.species);
        pl.store.save(p.getUniqueId());
        p.sendMessage(Component.text(said, net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    /** Cobblemon's nickname field: a Bedrock form or a vanilla dialog with one text input; empty goes back to the species. */
    static void rename(CobblemonLink pl, Player p, Mon m, int i, int tab) {
        java.util.function.Consumer<String> done = name -> pl.onMain(() -> {
            if (name != null) {
                String n = name.strip();
                m.nickname = n.isEmpty() ? null : n.substring(0, Math.min(12, n.length()));
                pl.store.save(p.getUniqueId());
            }
            open(pl, p, i, tab);
        });
        String current = m.nickname == null ? "" : m.nickname;
        if (Ui.bedrock(p) && Forms.available()) {
            Forms.input(p, "Nickname", pl.dex.name(m.species), current, done);
            return;
        }
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton ok = ActionButton.builder(Component.text("Done"))
                .action(DialogAction.customClick((r, w) -> done.accept(r.getText("name")), once)).build();
        ActionButton cancel = ActionButton.builder(Component.text("Cancel"))
                .action(DialogAction.customClick((r, w) -> done.accept(null), once)).build();
        p.showDialog(Dialog.create(f -> f.empty().base(DialogBase.builder(Component.text("Nickname")).canCloseWithEscape(true)
                        .inputs(List.of(io.papermc.paper.registry.data.dialog.input.DialogInput.text("name", Component.text(pl.dex.name(m.species)))
                                .initial(current).maxLength(12).build())).build())
                .type(DialogType.confirmation(ok, cancel))));
    }

    /** Up to 8 rows of [label, value, image] for the tab. */
    static List<String[]> rows(CobblemonLink pl, Player p, Mon m, int tab) {
        Dex.Species s = pl.dex.of(m);
        List<String[]> rows = new ArrayList<>();
        switch (tab) {
            case MOVES -> {
                for (int k = 0; k < 4; k++)
                    rows.add(new String[]{k < m.moves.size() ? pl.dex.label("move", m.moves.get(k)) : "", "", "none"});
            }
            case STATS -> {
                int[] st = stats(pl.dex, m);
                for (int k = 0; k < 6; k++) {
                    String mark = k == 0 ? "" : nature(m.nature, k) > 0 ? " +" : nature(m.nature, k) < 0 ? " -" : "";
                    rows.add(new String[]{STAT_NAMES[k] + mark, (k == 0 ? m.hp(pl.dex) + "/" : "") + st[k] + "   IV " + m.ivs[k], "none"});
                }
            }
            default -> {
                long next = m.level >= 100 ? 0 : Rules.expFor(s.expGroup, m.level + 1) - m.exp;
                rows.add(new String[]{"Pokédex No.", String.format("%04d", s.dex), "none"});
                rows.add(new String[]{"Species", pl.dex.name(m.species), "none"});
                rows.add(new String[]{"Type", String.join(" / ", s.types.stream().map(t -> pl.dex.label("type", t)).toList()), "none"});
                rows.add(new String[]{"OT", p.getName(), "none"});
                rows.add(new String[]{"Nature", m.nature, "none"});
                rows.add(new String[]{"Ability", pl.dex.label("ability", m.ability), "none"});
                rows.add(new String[]{"EXP. Points", String.valueOf(m.exp), "none"});
                rows.add(new String[]{"To Next Lv.", String.valueOf(next), "none"});
            }
        }
        return rows;
    }

    /** HP, Atk, Def, SpA, SpD, Spe at the Pokémon's level, IVs and nature (no EVs yet). */
    static int[] stats(Dex dex, Mon m) {
        Dex.Species s = dex.of(m);
        int[] out = new int[6];
        out[0] = m.maxHp(dex);
        for (int k = 1; k < 6; k++) {
            int n = nature(m.nature, k);
            out[k] = (int) (Rules.stat(s.base(k), m.ivs[k], m.level) * (n > 0 ? 1.1 : n < 0 ? 0.9 : 1.0));
        }
        return out;
    }

    /** +1 if the nature raises stat k (Dex.STATS order), -1 if it lowers it, 0 otherwise (neutral natures both). */
    static int nature(String nature, int k) {
        int n = java.util.Arrays.asList(Mon.NATURES).indexOf(nature);
        if (n < 0 || n / 5 == n % 5) return 0;
        return NATURE_STATS[n / 5] == k ? 1 : NATURE_STATS[n % 5] == k ? -1 : 0;
    }

    // ---- vanilla Java with the UI pack ----

    private static final char BASE = '', TAB_ON = '', TAB_ICON = '', PAGE_INFO = '', PAGE_FRAME = '',
            PAGE_STATS = '', MOVE = '', MOVE_SEL = '', EVOLVE_PLATE = '', PLATE = '', SHINY = '', // build_summary.py
            BALL = '', MALE = '', FEMALE = '', RING = '', TYPE_INFO = '', BAR = ''; // build_pc.py, build_dex.py
    private static final String[] SHORT = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
    private static final java.util.Map<java.util.UUID, Integer> picked = new java.util.HashMap<>(); // Moves: the move to swap

    private static boolean art(Player p) {
        return !Ui.bedrock(p) && Ui.pcWidths != null && Ui.pcWidths.getAsJsonObject("ui").has(String.valueOf(BASE));
    }

    static void forget(Player p) { picked.remove(p.getUniqueId()); }

    /**
     * Cobblemon's Summary over a 5-row chest: name card (click: nickname) and the tabs on the first row, the picture,
     * held item and Evolve on the left, the tab's page on the right (Moves: click two moves to swap them), the party
     * on the inventory rows, Send out / Lead / To PC on the hotbar.
     */
    private static void art(CobblemonLink pl, Player p, Mon m, int i, int tab) {
        Store.Data d = pl.store.get(p.getUniqueId());
        boolean battling = pl.battles.containsKey(p.getUniqueId()), out = pl.companions.isOut(p, m);
        if (tab != MOVES) picked.remove(p.getUniqueId());
        Integer pick = picked.get(p.getUniqueId());
        String[] actions = {battling ? "" : out ? "Recall" : "Send out", battling || i == 0 ? "" : "Lead", battling || d.party.size() <= 1 ? "" : "To PC"};
        Menu menu = new Menu(45, title(pl, p, d, m, i, tab, pick, actions, m.evolution != null && !battling));
        for (int t = 0; t < TABS.length; t++) {
            int tt = t;
            for (int c = 0; c < 2; c++) menu.on(PokedexScreen.raw(3 + 2 * t + c, 0), e -> open(pl, p, i, tt));
        }
        for (int c = 0; c < 3; c++) menu.on(PokedexScreen.raw(c, 0), e -> rename(pl, p, m, i, tab));
        if (m.evolution != null && !battling)
            for (int c = 1; c < 3; c++) menu.on(PokedexScreen.raw(c, 4), e -> { evolve(pl, p, m); open(pl, p, i, tab); });
        for (int k = 0; k < d.party.size(); k++) {
            int kk = k;
            menu.on(PokedexScreen.raw(k % 3, 6 + k / 3), e -> open(pl, p, kk, tab));
        }
        if (tab == MOVES && !battling)
            for (int k = 0; k < m.moves.size(); k++) {
                int kk = k;
                for (int c = 3; c < 9; c++) menu.on(PokedexScreen.raw(c, 1 + k), e -> {
                    Integer was = picked.remove(p.getUniqueId());
                    if (was == null) picked.put(p.getUniqueId(), kk);
                    else if (was != kk) {
                        java.util.Collections.swap(m.moves, was, kk);
                        pl.store.save(p.getUniqueId());
                    }
                    open(pl, p, i, tab);
                });
            }
        Runnable[] act = {() -> { pl.companions.toggle(p, m); open(pl, p, i, tab); },
                () -> { d.party.addFirst(d.party.remove(i)); pl.store.save(p.getUniqueId()); open(pl, p, 0, tab); },
                () -> {
                    if (d.toPc(m)) {
                        pl.companions.recall(p, m.id);
                        d.party.remove(m);
                        pl.store.save(p.getUniqueId());
                    }
                    open(pl, p, Math.max(0, i - 1), tab);
                }};
        for (int a = 0; a < 3; a++) {
            if (actions[a].isEmpty()) continue;
            Runnable r = act[a];
            for (int c = 0; c < 3; c++) menu.on(PokedexScreen.raw(3 * a + c, 8), e -> r.run());
        }
        p.openInventory(menu.getInventory());
        PcScreen.hideInventory(p, 45); // the party and the page lie over the player's own inventory
        pl.onMain(() -> { if (p.getOpenInventory().getTopInventory().getHolder() == menu) PcScreen.hideInventory(p, 45); });
    }

    private static Component title(CobblemonLink pl, Player p, Store.Data d, Mon m, int i, int tab, Integer pick,
                                   String[] actions, boolean evolve) {
        Ui.Title t = new Ui.Title();
        Dex.Species s = pl.dex.of(m);
        int x = PokedexScreen.x(0), cardX = x - 3, cardY = PokedexScreen.y(0) - 9;
        PcScreen.glyph(t, 0, 0, BASE, 7);
        int tx = PokedexScreen.x(3 + 2 * tab) - 1;
        PcScreen.glyph(t, tx, PokedexScreen.y(0) + 4, TAB_ON, 7);
        PcScreen.glyph(t, tx + 12, PokedexScreen.y(0) + 6, (char) (TAB_ICON + tab), 7);
        // name card, picture, types, held item, Evolve
        PcScreen.text(t, "pc_bold", cardX + 5, cardY + 1, "Lv." + m.level, false);
        PcScreen.glyph(t, cardX + 4, cardY + 11, BALL, 6);
        PcScreen.text(t, "pc_bold", cardX + 11, cardY + 11, PcScreen.fit("pc_bold", m.name(pl.dex), m.shiny ? 30 : 36), false);
        if (m.shiny) PcScreen.glyph(t, cardX + 43, cardY + 10, SHINY, 7);
        if (!m.gender.isEmpty()) PcScreen.glyph(t, cardX + 51, cardY + 13, "M".equals(m.gender) ? MALE : FEMALE, 4);
        Integer b = Ui.bodies.get(m.model(pl.dex));
        if (b != null) PcScreen.put(t, 14, 50, Component.text(Character.toString(0xF9000 + b)).font(Key.key("cobblemonlink", "body_big")), 45, 7);
        for (int k = 0; k < s.types.size(); k++) PcScreen.glyph(t, 12 + 8 * k, 47, (char) (TYPE_INFO + Ui.TYPES.indexOf(s.types.get(k))), 7);
        if (evolve) {
            PcScreen.glyph(t, PokedexScreen.x(1) - 1, PokedexScreen.y(4) - 1, EVOLVE_PLATE, 7);
            PcScreen.text(t, "pc_bold", PokedexScreen.x(1) + 18, PokedexScreen.y(4) + 5, "Evolve", true);
        }
        // the party
        PcScreen.text(t, "pc_label", 36, PokedexScreen.y(5) + 3, "Party", true);
        for (int k = 0; k < d.party.size(); k++) {
            Mon pm = d.party.get(k);
            int cx = PokedexScreen.x(k % 3), cy = PokedexScreen.y(6 + k / 3);
            Integer pb = Ui.bodies.get(pm.model(pl.dex));
            if (pb != null) PcScreen.put(t, cx, cy, Component.text(Character.toString(0xF9000 + pb)).font(Key.key("cobblemonlink", "body")), 17, 7);
            char lv = (char) (0xE000 + pm.level);
            PcScreen.put(t, cx, cy, Component.text(String.valueOf(lv)).font(Key.key("cobblemonlink", "pc_lv")), PcScreen.width("pc_lv", lv), 4);
            if (k == i) PcScreen.glyph(t, cx - 1, cy - 1, RING, 7);
        }
        // the hotbar's actions
        for (int a = 0; a < 3; a++) {
            if (actions[a].isEmpty()) continue;
            int ax = PokedexScreen.x(3 * a) - 1;
            PcScreen.glyph(t, ax, PokedexScreen.y(8) - 1, PLATE, 7);
            PcScreen.text(t, "pc_label", ax + 27.5, PokedexScreen.y(8) + 4, actions[a], true);
        }
        // the page
        int px = PokedexScreen.x(3) - 1, py = PokedexScreen.y(1) - 2;
        switch (tab) {
            case MOVES -> {
                PcScreen.glyph(t, px, py, PAGE_FRAME, 7);
                for (int k = 0; k < m.moves.size(); k++) {
                    int my = PokedexScreen.y(1 + k) - 1;
                    PcScreen.glyph(t, px, my, MOVE, 7);
                    PcScreen.text(t, "pc_bold", px + 6, my + 6, pl.dex.label("move", m.moves.get(k)), false);
                    if (pick != null && pick == k) PcScreen.glyph(t, px, my, MOVE_SEL, 7);
                }
                PcScreen.text(t, "pc_text", px + 6, PokedexScreen.y(5) + 2, pick == null ? "Click a move, then another," : "Now click where it goes.", false);
                if (pick == null) PcScreen.text(t, "pc_text", px + 6, PokedexScreen.y(5) + 9, "to swap their places.", false);
            }
            case STATS -> {
                PcScreen.glyph(t, px, py, PAGE_STATS, 7);
                int[] st = stats(pl.dex, m);
                int most = java.util.Arrays.stream(st).max().orElse(1);
                for (int k = 0; k < 6; k++) {
                    int ry = py + 5 + 17 * k;
                    int n = nature(m.nature, k);
                    PcScreen.text(t, "pc_bold", px + 7, ry + 4, SHORT[k] + (n > 0 ? "+" : n < 0 ? "-" : ""), false);
                    PcScreen.text(t, "pc_text", px + 44, ry + 4, (k == 0 ? m.hp(pl.dex) + "/" : "") + st[k], false);
                    int w = Math.max(1, Math.min(22, st[k] * 22 / Math.max(1, most))); // the strongest stat fills it
                    if (k > 0) PcScreen.glyph(t, px + 62, ry + 5, (char) (BAR + w - 1), 3);
                    PcScreen.text(t, "pc_text", px + 104 - PcScreen.width("pc_text", "IV " + m.ivs[k]), ry + 4, "IV " + m.ivs[k], false);
                }
                PcScreen.text(t, "pc_bold", px + 7, py + 110, "Nature: " + m.nature, false);
                int up = 0, down = 0;
                for (int k = 1; k < 6; k++) { if (nature(m.nature, k) > 0) up = k; if (nature(m.nature, k) < 0) down = k; }
                PcScreen.text(t, "pc_text", px + 7, py + 118, up == 0 ? "No stat changes" : "Raises " + STAT_NAMES[up] + ", lowers " + STAT_NAMES[down], false);
            }
            default -> {
                PcScreen.glyph(t, px, py, PAGE_INFO, 7);
                long next = m.level >= 100 ? 0 : Rules.expFor(s.expGroup, m.level + 1) - m.exp;
                String[][] rows = {{"Dex No.", String.format("%04d", s.dex)}, {"Species", pl.dex.name(m.species)},
                        {"Type", String.join(" / ", s.types.stream().map(ty -> pl.dex.label("type", ty)).toList())},
                        {"OT", p.getName()}, {"Nature", m.nature}, {"Ability", pl.dex.label("ability", m.ability)}};
                for (int k = 0; k < rows.length; k++) {
                    int ry = py + 5 + 15 * k;
                    PcScreen.text(t, "pc_bold", px + 7, ry + 3, rows[k][0], false);
                    PcScreen.text(t, "pc_bold", px + 44, ry + 3, PcScreen.fit("pc_bold", rows[k][1], k == 0 ? 28 : 56), false);
                }
                PcScreen.text(t, "pc_text", px + 48, py + 118, "Exp. Points", false);
                PcScreen.text(t, "pc_text", px + 101 - PcScreen.width("pc_text", String.valueOf(m.exp)), py + 118, String.valueOf(m.exp), false);
                PcScreen.text(t, "pc_text", px + 48, py + 128, "To Next Lv.", false);
                PcScreen.text(t, "pc_text", px + 101 - PcScreen.width("pc_text", String.valueOf(next)), py + 128, String.valueOf(next), false);
            }
        }
        return t.build();
    }

    /** Vanilla Java without the UI pack: the same, as a dialog with the tabs and the party as buttons. */
    private static void dialog(CobblemonLink pl, Player p, Mon m, int i, int tab) {
        List<DialogBody> body = new ArrayList<>();
        StringBuilder b = new StringBuilder(m.name(pl.dex) + "  Lv." + m.level + ("M".equals(m.gender) ? "  ♂" : "F".equals(m.gender) ? "  ♀" : "") + "\n");
        for (String[] row : rows(pl, p, m, tab)) b.append("\n").append(row[0]).append(row[1].isEmpty() ? "" : ":  " + row[1]);
        body.add(DialogBody.plainMessage(Component.text(b.toString()), 300));
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        List<ActionButton> buttons = new ArrayList<>();
        for (int t = 0; t < TABS.length; t++) {
            int tt = t;
            buttons.add(ActionButton.builder(Component.text((t == tab ? "▶ " : "") + TABS[t])).width(96)
                    .action(DialogAction.customClick((r, w) -> pl.onMain(() -> open(pl, p, i, tt)), once)).build());
        }
        if (m.evolution != null && !pl.battles.containsKey(p.getUniqueId()))
            buttons.add(ActionButton.builder(Component.text("Evolve")).width(96)
                    .action(DialogAction.customClick((r, w) -> pl.onMain(() -> { evolve(pl, p, m); open(pl, p, i, tab); }), once)).build());
        Store.Data d = pl.store.get(p.getUniqueId());
        for (int k = 0; k < d.party.size(); k++) {
            int kk = k;
            buttons.add(ActionButton.builder(Component.text((k == i ? "▶ " : "") + d.party.get(k).name(pl.dex))).width(96)
                    .action(DialogAction.customClick((r, w) -> pl.onMain(() -> open(pl, p, kk, tab)), once)).build());
        }
        p.showDialog(Dialog.create(f -> f.empty().base(DialogBase.builder(Component.text("Summary")).canCloseWithEscape(true).body(body).build())
                .type(DialogType.multiAction(buttons).columns(3).build())));
    }
}
