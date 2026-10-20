package io.github.kahngjoonkoh.cobblemonlink;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Labels drawn with the UI pack's font (tools/build_ui.py): Cobblemon's tile art as glyphs, text laid over them with
 * negative spaces. The client renders all of it. Bedrock has no such font, so Bedrock players get plain text.
 */
final class Ui {
    static final Key FONT = Key.key("cobblemonlink", "ui");
    static final Key UNIFORM = Key.key("minecraft", "uniform"); // Cobblemon's DEFAULT_LARGE
    static final Key SMALL_LOW = Key.key("cobblemonlink", "small_low");
    static final Key PORTRAIT = Key.key("cobblemonlink", "portrait");
    static Map<String, Integer> portraits = Map.of(); // look -> its number (build_ui.py: tools/picture_ids.json)
    static Map<String, Integer> cards = Map.of();     // starter -> index (build_ui.py cards)
    static Map<String, Integer> tiles = Map.of();     // starter -> index (build_ui.py tiles)
    static Map<String, Integer> bodies = Map.of();    // look -> its number (build_ui.py bodies)
    static com.google.gson.JsonObject pcWidths;       // build_pc.py's pc_widths.json: font -> char -> advance
    static boolean partyPanel;                         // the UI pack has PartyPanel's art (build_hud.py)
    static final int MOVE_W = 93, PARTY_W = 95, MENU_W = 91; // glyph advances: Cobblemon's tile width + 1
    // full-size art for the cursor screen (build_ui.py): idle glyph, hover glyph = idle + 1
    static final char MENU = '\ue200', MOVE = '\ue210', PARTY = '\ue240', BACK = '\ue242', CURSOR = '\ue244', TYPE_BIG = '\ue250';
    static final List<String> TYPES = List.of("normal", "fire", "water", "grass", "electric", "ice", "fighting",
            "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");
    static final Map<String, Integer> TYPE_COLOR = Map.ofEntries(
            Map.entry("normal", 0xA8A878), Map.entry("fire", 0xF08030), Map.entry("water", 0x6890F0),
            Map.entry("grass", 0x78C850), Map.entry("electric", 0xF8D030), Map.entry("ice", 0x98D8D8),
            Map.entry("fighting", 0xC03028), Map.entry("poison", 0xA040A0), Map.entry("ground", 0xE0C068),
            Map.entry("flying", 0xA890F0), Map.entry("psychic", 0xF85888), Map.entry("bug", 0xA8B820),
            Map.entry("rock", 0xB8A038), Map.entry("ghost", 0x705898), Map.entry("dragon", 0x7038F8),
            Map.entry("dark", 0x705848), Map.entry("steel", 0xB8B8D0), Map.entry("fairy", 0xEE99AC));
    // vanilla ascii.png advances for ' '..'~' (measured from the 26.3 client); everything else counts as 6
    private static final String ASCII = "42466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";
    // uniform (Unifont) advances for ' '..'~': inked width / 2 + 1, computed from the 26.3 client's unifont.zip; others count 4
    private static final String UNI = "41344441224423244344444444223434444444444344444444444444444242442444443443343444444434444443134";

    /** The UI pack's portraits, numbered as build_ui.py numbers them: sorted by species name. */
    static void loadPortraits(java.nio.file.Path zip) throws java.io.IOException {
        try (var z = new java.util.zip.ZipFile(zip.toFile())) {
            cards = index(z, "assets/cobblemonlink/textures/font/card/", false);
            tiles = index(z, "assets/cobblemonlink/textures/font/tile/", false);
            var ids = z.getEntry("assets/cobblemonlink/picture_ids.json"); // the pictures live in the picture packs
            if (ids != null) try (var in = z.getInputStream(ids)) {
                var o = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                portraits = numbers(o.getAsJsonObject("portrait"));
                bodies = numbers(o.getAsJsonObject("body"));
            } else { // a UI pack from before the picture packs
                portraits = index(z, "assets/cobblemonlink/textures/font/portrait/", true);
                bodies = index(z, "assets/cobblemonlink/textures/font/body/", false); // unown__character_r is a body
            }
            partyPanel = z.getEntry("assets/cobblemonlink/textures/font/hud_slot.png") != null;
            var w = z.getEntry("assets/cobblemonlink/pc_widths.json");
            if (w != null) try (var in = z.getInputStream(w)) {
                pcWidths = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }

    static Map<String, Integer> numbers(com.google.gson.JsonObject o) {
        Map<String, Integer> m = new java.util.HashMap<>();
        if (o != null) o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsInt()));
        return m;
    }

