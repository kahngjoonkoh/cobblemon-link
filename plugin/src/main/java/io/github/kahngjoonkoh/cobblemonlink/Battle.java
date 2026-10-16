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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
                    if (text != null) player.sendMessage(Component.text(text, NamedTextColor.YELLOW));
                }
            }
            case "state" -> {
                state = msg;
                JsonArray p1 = state.getAsJsonArray("p1");
                for (int i = 0; i < p1.size(); i++)
                    if (p1.get(i).getAsJsonObject().get("active").getAsBoolean()) participants.add(order.get(i));
            }
            case "end" -> finish(msg.get("winner").isJsonNull() ? null : msg.get("winner").getAsString(), false);
            case "error" -> {
                player.sendMessage(Component.text(msg.get("message").getAsString(), NamedTextColor.RED));
                if (request != null && !request.has("wait")) openMenu();
            }
            default -> {}
        }
    }

    void openMenu() {
        if (over || request == null) return;
        Menu m = new Menu(27, "Wild " + wild.name(pl.dex) + "  Lv." + wild.level);
        JsonArray side = request.getAsJsonObject("side").getAsJsonArray("pokemon");
        boolean forceSwitch = request.has("forceSwitch");
        JsonObject active = forceSwitch ? null : request.getAsJsonArray("active").get(0).getAsJsonObject();

        for (JsonElement el : side) {
            JsonObject p = el.getAsJsonObject();
            if (p.get("active").getAsBoolean())
                m.set(0, Menu.icon(Material.LIME_DYE, "Your " + p.get("details").getAsString(), List.of("HP " + p.get("condition").getAsString())), null);
        }
        if (state != null) {
            JsonObject w = state.getAsJsonArray("p2").get(0).getAsJsonObject();
            String status = w.get("status").getAsString();
            m.set(8, Menu.icon(Material.RED_DYE, "Wild " + wild.name(pl.dex) + "  Lv." + wild.level,
                    List.of("HP " + Math.round(100.0 * w.get("hp").getAsInt() / w.get("maxhp").getAsInt()) + "%"
                            + (status.isEmpty() ? "" : "  " + status.toUpperCase()))), null);
        }
        if (active != null) {
            JsonArray moves = active.getAsJsonArray("moves");
            for (int i = 0; i < moves.size(); i++) {
                JsonObject mv = moves.get(i).getAsJsonObject();
                boolean disabled = mv.has("disabled") && mv.get("disabled").getAsBoolean();
                String pp = mv.has("pp") ? "PP " + mv.get("pp").getAsInt() + "/" + mv.get("maxpp").getAsInt() : "";
                String choice = "move " + (i + 1);
                m.set(10 + i, Menu.icon(disabled ? Material.GRAY_DYE : Material.PAPER, mv.get("move").getAsString(),
                        List.of(pp)), disabled ? null : e -> choose(choice));
            }
            int balls = pl.countBalls(player);
            m.set(15, Menu.icon(Material.SNOWBALL, "Throw Poké Ball", List.of("You have " + balls)), e -> throwBall());
            m.set(16, Menu.icon(Material.FEATHER, "Run", List.of()), e -> {
                player.sendMessage(Component.text("Got away safely!", NamedTextColor.YELLOW));
                finish(null, false);
            });
        }
        boolean trapped = active != null && active.has("trapped") && active.get("trapped").getAsBoolean();
        for (int i = 0; i < side.size() && !trapped; i++) {
            JsonObject p = side.get(i).getAsJsonObject();
            String cond = p.get("condition").getAsString();
            if (p.get("active").getAsBoolean() || cond.endsWith(" fnt")) continue;
            String choice = "switch " + (i + 1);
            m.set(18 + i, Menu.icon(Material.LIGHT_BLUE_DYE, "Switch to " + p.get("details").getAsString().split(",")[0],
                    List.of("HP " + cond)), e -> choose(choice));
        }
        player.openInventory(m.getInventory());
    }

    private void choose(String choice) {
        player.closeInventory();
        if (out != null) out.println("{\"choose\":\"" + choice + "\"}");
    }

    /** ponytail: a failed throw doesn't cost a turn (Showdown has no item action); the main games give the wild Pokémon a free move. */
    private void throwBall() {
        if (!pl.takeBall(player)) {
            player.sendMessage(Component.text("You have no Poké Balls.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
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
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();

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
