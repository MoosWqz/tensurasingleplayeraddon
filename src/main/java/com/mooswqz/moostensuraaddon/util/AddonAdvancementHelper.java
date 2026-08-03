package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.BorrowedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.lifecycle.GranterAcquisitionTracker;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

/**
 * Idempotent advancement awards with incarnation-reset suppression.
 */
public final class AddonAdvancementHelper {

    public static final ResourceLocation ROOT = id("root");
    public static final ResourceLocation A_NAME_TO_ANCHOR_THE_SOUL =
            id("a_name_to_anchor_the_soul");
    public static final ResourceLocation SAGES_FIRST_STEP =
            id("sages_first_step");
    public static final ResourceLocation GREAT_CRYSTAL_RESONANCE =
            id("great_crystal_resonance");
    public static final ResourceLocation THE_GREAT_SAGE_AWAKENS =
            id("the_great_sage_awakens");
    public static final ResourceLocation AUTHORITY_TO_BESTOW =
            id("authority_to_bestow");
    public static final ResourceLocation FIRST_GIFT =
            id("first_gift");
    public static final ResourceLocation WHAT_WAS_GIVEN_CAN_RETURN =
            id("what_was_given_can_return");
    public static final ResourceLocation GRANTER_MASTERED =
            id("granter_mastered");
    public static final ResourceLocation PATH_OF_BENEVOLENCE =
            id("path_of_benevolence");
    public static final ResourceLocation BORROWED_POWER =
            id("borrowed_power");
    public static final ResourceLocation A_POWER_MADE_PERMANENT =
            id("a_power_made_permanent");
    public static final ResourceLocation PATH_OF_GOVERNANCE =
            id("path_of_governance");
    public static final ResourceLocation SEIZED_AUTHORITY =
            id("seized_authority");
    public static final ResourceLocation SOUL_STRAIN =
            id("soul_strain");
    public static final ResourceLocation THE_PRICE_OF_POWER =
            id("the_price_of_power");

    private AddonAdvancementHelper() {
    }

