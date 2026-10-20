package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.text.Normalizer;
import net.minecraft.network.chat.Component;

/**
 * The Paper version's 5-row chest art helpers (PcScreen / PokedexScreen): the chest's top left is (-PX, -PY) on the
 * 182 x 222 panel; a slot's column c, row r (rows 5-7 the inventory, 8 the hotbar) is at x(c), y(r), raw slot raw(c, r).
 */
final class Art {
    static final int PX = -3, PY = -10, WHITE = 255;

    private Art() {}

    static int raw(int c, int r) { return r < 5 ? r * 9 + c : r < 8 ? 45 + (r - 5) * 9 + c : 72 + c; }

    static int x(int c) { return 11 + 18 * c; }

    static int y(int r) { return r < 5 ? 28 + 18 * r : r < 8 ? 131 + 18 * (r - 5) : 189; }

    static void glyph(Ui.Title t, double x, int y, char c, int ascent) { put(t, x, y, Ui.glyphs(String.valueOf(c)), width("ui", c), ascent); }

    static void put(Ui.Title t, double x, int y, Component c, double advance, int ascent) { put(t, x, y, c, advance, ascent, WHITE); }

    static void put(Ui.Title t, double x, int y, Component c, double advance, int ascent, int grey) {
        t.add(x + PX - 8, y + PY - 6 - (7 - ascent), c, advance, grey);
    }

    /** One of build_pc.py's text fonts (shadow baked in), each letter then U+E300; centred on x if centre. Top y. */
    static void text(Ui.Title t, String font, double x, int y, String s, boolean centre) { text(t, font, x, y, s, centre, WHITE); }

    static void text(Ui.Title t, String font, double x, int y, String s, boolean centre, int grey) {
        s = plain(s);
        double w = width(font, s);
        int ascent = font.equals("pc_label") ? 7 : 6;
        put(t, centre ? x - w / 2 : x, y, Ui.text(Ui.tight(s), Ui.id(font)), w, ascent, grey);
    }

    static double width(String font, String s) {
        double w = 0;
        for (char c : s.toCharArray()) w += width(font, c) - 1;
        return w;
    }

    static int width(String font, char c) {
        var f = Ui.pcWidths == null ? null : Ui.pcWidths.getAsJsonObject(font);
        var e = f == null ? null : f.get(String.valueOf(c));
        return e == null ? 4 : e.getAsInt();
    }

    static String fit(String font, String s, int max) {
        if (width(font, s) <= max) return s;
        while (!s.isEmpty() && width(font, s + "...") > max) s = s.substring(0, s.length() - 1);
        return s.strip() + "...";
    }

    /** The PC fonts are ASCII plus ♀ ♂ é. */
    static String plain(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == 'é' || c == '♀' || c == '♂' || c >= 0x20 && c < 0x7f) b.append(c);
            else b.append(Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("[^\\x20-\\x7e]", ""));
        }
        return b.toString();
    }

    static Component body(String look, boolean big) {
        Integer b = Ui.bodies.get(look);
        return b == null ? null : Ui.text(Character.toString(0xF9000 + b), Ui.id(big ? "body_big" : "body"));
    }

    static Component level(int level) { return Ui.text(String.valueOf((char) (0xE000 + level)), Ui.id("pc_lv")); }
}
