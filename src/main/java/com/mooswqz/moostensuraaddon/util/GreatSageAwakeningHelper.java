package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;

public final class GreatSageAwakeningHelper {
    private GreatSageAwakeningHelper() {
    }

    public static RequirementCheck checkCommandRequirements(ServerPlayer player) {
        return checkRequirements(player, false);
    }

    public static RequirementCheck checkAltarRequirements(ServerPlayer player) {
        return checkRequirements(player, true);
    }

    private static RequirementCheck checkRequirements(ServerPlayer player, boolean altarRitual) {
        int requiredXpLevel = MoosTensuraConfig.SAGE_UPGRADE_REQUIRED_XP_LEVEL.get();
        int requiredRawXp = XpUtils.getLevelEquivalentXpCost(requiredXpLevel);
        int relativeLevelDeduction = MoosTensuraConfig.SAGE_UPGRADE_RELATIVE_LEVEL_DEDUCTION.get();
        double requiredMagicules = MoosTensuraConfig.SAGE_UPGRADE_MAGICULE_COST.get();
        double requiredEp = altarRitual ? MoosTensuraConfig.GREAT_SAGE_RITUAL_REQUIRED_EP.get() : 0.0D;
        int requiredMasteredSkills = altarRitual ? MoosTensuraConfig.GREAT_SAGE_RITUAL_REQUIRED_MASTERED_SKILLS.get() : 0;

        IExistence existence = TensuraStorages.getExistenceFrom(player);
        double currentMagicules = existence == null ? 0.0D : existence.getMagicule();
        double currentEp = existence == null ? 0.0D : existence.getEP();
        int currentMasteredSkills = getMasteredSkillCount(player);

        if (!MoosTensuraConfig.SAGE_UPGRADE_ENABLED.get()) {
            return RequirementCheck.failed(
                    FailureReason.DISABLED,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        if (altarRitual && !MoosTensuraConfig.GREAT_SAGE_RITUAL_ENABLED.get()) {
            return RequirementCheck.failed(
                    FailureReason.DISABLED,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        if (hasGreatSage(player)) {
            return RequirementCheck.failed(
                    FailureReason.ALREADY_HAS_GREAT_SAGE,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        if (MoosTensuraConfig.SAGE_UPGRADE_REQUIRE_SAGE.get() && findSage(player).isEmpty()) {
            return RequirementCheck.failed(
                    FailureReason.NO_SAGE,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        if (!XpUtils.hasLevelEquivalentXp(player, requiredXpLevel)) {
            return RequirementCheck.failed(
                    FailureReason.NOT_ENOUGH_XP,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        if (existence == null) {
            return RequirementCheck.failed(
                    FailureReason.NO_EXISTENCE,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        if (currentMagicules < requiredMagicules) {
            return RequirementCheck.failed(
                    FailureReason.NOT_ENOUGH_MAGICULES,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        if (altarRitual && currentEp < requiredEp) {
            return RequirementCheck.failed(
                    FailureReason.NOT_ENOUGH_EP,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        if (altarRitual && currentMasteredSkills < requiredMasteredSkills) {
            return RequirementCheck.failed(
                    FailureReason.NOT_ENOUGH_MASTERED_SKILLS,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        if (altarRitual
                && MoosTensuraConfig.GREAT_SAGE_RITUAL_REQUIRE_NAMED.get()
                && !TensuraPlayerStateHelper.isNamedOrEndowed(player)) {
            return RequirementCheck.failed(
                    FailureReason.NOT_NAMED,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        return RequirementCheck.success(
                requiredXpLevel,
                requiredRawXp,
                relativeLevelDeduction,
                requiredMagicules,
                currentMagicules,
                requiredEp,
                currentEp,
                requiredMasteredSkills,
                currentMasteredSkills
        );
    }

    public static CompletionResult completeGreatSageEvolution(ServerPlayer player, boolean altarRitual) {
        RequirementCheck check = altarRitual ? checkAltarRequirements(player) : checkCommandRequirements(player);

        if (!check.successful()) {
            return CompletionResult.failed(check.failureReason(), check);
        }

        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return CompletionResult.failed(FailureReason.NO_EXISTENCE, check);
        }

        var skills = SkillAPI.getSkillsFrom(player);
        Optional<ManasSkillInstance> sageOptional = findSage(player);

        TensuraSkillInstance greatSageInstance = new TensuraSkillInstance(UniqueSkills.GREAT_SAGE.get());
        greatSageInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);

        boolean learnedGreatSage = SkillHelper.learnSkill(
                player,
                greatSageInstance,
                -1,
                Component.translatable("moostensuraaddon.command.upgradesage.great_sage_acquired")
        );

        if (!learnedGreatSage) {
            return CompletionResult.failed(FailureReason.LEARN_FAILED, check);
        }

        if (sageOptional.isPresent()) {
            ManasSkillInstance sageInstance = sageOptional.get();

            if (sageInstance.getSkillId() != null) {
                skills.forgetSkill(
                        sageInstance.getSkillId(),
                        Component.translatable("moostensuraaddon.command.upgradesage.sage_evolved")
                );
            }
        }

        XpUtils.deductRelativeLevels(player, check.relativeLevelDeduction());

        existence.setMagicule(Math.max(0.0D, existence.getMagicule() - check.requiredMagicules()));
        existence.markDirty();

        AddonAdvancementHelper.awardGreatSageAwakens(player);
        AddonAdvancementHelper.awardStateBasedAdvancements(player);

        return CompletionResult.success(check);
    }

    public static boolean hasGreatSage(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        ResourceLocation greatSageId = UniqueSkills.GREAT_SAGE.get().getRegistryName();

        if (SkillAPI.getSkillsFrom(player).getSkill(greatSageId).isPresent()) {
            return true;
        }

        return SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .anyMatch(GreatSageAwakeningHelper::isGreatSageSkill);
    }

    public static Optional<ManasSkillInstance> findSage(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .filter(GreatSageAwakeningHelper::isSage)
                .findFirst();
    }

    public static String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
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

    private static boolean isGreatSageSkill(ManasSkillInstance instance) {
        if (instance == null) {
            return false;
        }

        ResourceLocation skillId = instance.getSkillId();

        if (skillId != null && skillId.toString().equals("tensura:great_sage")) {
            return true;
        }

        String displayName = instance.getDisplayName().getString();

        return displayName.equalsIgnoreCase("Great Sage")
                || displayName.equalsIgnoreCase("The Great Sage");
    }

    private static int getMasteredSkillCount(ServerPlayer player) {
        return (int) SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .filter(instance -> instance.isMastered(player))
                .count();
    }

    public record RequirementCheck(
            boolean successful,
            FailureReason failureReason,
            int requiredXpLevel,
            int requiredRawXp,
            int relativeLevelDeduction,
            double requiredMagicules,
            double currentMagicules,
            double requiredEp,
            double currentEp,
            int requiredMasteredSkills,
            int currentMasteredSkills
    ) {
        public static RequirementCheck success(
                int requiredXpLevel,
                int requiredRawXp,
                int relativeLevelDeduction,
                double requiredMagicules,
                double currentMagicules,
                double requiredEp,
                double currentEp,
                int requiredMasteredSkills,
                int currentMasteredSkills
        ) {
            return new RequirementCheck(
                    true,
                    FailureReason.NONE,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }

        public static RequirementCheck failed(
                FailureReason failureReason,
                int requiredXpLevel,
                int requiredRawXp,
                int relativeLevelDeduction,
                double requiredMagicules,
                double currentMagicules,
                double requiredEp,
                double currentEp,
                int requiredMasteredSkills,
                int currentMasteredSkills
        ) {
            return new RequirementCheck(
                    false,
                    failureReason,
                    requiredXpLevel,
                    requiredRawXp,
                    relativeLevelDeduction,
                    requiredMagicules,
                    currentMagicules,
                    requiredEp,
                    currentEp,
                    requiredMasteredSkills,
                    currentMasteredSkills
            );
        }
    }

    public record CompletionResult(
            boolean successful,
            FailureReason failureReason,
            RequirementCheck requirementCheck
    ) {
        public static CompletionResult success(RequirementCheck requirementCheck) {
            return new CompletionResult(true, FailureReason.NONE, requirementCheck);
        }

        public static CompletionResult failed(FailureReason failureReason, RequirementCheck requirementCheck) {
            return new CompletionResult(false, failureReason, requirementCheck);
        }
    }

    public enum FailureReason {
        NONE,
        DISABLED,
        ALREADY_HAS_GREAT_SAGE,
        NO_SAGE,
        NOT_ENOUGH_XP,
        NO_EXISTENCE,
        NOT_ENOUGH_MAGICULES,
        NOT_ENOUGH_EP,
        NOT_ENOUGH_MASTERED_SKILLS,
        NOT_NAMED,
        LEARN_FAILED
    }
}