package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Cobblemon's Pokémon (and thrown Poké Balls) for players without the mod: a ModelHolder each (vanilla Java sees its
 * displays) and, for Bedrock, GeyserModelEngine's custom entity on a pig (mixin.SendMixin) with its scale and
 * animation properties.
 */
public final class Mons {
    private static final Map<Integer, ModelHolder> HOLDERS = new ConcurrentHashMap<>();

    private Mons() {}

    static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> ensure(e));
        ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
            ModelHolder h = HOLDERS.remove(e.getId());
            if (h != null) h.destroy(); // Cobblemon's removals don't always reach Polymer's attachment
        });
    }

    public static ModelHolder holder(int entityId) { return HOLDERS.get(entityId); }

    /**
     * The entity's holder, made now if it has none yet: a new entity is sent to players before the level's load
     * event (ChunkMap tracks it first). Null for anything that isn't a Pokémon or a thrown Poké Ball with a model.
     */
    public static ModelHolder ensure(Entity e) {
        ModelHolder h = HOLDERS.get(e.getId());
        if (h != null || e.isRemoved() || !(e.level() instanceof ServerLevel level)) return h;
        if (e instanceof com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity b) ball(b, level);
        else if (e instanceof PokemonEntity p) pokemon(p, level);
        return HOLDERS.get(e.getId());
    }

    private static void pokemon(PokemonEntity p, ServerLevel level) {
        Models.Model m = Models.get(look(p));
        if (m == null) return;
        ModelHolder h = new ModelHolder(p, m, scale(p)) {
            @Override
            protected void bedrockChanged(int[] anims) { // GeyserModelEngine's anim0.. properties
                level.getChunkSource().broadcast(p, new ClientboundCustomPayloadPacket(GeyserUtilsPayload.anims(p.getId(), anims)));
            }
        };
        h.battling(p::isBattling);
        HOLDERS.put(p.getId(), h);
        EntityAttachment.ofTicking(h, p);
    }

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

    /**
     * Cobblemon's thrown Poké Ball (EmptyPokeBallEntity) as cobblemon-link's ball model: spinning in flight, open on
     * the hit, shut and falling, a bob a shake, then capture or break, from the ball's own capture state.
     * ponytail: every ball is the Poké Ball's model.
     */
    static void ball(com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity b, ServerLevel level) {
        Models.Model m = Models.get("poke_ball");
        if (m == null) return;
        int[] shakes = {0};
        boolean[] last = {false};
        ModelHolder h = new ModelHolder(b, m, 1f, false) {
            @Override
            protected void bedrockChanged(int[] anims) {
                level.getChunkSource().broadcast(b, new ClientboundCustomPayloadPacket(GeyserUtilsPayload.anims(b.getId(), anims)));
            }
        };
        h.state(() -> switch (b.getCaptureState()) {
            case HIT, CRITICAL -> "open";
            case FALL -> "shut";
            case SHAKE -> {
                boolean s = b.getEntityData().get(shakeData());
                if (s != last[0]) {
                    last[0] = s;
                    shakes[0]++;
                }
                yield shakes[0] == 0 ? "shut" : "bob" + (1 + (shakes[0] - 1) % 3);
            }
            case CAPTURED -> "capture";
            case BROKEN_FREE -> "break";
            default -> "throw";
        });
        HOLDERS.put(b.getId(), h);
        EntityAttachment.ofTicking(h, b);
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.network.syncher.EntityDataAccessor<Boolean> shakeData() {
        try {
            var f = com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity.class.getDeclaredField("SHAKE");
            f.setAccessible(true);
            return (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
