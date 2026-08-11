package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerPlayer;

public final class RecognitionUnnameService {

    private RecognitionUnnameService() {
    }

    /**
     * Removes the player's native Tensura name and clears only the naming
     * result stored by this addon.
     *
     * Recognition deeds, awakening flags, path progress, mastery, EP,
     * magicules, skills, subordinate history and incarnation identity are
     * deliberately preserved.
     */
    public static Result unname(
            ServerPlayer player
    ) {
        if (player == null) {
            return Result.failed(
                    "",
                    "A player is required."
            );
        }

        RecognitionData recognitionData =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        if (recognitionData.isWriteBlockedByFutureVersion()) {
            return Result.failed(
                    "",
                    "A future recognition version is preserved read-only. Use the newer addon version that created it."
            );
        }

        IExistence existence =
                TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return Result.failed(
                    "",
                    "Tensura existence storage could not be found."
            );
        }

        String previousNativeName =
                sanitizeName(
                        existence.getName()
                );

        RecognitionNativeNameStorageService.Result nativeClearResult =
                RecognitionNativeNameStorageService.write(
                        player,
                        existence,
                        ""
                );

        /*
         * Do not clear the addon's commitment data unless the native Tensura
         * name was successfully cleared. This prevents the two systems from
         * becoming desynchronized after a failed debug operation.
         */
        if (!nativeClearResult.success()) {
            return Result.failed(
                    previousNativeName,
                    nativeClearResult.errorMessage()
            );
        }

        recognitionData
                .clearNamingCommitPreservingLifeProgress();

        /*
         * The protected unname route deliberately permits another native
         * endowment in the same life. Keeping the old marker would suppress
         * Tensura's naming request after the next recognition commitment.
         */
        AddonIncarnationState.load(player)
                .clearNativeEndowmentState();

        /*
         * Reassigning the mutable attachment makes the changed state explicit
         * to NeoForge and keeps the command safe if attachment handling changes
         * later.
         */
        player.setData(
                AttachmentRegistry.RECOGNITION_DATA,
                recognitionData
        );

        /*
         * Remove both the permanent recognition attributes and the
         * effort-scaled native-endowment capacity immediately. The service
         * also clamps magicules and aura to their restored maxima.
         */
        RecognitionStrengthRewardService.reconcile(
                player
        );

        /*
         * Immediately remove the title from the nametag, tab list and all
         * server-side display-name consumers.
         */
        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(player);

        return Result.succeeded(
                previousNativeName
        );
    }

    private static String sanitizeName(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    public record Result(
            boolean success,
            String previousNativeName,
            String errorMessage
    ) {

        public Result {
            previousNativeName =
                    previousNativeName == null
                            ? ""
                            : previousNativeName.trim();

            errorMessage =
                    errorMessage == null
                            ? ""
                            : errorMessage.trim();
        }

        public static Result succeeded(
                String previousNativeName
        ) {
            return new Result(
                    true,
                    previousNativeName,
                    ""
            );
        }

        public static Result failed(
                String previousNativeName,
                String errorMessage
        ) {
            return new Result(
                    false,
                    previousNativeName,
                    errorMessage
            );
        }

        public boolean previouslyNamed() {
            return !previousNativeName.isBlank();
        }
    }

}
