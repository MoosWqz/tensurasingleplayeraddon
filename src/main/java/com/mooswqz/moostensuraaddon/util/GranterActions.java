package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.GrantedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.network.OpenGranterScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenSubordinateOverviewScreenPayload;
import com.mooswqz.moostensuraaddon.skill.GranterMode;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class GranterActions {
    private static final String SELECTED_SKILL_TAG = "SelectedSkill";

    private static final double TARGET_RANGE = 16.0D;
    private static final double TARGET_RADIUS = 0.5D;

    private static final int MASTERY_SUCCESSFUL_GRANT = 25;
    private static final int MASTERY_SUCCESSFUL_TAKE_BACK = 5;
    private static final int MASTERY_NEW_SUBORDINATE_BONUS = 25;
    private static final int MASTERY_NEW_SKILL_TYPE_BONUS = 15;

    public static void openSkillSelection(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenGranterScreenPayload());
    }

    public static void setSelectedSkill(ServerPlayer player, ResourceLocation skillId) {
        Optional<ManasSkillInstance> selectionSkillOptional = getSelectionSkillInstance(player);

        if (selectionSkillOptional.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_granter")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (!canPlayerSelectSkill(player, skillId)) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.invalid_selection")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ManasSkillInstance selectionInstance = selectionSkillOptional.get();
        selectionInstance.getOrCreateTag().putString(SELECTED_SKILL_TAG, skillId.toString());
        selectionInstance.markDirty();

        sendFeedback(player, Component.translatable(
                "moostensuraaddon.granter.selected",
                getSkillDisplayName(player, skillId)
        ).withStyle(ChatFormatting.AQUA));
    }

    public static Optional<ResourceLocation> getSelectedSkillId(ManasSkillInstance granterInstance) {
        if (granterInstance == null || !granterInstance.getOrCreateTag().contains(SELECTED_SKILL_TAG)) {
            return Optional.empty();
        }

        String raw = granterInstance.getOrCreateTag().getString(SELECTED_SKILL_TAG);
        ResourceLocation id = ResourceLocation.tryParse(raw);

        return Optional.ofNullable(id);
    }

    public static boolean canPlayerSelectSkill(ServerPlayer player, ResourceLocation skillId) {
        if (!isGrantableSkill(skillId)) {
            return false;
        }

        Optional<ManasSkillInstance> skillOptional = SkillAPI.getSkillsFrom(player).getSkill(skillId);

        if (skillOptional.isEmpty()) {
            return false;
        }

        if (hasUltimateGranterSkill(player)) {
            return true;
        }

        return skillOptional.get().isMastered(player);
    }

    public static boolean isGrantableSkill(ResourceLocation skillId) {
        if (skillId == null) {
            return false;
        }

        if (MoosTensuraConfig.isSkillBlacklisted(skillId, MoosTensuraConfig.SkillAction.GRANT)) {
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

        ManasSkill skill = SkillAPI.getSkillRegistry().get(skillId);

        if (!(skill instanceof Skill tensuraSkill)) {
            return false;
        }

        Skill.SkillType type = tensuraSkill.getType();

        return type == Skill.SkillType.COMMON
                || type == Skill.SkillType.EXTRA
                || type == Skill.SkillType.RESISTANCE
                || type == Skill.SkillType.UNIQUE;
    }

    public static void grantSelectedSkill(ServerPlayer player, ManasSkillInstance granterInstance) {
        if (granterInstance.onCoolDown(GranterMode.GRANT.id())) {
            int remainingTicks = granterInstance.getCoolDown(GranterMode.GRANT.id());
            int remainingSeconds = (int) Math.ceil(remainingTicks / 20.0D);

            sendFeedback(player, Component.translatable(
                    "moostensuraaddon.granter.error.grant_cooldown",
                    remainingSeconds
            ).withStyle(ChatFormatting.RED));

            return;
        }

        Optional<ResourceLocation> selectedOptional = getSelectedSkillId(granterInstance);

        if (selectedOptional.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_skill_selected")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ResourceLocation selectedSkillId = selectedOptional.get();

        Optional<ManasSkillInstance> sourceSkillOptional =
                SkillAPI.getSkillsFrom(player).getSkill(selectedSkillId);

        if (sourceSkillOptional.isEmpty() || !sourceSkillOptional.get().isMastered(player)) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.not_mastered")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        LivingEntity target = getLookedAtSubordinate(player);

        if (target == null) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_subordinate")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Skills targetSkills = SkillAPI.getSkillsFrom(target);

        if (targetSkills.getSkill(selectedSkillId).isPresent()) {
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

        if (!hasEnoughGrantMagicules(player)) {
            double currentMagicules = getCurrentMagicules(player);

            sendFeedback(player, Component.translatable(
                    "moostensuraaddon.granter.error.not_enough_grant_magicules",
                    formatNumber(getGrantMagiculeCost()),
                    formatNumber(currentMagicules)
            ).withStyle(ChatFormatting.RED));

            return;
        }

        ManasSkillInstance sourceInstance = sourceSkillOptional.get();
        ManasSkillInstance grantedInstance = selectedSkill.createDefaultInstance();

        grantedInstance.setMastery(Math.min(sourceInstance.getMastery(), grantedInstance.getMaxMastery()));
        grantedInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);

        boolean learned = targetSkills.learnSkill(
                grantedInstance,
                Component.translatable("moostensuraaddon.granter.learned", player.getDisplayName())
        );

        if (!learned) {
            sendGrantFailureReason(player, target, selectedSkillId);
            return;
        }

        SkillToggleHelper.autoToggleIfPossible(target, grantedInstance);

        GrantedSkillData data = target.getData(AttachmentRegistry.GRANTED_SKILL_DATA);
        data.addGrant(selectedSkillId.toString(), player.getUUID(), false);
        target.setData(AttachmentRegistry.GRANTED_SKILL_DATA, data);

        consumeGrantMagicules(player);

        granterInstance.setCoolDown(GranterMode.GRANT.id(), getGrantCooldownTicks());

        recordSuccessfulGrantAndSyncMastery(player, granterInstance, target, selectedSkillId);

        granterInstance.markDirty();

        AddonAdvancementHelper.awardFirstGift(player);

        if (granterInstance.isMastered(player)) {
            AddonAdvancementHelper.awardGranterMastered(player);
        }

        sendFeedback(player, Component.translatable(
                "moostensuraaddon.granter.granted_with_cost",
                getSkillDisplayName(player, selectedSkillId),
                target.getDisplayName(),
                formatNumber(getGrantMagiculeCost())
        ).withStyle(ChatFormatting.GREEN));
    }

    public static void takeBackSelectedSkill(ServerPlayer player, ManasSkillInstance granterInstance) {
        Optional<ResourceLocation> selectedOptional = getSelectedSkillId(granterInstance);

        if (selectedOptional.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_skill_selected")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ResourceLocation selectedSkillId = selectedOptional.get();
        LivingEntity target = getLookedAtSubordinate(player);

        if (target == null) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_subordinate")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        GrantedSkillData data = target.getData(AttachmentRegistry.GRANTED_SKILL_DATA);

        Optional<GrantedSkillData.GrantedSkillRecord> recordOptional =
                data.getGrant(selectedSkillId.toString(), player.getUUID());

        if (recordOptional.isEmpty()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.not_granted_by_you")
                    .withStyle(ChatFormatting.RED));
            return;
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

        recordSuccessfulTakeBackAndSyncMastery(player, granterInstance, target);

        AddonAdvancementHelper.awardWhatWasGivenCanReturn(player);

        if (granterInstance.isMastered(player)) {
            AddonAdvancementHelper.awardGranterMastered(player);
        }

        sendFeedback(player, Component.translatable(
                "moostensuraaddon.granter.take_back_success",
                getSkillDisplayName(player, selectedSkillId),
                target.getDisplayName()
        ).withStyle(ChatFormatting.GREEN));
    }

    public static void listSubordinateSkills(ServerPlayer player) {
        openSubordinateOverview(player);
    }

    public static void openSubordinateOverview(ServerPlayer player) {
        LivingEntity target = getLookedAtSubordinate(player);

        if (target == null) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.no_subordinate")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        recognizeSubordinate(player, target);

        PacketDistributor.sendToPlayer(player, new OpenSubordinateOverviewScreenPayload(
                List.of(buildSubordinateOverviewEntry(player, target))
        ));
    }

    public static OpenSubordinateOverviewScreenPayload.TargetEntry buildSubordinateOverviewEntry(
            ServerPlayer viewer,
            LivingEntity target
    ) {
        IExistence existence = TensuraStorages.getExistenceFrom(target);

        double magicules = existence == null ? 0.0D : existence.getMagicule();
        double ep = existence == null ? 0.0D : existence.getEP();

        List<OpenSubordinateOverviewScreenPayload.SkillEntry> skillEntries = new ArrayList<>();

        GrantedSkillData grantedSkillData = target.getData(AttachmentRegistry.GRANTED_SKILL_DATA);

        for (ManasSkillInstance instance : SkillAPI.getSkillsFrom(target).getLearnedSkills()) {
            if (instance == null || instance.getSkillId() == null) {
                continue;
            }

            ResourceLocation skillId = instance.getSkillId();
            Component displayName = instance.getDisplayName();

            SkillCategoryHelper.SkillCategory category = SkillCategoryHelper.getCategory(skillId, displayName);

            boolean grantedByViewer = grantedSkillData.getGrant(skillId.toString(), viewer.getUUID()).isPresent();

            skillEntries.add(new OpenSubordinateOverviewScreenPayload.SkillEntry(
                    skillId.toString(),
                    displayName.getString(),
                    SkillCategoryHelper.getCategoryHeader(category).getString(),
                    category.sortOrder(),
                    instance.isMastered(target),
                    grantedByViewer
            ));
        }

        skillEntries.sort(Comparator
                .comparingInt(OpenSubordinateOverviewScreenPayload.SkillEntry::categoryOrder)
                .thenComparing(OpenSubordinateOverviewScreenPayload.SkillEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OpenSubordinateOverviewScreenPayload.SkillEntry::skillId));

        return new OpenSubordinateOverviewScreenPayload.TargetEntry(
                target.getUUID().toString(),
                target.getDisplayName().getString(),
                target.getHealth(),
                target.getMaxHealth(),
                magicules,
                ep,
                skillEntries
        );
    }

    public static boolean giveGranter(ServerPlayer player) {
        Skills skills = SkillAPI.getSkillsFrom(player);
        ResourceLocation granterId = SkillRegistry.GRANTER.get().getRegistryName();

        if (skills.getSkill(granterId).isPresent()) {
            AddonAdvancementHelper.awardAuthorityToBestow(player);
            return false;
        }

        boolean learned = skills.learnSkill(
                SkillRegistry.GRANTER.get().createDefaultInstance(),
                Component.translatable("moostensuraaddon.granter.acquired")
        );

        if (learned) {
            AddonAdvancementHelper.awardAuthorityToBestow(player);
        }

        return learned;
    }

    public static boolean hasGreatSage(ServerPlayer player) {
        ResourceLocation greatSageId = UniqueSkills.GREAT_SAGE.get().getRegistryName();

        if (SkillAPI.getSkillsFrom(player).getSkill(greatSageId).isPresent()) {
            return true;
        }

        return SkillAPI.getSkillsFrom(player)
                .getLearnedSkills()
                .stream()
                .anyMatch(GranterActions::isGreatSageSkill);
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

    private static Optional<ManasSkillInstance> getSelectionSkillInstance(ServerPlayer player) {
        Skills skills = SkillAPI.getSkillsFrom(player);

        Optional<ManasSkillInstance> benevolentOptional =
                skills.getSkill(SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName());

        if (benevolentOptional.isPresent()) {
            return benevolentOptional;
        }

        Optional<ManasSkillInstance> absoluteOptional =
                skills.getSkill(SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName());

        if (absoluteOptional.isPresent()) {
            return absoluteOptional;
        }

        return skills.getSkill(SkillRegistry.GRANTER.get().getRegistryName());
    }

    private static boolean hasUltimateGranterSkill(ServerPlayer player) {
        Skills skills = SkillAPI.getSkillsFrom(player);

        return skills.getSkill(SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName()).isPresent()
                || skills.getSkill(SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName()).isPresent();
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

    private static void recordSuccessfulGrantAndSyncMastery(
            ServerPlayer player,
            ManasSkillInstance granterInstance,
            LivingEntity target,
            ResourceLocation grantedSkillId
    ) {
        GranterProgressData progress = player.getData(AttachmentRegistry.GRANTER_PROGRESS_DATA);

        progress.incrementSuccessfulGrants();

        boolean newSubordinate = progress.recognizeSubordinate(target.getUUID());
        boolean newSkillType = progress.recordGrantedSkillType(grantedSkillId.toString());

        int masteryGain = MASTERY_SUCCESSFUL_GRANT;

        if (newSubordinate) {
            masteryGain += MASTERY_NEW_SUBORDINATE_BONUS;
        }

        if (newSkillType) {
            masteryGain += MASTERY_NEW_SKILL_TYPE_BONUS;
        }

        addEarnedMasteryAndSyncToTensura(granterInstance, progress, masteryGain);

        player.setData(AttachmentRegistry.GRANTER_PROGRESS_DATA, progress);

        sendMasteryProgressMessage(
                player,
                masteryGain,
                granterInstance,
                "Grants: " + progress.getSuccessfulGrants()
                        + " | Recognized: " + progress.getRecognizedSubordinateCount()
                        + " | Skill Types: " + progress.getGrantedSkillTypeCount()
        );
    }

    private static void recordSuccessfulTakeBackAndSyncMastery(
            ServerPlayer player,
            ManasSkillInstance granterInstance,
            LivingEntity target
    ) {
        GranterProgressData progress = player.getData(AttachmentRegistry.GRANTER_PROGRESS_DATA);

        progress.incrementSuccessfulTakeBacks();
        progress.recognizeSubordinate(target.getUUID());

        addEarnedMasteryAndSyncToTensura(granterInstance, progress, MASTERY_SUCCESSFUL_TAKE_BACK);

        player.setData(AttachmentRegistry.GRANTER_PROGRESS_DATA, progress);

        sendMasteryProgressMessage(
                player,
                MASTERY_SUCCESSFUL_TAKE_BACK,
                granterInstance,
                "Take Backs: " + progress.getSuccessfulTakeBacks()
                        + " | Recognized: " + progress.getRecognizedSubordinateCount()
        );
    }

    private static void recognizeSubordinate(ServerPlayer player, LivingEntity target) {
        GranterProgressData progress = player.getData(AttachmentRegistry.GRANTER_PROGRESS_DATA);
        boolean changed = progress.recognizeSubordinate(target.getUUID());

        if (changed) {
            player.setData(AttachmentRegistry.GRANTER_PROGRESS_DATA, progress);
        }
    }

    private static void addEarnedMasteryAndSyncToTensura(
            ManasSkillInstance granterInstance,
            GranterProgressData progress,
            int masteryGain
    ) {
        if (masteryGain <= 0) {
            return;
        }

        int currentRealMastery = (int) Math.floor(Math.max(0.0D, granterInstance.getMastery()));
        int maxMastery = (int) Math.floor(Math.max(0.0D, granterInstance.getMaxMastery()));

        progress.addEarnedGranterMastery(masteryGain, currentRealMastery, maxMastery);

        syncStoredMasteryToTensura(granterInstance, progress);
    }

    private static void syncStoredMasteryToTensura(
            ManasSkillInstance granterInstance,
            GranterProgressData progress
    ) {
        int currentRealMastery = (int) Math.floor(Math.max(0.0D, granterInstance.getMastery()));
        int maxMastery = (int) Math.floor(Math.max(0.0D, granterInstance.getMaxMastery()));

        if (maxMastery <= 0) {
            return;
        }

        int desiredMastery = Math.min(progress.getEarnedGranterMastery(), maxMastery);

        if (currentRealMastery >= desiredMastery) {
            return;
        }

        granterInstance.setMastery(desiredMastery);
        granterInstance.markDirty();
    }

    private static void sendMasteryProgressMessage(
            ServerPlayer player,
            int masteryGain,
            ManasSkillInstance granterInstance,
            String extra
    ) {
        int currentMastery = (int) Math.floor(Math.max(0.0D, granterInstance.getMastery()));
        int maxMastery = (int) Math.floor(Math.max(0.0D, granterInstance.getMaxMastery()));
        double percent = maxMastery <= 0 ? 0.0D : (currentMastery * 100.0D / maxMastery);

        sendFeedback(player, Component.literal(
                "[Granter Progress] +" + masteryGain + " mastery"
                        + " | Mastery: " + currentMastery + " / " + maxMastery
                        + " (" + formatPercent(percent) + ")"
                        + " | " + extra
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void sendGrantFailureReason(ServerPlayer player, LivingEntity target, ResourceLocation skillId) {
        if (SkillAPI.getSkillsFrom(target).getSkill(skillId).isPresent()) {
            sendFeedback(player, Component.translatable("moostensuraaddon.granter.error.target_already_has")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        IExistence targetExistence = TensuraStorages.getExistenceFrom(target);

        if (targetExistence != null) {
            double magicules = targetExistence.getMagicule();
            double ep = targetExistence.getEP();

            if (magicules <= 0.0D) {
                sendFeedback(player, Component.translatable(
                        "moostensuraaddon.granter.error.not_enough_magicules",
                        target.getDisplayName()
                ).withStyle(ChatFormatting.RED));
                return;
            }

            if (ep <= 0.0D) {
                sendFeedback(player, Component.translatable(
                        "moostensuraaddon.granter.error.not_enough_ep",
                        target.getDisplayName()
                ).withStyle(ChatFormatting.RED));
                return;
            }
        }

        sendFeedback(player, Component.translatable(
                "moostensuraaddon.granter.error.grant_failed_detailed",
                target.getDisplayName(),
                getSkillDisplayName(player, skillId)
        ).withStyle(ChatFormatting.RED));
    }

    private static Component getSkillDisplayName(ServerPlayer player, ResourceLocation skillId) {
        if (player != null && skillId != null) {
            Optional<ManasSkillInstance> playerSkill = SkillAPI.getSkillsFrom(player).getSkill(skillId);

            if (playerSkill.isPresent()) {
                return playerSkill.get().getDisplayName().copy();
            }
        }

        return skillId == null ? Component.literal("Unknown Skill") : Component.literal(skillId.toString());
    }

    private static void sendFeedback(ServerPlayer player, Component message) {
        ActionbarHelper.send(player, message);
    }

    private static double getGrantMagiculeCost() {
        return MoosTensuraConfig.GRANTER_GRANT_MAGICULE_COST.get();
    }

    private static int getGrantCooldownTicks() {
        return MoosTensuraConfig.GRANTER_GRANT_COOLDOWN_TICKS.get();
    }

    private static boolean hasEnoughGrantMagicules(ServerPlayer player) {
        return getCurrentMagicules(player) >= getGrantMagiculeCost();
    }

    private static double getCurrentMagicules(ServerPlayer player) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return 0.0D;
        }

        return existence.getMagicule();
    }

    private static void consumeGrantMagicules(ServerPlayer player) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return;
        }

        double currentMagicules = existence.getMagicule();
        existence.setMagicule(Math.max(0.0D, currentMagicules - getGrantMagiculeCost()));
        existence.markDirty();
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.US, "%.1f%%", value);
    }
}