package io.github.kahngjoonkoh.cobblemonlink.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The cobblemon-link client: a cobblemon-link server (plugin Mod.java) sends its screens as forms on
 * cobblemonlink:form ({"title", "content", "buttons": [[text, image], ...]} or {"close": true}); FormScreen draws
 * them as Cobblemon's screens and sends each tap back on cobblemonlink:click ({"i": index}, -1 when closed).
 * Registering the form channel is how the server knows the mod is here; on other servers it does nothing.
 */
public final class CobblemonLinkClient implements ClientModInitializer {
    /** Raw JSON either way: the server is a Paper plugin, which sends plugin messages as plain bytes. */
    public record Form(byte[] data) implements CustomPacketPayload {
        static final Type<Form> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cobblemonlink", "form"));
        static final StreamCodec<FriendlyByteBuf, Form> CODEC = StreamCodec.of((buf, f) -> buf.writeBytes(f.data), buf -> new Form(bytes(buf)));
        @Override public Type<Form> type() { return TYPE; }
    }

    public record Click(byte[] data) implements CustomPacketPayload {
        static final Type<Click> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cobblemonlink", "click"));
        static final StreamCodec<FriendlyByteBuf, Click> CODEC = StreamCodec.of((buf, c) -> buf.writeBytes(c.data), buf -> new Click(bytes(buf)));
        @Override public Type<Click> type() { return TYPE; }
    }

    private static byte[] bytes(FriendlyByteBuf buf) {
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        return b;
    }

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(Form.TYPE, Form.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Click.TYPE, Click.CODEC);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("cobblemonlink", "party"),
                (g, delta) -> PartyHud.draw(g));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> PartyHud.party = java.util.List.of());
        ClientPlayNetworking.registerGlobalReceiver(Form.TYPE, (form, ctx) -> {
            JsonObject o = JsonParser.parseString(new String(form.data(), StandardCharsets.UTF_8)).getAsJsonObject();
            Minecraft mc = Minecraft.getInstance();
            if (o.has("hud")) {
                PartyHud.update(o.getAsJsonArray("hud"));
                return;
            }
            if (o.has("close")) {
                if (mc.gui.screen() instanceof FormScreen s) s.closeQuietly();
                return;
            }
            if (mc.gui.screen() instanceof FormScreen s) s.update(o);
            else mc.setScreenAndShow(new FormScreen(o));
        });
    }

    static void click(int i) {
        if (ClientPlayNetworking.canSend(Click.TYPE))
            ClientPlayNetworking.send(new Click(("{\"i\":" + i + "}").getBytes(StandardCharsets.UTF_8)));
    }
}
