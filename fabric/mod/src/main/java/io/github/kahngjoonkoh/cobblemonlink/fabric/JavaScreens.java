package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Vanilla Java's screens at the basic level: chest menus with plain items, as the Paper version's menus without the
 * UI pack. The same Cobblemon actions as the Bedrock forms (Summary, Pc, Dex, Starters, BattleUi).
 */
public final class JavaScreens {
    private static final Map<String, Item> TYPE_ICON = Map.ofEntries(
            Map.entry("normal", Items.WHITE_WOOL), Map.entry("fire", Items.ORANGE_WOOL), Map.entry("water", Items.BLUE_WOOL),
            Map.entry("grass", Items.LIME_WOOL), Map.entry("electric", Items.YELLOW_WOOL), Map.entry("ice", Items.LIGHT_BLUE_WOOL),
            Map.entry("fighting", Items.RED_WOOL), Map.entry("poison", Items.PURPLE_WOOL), Map.entry("ground", Items.BROWN_WOOL),
            Map.entry("flying", Items.FEATHER), Map.entry("psychic", Items.PINK_WOOL), Map.entry("bug", Items.GREEN_WOOL),
            Map.entry("rock", Items.COBBLESTONE), Map.entry("ghost", Items.GRAY_WOOL), Map.entry("dragon", Items.CYAN_WOOL),
            Map.entry("dark", Items.BLACK_WOOL), Map.entry("steel", Items.IRON_BLOCK), Map.entry("fairy", Items.MAGENTA_WOOL));

    private JavaScreens() {}

    static Item typeIcon(String type) { return TYPE_ICON.getOrDefault(type, Items.WHITE_WOOL); }

    static String type(ElementalType t) { return t.getName().toLowerCase(Locale.ROOT); }

    /** A Pokémon as an item: its first type's wool (a barrier when fainted), name, level and a summary in the lore. */
    static ItemStack mon(Pokemon m, String... extra) {
        List<String> lore = new ArrayList<>();
        lore.add("HP " + m.getCurrentHealth() + "/" + m.getMaxHealth() + (m.isFainted() ? "  (fainted)" : ""));
        List<String> types = new ArrayList<>();
        m.getTypes().forEach(t -> types.add(Lang.resolve(m.getOwnerPlayer(), t.getDisplayName()).getString()));
        lore.add(String.join(" / ", types) + "   " + Lang.get(m.getOwnerPlayer(), m.getNature().getDisplayName()) + "   " + Lang.get(m.getOwnerPlayer(), m.getAbility().getDisplayName()));
        lore.add("Moves: " + String.join(", ", m.getMoveSet().getMoves().stream().map(mv -> Lang.resolve(m.getOwnerPlayer(), mv.getDisplayName()).getString()).toList()));
        var curve = m.getSpecies().getExperienceGroup();
        lore.add("EXP to next level: " + (m.getLevel() >= 100 ? 0 : curve.getExperience(m.getLevel() + 1) - m.getExperience()));
        if (m.getShiny()) lore.add("★ Shiny");
        lore.addAll(List.of(extra));
        String g = m.getGender() == Gender.MALE ? " ♂" : m.getGender() == Gender.FEMALE ? " ♀" : "";
        Item item = m.isFainted() ? Items.BARRIER : typeIcon(type(m.getPrimaryType()));
        return Chest.icon(item, BattleUi.name(m) + g + "  Lv." + m.getLevel(), lore);
    }

    // ---- battle ----

    /** BattleUi's menus as a 3-row chest: the choices on the middle row, the battle log on a book. */
    static void battle(ServerPlayer p, int kind, List<Forms.Opt> opts, List<String> log, Runnable closed) {
        Chest c = new Chest(3, kind == 1 ? "What will you do?" : kind == 2 ? "Fight" : "Switch");
        c.set(4, Chest.icon(Items.BOOK, "Battle", log.isEmpty() ? List.of("What will you do?") : log), null);
        int[] slots = kind == 1 ? new int[]{10, 12, 14, 16} : new int[]{10, 11, 12, 13, 14, 15, 17};
        int n = 0;
        for (Forms.Opt o : opts) {
            if (o.image().equals("none") || o.text().isBlank() && !o.image().equals("back")) continue;
            int slot = o.image().equals("back") ? 17 : n < slots.length ? slots[n++] : -1;
            if (slot < 0) continue;
            String[] lines = o.text().split("\n");
            List<String> lore = lines.length > 1 ? List.of(lines[1].strip()) : List.of();
            c.set(slot, Chest.icon(battleItem(o.image()), o.image().equals("back") ? "Back" : lines[0], lore), () -> {
                p.closeContainer();
                o.action().run();
            });
        }
        c.open(p);
    }

    private static ItemStack battleItem(String image) {
        return switch (image) {
            case "menu_fight" -> new ItemStack(Items.IRON_SWORD);
            case "menu_switch" -> new ItemStack(Items.ENDER_PEARL);
            case "menu_bag" -> new ItemStack(CobblemonItems.POKE_BALL);
            case "menu_run" -> new ItemStack(Items.FEATHER);
            case "back" -> new ItemStack(Items.ARROW);
            case "party" -> new ItemStack(Items.NAME_TAG);
            default -> new ItemStack(image.startsWith("move_") ? typeIcon(image.substring(5)) : Items.PAPER);
        };
    }

