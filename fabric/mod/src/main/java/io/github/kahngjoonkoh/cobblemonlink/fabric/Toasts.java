package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cobblemon's toasts (new Pokédex entries, ...) as real toasts for vanilla Java: a one-off advancement, already done,
 * that shows a toast, removed again the next tick so it never stays in the advancements screen.
 * ponytail: the toast's header is Minecraft's "Advancement Made!"; Cobblemon's frame and progress bar aren't drawn.
 */
final class Toasts {
    private Toasts() {}

    private static int next;

    static void show(ServerPlayer p, ItemStack icon, Component text) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobblemonlink", "toast/" + next++);
        var requirements = AdvancementRequirements.allOf(List.of("shown"));
        var display = new DisplayInfo(icon == null || icon.isEmpty() ? new ItemStack(Items.PAPER) : icon, Lang.resolve(p, text), Component.empty(),
                Optional.empty(), AdvancementType.TASK, true, false, true);
        var advancement = new Advancement(Optional.empty(), Optional.of(display), AdvancementRewards.EMPTY,
                Map.of("shown", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())), requirements, false);
        var progress = new AdvancementProgress();
        progress.update(requirements);
        progress.grantProgress("shown");
        p.connection.send(new ClientboundUpdateAdvancementsPacket(false, List.of(new AdvancementHolder(id, advancement)), Set.of(), Map.of(id, progress)));
        p.server.execute(() -> p.connection.send(new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(id), Map.of())));
    }
}
