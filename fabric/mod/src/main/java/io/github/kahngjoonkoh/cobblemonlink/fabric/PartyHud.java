package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's party overlay (PartyOverlay) for players without the mod, as the Paper version's PartyPanel: the party
 * down the left of the screen; after a battle the EXP bar fills, "+N EXP" floats up, the level-up scroll runs and the
 * new-move and evolution notices pop up (PartyOverlayDataControl's timings). Hidden in battles and by /partyhud.
 * Bedrock: the panel's values as a title the UI pack draws and never shows (build_bedrock_ui.py hud_screen).
 * Gains are seen by comparing each Pokémon with the last tick (EXP, moves, pending evolutions), so they come from
 * Cobblemon's own battles, candies or commands alike.
 * ponytail: the lead is party slot 0 (Cobblemon's selected slot is the client's), no status icons.
 */
public final class PartyHud {
    static final int BAR = 18, BEFORE = 15, FLASH = 2, POP_IN = 3, POP_HOLD = 40, POP_OUT = 3, LEVEL_UP = 10;
    static final int POPUP = POP_IN + POP_HOLD + POP_OUT, LAST = BEFORE + FLASH + POPUP, EXP_HOLD = BEFORE + FLASH + POPUP - 6;
    /** build_bedrock_ui.py HUD_FIELDS: state, level, name, gender, HP, EXP, scroll, evolution and move pop-ups, "+N EXP" fade and text, portrait. */
    static final int[] FIELDS = {1, 4, 12, 1, 3, 3, 3, 2, 2, 1, 11, 76};
    static final String HIDDEN = "|CLn0", HIDE_TAG = "cobblemonlink.hideparty";

    /** One Pokémon as last seen, to notice gains. */
    record Seen(int level, int exp, int moves, int evolutions) {
        static Seen of(Pokemon m) {
            int moves = m.getMoveSet().getMoves().size();
            for (var ignored : m.getBenchedMoves()) moves++;
            return new Seen(m.getLevel(), m.getExperience(), moves, m.getEvolutionProxy().server().size());
        }
    }

    /** A gain to show: the level before (-1: none), EXP, new moves, evolutions; start = first tick shown. */
    static final class Gain {
        final int oldLevel, moves, evolutions;
        final long exp;
        long start = -1;

        Gain(int oldLevel, long exp, int moves, int evolutions) {
            this.oldLevel = oldLevel;
            this.exp = exp;
            this.moves = moves;
            this.evolutions = evolutions;
        }
    }

    private static final Map<UUID, Map<UUID, Seen>> SEEN = new HashMap<>();
    private static final Map<UUID, Map<UUID, Gain>> GAINS = new HashMap<>();
    private static final Set<UUID> SHOWN = new HashSet<>();
    private static long now;

    private PartyHud() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PartyHud::tick);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            SEEN.remove(h.player.getUUID());
            GAINS.remove(h.player.getUUID());
            SHOWN.remove(h.player.getUUID());
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                net.minecraft.commands.Commands.literal("partyhud").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    boolean hide = !p.getTags().contains(HIDE_TAG); // a player tag: saved with the player
                    if (hide) p.addTag(HIDE_TAG);
                    else p.removeTag(HIDE_TAG);
                    p.sendSystemMessage(Component.literal(hide ? "Party hidden. /partyhud shows it again." : "Party shown."));
                    return 1;
                })));
    }

    static List<Pokemon> party(ServerPlayer p) {
        List<Pokemon> out = new ArrayList<>();
        for (Pokemon m : Cobblemon.INSTANCE.getStorage().getParty(p)) if (m != null) out.add(m);
        return out;
    }

    private static void tick(MinecraftServer server) {
        now++;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!Clients.bedrock(p)) continue; // ponytail: vanilla Java's overlay comes with the full level
            UUID id = p.getUUID();
            List<Pokemon> party = party(p);
            Map<UUID, Gain> g = GAINS.computeIfAbsent(id, k -> new HashMap<>());
            notice(id, party, g);
            boolean visible = !party.isEmpty() && !p.getTags().contains(HIDE_TAG) && BattleRegistry.getBattleByParticipatingPlayer(p) == null;
            if (!visible) {
                if (SHOWN.remove(id)) title(p, HIDDEN);
                continue;
            }
            g.values().removeIf(x -> x.start >= 0 && now - x.start > LAST);
            if (!g.isEmpty() || SHOWN.add(id) || now % 10 == 0) title(p, bedrock(party, g));
        }
    }

    /** Compares each Pokémon with last tick's: more EXP is a gain (with its level, moves and evolutions). */
    private static void notice(UUID player, List<Pokemon> party, Map<UUID, Gain> gains) {
        Map<UUID, Seen> seen = SEEN.computeIfAbsent(player, k -> new HashMap<>());
        for (Pokemon m : party) {
            Seen now = Seen.of(m), was = seen.put(m.getUuid(), now);
            if (was == null || now.exp() <= was.exp()) continue;
            gains.put(m.getUuid(), new Gain(now.level() != was.level() ? was.level() : -1, now.exp() - was.exp(),
                    Math.max(0, now.moves() - was.moves()), Math.max(0, now.evolutions() - was.evolutions())));
        }
    }

    private static void title(ServerPlayer p, String text) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 40, 0));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text)));
    }

    /** The party for Bedrock's UI pack: "|CLn", its size, then six slots of FIELDS, ASCII padded with '~'. */
    static String bedrock(List<Pokemon> party, Map<UUID, Gain> gains) {
        int n = Math.min(6, party.size());
        StringBuilder b = new StringBuilder("|CLn").append(n);
        for (int i = 0; i < 6; i++) {
            if (i >= n) {
                b.append("~".repeat(java.util.Arrays.stream(FIELDS).sum()));
                continue;
            }
            Pokemon m = party.get(i);
            Gain g = gains.get(m.getUuid());
            if (g != null && g.start < 0) g.start = now;
            long t = g == null ? -1 : now - g.start;
            int scroll = 0;
            String ev = "-", mv = "-", ea = "-", et = "";
            if (g != null && g.oldLevel >= 0) {
                double tr = (t - BEFORE - FLASH) / (double) LEVEL_UP;
                if (tr >= 0 && tr < 1) scroll = Math.max(1, (int) Math.ceil(tr * 14));
            }
            if (g != null) {
                double[] ex = fade(t, POP_IN, EXP_HOLD, POP_OUT);
                double[] mvf = g.moves > 0 ? fade(t - BEFORE - FLASH, POP_IN, POP_HOLD, POP_OUT) : new double[]{0, 0};
                double[] evf = g.evolutions > 0 ? fade(t - BEFORE - FLASH, POP_IN, POP_HOLD, POP_OUT) : new double[]{0, 0};
                if (ex[1] > 0.25) {
                    ea = "y";
                    et = "+" + g.exp + " EXP";
                }
                if (evf[1] > 0) ev = step(evf[1]) + (ex[1] > 0 && mvf[1] > 0 ? "b" : "a");
                if (mvf[1] > 0) mv = step(mvf[1]) + (ex[1] > 0 ? "a" : "b");
            }
            int hp = m.getCurrentHealth(), max = Math.max(1, m.getMaxHealth()), h = hp <= 0 ? 0 : Math.max(1, Math.round(BAR * hp / (float) max));
            String look = Models.look(m.getSpecies().getResourceIdentifier().getPath(), m.getAspects());
            String[] v = {String.valueOf("abcd".charAt(m.isFainted() ? (i == 0 ? 3 : 2) : (i == 0 ? 1 : 0))),
                    "L" + (g != null && g.oldLevel >= 0 && t < BEFORE ? g.oldLevel : m.getLevel()), ascii(BattleUi.name(m)),
                    m.getGender() == Gender.MALE ? "m" : m.getGender() == Gender.FEMALE ? "f" : "n", "h" + two(h),
                    "x" + two(Math.round(BAR * expRatio(m, g, t))), "s" + two(scroll), ev, mv, ea, et,
                    Models.has(look) ? "items/portrait/" + look : "cobblemonlink/none"};
            for (int k = 0; k < FIELDS.length; k++) {
                String x = v[k].length() > FIELDS[k] ? v[k].substring(0, FIELDS[k]) : v[k];
                b.append(x).append("~".repeat(FIELDS[k] - x.length()));
            }
        }
        return b.toString();
    }

    /** Cobblemon's EXP bar: filling up to the old level's end, then from 0 to now (level up), else from before to now. */
    static float expRatio(Pokemon m, Gain g, long t) {
        var curve = m.getSpecies().getExperienceGroup();
        int level = m.getLevel();
        long base = curve.getExperience(level), next = curve.getExperience(level + 1) - base;
        float cur = level >= 100 ? 0 : (m.getExperience() - base) / (float) Math.max(1, next);
        if (g == null || t < 0) return Math.clamp(cur, 0, 1);
        if (g.oldLevel >= 0) {
            if (t <= BEFORE) {
                long ob = curve.getExperience(g.oldLevel), on = curve.getExperience(g.oldLevel + 1) - ob;
                float from = (m.getExperience() - g.exp - ob) / (float) Math.max(1, on);
                return Math.clamp(from + (1 - from) * t / (float) BEFORE, 0, 1);
            }
            if (t <= BEFORE + FLASH) return 1;
            return Math.clamp(cur * Math.min(1, (t - BEFORE - FLASH) / (float) BEFORE), 0, 1);
        }
        float from = cur - g.exp / (float) Math.max(1, next), tr = Math.min(1, t / (float) (BEFORE * 2 + FLASH));
        return Math.clamp(from + (cur - from) * tr, 0, 1);
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

    private static String step(double opacity) { return String.valueOf("abcdefgh".charAt(Math.min(7, (int) Math.round((1 - opacity) * 8)))); }

    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    /** Plain ASCII without the pack's '~' and '|': Bedrock's UI cuts the title by bytes. */
    static String ascii(String s) {
        s = java.text.Normalizer.normalize(s.replace('♀', 'F').replace('♂', 'M'), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(c >= 32 && c < 127 && c != '~' && c != '|' ? c : '?');
        return b.toString();
    }
}
