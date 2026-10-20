package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        // a Pokémon that changed look (evolved, changed form or became shiny) is drawn again, with an evolution's
        // sparkle and sound
        int[] tick = {0};
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick[0]++;
            // Cobblemon's evolution scene, shortly: three seconds of light gathering round the old look, a flash, the new one
            for (var e : new java.util.ArrayList<>(CHANGING.entrySet())) {
                ModelHolder h = HOLDERS.get(e.getKey());
                if (h == null || !(h.entity() instanceof PokemonEntity p) || p.isRemoved()) { CHANGING.remove(e.getKey()); continue; }
                int t = e.getValue() + 1;
                CHANGING.put(e.getKey(), t);
                // Cobblemon's evo_buildup (light gathering in its colour), evo_sparkle, then evo_sparkleburst with the flash
                if (t % 4 == 0) burst(p, net.minecraft.core.particles.ParticleTypes.ENCHANT, 12, 0.6f);
                if (t % 3 == 0) burst(p, Particles.dust("evo_buildup"), 6, 0.05f);
                if (t % 10 == 0) burst(p, net.minecraft.core.particles.ParticleTypes.END_ROD, 4, 0.2f);
                if (t < SCENE) continue;
                CHANGING.remove(e.getKey());
                HOLDERS.remove(e.getKey()).destroy();
                burst(p, net.minecraft.core.particles.ParticleTypes.FLASH, 1, 0);
                burst(p, Particles.dust("evo_sparkleburst"), 30, 0.3f);
                redraw(p);
            }
            for (var e : new java.util.ArrayList<>(CAMERAS.entrySet()))
                if (e.getValue().until() <= tick[0] || e.getValue().player().hasDisconnected()) {
                    CAMERAS.remove(e.getKey());
                    e.getValue().player().connection.send(new net.minecraft.network.protocol.game.ClientboundSetCameraPacket(e.getValue().player()));
                    e.getValue().stand().discard();
                }
            if (tick[0] % 20 != 0) return;
            for (var e : new java.util.ArrayList<>(HOLDERS.entrySet())) {
                if (!(e.getValue().entity() instanceof PokemonEntity p) || p.isRemoved() || CHANGING.containsKey(e.getKey())) continue;
                String look = look(p);
                if (look.equals(e.getValue().model().name()) || Models.get(look) == null) continue;
                CHANGING.put(e.getKey(), 0);
                Sounds.play(p, "evolution.full");
                camera(p, tick[0] + SCENE + 30); // the scene, the new look, then back
            }
        });
    }

    /** Draw p again after its look changed: Java gets the new model, Bedrock the entity again with the new one. */
    /** Pokémon whose look changed, and the ticks of their scene so far. */
    private static final Map<Integer, Integer> CHANGING = new ConcurrentHashMap<>();
    static final int SCENE = 60;

    /** The owner's view during a look change: Cobblemon's cutscene camera, shortly (Java only; Bedrock keeps its own view). */
    private record Camera(ServerPlayer player, net.minecraft.world.entity.decoration.ArmorStand stand, int until) {}
    private static final Map<Integer, Camera> CAMERAS = new ConcurrentHashMap<>();

    /** Looks at p from in front of it, a little above, through an invisible marker armor stand. */
    private static void camera(PokemonEntity p, int until) {
        if (!(p.getOwner() instanceof ServerPlayer owner) || owner.distanceToSqr(p) > 32 * 32 || Clients.bedrock(owner) || !Clients.bridged(owner.connection)) return;
        var level = (ServerLevel) p.level();
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, 0, 0, 0);
        var front = net.minecraft.world.phys.Vec3.directionFromRotation(0, p.getYRot()).scale(2 + p.getBbWidth());
        stand.setPos(p.position().add(front).add(0, p.getBbHeight() * 0.6 - stand.getEyeHeight(), 0));
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.position().add(0, p.getBbHeight() / 2, 0));
        level.addFreshEntity(stand);
        owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCameraPacket(stand));
        CAMERAS.put(p.getId(), new Camera(owner, stand, until));
    }

    /** Particles round p for every player without the mod near it. */
    private static void burst(PokemonEntity p, net.minecraft.core.particles.ParticleOptions type, int count, float speed) {
        if (!(p.level() instanceof ServerLevel level)) return;
        for (var player : level.players())
            if (player.distanceToSqr(p) < 64 * 64 && Clients.bridged(player.connection))
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(type, false, p.getX(),
                        p.getY() + p.getBbHeight() / 2, p.getZ(), p.getBbWidth() / 2, p.getBbHeight() / 2, p.getBbWidth() / 2, speed, count));
    }

    private static void redraw(PokemonEntity p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        ModelHolder h = ensure(p);
        if (h == null) return;
        burst(p, net.minecraft.core.particles.ParticleTypes.END_ROD, 40, 0.05f);
        for (var player : level.players()) {
            if (player.distanceToSqr(p) > 64 * 64 || !Clients.bedrock(player)) continue;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(p.getId()));
            // SendMixin turns the spawn into the model's carrier, as for any Pokémon
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(p.getId(), p.getUUID(), p.getX(), p.getY(), p.getZ(),
                    p.getXRot(), p.getYRot(), net.minecraft.world.entity.EntityType.PIG, 0, net.minecraft.world.phys.Vec3.ZERO, p.getYHeadRot()));
        }
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
        else if (e instanceof com.cobblemon.mod.common.entity.npc.NPCEntity n) npc(n, level);
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

    /** Cobblemon's NPC variations: an NPC class ("cobblemon:sacchi") -> our model of its geometry ("npc_sacchi"). */
    private static Map<String, String> npcLooks;

    static String npcLook(com.cobblemon.mod.common.entity.npc.NPCEntity n) {
        if (npcLooks == null) {
            Map<String, String> m = new java.util.HashMap<>();
            var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cobblemon").flatMap(c -> c.findPath("assets/cobblemon/bedrock/npcs/variations"));
            if (dir.isPresent()) try (var walk = java.nio.file.Files.walk(dir.get())) {
                for (var f : (Iterable<java.nio.file.Path>) walk::iterator) {
                    if (!f.toString().endsWith(".json")) continue;
                    var o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
                    for (var v : o.getAsJsonArray("variations")) {
                        var vo = v.getAsJsonObject();
                        if (vo.has("model") && vo.getAsJsonArray("aspects").isEmpty())
                            m.put(o.get("name").getAsString(), "npc_" + vo.get("model").getAsString().split(":")[1].replace(".geo", ""));
                    }
                }
            } catch (java.io.IOException ignored) {}
            npcLooks = m;
        }
        return npcLooks.getOrDefault(n.getResourceIdentifier().toString(), "npc_trainer");
    }

    /**
     * An NPC's name for its tag: Cobblemon keeps a lang key as plain text ("npc.sacchi.name"); as a translatable it is
     * written out by each Java client in its own language (Cobblemon's lang files are in the pack).
     */
    static net.minecraft.network.chat.Component npcName(com.cobblemon.mod.common.entity.npc.NPCEntity n) {
        String s = n.getName().getString();
        return Lang.has(s) ? net.minecraft.network.chat.Component.translatable(s) : n.getName();
    }

    /** A Cobblemon NPC drawn like a Pokémon: its own model and skin, its name above. */
    private static void npc(com.cobblemon.mod.common.entity.npc.NPCEntity n, ServerLevel level) {
        Models.Model m = Models.get(npcLook(n));
        if (m == null) return;
        ModelHolder h = new ModelHolder(n, m, 1f) {
            @Override
            protected void bedrockChanged(int[] anims) {
                level.getChunkSource().broadcast(n, new ClientboundCustomPayloadPacket(GeyserUtilsPayload.anims(n.getId(), anims)));
            }
        };
        var name = new eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement(npcName(n));
        name.setBillboardMode(net.minecraft.world.entity.Display.BillboardConstraints.CENTER);
        name.setOffset(new net.minecraft.world.phys.Vec3(0, n.getBbHeight() + 0.3, 0));
        name.setViewRange(0.5f);
        h.addElement(name);
        HOLDERS.put(n.getId(), h);
        EntityAttachment.ofTicking(h, n);
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
        String[] heard = {"throw"};
        h.state(() -> {
            String state = ballState(b, shakes, last);
            if (!state.equals(heard[0])) { // the ball's sounds, as Cobblemon's client plays them
                heard[0] = state;
                String sound = switch (state) {
                    case "open" -> "poke_ball.open";
                    case "shut" -> "poke_ball.shut";
                    case "bob1", "bob2", "bob3" -> "poke_ball.shake";
                    case "capture" -> "poke_ball.capture_succeeded";
                    case "break" -> "poke_ball.break";
                    default -> null;
                };
                if (sound != null) Sounds.play(b, sound);
            }
            return state;
        });
        HOLDERS.put(b.getId(), h);
        EntityAttachment.ofTicking(h, b);
        Sounds.play(b, "poke_ball.throw");
    }

    private static String ballState(com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity b, int[] shakes, boolean[] last) {
        return switch (b.getCaptureState()) {
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
        };
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
