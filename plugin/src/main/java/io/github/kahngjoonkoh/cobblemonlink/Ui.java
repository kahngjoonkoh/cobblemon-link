package io.github.kahngjoonkoh.cobblemonlink;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/**
 * Labels drawn with the UI pack's font (tools/build_ui.py): Cobblemon's tile art as glyphs, text laid over them with
 * negative spaces. The client renders all of it. Bedrock has no such font, so Bedrock players get plain text.
 */
final class Ui {
    static final Key FONT = Key.key("cobblemonlink", "ui");
    static final int MOVE_W = 141, MENU_W = 111, HP_W = 48; // glyph advances: image width + 1
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

    /** Floodgate gives Bedrock players UUIDs with the top 64 bits zero. ponytail: breaks if Floodgate's uuid prefix is changed. */
    static boolean bedrock(Player p) { return p.getUniqueId().getMostSignificantBits() == 0; }

    static int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += c >= 32 && c < 127 ? ASCII.charAt(c - 32) - '0' : 6;
        return w;
    }

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

    /** Cobblemon's move tile tinted by type: [icon] Name ........ 10/10. */
    static Component moveTile(String name, String type, String pp, boolean disabled) {
        TextColor tint = disabled ? NamedTextColor.DARK_GRAY : TextColor.color(TYPE_COLOR.getOrDefault(type, 0xA8A878));
        return tile('\ue000', tint, List.of(type), name, pp);
    }

    /** Cobblemon's party tile: [icons] Name Lv.5 ........ 30/35. */
    static Component partyTile(List<String> types, String name, String hp) {
        return tile('\ue005', NamedTextColor.WHITE, types, name, hp);
    }

    /** A MOVE_W-wide tile glyph with icons and text laid over it; exactly MOVE_W wide, so it fits the button. */
    private static Component tile(char glyph, TextColor tint, List<String> icons, String name, String right) {
        int used = 4 + 10 * icons.size() + 3 + width(name) + 5;
        if (used + width(right) > MOVE_W) right = "";
        var b = Component.text().append(glyphs(String.valueOf(glyph)).color(tint), space(-MOVE_W + 4));
        for (String t : icons) b.append(typeIcon(t));
        return b.append(space(3), Component.text(name, NamedTextColor.WHITE), space(MOVE_W - used - width(right)),
                Component.text(right, NamedTextColor.WHITE), space(5)).build();
    }

    /** One of Cobblemon's Fight/Switch/Bag/Run tiles (0..3) with a label. Exactly MENU_W wide. */
    static Component menuTile(int which, String label) {
        return Component.text()
                .append(glyphs(String.valueOf((char) (0xE001 + which))).color(NamedTextColor.WHITE), space(-MENU_W + 6),
                        Component.text(label, NamedTextColor.WHITE), space(MENU_W - 6 - width(label)))
                .build();
    }

    /** [type icons] Name Lv.5  [HP bar]  30/35 */
    static Component info(String name, List<String> types, float ratio, String hpText, String status) {
        int fill = Math.round(ratio * HP_W);
        TextColor c = ratio > 0.5f ? TextColor.color(0x4CD964) : ratio > 0.2f ? TextColor.color(0xF8D030) : TextColor.color(0xF04030);
        var b = Component.text();
        for (String t : types) b.append(typeIcon(t), space(1));
        b.append(Component.text(" " + name + "  "), glyphs("\ue040"), space(-HP_W - 2));
        if (fill > 0) b.append(glyphs(String.valueOf((char) (0xE040 + fill))).color(c));
        b.append(space(HP_W + 2 - (fill > 0 ? fill + 1 : 0)), Component.text(hpText.isEmpty() ? "" : "  " + hpText));
        if (!status.isEmpty()) b.append(Component.text("  " + status.toUpperCase(), NamedTextColor.LIGHT_PURPLE));
        return b.build();
    }
}
