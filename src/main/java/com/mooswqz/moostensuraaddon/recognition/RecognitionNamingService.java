package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class RecognitionNamingService {

    public static final int REQUIRED_EXPERIENCE_LEVEL =
            50;

    private RecognitionNamingService() {
    }

    /**
     * Performs a complete live evaluation.
     *
     * This method synchronizes the player state first, making it suitable for
     * altar interactions and the recognition progress screen.
     */
    public static RecognitionNamingEligibility evaluate(
            ServerPlayer player
    ) {
        if (player == null) {
            throw new IllegalArgumentException(
                    "A player is required for naming evaluation."
            );
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        TensuraRecognitionStateHelper.synchronize(
                player,
                data
        );

        RecognitionAuthorityProgress.synchronize(
                player,
                data
        );

        RecognitionEvaluation evaluation =
                RecognitionPathEvaluator.evaluate(
                        data
                );

        return evaluate(
                player,
                data,
                evaluation
        );
    }

    /**
     * Reuses an evaluation that has already been calculated.
     *
     * This is primarily used by diagnostics and the recognition screen so the
     * complete recognition graph does not need to be calculated twice.
     */
    public static RecognitionNamingEligibility evaluate(
            ServerPlayer player,
            RecognitionData data,
            RecognitionEvaluation evaluation
    ) {
        if (player == null) {
            throw new IllegalArgumentException(
                    "A player is required for naming evaluation."
            );
        }

        RecognitionData safeData =
                data == null
                        ? new RecognitionData()
                        : data;

        RecognitionEvaluation safeEvaluation =
                evaluation == null
                        ? RecognitionPathEvaluator.evaluate(
                        safeData
                )
                        : evaluation;

        String nativeName =
                getNativeName(
                        player
                );

        boolean nativeNamed =
                !nativeName.isBlank();

        synchronizeCommittedResultMetadata(
                player,
                safeData,
                nativeName
        );

        boolean recognitionCommitted =
                safeData.isNamingCommitted();

        int recognizedLevel =
                getRecognizedExperienceLevel(
                        player,
                        safeData
                );

        RecognitionNamingCandidate candidate =
                safeEvaluation.getSelection()
                        .map(selection ->
                                createCandidate(
                                        player,
                                        selection
                                )
                        )
                        .orElse(null);

        RecognitionNamingEligibility.Status status;

        if (recognitionCommitted) {
            status =
                    RecognitionNamingEligibility.Status
                            .ALREADY_COMMITTED;
        } else if (nativeNamed) {
            status =
                    RecognitionNamingEligibility.Status
                            .ALREADY_NAMED;
        } else if (recognizedLevel
                < REQUIRED_EXPERIENCE_LEVEL) {
            status =
                    RecognitionNamingEligibility.Status
                            .NOT_ENOUGH_LEVEL;
        } else if (candidate == null) {
            status =
                    RecognitionNamingEligibility.Status
                            .NO_RECOGNITION_SELECTION;
        } else {
            status =
                    RecognitionNamingEligibility.Status.READY;
        }

        return new RecognitionNamingEligibility(
                status,
                recognizedLevel,
                REQUIRED_EXPERIENCE_LEVEL,
                nativeNamed,
                nativeName,
                recognitionCommitted,
                safeEvaluation,
                candidate
        );
    }

    /**
     * Preferred commitment route for altar runtimes that already hold the
     * eligibility snapshot fixed at ritual start.
     *
     * <p>The compatibility {@code RecognitionData.commitNaming(selection,
     * title)} route remains valid, but this method can also freeze the exact
     * display name and incarnation ID in the same transaction.</p>
     */
    public static boolean commitRecognition(
            ServerPlayer player,
            RecognitionNamingEligibility eligibility
    ) {
        if (player == null
                || eligibility == null
                || eligibility.status()
                != RecognitionNamingEligibility.Status.READY
                || eligibility.candidate() == null) {

            return false;
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        if (data.isNamingCommitted()) {
            return false;
        }

        RecognitionNamingCandidate candidate =
                eligibility.candidate();

        RecognitionPathSelection selection =
                new RecognitionPathSelection(
                        candidate.primaryPath(),
                        candidate.hasSecondaryPath()
                                ? candidate.secondaryPath()
                                : null,
                        candidate.pure(),
                        candidate.primaryScore(),
                        candidate.secondaryScore()
                );

        RecognitionEvaluation evaluation =
                eligibility.evaluation();

        String accountName =
                player.getGameProfile()
                        .getName();

        String frozenDisplayName =
                candidate.formatDisplayName(
                        accountName
                );

        String incarnationId =
                resolveCommitIncarnationId(
                        player,
                        data
                );

        RecognitionCommitRecord record =
                RecognitionCommitRecord
                        .fromSelection(
                                selection,
                                candidate.bestowedTitle(),
                                frozenDisplayName,
                                evaluation.getBalance()
                                        .sourceId(),
                                evaluation.getBalanceRevision(),
                                System.currentTimeMillis(),
                                incarnationId
                        );

        boolean committed = data.commitNaming(
                record
        );

        if (!committed) {
            return false;
        }

        RecognitionStrengthRewardService.initializeNewCommit(
                data,
                evaluation.getDimensions()
                        .identityStrength(),
                evaluation.getBalance()
                        .identityStrength()
                        .maximum(),
                candidate.pure()
        );

        /*
         * Attribute reconciliation is deliberately recoverable. The identity
         * commitment stays frozen even if another mod temporarily prevents an
         * attribute from being available; the existing 40-tick synchronizer
         * repairs the reward later without allowing a reroll.
         */
        RecognitionStrengthRewardService.reconcile(
                player
        );

        return true;
    }

    /**
     * Completes only metadata that could not exist in the version-1 save
     * format. Existing frozen values are never replaced.
     */
    private static void synchronizeCommittedResultMetadata(
            ServerPlayer player,
            RecognitionData data,
            String nativeName
    ) {
        if (player == null
                || data == null
                || !data.isNamingCommitted()) {
            return;
        }

        String frozenDisplayName =
                data.getFrozenDisplayName();

        if (frozenDisplayName.isBlank()) {
            if (nativeName != null
                    && !nativeName.isBlank()) {
                frozenDisplayName =
                        nativeName.trim();
            } else {
                String accountName =
                        player.getGameProfile()
                                .getName();

                String bestowedTitle =
                        data.getBestowedTitle();

                if (bestowedTitle.isBlank()) {
                    frozenDisplayName =
                            accountName;
                } else {
                    frozenDisplayName =
                            accountName
                                    + " "
                                    + bestowedTitle;
                }
            }
        }

        String incarnationId =
                data.getIncarnationId();

        if (incarnationId.isBlank()) {
            incarnationId =
                    createLegacyIncarnationId(
                            player
                    );
        }

        data.completeCommittedPlayerMetadata(
                frozenDisplayName,
                incarnationId
        );
    }

    private static String resolveCommitIncarnationId(
            ServerPlayer player,
            RecognitionData data
    ) {
        String existing =
                data.getIncarnationId();

        if (!existing.isBlank()) {
            return existing;
        }

        return "incarnation-v1-"
                + player.getUUID()
                + "-"
                + Long.toUnsignedString(
                System.currentTimeMillis(),
                36
        );
    }

    private static String createLegacyIncarnationId(
            ServerPlayer player
    ) {
        return "legacy-v1-"
                + player.getUUID();
    }

    /**
     * Returns the highest experience level legitimately reached during this
     * incarnation.
     *
     * The current level is synchronized immediately before the stored maximum
     * is read. Reaching the requirement and interacting with the altar
     * therefore does not depend on a periodic recognition synchronization
     * tick.
     *
     * Losing levels through death, enchanting, anvils or another progression
     * system does not erase an already-earned recognition milestone.
     */
    private static int getRecognizedExperienceLevel(
            ServerPlayer player,
            RecognitionData data
    ) {
        if (player == null || data == null) {
            return 0;
        }

        int currentLevel =
                Math.max(
                        0,
                        player.experienceLevel
                );

        data.setCounterMaximum(
                RecognitionStatKeys.HIGHEST_EXPERIENCE_LEVEL,
                currentLevel
        );

        return Math.max(
                currentLevel,
                data.getCounter(
                        RecognitionStatKeys
                                .HIGHEST_EXPERIENCE_LEVEL
                )
        );
    }

    private static RecognitionNamingCandidate createCandidate(
            ServerPlayer player,
            RecognitionPathSelection selection
    ) {
        RecognitionPath primaryPath =
                selection.primaryPath();

        RecognitionPath secondaryPath =
                selection.pure()
                        ? null
                        : selection.secondaryPath();

        String title =
                selectDeterministicTitle(
                        player,
                        primaryPath,
                        secondaryPath,
                        selection.pure()
                );

        return new RecognitionNamingCandidate(
                primaryPath,
                secondaryPath,
                selection.pure(),
                selection.primaryScore(),
                selection.pure()
                        ? 0.0D
                        : selection.secondaryScore(),
                title
        );
    }

    /**
     * Selects a stable title from the currently loaded server datapack pool.
     *
     * The title result remains deterministic for the same player, world,
     * primary path, secondary path and pure/combined state as long as the
     * loaded title list retains the same ordering.
     *
     * Once the naming ritual commits a title, that title is stored in the
     * player's RecognitionData and is no longer affected by datapack reloads.
     */
    private static String selectDeterministicTitle(
            ServerPlayer player,
            RecognitionPath primaryPath,
            RecognitionPath secondaryPath,
            boolean pure
    ) {
        List<String> titles =
                RecognitionTitlePoolManager.getTitles(
                        primaryPath,
                        pure
                );

        if (titles.isEmpty()) {
            return RecognitionTitlePoolManager
                    .getFallbackTitle(
                            primaryPath,
                            pure
                    );
        }

        long hash =
                0xcbf29ce484222325L;

        hash = mix(
                hash,
                player.getUUID()
                        .getMostSignificantBits()
        );

        hash = mix(
                hash,
                player.getUUID()
                        .getLeastSignificantBits()
        );

        hash = mix(
                hash,
                player.serverLevel()
                        .getSeed()
        );

        hash = mixString(
                hash,
                primaryPath == null
                        ? "none"
                        : primaryPath.getId()
        );

        hash = mixString(
                hash,
                secondaryPath == null
                        ? "none"
                        : secondaryPath.getId()
        );

        hash = mixString(
                hash,
                pure
                        ? "pure"
                        : "combined"
        );

        int index =
                Math.floorMod(
                        hash,
                        titles.size()
                );

        return titles.get(index);
    }

    private static String getNativeName(
            ServerPlayer player
    ) {
        IExistence existence =
                TensuraStorages.getExistenceFrom(
                        player
                );

        if (existence == null) {
            return "";
        }

        String name =
                existence.getName();

        return name == null
                ? ""
                : name.trim();
    }

    private static long mix(
            long hash,
            long value
    ) {
        long result =
                hash;

        for (int shift = 0;
             shift < Long.SIZE;
             shift += Byte.SIZE) {

            result ^=
                    (value >>> shift)
                            & 0xffL;

            result *=
                    0x100000001b3L;
        }

        return result;
    }

    private static long mixString(
            long hash,
            String value
    ) {
        String safeValue =
                value == null
                        ? ""
                        : value;

        long result =
                hash;

        for (int index = 0;
             index < safeValue.length();
             index++) {

            result ^=
                    safeValue.charAt(
                            index
                    );

            result *=
                    0x100000001b3L;
        }

        return result;
    }
}