package io.github.kahngjoonkoh.clbridge;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import de.tomalbrc.bil.core.holder.positioned.PositionedHolder;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * PoC bridge: vanilla clients on a real-Cobblemon server. PolyMc makes the login work; this hides what PolyMc doesn't
 * (Cobblemon's own entity data types, the Pokémon entity itself) from players without Cobblemon, and shows them each
 * Pokémon as a cobblemon-link model drawn with display entities (blockbench-import-library on Polymer).
 */
public class Bridge implements ModInitializer {
    static final Map<String, Model> MODELS = new HashMap<>();
    /** Minecraft's own entity data types: anything else is a mod's, which a vanilla client can't decode. */
    public static final Set<EntityDataSerializer<?>> VANILLA_DATA = new HashSet<>();

    static {
        for (var f : EntityDataSerializers.class.getFields())
            if (Modifier.isStatic(f.getModifiers()) && EntityDataSerializer.class.isAssignableFrom(f.getType()))
                try { VANILLA_DATA.add((EntityDataSerializer<?>) f.get(null)); } catch (IllegalAccessException ignored) {}
    }

    /** The player's client has no Cobblemon: it never registered a cobblemon: channel. */
    public static boolean vanilla(ServerGamePacketListenerImpl h) {
        return ServerPlayNetworking.getSendable(h).stream().noneMatch(id -> id.getNamespace().equals("cobblemon"));
    }

    @Override
    public void onInitialize() {
        // ponytail: models from a folder at startup (Polymer builds its pack once); every species later
        File dir = new File("config/cl-bridge/models");
        for (File f : Objects.requireNonNullElse(dir.listFiles((d, n) -> n.endsWith(".bbmodel")), new File[0]))
            MODELS.put(f.getName().replace(".bbmodel", ""), BbModelLoader.load(f.getPath()));
        System.out.println("[cl-bridge] models: " + MODELS.keySet() + ", vanilla data types: " + VANILLA_DATA.size());
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (!(e instanceof PokemonEntity p)) return;
            Model m = MODELS.get(p.getPokemon().getSpecies().getName().toLowerCase(Locale.ROOT));
            System.out.println("[cl-bridge] pokemon " + p.getPokemon().getSpecies().getName() + " model=" + (m != null));
            if (m == null) return;
            var holder = new PositionedHolder(level, p.position(), m) {
                @Override
                public boolean startWatching(ServerGamePacketListenerImpl h) {
                    return vanilla(h) && super.startWatching(h); // Cobblemon players see Cobblemon's own model
                }
            };
            EntityAttachment.ofTicking(holder, p);
            holder.getAnimator().playAnimation("idle");
        });
    }
}
