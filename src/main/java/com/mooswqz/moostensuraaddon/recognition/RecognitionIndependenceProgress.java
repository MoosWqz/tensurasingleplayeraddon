package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Event-driven recording and one-time backfill for configured independence
 * advancements.
 */
public final class RecognitionIndependenceProgress {

    private RecognitionIndependenceProgress() {
    }

    /**
     * Backfills only the currently configured advancement IDs when the
     * datapack-definition fingerprint changes for this player.
     *
     * <p>Once the fingerprint matches, this method is a string comparison and
     * performs no advancement lookup.</p>
     */
    public static boolean synchronize(
            ServerPlayer player,
            RecognitionData data
    ) {
        if (player == null
                || data == null) {
            return false;
        }

        RecognitionIndependenceMilestoneManager.State state =
                RecognitionIndependenceMilestoneManager
                        .getState();

        String storedFingerprint =
                data.getString(
                        RecognitionStatKeys
                                .INDEPENDENCE_DEFINITION_FINGERPRINT
                );

        if (state.fingerprint()
                .equals(
                        storedFingerprint
                )) {
            return false;
        }

        MinecraftServer server =
                player.getServer();

        if (server == null) {
            return false;
        }

        boolean changed =
                false;

        for (RecognitionIndependenceMilestoneManager.Milestone milestone :
                state.milestones()) {

            AdvancementHolder advancement =
                    server.getAdvancements()
                            .get(
                                    milestone.advancementId()
                            );

            if (advancement == null) {
                continue;
            }

            if (player.getAdvancements()
                    .getOrStartProgress(
                            advancement
                    )
                    .isDone()) {

                changed |=
                        data.addUniqueValue(
                                RecognitionStatKeys
                                        .INDEPENDENCE_MILESTONES,
                                milestone.id()
                                        .toString()
                        );
            }
        }

        data.setString(
                RecognitionStatKeys
                        .INDEPENDENCE_DEFINITION_FINGERPRINT,
                state.fingerprint()
        );

        return changed;
    }

    public static boolean recordEarned(
            ServerPlayer player,
            ResourceLocation advancementId
    ) {
        if (player == null
                || advancementId == null) {
            return false;
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry
                                .RECOGNITION_DATA
                );

        RecognitionIndependenceMilestoneManager.State state =
                RecognitionIndependenceMilestoneManager
                        .getState();

        RecognitionIndependenceMilestoneManager.Milestone milestone =
                state.byAdvancement()
                        .get(
                                advancementId
                        );

        if (milestone == null) {
            return false;
        }

        boolean newlyRecorded =
                data.addUniqueValue(
                        RecognitionStatKeys
                                .INDEPENDENCE_MILESTONES,
                        milestone.id()
                                .toString()
                );

        if (!newlyRecorded) {
            return false;
        }

        RecognitionIdentityHistoryIntegration
                .recordIndependenceMilestone(
                        data,
                        milestone.points(),
                        getOverworldGameTime(
                                player
                        )
                );

        return true;
    }

    private static long getOverworldGameTime(
            ServerPlayer player
    ) {
        if (player == null) {
            return 0L;
        }

        if (player.getServer() != null) {
            return Math.max(
                    0L,
                    player.getServer()
                            .overworld()
                            .getGameTime()
            );
        }

        return Math.max(
                0L,
                player.level()
                        .getGameTime()
        );
    }

}