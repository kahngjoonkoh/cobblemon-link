package io.github.kahngjoonkoh.cobblemonlink.fabric;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Cobblemon's sounds that its client plays on its own (cries, the Poké Ball's throw, shakes and capture), played by
 * the server for players without the mod. Java hears them from the pack (PolyMc carries Cobblemon's sounds), Bedrock
 * from tools/bedrock_sounds.py's pack (Geyser passes the name through).
 */
public final class Sounds {
    private Sounds() {}

    /** A Cobblemon sound at the entity, for the players without the mod within 32 blocks. */
    public static void play(Entity at, String event) {
        if (!(at.level() instanceof ServerLevel level)) return;
        var sound = Holder.direct(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon", event)));
        var packet = new ClientboundSoundEntityPacket(sound, SoundSource.NEUTRAL, at, 1f, 1f, level.random.nextLong());
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(at) < 32 * 32 && Clients.bridged(p.connection)) p.connection.send(packet);
    }

    public static void cry(com.cobblemon.mod.common.entity.pokemon.PokemonEntity p) {
        play(p, "pokemon." + p.getPokemon().getSpecies().getResourceIdentifier().getPath() + ".cry");
    }
}
