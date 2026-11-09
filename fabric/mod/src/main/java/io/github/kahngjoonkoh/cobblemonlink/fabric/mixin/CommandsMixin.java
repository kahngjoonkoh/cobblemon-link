package io.github.kahngjoonkoh.cobblemonlink.fabric.mixin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import io.github.kahngjoonkoh.cobblemonlink.fabric.Clients;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Cobblemon's own argument types (cobblemon:pokemon, party_slot, move...) go to players without the mod as a plain
 * string the server suggests for (Cobblemon's suggestions, asked by the client). Sent as they were, a vanilla client
 * can't read the command list, and on Bedrock Geyser dropped it ("Index 63 out of bounds for length 62": ViaVersion
 * passes the unknown id through), so Bedrock had no command suggestions at all.
 */
@Mixin(Commands.class)
public class CommandsMixin {
    @ModifyVariable(method = "fillUsableCommands", at = @At("STORE"), ordinal = 0)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentBuilder clbridge$plainArguments(ArgumentBuilder builder, CommandNode<CommandSourceStack> root,
                                                    CommandNode<?> rootCopy, CommandSourceStack source) {
        if (!(builder instanceof RequiredArgumentBuilder r) || source.getPlayer() == null || Clients.modded(source.getPlayer().connection)) return builder;
        var key = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(ArgumentTypeInfos.byClass(r.getType()));
        if (key == null || key.getNamespace().equals("minecraft") || key.getNamespace().equals("brigadier")) return builder;
        // a properties list ("pikachu shiny level=5") is the rest of the line; others are one word
        RequiredArgumentBuilder plain = RequiredArgumentBuilder.argument(r.getName(), key.getPath().endsWith("properties") ? StringArgumentType.greedyString() : StringArgumentType.word());
        plain.suggests((SuggestionProvider) SuggestionProviders.ASK_SERVER);
        plain.requires(r.getRequirement()).executes(r.getCommand());
        if (r.getRedirect() != null) plain.forward(r.getRedirect(), r.getRedirectModifier(), r.isFork());
        return plain;
    }
}
