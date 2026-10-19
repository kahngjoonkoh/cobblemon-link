package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cobblemon's party for a vanilla client, read from and acted on through Cobblemon's own API: a one-row chest, a
 * slot a Pokémon; clicking sends it out (or recalls it). ponytail: plain items and names; cobblemon-link's chest art
 * and portraits go on top of the same menu.
 */
final class PartyMenu {
    static void open(ServerPlayer p) {
        var party = Cobblemon.INSTANCE.getStorage().getParty(p);
        var box = new SimpleContainer(9);
        for (int i = 0; i < 6; i++) {
            Pokemon m = party.get(i);
            if (m == null) continue;
            var item = new ItemStack(m.getEntity() != null ? Items.ENDER_EYE : Items.PAPER);
            item.set(DataComponents.CUSTOM_NAME, Component.literal(m.getSpecies().getName() + "  Lv." + m.getLevel() + "  "
                    + m.getCurrentHealth() + "/" + m.getMaxHealth() + " HP" + (m.getEntity() != null ? "  (out)" : "")));
            box.setItem(i, item);
        }
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, box, 1) {
            @Override
            public void clicked(int slot, int button, ClickType type, Player who) {
                if (slot < 0 || slot >= 6 || party.get(slot) == null) return; // nothing moves
                Pokemon m = party.get(slot);
                if (m.getEntity() != null) m.recall();
                else m.sendOut(p.serverLevel(), p.position().add(p.getLookAngle().scale(2)), null, e -> kotlin.Unit.INSTANCE);
                p.closeContainer();
            }

            @Override
            public ItemStack quickMoveStack(Player who, int slot) { return ItemStack.EMPTY; }
        }, Component.literal("Party (Cobblemon)")));
    }
}
