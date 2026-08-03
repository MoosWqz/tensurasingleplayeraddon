package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.GrantedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.network.OpenUltimateMultiGrantScreenPayload;
import com.mooswqz.moostensuraaddon.recognition.RecognitionAuthorityProgress;
import com.mooswqz.moostensuraaddon.skill.GranterMode;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AuthorityActionService {

    private static final double TARGET_RANGE = 16.0D;
    private static final double TARGET_RADIUS = 0.5D;
    private static final double BENEVOLENT_SCOPE_RADIUS = 32.0D;
    private static final double GOVERNANCE_SCOPE_RADIUS = 128.0D;

    private static final int MASTERY_SUCCESSFUL_GRANT = 25;
    private static final int MASTERY_SUCCESSFUL_TAKE_BACK = 5;
    private static final int MASTERY_NEW_SUBORDINATE_BONUS = 25;
    private static final int MASTERY_NEW_SKILL_TYPE_BONUS = 15;
    private static final int BENEVOLENT_DIRECT_MASTERY_PER_SKILL = 35;
    private static final int GOVERNANCE_DIRECT_MASTERY_PER_SKILL = 30;
    private static final int BENEVOLENT_MASS_MASTERY_PER_TARGET = 12;
    private static final int GOVERNANCE_MASS_MASTERY_PER_TARGET = 10;
    private static final int TAKE_BACK_MASTERY_PER_TARGET = 6;

    private AuthorityActionService() {
    }

    public static void openGranterGrant(
            ServerPlayer player,
            ManasSkillInstance granterInstance
    ) {
        AuthorityCostMigrationService.applyRecommendedDefaults();

        LivingEntity target = getLookedAtSubordinate(player);

        if (target == null) {
            sendError(player, AuthorityText.component("error.look_at_subordinate"));
            return;
        }

        int cooldownTicks = granterInstance == null
                ? 0
                : Math.max(
                0,
                granterInstance.getCoolDown(GranterMode.GRANT.id())
        );

        List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries =
                buildGrantEntries(
                        player,
                        target,
                        AuthorityActionMode.GRANTER_GRANT,
                        List.of(target)
                );

        openScreen(
                player,
                AuthorityActionMode.GRANTER_GRANT,
                target,
                getCurrentMagicules(player),
                cooldownTicks,
                false,
                false,
                entries
        );
    }

    public static void openGranterTakeBack(
            ServerPlayer player,
            ManasSkillInstance granterInstance
    ) {
        AuthorityCostMigrationService.applyRecommendedDefaults();

        LivingEntity target = getLookedAtSubordinate(player);

        if (target == null) {
            sendError(player, AuthorityText.component("error.look_at_subordinate"));
            return;
        }

        List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries =
                buildTakeBackEntries(
                        player,
                        List.of(target),
                        1
                );

        if (entries.isEmpty()) {
            sendError(
                    player,
                    AuthorityText.component(
                            "error.target_has_no_grant",
                            target.getDisplayName()
                    )
            );
            return;
        }

        openScreen(
                player,
                AuthorityActionMode.GRANTER_TAKE_BACK,
                target,
                getCurrentMagicules(player),
                0,
                false,
                false,
                entries
        );
    }

    public static void openUltimateBestow(
            ServerPlayer player,
            boolean benevolent
    ) {
        AuthorityCostMigrationService.applyRecommendedDefaults();

        AuthorityActionMode mode = benevolent
                ? AuthorityActionMode.BENEVOLENT_BESTOW
                : AuthorityActionMode.GOVERNANCE_INVEST;

        if (!hasAuthority(player, mode)) {
            sendError(player, AuthorityText.component("error.ultimate_missing"));
            return;
        }

        LivingEntity target = getLookedAtSubordinate(player);

        if (target == null) {
            sendError(player, AuthorityText.component("error.look_at_subordinate"));
            return;
        }

        List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries =
                buildGrantEntries(
                        player,
                        target,
                        mode,
                        List.of(target)
                );

        openScreen(
                player,
                mode,
                target,
                getCurrentMagicules(player),
                0,
                false,
                false,
                entries
        );
    }

    public static void openUltimateMassGrant(
            ServerPlayer player,
            boolean benevolent
    ) {
        AuthorityCostMigrationService.applyRecommendedDefaults();

        AuthorityActionMode mode = benevolent
                ? AuthorityActionMode.BENEVOLENT_MASS_GRANT
                : AuthorityActionMode.GOVERNANCE_MASS_GRANT;

        if (!hasAuthority(player, mode)) {
            sendError(player, AuthorityText.component("error.ultimate_missing"));
            return;
        }

        List<LivingEntity> scope = getScopeSubordinates(player, mode);

        if (scope.isEmpty()) {
            sendError(player, AuthorityText.component("error.no_eligible_subordinates"));
            return;
        }

        List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries =
                buildMassGrantEntries(player, mode, scope);

        openScreen(
                player,
                mode,
                null,
                getCurrentMagicules(player),
                0,
                false,
                true,
                entries
        );
    }

    public static void openUltimateTakeBack(
            ServerPlayer player,
            boolean benevolent
    ) {
        AuthorityCostMigrationService.applyRecommendedDefaults();

        AuthorityActionMode mode = benevolent
                ? AuthorityActionMode.BENEVOLENT_TAKE_BACK
                : AuthorityActionMode.GOVERNANCE_TAKE_BACK;

        if (!hasAuthority(player, mode)) {
            sendError(player, AuthorityText.component("error.ultimate_missing"));
            return;
        }

        LivingEntity lookedTarget = getLookedAtSubordinate(player);
        List<LivingEntity> scope = getScopeSubordinates(player, mode);
        List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries =
                buildTakeBackEntries(
                        player,
                        scope,
                        scope.size()
                );

        if (entries.isEmpty()) {
            sendError(player, AuthorityText.component("error.no_reclaimable_grants"));
            return;
        }

        openScreen(
                player,
                mode,
                lookedTarget,
                getCurrentMagicules(player),
                0,
                true,
                lookedTarget == null,
                entries
        );
    }

    public static void execute(
            ServerPlayer player,
            String rawActionId,
            String rawTargetUuid,
            boolean allEligible,
            List<String> rawSkillIds
    ) {
        AuthorityCostMigrationService.applyRecommendedDefaults();

        Optional<AuthorityActionMode> modeOptional =
                AuthorityActionMode.fromId(rawActionId);

        if (modeOptional.isEmpty()) {
            sendError(player, AuthorityText.component("error.unknown_action"));
            return;
        }

        AuthorityActionMode mode = modeOptional.orElseThrow();
        AuthorityActionPolicy.RequestAnalysis request =
                AuthorityActionPolicy.analyseRequest(
                        mode,
                        rawSkillIds
                );

        if (request.uniqueSkillIds().isEmpty()) {
            sendError(player, AuthorityText.component("error.select_skill"));
            return;
        }

        if (request.malformed()) {
            sendError(
                    player,
                    request.overLimit()
                            ? AuthorityText.component("error.selection_over_limit")
                            : AuthorityText.component("error.invalid_or_duplicate")
            );
            return;
        }

        List<ResourceLocation> skillIds = new ArrayList<>();

        for (String rawSkillId : request.uniqueSkillIds()) {
            ResourceLocation skillId = ResourceLocation.tryParse(rawSkillId);

            if (skillId == null) {
                sendError(player, AuthorityText.component("error.invalid_registry_id"));
                return;
            }

            skillIds.add(skillId);
        }

        if (!hasAuthority(player, mode)) {
            sendError(player, AuthorityText.component("error.authority_unavailable"));
            return;
        }

        switch (mode) {
            case GRANTER_GRANT,
                 BENEVOLENT_BESTOW,
                 GOVERNANCE_INVEST -> executeDirectGrant(
                    player,
                    mode,
                    parseTargetUuid(rawTargetUuid),
                    skillIds
            );
            case GRANTER_TAKE_BACK -> executeTakeBack(
                    player,
                    mode,
                    parseTargetUuid(rawTargetUuid),
                    false,
                    skillIds.getFirst()
            );
            case BENEVOLENT_MASS_GRANT,
                 GOVERNANCE_MASS_GRANT -> executeMassGrant(
                    player,
                    mode,
                    skillIds.getFirst()
            );
            case BENEVOLENT_TAKE_BACK,
                 GOVERNANCE_TAKE_BACK -> executeTakeBack(
                    player,
                    mode,
                    parseTargetUuid(rawTargetUuid),
                    allEligible,
                    skillIds.getFirst()
            );
        }
    }

    private static void executeDirectGrant(
            ServerPlayer player,
            AuthorityActionMode mode,
            UUID targetUuid,
            List<ResourceLocation> skillIds
    ) {
        if (targetUuid == null) {
            sendError(player, AuthorityText.component("error.invalid_subordinate"));
            return;
        }

        LivingEntity target = getSubordinateByUuid(player, targetUuid);

        if (target == null) {
            sendError(player, AuthorityText.component("error.subordinate_unavailable"));
            return;
        }

        Optional<ManasSkillInstance> authorityOptional =
                getAuthorityInstance(player, mode);

        if (authorityOptional.isEmpty()) {
            sendError(player, AuthorityText.component("error.authority_unavailable"));
            return;
        }

        ManasSkillInstance authority = authorityOptional.orElseThrow();

        if (mode == AuthorityActionMode.GRANTER_GRANT
                && authority.onCoolDown(GranterMode.GRANT.id())) {
            sendError(
                    player,
                    AuthorityText.component(
                            "error.grant_cooldown",
                            formatSeconds(
                                    authority.getCoolDown(GranterMode.GRANT.id())
                            )
                    )
            );
            return;
        }

        List<GrantCandidate> candidates = new ArrayList<>();

        for (ResourceLocation skillId : skillIds) {
            Optional<ManasSkillInstance> sourceOptional =
                    SkillAPI.getSkillsFrom(player).getSkill(skillId);
            ManasSkill skill = SkillAPI.getSkillRegistry().get(skillId);

            if (sourceOptional.isEmpty() || skill == null) {
                sendError(player, AuthorityText.component("error.skill_unavailable"));
                return;
            }

            ManasSkillInstance source = sourceOptional.orElseThrow();

            if (!GranterActions.isGrantableSkill(skillId)
                    || SkillCategoryHelper.isIntrinsic(
                    source,
                    skillId,
                    source.getDisplayName()
            )) {
                sendError(player, AuthorityText.component(
                        "error.cannot_transfer",
                        source.getDisplayName()
                ));
                return;
            }

            if (mode == AuthorityActionMode.GRANTER_GRANT
                    && !source.isMastered(player)) {
                sendError(player, AuthorityText.component(
                        "error.not_mastered",
                        source.getDisplayName()
                ));
                return;
            }

            if (SkillAPI.getSkillsFrom(target).getSkill(skillId).isPresent()) {
                sendError(
                        player,
                        AuthorityText.component(
                                "error.target_already_possesses",
                                target.getDisplayName(),
                                source.getDisplayName()
                        )
                );
                return;
            }

            CostBreakdown cost = getDirectCost(mode, source, player);
            candidates.add(new GrantCandidate(skillId, skill, source, cost.finalCost()));
        }

        double totalCost = AuthorityActionPolicy.sumCosts(
                candidates.stream()
                        .map(GrantCandidate::cost)
                        .toList()
        );

        if (!hasMagicules(player, totalCost)) {
            sendNotEnoughMagicules(player, totalCost);
            return;
        }

        List<ResourceLocation> applied = new ArrayList<>();

        for (GrantCandidate candidate : candidates) {
            if (!grantSkillToTarget(
                    player,
                    target,
                    candidate.skill(),
                    candidate.source(),
                    candidate.skillId()
            )) {
                rollbackGrantedSkills(player, target, applied);
                sendError(
                        player,
                        AuthorityText.component("error.transfer_rolled_back")
                );
                return;
            }

            applied.add(candidate.skillId());
        }

        consumeMagicules(player, totalCost);
        recognizeSubordinate(player, target);

        if (mode == AuthorityActionMode.GRANTER_GRANT) {
            authority.setCoolDown(
                    GranterMode.GRANT.id(),
                    Math.max(0, MoosTensuraConfig.GRANTER_GRANT_COOLDOWN_TICKS.get())
            );
            recordGranterGrant(
                    player,
                    authority,
                    target,
                    skillIds.getFirst()
            );
        } else {
            int masteryPerSkill = mode.benevolent()
                    ? BENEVOLENT_DIRECT_MASTERY_PER_SKILL
                    : GOVERNANCE_DIRECT_MASTERY_PER_SKILL;
            addMastery(player, authority, masteryPerSkill * candidates.size());
            RecognitionAuthorityProgress.recordEmpoweredSubordinate(player, target);
            RecognitionAuthorityProgress.recordMassGrant(player, candidates.size());
            RecognitionAuthorityProgress.synchronize(player);
        }

        authority.markDirty();
        AddonAdvancementHelper.awardFirstGift(player);

        sendSuccess(
                player,
                mode == AuthorityActionMode.GRANTER_GRANT
                        ? AuthorityText.component(
                        "success.granter_grant",
                        candidates.getFirst().source().getDisplayName(),
                        target.getDisplayName(),
                        formatNumber(totalCost)
                )
                        : AuthorityText.component(
                        candidates.size() == 1
                        ? "success.direct_one"
                        : "success.direct_many",
                        mode.titleComponent(),
                        candidates.size(),
                        target.getDisplayName(),
                        formatNumber(totalCost)
                )
        );
    }

    private static void executeMassGrant(
            ServerPlayer player,
            AuthorityActionMode mode,
            ResourceLocation skillId
    ) {
        Optional<ManasSkillInstance> authorityOptional =
                getAuthorityInstance(player, mode);
        Optional<ManasSkillInstance> sourceOptional =
                SkillAPI.getSkillsFrom(player).getSkill(skillId);
        ManasSkill skill = SkillAPI.getSkillRegistry().get(skillId);

        if (authorityOptional.isEmpty()
                || sourceOptional.isEmpty()
                || skill == null) {
            sendError(player, AuthorityText.component("error.mass_skill_unavailable"));
            return;
        }

        ManasSkillInstance source = sourceOptional.orElseThrow();

        if (!source.isMastered(player)) {
            sendError(player, AuthorityText.component("error.mass_requires_mastery"));
            return;
        }

        if (!GranterActions.isGrantableSkill(skillId)
                || SkillCategoryHelper.isIntrinsic(
                source,
                skillId,
                source.getDisplayName()
        )) {
            sendError(player, AuthorityText.component(
                    "error.cannot_mass_grant",
                    source.getDisplayName()
            ));
            return;
        }

        List<LivingEntity> targets = getScopeSubordinates(player, mode)
                .stream()
                .filter(target -> SkillAPI.getSkillsFrom(target)
                        .getSkill(skillId)
                        .isEmpty())
                .toList();

        if (targets.isEmpty()) {
            sendError(player, AuthorityText.component("error.no_mass_recipient"));
            return;
        }

        double costPerTarget = getMassGrantCost(mode);
        double totalCost = costPerTarget * targets.size();

        if (!hasMagicules(player, totalCost)) {
            sendNotEnoughMagicules(player, totalCost);
            return;
        }

        List<LivingEntity> appliedTargets = new ArrayList<>();

        for (LivingEntity target : targets) {
            if (!grantSkillToTarget(
                    player,
                    target,
                    skill,
                    source,
                    skillId
            )) {
                for (LivingEntity appliedTarget : appliedTargets) {
                    rollbackGrantedSkills(
                            player,
                            appliedTarget,
                            List.of(skillId)
                    );
                }

                sendError(
                        player,
                        AuthorityText.component("error.mass_rolled_back")
                );
                return;
            }

            appliedTargets.add(target);
        }

        consumeMagicules(player, totalCost);

        for (LivingEntity target : targets) {
            recognizeSubordinate(player, target);
            RecognitionAuthorityProgress.recordEmpoweredSubordinate(player, target);
        }

        RecognitionAuthorityProgress.recordMassGrant(player, targets.size());
        RecognitionAuthorityProgress.synchronize(player);

        ManasSkillInstance authority = authorityOptional.orElseThrow();
        addMastery(
                player,
                authority,
                targets.size() * (mode.benevolent()
                        ? BENEVOLENT_MASS_MASTERY_PER_TARGET
                        : GOVERNANCE_MASS_MASTERY_PER_TARGET)
        );

        AddonAdvancementHelper.awardFirstGift(player);

        sendSuccess(
                player,
                AuthorityText.component(
                        targets.size() == 1
                                ? "success.mass_one"
                                : "success.mass_many",
                        source.getDisplayName(),
                        targets.size(),
                        formatNumber(totalCost)
                )
        );
    }

    private static void executeTakeBack(
            ServerPlayer player,
            AuthorityActionMode mode,
            UUID targetUuid,
            boolean allEligible,
            ResourceLocation skillId
    ) {
        Optional<ManasSkillInstance> authorityOptional =
                getAuthorityInstance(player, mode);

        if (authorityOptional.isEmpty()) {
            sendError(player, AuthorityText.component("error.authority_unavailable"));
            return;
        }

        List<LivingEntity> targets;

        if (mode == AuthorityActionMode.GRANTER_TAKE_BACK
                || !allEligible) {
            if (targetUuid == null) {
                sendError(player, AuthorityText.component("error.select_target_or_all"));
                return;
            }

            LivingEntity target = getSubordinateByUuid(player, targetUuid);

            if (target == null) {
                sendError(player, AuthorityText.component("error.subordinate_unavailable"));
                return;
            }

            targets = List.of(target);
        } else {
            targets = getScopeSubordinates(player, mode);
        }

        List<TakeBackCandidate> candidates = new ArrayList<>();

        for (LivingEntity target : targets) {
            GrantedSkillData data = target.getData(
                    AttachmentRegistry.GRANTED_SKILL_DATA
            );
            Optional<GrantedSkillData.GrantedSkillRecord> record =
                    data.getGrant(skillId.toString(), player.getUUID());

            if (record.isPresent()) {
                candidates.add(
                        new TakeBackCandidate(
                                target,
                                record.orElseThrow()
                        )
                );
            } else if (!allEligible) {
                sendError(
                        player,
                        AuthorityText.component(
                                "error.target_missing_your_grant",
                                target.getDisplayName()
                        )
                );
                return;
            }
        }

        if (candidates.isEmpty()) {
            sendError(player, AuthorityText.component("error.no_grant_to_take_back"));
            return;
        }

        for (TakeBackCandidate candidate : candidates) {
            LivingEntity target = candidate.target();
            GrantedSkillData data = target.getData(
                    AttachmentRegistry.GRANTED_SKILL_DATA
            );

            if (!candidate.record().targetHadSkillBefore()) {
                SkillAPI.getSkillsFrom(target).forgetSkill(
                        skillId,
                        Component.translatable(
                                "moostensuraaddon.granter.taken_back",
                                player.getDisplayName()
                        )
                );
            }

            data.removeGrant(skillId.toString(), player.getUUID());
            target.setData(
                    AttachmentRegistry.GRANTED_SKILL_DATA,
                    data
            );
            recognizeSubordinate(player, target);
        }

        ManasSkillInstance authority = authorityOptional.orElseThrow();

        if (mode == AuthorityActionMode.GRANTER_TAKE_BACK) {
            recordGranterTakeBack(
                    player,
                    authority,
                    candidates.getFirst().target()
            );
        } else {
            addMastery(
                    player,
                    authority,
                    candidates.size() * TAKE_BACK_MASTERY_PER_TARGET
            );
            RecognitionAuthorityProgress.recordGlobalTakeBack(
                    player,
                    candidates.size(),
                    mode.benevolent()
            );
            RecognitionAuthorityProgress.synchronize(player);
        }

        AddonAdvancementHelper.awardWhatWasGivenCanReturn(player);

        sendSuccess(
                player,
                AuthorityText.component(
                        candidates.size() == 1
                                ? "success.take_back_one"
                                : "success.take_back_many",
                        getSkillDisplayName(player, skillId),
                        candidates.size()
                )
        );
    }

    private static List<OpenUltimateMultiGrantScreenPayload.SkillEntry>
    buildGrantEntries(
            ServerPlayer player,
            LivingEntity target,
            AuthorityActionMode mode,
            List<LivingEntity> affectedTargets
    ) {
        List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries =
                new ArrayList<>();

        for (ManasSkillInstance source :
                SkillAPI.getSkillsFrom(player).getLearnedSkills()) {
            if (source == null || source.getSkillId() == null) {
                continue;
            }

            ResourceLocation skillId = source.getSkillId();
            Component displayName = source.getDisplayName();

            if (!GranterActions.isGrantableSkill(skillId)
                    || SkillCategoryHelper.isIntrinsic(
                    source,
                    skillId,
                    displayName
            )) {
                continue;
            }

            boolean mastered = source.isMastered(player);
            boolean targetAlreadyHas = SkillAPI.getSkillsFrom(target)
                    .getSkill(skillId)
                    .isPresent();
            boolean selectable = !targetAlreadyHas
                    && (mode.supportsUnmasteredSkills() || mastered);
            String disabledReason = "";

            if (targetAlreadyHas) {
                disabledReason = UiTranslationToken.encode(
                        "message.moostensuraaddon.authority.disabled.target_has_skill"
                );
            } else if (!mastered
                    && !mode.supportsUnmasteredSkills()) {
                disabledReason = UiTranslationToken.encode(
                        "message.moostensuraaddon.authority.disabled.granter_mastered_only"
                );
            }

            CostBreakdown cost = getDirectCost(mode, source, player);
            SkillCategoryHelper.SkillCategory category =
                    SkillCategoryHelper.getCategory(
                            source,
                            skillId,
                            displayName
                    );

            entries.add(
                    new OpenUltimateMultiGrantScreenPayload.SkillEntry(
                            skillId.toString(),
                            displayName.getString(),
                            category.name(),
                            mastered,
                            selectable,
                            disabledReason,
                            cost.standardCost(),
                            cost.surcharge(),
                            cost.finalCost(),
                            Math.max(1, affectedTargets.size())
                    )
            );
        }

        return sortEntries(entries);
    }

    private static List<OpenUltimateMultiGrantScreenPayload.SkillEntry>
    buildMassGrantEntries(
            ServerPlayer player,
            AuthorityActionMode mode,
            List<LivingEntity> scope
    ) {
        List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries =
                new ArrayList<>();
        double costPerTarget = getMassGrantCost(mode);

        for (ManasSkillInstance source :
                SkillAPI.getSkillsFrom(player).getLearnedSkills()) {
            if (source == null || source.getSkillId() == null) {
                continue;
            }

            ResourceLocation skillId = source.getSkillId();
            Component displayName = source.getDisplayName();

            if (!GranterActions.isGrantableSkill(skillId)
                    || SkillCategoryHelper.isIntrinsic(
                    source,
                    skillId,
                    displayName
            )
                    || !source.isMastered(player)) {
                continue;
            }

            int affectedTargets = (int) scope.stream()
                    .filter(target -> SkillAPI.getSkillsFrom(target)
                            .getSkill(skillId)
                            .isEmpty())
                    .count();
            boolean selectable = affectedTargets > 0;
            String disabledReason = selectable
                    ? ""
                    : UiTranslationToken.encode(
                    "message.moostensuraaddon.authority.disabled.every_target_has_skill"
            );
            double totalCost = costPerTarget * affectedTargets;
            SkillCategoryHelper.SkillCategory category =
                    SkillCategoryHelper.getCategory(
                            source,
                            skillId,
                            displayName
                    );

            entries.add(
                    new OpenUltimateMultiGrantScreenPayload.SkillEntry(
                            skillId.toString(),
                            displayName.getString(),
                            category.name(),
                            true,
                            selectable,
                            disabledReason,
                            costPerTarget,
                            0.0D,
                            totalCost,
                            affectedTargets
                    )
            );
        }

        return sortEntries(entries);
    }

    private static List<OpenUltimateMultiGrantScreenPayload.SkillEntry>
    buildTakeBackEntries(
            ServerPlayer player,
            List<LivingEntity> scope,
            int maximumTargets
    ) {
        Map<String, TakeBackAggregate> aggregates = new LinkedHashMap<>();

        for (LivingEntity target : scope.stream()
                .limit(Math.max(1, maximumTargets))
                .toList()) {
            GrantedSkillData data = target.getData(
                    AttachmentRegistry.GRANTED_SKILL_DATA
            );

            for (ManasSkillInstance instance :
                    SkillAPI.getSkillsFrom(target).getLearnedSkills()) {
                if (instance == null || instance.getSkillId() == null) {
                    continue;
                }

                ResourceLocation skillId = instance.getSkillId();

                if (data.getGrant(
                        skillId.toString(),
                        player.getUUID()
                ).isEmpty()) {
                    continue;
                }

                SkillCategoryHelper.SkillCategory category =
                        SkillCategoryHelper.getCategory(
                                instance,
                                skillId,
                                instance.getDisplayName()
                        );

                aggregates.compute(
                        skillId.toString(),
                        (ignored, current) -> current == null
                                ? new TakeBackAggregate(
                                skillId.toString(),
                                instance.getDisplayName().getString(),
                                category.name(),
                                1
                        )
                                : current.increment()
                );
            }
        }

        List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries =
                new ArrayList<>();

        for (TakeBackAggregate aggregate : aggregates.values()) {
            entries.add(
                    new OpenUltimateMultiGrantScreenPayload.SkillEntry(
                            aggregate.skillId(),
                            aggregate.displayName(),
                            aggregate.category(),
                            true,
                            true,
                            "",
                            0.0D,
                            0.0D,
                            0.0D,
                            aggregate.targetCount()
                    )
            );
        }

        return sortEntries(entries);
    }

    private static List<OpenUltimateMultiGrantScreenPayload.SkillEntry>
    sortEntries(
            List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries
    ) {
        entries.sort(
                Comparator.comparingInt(
                                (OpenUltimateMultiGrantScreenPayload.SkillEntry entry) ->
                                        parseCategory(entry.category()).sortOrder()
                        )
                        .thenComparing(
                                OpenUltimateMultiGrantScreenPayload.SkillEntry::displayName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                OpenUltimateMultiGrantScreenPayload.SkillEntry::skillId
                        )
        );

        return List.copyOf(entries);
    }

    private static SkillCategoryHelper.SkillCategory parseCategory(
            String rawCategory
    ) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return SkillCategoryHelper.SkillCategory.OTHER;
        }

        try {
            return SkillCategoryHelper.SkillCategory.valueOf(
                    rawCategory.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            return SkillCategoryHelper.SkillCategory.OTHER;
        }
    }

    private static void openScreen(
            ServerPlayer player,
            AuthorityActionMode mode,
            LivingEntity target,
            double availableMagicules,
            int cooldownTicks,
            boolean allowAllEligible,
            boolean allEligibleByDefault,
            List<OpenUltimateMultiGrantScreenPayload.SkillEntry> entries
    ) {
        if (entries.isEmpty()) {
            sendError(player, AuthorityText.component("error.no_skill_available"));
            return;
        }

        if (target != null) {
            recognizeSubordinate(player, target);
        }

        String targetName;

        if (target != null) {
            targetName = target.getDisplayName().getString();
        } else if (mode == AuthorityActionMode.GOVERNANCE_TAKE_BACK) {
            targetName = UiTranslationToken.encode(
                    "message.moostensuraaddon.authority.scope.governance_128"
            );
        } else {
            targetName = UiTranslationToken.encode(
                    "message.moostensuraaddon.authority.scope.benevolent_32"
            );
        }

        PacketDistributor.sendToPlayer(
                player,
                new OpenUltimateMultiGrantScreenPayload(
                        mode.id(),
                        target == null ? "" : target.getUUID().toString(),
                        targetName,
                        availableMagicules,
                        cooldownTicks,
                        allowAllEligible,
                        allEligibleByDefault,
                        entries
                )
        );
    }

    private static Optional<ManasSkillInstance> getAuthorityInstance(
            ServerPlayer player,
            AuthorityActionMode mode
    ) {
        ResourceLocation authorityId;

        if (mode.granter()) {
            authorityId = SkillRegistry.GRANTER.get().getRegistryName();
        } else if (mode.benevolent()) {
            authorityId = SkillRegistry.BENEVOLENT_EMPOWERMENT
                    .get()
                    .getRegistryName();
        } else {
            authorityId = SkillRegistry.ABSOLUTE_GOVERNANCE
                    .get()
                    .getRegistryName();
        }

        return SkillAPI.getSkillsFrom(player).getSkill(authorityId);
    }

    private static boolean hasAuthority(
            ServerPlayer player,
            AuthorityActionMode mode
    ) {
        return getAuthorityInstance(player, mode).isPresent();
    }

    private static CostBreakdown getDirectCost(
            AuthorityActionMode mode,
            ManasSkillInstance source,
            ServerPlayer player
    ) {
        if (mode == AuthorityActionMode.GRANTER_GRANT) {
            double cost = sanitizeCost(
                    MoosTensuraConfig.GRANTER_GRANT_MAGICULE_COST.get()
            );
            return new CostBreakdown(cost, 0.0D, cost);
        }

        if (!mode.supportsUnmasteredSkills()) {
            return new CostBreakdown(0.0D, 0.0D, 0.0D);
        }

        double baseCost = sanitizeCost(
                mode.benevolent()
                        ? MoosTensuraConfig
                        .BENEVOLENT_GRANT_WITHOUT_MASTERY_BASE_COST
                        .get()
                        : MoosTensuraConfig
                        .ABSOLUTE_GRANT_WITHOUT_MASTERY_BASE_COST
                        .get()
        );
        double maximumExtra = sanitizeCost(
                mode.benevolent()
                        ? MoosTensuraConfig
                        .BENEVOLENT_GRANT_WITHOUT_MASTERY_EXTRA_COST
                        .get()
                        : MoosTensuraConfig
                        .ABSOLUTE_GRANT_WITHOUT_MASTERY_EXTRA_COST
                        .get()
        );

        if (source == null || source.isMastered(player)) {
            return new CostBreakdown(baseCost, 0.0D, baseCost);
        }

        double maxMastery = Math.max(0.0D, source.getMaxMastery());
        double currentMastery = Math.max(0.0D, source.getMastery());
        double missingRatio = maxMastery <= 0.0D
                ? 1.0D
                : 1.0D - Math.min(1.0D, currentMastery / maxMastery);
        double surcharge = maximumExtra * missingRatio;

        return new CostBreakdown(
                baseCost,
                surcharge,
                baseCost + surcharge
        );
    }

    private static double getMassGrantCost(
            AuthorityActionMode mode
    ) {
        return sanitizeCost(
                mode.benevolent()
                        ? MoosTensuraConfig
                        .BENEVOLENT_MASS_GRANT_COST_PER_TARGET
                        .get()
                        : MoosTensuraConfig
                        .ABSOLUTE_MASS_GRANT_COST_PER_TARGET
                        .get()
        );
    }

    private static List<LivingEntity> getScopeSubordinates(
            ServerPlayer player,
            AuthorityActionMode mode
    ) {
        double radius = mode.governance()
                ? GOVERNANCE_SCOPE_RADIUS
                : BENEVOLENT_SCOPE_RADIUS;

        return player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                entity -> entity != player
                        && SubordinateHelper.isSubordinate(player, entity)
        );
    }

    private static LivingEntity getLookedAtSubordinate(
            ServerPlayer player
    ) {
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(
                LivingEntity.class,
                player,
                TARGET_RANGE,
                TARGET_RADIUS,
                false,
                true,
                false
        );

        return target != null
                && SubordinateHelper.isSubordinate(player, target)
                ? target
                : null;
    }

    private static LivingEntity getSubordinateByUuid(
            ServerPlayer player,
            UUID targetUuid
    ) {
        if (targetUuid == null
                || !(player.serverLevel().getEntity(targetUuid)
                instanceof LivingEntity target)) {
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

    private static UUID parseTargetUuid(String rawUuid) {
        if (rawUuid == null || rawUuid.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(rawUuid.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean grantSkillToTarget(
            ServerPlayer player,
            LivingEntity target,
            ManasSkill skill,
            ManasSkillInstance source,
            ResourceLocation skillId
    ) {
        ManasSkillInstance granted = skill.createDefaultInstance();
        granted.setMastery(
                Math.min(
                        source.getMastery(),
                        granted.getMaxMastery()
                )
        );
        granted.getOrCreateTag().putBoolean("NoMagiculeCost", true);

        boolean learned = SkillAPI.getSkillsFrom(target).learnSkill(
                granted,
                Component.translatable(
                        "moostensuraaddon.granter.learned",
                        player.getDisplayName()
                )
        );

        if (!learned) {
            return false;
        }

        SkillToggleHelper.autoToggleIfPossible(target, granted);

        GrantedSkillData data = target.getData(
                AttachmentRegistry.GRANTED_SKILL_DATA
        );
        data.addGrant(
                skillId.toString(),
                player.getUUID(),
                false
        );
        target.setData(
                AttachmentRegistry.GRANTED_SKILL_DATA,
                data
        );

        return true;
    }

    private static void rollbackGrantedSkills(
            ServerPlayer player,
            LivingEntity target,
            List<ResourceLocation> skillIds
    ) {
        GrantedSkillData data = target.getData(
                AttachmentRegistry.GRANTED_SKILL_DATA
        );

        for (ResourceLocation skillId : skillIds) {
            SkillAPI.getSkillsFrom(target).forgetSkill(
                    skillId,
                    AuthorityText.component("rollback.reason")
            );
            data.removeGrant(skillId.toString(), player.getUUID());
        }

        target.setData(
                AttachmentRegistry.GRANTED_SKILL_DATA,
                data
        );
    }

    private static void recordGranterGrant(
            ServerPlayer player,
            ManasSkillInstance granter,
            LivingEntity target,
            ResourceLocation skillId
    ) {
        GranterProgressData progress = player.getData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA
        );
        progress.incrementSuccessfulGrants();

        boolean newSubordinate = progress.recognizeSubordinate(
                target.getUUID()
        );
        boolean newSkillType = progress.recordGrantedSkillType(
                skillId.toString()
        );
        int masteryGain = MASTERY_SUCCESSFUL_GRANT;

        if (newSubordinate) {
            masteryGain += MASTERY_NEW_SUBORDINATE_BONUS;
        }

        if (newSkillType) {
            masteryGain += MASTERY_NEW_SKILL_TYPE_BONUS;
        }

        addStoredGranterMastery(
                player,
                granter,
                progress,
                masteryGain
        );
        player.setData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA,
                progress
        );
        RecognitionAuthorityProgress.synchronize(player);
    }

    private static void recordGranterTakeBack(
            ServerPlayer player,
            ManasSkillInstance granter,
            LivingEntity target
    ) {
        GranterProgressData progress = player.getData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA
        );
        progress.incrementSuccessfulTakeBacks();
        progress.recognizeSubordinate(target.getUUID());
        addStoredGranterMastery(
                player,
                granter,
                progress,
                MASTERY_SUCCESSFUL_TAKE_BACK
        );
        player.setData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA,
                progress
        );
        RecognitionAuthorityProgress.synchronize(player);
    }

    private static void recognizeSubordinate(
            ServerPlayer player,
            LivingEntity target
    ) {
        GranterProgressData progress = player.getData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA
        );

        if (progress.recognizeSubordinate(target.getUUID())) {
            player.setData(
                    AttachmentRegistry.GRANTER_PROGRESS_DATA,
                    progress
            );
        }

        RecognitionAuthorityProgress.synchronize(player);
    }

    private static void addStoredGranterMastery(
            ServerPlayer player,
            ManasSkillInstance instance,
            GranterProgressData progress,
            int amount
    ) {
        if (instance == null || progress == null || amount <= 0) {
            return;
        }

        int currentMastery = (int) Math.floor(
                Math.max(0.0D, instance.getMastery())
        );
        int maximumMastery = (int) Math.floor(
                Math.max(0.0D, instance.getMaxMastery())
        );

        progress.addEarnedGranterMastery(
                amount,
                currentMastery,
                maximumMastery
        );

        if (maximumMastery > 0) {
            int storedMastery = Math.min(
                    progress.getEarnedGranterMastery(),
                    maximumMastery
            );

            if (currentMastery < storedMastery) {
                instance.setMastery(storedMastery);
                instance.markDirty();
                SkillAPI.getSkillsFrom(player)
                        .checkAndMarkDirty(instance);
            }
        }
    }

    private static void addMastery(
            ServerPlayer player,
            ManasSkillInstance instance,
            int amount
    ) {
        if (instance == null || amount <= 0) {
            return;
        }

        instance.addMasteryPoint(player, amount);
        instance.markDirty();
        SkillAPI.getSkillsFrom(player).checkAndMarkDirty(instance);
    }

    private static boolean hasMagicules(
            ServerPlayer player,
            double amount
    ) {
        return getCurrentMagicules(player) >= Math.max(0.0D, amount);
    }

    private static double getCurrentMagicules(
            ServerPlayer player
    ) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        return existence == null
                ? 0.0D
                : Math.max(0.0D, existence.getMagicule());
    }

    private static void consumeMagicules(
            ServerPlayer player,
            double amount
    ) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return;
        }

        existence.setMagicule(
                Math.max(0.0D, existence.getMagicule() - amount)
        );
        existence.markDirty();
    }

    private static Component getSkillDisplayName(
            ServerPlayer player,
            ResourceLocation skillId
    ) {
        if (player != null && skillId != null) {
            Optional<ManasSkillInstance> instance =
                    SkillAPI.getSkillsFrom(player).getSkill(skillId);

            if (instance.isPresent()) {
                return instance.orElseThrow().getDisplayName().copy();
            }
        }

        return skillId == null
                ? AuthorityText.component("unknown_skill")
                : Component.literal(skillId.toString());
    }

    private static void sendNotEnoughMagicules(
            ServerPlayer player,
            double required
    ) {
        sendError(
                player,
                AuthorityText.component(
                        "error.insufficient_magicules",
                        formatNumber(required),
                        formatNumber(getCurrentMagicules(player))
                )
        );
    }

    private static void sendError(
            ServerPlayer player,
            Component message
    ) {
        ActionbarHelper.send(
                player,
                message.copy().withStyle(ChatFormatting.RED)
        );
    }

    private static void sendSuccess(
            ServerPlayer player,
            Component message
    ) {
        ActionbarHelper.send(
                player,
                message.copy().withStyle(ChatFormatting.GREEN)
        );
    }

    private static double sanitizeCost(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? value
                : 0.0D;
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    private static Component formatSeconds(int ticks) {
        return AuthorityText.component(
                "seconds",
                String.format(
                        Locale.US,
                        "%.1f",
                        Math.max(0, ticks) / 20.0D
                )
        );
    }

    private record CostBreakdown(
            double standardCost,
            double surcharge,
            double finalCost
    ) {
        private CostBreakdown {
            standardCost = sanitizeCost(standardCost);
            surcharge = sanitizeCost(surcharge);
            finalCost = sanitizeCost(finalCost);
        }
    }

    private record GrantCandidate(
            ResourceLocation skillId,
            ManasSkill skill,
            ManasSkillInstance source,
            double cost
    ) {
        private GrantCandidate {
            cost = sanitizeCost(cost);
        }
    }

    private record TakeBackCandidate(
            LivingEntity target,
            GrantedSkillData.GrantedSkillRecord record
    ) {
    }

    private record TakeBackAggregate(
            String skillId,
            String displayName,
            String category,
            int targetCount
    ) {
        private TakeBackAggregate increment() {
            return new TakeBackAggregate(
                    skillId,
                    displayName,
                    category,
                    targetCount + 1
            );
        }
    }
}