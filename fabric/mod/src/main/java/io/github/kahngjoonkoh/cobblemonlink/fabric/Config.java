package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * config/cobblemonlink/config.json. Bedrock players always get everything; how much vanilla Java clients get is a
 * choice, because the server draws it all for them (fabric/README.md has the cost of each level):
 * off - turned away with a message; basic - still models, chest menus; standard - animated models (idle, walk,
 * battle, faint), the screens with Cobblemon's art; full - also move animations at full rate, the battle screen and
 * the party HUD, as the Paper version.
 */
public final class Config {
    public enum Level {
        OFF, BASIC, STANDARD, FULL;

        public static Level parse(String s) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                return FULL;
            }
        }

        public boolean atLeast(Level l) { return compareTo(l) >= 0; }

        /** Ticks between model poses: 0 = posed once (still models). */
        public int poseEvery() { return this == FULL ? 2 : this == STANDARD ? 4 : 0; }

        /** Cobblemon's one-shot animations (cry, moves, recoil) on Java models. */
        public boolean moveAnimations() { return this == FULL; }

        /** Cobblemon's art over chests (starter, Summary, PC, Pokédex); below this, plain chest menus. */
        public boolean screenArt() { return atLeast(STANDARD); }

        /** The battle screen (held camera, cursor, tiles) and the party HUD; below this, a chest menu battle. */
        public boolean battleScreen() { return this == FULL; }
    }

    /** The file's fields. */
    static final class File {
        String vanillaJava = "full";
    }

    private static Level vanillaJava = Level.FULL;

    private Config() {}

    public static Level vanillaJava() { return vanillaJava; }

    static void load(Path path) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File f = new File();
        try {
            if (Files.exists(path)) f = gson.fromJson(Files.readString(path), File.class);
            else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, gson.toJson(f));
            }
        } catch (IOException e) {
            System.out.println("[cobblemonlink] " + path + ": " + e);
        }
        vanillaJava = Level.parse(f.vanillaJava);
        System.out.println("[cobblemonlink] vanilla Java: " + vanillaJava);
    }
}
