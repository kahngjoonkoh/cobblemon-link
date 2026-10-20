package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Cobblemon's English text for Bedrock players: Geyser only knows Minecraft's own keys, so "cobblemon.*" keys are
 * written out on the server (Java clients read them from the resource pack). ponytail: English only.
 */
public final class Lang {
    private static final Map<String, String> EN = new HashMap<>();
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s|%%");

    private Lang() {}

    static void load() {
        for (String ns : new String[]{"cobblemon", "cobblemonlink"})
            try (var in = Lang.class.getClassLoader().getResourceAsStream("assets/" + ns + "/lang/en_us.json")) {
                if (in != null) JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                        .entrySet().forEach(e -> EN.put(e.getKey(), e.getValue().getAsString()));
            } catch (Exception e) {
                System.out.println("[cobblemonlink] no " + ns + " lang: " + e);
            }
        System.out.println("[cobblemonlink] lang: " + EN.size() + " keys");
    }

    public static boolean has(String key) { return EN.containsKey(key); }

    public static String get(String key) { return EN.getOrDefault(key, key); }

    /** c with every known mod key written out (styles kept); c itself when nothing changes. */
    public static Component resolve(Component c) {
        if (c == null || !needs(c)) return c;
        MutableComponent out;
        if (c.getContents() instanceof TranslatableContents t && EN.containsKey(t.getKey())) {
            out = Component.empty();
            String f = EN.get(t.getKey());
            Matcher m = ARG.matcher(f);
            int at = 0, next = 0;
            while (m.find()) {
                out.append(f.substring(at, m.start()));
                at = m.end();
                if (m.group().equals("%%")) { out.append("%"); continue; }
                int i = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                Object a = i < t.getArgs().length ? t.getArgs()[i] : "";
                out.append(a instanceof Component ac ? resolve(ac) : Component.literal(String.valueOf(a)));
            }
            out.append(f.substring(at));
        } else if (c.getContents() instanceof TranslatableContents t) { // Minecraft's key, a mod's text in its arguments
            Object[] args = t.getArgs().clone();
            for (int i = 0; i < args.length; i++) if (args[i] instanceof Component ac) args[i] = resolve(ac);
            out = Component.translatableWithFallback(t.getKey(), t.getFallback(), args);
        } else {
            out = MutableComponent.create(c.getContents());
        }
        out.setStyle(c.getStyle());
        for (Component s : c.getSiblings()) out.append(resolve(s));
        return out;
    }

    /** A mod item's name written out, and its custom name and lore. */
    public static net.minecraft.world.item.ItemStack item(net.minecraft.world.item.ItemStack it) {
        if (it.isEmpty()) return it;
        var name = it.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        var lore = it.get(net.minecraft.core.component.DataComponents.LORE);
        boolean modItem = !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it.getItem()).getNamespace().equals("minecraft");
        if (!modItem && (name == null || !needs(name)) && (lore == null || lore.lines().stream().noneMatch(Lang::needs))) return it;
        it = it.copy();
        // PolyMc names its stand-in item after the original one's key, so the written-out name goes in custom_name
        if (name != null) it.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, resolve(name));
        else if (modItem) it.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                resolve(it.getItem().getName(it)).copy().withStyle(st -> st.withItalic(false)));
        if (lore != null) it.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore.lines().stream().map(Lang::resolve).toList()));
        return it;
    }

    private static boolean needs(Component c) {
        if (c.getContents() instanceof TranslatableContents t) {
            if (EN.containsKey(t.getKey())) return true;
            for (Object a : t.getArgs()) if (a instanceof Component ac && needs(ac)) return true;
        }
        for (Component s : c.getSiblings()) if (needs(s)) return true;
        return false;
    }
}
