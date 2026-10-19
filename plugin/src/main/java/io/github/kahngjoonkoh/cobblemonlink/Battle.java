package io.github.kahngjoonkoh.cobblemonlink;

import io.github.kahngjoonkoh.cobblemonlink.api.WildBattleEndEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * One wild battle. Showdown (sim/server.js) runs the rules; this class is the menu, the chat log and the bookkeeping.
 * Socket reads happen on an async thread; everything else runs on the main thread.
 */
final class Battle {
    private final CobblemonLink pl;
    final Player player;
    final Entity wildEntity;
    final Mon wild;
    private final List<Integer> order = new ArrayList<>(); // battle slot -> party index
    private final Set<Integer> participants = new HashSet<>();
    private Socket socket;
    private PrintWriter out;
    private JsonObject request, state;
    private boolean over;
    private final List<String> turnLog = new ArrayList<>();
    private final List<String> log = new ArrayList<>(); // Java overlay's battle log, wrapped
    private BukkitTask overlayTask;
    private Screen screen; // Java cursor screen; null on Bedrock, when the camera couldn't be set, or while hidden
    private boolean hidden, general; // Java: stood up with Shift (Cobblemon's minimised); the Fight/Switch/Bag/Run tiles are up
    private int hiddenAt, lastSneak = -100;
    private Entity ally; // the player's active Pokémon, out beside them
    private int allyIndex = -1;
    private final BossBar wildBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
    private final BossBar ownBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);

    Battle(CobblemonLink pl, Player player, Entity wildEntity, Mon wild) {
        this.pl = pl;
        this.player = player;
        this.wildEntity = wildEntity;
        this.wild = wild;
    }

    /** Returns false (with a message) if the player has nothing able to battle. */
    boolean start() {
        List<Mon> party = pl.store.get(player.getUniqueId()).party;
        for (int i = 0; i < party.size(); i++) if (!party.get(i).fainted(pl.dex)) order.add(i);
        if (order.isEmpty()) {
            player.sendMessage(Component.text("You have no Pokémon able to battle. Sleep in a bed to heal them.", NamedTextColor.RED));
            return false;
        }
        for (int i = 0; i < party.size(); i++) if (!order.contains(i)) order.add(i); // fainted ones last
        List<Map<String, Object>> team = order.stream().map(i -> party.get(i).set(pl.dex)).toList();
        List<Integer> hp = order.stream().map(i -> party.get(i).hp(pl.dex)).toList();
        JsonObject init = new JsonObject();
        init.add("p1", Store.GSON.toJsonTree(team));
        init.add("p2", Store.GSON.toJsonTree(List.of(wild.set(pl.dex))));
        init.add("hp1", Store.GSON.toJsonTree(hp));
        wild.hp = -1;
        Spawner.freeze(wildEntity, true);
        Spawner.face(wildEntity, player.getLocation()); // Cobblemon turns the opponent toward the player
        String appeared = "A wild " + wild.name(pl.dex) + " (Lv." + wild.level + ") appeared!";
        log.addAll(Ui.wrap(appeared, 146)); // Cobblemon keeps battle text out of chat

        String host = pl.getConfig().getString("sim.host");
        int port = pl.getConfig().getInt("sim.port");
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 3000);
                socket = s;
                out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
                out.println(init);
                var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                for (String line; (line = in.readLine()) != null; ) {
                    JsonObject msg = JsonParser.parseString(line).getAsJsonObject();
                    onMain(() -> handle(msg));
                }
            } catch (IOException e) {
                if (!over) onMain(() -> player.sendMessage(
                        Component.text("Battle server unavailable: " + e.getMessage(), NamedTextColor.RED)));
            } finally {
                onMain(() -> finish(null, false));
            }
        });
        return true;
    }

    /** Plugin shutdown closes sockets from the main thread; scheduling after disable would throw. */
    private void onMain(Runnable r) {
        if (pl.isEnabled()) Bukkit.getScheduler().runTask(pl, r);
    }

    private void handle(JsonObject msg) {
        if (over) return;
        switch (msg.get("type").getAsString()) {
            case "request" -> {
                request = msg.getAsJsonObject("request");
                if (!request.has("wait")) openMenu();
            }
            case "log" -> {
                for (JsonElement l : msg.getAsJsonArray("lines")) {
                    String text = narrate(l.getAsString());
                    if (text == null) continue;
                    if (text.startsWith("—")) continue;
                    turnLog.add(text);
                    log.addAll(Ui.wrap(text, 146));
                }
            }
            case "state" -> {
                state = msg;
                JsonArray p1 = state.getAsJsonArray("p1");
                for (int i = 0; i < p1.size(); i++)
                    if (p1.get(i).getAsJsonObject().get("active").getAsBoolean()) {
                        participants.add(order.get(i));
                        JsonObject p = p1.get(i).getAsJsonObject();
                        sendOut(p.get("hp").getAsInt() > 0 ? order.get(i) : -1);
                    }
                updateBars();
            }
            case "end" -> finish(msg.get("winner").isJsonNull() ? null : msg.get("winner").getAsString(), false);
            case "error" -> {
                player.sendMessage(Component.text(msg.get("message").getAsString(), NamedTextColor.RED));
                if (request != null && !request.has("wait")) openMenu();
            }
            default -> {}
        }
    }

    /**
     * Cobblemon's flow: Fight / Switch / Bag / Run, then the moves or the party. Vanilla dialog screens, drawn by the
     * client; Java players see Cobblemon's tile art from the UI pack, Bedrock players (Geyser turns dialogs into forms) plain text.
     */
    void openMenu() {
        if (over || request == null || hidden) return; // hidden: the overlay prompts instead, like Cobblemon
        if (request.has("forceSwitch")) {
            openSwitch(false);
            return;
        }
        JsonObject active = request.getAsJsonArray("active").get(0).getAsJsonObject();
        boolean trapped = active.has("trapped") && active.get("trapped").getAsBoolean();
        // BattleGeneralActionSelection: Switch only when not trapped; Catch hides the battle so a ball can be thrown
        List<Integer> art = new ArrayList<>(List.of(0, 1, 2, 3)); // battle_menu_fight/switch/bag/run
        List<Opt> opts = new ArrayList<>(List.of(new Opt("Fight", "menu_fight", this::openFight),
                new Opt("Switch", "menu_switch", () -> openSwitch(true)),
                new Opt("Catch", "menu_bag", this::capture), new Opt("Run", "menu_run", this::run)));
        if (trapped) {
            art.remove(1);
            opts.remove(1);
        }
        if (cursor()) {
            List<Screen.Tile> tiles = new ArrayList<>();
            for (int i = 0; i < opts.size(); i++) { // 90x26 tiles, 3 px apart, from (12, H-85)
                int x = 12 + i % 2 * 93, top = 85 - i / 2 * 29;
                String name = opts.get(i).text();
                tiles.add(tile(x, top, 90, 26, (char) (Ui.MENU + 2 * art.get(i)), o -> o.label(Ui.Overlay.BL, x + 6, top - 8, name), opts.get(i).action()));
            }
            screen.show(tiles);
            general = true;
            turnLog.clear();
            return;
        }
        if (form(1, opts)) {
            turnLog.clear();
            return;
        }
        List<ActionButton> buttons = new ArrayList<>(List.of(
                menuButton(0, "Fight", "", this::openFight),
                menuButton(1, "Switch", trapped ? "You can't switch out!" : "", trapped ? null : () -> openSwitch(true)),
                menuButton(2, "Catch", "You have " + pl.countBalls(player) + " Poké Balls", this::capture),
                menuButton(3, "Run", "", this::run)));
        List<DialogBody> body = info();
        List<String> log = new ArrayList<>(turnLog.isEmpty() ? List.of("What will you do?") : turnLog);
        while (log.size() < 4) log.add(" "); // fixed height, so the buttons don't move between turns
        body.add(DialogBody.plainMessage(Component.text(String.join("\n", log)), 300));
        turnLog.clear();
        show("Wild " + wild.name(pl.dex) + "  Lv." + wild.level, body, buttons);
    }

    private void run() {
        player.sendMessage(Component.text("Got away safely!", NamedTextColor.YELLOW));
        finish(null, false);
    }

    private void openFight() {
        JsonArray moves = request.getAsJsonArray("active").get(0).getAsJsonObject().getAsJsonArray("moves");
        if (screen != null) {
            List<Screen.Tile> tiles = new ArrayList<>();
            for (int i = 0; i < moves.size(); i++) { // BattleMoveSelection: 92x24 tiles from (20, H-84), 13/5 px apart
                JsonObject mv = moves.get(i).getAsJsonObject();
                boolean disabled = mv.has("disabled") && mv.get("disabled").getAsBoolean();
                String name = mv.get("move").getAsString(), type = mv.has("type") ? mv.get("type").getAsString() : "normal";
                String pp = mv.has("pp") ? mv.get("pp").getAsInt() + "/" + mv.get("maxpp").getAsInt() : "";
                int x = 20 + i % 2 * 105, top = 84 - i / 2 * 29, t = Math.max(0, Ui.TYPES.indexOf(type));
                String choice = "move " + (i + 1);
                char idle = (char) (Ui.MOVE + 2 * t);
                tiles.add(new Screen.Tile(x, top, 92, 24, idle, disabled ? idle : (char) (idle + 1), o -> {
                    o.glyph(Ui.Overlay.BL, x - 9, top - 2, (char) (Ui.TYPE_BIG + t), 19);
                    o.label(Ui.Overlay.BL, x + 17, top - 2, name);
                    o.label(Ui.Overlay.BL, x + 75 - (int) (Ui.uniWidth(pp, true) / 2), top - 14, pp);
                }, disabled ? () -> {} : act(() -> choose(choice))));
            }
            tiles.add(backTile());
            screen.show(tiles);
            return;
        }
        if (Mod.form(player)) {
            List<Opt> opts = new ArrayList<>();
            for (int i = 0; i < 4; i++) { // always 4, so Back is the 5th button where the pack expects it
                if (i >= moves.size()) {
                    opts.add(new Opt(" ", "none", this::openFight));
                    continue;
                }
                JsonObject mv = moves.get(i).getAsJsonObject();
                boolean disabled = mv.has("disabled") && mv.get("disabled").getAsBoolean();
                String type = mv.has("type") ? mv.get("type").getAsString() : "normal";
                String pp = mv.has("pp") ? mv.get("pp").getAsInt() + "/" + mv.get("maxpp").getAsInt() : "";
                String choice = "move " + (i + 1);
                opts.add(new Opt(mv.get("move").getAsString() + "\n          " + pp, "move_" + (Ui.TYPES.contains(type) ? type : "normal"),
                        disabled ? this::openFight : () -> choose(choice)));
            }
            opts.add(new Opt(" ", "back", this::openMenu));
            form(2, opts);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            JsonObject mv = moves.get(i).getAsJsonObject();
            boolean disabled = mv.has("disabled") && mv.get("disabled").getAsBoolean();
            String name = mv.get("move").getAsString(), type = mv.has("type") ? mv.get("type").getAsString() : "normal";
            String pp = mv.has("pp") ? mv.get("pp").getAsInt() + "/" + mv.get("maxpp").getAsInt() : "";
            String choice = "move " + (i + 1);
            Component label = Ui.bedrock(player) ? Component.text(name + "  (" + pp + ")") : Ui.moveTile(name, type, pp, disabled);
            buttons.add(button(label, Ui.MOVE_W + 4, pl.dex.label("type", type) + "  PP " + pp, disabled ? this::openFight : () -> choose(choice)));
        }
        buttons.add(backButton());
        show("Fight", info(), buttons);
    }

    /** cancellable = false when a fainted Pokémon must be replaced. */
    private void openSwitch(boolean cancellable) {
        JsonArray side = request.getAsJsonObject("side").getAsJsonArray("pokemon");
        if (cursor()) {
            // BattleSwitchPokemonSelection: 94x29 tiles, 4/2 px apart. ponytail: Cobblemon centres them; the cursor can
            // only find tiles measured from the left edge (the server doesn't know the screen width), so they start at x=12.
            List<Screen.Tile> tiles = new ArrayList<>();
            int n = 0;
            for (int i = 0; i < side.size(); i++) {
                JsonObject p = side.get(i).getAsJsonObject();
                String cond = p.get("condition").getAsString();
                if (p.get("active").getAsBoolean() || cond.endsWith(" fnt")) continue;
                String choice = "switch " + (i + 1);
                Mon m = pl.store.get(player.getUniqueId()).party.get(order.get(p.get("slot").getAsInt()));
                int x = 12 + n % 2 * 98, top = 192 - n / 2 * 31;
                n++;
                tiles.add(tile(x, top, 94, 29, Ui.PARTY, o -> o.label(Ui.Overlay.BL, x + 5, top - 4, "Lv.")
                        .label(Ui.Overlay.BL, x + 18, top - 4, String.valueOf(m.level)).label(Ui.Overlay.BL, x + 5, top - 11, m.name(pl.dex))
                        .small(Ui.Overlay.BL, x + 47, top - 22, cond.split(" ")[0]), () -> choose(choice)));
            }
            if (cancellable) tiles.add(backTile());
            screen.show(tiles);
            return;
        }
        if (Mod.form(player)) {
            List<Opt> opts = new ArrayList<>();
            for (int i = 0; i < side.size(); i++) {
                JsonObject p = side.get(i).getAsJsonObject();
                String cond = p.get("condition").getAsString();
                if (p.get("active").getAsBoolean() || cond.endsWith(" fnt")) continue;
                String choice = "switch " + (i + 1);
                Mon m = pl.store.get(player.getUniqueId()).party.get(order.get(p.get("slot").getAsInt()));
                opts.add(new Opt(m.name(pl.dex) + "  Lv." + m.level + "\n" + cond.split(" ")[0], "party", () -> choose(choice)));
            }
            while (opts.size() < 5) opts.add(new Opt(" ", "none", () -> openSwitch(cancellable))); // Back is the 6th button
            if (cancellable) opts.add(new Opt(" ", "back", this::openMenu));
            form(3, opts);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < side.size(); i++) {
            JsonObject p = side.get(i).getAsJsonObject();
            String cond = p.get("condition").getAsString();
            if (p.get("active").getAsBoolean() || cond.endsWith(" fnt")) continue;
            String choice = "switch " + (i + 1);
            Mon m = pl.store.get(player.getUniqueId()).party.get(order.get(p.get("slot").getAsInt()));
            String name = m.name(pl.dex) + " Lv." + m.level, hp = cond.split(" ")[0];
            Component label = Ui.bedrock(player) ? Component.text(name + "  (" + hp + ")")
                    : Ui.partyTile(pl.dex.of(m).types, name, hp);
            buttons.add(button(label, Ui.PARTY_W + 4, "HP " + hp, () -> choose(choice)));
        }
        List<DialogBody> body = info();
        if (cancellable) buttons.add(backButton());
        else if (!turnLog.isEmpty()) { // what fainted, since the screen covers chat
            body.add(DialogBody.plainMessage(Component.text(String.join("\n", turnLog)), 300));
            turnLog.clear();
        }
        show(cancellable ? "Switch" : "Choose your next Pokémon", body, buttons);
    }

    record Opt(String text, String image, Runnable action) {}

    /** Bedrock: a Floodgate form the Geyser-sent pack (tools/build_bedrock_ui.py) draws as Cobblemon's tiles. */
    private boolean form(int kind, List<Opt> opts) {
        if (!Mod.form(player)) return false;
        List<String> last = log.subList(Math.max(0, log.size() - 4), log.size());
        Mod.send(player, "Battle" + Forms.MARK + kind, String.join("\n", last.isEmpty() ? List.of("What will you do?") : last),
                opts, i -> onMain(() -> {
                    if (over) return;
                    formOpen = false; // any answer closes it; the action may send the next one
                    if (i >= 0) opts.get(i).action().run();
                }));
        formOpen = true;
        return true;
    }

    /** Java players get the cursor screen (opened on first use); false on Bedrock or if it couldn't open. */
    private boolean cursor() {
        if (screen == null && !Ui.bedrock(player) && !Mod.has(player) && !triedScreen) {
            triedScreen = true;
            screen = Screen.open(pl, player, wildEntity.getLocation().add(0, wildEntity.getHeight() / 2, 0));
        }
        return screen != null;
    }

    private boolean triedScreen, formOpen; // formOpen: Bedrock, a battle form is on screen

    /** A clicked tile empties the screen first; whatever it opens next fills it again. */
    private Runnable act(Runnable r) {
        return () -> {
            screen.show(List.of());
            general = false;
            r.run();
        };
    }

    private Screen.Tile tile(int x, int top, int w, int h, char idle, java.util.function.Consumer<Ui.Overlay> label, Runnable action) {
        return new Screen.Tile(x, top, w, h, idle, (char) (idle + 1), label, act(action));
    }

    /** BattleBackButton: 58x34 art at half size, at (9, H-22). */
    private Screen.Tile backTile() {
        return new Screen.Tile(9, 22, 29, 17, Ui.BACK, (char) (Ui.BACK + 1), o -> {}, act(this::openMenu));
    }

    /** Swing from the player (left click), routed by the plugin; true if the cursor screen took it. */
    boolean swing() {
        if (screen == null) return false;
        screen.swing();
        return true;
    }

    Screen screen() { return screen; }

    /** Shift off the seat: Cobblemon's minimise. The camera and seat go, the battle and its faded overlay stay. */
    void hide() {
        if (over || screen == null) return;
        screen.close();
        screen = null;
        triedScreen = false;
        hidden = true;
        hiddenAt = Bukkit.getCurrentTick();
    }

    /**
     * Back to the battle: the Pokémon tapped, Shift double-tapped or /battle. Java only from the ground: the seat
     * doesn't fall, so it would hold a falling player in mid-air (and maybe forgive the fall).
     */
    void reveal() {
        if (over) return;
        if (!hidden) {
            if (screen == null) openMenu(); // Bedrock form or Java dialog: send it again
            return;
        }
        if (!player.isOnGround()) return;
        hidden = false;
        if (request != null && !request.has("wait")) openMenu();
        else if (cursor()) screen.show(List.of());
    }

    /**
     * Double-tap Shift: two presses within half a second, both after the one that hid it (Bedrock's sneak button too).
     * Java reopens on the release: a seat taken with Shift still down would throw the player straight off again.
     */
    void sneak(boolean down) {
        if (!down) {
            if (armed) reveal();
            armed = false;
            return;
        }
        int now = Bukkit.getCurrentTick();
        armed = now - lastSneak <= 10 && lastSneak > hiddenAt;
        lastSneak = now;
        if (armed && Ui.bedrock(player)) reveal();
    }

    private boolean armed;

    /**
     * Java: the overlay carries HP and the log, so the dialog is only the tiles, pushed down by an empty body.
     * ponytail: the client puts a dialog's body 63 px from the top; 90 px of blank lines lands the tiles where Cobblemon
     * has them (H-85) on a 240-270 px tall GUI (auto scale at 720p/1080p). Vanilla can't anchor them to the bottom.
     */
    private void show(String title, List<DialogBody> body, List<ActionButton> buttons) {
        boolean java = !Ui.bedrock(player);
        int rows = (buttons.size() + 1) / 2; // each row past two moves the tiles up a row (22 px), clear of the hotbar
        List<DialogBody> b = java ? List.of(DialogBody.plainMessage(Component.text("\n".repeat(9 - (rows - 2) * 22 / 9)), 10)) : body;
        player.showDialog(Dialog.create(f -> f.empty()
                .base(DialogBase.builder(java ? Component.empty() : Component.text(title)).canCloseWithEscape(true).body(b).build())
                .type(DialogType.multiAction(buttons).columns(2).build())));
    }

    /** HP of both sides; the dialog title covers the boss bars, so it's repeated here. */
    private List<DialogBody> info() {
        List<DialogBody> body = new ArrayList<>();
        if (state == null) return body;
        JsonObject w = state.getAsJsonArray("p2").get(0).getAsJsonObject();
        body.add(DialogBody.plainMessage(infoLine(wild, w, false), 300));
        JsonArray p1 = state.getAsJsonArray("p1");
        for (int i = 0; i < p1.size(); i++) {
            JsonObject p = p1.get(i).getAsJsonObject();
            if (p.get("active").getAsBoolean())
                body.add(DialogBody.plainMessage(infoLine(pl.store.get(player.getUniqueId()).party.get(order.get(i)), p, true), 300));
        }
        return body;
    }

    /** Bedrock's form text (Java gets the overlay). Numbers only for your own side, like the main games. */
    private Component infoLine(Mon m, JsonObject p, boolean own) {
        String name = (own ? "" : "Wild ") + m.name(pl.dex) + " Lv." + m.level;
        String hp = own ? p.get("hp").getAsInt() + "/" + p.get("maxhp").getAsInt() : "";
        String status = p.get("status").getAsString();
        int filled = Math.round(ratio(p) * 20);
        return Component.text(name + "  ").append(Component.text("|".repeat(filled), NamedTextColor.GREEN))
                .append(Component.text("|".repeat(20 - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text((hp.isEmpty() ? "" : "  " + hp) + (status.isEmpty() ? "" : "  " + status.toUpperCase())));
    }

    private ActionButton menuButton(int tile, String label, String tooltip, Runnable action) {
        Component c = Ui.bedrock(player) ? Component.text(label) : Ui.menuTile(tile, label);
        return button(c, Ui.MENU_W + 4, tooltip, action != null ? action : this::openMenu);
    }

    private ActionButton backButton() {
        return Ui.bedrock(player) ? button(Component.text("Back"), 100, "", this::openMenu) : button(Ui.back(), 33, "", this::openMenu);
    }

    /** Clicking closes the screen; actions that don't end the turn reopen a screen themselves. */
    private ActionButton button(Component label, int width, String tooltip, Runnable action) {
        var b = ActionButton.builder(label).width(width).action(DialogAction.customClick((resp, who) -> onMain(() -> { if (!over) action.run(); }),
                ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()));
        if (!tooltip.isEmpty()) b.tooltip(Component.text(tooltip));
        return b.build();
    }

    /** HP for both sides, visible outside the menu: Cobblemon's overlay on Java, boss bars on Bedrock. */
    private void updateBars() {
        if (!Ui.bedrock(player)) {
            if (overlayTask == null) // every tick: the cursor follows the aim (the action bar would also fade after 3 s)
                overlayTask = Bukkit.getScheduler().runTaskTimer(pl, () -> {
                    if (screen != null) screen.tick();
                    player.sendActionBar(overlay());
                }, 0, 1);
            player.sendActionBar(overlay());
            return;
        }
        if (overlayTask == null) // BattleOverlay actions_label, while the form is closed
            overlayTask = Bukkit.getScheduler().runTaskTimer(pl, () -> {
                if (!formOpen && request != null && !request.has("wait"))
                    player.sendActionBar(Component.text("You need to choose an action. Tap the Pokémon."));
            }, 20, 20);
        JsonObject w = state.getAsJsonArray("p2").get(0).getAsJsonObject();
        String status = w.get("status").getAsString();
        wildBar.name(Component.text("Wild " + wild.name(pl.dex) + "  Lv." + wild.level + (status.isEmpty() ? "" : "  " + status.toUpperCase())));
        wildBar.progress(ratio(w));
        JsonArray p1 = state.getAsJsonArray("p1");
        for (int i = 0; i < p1.size(); i++) {
            JsonObject p = p1.get(i).getAsJsonObject();
            if (!p.get("active").getAsBoolean()) continue;
            Mon m = pl.store.get(player.getUniqueId()).party.get(order.get(i));
            ownBar.name(Component.text(m.name(pl.dex) + "  Lv." + m.level + "   " + p.get("hp").getAsInt() + "/" + p.get("maxhp").getAsInt() + " HP"));
            ownBar.progress(ratio(p));
        }
        player.showBossBar(wildBar);
        player.showBossBar(ownBar);
    }

    /** Cobblemon's BattleOverlay tiles (top corners) and BattleMessagePane (bottom right), at its GUI-code positions. */
    private Component overlay() {
        var o = new Ui.Overlay();
        o.fade = hidden ? 4 : 0; // BattleOverlay MIN_OPACITY 0.5 while minimised
        JsonArray p1 = state.getAsJsonArray("p1");
        for (int i = 0; i < p1.size(); i++)
            if (p1.get(i).getAsJsonObject().get("active").getAsBoolean())
                panel(o, Ui.Overlay.TL, 12, pl.store.get(player.getUniqueId()).party.get(order.get(i)), p1.get(i).getAsJsonObject(), true);
        panel(o, Ui.Overlay.TR, -152, wild, state.getAsJsonArray("p2").get(0).getAsJsonObject(), false);
        o.fade = hidden ? 6 : 0; // BattleMessagePane at 0.3 with no screen open
        o.glyph(Ui.Overlay.BR, -181, 85, '\ue103', 170);
        List<String> last = log.subList(Math.max(0, log.size() - 4), log.size());
        for (int i = 0; i < last.size(); i++) o.text(Ui.Overlay.BR, -171, 80 - 10 * i, last.get(i));
        o.fade = 0;
        if (screen != null) {
            screen.draw(o);
            if (general || screen.idle()) hint(o, "Press Shift to move around.", 0.75); // BattleGUI hide_label
        } else if (hidden && request != null && !request.has("wait")) { // BattleOverlay actions_label, pulsing over 4 s
            hint(o, "You need to choose an action. Double-tap Shift.", 0.5 + 0.5 * Math.sin(Math.PI / 2 * Bukkit.getCurrentTick() / 20.0));
        }
        return o.build();
    }

    /** Cobblemon's centred prompt at a fifth of the screen's height. ponytail: fixed 48 px (a 240 px tall GUI). */
    private static void hint(Ui.Overlay o, String s, double opacity) {
        if (opacity < 0.05) return; // drawScaledText skips it too
        o.fade = Math.clamp(Math.round((1 - opacity) * 8), 0, 7);
        o.text(Ui.Overlay.TC, -Ui.width(s) / 2, 48, s);
        o.fade = 0;
    }

    /** One 140x40 tile: portrait ring, name, level, HP bar; yours shows HP numbers, the wild one a percentage. */
    private void panel(Ui.Overlay o, int corner, int x, Mon m, JsonObject p, boolean own) {
        o.glyph(corner, x + (own ? 5 : 107), 18, '\ue102', 29);
        var face = Ui.portrait(m.model(pl.dex), !own); // BattleOverlay: the portrait over the ring, under the tile
        if (face != null) o.add(corner, x + (own ? 5 : 107), 18, face, 29);
        o.glyph(corner, x, 10, own ? '\ue100' : '\ue101', 141);
        int ib = x + (own ? 40 : 7), bar = Math.round(ratio(p) * 97);
        o.label(corner, ib, 17, m.name(pl.dex)).label(corner, ib + 69, 17, "Lv.").label(corner, ib + 82, 17, String.valueOf(m.level));
        if (bar > 0) o.glyph(corner, own ? ib - 2 : ib - 2 + 97 - bar, 29, (char) (0xE110 + bar), bar + 1);
        o.small(corner, ib + (own ? 39 : 44), 28,
                own ? p.get("hp").getAsInt() + "/" + p.get("maxhp").getAsInt() : (int) Math.ceil(ratio(p) * 100) + "%");
    }

    private static float ratio(JsonObject p) {
        return Math.clamp((float) p.get("hp").getAsInt() / Math.max(1, p.get("maxhp").getAsInt()), 0f, 1f);
    }

    private void choose(String choice) {
        if (out != null) out.println("{\"choose\":\"" + choice + "\"}");
    }

    /**
     * Cobblemon sends the active Pokémon out next to the player, facing the opponent: here 2.5 blocks toward it and
     * 1.5 to the right, inside the battle camera's view. -1 (fainted) recalls it. ponytail: no throw animation, no ground search (it may fall).
     */
    private void sendOut(int partyIndex) {
        if (partyIndex == allyIndex) return;
        if (ally != null) ally.remove();
        ally = null;
        allyIndex = partyIndex;
        if (partyIndex < 0) return;
        var pos = player.getLocation();
        var toWild = wildEntity.getLocation().toVector().subtract(pos.toVector()).setY(0);
        if (toWild.lengthSquared() < 0.01) toWild = pos.getDirection().setY(0);
        toWild.normalize();
        var right = new org.bukkit.util.Vector(-toWild.getZ(), 0, toWild.getX());
        var at = pos.clone().add(toWild.clone().multiply(2.5)).add(right.multiply(1.5));
        at.setDirection(wildEntity.getLocation().toVector().subtract(at.toVector()));
        at.setPitch(0);
        ally = pl.spawner.sendOut(at, pl.store.get(player.getUniqueId()).party.get(partyIndex).model(pl.dex));
    }

    /** Cobblemon's Catch: the battle steps aside and the player throws a real Poké Ball (ballHit). */
    private void capture() {
        if (screen != null) hide();
        player.sendMessage(Component.text("Throw a Poké Ball at your opponent to capture it.")); // throw_pokeball_prompt
    }

    /**
     * A thrown Poké Ball hit this battle's wild Pokémon: the main-series formula with its battle HP and status.
     * ponytail: a failed throw doesn't cost a turn (Showdown has no item action); the main games give the wild Pokémon a free move.
     */
    void ballHit() {
        if (over) return;
        int hp = wild.maxHp(pl.dex), max = hp;
        String status = "";
        if (state != null) {
            JsonObject w = state.getAsJsonArray("p2").get(0).getAsJsonObject();
            hp = w.get("hp").getAsInt();
            max = w.get("maxhp").getAsInt();
            status = w.get("status").getAsString();
        }
        double chance = Rules.catchChance(pl.dex.of(wild).catchRate, hp, max, status);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            wild.hp = hp;
            finish(null, true);
        } else {
            turnLog.add("Oh no! The Pokémon broke free!");
            log.addAll(Ui.wrap("Oh no! The Pokémon broke free!", 146));
            openMenu();
        }
    }

    /** Ends the battle exactly once: writes HP back, awards EXP, releases or removes the wild Pokémon. */
    void finish(String winner, boolean caught) {
        if (over) return;
        over = true;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        player.closeDialog();
        if (screen != null) screen.close();
        player.hideBossBar(wildBar);
        player.hideBossBar(ownBar);
        sendOut(-1);
        if (overlayTask != null) {
            overlayTask.cancel();
            player.sendActionBar(Component.empty());
        }

        List<Mon> party = pl.store.get(player.getUniqueId()).party;
        if (state != null) {
            JsonArray p1 = state.getAsJsonArray("p1");
            for (int i = 0; i < p1.size() && i < order.size(); i++) {
                Mon m = party.get(order.get(i));
                int hp = p1.get(i).getAsJsonObject().get("hp").getAsInt();
                m.hp = hp >= m.maxHp(pl.dex) ? -1 : hp;
            }
        }
        if ("p1".equals(winner)) {
            for (int idx : participants) {
                Mon m = party.get(idx);
                if (m.fainted(pl.dex)) continue;
                int level = m.level, moves = m.moves.size() + (m.learnable == null ? 0 : m.learnable.size());
                String evolution = m.evolution;
                long exp = Rules.expGain(pl.dex.of(wild).baseExp, wild.level, m.level);
                boolean panel = pl.panel.on(player); // as Cobblemon: the party overlay shows it, not chat
                m.gainExp(pl.dex, exp, panel ? s -> {} : s -> player.sendMessage(Component.text(s, NamedTextColor.AQUA)));
                if (panel) pl.panel.gain(player, m, level, exp, m.moves.size() + (m.learnable == null ? 0 : m.learnable.size()) - moves,
                        m.evolution != null && !m.evolution.equals(evolution) ? 1 : 0);
            }
            wildEntity.remove();
        } else if (caught) {
            boolean toParty = pl.store.give(player.getUniqueId(), wild);
            player.sendMessage(Component.text("Gotcha! " + wild.name(pl.dex) + " was caught!"
                    + (toParty ? "" : " It was sent to the PC."), NamedTextColor.GREEN));
            wildEntity.remove();
        } else {
            if ("p2".equals(winner))
                player.sendMessage(Component.text("All your Pokémon fainted! Sleep in a bed to heal them.", NamedTextColor.RED));
            Spawner.freeze(wildEntity, false);
        }
        pl.store.save(player.getUniqueId());
        pl.battles.remove(player.getUniqueId());
        var outcome = "p1".equals(winner) ? WildBattleEndEvent.Outcome.WON : caught ? WildBattleEndEvent.Outcome.CAUGHT
                : "p2".equals(winner) ? WildBattleEndEvent.Outcome.LOST : WildBattleEndEvent.Outcome.FLED;
        if (pl.isEnabled()) new WildBattleEndEvent(player, wild.species, wild.level, wild.shiny, outcome).callEvent();
        if (pl.isEnabled()) pl.onMain(() -> MoveLearn.next(pl, player));
    }

    /** Showdown protocol line -> chat text, or null to skip (HP is shown in the menu instead). */
    static String narrate(String line) {
        String[] p = line.split("\\|");
        if (p.length < 2) return null;
        return switch (p[1]) {
            case "move" -> who(p[2]) + " used " + p[3] + "!";
            case "switch" -> p[2].startsWith("p1") ? "Go! " + p[2].substring(5) + "!" : null;
            case "faint" -> who(p[2]) + " fainted!";
            case "-supereffective" -> "It's super effective!";
            case "-resisted" -> "It's not very effective...";
            case "-immune" -> "It doesn't affect " + who(p[2]) + "...";
            case "-crit" -> "A critical hit!";
            case "-miss" -> who(p[2]) + "'s attack missed!";
            case "-fail" -> "But it failed!";
            case "-status" -> who(p[2]) + " is now " + switch (p[3]) {
                case "par" -> "paralyzed"; case "brn" -> "burned"; case "slp" -> "asleep";
                case "frz" -> "frozen"; case "psn", "tox" -> "poisoned"; default -> p[3]; } + "!";
            case "cant" -> who(p[2]) + " can't move!";
            case "-boost" -> who(p[2]) + "'s " + STAT_NAMES.getOrDefault(p[3], p[3]) + " rose!";
            case "-unboost" -> who(p[2]) + "'s " + STAT_NAMES.getOrDefault(p[3], p[3]) + " fell!";
            case "turn" -> "— Turn " + p[2] + " —";
            default -> null;
        };
    }

    private static final Map<String, String> STAT_NAMES = Map.of("atk", "Attack", "def", "Defense", "spa", "Sp. Atk",
            "spd", "Sp. Def", "spe", "Speed", "accuracy", "accuracy", "evasion", "evasiveness");

    private static String who(String ident) {
        String name = ident.length() > 5 ? ident.substring(5) : ident;
        return (ident.startsWith("p1") ? "Your " : "The wild ") + name;
    }
}
