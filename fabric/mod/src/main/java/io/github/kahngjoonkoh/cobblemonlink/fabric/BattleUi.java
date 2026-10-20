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
    // vanilla Java's battle screen (full level): the held camera and cursor (BattleScreen), hidden with Shift
    private BattleScreen screen;
    private boolean hidden, general, wasSneaking;
    private int ticks, hiddenAt, lastSneak = -100;

    private BattleUi(ServerPlayer player) {
        this.player = player;
        if (!javaScreen()) { // Java's battle screen draws Cobblemon's own HP tiles instead
            wildBar.addPlayer(player);
            ownBar.addPlayer(player);
        }
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
            if (ui.choosing && !ui.formOpen && !ui.javaScreen())
                ui.player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(Clients.bedrock(ui.player)
                        ? "You need to choose an action. Tap the Pokémon." : "You need to choose an action. Right-click the Pokémon or /battle.")));
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
        if (screen != null) screen.close();
        screen = null;
        if (javaScreen()) player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
        OPEN.remove(player.getUUID());
        wildBar.removeAllPlayers();
        ownBar.removeAllPlayers();
        choosing = false;
    }

    /** The Pokémon tapped or /battle: the menu again, if a choice is due (Java's screen: back from hiding). */
    void reveal() {
        if (javaScreen()) {
            if (!hidden || !player.onGround()) return;
            hidden = false;
            if (choosing) openMenu();
            else if (cursor()) screen.show(List.of());
            return;
        }
        if (choosing && (!formOpen || !Clients.bedrock(player))) openMenu();
    }

    boolean javaScreen() { return Clients.java(player) && Ui.loaded && Config.vanillaJava().battleScreen(); }

    /** Every tick for Java's battle screen: the cursor follows the aim, the overlay redraws, Shift hides it. */
    static void tickScreens() {
        for (var ui : new ArrayList<>(OPEN.values())) if (ui.javaScreen()) ui.screenTick();
    }

    private void screenTick() {
        ticks++;
        boolean sneaking = player.isShiftKeyDown();
        if (sneaking && !wasSneaking) { // double-tap Shift: back to the battle
            if (hidden && ticks - lastSneak <= 10 && lastSneak > hiddenAt) reveal();
            lastSneak = ticks;
        }
        wasSneaking = sneaking;
        if (screen != null && !screen.tick()) hide();
        player.connection.send(new ClientboundSetActionBarTextPacket(overlay()));
    }

    /** Shift off the seat: Cobblemon's minimise. Camera and seat go; the battle and its faded overlay stay. */
    void hide() {
        if (screen == null) return;
        screen.close();
        screen = null;
        hidden = true;
        hiddenAt = ticks;
    }

    void swing() { if (screen != null) screen.swing(); }

    /** Opens the screen on first use (from the ground: the seat doesn't fall). */
    private boolean cursor() {
        if (screen == null && !hidden && player.onGround()) {
            var foe = foe();
            screen = BattleScreen.open(player, foe == null ? player.getEyePosition().add(player.getLookAngle()) : foe.position().add(0, foe.getBbHeight() / 2, 0));
        }
        return screen != null;
    }

    private net.minecraft.world.entity.Entity foe() {
        PokemonBattle b = battle(player);
        if (b == null) return null;
        for (ActiveBattlePokemon ap : b.getActivePokemon())
            if (ap.getActor() != actor() && ap.getBattlePokemon() != null && ap.getBattlePokemon().getEntity() != null) return ap.getBattlePokemon().getEntity();
        return null;
    }

    private BattleScreen.Tile tile(int x, int top, int w, int h, char idle, java.util.function.Consumer<Ui.Overlay> label, Runnable action) {
        return new BattleScreen.Tile(x, top, w, h, idle, (char) (idle + 1), label, act(action));
    }

    /** A clicked tile empties the screen first; whatever it opens next fills it again. */
    private Runnable act(Runnable r) {
        return () -> {
            if (screen != null) screen.show(List.of());
            general = false;
            r.run();
        };
    }

    /** The menus as Cobblemon's tiles at its GUI positions (Paper's Battle). */
    private void screenMenu(int kind, List<Forms.Opt> opts) {
        List<BattleScreen.Tile> tiles = new ArrayList<>();
        int n = 0;
        for (Forms.Opt o : opts) {
            if (o.image().equals("back")) {
                tiles.add(new BattleScreen.Tile(9, 22, 29, 17, Ui.BACK, (char) (Ui.BACK + 1), x -> {}, act(o.action())));
                continue;
            }
            if (o.image().equals("none") || o.text().isBlank()) continue;
            String[] lines = o.text().split("\\n");
            String name = lines[0].strip(), second = lines.length > 1 ? lines[1].strip() : "";
            if (kind == 1) { // Fight / Switch / Bag / Run: 90x26, 3 px apart, from (12, H-85)
                int art = List.of("menu_fight", "menu_switch", "menu_bag", "menu_run").indexOf(o.image());
                int x = 12 + n % 2 * 93, top = 85 - n / 2 * 29;
                tiles.add(tile(x, top, 90, 26, (char) (Ui.MENU + 2 * Math.max(0, art)), ov -> ov.label(Ui.Overlay.BL, x + 6, top - 8, name), o.action()));
            } else if (kind == 2) { // BattleMoveSelection: 92x24, 13/5 px apart, from (20, H-84)
                String type = o.image().startsWith("move_") ? o.image().substring(5) : "normal";
                int t = Math.max(0, Ui.TYPES.indexOf(type)), x = 20 + n % 2 * 105, top = 84 - n / 2 * 29;
                char idle = (char) (Ui.MOVE + 2 * t);
                tiles.add(new BattleScreen.Tile(x, top, 92, 24, idle, (char) (idle + 1), ov -> {
                    ov.glyph(Ui.Overlay.BL, x - 9, top - 2, (char) (Ui.TYPE_BIG + t), 19);
                    ov.label(Ui.Overlay.BL, x + 17, top - 2, name);
                    ov.label(Ui.Overlay.BL, x + 75 - (int) (Ui.uniWidth(second, true) / 2), top - 14, second);
                }, act(o.action())));
            } else { // BattleSwitchPokemonSelection: 94x29, 4/2 px apart
                int x = 12 + n % 2 * 98, top = 192 - n / 2 * 31;
                String[] nl = name.split("  Lv\\.");
                tiles.add(tile(x, top, 94, 29, Ui.PARTY, ov -> ov.label(Ui.Overlay.BL, x + 5, top - 4, "Lv.")
                        .label(Ui.Overlay.BL, x + 18, top - 4, nl.length > 1 ? nl[1] : "").label(Ui.Overlay.BL, x + 5, top - 11, nl[0])
                        .small(Ui.Overlay.BL, x + 47, top - 22, second), o.action()));
            }
            n++;
        }
        screen.show(tiles);
        general = kind == 1;
    }

    /** Cobblemon's BattleOverlay tiles (top corners) and BattleMessagePane (bottom right), with the screen's tiles. */
    private Component overlay() {
        var o = new Ui.Overlay();
        o.fade = hidden ? 4 : 0;
        PokemonBattle b = battle(player);
        if (b != null) for (ActiveBattlePokemon ap : b.getActivePokemon()) {
            BattlePokemon bp = ap.getBattlePokemon();
            if (bp == null) continue;
            boolean own = ap.getActor() == actor();
            panel(o, own ? Ui.Overlay.TL : Ui.Overlay.TR, own ? 12 : -152, bp, own);
        }
        o.fade = hidden ? 6 : 0;
        o.glyph(Ui.Overlay.BR, -181, 85, '\ue103', 170);
        List<String> wrapped = new ArrayList<>();
        for (String l : log) wrapped.addAll(Ui.wrap(l, 146, Ui::width));
        List<String> last = wrapped.subList(Math.max(0, wrapped.size() - 4), wrapped.size());
        for (int i = 0; i < last.size(); i++) o.text(Ui.Overlay.BR, -171, 80 - 10 * i, last.get(i));
        o.fade = 0;
        if (screen != null) {
            screen.draw(o);
            if (general || screen.idle()) hint(o, "Press Shift to move around.", 0.75);
        } else if (hidden && choosing) hint(o, "You need to choose an action. Double-tap Shift.", 0.5 + 0.5 * Math.sin(Math.PI / 2 * ticks / 20.0));
        return o.build();
    }

    private static void hint(Ui.Overlay o, String s, double opacity) {
        if (opacity < 0.05) return;
        o.fade = Math.clamp(Math.round((1 - opacity) * 8), 0, 7);
        o.text(Ui.Overlay.TC, -Ui.width(s) / 2, 48, s);
        o.fade = 0;
    }

    /** One 140x40 tile: portrait ring, name, level, HP bar; yours with HP numbers, the foe's a percentage. */
    private void panel(Ui.Overlay o, int corner, int x, BattlePokemon bp, boolean own) {
        var mon = bp.getEffectedPokemon();
        o.glyph(corner, x + (own ? 5 : 107), 18, '\ue102', 29);
        var face = Ui.portrait(Summary.look(mon), !own);
        if (face != null) o.add(corner, x + (own ? 5 : 107), 18, face, 29);
        o.glyph(corner, x, 10, own ? '\ue100' : '\ue101', 141);
        int ib = x + (own ? 40 : 7);
        float ratio = Math.clamp((float) bp.getHealth() / Math.max(1, bp.getMaxHealth()), 0f, 1f);
        int bar = Math.round(ratio * 97);
        o.label(corner, ib, 17, name(mon)).label(corner, ib + 69, 17, "Lv.").label(corner, ib + 82, 17, String.valueOf(mon.getLevel()));
        if (bar > 0) o.glyph(corner, own ? ib - 2 : ib - 2 + 97 - bar, 29, (char) (0xE110 + bar), bar + 1);
        o.small(corner, ib + (own ? 39 : 44), 28, own ? bp.getHealth() + "/" + bp.getMaxHealth() : (int) Math.ceil(ratio * 100) + "%");
    }

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
        hide(); // Java's screen steps aside so a ball can be thrown
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
        if (javaScreen()) {
            formOpen = false;
            if (!hidden && cursor()) screenMenu(kind, opts);
            return;
        }
        if (!Clients.bedrock(player)) { // vanilla Java: a chest
            JavaScreens.battle(player, kind, opts, new ArrayList<>(last), () -> formOpen = false);
            formOpen = false; // ponytail: Esc on the chest isn't seen; /battle or a click on the Pokémon reopens it
            return;
        }
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
