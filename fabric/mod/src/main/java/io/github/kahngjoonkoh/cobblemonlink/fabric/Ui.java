package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

/**
 * The Paper version's Java UI art (plugin/.../Ui.java) for 1.21.1: Cobblemon's tile art as font glyphs, text laid over
 * them with negative spaces, pieces moved by the UI pack's text shader through colour codes (tools/ui_pack.py). The
 * pack (config/cobblemonlink/ui) is merged into Polymer's.
 * ponytail: a port of the Paper class with vanilla components; 1.21.1 text has no shadow switch, so the shader hides
 * the shadows of coded pieces instead.
 */
public final class Ui {
    static final ResourceLocation FONT = id("ui"), UNIFORM = ResourceLocation.withDefaultNamespace("uniform"),
            SMALL_LOW = id("small_low"), PORTRAIT = id("portrait");
    static Map<String, Integer> portraits = Map.of(), cards = Map.of(), tiles = Map.of(), bodies = Map.of();
    static JsonObject pcWidths;
    static boolean loaded;
    static final int MOVE_W = 93, PARTY_W = 95, MENU_W = 91;
    static final char MENU = '', MOVE = '', PARTY = '', BACK = '', CURSOR = '', TYPE_BIG = '';
    static final List<String> TYPES = BattleUi.TYPES;
    static final Map<String, Integer> TYPE_COLOR = Map.ofEntries(
            Map.entry("normal", 0xA8A878), Map.entry("fire", 0xF08030), Map.entry("water", 0x6890F0),
            Map.entry("grass", 0x78C850), Map.entry("electric", 0xF8D030), Map.entry("ice", 0x98D8D8),
            Map.entry("fighting", 0xC03028), Map.entry("poison", 0xA040A0), Map.entry("ground", 0xE0C068),
            Map.entry("flying", 0xA890F0), Map.entry("psychic", 0xF85888), Map.entry("bug", 0xA8B820),
            Map.entry("rock", 0xB8A038), Map.entry("ghost", 0x705898), Map.entry("dragon", 0x7038F8),
            Map.entry("dark", 0x705848), Map.entry("steel", 0xB8B8D0), Map.entry("fairy", 0xEE99AC));
    // vanilla ascii.png advances for ' '..'~'; everything else counts as 6 (the same in 1.21.1 and 26.3)
    private static final String ASCII = "42466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";
    // uniform (Unifont) advances for ' '..'~': inked width / 2 + 1; others count 4
    private static final String UNI = "41344441224423244344444444223434444444444344444444444444444242442444443443343444444434444443134";

