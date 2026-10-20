package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * A chest menu for vanilla Java players (the basic level's screens, as the Paper version's menus without the UI
 * pack): items are buttons, nothing moves, a click runs the slot's action (raw slot, so the player's own rows too).
 */
public final class Chest {
    private final SimpleContainer box;
    private final int rows;
    private final Component title;
    private final Map<Integer, IntConsumer> actions = new HashMap<>(); // raw slot -> action(button: 0 left, 1 right)
    private boolean hideInventory;

    /** The player's own items aren't drawn over the screen's art (only on their screen; the server keeps them). */
    public Chest hideInventory() {
        hideInventory = true;
        return this;
    }

    private void hide(ServerPlayer p) {
        var menu = p.containerMenu;
        for (int i = rows * 9; i < menu.slots.size(); i++)
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), i, ItemStack.EMPTY));
    }

    public Chest(int rows, String title) { this(rows, Component.literal(title)); }

    public Chest(int rows, Component title) {
        this.rows = rows;
        this.title = title;
        box = new SimpleContainer(rows * 9);
    }

    public Chest set(int slot, ItemStack item, Runnable action) {
        box.setItem(slot, item);
        if (action != null) actions.put(slot, b -> action.run());
        return this;
    }

    public Chest on(int slot, IntConsumer action) {
        actions.put(slot, action);
        return this;
    }

    public void open(ServerPlayer p) {
        MenuType<?> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(type, id, inv, box, rows) {
            @Override
            public void clicked(int slot, int button, ClickType click, Player who) {
                sendAllDataToRemote(); // the client thinks it moved something
                if (hideInventory) hide(p);
                IntConsumer a = actions.get(slot);
                if (a != null && click == ClickType.PICKUP) p.server.execute(() -> a.accept(button));
            }

            @Override
            public ItemStack quickMoveStack(Player who, int slot) { return ItemStack.EMPTY; }

            @Override
            public boolean stillValid(Player who) { return true; }

            @Override
            public void removed(Player who) {
                super.removed(who);
                if (hideInventory) p.server.execute(() -> p.inventoryMenu.sendAllDataToRemote()); // the real items again
            }
        }, title));
        if (hideInventory) p.server.execute(() -> hide(p)); // after the menu's first sync
    }

    /** An item named name (not italic), with grey lore lines. */
    public static ItemStack icon(Item item, String name, List<String> lore) { return icon(new ItemStack(item), name, lore); }

    public static ItemStack icon(ItemStack it, String name, List<String> lore) {
        it.set(DataComponents.CUSTOM_NAME, Component.literal(name).setStyle(Style.EMPTY.withItalic(false)));
        if (!lore.isEmpty()) it.set(DataComponents.LORE, new ItemLore(lore.stream()
                .map(l -> (Component) Component.literal(l).setStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY))).toList()));
        return it;
    }
}
