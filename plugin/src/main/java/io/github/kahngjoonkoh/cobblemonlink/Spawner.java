package io.github.kahngjoonkoh.cobblemonlink;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.TagKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Spawns wild Pokémon near players the way Cobblemon's PlayerSpawner does (defaults from CobblemonConfig): every
 * second per player, an 8x16x8 zone 16-64 blocks away (ahead of a moving player), skipped while the 3x3 chunks
 * around it already hold one Pokémon per chunk; up to 8 spawns per pass, each 8 blocks from any other entity; one
 * rarity bucket per pass; levels from the party (PlayerLevelRangeInfluence); CobblemonAgingDespawner despawning.
 * A wild Pokémon is a silent pig (for vanilla wander AI and a hitbox) wearing the BetterModel model;
 * GeyserModelEngine shows the same model to Bedrock players.
 * Spawn spots are Cobblemon's grounded, submerged, surface and seafloor positions anywhere in the zone (caves too).
 * ponytail: the pig carrier swims rather than dives; fishing spawns wait for Poké Rods.
 */
final class Spawner {
    private final CobblemonLink pl;
    private final Set<UUID> wild = new HashSet<>();
    private final Map<String, Boolean> biomeMatch = new HashMap<>();
    private final Map<UUID, Location> lastPos = new HashMap<>();
    private final Registry<Biome> biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);

    Spawner(CobblemonLink pl) { this.pl = pl; }

    boolean isWild(Entity e) { return e.getPersistentDataContainer().has(pl.wildKey); }

    Mon monOf(Entity e) {
        String json = e.getPersistentDataContainer().get(pl.wildKey, PersistentDataType.STRING);
        return json == null ? null : Store.GSON.fromJson(json, Mon.class);
    }

    void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location was = lastPos.put(p.getUniqueId(), p.getLocation());
            if (p.getGameMode() != GameMode.SPECTATOR) pass(p, was);
        }
        lastPos.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
    }

    /** CobblemonAgingDespawner, checked every 10 s. */
    void cleanup() {
        var c = pl.getConfig();
        double near = c.getDouble("spawning.despawn-near"), far = c.getDouble("spawning.despawn-far");
        int minAge = c.getInt("spawning.despawn-min-age-ticks"), maxAge = c.getInt("spawning.despawn-max-age-ticks");
        wild.removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            if (e == null || !e.isValid()) return true;
            int age = e.getTicksLived();
            if (age < minAge || pl.inBattle(e)) return false;
            double d = e.getWorld().getPlayers().stream().mapToDouble(p -> p.getLocation().distance(e.getLocation()))
                    .min().orElse(Double.MAX_VALUE);
            boolean gone = d >= near && (age > maxAge || d > far || age > (1 - (d - near) / (far - near)) * (maxAge - minAge));
            if (gone) e.remove();
            return gone;
        });
    }

    void removeAll() {
        for (UUID u : wild) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        wild.clear();
    }

    /** PlayerSpawner.getZoneInput + Spawner.calculateSpawnActionsForArea for one player. */
    private void pass(Player p, Location was) {
        var c = pl.getConfig();
        var rnd = ThreadLocalRandom.current();
        int diameter = c.getInt("spawning.zone-diameter"), height = c.getInt("spawning.zone-height");
        double r = rnd.nextDouble(c.getDouble("spawning.min-distance"), c.getDouble("spawning.max-distance"));
        Location at = p.getLocation();
        double dx = was == null ? 0 : (at.getX() - was.getX()) / c.getInt("spawning.interval-ticks");
        double dz = was == null ? 0 : (at.getZ() - was.getZ()) / c.getInt("spawning.interval-ticks");
        double theta = Math.hypot(dx, dz) < 0.1 || was == null || !was.getWorld().equals(at.getWorld())
                ? rnd.nextDouble(2 * Math.PI)                                 // standing: anywhere around
                : Math.atan2(dz, dx) + rnd.nextDouble(-Math.PI / 2, Math.PI / 2); // moving: ahead, +-90 degrees
        World w = p.getWorld();
        int bx = (int) Math.ceil(at.getX() + r * Math.cos(theta) - diameter / 2.0);
        int bz = (int) Math.ceil(at.getZ() + r * Math.sin(theta) - diameter / 2.0);
        int by = (int) Math.ceil(at.getY() - height / 2.0);
        double cx = bx + diameter / 2.0, cz = bz + diameter / 2.0;
        for (int x = (int) cx - 24 >> 4; x <= (int) cx + 24 >> 4; x++)                 // isBoxLoaded
            for (int z = (int) cz - 24 >> 4; z <= (int) cz + 24 >> 4; z++) if (!w.isChunkLoaded(x, z)) return;
        long nearby = w.getNearbyEntities(new Location(w, cx, at.getY(), cz), 24, 512, 24, this::isWild).size();
        if (nearby / 9.0 >= c.getDouble("spawning.per-chunk")) return;               // 3x3 chunks, one each

        double spacing = c.getDouble("spawning.entity-spacing");
        List<Location> others = new ArrayList<>(w.getNearbyEntities(new Location(w, cx, at.getY(), cz),
                diameter / 2.0 + spacing, height / 2.0 + spacing, diameter / 2.0 + spacing).stream()
                .filter(e -> !e.equals(p)).map(Entity::getLocation).toList());
        List<Block> spots = new ArrayList<>();
        for (int x = bx; x < bx + diameter; x++)
            for (int z = bz; z < bz + diameter; z++)
                for (int y = by; y < by + height; y++) {
                    Block spot = w.getBlockAt(x, y, z);
                    if (position(spot) == null) continue;
                    Location l = spot.getLocation().add(0.5, 0, 0.5);
                    if (others.stream().noneMatch(o -> o.distance(l) < spacing)) spots.add(spot);
                }
        String bucket = bucket();
        java.util.Collections.shuffle(spots);
        int left = c.getInt("spawning.max-per-pass");
        for (Block spot : spots) {
            if (left == 0) break;
            Location l = spot.getLocation().add(0.5, 0, 0.5);
            if (others.stream().anyMatch(o -> o.distance(l) < spacing)) continue;   // SpawnDetail.onSelection
            String type = position(spot);
            List<Dex.Spawn> fits = new ArrayList<>();
            for (Dex.Spawn s : pl.dex.spawns) if (bucket.equals(s.bucket) && s.type.equals(type) && fits(s, spot)) fits.add(s);
            if (fits.isEmpty()) continue;
            double wsum = fits.stream().mapToDouble(s -> s.weight).sum(), roll = rnd.nextDouble(wsum);
            Dex.Spawn pick = fits.getLast();
            for (Dex.Spawn s : fits) if ((roll -= s.weight) <= 0) { pick = s; break; }
            Entity e = spawn(l, Mon.create(pl.dex, pick.species, level(p, pick.level[0], pick.level[1]), pick.props));
            if (type.equals("submerged") || type.equals("seafloor")) Bukkit.getMobGoals().removeGoal((Mob) e, VanillaGoal.FLOAT); // stays down
            others.add(l);
            left--;
        }
    }

    /**
     * A Poké Rod's catch (Cobblemon's fishing spawns at the bobber): one rarity bucket, the spawns that fit the water
     * there and the rod; the Pokémon is "fished" (Lure Ball) and, under 90 kg, lobbed toward the player. Null: no bite.
     */
    Entity fish(Player p, Location hook, int lure, String rod) {
        String bucket = bucket();
        List<Dex.Spawn> fits = new ArrayList<>();
        for (Dex.Spawn s : pl.dex.spawns) if (s.type.equals("fishing") && bucket.equals(s.bucket) && fits(s, hook.getBlock(), lure, rod)) fits.add(s);
        if (fits.isEmpty()) return null;
        double roll = ThreadLocalRandom.current().nextDouble(fits.stream().mapToDouble(s -> s.weight).sum());
        Dex.Spawn pick = fits.getLast();
        for (Dex.Spawn s : fits) if ((roll -= s.weight) <= 0) { pick = s; break; }
        Mon m = Mon.create(pl.dex, pick.species, level(p, pick.level[0], pick.level[1]), pick.props);
        m.fished = true;
        Entity e = spawn(hook, m);
        if (pl.dex.of(m).weight < 900) // Cobblemon's lobPokemonTowardsTarget
            e.setVelocity(p.getLocation().toVector().subtract(hook.toVector()).multiply(0.12).setY(0.6));
        return e;
    }

    /** PlayerLevelRangeInfluence: the party's highest level +-5 (at least up to 10), inside the spawn's range. */
    int level(Player p, int min, int max) {
        int top = pl.store.get(p.getUniqueId()).party.stream().mapToInt(m -> m.level).max().orElse(0);
        int[] r = Rules.levelRange(top, min, max);
        return ThreadLocalRandom.current().nextInt(r[0], r[1] + 1);
    }

    /** One bucket per pass, from all of Cobblemon's world buckets (a pass with nothing in it spawns nothing). */
    private String bucket() {
        var buckets = pl.getConfig().getConfigurationSection("spawning.buckets");
        double roll = ThreadLocalRandom.current().nextDouble(buckets.getKeys(false).stream().mapToDouble(buckets::getDouble).sum());
        for (String b : buckets.getKeys(false)) if ((roll -= buckets.getDouble(b)) <= 0) return b;
        return "common";
    }

    Entity spawn(Location loc, Mon mon) {
        Pig pig = loc.getWorld().spawn(loc, Pig.class, e -> {
            e.setPersistent(false); // never saved with the chunk: models don't survive a reload anyway
            e.setRemoveWhenFarAway(true);
            e.setSilent(true);
            e.getPersistentDataContainer().set(pl.wildKey, PersistentDataType.STRING, Store.GSON.toJson(mon));
        });
        model(pig, mon.model(pl.dex));
        // Cobblemon's wild Pokémon don't follow food or breed like pigs (Paper keeps float, wander and look goals)
        var goals = Bukkit.getMobGoals();
        goals.removeGoal(pig, VanillaGoal.TEMPT);
        goals.removeGoal(pig, VanillaGoal.BREED);
        goals.removeGoal(pig, VanillaGoal.FOLLOW_PARENT);
        wild.add(pig.getUniqueId());
        return pig;
    }

    /** A party Pokémon out in the world (Companions): unhurtable, not wild, no AI of its own (Companions moves it). */
    Entity companion(Location loc, String model, Player owner) {
        Pig pig = loc.getWorld().spawn(loc, Pig.class, e -> {
            e.setPersistent(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.getPersistentDataContainer().set(pl.companionKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        });
        Bukkit.getMobGoals().removeAllGoals(pig);
        model(pig, model);
        return pig;
    }

    /** A pastured Pokémon: wanders like a wild one (pig goals), but can't be hurt or battled. */
    Entity roamer(Location loc, String model) {
        Pig pig = loc.getWorld().spawn(loc, Pig.class, e -> {
            e.setPersistent(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.getPersistentDataContainer().set(pl.companionKey, PersistentDataType.STRING, "pasture");
        });
        var goals = Bukkit.getMobGoals();
        goals.removeGoal(pig, VanillaGoal.TEMPT);
        goals.removeGoal(pig, VanillaGoal.BREED);
        goals.removeGoal(pig, VanillaGoal.FOLLOW_PARENT);
        model(pig, model);
        return pig;
    }

    boolean isCompanion(Entity e) { return e.getPersistentDataContainer().has(pl.companionKey); }

    /** The player's own Pokémon in a battle: same carrier, but still, unhurtable and not wild. */
    Entity sendOut(Location loc, String model) {
        Pig pig = loc.getWorld().spawn(loc, Pig.class, e -> {
            e.setPersistent(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setAware(false);
        });
        face(pig, loc.clone().add(loc.getDirection()));
        model(pig, model);
        return pig;
    }

    /** The Poké Ball's model (PokeBalls) on a still carrier that PokeBalls moves. */
    Entity ball(Location loc) {
        Pig pig = loc.getWorld().spawn(loc, Pig.class, e -> {
            e.setPersistent(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setAware(false);
            e.setGravity(false);
            e.setCollidable(false);
        });
        model(pig, "poke_ball");
        return pig;
    }

    /**
     * Plays one of the model's animations over its idle (a battle one, a cry, the ball's shakes): BetterModel runs it,
     * GeyserModelEngine mirrors it to Bedrock. False when the model has no such animation (it keeps its idle).
     */
    static boolean animate(Entity e, String anim, AnimationIterator.Type type) {
        var r = BetterModel.registryOrNull(BukkitAdapter.adapt(e));
        boolean played = false;
        if (r != null) for (var t : r.trackers()) played |= t.animate(anim, AnimationModifier.builder().type(type).build());
        return played;
    }

    static void stopAnimation(Entity e, String anim) {
        var r = BetterModel.registryOrNull(BukkitAdapter.adapt(e));
        if (r != null) for (var t : r.trackers()) t.stopAnimation(anim);
    }

    /** Hides the model (inside a Poké Ball) by scaling it to nothing: GeyserModelEngine sends the scale to Bedrock too. */
    static void hide(Entity e, boolean hidden) {
        var r = BetterModel.registryOrNull(BukkitAdapter.adapt(e));
        if (r == null) return;
        for (var t : r.trackers()) {
            ModelScaler was = SHOWN.computeIfAbsent(e.getUniqueId(), k -> t.scaler());
            t.scaler(hidden ? ModelScaler.value(0.001F) : was);
        }
        if (!hidden) SHOWN.remove(e.getUniqueId());
    }

    private static final Map<UUID, ModelScaler> SHOWN = new HashMap<>(); // a hidden model's own scaler

    /**
     * The BetterModel model on a carrier, animated for Java players only: Bedrock players see GeyserModelEngine's
     * native entity (animated on the phone), and Geyser would only decode and drop the Java bone stream.
     */
    private static void model(Pig pig, String model) {
        BetterModel.model(model).ifPresent(r -> r.create(BukkitAdapter.adapt(pig)).getPipeline()
                .viewFilter(p -> p.uuid().getMostSignificantBits() != 0)); // Ui.bedrock: Floodgate's UUIDs
    }

    static void face(Entity e, Location target) {
        Location l = e.getLocation();
        l.setDirection(target.toVector().subtract(l.toVector()));
        e.setRotation(l.getYaw(), 0);
        if (e instanceof org.bukkit.entity.LivingEntity le) le.setBodyYaw(l.getYaw()); // the model follows the body
    }

    static void freeze(Entity e, boolean frozen) {
        if (e instanceof Mob m) m.setAware(!frozen);
    }

    /**
     * Cobblemon's spawnable position for this block, or null: grounded (on a solid block, 2 blocks of room), submerged
     * (2 blocks of water), surface (air over water) or seafloor (water over a solid block).
     */
    static String position(Block b) {
        Block below = b.getRelative(0, -1, 0), above = b.getRelative(0, 1, 0);
        boolean water = b.getType() == org.bukkit.Material.WATER, waterBelow = below.getType() == org.bukkit.Material.WATER;
        boolean solidBelow = below.getType().isSolid();
        if (water) return solidBelow ? "seafloor" : above.getType() == org.bukkit.Material.WATER ? "submerged" : null;
        if (!b.isPassable() || b.isLiquid() || !above.isPassable() || above.isLiquid()) return null;
        return solidBelow ? "grounded" : waterBelow ? "surface" : null;
    }

    private boolean fits(Dex.Spawn s, Block at) { return fits(s, at, -1, null); }

    /** lure / rod: the Poké Rod's Lure level and id when fishing (-1 / null otherwise). */
    private boolean fits(Dex.Spawn s, Block at, int lure, String rod) {
        return check(s.cond, at, lure, rod) && (s.anti == null || !check(s.anti, at, lure, rod));
    }

    /** Every condition c sets holds at this block (Cobblemon's SpawningCondition.fits). */
    private boolean check(Dex.Cond c, Block at, int lure, String rod) {
        World w = at.getWorld();
        if (c.minLureLevel != null && lure < c.minLureLevel || c.maxLureLevel != null && lure > c.maxLureLevel) return false;
        if (c.rodType != null && !c.rodType.equals("cobblemon:" + rod)) return false;
        if (c.timeRange != null && !Evolutions.inTime(c.timeRange, w.getTime() % 24000)) return false; // Cobblemon's TimeRanges
        if (c.isRaining != null && c.isRaining != w.hasStorm()) return false;
        if (c.isThundering != null && c.isThundering != w.isThundering()) return false;
        if (c.minY != null && at.getY() < c.minY || c.maxY != null && at.getY() > c.maxY) return false;
        if (c.minX != null && at.getX() < c.minX || c.maxX != null && at.getX() > c.maxX) return false;
        int sky = at.getLightFromSky(), light = at.getLightLevel();
        if (c.minSkyLight != null && sky < c.minSkyLight || c.maxSkyLight != null && sky > c.maxSkyLight) return false;
        if (c.minLight != null && light < c.minLight || c.maxLight != null && light > c.maxLight) return false;
        if (c.canSeeSky != null && c.canSeeSky != (w.getHighestBlockYAt(at.getX(), at.getZ()) < at.getY())) return false;
        if (c.isSlimeChunk != null && c.isSlimeChunk != at.getChunk().isSlimeChunk()) return false;
        if (c.moonPhase != null && !Rules.moonPhase(c.moonPhase, (int) (w.getFullTime() / 24000 % 8))) return false;
        if (c.neededBaseBlocks != null && c.neededBaseBlocks.stream().noneMatch(id -> isBlock(id, at.getRelative(0, -1, 0)))) return false;
        if (c.neededNearbyBlocks != null && !nearby(c.neededNearbyBlocks, at)) return false;
        if (c.structures != null && !inStructure(c.structures, at)) return false;
        if (c.biomes != null) {
            Key biome = biomes.getKey(at.getBiome());
            if (biome == null || c.biomes.stream().noneMatch(b -> inBiome(b, biome))) return false;
        }
        return true;
    }

    /** A block id ("minecraft:water") or a block tag ("#minecraft:logs", "#c:sand"). */
    private static boolean isBlock(String id, Block b) {
        if (!id.startsWith("#")) return b.getType().getKey().toString().equals(id);
        var tag = Bukkit.getTag(org.bukkit.Tag.REGISTRY_BLOCKS, org.bukkit.NamespacedKey.fromString(id.substring(1)), org.bukkit.Material.class);
        return tag != null && tag.isTagged(b.getType());
    }

    /** Cobblemon's nearby blocks: 4 blocks around, 2 up and down (maxNearbyBlocks*Range). */
    private static boolean nearby(List<String> ids, Block at) {
        for (int dx = -4; dx <= 4; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -4; dz <= 4; dz++) {
                    Block b = at.getRelative(dx, dy, dz);
                    for (String id : ids) if (isBlock(id, b)) return true;
                }
        return false;
    }

    /** Inside one of these structures (ids or "#tags"): a generated structure whose box holds the block. */
    private boolean inStructure(List<String> ids, Block at) {
        for (var g : at.getWorld().getStructures(at.getX() >> 4, at.getZ() >> 4)) {
            if (!g.getBoundingBox().contains(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5)) continue;
            Key key = structures.getKey(g.getStructure());
            if (key == null) continue;
            for (String id : ids) {
                if (!id.startsWith("#")) { if (Key.key(id).equals(key)) return true; continue; }
                TagKey<org.bukkit.generator.structure.Structure> tag = TagKey.create(RegistryKey.STRUCTURE, Key.key(id.substring(1)));
                if (structures.hasTag(tag) && structures.getTag(tag).contains(TypedKey.create(RegistryKey.STRUCTURE, key))) return true;
            }
        }
        return false;
    }

    private final Registry<org.bukkit.generator.structure.Structure> structures = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);

    /** Is this location's biome in id (a biome or a "#tag")? */
    boolean inBiome(String id, org.bukkit.Location at) {
        Key biome = biomes.getKey(at.getBlock().getBiome());
        return biome != null && inBiome(id, biome);
    }

    /** "#cobblemon:is_forest" resolves through the bundled datapack's biome tags; plain ids compare directly. */
    private boolean inBiome(String id, Key biome) {
        return biomeMatch.computeIfAbsent(id + "@" + biome.asString(), k -> {
            if (!id.startsWith("#")) return Key.key(id).equals(biome);
            TagKey<Biome> tag = TagKey.create(RegistryKey.BIOME, Key.key(id.substring(1)));
            return biomes.hasTag(tag) && biomes.getTag(tag).contains(TypedKey.create(RegistryKey.BIOME, biome));
        });
    }
}
