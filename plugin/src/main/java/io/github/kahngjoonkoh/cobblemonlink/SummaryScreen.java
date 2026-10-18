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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Cobblemon's Summary screen (331 x 161): the Pokémon on the left (level, ball, name, gender, model, types), the Info /
 * Moves / Stats tabs in the middle, the party on the right to switch between them, and our party actions (send out or
 * recall, make lead, move to PC) under it. Drawn by the client from a form (MARK + 6: Bedrock pack and client mod);
 * vanilla Java gets the same content in a dialog.
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
            dialog(pl, p, m, i, tab);
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
        d.seen.add(m.species);
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

    /** Vanilla Java: the same, as a dialog with the tabs and the party as buttons. */
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