    private Ui() {}

    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("cobblemonlink", path); }

    /** At startup: the pack into Polymer's, and the art's indexes (pictures, cards, tiles, PC font widths). */
    static void register(Path dir) {
        if (!Files.exists(dir.resolve("pack.mcmeta"))) {
            System.out.println("[cobblemonlink] no UI pack in " + dir + " (tools/ui_pack.py): vanilla Java gets plain menus");
            return;
        }
        try {
            Path tex = dir.resolve("assets/cobblemonlink/textures/font");
            cards = index(tex.resolve("card"));
            tiles = index(tex.resolve("tile"));
            Path ids = dir.resolve("assets/cobblemonlink/picture_ids.json");
            if (Files.exists(ids)) {
                JsonObject o = JsonParser.parseString(Files.readString(ids)).getAsJsonObject();
                portraits = numbers(o.getAsJsonObject("portrait"));
                bodies = numbers(o.getAsJsonObject("body"));
            }
            Path w = dir.resolve("assets/cobblemonlink/pc_widths.json");
            if (Files.exists(w)) pcWidths = JsonParser.parseString(Files.readString(w)).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(b -> b.copyFromPath(dir));
        loaded = true;
        System.out.println("[cobblemonlink] UI pack: " + portraits.size() + " portraits, " + cards.size() + " cards");
    }

    private static Map<String, Integer> numbers(JsonObject o) {
        Map<String, Integer> m = new HashMap<>();
        if (o != null) o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsInt()));
        return m;
    }

    private static Map<String, Integer> index(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return Map.of();
        try (Stream<Path> s = Files.list(dir)) {
            List<String> names = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".png"))
                    .map(n -> n.substring(0, n.length() - 4)).sorted().toList();
            Map<String, Integer> m = new HashMap<>();
            for (int i = 0; i < names.size(); i++) m.put(names.get(i), i);
            return m;
        }
    }

    static MutableComponent text(String s, ResourceLocation font) { return Component.literal(s).withStyle(st -> st.withFont(font)); }

    static MutableComponent colour(Component c, int rgb) { return c.copy().withStyle(st -> st.withColor(TextColor.fromRgb(rgb))); }

    static MutableComponent card(String starter) {
        Integer i = cards.get(starter);
        return i == null ? null : text(Character.toString(0xF7000 + i), id("card"));
    }

    static MutableComponent tile(String starter) {
        Integer i = tiles.get(starter);
        return i == null ? null : text(Character.toString(0xF8000 + i), id("tile"));
    }

    static MutableComponent smallPortrait(String model) {
        Integer i = portraits.get(model);
        return i == null ? null : text(Character.toString(0xF0000 + 2 * i), id("portrait_small"));
    }

    static MutableComponent portrait(String model, boolean mirrored) {
        Integer i = portraits.get(model);
        return i == null ? null : text(Character.toString(0xF0000 + 2 * i + (mirrored ? 1 : 0)), PORTRAIT);
    }

    static int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += c >= 32 && c < 127 ? ASCII.charAt(c - 32) - '0' : 6;
        return w;
    }

    static double uniWidth(String s, boolean bold) {
        double w = 0;
        for (char c : s.toCharArray()) w += (c >= 32 && c < 127 ? UNI.charAt(c - 32) - '0' : 4) + (bold ? (c == ' ' ? 1 : 0.5) : 0);
        return w;
    }

    static MutableComponent uni(String s) { return Component.literal(s).withStyle(st -> st.withFont(UNIFORM).withColor(0xFFFFFF).withBold(true)); }

    /** Horizontal move of n px (negative = left). */
    static MutableComponent space(int n) {
        StringBuilder b = new StringBuilder();
        for (int left = Math.abs(n); left > 0; left -= 512) b.append((char) ((n < 0 ? 0xF000 : 0xF400) + Math.min(left, 512)));
        return glyphs(b.toString());
    }

    static MutableComponent glyphs(String s) { return text(s, FONT); }

    static int smallWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += (int) (0.5 + ((c >= 32 && c < 127 ? ASCII.charAt(c - 32) - '0' : 6) - 1) * 0.5) + 1;
        return w;
    }

    /** Half-size text spaced as Cobblemon's: 1 px less after each letter (U+E300). */
    static String tight(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(c).append('');
        return b.toString();
    }

    static int tightWidth(String s) { return smallWidth(s) - s.length(); }

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

    /**
     * Text pinned to the screen by the text shader, sent as the action bar: each piece coloured (254, corner
     * [+4 = shadow], y); the shader moves it and draws it white (or red, dark grey).
     */
    static final class Overlay {
        static final ResourceLocation SMALL = id("small");
        static final int TL = 0, TR = 1, BL = 2, BR = 3, TC = 8;
        static final int WHITE = 254, RED = 252, DARK_GRAY = 251;
        private final MutableComponent b = Component.empty();
        int fade, colour = WHITE;
        private double cursor;

        Overlay add(int corner, int x, int y, Component c, double width) {
            int step = (int) Math.round(x - cursor);
            b.append(space(step)).append(colour(c, colour << 16 | (corner | fade << 5) << 8 | y));
            cursor += step + width;
            return this;
        }

        Overlay glyph(int corner, int x, int y, char g, int width) { return add(corner, x, y, glyphs(String.valueOf(g)), width); }

        Overlay label(int corner, int x, int y, String s) {
            double w = uniWidth(s, true);
            add(corner + 4, x + 1, y + (corner >= BL ? -1 : 1), uni(s), w);
            return add(corner, x, y, uni(s), w);
        }

        Overlay text(int corner, int x, int y, String s) { return add(corner, x, y, Component.literal(s), width(s)); }

        /** The party panel's mode: x px from the screen's left edge, y px from its middle (-128..127). */
        Overlay mid(int x, int y, Component c, double width) {
            int step = (int) Math.round(x - cursor);
            b.append(space(step)).append(colour(c, 250 << 16 | (fade << 5) << 8 | (y + 128)));
            cursor += step + width;
            return this;
        }

        Overlay small(int corner, int x, int y, String s) {
            int w = smallWidth(s);
            return add(corner, x - w / 2, y, Ui.text(s, SMALL), w);
        }

        Component build() { return b.append(space((int) -Math.round(cursor))); }
    }

    /** Cobblemon's "Check out the starters!" toast, top right, with our command for its key. */
    static Component starterToast() {
        Overlay o = new Overlay().glyph(Overlay.TR, -160, 0, '', 161).glyph(Overlay.TR, -152, 8, '', 15);
        o.colour = Overlay.RED;
        o.text(Overlay.TR, -130, 7, "Check out the starters!");
        o.colour = Overlay.DARK_GRAY;
        return o.text(Overlay.TR, -130, 18, "Type /starter").build();
    }

    /** A chest title laid out like a screen: pieces x px right of the title's start, moved down by the shader (253). */
    static final class Title {
        private final MutableComponent b = Component.empty();
        private double cursor;

        Title add(double x, int down, Component c, double width, int grey) {
            int step = (int) Math.round(x - cursor);
            b.append(space(step)).append(colour(c, 253 << 16 | grey << 8 | (down + 64)));
            cursor += step + width;
            return this;
        }

        Component build() { return b.append(space((int) -Math.round(cursor))); }
    }
}
