package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Cobblemon's items (items.json, tools/build_items.py): vanilla items wearing Cobblemon's item_model (the items pack on
 * Java, Geyser's mappings on Bedrock), used like Cobblemon's PokemonSelectingItem: on a party Pokémon out of battle, or
 * as a bag item (Cobblemon's Showdown "useitem", which takes the turn) in battle.
 */
final class Items {
    static final class Def {
        String name, kind, stat, nature, icon; // icon: its texture (item/medicine/potion), for the battle hotbar
        int heal, pp, exp, amount;
        double ratio;
        List<String> cure = List.of();
    }

    final Map<String, Def> defs;
    /** Kinds used on a party Pokémon (the rest behave as plain items). */
    static final java.util.Set<String> USABLE = java.util.Set.of("potion", "revive", "cure", "ether", "elixir", "x_stat",
            "dire_hit", "guard_spec", "candy", "ev", "mint", "evolution", "held", "berry", "ability_capsule", "ability_patch");
    /** Cobblemon's TMs (tms.json): move -> its type and the TM Machine's ingredients ([id or #tag, count]). */
    Map<String, Tm> tms = Map.of();

    static final class Tm {
        String type;
        List<List<Object>> recipe = List.of();
    }
    private final CobblemonLink pl;
    private final NamespacedKey key;

    private Items(CobblemonLink pl, Map<String, Def> defs) {
        this.pl = pl;
        this.defs = defs;
        this.key = new NamespacedKey(pl, "item");
        this.tmKey = new NamespacedKey(pl, "tm");
    }

    static Items load(CobblemonLink pl, Path file) throws IOException {
        if (!Files.exists(file)) return new Items(pl, Map.of());
        Items it;
        try (Reader r = Files.newBufferedReader(file)) {
            it = new Items(pl, new Gson().fromJson(r, new TypeToken<Map<String, Def>>() {}.getType()));
        }
        Path tms = file.resolveSibling("tms.json");
        if (Files.exists(tms)) try (Reader r = Files.newBufferedReader(tms)) {
            it.tms = new Gson().fromJson(r, new TypeToken<Map<String, Tm>>() {}.getType());
        }
        return it;
    }

    /** A TM of this move: Cobblemon's TM art for its type; the move lives on the item. */
    ItemStack tm(String move) {
        Tm t = tms.get(move);
        if (t == null) return null;
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(m -> {
            m.customName(Component.text("TM ").append(Component.translatable("cobblemon.move." + move, pl.dex.label("move", move)))
                    .decoration(TextDecoration.ITALIC, false));
            m.setItemModel(new NamespacedKey("cobblemon", "tm_" + t.type));
            m.getPersistentDataContainer().set(tmKey, PersistentDataType.STRING, move);
        });
        return it;
    }

    String tmMove(ItemStack it) {
        return it == null || !it.hasItemMeta() ? null : it.getPersistentDataContainer().get(tmKey, PersistentDataType.STRING);
    }

    private final NamespacedKey tmKey;

    /** Using a TM: a party Pokémon that can learn it (Cobblemon's TM learnset) learns it; the TM is used up. */
    private boolean useTm(Player p, ItemStack stack, String move) {
        List<Mon> party = pl.store.get(p.getUniqueId()).party;
        if (party.isEmpty()) return true;
        String loc = CobblemonLink.loc(p), label = pl.dex.label("move", move, loc);
        pickPokemon(p, pl.t(p, "cobblemonlink.item.use_on", "TM " + label), party,
                m -> pl.dex.of(m).tmMoves.contains(move) && !m.moves.contains(move), m -> {
            if (!stack.isSimilar(p.getInventory().getItemInMainHand())) return;
            if (m.moves.size() < 4) {
                m.moves.add(move);
                if (m.pp != null) { m.pp = null; m.ppMax = null; }
                p.sendMessage(Component.text(pl.t(p, "cobblemon.tms.teach_move", m.name(pl.dex, loc), label), NamedTextColor.GREEN));
            } else { // Cobblemon asks which move to forget
                if (m.learnable == null) m.learnable = new java.util.ArrayList<>();
                m.learnable.add(0, move);
                MoveLearn.next(pl, p);
            }
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) p.getInventory().getItemInMainHand().subtract();
            pl.store.save(p.getUniqueId());
        });
        return true;
    }

    ItemStack create(String id, int amount) {
        if (id.equals("poke_ball")) return pl.ball(amount);
        Def d = defs.get(id);
        if (d == null) return pl.blocks == null ? null : pl.blocks.item(id, amount); // Cobblemon's blocks and seeds (CustomBlocks)
        boolean ball = d.kind.equals("ball");
        ItemStack it = new ItemStack(ball ? Material.SNOWBALL : d.kind.equals("rod") ? Material.FISHING_ROD : Material.PAPER, amount); // build_items.py maps these
        it.editMeta(m -> {
            // Cobblemon's own lang key: Paper renders it in each player's language (Java and, through Geyser, Bedrock)
            m.customName(Component.translatable("item.cobblemon." + id, d.name).decoration(TextDecoration.ITALIC, false));
            m.setItemModel(new NamespacedKey("cobblemon", id));
            m.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            if (ball) m.getPersistentDataContainer().set(pl.ballKey, PersistentDataType.BYTE, (byte) 1);
            if (!ball && !d.kind.equals("rod")) m.setMaxStackSize(64);
        });
        return it;
    }

    /** The Cobblemon item id of a stack (a plain Poké Ball is "poke_ball"), or null. */
    String id(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id != null ? id : pl.isBall(it) ? "poke_ball" : null;
    }

    // ---- Poké Balls ----

    /** What PokeBalls.kt's catch rate modifiers need to know about the throw. */
    record Throw(Mon wild, Entity entity, boolean inBattle, int turn, List<Mon> active, boolean owned) {}

    /**
     * The ball's catch rate multiplier (Cobblemon's PokeBalls.kt and CatchRateModifiers), or POSITIVE_INFINITY for a
     * guaranteed catch (Master Ball, Origin Ball).
     */
    static double ballBonus(String ball, Dex dex, Throw t) {
        Dex.Species s = dex.of(t.wild());
        Location at = t.entity().getLocation();
        long time = at.getWorld().getTime();
        return switch (ball) {
            case "master_ball", "ancient_origin_ball" -> Double.POSITIVE_INFINITY;
            case "great_ball", "sport_ball", "ancient_great_ball", "ancient_leaden_ball", "ancient_wing_ball" -> 1.5;
            case "ultra_ball", "ancient_ultra_ball", "ancient_gigaton_ball", "ancient_jet_ball" -> 2;
            case "safari_ball" -> t.inBattle() ? 1 : 1.5;
            case "fast_ball" -> s.base(5) >= 100 ? 4 : 1;
            case "level_ball" -> {
                int top = t.active().stream().mapToInt(m -> m.level).max().orElse(1), l = t.wild().level;
                yield top > l * 4 ? 4 : top > l * 2 ? 3 : top > l ? 2 : 1;
            }
            case "lure_ball" -> t.wild().fished ? 4 : 1;
            case "heavy_ball" -> s.weight >= 3000 ? 4 : s.weight >= 2000 ? 2.5 : s.weight >= 1000 ? 1.5 : 1;
            case "love_ball" -> {
                String g = t.wild().gender;
                if (g == null || g.isEmpty()) yield 1;
                var opposite = t.active().stream().filter(m -> !m.gender.isEmpty() && !m.gender.equals(g)).toList();
                yield opposite.stream().anyMatch(m -> m.species.equals(t.wild().species)) ? 8 : opposite.isEmpty() ? 1 : 2.5;
            }
            case "moon_ball" -> time % 24000 < 12000 ? 1 : switch ((int) (at.getWorld().getFullTime() / 24000 % 8)) {
                case 2, 6 -> 1.5;
                case 1, 7 -> 2.5;
                case 0 -> 4;
                default -> 1;
            };
            case "net_ball" -> s.types.contains("bug") || s.types.contains("water") ? 3 : 1;
            case "dive_ball" -> t.entity().isInWater() ? 3.5 : 1;
            case "nest_ball" -> t.wild().level < 30 ? (41 - t.wild().level) / 10.0 : 1;
            case "repeat_ball" -> t.owned() ? 3.5 : 1;
            case "timer_ball" -> Math.min(4, 1 + t.turn() * (1229.0 / 4096));
            case "dusk_ball" -> {
                int light = at.getBlock().getLightLevel();
                yield light == 0 ? 3.5 : light <= 7 ? 3 : 1;
            }
            case "quick_ball" -> t.turn() == 1 ? 5 : 1;
            case "park_ball" -> at.getWorld().getBiome(at).getKey().getKey().matches(".*(plains|forest|meadow|birch|cherry).*") ? 2.5 : 1; // ponytail: #cobblemon:is_temperate by name
            case "dream_ball" -> "slp".equals(t.wild().status) ? 4 : 1;
            case "beast_ball" -> s.ultraBeast ? 5 : 0.1;
            default -> 1;
        };
    }

    /** CaptureEffects: the Friend Ball's 150 friendship, the Heal Ball's full restore; and the ball it lives in. */
    static void caught(String ball, Mon m) {
        m.ball = ball;
        if (ball.equals("friend_ball")) m.friendship = 150;
        if (ball.equals("heal_ball")) m.heal();
    }

    // ---- using an item on a Pokémon ----

    /**
     * Right-click with an item: Cobblemon's party select (a chest of the party, so every client draws it), then the
     * item's effect; in battle, the bag item goes to Showdown and takes the turn.
     */
    boolean use(Player p, ItemStack stack) {
        String tm = tmMove(stack);
        if (tm != null) {
            if (pl.battles.containsKey(p.getUniqueId())) return true;
            return useTm(p, stack, tm);
        }
        String id = id(stack);
        Def d = id == null ? null : defs.get(id);
        if (d != null && d.kind.equals("pokedex")) { // Cobblemon's Pokédex items
            PokedexScreen.open(pl, p, 0, null);
            return true;
        }
        if (d == null || !USABLE.contains(d.kind)) return false; // materials, balls, rods, machines, fossils: vanilla handling
        Battle b = pl.battles.get(p.getUniqueId());
        List<Mon> party = pl.store.get(p.getUniqueId()).party;
        if (party.isEmpty()) return true;
        if (d.kind.equals("held") || d.kind.equals("berry") && b == null) { // Cobblemon's held item: give it, and get back what the Pokémon held before
            if (b != null) {
                p.sendMessage(Component.text(pl.t(p, "cobblemonlink.item.no_held_in_battle"), NamedTextColor.RED));
                return true;
            }
            pickPokemon(p, pl.t(p, "cobblemonlink.item.give_to", name(p, id)), party, m -> true, m -> {
                if (!stack.isSimilar(p.getInventory().getItemInMainHand())) return;
                if (d.kind.equals("berry") && d.stat != null && !canUse(d, m)) { // nothing to lower: says so, keeps the berry
                    p.sendMessage(Component.text(pl.t(p, "cobblemonlink.item.no_effect"), NamedTextColor.RED));
                    return;
                }
                if (d.kind.equals("berry") && canUse(d, m)) { // a berry it can eat now is eaten
                    if (d.pp > 0) pickMove(p, d.name, m, move -> apply(p, null, id, d, m, move));
                    else apply(p, null, id, d, m, null);
                    return;
                }
                String old = m.held;
                m.held = id;
                if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) p.getInventory().getItemInMainHand().subtract();
                if (old != null && create(old, 1) != null) p.getInventory().addItem(create(old, 1)).values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
                p.sendMessage(Component.text(pl.t(p, "cobblemonlink.item.holding", m.name(pl.dex, CobblemonLink.loc(p)), name(p, id)), NamedTextColor.GREEN));
                pl.store.save(p.getUniqueId());
            });
            return true;
        }
        boolean battleOnly = d.kind.equals("x_stat") || d.kind.equals("dire_hit") || d.kind.equals("guard_spec");
        if (b == null && battleOnly) {
            p.sendMessage(Component.text(pl.t(p, "cobblemonlink.item.battle_only"), NamedTextColor.RED));
            return true;
        }
        if (b != null && !b.canUseItem()) {
            p.sendMessage(Component.text(pl.t(p, "cobblemon.battle.bagitem.cannot"), NamedTextColor.RED));
            return true;
        }
        pickPokemon(p, pl.t(p, "cobblemonlink.item.use_on", name(p, id)), party, m -> b != null ? b.canUse(m, d) : canUse(d, m), m -> {
            if (!stack.isSimilar(p.getInventory().getItemInMainHand())) return; // the item left the hand meanwhile
            if (d.kind.equals("ether") || d.kind.equals("berry") && d.pp > 0) {
                pickMove(p, d.name, m, move -> apply(p, b, id, d, m, move));
            } else apply(p, b, id, d, m, null);
        });
        return true;
    }

    private void apply(Player p, Battle b, String id, Def d, Mon m, String move) {
        if (b != null) {
            String input = battleInput(d, id, b, m, move);
            if (input == null || !b.useItem(m, id, input)) {
                p.sendMessage(Component.text(pl.t(p, "cobblemon.battle.bagitem.invalid"), NamedTextColor.RED));
                return;
            }
            if (d.kind.equals("x_stat") || d.kind.equals("dire_hit") || d.kind.equals("guard_spec")) m.addFriendship(pl.dex, 1);
        } else {
            String msg = applyOutside(pl.dex, d, m, move, p);
            if (msg != null && d.kind.equals("candy")) {
                pl.evolutions.update(m, p, x -> {});
                Bukkit.getScheduler().runTask(pl, () -> MoveLearn.next(pl, p)); // a 5th move asks which to forget
            }
            if (msg == null) {
                p.sendMessage(Component.text(pl.t(p, "cobblemon.battle.bagitem.invalid"), NamedTextColor.RED));
                return;
            }
            p.sendMessage(Component.text(msg, NamedTextColor.GREEN));
        }
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) p.getInventory().getItemInMainHand().subtract();
        pl.store.save(p.getUniqueId());
    }

    /** Cobblemon's getShowdownInput for a bag item, or null if it isn't one. */
    static String battleInput(Def d, String id, Battle b, Mon m, String move) {
        return switch (d.kind) {
            case "potion" -> d.cure.isEmpty() ? "potion " + (d.heal < 0 ? b.maxHp(m) - b.hp(m) : d.heal) : "full_restore";
            case "revive" -> "revive " + (d.ratio >= 1 ? "1" : "0.5");
            case "cure" -> "cure_status " + String.join(" ", d.cure);
            case "ether" -> move == null ? null : "ether " + move + (d.pp < 0 ? "" : " " + d.pp);
            case "elixir" -> "elixir" + (d.pp < 0 ? "" : " " + d.pp);
            case "x_stat" -> "x_stat " + d.stat + " 2";
            case "dire_hit" -> "dire_hit";
            case "guard_spec" -> "guard_spec";
            case "berry" -> d.heal > 0 ? "potion " + d.heal : d.ratio > 0 ? "potion_by_portion " + d.ratio
                    : !d.cure.isEmpty() ? "cure_status " + String.join(" ", d.cure) : d.pp > 0 && move != null ? "ether " + move + " " + d.pp : null;
            default -> null;
        };
    }

    /** PotionItem, ReviveItem, StatusCureItem... canUseOnPokemon, out of battle. */
    boolean canUse(Def d, Mon m) {
        Dex dex = pl.dex;
        boolean fainted = m.fainted(dex);
        return switch (d.kind) {
            case "potion" -> !fainted && (m.hp(dex) < m.maxHp(dex) || !d.cure.isEmpty() && !m.status.isEmpty());
            case "revive" -> fainted;
            case "cure" -> !fainted && d.cure.contains(m.status);
            case "ether", "elixir" -> m.pp != null;
            case "candy" -> m.level < 100;
            case "ev" -> m.ev(java.util.Arrays.asList(Dex.STATS).indexOf(d.stat)) < Rules.EV_STAT_MAX;
            case "mint" -> !d.nature.equalsIgnoreCase(m.mint != null ? m.mint : m.nature);
            case "evolution" -> pl.evolutions.byItem(m, d == null ? null : idOf(d)) != null;
            case "berry" -> !fainted && (d.heal > 0 || d.ratio > 0 ? m.hp(dex) < m.maxHp(dex)
                    : !d.cure.isEmpty() ? d.cure.contains(m.status)
                    : d.pp > 0 ? m.pp != null && m.ppMax != null && !m.pp.equals(m.ppMax)
                    : m.ev(java.util.Arrays.asList(Dex.STATS).indexOf(d.stat)) > 0); // no EVs to lower: no effect, as Cobblemon
            case "ability_capsule" -> { // the other standard ability; not from a hidden one
                List<String> a = dex.of(m).abilities;
                yield a.size() > 1 && a.contains(m.ability);
            }
            case "ability_patch" -> dex.of(m).hidden != null && !dex.of(m).hidden.equals(m.ability);
            default -> false;
        };
    }

    private String idOf(Def d) {
        for (var e : defs.entrySet()) if (e.getValue() == d) return e.getKey();
        return null;
    }

    /** The out-of-battle effect; a message, or null if it did nothing. */
    String applyOutside(Dex dex, Def d, Mon m, String move, Player p) {
        if (!canUse(d, m)) return null;
        String loc = CobblemonLink.loc(p), name = m.name(dex, loc);
        switch (d.kind) {
            case "potion" -> {
                int before = m.hp(dex);
                m.hp = d.heal < 0 ? -1 : Math.min(m.maxHp(dex), before + d.heal);
                if (m.hp >= m.maxHp(dex)) m.hp = -1;
                if (!d.cure.isEmpty()) m.status = "";
                return pl.t(p, "cobblemonlink.item.hp", name, m.hp(dex) - before);
            }
            case "revive" -> {
                m.hp = Math.max(1, (int) Math.floor(m.maxHp(dex) * d.ratio));
                if (m.hp >= m.maxHp(dex)) m.hp = -1;
                m.faintSecs = -1;
                return pl.t(p, "cobblemonlink.item.revived", name);
            }
            case "cure" -> {
                m.status = "";
                return pl.t(p, "cobblemonlink.battle.cured", name);
            }
            case "ether", "elixir" -> {
                if (m.pp == null || m.ppMax == null) return null;
                for (int i = 0; i < m.pp.size() && i < m.ppMax.size(); i++) {
                    if (d.kind.equals("ether") && !m.moves.get(i).equals(move)) continue;
                    m.pp.set(i, d.pp < 0 ? m.ppMax.get(i) : Math.min(m.ppMax.get(i), m.pp.get(i) + d.pp));
                }
                return pl.t(p, "cobblemonlink.item.pp", name);
            }
            case "candy" -> {
                long exp = d.exp < 0 ? Rules.expFor(dex.of(m).expGroup, m.level + 1) - m.exp : d.exp;
                List<String> said = new ArrayList<>();
                m.gainExp(dex, exp, said::add, loc);
                return String.join(" ", said);
            }
            case "ev" -> {
                if (m.evs == null) m.evs = new int[6];
                int k = java.util.Arrays.asList(Dex.STATS).indexOf(d.stat);
                int added = Rules.addEv(m.evs, k, d.amount);
                return added == 0 ? null : pl.t(p, "cobblemonlink.item.ev", name, SummaryScreen.statName(p, k), added);
            }
            case "mint" -> {
                m.mint = Character.toUpperCase(d.nature.charAt(0)) + d.nature.substring(1);
                return pl.t(p, "cobblemonlink.item.mint", name, name(p, idOf(d)));
            }
            case "evolution" -> {
                String msg = pl.evolutions.evolveWithItem(m, idOf(d), p);
                return msg;
            }
            case "berry" -> {
                if (d.heal > 0 || d.ratio > 0) {
                    int before = m.hp(dex);
                    m.hp = Math.min(m.maxHp(dex), before + (d.heal > 0 ? d.heal : (int) Math.floor(m.maxHp(dex) * d.ratio)));
                    if (m.hp >= m.maxHp(dex)) m.hp = -1;
                    return pl.t(p, "cobblemonlink.item.hp", name, m.hp(dex) - before);
                }
                if (!d.cure.isEmpty()) {
                    m.status = "";
                    return pl.t(p, "cobblemonlink.battle.cured", name);
                }
                if (d.pp > 0) {
                    for (int i = 0; i < m.pp.size() && i < m.ppMax.size(); i++)
                        if (m.moves.get(i).equals(move)) m.pp.set(i, Math.min(m.ppMax.get(i), m.pp.get(i) + d.pp));
                    return pl.t(p, "cobblemonlink.item.pp", name);
                }
                // Pomeg and the like: EVs down by 10, friendship up (Gen 4+: 10 / 5 / 2)
                int k = java.util.Arrays.asList(Dex.STATS).indexOf(d.stat), f = m.friendship(dex);
                if (m.evs == null) m.evs = new int[6];
                int before = m.evs[k];
                m.evs[k] = Math.max(0, before + d.amount);
                m.addFriendship(dex, f < 100 ? 10 : f < 200 ? 5 : 2);
                return pl.t(p, "cobblemonlink.item.ev_down", name, SummaryScreen.statName(p, k), before - m.evs[k]);
            }
            case "ability_capsule", "ability_patch" -> {
                List<String> a = dex.of(m).abilities;
                m.ability = d.kind.equals("ability_patch") ? dex.of(m).hidden : a.get((a.indexOf(m.ability) + 1) % a.size());
                return pl.t(p, "cobblemon.ability_changer.changed", name, dex.label("ability", m.ability, loc));
            }
            default -> {
                return null;
            }
        }
    }

    /** The item's name in the player's language (Cobblemon's item.cobblemon.<id>). */
    String name(Player p, String id) {
        Def d = defs.get(id);
        return pl.lang.getOr(CobblemonLink.loc(p), "item.cobblemon." + id, d == null ? id : d.name);
    }

    // ---- party select ----

    /** Cobblemon's party select: one chest row, the party from the second slot; the ones it can't be used on greyed. */
    void pickPokemon(Player p, String title, List<Mon> party, java.util.function.Predicate<Mon> ok, Consumer<Mon> chosen) {
        Menu menu = new Menu(9, title);
        for (int i = 0; i < party.size(); i++) {
            Mon m = party.get(i);
            boolean can = ok.test(m);
            String no = pl.t(p, "cobblemonlink.item.no_effect");
            ItemStack icon = Menu.monIcon(pl.dex, m, can ? pl.t(p, "cobblemonlink.item.click") : no);
            if (!can) icon = Menu.icon(Material.GRAY_STAINED_GLASS_PANE, m.name(pl.dex, CobblemonLink.loc(p)) + "  Lv." + m.level, List.of(no));
            menu.set(i + 1, icon, can ? e -> {
                p.closeInventory();
                chosen.accept(m);
            } : null);
        }
        p.openInventory(menu.getInventory());
    }

    /** Ether / Max Ether: which move. */
    void pickMove(Player p, String title, Mon m, Consumer<String> chosen) {
        Menu menu = new Menu(9, title);
        for (int i = 0; i < m.moves.size(); i++) {
            String mv = m.moves.get(i);
            String pp = m.pp != null && i < m.pp.size() && m.ppMax != null && i < m.ppMax.size() ? m.pp.get(i) + "/" + m.ppMax.get(i) : "full";
            menu.set(i + 2, Menu.icon(Material.PAPER, pl.dex.label("move", mv), List.of("PP " + pp)), e -> {
                p.closeInventory();
                chosen.accept(mv);
            });
        }
        p.openInventory(menu.getInventory());
    }
}
