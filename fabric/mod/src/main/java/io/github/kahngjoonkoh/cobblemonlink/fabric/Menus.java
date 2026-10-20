package io.github.kahngjoonkoh.cobblemonlink.fabric;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cobblemon's own menus (cooking pot, TM machine) for players without the mod: the client is told it's a chest, and
 * the slots are renumbered both ways, so the real menu runs on the server untouched. Spare chest slots are filler,
 * or buttons for what Cobblemon's screen does with its own widgets (the pot's lid). Works for Bedrock too (Geyser
 * shows the chest).
 */
public final class Menus {
    /** A chest standing in for menu containerId: chest slot -> menu slot (-1 for filler / buttons). */
    record Remap(int containerId, int rows, int[] toServer, Map<Integer, Button> buttons) {}

    record Button(ItemStack icon, Runnable action) {}

    private static final Map<UUID, Remap> OPEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();

    /** Minecraft's own menu types (Cobblemon registers its pot's as "minecraft:cooking_pot_menu", so not by name). */
    private static final java.util.Set<MenuType<?>> VANILLA = new java.util.HashSet<>();

    static {
        for (var f : MenuType.class.getFields())
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == MenuType.class)
                try { VANILLA.add((MenuType<?>) f.get(null)); } catch (IllegalAccessException ignored) {}
    }

    static boolean modded(MenuType<?> t) { return !VANILLA.contains(t); }

    private Menus() {}

    /**
     * Chest slot -> menu slot: the menu's own slots (layout, or in order) in the top rows, the player's inventory in
     * the bottom four as a chest has it (main 9..35, then the hotbar). inv[i] = the menu slot holding inventory slot i.
     */
    static int[] layout(int rows, int[] own, int[] inv) {
        int[] map = new int[rows * 9 + 36];
        Arrays.fill(map, -1);
        System.arraycopy(own, 0, map, 0, own.length);
        for (int i = 0; i < 36; i++) map[rows * 9 + i] = inv[i < 27 ? i + 9 : i - 27];
        return map;
    }

    /** OpenScreen for a modded menu: hidden for now, the chest goes out once the menu is the player's (next tick). */
    static Packet<?> open(ClientboundOpenScreenPacket o, ServerPlayer p) {
        PENDING.put(p.getUUID(), o.getContainerId());
        Component title = o.getTitle();
        Tasks.next(() -> {
            AbstractContainerMenu m = p.containerMenu;
            if (m.containerId != o.getContainerId()) return;
            Remap r = remap(m, p);
            OPEN.put(p.getUUID(), r);
            PENDING.remove(p.getUUID());
            MenuType<?> type = switch (r.rows) { case 1 -> MenuType.GENERIC_9x1; case 2 -> MenuType.GENERIC_9x2; case 3 -> MenuType.GENERIC_9x3;
                case 4 -> MenuType.GENERIC_9x4; case 5 -> MenuType.GENERIC_9x5; default -> MenuType.GENERIC_9x6; };
            p.connection.send(new ClientboundOpenScreenPacket(m.containerId, type, Lang.resolve(title)));
            m.sendAllDataToRemote();
        });
        return new ClientboundBundlePacket(List.of());
    }

    private static Remap remap(AbstractContainerMenu m, ServerPlayer p) {
        int[] inv = new int[36];
        List<Integer> own = new ArrayList<>();
        for (int s = 0; s < m.slots.size(); s++) {
            var slot = m.slots.get(s);
            if (slot.container instanceof Inventory && slot.getContainerSlot() < 36) inv[slot.getContainerSlot()] = s;
            else if (!(slot.container instanceof Inventory)) own.add(s);
        }
        Map<Integer, Button> buttons = new HashMap<>();
        int[] top;
        int rows;
        if (m instanceof com.cobblemon.mod.common.block.campfirepot.CookingPotMenu pot) {
            // as Cobblemon draws it: the 3 x 3 grid, the seasonings beside it, the result; the lid as a button
            rows = 3;
            top = new int[27];
            Arrays.fill(top, -1);
            for (int i = 0; i < 9; i++) top[i / 3 * 9 + 1 + i % 3] = own.get(1 + i);
            for (int i = 0; i < 3 && 10 + i < own.size(); i++) top[i * 9 + 5] = own.get(10 + i);
            top[9 + 7] = own.get(0);
            buttons.put(2 * 9 + 7, new Button(Chest.icon(Items.CAMPFIRE, "Lid: open / close (cooks when closed)", List.of()), () -> {
                boolean open = pot.getContainerData().get(com.cobblemon.mod.common.block.entity.CampfireBlockEntity.IS_LID_OPEN_INDEX) == 1;
                com.cobblemon.mod.common.client.net.cooking.ToggleCookingPotLidHandler.INSTANCE.handle(
                        new com.cobblemon.mod.common.net.messages.client.cooking.ToggleCookingPotLidPacket(!open), p.server, p);
            }));
        } else {
            rows = Math.max(1, Math.min(6, (own.size() + 8) / 9));
            top = own.stream().limit(rows * 9L).mapToInt(Integer::intValue).toArray();
        }
        return new Remap(m.containerId, rows, layout(rows, top, inv), buttons);
    }

    private static final ItemStack FILLER = Chest.icon(Items.GRAY_STAINED_GLASS_PANE, " ", List.of());

    /** The server's slot packets, renumbered for the chest; null when not ours. */
    static Packet<?> out(Packet<?> packet, ServerPlayer p) {
        Integer pending = PENDING.get(p.getUUID());
        Remap r = OPEN.get(p.getUUID());
        int id = packet instanceof ClientboundContainerSetContentPacket c ? c.getContainerId()
                : packet instanceof ClientboundContainerSetSlotPacket s ? s.getContainerId()
                : packet instanceof ClientboundContainerSetDataPacket d ? d.getContainerId() : -2;
        if (id == -2) return null;
        if (pending != null && pending == id) return new ClientboundBundlePacket(List.of()); // before the chest is open
        if (r == null || r.containerId != id) return null;
        if (packet instanceof ClientboundContainerSetDataPacket) return new ClientboundBundlePacket(List.of());
        if (packet instanceof ClientboundContainerSetContentPacket c) {
            NonNullList<ItemStack> items = NonNullList.withSize(r.toServer.length, ItemStack.EMPTY);
            for (int i = 0; i < r.toServer.length; i++) {
                int s = r.toServer[i];
                Button b = r.buttons.get(i);
                items.set(i, b != null ? b.icon : s < 0 ? (i < r.rows * 9 ? FILLER : ItemStack.EMPTY) : s < c.getItems().size() ? c.getItems().get(s) : ItemStack.EMPTY);
            }
            return new ClientboundContainerSetContentPacket(id, c.getStateId(), items, c.getCarriedItem());
        }
        var s = (ClientboundContainerSetSlotPacket) packet;
        for (int i = 0; i < r.toServer.length; i++)
            if (r.toServer[i] == s.getSlot()) return new ClientboundContainerSetSlotPacket(id, s.getStateId(), i, s.getItem());
        return new ClientboundBundlePacket(List.of());
    }

    /** A click on the chest, as the menu's slot (filler: slot -1, nothing happens; a button runs). */
    public static ServerboundContainerClickPacket click(ServerboundContainerClickPacket c, ServerPlayer p) {
        Remap r = OPEN.get(p.getUUID());
        if (r == null || r.containerId != c.getContainerId() || !p.server.isSameThread()) return c;
        int slot = c.getSlotNum();
        if (slot >= 0) {
            Button b = r.buttons.get(slot);
            if (b != null) b.action.run();
            slot = slot < r.toServer.length ? r.toServer[slot] : -1;
            if (slot < 0) p.server.execute(() -> p.containerMenu.sendAllDataToRemote()); // undo what the client guessed
        }
        var changed = new Int2ObjectOpenHashMap<ItemStack>();
        c.getChangedSlots().forEach((k, v) -> { if (k >= 0 && k < r.toServer.length && r.toServer[k] >= 0) changed.put(r.toServer[k], v); });
        return new ServerboundContainerClickPacket(c.getContainerId(), c.getStateId(), slot, c.getButtonNum(), c.getClickType(), c.getCarriedItem(), changed);
    }

    static void closed(ServerPlayer p) { OPEN.remove(p.getUUID()); PENDING.remove(p.getUUID()); }
}
