package io.github.kahngjoonkoh.cobblemonlink;

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
 * Spawns wild Pokémon near players from Cobblemon's spawn pools, capped per player.
 * A wild Pokémon is a silent pig (for vanilla wander AI and a hitbox) wearing the BetterModel model;
 * GeyserModelEngine shows the same model to Bedrock players.
 * ponytail: grounded spawns only; water, flying, cave and structure spawns need other carriers/checks.
 */
final class Spawner {
    private final CobblemonLink pl;
    private final Set<UUID> wild = new HashSet<>();
    private final Map<String, Boolean> biomeMatch = new HashMap<>();
    private final Registry<Biome> biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);

    Spawner(CobblemonLink pl) { this.pl = pl; }

    boolean isWild(Entity e) { return e.getPersistentDataContainer().has(pl.wildKey); }

    Mon monOf(Entity e) {
        String json = e.getPersistentDataContainer().get(pl.wildKey, PersistentDataType.STRING);
        return json == null ? null : Store.GSON.fromJson(json, Mon.class);
    }

    void tick() {
        int cap = pl.getConfig().getInt("spawning.per-player-cap");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || pl.battles.containsKey(p.getUniqueId())) continue;
            int r = pl.getConfig().getInt("spawning.count-radius");
            if (p.getWorld().getNearbyEntities(p.getLocation(), r, r, r, this::isWild).size() < cap) trySpawn(p);
        }
    }

    void cleanup() {
        int far = pl.getConfig().getInt("spawning.despawn-distance");
        long maxAge = pl.getConfig().getLong("spawning.lifetime-seconds") * 20;
        wild.removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            if (e == null || !e.isValid()) return true;
            if (pl.inBattle(e)) return false;
            boolean nobodyNear = e.getWorld().getNearbyPlayers(e.getLocation(), far).isEmpty();
            if (nobodyNear || e.getTicksLived() > maxAge) {
                e.remove();
                return true;
            }
            return false;
        });
    }

    void removeAll() {
        for (UUID u : wild) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        wild.clear();
    }

    private void trySpawn(Player p) {
        var rnd = ThreadLocalRandom.current();
        int min = pl.getConfig().getInt("spawning.min-distance"), max = pl.getConfig().getInt("spawning.max-distance");
        double angle = rnd.nextDouble(Math.PI * 2), dist = rnd.nextDouble(min, max);
        World w = p.getWorld();
        int x = (int) Math.floor(p.getX() + Math.cos(angle) * dist), z = (int) Math.floor(p.getZ() + Math.sin(angle) * dist);
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        Block ground = w.getHighestBlockAt(x, z);
        if (!ground.getType().isSolid()) return; // water, lava, leaves-only columns
        Block at = ground.getRelative(0, 1, 0);
        List<Dex.Spawn> fits = new ArrayList<>();
        for (Dex.Spawn s : pl.dex.spawns) if (fits(s, at)) fits.add(s);
        Dex.Spawn pick = pick(fits);
        if (pick == null) return;
        int level = rnd.nextInt(pick.level[0], pick.level[1] + 1);
        spawn(at.getLocation().add(0.5, 0, 0.5), Mon.create(pl.dex, pick.species, level));
    }

    Entity spawn(Location loc, Mon mon) {
        Pig pig = loc.getWorld().spawn(loc, Pig.class, e -> {
            e.setPersistent(false); // never saved with the chunk: models don't survive a reload anyway
            e.setRemoveWhenFarAway(true);
            e.setSilent(true);
            e.getPersistentDataContainer().set(pl.wildKey, PersistentDataType.STRING, Store.GSON.toJson(mon));
        });
        BetterModel.model(mon.species).ifPresent(r -> r.create(BukkitAdapter.adapt(pig)));
        wild.add(pig.getUniqueId());
        return pig;
    }

    static void freeze(Entity e, boolean frozen) {
        if (e instanceof Mob m) m.setAware(!frozen);
    }

    private boolean fits(Dex.Spawn s, Block at) {
        World w = at.getWorld();
        long t = w.getTime();
        boolean day = t >= 23460 || t < 12542;
        if ("day".equals(s.timeRange) && !day) return false;
        if ("night".equals(s.timeRange) && day) return false;
        if ("dusk".equals(s.timeRange) && (t < 12000 || t > 14000)) return false;
        if (s.isRaining != null && s.isRaining != w.hasStorm()) return false;
        if (s.minY != null && at.getY() < s.minY || s.maxY != null && at.getY() > s.maxY) return false;
        int sky = at.getLightFromSky();
        if (s.minSkyLight != null && sky < s.minSkyLight || s.maxSkyLight != null && sky > s.maxSkyLight) return false;
        if (Boolean.FALSE.equals(s.canSeeSky)) return false; // surface spawner: we only ever see sky
        Key biome = biomes.getKey(at.getBiome());
        if (biome == null) return false;
        return s.biomes.stream().anyMatch(b -> inBiome(b, biome)) && s.antiBiomes.stream().noneMatch(b -> inBiome(b, biome));
    }

    /** "#cobblemon:is_forest" resolves through the bundled datapack's biome tags; plain ids compare directly. */
    private boolean inBiome(String id, Key biome) {
        return biomeMatch.computeIfAbsent(id + "@" + biome.asString(), k -> {
            if (!id.startsWith("#")) return Key.key(id).equals(biome);
            TagKey<Biome> tag = TagKey.create(RegistryKey.BIOME, Key.key(id.substring(1)));
            return biomes.hasTag(tag) && biomes.getTag(tag).contains(TypedKey.create(RegistryKey.BIOME, biome));
        });
    }

    /** Cobblemon picks a rarity bucket first, then a weighted entry inside it. */
    private Dex.Spawn pick(List<Dex.Spawn> fits) {
        if (fits.isEmpty()) return null;
        var rnd = ThreadLocalRandom.current();
        var buckets = pl.getConfig().getConfigurationSection("spawning.buckets");
        Map<String, List<Dex.Spawn>> by = new HashMap<>();
        for (Dex.Spawn s : fits) by.computeIfAbsent(s.bucket, b -> new ArrayList<>()).add(s);
        double total = by.keySet().stream().mapToDouble(b -> buckets.getDouble(b, 0)).sum();
        if (total <= 0) return null;
        double roll = rnd.nextDouble(total);
        for (var e : by.entrySet()) {
            roll -= buckets.getDouble(e.getKey(), 0);
            if (roll > 0) continue;
            double wsum = e.getValue().stream().mapToDouble(s -> s.weight).sum(), r2 = rnd.nextDouble(wsum);
            for (Dex.Spawn s : e.getValue()) if ((r2 -= s.weight) <= 0) return s;
            return e.getValue().getLast();
        }
        return null;
    }
}