    // ---- starter ----

    /** Cobblemon's starters, a row a region (its name, then its starters); a click chooses. */
    static void starter(ServerPlayer p, List<Starters.Region> regions, int page) {
        int per = 6, pages = (regions.size() + per - 1) / per, pg = Math.floorMod(page, Math.max(1, pages));
        Chest c = new Chest(6, "Choose your first Pokémon");
        for (int r = 0; r < per && pg * per + r < regions.size(); r++) {
            Starters.Region region = regions.get(pg * per + r);
            c.set(r * 9, Chest.icon(Items.MAP, region.name(), List.of()), null);
            for (int j = 0; j < region.species().size() && j < 7; j++) {
                var sp = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(region.species().get(j));
                if (sp == null) continue;
                int idx = j;
                List<String> types = new ArrayList<>();
                sp.getTypes().forEach(t -> types.add(Lang.resolve(p, t.getDisplayName()).getString()));
                List<String> lore = new ArrayList<>(List.of(String.join(" / ", types)));
                lore.addAll(wrap(Starters.desc(region.species().get(j)), 40));
                c.set(r * 9 + 2 + j, Chest.icon(typeIcon(type(sp.getPrimaryType())), Lang.resolve(p, sp.getTranslatedName()).getString(), lore), () -> {
                    p.closeContainer();
                    Cobblemon.INSTANCE.getStarterHandler().chooseStarter(p, region.category(), idx);
                });
            }
        }
        if (pages > 1) {
            c.set(8, Chest.icon(Items.ARROW, "Previous regions", List.of()), () -> starter(p, regions, pg - 1));
            c.set(53, Chest.icon(Items.ARROW, "More regions", List.of()), () -> starter(p, regions, pg + 1));
        }
        c.open(p);
    }

    // ---- party and Summary ----

    /** The party (the Paper version's party menu): a click opens the Pokémon's options. */
    static void party(ServerPlayer p) {
        List<Pokemon> party = PartyHud.party(p);
        Chest c = new Chest(1, "Party");
        for (int i = 0; i < party.size(); i++) {
            int idx = i;
            Pokemon m = party.get(i);
            c.set(i, mon(m, m.getEntity() != null ? "Out with you" : "Click for options"), () -> mon(p, idx));
        }
        c.open(p);
    }

    /** One Pokémon's options: send out or recall, lead, to PC, evolve; big buttons. */
    static void mon(ServerPlayer p, int idx) {
        List<Pokemon> party = PartyHud.party(p);
        if (idx >= party.size()) return;
        Pokemon m = party.get(idx);
        boolean battling = BattleRegistry.getBattleByParticipatingPlayer(p) != null;
        Chest c = new Chest(1, BattleUi.name(m));
        c.set(0, mon(m), null);
        if (!battling) {
            boolean out = m.getEntity() != null;
            if (out || !m.isFainted()) c.set(2, Chest.icon(out ? Items.ENDER_PEARL : CobblemonItems.POKE_BALL, out ? "Recall" : "Send out", List.of()), () -> {
                if (m.getEntity() != null) m.recall();
                else m.sendOutWithAnimation(p, p.serverLevel(), p.position().add(p.getLookAngle().multiply(2, 0, 2)), null, true, null, e -> kotlin.Unit.INSTANCE);
                party(p);
            });
            if (idx > 0) c.set(3, Chest.icon(Items.NETHER_STAR, "Make lead", List.of()), () -> {
                var store = Cobblemon.INSTANCE.getStorage().getParty(p);
                var list = store.toGappyList();
                store.swap(new PartyPosition(list.indexOf(party.getFirst())), new PartyPosition(list.indexOf(m)));
                party(p);
            });
            if (party.size() > 1) c.set(4, Chest.icon(Items.ENDER_CHEST, "Move to PC", List.of()), () -> {
                if (m.getEntity() != null) m.recall();
                if (Cobblemon.INSTANCE.getStorage().getParty(p).remove(m) && !Cobblemon.INSTANCE.getStorage().getPC(p).add(m))
                    Cobblemon.INSTANCE.getStorage().getParty(p).add(m);
                party(p);
            });
            var evo = m.getEvolutionProxy().server();
            if (!evo.isEmpty()) c.set(5, Chest.icon(Items.EXPERIENCE_BOTTLE, "Evolve", List.of()), () -> {
                if (!evo.isEmpty()) evo.start(evo.iterator().next());
                p.closeContainer();
            });
            List<String> stats = new ArrayList<>();
            for (String[] row : Summary.rows(p, m, Summary.STATS)) stats.add(row[0] + ": " + row[1]);
            c.set(6, Chest.icon(Items.WRITABLE_BOOK, "Stats", stats), null);
        }
        c.set(8, Chest.icon(Items.ARROW, "Back", List.of()), () -> party(p));
        c.open(p);
    }

    // ---- PC (the Paper version's chest without the UI pack) ----

