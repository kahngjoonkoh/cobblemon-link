package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;

/**
 * Cobblemon's Pokémon entities for players without the mod: a ModelHolder each (vanilla Java sees its displays) and,
 * for Bedrock, GeyserModelEngine's custom entity on a pig (mixin.SendMixin) with its scale and animation properties.
 */
public final class Mons {
    private static final Map<Integer, ModelHolder> HOLDERS = new ConcurrentHashMap<>();

    private Mons() {}

    static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (!(e instanceof PokemonEntity p)) return;
            String look = look(p);
            Models.Model m = Models.get(look);
            if (m == null) return;
            ModelHolder h = new ModelHolder(p, m, scale(p)) {
                @Override
                protected void bedrockChanged(int[] anims) { // GeyserModelEngine's anim0.. properties
                    level.getChunkSource().broadcast(p, new ClientboundCustomPayloadPacket(GeyserUtilsPayload.anims(p.getId(), anims)));
                }
            };
            h.battling(p::isBattling);
            EntityAttachment.ofTicking(h, p);
            HOLDERS.put(p.getId(), h);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
            if (!(e instanceof PokemonEntity)) return;
            ModelHolder h = HOLDERS.remove(e.getId());
            if (h != null) h.destroy(); // Cobblemon's removals don't always reach Polymer's attachment
        });
    }

    public static ModelHolder holder(int entityId) { return HOLDERS.get(entityId); }

    /** The look (model) a Pokémon is drawn with: its species and aspects through Cobblemon's variation table. */
    public static String look(PokemonEntity p) {
        var mon = p.getPokemon();
        return Models.look(mon.getSpecies().getResourceIdentifier().getPath(), mon.getAspects());
    }

    /** Cobblemon's render scale: the form's base scale times the Pokémon's own. */
    public static float scale(PokemonEntity p) {
        var mon = p.getPokemon();
        return mon.getForm().getBaseScale() * mon.getEffectiveScale();
    }

    static ServerLevel level(PokemonEntity p) { return (ServerLevel) p.level(); }
}
