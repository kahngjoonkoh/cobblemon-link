package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;

/**
 * cobblemon-link on a real Cobblemon server: players without the mod (vanilla Java 1.21.1, Bedrock through Geyser)
 * play Cobblemon itself. PolyMc makes the login work; this mod hides what PolyMc doesn't (mixin.SendMixin), draws the
 * Pokémon (Mons), sends the resource pack (Packs) and runs the screens.
 */
public class Bridge implements ModInitializer {
    /** Minecraft's own entity data types: anything else is a mod's, which a vanilla client can't decode. */
    public static final Set<EntityDataSerializer<?>> VANILLA_DATA = new HashSet<>();

    static {
        for (var f : EntityDataSerializers.class.getFields())
            if (Modifier.isStatic(f.getModifiers()) && EntityDataSerializer.class.isAssignableFrom(f.getType()))
                try { VANILLA_DATA.add((EntityDataSerializer<?>) f.get(null)); } catch (IllegalAccessException ignored) {}
    }

    @Override
    public void onInitialize() {
        Config.load(Path.of("config/cobblemonlink/config.json"));
        Tasks.register();
        Clients.register();
        Polys.register();
        Lang.load();
        PayloadTypeRegistry.playS2C().register(GeyserUtilsPayload.TYPE, GeyserUtilsPayload.CODEC);
        Models.register(Path.of("config/cobblemonlink/models"));
        Ui.register(Path.of("config/cobblemonlink/ui"));
        Mons.register();
        Battles.register();
        PartyHud.register();
        Starters.register();
        Summary.register();
        Pc.register();
        Dex.register();
        Extras.register();
        Furniture.register();
        // the pack once in game, vanilla Java only (Packs); Polymer may still be building it
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> {
            System.out.println("[cobblemonlink] " + h.player.getScoreboardName() + " is " + Clients.kind(h));
            if (Clients.java(h) && Config.vanillaJava() == Config.Level.OFF) {
                h.disconnect(net.minecraft.network.chat.Component.literal(
                        "This server plays Cobblemon.\n\nInstall the Cobblemon mod (Fabric, Minecraft 1.21.1) to join on Java,\nor join from Minecraft Bedrock: no mods needed."));
                return;
            }
            // ponytail: 8 s in, after the join toasts: the 1.21.1 client can crash its pack download when a toast
            // expires while the progress toast updates (ToastComponent isn't thread-safe)
            long at = System.currentTimeMillis() + 8000;
            if (Clients.java(h)) Tasks.until(() -> h.player.hasDisconnected() || System.currentTimeMillis() > at && Packs.send(h));
        });
    }
}
