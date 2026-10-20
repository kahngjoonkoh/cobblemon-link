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
 * Cobblemon's text (and ours, "cobblemonlink.*") in each player's own language, falling back to en_us: written out
 * on the server where the client can't (Bedrock: Geyser only knows Minecraft's keys; our screens' item names and
 * forms). Java clients also get every language file in the pack. The language is the one the client reports
 * (Geyser reports the Bedrock player's), e.g. "de_de".
 */
public final class Lang {
    static final String[] NAMESPACES = {"cobblemon", "cobblemonlink"};
    private static final Map<String, Map<String, String>> LANGS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, String> EN = table("en_us");
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s|%%");

    private Lang() {}

    static void load() {
        System.out.println("[cobblemonlink] lang: " + EN.size() + " keys");
        // every language file to vanilla Java clients (PolyMc's pack has none)
        eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(b -> {
            for (String ns : NAMESPACES) for (String code : codes(ns)) {
                byte[] d = bytes("assets/" + ns + "/lang/" + code + ".json");
                if (d != null) b.addData("assets/" + ns + "/lang/" + code + ".json", d);
            }
        });
    }

    /** The language files a mod ships (Cobblemon 1.8.2: 29), from its lang folder. */
    static java.util.List<String> codes(String ns) {
        java.util.List<String> out = new java.util.ArrayList<>();
        var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(ns).flatMap(m -> m.findPath("assets/" + ns + "/lang"));
        if (dir.isEmpty()) return out;
        try (var files = java.nio.file.Files.list(dir.get())) {
            files.forEach(f -> { String n = f.getFileName().toString(); if (n.endsWith(".json")) out.add(n.substring(0, n.length() - 5)); });
        } catch (java.io.IOException e) {
            out.add("en_us");
        }
        return out;
    }

    private static byte[] bytes(String path) {
        try (var in = Lang.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** A language's keys (ours and Cobblemon's), read once; empty when nobody ships it. */
    static Map<String, String> table(String code) {
        return LANGS.computeIfAbsent(code, c -> {
            Map<String, String> m = new HashMap<>();
            for (String ns : NAMESPACES) {
                byte[] d = bytes("assets/" + ns + "/lang/" + c + ".json");
                if (d != null) JsonParser.parseString(new String(d, StandardCharsets.UTF_8)).getAsJsonObject()
                        .entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
            }
            return m;
        });
    }

    /** The player's language code as the client reports it ("de_de"); en_us when unknown. */
    static String code(net.minecraft.server.level.ServerPlayer p) { return code(p == null ? null : p.clientInformation().language()); }

    static String code(String reported) {
        return reported == null || reported.isEmpty() ? "en_us" : reported.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }

    /** key in language code, then en_us, then the key itself. */
    static String lookup(String code, String key) {
        String v = table(code).get(key);
        return v != null ? v : EN.getOrDefault(key, key);
    }

    public static boolean has(String key) { return EN.containsKey(key); }

    public static String get(String key) { return EN.getOrDefault(key, key); }

    public static String get(net.minecraft.server.level.ServerPlayer p, String key) { return lookup(code(p), key); }

    /** One of our own or Cobblemon's keys, formatted with args (String.format style) in the player's language. */
    public static String t(net.minecraft.server.level.ServerPlayer p, String key, Object... args) {
        String f = get(p, key);
        try { return args.length == 0 ? f : String.format(f, args); } catch (java.util.IllegalFormatException e) { return f; }
    }

    private static final ThreadLocal<String> CODE = ThreadLocal.withInitial(() -> "en_us");

    /** c for player p: every known mod key written out in p's language. */
    public static Component resolve(net.minecraft.server.level.ServerPlayer p, Component c) {
        String was = CODE.get();
        CODE.set(code(p));
        try { return resolve(c); } finally { CODE.set(was); }
    }

    public static net.minecraft.world.item.ItemStack item(net.minecraft.server.level.ServerPlayer p, net.minecraft.world.item.ItemStack it) {
        String was = CODE.get();
        CODE.set(code(p));
        try { return item(it); } finally { CODE.set(was); }
    }

    /** c with every known mod key written out (styles kept), in the language being resolved for (en_us by default). */
    public static Component resolve(Component c) {
        if (c == null || !needs(c)) return c;
        MutableComponent out;
        if (c.getContents() instanceof TranslatableContents t && EN.containsKey(t.getKey())) {
            out = Component.empty();
            String f = lookup(CODE.get(), t.getKey());
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
