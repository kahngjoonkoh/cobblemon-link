package io.github.kahngjoonkoh.cobblemonlink.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** GeyserUtils' plugin message (geyserutils:main): its Jackson JSON, read by the GeyserUtils Geyser extension. */
public record GeyserUtilsPayload(byte[] json) implements CustomPacketPayload {
    public static final Type<GeyserUtilsPayload> TYPE = new Type<>(ResourceLocation.parse("geyserutils:main"));
    public static final StreamCodec<FriendlyByteBuf, GeyserUtilsPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBytes(p.json), buf -> { byte[] b = new byte[buf.readableBytes()]; buf.readBytes(b); return new GeyserUtilsPayload(b); });

    /** Draw Java entity id as the custom Bedrock entity identifier (GeyserModelEngineExtension's "modelengine:<model>"). */
    public static GeyserUtilsPayload customEntity(int id, String identifier) {
        return new GeyserUtilsPayload(("{\"@class\":\"me.zimzaza4.geyserutils.common.packet.CustomEntityPacket\",\"entityId\":" + id
                + ",\"identifier\":\"" + identifier + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
