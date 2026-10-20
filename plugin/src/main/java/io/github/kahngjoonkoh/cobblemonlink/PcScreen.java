package io.github.kahngjoonkoh.cobblemonlink;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Cobblemon's PC for vanilla clients: a 5-row chest whose title draws the screen (build_pc.py's art), its slots the
 * buttons. The box (6 x 5) on chest columns 0-5, the party (2 x 3) on columns 7-8, sort and type filter under the
 * party, ◀ box name ▶ and Release on the first inventory row; the selected Pokémon in the info strip below.
 * Click a Pokémon to select it, then a slot to move it there or a Pokémon to swap; the selection survives paging, so
 * a Pokémon moves between boxes. Release asks Yes / No and never takes your last Pokémon (nor empties the party).
 * Clicking the box's name opens a dialog: its name and its wallpaper (Cobblemon's; the biome ones unlocked by
 * visiting, as Cobblemon does). Bedrock, or Java without the UI pack: the same slots in a 6-row chest of item icons.
 * ponytail: still pictures (Cobblemon animates the selected Pokémon); no held items yet (an empty slot); every
 * catch is a Poké Ball, so that is the ball shown.
 */
final class PcScreen {
    static final int PX = -3, PY = -10; // the chest's top left is (-PX, -PY) on the 182 x 222 panel (build_pc.py)
    static final List<String> WALLPAPERS = List.of("basic_01", "basic_02", "basic_03", "basic_04", "basic_05",
            "basic_06", "basic_07", "basic_08", "basic_09", "basic_10", "basic_11", "biome_cave", "biome_forest",
            "biome_nether", "biome_ocean", "biome_the_end", "pokemon_alpha"); // build_pc.py WALLPAPERS
    static final List<String> WALLPAPER_NAMES = List.of("Red", "Orange", "Yellow", "Green", "Teal", "Blue", "Purple",
            "Pink", "Light Gray", "Gray", "Dark Gray", "Cave", "Forest", "Nether", "Ocean", "The End", "Alpha");
    static final int UNLOCKED = 11; // the basic colours are always there
    private static final char BASE = '\ue2a0', RING = '\ue2a1', RELEASE = '\ue2a2', HALF = '\ue2a3', SORT = '\ue2a4',
            FILTER_ALL = '\ue2a9', BALL = '\ue2aa', MALE = '\ue2ab', FEMALE = '\ue2ac', WALL = '\ue2d0',
            TYPE_FILTER = '\ue2b0', TYPE_INFO = '\ue320', TYPE_CELL = '\ue340'; // build_pc.py
    private static final String[] SORTS = {"Name", "Level", "Type", "Pokédex number", "Gender"}; // build_pc.py sort_* order
    // raw slots: sort and type filter under the party; on the first inventory row ◀, the name, ▶, Release (Yes, No)
    static final int SORT_SLOT = 43, FILTER_SLOT = 44, PREV = 45, NEXT = 50, YES = 52, NO = 53;
    static final List<Integer> NAME_SLOTS = List.of(46, 47, 48, 49);
    private static final int WHITE = 255;

    static int boxSlot(int s) { return s / 6 * 9 + s % 6; }

    static int partySlot(int i) { return i / 2 * 9 + 7 + i % 2; }

    static int slotX(int c) { return 11 + 18 * c; }

    static int slotY(int r) { return 28 + 18 * r; }

    /** What a player sees: the box, the selected Pokémon, the type filter, the last sort, a release being confirmed. */
    static final class View {
        int box, sort = -1;
        Mon selected;
        String type;
        boolean confirm;
    }

    /** Where a Pokémon is: box -1 is the party. */
    record Loc(int box, int slot) {}

    private final CobblemonLink pl;
    private final Map<UUID, View> views = new HashMap<>();

    PcScreen(CobblemonLink pl) { this.pl = pl; }

    /** Opens on the box last looked at, nothing selected. */
    void open(Player p) {
        View v = views.computeIfAbsent(p.getUniqueId(), k -> new View());
        v.selected = null;
        v.confirm = false;
        show(p, v);
    }

    void forget(Player p) { views.remove(p.getUniqueId()); }

