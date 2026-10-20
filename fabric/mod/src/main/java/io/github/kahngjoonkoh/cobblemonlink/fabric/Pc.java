package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSortMode;
import com.cobblemon.mod.common.api.storage.StorePosition;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's PC for players without the mod, on Cobblemon's own PC store: opened the way Cobblemon opens it (a PC
 * block, or /pc), so its OpenPCPacket opens this. Select a Pokémon, then a slot to move it there or a Pokémon to swap,
 * across boxes and the party; sort the box (Cobblemon's sort modes), filter by type, release (Yes / No, never the
 * party's last Pokémon), the box's name and wallpaper (Cobblemon's, the unlockable ones once unlocked).
 * Bedrock: the Paper version's form (title ending MARK + 5; build_bedrock_ui.py pc_form): ◀, ▶, the box name,
 * Filter, sort, Release, Yes, No, the wallpaper and info panel holders, then the box's 30 slots and the party's 6.
 */
public final class Pc {
    static final List<String> WALLPAPERS = List.of("basic_01", "basic_02", "basic_03", "basic_04", "basic_05",
            "basic_06", "basic_07", "basic_08", "basic_09", "basic_10", "basic_11", "biome_cave", "biome_forest",
            "biome_nether", "biome_ocean", "biome_the_end", "pokemon_alpha");
    static final List<String> WALLPAPER_NAMES = List.of("Red", "Orange", "Yellow", "Green", "Teal", "Blue", "Purple",
            "Pink", "Light Gray", "Gray", "Dark Gray", "Cave", "Forest", "Nether", "Ocean", "The End", "Alpha");
    static final int BASIC = 11;
    private static final PokemonSortMode[] SORTS = {PokemonSortMode.NAME, PokemonSortMode.LEVEL, PokemonSortMode.TYPE,
            PokemonSortMode.POKEDEX_NUMBER, PokemonSortMode.GENDER};
    private static final String[] SORT_ART = {"name", "level", "type", "pokedex_number", "gender"};

    static final class View {
        int box, sort = -1;
        UUID selected;
        String type;
        boolean confirm;
    }

    /** Where a Pokémon is: box -1 is the party. */
    record Loc(int box, int slot) {
        StorePosition pos() { return box < 0 ? new PartyPosition(slot) : new PCPosition(box, slot); }
    }

    private static final Map<UUID, View> VIEWS = new HashMap<>();

    private Pc() {}

    static void register() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> VIEWS.remove(h.player.getUUID()));
    }

    /** Cobblemon opened its PC for a player without the mod. */
    public static void open(ServerPlayer p) {
        View v = VIEWS.computeIfAbsent(p.getUUID(), k -> new View());
        v.selected = null;
        v.confirm = false;
        show(p, v);
    }

    static PCStore pc(ServerPlayer p) { return Cobblemon.INSTANCE.getStorage().getPC(p); }

    static PlayerPartyStore party(ServerPlayer p) { return Cobblemon.INSTANCE.getStorage().getParty(p); }

    static Pokemon get(ServerPlayer p, Loc at) {
        return at.box() < 0 ? party(p).get(new PartyPosition(at.slot())) : pc(p).get(new PCPosition(at.box(), at.slot()));
    }

    static Loc where(ServerPlayer p, UUID id) {
        for (int i = 0; i < 6; i++) {
            Pokemon m = party(p).get(new PartyPosition(i));
            if (m != null && m.getUuid().equals(id)) return new Loc(-1, i);
        }
        List<PCBox> boxes = pc(p).getBoxes();
        for (int b = 0; b < boxes.size(); b++)
            for (int s = 0; s < 30; s++) {
                Pokemon m = boxes.get(b).get(s);
                if (m != null && m.getUuid().equals(id)) return new Loc(b, s);
            }
        return null;
    }

    private static void show(ServerPlayer p, View v) {
        v.box = Math.floorMod(v.box, pc(p).getBoxes().size());
        if (v.selected != null && where(p, v.selected) == null) v.selected = null;
        if (Clients.bedrock(p)) bedrock(p, v);
        else JavaScreens.pc(p, v);
    }

    /** Java's filter: the next (or previous) type. */
    static void cycleType(ServerPlayer p, boolean back) {
        View v = VIEWS.get(p.getUUID());
        int i = v.type == null ? -1 : BattleUi.TYPES.indexOf(v.type), n = BattleUi.TYPES.size() + 1;
        i = Math.floorMod(i + 1 + (back ? -1 : 1), n) - 1;
        v.type = i < 0 ? null : BattleUi.TYPES.get(i);
        v.confirm = false;
        show(p, v);
    }

    /** Java's box settings: the next unlocked wallpaper. ponytail: renaming a box needs a text field (Bedrock forms). */
    static void nextWallpaper(ServerPlayer p) {
        View v = VIEWS.get(p.getUUID());
        PCBox box = pc(p).getBoxes().get(v.box);
        int w = WALLPAPERS.indexOf(wallpaperOf(box));
        do w = (w + 1) % WALLPAPERS.size(); while (locked(p, w));
        box.setWallpaper(wallpaper(w));
        p.sendSystemMessage(Component.literal("Wallpaper: " + WALLPAPER_NAMES.get(w)));
        show(p, v);
    }

    private static boolean shown(View v, Pokemon m) {
        if (v.type == null) return true;
        for (ElementalType t : m.getTypes()) if (t.getName().toLowerCase(Locale.ROOT).equals(v.type)) return true;
        return false;
    }

    private static boolean busy(ServerPlayer p) {
        if (BattleRegistry.getBattleByParticipatingPlayer(p) == null) return false;
        p.sendSystemMessage(Component.literal("You can't change your PC during a battle.").withStyle(ChatFormatting.RED));
        return true;
    }

    private static int partyCount(ServerPlayer p) {
        int n = 0;
        for (Pokemon m : party(p)) if (m != null) n++;
        return n;
    }

    static void click(ServerPlayer p, Loc at) {
        View v = VIEWS.computeIfAbsent(p.getUUID(), k -> new View());
        Pokemon there = get(p, at);
        if (v.confirm) v.confirm = false; // any other click cancels a release
        else if (there != null && !shown(v, there)) return;
        else if (v.selected == null) v.selected = there == null ? null : there.getUuid();
        else if (there != null && there.getUuid().equals(v.selected)) v.selected = null;
        else if (!busy(p)) {
            String why = move(p, v.selected, at);
            if (why != null) p.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
            else v.selected = null;
        }
        show(p, v);
    }

    /** Moves the Pokémon to at, swapping with whatever is there; null if done, else why not. */
    static String move(ServerPlayer p, UUID id, Loc at) {
        Loc from = where(p, id);
        if (from == null) return "That Pokémon isn't in your PC any more.";
        Pokemon mon = get(p, from), other = get(p, at);
        if (other == null && from.box() < 0 && at.box() >= 0 && partyCount(p) <= 1) return "Your party needs at least one Pokémon.";
        if (mon.getEntity() != null && at.box() >= 0) mon.recall();
        if (other != null && other.getEntity() != null && from.box() >= 0) other.recall();
        var fromStore = from.box() < 0 ? party(p) : pc(p);
        var toStore = at.box() < 0 ? party(p) : pc(p);
        if (fromStore == toStore && fromStore instanceof PCStore pc) pc.swap((PCPosition) from.pos(), (PCPosition) at.pos());
        else if (fromStore == toStore) party(p).swap((PartyPosition) from.pos(), (PartyPosition) at.pos());
        else {
            remove(p, from);
            if (other != null) remove(p, at);
            set(p, at, mon);
            if (other != null) set(p, from, other);
        }
        return null;
    }

    private static void remove(ServerPlayer p, Loc at) {
        if (at.box() < 0) party(p).remove((PartyPosition) at.pos());
        else pc(p).remove((PCPosition) at.pos());
    }

    private static void set(ServerPlayer p, Loc at, Pokemon m) {
        if (at.box() < 0) party(p).set((PartyPosition) at.pos(), m);
        else pc(p).set((PCPosition) at.pos(), m);
    }

    static void page(ServerPlayer p, int step) {
        View v = VIEWS.get(p.getUUID());
        v.box += step;
        v.confirm = false;
        show(p, v);
    }

    static void sort(ServerPlayer p) {
        View v = VIEWS.get(p.getUUID());
        v.confirm = false;
        if (!busy(p)) {
            v.sort = (v.sort + 1) % SORTS.length;
            pc(p).getBoxes().get(v.box).sort(SORTS[v.sort], false);
        }
        show(p, v);
    }

    static void filter(ServerPlayer p) { // Cobblemon's Filter field: here a type dropdown
        View v = VIEWS.get(p.getUUID());
        List<String> names = new ArrayList<>(List.of("All types"));
        BattleUi.TYPES.forEach(t -> names.add(t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1)));
        Forms.choice(p, "Filter", "Show", names, v.type == null ? 0 : BattleUi.TYPES.indexOf(v.type) + 1, i -> {
            if (i >= 0) v.type = i == 0 ? null : BattleUi.TYPES.get(i - 1);
            v.confirm = false;
            show(p, v);
        });
    }

    static void release(ServerPlayer p, boolean yes) {
        View v = VIEWS.get(p.getUUID());
        if (!v.confirm) v.confirm = true; // Release, then Yes / No
        else {
            v.confirm = false;
            Loc at = yes && !busy(p) ? where(p, v.selected) : null;
            int pcCount = 0;
            for (PCBox b : pc(p).getBoxes()) pcCount += 30 - b.getUnoccupiedSlots();
            if (at != null && partyCount(p) + pcCount <= 1) p.sendSystemMessage(Component.literal("You can't release your last Pokémon.").withStyle(ChatFormatting.RED));
            else if (at != null && at.box() < 0 && partyCount(p) <= 1) p.sendSystemMessage(Component.literal("Your party needs at least one Pokémon.").withStyle(ChatFormatting.RED));
            else if (at != null) {
                Pokemon m = get(p, at);
                if (m.getEntity() != null) m.recall();
                remove(p, at);
                p.sendSystemMessage(Component.literal(BattleUi.name(m) + " was released.").withStyle(ChatFormatting.YELLOW));
                v.selected = null;
            }
        }
        show(p, v);
    }

    static String wallpaperOf(PCBox box) {
        ResourceLocation w = box.getWallpaper();
        if (w == null) return "basic_05";
        String path = w.getPath();
        String file = path.substring(path.lastIndexOf('/') + 1).replace("wallpaper_", "").replace(".png", "");
        return WALLPAPERS.contains(file) ? file : "basic_05";
    }

    static ResourceLocation wallpaper(int i) {
        String n = WALLPAPERS.get(i), cat = n.startsWith("basic") ? "basic" : n.startsWith("biome") ? "biome" : "misc";
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pc/wallpaper/" + cat + "/wallpaper_" + n + ".png");
    }

    static boolean locked(ServerPlayer p, int i) {
        return i >= BASIC && !pc(p).getUnlockedWallpapers().contains(ResourceLocation.fromNamespaceAndPath("cobblemon", WALLPAPERS.get(i)));
    }

    static String boxName(PCBox box, int i) { return box.getName() == null || box.getName().isBlank() ? "Box " + (i + 1) : box.getName(); }

    /** The box's name and wallpaper (Cobblemon's rename and wallpaper requests); locked wallpapers show as "???". */
    static void rename(ServerPlayer p) {
        View v = VIEWS.get(p.getUUID());
        v.confirm = false;
        PCBox box = pc(p).getBoxes().get(v.box);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < WALLPAPERS.size(); i++) names.add(locked(p, i) ? "???" : WALLPAPER_NAMES.get(i));
        Forms.boxSettings(p, "Box", boxName(box, v.box), names, WALLPAPERS.indexOf(wallpaperOf(box)), (name, w) -> {
            if (name != null) {
                String n = name.strip();
                box.setName(n.isEmpty() ? null : n.substring(0, Math.min(16, n.length())));
                if (w >= 0 && !locked(p, w)) box.setWallpaper(wallpaper(w));
            }
            show(p, v);
        });
    }

    private static void bedrock(ServerPlayer p, View v) {
        PCBox box = pc(p).getBoxes().get(v.box);
        Pokemon sel = null;
        if (v.selected != null) {
            Loc at = where(p, v.selected);
            sel = at == null ? null : get(p, at);
        }
        Runnable stay = () -> show(p, v);
        List<Forms.Opt> o = new ArrayList<>();
        o.add(new Forms.Opt("<", "none", () -> page(p, -1)));
        o.add(new Forms.Opt(">", "none", () -> page(p, 1)));
        o.add(new Forms.Opt(boxName(box, v.box), "none", () -> rename(p)));
        o.add(new Forms.Opt(v.type == null ? "Filter" : v.type.substring(0, 1).toUpperCase(Locale.ROOT) + v.type.substring(1), "none", () -> filter(p)));
        o.add(new Forms.Opt(" ", v.sort < 0 ? "pc/sort_options" : "pc/sort_" + SORT_ART[v.sort], () -> sort(p)));
        o.add(new Forms.Opt(sel != null && !v.confirm ? "Release" : "", "none", () -> release(p, true)));
        o.add(new Forms.Opt(sel != null && v.confirm ? "Yes" : "", "none", () -> release(p, true)));
        o.add(new Forms.Opt(sel != null && v.confirm ? "No" : "", "none", () -> release(p, false)));
        o.add(new Forms.Opt("", "pc/wall/" + wallpaperOf(box), stay));
        String look = sel == null ? null : Summary.look(sel);
        o.add(new Forms.Opt(sel == null ? "" : "Lv. " + sel.getLevel(), look != null && Models.has(look) ? "body/" + look : "none", stay));
        o.add(new Forms.Opt(sel == null ? "" : Lang.get(sel.getNature().getDisplayName()), "none", stay));
        o.add(new Forms.Opt(sel == null ? "" : Lang.get(sel.getAbility().getDisplayName()), "none", stay));
        for (int k = 0; k < 4; k++) {
            var moves = sel == null ? List.<com.cobblemon.mod.common.api.moves.Move>of() : sel.getMoveSet().getMoves();
            o.add(new Forms.Opt(k >= moves.size() ? "" : Lang.resolve(moves.get(k).getDisplayName()).getString(), "none", stay));
        }
        o.add(new Forms.Opt(sel == null ? "" : sel.getGender() == Gender.MALE ? "♂" : sel.getGender() == Gender.FEMALE ? "♀" : "", "none", stay));
        List<ElementalType> types = new ArrayList<>();
        if (sel != null) sel.getTypes().forEach(types::add);
        for (int k = 0; k < 2; k++)
            o.add(new Forms.Opt("", k < types.size() ? "pc/type/" + types.get(k).getName().toLowerCase(Locale.ROOT) : "none", stay));
        for (int s = 0; s < 30; s++) {
            Loc at = new Loc(v.box, s);
            o.add(slot(v, box.get(s), () -> click(p, at)));
        }
        for (int i = 0; i < 6; i++) {
            Loc at = new Loc(-1, i);
            o.add(slot(v, party(p).get(new PartyPosition(i)), () -> click(p, at)));
        }
        String title = (sel == null ? "" : v.confirm ? "Release?" : BattleUi.name(sel)) + Forms.MARK + 5;
        Forms.send(p, title, "", o, i -> { if (i >= 0 && i < o.size()) o.get(i).action().run(); });
    }

    private static Forms.Opt slot(View v, Pokemon m, Runnable click) {
        if (m == null || !shown(v, m)) return new Forms.Opt(" ", "none", click);
        StringBuilder t = new StringBuilder("Lv" + m.getLevel());
        int j = 0;
        for (ElementalType type : m.getTypes()) if (j < 2) t.append('~').append(type.getName().toLowerCase(Locale.ROOT)).append(++j);
        if (m.getUuid().equals(v.selected)) t.append('*');
        String look = Summary.look(m);
        return new Forms.Opt(t.toString(), Models.has(look) ? "body/" + look : "none", click);
    }
}
