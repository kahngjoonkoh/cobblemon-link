package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Battles for players without the mod. They can't press Cobblemon's R key, so tapping, right-clicking or hitting a
 * wild Pokémon starts the battle (Cobblemon's own pve), and tapping the opponent during it reopens the menu.
 * Cobblemon's battle messages to them drive BattleUi.
 */
public final class Battles {
    private Battles() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTickCount() % 20 == 0) BattleUi.tick();
            BattleUi.tickScreens();
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                net.minecraft.commands.Commands.literal("battle").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    var ui = BattleUi.get(p);
                    if (ui == null) c.getSource().sendFailure(net.minecraft.network.chat.Component.literal(Lang.t(p, "cobblemonlink.battle.not_in")));
                    else ui.reveal();
                    return 1;
                })));
    }

    /** A player without the mod used (or hit) a Pokémon; true if this took the click. */
    public static boolean interact(ServerPlayer p, PokemonEntity pe) {
        if (!Clients.bridged(p.connection)) return false;
        if (p.isShiftKeyDown() && p.getUUID().equals(pe.getOwnerUUID()) && BattleUi.get(p) == null) { // Cobblemon's interaction wheel (before the ball: its held-item choice uses what's in hand)
            Wheel.open(p, pe);
            return true;
        }
        if (p.getMainHandItem().getItem() instanceof PokeBallItem) { // the client sent a click on the Pokémon, not a throw
            p.getMainHandItem().use(p.level(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
            return true;
        }
        if (p.getMainHandItem().getItem() instanceof com.cobblemon.mod.common.item.PokedexItem && BattleUi.get(p) == null) {
            Dex.scan(p, pe);
            return true;
        }
        var ui = BattleUi.get(p);
        if (ui != null) { // in a battle: the menu again
            ui.reveal();
            return true;
        }
        if (!pe.getPokemon().isWild() || pe.isBattling()) return false;
        var result = BattleBuilder.INSTANCE.pve(p, pe);
        if (result instanceof com.cobblemon.mod.common.battles.ErroredBattleStart err) err.sendTo(p, c -> c);
        return true;
    }

    /** Cobblemon's battle packets to a player without the mod (mixin.SendMixin, any thread). */
    public static void packet(ServerPlayer p, CustomPacketPayload payload) {
        // ponytail: vanilla Java gets the chest battle at every level; the full level's battle screen isn't ported yet
        if (payload instanceof com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket bi)
            p.server.execute(() -> BattleUi.spectate(p, bi.getBattleId()));
        else if (payload instanceof BattleMakeChoicePacket) p.server.execute(() -> BattleUi.of(p).makeChoice());
        else if (payload instanceof BattleMessagePacket m) p.server.execute(() -> BattleUi.of(p).message(m.getMessages()));
        else if (payload instanceof BattleEndPacket) p.server.execute(() -> { var ui = BattleUi.get(p); if (ui != null) ui.end(); });
    }
}
