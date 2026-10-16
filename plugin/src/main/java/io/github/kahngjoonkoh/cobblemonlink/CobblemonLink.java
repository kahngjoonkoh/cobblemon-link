package io.github.kahngjoonkoh.cobblemonlink;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CobblemonLink extends JavaPlugin implements Listener {
    static final List<String> STARTERS = List.of("bulbasaur", "charmander", "squirtle", "chikorita", "cyndaquil",
            "totodile", "treecko", "torchic", "mudkip", "turtwig", "chimchar", "piplup", "snivy", "tepig", "oshawott",
            "chespin", "fennekin", "froakie", "rowlet", "litten", "popplio", "grookey", "scorbunny", "sobble",
            "sprigatito", "fuecoco", "quaxly");

    final NamespacedKey wildKey = new NamespacedKey(this, "wild");
    final NamespacedKey ballKey = new NamespacedKey(this, "poke_ball");
    final Map<UUID, Battle> battles = new HashMap<>();
    Dex dex;
    Store store;
    Spawner spawner;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Path data = getDataFolder().toPath().resolve("gamedata.json");
        try {
            dex = Dex.load(data);
        } catch (IOException e) {
            getLogger().severe("Missing " + data + " (server/setup.sh installs it). Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        store = new Store(getDataFolder().toPath().resolve("players"));
        spawner = new Spawner(this);
        getServer().getPluginManager().registerEvents(this, this);

        ShapedRecipe recipe = new ShapedRecipe(ballKey, ball(4));
        recipe.shape("R", "B", "I").setIngredient('R', Material.RED_DYE)
                .setIngredient('B', Material.STONE_BUTTON).setIngredient('I', Material.IRON_INGOT);
        Bukkit.addRecipe(recipe);

        var cfg = getConfig();
        Bukkit.getScheduler().runTaskTimer(this, spawner::tick, 100, cfg.getLong("spawning.interval-ticks"));
        Bukkit.getScheduler().runTaskTimer(this, spawner::cleanup, 200, 200);
        Bukkit.getScheduler().runTaskTimer(this, this::guardBattles, 20, 20);
        registerCommands();
        getLogger().info(dex.species.size() + " species, " + dex.spawns.size() + " spawn entries loaded");
    }

    @Override
    public void onDisable() {
        new ArrayList<>(battles.values()).forEach(b -> b.finish(null, false));
        if (spawner != null) spawner.removeAll();
        if (store != null) Bukkit.getOnlinePlayers().forEach(p -> store.save(p.getUniqueId()));
    }

    // ---- Poké Balls ----

    ItemStack ball(int amount) {
        ItemStack it = new ItemStack(Material.SNOWBALL, amount);
        it.editMeta(m -> {
            m.customName(Component.text("Poké Ball", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Component.text("Throw at a wild Pokémon, or use it in battle", NamedTextColor.GRAY)));
            m.getPersistentDataContainer().set(ballKey, PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    boolean isBall(ItemStack it) {
        return it != null && it.getType() == Material.SNOWBALL && it.getPersistentDataContainer().has(ballKey);
    }

    int countBalls(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (isBall(it)) n += it.getAmount();
        return n;
    }

    boolean takeBall(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isBall(it)) {
                it.subtract();
                return true;
            }
        }
        return false;
    }

    boolean inBattle(Entity wild) {
        return battles.values().stream().anyMatch(b -> b.wildEntity.equals(wild));
    }

    // ---- events ----

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Store.Data d = store.get(e.getPlayer().getUniqueId());
        if (d.party.isEmpty() && d.pc.isEmpty())
            e.getPlayer().sendMessage(Component.text("Welcome! Pick your first Pokémon with /starter", NamedTextColor.GOLD));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Battle b = battles.get(e.getPlayer().getUniqueId());
        if (b != null) b.finish(null, false);
        store.unload(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!spawner.isWild(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) startBattle(e.getPlayer(), e.getRightClicked());
    }

    /** Hitting a wild Pokémon also starts a battle: on Bedrock touch screens a tap is an attack. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!spawner.isWild(e.getEntity())) return;
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player p) startBattle(p, e.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (spawner.isWild(e.getEntity())) e.setCancelled(true); // lightning would turn the pig into a piglin
    }

    @EventHandler
    public void onBallHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball ball) || !isBall(ball.getItem())) return;
        Entity hit = e.getHitEntity();
        if (hit == null || !spawner.isWild(hit) || !(ball.getShooter() instanceof Player p)) return;
        e.setCancelled(true);
        ball.remove();
        if (inBattle(hit)) {
            p.sendMessage(Component.text("That Pokémon is already in a battle.", NamedTextColor.RED));
            return;
        }
        Mon wild = spawner.monOf(hit);
        double chance = Rules.catchChance(dex.get(wild.species).catchRate, wild.hp(dex), wild.maxHp(dex), "");
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            boolean toParty = store.give(p.getUniqueId(), wild);
            hit.remove();
            p.sendMessage(Component.text("Gotcha! " + wild.name(dex) + " (Lv." + wild.level + ") was caught!"
                    + (toParty ? "" : " It was sent to the PC."), NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text("The wild " + wild.name(dex) + " broke free! Weaken it in battle first.", NamedTextColor.YELLOW));
        }
    }

    /** ponytail: beds heal (any time you lie down); a Pokémon Center block is the upgrade. */
    @EventHandler
    public void onBed(PlayerBedEnterEvent e) {
        heal(e.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getRawSlot() < e.getView().getTopInventory().getSize()) m.click(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ---- actions ----

    void startBattle(Player p, Entity wildEntity) {
        if (battles.containsKey(p.getUniqueId())) {
            battles.get(p.getUniqueId()).openMenu();
            return;
        }
        if (inBattle(wildEntity)) {
            p.sendMessage(Component.text("That Pokémon is already in a battle.", NamedTextColor.RED));
            return;
        }
        if (store.get(p.getUniqueId()).party.isEmpty()) {
            p.sendMessage(Component.text("You don't have any Pokémon yet. Use /starter", NamedTextColor.RED));
            return;
        }
        Battle b = new Battle(this, p, wildEntity, spawner.monOf(wildEntity));
        battles.put(p.getUniqueId(), b);
        if (!b.start()) battles.remove(p.getUniqueId());
    }

    /** Ends battles whose player wandered off or whose wild Pokémon vanished. */
    private void guardBattles() {
        int max = getConfig().getInt("battle.max-distance");
        for (Battle b : new ArrayList<>(battles.values())) {
            boolean gone = !b.wildEntity.isValid() || !b.player.isOnline()
                    || !b.player.getWorld().equals(b.wildEntity.getWorld())
                    || b.player.getLocation().distance(b.wildEntity.getLocation()) > max;
            if (gone) {
                b.player.sendMessage(Component.text("You fled the battle.", NamedTextColor.YELLOW));
                b.finish(null, false);
            }
        }
    }

    void heal(Player p) {
        Store.Data d = store.get(p.getUniqueId());
        if (d.party.isEmpty() || battles.containsKey(p.getUniqueId())) return;
        d.party.forEach(m -> m.hp = -1);
        store.save(p.getUniqueId());
        p.sendMessage(Component.text("Your Pokémon are fully healed.", NamedTextColor.GREEN));
    }

    void openStarter(Player p) {
        Menu m = new Menu(27, "Choose your first Pokémon");
        int slot = 0;
        for (String s : STARTERS) {
            if (dex.get(s) == null) continue;
            m.set(slot++, Menu.icon(Menu.typeIcon(dex.get(s).types.getFirst()), dex.name(s),
                    List.of(String.join(" / ", dex.get(s).types.stream().map(t -> dex.label("type", t)).toList()))), e -> {
                Store.Data d = store.get(p.getUniqueId());
                p.closeInventory();
                if (!d.party.isEmpty() || !d.pc.isEmpty()) return;
                Mon mon = Mon.create(dex, s, 5);
                store.give(p.getUniqueId(), mon);
                p.getInventory().addItem(ball(5));
                p.sendMessage(Component.text("You chose " + mon.name(dex) + "! Here are 5 Poké Balls. "
                        + "Right-click (or tap) a wild Pokémon to battle it.", NamedTextColor.GREEN));
            });
        }
        p.openInventory(m.getInventory());
    }

    void openParty(Player p) {
        Store.Data d = store.get(p.getUniqueId());
        Menu m = new Menu(9, "Party");
        for (int i = 0; i < d.party.size(); i++) {
            int idx = i;
            m.set(i, Menu.monIcon(dex, d.party.get(i), "Click: make lead   Shift-click: send to PC"), e -> {
                if (battles.containsKey(p.getUniqueId())) return;
                if (e.isShiftClick()) {
                    if (d.party.size() > 1) d.pc.add(d.party.remove(idx));
                } else {
                    d.party.addFirst(d.party.remove(idx));
                }
                store.save(p.getUniqueId());
                openParty(p);
            });
        }
        m.set(8, Menu.icon(Material.ENDER_CHEST, "Open PC", List.of()), e -> openPc(p, 0));
        p.openInventory(m.getInventory());
    }

    void openPc(Player p, int page) {
        Store.Data d = store.get(p.getUniqueId());
        int pages = Math.max(1, (d.pc.size() + 44) / 45);
        int pg = Math.min(page, pages - 1);
        Menu m = new Menu(54, "PC  " + (pg + 1) + "/" + pages);
        for (int i = 0; i < 45 && pg * 45 + i < d.pc.size(); i++) {
            int idx = pg * 45 + i;
            m.set(i, Menu.monIcon(dex, d.pc.get(idx), "Click: move to party"), e -> {
                if (battles.containsKey(p.getUniqueId()) || d.party.size() >= Store.PARTY) return;
                d.party.add(d.pc.remove(idx));
                store.save(p.getUniqueId());
                openPc(p, pg);
            });
        }
        for (int i = 0; i < d.party.size(); i++) {
            int idx = i;
            m.set(45 + i, Menu.monIcon(dex, d.party.get(i), "Click: move to PC"), e -> {
                if (battles.containsKey(p.getUniqueId()) || d.party.size() <= 1) return;
                d.pc.add(d.party.remove(idx));
                store.save(p.getUniqueId());
                openPc(p, pg);
            });
        }
        if (pg > 0) m.set(52, Menu.icon(Material.ARROW, "Previous page", List.of()), e -> openPc(p, pg - 1));
        if (pg < pages - 1) m.set(53, Menu.icon(Material.ARROW, "Next page", List.of()), e -> openPc(p, pg + 1));
        p.openInventory(m.getInventory());
    }

    // ---- commands ----

    private void registerCommands() {
        playerCommand("starter", "Choose your first Pokémon", null, (p, a) -> {
            Store.Data d = store.get(p.getUniqueId());
            if (!d.party.isEmpty() || !d.pc.isEmpty()) p.sendMessage(Component.text("You already have Pokémon.", NamedTextColor.RED));
            else openStarter(p);
        });
        playerCommand("party", "Show your party", null, (p, a) -> openParty(p));
        playerCommand("pc", "Open your PC", null, (p, a) -> openPc(p, 0));
        playerCommand("battle", "Reopen the battle menu", null, (p, a) -> {
            Battle b = battles.get(p.getUniqueId());
            if (b == null) p.sendMessage(Component.text("You are not in a battle.", NamedTextColor.RED));
            else b.openMenu();
        });
        playerCommand("pokeheal", "Heal your party (admin)", "cobblemonlink.admin", (p, a) -> heal(p));
        playerCommand("pokegive", "/pokegive <species> [level] (admin)", "cobblemonlink.admin", (p, a) -> {
            if (a.length < 1 || dex.get(a[0]) == null) {
                p.sendMessage(Component.text("Unknown species.", NamedTextColor.RED));
                return;
            }
            Mon m = Mon.create(dex, a[0], a.length > 1 ? Integer.parseInt(a[1]) : 5);
            store.give(p.getUniqueId(), m);
            p.sendMessage(Component.text("Gave " + m.name(dex) + " Lv." + m.level, NamedTextColor.GREEN));
        });
        playerCommand("pokespawn", "/pokespawn <species> [level] (admin)", "cobblemonlink.admin", (p, a) -> {
            if (a.length < 1 || dex.get(a[0]) == null) {
                p.sendMessage(Component.text("Unknown species.", NamedTextColor.RED));
                return;
            }
            var loc = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3));
            spawner.spawn(loc, Mon.create(dex, a[0], a.length > 1 ? Integer.parseInt(a[1]) : 5));
        });
        playerCommand("pokeballs", "/pokeballs [amount] (admin)", "cobblemonlink.admin",
                (p, a) -> p.getInventory().addItem(ball(a.length > 0 ? Integer.parseInt(a[0]) : 16)));
    }

    private void playerCommand(String name, String description, String permission, BiConsumer<Player, String[]> run) {
        registerCommand(name, description, List.of(), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (src.getExecutor() instanceof Player p) {
                    try {
                        run.accept(p, args);
                    } catch (NumberFormatException e) {
                        p.sendMessage(Component.text("Not a number: " + e.getMessage(), NamedTextColor.RED));
                    }
                } else {
                    src.getSender().sendMessage("Players only.");
                }
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                if (args.length <= 1 && (name.equals("pokegive") || name.equals("pokespawn"))) {
                    String prefix = args.length == 0 ? "" : args[0];
                    return dex.species.keySet().stream().filter(s -> s.startsWith(prefix)).sorted().limit(50).toList();
                }
                return List.of();
            }

            @Override
            public String permission() { return permission; }
        });
    }
}
