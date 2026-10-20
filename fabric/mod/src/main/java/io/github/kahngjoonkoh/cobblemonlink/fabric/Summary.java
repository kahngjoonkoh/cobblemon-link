package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's Summary for players without the mod, on Cobblemon's own Pokémon: the Info / Moves / Stats tabs, the
 * party to switch between, send out or recall, make lead, to PC, nickname, and Evolve when an evolution is pending.
 * /party opens it. Bedrock: the Paper version's form (title ending MARK + 6): buttons 0-2 the tabs ("*" on the
 * open one), 3-5 the actions ("" when not offered), 6 the Pokémon ("Lv. n", its body picture), 7 gender, 8-9 type
 * icons, 10 the name (tap: nickname), 11-16 the party, 17-40 rows of three (label, value, image), 41 Evolve.
 * The Moves tab lists the 4 moves and then Cobblemon's benched moves; tap one, then another, to swap them.
 */
public final class Summary {
    static final int INFO = 0, MOVES = 1, STATS = 2, EVOLVE = 17 + 3 * 8;
    private static final String[] TABS = {"Info", "Moves", "Stats"};
    private static final Stats[] STATS_ORDER = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
    private static final String[] STAT_NAMES = {"HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"};
    private static final java.util.Map<java.util.UUID, Integer> PICKED = new java.util.HashMap<>();

    private Summary() {}

