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
 * After the party cards (42-59): 60 the Marks tab, 61 the shown mark (its title, its icon), 62 its description, 63-92
 * the marks it has (30 a page: "s" the shown one, "n" the rest), 93 the next page, 94-99 the six markings ("0" "1" "2").
 */
public final class Summary {
    static final int INFO = 0, MOVES = 1, STATS = 2, MARKS = 3, EVOLVE = 17 + 3 * 8, MARK_SLOTS = 30;
    private static final String[] TABS = {"Info", "Moves", "Stats"};
    private static final Stats[] STATS_ORDER = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
    private static final java.util.Map<java.util.UUID, Integer> PICKED = new java.util.HashMap<>(), PAGE = new java.util.HashMap<>();

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
        else if (Ui.loaded && Config.vanillaJava().screenArt()) SummaryArt.open(p, party, i, tab);
        else JavaScreens.party(p);
    }

    static void bedrock(ServerPlayer p, List<Pokemon> party, int i, int tab) {
        Pokemon m = party.get(i);
        Runnable stay = () -> open(p, i, tab);
        boolean battling = BattleRegistry.getBattleByParticipatingPlayer(p) != null;
        List<Forms.Opt> o = new ArrayList<>();
        for (int t = 0; t < TABS.length; t++) {
            int tt = t;
            o.add(new Forms.Opt(Lang.t(p, "cobblemon.ui." + TABS[t].toLowerCase(Locale.ROOT)) + (t == tab ? "*" : ""), "summary/tab_" + TABS[t].toLowerCase(Locale.ROOT), () -> {
                PICKED.remove(p.getUUID());
                open(p, i, tt);
            }));
        }
        boolean out = m.getEntity() != null;
        o.add(new Forms.Opt(battling || m.isFainted() && !out ? "" : Lang.t(p, out ? "cobblemonlink.party.recall" : "cobblemonlink.party.send_out"), "none", () -> {
            if (m.getEntity() != null) m.recall();
            else if (!m.isFainted() && !battling) m.sendOutWithAnimation(p, p.serverLevel(), p.position().add(p.getLookAngle().multiply(2, 0, 2)), null, true, null, e -> kotlin.Unit.INSTANCE);
            open(p, i, tab);
        }));
        o.add(new Forms.Opt(battling || i == 0 ? "" : Lang.t(p, "cobblemonlink.summary.lead"), "none", () -> {
            var store = Cobblemon.INSTANCE.getStorage().getParty(p);
            store.swap(slot(store, party.getFirst()), slot(store, m));
            open(p, 0, tab);
        }));
        o.add(new Forms.Opt(battling || party.size() <= 1 ? "" : Lang.t(p, "cobblemonlink.summary.to_pc"), "none", () -> {
            if (m.getEntity() != null) m.recall();
            if (Cobblemon.INSTANCE.getStorage().getParty(p).remove(m) && !Cobblemon.INSTANCE.getStorage().getPC(p).add(m))
                Cobblemon.INSTANCE.getStorage().getParty(p).add(m); // the PC is full: back to the party
            open(p, Math.max(0, i - 1), tab);
        }));
        String look = look(m);
        o.add(new Forms.Opt(Lang.t(p, "cobblemon.ui.lv.number", m.getLevel()), Models.has(look) ? "body/" + look : "none", stay));
        o.add(new Forms.Opt(m.getGender() == Gender.MALE ? "♂" : m.getGender() == Gender.FEMALE ? "♀" : "", "none", stay));
        List<ElementalType> types = new ArrayList<>();
        m.getTypes().forEach(types::add);
        for (int k = 0; k < 2; k++)
            o.add(new Forms.Opt("", k < types.size() ? "pc/type/" + types.get(k).getName().toLowerCase(Locale.ROOT) : "none", stay));
        o.add(new Forms.Opt(Lang.t(p, "cobblemonlink.summary.rename"), "none", () -> Forms.input(p, Lang.t(p, "cobblemonlink.summary.nickname"), BattleUi.name(m), m.getNickname() == null ? "" : m.getNickname().getString(), name -> {
            if (name != null) {
                String n = name.strip();
                m.setNickname(n.isEmpty() ? null : Component.literal(n.substring(0, Math.min(12, n.length()))));
            }
            open(p, i, tab);
        })));
        for (int k = 0; k < 6; k++) {
            int kk = k;
            Pokemon pm = k < party.size() ? party.get(k) : null;
            o.add(pm == null ? new Forms.Opt("", "none", stay) : new Forms.Opt(Lang.t(p, "cobblemon.ui.lv.number", pm.getLevel()) + (k == i ? "*" : "") + (pm.isFainted() ? "!" : ""),
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
        o.add(new Forms.Opt(evo.isEmpty() || battling ? "" : Lang.t(p, "cobblemon.ui.evolve"), "none", () -> {
            if (!evo.isEmpty()) evo.start(evo.iterator().next());
            open(p, i, tab);
        }));
        // Cobblemon's party card (PartySlotWidget) for each slot: its name (42 + k), its HP bar (48 + k: the texture of
        // that width and colour) and its ball, with "m" / "f" for the gender icon (54 + k); build_bedrock_ui.py sum_card
        for (int k = 0; k < 6; k++) {
            Pokemon pm = k < party.size() ? party.get(k) : null;
            o.add(new Forms.Opt(pm == null ? "" : BattleUi.name(pm), "none", stay));
        }
        for (int k = 0; k < 6; k++) {
            Pokemon pm = k < party.size() ? party.get(k) : null;
            o.add(new Forms.Opt(pm == null ? "" : pm.isFainted() ? "f" : k == i ? "s" : "n", // the card's art (sum_card)
                    pm == null ? "none" : hpBar(pm.getCurrentHealth(), pm.getMaxHealth()), stay));
        }
        for (int k = 0; k < 6; k++) {
            Pokemon pm = k < party.size() ? party.get(k) : null;
            o.add(pm == null ? new Forms.Opt("", "none", stay) : new Forms.Opt(pm.getGender() == Gender.MALE ? "m" : pm.getGender() == Gender.FEMALE ? "f" : "",
                    "ball/" + pm.getCaughtBall().getName().getPath(), stay));
        }
        marks(p, m, i, tab, o, stay);
        String title = BattleUi.name(m) + (m.getShiny() ? " ★" : "") + Forms.MARK + 6;
        Forms.send(p, title, "", o, c -> { if (c >= 0 && c < o.size()) o.get(c).action().run(); });
    }

    /** Cobblemon 1.8.4's Marks tab (MarksWidget) and the markings under the Pokémon (MarkingsWidget): holders 60-99. */
    private static void marks(ServerPlayer p, Pokemon m, int i, int tab, List<Forms.Opt> o, Runnable stay) {
        o.add(new Forms.Opt("Marks" + (tab == MARKS ? "*" : ""), "summary/tab_marks", () -> {
            PICKED.remove(p.getUUID());
            open(p, i, MARKS);
        }));
        boolean on = tab == MARKS;
        var active = m.getActiveMark();
        var name = Component.literal(BattleUi.name(m));
        o.add(new Forms.Opt(!on ? "" : active == null ? name.getString() : titled(p, active, name), active == null || !on ? "none" : markTexture(active), stay));
        o.add(new Forms.Opt(on && active != null ? Lang.t(p, "cobblemon.mark." + active.getIdentifier().getPath() + ".desc") : "", "none", stay));
        List<com.cobblemon.mod.common.api.mark.Mark> have = new ArrayList<>(m.getMarks());
        have.sort(java.util.Comparator.comparingInt(com.cobblemon.mod.common.api.mark.Mark::getSortOrder)
                .thenComparing(k -> k.getIdentifier() == null ? "" : k.getIdentifier().toString()));
        int pages = Math.max(1, (have.size() + MARK_SLOTS - 1) / MARK_SLOTS), page = Math.min(PAGE.getOrDefault(p.getUUID(), 0), pages - 1);
        for (int k = 0; k < MARK_SLOTS; k++) {
            int at = page * MARK_SLOTS + k;
            var mk = on && at < have.size() ? have.get(at) : null;
            o.add(mk == null ? new Forms.Opt("", "none", stay) : new Forms.Opt(mk.equals(active) ? "s" : "n", markTexture(mk), () -> {
                m.setActiveMark(mk); // Cobblemon's SetActiveMarkPacket handler does the same for a mark it has
                open(p, i, MARKS);
            }));
        }
        o.add(new Forms.Opt(on && pages > 1 ? (page + 1) + "/" + pages : "", "none", () -> {
            PAGE.put(p.getUUID(), (page + 1) % pages); // ponytail: Cobblemon scrolls; a tap on its scroll bar turns the page
            open(p, i, MARKS);
        }));
        List<Integer> markings = new ArrayList<>(m.getMarkings());
        while (markings.size() < 6) markings.add(0);
        for (int k = 0; k < 6; k++) {
            int kk = k;
            o.add(new Forms.Opt(String.valueOf(markings.get(k)), "none", () -> { // none, blue, red, none... as Cobblemon's click
                List<Integer> next = new ArrayList<>(markings);
                next.set(kk, (next.get(kk) + 1) % 3);
                m.setMarkings(next);
                open(p, i, tab);
            }));
        }
    }

    /** "Eevee the Alpha", the title in the mark's colour (the nearest of Bedrock's text colours). */
    static String titled(ServerPlayer p, com.cobblemon.mod.common.api.mark.Mark mk, Component name) {
        String t = Lang.resolve(p, mk.getTitle(Component.literal("\u0000"))).getString(), n = name.getString();
        int at = t.indexOf('\u0000');
        String colour = nearest(mk.getTitleColour());
        return at < 0 ? colour + t : t.substring(0, at) + n + colour + t.substring(at + 1).replace("\u0000", n);
    }

    private static final int[] PALETTE = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    static String nearest(String hex) {
        if (hex == null || hex.isEmpty()) return "";
        int c = Integer.parseInt(hex.replace("#", ""), 16), best = 15;
        long bd = Long.MAX_VALUE;
        for (int k = 0; k < 16; k++) {
            int q = PALETTE[k];
            long d = sq((c >> 16 & 255) - (q >> 16 & 255)) + sq((c >> 8 & 255) - (q >> 8 & 255)) + sq((c & 255) - (q & 255));
            if (d < bd) { bd = d; best = k; }
        }
        return "§" + "0123456789abcdef".charAt(best);
    }

    private static long sq(int v) { return (long) v * v; }

    /** cobblemon:textures/gui/mark/mark_alpha.png -> the UI pack's "mark/mark_alpha". */
    static String markTexture(com.cobblemon.mod.common.api.mark.Mark mk) {
        String path = mk.getTexture().getPath();
        return "mark/" + path.substring(path.lastIndexOf('/') + 1).replace(".png", "");
    }

    /**
     * The moves Cobblemon's move swap offers besides the 4 known: benched ones, then the level-up moves it learned
     * with no room (Cobblemon 1.8.4 doesn't bench those; they are relearnable, and its Summary lists them).
     */
    static List<com.cobblemon.mod.common.api.moves.MoveTemplate> others(Pokemon m) {
        java.util.LinkedHashSet<com.cobblemon.mod.common.api.moves.MoveTemplate> out = new java.util.LinkedHashSet<>();
        m.getBenchedMoves().forEach(b -> out.add(b.getMoveTemplate()));
        m.getRelearnableMoves().forEach(out::add);
        m.getMoveSet().getMoves().forEach(mv -> out.remove(mv.getTemplate()));
        return new ArrayList<>(out);
    }

    /** Moves tab: the first tap picks a move, the second swaps it there (Cobblemon's move swap, benched moves too). */
    private static void pickMove(ServerPlayer p, Pokemon m, int row, int i) {
        Integer first = PICKED.remove(p.getUUID());
        if (first == null) {
            PICKED.put(p.getUUID(), row);
            p.sendSystemMessage(Component.literal(Lang.t(p, "cobblemonlink.summary.swap_hint")));
        } else if (first != row) {
            List<Move> moves = m.getMoveSet().getMoves();
            var others = others(m);
            if (first < 4 && row < 4) m.getMoveSet().swapMove(first, row);
            else if (Math.min(first, row) < moves.size() && Math.max(first, row) >= 4 && Math.max(first, row) - 4 < others.size())
                // a known move for a benched or relearnable one, as Cobblemon's move swap (BenchMoveHandler)
                m.exchangeMove(moves.get(Math.min(first, row)).getTemplate(), others.get(Math.max(first, row) - 4));
        }
        open(p, i, MOVES);
    }

    /** Cobblemon's card HP bar (37 px): one picture a width, coloured by tools/build_bedrock_ui.py's hp_rgb (its gradient). */
    static String hpBar(int hp, int max) {
        int w = max <= 0 ? 0 : Math.round(Math.clamp((float) hp / max, 0, 1) * 37);
        return w == 0 ? "none" : "summary/hp/" + w;
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
                    rows.add(mv == null ? new String[]{"", "", "none"} : new String[]{(picked != null && picked == k ? "> " : "") + Lang.resolve(p, mv.getDisplayName()).getString(),
                            mv.getCurrentPp() + "/" + mv.getMaxPp(), "pc/type/" + mv.getType().getName().toLowerCase(Locale.ROOT)});
                }
                int k = 4;
                for (var t : others(m)) {
                    if (k >= 8) break;
                    rows.add(new String[]{(picked != null && picked == k ? "> " : "") + Lang.resolve(p, t.getDisplayName()).getString(),
                            Lang.t(p, "cobblemonlink.summary.benched"), "pc/type/" + t.getElementalType().getName().toLowerCase(Locale.ROOT)});
                    k++;
                }
            }
            case MARKS -> {} // its own holders (marks())
            case STATS -> {
                for (int k = 0; k < 6; k++) {
                    Stat s = STATS_ORDER[k];
                    String mark = k == 0 ? "" : s == m.getNature().getIncreasedStat() && s != m.getNature().getDecreasedStat() ? " +"
                            : s == m.getNature().getDecreasedStat() && s != m.getNature().getIncreasedStat() ? " -" : "";
                    rows.add(new String[]{Lang.resolve(p, s.getDisplayName()).getString() + mark, (k == 0 ? m.getCurrentHealth() + "/" : "") + m.getStat(s)
                            + "   " + Lang.t(p, "cobblemon.ui.stats.ivs") + " " + m.getIvs().getOrDefault(s) + "  " + Lang.t(p, "cobblemon.ui.stats.evs") + " " + m.getEvs().getOrDefault(s), "none"});
                }
            }
            default -> {
                var curve = m.getSpecies().getExperienceGroup();
                long next = m.getLevel() >= 100 ? 0 : curve.getExperience(m.getLevel() + 1) - m.getExperience();
                List<String> types = new ArrayList<>();
                m.getTypes().forEach(t -> types.add(Lang.resolve(p, t.getDisplayName()).getString()));
                rows.add(new String[]{Lang.t(p, "cobblemon.ui.info.pokedex_number"), String.format("%04d", m.getSpecies().getNationalPokedexNumber()), "none"});
                rows.add(new String[]{Lang.t(p, "cobblemon.ui.info.species"), Lang.resolve(p, m.getSpecies().getTranslatedName()).getString(), "none"});
                rows.add(new String[]{Lang.t(p, "cobblemon.ui.info.type"), String.join(" / ", types), "none"});
                rows.add(new String[]{Lang.t(p, "cobblemon.ui.info.original_trainer"), m.getOriginalTrainerName() == null ? p.getScoreboardName() : m.getOriginalTrainerName(), "none"});
                rows.add(new String[]{Lang.t(p, "cobblemon.ui.info.nature"), Lang.get(p, m.getNature().getDisplayName()), "none"});
                rows.add(new String[]{Lang.t(p, "cobblemon.ui.info.ability"), Lang.get(p, m.getAbility().getDisplayName()), "none"});
                rows.add(new String[]{Lang.t(p, "cobblemon.ui.info.experience_points"), String.valueOf(m.getExperience()), "none"});
                rows.add(new String[]{Lang.t(p, "cobblemon.ui.info.to_next_level"), String.valueOf(next), "none"});
            }
        }
        return rows;
    }
}
