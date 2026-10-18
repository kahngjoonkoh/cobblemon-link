package io.github.kahngjoonkoh.cobblemonlink.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A wild battle ended. Fired after cobblemon-link has given EXP, saved the party and removed or released the wild Pokémon. */
public final class WildBattleEndEvent extends Event {
    public enum Outcome { WON, CAUGHT, LOST, FLED }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String species;
    private final int level;
    private final boolean shiny;
    private final Outcome outcome;

    public WildBattleEndEvent(Player player, String species, int level, boolean shiny, Outcome outcome) {
        this.player = player;
        this.species = species;
        this.level = level;
        this.shiny = shiny;
        this.outcome = outcome;
    }

    public Player getPlayer() { return player; }
    /** Cobblemon species id, e.g. "pikachu". */
    public String getSpecies() { return species; }
    public int getLevel() { return level; }
    public boolean isShiny() { return shiny; }
    public Outcome getOutcome() { return outcome; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
