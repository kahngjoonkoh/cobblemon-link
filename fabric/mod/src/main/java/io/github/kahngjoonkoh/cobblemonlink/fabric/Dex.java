package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's Pokédex for players without the mod, from Cobblemon's own Pokédex records (seen and caught): every
 * implemented species in national order, 25 a page. /pokedex opens it (Cobblemon's Pokédex item opens its client
 * screen, which they don't have). Bedrock: the Paper version's form (title ending MARK + 8): 0 / 1 previous / next
 * page, 2 the page, 3 the chosen one, 4-5 its types, 6 seen, 7 owned, 8-32 the page's slots ("0258", "c" caught,
 * "*" chosen; image its picture or "pokedex/unknown").
 */
public final class Dex {
    static final int PAGE = 25;
    private static List<Species> order;

    private Dex() {}

    static void register() {
        // "/dex": Cobblemon's own /pokedex is an operator command, which keeps ours from non-operators
        for (String name : new String[]{"pokedex", "dex"})
            net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                    net.minecraft.commands.Commands.literal(name).executes(c -> {
                        open(c.getSource().getPlayerOrException(), 0, null);
                        return 1;
                    })));
    }

    /** Each player's chosen Pokédex (Cobblemon's regional dexes); national when none. */
    private static final java.util.Map<java.util.UUID, net.minecraft.resources.ResourceLocation> REGION = new java.util.concurrent.ConcurrentHashMap<>();

    /** Cobblemon's dexes in their order, and their names in p's language. */
    static List<net.minecraft.resources.ResourceLocation> regions() {
        return com.cobblemon.mod.common.api.pokedex.Dexes.INSTANCE.getDexEntryMap().keySet().stream().filter(k -> !k.getPath().equals("unknown")).toList();
    }

    static net.minecraft.resources.ResourceLocation region(ServerPlayer p) {
        return REGION.getOrDefault(p.getUUID(), net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "national"));
    }

    static String regionName(ServerPlayer p, net.minecraft.resources.ResourceLocation id) {
        return Lang.t(p, "cobblemon.ui.pokedex.region." + id.getPath());
    }

    /** The species in p's chosen dex, in its order. */
    static List<Species> order(ServerPlayer p) {
        var id = REGION.get(p.getUUID());
        var def = id == null ? null : com.cobblemon.mod.common.api.pokedex.Dexes.INSTANCE.getDexEntryMap().get(id);
        if (def == null || id.getPath().equals("national")) return order();
        List<Species> out = new ArrayList<>();
        for (var e : def.getEntries()) {
            Species sp = PokemonSpecies.getByIdentifier(e.getSpeciesId());
            if (sp != null && !out.contains(sp)) out.add(sp);
        }
        return out.isEmpty() ? order() : out;
    }

    /** Pick a dex: a dropdown on Bedrock (then the Pokédex again), the next one on Java. */
    static void chooseRegion(ServerPlayer p, Runnable then) {
        var all = regions();
        var cur = REGION.getOrDefault(p.getUUID(), net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "national"));
        if (Clients.bedrock(p)) {
            Forms.choice(p, Lang.t(p, "cobblemonlink.dex.region"), Lang.t(p, "cobblemonlink.dex.region"),
                    all.stream().map(r -> regionName(p, r)).toList(), Math.max(0, all.indexOf(cur)), i -> {
                        if (i >= 0) REGION.put(p.getUUID(), all.get(i));
                        then.run();
                    });
            return;
        }
        REGION.put(p.getUUID(), all.get((all.indexOf(cur) + 1) % all.size()));
        then.run();
    }

    static List<Species> order() {
        if (order == null) order = PokemonSpecies.getImplemented().stream().filter(s -> s.getNationalPokedexNumber() > 0)
                .sorted(Comparator.comparingInt(Species::getNationalPokedexNumber)).toList();
        return order;
    }

    static PokedexEntryProgress knowledge(ServerPlayer p, Species s) {
        return Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(p).getKnowledgeForSpecies(s.getResourceIdentifier());
    }

    /**
     * Cobblemon's Pokédex scanner for players without the mod: right-click a Pokémon holding a Pokédex, and Cobblemon
     * scans it (start, then finish a moment later, as its client does) and our Pokédex opens at its entry.
     */
    static void scan(ServerPlayer p, com.cobblemon.mod.common.entity.pokemon.PokemonEntity pe) {
        com.cobblemon.mod.common.net.serverhandling.pokedex.scanner.StartScanningHandler.INSTANCE.handle(
                new com.cobblemon.mod.common.net.messages.server.pokedex.scanner.StartScanningPacket(pe.getId(), 1), p.server, p);
        long at = System.currentTimeMillis() + 2500; // ponytail: Cobblemon wants a scan held a moment; 2.5 s is enough
        Tasks.until(() -> {
            if (p.hasDisconnected() || pe.isRemoved()) return true;
            if (System.currentTimeMillis() < at) return false;
            com.cobblemon.mod.common.net.serverhandling.pokedex.scanner.FinishScanningHandler.INSTANCE.handle(
                    new com.cobblemon.mod.common.net.messages.server.pokedex.scanner.FinishScanningPacket(pe.getId(), 1), p.server, p);
            Species sp = pe.getPokemon().getSpecies();
            open(p, Math.max(0, order(p).indexOf(sp)) / PAGE, sp);
            return true;
        });
    }

    static void open(ServerPlayer p, int page, Species chosen) {
        if (Clients.bedrock(p)) bedrock(p, page, chosen);
        else if (Ui.loaded && Config.vanillaJava().screenArt()) DexArt.open(p);
        else JavaScreens.dex(p, page);
    }

    static void bedrock(ServerPlayer p, int page, Species chosen) {
        List<Species> all = order(p);
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE), pg = Math.floorMod(page, pages);
        List<Species> here = all.subList(pg * PAGE, Math.min(all.size(), pg * PAGE + PAGE));
        Species sel = chosen != null ? chosen : here.getFirst();
        PokedexEntryProgress k = knowledge(p, sel);
        boolean seen = k != PokedexEntryProgress.UNREGISTERED, caught = k == PokedexEntryProgress.OWNED;
        int seenCount = 0, caughtCount = 0;
        for (Species s : all) {
            var x = knowledge(p, s);
            if (x != PokedexEntryProgress.UNREGISTERED) seenCount++;
            if (x == PokedexEntryProgress.OWNED) caughtCount++;
        }
        Runnable stay = () -> bedrock(p, pg, sel);
        List<Forms.Opt> o = new ArrayList<>();
        o.add(new Forms.Opt("<", "none", () -> bedrock(p, pg - 1, null)));
        o.add(new Forms.Opt(">", "none", () -> bedrock(p, pg + 1, null)));
        // the page counter picks the dex (Cobblemon's regional Pokédexes)
        o.add(new Forms.Opt((pg + 1) + "/" + pages, "none", () -> chooseRegion(p, () -> bedrock(p, 0, null))));
        o.add(new Forms.Opt(Lang.t(p, "cobblemon.ui.info.pokedex_number") + String.format(" %04d", sel.getNationalPokedexNumber()), seen ? picture(sel) : "pokedex/unknown", stay));
        List<String> types = new ArrayList<>();
        sel.getTypes().forEach(t -> types.add(t.getName().toLowerCase(Locale.ROOT)));
        for (int t = 0; t < 2; t++) o.add(new Forms.Opt("", seen && t < types.size() ? "pc/type/" + types.get(t) : "none", stay));
        o.add(new Forms.Opt(String.valueOf(seenCount), "none", stay));
        o.add(new Forms.Opt(String.valueOf(caughtCount), "none", stay));
        for (int i = 0; i < PAGE; i++) {
            if (i >= here.size()) {
                o.add(new Forms.Opt("", "none", stay));
                continue;
            }
            Species sp = here.get(i);
            var kk = knowledge(p, sp);
            o.add(new Forms.Opt(String.format("%04d", sp.getNationalPokedexNumber()) + (kk == PokedexEntryProgress.OWNED ? "c" : "") + (sp == sel ? "*" : ""),
                    kk != PokedexEntryProgress.UNREGISTERED ? picture(sp) : "pokedex/unknown", () -> bedrock(p, pg, sp)));
        }
        String entry = caught ? Starters.desc(p, sel.getResourceIdentifier().getPath()) : seen ? Lang.t(p, "cobblemonlink.dex.catch_to_read") : "";
        String name = seen ? Lang.resolve(p, sel.getTranslatedName()).getString() : "???";
        Forms.send(p, name + Forms.MARK + 8, entry, o, c -> { if (c >= 0 && c < o.size()) o.get(c).action().run(); });
    }

    private static String picture(Species s) {
        String id = s.getResourceIdentifier().getPath();
        return Models.has(id) ? "body/" + id : "pokedex/unknown";
    }
}
