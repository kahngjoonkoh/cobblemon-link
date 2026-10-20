package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Paper version's small things: beds heal the party (config bedsHeal), /pokeballs [n] for admins (Cobblemon has
 * /pokegive, /pokespawn, /pokeheal and /pc already).
 */
final class Extras {
    private Extras() {}

    static void register() {
        EntitySleepEvents.START_SLEEPING.register((e, pos) -> {
            if (!Config.bedsHeal() || !(e instanceof ServerPlayer p) || BattleRegistry.getBattleByParticipatingPlayer(p) != null) return;
            var party = Cobblemon.INSTANCE.getStorage().getParty(p);
            if (PartyHud.party(p).isEmpty()) return;
            party.heal();
            p.sendSystemMessage(Component.literal(Lang.t(p, "cobblemon.healingmachine.healed")).withStyle(ChatFormatting.GREEN));
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, ctx, env) -> d.register(
                Commands.literal("pokeballs").requires(s -> s.hasPermission(2))
                        .executes(c -> give(c.getSource().getPlayerOrException(), 16))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 640))
                                .executes(c -> give(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "amount"))))));
    }

    private static int give(ServerPlayer p, int n) {
        p.getInventory().add(new ItemStack(CobblemonItems.POKE_BALL, n));
        return 1;
    }
}
