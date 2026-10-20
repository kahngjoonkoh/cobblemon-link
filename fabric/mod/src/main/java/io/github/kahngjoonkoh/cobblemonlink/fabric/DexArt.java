package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's Pokédex for vanilla Java (standard level and up), the Paper version's PokedexScreen art, on Cobblemon's
 * Pokédex records: 45 species a page on the lower rows, the chosen one on top (picture, types, number, name) with the
 * Entry / Abilities / Base stats / Moves / Evolutions tabs on the hotbar (click the open tab again for its next part),
 * the page arrows and the All / Seen / Caught filter.
 * ponytail: no search field (no text input on 1.21.1), no form, gender or shiny toggles yet.
 */
final class DexArt {
    static final int PER = 45, LINES = 6, BAR_MAX = 48, INK = 60;
    static final String[] FILTERS = {"All", "Seen", "Caught"};
    private static final char BASE = '', SELECT = '', UNKNOWN = '', CAUGHT = '', UNKNOWN_BIG = '',
            TAB_ON = '', TAB_ICON = '', PLATFORM = '', BAR = '', TYPE_ICON = '';
    private static final String[][] REGIONS = {{"1", "Kanto"}, {"152", "Johto"}, {"252", "Hoenn"}, {"387", "Sinnoh"},
            {"494", "Unova"}, {"650", "Kalos"}, {"722", "Alola"}, {"810", "Galar"}, {"906", "Paldea"}};
    private static final Stats[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

    static final class View {
        int page, tab, scroll, filter;
        Species sel;
    }

    private static final Map<UUID, View> VIEWS = new HashMap<>();

    private DexArt() {}

    static int tileRow(int i) { return 3 + i / 9; }

    static String region(int dex) {
        String r = REGIONS[0][1];
        for (String[] e : REGIONS) if (dex >= Integer.parseInt(e[0])) r = e[1];
        return r;
    }

    static boolean seen(ServerPlayer p, Species s) { return Dex.knowledge(p, s) != PokedexEntryProgress.UNREGISTERED; }

    static boolean caught(ServerPlayer p, Species s) { return Dex.knowledge(p, s) == PokedexEntryProgress.OWNED; }

    static void open(ServerPlayer p) { show(p, VIEWS.computeIfAbsent(p.getUUID(), k -> new View())); }

    static void show(ServerPlayer p, View v) {
        List<Species> all = Dex.order().stream().filter(s -> v.filter == 0 || (v.filter == 1 ? seen(p, s) : caught(p, s))).toList();
        int pages = Math.max(1, (all.size() + PER - 1) / PER);
        v.page = Math.floorMod(v.page, pages);
        if (v.sel == null) v.sel = Dex.order().getFirst();
        Chest m = new Chest(5, title(p, v, all, pages)).hideInventory();
        for (int i = 0; i < PER; i++) {
            int k = v.page * PER + i;
            if (k >= all.size()) break;
            Species sp = all.get(k);
            m.on(Art.raw(i % 9, tileRow(i)), b -> {
                if (sp != v.sel) {
                    v.sel = sp;
                    v.scroll = 0;
                }
                show(p, v);
            });
        }
        m.on(Art.raw(0, 8), b -> { v.page--; show(p, v); });
        m.on(Art.raw(8, 8), b -> { v.page++; show(p, v); });
        m.on(Art.raw(1, 8), b -> {
            v.filter = Math.floorMod(v.filter + (b == 1 ? -1 : 1), FILTERS.length);
            v.page = 0;
            show(p, v);
        });
        for (int k = 0; k < 5; k++) {
            int tab = k;
            m.on(Art.raw(3 + k, 8), b -> {
                v.scroll = tab == v.tab ? v.scroll + 1 : 0;
                v.tab = tab;
                show(p, v);
            });
        }
        m.open(p);
    }

    private static Component title(ServerPlayer p, View v, List<Species> all, int pages) {
        Ui.Title t = new Ui.Title();
        int seenCount = 0, caughtCount = 0;
        for (Species s : Dex.order()) {
            if (seen(p, s)) seenCount++;
            if (caught(p, s)) caughtCount++;
        }
        Art.glyph(t, 0, 0, BASE, 7);
        Art.text(t, "pc_label", 19, 12, "National", false);
        Art.text(t, "pc_bold", 125, 13, String.format("%04d", seenCount), false);
        Art.text(t, "pc_bold", 155, 13, String.format("%04d", caughtCount), false);
        for (int i = 0; i < PER && v.page * PER + i < all.size(); i++) {
            Species sp = all.get(v.page * PER + i);
            int cx = Art.x(i % 9), cy = Art.y(tileRow(i));
            var b = Art.body(sp.getResourceIdentifier().getPath(), false);
            if (seen(p, sp) && b != null) Art.put(t, cx, cy, b, 17, 7);
            else Art.glyph(t, cx + 4, cy + 4, UNKNOWN, 7);
            if (caught(p, sp)) Art.glyph(t, cx + 11, cy, CAUGHT, 5);
            String no = String.format("%04d", sp.getNationalPokedexNumber());
            Art.put(t, cx, cy, Ui.text(no, Ui.id("dex_no")), Art.width("dex_no", no) + no.length(), 4);
            if (sp == v.sel) Art.glyph(t, cx - 1, cy - 1, SELECT, 7);
        }
        String strip;
        if (all.isEmpty()) strip = "Nothing " + FILTERS[v.filter].toLowerCase(Locale.ROOT) + " yet";
        else {
            int lo = all.get(v.page * PER).getNationalPokedexNumber(), hi = all.get(Math.min(all.size(), v.page * PER + PER) - 1).getNationalPokedexNumber();
            String where = region(lo).equals(region(hi)) ? region(lo) : region(lo) + "-" + region(hi);
            strip = where + "  " + String.format("%04d-%04d", lo, hi) + "  " + FILTERS[v.filter] + "  " + (v.page + 1) + "/" + pages;
        }
        Art.text(t, "pc_bold", 91, 121, Art.fit("pc_bold", strip, 160), true);
        Art.glyph(t, Art.x(3 + v.tab) - 1, Art.y(8) - 1, TAB_ON, 7);
        Art.glyph(t, Art.x(3 + v.tab), Art.y(8), (char) (TAB_ICON + v.tab), 7);
        Species s = v.sel;
        boolean seen = seen(p, s), caught = caught(p, s);
        List<String> types = new ArrayList<>();
        s.getTypes().forEach(ty -> types.add(JavaScreens.type(ty)));
        if (seen) {
            Art.glyph(t, 12, 62, (char) (PLATFORM + Ui.TYPES.indexOf(types.getFirst())), 7);
            var body = Art.body(s.getResourceIdentifier().getPath(), true);
            if (body != null) Art.put(t, 24, 29, body, 45, 7);
            for (int k = 0; k < types.size(); k++) Art.glyph(t, 12 + 8 * k, 29, (char) (TYPE_ICON + Ui.TYPES.indexOf(types.get(k))), 7);
        } else {
            Art.glyph(t, 12, 62, (char) (PLATFORM + Ui.TYPES.size()), 7);
            Art.glyph(t, 38, 40, UNKNOWN_BIG, 7);
        }
        Art.text(t, "pc_bold", 84, 29, String.format("%04d", s.getNationalPokedexNumber()), false);
        Art.text(t, "pc_bold", 104, 29, Art.fit("pc_bold", seen ? Lang.resolve(s.getTranslatedName()).getString() : "-----", 66), false);
        List<String> lines = page(p, v, s, seen, caught);
        int per = LINES, from = lines.size() <= per ? 0 : Math.floorMod(v.scroll, (lines.size() + per - 1) / per) * per;
        for (int k = from; k < Math.min(lines.size(), from + per); k++) {
            int ly = 40 + 6 * (k - from);
            String line = lines.get(k);
            if (v.tab == 2 && line.contains("|")) {
                String[] f = line.split("\\|");
                Art.text(t, "pc_bold", 85, ly, f[0], false, INK);
                Art.text(t, "pc_text", 103, ly, f[1], false, INK);
                int w = Math.max(1, Math.min(BAR_MAX, Integer.parseInt(f[1]) * BAR_MAX / 255));
                Art.glyph(t, 118, ly + 1, (char) (BAR + w - 1), 3);
            } else Art.text(t, "pc_text", 85, ly, line, false, INK);
        }
        if (lines.size() > per) Art.text(t, "pc_text", 168, 74, "v", false, INK);
        return t.build();
    }

    static List<String> page(ServerPlayer p, View v, Species s, boolean seen, boolean caught) {
        java.util.function.ToIntFunction<String> width = x -> (int) Math.ceil(Art.width("pc_text", x));
        if (!seen) return v.tab == 0 ? List.of("Not seen yet.") : List.of();
        return switch (v.tab) {
            case 0 -> caught ? Ui.wrap(Art.plain(Starters.desc(s.getResourceIdentifier().getPath())), 84, width) : List.of("Catch it to read its", "Pokédex entry.");
            case 1 -> {
                List<String> out = new ArrayList<>();
                for (var a : s.getAbilities()) {
                    String n = Lang.get(a.getTemplate().getDisplayName());
                    if (!out.contains(n)) out.add(n);
                }
                yield out;
            }
            case 2 -> {
                String[] label = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
                List<String> out = new ArrayList<>();
                for (int i = 0; i < 6; i++) out.add(label[i] + "|" + s.getBaseStats().getOrDefault(STATS[i], 0));
                yield out;
            }
            case 3 -> {
                List<String> out = new ArrayList<>();
                new java.util.TreeMap<>(s.getMoves().getLevelUpMoves()).forEach((lv, ms) -> ms.forEach(mv ->
                        out.add("Lv" + lv + "  " + Lang.resolve(mv.getDisplayName()).getString())));
                yield out;
            }
            default -> evolutions(p, s);
        };
    }

    /** The whole family from its first stage: "Lv16 > Ivysaur"; unseen ones as ???. */
    static List<String> evolutions(ServerPlayer p, Species sp) {
        Species root = sp;
        for (int guard = 0; root.getPreEvolution() != null && guard < 5; guard++) root = root.getPreEvolution().getSpecies();
        List<String> out = new ArrayList<>();
        family(p, root, "", out);
        return out.size() == 1 ? List.of(out.getFirst(), "Doesn't evolve.") : out;
    }

    private static void family(ServerPlayer p, Species s, String how, List<String> out) {
        String name = seen(p, s) ? Lang.resolve(s.getTranslatedName()).getString() : "???";
        out.add(how.isEmpty() ? name : how + " > " + name);
        if (out.size() > 12) return;
        for (var e : s.getEvolutions()) {
            String result = e.getResult().getSpecies();
            var next = result == null ? null : com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(result.replace("cobblemon:", ""));
            if (next == null) continue;
            String req = "Evolves";
            for (var r : e.getRequirements()) if (r instanceof LevelRequirement l) req = "Lv" + l.getMinLevel();
            family(p, next, req, out);
        }
    }
}