    static void register() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                net.minecraft.commands.Commands.literal("party").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    if (PartyHud.party(p).isEmpty()) Cobblemon.INSTANCE.getStarterHandler().requestStarterChoice(p);
                    else open(p, 0, INFO);
                    return 1;
                })));
    }

    public static void open(ServerPlayer p, int index, int tab) {
        List<Pokemon> party = PartyHud.party(p);
        if (party.isEmpty()) return;
        int i = Math.clamp(index, 0, party.size() - 1);
        if (Clients.bedrock(p)) bedrock(p, party, i, tab);
        else PartyMenu.open(p); // ponytail: vanilla Java's Summary comes with the vanillaJava levels
    }

    static void bedrock(ServerPlayer p, List<Pokemon> party, int i, int tab) {
        Pokemon m = party.get(i);
        Runnable stay = () -> open(p, i, tab);
        boolean battling = BattleRegistry.getBattleByParticipatingPlayer(p) != null;
        List<Forms.Opt> o = new ArrayList<>();
        for (int t = 0; t < TABS.length; t++) {
            int tt = t;
            o.add(new Forms.Opt(TABS[t] + (t == tab ? "*" : ""), "summary/tab_" + TABS[t].toLowerCase(Locale.ROOT), () -> {
                PICKED.remove(p.getUUID());
                open(p, i, tt);
            }));
        }
        boolean out = m.getEntity() != null;
        o.add(new Forms.Opt(battling || m.isFainted() && !out ? "" : out ? "Recall" : "Send out", "none", () -> {
            if (m.getEntity() != null) m.recall();
            else if (!m.isFainted() && !battling) m.sendOutWithAnimation(p, p.serverLevel(), p.position().add(p.getLookAngle().multiply(2, 0, 2)), null, true, null, e -> kotlin.Unit.INSTANCE);
            open(p, i, tab);
        }));
        o.add(new Forms.Opt(battling || i == 0 ? "" : "Lead", "none", () -> {
            var store = Cobblemon.INSTANCE.getStorage().getParty(p);
            store.swap(slot(store, party.getFirst()), slot(store, m));
            open(p, 0, tab);
        }));
        o.add(new Forms.Opt(battling || party.size() <= 1 ? "" : "To PC", "none", () -> {
            if (m.getEntity() != null) m.recall();
            if (Cobblemon.INSTANCE.getStorage().getParty(p).remove(m) && !Cobblemon.INSTANCE.getStorage().getPC(p).add(m))
                Cobblemon.INSTANCE.getStorage().getParty(p).add(m); // the PC is full: back to the party
            open(p, Math.max(0, i - 1), tab);
        }));
        String look = look(m);
        o.add(new Forms.Opt("Lv. " + m.getLevel(), Models.has(look) ? "body/" + look : "none", stay));
        o.add(new Forms.Opt(m.getGender() == Gender.MALE ? "♂" : m.getGender() == Gender.FEMALE ? "♀" : "", "none", stay));
        List<ElementalType> types = new ArrayList<>();
        m.getTypes().forEach(types::add);
        for (int k = 0; k < 2; k++)
            o.add(new Forms.Opt("", k < types.size() ? "pc/type/" + types.get(k).getName().toLowerCase(Locale.ROOT) : "none", stay));
        o.add(new Forms.Opt("Rename", "none", () -> Forms.input(p, "Nickname", BattleUi.name(m), m.getNickname() == null ? "" : m.getNickname().getString(), name -> {
            if (name != null) {
                String n = name.strip();
                m.setNickname(n.isEmpty() ? null : Component.literal(n.substring(0, Math.min(12, n.length()))));
            }
            open(p, i, tab);
        })));
        for (int k = 0; k < 6; k++) {
            int kk = k;
            Pokemon pm = k < party.size() ? party.get(k) : null;
            o.add(pm == null ? new Forms.Opt("", "none", stay) : new Forms.Opt("Lv" + pm.getLevel() + (k == i ? "*" : ""),
                    Models.has(look(pm)) ? "body/" + look(pm) : "none", () -> {
                        PICKED.remove(p.getUUID());
                        open(p, kk, tab);
                    }));
        }
        List<String[]> rows = rows(p, m, tab);
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            int rr = r;
            Runnable tap = tab == MOVES ? () -> pickMove(p, m, rr, i) : stay;
            o.add(new Forms.Opt(row[0], "none", tap));
            o.add(new Forms.Opt(row[1], "none", tap));
            o.add(new Forms.Opt("", row[2], tap));
        }
        while (o.size() < EVOLVE) o.add(new Forms.Opt("", "none", stay));
        var evo = m.getEvolutionProxy().server();
        o.add(new Forms.Opt(evo.isEmpty() || battling ? "" : "Evolve", "none", () -> {
            if (!evo.isEmpty()) evo.start(evo.iterator().next());
            open(p, i, tab);
        }));
        String title = BattleUi.name(m) + (m.getShiny() ? " ★" : "") + Forms.MARK + 6;
        Forms.send(p, title, "", o, c -> { if (c >= 0 && c < o.size()) o.get(c).action().run(); });
    }

    /** Moves tab: the first tap picks a move, the second swaps it there (Cobblemon's move swap, benched moves too). */
    private static void pickMove(ServerPlayer p, Pokemon m, int row, int i) {
        Integer first = PICKED.remove(p.getUUID());
        if (first == null) {
            PICKED.put(p.getUUID(), row);
            p.sendSystemMessage(Component.literal("Tap another move to swap them."));
        } else if (first != row) {
            List<Move> moves = m.getMoveSet().getMoves();
            List<com.cobblemon.mod.common.api.moves.BenchedMove> bench = new ArrayList<>();
            m.getBenchedMoves().forEach(bench::add);
            if (first < 4 && row < 4) m.getMoveSet().swapMove(first, row);
            else if (Math.min(first, row) < moves.size() && Math.max(first, row) >= 4 && Math.max(first, row) - 4 < bench.size()) {
                // a known move for a benched one: Cobblemon's BenchMovePacket handler does the same
                int known = Math.min(first, row);
                var benched = bench.get(Math.max(first, row) - 4).getMoveTemplate();
                var old = moves.get(known);
                m.getBenchedMoves().remove(benched);
                m.getBenchedMoves().add(new com.cobblemon.mod.common.api.moves.BenchedMove(old.getTemplate(), old.getRaisedPpStages()));
                m.getMoveSet().setMove(known, benched.create());
            }
        }
        open(p, i, MOVES);
    }

    static String look(Pokemon m) { return Models.look(m.getSpecies().getResourceIdentifier().getPath(), m.getAspects()); }

    private static com.cobblemon.mod.common.api.storage.party.PartyPosition slot(com.cobblemon.mod.common.api.storage.party.PartyStore s, Pokemon m) {
        var list = s.toGappyList();
        return new com.cobblemon.mod.common.api.storage.party.PartyPosition(list.indexOf(m));
    }

    /** Up to 8 rows of [label, value, image] for the tab. */
    static List<String[]> rows(ServerPlayer p, Pokemon m, int tab) {
        List<String[]> rows = new ArrayList<>();
        switch (tab) {
            case MOVES -> {
                List<Move> moves = m.getMoveSet().getMoves();
                Integer picked = PICKED.get(p.getUUID());
                for (int k = 0; k < 4; k++) {
                    Move mv = k < moves.size() ? moves.get(k) : null;
                    rows.add(mv == null ? new String[]{"", "", "none"} : new String[]{(picked != null && picked == k ? "> " : "") + Lang.resolve(mv.getDisplayName()).getString(),
                            mv.getCurrentPp() + "/" + mv.getMaxPp(), "pc/type/" + mv.getType().getName().toLowerCase(Locale.ROOT)});
                }
                int k = 4;
                for (var b : m.getBenchedMoves()) {
                    if (k >= 8) break;
                    rows.add(new String[]{(picked != null && picked == k ? "> " : "") + Lang.resolve(b.getMoveTemplate().getDisplayName()).getString(),
                            "(benched)", "pc/type/" + b.getMoveTemplate().getElementalType().getName().toLowerCase(Locale.ROOT)});
                    k++;
                }
            }
            case STATS -> {
                for (int k = 0; k < 6; k++) {
                    Stat s = STATS_ORDER[k];
                    String mark = k == 0 ? "" : s == m.getNature().getIncreasedStat() && s != m.getNature().getDecreasedStat() ? " +"
                            : s == m.getNature().getDecreasedStat() && s != m.getNature().getIncreasedStat() ? " -" : "";
                    rows.add(new String[]{STAT_NAMES[k] + mark, (k == 0 ? m.getCurrentHealth() + "/" : "") + m.getStat(s)
                            + "   IV " + m.getIvs().getOrDefault(s) + "  EV " + m.getEvs().getOrDefault(s), "none"});
                }
            }
            default -> {
                var curve = m.getSpecies().getExperienceGroup();
                long next = m.getLevel() >= 100 ? 0 : curve.getExperience(m.getLevel() + 1) - m.getExperience();
                List<String> types = new ArrayList<>();
                m.getTypes().forEach(t -> types.add(Lang.resolve(t.getDisplayName()).getString()));
                rows.add(new String[]{"Pokédex No.", String.format("%04d", m.getSpecies().getNationalPokedexNumber()), "none"});
                rows.add(new String[]{"Species", Lang.resolve(m.getSpecies().getTranslatedName()).getString(), "none"});
                rows.add(new String[]{"Type", String.join(" / ", types), "none"});
                rows.add(new String[]{"OT", m.getOriginalTrainerName() == null ? p.getScoreboardName() : m.getOriginalTrainerName(), "none"});
                rows.add(new String[]{"Nature", Lang.get(m.getNature().getDisplayName()), "none"});
                rows.add(new String[]{"Ability", Lang.get(m.getAbility().getDisplayName()), "none"});
                rows.add(new String[]{"EXP. Points", String.valueOf(m.getExperience()), "none"});
                rows.add(new String[]{"To Next Lv.", String.valueOf(next), "none"});
            }
        }
        return rows;
    }
}
