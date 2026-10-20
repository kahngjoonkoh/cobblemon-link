package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.api.pasture.PastureLink;
import com.cobblemon.mod.common.api.pasture.PastureLinkManager;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.net.messages.server.pasture.PasturePokemonPacket;
import com.cobblemon.mod.common.net.messages.server.pasture.UnpastureAllPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.pasture.UnpasturePokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pasture.PasturePokemonHandler;
import com.cobblemon.mod.common.net.serverhandling.pasture.UnpastureAllPokemonHandler;
import com.cobblemon.mod.common.net.serverhandling.pasture.UnpasturePokemonHandler;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Cobblemon's pasture screen for players without the mod (Cobblemon opens it with OpenPasturePacket after linking the
 * pasture to the player's PC): the Pokémon out to pasture, recall one or all of yours, or put one from your PC out.
 * Every choice goes through Cobblemon's own pasture handlers, which check the link and permissions.
 * Bedrock: forms; vanilla Java: chests.
 */
final class Pasture {
    private Pasture() {}

    /** Cobblemon has opened the pasture: its link is the player's from now on. */
    static void open(ServerPlayer p) {
        PastureLink link = PastureLinkManager.getLinkByPlayer(p);
        if (link == null || !(p.level().getBlockEntity(link.getPos()) instanceof PokemonPastureBlockEntity be)) return;
        var perms = link.getPermissions();
        List<Forms.Opt> opts = new ArrayList<>();
        List<Pokemon> shown = new ArrayList<>(); // opts' Pokémon, null for the buttons
        if (perms.getCanPasture() && be.getTetheredPokemon().size() < perms.getMaxPokemon()) {
            opts.add(new Forms.Opt("Pasture a Pokémon", "none", () -> choose(p, link)));
            shown.add(null);
        }
        for (var t : be.getTetheredPokemon()) {
            Pokemon m = t.getPokemon();
            if (m == null) continue;
            boolean mine = t.getPlayerId().equals(p.getUUID());
            String text = BattleUi.name(m) + "  Lv. " + m.getLevel() + (mine ? "" : "  (" + t.getPlayerName() + ")");
            Runnable recall = mine || perms.getCanUnpastureOthers() ? () -> {
                UnpasturePokemonHandler.INSTANCE.handle(new UnpasturePokemonPacket(link.getLinkId(), t.getPokemonId()), p.server, p);
                p.server.execute(() -> open(p));
            } : () -> open(p);
            opts.add(new Forms.Opt(text, image(m), recall));
            shown.add(m);
        }
        opts.add(new Forms.Opt("Recall all mine", "none", () -> {
            UnpastureAllPokemonHandler.INSTANCE.handle(new UnpastureAllPokemonPacket(link.getLinkId()), p.server, p);
            p.server.execute(() -> open(p));
        }));
        shown.add(null);
        String title = "Pasture  " + be.getTetheredPokemon().size() + "/" + perms.getMaxPokemon();
        if (Clients.bedrock(p)) {
            Forms.send(p, title, "Tap a Pokémon to bring it back to your PC.", opts, i -> { if (i >= 0) opts.get(i).action().run(); });
            return;
        }
        Chest c = new Chest(6, title);
        for (int i = 0; i < Math.min(opts.size(), 54); i++) {
            Pokemon m = shown.get(i);
            c.set(i, m == null ? Chest.icon(Items.HAY_BLOCK, opts.get(i).text(), List.of()) : JavaScreens.mon(m, "Click: recall"), opts.get(i).action());
        }
        c.open(p);
    }

    /** The Pokémon in the linked PC that aren't out already. */
    private static void choose(ServerPlayer p, PastureLink link) {
        var pc = link.getPC(p.registryAccess());
        if (pc == null) return;
        List<Pokemon> mons = new ArrayList<>();
        for (Pokemon m : pc) if (m.getTetheringId() == null) mons.add(m);
        Runnable[] act = new Runnable[mons.size()];
        for (int i = 0; i < act.length; i++) {
            Pokemon m = mons.get(i);
            act[i] = () -> {
                PasturePokemonHandler.INSTANCE.handle(new PasturePokemonPacket(m.getUuid(), link.getLinkId()), p.server, p);
                p.server.execute(() -> open(p));
            };
        }
        if (Clients.bedrock(p)) {
            List<Forms.Opt> opts = new ArrayList<>();
            for (int i = 0; i < act.length; i++) opts.add(new Forms.Opt(BattleUi.name(mons.get(i)) + "  Lv. " + mons.get(i).getLevel(), image(mons.get(i)), act[i]));
            Forms.send(p, "Pasture a Pokémon", mons.isEmpty() ? "Your PC has no Pokémon to put out." : "From your PC:", opts,
                    i -> { if (i >= 0) opts.get(i).action().run(); else open(p); });
            return;
        }
        // ponytail: the first 53 PC Pokémon; pages if someone's PC is bigger and they want the rest
        Chest c = new Chest(6, "Pasture a Pokémon");
        for (int i = 0; i < Math.min(act.length, 53); i++) c.set(i, JavaScreens.mon(mons.get(i), "Click: put out to pasture"), act[i]);
        c.set(53, Chest.icon(Items.ARROW, "Back", List.of()), () -> open(p));
        c.open(p);
    }

    private static String image(Pokemon m) {
        String look = Summary.look(m);
        return Models.has(look) ? "body/" + look : "none";
    }
}