    private void show(Player p, View v) {
        Store.Data d = pl.store.get(p.getUniqueId());
        v.box = Math.floorMod(v.box, d.boxes.size());
        if (v.selected != null && where(d, v.selected) == null) v.selected = null;
        if (Mod.form(p)) {
            bedrock(p, d, v);
            return;
        }
        boolean art = !Ui.bedrock(p) && Ui.pcWidths != null;
        Menu m = art ? new Menu(45, title(p, d, v)) : new Menu(54, Component.text(boxName(p, d, v.box) + "  " + (v.box + 1) + "/" + d.boxes.size()));
        Store.Box box = d.boxes.get(v.box);
        for (int s = 0; s < Store.BOX; s++) {
            Loc at = new Loc(v.box, s);
            Mon mon = box.slots[s];
            if (!art && mon != null && shown(v, mon)) m.set(boxSlot(s), icon(mon, v), null);
            m.on(boxSlot(s), e -> click(p, at));
        }
        for (int i = 0; i < Store.PARTY; i++) {
            Loc at = new Loc(-1, i);
            if (!art && i < d.party.size()) m.set(partySlot(i), icon(d.party.get(i), v), null);
            m.on(partySlot(i), e -> click(p, at));
        }
        m.on(SORT_SLOT, e -> sort(p));
        m.on(FILTER_SLOT, e -> filter(p, e.isRightClick()));
        m.on(PREV, e -> page(p, -1));
        m.on(NEXT, e -> page(p, 1));
        NAME_SLOTS.forEach(s -> m.on(s, e -> rename(p)));
        if (v.selected != null) {
            m.on(YES, e -> release(p, true));
            m.on(NO, e -> release(p, false));
        }
        if (!art) {
            int sort = Math.max(0, v.sort);
            m.set(SORT_SLOT, Menu.icon(Material.HOPPER, "Sort: " + SORTS[sort], List.of("Click: sort this box by the next")), null);
            m.set(FILTER_SLOT, Menu.icon(v.type == null ? Material.WHITE_WOOL : Menu.typeIcon(v.type),
                    "Show: " + (v.type == null ? "all types" : pl.dex.label("type", v.type)), List.of("Click: next type, right-click: back")), null);
            m.set(PREV, Menu.icon(Material.ARROW, Ui.tr(p, "cobblemonlink.ui.prev_box", "Previous box"), List.of()), null);
            m.set(NEXT, Menu.icon(Material.ARROW, Ui.tr(p, "cobblemonlink.ui.next_box", "Next box"), List.of()), null);
            m.set(47, Menu.icon(Material.NAME_TAG, boxName(p, d, v.box), List.of("Click: rename, wallpaper")), null);
            if (v.selected != null && v.confirm) {
                m.set(YES, Menu.icon(Material.LIME_WOOL, "Yes, release " + v.selected.name(pl.dex, CobblemonLink.loc(p)), List.of()), null);
                m.set(NO, Menu.icon(Material.RED_WOOL, "No", List.of()), null);
            } else if (v.selected != null) {
                m.set(YES, Menu.icon(Material.LAVA_BUCKET, "Release " + v.selected.name(pl.dex, CobblemonLink.loc(p)), List.of()), null);
            }
        }
        p.openInventory(m.getInventory());
        if (art) { // a click's resync lands after this, so again a tick later
            hideInventory(p, 45);
            pl.onMain(() -> { if (p.getOpenInventory().getTopInventory().getHolder() == m) hideInventory(p, 45); });
        }
    }