    private static ResourceLocation id(
            String path
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                MoosTensuraAddon.MODID,
                path
        );
    }

    public static void award(
            ServerPlayer player,
            ResourceLocation advancementId
    ) {
        if (player == null
                || advancementId == null
                || AddonIncarnationState.isResetGuardActive(player)) {
            return;
        }

        MinecraftServer server = player.getServer();

        if (server == null) {
            return;
        }

        AdvancementHolder holder = server.getAdvancements()
                .get(advancementId);

        if (holder == null) {
            return;
        }

        PlayerAdvancements advancements = player.getAdvancements();
        AdvancementProgress progress = advancements
                .getOrStartProgress(holder);

        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            advancements.award(holder, criterion);
        }
    }

    public static void awardRoot(
            ServerPlayer player
    ) {
        award(player, ROOT);
    }

    public static void awardNameAnchor(
            ServerPlayer player
    ) {
        awardRoot(player);
        award(player, A_NAME_TO_ANCHOR_THE_SOUL);
    }

    public static void awardSageFirstStep(
            ServerPlayer player
    ) {
        awardRoot(player);
        award(player, SAGES_FIRST_STEP);
    }

    public static void awardGreatCrystalResonance(
            ServerPlayer player
    ) {
        awardSageFirstStep(player);
        award(player, GREAT_CRYSTAL_RESONANCE);
    }

    public static void awardGreatSageAwakens(
            ServerPlayer player
    ) {
        awardSageFirstStep(player);
        award(player, THE_GREAT_SAGE_AWAKENS);
    }

    /**
     * Compatibility entry point used by existing Granter acquisition code.
     * The transition tracker decides whether this is a real acquisition,
     * a harmless repeated call, or a repair of a confirmed life state.
     */
    public static void awardAuthorityToBestow(
            ServerPlayer player
    ) {
        GranterAcquisitionTracker.observe(player);
    }

    /**
     * Direct non-recursive award used only after the lifecycle tracker has
     * confirmed acquisition for this incarnation.
     */
    public static void awardAuthorityToBestowConfirmed(
            ServerPlayer player
    ) {
        awardGreatSageAwakens(player);
        award(player, AUTHORITY_TO_BESTOW);
    }

    public static void awardFirstGift(
            ServerPlayer player
    ) {
        GranterAcquisitionTracker.confirmFromAuthorityEvidence(player);
        awardAuthorityToBestowConfirmed(player);
        award(player, FIRST_GIFT);
    }

    public static void awardWhatWasGivenCanReturn(
            ServerPlayer player
    ) {
        awardFirstGift(player);
        award(player, WHAT_WAS_GIVEN_CAN_RETURN);
    }

    public static void awardGranterMastered(
            ServerPlayer player
    ) {
        GranterAcquisitionTracker.confirmFromAuthorityEvidence(player);
        awardAuthorityToBestowConfirmed(player);
        award(player, GRANTER_MASTERED);
    }

    public static void awardPathOfBenevolence(
            ServerPlayer player
    ) {
        awardGranterMastered(player);
        award(player, PATH_OF_BENEVOLENCE);
    }

    public static void awardBorrowedPower(
            ServerPlayer player
    ) {
        awardPathOfBenevolence(player);
        award(player, BORROWED_POWER);
    }

    public static void awardPowerMadePermanent(
            ServerPlayer player
    ) {
        awardBorrowedPower(player);
        award(player, A_POWER_MADE_PERMANENT);
    }

    public static void awardPathOfGovernance(
            ServerPlayer player
    ) {
        awardGranterMastered(player);
        award(player, PATH_OF_GOVERNANCE);
    }

    public static void awardSeizedAuthority(
            ServerPlayer player
    ) {
        awardPathOfGovernance(player);
        award(player, SEIZED_AUTHORITY);
    }

    public static void awardSoulStrain(
            ServerPlayer player
    ) {
        awardSeizedAuthority(player);
        award(player, SOUL_STRAIN);
    }

    public static void awardPriceOfPower(
            ServerPlayer player
    ) {
        awardSoulStrain(player);
        award(player, THE_PRICE_OF_POWER);
    }

    public static void awardStateBasedAdvancements(
            ServerPlayer player
    ) {
        if (player == null
                || player.level().isClientSide()
                || AddonIncarnationState.isResetGuardActive(player)) {
            return;
        }

        awardRoot(player);

        boolean named = isNamedOrEndowed(player);
        boolean sage = hasExactSkill(
                player,
                "tensura:sage",
                "Sage"
        );
        boolean greatSage = GranterActions.hasGreatSage(player);
        boolean granter = hasSkill(
                player,
                SkillRegistry.GRANTER.get().getRegistryName()
        );
        boolean benevolent = hasSkill(
                player,
                SkillRegistry.BENEVOLENT_EMPOWERMENT
                        .get()
                        .getRegistryName()
        );
        boolean governance = hasSkill(
                player,
                SkillRegistry.ABSOLUTE_GOVERNANCE
                        .get()
                        .getRegistryName()
        );

        if (named) {
            awardNameAnchor(player);
        }

        if (sage) {
            awardSageFirstStep(player);
        }

        if (greatSage) {
            awardGreatSageAwakens(player);
        }

        if (granter || benevolent || governance) {
            GranterAcquisitionTracker.observe(player);
        }

        if (benevolent) {
            awardPathOfBenevolence(player);
        }

        if (governance) {
            awardPathOfGovernance(player);
        }

        awardGranterProgressAdvancements(player);
        awardBorrowProgressAdvancements(player);
        awardMasteryAdvancements(player);
    }

    private static void awardGranterProgressAdvancements(
            ServerPlayer player
    ) {
        GranterProgressData progress = player.getData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA
        );

        if (progress.getSuccessfulGrants() > 0) {
            awardFirstGift(player);
        }

        if (progress.getSuccessfulTakeBacks() > 0) {
            awardWhatWasGivenCanReturn(player);
        }
    }

    private static void awardBorrowProgressAdvancements(
            ServerPlayer player
    ) {
        BorrowedSkillData data = player.getData(
                AttachmentRegistry.BORROWED_SKILL_DATA
        );
        Map<String, Integer> history = data.getBorrowHistory();

        if (!history.isEmpty()) {
            awardBorrowedPower(player);
        }

        for (Map.Entry<String, Integer> entry : history.entrySet()) {
            if (entry == null
                    || entry.getKey() == null
                    || entry.getKey().isBlank()) {
                continue;
            }

            String skillId = entry.getKey();

            if (!data.isBorrowedSkill(skillId)
                    && playerHasSkill(player, skillId)) {
                awardPowerMadePermanent(player);
                return;
            }
        }
    }

    private static void awardMasteryAdvancements(
            ServerPlayer player
    ) {
        Optional<ManasSkillInstance> granter =
                SkillAPI.getSkillsFrom(player).getSkill(
                        SkillRegistry.GRANTER.get().getRegistryName()
                );

        if (granter.isPresent()
                && granter.orElseThrow().isMastered(player)) {
            awardGranterMastered(player);
            return;
        }

        if (hasSkill(
                player,
                SkillRegistry.BENEVOLENT_EMPOWERMENT
                        .get()
                        .getRegistryName()
        ) || hasSkill(
                player,
                SkillRegistry.ABSOLUTE_GOVERNANCE
                        .get()
                        .getRegistryName()
        )) {
            awardGranterMastered(player);
        }
    }

    public static boolean isNamedOrEndowed(
            ServerPlayer player
    ) {
        if (player == null) {
            return false;
        }

        IExistence existence = TensuraStorages
                .getExistenceFrom(player);

        if (existence == null) {
            return false;
        }

        String name = existence.getName();
        return name != null && !name.isBlank();
    }

    private static boolean hasSkill(
            ServerPlayer player,
            ResourceLocation skillId
    ) {
        return player != null
                && skillId != null
                && SkillAPI.getSkillsFrom(player)
                .getSkill(skillId)
                .isPresent();
    }

    private static boolean playerHasSkill(
            ServerPlayer player,
            String rawSkillId
    ) {
        ResourceLocation skillId = ResourceLocation.tryParse(rawSkillId);
        return skillId != null && hasSkill(player, skillId);
    }

    private static boolean hasExactSkill(
            ServerPlayer player,
            String registryId,
            String displayName
    ) {
        if (player == null) {
            return false;
        }

        for (ManasSkillInstance instance :
                SkillAPI.getSkillsFrom(player).getLearnedSkills()) {
            if (instance == null) {
                continue;
            }

            ResourceLocation skillId = instance.getSkillId();

            if (skillId != null
                    && registryId.equals(skillId.toString())) {
                return true;
            }

            String actualName = instance.getDisplayName().getString();

            if (actualName.equalsIgnoreCase(displayName)
                    || actualName.equalsIgnoreCase(
                    "The " + displayName
            )) {
                return true;
            }
        }

        return false;
    }
}