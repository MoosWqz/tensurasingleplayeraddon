package com.mooswqz.moostensuraaddon.lifecycle;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.BorrowedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GrantedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.recognition.RecognitionDisplayNameSyncService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionProgressScreenService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStrengthRewardService;
import com.mooswqz.moostensuraaddon.util.SubordinateOverviewService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Single authoritative reset route for every life-bound addon subsystem.
 */
public final class AddonPlayerDataResetService {

    private AddonPlayerDataResetService() {
    }

    public static ResetResult resetForNewIncarnation(
            ServerPlayer player,
            ResetReason reason
    ) {
        if (player == null || reason == null) {
            return ResetResult.failed(
                    "A player and reset reason are required."
            );
        }

        long now = System.currentTimeMillis();
        AddonIncarnationState state =
                AddonIncarnationState.load(player);

        if (state.isDuplicateReset(reason.id(), now)) {
            return ResetResult.duplicate(
                    state.getResetSequence(),
                    state.getLifeToken()
            );
        }

        state.beginNewIncarnation(
                reason.id(),
                reason.guardDurationMillis(),
                now
        );

        RecognitionData recognitionData = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );
        recognitionData.resetForNewIncarnation(
                state.getLifeToken()
        );

        player.setData(
                AttachmentRegistry.GRANTED_SKILL_DATA,
                new GrantedSkillData()
        );
        player.setData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA,
                new GranterProgressData()
        );
        player.setData(
                AttachmentRegistry.BORROWED_SKILL_DATA,
                new BorrowedSkillData()
        );
        player.setData(
                AttachmentRegistry.RECOGNITION_DATA,
                recognitionData
        );

        RecognitionStrengthRewardService.reconcile(player);
        RecognitionProgressScreenService.clear(player.getUUID());
        SubordinateOverviewService.forget(player.getUUID());
        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(player);

        return ResetResult.completed(
                state.getResetSequence(),
                state.getLifeToken()
        );
    }

    public enum ResetReason {
        CHARACTER_RESET(
                "character_reset",
                AddonLifecyclePolicy.CHARACTER_RESET_GUARD_MILLIS
        ),
        ADMIN_COMMAND(
                "admin_command",
                AddonLifecyclePolicy.ADMIN_RESET_GUARD_MILLIS
        );

        private final String id;
        private final long guardDurationMillis;

        ResetReason(
                String id,
                long guardDurationMillis
        ) {
            this.id = id;
            this.guardDurationMillis = Math.max(
                    0L,
                    guardDurationMillis
            );
        }

        public String id() {
            return id;
        }

        public long guardDurationMillis() {
            return guardDurationMillis;
        }
    }

    public record ResetResult(
            Status status,
            int resetSequence,
            String lifeToken,
            String message
    ) {
        public boolean successful() {
            return status == Status.COMPLETED
                    || status == Status.DUPLICATE_SUPPRESSED;
        }

        private static ResetResult completed(
                int sequence,
                String lifeToken
        ) {
            return new ResetResult(
                    Status.COMPLETED,
                    sequence,
                    lifeToken,
                    "Addon incarnation data reset successfully."
            );
        }

        private static ResetResult duplicate(
                int sequence,
                String lifeToken
        ) {
            return new ResetResult(
                    Status.DUPLICATE_SUPPRESSED,
                    sequence,
                    lifeToken,
                    "A duplicate reset callback was suppressed."
            );
        }

        private static ResetResult failed(
                String message
        ) {
            return new ResetResult(
                    Status.FAILED,
                    0,
                    "",
                    message == null ? "Reset failed." : message
            );
        }
    }

    public enum Status {
        COMPLETED,
        DUPLICATE_SUPPRESSED,
        FAILED
    }
}