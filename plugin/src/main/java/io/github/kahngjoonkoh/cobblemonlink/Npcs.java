package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Cobblemon's NPC trainers (data/cobblemon/npcs + npc_presets, gamedata.json "npcs"): /spawnnpc puts one down as a
 * Mannequin (a player-shaped entity both clients draw); right-clicking it starts a battle against its party, which
 * picks its moves with the sim's TrainerAI at the NPC's skill.
 * ponytail: the default player skin, not Cobblemon's NPC models; no dialogues.
 */
final class Npcs {
    /** One NPC class: name, skill (0-5), party size, Cobblemon's pool party. */
    static final class Npc {
        String name, dialogue; // dialogue: what right-clicking it opens (else a battle)
        int skill, min = 1, max = 6;
        List<Entry> pool = List.of();
    }

    static final class Entry {
        String species;
        int weight = 1, times = 1;
        List<Integer> levels = List.of(5, 5);
    }

    private final CobblemonLink pl;
    private final NamespacedKey key, partyKey;

    Npcs(CobblemonLink pl) {
        this.pl = pl;
        key = new NamespacedKey(pl, "npc");
        partyKey = new NamespacedKey(pl, "npc_party");
    }

    /** Cobblemon's PoolPartyProvider: min..max Pokémon, weighted picks, each entry at most `times` times. */
    static List<Mon> party(Dex dex, Npc n, Random r) {
        List<Entry> left = new ArrayList<>(n.pool);
        Map<Entry, Integer> used = new java.util.HashMap<>();
        List<Mon> out = new ArrayList<>();
        int count = n.min + r.nextInt(Math.max(1, n.max - n.min + 1));
        while (out.size() < count && !left.isEmpty()) {
            int total = left.stream().mapToInt(e -> e.weight).sum(), pick = r.nextInt(Math.max(1, total));
            Entry e = left.getLast();
            for (Entry x : left) if ((pick -= x.weight) < 0) { e = x; break; }
            int lo = e.levels.getFirst(), hi = e.levels.getLast();
            out.add(Mon.create(dex, e.species, lo + r.nextInt(hi - lo + 1)));
            if (used.merge(e, 1, Integer::sum) >= e.times) left.remove(e);
        }
        return out;
    }

    boolean isNpc(Entity e) { return e.getPersistentDataContainer().has(key); }

    /** /spawnnpc: the NPC at the player's feet, its party rolled once (Cobblemon's isStatic pools). */
    String spawn(Location at, String id) {
        Npc n = pl.dex.npcs.get(id);
        if (n == null) throw new IllegalArgumentException("Unknown NPC. Known: " + String.join(", ", pl.dex.npcs.keySet()));
        List<Mon> team = party(pl.dex, n, new Random());
        at.getWorld().spawn(at, Mannequin.class, m -> {
            m.customName(Component.text(pl.lang.getOr("en_us", n.name, n.name)));
            m.setCustomNameVisible(true);
            m.setDescription(n.pool.isEmpty() ? null : Component.text("Trainer", NamedTextColor.GRAY)); // null: no second line under the name
            m.setInvulnerable(true);
            m.setImmovable(true);
            m.setPersistent(true);
            m.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            m.getPersistentDataContainer().set(partyKey, PersistentDataType.STRING, Store.GSON.toJson(team));
        });
        String name = pl.lang.getOr("en_us", n.name, n.name);
        return n.pool.isEmpty() ? name + " is here." : name + " is here with " + team.size() + " Pokémon.";
    }

    /** Right-click: the NPC's dialogue, or straight into a battle when it has none. */
    void challenge(Player p, Entity npc) {
        Npc n = pl.dex.npcs.get(npc.getPersistentDataContainer().get(key, PersistentDataType.STRING));
        if (n == null || pl.battles.containsKey(p.getUniqueId())) return;
        if (n.dialogue != null && pl.dex.dialogues.containsKey(n.dialogue)) pl.dialogues.start(p, npc, n, n.dialogue);
        else battle(p, npc, "singles");
    }

    /** A battle against the NPC's party, healed as it was spawned. */
    void battle(Player p, Entity npc, String format) {
        Npc n = pl.dex.npcs.get(npc.getPersistentDataContainer().get(key, PersistentDataType.STRING));
        if (n == null || n.pool.isEmpty() || pl.battles.containsKey(p.getUniqueId())) return;
        if (pl.inBattle(npc)) {
            p.sendMessage(Component.text(pl.t(p, "cobblemonlink.battle.busy"), NamedTextColor.RED));
            return;
        }
        if (pl.store.get(p.getUniqueId()).party.isEmpty()) return;
        List<Mon> team = Store.GSON.fromJson(npc.getPersistentDataContainer().get(partyKey, PersistentDataType.STRING),
                new TypeToken<List<Mon>>() {}.getType());
        pl.companions.recallAll(p);
        Battle b = Battle.trainer(pl, p, npc, pl.lang.getOr(CobblemonLink.loc(p), n.name, n.name), team, n.skill);
        b.format = format;
        pl.battles.put(p.getUniqueId(), b);
        if (!b.start()) pl.battles.remove(p.getUniqueId());
    }
}
