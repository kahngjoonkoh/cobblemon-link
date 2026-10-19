package io.github.kahngjoonkoh.cobblemonlink;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Cobblemon's party overlay (PartyOverlay) for vanilla Java with the UI pack: the party down the left edge, centred
 * vertically, a 62 x 30 slot each 4 px apart (portrait, Lv, name, gender, ball, HP and EXP bars; the lead pokes out as
 * Cobblemon's selected slot). Sent as the action bar through the text shader's colour 250 (build_ui.py TEXT_VSH),
 * every half second, every tick while something animates. As in Cobblemon, EXP from a battle shows here, not in chat:
 * the EXP bar fills, "+N EXP" floats up, the level-up scroll runs over the portrait and the new-move and evolution
 * notices pop up (PartyOverlayDataControl's timings). Hidden in battles (the battle overlay), while a menu is open,
 * and by /partyhud (Cobblemon's O key). Other action bar messages (say()) ride along with it.
 * ponytail: no status icons outside battle, no EXP bar flash; the half-size text has no shadow (half a px).
 */
final class PartyPanel {
    // build_hud.py's glyphs
    private static final char SLOT = '', SLOT_ACTIVE = '', SLOT_FAINTED = '', SLOT_FAINTED_ACTIVE = '',
            PORTRAIT_BG = '', MALE = '', FEMALE = '', BALL = '', POPUP_EVOLUTION = '',
            POPUP_MOVE = '', SCROLL = '', HP = '', EXP = '';
    private static final int BAR = 18;
    // PartyOverlayDataControl: bar update before/after the level up, its flash, the pop-ups' fade in/hold/out, the scroll
    static final int BEFORE = 15, FLASH = 2, POP_IN = 3, POP_HOLD = 40, POP_OUT = 3, LEVEL_UP = 10;
    static final int POPUP = POP_IN + POP_HOLD + POP_OUT, LAST = BEFORE + FLASH + POPUP, EXP_HOLD = BEFORE + FLASH + POPUP - 6;
    private static final Key PORTRAIT = Key.key("cobblemonlink", "portrait_hud");

    /** A battle's EXP for one Pokémon: its level before (-1: no level up), the EXP, new moves, evolutions unlocked. */
    static final class Gain {
        final int oldLevel, moves, evolutions;
        final long exp;
        long start = -1; // the tick it first showed (the panel is hidden during the battle itself)

        Gain(int oldLevel, long exp, int moves, int evolutions) {
            this.oldLevel = oldLevel;
            this.exp = exp;
            this.moves = moves;
            this.evolutions = evolutions;
        }
    }

    private final CobblemonLink pl;
    private final Map<UUID, Map<String, Gain>> gains = new HashMap<>();
    private final Map<UUID, Component> message = new HashMap<>();
    private final Map<UUID, Long> messageUntil = new HashMap<>();
    private final Set<UUID> ready = new HashSet<>(), shown = new HashSet<>();

    PartyPanel(CobblemonLink pl) { this.pl = pl; }

    /** The player gets the panel (vanilla Java with the UI pack, not hidden); the client mod draws its own. */
    boolean on(Player p) {
        return Ui.partyPanel && !Ui.bedrock(p) && !Mod.has(p) && !pl.store.get(p.getUniqueId()).hideParty;
    }

    void ready(Player p) { ready.add(p.getUniqueId()); }

    void forget(Player p) {
        UUID id = p.getUniqueId();
        gains.remove(id);
        message.remove(id);
        messageUntil.remove(id);
        ready.remove(id);
        shown.remove(id);
    }

    /** A battle's EXP for mon, to show on the panel. */
    void gain(Player p, Mon m, int oldLevel, long exp, int moves, int evolutions) {
        gains.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(m.id, new Gain(m.level != oldLevel ? oldLevel : -1, exp, moves, evolutions));
    }

    /** An action bar message (3 s): with the panel when it's shown, else as a plain action bar. */
    void say(Player p, String text, boolean red) {
        if (!on(p) || !shown.contains(p.getUniqueId())) {
            p.sendActionBar(Component.text(text, red ? net.kyori.adventure.text.format.NamedTextColor.RED : net.kyori.adventure.text.format.NamedTextColor.WHITE));
            return;
        }
        Ui.Overlay o = new Ui.Overlay();
        o.colour = red ? Ui.Overlay.RED : Ui.Overlay.WHITE;
        int w = Ui.width(text);
        message.put(p.getUniqueId(), o.add(Hotbar.BC, -w / 2, 72, Component.text(text), w).build());
        messageUntil.put(p.getUniqueId(), (long) Bukkit.getCurrentTick() + 60);
    }

    void tick() {
        long now = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Store.Data d = pl.store.get(id);
            boolean visible = on(p) && ready.contains(id) && !d.party.isEmpty() && !pl.battles.containsKey(id)
                    && !(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu);
            if (!visible) {
                if (shown.remove(id) && !pl.battles.containsKey(id)) p.sendActionBar(Component.empty());
                continue;
            }
            Map<String, Gain> g = gains.getOrDefault(id, Map.of());
            g.values().removeIf(x -> x.start >= 0 && now - x.start > LAST);
            Long until = messageUntil.get(id);
            if (until != null && until < now) {
                message.remove(id);
                messageUntil.remove(id);
            }
            if (!g.isEmpty() || shown.add(id) || now % 10 == 0) p.sendActionBar(draw(p, d, g, now));
        }
    }

    private Component draw(Player p, Store.Data d, Map<String, Gain> gains, long now) {
        Ui.Overlay o = new Ui.Overlay();
        int n = d.party.size(), top = -n * 15 - 10; // PartyOverlay: (mid - totalHeight / 2) - (SLOT_SPACING * 5) / 2
        for (int i = 0; i < n; i++) {
            Mon m = d.party.get(i);
            int sel = i == 0 ? 6 : 0, y = top + 34 * i;
            Gain g = gains.get(m.id);
            if (g != null && g.start < 0) g.start = now;
            long t = g == null ? -1 : now - g.start;
            o.mid(22 + sel, y + 2, Ui.glyphs(String.valueOf(PORTRAIT_BG)), 22);
            Integer pi = Ui.portraits.get(m.model(pl.dex));
            if (pi != null) o.mid(22 + sel, y + 2, Component.text(Character.toString(0xF0000 + 2 * pi)).font(PORTRAIT), 22);
            boolean fainted = m.fainted(pl.dex);
            o.mid(0, y, Ui.glyphs(String.valueOf(fainted ? (i == 0 ? SLOT_FAINTED_ACTIVE : SLOT_FAINTED) : (i == 0 ? SLOT_ACTIVE : SLOT))), 63);
            if (g != null && g.oldLevel >= 0) { // the level-up scroll over the portrait, after the bar fills
                double tr = (t - BEFORE - FLASH) / (double) LEVEL_UP;
                if (tr >= 0 && tr < 1) o.mid(24 + sel, y + 4, Ui.glyphs(String.valueOf((char) (SCROLL + Math.max(1, (int) Math.ceil(tr * 14)) - 1))), 18);
            }
            small(o, sel + 7, y + 13, "Lv.", true);
            small(o, sel + 7, y + 18, String.valueOf(g != null && g.oldLevel >= 0 && t < BEFORE ? g.oldLevel : m.level), true);
            small(o, sel + 2, y + 25, fit(m.name(pl.dex), 37), false);
            if ("M".equals(m.gender) || "F".equals(m.gender))
                o.mid(sel + 40, y + 25 - 3, Ui.glyphs(String.valueOf("M".equals(m.gender) ? MALE : FEMALE)), 4); // ascent 4: 3 px up
            o.mid(sel + 43, y + 22, Ui.glyphs(String.valueOf(BALL)), 10);
            int hp = m.hp(pl.dex), max = Math.max(1, m.maxHp(pl.dex)), h = hp <= 0 ? 0 : Math.max(1, Math.round(BAR * hp / (float) max));
            if (h > 0) o.mid(sel + 46, y + 5 + BAR - h - low(h), Ui.glyphs(String.valueOf((char) (HP + h - 1))), 3);
            int e = Math.round(BAR * expRatio(m, g, t));
            if (e > 0) o.mid(sel + 49, y + 5 + BAR - e - low(e), Ui.glyphs(String.valueOf((char) (EXP + e - 1))), 2);
            if (g == null) continue;
            // "+N EXP", floating in and out beside the bars
            double[] ex = fade(t, POP_IN, EXP_HOLD, POP_OUT);
            if (ex[1] > 0) {
                o.fade = Math.min(7, (int) Math.round((1 - ex[1]) * 8));
                small(o, sel + 57, y + 17 + (int) ex[0], "+" + g.exp + " EXP", false);
                o.fade = 0;
            }
            double[] mv = g.moves > 0 ? fade(t - BEFORE - FLASH, POP_IN, POP_HOLD, POP_OUT) : new double[]{0, 0};
            double[] ev = g.evolutions > 0 ? fade(t - BEFORE - FLASH, POP_IN, POP_HOLD, POP_OUT) : new double[]{0, 0};
            if (ev[1] > 0) {
                o.fade = Math.min(7, (int) Math.round((1 - ev[1]) * 8));
                o.mid(sel + (ex[1] > 0 && mv[1] > 0 ? 78 : 56), y + 4 + (int) ev[0], Ui.glyphs(String.valueOf(POPUP_EVOLUTION)), 20);
            }
            if (mv[1] > 0) {
                o.fade = Math.min(7, (int) Math.round((1 - mv[1]) * 8));
                o.mid(sel + 56, y + (ex[1] > 0 ? 4 : 17) + (int) mv[0], Ui.glyphs(String.valueOf(POPUP_MOVE)), 20);
            }
            o.fade = 0;
        }
        Component c = o.build();
        Component msg = message.get(p.getUniqueId());
        return msg == null ? c : Component.text().append(c, msg).build();
    }

    /** Cobblemon's EXP bar: filling up to the old level's end, then from 0 to now (level up), else from before to now. */
    private float expRatio(Mon m, Gain g, long t) {
        Dex.Species s = pl.dex.of(m);
        long base = m.level == 1 ? 0 : Rules.expFor(s.expGroup, m.level), next = Rules.expFor(s.expGroup, m.level + 1) - base;
        float now = m.level >= 100 ? 0 : (m.exp - base) / (float) Math.max(1, next);
        if (g == null || t < 0) return Math.clamp(now, 0, 1);
        if (g.oldLevel >= 0) {
            if (t <= BEFORE) {
                long ob = g.oldLevel == 1 ? 0 : Rules.expFor(s.expGroup, g.oldLevel), on = Rules.expFor(s.expGroup, g.oldLevel + 1) - ob;
                float from = (m.exp - g.exp - ob) / (float) Math.max(1, on);
                return Math.clamp(from + (1 - from) * t / (float) BEFORE, 0, 1);
            }
            if (t <= BEFORE + FLASH) return 1;
            return Math.clamp(now * Math.min(1, (t - BEFORE - FLASH) / (float) BEFORE), 0, 1);
        }
        float from = now - g.exp / (float) Math.max(1, next), tr = Math.min(1, t / (float) (BEFORE * 2 + FLASH));
        return Math.clamp(from + (now - from) * tr, 0, 1);
    }

    /** PartyOverlay's pop-up fade: {y offset, opacity} at t ticks (fade in over in ticks, hold, fade out). */
    static double[] fade(long t, int in, int hold, int out) {
        if (t <= 0) return new double[]{0, 0};
        if (t <= in) {
            double step = Math.floor(t / (double) in * 3) + 1;
            return new double[]{4 - step, step * 0.25};
        }
        if (t <= in + hold) return new double[]{0, 1};
        double step = Math.floor((t - in - hold) / (double) out * 3) + 1;
        return new double[]{-step, Math.max(0, 1 - step * 0.25)};
    }

    /** A glyph's top sits 7 - ascent px under the line's top: a bar shorter than 7 px (ascent = its height) is that much lower. */
    private static int low(int h) { return Math.max(0, 7 - h); }

    /** Cobblemon's half-scale text (cobblemonlink:small: ascent 3, so 4 px up), left-aligned at x or centred on it. */
    private static void small(Ui.Overlay o, int x, int y, String s, boolean centre) {
        int w = Ui.smallWidth(s);
        o.mid(centre ? x - w / 2 : x, y - 4, Component.text(Ui.tight(s)).font(Ui.Overlay.SMALL), Ui.tightWidth(s));
    }

    private static String fit(String s, int max) {
        while (s.length() > 1 && Ui.tightWidth(s) > max) s = s.substring(0, s.length() - 1);
        return s;
    }
}
