package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.papermc.paper.potion.PotionMix;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;

/**
 * Cobblemon's crafting, furnace, smithing and brewing-stand recipes (recipes.json, tools/build_items.py), registered
 * with Cobblemon's items as exact-item ingredients and results. A recipe naming something this server lacks is skipped.
 */
final class Recipes {
    static final class Def {
        String id, type, result;
        int count = 1, time = 200;
        float xp;
        List<String> pattern;
        Map<String, List<String>> key;
        List<List<String>> ingredients;
        List<String> ingredient, base, addition, template, input, bottle;
    }

    /** Cobblemon's Campfire Pot recipes (cooking_pot, cooking_pot_shapeless), cooked by Machines' pot menu. */
    static final List<Def> pot = new ArrayList<>();

    static int register(CobblemonLink pl, Path file) throws IOException {
        if (!Files.exists(file)) return 0;
        List<Def> defs;
        try (Reader r = Files.newBufferedReader(file)) {
            defs = new Gson().fromJson(r, new TypeToken<List<Def>>() {}.getType());
        }
        int n = 0;
        for (Def d : defs) {
            try {
                if (add(pl, d)) n++;
            } catch (IllegalArgumentException | IllegalStateException e) {
                pl.getLogger().fine("recipe " + d.id + " skipped: " + e.getMessage());
            }
        }
        return n;
    }

    private static boolean add(CobblemonLink pl, Def d) {
        NamespacedKey key = new NamespacedKey(pl, "cobblemon_" + d.id);
        ItemStack result = stack(pl, d.result, d.count);
        if (result == null) return false;
        Recipe r = switch (d.type) {
            case "shaped" -> {
                ShapedRecipe s = new ShapedRecipe(key, result).shape(d.pattern.toArray(String[]::new));
                for (var e : d.key.entrySet()) s.setIngredient(e.getKey().charAt(0), choice(pl, e.getValue()));
                yield s;
            }
            case "shapeless" -> {
                ShapelessRecipe s = new ShapelessRecipe(key, result);
                for (List<String> i : d.ingredients) s.addIngredient(choice(pl, i));
                yield s;
            }
            case "smelting" -> new FurnaceRecipe(key, result, choice(pl, d.ingredient), d.xp, d.time);
            case "blasting" -> new BlastingRecipe(key, result, choice(pl, d.ingredient), d.xp, d.time);
            case "smithing" -> new SmithingTransformRecipe(key, result, choice(pl, d.template), choice(pl, d.base), choice(pl, d.addition));
            case "pot", "pot_shapeless" -> {
                if (d.result.startsWith("cobblemon:") && pl.items.create(d.result.substring(10), 1) == null) yield null;
                pot.add(d);
                yield null;
            }
            case "brewing" -> {
                Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, result, choice(pl, d.bottle), choice(pl, d.input)));
                yield null;
            }
            default -> throw new IllegalArgumentException(d.type);
        };
        if (r != null) Bukkit.addRecipe(r);
        return true;
    }

    /**
     * The pot recipe this 3x3 grid (row by row, null = empty) makes, or null: shaped ones anywhere in the grid (not
     * mirrored), shapeless ones by their ingredients.
     */
    static Def cook(CobblemonLink pl, ItemStack[] grid) {
        for (Def d : pot) if (d.type.equals("pot") ? shaped(pl, d, grid) : shapeless(pl, d, grid)) return d;
        return null;
    }

    private static boolean shaped(CobblemonLink pl, Def d, ItemStack[] grid) {
        int h = d.pattern.size(), w = d.pattern.stream().mapToInt(String::length).max().orElse(0);
        for (int oy = 0; oy + h <= 3; oy++)
            for (int ox = 0; ox + w <= 3; ox++) {
                boolean ok = true;
                for (int i = 0; i < 9 && ok; i++) {
                    int x = i % 3 - ox, y = i / 3 - oy;
                    char c = x >= 0 && y >= 0 && y < h && x < d.pattern.get(y).length() ? d.pattern.get(y).charAt(x) : ' ';
                    ok = c == ' ' ? empty(grid[i]) : !empty(grid[i]) && is(pl, grid[i], d.key.get(String.valueOf(c)));
                }
                if (ok) return true;
            }
        return false;
    }

    private static boolean shapeless(CobblemonLink pl, Def d, ItemStack[] grid) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : grid) if (!empty(s)) left.add(s);
        if (left.size() != d.ingredients.size()) return false;
        for (List<String> ing : d.ingredients) { // ponytail: greedy, fine for Cobblemon's recipes (no overlapping choices)
            ItemStack hit = left.stream().filter(s -> is(pl, s, ing)).findFirst().orElse(null);
            if (hit == null) return false;
            left.remove(hit);
        }
        return true;
    }

    static boolean empty(ItemStack s) { return s == null || s.isEmpty(); }

    /** An item matches one of these ids or tags (a Cobblemon item only its own id). */
    static boolean is(CobblemonLink pl, ItemStack s, List<String> ids) {
        String cob = pl.items.id(s);
        for (String id : ids) {
            if (id.startsWith("cobblemon:")) { if (id.substring(10).equals(cob)) return true; }
            else if (cob != null) continue;
            else if (id.startsWith("#")) {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.fromString(id.substring(1)), Material.class);
                if (tag != null && tag.isTagged(s.getType())) return true;
            } else if (s.getType().getKey().toString().equals(id)) return true;
        }
        return false;
    }

    static ItemStack result(CobblemonLink pl, Def d) { return stack(pl, d.result, d.count); }

    /** "cobblemon:x" -> Cobblemon's item; "minecraft:x" -> the vanilla item. */
    private static ItemStack stack(CobblemonLink pl, String id, int count) {
        if (id.startsWith("cobblemon:")) return pl.items.create(id.substring(10), count);
        Material m = Material.matchMaterial(id);
        return m == null || !m.isItem() ? null : new ItemStack(m, count);
    }

    /** Vanilla items and tags as a material choice; any Cobblemon item makes it an exact choice of stacks. */
    private static RecipeChoice choice(CobblemonLink pl, List<String> ids) {
        List<Material> mats = new ArrayList<>();
        List<ItemStack> exact = new ArrayList<>();
        for (String id : ids) {
            if (id.startsWith("#")) {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.fromString(id.substring(1)), Material.class);
                if (tag == null) throw new IllegalArgumentException("no tag " + id);
                mats.addAll(tag.getValues());
            } else {
                ItemStack s = stack(pl, id, 1);
                if (s == null) throw new IllegalArgumentException("no item " + id);
                if (id.startsWith("cobblemon:")) exact.add(s);
                else mats.add(s.getType());
            }
        }
        if (exact.isEmpty()) return new RecipeChoice.MaterialChoice(mats);
        mats.forEach(m -> exact.add(new ItemStack(m)));
        return new RecipeChoice.ExactChoice(exact);
    }
}
