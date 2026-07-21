package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public final class RecognitionAuthorityProgress {

    private RecognitionAuthorityProgress() {
    }

    public static void synchronize(ServerPlayer player) {
        if (player == null) {
            return;
        }

        RecognitionData recognitionData = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        synchronize(player, recognitionData);
    }

    public static void synchronize(
            ServerPlayer player,
            RecognitionData recognitionData
    ) {
        if (player == null || recognitionData == null) {
            return;
        }

        GranterProgressData granterProgress = player.getData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA
        );

        int currentSubordinates = Math.max(
                0,
                granterProgress.getRecognizedSubordinateCount()
        );

        recognitionData.setCounter(
                RecognitionStatKeys.CURRENT_SUBORDINATES,
                currentSubordinates
        );

        recognitionData.setCounterMaximum(
                RecognitionStatKeys.HIGHEST_SUBORDINATES,
                currentSubordinates
        );
    }

    public static void recordEmpoweredSubordinate(
            ServerPlayer player,
            LivingEntity subordinate
    ) {
        if (player == null || subordinate == null) {
            return;
        }

        RecognitionData recognitionData = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        recognitionData.addUniqueValue(
                RecognitionStatKeys.UNIQUE_SUBORDINATES_EMPOWERED,
                subordinate.getUUID().toString()
        );

        synchronize(player, recognitionData);
    }

    public static void recordMassGrant(
            ServerPlayer player,
            int successfulTargets
    ) {
        if (player == null || successfulTargets <= 1) {
            return;
        }

        RecognitionData recognitionData = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        recognitionData.incrementCounter(
                RecognitionStatKeys.MASS_GRANTS_PERFORMED
        );
    }

    public static void recordGlobalTakeBack(
            ServerPlayer player,
            int successfulTargets,
            boolean benevolent
    ) {
        if (player == null
                || successfulTargets <= 0
                || benevolent) {
            return;
        }

        /*
         * Benevolent Empowerment performs a ranged reclamation.
         * Absolute Governance represents the global-authority route tracked
         * by this specific recognition counter.
         */
        RecognitionData recognitionData = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        recognitionData.incrementCounter(
                RecognitionStatKeys.GLOBAL_TAKE_BACKS_PERFORMED
        );
    }

    public static void recordSkillsSeized(
            ServerPlayer player,
            int successfulSkills
    ) {
        if (player == null || successfulSkills <= 0) {
            return;
        }

        RecognitionData recognitionData = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        int current = recognitionData.getCounter(
                RecognitionStatKeys.SKILLS_SEIZED
        );

        recognitionData.setCounter(
                RecognitionStatKeys.SKILLS_SEIZED,
                current + successfulSkills
        );
    }

    public static void removeDeadSubordinate(
            ServerPlayer owner,
            UUID subordinateUuid
    ) {
        if (owner == null || subordinateUuid == null) {
            return;
        }

        GranterProgressData granterProgress = owner.getData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA
        );

        boolean removed = granterProgress
                .forgetRecognizedSubordinate(subordinateUuid);

        if (removed) {
            owner.setData(
                    AttachmentRegistry.GRANTER_PROGRESS_DATA,
                    granterProgress
            );
        }

        synchronize(owner);
    }
}