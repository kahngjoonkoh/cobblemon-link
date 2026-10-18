package io.github.kahngjoonkoh.cobblemonlink.api;

import io.github.kahngjoonkoh.cobblemonlink.CobblemonLink;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What other plugins (e.g. cobbleverse-link) may use. Events: {@link StarterPromptEvent}, {@link WildBattleEndEvent}.
 * Add `CobblemonLink` as a dependency (load BEFORE) in your paper-plugin.yml.
 */
public final class CobblemonLinkApi {
    private CobblemonLinkApi() {}

    private static CobblemonLink plugin() { return CobblemonLink.getPlugin(CobblemonLink.class); }

    /** True once the player has any Pokémon (party or PC). */
    public static boolean hasPokemon(Player p) { return plugin().hasPokemon(p); }

    /**
     * Cobblemon's starter choice from these species (Cobblemon ids), titled "<from>: choose your first Pokémon".
     * Does nothing for a player who already has Pokémon.
     */
    public static void openStarter(Player p, List<String> species, String from) { plugin().openStarter(p, species, from); }

    /** The starters by region, Kanto to Paldea, three each (Cobblemon's default starter list). */
    public static List<List<String>> startersByRegion() { return CobblemonLink.startersByRegion(); }

    /** n of cobblemon-link's Poké Ball item (thrown to catch; Cobblemon's art). */
    public static ItemStack pokeBall(int n) { return plugin().ball(n); }

    /** True if the item is cobblemon-link's Poké Ball. */
    public static boolean isPokeBall(ItemStack item) { return plugin().isBall(item); }
}
