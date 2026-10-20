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

    /** GeyserModelEngine's animation properties (modelengine:anim0..), one bundle. */
    public static GeyserUtilsPayload anims(int id, int[] bits) {
        StringBuilder b = new StringBuilder("{\"@class\":\"me.zimzaza4.geyserutils.common.packet.BundlePacket\",\"packets\":[\"java.util.ArrayList\",[");
        for (int i = 0; i < bits.length; i++)
            b.append(i > 0 ? "," : "").append("{\"@class\":\"me.zimzaza4.geyserutils.common.packet.EntityPropertyPacket\",\"entityId\":")
                    .append(id).append(",\"identifier\":\"modelengine:anim").append(i).append("\",\"value\":").append(bits[i]).append('}');
        return new GeyserUtilsPayload(b.append("]]}").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** GeyserModelEngine's entity scale. */
    public static GeyserUtilsPayload scale(int id, float scale) {
        return new GeyserUtilsPayload(("{\"@class\":\"me.zimzaza4.geyserutils.common.packet.CustomEntityDataPacket\",\"entityId\":" + id
                + ",\"height\":null,\"width\":null,\"scale\":[\"java.lang.Float\"," + scale + "],\"color\":null,\"variant\":null}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
