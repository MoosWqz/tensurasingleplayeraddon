package com.mooswqz.moostensuraaddon.lifecycle;

import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import com.mooswqz.moostensuraaddon.util.AddonAdvancementHelper;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Persists the Granter-family ownership transition for one incarnation.
 * Existing ownership on login is treated as a baseline, not a new award.
 */
public final class GranterAcquisitionTracker {

    private GranterAcquisitionTracker() {
    }

    public static void initializeOnLogin(
            ServerPlayer player
    ) {
        observe(player, false);
    }

    public static void observe(
            ServerPlayer player
    ) {
        observe(player, false);
    }

    public static void confirmFromAuthorityEvidence(
            ServerPlayer player
    ) {
        observe(player, true);
    }

    private static void observe(
            ServerPlayer player,
            boolean confirmedAuthorityEvidence
    ) {
        if (player == null
                || player.level().isClientSide()
                || AddonIncarnationState.isResetGuardActive(player)) {
            return;
        }

        AddonIncarnationState state =
                AddonIncarnationState.load(player);
        boolean currentlyOwned = ownsAnyAuthority(player);
        boolean advancementDone = isAdvancementDone(
                player,
                AddonAdvancementHelper.AUTHORITY_TO_BESTOW
        );

        GranterAcquisitionPolicy.Observation observation =
                GranterAcquisitionPolicy.evaluate(
                        state.isAuthorityObservationInitialized(),
                        state.wasAuthorityLastOwned(),
                        state.isAuthorityAcquiredThisLife(),
                        currentlyOwned,
                        advancementDone,
                        confirmedAuthorityEvidence
                );

        state.updateAuthorityObservation(observation);

        if (observation.shouldAwardAdvancement()) {
            AddonAdvancementHelper
                    .awardAuthorityToBestowConfirmed(player);
        }
    }

    public static boolean ownsAnyAuthority(
            ServerPlayer player
    ) {
        if (player == null) {
            return false;
        }

        return hasSkill(
                player,
                SkillRegistry.GRANTER.get().getRegistryName()
        ) || hasSkill(
                player,
                SkillRegistry.BENEVOLENT_EMPOWERMENT
                        .get()
                        .getRegistryName()
        ) || hasSkill(
                player,
                SkillRegistry.ABSOLUTE_GOVERNANCE
                        .get()
                        .getRegistryName()
        );
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

    private static boolean isAdvancementDone(
            ServerPlayer player,
            ResourceLocation advancementId
    ) {
        if (player == null || advancementId == null) {
            return false;
        }

        MinecraftServer server = player.getServer();

        if (server == null) {
            return false;
        }

        AdvancementHolder holder = server.getAdvancements()
                .get(advancementId);

        return holder != null
                && player.getAdvancements()
                .getOrStartProgress(holder)
                .isDone();
    }
}