package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;

public final class GreatSageEvolutionService {
    private GreatSageEvolutionService() {
    }

    public static EvolutionResult attemptNormalUpgrade(ServerPlayer player) {
        if (player == null) {
            return EvolutionResult.failure(Component.literal("Player could not be found.")
                    .withStyle(ChatFormatting.RED));
        }

        if (!MoosTensuraConfig.SAGE_UPGRADE_ENABLED.get()) {
            return EvolutionResult.failure(Component.translatable("moostensuraaddon.command.upgradesage.disabled")
                    .withStyle(ChatFormatting.RED));
        }

        if (hasGreatSage(player)) {
            AddonAdvancementHelper.awardGreatSageAwakens(player);

            return EvolutionResult.failure(Component.translatable("moostensuraaddon.command.upgradesage.already_has_great_sage")
                    .withStyle(ChatFormatting.RED));
        }

        Optional<ManasSkillInstance> sageOptional = findSage(player);

        if (MoosTensuraConfig.SAGE_UPGRADE_REQUIRE_SAGE.get() && sageOptional.isEmpty()) {
            return EvolutionResult.failure(Component.translatable("moostensuraaddon.command.upgradesage.no_sage")
                    .withStyle(ChatFormatting.RED));
        }

        int requiredXpLevel = MoosTensuraConfig.SAGE_UPGRADE_REQUIRED_XP_LEVEL.get();
        int relativeLevelDeduction = MoosTensuraConfig.SAGE_UPGRADE_RELATIVE_LEVEL_DEDUCTION.get();
        double magiculeCost = MoosTensuraConfig.SAGE_UPGRADE_MAGICULE_COST.get();

        if (player.experienceLevel < requiredXpLevel) {
            return EvolutionResult.failure(Component.translatable(
                    "moostensuraaddon.command.upgradesage.not_enough_xp_level",
                    requiredXpLevel,
                    player.experienceLevel
            ).withStyle(ChatFormatting.RED));
        }

        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return EvolutionResult.failure(Component.translatable("moostensuraaddon.command.upgradesage.failed")
                    .withStyle(ChatFormatting.RED));
        }

        if (existence.getMagicule() < magiculeCost) {
            return EvolutionResult.failure(Component.translatable(
                    "moostensuraaddon.command.upgradesage.not_enough_magicules",
                    formatNumber(magiculeCost),
                    formatNumber(existence.getMagicule())
            ).withStyle(ChatFormatting.RED));
        }

        boolean upgraded = grantGreatSage(player, sageOptional);

        if (!upgraded) {
            return EvolutionResult.failure(Component.translatable("moostensuraaddon.command.upgradesage.failed")
                    .withStyle(ChatFormatting.RED));
        }

        XpUtils.deductRelativeLevels(player, relativeLevelDeduction);

        existence.setMagicule(Math.max(0.0D, existence.getMagicule() - magiculeCost));
        existence.markDirty();

        AddonAdvancementHelper.awardGreatSageAwakens(player);
        AddonAdvancementHelper.awardStateBasedAdvancements(player);

        return EvolutionResult.success(Component.translatable(
                "moostensuraaddon.command.upgradesage.success",
                relativeLevelDeduction,
                formatNumber(magiculeCost)
        ).withStyle(ChatFormatting.GOLD));
    }

    public static EvolutionResult forceUpgrade(ServerPlayer player) {
        if (player == null) {
            return EvolutionResult.failure(Component.literal("Player could not be found.")
                    .withStyle(ChatFormatting.RED));
        }

        if (hasGreatSage(player)) {
            AddonAdvancementHelper.awardGreatSageAwakens(player);

            return EvolutionResult.failure(Component.translatable("moostensuraaddon.command.upgradesage.already_has_great_sage")
                    .withStyle(ChatFormatting.RED));
        }

        Optional<ManasSkillInstance> sageOptional = findSage(player);

        boolean upgraded = grantGreatSage(player, sageOptional);

        if (!upgraded) {
            return EvolutionResult.failure(Component.translatable("moostensuraaddon.command.upgradesage.failed")
                    .withStyle(ChatFormatting.RED));
        }

        AddonAdvancementHelper.awardGreatSageAwakens(player);
        AddonAdvancementHelper.awardStateBasedAdvancements(player);

        return EvolutionResult.success(Component.translatable("moostensuraaddon.command.upgradesage.force_success")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static boolean hasGreatSage(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        ResourceLocation greatSageId = UniqueSkills.GREAT_SAGE.get().getRegistryName();

        return SkillAPI.getSkillsFrom(player)
                .getSkill(greatSageId)
                .isPresent();
    }

    public static Optional<ManasSkillInstance> findSage(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }

        return SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .filter(GreatSageEvolutionService::isSage)
                .findFirst();
    }

    private static boolean grantGreatSage(ServerPlayer player, Optional<ManasSkillInstance> sageOptional) {
        TensuraSkillInstance greatSageInstance = new TensuraSkillInstance(UniqueSkills.GREAT_SAGE.get());
        greatSageInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);

        boolean learnedGreatSage = SkillHelper.learnSkill(
                player,
                greatSageInstance,
                -1,
                Component.translatable("moostensuraaddon.command.upgradesage.great_sage_acquired")
        );

        if (!learnedGreatSage) {
            return false;
        }

        if (sageOptional.isPresent()) {
            ManasSkillInstance sageInstance = sageOptional.get();

            if (sageInstance.getSkillId() != null) {
                SkillAPI.getSkillsFrom(player).forgetSkill(
                        sageInstance.getSkillId(),
                        Component.translatable("moostensuraaddon.command.upgradesage.sage_evolved")
                );
            }
        }

        return true;
    }

    private static boolean isSage(ManasSkillInstance instance) {
        if (instance == null) {
            return false;
        }

        ResourceLocation skillId = instance.getSkillId();

        if (skillId != null && skillId.toString().equals("tensura:sage")) {
            return true;
        }

        return instance.getDisplayName()
                .getString()
                .equalsIgnoreCase("Sage");
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    public record EvolutionResult(boolean successful, Component message) {
        public static EvolutionResult success(Component message) {
            return new EvolutionResult(true, message);
        }

        public static EvolutionResult failure(Component message) {
            return new EvolutionResult(false, message);
        }
    }
}