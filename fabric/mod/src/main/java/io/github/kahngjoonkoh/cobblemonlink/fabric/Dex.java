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
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                net.minecraft.commands.Commands.literal("pokedex").executes(c -> {
                    open(c.getSource().getPlayerOrException(), 0, null);
                    return 1;
                })));
    }

    static List<Species> order() {
        if (order == null) order = PokemonSpecies.getImplemented().stream().filter(s -> s.getNationalPokedexNumber() > 0)
                .sorted(Comparator.comparingInt(Species::getNationalPokedexNumber)).toList();
        return order;
    }

    static PokedexEntryProgress knowledge(ServerPlayer p, Species s) {
        return Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(p).getKnowledgeForSpecies(s.getResourceIdentifier());
    }

    static void open(ServerPlayer p, int page, Species chosen) {
        if (Clients.bedrock(p)) bedrock(p, page, chosen);
        else if (Ui.loaded && Config.vanillaJava().screenArt()) DexArt.open(p);
        else JavaScreens.dex(p, page);
    }

    static void bedrock(ServerPlayer p, int page, Species chosen) {
        List<Species> all = order();
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
        o.add(new Forms.Opt((pg + 1) + "/" + pages, "none", stay));
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
