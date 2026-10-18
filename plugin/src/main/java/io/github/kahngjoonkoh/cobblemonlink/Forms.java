package io.github.kahngjoonkoh.cobblemonlink;

import java.util.List;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

/** Bedrock battle forms through Floodgate. Kept apart so Battle loads without Floodgate (its classes are optional). */
final class Forms {
    /** Ends a battle form's title; the Bedrock pack keys its layout on it (build_bedrock_ui.py MARK). */
    static final String MARK = "§r§l§";

    static boolean available() { return Bukkit.getPluginManager().isPluginEnabled("floodgate"); }

    /** clicked gets the button's index, or -1 when the player closes the form. */
    static void send(Player p, String title, String content, List<Battle.Opt> opts, IntConsumer clicked) {
        var b = SimpleForm.builder().title(title).content(content);
        for (Battle.Opt o : opts)  // a bare name is battle art (textures/cobblemonlink/), a path is used as it is
            b.button(o.text(), FormImage.Type.PATH, o.image().startsWith("textures/") ? o.image() : "textures/cobblemonlink/" + o.image());
        b.validResultHandler(r -> clicked.accept(r.clickedButtonId()));
        b.closedOrInvalidResultHandler(() -> clicked.accept(-1));
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), b.build());
    }

    /** The PC's box settings: a name field and a wallpaper dropdown; done gets (null, -1) when closed. */
    static void boxSettings(Player p, String title, String name, List<String> wallpapers, int wallpaper,
                            java.util.function.BiConsumer<String, Integer> done) {
        var b = org.geysermc.cumulus.form.CustomForm.builder().title(title)
                .input("Name", "Box name", name).dropdown("Wallpaper", wallpapers, wallpaper);
        b.validResultHandler(r -> done.accept(r.asInput(0), r.asDropdown(1)));
        b.closedOrInvalidResultHandler(() -> done.accept(null, -1));
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), b.build());
    }

    /** One text field; done gets the text, or null when closed. */
    static void input(Player p, String title, String label, String initial, java.util.function.Consumer<String> done) {
        var b = org.geysermc.cumulus.form.CustomForm.builder().title(title).input(label, label, initial);
        b.validResultHandler(r -> done.accept(r.asInput(0)));
        b.closedOrInvalidResultHandler(() -> done.accept(null));
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), b.build());
    }

    /** One dropdown; chosen gets its index, or -1 when closed. */
    static void choice(Player p, String title, String label, List<String> options, int initial, IntConsumer chosen) {
        var b = org.geysermc.cumulus.form.CustomForm.builder().title(title).dropdown(label, options, initial);
        b.validResultHandler(r -> chosen.accept(r.asDropdown(0)));
        b.closedOrInvalidResultHandler(() -> chosen.accept(-1));
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), b.build());
    }
}
