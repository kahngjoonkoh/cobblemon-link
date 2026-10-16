package io.github.kahngjoonkoh.cobblemonlink;

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
    private final Mon wild;
    private final List<Integer> order = new ArrayList<>(); // battle slot -> party index
    private final Set<Integer> participants = new HashSet<>();
    private Socket socket;
    private PrintWriter out;
    private JsonObject request, state;
    private boolean over;
    private final List<String> turnLog = new ArrayList<>();
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
        player.sendMessage(Component.text("A wild " + wild.name(pl.dex) + " (Lv." + wild.level + ") appeared!", NamedTextColor.GOLD));

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
                    player.sendMessage(Component.text(text, NamedTextColor.YELLOW));
                    if (!text.startsWith("—")) turnLog.add(text);
                }
            }
            case "state" -> {
                state = msg;
                JsonArray p1 = state.getAsJsonArray("p1");
                for (int i = 0; i < p1.size(); i++)
                    if (p1.get(i).getAsJsonObject().get("active").getAsBoolean()) participants.add(order.get(i));
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
     * Vanilla dialog screen: drawn by the client, so no inventory traffic. Geyser shows it as a Bedrock form.
     * The body repeats this turn's log, since the screen covers chat.
     */
    void openMenu() {
        if (over || request == null) return;
        JsonArray side = request.getAsJsonObject("side").getAsJsonArray("pokemon");
        boolean forceSwitch = request.has("forceSwitch");
        JsonObject active = forceSwitch ? null : request.getAsJsonArray("active").get(0).getAsJsonObject();
        List<ActionButton> buttons = new ArrayList<>();
        if (active != null) {
            JsonArray moves = active.getAsJsonArray("moves");
            for (int i = 0; i < moves.size(); i++) {
                JsonObject mv = moves.get(i).getAsJsonObject();
                boolean disabled = mv.has("disabled") && mv.get("disabled").getAsBoolean();
                String pp = mv.has("pp") ? "PP " + mv.get("pp").getAsInt() + "/" + mv.get("maxpp").getAsInt() : "";
                String choice = "move " + (i + 1);
                buttons.add(button(Component.text(mv.get("move").getAsString(), disabled ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE),
                        pp, disabled ? null : () -> choose(choice)));
            }
            if (moves.size() % 2 == 1) buttons.add(button(Component.empty(), "", null)); // keeps the grid 2 wide
            buttons.add(button(Component.text("Throw Poké Ball", NamedTextColor.RED), "You have " + pl.countBalls(player), this::throwBall));
            buttons.add(button(Component.text("Run", NamedTextColor.GRAY), "", () -> {
                player.sendMessage(Component.text("Got away safely!", NamedTextColor.YELLOW));
                finish(null, false);
            }));
        }
        boolean trapped = active != null && active.has("trapped") && active.get("trapped").getAsBoolean();
        for (int i = 0; i < side.size() && !trapped; i++) {
            JsonObject p = side.get(i).getAsJsonObject();
            String cond = p.get("condition").getAsString();
            if (p.get("active").getAsBoolean() || cond.endsWith(" fnt")) continue;
            String choice = "switch " + (i + 1);
            buttons.add(button(Component.text("Switch: " + p.get("details").getAsString().split(",")[0], NamedTextColor.AQUA),
                    "HP " + cond, () -> choose(choice)));
        }
        Component log = Component.text(turnLog.isEmpty() ? "What will you do?" : String.join("\n", turnLog));
        turnLog.clear();
        List<DialogBody> body = new ArrayList<>();
        if (state != null) { // the dialog title covers the boss bars, so HP is repeated here
            JsonObject w = state.getAsJsonArray("p2").get(0).getAsJsonObject();
            body.add(DialogBody.plainMessage(hpLine("Wild " + wild.name(pl.dex) + " Lv." + wild.level, w, false), 300));
            JsonArray p1 = state.getAsJsonArray("p1");
            for (int i = 0; i < p1.size(); i++) {
                JsonObject p = p1.get(i).getAsJsonObject();
                Mon m = pl.store.get(player.getUniqueId()).party.get(order.get(i));
                if (p.get("active").getAsBoolean()) body.add(DialogBody.plainMessage(hpLine(m.name(pl.dex) + " Lv." + m.level, p, true), 300));
            }
        }
        body.add(DialogBody.plainMessage(log, 300));
        String title = forceSwitch ? "Choose your next Pokémon" : "Wild " + wild.name(pl.dex) + "  Lv." + wild.level;
        player.showDialog(Dialog.create(f -> f.empty()
                .base(DialogBase.builder(Component.text(title)).canCloseWithEscape(true)
                        .body(body).build())
                .type(DialogType.multiAction(buttons).columns(2).build())));
    }

    /** Clicking closes the screen, so a null (greyed-out) action just reopens it. */
    private ActionButton button(Component label, String tooltip, Runnable action) {
        Runnable run = action != null ? action : this::openMenu;
        var b = ActionButton.builder(label).width(150).action(DialogAction.customClick((resp, who) -> onMain(() -> { if (!over) run.run(); }),
                ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build()));
        if (!tooltip.isEmpty()) b.tooltip(Component.text(tooltip));
        return b.build();
    }

    /** Boss bars: HP for both sides, visible outside the menu. */
    private void updateBars() {
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

    /** "Pikachu Lv.12  ██████░░░░  30/35" — numbers only for your own side, like the main games. */
    private static Component hpLine(String name, JsonObject p, boolean numbers) {
        float r = ratio(p);
        int filled = Math.round(r * 20);
        NamedTextColor c = r > 0.5f ? NamedTextColor.GREEN : r > 0.2f ? NamedTextColor.GOLD : NamedTextColor.RED;
        String status = p.get("status").getAsString();
        return Component.text(name + "  ")
                .append(Component.text("|".repeat(filled), c))
                .append(Component.text("|".repeat(20 - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(numbers ? "  " + p.get("hp").getAsInt() + "/" + p.get("maxhp").getAsInt() : ""))
                .append(Component.text(status.isEmpty() ? "" : "  " + status.toUpperCase(), NamedTextColor.LIGHT_PURPLE));
    }

    private static float ratio(JsonObject p) {
        return Math.clamp((float) p.get("hp").getAsInt() / Math.max(1, p.get("maxhp").getAsInt()), 0f, 1f);
    }

    private void choose(String choice) {
        if (out != null) out.println("{\"choose\":\"" + choice + "\"}");
    }

    /** ponytail: a failed throw doesn't cost a turn (Showdown has no item action); the main games give the wild Pokémon a free move. */
    private void throwBall() {
        if (!pl.takeBall(player)) {
            player.sendMessage(Component.text("You have no Poké Balls.", NamedTextColor.RED));
            return;
        }
        int hp = wild.maxHp(pl.dex), max = hp;
        String status = "";
        if (state != null) {
            JsonObject w = state.getAsJsonArray("p2").get(0).getAsJsonObject();
            hp = w.get("hp").getAsInt();
            max = w.get("maxhp").getAsInt();
            status = w.get("status").getAsString();
        }
        double chance = Rules.catchChance(pl.dex.get(wild.species).catchRate, hp, max, status);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            wild.hp = hp;
            finish(null, true);
        } else {
            player.sendMessage(Component.text("Oh no! The Pokémon broke free!", NamedTextColor.YELLOW));
            turnLog.add("Oh no! The Pokémon broke free!");
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
        player.hideBossBar(wildBar);
        player.hideBossBar(ownBar);

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
                m.gainExp(pl.dex, Rules.expGain(pl.dex.get(wild.species).baseExp, wild.level, m.level),
                        s -> player.sendMessage(Component.text(s, NamedTextColor.AQUA)));
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
