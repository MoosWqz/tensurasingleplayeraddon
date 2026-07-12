package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.util.AddonAdvancementHelper;
import com.mooswqz.moostensuraaddon.util.TensuraPlayerStateHelper;
import com.mooswqz.moostensuraaddon.util.XpUtils;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class GetNamedCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("getnamed")
                .requires(source -> source.hasPermission(0))
                .executes(context -> execute(context.getSource()))
        );
    }

    private static int execute(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!MoosTensuraConfig.SELF_ENDOWMENT_ENABLED.get()) {
            player.sendSystemMessage(Component.translatable("moostensuraaddon.command.getnamed.disabled")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            player.sendSystemMessage(Component.translatable("moostensuraaddon.command.getnamed.failed")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (TensuraPlayerStateHelper.isNamedOrEndowed(player)) {
            AddonAdvancementHelper.awardNameAnchor(player);

            player.sendSystemMessage(Component.translatable("moostensuraaddon.command.getnamed.already_named")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int requiredLevel = MoosTensuraConfig.SELF_ENDOWMENT_REQUIRED_LEVEL.get();
        double requiredEp = MoosTensuraConfig.SELF_ENDOWMENT_REQUIRED_EP.get();
        double magiculeCost = MoosTensuraConfig.SELF_ENDOWMENT_REQUIRED_MAGICULES.get();
        int xpLevelEquivalentCost = MoosTensuraConfig.SELF_ENDOWMENT_XP_LEVEL_COST.get();

        if (player.experienceLevel < requiredLevel) {
            player.sendSystemMessage(Component.translatable(
                    "moostensuraaddon.command.getnamed.not_enough_levels",
                    requiredLevel
            ).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (existence.getEP() < requiredEp) {
            player.sendSystemMessage(Component.translatable(
                    "moostensuraaddon.command.getnamed.not_enough_ep",
                    formatNumber(requiredEp),
                    formatNumber(existence.getEP())
            ).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!hasAtLeastOneMasteredSkill(player)) {
            player.sendSystemMessage(Component.translatable("moostensuraaddon.command.getnamed.no_mastered_skill")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!XpUtils.hasLevelEquivalentXp(player, xpLevelEquivalentCost)) {
            player.sendSystemMessage(Component.translatable(
                    "moostensuraaddon.command.getnamed.not_enough_xp",
                    xpLevelEquivalentCost,
                    XpUtils.getLevelEquivalentXpCost(xpLevelEquivalentCost)
            ).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (existence.getMagicule() < magiculeCost) {
            player.sendSystemMessage(Component.translatable(
                    "moostensuraaddon.command.getnamed.not_enough_magicules",
                    formatNumber(magiculeCost),
                    formatNumber(existence.getMagicule())
            ).withStyle(ChatFormatting.RED));
            return 0;
        }

        String assignedName = player.getGameProfile().getName();

        RequestNamingMenuPacket.name(
                player,
                null,
                RequestNamingMenuPacket.NamingType.HIGH,
                assignedName
        );

        IExistence refreshedExistence = TensuraStorages.getExistenceFrom(player);

        if (refreshedExistence == null || !TensuraPlayerStateHelper.isNamedOrEndowed(player)) {
            player.sendSystemMessage(Component.translatable("moostensuraaddon.command.getnamed.failed")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        refreshedExistence.setMagicule(Math.max(0.0D, refreshedExistence.getMagicule() - magiculeCost));
        refreshedExistence.markDirty();

        XpUtils.deductLevelEquivalentXp(player, xpLevelEquivalentCost);

        AddonAdvancementHelper.awardNameAnchor(player);
        AddonAdvancementHelper.awardStateBasedAdvancements(player);

        player.sendSystemMessage(Component.translatable(
                "moostensuraaddon.command.getnamed.success",
                assignedName,
                xpLevelEquivalentCost,
                formatNumber(magiculeCost)
        ).withStyle(ChatFormatting.GOLD));

        return 1;
    }

    private static boolean hasAtLeastOneMasteredSkill(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .anyMatch(instance -> instance.isMastered(player));
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }
}