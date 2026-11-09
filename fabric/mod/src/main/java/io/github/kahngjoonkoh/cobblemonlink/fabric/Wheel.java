package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractTypePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.server.pokemon.interact.InteractPokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.pokemon.interact.InteractPokemonHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Cobblemon's interaction wheel for your own Pokémon (shoulder, ride, held item, cosmetic item), opened by Shift +
 * right-click (or tap) since its key needs the mod. Each choice goes through Cobblemon's own InteractPokemonHandler,
 * which checks what the Pokémon can do; the held item is the one in your main hand (empty hand: take it back).
 * Bedrock: a form; vanilla Java: a chest.
 */
final class Wheel {
    private Wheel() {}

    static void open(ServerPlayer p, PokemonEntity pe) {
        record Choice(String name, String image, net.minecraft.world.item.Item item, InteractTypePokemon type) {}
        // the choices Cobblemon's own wheel enables (PokemonEntity's InteractPokemonUIPacket): a Mudkip offered
        // Shoulder and Ride, and pressing them did nothing (seen on a real Bedrock 26.60 client)
        boolean shoulder = pe.canSitOnShoulder() && Cobblemon.INSTANCE.getStorage().getParty(p).toGappyList().contains(pe.getPokemon());
        boolean ride = pe.isRideableFor(p);
        List<Choice> all = List.of(new Choice(Lang.t(p, "cobblemon.ui.interact.mount.shoulder"), "none", Items.FEATHER, InteractTypePokemon.SHOULDER),
                new Choice(Lang.t(p, "cobblemon.ui.interact.ride"), "none", Items.SADDLE, InteractTypePokemon.RIDE),
                new Choice(Lang.t(p, p.getMainHandItem().isEmpty() ? "cobblemonlink.wheel.take_item" : "cobblemon.ui.interact.give.item"), "none", Items.CHEST, InteractTypePokemon.HELD_ITEM),
                new Choice(Lang.t(p, p.getMainHandItem().isEmpty() ? "cobblemonlink.wheel.take_cosmetic" : "cobblemon.ui.interact.give.cosmetic_item"), "none", Items.LEATHER_HELMET, InteractTypePokemon.COSMETIC_ITEM));
        List<Choice> choices = all.stream().filter(c -> c.type() != InteractTypePokemon.SHOULDER || shoulder)
                .filter(c -> c.type() != InteractTypePokemon.RIDE || ride).toList();
        Runnable[] act = new Runnable[choices.size()];
        for (int i = 0; i < act.length; i++) {
            var type = choices.get(i).type();
            act[i] = () -> InteractPokemonHandler.INSTANCE.handle(new InteractPokemonPacket(pe.getUUID(), type), p.server, p);
        }
        String title = BattleUi.name(pe.getPokemon());
        if (Clients.bedrock(p)) {
            List<Forms.Opt> opts = new ArrayList<>();
            for (int i = 0; i < act.length; i++) opts.add(new Forms.Opt(choices.get(i).name(), choices.get(i).image(), act[i]));
            Forms.send(p, title, "", opts, i -> { if (i >= 0 && i < opts.size()) opts.get(i).action().run(); });
            return;
        }
        Chest c = new Chest(1, title);
        for (int i = 0; i < act.length; i++) {
            Runnable r = act[i];
            c.set(1 + 2 * i, Chest.icon(choices.get(i).item(), choices.get(i).name(), List.of()), () -> { p.closeContainer(); r.run(); });
        }
        c.open(p);
    }
}
