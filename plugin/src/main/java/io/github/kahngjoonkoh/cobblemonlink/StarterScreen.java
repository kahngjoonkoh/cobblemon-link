package io.github.kahngjoonkoh.cobblemonlink;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Cobblemon's StarterSelectionScreen for vanilla Java: a 4-row chest whose title draws the whole screen (build_ui.py's
 * starter art, cut to fit the chest; the starter's card; the faces; the text) and whose slots are its buttons, each
 * exactly over its slots. The chest (176 x 186) sits at -PX, -PY behind the 202 x 202 panel, centred in it.
 * The list is Cobblemon's, 4 regions in view: a header and 3 tiles a region, 36 px apart. A tile can span two slot
 * rows (both take its clicks); no slot touches two tiles. The bottom row: "I choose you!" (4 slots), the page arrows
 * (ours) under the list's first and last tile column. No back button: Esc or E closes the chest.
 * ponytail: still pictures (Cobblemon animates the model and the background); tiles are a slot's 16 px, not 25, with
 * the battle portrait's close-up; the hover square sits a few px off the first two regions' tiles; empty slots light
 * up under the cursor; a player's own items would show through the panel's lower half.
 */
final class StarterScreen {
    static final int PX = -13, PY = -15; // the chest's top left is (-PX, -PY) on the panel; its title starts at 8, 6
    static final List<String> REGIONS = List.of("Kanto", "Johto", "Hoenn", "Sinnoh", "Unova", "Kalos", "Alola", "Galar", "Paldea");
    private static final char BASE = '\ue280', BLOCK = '\ue281', SELECTED = '\ue282', THUMB = '\ue283', UP = '\ue284',
            DOWN = '\ue285', BALL = '\ue286';
    private static final int WHITE = 255, SHADOW = 63, DIM = 90; // grey levels
    private static final int VISIBLE = 4, PER_PAGE = 3; // regions on the Java screen; on the Bedrock form
    // raw slots of column 6 for region k's tiles (chest rows 0+1, 2+3; inventory rows 0, 2), then on the hotbar
    // "I choose you!" (columns 0-3) and the page arrows (6, 8)
    private static final int[][] TILE_SLOTS = {{6, 15}, {24, 33}, {42}, {60}};
    private static final int[] TILE_Y = {45, 81, 117, 153}; // the tiles' tops on the panel
    private static final int UP_SLOT = 63 + 6, DOWN_SLOT = 63 + 8;
    private static final List<Integer> CHOOSE_SLOTS = List.of(63, 64, 65, 66);

    record Region(String name, List<String> species) {}

    /** Every region's starters as Cobblemon lists them; anything else (another plugin's list, the random one) after. */
    static List<Region> regions(List<String> species, String surprise) {
        List<Region> out = new ArrayList<>();
        List<String> rest = new ArrayList<>(species);
        for (int i = 0; i < REGIONS.size(); i++) {
            List<String> r = CobblemonLink.STARTERS.subList(3 * i, 3 * i + 3).stream().filter(rest::contains).toList();
            if (!r.isEmpty()) out.add(new Region(REGIONS.get(i), r));
            rest.removeAll(r);
        }
        for (int i = 0; i < rest.size(); i += 3) out.add(new Region("Other", rest.subList(i, Math.min(i + 3, rest.size()))));
        if (surprise != null) out.add(new Region("Random", List.of(surprise)));
        return out;
    }

    /** top: the first region shown; the arrows move 4 regions, the last page ends at the last region. */
    static void open(CobblemonLink pl, Player p, List<Region> regions, int top, String selected, Consumer<String> choose) {
        Menu m = new Menu(36, title(pl, regions, top, selected));
        for (int k = 0; k < VISIBLE && top + k < regions.size(); k++) {
            List<String> species = regions.get(top + k).species();
            for (int j = 0; j < species.size(); j++) {
                String s = species.get(j);
                for (int slot : TILE_SLOTS[k]) m.on(slot + j, e -> open(pl, p, regions, top, s, choose));
            }
        }
        int last = Math.max(0, regions.size() - VISIBLE);
        if (top > 0) m.on(UP_SLOT, e -> open(pl, p, regions, Math.max(0, top - VISIBLE), selected, choose));
        if (top < last) m.on(DOWN_SLOT, e -> open(pl, p, regions, Math.min(last, top + VISIBLE), selected, choose));
        for (int slot : CHOOSE_SLOTS) m.on(slot, e -> choose.accept(selected));
        p.openInventory(m.getInventory());
    }

    /**
     * Bedrock: the same screen as a Floodgate form, its title ending in Forms.MARK + 4 (build_bedrock_ui.py
     * starter_form draws it). Buttons: 0 "I choose you!" (text: the Pokédex number, image: the card); 1 and 2 the page
     * arrows; then a row of 3 a region, padded (text: the region's name on its first tile, "*" on the selected one).
     * A tap re-sends the form, as a click reopens the chest.
     */
    static void bedrock(CobblemonLink pl, Player p, List<Region> regions, int page, String selected, Consumer<String> choose) {
        Dex.Species d = pl.dex.get(selected);
        Runnable stay = () -> bedrock(pl, p, regions, page, selected, choose);
        List<Battle.Opt> opts = new ArrayList<>();
        opts.add(new Battle.Opt(String.format("#%04d", d.dex), "card/" + selected, () -> choose.accept(selected)));
        opts.add(new Battle.Opt("", "up", page > 0 ? () -> bedrock(pl, p, regions, page - PER_PAGE, selected, choose) : stay));
        opts.add(new Battle.Opt("", "down", page + PER_PAGE < regions.size()
                ? () -> bedrock(pl, p, regions, page + PER_PAGE, selected, choose) : stay));
        for (int k = 0; k < PER_PAGE && page + k < regions.size(); k++) {
            Region r = regions.get(page + k);
            for (int j = 0; j < 3; j++) {
                if (j >= r.species().size()) {
                    opts.add(new Battle.Opt("", "none", stay));
                    continue;
                }
                String s = r.species().get(j);
                opts.add(new Battle.Opt((j == 0 ? r.name() : "") + (s.equals(selected) ? "*" : ""),
                        Ui.tiles.containsKey(s) ? "tile/" + s : "textures/items/portrait/" + Ui.bedrockName(s),
                        () -> bedrock(pl, p, regions, page, s, choose)));
            }
        }
        Mod.send(p, pl.dex.name(selected) + Forms.MARK + 4, d.desc == null ? "" : d.desc, opts,
                i -> pl.getServer().getScheduler().runTask(pl, () -> { if (i >= 0) opts.get(i).action().run(); }));
    }

