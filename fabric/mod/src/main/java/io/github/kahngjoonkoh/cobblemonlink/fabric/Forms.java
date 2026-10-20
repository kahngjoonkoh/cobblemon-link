package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.server.level.ServerPlayer;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Bedrock forms through Floodgate, as on Paper (plugin/.../Forms.java): the Bedrock UI pack (tools/build_bedrock_ui.py)
 * draws a form whose title ends in MARK + kind as Cobblemon's screen. Answers come back on Floodgate's thread, so
 * they are handed to the server thread.
 */
public final class Forms {
    /** Ends a styled form's title; the Bedrock pack keys its layout on it (build_bedrock_ui.py MARK). */
    public static final String MARK = "§r§l§";

    public record Opt(String text, String image, Runnable action) {}

    private Forms() {}

    /** clicked gets the button's index, or -1 when the player closes the form. */
    public static void send(ServerPlayer p, String title, String content, List<Opt> opts, IntConsumer clicked) {
        var b = SimpleForm.builder().title(title).content(content);
        for (Opt o : opts) // a bare name is battle art (textures/cobblemonlink/), a path is used as it is
            b.button(o.text(), FormImage.Type.PATH, o.image().startsWith("textures/") ? o.image() : "textures/cobblemonlink/" + o.image());
        b.validResultHandler(r -> onMain(p, () -> clicked.accept(r.clickedButtonId())));
        b.closedOrInvalidResultHandler(() -> onMain(p, () -> clicked.accept(-1)));
        FloodgateApi.getInstance().sendForm(p.getUUID(), b.build());
    }

    /** One text field; done gets the text, or null when closed. */
    public static void input(ServerPlayer p, String title, String label, String initial, java.util.function.Consumer<String> done) {
        var b = CustomForm.builder().title(title).input(label, label, initial);
        b.validResultHandler(r -> onMain(p, () -> done.accept(r.asInput(0))));
        b.closedOrInvalidResultHandler(() -> onMain(p, () -> done.accept(null)));
        FloodgateApi.getInstance().sendForm(p.getUUID(), b.build());
    }

    /** The PC's box settings: a name field and a wallpaper dropdown; done gets (null, -1) when closed. */
    public static void boxSettings(ServerPlayer p, String title, String name, List<String> wallpapers, int wallpaper,
                                   java.util.function.BiConsumer<String, Integer> done) {
        var b = CustomForm.builder().title(title).input("Name", "Box name", name).dropdown("Wallpaper", wallpapers, wallpaper);
        b.validResultHandler(r -> onMain(p, () -> done.accept(r.asInput(0), r.asDropdown(1))));
        b.closedOrInvalidResultHandler(() -> onMain(p, () -> done.accept(null, -1)));
        FloodgateApi.getInstance().sendForm(p.getUUID(), b.build());
    }

    /** One dropdown; chosen gets its index, or -1 when closed. */
    public static void choice(ServerPlayer p, String title, String label, List<String> options, int initial, IntConsumer chosen) {
        var b = CustomForm.builder().title(title).dropdown(label, options, initial);
        b.validResultHandler(r -> onMain(p, () -> chosen.accept(r.asDropdown(0))));
        b.closedOrInvalidResultHandler(() -> onMain(p, () -> chosen.accept(-1)));
        FloodgateApi.getInstance().sendForm(p.getUUID(), b.build());
    }

    private static void onMain(ServerPlayer p, Runnable r) {
        p.server.execute(() -> { if (!p.hasDisconnected()) r.run(); });
    }
}
