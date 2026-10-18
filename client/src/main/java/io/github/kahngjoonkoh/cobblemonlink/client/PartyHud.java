package io.github.kahngjoonkoh.cobblemonlink.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Cobblemon's party overlay (PartyOverlay): the party down the left edge, middle of the screen, a 62 x 30 slot each,
 * 4 px apart: portrait, "Lv" and level, name, gender, HP bar; the lead's slot is the active one. Data from the server
 * ({"hud": ...}, plugin Mod.hud). ponytail: no EXP bar, status or shoulder icons; the lead stands for Cobblemon's
 * selected slot.
 */
final class PartyHud {
    record Slot(int level, int hp, String name, String gender, String model) {}

    static List<Slot> party = List.of();

    static void update(JsonArray a) {
        List<Slot> out = new ArrayList<>();
        for (JsonElement e : a) {
            String[] t = e.getAsJsonArray().get(0).getAsString().split("\\|", -1);
            out.add(new Slot(Integer.parseInt(t[0]), Integer.parseInt(t[1]), t[2], t.length > 3 ? t[3] : "", e.getAsJsonArray().get(1).getAsString()));
        }
        party = out;
    }

    private static Identifier tex(String path) { return Identifier.fromNamespaceAndPath("cobblemonlink", path); }

    static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (party.isEmpty() || mc.gui.screen() != null) return; // F1 hides HUD elements itself
        int total = party.size() * 30 + (party.size() - 1) * 4, y0 = g.guiHeight() / 2 - total / 2 - 10;
        for (int i = 0; i < party.size(); i++) {
            Slot s = party.get(i);
            int y = y0 + i * 34, x = i == 0 ? 2 : 0; // the lead pokes out, as Cobblemon's selected slot
            boolean fainted = s.hp() == 0;
            String slot = "textures/form/party/party_slot" + (fainted ? "_fainted" : "") + (i == 0 ? "_active" : "") + ".png";
            g.blit(RenderPipelines.GUI_TEXTURED, tex(slot), x, y, 0, 0, 62, 30, 62, 30);
            g.blit(RenderPipelines.GUI_TEXTURED, tex("textures/form/party/party_slot_portrait_background.png"), x + 24, y + 2, 0, 0, 21, 21, 21, 21);
            g.blit(RenderPipelines.GUI_TEXTURED, tex("textures/item/portrait/" + s.model() + ".png"), x + 24, y + 2, 0, 0, 21, 21, 28, 28);
            small(g, "Lv", x + 4, y + 13);
            small(g, String.valueOf(s.level()), x + 4, y + 18);
            small(g, s.name(), x + 3, y + 25);
            if (!s.gender().isEmpty())
                g.blit(RenderPipelines.GUI_TEXTURED, tex("textures/form/party/party_gender_" + ("M".equals(s.gender()) ? "male" : "female") + ".png"),
                        x + 40, y + 25, 0, 0, 3, 3, 6, 5, 6, 5);
            int h = 19 * s.hp() / 1000, colour = s.hp() > 500 ? 0xFF38D85B : s.hp() > 200 ? 0xFFF8D030 : 0xFFE84040;
            g.fill(x + 47, y + 4 + 19 - h, x + 49, y + 4 + 19, colour);
        }
    }

    private static void small(GuiGraphicsExtractor g, String s, int x, int y) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.5f, 0.5f);
        g.text(Minecraft.getInstance().font, s, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
