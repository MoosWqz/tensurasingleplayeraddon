package com.mooswqz.moostensuraaddon.recognition;

/**
 * Complete immutable input for one recognition commitment.
 *
 * <p>The object is fully validated before {@code RecognitionData} mutates its
 * maps, allowing the saved result to be written as one server-thread
 * transaction with rollback protection.</p>
 */
public record RecognitionCommitRecord(
        String primaryPathId,
        String secondaryPathId,
        boolean pure,
        String contradictionModifier,
        String bestowedTitle,
        String frozenDisplayName,
        int resultVersion,
        int rulesVersion,
        int rewardProfileVersion,
        String balanceSource,
        long balanceRevision,
        long committedAtEpochMillis,
        String incarnationId,
        double primaryScore,
        double secondaryScore
) {

    public static final int CURRENT_RESULT_VERSION =
            1;

    public static final int CURRENT_RULES_VERSION =
            2;

    public static final int CURRENT_REWARD_PROFILE_VERSION =
            RecognitionStrengthRewardFormula.PROFILE_VERSION;

    public static final String NO_CONTRADICTION =
            "none";

    public static final String UNKNOWN_BALANCE_SOURCE =
            "legacy_unknown";

    public static final String NATIVE_MIGRATION_SOURCE =
            "native_v2";

    public RecognitionCommitRecord {
        primaryPathId =
                RecognitionPath.canonicalizeStoredId(
                        primaryPathId
                );

        RecognitionPath primaryPath =
                RecognitionPath.byId(
                                primaryPathId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "A recognition commitment requires a known primary path."
                                )
                        );

        secondaryPathId =
                RecognitionPath.canonicalizeStoredId(
                        secondaryPathId
                );

        if (pure) {
            secondaryPathId = "";
            secondaryScore = 0.0D;
        } else {
            RecognitionPath secondaryPath =
                    RecognitionPath.byId(
                                    secondaryPathId
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "A combined recognition commitment requires a known secondary path."
                                    )
                            );

            if (secondaryPath == primaryPath) {
                throw new IllegalArgumentException(
                        "Primary and secondary recognition paths must differ."
                );
            }
        }

        contradictionModifier =
                normalizeIdentifier(
                        contradictionModifier,
                        NO_CONTRADICTION
                );

        bestowedTitle =
                cleanRequiredText(
                        bestowedTitle,
                        "A recognition commitment requires a bestowed title."
                );

        frozenDisplayName =
                cleanOptionalText(
                        frozenDisplayName
                );

        resultVersion =
                requirePositiveVersion(
                        resultVersion,
                        "resultVersion"
                );

        rulesVersion =
                requirePositiveVersion(
                        rulesVersion,
                        "rulesVersion"
                );

        rewardProfileVersion =
                requirePositiveVersion(
                        rewardProfileVersion,
                        "rewardProfileVersion"
                );

        balanceSource =
                cleanOptionalText(
                        balanceSource
                );

        if (balanceSource.isBlank()) {
            balanceSource =
                    UNKNOWN_BALANCE_SOURCE;
        }

        balanceRevision =
                Math.max(
                        0L,
                        balanceRevision
                );

        committedAtEpochMillis =
                Math.max(
                        0L,
                        committedAtEpochMillis
                );

        incarnationId =
                cleanOptionalText(
                        incarnationId
                );

        primaryScore =
                sanitizeScore(
                        primaryScore
                );

        secondaryScore =
                pure
                        ? 0.0D
                        : sanitizeScore(
                        secondaryScore
                );
    }

    public static RecognitionCommitRecord fromSelection(
            RecognitionPathSelection selection,
            String bestowedTitle,
            String frozenDisplayName,
            String balanceSource,
            long balanceRevision,
            long committedAtEpochMillis,
            String incarnationId
    ) {
        if (selection == null) {
            throw new IllegalArgumentException(
                    "A recognition selection is required."
            );
        }

        return new RecognitionCommitRecord(
                selection.primaryPath()
                        .getId(),
                selection.hasSecondaryPath()
                        ? selection.secondaryPath()
                        .getId()
                        : "",
                selection.pure(),
                NO_CONTRADICTION,
                bestowedTitle,
                frozenDisplayName,
                CURRENT_RESULT_VERSION,
                CURRENT_RULES_VERSION,
                CURRENT_REWARD_PROFILE_VERSION,
                balanceSource,
                balanceRevision,
                committedAtEpochMillis,
                incarnationId,
                selection.primaryScore(),
                selection.pure()
                        ? 0.0D
                        : selection.secondaryScore()
        );
    }

    public RecognitionPathSelection toSelection() {
        RecognitionPath primaryPath =
                RecognitionPath.byId(
                                primaryPathId
                        )
                        .orElseThrow();

        RecognitionPath secondaryPath =
                pure
                        ? null
                        : RecognitionPath.byId(
                                secondaryPathId
                        )
                        .orElseThrow();

        return new RecognitionPathSelection(
                primaryPath,
                secondaryPath,
                pure,
                primaryScore,
                secondaryScore
        );
    }

    private static int requirePositiveVersion(
            int value,
            String fieldName
    ) {
        if (value < 1) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must be at least 1."
            );
        }

        return value;
    }

    private static String cleanRequiredText(
            String value,
            String errorMessage
    ) {
        String cleaned =
                cleanOptionalText(
                        value
                );

        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(
                    errorMessage
            );
        }

        return cleaned;
    }

    private static String cleanOptionalText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private static String normalizeIdentifier(
            String value,
            String fallback
    ) {
        String cleaned =
                cleanOptionalText(
                        value
                )
                        .toLowerCase(
                                java.util.Locale.ROOT
                        )
                        .replace('-', '_')
                        .replace(' ', '_');

        while (cleaned.contains("__")) {
            cleaned =
                    cleaned.replace(
                            "__",
                            "_"
                    );
        }

        return cleaned.isBlank()
                ? fallback
                : cleaned;
    }

    private static double sanitizeScore(
            double value
    ) {
        return !Double.isFinite(value)
                || value < 0.0D
                ? 0.0D
                : value;
    }
}