package io.github.kahngjoonkoh.cobblemonlink;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Cobblemon's admin commands that act on a named player's Pokémon (slots count from 1, like Cobblemon's). Each
 * returns its reply; the console and command blocks can use them too.
 */
final class AdminCommands {
    private final CobblemonLink pl;

    AdminCommands(CobblemonLink pl) { this.pl = pl; }

    void register() {
        add("spawnherd", "/spawnherd <leader species>", (s, a) -> { // a herd as the spawner makes them, 3 blocks ahead
            if (!(s instanceof Player p)) throw new IllegalArgumentException("Players only.");
            Dex.Spawn h = pl.dex.spawns.stream().filter(x -> x.herd != null && x.species.equals(a[0])).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No herd led by " + a[0] + "."));
            pl.spawner.herd(p, p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(4)), h, new java.util.Random());
            return pl.t(p, "cobblemonlink.herd.spawned", pl.dex.name(a[0], CobblemonLink.loc(p)));
        });
        add("spawnnpc", "/spawnnpc <npc>", (s, a) -> {
            if (!(s instanceof Player p)) throw new IllegalArgumentException("Players only.");
            return pl.npcs.spawn(p.getLocation(), a[0]);
        });
        add("stopbattle", "/stopbattle <player>", (s, a) -> {
            Battle b = pl.battles.get(player(a, 0).getUniqueId());
            if (b == null) return "Not in a battle.";
            b.finish(null, false);
            return "Battle stopped.";
        });
        add("levelup", "/levelup <player> <slot>", (s, a) -> {
            Mon m = mon(a);
            m.gainExp(pl.dex, Rules.expFor(pl.dex.of(m).expGroup, Math.min(100, m.level + 1)) - m.exp, x -> {});
            return save(a, m.name(pl.dex) + " is now level " + m.level + ".");
        });
        add("teach", "/teach <player> <slot> <move>", (s, a) -> {
            Mon m = mon(a);
            String move = a[2].toLowerCase();
            if (!pl.dex.names.getOrDefault("move", java.util.Map.of()).containsKey(move)) return "Unknown move.";
            if (m.moves.size() < 4) m.moves.add(move);
            else m.moves.set(3, move);
            m.pp = null;
            return save(a, m.name(pl.dex) + " learned " + pl.dex.label("move", move) + ".");
        });
        add("held_item", "/held_item <player> <slot> <item|none>", (s, a) -> {
            Mon m = mon(a);
            m.held = a[2].equals("none") ? null : a[2];
            return save(a, m.name(pl.dex) + " holds " + (m.held == null ? "nothing" : m.held) + ".");
        });
        add("friendship", "/friendship <player> <slot> [value]", (s, a) -> {
            Mon m = mon(a);
            if (a.length > 2) m.friendship = Math.clamp(Integer.parseInt(a[2]), 0, 255);
            return save(a, m.name(pl.dex) + "'s friendship: " + m.friendship(pl.dex));
        });
        add("takepokemon", "/takepokemon <player> <slot>", (s, a) -> {
            Store.Data d = pl.store.get(player(a, 0).getUniqueId());
            Mon m = mon(a);
            d.party.remove(m);
            return save(a, "Took " + m.name(pl.dex) + ".");
        });
        add("pokebox", "/pokebox <player> <slot>: party Pokémon to the PC", (s, a) -> {
            Store.Data d = pl.store.get(player(a, 0).getUniqueId());
            Mon m = mon(a);
            if (!d.toPc(m)) return "The PC is full.";
            d.party.remove(m);
            return save(a, m.name(pl.dex) + " was sent to the PC.");
        });
        add("pokeboxall", "/pokeboxall <player>: the whole party to the PC", (s, a) -> {
            Store.Data d = pl.store.get(player(a, 0).getUniqueId());
            int n = 0;
            for (Mon m : List.copyOf(d.party)) if (d.toPc(m)) { d.party.remove(m); n++; }
            return save(a, n + " Pokémon sent to the PC.");
        });
        add("pctake", "/pctake <player> <box> <slot>", (s, a) -> {
            Store.Data d = pl.store.get(player(a, 0).getUniqueId());
            Store.Box box = d.boxes.get(Integer.parseInt(a[1]) - 1);
            int slot = Integer.parseInt(a[2]) - 1;
            Mon m = box.slots[slot];
            if (m == null) return "That slot is empty.";
            box.slots[slot] = null;
            return save(a, "Took " + m.name(pl.dex) + " from the PC.");
        });
        add("clearparty", "/clearparty <player>", (s, a) -> {
            pl.store.get(player(a, 0).getUniqueId()).party.clear();
            return save(a, "Party cleared.");
        });
        add("clearpc", "/clearpc <player>", (s, a) -> {
            for (Store.Box b : pl.store.get(player(a, 0).getUniqueId()).boxes) java.util.Arrays.fill(b.slots, null);
            return save(a, "PC cleared.");
        });
        add("boxcount", "/boxcount <player>: how many Pokémon are in the PC", (s, a) ->
                player(a, 0).getName() + " has " + pl.store.get(player(a, 0).getUniqueId()).pcCount() + " Pokémon in the PC.");
        add("checkspawn", "/checkspawn <player> [grounded|submerged|surface|seafloor|fishing]: what can spawn where they stand", (s, a) -> {
            Player p = player(a, 0);
            String type = a.length > 1 ? a[1] : "grounded";
            var block = type.equals("fishing") ? p.getTargetBlockExact(32, org.bukkit.FluidCollisionMode.ALWAYS) : p.getLocation().getBlock();
            if (block == null) return "Look at water.";
            var fits = pl.spawner.fitting(block, type, 3, null);
            return fits.isEmpty() ? "Nothing fits here." : fits.entrySet().stream()
                    .map(e -> e.getKey() + ": " + String.join(", ", new java.util.TreeSet<>(e.getValue()))).collect(java.util.stream.Collectors.joining("\n"));
        });
        add("pokemonedit", "/pokemonedit <player> <slot> <key=value...> (level, shiny, nature, ability, gender, nickname, held_item, friendship)", (s, a) -> {
            Mon m = mon(a);
            for (int i = 2; i < a.length; i++) {
                String[] kv = a[i].split("=", 2);
                String v = kv.length > 1 ? kv[1] : "true";
                switch (kv[0]) {
                    case "level" -> { m.level = Math.clamp(Integer.parseInt(v), 1, 100); m.exp = Rules.expFor(pl.dex.of(m).expGroup, m.level); }
                    case "shiny" -> m.shiny = Boolean.parseBoolean(v);
                    case "nature" -> m.nature = Character.toUpperCase(v.charAt(0)) + v.substring(1).toLowerCase();
                    case "ability" -> m.ability = v;
                    case "gender" -> m.gender = v.startsWith("f") ? "F" : v.startsWith("m") ? "M" : "";
                    case "nickname" -> m.nickname = v;
                    case "held_item" -> m.held = v.equals("none") ? null : v;
                    case "friendship" -> m.friendship = Math.clamp(Integer.parseInt(v), 0, 255);
                    default -> { return "Unknown property " + kv[0] + "."; }
                }
            }
            return save(a, "Edited " + m.name(pl.dex) + ".");
        });
    }

    private Player player(String[] a, int i) {
        Player p = a.length > i ? Bukkit.getPlayerExact(a[i]) : null;
        if (p == null) throw new IllegalArgumentException("No such player online.");
        return p;
    }

    private Mon mon(String[] a) {
        List<Mon> party = pl.store.get(player(a, 0).getUniqueId()).party;
        int slot = a.length > 1 ? Integer.parseInt(a[1]) - 1 : -1;
        if (slot < 0 || slot >= party.size()) throw new IllegalArgumentException("No Pokémon in that slot.");
        return party.get(slot);
    }

    private String save(String[] a, String reply) {
        pl.store.save(player(a, 0).getUniqueId());
        return reply;
    }

    private void add(String name, String usage, BiFunction<CommandSender, String[], String> run) {
        pl.registerCommand(name, usage + " (admin)", List.of(), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                CommandSender s = src.getExecutor() instanceof Player p ? p : src.getSender(); // "execute as" too
                try {
                    s.sendMessage(Component.text(run.apply(s, args), NamedTextColor.GREEN));
                } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                    s.sendMessage(Component.text(e instanceof IndexOutOfBoundsException ? usage : e.getMessage(), NamedTextColor.RED));
                }
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                return args.length <= 1 ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
            }

            @Override
            public String permission() { return "cobblemonlink.admin"; }
        });
    }
}
