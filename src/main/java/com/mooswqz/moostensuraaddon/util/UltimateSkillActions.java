package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.BorrowedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GrantedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.recognition.RecognitionAuthorityProgress;
import com.mooswqz.moostensuraaddon.network.OpenSubordinateOverviewScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateMultiGrantScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateConfirmationScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateSubordinateSkillScreenPayload;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class UltimateSkillActions {
    private static final double TARGET_RANGE = 16.0D;
    private static final double TARGET_RADIUS = 0.5D;
    private static final double RANGED_RADIUS = 32.0D;

    private static final int BENEVOLENT_MASTERY_MASS_GRANT_PER_TARGET = 12;
    private static final int ABSOLUTE_MASTERY_MASS_GRANT_PER_TARGET = 10;
    private static final int MASTERY_RANGED_TAKE_BACK_PER_TARGET = 6;
    private static final int MASTERY_BORROW_PER_SKILL = 20;
    private static final int MASTERY_SEIZE_PER_SKILL = 25;
    private static final int BENEVOLENT_MASTERY_GRANT_WITHOUT_MASTERY = 35;
    private static final int ABSOLUTE_MASTERY_GRANT_WITHOUT_MASTERY = 30;
    private static final int MASTERY_RANGED_SKILL_VIEW_PER_TARGET = 2;
    private static final int MASTERY_RANGED_SKILL_VIEW_MAX = 20;

    private static final boolean DEBUG_ULTIMATE_MASTERY = false;

    public static void chooseSkill(ServerPlayer player) {
        boolean benevolent = hasBenevolentEmpowerment(player);
        boolean governance = hasAbsoluteGovernance(player);

        if (!benevolent && !governance) {
            GranterActions.openSkillSelection(player);
            return;
        }

        LivingEntity target = getLookedAtSubordinate(player);

        if (target == null) {
            /*
             * Looking away preserves the existing one-skill selector for
             * Mass Grant and Ranged Take Back. Looking at a subordinate
             * opens the Ultimate multi-grant workflow.
             */
            GranterActions.openSkillSelection(player);
            return;
        }

        openMultiGrantSelection(
                player,
                target,
                benevolent
        );
    }

    private static void openMultiGrantSelection(
            ServerPlayer player,
            LivingEntity target,
            boolean benevolent
    ) {
        Optional<ManasSkillInstance> authorityOptional =
                getUltimateInstance(player, benevolent);

        if (authorityOptional.isEmpty()) {
            sendFeedback(
                    player,
                    Component.translatable(
                                    "moostensuraaddon.granter.error.no_granter"
                            )
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        String currentActiveSkillId =
                GranterActions.getSelectedSkillId(
                                authorityOptional.get()
                        )
                        .map(ResourceLocation::toString)
                        .orElse("");

        List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries =
                new ArrayList<>();

        for (ManasSkillInstance sourceInstance :
                SkillAPI.getSkillsFrom(player)
                        .getLearnedSkills()) {
            if (sourceInstance == null
                    || sourceInstance.getSkillId() == null) {
                continue;
            }

            ResourceLocation skillId = sourceInstance.getSkillId();
            Component displayName = sourceInstance.getDisplayName();

            if (!GranterActions.isGrantableSkill(skillId)
                    || SkillCategoryHelper.isIntrinsic(
                    sourceInstance,
                    skillId,
                    displayName
            )
                    || SkillAPI.getSkillsFrom(target)
                    .getSkill(skillId)
                    .isPresent()) {
                continue;
            }

            SkillCategoryHelper.SkillCategory category =
                    SkillCategoryHelper.getCategory(
                            sourceInstance,
                            skillId,
                            displayName
                    );

            entries.add(
                    new OpenUltimateMultiGrantScreenPayload
                            .SkillEntry(
                            skillId.toString(),
                            displayName.getString(),
                            category.name(),
                            sourceInstance.isMastered(player),
                            getGrantWithoutMasteryCost(
                                    benevolent,
                                    sourceInstance
                            )
                    )
            );
        }

        entries.sort(
                Comparator.comparingInt(
                                (OpenUltimateMultiGrantScreenPayload.SkillEntry entry) ->
                                        SkillCategoryHelper
                                                .SkillCategory
                                                .valueOf(entry.category())
                                                .sortOrder()
                        )
                        .thenComparing(
                                OpenUltimateMultiGrantScreenPayload
                                        .SkillEntry::displayName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                OpenUltimateMultiGrantScreenPayload
                                        .SkillEntry::skillId
                        )
        );

        if (entries.isEmpty()) {
            sendFeedback(
                    player,
                    AuthorityText.component(
                                    "error.target_has_all_grantable",
                                    target.getDisplayName()
                            )
                            .withStyle(ChatFormatting.YELLOW)
            );
            return;
        }

        recognizeSubordinate(player, target);

        PacketDistributor.sendToPlayer(
                player,
                new OpenUltimateMultiGrantScreenPayload(
                        benevolent,
                        target.getUUID().toString(),
                        target.getDisplayName().getString(),
                        currentActiveSkillId,
                        entries
                )
        );
    }

    public static void massGrant(ServerPlayer player, ManasSkillInstance ultimateInstance, boolean benevolent) {
        openMassGrantConfirmation(player, ultimateInstance, benevolent);
    }

    public static void rangedTakeBack(ServerPlayer player, ManasSkillInstance ultimateInstance, boolean benevolent) {
        openRangedTakeBackConfirmation(player, ultimateInstance, benevolent);
    }

    public static void executeConfirmedMassGrant(ServerPlayer player, boolean benevolent) {
        Optional<ManasSkillInstance> ultimateOptional = getUltimateInstance(player, benevolent);

        if (ultimateOptional.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_granter")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        executeMassGrant(player, ultimateOptional.get(), benevolent);
    }

    public static void executeConfirmedRangedTakeBack(ServerPlayer player, boolean benevolent) {
        Optional<ManasSkillInstance> ultimateOptional = getUltimateInstance(player, benevolent);

        if (ultimateOptional.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_granter")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        executeRangedTakeBack(player, ultimateOptional.get(), benevolent);
    }

    private static void openMassGrantConfirmation(ServerPlayer player, ManasSkillInstance ultimateInstance, boolean benevolent) {
        Optional<ResourceLocation> selectedOptional = GranterActions.getSelectedSkillId(ultimateInstance);

        if (selectedOptional.isEmpty()) {
            sendNoSelectedSkill(player);
            return;
        }

        ResourceLocation selectedSkillId = selectedOptional.get();

        Optional<ManasSkillInstance> sourceOptional = SkillAPI.getSkillsFrom(player).getSkill(selectedSkillId);

        if (sourceOptional.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.user_missing_skill")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ManasSkillInstance sourceInstance = sourceOptional.get();

        if (!sourceInstance.isMastered(player)) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.mass_grant_requires_mastery")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (!GranterActions.isGrantableSkill(selectedSkillId)) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.invalid_selection")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (SkillAPI.getSkillRegistry().get(selectedSkillId) == null) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.unknown_skill")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        List<LivingEntity> targets = getValidMassGrantTargets(player, selectedSkillId);

        if (targets.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.no_valid_mass_grant_targets")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        double costPerTarget = getMassGrantCostPerTarget(benevolent);
        double totalCost = costPerTarget * targets.size();

        if (!hasMagicules(player, totalCost)) {
            sendNotEnoughMagicules(player, totalCost);
            return;
        }

        PacketDistributor.sendToPlayer(player, new OpenUltimateConfirmationScreenPayload(
                true,
                benevolent,
                selectedSkillId.toString(),
                getSkillDisplayName(player, selectedSkillId).getString(),
                targets.size(),
                totalCost
        ));
    }

    private static void openRangedTakeBackConfirmation(ServerPlayer player, ManasSkillInstance ultimateInstance, boolean benevolent) {
        Optional<ResourceLocation> selectedOptional = GranterActions.getSelectedSkillId(ultimateInstance);

        if (selectedOptional.isEmpty()) {
            sendNoSelectedSkill(player);
            return;
        }

        ResourceLocation selectedSkillId = selectedOptional.get();

        List<LivingEntity> targets = getValidRangedTakeBackTargets(player, selectedSkillId);

        if (targets.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.no_take_back_targets")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        PacketDistributor.sendToPlayer(player, new OpenUltimateConfirmationScreenPayload(
                false,
                benevolent,
                selectedSkillId.toString(),
                getSkillDisplayName(player, selectedSkillId).getString(),
                targets.size(),
                0.0D
        ));
    }

    private static void executeMassGrant(ServerPlayer player, ManasSkillInstance ultimateInstance, boolean benevolent) {
        Optional<ResourceLocation> selectedOptional = GranterActions.getSelectedSkillId(ultimateInstance);

        if (selectedOptional.isEmpty()) {
            sendNoSelectedSkill(player);
            return;
        }

        ResourceLocation selectedSkillId = selectedOptional.get();

        Optional<ManasSkillInstance> sourceOptional = SkillAPI.getSkillsFrom(player).getSkill(selectedSkillId);

        if (sourceOptional.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.user_missing_skill")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ManasSkillInstance sourceInstance = sourceOptional.get();

        if (!sourceInstance.isMastered(player)) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.mass_grant_requires_mastery")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (!GranterActions.isGrantableSkill(selectedSkillId)) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.invalid_selection")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ManasSkill selectedSkill = SkillAPI.getSkillRegistry().get(selectedSkillId);

        if (selectedSkill == null) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.unknown_skill")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        List<LivingEntity> targets = getValidMassGrantTargets(player, selectedSkillId);

        if (targets.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.no_valid_mass_grant_targets")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        double costPerTarget = getMassGrantCostPerTarget(benevolent);
        double totalCost = costPerTarget * targets.size();

        if (!hasMagicules(player, totalCost)) {
            sendNotEnoughMagicules(player, totalCost);
            return;
        }

        int granted = 0;

        for (LivingEntity target : targets) {
            boolean learned = grantSkillToTarget(player, target, selectedSkill, sourceInstance, selectedSkillId);

            if (learned) {
                granted++;
                recognizeSubordinate(player, target);

                RecognitionAuthorityProgress.recordEmpoweredSubordinate(
                        player,
                        target
                );
            }
        }

        if (granted <= 0) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.mass_grant_failed")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        RecognitionAuthorityProgress.recordMassGrant(
                player,
                granted
        );

        double realCost = costPerTarget * granted;
        consumeMagicules(player, realCost);

        int masteryGain = granted * (benevolent
                ? BENEVOLENT_MASTERY_MASS_GRANT_PER_TARGET
                : ABSOLUTE_MASTERY_MASS_GRANT_PER_TARGET);

        addUltimateMastery(player, ultimateInstance, masteryGain);

        AddonAdvancementHelper.awardFirstGift(player);

        sendFeedback(player, Component.translatable(
                "moostensuraaddon.ultimate.mass_grant.success",
                getSkillDisplayName(player, selectedSkillId),
                granted,
                formatNumber(realCost)
        ).withStyle(ChatFormatting.GREEN));
    }

    private static void executeRangedTakeBack(ServerPlayer player, ManasSkillInstance ultimateInstance, boolean benevolent) {
        Optional<ResourceLocation> selectedOptional = GranterActions.getSelectedSkillId(ultimateInstance);

        if (selectedOptional.isEmpty()) {
            sendNoSelectedSkill(player);
            return;
        }

        ResourceLocation selectedSkillId = selectedOptional.get();
        int takenBack = 0;

        for (LivingEntity target : getValidRangedTakeBackTargets(player, selectedSkillId)) {
            GrantedSkillData data = target.getData(AttachmentRegistry.GRANTED_SKILL_DATA);

            Optional<GrantedSkillData.GrantedSkillRecord> recordOptional =
                    data.getGrant(selectedSkillId.toString(), player.getUUID());

            if (recordOptional.isEmpty()) {
                continue;
            }

            GrantedSkillData.GrantedSkillRecord record = recordOptional.get();

            if (!record.targetHadSkillBefore()) {
                SkillAPI.getSkillsFrom(target).forgetSkill(
                        selectedSkillId,
                        Component.translatable("moostensuraaddon.granter.taken_back", player.getDisplayName())
                );
            }

            data.removeGrant(selectedSkillId.toString(), player.getUUID());
            target.setData(AttachmentRegistry.GRANTED_SKILL_DATA, data);

            recognizeSubordinate(player, target);
            takenBack++;
        }

        if (takenBack <= 0) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.no_take_back_targets")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        RecognitionAuthorityProgress.recordGlobalTakeBack(
                player,
                takenBack,
                benevolent
        );

        addUltimateMastery(player, ultimateInstance, takenBack * MASTERY_RANGED_TAKE_BACK_PER_TARGET);

        AddonAdvancementHelper.awardWhatWasGivenCanReturn(player);

        sendFeedback(player, Component.translatable(
                benevolent
                        ? "moostensuraaddon.ultimate.benevolent.ranged_take_back.success"
                        : "moostensuraaddon.ultimate.absolute.ranged_take_back.success",
                getSkillDisplayName(player, selectedSkillId),
                takenBack
        ).withStyle(benevolent ? ChatFormatting.GOLD : ChatFormatting.DARK_PURPLE));
    }

    public static void openBorrowSkillSelection(ServerPlayer player) {
        if (!hasBenevolentEmpowerment(player)) {
            return;
        }

        openSubordinateSkillSelection(player, false);
    }

    public static void openSeizeSkillSelection(ServerPlayer player) {
        if (!hasAbsoluteGovernance(player)) {
            return;
        }

        openSubordinateSkillSelection(player, true);
    }

    private static void openSubordinateSkillSelection(ServerPlayer player, boolean seize) {
        AuthorityCostMigrationService.applyRecommendedDefaults();

        LivingEntity target = getLookedAtSubordinate(player);

        if (target == null) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_subordinate")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        BorrowedSkillData borrowedSkillData = player.getData(AttachmentRegistry.BORROWED_SKILL_DATA);

        List<OpenUltimateSubordinateSkillScreenPayload.SkillEntry> skillEntries = SkillAPI.getSkillsFrom(target)
                .getLearnedSkills()
                .stream()
                .filter(instance -> instance != null && instance.getSkillId() != null)
                .filter(instance -> isValidBorrowOrSeizeSkill(instance.getSkillId(), seize))
                .filter(instance -> SkillAPI.getSkillsFrom(player).getSkill(instance.getSkillId()).isEmpty())
                .map(instance -> {
                    ResourceLocation skillId = instance.getSkillId();
                    Component displayName = instance.getDisplayName();
                    double borrowChance = seize
                            ? 0.0D
                            : getBorrowPermanentChance(
                            borrowedSkillData.getBorrowCount(
                                    skillId.toString()
                            )
                    );
                    SkillCategoryHelper.SkillCategory category =
                            SkillCategoryHelper.getCategory(
                                    instance,
                                    skillId,
                                    displayName
                            );

                    return new OpenUltimateSubordinateSkillScreenPayload.SkillEntry(
                            skillId.toString(),
                            displayName.getString(),
                            category.name(),
                            instance.isMastered(target),
                            borrowChance
                    );
                })
                .sorted(
                        Comparator.comparingInt(
                                        (OpenUltimateSubordinateSkillScreenPayload.SkillEntry entry) ->
                                                SkillCategoryHelper.SkillCategory
                                                        .valueOf(entry.category())
                                                        .sortOrder()
                                )
                                .thenComparing(
                                        OpenUltimateSubordinateSkillScreenPayload.SkillEntry::displayName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        OpenUltimateSubordinateSkillScreenPayload.SkillEntry::skillId
                                )
                )
                .toList();

        if (skillEntries.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.target_has_no_selectable_skills")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        recognizeSubordinate(player, target);

        double costPerSkill = seize
                ? MoosTensuraConfig.SEIZE_COST_PER_SKILL.get()
                : MoosTensuraConfig.BORROW_COST_PER_SKILL.get();

        PacketDistributor.sendToPlayer(player, new OpenUltimateSubordinateSkillScreenPayload(
                seize,
                target.getUUID().toString(),
                target.getDisplayName().getString(),
                getCurrentMagicules(player),
                costPerSkill,
                MoosTensuraConfig.SEIZE_DEATH_CHANCE_PER_SKILL.get(),
                MoosTensuraConfig.SEIZE_DEATH_CHANCE_MAX.get(),
                skillEntries
        ));
    }

    public static void executeBorrowOrSeizeSelection(
            ServerPlayer player,
            boolean seize,
            UUID targetUuid,
            List<ResourceLocation> skillIds
    ) {
        AuthorityCostMigrationService.applyRecommendedDefaults();

        if (skillIds == null || skillIds.isEmpty()) {
            sendFeedback(
                    player,
                    AuthorityText.component("error.select_skill")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        UltimateBorrowSeizePolicy.RequestAnalysis request =
                UltimateBorrowSeizePolicy.analyseRequest(
                        skillIds.stream()
                                .map((ResourceLocation skillId) -> skillId == null
                                        ? ""
                                        : skillId.toString())
                                .toList()
                );

        if (request.overLimit()) {
            sendFeedback(
                    player,
                    AuthorityText.component("error.selection_over_limit")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (request.rejectedCount() > 0
                || request.uniqueSkillIds().size() != skillIds.size()) {
            sendFeedback(
                    player,
                    AuthorityText.component("error.invalid_or_duplicate")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        List<ResourceLocation> validatedSkillIds = new ArrayList<>();

        for (String rawSkillId : request.uniqueSkillIds()) {
            ResourceLocation skillId = ResourceLocation.tryParse(rawSkillId);

            if (skillId == null) {
                sendFeedback(
                        player,
                        AuthorityText.component("error.invalid_registry_id")
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }

            validatedSkillIds.add(skillId);
        }

        if (seize && !hasAbsoluteGovernance(player)) {
            sendFeedback(
                    player,
                    AuthorityText.component("error.governance_unavailable")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (!seize && !hasBenevolentEmpowerment(player)) {
            sendFeedback(
                    player,
                    AuthorityText.component("error.benevolent_unavailable")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        LivingEntity target = getSubordinateByUuid(player, targetUuid);

        if (target == null) {
            sendFeedback(
                    player,
                    AuthorityText.component("error.subordinate_unavailable")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        for (ResourceLocation skillId : validatedSkillIds) {
            if (!isValidBorrowOrSeizeSkill(skillId, seize)) {
                sendFeedback(
                        player,
                        AuthorityText.component(
                                        "error.skill_invalid_for_action",
                                        skillId.toString()
                                )
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }

            Optional<ManasSkillInstance> targetSkill =
                    SkillAPI.getSkillsFrom(target).getSkill(skillId);

            if (targetSkill.isEmpty()) {
                sendFeedback(
                        player,
                        AuthorityText.component(
                                        "error.target_no_longer_possesses",
                                        target.getDisplayName(),
                                        skillId.toString()
                                )
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }

            if (SkillAPI.getSkillsFrom(player).getSkill(skillId).isPresent()) {
                sendFeedback(
                        player,
                        AuthorityText.component(
                                        "error.player_already_possesses",
                                        targetSkill.orElseThrow().getDisplayName()
                                )
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }

            if (SkillAPI.getSkillRegistry().get(skillId) == null) {
                sendFeedback(
                        player,
                        AuthorityText.component("error.skill_unregistered")
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }
        }

        double costPerSkill = seize
                ? MoosTensuraConfig.SEIZE_COST_PER_SKILL.get()
                : MoosTensuraConfig.BORROW_COST_PER_SKILL.get();
        double totalCost = UltimateBorrowSeizePolicy.calculateTotalCost(
                costPerSkill,
                validatedSkillIds.size()
        );

        if (!hasMagicules(player, totalCost)) {
            sendNotEnoughMagicules(player, totalCost);
            return;
        }

        int successful = 0;
        int permanentBorrowed = 0;
        double highestBorrowChance = 0.0D;

        for (ResourceLocation skillId : validatedSkillIds) {
            BorrowOrSeizeResult result = seize
                    ? executeSingleSeize(player, target, skillId)
                    : executeSingleBorrow(player, target, skillId);

            if (!result.success()) {
                sendFeedback(
                        player,
                        AuthorityText.component("error.application_failed")
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }

            successful++;

            if (!seize) {
                highestBorrowChance = Math.max(
                        highestBorrowChance,
                        result.permanentChance()
                );

                if (result.permanent()) {
                    permanentBorrowed++;
                }
            }
        }

        consumeMagicules(player, totalCost);
        recognizeSubordinate(player, target);
        addUltimateMastery(
                player,
                seize,
                successful * (seize
                        ? MASTERY_SEIZE_PER_SKILL
                        : MASTERY_BORROW_PER_SKILL)
        );

        if (seize) {
            RecognitionAuthorityProgress.recordSkillsSeized(
                    player,
                    successful
            );

            double deathChance = getSeizeDeathChance(successful);
            boolean killedBySeize = rollSeizeDeath(
                    player,
                    target,
                    deathChance
            );

            AddonAdvancementHelper.awardSeizedAuthority(player);

            if (successful >= 5) {
                AddonAdvancementHelper.awardSoulStrain(player);
            }

            if (killedBySeize) {
                AddonAdvancementHelper.awardPriceOfPower(player);
            }

            MutableComponent message = Component.translatable(
                    "moostensuraaddon.ultimate.absolute.seize.multi_success",
                    successful,
                    target.getDisplayName(),
                    formatNumber(totalCost)
            ).withStyle(ChatFormatting.DARK_PURPLE);

            message.append(
                    AuthorityText.component(
                                    "seize.soul_strain",
                                    formatPercent(deathChance)
                            )
                            .withStyle(ChatFormatting.RED)
            );

            if (killedBySeize) {
                message.append(
                        AuthorityText.component("seize.soul_shattered")
                                .withStyle(ChatFormatting.DARK_RED)
                );
            }

            sendFeedback(player, message);
        } else {
            MutableComponent message = Component.translatable(
                    "moostensuraaddon.ultimate.benevolent.borrow.multi_success",
                    successful,
                    target.getDisplayName(),
                    formatNumber(totalCost)
            ).withStyle(ChatFormatting.GOLD);

            message.append(
                    AuthorityText.component(
                                    "borrow.chance",
                                    formatPercent(highestBorrowChance)
                            )
                            .withStyle(ChatFormatting.LIGHT_PURPLE)
            );

            if (permanentBorrowed > 0) {
                message.append(
                        AuthorityText.component(
                                        "borrow.permanent_count",
                                        permanentBorrowed
                                )
                                .withStyle(ChatFormatting.LIGHT_PURPLE)
                );
            }

            sendFeedback(player, message);
        }
    }

    public static void executeMultiGrantSelection(
            ServerPlayer player,
            boolean benevolent,
            String targetUuid,
            List<String> requestedSkillIds
    ) {
        if (player == null) {
            return;
        }

        UltimateMultiGrantPolicy.RequestAnalysis request =
                UltimateMultiGrantPolicy.analyseRequest(
                        requestedSkillIds
                );

        if (request.uniqueSkillIds().isEmpty()) {
            return;
        }

        if (request.overLimit()) {
            sendFeedback(
                    player,
                    AuthorityText.component("error.selection_over_limit")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (benevolent
                ? !hasBenevolentEmpowerment(player)
                : !hasAbsoluteGovernance(player)) {
            return;
        }

        if (targetUuid == null
                || targetUuid.isBlank()) {
            return;
        }

        UUID parsedTargetUuid;

        try {
            parsedTargetUuid = UUID.fromString(targetUuid);
        } catch (IllegalArgumentException exception) {
            return;
        }

        LivingEntity target = getSubordinateByUuid(
                player,
                parsedTargetUuid
        );

        if (target == null) {
            sendFeedback(
                    player,
                    Component.translatable(
                                    "moostensuraaddon.granter.error.no_subordinate"
                            )
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        List<MultiGrantCandidate> candidates =
                new ArrayList<>();
        int rejected = request.rejectedCount();

        for (String rawSkillId : request.uniqueSkillIds()) {
            ResourceLocation skillId = ResourceLocation.tryParse(
                    rawSkillId
            );

            if (skillId == null
                    || !GranterActions.isGrantableSkill(skillId)
                    || SkillAPI.getSkillsFrom(target)
                    .getSkill(skillId)
                    .isPresent()) {
                rejected++;
                continue;
            }

            Optional<ManasSkillInstance> sourceOptional =
                    SkillAPI.getSkillsFrom(player)
                            .getSkill(skillId);
            ManasSkill selectedSkill =
                    SkillAPI.getSkillRegistry()
                            .get(skillId);

            if (sourceOptional.isEmpty()
                    || selectedSkill == null) {
                rejected++;
                continue;
            }

            ManasSkillInstance sourceInstance =
                    sourceOptional.get();

            if (SkillCategoryHelper.isIntrinsic(
                    sourceInstance,
                    skillId,
                    sourceInstance.getDisplayName()
            )) {
                rejected++;
                continue;
            }

            candidates.add(
                    new MultiGrantCandidate(
                            skillId,
                            selectedSkill,
                            sourceInstance,
                            getGrantWithoutMasteryCost(
                                    benevolent,
                                    sourceInstance
                            )
                    )
            );
        }

        if (candidates.isEmpty()) {
            sendFeedback(
                    player,
                    AuthorityText.component(
                                    "error.none_grantable_to_target",
                                    target.getDisplayName()
                            )
                            .withStyle(ChatFormatting.YELLOW)
            );
            return;
        }

        double requestedCost = candidates.stream()
                .mapToDouble(MultiGrantCandidate::cost)
                .sum();

        if (!hasMagicules(player, requestedCost)) {
            sendNotEnoughMagicules(
                    player,
                    requestedCost
            );
            return;
        }

        int granted = 0;
        double realCost = 0.0D;

        for (MultiGrantCandidate candidate : candidates) {
            if (SkillAPI.getSkillsFrom(target)
                    .getSkill(candidate.skillId())
                    .isPresent()) {
                rejected++;
                continue;
            }

            boolean learned = grantSkillToTarget(
                    player,
                    target,
                    candidate.skill(),
                    candidate.sourceInstance(),
                    candidate.skillId()
            );

            if (!learned) {
                rejected++;
                continue;
            }

            granted++;
            realCost += candidate.cost();
        }

        if (granted <= 0) {
            sendFeedback(
                    player,
                    Component.translatable(
                                    "moostensuraaddon.granter.error.grant_failed"
                            )
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        consumeMagicules(player, realCost);
        recognizeSubordinate(player, target);

        RecognitionAuthorityProgress.recordEmpoweredSubordinate(
                player,
                target
        );
        RecognitionAuthorityProgress.recordMassGrant(
                player,
                granted
        );
        RecognitionAuthorityProgress.synchronize(player);

        Optional<ManasSkillInstance> authorityOptional =
                getUltimateInstance(player, benevolent);

        if (authorityOptional.isPresent()) {
            addUltimateMastery(
                    player,
                    authorityOptional.get(),
                    granted * (benevolent
                            ? BENEVOLENT_MASTERY_GRANT_WITHOUT_MASTERY
                            : ABSOLUTE_MASTERY_GRANT_WITHOUT_MASTERY)
            );
        }

        AddonAdvancementHelper.awardFirstGift(player);

        sendFeedback(
                player,
                AuthorityText.component(
                                rejected > 0
                                        ? "success.compat_multi_grant_skipped"
                                        : "success.compat_multi_grant",
                                granted,
                                target.getDisplayName(),
                                formatNumber(realCost),
                                rejected
                        )
                        .withStyle(
                                benevolent
                                        ? ChatFormatting.GOLD
                                        : ChatFormatting.DARK_PURPLE
                        )
        );
    }

    public static void grantWithoutMastery(ServerPlayer player, ManasSkillInstance ultimateInstance, boolean benevolent) {
        Optional<ResourceLocation> selectedOptional = GranterActions.getSelectedSkillId(ultimateInstance);

        if (selectedOptional.isEmpty()) {
            sendNoSelectedSkill(player);
            return;
        }

        ResourceLocation selectedSkillId = selectedOptional.get();

        if (!GranterActions.isGrantableSkill(selectedSkillId)) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.invalid_selection")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        LivingEntity target = getLookedAtSubordinate(player);

        if (target == null) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_subordinate")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Optional<ManasSkillInstance> sourceSkillOptional = SkillAPI.getSkillsFrom(player).getSkill(selectedSkillId);

        if (sourceSkillOptional.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.ultimate.error.user_missing_skill")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (SkillAPI.getSkillsFrom(target).getSkill(selectedSkillId).isPresent()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.target_already_has")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ManasSkill selectedSkill = SkillAPI.getSkillRegistry().get(selectedSkillId);

        if (selectedSkill == null) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.unknown_skill")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ManasSkillInstance sourceInstance = sourceSkillOptional.get();

        double cost = getGrantWithoutMasteryCost(benevolent, sourceInstance);

        if (!hasMagicules(player, cost)) {
            sendNotEnoughMagicules(player, cost);
            return;
        }

        boolean learned = grantSkillToTarget(player, target, selectedSkill, sourceInstance, selectedSkillId);

        if (!learned) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.grant_failed")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        consumeMagicules(player, cost);
        recognizeSubordinate(player, target);

        RecognitionAuthorityProgress.recordEmpoweredSubordinate(
                player,
                target
        );

        addUltimateMastery(player, ultimateInstance, benevolent
                ? BENEVOLENT_MASTERY_GRANT_WITHOUT_MASTERY
                : ABSOLUTE_MASTERY_GRANT_WITHOUT_MASTERY);

        AddonAdvancementHelper.awardFirstGift(player);

        sendFeedback(player, Component.translatable(
                benevolent
                        ? "moostensuraaddon.ultimate.benevolent.grant_without_mastery.success"
                        : "moostensuraaddon.ultimate.absolute.grant_without_mastery.success",
                getSkillDisplayName(player, selectedSkillId),
                target.getDisplayName(),
                formatNumber(cost)
        ).withStyle(benevolent ? ChatFormatting.GOLD : ChatFormatting.DARK_PURPLE));
    }

    public static void rangedSkillView(
            ServerPlayer player,
            boolean benevolent
    ) {
        List<LivingEntity> targets =
                SubordinateOverviewService
                        .discoverNearbySubordinates(player);

        for (LivingEntity target : targets) {
            recognizeSubordinate(player, target);
        }

        int masteryGain = Math.min(
                targets.size()
                        * MASTERY_RANGED_SKILL_VIEW_PER_TARGET,
                MASTERY_RANGED_SKILL_VIEW_MAX
        );

        if (masteryGain > 0) {
            addUltimateMastery(
                    player,
                    !benevolent,
                    masteryGain
            );
        }

        SubordinateOverviewService.sendSnapshot(
                player,
                benevolent,
                targets
        );
    }

    private static BorrowOrSeizeResult executeSingleBorrow(ServerPlayer player, LivingEntity target, ResourceLocation skillId) {
        Optional<ManasSkillInstance> targetSkillOptional = SkillAPI.getSkillsFrom(target).getSkill(skillId);

        if (targetSkillOptional.isEmpty()) {
            return BorrowOrSeizeResult.failed();
        }

        if (SkillAPI.getSkillsFrom(player).getSkill(skillId).isPresent()) {
            return BorrowOrSeizeResult.failed();
        }

        ManasSkill selectedSkill = SkillAPI.getSkillRegistry().get(skillId);

        if (selectedSkill == null) {
            return BorrowOrSeizeResult.failed();
        }

        BorrowedSkillData borrowedSkillData = player.getData(AttachmentRegistry.BORROWED_SKILL_DATA);
        int previousBorrowCount = borrowedSkillData.getBorrowCount(skillId.toString());
        double permanentChance = getBorrowPermanentChance(previousBorrowCount);

        ManasSkillInstance borrowedInstance = selectedSkill.createDefaultInstance();
        ManasSkillInstance targetInstance = targetSkillOptional.get();

        borrowedInstance.setMastery(Math.min(targetInstance.getMastery(), borrowedInstance.getMaxMastery()));
        borrowedInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);

        boolean learned = SkillAPI.getSkillsFrom(player).learnSkill(
                borrowedInstance,
                Component.translatable("moostensuraaddon.ultimate.benevolent.borrow.learned", target.getDisplayName())
        );

        if (!learned) {
            return BorrowOrSeizeResult.failed();
        }

        borrowedSkillData.incrementBorrowCount(skillId.toString());
        AddonAdvancementHelper.awardBorrowedPower(player);

        SkillToggleHelper.autoToggleIfPossible(player, borrowedInstance);
        healBeneficiary(target);

        boolean permanent = player.getRandom().nextDouble() < permanentChance;

        if (!permanent) {
            long expiresAt = player.level().getGameTime() + MoosTensuraConfig.BORROW_DURATION_TICKS.get();

            borrowedSkillData.addBorrowedSkill(skillId.toString(), expiresAt);
            player.setData(AttachmentRegistry.BORROWED_SKILL_DATA, borrowedSkillData);
        } else {
            player.setData(AttachmentRegistry.BORROWED_SKILL_DATA, borrowedSkillData);
            AddonAdvancementHelper.awardPowerMadePermanent(player);

            sendFeedback(player, Component.translatable(
                    "moostensuraaddon.ultimate.benevolent.borrow.permanent_single",
                    targetInstance.getDisplayName()
            ).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        return BorrowOrSeizeResult.success(permanent, permanentChance);
    }

    private static BorrowOrSeizeResult executeSingleSeize(ServerPlayer player, LivingEntity target, ResourceLocation skillId) {
        Optional<ManasSkillInstance> targetSkillOptional = SkillAPI.getSkillsFrom(target).getSkill(skillId);

        if (targetSkillOptional.isEmpty()) {
            return BorrowOrSeizeResult.failed();
        }

        if (SkillAPI.getSkillsFrom(player).getSkill(skillId).isPresent()) {
            return BorrowOrSeizeResult.failed();
        }

        ManasSkill selectedSkill = SkillAPI.getSkillRegistry().get(skillId);

        if (selectedSkill == null) {
            return BorrowOrSeizeResult.failed();
        }

        ManasSkillInstance seizedInstance = selectedSkill.createDefaultInstance();
        ManasSkillInstance targetInstance = targetSkillOptional.get();

        seizedInstance.setMastery(Math.min(targetInstance.getMastery(), seizedInstance.getMaxMastery()));
        seizedInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);

        boolean learned = SkillAPI.getSkillsFrom(player).learnSkill(
                seizedInstance,
                Component.translatable("moostensuraaddon.ultimate.absolute.seize.learned", target.getDisplayName())
        );

        if (!learned) {
            return BorrowOrSeizeResult.failed();
        }

        SkillToggleHelper.autoToggleIfPossible(player, seizedInstance);

        SkillAPI.getSkillsFrom(target).forgetSkill(
                skillId,
                Component.translatable("moostensuraaddon.ultimate.absolute.seize.removed", player.getDisplayName())
        );

        dealNonLethalSeizeBacklash(target);

        return BorrowOrSeizeResult.success(false, 0.0D);
    }

    private static boolean grantSkillToTarget(
            ServerPlayer player,
            LivingEntity target,
            ManasSkill selectedSkill,
            ManasSkillInstance sourceInstance,
            ResourceLocation selectedSkillId
    ) {
        ManasSkillInstance grantedInstance = selectedSkill.createDefaultInstance();

        grantedInstance.setMastery(Math.min(sourceInstance.getMastery(), grantedInstance.getMaxMastery()));
        grantedInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);

        boolean learned = SkillAPI.getSkillsFrom(target).learnSkill(
                grantedInstance,
                Component.translatable("moostensuraaddon.granter.learned", player.getDisplayName())
        );

        if (!learned) {
            return false;
        }

        SkillToggleHelper.autoToggleIfPossible(target, grantedInstance);

        GrantedSkillData data = target.getData(AttachmentRegistry.GRANTED_SKILL_DATA);
        data.addGrant(selectedSkillId.toString(), player.getUUID(), false);
        target.setData(AttachmentRegistry.GRANTED_SKILL_DATA, data);

        return true;
    }

    private static List<LivingEntity> getValidMassGrantTargets(ServerPlayer player, ResourceLocation selectedSkillId) {
        return getNearbySubordinates(player)
                .stream()
                .filter(target -> SkillAPI.getSkillsFrom(target).getSkill(selectedSkillId).isEmpty())
                .toList();
    }

    private static List<LivingEntity> getValidRangedTakeBackTargets(ServerPlayer player, ResourceLocation selectedSkillId) {
        return getNearbySubordinates(player)
                .stream()
                .filter(target -> {
                    GrantedSkillData data = target.getData(AttachmentRegistry.GRANTED_SKILL_DATA);

                    return data.getGrant(selectedSkillId.toString(), player.getUUID()).isPresent();
                })
                .toList();
    }

    private static Optional<ManasSkillInstance> getUltimateInstance(ServerPlayer player, boolean benevolent) {
        ResourceLocation skillId = benevolent
                ? SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName()
                : SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName();

        return SkillAPI.getSkillsFrom(player).getSkill(skillId);
    }

    private static double getMassGrantCostPerTarget(boolean benevolent) {
        return benevolent
                ? MoosTensuraConfig.BENEVOLENT_MASS_GRANT_COST_PER_TARGET.get()
                : MoosTensuraConfig.ABSOLUTE_MASS_GRANT_COST_PER_TARGET.get();
    }

    private static double getGrantWithoutMasteryCost(boolean benevolent, ManasSkillInstance sourceInstance) {
        double baseCost = benevolent
                ? MoosTensuraConfig.BENEVOLENT_GRANT_WITHOUT_MASTERY_BASE_COST.get()
                : MoosTensuraConfig.ABSOLUTE_GRANT_WITHOUT_MASTERY_BASE_COST.get();

        double extraCost = benevolent
                ? MoosTensuraConfig.BENEVOLENT_GRANT_WITHOUT_MASTERY_EXTRA_COST.get()
                : MoosTensuraConfig.ABSOLUTE_GRANT_WITHOUT_MASTERY_EXTRA_COST.get();

        if (sourceInstance == null) {
            return baseCost + extraCost;
        }

        double maxMastery = Math.max(0.0D, sourceInstance.getMaxMastery());

        if (maxMastery <= 0.0D) {
            return baseCost + extraCost;
        }

        double currentMastery = Math.max(0.0D, sourceInstance.getMastery());
        double missingRatio = 1.0D - Math.min(1.0D, currentMastery / maxMastery);

        return baseCost + extraCost * missingRatio;
    }

    private static LivingEntity getLookedAtSubordinate(ServerPlayer player) {
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(
                LivingEntity.class,
                player,
                TARGET_RANGE,
                TARGET_RADIUS,
                false,
                true,
                false
        );

        if (target == null) {
            return null;
        }

        return SubordinateHelper.isSubordinate(player, target) ? target : null;
    }

    private static LivingEntity getSubordinateByUuid(ServerPlayer player, UUID targetUuid) {
        if (!(player.serverLevel().getEntity(targetUuid) instanceof LivingEntity target)) {
            return null;
        }

        if (!SubordinateHelper.isSubordinate(player, target)) {
            return null;
        }

        if (player.distanceTo(target) > TARGET_RANGE + 4.0D) {
            return null;
        }

        return target;
    }

    private static List<LivingEntity> getNearbySubordinates(ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(RANGED_RADIUS),
                entity -> entity != player && SubordinateHelper.isSubordinate(player, entity)
        );
    }

    private static boolean isValidBorrowOrSeizeSkill(ResourceLocation skillId, boolean seize) {
        if (skillId == null) {
            return false;
        }

        if (!GranterActions.isGrantableSkill(skillId)) {
            return false;
        }

        if (MoosTensuraConfig.isSkillBlacklisted(
                skillId,
                seize ? MoosTensuraConfig.SkillAction.SEIZE : MoosTensuraConfig.SkillAction.BORROW
        )) {
            return false;
        }

        if (skillId.equals(SkillRegistry.GRANTER.get().getRegistryName())) {
            return false;
        }

        if (skillId.equals(SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName())) {
            return false;
        }

        if (skillId.equals(SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName())) {
            return false;
        }

        return true;
    }

    private static boolean hasBenevolentEmpowerment(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName())
                .isPresent();
    }

    private static boolean hasAbsoluteGovernance(ServerPlayer player) {
        return SkillAPI.getSkillsFrom(player)
                .getSkill(SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName())
                .isPresent();
    }

    private static void recognizeSubordinate(
            ServerPlayer player,
            LivingEntity target
    ) {
        GranterProgressData progress = player.getData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA
        );
        boolean changed = progress.recognizeSubordinate(
                target.getUUID()
        );

        if (changed) {
            player.setData(
                    AttachmentRegistry.GRANTER_PROGRESS_DATA,
                    progress
            );
        }

        RecognitionAuthorityProgress.synchronize(player);
    }

    private static double getCurrentMagicules(
            ServerPlayer player
    ) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);

        return existence == null
                ? 0.0D
                : Math.max(0.0D, existence.getMagicule());
    }

    private static boolean hasMagicules(ServerPlayer player, double amount) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return false;
        }

        return existence.getMagicule() >= amount;
    }

    private static void consumeMagicules(ServerPlayer player, double amount) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return;
        }

        existence.setMagicule(Math.max(0.0D, existence.getMagicule() - amount));
        existence.markDirty();
    }

    private static void sendNotEnoughMagicules(ServerPlayer player, double required) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        double current = existence == null ? 0.0D : existence.getMagicule();

        sendFeedback(player, Component.translatable(
                "moostensuraaddon.ultimate.error.not_enough_magicules",
                formatNumber(required),
                formatNumber(current)
        ).withStyle(ChatFormatting.RED));
    }

    private static void sendNoSelectedSkill(ServerPlayer player) {
        sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_skill_selected")
                .withStyle(ChatFormatting.RED));
    }

    private static void healBeneficiary(LivingEntity target) {
        float healAmount = Math.max(1.0F, target.getMaxHealth() * 0.05F);
        target.heal(healAmount);
    }

    private static void dealNonLethalSeizeBacklash(LivingEntity target) {
        float damage = Math.max(1.0F, target.getMaxHealth() * 0.05F);
        target.setHealth(Math.max(1.0F, target.getHealth() - damage));
    }

    private static double getBorrowPermanentChance(int previousBorrowCount) {
        double baseChance = MoosTensuraConfig.BORROW_PERMANENT_BASE_CHANCE.get();
        double bonusPerBorrow = MoosTensuraConfig.BORROW_PERMANENT_BONUS_PER_PREVIOUS_BORROW.get();
        double maxChance = MoosTensuraConfig.BORROW_PERMANENT_MAX_CHANCE.get();

        double chance = baseChance + Math.max(0, previousBorrowCount) * bonusPerBorrow;

        return Math.max(0.0D, Math.min(maxChance, chance));
    }

    private static double getSeizeDeathChance(int successfulSeizedSkills) {
        if (successfulSeizedSkills <= 0) {
            return 0.0D;
        }

        double chancePerSkill = MoosTensuraConfig.SEIZE_DEATH_CHANCE_PER_SKILL.get();
        double maxChance = MoosTensuraConfig.SEIZE_DEATH_CHANCE_MAX.get();

        double chance = successfulSeizedSkills * chancePerSkill;

        return Math.max(0.0D, Math.min(maxChance, chance));
    }

    private static boolean rollSeizeDeath(ServerPlayer player, LivingEntity target, double deathChance) {
        if (player == null || target == null || deathChance <= 0.0D) {
            return false;
        }

        if (target.isDeadOrDying()) {
            return false;
        }

        boolean shouldDie = player.getRandom().nextDouble() < deathChance;

        if (!shouldDie) {
            return false;
        }

        target.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);

        return target.isDeadOrDying() || target.getHealth() <= 0.0F;
    }

    private static void addUltimateMastery(ServerPlayer player, boolean absolute, int amount) {
        if (amount <= 0) {
            return;
        }

        ResourceLocation skillId = absolute
                ? SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName()
                : SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName();

        SkillAPI.getSkillsFrom(player)
                .getSkill(skillId)
                .ifPresent(instance -> addUltimateMastery(player, instance, amount));
    }

    private static void addUltimateMastery(ServerPlayer player, ManasSkillInstance instance, int amount) {
        if (amount <= 0 || instance == null) {
            return;
        }

        double beforeMastery = Math.max(0.0D, instance.getMastery());

        instance.addMasteryPoint(player, amount);
        instance.markDirty();
        SkillAPI.getSkillsFrom(player).checkAndMarkDirty(instance);

        double afterMastery = Math.max(0.0D, instance.getMastery());

        if (DEBUG_ULTIMATE_MASTERY) {
            sendFeedback(player, Component.literal(
                    "[Ultimate Mastery] +" + amount
                            + " | " + formatNumber(beforeMastery)
                            + " -> " + formatNumber(afterMastery)
                            + " / " + formatNumber(instance.getMaxMastery())
            ).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private static Component getSkillDisplayName(ServerPlayer player, ResourceLocation skillId) {
        if (player != null && skillId != null) {
            Optional<ManasSkillInstance> playerSkill = SkillAPI.getSkillsFrom(player).getSkill(skillId);

            if (playerSkill.isPresent()) {
                return playerSkill.get().getDisplayName().copy();
            }
        }

        return skillId == null
                ? AuthorityText.component("unknown_skill")
                : Component.literal(skillId.toString());
    }

    private static void sendFeedback(ServerPlayer player, Component message) {
        ActionbarHelper.send(player, message);
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.US, "%.1f%%", value * 100.0D);
    }

    private record MultiGrantCandidate(
            ResourceLocation skillId,
            ManasSkill skill,
            ManasSkillInstance sourceInstance,
            double cost
    ) {

        private MultiGrantCandidate {
            cost = !Double.isFinite(cost) || cost < 0.0D
                    ? 0.0D
                    : cost;
        }
    }

    private record BorrowOrSeizeResult(
            boolean success,
            boolean permanent,
            double permanentChance
    ) {
        private static BorrowOrSeizeResult failed() {
            return new BorrowOrSeizeResult(false, false, 0.0D);
        }

        private static BorrowOrSeizeResult success(boolean permanent, double permanentChance) {
            return new BorrowOrSeizeResult(true, permanent, permanentChance);
        }
    }
}