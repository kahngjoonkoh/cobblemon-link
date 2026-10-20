package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.FleeAttemptActionResponse;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownPokemon;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Cobblemon's battle screen for players without the mod, driven by Cobblemon's own battle: when Cobblemon asks the
 * player to choose (BattleMakeChoicePacket), the menu opens; a choice goes back as the actor's Showdown response, as
 * Cobblemon's client sends it. The battle log is Cobblemon's battle messages.
 * Bedrock: Floodgate forms in the Paper version's layout (title "Battle" + MARK + 1 menu, 2 moves, 3 party), HP in
 * boss bars, a prompt in the action bar while the form is closed (tap the Pokémon or /battle reopens it).
 */
public final class BattleUi {
    private static final Map<UUID, BattleUi> OPEN = new HashMap<>();
    static final List<String> TYPES = List.of("normal", "fire", "water", "grass", "electric", "ice", "fighting",
            "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");

    private final ServerPlayer player;
    private final List<String> log = new ArrayList<>();
    private final ServerBossEvent wildBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final ServerBossEvent ownBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    private boolean choosing, formOpen;

    private BattleUi(ServerPlayer player) {
        this.player = player;
        wildBar.addPlayer(player);
        ownBar.addPlayer(player);
    }

    static BattleUi of(ServerPlayer p) { return OPEN.computeIfAbsent(p.getUUID(), k -> new BattleUi(p)); }

    static BattleUi get(ServerPlayer p) { return OPEN.get(p.getUUID()); }