    static int boxSlot(int s) { return s / 6 * 9 + s % 6; }

    static int partySlot(int i) { return i / 2 * 9 + 7 + i % 2; }

    static void pc(ServerPlayer p, Pc.View v) {
        PCBox box = Pc.pc(p).getBoxes().get(v.box);
        Chest c = new Chest(6, Pc.boxName(box, v.box) + "  " + (v.box + 1) + "/" + Pc.pc(p).getBoxes().size());
        Pokemon sel = null;
        if (v.selected != null) {
            Pc.Loc at = Pc.where(p, v.selected);
            sel = at == null ? null : Pc.get(p, at);
        }
        for (int s = 0; s < 30; s++) {
            Pc.Loc at = new Pc.Loc(v.box, s);
            Pokemon m = box.get(s);
            c.set(boxSlot(s), m == null ? ItemStack.EMPTY : mon(m, m.getUuid().equals(v.selected) ? "Selected: click a slot to move it" : "Click: select"),
                    () -> Pc.click(p, at));
        }
        for (int i = 0; i < 6; i++) {
            Pc.Loc at = new Pc.Loc(-1, i);
            Pokemon m = Pc.party(p).get(new PartyPosition(i));
            c.set(partySlot(i), m == null ? ItemStack.EMPTY : mon(m, m.getUuid().equals(v.selected) ? "Selected: click a slot to move it" : "Click: select"),
                    () -> Pc.click(p, at));
        }
        String[] sorts = {"Name", "Level", "Type", "Pokédex number", "Gender"};
        c.set(43, Chest.icon(Items.HOPPER, "Sort: " + sorts[Math.max(0, v.sort)], List.of("Click: sort this box by the next")), () -> Pc.sort(p));
        c.set(44, Chest.icon(v.type == null ? Items.WHITE_WOOL : typeIcon(v.type), "Show: " + (v.type == null ? "all types" : v.type),
                List.of("Click: next type, right-click: back")), null);
        c.on(44, b -> Pc.cycleType(p, b == 1));
        c.set(45, Chest.icon(Items.ARROW, "Previous box", List.of()), () -> Pc.page(p, -1));
        c.set(50, Chest.icon(Items.ARROW, "Next box", List.of()), () -> Pc.page(p, 1));
        c.set(47, Chest.icon(Items.NAME_TAG, Pc.boxName(box, v.box), List.of("Click: next wallpaper")), () -> Pc.nextWallpaper(p));
        if (sel != null && v.confirm) {
            c.set(52, Chest.icon(Items.LIME_WOOL, "Yes, release " + BattleUi.name(sel), List.of()), () -> Pc.release(p, true));
            c.set(53, Chest.icon(Items.RED_WOOL, "No", List.of()), () -> Pc.release(p, false));
        } else if (sel != null) c.set(52, Chest.icon(Items.LAVA_BUCKET, "Release " + BattleUi.name(sel), List.of()), () -> Pc.release(p, true));
        c.open(p);
    }

    // ---- Pokédex ----

    /** 45 a page (unseen: "???"), arrows on the last row. */
    static void dex(ServerPlayer p, int page) {
        List<Species> all = Dex.order();
        int per = 45, pages = (all.size() + per - 1) / per, pg = Math.floorMod(page, pages);
        int seen = 0, caught = 0;
        for (Species s : all) {
            var k = Dex.knowledge(p, s);
            if (k != PokedexEntryProgress.UNREGISTERED) seen++;
            if (k == PokedexEntryProgress.OWNED) caught++;
        }
        Chest c = new Chest(6, "Pokédex  " + seen + " seen, " + caught + " caught  " + (pg + 1) + "/" + pages);
        for (int i = 0; i < per && pg * per + i < all.size(); i++) {
            Species s = all.get(pg * per + i);
            String no = String.format("No. %04d  ", s.getNationalPokedexNumber());
            var k = Dex.knowledge(p, s);
            if (k == PokedexEntryProgress.UNREGISTERED) {
                c.set(i, Chest.icon(Items.GRAY_STAINED_GLASS_PANE, no + "???", List.of()), null);
                continue;
            }
            List<String> types = new ArrayList<>();
            s.getTypes().forEach(t -> types.add(Lang.resolve(p, t.getDisplayName()).getString()));
            List<String> lore = new ArrayList<>(List.of(String.join(" / ", types)));
            if (k == PokedexEntryProgress.OWNED) lore.addAll(wrap(Starters.desc(s.getResourceIdentifier().getPath()), 40));
            else lore.add("Seen");
            c.set(i, Chest.icon(typeIcon(type(s.getPrimaryType())), no + Lang.resolve(p, s.getTranslatedName()).getString(), lore), null);
        }
        if (pg > 0) c.set(45, Chest.icon(Items.ARROW, "Previous page", List.of()), () -> dex(p, pg - 1));
        if (pg < pages - 1) c.set(53, Chest.icon(Items.ARROW, "Next page", List.of()), () -> dex(p, pg + 1));
        c.open(p);
    }

    static List<String> wrap(String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + w.length() > max) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(w);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}
