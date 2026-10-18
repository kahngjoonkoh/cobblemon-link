package io.github.kahngjoonkoh.cobblemonlink.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * A cobblemon-link form drawn as Cobblemon's screen, at Cobblemon's positions: the same layouts the server's Bedrock
 * pack gives Bedrock players (tools/build_bedrock_ui.py), by the kind at the end of the title (MARK + kind):
 * 1 battle menu, 2 moves, 3 switch, 4 starter, 5 PC. Art from the server's resource pack (cobblemonlink:textures/form/).
 * A tap sends the button's index; the server answers with the next form, or closes this one.
 * ponytail: still pictures, as on Bedrock (Cobblemon animates its models); no dragging in the PC (tap, then tap).
 */
final class FormScreen extends Screen {
    private static final String MARK = "§r§l§";
    private static final int WHITE = 0xFFFFFFFF;
    private String name = "", content = "";
    private int kind;
    private final List<String> texts = new ArrayList<>(), images = new ArrayList<>();
    private final List<int[]> hits = new ArrayList<>(); // x, y, w, h, index (-1: close)
    private boolean quiet;
    private int mx, my;

    FormScreen(JsonObject form) {
        super(Component.literal("cobblemon-link"));
        update(form);
    }

    void update(JsonObject form) {
        String title = form.get("title").getAsString();
        int at = title.lastIndexOf(MARK);
        kind = at >= 0 && at + MARK.length() < title.length() ? title.charAt(at + MARK.length()) - '0' : 0;
        name = at >= 0 ? title.substring(0, at) : title;
        content = form.has("content") ? form.get("content").getAsString() : "";
        texts.clear();
        images.clear();
        for (JsonElement e : form.getAsJsonArray("buttons")) {
            JsonArray b = e.getAsJsonArray();
            texts.add(b.get(0).getAsString());
            images.add(b.get(1).getAsString());
        }
    }

    void closeQuietly() {
        quiet = true;
        onClose();
    }

