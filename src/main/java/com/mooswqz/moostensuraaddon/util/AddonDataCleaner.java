package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.BorrowedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GrantedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public final class AddonDataCleaner {
    private static final double NEARBY_ENTITY_CLEANUP_RADIUS = 64.0D;

    private AddonDataCleaner() {
    }

    public static CleanupResult cleanupPlayer(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) {
            return CleanupResult.empty();
        }

        boolean changed = false;
        int expiredBorrowedSkills = 0;

        BorrowedSkillData borrowedSkillData = player.getData(AttachmentRegistry.BORROWED_SKILL_DATA);

        expiredBorrowedSkills += removeExpiredBorrowedSkills(player, borrowedSkillData);

        boolean borrowedChanged = expiredBorrowedSkills > 0;

        borrowedChanged |= borrowedSkillData.cleanupAll(
                player.level().getGameTime(),
                AddonDataCleaner::isKnownSkillIdString,
                skillId -> playerHasSkill(player, skillId)
        );

        borrowedChanged |= borrowedSkillData.cleanupBorrowHistory(AddonDataCleaner::isKnownSkillIdString);

        if (borrowedChanged) {
            player.setData(AttachmentRegistry.BORROWED_SKILL_DATA, borrowedSkillData);
            changed = true;
        }

        GrantedSkillData grantedSkillData = player.getData(AttachmentRegistry.GRANTED_SKILL_DATA);

        if (grantedSkillData.cleanup(AddonDataCleaner::isKnownSkillIdString)) {
            player.setData(AttachmentRegistry.GRANTED_SKILL_DATA, grantedSkillData);
            changed = true;
        }

        GranterProgressData progressData = player.getData(AttachmentRegistry.GRANTER_PROGRESS_DATA);

        if (progressData.cleanup(AddonDataCleaner::isKnownSkillIdString)) {
            player.setData(AttachmentRegistry.GRANTER_PROGRESS_DATA, progressData);
            changed = true;
        }

        return new CleanupResult(changed, expiredBorrowedSkills, 0);
    }

    public static int cleanupNearbyGrantedData(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) {
            return 0;
        }

        int cleanedEntities = 0;

        List<LivingEntity> nearbyEntities = player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(NEARBY_ENTITY_CLEANUP_RADIUS),
                entity -> entity != null && entity != player
        );

        for (LivingEntity entity : nearbyEntities) {
            if (cleanupGrantedData(entity)) {
                cleanedEntities++;
            }
        }

        return cleanedEntities;
    }

    public static CleanupResult cleanupPlayerAndNearbyData(ServerPlayer player) {
        CleanupResult playerResult = cleanupPlayer(player);
        int nearbyCleaned = cleanupNearbyGrantedData(player);

        return new CleanupResult(
                playerResult.changed() || nearbyCleaned > 0,
                playerResult.expiredBorrowedSkills(),
                nearbyCleaned
        );
    }

    public static boolean cleanupGrantedData(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return false;
        }

        GrantedSkillData grantedSkillData = entity.getData(AttachmentRegistry.GRANTED_SKILL_DATA);

        if (!grantedSkillData.cleanup(AddonDataCleaner::isKnownSkillIdString)) {
            return false;
        }

        entity.setData(AttachmentRegistry.GRANTED_SKILL_DATA, grantedSkillData);

        return true;
    }

    private static int removeExpiredBorrowedSkills(ServerPlayer player, BorrowedSkillData borrowedSkillData) {
        if (player == null || borrowedSkillData == null) {
            return 0;
        }

        long gameTime = player.level().getGameTime();
        List<BorrowedSkillData.BorrowedSkillRecord> expired = new ArrayList<>(
                borrowedSkillData.getExpiredBorrowedSkills(gameTime)
        );

        if (expired.isEmpty()) {
            return 0;
        }

        int removed = 0;

        for (BorrowedSkillData.BorrowedSkillRecord record : expired) {
            if (record == null || record.skillId() == null || record.skillId().isBlank()) {
                continue;
            }

            ResourceLocation skillId = ResourceLocation.tryParse(record.skillId());

            if (skillId != null && playerHasSkill(player, skillId)) {
                SkillAPI.getSkillsFrom(player).forgetSkill(
                        skillId,
                        Component.translatable("moostensuraaddon.ultimate.benevolent.borrow.expired")
                );
            }

            if (borrowedSkillData.removeBorrowedSkill(record.skillId())) {
                removed++;
            }
        }

        return removed;
    }

    private static boolean playerHasSkill(ServerPlayer player, String skillId) {
        ResourceLocation id = ResourceLocation.tryParse(skillId);

        if (id == null) {
            return false;
        }

        return playerHasSkill(player, id);
    }

    private static boolean playerHasSkill(ServerPlayer player, ResourceLocation skillId) {
        if (player == null || skillId == null) {
            return false;
        }

        return SkillAPI.getSkillsFrom(player).getSkill(skillId).isPresent();
    }

    public static boolean isKnownSkillIdString(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return false;
        }

        ResourceLocation id = ResourceLocation.tryParse(skillId);

        if (id == null) {
            return false;
        }

        return SkillAPI.getSkillRegistry().get(id) != null;
    }

    public record CleanupResult(
            boolean changed,
            int expiredBorrowedSkills,
            int cleanedNearbyEntities
    ) {
        private static CleanupResult empty() {
            return new CleanupResult(false, 0, 0);
        }
    }
}