    /** mirrored: the folder also holds each picture mirrored as <name>_r (portraits), which isn't counted. */
    static Map<String, Integer> index(java.util.zip.ZipFile z, String dir, boolean mirrored) {
        List<String> names = z.stream().map(java.util.zip.ZipEntry::getName)
                .filter(n -> n.startsWith(dir) && n.endsWith(".png") && !(mirrored && n.endsWith("_r.png")))
                .map(n -> n.substring(dir.length(), n.length() - 4)).sorted().toList();
        Map<String, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < names.size(); i++) m.put(names.get(i), i);
        return m;
    }

    /** Cobblemon's starter-screen card (window, platform, model, types; build_cards.py); null if none. 125 px tall. */
    static TextComponent card(String starter) {
        Integer i = cards.get(starter);
        return i == null ? null : Component.text(Character.toString(0xF7000 + i)).font(Key.key("cobblemonlink", "card"));
    }

    /** A starter's face for StarterScreen's list (build_ui.py tiles), 14 px from the line's top (ascent 7); null if none. */
    static TextComponent tile(String starter) {
        Integer i = tiles.get(starter);
        return i == null ? null : Component.text(Character.toString(0xF8000 + i)).font(Key.key("cobblemonlink", "tile"));
    }

    /** The portrait at 16 px, for text; null if none. */
    static TextComponent smallPortrait(String model) {
        Integer i = portraits.get(model);
        return i == null ? null : Component.text(Character.toString(0xF0000 + 2 * i)).font(Key.key("cobblemonlink", "portrait_small"));
    }

    /** Cobblemon's 28 px battle-tile portrait, facing right (yours) or mirrored (the wild side); null if none. */
    static TextComponent portrait(String species, boolean mirrored) {
        Integer i = portraits.get(species);
        return i == null ? null : Component.text(Character.toString(0xF0000 + 2 * i + (mirrored ? 1 : 0))).font(PORTRAIT);
    }

    static Lang lang = Lang.load(null); // the plugin's, with Cobblemon's languages

    /** A screen label in the player's language: Cobblemon's (or our) key, else the English. */
    static String tr(org.bukkit.entity.Player p, String key, String english) {
        return lang.getOr(CobblemonLink.loc(p), key, english);
    }

    /** A look's name in Bedrock texture paths: long ones shortened like build_bedrock_ui.py's short() (paths under 80). */
    static String bedrockName(String name) {
        if (name.length() <= 40) return name;
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-1").digest(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return name.substring(0, 31) + "_" + java.util.HexFormat.of().formatHex(h).substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Floodgate gives Bedrock players UUIDs with the top 64 bits zero. ponytail: breaks if Floodgate's uuid prefix is changed. */
    static boolean bedrock(Player p) { return p.getUniqueId().getMostSignificantBits() == 0; }

    static int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += c >= 32 && c < 127 ? ASCII.charAt(c - 32) - '0' : wide(c) ? 8 : 6;
        return w;
    }

    /** A full-width glyph (Hangul, kana, CJK, full-width forms): measured on the client, an advance of 8. */
    static boolean wide(char c) {
        return c >= 0x1100 && c <= 0x115F || c >= 0x2E80 && c <= 0xA4CF || c >= 0xAC00 && c <= 0xD7A3
                || c >= 0xF900 && c <= 0xFAFF || c >= 0xFE30 && c <= 0xFE4F || c >= 0xFF00 && c <= 0xFF60 || c >= 0xFFE0 && c <= 0xFFE6;
    }

    /** Width in the uniform font (UnihexProvider: inked width / 2 + 1). */
    static double uniWidth(String s, boolean bold) {
        double w = 0;
        for (char c : s.toCharArray()) // bold: half a pixel on a unifont glyph, a whole one on the space (include/space)
            w += (c >= 32 && c < 127 ? UNI.charAt(c - 32) - '0' : wide(c) ? 8 : 5) + (bold ? (c == ' ' ? 1 : 0.5) : 0);
        return w;
    }

    static Component uni(String s) { return Component.text(s, NamedTextColor.WHITE).font(UNIFORM).decorate(TextDecoration.BOLD); }

    /** Horizontal move of n px (negative = left). */
    static Component space(int n) {
        StringBuilder b = new StringBuilder();
        for (int left = Math.abs(n); left > 0; left -= 512) b.append((char) ((n < 0 ? 0xF000 : 0xF400) + Math.min(left, 512)));
        return glyphs(b.toString());
    }

    static TextComponent glyphs(String s) { return Component.text(s).font(FONT).shadowColor(ShadowColor.none()); }

    static Component typeIcon(String type) {
        int i = TYPES.indexOf(type);
        return i < 0 ? Component.empty() : glyphs(String.valueOf((char) (0xE010 + i))).color(NamedTextColor.WHITE);
    }

    /** Cobblemon's move tile tinted by type: [icon] Name ... 10/10, in its bold uniform font. */
    static Component moveTile(String name, String type, String pp, boolean disabled) {
        TextColor tint = disabled ? NamedTextColor.DARK_GRAY : TextColor.color(TYPE_COLOR.getOrDefault(type, 0xA8A878));
        return tile('\ue000', MOVE_W, tint, List.of(type), name, pp);
    }

    /** Cobblemon's party tile: [icons] Name Lv.5 ... 30/35. */
    static Component partyTile(List<String> types, String name, String hp) {
        return tile('\ue005', PARTY_W, NamedTextColor.WHITE, types, name, hp);
    }

    /**
     * A w-wide tile glyph, icons and the name on top, the PP/HP small in the bottom right (Cobblemon's second row;
     * a 20-px button has no room for it at full size). Exactly w wide in total, so it fills the button.
     */
    private static Component tile(char glyph, int w, TextColor tint, List<String> icons, String name, String right) {
        int start = 3 + 10 * icons.size() + 2, rightW = smallWidth(right);
        var b = Component.text().append(glyphs(String.valueOf(glyph)).color(tint), space(-w + 3));
        for (String t : icons) b.append(typeIcon(t));
        double end = start + uniWidth(name, true);
        b.append(space(2), uni(name), space((int) Math.round(w - 4 - rightW - end)));
        return b.append(Component.text(right, NamedTextColor.WHITE).font(SMALL_LOW), space(4)).build();
    }

    /** Cobblemon's back arrow. */
    static Component back() { return glyphs("\ue006").color(NamedTextColor.WHITE); }

    /** One of Cobblemon's Fight/Switch/Bag/Run tiles (0..3) with a label. Exactly MENU_W wide. */
    static Component menuTile(int which, String label) {
        return Component.text()
                .append(glyphs(String.valueOf((char) (0xE001 + which))).color(NamedTextColor.WHITE), space(-MENU_W + 6),
                        Component.text(label, NamedTextColor.WHITE), space(MENU_W - 6 - width(label)))
                .build();
    }

    /**
     * Text pinned to the screen's corners by the UI pack's text shader (build_ui.py TEXT_VSH), sent as the action bar.
     * Each piece is coloured (254, corner [+4 = shadow], y); the shader moves it and draws it white.
     */
    static final class Overlay {
        static final Key SMALL = Key.key("cobblemonlink", "small");
        static final int TL = 0, TR = 1, BL = 2, BR = 3, TC = 8;
        static final int WHITE = 254, RED = 252, DARK_GRAY = 251; // the shader's colour codes (build_ui.py TEXT_VSH)
        private final TextComponent.Builder b = Component.text();
        int fade; // 0..7: what's added next is drawn at (8 - fade) / 8 opacity
        int colour = WHITE; // what's added next is drawn white, red or dark grey
        private double cursor; // bold uniform text advances in half pixels

        /** x from the corner's left edge (negative from a right edge), y px from its top or bottom edge to the text's top. */
        Overlay add(int corner, int x, int y, Component c, double width) {
            int step = (int) Math.round(x - cursor);
            b.append(space(step), c.color(TextColor.color(colour, corner | fade << 5, y)).shadowColor(ShadowColor.none()));
            cursor += step + width;
            return this;
        }

        /** width = the glyph's advance: the client counts to its last visible column, plus 1. A wrong one shifts everything after it. */
        Overlay glyph(int corner, int x, int y, char g, int width) { return add(corner, x, y, glyphs(String.valueOf(g)), width); }

        /** Cobblemon's bold uniform label with its drop shadow (a dark copy 1 px down-right). */
        Overlay label(int corner, int x, int y, String s) {
            double w = uniWidth(s, true);
            add(corner + 4, x + 1, y + (corner >= BL ? -1 : 1), uni(s), w);
            return add(corner, x, y, uni(s), w);
        }

        Overlay text(int corner, int x, int y, String s) { return add(corner, x, y, Component.text(s), width(s)); }

        /** The party panel's mode (PartyPanel): x px from the screen's left edge, y px from its middle (-128..127). */
        Overlay mid(int x, int y, Component c, double width) {
            int step = (int) Math.round(x - cursor);
            b.append(space(step), c.color(TextColor.color(250, fade << 5, y + 128)).shadowColor(ShadowColor.none()));
            cursor += step + width;
            return this;
        }

        /** Half-size text, centred on x. */
        Overlay small(int corner, int x, int y, String s) {
            int w = smallWidth(s);
            return add(corner, x - w / 2, y, Component.text(s).font(SMALL), w);
        }

        /** Ends with a step back to the start: zero total width, so the action bar's origin is the screen centre. */
        Component build() { return b.append(space((int) -Math.round(cursor))).build(); }
    }

    /** Cobblemon's "Check out the starters!" toast, top right, with our command for its key (CobblemonLink.starterToast). */
    static Component starterToast() {
        Overlay o = new Overlay().glyph(Overlay.TR, -160, 0, '\ue271', 161).glyph(Overlay.TR, -152, 8, '\ue270', 15);
        o.colour = Overlay.RED;
        o.text(Overlay.TR, -130, 7, "Check out the starters!");
        o.colour = Overlay.DARK_GRAY;
        return o.text(Overlay.TR, -130, 18, "Type /starter").build();
    }

    /**
     * A chest title laid out like a screen (StarterScreen): pieces at x px right of the title's start, each moved down
     * by the text shader's (253, grey, b) code to b - 64 px below the title's line (build_ui.py TEXT_VSH).
     */
    static final class Title {
        private final TextComponent.Builder b = Component.text();
        private double cursor;

        /** width = the piece's advance; a wrong one shifts everything after it. */
        Title add(double x, int down, Component c, double width, int grey) {
            int step = (int) Math.round(x - cursor);
            b.append(space(step), c.color(TextColor.color(253, grey, down + 64)).shadowColor(ShadowColor.none()));
            cursor += step + width;
            return this;
        }

        Component build() { return b.append(space((int) -Math.round(cursor))).build(); }
    }

    /** Half-size text spaced as Cobblemon's half-scaled text: 1 px less after each letter (build_ui.py's U+E300). */
    static String tight(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(c).append('\ue300');
        return b.toString();
    }

    static int tightWidth(String s) { return smallWidth(s) - s.length(); }

    static int smallWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += (int) (0.5 + ((c >= 32 && c < 127 ? ASCII.charAt(c - 32) - '0' : 6) - 1) * 0.5) + 1;
        return w;
    }

    /** Word-wraps to Cobblemon's battle log width. */
    static List<String> wrap(String s, int max) { return wrap(s, max, Ui::width); }

    static List<String> wrap(String s, int max, java.util.function.ToIntFunction<String> width) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            if (!line.isEmpty() && width.applyAsInt(line + " " + word) > max) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}
