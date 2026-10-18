package io.github.kahngjoonkoh.cobblemonlink.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * cobblemon-link is about to offer a player without Pokémon their starter (on first join, Ctrl while standing still,
 * the Pokémon menu or /starter). Cancel it to offer the starter some other way (an NPC, a quest); then call
 * {@link CobblemonLinkApi#openStarter} when it's time.
 */
public final class StarterPromptEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final boolean firstJoin;
    private boolean cancelled;

    public StarterPromptEvent(Player player, boolean firstJoin) {
        this.player = player;
        this.firstJoin = firstJoin;
    }

    public Player getPlayer() { return player; }

    /** True for the prompt on joining, false when the player asked for it. */
    public boolean isFirstJoin() { return firstJoin; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
