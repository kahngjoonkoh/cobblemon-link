package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Typing text on vanilla Java without a mod: a sign shown only to that player (above them, out of sight), its editor
 * opened; what they write comes back as a sign update, which we take instead of Minecraft (SignMixin).
 */
public final class Signs {
    private record Ask(BlockPos pos, Consumer<String> done) {}

    private static final Map<UUID, Ask> ASKING = new ConcurrentHashMap<>();

    private Signs() {}

    /** Ask p to type (once: asking again while a sign is open keeps that sign; the client would answer it empty). */
    static void ask(ServerPlayer p, Consumer<String> done) {
        if (ASKING.containsKey(p.getUUID())) return;
        BlockPos pos = p.blockPosition().atY(Math.min(p.level().getMaxBuildHeight() - 1, p.blockPosition().getY() + 4));
        ASKING.put(p.getUUID(), new Ask(pos, done));
        p.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.OAK_SIGN.defaultBlockState()));
        p.connection.send(new ClientboundOpenSignEditorPacket(pos, true));
    }

    /** A sign update from p: ours if they were asked (the answer, and the real block shown again). */
    public static boolean answered(ServerPlayer p, BlockPos pos, String[] lines) {
        Ask a = ASKING.get(p.getUUID());
        if (a == null || !a.pos.equals(pos)) return false;
        ASKING.remove(p.getUUID());
        p.connection.send(new ClientboundBlockUpdatePacket(pos, p.level().getBlockState(pos)));
        String text = String.join(" ", lines).trim().replaceAll(" +", " ");
        p.server.execute(() -> a.done.accept(text));
        return true;
    }
}
