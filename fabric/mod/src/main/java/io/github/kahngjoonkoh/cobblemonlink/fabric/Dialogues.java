package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.net.messages.client.dialogue.DialogueClosedPacket;
import com.cobblemon.mod.common.net.messages.client.dialogue.DialogueOpenedPacket;
import com.cobblemon.mod.common.net.messages.client.dialogue.dto.DialogueDTO;
import com.cobblemon.mod.common.net.messages.client.dialogue.dto.DialogueInputDTO;
import com.cobblemon.mod.common.net.messages.server.dialogue.EscapeDialoguePacket;
import com.cobblemon.mod.common.net.messages.server.dialogue.InputToDialoguePacket;
import com.cobblemon.mod.common.net.serverhandling.dialogue.EscapeDialogueHandler;
import com.cobblemon.mod.common.net.serverhandling.dialogue.InputToDialogueHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * Cobblemon's dialogues (NPCs, scripted conversations) for players without the mod: each page with its speaker, lines
 * and choices (or a text box, or Continue), answered through Cobblemon's own dialogue handlers. Bedrock: a form;
 * vanilla Java: a chest (the lines on a book, a choice per item).
 * A text answer on Java is typed on a sign (Signs).
 */
final class Dialogues {
    /** What Cobblemon's dialogue box sends to go on to the next page. */
    static final String SKIP = "skip!";

    /** Each player's dialogue speakers: an update to a dialogue (a new page) leaves them out. */
    private static final java.util.Map<java.util.UUID, java.util.Map<String, com.cobblemon.mod.common.net.messages.client.dialogue.dto.DialogueSpeakerDTO>> SPEAKERS = new java.util.concurrent.ConcurrentHashMap<>();

    private Dialogues() {}

    static void packet(ServerPlayer p, CustomPacketPayload payload) {
        if (payload instanceof DialogueOpenedPacket o) p.server.execute(() -> show(p, o.getDialogueDTO()));
        else if (payload instanceof DialogueClosedPacket) p.server.execute(() -> {
            SPEAKERS.remove(p.getUUID());
            if (!Clients.bedrock(p)) p.closeContainer();
        });
    }

    private static void show(ServerPlayer p, DialogueDTO d) {
        var page = d.getCurrentPageDTO();
        if (d.getSpeakers() != null) SPEAKERS.put(p.getUUID(), d.getSpeakers());
        var speakers = SPEAKERS.getOrDefault(p.getUUID(), java.util.Map.of());
        var speaker = page.getSpeaker() == null ? null : speakers.get(page.getSpeaker());
        String title = speaker == null || speaker.getName() == null ? "" : Lang.resolve(p, speaker.getName()).getString();
        List<String> lines = new ArrayList<>();
        for (var l : page.getLines()) lines.add(Lang.resolve(p, l).getString());
        DialogueInputDTO in = d.getDialogueInput();
        List<Forms.Opt> opts = new ArrayList<>();
        Runnable escape = () -> EscapeDialogueHandler.INSTANCE.handle(new EscapeDialoguePacket(), p.server, p);
        switch (in.getInputType()) {
            case OPTION -> {
                for (var o : in.getOptions())
                    if (o.getSelectable()) opts.add(new Forms.Opt(Lang.resolve(p, o.getText()).getString(), "none", () -> answer(p, in, o.getValue())));
            }
            case TEXT -> {
                if (Clients.bedrock(p)) {
                    Forms.input(p, title, String.join("\n", lines), "", t -> { if (t == null) escape.run(); else answer(p, in, t); });
                    return;
                }
                // Java: a sign to type on (placed only on the player's client, the real block put back after)
                p.closeContainer();
                if (!lines.isEmpty()) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(title.isEmpty() ? String.join(" ", lines) : title + ": " + String.join(" ", lines)));
                Signs.ask(p, t -> { if (t.isEmpty()) escape.run(); else answer(p, in, t); }); // nothing written: leave
                return;
            }
            default -> opts.add(new Forms.Opt(Lang.t(p, "cobblemonlink.dialogue.continue"), "none", () -> answer(p, in, SKIP)));
        }
        if (Clients.bedrock(p)) {
            Forms.send(p, title, String.join("\n", lines), opts, i -> { if (i >= 0) opts.get(i).action().run(); else escape.run(); });
            return;
        }
        Chest c = new Chest(1, title);
        c.set(0, Chest.icon(Items.WRITTEN_BOOK, title.isEmpty() ? "..." : title, lines), null);
        for (int i = 0; i < Math.min(opts.size(), 7); i++) c.set(2 + i, Chest.icon(Items.PAPER, opts.get(i).text(), List.of()), opts.get(i).action());
        c.open(p);
    }

    private static void answer(ServerPlayer p, DialogueInputDTO in, String value) {
        InputToDialogueHandler.INSTANCE.handle(new InputToDialoguePacket(in.getInputId(), value), p.server, p);
    }
}