    static Component title(CobblemonLink pl, List<Region> regions, int top, String selected) {
        Dex.Species d = pl.dex.get(selected);
        Ui.Title t = new Ui.Title();
        art(t, 0, 0, BASE, 203); // 202 px wide (build_ui.py starter_screen)
        var card = Ui.card(selected);
        if (card != null) t.add(x(1), down(17), card, 111, WHITE); // 110 px: its window 18 px narrower
        for (int k = 0; k < VISIBLE && top + k < regions.size(); k++) {
            Region r = regions.get(top + k);
            int y = TILE_Y[k];
            art(t, 119, y - 9, BLOCK, 73); // header and container, 72 px; the tiles 9 px down, 10 px in
            small(t, 155 - Ui.tightWidth(r.name()) / 2.0, y - 8, r.name());
            for (int j = 0; j < r.species().size(); j++) {
                String s = r.species().get(j);
                // the battle portrait's close-up (a whole body is too small here): 14 px in the frame, or a starter
                // without one (the random one) at 16 px, ascent 12
                var face = Ui.tile(s);
                if (face != null) t.add(x(130 + 18 * j), down(y + 1), face, 15, WHITE);
                else if ((face = Ui.smallPortrait(s)) != null) t.add(x(129 + 18 * j), down(y + 5), face, 17, WHITE);
                if (s.equals(selected)) art(t, 129 + 18 * j, y, SELECTED, 17); // Cobblemon's blue tile, as a rim over the face
            }
        }
        int last = Math.max(0, regions.size() - VISIBLE);
        art(t, 194, 32 + (144 - 64) * top / Math.max(1, last), THUMB, 4); // the track: 32-176
        if (last > 0) { // on the list's footer, under the first and the last tile column
            t.add(x(134), down(183), glyph(UP), 8, top > 0 ? WHITE : DIM);
            t.add(x(170), down(183), glyph(DOWN), 8, top < last ? WHITE : DIM);
        }
        art(t, 4, 7, BALL, 9);
        label(t, 144.5, 16, "Starter Selection", true, false); // regular weight: the title tab is narrower than Cobblemon's
        label(t, 56, 181, "I choose you!", true, true);
        // Cobblemon's lines: 5.5 px apart from 143, in its box 18 px narrower (96), so a 6th fits 5 px apart;
        // anything longer is cut with "..."
        List<String> lines = Ui.wrap(plain(d.desc == null ? "" : d.desc), 96, Ui::tightWidth);
        int n = Math.min(6, lines.size());
        double gap = n <= 5 ? 5.5 : 5, first = n <= 5 ? 143 : 142;
        for (int i = 0; i < n; i++)
            small(t, 8, (int) Math.round(first + gap * i), i == 5 && lines.size() > 6 ? lines.get(i) + "..." : lines.get(i));
        label(t, 70, 5, String.format("#%04d", d.dex), false, true);
        label(t, 14, 6, pl.dex.name(selected), false, true); // last: a non-ASCII name's width is a guess
        return t.build();
    }

    private static double x(double panelX) { return panelX + PX - 8; }

    /** The shader's move for a piece whose top (at ascent 7) should be panelY down the panel. */
    private static int down(int panelY) { return panelY + PY - 6; }

    private static Component glyph(char c) { return Ui.glyphs(String.valueOf(c)); }

    private static void art(Ui.Title t, double panelX, int panelY, char c, int advance) {
        t.add(x(panelX), down(panelY), glyph(c), advance, WHITE);
    }

    /** Cobblemon's DEFAULT_LARGE text (bold unless not) with its drop shadow; centred on panelX if centre. */
    private static void label(Ui.Title t, double panelX, int panelY, String s, boolean centre, boolean bold) {
        double w = Ui.uniWidth(s, bold), left = centre ? panelX - w / 2 : panelX;
        Component c = bold ? Ui.uni(s) : Component.text(s).font(Ui.UNIFORM);
        t.add(x(left + 1), down(panelY + 1), c, w, SHADOW);
        t.add(x(left), down(panelY), c, w, WHITE);
    }

    /** Cobblemon's half-scaled text: vanilla's ascii at half size, as tightly spaced (its top 4 px below its line's top). */
    private static void small(Ui.Title t, double panelX, int panelY, String s) {
        t.add(x(panelX), down(panelY - 4), Component.text(Ui.tight(s)).font(Key.key("cobblemonlink", "small")), Ui.tightWidth(s), WHITE);
    }

    /** The small font is ASCII only: "Pokémon" -> "Pokemon". */
    private static String plain(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7e]", "");
    }
}
