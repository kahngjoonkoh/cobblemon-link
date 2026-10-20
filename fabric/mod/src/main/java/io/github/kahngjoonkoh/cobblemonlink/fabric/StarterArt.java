package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import java.text.Normalizer;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's StarterSelectionScreen for vanilla Java (standard level and up), the Paper version's StarterScreen: a
 * 4-row chest whose title draws the whole screen and whose slots are its buttons. 4 regions in view, a header and 3
 * tiles a region; the bottom row: "I choose you!" and the page arrows. Picks go to Cobblemon's StarterHandler.
 * ponytail: a port of the Paper class; still pictures; Esc or E closes it.
 */
final class StarterArt {
    static final int PX = -13, PY = -15;
    private static final char BASE = '', BLOCK = '', SELECTED = '', THUMB = '', UP = '',
            DOWN = '', BALL = '';
    private static final int WHITE = 255, SHADOW = 63, DIM = 90, VISIBLE = 4;
    private static final int[][] TILE_SLOTS = {{6, 15}, {24, 33}, {42}, {60}};
    private static final int[] TILE_Y = {45, 81, 117, 153};
    private static final int UP_SLOT = 63 + 6, DOWN_SLOT = 63 + 8;
    private static final List<Integer> CHOOSE_SLOTS = List.of(63, 64, 65, 66);

    private StarterArt() {}

    /** top: the first region shown; region, index: the selected starter. */
    static void open(ServerPlayer p, List<Starters.Region> regions, int top, int region, int index) {
        Chest m = new Chest(4, title(regions, top, region, index)).hideInventory();
        for (int k = 0; k < VISIBLE && top + k < regions.size(); k++) {
            List<String> species = regions.get(top + k).species();
            int r = top + k;
            for (int j = 0; j < species.size() && j < 3; j++) {
                int jj = j;
                for (int slot : TILE_SLOTS[k]) m.on(slot + j, b -> open(p, regions, top, r, jj));
            }
        }
        int last = Math.max(0, regions.size() - VISIBLE);
        if (top > 0) m.on(UP_SLOT, b -> open(p, regions, Math.max(0, top - VISIBLE), region, index));
        if (top < last) m.on(DOWN_SLOT, b -> open(p, regions, Math.min(last, top + VISIBLE), region, index));
        for (int slot : CHOOSE_SLOTS) m.on(slot, b -> {
            p.closeContainer();
            Cobblemon.INSTANCE.getStarterHandler().chooseStarter(p, regions.get(region).category(), index);
        });
        m.open(p);
    }

    static Component title(List<Starters.Region> regions, int top, int region, int index) {
        String selected = regions.get(region).species().get(index);
        var sp = PokemonSpecies.getByName(selected);
        Ui.Title t = new Ui.Title();
        art(t, 0, 0, BASE, 203);
        var card = Ui.card(selected);
        if (card != null) t.add(x(1), down(17), card, 111, WHITE);
        for (int k = 0; k < VISIBLE && top + k < regions.size(); k++) {
            Starters.Region r = regions.get(top + k);
            int y = TILE_Y[k];
            art(t, 119, y - 9, BLOCK, 73);
            small(t, 155 - Ui.tightWidth(plain(r.name())) / 2.0, y - 8, plain(r.name()));
            for (int j = 0; j < r.species().size() && j < 3; j++) {
                String s = r.species().get(j);
                var face = Ui.tile(s);
                if (face != null) t.add(x(130 + 18 * j), down(y + 1), face, 15, WHITE);
                else if ((face = Ui.smallPortrait(s)) != null) t.add(x(129 + 18 * j), down(y + 5), face, 17, WHITE);
                if (top + k == region && j == index) art(t, 129 + 18 * j, y, SELECTED, 17);
            }
        }
        int last = Math.max(0, regions.size() - VISIBLE);
        art(t, 194, 32 + (144 - 64) * top / Math.max(1, last), THUMB, 4);
        if (last > 0) {
            t.add(x(134), down(183), Ui.glyphs(String.valueOf(UP)), 8, top > 0 ? WHITE : DIM);
            t.add(x(170), down(183), Ui.glyphs(String.valueOf(DOWN)), 8, top < last ? WHITE : DIM);
        }
        art(t, 4, 7, BALL, 9);
        label(t, 144.5, 16, "Starter Selection", true, false);
        label(t, 56, 181, "I choose you!", true, true);
        List<String> lines = Ui.wrap(plain(Starters.desc(selected)), 96, Ui::tightWidth);
        int n = Math.min(6, lines.size());
        double gap = n <= 5 ? 5.5 : 5, first = n <= 5 ? 143 : 142;
        for (int i = 0; i < n; i++)
            small(t, 8, (int) Math.round(first + gap * i), i == 5 && lines.size() > 6 ? lines.get(i) + "..." : lines.get(i));
        label(t, 70, 5, String.format("#%04d", sp == null ? 0 : sp.getNationalPokedexNumber()), false, true);
        label(t, 14, 6, sp == null ? selected : Lang.resolve(sp.getTranslatedName()).getString(), false, true);
        return t.build();
    }

    private static double x(double panelX) { return panelX + PX - 8; }

    private static int down(int panelY) { return panelY + PY - 6; }

    private static void art(Ui.Title t, double panelX, int panelY, char c, int advance) {
        t.add(x(panelX), down(panelY), Ui.glyphs(String.valueOf(c)), advance, WHITE);
    }

    private static void label(Ui.Title t, double panelX, int panelY, String s, boolean centre, boolean bold) {
        double w = Ui.uniWidth(s, bold), left = centre ? panelX - w / 2 : panelX;
        Component c = bold ? Ui.uni(s) : Ui.text(s, Ui.UNIFORM);
        t.add(x(left + 1), down(panelY + 1), c, w, SHADOW);
        t.add(x(left), down(panelY), c, w, WHITE);
    }

    private static void small(Ui.Title t, double panelX, int panelY, String s) {
        t.add(x(panelX), down(panelY - 4), Ui.text(Ui.tight(s), Ui.Overlay.SMALL), Ui.tightWidth(s), WHITE);
    }

    private static String plain(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7e]", "");
    }
}
