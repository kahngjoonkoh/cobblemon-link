package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.config.starter.StarterCategory;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's starter choice for players without the mod. /starter asks Cobblemon's StarterHandler, which decides
 * whether the player may choose and answers with its starter screen packet; that opens our screen here, and a pick
 * goes back through chooseStarter, as Cobblemon's own screen does. Until then the starter prompt stays in the action
 * bar (Cobblemon's "Check out the starters!" toast, with our command for its M key).
 * Bedrock: the Paper version's starter form (title ending MARK + 4; build_bedrock_ui.py starter_form).
 */
public final class Starters {
    static final int PER_PAGE = 3; // regions on a Bedrock form page

    private Starters() {}

    static void register() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                net.minecraft.commands.Commands.literal("starter").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    if (Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(p).getStarterSelected())
                        c.getSource().sendFailure(Component.literal("You already chose your starter."));
                    else Cobblemon.INSTANCE.getStarterHandler().requestStarterChoice(p);
                    return 1;
                })));
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTickCount() % 40 != 0) return;
            for (ServerPlayer p : s.getPlayerList().getPlayers())
                if (Clients.bridged(p.connection) && !Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(p).getStarterSelected())
                    p.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Check out the starters! Type /starter")
                            .withStyle(net.minecraft.ChatFormatting.RED)));
        });
    }

    record Region(String category, String name, List<String> species) {}

    static List<Region> regions(ServerPlayer p) {
        List<Region> out = new ArrayList<>();
        for (StarterCategory c : Cobblemon.INSTANCE.getStarterHandler().getStarterList(p)) {
            List<String> sp = c.getPokemon().stream().map(x -> x.getSpecies() == null ? "" : x.getSpecies().replace("cobblemon:", "")).toList();
            out.add(new Region(c.getName(), Lang.get(c.getDisplayName()), sp));
        }
        return out;
    }

    /** Cobblemon's starter screen packet reached a player without the mod. */
    public static void open(ServerPlayer p) {
        List<Region> regions = regions(p);
        if (regions.isEmpty() || regions.getFirst().species().isEmpty()) return;
        if (Clients.bedrock(p)) bedrock(p, regions, 0, 0, 0);
        else JavaScreens.starter(p, regions, 0);
    }

    static String desc(String species) {
        var s = PokemonSpecies.getByName(species);
        if (s == null) return "";
        return String.join(" ", s.getPokedex().stream().map(Lang::get).toList());
    }

    /**
     * The form: 0 "I choose you!" (the Pokédex number, the card), 1 and 2 the page arrows, then a row of 3 a region
     * (the region's name on its first tile, "*" on the selected one). A tap re-sends it, as a click reopens a chest.
     */
    static void bedrock(ServerPlayer p, List<Region> regions, int page, int region, int index) {
        String selected = regions.get(region).species().get(index);
        var species = PokemonSpecies.getByName(selected);
        Runnable stay = () -> bedrock(p, regions, page, region, index);
        List<Forms.Opt> opts = new ArrayList<>();
        opts.add(new Forms.Opt(String.format("#%04d", species == null ? 0 : species.getNationalPokedexNumber()), "card/" + selected,
                () -> Cobblemon.INSTANCE.getStarterHandler().chooseStarter(p, regions.get(region).category(), index)));
        opts.add(new Forms.Opt("", "up", page > 0 ? () -> bedrock(p, regions, page - PER_PAGE, region, index) : stay));
        opts.add(new Forms.Opt("", "down", page + PER_PAGE < regions.size() ? () -> bedrock(p, regions, page + PER_PAGE, region, index) : stay));
        for (int k = 0; k < PER_PAGE && page + k < regions.size(); k++) {
            Region r = regions.get(page + k);
            int ri = page + k;
            for (int j = 0; j < 3; j++) {
                if (j >= r.species().size()) {
                    opts.add(new Forms.Opt("", "none", stay));
                    continue;
                }
                String s = r.species().get(j);
                int sj = j;
                opts.add(new Forms.Opt((j == 0 ? r.name() : "") + (ri == region && j == index ? "*" : ""), "tile/" + s,
                        () -> bedrock(p, regions, page, ri, sj)));
            }
        }
        String name = species == null ? selected : Lang.resolve(species.getTranslatedName()).getString();
        Forms.send(p, name + Forms.MARK + 4, desc(selected), opts, i -> { if (i >= 0 && i < opts.size()) opts.get(i).action().run(); });
    }
}