    /** Every second: HP bars and the prompt while the menu is closed. */
    static void tick() {
        for (var ui : new ArrayList<>(OPEN.values())) {
            if (ui.player.hasDisconnected() || battle(ui.player) == null) {
                ui.end();
                continue;
            }
            ui.bars();
            if (ui.choosing && !ui.formOpen)
                ui.player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("You need to choose an action. Tap the Pokémon.")));
        }
    }

    static PokemonBattle battle(ServerPlayer p) { return BattleRegistry.getBattleByParticipatingPlayer(p); }

    private BattleActor actor() {
        PokemonBattle b = battle(player);
        return b == null ? null : b.getActor(player.getUUID());
    }

    // ---- Cobblemon's messages to the player ----

    void message(List<Component> lines) {
        for (Component c : lines) {
            String s = Lang.resolve(c).getString();
            if (!s.isBlank()) log.add(s);

        }
        while (log.size() > 40) log.removeFirst();
    }

    void makeChoice() {
        bars();
        if (choosing && formOpen) return; // Cobblemon may ask twice for one turn
        choosing = true;
        openMenu();
    }

    void end() {
        OPEN.remove(player.getUUID());
        wildBar.removeAllPlayers();
        ownBar.removeAllPlayers();
        choosing = false;
    }

    /** The Pokémon tapped or /battle: the menu again, if a choice is due. */
    void reveal() { if (choosing && !formOpen) openMenu(); }

    // ---- menus ----

    private ShowdownActionRequest request() {
        BattleActor a = actor();
        return a == null || !a.getMustChoose() ? null : a.getRequest();
    }

    void openMenu() {
        ShowdownActionRequest r = request();
        if (r == null) {
            choosing = false;
            return;
        }
        if (r.getForceSwitch().stream().anyMatch(Boolean::booleanValue)) {
            openSwitch(false);
            return;
        }
        boolean trapped = !r.getActive().isEmpty() && r.getActive().getFirst().getTrapped();
        List<Forms.Opt> opts = new ArrayList<>(List.of(new Forms.Opt("Fight", "menu_fight", this::openFight),
                new Forms.Opt("Switch", "menu_switch", () -> openSwitch(true)),
                new Forms.Opt("Catch", "menu_bag", this::capture), new Forms.Opt("Run", "menu_run", () -> respond(new FleeAttemptActionResponse()))));
        if (trapped) opts.remove(1);
        form(1, opts);
    }

    private void openFight() {
        ShowdownActionRequest r = request();
        BattleActor a = actor();
        if (r == null || r.getActive().isEmpty()) return;
        List<InBattleMove> moves = r.getActive().getFirst().getMoves();
        List<Forms.Opt> opts = new ArrayList<>();
        for (int i = 0; i < 4; i++) { // always 4, so Back is the 5th button where the pack expects it
            if (i >= moves.size()) {
                opts.add(new Forms.Opt(" ", "none", this::openFight));
                continue;
            }
            InBattleMove mv = moves.get(i);
            var template = Moves.getByName(mv.getId());
            String type = template == null ? "normal" : template.getElementalType().getName().toLowerCase(java.util.Locale.ROOT);
            String name = template == null ? mv.getMove() : Lang.resolve(template.getDisplayName()).getString();
            opts.add(new Forms.Opt(name + "\n          " + mv.getPp() + "/" + mv.getMaxpp(), "move_" + (TYPES.contains(type) ? type : "normal"),
                    mv.getDisabled() ? this::openFight : () -> respond(new MoveActionResponse(mv.getId(), target(a, mv), null))));
        }
        opts.add(new Forms.Opt(" ", "back", this::openMenu));
        form(2, opts);
    }

    /** As Cobblemon's client: the target when the move has exactly one, else none (Showdown picks). */
    private static String target(BattleActor a, InBattleMove mv) {
        if (a == null || a.getActivePokemon().isEmpty()) return null;
        var targets = mv.getTargets(a.getActivePokemon().getFirst());
        return targets == null || targets.size() != 1 ? null : targets.getFirst().getPNX();
    }

    /** cancellable = false when a fainted Pokémon must be replaced. */
    private void openSwitch(boolean cancellable) {
        ShowdownActionRequest r = request();
        BattleActor a = actor();
        if (r == null || a == null) return;
        List<Forms.Opt> opts = new ArrayList<>();
        for (ShowdownPokemon sp : r.getSide().getPokemon()) {
            if (sp.getActive() || sp.getCondition().endsWith(" fnt")) continue;
            BattlePokemon bp = a.getPokemonList().stream().filter(p -> p.getUuid().equals(sp.getUuid())).findFirst().orElse(null);
            if (bp == null) continue;
            var mon = bp.getEffectedPokemon();
            opts.add(new Forms.Opt(name(mon) + "  Lv." + mon.getLevel() + "\n" + bp.getHealth() + "/" + bp.getMaxHealth(), "party",
                    () -> respond(new SwitchActionResponse(sp.getUuid()))));
        }
        while (opts.size() < 5) opts.add(new Forms.Opt(" ", "none", () -> openSwitch(cancellable))); // Back is the 6th
        if (cancellable) opts.add(new Forms.Opt(" ", "back", this::openMenu));
        form(3, opts);
    }

    /** Cobblemon's Catch: the menu steps aside and the player throws a Poké Ball (Cobblemon's own capture). */
    private void capture() {
        player.sendSystemMessage(Component.literal("Throw a Poké Ball at your opponent to capture it."));
    }

    private void respond(ShowdownActionResponse response) {
        BattleActor a = actor();
        if (a == null || !a.getMustChoose()) return;
        try {
            a.setActionResponses(List.of(response));
            choosing = false;
        } catch (RuntimeException e) { // IllegalActionChoiceException: Cobblemon's reason, then the menu again
            player.sendSystemMessage(Component.literal(String.valueOf(e.getMessage())).withStyle(ChatFormatting.RED));
            openMenu();
        }
    }

    private void form(int kind, List<Forms.Opt> opts) {
        List<String> last = log.subList(Math.max(0, log.size() - 4), log.size());
        formOpen = true;
        Forms.send(player, "Battle" + Forms.MARK + kind, String.join("\n", last.isEmpty() ? List.of("What will you do?") : last), opts, i -> {
            formOpen = false; // any answer closes it; the action may send the next one
            if (i >= 0 && i < opts.size()) opts.get(i).action().run();
        });
    }

    // ---- HP ----

    private void bars() {
        PokemonBattle b = battle(player);
        if (b == null) return;
        for (ActiveBattlePokemon ap : b.getActivePokemon()) {
            BattlePokemon bp = ap.getBattlePokemon();
            if (bp == null) continue;
            var mon = bp.getEffectedPokemon();
            boolean own = ap.getActor() == actor();
            float ratio = Math.clamp((float) bp.getHealth() / Math.max(1, bp.getMaxHealth()), 0f, 1f);
            var status = mon.getStatus() == null ? "" : "  " + mon.getStatus().getStatus().getShowdownName().toUpperCase(java.util.Locale.ROOT);
            if (own) {
                ownBar.setName(Component.literal(name(mon) + "  Lv." + mon.getLevel() + "   " + bp.getHealth() + "/" + bp.getMaxHealth() + " HP" + status));
                ownBar.setProgress(ratio);
            } else {
                wildBar.setName(Component.literal((mon.isWild() ? "Wild " : "") + name(mon) + "  Lv." + mon.getLevel() + status));
                wildBar.setProgress(ratio);
            }
        }
    }

    static String name(com.cobblemon.mod.common.pokemon.Pokemon mon) { return Lang.resolve(mon.getDisplayName(false)).getString(); }
}
