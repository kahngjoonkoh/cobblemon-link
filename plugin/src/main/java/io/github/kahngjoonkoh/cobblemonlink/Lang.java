package io.github.kahngjoonkoh.cobblemonlink;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text in each player's own language: Cobblemon's lang files (29 languages, its keys for species, moves, abilities,
 * types, items and battle messages) plus this plugin's own keys (resources/lang/*.json, en_us and ko_kr). A key
 * missing in the player's language falls back to en_us, then to the key itself. Rendered on the server, so Java and
 * Bedrock get the same words.
 */
final class Lang {
    static final String FALLBACK = "en_us";
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s");
    private final Map<String, Map<String, String>> byLocale = new HashMap<>();

    /** dir: Cobblemon's assets/cobblemon/lang (may be missing); ours come from the jar and win on clashes. */
    static Lang load(Path dir) {
        Lang l = new Lang();
        try (var files = dir != null && Files.isDirectory(dir) ? Files.list(dir) : java.util.stream.Stream.<Path>empty()) {
            for (Path f : (Iterable<Path>) files::iterator)
                if (f.toString().endsWith(".json"))
                    try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                        l.add(f.getFileName().toString().replace(".json", ""), r);
                    }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        for (String loc : new String[] {"en_us", "ko_kr"}) {
            InputStream in = Lang.class.getResourceAsStream("/lang/" + loc + ".json");
            if (in != null) try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                l.add(loc, r);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return l;
    }

    /** One lang file; Cobblemon's repeat some keys, so the last one wins (JsonParser, not a strict Map). */
    void add(String locale, Reader r) {
        Map<String, String> m = byLocale.computeIfAbsent(locale.toLowerCase(Locale.ROOT), k -> new HashMap<>());
        for (var e : com.google.gson.JsonParser.parseReader(r).getAsJsonObject().entrySet())
            if (e.getValue().isJsonPrimitive()) m.put(e.getKey(), e.getValue().getAsString());
    }

    /** "ko_KR" -> "ko_kr" (the client's language as Minecraft names it). */
    static String locale(Locale l) { return l == null ? FALLBACK : l.toString().toLowerCase(Locale.ROOT); }

    boolean has(String locale, String key) { return lookup(locale, key) != null; }

    private String lookup(String locale, String key) {
        Map<String, String> m = byLocale.get(locale);
        String v = m == null ? null : m.get(key);
        if (v == null && !FALLBACK.equals(locale)) v = byLocale.getOrDefault(FALLBACK, Map.of()).get(key);
        return v;
    }

    /** The text for key in locale, with Cobblemon's %1$s / %s arguments filled in; the key itself if unknown. */
    String get(String locale, String key, Object... args) {
        String v = lookup(locale, key);
        if (v == null) return key;
        Matcher m = ARG.matcher(v);
        StringBuilder out = new StringBuilder();
        int next = 0;
        while (m.find()) {
            int i = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
            m.appendReplacement(out, Matcher.quoteReplacement(i < args.length ? String.valueOf(args[i]) : ""));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Cobblemon's item names for every language in Adventure's GlobalTranslator, so a translatable item name
     * (Items.create) reaches each player in their language: Paper renders it per player, for Geyser too.
     */
    void registerItemNames() {
        var store = net.kyori.adventure.translation.TranslationStore.messageFormat(net.kyori.adventure.key.Key.key("cobblemonlink", "items"));
        store.defaultLocale(Locale.US);
        byLocale.forEach((loc, keys) -> {
            String[] p = loc.split("_");
            Locale l = p.length > 1 ? Locale.of(p[0], p[1].toUpperCase(Locale.ROOT)) : Locale.of(p[0]);
            keys.forEach((k, v) -> {
                if (k.startsWith("item.cobblemon.") && !k.endsWith(".tooltip"))
                    store.register(k, l, new java.text.MessageFormat(v.replace("'", "''"), l));
            });
        });
        net.kyori.adventure.translation.GlobalTranslator.translator().addSource(store);
    }

    /** get(), or `english` when neither the locale nor en_us has the key (data-driven names). */
    String getOr(String locale, String key, String english) {
        String v = lookup(locale, key);
        return v == null ? english : v;
    }
}
