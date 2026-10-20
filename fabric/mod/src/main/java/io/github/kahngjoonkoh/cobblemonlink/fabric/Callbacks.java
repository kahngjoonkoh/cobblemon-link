package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.api.callback.MoveSelectCallbacks;
import com.cobblemon.mod.common.api.callback.MoveSelectDTO;
import com.cobblemon.mod.common.api.callback.PartyMoveSelectCallbacks;
import com.cobblemon.mod.common.api.callback.PartySelectCallbacks;
import com.cobblemon.mod.common.api.callback.PartySelectPokemonDTO;
import com.cobblemon.mod.common.net.messages.client.callback.OpenMoveCallbackPacket;
import com.cobblemon.mod.common.net.messages.client.callback.OpenPartyCallbackPacket;
import com.cobblemon.mod.common.net.messages.client.callback.OpenPartyMoveCallbackPacket;
import com.cobblemon.mod.common.net.messages.client.toast.ToastPacket;
import com.cobblemon.mod.common.net.messages.client.ui.PokedexUIPacket;
import com.cobblemon.mod.common.net.messages.client.ui.SummaryUIPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Cobblemon's smaller client screens for players without the mod: "choose a Pokémon" and "choose a move" (a potion,
 * a candy, an evolution stone or an Ether used on a Pokémon; held items), the Summary and Pokédex Cobblemon opens
 * itself (its commands, the Pokédex item) and its toasts. The answer goes back through Cobblemon's own callbacks.
 * Bedrock: plain Floodgate forms; vanilla Java: chests.
 */
final class Callbacks {
    private Callbacks() {}

    /** Cobblemon's packets to a player without the mod (mixin.SendMixin, any thread). */
    static void packet(ServerPlayer p, CustomPacketPayload payload) {
        if (payload instanceof OpenPartyCallbackPacket c) p.server.execute(() -> party(p, c.getUuid(), c.getTitle(), c.getPokemon(),
                i -> PartySelectCallbacks.INSTANCE.handleCallback(p, c.getUuid(), i), () -> PartySelectCallbacks.INSTANCE.handleCancelled(p, c.getUuid())));
        else if (payload instanceof OpenMoveCallbackPacket c) p.server.execute(() -> moves(p, c.getTitle(), c.getMoves(),
                i -> MoveSelectCallbacks.INSTANCE.handleCallback(p, c.getUuid(), i), () -> MoveSelectCallbacks.INSTANCE.handleCancelled(p, c.getUuid())));
        else if (payload instanceof OpenPartyMoveCallbackPacket c) p.server.execute(() -> {
            List<PartySelectPokemonDTO> mons = new ArrayList<>();
            c.getPokemonList().forEach(x -> mons.add(x.getFirst()));
            Runnable cancel = () -> PartyMoveSelectCallbacks.INSTANCE.handleCancelled(p, c.getUuid());
            party(p, c.getUuid(), c.getPartyTitle(), mons, i -> moves(p, c.getPartyTitle(), c.getPokemonList().get(i).getSecond(),
                    m -> PartyMoveSelectCallbacks.INSTANCE.handleCallback(p, c.getUuid(), i, m), cancel), cancel);
        });
        else if (payload instanceof SummaryUIPacket) p.server.execute(() -> Summary.open(p, 0, Summary.INFO));
        else if (payload instanceof PokedexUIPacket) p.server.execute(() -> Dex.open(p, 0, null));
        else if (payload instanceof ToastPacket t && !Clients.java(p) || payload instanceof ToastPacket && Config.vanillaJava().screenArt())
            p.server.execute(() -> p.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.empty().append(((ToastPacket) payload).getTitle()).append(" ").append(((ToastPacket) payload).getDescription()))));
    }

    static void party(ServerPlayer p, UUID id, Component title, List<PartySelectPokemonDTO> mons, IntConsumer chosen, Runnable cancelled) {
        String t = Lang.resolve(p, title).getString();
        if (Clients.bedrock(p)) {
            List<Forms.Opt> opts = new ArrayList<>();
            for (int i = 0; i < mons.size(); i++) {
                var m = mons.get(i);
                String species = m.getPokemonProperties().getSpecies() == null ? "" : m.getPokemonProperties().getSpecies();
                String look = Models.look(species.replace("cobblemon:", ""), m.getAspects());
                int ii = i;
                opts.add(new Forms.Opt(name(m) + "\n" + m.getCurrentHealth() + "/" + m.getMaxHealth() + (m.getEnabled() ? "" : "  (can't)"),
                        Models.has(look) ? "body/" + look : "none", () -> chosen.accept(ii)));
            }
            Forms.send(p, t, "", opts, i -> { if (i >= 0 && i < opts.size() && mons.get(i).getEnabled()) opts.get(i).action().run(); else cancelled.run(); });
            return;
        }
        Chest c = new Chest(1, t);
        for (int i = 0; i < mons.size() && i < 9; i++) {
            var m = mons.get(i);
            int ii = i;
            c.set(i, Chest.icon(m.getEnabled() ? Items.NAME_TAG : Items.BARRIER, name(m), List.of(m.getCurrentHealth() + "/" + m.getMaxHealth() + " HP")),
                    m.getEnabled() ? () -> { p.closeContainer(); chosen.accept(ii); } : null);
        }
        c.open(p);
    }

    static void moves(ServerPlayer p, Component title, List<MoveSelectDTO> moves, IntConsumer chosen, Runnable cancelled) {
        String t = Lang.resolve(p, title).getString();
        if (Clients.bedrock(p)) {
            List<Forms.Opt> opts = new ArrayList<>();
            for (int i = 0; i < moves.size(); i++) {
                var m = moves.get(i);
                int ii = i;
                String type = m.getMoveTemplate().getElementalType().getName().toLowerCase(java.util.Locale.ROOT);
                opts.add(new Forms.Opt(Lang.resolve(p, m.getMoveTemplate().getDisplayName()).getString() + "\n" + m.getPp() + "/" + m.getPpMax(),
                        "move_" + (BattleUi.TYPES.contains(type) ? type : "normal"), () -> chosen.accept(ii)));
            }
            Forms.send(p, t, "", opts, i -> { if (i >= 0 && i < opts.size() && moves.get(i).getEnabled()) opts.get(i).action().run(); else cancelled.run(); });
            return;
        }
        Chest c = new Chest(1, t);
        for (int i = 0; i < moves.size() && i < 9; i++) {
            var m = moves.get(i);
            int ii = i;
            String type = m.getMoveTemplate().getElementalType().getName().toLowerCase(java.util.Locale.ROOT);
            c.set(i, Chest.icon(m.getEnabled() ? JavaScreens.typeIcon(type) : Items.BARRIER, Lang.resolve(p, m.getMoveTemplate().getDisplayName()).getString(),
                    List.of("PP " + m.getPp() + "/" + m.getPpMax())), m.getEnabled() ? () -> { p.closeContainer(); chosen.accept(ii); } : null);
        }
        c.open(p);
    }

    private static String name(PartySelectPokemonDTO m) {
        var props = m.getPokemonProperties();
        String n = props.getNickname() != null ? props.getNickname().getString() : null;
        if (n != null && !n.isBlank()) return n;
        var sp = props.getSpecies() == null ? null : com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(props.getSpecies().replace("cobblemon:", ""));
        return sp == null ? String.valueOf(props.getSpecies()) : Lang.resolve((net.minecraft.server.level.ServerPlayer) null, sp.getTranslatedName()).getString() + (props.getLevel() != null ? "  Lv." + props.getLevel() : "");
    }
}
