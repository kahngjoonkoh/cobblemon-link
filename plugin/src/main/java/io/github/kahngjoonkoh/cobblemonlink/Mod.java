package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * The optional client mod (client/ in this repo): Java players who have it get the Bedrock forms' screens (battle
 * menus, starter, PC: a title ending in Forms.MARK + kind, a text, buttons of text + image) drawn by their own client,
 * instead of the chest and dialog screens. The mod announces itself by registering FORM; a tap comes back on CLICK
 * as the button's index (-1: closed).
 * Form JSON: {"title", "content", "buttons": [[text, image], ...]} or {"close": true}.
 */
final class Mod implements PluginMessageListener, Listener {
    static final String FORM = "cobblemonlink:form", CLICK = "cobblemonlink:click";
    private static final Set<UUID> modded = new HashSet<>();
    private static final Map<UUID, IntConsumer> pending = new HashMap<>();
    private static final Set<UUID> sentNow = new HashSet<>();
    private static CobblemonLink pl;

    static void enable(CobblemonLink plugin) {
        pl = plugin;
        Mod m = new Mod();
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, FORM);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CLICK, m);
        plugin.getServer().getPluginManager().registerEvents(m, plugin);
    }

    static boolean has(Player p) { return modded.contains(p.getUniqueId()); }

    /** Screens drawn by the client from a form: the mod's players, and Bedrock's (Floodgate). */
    static boolean form(Player p) { return has(p) || Ui.bedrock(p) && Forms.available(); }

    /** A form to whoever draws it: the mod, or Floodgate. clicked gets the button's index, or -1 when closed. */
    static void send(Player p, String title, String content, List<Battle.Opt> opts, IntConsumer clicked) {
        if (!has(p)) {
            Forms.send(p, title, content, opts, clicked);
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("title", title);
        o.addProperty("content", content);
        JsonArray b = new JsonArray();
        for (Battle.Opt opt : opts) {
            JsonArray x = new JsonArray();
            x.add(opt.text());
            x.add(opt.image());
            b.add(x);
        }
        o.add("buttons", b);
        pending.put(p.getUniqueId(), clicked);
        sentNow.add(p.getUniqueId());
        p.sendPluginMessage(pl, FORM, o.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static final Map<UUID, String> lastHud = new HashMap<>();

    /**
     * Cobblemon's party overlay (the mod draws it on the left): {"hud": [[text, image], ...]}, one a party member,
     * text "level|hp per mille|name|gender" (gender M, F or empty), image its portrait. Sent when it changes.
     */
    static void hud(Player p) {
        if (!has(p)) return;
        Store.Data d = pl.store.get(p.getUniqueId());
        JsonArray a = new JsonArray();
        for (Mon m : d.party) {
            JsonArray x = new JsonArray();
            x.add(m.level + "|" + m.hp(pl.dex) * 1000 / Math.max(1, m.maxHp(pl.dex)) + "|" + m.name(pl.dex) + "|" + m.gender);
            x.add(m.model(pl.dex));
            a.add(x);
        }
        JsonObject o = new JsonObject();
        o.add("hud", a);
        String json = o.toString();
        if (json.equals(lastHud.put(p.getUniqueId(), json))) return;
        p.sendPluginMessage(pl, FORM, json.getBytes(StandardCharsets.UTF_8));
    }

    /** Closes the mod's screen (as a Bedrock form closes itself once tapped). */
    static void close(Player p) {
        if (!has(p)) return;
        pending.remove(p.getUniqueId());
        p.sendPluginMessage(pl, FORM, "{\"close\":true}".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] msg) {
        if (!CLICK.equals(channel)) return;
        int i;
        try {
            i = JsonParser.parseString(new String(msg, StandardCharsets.UTF_8)).getAsJsonObject().get("i").getAsInt();
        } catch (RuntimeException e) {
            return;
        }
        pl.onMain(() -> {
            IntConsumer c = pending.remove(p.getUniqueId());
            sentNow.remove(p.getUniqueId());
            if (c != null) c.accept(i);
            // a tap that opened no new screen (it chose, or waits on the battle) closes this one, as on Bedrock;
            // checked a tick later, as the handlers run their action on the next tick
            if (i >= 0) pl.onMain(() -> { if (!sentNow.remove(p.getUniqueId())) close(p); });
        });
    }

    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent e) {
        if (FORM.equals(e.getChannel())) modded.add(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        modded.remove(e.getPlayer().getUniqueId());
        lastHud.remove(e.getPlayer().getUniqueId());
        pending.remove(e.getPlayer().getUniqueId());
    }
}
