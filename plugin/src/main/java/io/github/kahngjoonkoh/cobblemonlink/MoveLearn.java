package io.github.kahngjoonkoh.cobblemonlink;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Cobblemon's move learning: a party Pokémon that wanted a 5th move (Mon.learnable) asks which move to forget, or not
 * to learn it. A form (MARK + 7: Bedrock pack, client mod): buttons 0-3 the known moves, 4 "Don't learn"; the title
 * says which move. Vanilla Java: a dialog. One question at a time, after each battle.
 */
final class MoveLearn {
    private MoveLearn() {}

    static void next(CobblemonLink pl, Player p) {
        if (!p.isOnline() || pl.battles.containsKey(p.getUniqueId())) return;
        Store.Data d = pl.store.get(p.getUniqueId());
        for (Mon m : d.party) {
            if (m.learnable == null || m.learnable.isEmpty()) continue;
            String move = m.learnable.getFirst();
            if (m.moves.contains(move) || m.moves.size() < 4) { // learned meanwhile, or there's room after all
                m.learnable.removeFirst();
                if (!m.moves.contains(move)) m.moves.add(move);
                continue;
            }
            ask(pl, p, m, move);
            return;
        }
    }

    private static void ask(CobblemonLink pl, Player p, Mon m, String move) {
        String name = m.name(pl.dex), label = pl.dex.label("move", move);
        Runnable skip = () -> {
            m.learnable.remove(move);
            pl.store.save(p.getUniqueId());
            p.sendMessage(Component.text(name + " did not learn " + label + ".", NamedTextColor.AQUA));
            next(pl, p);
        };
        List<Battle.Opt> o = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            int kk = k;
            String old = pl.dex.label("move", m.moves.get(k));
            o.add(new Battle.Opt(old, "none", () -> {
                m.moves.set(kk, move);
                m.learnable.remove(move);
                pl.store.save(p.getUniqueId());
                p.sendMessage(Component.text("1, 2, and... Poof! " + name + " forgot " + old + ". And... " + name
                        + " learned " + label + "!", NamedTextColor.AQUA));
                next(pl, p);
            }));
        }
        o.add(new Battle.Opt("Don't learn", "none", skip));
        if (Mod.form(p)) {
            Mod.send(p, name + " wants to learn " + label + Forms.MARK + 7, "Which move should " + name + " forget?", o,
                    i -> pl.onMain(() -> { if (i >= 0 && i < o.size()) o.get(i).action().run(); }));
            return;
        }
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        List<ActionButton> buttons = new ArrayList<>();
        for (Battle.Opt opt : o)
            buttons.add(ActionButton.builder(Component.text(opt.text())).width(120)
                    .action(DialogAction.customClick((r, w) -> pl.onMain(opt.action()), once)).build());
        p.showDialog(Dialog.create(f -> f.empty().base(DialogBase.builder(Component.text(name + " wants to learn " + label))
                        .canCloseWithEscape(false)
                        .body(List.of(DialogBody.plainMessage(Component.text("Which move should " + name + " forget?"), 240))).build())
                .type(DialogType.multiAction(buttons).columns(2).build())));
    }
}
