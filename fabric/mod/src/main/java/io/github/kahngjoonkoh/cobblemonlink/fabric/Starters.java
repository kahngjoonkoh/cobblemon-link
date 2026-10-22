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

    private Starters() {}

    static void register() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                net.minecraft.commands.Commands.literal("starter").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    if (Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(p).getStarterSelected())
                        c.getSource().sendFailure(Component.literal(Lang.t(p, "cobblemon.ui.starter.alreadyselected")));
                    else Cobblemon.INSTANCE.getStarterHandler().requestStarterChoice(p);
                    return 1;
                })));
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTickCount() % 40 != 0) return;
            for (ServerPlayer p : s.getPlayerList().getPlayers())
                if (Clients.bridged(p.connection) && PartyHud.party(p).isEmpty() && !Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(p).getStarterSelected())
                    p.connection.send(new ClientboundSetActionBarTextPacket(Ui.loaded && Clients.java(p) && Config.vanillaJava().screenArt()
                            ? Ui.starterToast() // Cobblemon's toast art, top right
                            : Component.literal(Lang.t(p, "cobblemon.ui.starter.choose_starter_title") + " " + Lang.t(p, "cobblemonlink.starter.hint")).withStyle(net.minecraft.ChatFormatting.RED)));
        });
    }

    record Region(String category, String name, List<String> species) {}

    static List<Region> regions(ServerPlayer p) {
        List<Region> out = new ArrayList<>();
        for (StarterCategory c : Cobblemon.INSTANCE.getStarterHandler().getStarterList(p)) {
            List<String> sp = c.getPokemon().stream().map(x -> x.getSpecies() == null ? "" : x.getSpecies().replace("cobblemon:", "")).toList();
            out.add(new Region(c.getName(), Lang.get(p, c.getDisplayName()), sp));
        }
        return out;
    }

    /** Cobblemon's starter screen packet reached a player without the mod. */
    public static void open(ServerPlayer p) {
        List<Region> regions = regions(p);
        if (regions.isEmpty() || regions.getFirst().species().isEmpty()) return;
        if (Clients.bedrock(p)) bedrock(p, regions, 0, 0, 0);
        else if (Ui.loaded && Config.vanillaJava().screenArt()) StarterArt.open(p, regions, 0, 0, 0);
        else JavaScreens.starter(p, regions, 0);
    }

    static String desc(ServerPlayer p, String species) {
        var s = PokemonSpecies.getByName(species);
        if (s == null) return "";
        return String.join(" ", s.getPokedex().stream().map(k -> Lang.get(p, k)).toList());
    }

    /** build_bedrock_ui.py STARTER_BUTTONS / STARTER_MAX: four buttons a starter, a region three starters. */
    static final int PER = 4, MAX = 36;

    /**
     * The form, all regions at once (the pack's starter_form browses them without the form closing): per starter
     * "I choose you!" (the card, its Pokédex number), its tile (the region's name on a region's first), its name and
     * Pokédex entry. Only an "I choose you!" (button PER * k) picks; closing leaves the prompt up.
     */
    static void bedrock(ServerPlayer p, List<Region> regions, int page, int region, int index) {
        List<Forms.Opt> opts = new ArrayList<>();
        List<Runnable> picks = new ArrayList<>();
        for (int ri = 0; ri < regions.size() && opts.size() < PER * MAX; ri++) {
            Region r = regions.get(ri);
            for (int j = 0; j < 3; j++) {
                String s = j < r.species().size() ? r.species().get(j) : null;
                var species = s == null ? null : PokemonSpecies.getByName(s);
                if (species == null) {
                    for (int k = 0; k < PER; k++) opts.add(new Forms.Opt("", "none", () -> {}));
                    picks.add(null);
                    continue;
                }
                String category = r.category();
                int sj = j;
                opts.add(new Forms.Opt(String.format("#%04d", species.getNationalPokedexNumber()), "card/" + s, () -> {}));
                opts.add(new Forms.Opt(j == 0 ? r.name() : "", "tile/" + s, () -> {}));
                opts.add(new Forms.Opt(Lang.resolve(p, species.getTranslatedName()).getString(), "none", () -> {}));
                opts.add(new Forms.Opt(desc(p, s), "none", () -> {}));
                picks.add(() -> { // a second press (or a late answer) after the pick is ignored, not "already selected"
                    if (!Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(p).getStarterSelected())
                        Cobblemon.INSTANCE.getStarterHandler().chooseStarter(p, category, sj);
                });
            }
        }
        Forms.send(p, Lang.t(p, "cobblemon.ui.starter.choose_starter_title") + Forms.MARK + 4, "", opts, i -> {
            if (i >= 0 && i % PER == 0 && i / PER < picks.size() && picks.get(i / PER) != null) picks.get(i / PER).run();
        });
    }
}
