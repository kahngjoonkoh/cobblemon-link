package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's PC for vanilla Java (standard level and up), the Paper version's PcScreen art: a 5-row chest whose title
 * draws the screen. The box on columns 0-5, the party on 7-8, sort and type filter under the party, ◀ name ▶ and
 * Release on the first inventory row, the selected Pokémon in the strip below. Clicking the name: the next wallpaper
 * (ponytail: no text field for its name on Java; Bedrock's form renames).
 */
final class PcArt {
    private static final char BASE = '', RING = '', RELEASE = '', HALF = '', SORT = '',
            FILTER_ALL = '', BALL = '', MALE = '', FEMALE = '', WALL = '',
            TYPE_FILTER = '', TYPE_INFO = '', TYPE_CELL = '';
    static final int SORT_SLOT = 43, FILTER_SLOT = 44, PREV = 45, NEXT = 50, YES = 52, NO = 53;
    static final List<Integer> NAME_SLOTS = List.of(46, 47, 48, 49);

    private PcArt() {}

    static int boxSlot(int s) { return s / 6 * 9 + s % 6; }

    static int partySlot(int i) { return i / 2 * 9 + 7 + i % 2; }

    static void open(ServerPlayer p, Pc.View v) {
        PCBox box = Pc.pc(p).getBoxes().get(v.box);
        Pokemon sel = null;
        if (v.selected != null) {
            Pc.Loc at = Pc.where(p, v.selected);
            sel = at == null ? null : Pc.get(p, at);
        }
        Chest m = new Chest(5, title(p, v, box, sel)).hideInventory();
        for (int s = 0; s < 30; s++) {
            Pc.Loc at = new Pc.Loc(v.box, s);
            m.on(boxSlot(s), b -> Pc.click(p, at));
        }
        for (int i = 0; i < 6; i++) {
            Pc.Loc at = new Pc.Loc(-1, i);
            m.on(partySlot(i), b -> Pc.click(p, at));
        }
        m.on(SORT_SLOT, b -> Pc.sort(p));
        m.on(FILTER_SLOT, b -> Pc.cycleType(p, b == 1));
        m.on(PREV, b -> Pc.page(p, -1));
        m.on(NEXT, b -> Pc.page(p, 1));
        NAME_SLOTS.forEach(s -> m.on(s, b -> Pc.nextWallpaper(p)));
        if (sel != null) {
            m.on(YES, b -> Pc.release(p, true));
            m.on(NO, b -> Pc.release(p, !v.confirm));
        }
        m.open(p);
    }

    private static boolean shown(Pc.View v, Pokemon m) {
        if (v.type == null) return true;
        for (var t : m.getTypes()) if (JavaScreens.type(t).equals(v.type)) return true;
        return false;
    }

    static Component title(ServerPlayer p, Pc.View v, PCBox box, Pokemon sel) {
        Ui.Title t = new Ui.Title();
        Art.glyph(t, 0, 0, BASE, 7);
        int w = Pc.WALLPAPERS.indexOf(Pc.wallpaperOf(box));
        if (w >= 0 && w != 4) Art.glyph(t, 9, 26, (char) (WALL + w), 7);
        for (int s = 0; s < 30; s++) {
            Pokemon m = box.get(s);
            if (m != null && shown(v, m)) cell(t, Art.x(s % 6), Art.y(s / 6), m, m == sel);
        }
        for (int i = 0; i < 6; i++) {
            Pokemon m = Pc.party(p).get(new PartyPosition(i));
            if (m != null) cell(t, Art.x(7 + i % 2), Art.y(i / 2), m, m == sel);
        }
        Art.glyph(t, Art.x(7) + 3, Art.y(4) + 4, (char) (SORT + Math.max(0, v.sort)), 7);
        Art.glyph(t, Art.x(8) + 3, Art.y(4) + 4, v.type == null ? FILTER_ALL : (char) (TYPE_FILTER + Ui.TYPES.indexOf(v.type)), 7);
        Art.text(t, "pc_label", 64, 130, Pc.boxName(box, v.box), true);
        if (sel != null) {
            if (v.confirm) {
                Art.glyph(t, Art.x(7), 131, HALF, 7);
                Art.glyph(t, Art.x(8), 131, HALF, 7);
                Art.text(t, "pc_label", Art.x(7) + 8.5, 134, "Yes", true);
                Art.text(t, "pc_label", Art.x(8) + 8.5, 134, "No", true);
            } else {
                Art.glyph(t, Art.x(7), 131, RELEASE, 7);
                Art.text(t, "pc_label", Art.x(7) + 17, 134, "Release", true);
            }
            info(t, sel, v.confirm);
        }
        return t.build();
    }

    private static void cell(Ui.Title t, int x, int y, Pokemon m, boolean selected) {
        var b = Art.body(Summary.look(m), false);
        if (b != null) Art.put(t, x, y, b, 17, 7);
        Art.put(t, x, y, Art.level(m.getLevel()), Art.width("pc_lv", (char) (0xE000 + m.getLevel())), 4);
        int j = 0;
        for (var type : m.getTypes()) if (j < 2) Art.glyph(t, x + 13 - 3 * j++, y, (char) (TYPE_CELL + Ui.TYPES.indexOf(JavaScreens.type(type))), 4);
        if (selected) Art.glyph(t, x - 1, y - 1, RING, 7);
    }

    private static void info(Ui.Title t, Pokemon m, boolean confirm) {
        Art.text(t, "pc_bold", 7, 147, "Lv." + m.getLevel(), false);
        Art.glyph(t, 5, 155, BALL, 6);
        Art.text(t, "pc_bold", 12, 155, confirm ? "Release?" : BattleUi.name(m), false);
        if (m.getGender() != Gender.GENDERLESS) Art.glyph(t, 48, 157, m.getGender() == Gender.MALE ? MALE : FEMALE, 4);
        var b = Art.body(Summary.look(m), true);
        if (b != null) Art.put(t, 6, 166, b, 45, 7);
        List<String> types = new ArrayList<>();
        m.getTypes().forEach(ty -> types.add(Lang.resolve(ty.getDisplayName()).getString()));
        String[][] rows = {{"Type", String.join(" / ", types)}, {"Nature", Lang.get(m.getNature().getDisplayName())},
                {"Ability", Lang.get(m.getAbility().getDisplayName())},
                {"Moves", String.join(", ", m.getMoveSet().getMoves().stream().map(mv -> Lang.resolve(mv.getDisplayName()).getString()).toList())}};
        for (int i = 0; i < rows.length; i++) {
            int y = 152 + 3 + 15 * i;
            Art.text(t, "pc_bold", 57, y, rows[i][0], false);
            String value = Art.fit("pc_text", Art.plain(rows[i][1]), 121 - (i == 0 ? 16 : 0));
            Art.text(t, "pc_text", 57, y + 7, value, false);
            if (i == 0) {
                double x = 57 + Art.width("pc_text", value) + 3;
                for (var ty : m.getTypes()) {
                    Art.glyph(t, x, y + 7, (char) (TYPE_INFO + Ui.TYPES.indexOf(JavaScreens.type(ty))), 7);
                    x += 8;
                }
            }
        }
    }
}
