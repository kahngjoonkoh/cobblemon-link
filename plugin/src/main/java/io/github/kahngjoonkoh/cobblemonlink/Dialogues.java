package io.github.kahngjoonkoh.cobblemonlink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Cobblemon's NPC dialogues (data/cobblemon/dialogues): pages of lines from a speaker, then the page's input: an
 * action (a Next button), options (a button each), or nothing (the next page). MoLang runs the actions (Molang.java)
 * with the queries Cobblemon's own dialogues ask: names, battles, the party's health, a nearby Healing Machine.
 * Java sees a vanilla dialog, Bedrock a form.
 * ponytail: no speaker faces, gibber sounds or typing effect; auto-continue pages wait for Next.
 */
final class Dialogues {
    private final CobblemonLink pl;

    Dialogues(CobblemonLink pl) { this.pl = pl; }

    /** One player's run through a dialogue. */
    private final class Run {
        final Player p;
        final Entity npc;
        final Npcs.Npc def;
        JsonObject d;
        final Molang m = new Molang();
        int page;
        boolean closed;

        Run(Player p, Entity npc, Npcs.Npc def, JsonObject d) {
            this.p = p;
            this.npc = npc;
            this.def = def;
            this.d = d;
            String name = pl.lang.getOr(CobblemonLink.loc(p), def.name, def.name);
            m.funcs.put("q.player.username", a -> p.getName());
            m.funcs.put("q.npc.name", a -> name);
            m.funcs.put("q.npc.can_battle", a -> !def.pool.isEmpty());
            m.funcs.put("q.npc.is_in_battle_with", a -> pl.battles.containsKey(p.getUniqueId()));
            m.funcs.put("q.npc.is_doing_activity", a -> true);
            m.funcs.put("q.npc.was_hurt_by", a -> false);
            m.funcs.put("q.player.is_party_at_full_health", a -> pl.store.get(p.getUniqueId()).party.stream()
                    .allMatch(x -> x.hp(pl.dex) >= x.maxHp(pl.dex) && x.status.isEmpty()));
            m.funcs.put("q.npc.find_nearby_block", a -> pl.machines.near(npc.getLocation(), String.valueOf(a.isEmpty() ? "" : a.getFirst()).replace("cobblemon:", ""), 8) ? 1.0 : 0.0);
            m.funcs.put("q.player.can_heal_at_healer", a -> true);
            m.funcs.put("q.player.main_held_item.is_of", a -> p.getInventory().getItemInMainHand().getType().getKey().toString().equals(Molang.text(a.getFirst())));
            m.funcs.put("q.dialogue.close", a -> { closed = true; return 0.0; });
            m.funcs.put("q.dialogue.set_page", a -> { page = index(Molang.text(a.getFirst())); return 0.0; });
            m.funcs.put("q.npc.start_battle", a -> { closed = true; pl.onMain(() -> pl.npcs.battle(p, npc)); return 0.0; });
            m.funcs.put("q.npc.run_action_effect", a -> {
                if (String.valueOf(a.getFirst()).contains("heal")) pl.heal(p);
                return 0.0;
            });
        }

        int index(String id) {
            JsonArray pages = d.getAsJsonArray("pages");
            for (int k = 0; k < pages.size(); k++) {
                JsonElement e = pages.get(k).getAsJsonObject().get("id");
                if (e != null && e.getAsString().equals(id)) return k;
            }
            closed = true;
            return -1;
        }

        void run(JsonElement action) {
            if (action == null) return;
            if (action.isJsonArray()) for (JsonElement a : action.getAsJsonArray()) m.run(a.getAsString());
            else m.run(action.getAsString());
        }

        String text(JsonElement line) {
            if (line.isJsonPrimitive()) return line.getAsString();
            return Molang.text(m.run(line.getAsJsonObject().get("expression").getAsString()));
        }

        /** Shows the current page; buttons run its input and show the next one. */
        void show() {
            JsonArray pages = d.getAsJsonArray("pages");
            if (closed || page < 0 || page >= pages.size()) {
                p.closeDialog();
                String cb = Molang.text(m.vars.getOrDefault("callback_dialogue", ""));
                if (!cb.isEmpty() && !cb.equals("0") && pl.dex.dialogues.containsKey(cb)) start(p, npc, def, cb);
                return;
            }
            JsonObject pg = pages.get(page).getAsJsonObject();
            JsonObject speaker = d.has("speakers") && pg.has("speaker") ? d.getAsJsonObject("speakers").getAsJsonObject(pg.get("speaker").getAsString()) : null;
            String who = speaker == null || !speaker.has("name") ? "" : speaker.get("name").isJsonPrimitive() ? speaker.get("name").getAsString()
                    : Molang.text(m.run(speaker.getAsJsonObject("name").get("expression").getAsString()));
            List<String> lines = new ArrayList<>();
            for (JsonElement l : pg.getAsJsonArray("lines")) lines.add(text(l));
            List<String> labels = new ArrayList<>();
            List<Runnable> acts = new ArrayList<>();
            JsonElement input = pg.get("input");
            int here = page;
            if (input != null && input.isJsonObject() && "option".equals(input.getAsJsonObject().get("type").getAsString())) {
                for (JsonElement o : input.getAsJsonObject().getAsJsonArray("options")) {
                    JsonObject opt = o.getAsJsonObject();
                    if (opt.has("isVisible") && !Molang.truthy(m.run(opt.get("isVisible").getAsString()))) continue;
                    if (opt.has("isSelectable") && !Molang.truthy(m.run(opt.get("isSelectable").getAsString()))) continue;
                    labels.add(opt.get("text").getAsString());
                    acts.add(() -> { run(opt.get("action")); if (page == here && !closed) page++; show(); });
                }
            } else {
                JsonElement action = input == null ? null : input.isJsonObject() ? input.getAsJsonObject().get("action") : input;
                labels.add(pl.t(p, "cobblemonlink.dialogue.next"));
                acts.add(() -> { run(action); if (page == here && !closed) page++; show(); });
            }
            String body = String.join("\n", lines);
            if (Ui.bedrock(p) && Forms.available()) {
                List<Battle.Opt> opts = labels.stream().map(l -> new Battle.Opt(l, "none", null)).toList();
                Forms.send(p, who, body, opts, k -> pl.onMain(() -> { if (k >= 0) acts.get(k).run(); }));
                return;
            }
            var once = ClickCallback.Options.builder().uses(1).build();
            List<ActionButton> buttons = new ArrayList<>();
            for (int k = 0; k < labels.size(); k++) {
                Runnable r = acts.get(k);
                buttons.add(ActionButton.builder(Component.text(labels.get(k))).width(120)
                        .action(DialogAction.customClick((v, a) -> pl.onMain(r), once)).build());
            }
            p.showDialog(Dialog.create(f -> f.empty()
                    .base(DialogBase.builder(Component.text(who)).canCloseWithEscape(true)
                            .body(List.of(DialogBody.plainMessage(Component.text(body), 300))).build())
                    .type(DialogType.multiAction(buttons).columns(Math.min(3, buttons.size())).build())));
        }
    }

    /** Starts NPC dialogue `id` for the player (its initialisation actions first). */
    void start(Player p, Entity npc, Npcs.Npc def, String id) {
        Map<String, Object> raw = pl.dex.dialogues.get(id);
        if (raw == null) return;
        JsonObject d = Store.GSON.toJsonTree(raw).getAsJsonObject();
        Run r = new Run(p, npc, def, d);
        r.run(d.get("initializationAction"));
        r.show();
    }
}
