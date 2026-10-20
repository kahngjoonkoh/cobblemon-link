package io.github.kahngjoonkoh.cobblemonlink;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.Tag;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
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
    PcScreen pc;
    PartyPanel panel;
    PokeBalls balls;
    Spawner spawner;
    Companions companions;
    Items items;
    Evolutions evolutions;
    Lang lang = Lang.load(null);
    Machines machines;
    final Trades trades = new Trades(this);
    final NamespacedKey companionKey = new NamespacedKey(this, "companion");
    private final Map<UUID, Input> lastInput = new HashMap<>();
    private final List<ResourcePackInfo> packs = new ArrayList<>(); // sent to Java players on join

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
        lang = Lang.load(getDataFolder().toPath().resolve("lang")); // Cobblemon's 29 languages (setup.sh), and ours
        dex.lang = lang;
        Ui.lang = lang;
        lang.registerItemNames();
        store = new Store(getDataFolder().toPath().resolve("players"), getConfig().getInt("pc.boxes", 40));
        try {
            items = Items.load(this, getDataFolder().toPath().resolve("items.json")); // setup.sh installs it
        } catch (IOException e) {
            getLogger().warning("items.json unreadable: " + e);
        }
        evolutions = new Evolutions(this);
        machines = new Machines(this, dex.fossils);
        try {
            getLogger().info(Recipes.register(this, getDataFolder().toPath().resolve("recipes.json")) + " of Cobblemon's recipes");
        } catch (IOException e) {
            getLogger().warning("recipes.json unreadable: " + e);
        }
        pc = new PcScreen(this);
        panel = new PartyPanel(this);
        balls = new PokeBalls(this);
        Mod.enable(this);
        spawner = new Spawner(this);
        companions = new Companions(this);
        getServer().getPluginManager().registerEvents(this, this);

        ShapedRecipe recipe = new ShapedRecipe(ballKey, ball(4));
        recipe.shape("R", "B", "I").setIngredient('R', Material.RED_DYE)
                .setIngredient('B', Material.STONE_BUTTON).setIngredient('I', Material.IRON_INGOT);
        Bukkit.addRecipe(recipe);

        var cfg = getConfig();
        Bukkit.getScheduler().runTaskTimer(this, spawner::tick, 100, cfg.getLong("spawning.interval-ticks"));
        Bukkit.getScheduler().runTaskTimer(this, spawner::cleanup, 200, 200);
        Bukkit.getScheduler().runTaskTimer(this, () -> companions.tick(), 10, 10);
        Bukkit.getScheduler().runTaskTimer(this, this::guardBattles, 20, 20);
        Bukkit.getScheduler().runTaskTimer(this, this::partySecond, 20, 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> machines.tick(), 40, 20); // Cobblemon's machine blocks
        Bukkit.getScheduler().runTaskTimer(this, this::showStarterToasts, 40, 40);
        Bukkit.getScheduler().runTaskTimer(this, this::unlockWallpapers, 20, 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> panel.tick(), 20, 1); // Cobblemon's party overlay, vanilla Java
        Bukkit.getScheduler().runTaskTimer(this, () -> balls.tick(), 1, 1); // thrown Poké Balls' models
        Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(Mod::hud), 20, 10); // the mod's party overlay
        registerCommands();
        buildIcons();
        loadPack("cobblemon-link-ui.zip");
        loadPack("cobblemon-link-icons.zip");
        loadPack("cobblemon-link-items.zip"); // Cobblemon's item art (tools/build_items.py)
        loadPack("cobblemon-link-sounds.zip"); // Cobblemon's sounds (tools/build_sounds.py)
        try (var pics = Files.list(getDataFolder().toPath().resolveSibling("BetterModel"))) { // the Pokémon pictures, a pack a region
            pics.map(f -> f.getFileName().toString()).filter(n -> n.startsWith("cobblemon-link-pics-") && n.endsWith(".zip"))
                    .sorted().forEach(this::loadPack);
        } catch (IOException e) {
            getLogger().warning("No picture packs: " + e);
        }
        getLogger().info(dex.species.size() + " species, " + dex.spawns.size() + " spawn entries loaded");
    }

    @Override
    public void onDisable() {
        new ArrayList<>(battles.values()).forEach(b -> b.finish(null, false));
        if (spawner != null) spawner.removeAll();
        if (machines != null) machines.removeAll();
        if (store != null) Bukkit.getOnlinePlayers().forEach(p -> store.save(p.getUniqueId()));
    }

    /** The player's client language, as Minecraft names it (ko_kr). */
    static String loc(Player p) { return Lang.locale(p.locale()); }

    /** key in the player's language. */
    String t(Player p, String key, Object... args) { return lang.get(loc(p), key, args); }

    // ---- Poké Balls ----

    static final NamespacedKey BALL_MODEL = new NamespacedKey("cobblemon", "poke_ball");

    public ItemStack ball(int amount) {
        ItemStack it = new ItemStack(Material.SNOWBALL, amount);
        it.editMeta(m -> {
            m.customName(Component.text("Poké Ball", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Component.text("Throw at a wild Pokémon", NamedTextColor.GRAY)));
            m.setItemModel(BALL_MODEL); // Cobblemon's art from the UI pack; Geyser maps it for Bedrock (build_bedrock_ui.py)
            m.getPersistentDataContainer().set(ballKey, PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    public boolean isBall(ItemStack it) {
        return it != null && it.getType() == Material.SNOWBALL && it.getPersistentDataContainer().has(ballKey);
    }

    int countBalls(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (isBall(it)) n += it.getAmount();
        return n;
    }

    boolean inBattle(Entity wild) {
        return battles.values().stream().anyMatch(b -> b.wildEntity.equals(wild));
    }

    // ---- events ----

    /**
     * The battle hotbar's item icons (Icons.java), rendered from the vanilla client jar and written next to the UI pack.
     * ponytail: runs on the main thread at startup; the first start downloads the client jar (~30 MB) once.
     */
    private void buildIcons() {
        try {
            Icons ic = Icons.build(Icons.clientJar(getDataFolder().toPath(), Bukkit.getMinecraftVersion()));
            Hotbar.icons = ic.icons;
            Hotbar.hud = ic.hud;
            if (ic.pack != null) Files.write(getDataFolder().toPath().resolveSibling("BetterModel").resolve("cobblemon-link-icons.zip"), ic.pack);
            getLogger().info(ic.icons.size() + " item icons");
        } catch (IOException e) {
            getLogger().warning("No item icons for the battle hotbar (" + e + ")");
        }
    }

    /**
     * setup.sh puts the UI pack next to BetterModel's build.zip, so it is served from the same place:
     * its URL is the server resource pack's URL with the file name swapped.
     */
    private void loadPack(String name) {
        Path zip = getDataFolder().toPath().resolveSibling("BetterModel").resolve(name);
        String main = getServer().getResourcePack();
        if (!Files.exists(zip) || main == null || main.isEmpty()) {
            getLogger().warning("No " + zip + " or no server resource pack: Java battle menus use plain text.");
            return;
        }
        try {
            if (name.equals("cobblemon-link-ui.zip")) Ui.loadPortraits(zip);
            byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(zip));
            packs.add(ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(sha1),
                    URI.create(main).resolve(name), HexFormat.of().formatHex(sha1)));
        } catch (IOException | NoSuchAlgorithmException e) {
            getLogger().warning(name + " unreadable: " + e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!packs.isEmpty() && !Ui.bedrock(e.getPlayer()))
            e.getPlayer().sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(packs).replace(false).required(true));
        Player p = e.getPlayer();
        Store.Data d = store.get(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> MoveLearn.next(this, p), 200); // moves still waiting from last time
        if (d.empty() && prompt(p, true)) { // Cobblemon's "Check out the starters!" prompt, with our command
            p.sendMessage(Component.text("Type /starter to choose your first Pokémon and begin your journey!", NamedTextColor.GOLD));
            (plainToast(p) ? starterToast : toastAfterPacks).add(p.getUniqueId());
        }
    }

    /**
     * Keys a vanilla client reports (1.21.2+ input packet): Ctrl (sprint) pressed while standing still is Cobblemon's
     * R (send out / recall the lead); with Shift held it opens the party. Moving Ctrl is sprinting.
     */
    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Player p = e.getPlayer();
        Input now = e.getInput(), was = lastInput.put(p.getUniqueId(), now);
        boolean pressed = now.isSprint() && (was == null || !was.isSprint());
        boolean still = !now.isForward() && !now.isBackward() && !now.isLeft() && !now.isRight() && !now.isJump();
        if (!pressed || !still || battles.containsKey(p.getUniqueId())) return;
        Store.Data d = store.get(p.getUniqueId());
        if (d.party.isEmpty()) return; // the starter is /starter
        if (now.isSneak()) openParty(p);
        else companions.toggle(p, d.party.getFirst());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) { companions.recallAll(e.getPlayer()); }

    /** The starter menu, unless another plugin takes over the starter (api.StarterPromptEvent). */
    void offerStarter(Player p) {
        if (prompt(p, false)) openStarter(p);
    }

    private boolean prompt(Player p, boolean firstJoin) {
        return new io.github.kahngjoonkoh.cobblemonlink.api.StarterPromptEvent(p, firstJoin).callEvent();
    }


    public boolean hasPokemon(Player p) {
        Store.Data d = store.get(p.getUniqueId());
        return !d.empty();
    }

    public static List<List<String>> startersByRegion() {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i + 3 <= STARTERS.size(); i += 3) out.add(STARTERS.subList(i, i + 3));
        return out;
    }

    /** Buttons of the "Pokémon" dialog (datapack: Quick Actions key G, and the pause menu; tools/build_data.py). */
    @EventHandler
    public void onQuickAction(io.papermc.paper.event.player.PlayerCustomClickEvent e) {
        if (!e.getIdentifier().namespace().equals("cobblemonlink")
                || !(e.getCommonConnection() instanceof io.papermc.paper.connection.PlayerGameConnection c)) return;
        Player p = c.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            Store.Data d = store.get(p.getUniqueId());
            Battle b = battles.get(p.getUniqueId());
            switch (e.getIdentifier().value()) {
                case "lead" -> {
                    if (d.party.isEmpty()) offerStarter(p);
                    else if (b == null) companions.toggle(p, d.party.getFirst());
                }
                case "party" -> { if (d.party.isEmpty()) offerStarter(p); else openParty(p); }
                case "pc" -> pc.open(p);
                case "battle" -> { if (b != null) b.reveal(); }
                default -> {}
            }
        });
    }

    private final java.util.Set<UUID> starterToast = new java.util.HashSet<>(), toastAfterPacks = new java.util.HashSet<>();

    private boolean plainToast(Player p) { return Ui.bedrock(p) || packs.isEmpty(); } // no UI pack: no toast art

    /** The toast is pack art: it waits for the packs (the client loads them all in one go). */
    @EventHandler
    public void onPack(org.bukkit.event.player.PlayerResourcePackStatusEvent e) {
        if (e.getStatus() != org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) return;
        panel.ready(e.getPlayer()); // the panel is pack art too
        if (toastAfterPacks.remove(e.getPlayer().getUniqueId())) starterToast.add(e.getPlayer().getUniqueId());
    }

    /** Every 2 s: the starter toast stays up (an action bar lasts 3 s) until the player has a Pokémon. */
    private void showStarterToasts() {
        starterToast.removeIf(id -> {
            Player p = getServer().getPlayer(id);
            if (p == null || hasPokemon(p)) return true;
            p.sendActionBar(plainToast(p) ? Component.text("Check out the starters! Type /starter", NamedTextColor.RED) : Ui.starterToast());
            return false;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        toastAfterPacks.remove(e.getPlayer().getUniqueId());
        Battle b = battles.get(e.getPlayer().getUniqueId());
        if (b != null) b.finish(null, false);
        companions.recallAll(e.getPlayer());
        lastInput.remove(e.getPlayer().getUniqueId());
        pc.forget(e.getPlayer());
        PokedexScreen.forget(e.getPlayer());
        panel.forget(e.getPlayer());
        SummaryScreen.forget(e.getPlayer());
        store.unload(e.getPlayer().getUniqueId());
    }

    /** Dying ends the battle (you respawn away from it, outside the battle camera). */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Battle b = battles.get(e.getPlayer().getUniqueId());
        if (b != null) b.finish(null, false);
        companions.recallAll(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (spawner.isCompanion(e.getRightClicked())) e.setCancelled(true); // no saddles or leads on your Pokémon
        if (!spawner.isWild(e.getRightClicked())) return;
        e.setCancelled(true);
        Battle b = battles.get(e.getPlayer().getUniqueId());
        boolean throwing = b != null && b.wildEntity.equals(e.getRightClicked()) && isBall(e.getPlayer().getInventory().getItemInMainHand());
        if (e.getHand() == EquipmentSlot.HAND && !throwing) startBattle(e.getPlayer(), e.getRightClicked()); // else the ball flies
    }

    /** A Pokémon's or machine's pig carrier leaves nothing behind (no porkchops when /kill clears them). */
    @EventHandler
    public void onCarrierDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (spawner.isWild(e.getEntity()) || spawner.isCompanion(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    /** Hitting a wild Pokémon also starts a battle: on Bedrock touch screens a tap is an attack. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!spawner.isWild(e.getEntity())) return;
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player p) startBattle(p, e.getEntity());
    }

    /** Java cursor screen: a left click is an arm swing; the seat and the world are off-limits meanwhile. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        Battle b = battles.get(e.getPlayer().getUniqueId());
        if (b != null && e.getAnimationType() == PlayerAnimationType.ARM_SWING) b.swing();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent e) {
        Battle b = e.getEntity() instanceof Player p ? battles.get(p.getUniqueId()) : null;
        if (b != null && b.screen() != null && b.screen().holds(e.getDismounted())) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(this, b::hide); // Shift: hide the battle; not inside the dismount itself
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Battle b = battles.get(e.getPlayer().getUniqueId());
        if (b != null) b.sneak(e.isSneaking());
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Battle b = battles.get(e.getPlayer().getUniqueId());
        if (b != null && b.screen() != null) e.setCancelled(true);
        // Cobblemon's machine blocks: use, pick up (Shift + left-click), place (right-click with the item)
        Block clicked = e.getClickedBlock();
        Machines.Machine machine = clicked == null || machines == null ? null : machines.at(clicked);
        if (machine != null && e.getHand() == EquipmentSlot.HAND) {
            e.setCancelled(true);
            if (e.getAction().isRightClick()) machines.use(e.getPlayer(), machine, e.getPlayer().getInventory().getItemInMainHand());
            else if (e.getPlayer().isSneaking()) machines.pickUp(e.getPlayer(), machine);
            return;
        }
        String held = items == null ? null : items.id(e.getPlayer().getInventory().getItemInMainHand());
        if (held != null && clicked != null && e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && e.getHand() == EquipmentSlot.HAND
                && items.defs.containsKey(held) && items.defs.get(held).kind.equals("machine")) {
            e.setCancelled(true);
            if (machines.place(e.getPlayer(), held, clicked, e.getBlockFace()) && e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE)
                e.getPlayer().getInventory().getItemInMainHand().subtract();
            return;
        }
        // Cobblemon's items: right-click with one in the main hand to use it on a party Pokémon (not on a crafting
        // table, chest or other block that opens)
        if (clicked != null && clicked.getType().isInteractable() && !e.getPlayer().isSneaking()) return;
        if (e.getHand() == EquipmentSlot.HAND && e.getAction().isRightClick() && items != null
                && items.use(e.getPlayer(), e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (spawner.isWild(e.getEntity())) e.setCancelled(true); // lightning would turn the pig into a piglin
    }

    @EventHandler(ignoreCancelled = true)
    public void onBallThrow(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof Snowball ball && isBall(ball.getItem()) && ball.getShooter() instanceof Player) balls.launch(ball);
    }

    @EventHandler
    public void onBallHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball ball) || !isBall(ball.getItem())) return;
        Entity model = balls.land(ball), hit = e.getHitEntity();
        if (hit == null || !spawner.isWild(hit) || !(ball.getShooter() instanceof Player p) || balls.capturing(hit)) {
            if (model != null) model.remove(); // ponytail: a miss just vanishes (Cobblemon's ball drops to the ground)
            return;
        }
        e.setCancelled(true);
        ball.remove();
        Battle mine = battles.get(p.getUniqueId());
        String ballId = items == null ? "poke_ball" : java.util.Objects.requireNonNullElse(items.id(ball.getItem()), "poke_ball");
        if (mine != null && mine.wildEntity.equals(hit)) {
            mine.ballHit(model, ballId);
            return;
        }
        if (inBattle(hit)) {
            if (model != null) model.remove();
            p.sendMessage(Component.text(t(p, "cobblemonlink.battle.busy"), NamedTextColor.RED));
            return;
        }
        Mon wild = spawner.monOf(hit);
        Store.Data d = store.get(p.getUniqueId());
        double bonus = Items.ballBonus(ballId, dex, new Items.Throw(wild, hit, false, 0, d.party, d.caught.contains(wild.species)));
        var result = Double.isInfinite(bonus) ? new Rules.Capture(3, true, false) : Rules.capture(dex.of(wild).catchRate,
                wild.hp(dex), wild.maxHp(dex), wild.status == null ? "" : wild.status, wild.level, false, bonus, 0,
                d.caught.size(), ThreadLocalRandom.current());
        balls.capture(model, hit, result, caught -> {
            if (!hit.isValid() || !p.isOnline()) return;
            if (caught) {
                Items.caught(ballId, wild);
                boolean toParty = store.give(p.getUniqueId(), wild);
                hit.remove();
                p.sendMessage(Component.text(t(p, "cobblemon.capture.succeeded", wild.name(dex, loc(p)))
                        + (toParty ? "" : " " + t(p, "cobblemonlink.capture.to_pc", wild.name(dex, loc(p)))), NamedTextColor.GREEN));
            } else {
                p.sendMessage(Component.text(t(p, "cobblemonlink.capture.fled_hint", wild.name(dex, loc(p))), NamedTextColor.YELLOW));
            }
        });
    }

    private static final String[] APRICORNS = {"red", "yellow", "green", "blue", "pink", "black", "white"};
    private static final String[] MINT_LEAVES = {"red", "blue", "cyan", "pink", "green", "white"};

    /**
     * Where the recipes' materials come from without Cobblemon's blocks: apricorns from leaves (its apricorn trees),
     * mint leaves and medicinal leeks from grass (its mint and leek plants), berries from sweet berry bushes.
     * ponytail: drop chances stand in for Cobblemon's world-generated plants and trees.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (items == null || e.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        var r = ThreadLocalRandom.current();
        Material t = e.getBlock().getType();
        String id = null;
        if (Tag.LEAVES.isTagged(t) && r.nextInt(40) == 0) id = APRICORNS[r.nextInt(APRICORNS.length)] + "_apricorn";
        else if ((t == Material.SHORT_GRASS || t == Material.TALL_GRASS) && r.nextInt(60) == 0)
            id = r.nextInt(4) == 0 ? "medicinal_leek" : MINT_LEAVES[r.nextInt(MINT_LEAVES.length)] + "_mint_leaf";
        else if (t == Material.AMETHYST_CLUSTER && r.nextInt(3) == 0) { // type gems, for the TM Machine
            var gems = items.defs.keySet().stream().filter(k -> k.endsWith("_gem")).sorted().toList();
            id = gems.isEmpty() ? null : gems.get(r.nextInt(gems.size()));
        } else if ((t == Material.STONE || t == Material.DEEPSLATE || t == Material.SANDSTONE) && r.nextInt(400) == 0) { // fossils
            var fossils = items.defs.entrySet().stream().filter(x -> x.getValue().kind.equals("fossil")).map(Map.Entry::getKey).sorted().toList();
            id = fossils.isEmpty() ? null : fossils.get(r.nextInt(fossils.size()));
        } else if (t == Material.SWEET_BERRY_BUSH && r.nextInt(8) == 0) {
            var berries = items.defs.keySet().stream().filter(k -> k.endsWith("_berry")).sorted().toList();
            id = berries.isEmpty() ? null : berries.get(r.nextInt(berries.size()));
        }
        ItemStack drop = id == null ? null : items.create(id, 1);
        if (drop != null) e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), drop);
    }

    /** Cobblemon's Poké Rods: a bite is a Pokémon 85% of the time (pokemonSpawnChance), else the vanilla catch. */
    @EventHandler(ignoreCancelled = true)
    public void onFish(org.bukkit.event.player.PlayerFishEvent e) {
        if (e.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH || items == null) return;
        var inv = e.getPlayer().getInventory(); // getHand() is often null on a catch: the rod is whichever hand holds one
        ItemStack rodItem = e.getHand() != null ? inv.getItem(e.getHand())
                : inv.getItemInMainHand().getType() == Material.FISHING_ROD ? inv.getItemInMainHand() : inv.getItemInOffHand();
        String rod = items.id(rodItem);
        if (rod == null || !rod.endsWith("_rod") || ThreadLocalRandom.current().nextInt(100) >= 85) return;
        Entity mon = spawner.fish(e.getPlayer(), e.getHook().getLocation(), rodItem.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LURE), rod);
        if (mon == null) return;
        if (e.getCaught() != null) e.getCaught().remove(); // a Pokémon instead of the fish
        e.setExpToDrop(ThreadLocalRandom.current().nextInt(1, 7));
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
        m.click(e); // raw slots past the menu are only ever StarterScreen's buttons
    }

    /** The PC hides the player's own items from their screen (PcScreen.hideInventory): show them again. */
    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Menu m && m.onClose != null) m.onClose.run();
        if (e.getInventory().getHolder() instanceof Menu && e.getPlayer() instanceof Player p)
            Bukkit.getScheduler().runTask(this, () -> { if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)) p.updateInventory(); });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    private final Map<UUID, UUID> challenges = new HashMap<>(); // challenged -> challenger

    /** Cobblemon's battle challenge between players (32 blocks), accepted from a clickable chat line. */
    private void challenge(Player p, String[] a) {
        if (a.length == 2 && a[0].equalsIgnoreCase("accept")) {
            Player by = Bukkit.getPlayerExact(a[1]);
            if (by == null || !by.getUniqueId().equals(challenges.remove(p.getUniqueId()))) {
                p.sendMessage(Component.text(t(p, "cobblemonlink.trade.none", a[1]), NamedTextColor.RED));
                return;
            }
            if (battles.containsKey(p.getUniqueId()) || battles.containsKey(by.getUniqueId())) {
                p.sendMessage(Component.text(t(p, "cobblemonlink.challenge.busy"), NamedTextColor.RED));
                return;
            }
            Battle[] b = Battle.pvp(this, by, p);
            battles.put(by.getUniqueId(), b[0]);
            battles.put(p.getUniqueId(), b[1]);
            if (!b[0].start()) {
                battles.remove(by.getUniqueId());
                battles.remove(p.getUniqueId());
            }
            return;
        }
        Player other = a.length == 1 ? Bukkit.getPlayerExact(a[0]) : null;
        if (other == null || other.equals(p)) {
            p.sendMessage(Component.text("/challenge <player>", NamedTextColor.RED));
            return;
        }
        if (!other.getWorld().equals(p.getWorld()) || other.getLocation().distance(p.getLocation()) > 32) {
            p.sendMessage(Component.text(t(p, "cobblemonlink.trade.too_far", other.getName()), NamedTextColor.RED));
            return;
        }
        challenges.put(other.getUniqueId(), p.getUniqueId());
        p.sendMessage(Component.text(t(p, "cobblemonlink.challenge.sent", other.getName()), NamedTextColor.GRAY));
        other.sendMessage(Component.text(t(other, "cobblemonlink.challenge.received", p.getName()), NamedTextColor.YELLOW)
                .append(Component.text(t(other, "cobblemonlink.trade.accept"), NamedTextColor.GREEN).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/challenge accept " + p.getName()))));
    }

    // ---- actions ----

    void startBattle(Player p, Entity wildEntity) {
        if (battles.containsKey(p.getUniqueId())) {
            Battle b = battles.get(p.getUniqueId());
            if (b.screen() == null) b.reveal(); // the cursor screen takes clicks as swings instead
            return;
        }
        if (!Ui.bedrock(p) && !p.isOnGround()) return; // the battle seat would hold them in mid-air (Battle.reveal)
        if (inBattle(wildEntity)) {
            p.sendMessage(Component.text("That Pokémon is already in a battle.", NamedTextColor.RED));
            return;
        }
        if (store.get(p.getUniqueId()).party.isEmpty()) {
            p.sendMessage(Component.text("You don't have any Pokémon yet. Use /starter", NamedTextColor.RED));
            return;
        }
        companions.recallAll(p); // the battle sends out its own
        Battle b = new Battle(this, p, wildEntity, spawner.monOf(wildEntity));
        store.get(p.getUniqueId()).see(b.wild); // the Pokédex: seen in battle
        battles.put(p.getUniqueId(), b);
        if (!b.start()) battles.remove(p.getUniqueId());
    }

    /** Ends battles whose player wandered off or whose wild Pokémon vanished. */
    private void guardBattles() {
        int max = getConfig().getInt("battle.max-distance");
        for (Battle b : new ArrayList<>(battles.values())) {
            boolean gone = !b.wildEntity.isValid() || !b.player.isOnline() || b.wildEntity instanceof Player rp && !rp.isOnline()
                    || !b.player.getWorld().equals(b.wildEntity.getWorld())
                    || b.player.getLocation().distance(b.wildEntity.getLocation()) > max;
            if (gone) {
                b.player.sendMessage(Component.text(t(b.player, "cobblemonlink.battle.walked_away"), NamedTextColor.YELLOW));
                b.finish(null, false);
            }
        }
    }

    /** Cobblemon's passive healing, faint recovery and status expiry, every second out of battle. */
    private void partySecond() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (battles.containsKey(p.getUniqueId())) continue;
            for (Mon m : store.get(p.getUniqueId()).party) {
                if (m.second(dex)) p.sendMessage(Component.text(t(p, "cobblemon.party.faintRecover", m.name(dex, loc(p)))));
            }
        }
    }

    void heal(Player p) {
        Store.Data d = store.get(p.getUniqueId());
        if (d.party.isEmpty() || battles.containsKey(p.getUniqueId())) return;
        d.party.forEach(Mon::heal);
        store.save(p.getUniqueId());
        p.sendMessage(Component.text(t(p, "cobblemonlink.healed"), NamedTextColor.GREEN));
    }

    void openStarter(Player p) { openStarter(p, STARTERS, null); }

    /**
     * The starter choice on first join (and /starter): every region's starters, as Cobblemon's starter screen offers,
     * and with starter.random-pokemon one random Pokémon too (rolled once per player: a wild-spawning Pokémon that
     * isn't a starter or a level-up evolution).
     * species/from let another plugin offer a smaller choice (e.g. one region), without the random one.
     */
    public void openStarter(Player p, List<String> species, String from) {
        if (hasPokemon(p)) return;
        Menu m = new Menu(36, from == null ? "Choose your first Pokémon" : from + ": choose your first Pokémon");
        List<String> choices = new ArrayList<>(species.stream().filter(s -> dex.get(s) != null).toList());
        String surprise = from == null && getConfig().getBoolean("starter.random-pokemon", false)
                ? starterRolls.computeIfAbsent(p.getUniqueId(), k -> randomStarter()) : null;
        List<String> all = new ArrayList<>(choices);
        if (surprise != null) all.add(surprise);
        // Cobblemon's starter screen: a painted chest (Java) or a restyled form (Bedrock), both StarterScreen
        if (Mod.form(p)) {
            StarterScreen.bedrock(this, p, StarterScreen.regions(choices, surprise), 0, all.getFirst(), s -> chooseStarter(p, s));
            return;
        }
        if (!Ui.cards.isEmpty()) {
            StarterScreen.open(this, p, StarterScreen.regions(choices, surprise), 0, all.getFirst(), s -> chooseStarter(p, s));
            return;
        }
        int slot = 0; // no UI pack (no portraits): a chest menu
        for (String s : choices) m.set(slot++, starterIcon(s, null), e -> chooseStarter(p, s));
        if (surprise != null) m.set(31, starterIcon(surprise, "A Pokémon you might not meet for a while"), e -> chooseStarter(p, surprise));
        p.openInventory(m.getInventory());
    }

    private final Map<UUID, String> starterRolls = new HashMap<>();
    /** A first-stage Pokémon from the common/uncommon wild spawns that isn't one of the starters. */
    private String randomStarter() {
        java.util.Set<String> evolved = new java.util.HashSet<>();
        dex.species.values().forEach(s -> s.evolutions.forEach(e -> evolved.add(e.get(1))));
        List<String> pool = dex.spawns.stream().filter(s -> s.bucket.equals("common") || s.bucket.equals("uncommon"))
                .map(s -> s.species).distinct().filter(s -> !evolved.contains(s) && !STARTERS.contains(s)).toList();
        return pool.isEmpty() ? STARTERS.getFirst() : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private ItemStack starterIcon(String species, String extra) {
        Dex.Species d = dex.get(species);
        List<String> lore = new ArrayList<>(List.of(String.join(" / ", d.types.stream().map(t -> dex.label("type", t)).toList())));
        if (extra != null) lore.add(extra);
        return Menu.icon(Menu.speciesItem(species, Menu.typeIcon(d.types.getFirst())), dex.name(species), lore);
    }

    private void chooseStarter(Player p, String species) {
        p.closeInventory();
        p.closeDialog();
        if (hasPokemon(p)) return;
        Mon mon = Mon.create(dex, species, getConfig().getInt("starter.level", 10)); // Cobblemon's starters are level 10
        store.give(p.getUniqueId(), mon);
        starterRolls.remove(p.getUniqueId());
        int balls = getConfig().getInt("starter.balls", 0);
        if (balls > 0) p.getInventory().addItem(ball(balls));
        p.sendMessage(Component.text("You chose " + mon.name(dex) + "!" + (balls > 0 ? " Here are " + balls + " Poké Balls." : "")
                + " Right-click (or tap) a wild Pokémon to battle it.", NamedTextColor.GREEN));
    }

    /** One party Pokémon's options: big buttons, so a tap on a phone is enough (Bedrock menus have no right-click). */
    void openMon(Player p, int idx) {
        Store.Data d = store.get(p.getUniqueId());
        if (idx >= d.party.size() || battles.containsKey(p.getUniqueId())) return;
        Mon mon = d.party.get(idx);
        Menu m = new Menu(9, mon.name(dex));
        boolean isOut = companions.isOut(p, mon);
        m.set(2, Menu.icon(isOut ? Material.ENDER_PEARL : Material.SNOWBALL, isOut ? "Recall" : "Send out", List.of()), e -> {
            companions.toggle(p, mon);
            openParty(p);
        });
        if (idx > 0) m.set(4, Menu.icon(Material.NETHER_STAR, "Make lead", List.of()), e -> {
            d.party.addFirst(d.party.remove(idx));
            store.save(p.getUniqueId());
            openParty(p);
        });
        if (d.party.size() > 1) m.set(6, Menu.icon(Material.ENDER_CHEST, "Move to PC", List.of()), e -> {
            if (!d.toPc(mon)) {
                p.sendMessage(Component.text("Your PC is full.", NamedTextColor.RED));
                return;
            }
            companions.recall(p, mon.id);
            d.party.remove(idx);
            store.save(p.getUniqueId());
            openParty(p);
        });
        m.set(0, Menu.monIcon(dex, mon, "Click: summary"), e -> SummaryScreen.open(this, p, idx, SummaryScreen.INFO));
        m.set(8, Menu.icon(Material.ARROW, "Back", List.of()), e -> openParty(p));
        p.openInventory(m.getInventory());
    }

    void openParty(Player p) {
        Store.Data d = store.get(p.getUniqueId());
        if ((Mod.form(p) || SummaryScreen.art(this, p)) && !d.party.isEmpty()) { // Cobblemon's party is its Summary screen
            SummaryScreen.open(this, p, 0, SummaryScreen.INFO);
            return;
        }
        Menu m = new Menu(9, "Party");
        for (int i = 0; i < d.party.size(); i++) {
            int idx = i;
            Mon mon = d.party.get(i);
            m.set(i, Menu.monIcon(dex, mon, companions.isOut(p, mon) ? "Out with you" : "Click for options"), e -> openMon(p, idx));
        }
        m.set(8, Menu.icon(Material.ENDER_CHEST, "Open PC", List.of()), e -> pc.open(p));
        p.openInventory(m.getInventory());
    }

    void onMain(Runnable r) { Bukkit.getScheduler().runTask(this, r); }

    /**
     * Cobblemon's wallpaper unlocks (its player_tick_pre callback): once a second, standing in a cave, forest, ocean,
     * Nether or End biome unlocks that biome's wallpaper. ponytail: biome names stand in for Cobblemon's biome tags;
     * the alpha wallpaper (catching an alpha) never unlocks, as nothing spawns alpha yet.
     */
    private void unlockWallpapers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Store.Data d = store.get(p.getUniqueId());
            String biome = p.getLocation().getBlock().getBiome().getKey().getKey();
            String w = switch (p.getWorld().getEnvironment()) {
                case NETHER -> "biome_nether";
                case THE_END -> "biome_the_end";
                default -> biome.contains("ocean") ? "biome_ocean"
                        : biome.equals("lush_caves") || biome.equals("dripstone_caves") || biome.equals("deep_dark") ? "biome_cave"
                        : biome.contains("forest") || biome.equals("pale_garden") ? "biome_forest" : null;
            };
            if (w != null && d.wallpapers.add(w)) {
                store.save(p.getUniqueId());
                p.sendMessage(Component.text("Wallpaper Unlocked: " + PcScreen.WALLPAPER_NAMES.get(PcScreen.WALLPAPERS.indexOf(w))
                        + " (your PC's box settings)", NamedTextColor.GOLD));
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.5f);
            }
        }
    }

    // ---- commands ----

    private void registerCommands() {
        playerCommand("starter", "Choose your first Pokémon", null, (p, a) -> {
            Store.Data d = store.get(p.getUniqueId());
            if (!d.empty()) p.sendMessage(Component.text("You already have Pokémon.", NamedTextColor.RED));
            else offerStarter(p);
        });
        playerCommand("party", "Show your party", null, (p, a) -> openParty(p));
        playerCommand("partyhud", "Show or hide the party on the left of the screen", null, (p, a) -> {
            Store.Data d = store.get(p.getUniqueId());
            d.hideParty = !d.hideParty;
            store.save(p.getUniqueId());
            p.sendMessage(Component.text(d.hideParty ? "Party hidden. /partyhud shows it again." : "Party shown.", NamedTextColor.GRAY));
        });
        playerCommand("pokedex", "Open your Pokédex", null, (p, a) -> PokedexScreen.open(this, p, 0, null));
        playerCommand("pc", "Open your PC (admin; players use the PC button in the Pokémon menu)", "cobblemonlink.admin", (p, a) -> pc.open(p));
        playerCommand("battle", "Reopen the battle menu", null, (p, a) -> {
            Battle b = battles.get(p.getUniqueId());
            if (b == null) p.sendMessage(Component.text("You are not in a battle.", NamedTextColor.RED));
            else b.reveal();
        });
        new AdminCommands(this).register();
        playerCommand("challenge", "/challenge <player>: a Pokémon battle; /challenge accept <player>", null, this::challenge);
        playerCommand("spectate", "/spectate <player>: watch their battle (64 blocks); again to stop", null, (p, a) -> {
            for (Battle b : battles.values()) // stop watching any battle
                if (b.spectators.remove(p.getUniqueId())) { p.sendMessage(Component.text(t(p, "cobblemonlink.spectate.stop"), NamedTextColor.GRAY)); return; }
            Player who = a.length == 1 ? Bukkit.getPlayerExact(a[0]) : null;
            Battle b = who == null ? null : battles.get(who.getUniqueId());
            if (b != null && b.rival != null && b.side.equals("p2")) b = b.rival; // watch from the challenger's side
            if (b == null || !who.getWorld().equals(p.getWorld()) || who.getLocation().distance(p.getLocation()) > 64) {
                p.sendMessage(Component.text(t(p, "cobblemonlink.spectate.none"), NamedTextColor.RED));
                return;
            }
            b.spectators.add(p.getUniqueId());
            p.sendMessage(Component.text(t(p, "cobblemonlink.spectate.start", who.getName()), NamedTextColor.GRAY));
        });
        playerCommand("trade", "/trade <player>: offer a Pokémon trade; /trade accept <player>", null, (p, a) -> trades.command(p, a));
        playerCommand("pokeheal", "Heal your party (admin)", "cobblemonlink.admin", (p, a) -> heal(p));
        playerCommand("pokegive", "/pokegive <species> [level] [properties...] (admin)", "cobblemonlink.admin", (p, a) -> {
            if (a.length < 1 || dex.get(a[0]) == null) {
                p.sendMessage(Component.text("Unknown species.", NamedTextColor.RED));
                return;
            }
            Mon m = Mon.create(dex, a[0], a.length > 1 ? Integer.parseInt(a[1]) : 5,
                    a.length > 2 ? List.of(a).subList(2, a.length) : List.of()); // e.g. alolan shiny
            store.give(p.getUniqueId(), m);
            p.sendMessage(Component.text("Gave " + m.name(dex) + " Lv." + m.level, NamedTextColor.GREEN));
        });
        playerCommand("pokespawn", "/pokespawn <species> [level] [properties...] (admin)", "cobblemonlink.admin", (p, a) -> {
            if (a.length < 1 || dex.get(a[0]) == null) {
                p.sendMessage(Component.text("Unknown species.", NamedTextColor.RED));
                return;
            }
            var loc = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3));
            // extra words are Cobblemon spawn properties: /pokespawn raichu 20 alolan shiny
            spawner.spawn(loc, Mon.create(dex, a[0], a.length > 1 ? Integer.parseInt(a[1]) : 5,
                    a.length > 2 ? List.of(a).subList(2, a.length) : List.of()));
        });
        playerCommand("pokeballs", "/pokeballs [amount] (admin)", "cobblemonlink.admin",
                (p, a) -> p.getInventory().addItem(ball(a.length > 0 ? Integer.parseInt(a[0]) : 16)));
        playerCommand("pokeitem", "/pokeitem <item> [amount]: one of Cobblemon's items (admin); tm:<move> for a TM", "cobblemonlink.admin", (p, a) -> {
            ItemStack it = a.length < 1 ? null : a[0].startsWith("tm:") ? items.tm(a[0].substring(3))
                    : items.create(a[0], a.length > 1 ? Integer.parseInt(a[1]) : 1);
            if (it == null) p.sendMessage(Component.text("Unknown item.", NamedTextColor.RED));
            else p.getInventory().addItem(it);
        });
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
                if (args.length <= 1 && name.equals("pokeitem")) {
                    String prefix = args.length == 0 ? "" : args[0];
                    return items.defs.keySet().stream().filter(s -> s.startsWith(prefix)).sorted().limit(50).toList();
                }
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
