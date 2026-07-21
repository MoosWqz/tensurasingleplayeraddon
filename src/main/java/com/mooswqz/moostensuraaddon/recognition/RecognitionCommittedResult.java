package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.List;
import java.util.Optional;

/**
 * Read-only snapshot of the recognition result frozen into player data.
 *
 * <p>Raw path IDs are always retained. Resolved enum values are optional so a
 * future or missing path identifier can be diagnosed without silently
 * replacing the player's historical result with a live evaluator result.</p>
 */
public record RecognitionCommittedResult(
        boolean committed,
        int dataVersion,
        int resultVersion,
        int rulesVersion,
        int rewardProfileVersion,
        String primaryPathId,
        String secondaryPathId,
        boolean pure,
        String contradictionModifier,
        String bestowedTitle,
        String frozenDisplayName,
        String balanceSource,
        long balanceRevision,
        long committedAtEpochMillis,
        String incarnationId,
        double primaryScore,
        double secondaryScore,
        String migrationSource,
        List<String> validationIssues
) {

    public RecognitionCommittedResult {
        primaryPathId =
                clean(
                        primaryPathId
                );

        secondaryPathId =
                clean(
                        secondaryPathId
                );

        contradictionModifier =
                clean(
                        contradictionModifier
                );

        bestowedTitle =
                clean(
                        bestowedTitle
                );

        frozenDisplayName =
                clean(
                        frozenDisplayName
                );

        balanceSource =
                clean(
                        balanceSource
                );

        incarnationId =
                clean(
                        incarnationId
                );

        migrationSource =
                clean(
                        migrationSource
                );

        validationIssues =
                validationIssues == null
                        ? List.of()
                        : List.copyOf(
                        validationIssues
                );

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

    public Optional<RecognitionPath> primaryPath() {
        return RecognitionPath.byId(
                primaryPathId
        );
    }

    public Optional<RecognitionPath> secondaryPath() {
        return RecognitionPath.byId(
                secondaryPathId
        );
    }

    public boolean hasSecondaryPathId() {
        return !secondaryPathId.isBlank();
    }

    public boolean valid() {
        return committed
                && validationIssues.isEmpty();
    }

    public MigrationState migrationState() {
        if (!committed) {
            return MigrationState.NOT_COMMITTED;
        }

        if (dataVersion
                > RecognitionData.CURRENT_DATA_VERSION
                || resultVersion
                > RecognitionCommitRecord
                .CURRENT_RESULT_VERSION
                || rulesVersion
                > RecognitionCommitRecord
                .CURRENT_RULES_VERSION
                || rewardProfileVersion
                > RecognitionCommitRecord
                .CURRENT_REWARD_PROFILE_VERSION) {

            return MigrationState.FUTURE_VERSION;
        }

        if (!validationIssues.isEmpty()) {
            return MigrationState.INCOMPLETE;
        }

        if (!migrationSource.isBlank()
                && !RecognitionCommitRecord
                .NATIVE_MIGRATION_SOURCE
                .equals(migrationSource)) {

            return MigrationState.LEGACY_BACKFILLED;
        }

        return MigrationState.CURRENT;
    }

    public String pathSummary() {
        if (!committed) {
            return "none";
        }

        if (pure) {
            return "Pure " + displayPathId(
                    primaryPathId
            );
        }

        if (secondaryPathId.isBlank()) {
            return displayPathId(
                    primaryPathId
            );
        }

        return displayPathId(
                primaryPathId
        )
                + " / "
                + displayPathId(
                secondaryPathId
        );
    }

    private static String displayPathId(
            String rawId
    ) {
        if (rawId == null || rawId.isBlank()) {
            return "missing";
        }

        return RecognitionPath.byId(rawId)
                .map(RecognitionPath::getId)
                .orElse(
                        rawId
                                + " [unknown]"
                );
    }

    private static String clean(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private static double sanitizeScore(
            double value
    ) {
        return !Double.isFinite(value)
                || value < 0.0D
                ? 0.0D
                : value;
    }

    public enum MigrationState {
        NOT_COMMITTED("not committed"),
        CURRENT("current"),
        LEGACY_BACKFILLED("legacy result backfilled"),
        INCOMPLETE("incomplete or invalid"),
        FUTURE_VERSION("future version preserved");

        private final String displayName;

        MigrationState(
                String displayName
        ) {
            this.displayName =
                    displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}