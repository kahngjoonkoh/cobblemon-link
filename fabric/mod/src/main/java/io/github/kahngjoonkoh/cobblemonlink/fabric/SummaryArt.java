package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's Summary for vanilla Java (standard level and up), the Paper version's SummaryScreen art: a 5-row chest
 * whose title draws the screen. Name card (click: nickname... ponytail: no text field on Java, so it is plain), the
 * tabs, the picture and Evolve on the left, the tab's page on the right (Moves: click two moves to swap them), the
 * party on the inventory rows, Send out / Lead / To PC on the hotbar.
 */
final class SummaryArt {
    private static final char BASE = '', TAB_ON = '', TAB_ICON = '', PAGE_INFO = '', PAGE_FRAME = '',
            PAGE_STATS = '', MOVE = '', MOVE_SEL = '', EVOLVE_PLATE = '', PLATE = '', SHINY = '',
            BALL = '', MALE = '', FEMALE = '', RING = '', TYPE_INFO = '', BAR = '';
    private static final String[] SHORT = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
    private static final String[] STAT_NAMES = {"HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"};
    private static final Stats[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
    private static final Map<UUID, Integer> PICKED = new HashMap<>();

    private SummaryArt() {}

    static void open(ServerPlayer p, List<Pokemon> party, int i, int tab) {
        Pokemon m = party.get(i);
        boolean battling = BattleRegistry.getBattleByParticipatingPlayer(p) != null, out = m.getEntity() != null;
        if (tab != Summary.MOVES) PICKED.remove(p.getUUID());
        Integer pick = PICKED.get(p.getUUID());
        String[] actions = {battling || m.isFainted() && !out ? "" : out ? "Recall" : "Send out", battling || i == 0 ? "" : "Lead",
                battling || party.size() <= 1 ? "" : "To PC"};
        var evo = m.getEvolutionProxy().server();
        boolean evolve = !evo.isEmpty() && !battling;
        Chest menu = new Chest(5, title(p, party, m, i, tab, pick, actions, evolve)).hideInventory();
        for (int t = 0; t < 3; t++) {
            int tt = t;
            for (int c = 0; c < 2; c++) menu.on(Art.raw(3 + 2 * t + c, 0), b -> Summary.open(p, i, tt));
        }
        if (evolve) for (int c = 1; c < 3; c++) menu.on(Art.raw(c, 4), b -> {
            if (!evo.isEmpty()) evo.start(evo.iterator().next());
            p.closeContainer();
        });
        for (int k = 0; k < party.size(); k++) {
            int kk = k;
            menu.on(Art.raw(k % 3, 6 + k / 3), b -> Summary.open(p, kk, tab));
        }
        if (tab == Summary.MOVES && !battling)
            for (int k = 0; k < m.getMoveSet().getMoves().size(); k++) {
                int kk = k;
                for (int c = 3; c < 9; c++) menu.on(Art.raw(c, 1 + k), b -> {
                    Integer was = PICKED.remove(p.getUUID());
                    if (was == null) PICKED.put(p.getUUID(), kk);
                    else if (was != kk) m.getMoveSet().swapMove(was, kk);
                    Summary.open(p, i, tab);
                });
            }
        Runnable[] act = {() -> {
            if (m.getEntity() != null) m.recall();
            else m.sendOutWithAnimation(p, p.serverLevel(), p.position().add(p.getLookAngle().multiply(2, 0, 2)), null, true, null, e -> kotlin.Unit.INSTANCE);
            Summary.open(p, i, tab);
        }, () -> {
            var store = Cobblemon.INSTANCE.getStorage().getParty(p);
            var list = store.toGappyList();
            store.swap(new PartyPosition(list.indexOf(party.getFirst())), new PartyPosition(list.indexOf(m)));
            Summary.open(p, 0, tab);
        }, () -> {
            if (m.getEntity() != null) m.recall();
            if (Cobblemon.INSTANCE.getStorage().getParty(p).remove(m) && !Cobblemon.INSTANCE.getStorage().getPC(p).add(m))
                Cobblemon.INSTANCE.getStorage().getParty(p).add(m);
            Summary.open(p, Math.max(0, i - 1), tab);
        }};
        for (int a = 0; a < 3; a++) {
            if (actions[a].isEmpty()) continue;
            Runnable r = act[a];
            for (int c = 0; c < 3; c++) menu.on(Art.raw(3 * a + c, 8), b -> r.run());
        }
        menu.open(p);
    }

    private static Component title(ServerPlayer p, List<Pokemon> party, Pokemon m, int i, int tab, Integer pick, String[] actions, boolean evolve) {
        Ui.Title t = new Ui.Title();
        int x = Art.x(0), cardX = x - 3, cardY = Art.y(0) - 9;
        Art.glyph(t, 0, 0, BASE, 7);
        int tx = Art.x(3 + 2 * tab) - 1;
        Art.glyph(t, tx, Art.y(0) + 4, TAB_ON, 7);
        Art.glyph(t, tx + 12, Art.y(0) + 6, (char) (TAB_ICON + tab), 7);
        Art.text(t, "pc_bold", cardX + 5, cardY + 1, "Lv." + m.getLevel(), false);
        Art.glyph(t, cardX + 4, cardY + 11, BALL, 6);
        Art.text(t, "pc_bold", cardX + 11, cardY + 11, Art.fit("pc_bold", BattleUi.name(m), m.getShiny() ? 30 : 36), false);
        if (m.getShiny()) Art.glyph(t, cardX + 43, cardY + 10, SHINY, 7);
        if (m.getGender() != Gender.GENDERLESS) Art.glyph(t, cardX + 51, cardY + 13, m.getGender() == Gender.MALE ? MALE : FEMALE, 4);
        var b = Art.body(Summary.look(m), true);
        if (b != null) Art.put(t, 14, 50, b, 45, 7);
        int k0 = 0;
        for (var type : m.getTypes()) Art.glyph(t, 12 + 8 * k0++, 47, (char) (TYPE_INFO + Ui.TYPES.indexOf(JavaScreens.type(type))), 7);
        if (evolve) {
            Art.glyph(t, Art.x(1) - 1, Art.y(4) - 1, EVOLVE_PLATE, 7);
            Art.text(t, "pc_bold", Art.x(1) + 18, Art.y(4) + 5, "Evolve", true);
        }
        Art.text(t, "pc_label", 36, Art.y(5) + 3, "Party", true);
        for (int k = 0; k < party.size(); k++) {
            Pokemon pm = party.get(k);
            int cx = Art.x(k % 3), cy = Art.y(6 + k / 3);
            var pb = Art.body(Summary.look(pm), false);
            if (pb != null) Art.put(t, cx, cy, pb, 17, 7);
            Art.put(t, cx, cy, Art.level(pm.getLevel()), Art.width("pc_lv", (char) (0xE000 + pm.getLevel())), 4);
            if (k == i) Art.glyph(t, cx - 1, cy - 1, RING, 7);
        }
        for (int a = 0; a < 3; a++) {
            if (actions[a].isEmpty()) continue;
            int ax = Art.x(3 * a) - 1;
            Art.glyph(t, ax, Art.y(8) - 1, PLATE, 7);
            Art.text(t, "pc_label", ax + 27.5, Art.y(8) + 4, actions[a], true);
        }
        int px = Art.x(3) - 1, py = Art.y(1) - 2;
        switch (tab) {
            case Summary.MOVES -> {
                Art.glyph(t, px, py, PAGE_FRAME, 7);
                var moves = m.getMoveSet().getMoves();
                for (int k = 0; k < moves.size(); k++) {
                    int my = Art.y(1 + k) - 1;
                    Art.glyph(t, px, my, MOVE, 7);
                    Art.text(t, "pc_bold", px + 6, my + 6, Lang.resolve(moves.get(k).getDisplayName()).getString(), false);
                    if (pick != null && pick == k) Art.glyph(t, px, my, MOVE_SEL, 7);
                }
                Art.text(t, "pc_text", px + 6, Art.y(5) + 2, pick == null ? "Click a move, then another," : "Now click where it goes.", false);
                if (pick == null) Art.text(t, "pc_text", px + 6, Art.y(5) + 9, "to swap their places.", false);
            }
            case Summary.STATS -> {
                Art.glyph(t, px, py, PAGE_STATS, 7);
                int[] st = new int[6];
                for (int k = 0; k < 6; k++) st[k] = m.getStat(STATS[k]);
                int most = java.util.Arrays.stream(st).max().orElse(1);
                var nat = m.getNature();
                int up = 0, down = 0;
                for (int k = 0; k < 6; k++) {
                    int ry = py + 5 + 17 * k;
                    boolean plus = STATS[k] == nat.getIncreasedStat() && nat.getIncreasedStat() != nat.getDecreasedStat();
                    boolean minus = STATS[k] == nat.getDecreasedStat() && nat.getIncreasedStat() != nat.getDecreasedStat();
                    if (plus) up = k;
                    if (minus) down = k;
                    Art.text(t, "pc_bold", px + 7, ry + 4, SHORT[k] + (plus ? "+" : minus ? "-" : ""), false);
                    Art.text(t, "pc_text", px + 44, ry + 4, (k == 0 ? m.getCurrentHealth() + "/" : "") + st[k], false);
                    int w = Math.max(1, Math.min(22, st[k] * 22 / Math.max(1, most)));
                    if (k > 0) Art.glyph(t, px + 62, ry + 5, (char) (BAR + w - 1), 3);
                    String iv = "IV " + m.getIvs().getOrDefault(STATS[k]);
                    Art.text(t, "pc_text", px + 104 - Art.width("pc_text", iv), ry + 4, iv, false);
                }
                String natName = Lang.get(nat.getDisplayName());
                Art.text(t, "pc_bold", px + 7, py + 110, "Nature: " + natName, false);
                Art.text(t, "pc_text", px + 7, py + 118, up == 0 ? "No stat changes" : "Raises " + STAT_NAMES[up] + ", lowers " + STAT_NAMES[down], false);
            }
            default -> {
                Art.glyph(t, px, py, PAGE_INFO, 7);
                var curve = m.getSpecies().getExperienceGroup();
                long next = m.getLevel() >= 100 ? 0 : curve.getExperience(m.getLevel() + 1) - m.getExperience();
                List<String> types = new ArrayList<>();
                m.getTypes().forEach(ty -> types.add(Lang.resolve(ty.getDisplayName()).getString()));
                String[][] rows = {{"Dex No.", String.format("%04d", m.getSpecies().getNationalPokedexNumber())},
                        {"Species", Lang.resolve(m.getSpecies().getTranslatedName()).getString()}, {"Type", String.join(" / ", types)},
                        {"OT", m.getOriginalTrainerName() == null ? p.getScoreboardName() : m.getOriginalTrainerName()},
                        {"Nature", Lang.get(m.getNature().getDisplayName())}, {"Ability", Lang.get(m.getAbility().getDisplayName())}};
                for (int k = 0; k < rows.length; k++) {
                    int ry = py + 5 + 15 * k;
                    Art.text(t, "pc_bold", px + 7, ry + 3, rows[k][0], false);
                    Art.text(t, "pc_bold", px + 44, ry + 3, Art.fit("pc_bold", rows[k][1], k == 0 ? 28 : 56), false);
                }
                Art.text(t, "pc_text", px + 48, py + 118, "Exp. Points", false);
                String exp = String.valueOf(m.getExperience());
                Art.text(t, "pc_text", px + 101 - Art.width("pc_text", exp), py + 118, exp, false);
                Art.text(t, "pc_text", px + 48, py + 128, "To Next Lv.", false);
                Art.text(t, "pc_text", px + 101 - Art.width("pc_text", String.valueOf(next)), py + 128, String.valueOf(next), false);
            }
        }
        return t.build();
    }
}