    @Override
    public void onClose() {
        if (!quiet) CobblemonLinkClient.click(-1);
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {} // the world stays in view, as in Cobblemon

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        for (int k = hits.size() - 1; k >= 0; k--) {
            int[] h = hits.get(k);
            if (e.x() >= h[0] && e.x() < h[0] + h[2] && e.y() >= h[1] && e.y() < h[1] + h[3]) {
                if (h[4] < 0) onClose();
                else CobblemonLinkClient.click(h[4]);
                return true;
            }
        }
        return super.mouseClicked(e, doubled);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hits.clear();
        mx = mouseX;
        my = mouseY;
        switch (kind) {
            case 1, 2, 3 -> battle(g);
            case 4 -> panel(g, 239, 197, this::starter);
            case 5 -> panel(g, 349, 205, this::pc);
            case 6 -> panel(g, 331, 161, this::summary);
            case 7 -> panel(g, 134, 160, this::learn);
            case 8 -> panel(g, 345, 207, this::dex);
            default -> {}
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    // ---- drawing helpers: pieces at panel coordinates, through the current origin and scale ----

    private float ox, oy, sc = 1;

    private interface Painter { void paint(GuiGraphicsExtractor g); }

    /** A w x h panel in the middle of the screen, shrunk if the screen is smaller (Cobblemon's PC wants 349 px). */
    private void panel(GuiGraphicsExtractor g, int w, int h, Painter p) {
        sc = Math.min(1f, Math.min((float) width / w, (float) height / h));
        ox = (width - w * sc) / 2;
        oy = (height - h * sc) / 2;
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(sc, sc);
        p.paint(g);
        g.pose().popMatrix();
        ox = oy = 0;
        sc = 1;
    }

    private boolean over(int x, int y, int w, int h) {
        float px = (mx - ox) / sc, py = (my - oy) / sc;
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private void hit(int x, int y, int w, int h, int index) {
        hits.add(new int[]{Math.round(ox + x * sc), Math.round(oy + y * sc), Math.round(w * sc), Math.round(h * sc), index});
    }

    private static final Map<Identifier, int[]> SIZES = new HashMap<>();

    /** A server image name (as the Bedrock pack names it) to the Java pack's texture. */
    private static Identifier id(String image) {
        String path = image.startsWith("textures/cobblemonlink/") ? image.substring("textures/cobblemonlink/".length()) : image;
        if (path.startsWith("body/")) return Identifier.fromNamespaceAndPath("cobblemonlink", "textures/font/" + path + ".png");
        if (path.startsWith("textures/items/portrait/"))
            return Identifier.fromNamespaceAndPath("cobblemonlink", "textures/item/portrait/" + path.substring("textures/items/portrait/".length()) + ".png");
        return Identifier.fromNamespaceAndPath("cobblemonlink", "textures/form/" + path + ".png");
    }

    /** The texture's size, or null if the pack has no such texture. */
    private static int[] size(Identifier id) {
        return SIZES.computeIfAbsent(id, k -> {
            var r = Minecraft.getInstance().getResourceManager().getResource(k);
            if (r.isEmpty()) return new int[0];
            try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
                return new int[]{img.getWidth(), img.getHeight()};
            } catch (Exception e) {
                return new int[0];
            }
        });
    }

    private static boolean none(String image) {
        return image == null || image.isEmpty() || image.endsWith("/none") || image.equals("none");
    }

    /** The image stretched to w x h at (x, y). */
    private void img(GuiGraphicsExtractor g, String image, int x, int y, int w, int h) {
        if (none(image)) return;
        Identifier id = id(image);
        int[] s = size(id);
        if (s.length == 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, s[0], s[1], s[0], s[1]);
    }

    /** idle art, or its "_hover" twin under the mouse (Cobblemon's hover). */
    private void art(GuiGraphicsExtractor g, String image, int x, int y, int w, int h) {
        img(g, over(x, y, w, h) && size(id(image + "_hover")).length > 0 ? image + "_hover" : image, x, y, w, h);
    }

    private void text(GuiGraphicsExtractor g, String s, float x, float y, float scale, boolean centre, float boxW) {
        Font f = font;
        float w = f.width(s) * scale;
        float left = centre ? x + (boxW - w) / 2 : x;
        g.pose().pushMatrix();
        g.pose().translate(left, y);
        g.pose().scale(scale, scale);
        g.text(f, s, 0, 0, WHITE, true);
        g.pose().popMatrix();
    }

    private void lines(GuiGraphicsExtractor g, String s, int x, int y, int w, float scale, int lineH) {
        int row = 0;
        for (String part : s.split("\n"))
            for (FormattedCharSequence seq : font.split(FormattedText.of(part), (int) (w / scale))) {
                g.pose().pushMatrix();
                g.pose().translate(x, y + row++ * lineH);
                g.pose().scale(scale, scale);
                g.text(font, seq, 0, 0, WHITE, true);
                g.pose().popMatrix();
            }
    }

    private String t(int i) { return i < texts.size() ? texts.get(i) : ""; }

    private String im(int i) { return i < images.size() ? images.get(i) : "none"; }

    // ---- battle: BattleGeneralActionSelection, BattleMoveSelection, BattleSwitchPokemonSelection, BattleMessagePane ----

    private void battle(GuiGraphicsExtractor g) {
        int lx = width - 12 - 169, ly = height - 30 - 55;
        img(g, "log", lx, ly, 169, 55);
        lines(g, content, lx + 8, ly + 5, 153, 0.75f, 8);
        if (kind == 1) { // BattleGUI's hide label: closing is how you step aside
            String hint = "Tap here to move around.";
            int w = font.width(hint), x = (width - w) / 2, y = height / 5;
            g.text(font, hint, x, y, 0xBFFFFFFF, true);
            hit(x, y, w, 9, -1);
        }
        int cols = 2, cw = kind == 1 ? 93 : kind == 2 ? 105 : 98, ch = kind == 1 ? 29 : kind == 2 ? 29 : 31;
        int rows = kind == 1 ? 2 : kind == 2 ? 3 : 4;
        int left = kind == 2 ? 20 : 12, top = height + (kind == 1 ? -27 : kind == 2 ? 3 : -68) - rows * ch;
        int tw = kind == 1 ? 90 : kind == 2 ? 92 : 94, th = kind == 1 ? 26 : kind == 2 ? 24 : 29;
        int[] label = kind == 1 ? new int[]{6, 8} : kind == 2 ? new int[]{17, 2} : new int[]{5, 4};
        for (int i = 0; i < texts.size() && i < cols * rows; i++) {
            int x = left + cw * (i % cols), y = top + ch * (i / cols);
            String image = im(i);
            String bare = image.startsWith("textures/cobblemonlink/") ? image.substring("textures/cobblemonlink/".length()) : image;
            if (bare.equals("back")) { // Cobblemon's back arrow, at (9, H-22)
                int bx = x + (kind == 2 ? -11 : -101), by = y + (kind == 2 ? 4 : 108);
                art(g, bare, bx, by, 29, 17);
                hit(bx, by, 29, 17, i);
                continue;
            }
            if (none(bare)) continue;
            art(g, bare, x, y, tw, th);
            lines(g, t(i), x + label[0], y + label[1], tw - label[0] - 2, 1f, 9);
            hit(x, y, tw, th, i);
        }
    }

    // ---- 4: Cobblemon's StarterSelectionScreen (StarterScreen.bedrock) ----

    private void starter(GuiGraphicsExtractor g) {
        img(g, "starter_base", 0, 0, 239, 197);
        img(g, im(0), 1, 13, 128, 125); // the card
        img(g, "starter_ball", 4, 3, 8, 8);
        text(g, name, 14, 2, 1, false, 0);
        text(g, t(0), 79, 2, 1, false, 0);
        text(g, "Starter Selection", 134, 11, 1, true, 77);
        lines(g, content, 8, 143, 114, 0.5f, 5);
        art(g, "starter_choose", 13, 180, 106, 14);
        text(g, "I choose you!", 13, 183, 1, true, 106);
        hit(13, 180, 106, 14, 0);
        art(g, "starter_exit", 210, 181, 16, 12);
        hit(210, 181, 16, 12, -1);
        img(g, "starter_up", 151, 162, 7, 4);
        hit(148, 160, 13, 8, 1);
        img(g, "starter_down", 205, 162, 7, 4);
        hit(202, 160, 13, 8, 2);
        for (int i = 3; i < texts.size() && i < 15; i++) { // 3 tiles a row, 27 x 44 cells from (143, 27)
            String image = im(i);
            if (!image.contains("tile/") && !image.contains("portrait")) continue;
            int n = i - 3, x = 143 + 27 * (n % 3), y = 27 + 44 * (n / 3);
            String text = t(i), region = text.replace("*", "");
            if (!region.isEmpty()) {
                img(g, "starter_block", x - 5, y, 89, 40);
                text(g, region, x - 5, y + 1, 0.5f, true, 89);
            }
            art(g, "starter_tile", x, y + 11, 25, 25);
            if (text.contains("*")) img(g, "starter_tile_active", x, y + 11, 25, 25);
            img(g, image, x + 1, y + 12, 23, 23);
            hit(x, y + 11, 25, 25, i);
        }
    }

    // ---- 5: Cobblemon's PCGUI (PcScreen.bedrock; button order as build_bedrock_ui.py PC_HOLDERS) ----

    private static final String[] TYPES = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison",
            "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};

    private void pc(GuiGraphicsExtractor g) {
        img(g, "pc/base", 0, 0, 349, 205);
        img(g, im(8), 85, 27, 174, 155); // the box's wallpaper
        img(g, "pc/grid", 92, 38, 160, 133);
        img(g, "pc/overlay", 85, 27, 174, 155);
        text(g, "Party", 267, 9, 1, true, 82);
        text(g, "Held Item", 24, 107, 0.5f, false, 0);
        text(g, "Nature", 9, 128, 0.5f, true, 63);
        text(g, "Ability", 9, 145, 0.5f, true, 63);
        text(g, "Moves", 9, 162, 0.5f, true, 63);
        text(g, t(10), 9, 136, 0.5f, true, 63);
        text(g, t(11), 9, 153, 0.5f, true, 63);
        for (int k = 0; k < 4; k++) text(g, t(12 + k), 9, 170 + 7 * k, 0.5f, true, 63);
        img(g, im(9), 9, 30, 60, 60); // the selected Pokémon
        text(g, t(9), 6, 2, 1, false, 0);
        text(g, t(16), 69, 11, 1, false, 0);
        img(g, im(17), 62, 28, 8, 8);
        img(g, im(18), 53, 28, 8, 8);
        if (!name.isEmpty()) {
            img(g, "starter_ball", 3, 12, 8, 8);
            text(g, name, 12, 11, 1, false, 0);
        }
        button(g, 0, 113, 13, 12, 12, null);
        img(g, "pc/arrow_previous", 117, 16, 7, 7);
        button(g, 1, 216, 13, 12, 12, null);
        img(g, "pc/arrow_next", 220, 16, 7, 7);
        button(g, 2, 126, 11, 90, 12, null);
        text(g, t(2), 126, 13, 1, true, 90);
        button(g, 3, 126, 182, 88, 14, null);
        img(g, "pc/filter", 130, 185, 8, 8);
        text(g, t(3), 126, 185, 1, true, 88);
        button(g, 4, 216, 184, 12, 12, null);
        img(g, im(4), 218, 186, 8, 8);
        if (!t(5).isEmpty()) {
            button(g, 5, 279, 151, 58, 16, "pc/release");
            text(g, "Release", 279, 155, 1, true, 58);
        }
        if (!t(6).isEmpty()) {
            button(g, 6, 277, 152, 30, 13, "pc/half");
            text(g, "Yes", 277, 155, 1, true, 30);
            button(g, 7, 308, 152, 30, 13, "pc/half");
            text(g, "No", 308, 155, 1, true, 30);
        }
        art(g, "pc/exit", 320, 186, 26, 13);
        hit(320, 186, 26, 13, -1);
        for (int s = 0; s < 30; s++) slot(g, 19 + s, 92 + 27 * (s % 6), 38 + 27 * (s / 6));
        for (int i = 0; i < 6; i++) slot(g, 49 + i, 278 + i % 2 * 31, 35 + 31 * (i / 2) + i % 2 * 8);
    }

    // ---- 6: Cobblemon's Summary (SummaryScreen.java; buttons as its javadoc) ----

    private void summary(GuiGraphicsExtractor g) {
        img(g, "summary/base", 0, 0, 331, 161);
        img(g, "summary/info_base", 77, 12, 134, 148);
        img(g, "summary/party_bg", 216, 24, 114, 113);
        for (int k = 0; k < 3; k++) {
            String[] tab = {"info", "moves", "stats"};
            int x = 78 + 31 * k;
            img(g, "summary/tab_" + tab[k], x, -1, 39, 13);
            if (t(k).contains("*")) g.fill(x + 15, 11, x + 24, 12, 0xFFFFFFFF); // the open tab
            else if (over(x, -1, 31, 13)) g.fill(x + 2, 1, x + 30, 11, 0x3CFFFFFF);
            hit(x, -1, 31, 13, k);
        }
        img(g, im(6), 9, 35, 60, 60);
        text(g, t(6), 6, 4, 1, false, 0);
        text(g, t(7), 69, 14, 1, false, 0);
        img(g, im(8), 27, 119, 12, 12);
        img(g, im(9), 41, 119, 12, 12);
        text(g, "Held Item", 24, 113, 0.5f, false, 0);
        img(g, "starter_ball", 3, 15, 8, 8);
        text(g, name, 12, 14, 1, false, 0);
        if (over(12, 13, 56, 11)) g.fill(12, 13, 68, 24, 0x3CFFFFFF);
        hit(12, 13, 56, 11, 10); // the name: a tap renames
        int[][] act = {{217, 30}, {249, 24}, {275, 24}};
        for (int k = 0; k < 3; k++) {
            if (t(3 + k).isEmpty()) continue;
            button(g, 3 + k, act[k][0], 139, act[k][1], 11, null);
            g.fill(act[k][0], 139, act[k][0] + act[k][1], 150, 0x40000000);
            text(g, t(3 + k), act[k][0], 141, 0.75f, true, act[k][1]);
        }
        art(g, "pc/exit", 302, 145, 26, 13);
        hit(302, 145, 26, 13, -1);
        if (!t(41).isEmpty()) { // Cobblemon's Evolve button
            button(g, 41, 12, 145, 54, 15, "summary/evolve");
            text(g, t(41), 12, 149, 1, true, 54);
        }
        for (int i = 0; i < 6; i++) {
            int x = 216 + 6 + i % 2 * 51 + 9, y = 24 + 8 + 32 * (i / 2) + i % 2 * 8;
            if (!t(11 + i).isEmpty()) slot(g, 11 + i, x, y);
        }
        for (int r = 0; r < 8; r++) {
            int y = r < 5 ? 12 + 7 + 15 * r : new int[]{12 + 84, 12 + 124, 12 + 136}[r - 5];
            text(g, t(17 + 3 * r), 77 + 5, y, 0.5f, false, 0);
            text(g, t(18 + 3 * r), 77 + 50, y, 0.5f, false, 0);
        }
    }

    // ---- 7: move learning (MoveLearn.java): the question, the known moves to forget, "Don't learn" ----

    private void learn(GuiGraphicsExtractor g) {
        text(g, name, 0, 0, 1, true, 134);
        img(g, "summary/moves_base", 0, 12, 134, 148);
        lines(g, content, 5, 136, 58, 0.5f, 5);
        for (int k = 0; k < 4; k++) {
            button(g, k, 12, 18 + 25 * k, 110, 22, null);
            text(g, t(k), 12, 25 + 25 * k, 1, true, 110);
        }
        button(g, 4, 68, 125, 60, 28, null);
        text(g, t(4), 68, 135, 0.75f, true, 60);
    }

    // ---- 8: Cobblemon's Pokédex (PokedexScreen.java; buttons as its javadoc) ----

    private void dex(GuiGraphicsExtractor g) {
        img(g, "pokedex/base", 0, 0, 345, 207);
        text(g, "Pok\u00e9dex", 36, 14, 1, false, 0);
        img(g, "pokedex/seen", 252, 15, 7, 7);
        text(g, t(6), 262, 14, 1, false, 0);
        img(g, "pokedex/caught", 290, 15, 7, 7);
        text(g, t(7), 300, 14, 1, false, 0);
        button(g, 0, 26, 179, 12, 12, null);
        text(g, "<", 29, 181, 1, false, 0);
        text(g, t(2), 40, 181, 1, true, 94);
        button(g, 1, 134, 179, 12, 12, null);
        text(g, ">", 137, 181, 1, false, 0);
        img(g, "pokedex/viewport", 180, 39, 139, 70);
        text(g, t(3), 183, 30, 1, false, 0);
        text(g, name, 235, 30, 1, false, 0);
        img(g, im(3), 220, 44, 60, 60);
        img(g, im(4), 232, 112, 12, 12);
        img(g, im(5), 246, 112, 12, 12);
        lines(g, content, 189, 135, 121, 0.5f, 5);
        art(g, "pc/exit", 312, 186, 26, 13);
        hit(312, 186, 26, 13, -1);
        for (int k = 0; k < 25; k++) {
            String text = t(8 + k);
            if (text.isEmpty()) continue;
            int x = 26 + 27 * (k % 5), y = 39 + 27 * (k / 5);
            img(g, "pokedex/slot", x, y, 25, 25);
            img(g, im(8 + k), x + 1, y + 1, 23, 23);
            text(g, text.replace("c", "").replace("*", ""), x + 2, y + 1, 0.5f, false, 0);
            if (text.contains("c")) img(g, "pokedex/caught", x + 18, y + 18, 6, 6);
            if (text.contains("*") || over(x, y, 25, 25)) img(g, "pokedex/slot_select", x, y, 25, 25);
            hit(x, y, 25, 25, 8 + k);
        }
    }

    /** A plain button: its art (+ hover) or a light square under the mouse. */
    private void button(GuiGraphicsExtractor g, int i, int x, int y, int w, int h, String tex) {
        if (tex != null) art(g, tex, x, y, w, h);
        else if (over(x, y, w, h)) g.fill(x, y, x + w, y + h, 0x3CFFFFFF);
        hit(x, y, w, h, i);
    }

    /** A box or party slot: "Lv5~water1~flying2*" (Lv, type icons, selected) over the whole Pokémon. */
    private void slot(GuiGraphicsExtractor g, int i, int x, int y) {
        if (i >= texts.size()) return;
        String text = t(i);
        img(g, im(i), x, y, 25, 25);
        String lv = text.contains("~") ? text.substring(0, text.indexOf('~')) : text.replace("*", "");
        text(g, lv.replace("*", ""), x + 1, y + 1, 0.5f, false, 0);
        for (String ty : TYPES)
            for (int n = 1; n <= 2; n++)
                if (text.contains("~" + ty + n)) img(g, "pc/tcell/" + ty, x + 20 - 5 * (n - 1), y + 1, 4, 4);
        if (text.contains("*")) img(g, "pc/pointer", x + 7, y - 6, 11, 8);
        if (over(x, y, 25, 25)) g.fill(x, y, x + 25, y + 25, 0x3CFFFFFF);
        hit(x, y, 25, 25, i);
    }
}