    /**
     * The info strip lies over the player's own inventory rows, whose items the client draws on top: tell the client
     * those slots are empty (the server keeps them; the next real update shows them again, so on close).
     * ponytail: Paper has no API for a fake container content packet; reflection on the Mojang names, skipped if they change.
     */
    static void hideInventory(Player p, int containerSlots) {
        try {
            Object h = p.getClass().getMethod("getHandle").invoke(p);
            Object menu = h.getClass().getField("containerMenu").get(h);
            int id = menu.getClass().getField("containerId").getInt(menu);
            int state = (int) menu.getClass().getMethod("getStateId").invoke(menu);
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) menu.getClass().getMethod("getItems").invoke(menu);
            Object empty = Class.forName("net.minecraft.world.item.ItemStack").getField("EMPTY").get(null);
            for (int i = containerSlots; i < items.size(); i++) items.set(i, empty);
            Object carried = menu.getClass().getMethod("getCarried").invoke(menu);
            Class<?> type = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket");
            Object pkt = null;
            for (var c : type.getConstructors())
                if (c.getParameterCount() == 4) pkt = c.newInstance(id, state, items, carried);
            Object conn = h.getClass().getField("connection").get(h);
            conn.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet")).invoke(conn, pkt);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // the items show through; nothing else breaks
        }
    }

    private ItemStack icon(Mon mon, View v) {
        return Menu.monIcon(pl.dex, mon, mon == v.selected ? "Selected: click a slot to move it" : "Click: select");
    }

    private boolean shown(View v, Mon m) { return v.type == null || pl.dex.of(m).types.contains(v.type); }

    private String boxName(Player p, Store.Data d, int box) {
        String n = d.boxes.get(box).name;
        return n == null || n.isBlank() ? Ui.lang.get(CobblemonLink.loc(p), "cobblemon.ui.pc.box.title", box + 1) : n;
    }

    private View view(Player p) { return views.computeIfAbsent(p.getUniqueId(), k -> new View()); }

    private boolean busy(Player p) {
        if (!pl.battles.containsKey(p.getUniqueId())) return false;
        p.sendMessage(Component.text(Ui.tr(p, "cobblemonlink.ui.pc_battle", "You can't change your PC during a battle."), NamedTextColor.RED));
        return true;
    }

    // ---- clicks ----

    static Mon get(Store.Data d, Loc at) {
        if (at.box() < 0) return at.slot() < d.party.size() ? d.party.get(at.slot()) : null;
        return d.boxes.get(at.box()).slots[at.slot()];
    }

    static Loc where(Store.Data d, Mon m) {
        int i = d.party.indexOf(m);
        if (i >= 0) return new Loc(-1, i);
        for (int b = 0; b < d.boxes.size(); b++)
            for (int s = 0; s < Store.BOX; s++) if (d.boxes.get(b).slots[s] == m) return new Loc(b, s);
        return null;
    }

    private void click(Player p, Loc at) {
        View v = view(p);
        Store.Data d = pl.store.get(p.getUniqueId());
        Mon there = get(d, at);
        if (v.confirm) v.confirm = false; // any other click cancels a release
        else if (there != null && !shown(v, there)) return; // hidden by the type filter
        else if (v.selected == null) v.selected = there;
        else if (there == v.selected) v.selected = null;
        else if (!busy(p)) {
            String why = move(d, v.selected, at);
            if (why != null) p.sendMessage(Component.text(why, NamedTextColor.RED));
            else {
                pl.store.save(p.getUniqueId());
                if (where(d, v.selected).box() >= 0) pl.companions.recall(p, v.selected.id);
                if (there != null && where(d, there).box() >= 0) pl.companions.recall(p, there.id);
                v.selected = null;
            }
        }
        show(p, v);
    }

    /** Moves mon to at, swapping with whatever is there; null if done, else why not. */
    static String move(Store.Data d, Mon mon, Loc at) {
        Loc from = where(d, mon);
        Mon other = get(d, at);
        if (from == null) return "That Pokémon isn't in your PC any more.";
        if (other == null) {
            if (at.box() < 0) { // into the party's next free cell
                if (from.box() >= 0 && d.party.size() >= Store.PARTY) return "Your party is full.";
                remove(d, from);
                d.party.add(mon);
                return null;
            }
            if (from.box() < 0 && d.party.size() <= 1) return "Your party needs at least one Pokémon.";
            remove(d, from);
            d.boxes.get(at.box()).slots[at.slot()] = mon;
            return null;
        }
        put(d, from, other);
        put(d, at, mon);
        return null;
    }

    private static void remove(Store.Data d, Loc at) {
        if (at.box() < 0) d.party.remove(at.slot());
        else d.boxes.get(at.box()).slots[at.slot()] = null;
    }

    private static void put(Store.Data d, Loc at, Mon m) {
        if (at.box() < 0) d.party.set(at.slot(), m);
        else d.boxes.get(at.box()).slots[at.slot()] = m;
    }

    private void page(Player p, int step) {
        View v = view(p);
        v.box += step;
        v.confirm = false;
        show(p, v);
    }

    /** Cobblemon's sort buttons: the box sorted by the next of name, level, type, Pokédex number, gender. */
    private void sort(Player p) {
        View v = view(p);
        v.confirm = false;
        if (!busy(p)) {
            v.sort = (v.sort + 1) % SORTS.length;
            Store.Data d = pl.store.get(p.getUniqueId());
            sortBox(pl.dex, d.boxes.get(v.box), v.sort);
            pl.store.save(p.getUniqueId());
        }
        show(p, v);
    }

    static void sortBox(Dex dex, Store.Box box, int by) {
        Comparator<Mon> c = switch (by) {
            case 1 -> Comparator.comparingInt(m -> m.level);
            case 2 -> Comparator.comparingInt(m -> Ui.TYPES.indexOf(dex.of(m).types.getFirst()));
            case 3 -> Comparator.comparingInt(m -> dex.of(m).dex);
            case 4 -> Comparator.comparingInt(m -> "MF".indexOf(m.gender) < 0 ? 2 : "MF".indexOf(m.gender));
            default -> Comparator.comparing(m -> m.name(dex));
        };
        List<Mon> mons = new ArrayList<>();
        for (Mon m : box.slots) if (m != null) mons.add(m);
        mons.sort(c.thenComparing(m -> m.name(dex)));
        box.slots = new Mon[Store.BOX];
        for (int i = 0; i < mons.size(); i++) box.slots[i] = mons.get(i);
    }

    private void filter(Player p, boolean back) {
        View v = view(p);
        if (Ui.bedrock(p) && Forms.available()) { // Cobblemon's Filter field: here a type dropdown
            List<String> names = new ArrayList<>(List.of("All types"));
            Ui.TYPES.forEach(t -> names.add(pl.dex.label("type", t)));
            Forms.choice(p, "Filter", "Show", names, v.type == null ? 0 : Ui.TYPES.indexOf(v.type) + 1, i -> pl.onMain(() -> {
                if (i >= 0) v.type = i == 0 ? null : Ui.TYPES.get(i - 1);
                v.confirm = false;
                show(p, v);
            }));
            return;
        }
        int i = v.type == null ? -1 : Ui.TYPES.indexOf(v.type), n = Ui.TYPES.size() + 1;
        i = Math.floorMod(i + 1 + (back ? -1 : 1), n) - 1;
        v.type = i < 0 ? null : Ui.TYPES.get(i);
        v.confirm = false;
        show(p, v);
    }

    private void release(Player p, boolean yes) {
        View v = view(p);
        Store.Data d = pl.store.get(p.getUniqueId());
        if (!v.confirm) v.confirm = true; // Release (both its slots), then Yes / No
        else {
            v.confirm = false;
            Loc at = yes && !busy(p) ? where(d, v.selected) : null;
            if (at != null && d.party.size() + d.pcCount() <= 1) p.sendMessage(Component.text("You can't release your last Pokémon.", NamedTextColor.RED));
            else if (at != null && at.box() < 0 && d.party.size() <= 1) p.sendMessage(Component.text("Your party needs at least one Pokémon.", NamedTextColor.RED));
            else if (at != null) {
                remove(d, at);
                pl.companions.recall(p, v.selected.id);
                pl.store.save(p.getUniqueId());
                p.sendMessage(Component.text(v.selected.name(pl.dex, CobblemonLink.loc(p)) + " was released.", NamedTextColor.YELLOW));
                v.selected = null;
            }
        }
        show(p, v);
    }

    /** The box's name and wallpaper, in a dialog (Bedrock: a form); locked wallpapers show as "???" and change nothing. */
    private void rename(Player p) {
        View v = view(p);
        v.confirm = false;
        Store.Data d = pl.store.get(p.getUniqueId());
        Store.Box box = d.boxes.get(v.box);
        int boxIndex = v.box;
        if (Ui.bedrock(p) && Forms.available()) {
            List<String> names = new ArrayList<>(List.of("Default"));
            for (int i = 0; i < WALLPAPERS.size(); i++) names.add(locked(d, i) ? "???" : WALLPAPER_NAMES.get(i));
            Forms.boxSettings(p, boxName(p, d, boxIndex), box.name == null ? "" : box.name, names, box.wallpaper + 1, (name, w) -> pl.onMain(() -> {
                if (name != null) {
                    box.name = name.isBlank() ? null : name.strip();
                    if (box.name != null && box.name.length() > 16) box.name = box.name.substring(0, 16);
                    if (w == 0 || w > 0 && !locked(d, w - 1)) box.wallpaper = w - 1;
                    pl.store.save(p.getUniqueId());
                }
                v.box = boxIndex;
                show(p, v);
            }));
            return;
        }
        List<SingleOptionDialogInput.OptionEntry> opts = new ArrayList<>();
        opts.add(SingleOptionDialogInput.OptionEntry.create("-1", Component.text("Default"), box.wallpaper < 0));
        for (int i = 0; i < WALLPAPERS.size(); i++)
            opts.add(SingleOptionDialogInput.OptionEntry.create(String.valueOf(i),
                    Component.text(locked(d, i) ? "???" : WALLPAPER_NAMES.get(i)), box.wallpaper == i));
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton done = ActionButton.builder(Component.text("Done")).action(DialogAction.customClick((resp, who) -> pl.onMain(() -> {
            String name = resp.getText("name");
            box.name = name == null || name.isBlank() ? null : name.strip();
            int w = Integer.parseInt(resp.getText("wallpaper"));
            if (w < 0 || !locked(d, w)) box.wallpaper = w;
            pl.store.save(p.getUniqueId());
            v.box = boxIndex;
            show(p, v);
        }), once)).build();
        ActionButton cancel = ActionButton.builder(Component.text("Cancel")).action(DialogAction.customClick((resp, who) -> pl.onMain(() -> show(p, v)), once)).build();
        p.closeInventory();
        p.showDialog(Dialog.create(f -> f.empty().base(DialogBase.builder(Component.text(boxName(p, d, boxIndex))).canCloseWithEscape(true)
                .inputs(List.of(DialogInput.text("name", Component.text("Name")).initial(box.name == null ? "" : box.name).maxLength(16).build(),
                        DialogInput.singleOption("wallpaper", Component.text("Wallpaper"), opts).build())).build())
                .type(DialogType.confirmation(done, cancel))));
    }

    static boolean locked(Store.Data d, int wallpaper) {
        return wallpaper >= UNLOCKED && !d.wallpapers.contains(WALLPAPERS.get(wallpaper));
    }

    // ---- Bedrock ----

    /**
     * Cobblemon's PC as a Floodgate form, title ending in Forms.MARK + 5 (build_bedrock_ui.py pc_form draws it at
     * Cobblemon's positions). Buttons, in pc_form's PC_HOLDERS order: ◀, ▶, the box name, Filter, sort, Release,
     * Yes, No, then picture-and-text holders for the wallpaper and the info panel, then the box's 30 slots and the
     * party's 6 (text "Lv5" + "~<type><n>" + "*" when selected, image the whole Pokémon; " " for an empty slot).
     * A tap re-sends the form, as a click reopens the chest.
     */
    private void bedrock(Player p, Store.Data d, View v) {
        Store.Box box = d.boxes.get(v.box);
        Mon sel = v.selected;
        Dex.Species ss = sel == null ? null : pl.dex.of(sel);
        List<Battle.Opt> o = new ArrayList<>();
        Runnable stay = () -> show(p, v);
        o.add(new Battle.Opt("<", "none", () -> page(p, -1)));
        o.add(new Battle.Opt(">", "none", () -> page(p, 1)));
        o.add(new Battle.Opt(boxName(p, d, v.box), "none", () -> rename(p)));
        o.add(new Battle.Opt(v.type == null ? Ui.tr(p, "cobblemon.ui.pc.filter", "Filter") : pl.dex.label("type", v.type, CobblemonLink.loc(p)), "none", () -> filter(p, false)));
        o.add(new Battle.Opt(" ", v.sort < 0 ? "pc/sort_options" : "pc/sort_" + List.of("name", "level", "type", "pokedex_number", "gender").get(v.sort), () -> sort(p)));
        o.add(new Battle.Opt(sel != null && !v.confirm ? Ui.tr(p, "cobblemon.ui.pc.release", "Release") : "", "none", () -> release(p, true)));
        o.add(new Battle.Opt(sel != null && v.confirm ? Ui.tr(p, "cobblemonlink.ui.yes", "Yes") : "", "none", () -> release(p, true)));
        o.add(new Battle.Opt(sel != null && v.confirm ? Ui.tr(p, "cobblemonlink.ui.no", "No") : "", "none", () -> release(p, false)));
        o.add(new Battle.Opt("", "pc/wall/" + WALLPAPERS.get(box.wallpaper < 0 ? 4 : box.wallpaper), stay));
        Integer body = sel == null ? null : Ui.bodies.get(sel.model(pl.dex));
        o.add(new Battle.Opt(sel == null ? "" : "Lv. " + sel.level, body == null ? "none" : "body/" + Ui.bedrockName(sel.model(pl.dex)), stay));
        o.add(new Battle.Opt(sel == null ? "" : Ui.nature(p, sel.nature), "none", stay));
        o.add(new Battle.Opt(sel == null ? "" : pl.dex.label("ability", sel.ability), "none", stay));
        for (int k = 0; k < 4; k++)
            o.add(new Battle.Opt(sel == null || k >= sel.moves.size() ? "" : pl.dex.label("move", sel.moves.get(k)), "none", stay));
        o.add(new Battle.Opt(sel == null ? "" : "M".equals(sel.gender) ? "\u2642" : "F".equals(sel.gender) ? "\u2640" : "", "none", stay));
        for (int k = 0; k < 2; k++)
            o.add(new Battle.Opt("", ss == null || k >= ss.types.size() ? "none" : "pc/type/" + ss.types.get(k), stay));
        for (int s = 0; s < Store.BOX; s++) {
            Loc at = new Loc(v.box, s);
            o.add(slotOpt(v, box.slots[s], () -> click(p, at)));
        }
        for (int i = 0; i < Store.PARTY; i++) {
            Loc at = new Loc(-1, i);
            o.add(slotOpt(v, i < d.party.size() ? d.party.get(i) : null, () -> click(p, at)));
        }
        String title = (sel == null ? "" : (v.confirm ? Ui.tr(p, "cobblemonlink.ui.release_q", "Release?") : sel.name(pl.dex, CobblemonLink.loc(p)))) + Forms.MARK + 5;
        Mod.send(p, title, "", o, i -> pl.onMain(() -> { if (i >= 0) o.get(i).action().run(); }));
    }

    private Battle.Opt slotOpt(View v, Mon m, Runnable click) {
        if (m == null || !shown(v, m)) return new Battle.Opt(" ", "none", click);
        StringBuilder t = new StringBuilder("Lv" + m.level);
        List<String> types = pl.dex.of(m).types;
        for (int j = 0; j < types.size() && j < 2; j++) t.append('~').append(types.get(j)).append(j + 1);
        if (m == v.selected) t.append('*');
        return new Battle.Opt(t.toString(), Ui.bodies.containsKey(m.model(pl.dex)) ? "body/" + Ui.bedrockName(m.model(pl.dex)) : "none", click);
    }

    // ---- the picture ----

    Component title(Player p, Store.Data d, View v) {
        Ui.Title t = new Ui.Title();
        glyph(t, 0, 0, BASE, 7);
        Store.Box box = d.boxes.get(v.box);
        if (box.wallpaper >= 0) glyph(t, 9, 26, (char) (WALL + box.wallpaper), 7);
        for (int s = 0; s < Store.BOX; s++) {
            Mon m = box.slots[s];
            if (m != null && shown(v, m)) cell(t, slotX(s % 6), slotY(s / 6), m, m == v.selected);
        }
        for (int i = 0; i < d.party.size(); i++) cell(t, slotX(7 + i % 2), slotY(i / 2), d.party.get(i), d.party.get(i) == v.selected);
        glyph(t, slotX(7) + 3, slotY(4) + 4, (char) (SORT + Math.max(0, v.sort)), 7);
        glyph(t, slotX(8) + 3, slotY(4) + 4, v.type == null ? FILTER_ALL : (char) (TYPE_FILTER + Ui.TYPES.indexOf(v.type)), 7);
        text(t, "pc_label", 64, 130, boxName(p, d, v.box), true);
        if (v.selected != null) {
            if (v.confirm) {
                glyph(t, slotX(7), 131, HALF, 7);
                glyph(t, slotX(8), 131, HALF, 7);
                text(t, "pc_label", slotX(7) + 8.5, 134, Ui.tr(p, "cobblemonlink.ui.yes", "Yes"), true);
                text(t, "pc_label", slotX(8) + 8.5, 134, Ui.tr(p, "cobblemonlink.ui.no", "No"), true);
            } else {
                glyph(t, slotX(7), 131, RELEASE, 7);
                text(t, "pc_label", slotX(7) + 17, 134, Ui.tr(p, "cobblemon.ui.pc.release", "Release"), true);
            }
            info(p, t, v.selected, v.confirm);
        }
        return t.build();
    }

    /** A box or party Pokémon: the whole body, "Lv5" top left, its type icons top right, the blue ring if selected. */
    private void cell(Ui.Title t, int x, int y, Mon m, boolean selected) {
        Integer b = Ui.bodies.get(m.model(pl.dex));
        if (b != null) put(t, x, y, Component.text(Character.toString(0xF9000 + b)).font(Key.key("cobblemonlink", "body")), 17, 7);
        put(t, x, y, Component.text(Character.toString(0xE000 + m.level)).font(Key.key("cobblemonlink", "pc_lv")), width("pc_lv", (char) (0xE000 + m.level)), 4);
        List<String> types = pl.dex.of(m).types;
        for (int j = 0; j < types.size() && j < 2; j++) glyph(t, x + 13 - 3 * j, y, (char) (TYPE_CELL + Ui.TYPES.indexOf(types.get(j))), 4);
        if (selected) glyph(t, x - 1, y - 1, RING, 7);
    }

    /** The info strip: the name card (Lv tab, ball, name, gender), the whole Pokémon, Type / Nature / Ability / Moves. */
    private void info(Player p, Ui.Title t, Mon m, boolean confirm) {
        Dex.Species s = pl.dex.of(m);
        text(t, "pc_bold", 7, 147, "Lv." + m.level, false);
        glyph(t, 5, 155, BALL, 6);
        text(t, "pc_bold", 12, 155, confirm ? Ui.tr(p, "cobblemonlink.ui.release_q", "Release?") : m.name(pl.dex, CobblemonLink.loc(p)), false);
        if (!m.gender.isEmpty()) glyph(t, 48, 157, "M".equals(m.gender) ? MALE : FEMALE, 4);
        Integer b = Ui.bodies.get(m.model(pl.dex));
        if (b != null) put(t, 6, 166, Component.text(Character.toString(0xF9000 + b)).font(Key.key("cobblemonlink", "body_big")), 45, 7);
        String loc = CobblemonLink.loc(p), typeNames = String.join(" / ", s.types.stream().map(ty -> pl.dex.label("type", ty, loc)).toList());
        String[][] rows = {{Ui.tr(p, "cobblemon.ui.info.type", "Type"), typeNames}, {Ui.tr(p, "cobblemon.ui.info.nature", "Nature"), Ui.nature(p, m.nature)},
                {Ui.tr(p, "cobblemon.ui.info.ability", "Ability"), pl.dex.label("ability", m.ability, loc)},
                {Ui.tr(p, "cobblemon.ui.moves", "Moves"), String.join(", ", m.moves.stream().map(mv -> pl.dex.label("move", mv, loc)).toList())}};
        for (int i = 0; i < rows.length; i++) {
            int y = 152 + 3 + 15 * i;
            boolean wide = (rows[i][0] + rows[i][1]).chars().anyMatch(c -> Ui.wide((char) c));
            String value = fit("pc_text", rows[i][1], 121 - (i == 0 ? 16 : 0) - (wide ? (int) Ui.width(rows[i][0]) + 6 : 0));
            if (wide) { // Minecraft's font is too tall for two lines a row: label and value side by side
                text(t, "pc_bold", 57, y + 3, rows[i][0], false);
                text(t, "pc_text", 57 + Ui.width(rows[i][0]) + 6, y + 3, value, false);
            } else {
                text(t, "pc_bold", 57, y, rows[i][0], false);
                text(t, "pc_text", 57, y + 7, value, false); // text() plains it
            }
            if (i == 0) {
                double x = 57 + (wide ? Ui.width(rows[i][0]) + 6 : 0) + width("pc_text", value) + 3;
                for (String ty : s.types) {
                    glyph(t, x, y + (wide ? 3 : 7), (char) (TYPE_INFO + Ui.TYPES.indexOf(ty)), 7);
                    x += 8;
                }
            }
        }
    }

    static void glyph(Ui.Title t, double x, int y, char c, int ascent) {
        put(t, x, y, Ui.glyphs(String.valueOf(c)), width("ui", c), ascent);
    }

    /** A piece whose top should be y px down the panel (a glyph's top is ascent above its baseline, 7 below the line). */
    static void put(Ui.Title t, double x, int y, Component c, double advance, int ascent) {
        put(t, x, y, c, advance, ascent, WHITE);
    }

    /** grey: the level it's drawn at (the shader multiplies the piece's colours by grey / 255). */
    static void put(Ui.Title t, double x, int y, Component c, double advance, int ascent, int grey) {
        t.add(x + PX - 8, y + PY - 6 - (7 - ascent), c, advance, grey);
    }

    /**
     * One of build_pc.py's text fonts (its shadow baked in), each letter followed by U+E300 (1 px back: the client's
     * bitmap advance adds a whole px, Unifont's half); centred on x if centre. Top y.
     */
    static void text(Ui.Title t, String font, double x, int y, String s, boolean centre) {
        text(t, font, x, y, s, centre, WHITE);
    }

    static void text(Ui.Title t, String font, double x, int y, String s, boolean centre, int grey) {
        if (s.chars().anyMatch(c -> Ui.wide((char) c))) { // Korean, Japanese, Chinese: Minecraft's own font draws them
            double w = Ui.width(s);
            put(t, centre ? x - w / 2 : x, y, Component.text(s), w, 7, grey);
            return;
        }
        s = plain(s);
        double w = width(font, s);
        int ascent = font.equals("pc_label") ? 7 : 6;
        put(t, centre ? x - w / 2 : x, y, Component.text(Ui.tight(s)).font(Key.key("cobblemonlink", font)), w, ascent, grey);
    }

    /** Width as text() lays it out: each advance less 1. */
    static double width(String font, String s) {
        double w = 0;
        if (s.chars().anyMatch(c -> Ui.wide((char) c))) return Ui.width(s); // text() draws it all in Minecraft's font
        for (char c : s.toCharArray()) w += width(font, c) - 1;
        return w;
    }

    static int width(String font, char c) {
        var f = Ui.pcWidths == null ? null : Ui.pcWidths.getAsJsonObject(font);
        var e = f == null ? null : f.get(String.valueOf(c));
        return e == null ? 4 : e.getAsInt();
    }

    /** s cut to max px with "..." if it doesn't fit. */
    static String fit(String font, String s, int max) {
        if (width(font, s) <= max) return s;
        while (!s.isEmpty() && width(font, s + "...") > max) s = s.substring(0, s.length() - 1);
        return s.strip() + "...";
    }

    /** The PC fonts are ASCII plus ♀ ♂ é: anything else loses its accent or goes. */
    static String plain(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == 'é' || c == '♀' || c == '♂' || c >= 0x20 && c < 0x7f) b.append(c);
            else b.append(Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("[^\\x20-\\x7e]", ""));
        }
        return b.toString();
    }
}
