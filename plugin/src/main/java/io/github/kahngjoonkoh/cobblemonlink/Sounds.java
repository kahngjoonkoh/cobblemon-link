package io.github.kahngjoonkoh.cobblemonlink;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Cobblemon's sounds by their event names (tools/build_sounds.py): the Java pack and Geyser's Bedrock pack define
 * "cobblemon:<event>", so one call reaches both clients.
 */
final class Sounds {
    private Sounds() {}

    static void play(Location at, String event) {
        if (at != null && at.getWorld() != null) at.getWorld().playSound(at, "cobblemon:" + event, SoundCategory.NEUTRAL, 1f, 1f);
    }

    static void play(Entity e, String event) { if (e != null) play(e.getLocation(), event); }

    /** A Pokémon's cry (Cobblemon plays it on a send-out and when a battle starts). */
    static void cry(Entity e, String species) { play(e, "pokemon." + species + ".cry"); }

    /** Cobblemon's battle music: wild (pvw) or against a player (pvp); stopped when the battle ends. */
    /** kind: pvw (wild), pvn (NPC trainer), pvp. */
    static void music(Player p, String kind) {
        p.playSound(p.getLocation(), "cobblemon:battle." + kind + ".default", SoundCategory.MUSIC, 0.8f, 1f);
    }

    static void stopMusic(Player p) {
        p.stopSound("cobblemon:battle.pvw.default", SoundCategory.MUSIC);
        p.stopSound("cobblemon:battle.pvp.default", SoundCategory.MUSIC);
        p.stopSound("cobblemon:battle.pvn.default", SoundCategory.MUSIC);
    }
}
