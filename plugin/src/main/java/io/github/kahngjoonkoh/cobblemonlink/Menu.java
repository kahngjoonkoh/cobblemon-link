package io.github.kahngjoonkoh.cobblemonlink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Chest menu: the one UI that vanilla Java and Bedrock (through Geyser) both render. Clicks are always cancelled. */
final class Menu implements InventoryHolder {
    private static final Map<String, Material> TYPE_ICON = Map.ofEntries(
            Map.entry("normal", Material.WHITE_WOOL), Map.entry("fire", Material.ORANGE_WOOL),
            Map.entry("water", Material.BLUE_WOOL), Map.entry("grass", Material.LIME_WOOL),
            Map.entry("electric", Material.YELLOW_WOOL), Map.entry("ice", Material.LIGHT_BLUE_WOOL),
            Map.entry("fighting", Material.RED_WOOL), Map.entry("poison", Material.PURPLE_WOOL),
            Map.entry("ground", Material.BROWN_WOOL), Map.entry("flying", Material.FEATHER),
            Map.entry("psychic", Material.PINK_WOOL), Map.entry("bug", Material.GREEN_WOOL),
            Map.entry("rock", Material.COBBLESTONE), Map.entry("ghost", Material.GRAY_WOOL),
            Map.entry("dragon", Material.CYAN_WOOL), Map.entry("dark", Material.BLACK_WOOL),
            Map.entry("steel", Material.IRON_BLOCK), Map.entry("fairy", Material.MAGENTA_WOOL));

    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    Menu(int size, String title) { this(size, Component.text(title)); }

    Menu(int size, Component title) { inv = Bukkit.createInventory(this, size, title); }

    /** A click on a raw slot of the whole view, the player's inventory included (StarterScreen's buttons). */
    Menu on(int rawSlot, Consumer<InventoryClickEvent> action) {
        actions.put(rawSlot, action);
        return this;
    }

    Menu set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        return this;
    }

    void click(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    @Override
    public Inventory getInventory() { return inv; }

    static Material typeIcon(String type) { return TYPE_ICON.getOrDefault(type, Material.WHITE_WOOL); }

    /** The species' portrait (UI pack item model; Geyser maps it for Bedrock), or `fallback` without the pack. */
    static ItemStack speciesItem(String species, Material fallback) {
        if (!Ui.portraits.containsKey(species)) return new ItemStack(fallback);
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(meta -> meta.setItemModel(new org.bukkit.NamespacedKey("cobblemonlink", "portrait/" + species)));
        return it;
    }

    static ItemStack icon(Material m, String name, List<String> lore) { return icon(new ItemStack(m), name, lore); }

    static ItemStack icon(ItemStack it, String name, List<String> lore) {
        it.editMeta(meta -> {
            meta.customName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(l -> (Component) Component.text(l, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)).toList());
        });
        return it;
    }

    static ItemStack monIcon(Dex dex, Mon m, String... extra) {
        Dex.Species s = dex.of(m);
        Dex.Form form = dex.form(m.species, m.allAspects());
        List<String> lore = new ArrayList<>();
        lore.add("HP " + m.hp(dex) + "/" + m.maxHp(dex) + (m.fainted(dex) ? "  (fainted)" : ""));
        lore.add(String.join(" / ", s.types.stream().map(t -> dex.label("type", t)).toList())
                + "   " + m.nature + "   " + dex.label("ability", m.ability));
        lore.add("Moves: " + String.join(", ", m.moves.stream().map(mv -> dex.label("move", mv)).toList()));
        long next = m.level >= 100 ? 0 : Rules.expFor(s.expGroup, m.level + 1) - m.exp;
        lore.add("EXP to next level: " + next);
        if (form != null) lore.add(form.name + " Form");
        if (m.shiny) lore.add("★ Shiny");
        lore.addAll(List.of(extra));
        String g = m.gender.equals("M") ? " ♂" : m.gender.equals("F") ? " ♀" : "";
        return icon(m.fainted(dex) ? new ItemStack(Material.BARRIER) : speciesItem(m.model(dex), typeIcon(s.types.getFirst())),
                m.name(dex) + g + "  Lv." + m.level, lore);
    }
}
