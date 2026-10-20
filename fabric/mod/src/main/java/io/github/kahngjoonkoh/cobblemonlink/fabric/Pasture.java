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
            opts.add(new Forms.Opt(Lang.t(p, "cobblemonlink.pasture.add"), "none", () -> choose(p, link)));
            shown.add(null);
        }
        for (var t : be.getTetheredPokemon()) {
            Pokemon m = t.getPokemon();
            if (m == null) continue;
            boolean mine = t.getPlayerId().equals(p.getUUID());
            String text = BattleUi.name(m) + "  " + Lang.t(p, "cobblemon.ui.lv") + " " + m.getLevel() + (mine ? "" : "  (" + t.getPlayerName() + ")"); // ponytail: "Lv." from Cobblemon below
            Runnable recall = mine || perms.getCanUnpastureOthers() ? () -> {
                UnpasturePokemonHandler.INSTANCE.handle(new UnpasturePokemonPacket(link.getLinkId(), t.getPokemonId()), p.server, p);
                p.server.execute(() -> open(p));
            } : () -> open(p);
            opts.add(new Forms.Opt(text, image(m), recall));
            shown.add(m);
        }
        opts.add(new Forms.Opt(Lang.t(p, "cobblemon.ui.pasture.recall_all"), "none", () -> {
            UnpastureAllPokemonHandler.INSTANCE.handle(new UnpastureAllPokemonPacket(link.getLinkId()), p.server, p);
            p.server.execute(() -> open(p));
        }));
        shown.add(null);
        String title = Lang.t(p, "cobblemon.ui.pasture") + "  " + be.getTetheredPokemon().size() + "/" + perms.getMaxPokemon();
        if (Clients.bedrock(p)) {
            Forms.send(p, title, Lang.t(p, "cobblemonlink.pasture.hint"), opts, i -> { if (i >= 0) opts.get(i).action().run(); });
            return;
        }
        Chest c = new Chest(6, title);
        for (int i = 0; i < Math.min(opts.size(), 54); i++) {
            Pokemon m = shown.get(i);
            c.set(i, m == null ? Chest.icon(Items.HAY_BLOCK, opts.get(i).text(), List.of()) : JavaScreens.mon(p, m, Lang.t(p, "cobblemonlink.pasture.click_recall")), opts.get(i).action());
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
            for (int i = 0; i < act.length; i++) opts.add(new Forms.Opt(BattleUi.name(mons.get(i)) + "  " + Lang.t(p, "cobblemon.ui.lv") + " " + mons.get(i).getLevel(), image(mons.get(i)), act[i]));
            Forms.send(p, Lang.t(p, "cobblemonlink.pasture.add"), Lang.t(p, mons.isEmpty() ? "cobblemonlink.pasture.none" : "cobblemonlink.pasture.from_pc"), opts,
                    i -> { if (i >= 0) opts.get(i).action().run(); else open(p); });
            return;
        }
        // ponytail: the first 53 PC Pokémon; pages if someone's PC is bigger and they want the rest
        Chest c = new Chest(6, Lang.t(p, "cobblemonlink.pasture.add"));
        for (int i = 0; i < Math.min(act.length, 53); i++) c.set(i, JavaScreens.mon(p, mons.get(i), Lang.t(p, "cobblemonlink.pasture.click_add")), act[i]);
        c.set(53, Chest.icon(Items.ARROW, Lang.t(p, "cobblemonlink.back"), List.of()), () -> open(p));
        c.open(p);
    }

    private static String image(Pokemon m) {
        String look = Summary.look(m);
        return Models.has(look) ? "body/" + look : "none";
    }
}
