package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cobblemon's battle HP tiles for Bedrock (BattleOverlay: yours top left, theirs top right, stacked in doubles and
 * triples): a title the UI pack draws and never shows (build_bedrock_ui.py bt_screen), "|CB" then six tiles of
 * "Z<i>" and FIELDS, ASCII padded with '~', as the party HUD does.
 */
public final class BattleTiles {
    /** build_bedrock_ui.py BT_FIELDS: used, portrait, name, level, gender, HP bar step, HP text, status. */
    static final int[] FIELDS = {1, 40, 12, 4, 1, 3, 7, 3};
    static final int STEPS = 48;
    static final java.util.Set<String> STATUS = java.util.Set.of("brn", "frz", "par", "psn", "slp", "tox");

    private BattleTiles() {}

    static void send(ServerPlayer p, PokemonBattle b, Predicate<ActiveBattlePokemon> own, Function<Pokemon, String> name) {
        String[][] tiles = new String[6][];
        int[] n = {0, 3};
        for (ActiveBattlePokemon ap : b.getActivePokemon()) {
            var bp = ap.getBattlePokemon();
            if (bp == null) continue;
            boolean mine = own.test(ap);
            int i = n[mine ? 0 : 1]++;
            if (mine ? i > 2 : i > 5) continue;
            var mon = bp.getEffectedPokemon();
            String sd = mon.getStatus() == null ? null : mon.getStatus().getStatus().getShowdownName();
            tiles[i] = tile(mon, name.apply(mon), bp.getHealth(), bp.getMaxHealth(), mine, sd);
        }
        title(p, payload(tiles));
    }

    /** The party HUD takes the title back (PartyHud draws it again while visible). */
    static void clear(ServerPlayer p) { title(p, PartyHud.HIDDEN); }

    static String[] tile(Pokemon m, String name, int hp, int max, boolean mine, String status) {
        float ratio = Math.clamp(hp / (float) Math.max(1, max), 0f, 1f);
        int step = hp <= 0 ? 0 : Math.max(1, Math.round(STEPS * ratio));
        String look = Models.look(m.getSpecies().getResourceIdentifier().getPath(), m.getAspects());
        return new String[]{"a", Models.has(look) ? PartyHud.bedrockName(look) : "", // under textures/items/portrait/
                PartyHud.ascii(name), "L" + m.getLevel(), m.getGender() == Gender.MALE ? "m" : m.getGender() == Gender.FEMALE ? "f" : "n",
                String.format("b%02d", step), mine ? hp + "/" + max : (int) Math.ceil(ratio * 100) + "%",
                status != null && STATUS.contains(status) ? status : "---"};
    }

    /** "|CB" and six tiles, each "Z<i>" then FIELDS (null: unused, all '~'), as the party HUD's slots. */
    static String payload(String[][] tiles) {
        StringBuilder b = new StringBuilder("|CB");
        for (int i = 0; i < 6; i++) {
            b.append('Z').append(i);
            for (int k = 0; k < FIELDS.length; k++) {
                String x = tiles[i] == null ? "" : tiles[i][k];
                if (x.length() > FIELDS[k]) x = x.substring(0, FIELDS[k]);
                b.append(x).append("~".repeat(FIELDS[k] - x.length()));
            }
        }
        return b.toString();
    }

    private static void title(ServerPlayer p, String text) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 40, 0));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text)));
    }
}
