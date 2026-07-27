package com.mooswqz.moostensuraaddon.recognition;

import java.util.List;
import java.util.Optional;

/**
 * Immutable, read-only view of the stored contradiction-history foundation.
 */
public record RecognitionIdentityHistorySnapshot(
        int storedVersion,
        int currentVersion,
        RecognitionIdentityHistoryService.MigrationState migrationState,
        double goodMomentum,
        double evilMomentum,
        double orderMomentum,
        double freedomMomentum,
        double highestGoodCommitment,
        double highestEvilCommitment,
        double highestOrderCommitment,
        double highestFreedomCommitment,
        int moralReversalCount,
        int temperamentReversalCount,
        long lastGoodDeedGameTime,
        long lastEvilDeedGameTime,
        long lastOrderDeedGameTime,
        long lastFreedomDeedGameTime,
        String rawModifierId,
        String migrationSource,
        List<String> validationIssues
) {

    public RecognitionIdentityHistorySnapshot {
        storedVersion = Math.max(0, storedVersion);
        currentVersion = Math.max(1, currentVersion);
        migrationState = migrationState == null
                ? RecognitionIdentityHistoryService.MigrationState.INVALID_DATA
                : migrationState;

        goodMomentum = sanitize(goodMomentum);
        evilMomentum = sanitize(evilMomentum);
        orderMomentum = sanitize(orderMomentum);
        freedomMomentum = sanitize(freedomMomentum);

        highestGoodCommitment = sanitize(highestGoodCommitment);
        highestEvilCommitment = sanitize(highestEvilCommitment);
        highestOrderCommitment = sanitize(highestOrderCommitment);
        highestFreedomCommitment = sanitize(highestFreedomCommitment);

        moralReversalCount = Math.max(0, moralReversalCount);
        temperamentReversalCount = Math.max(0, temperamentReversalCount);

        lastGoodDeedGameTime = Math.max(0L, lastGoodDeedGameTime);
        lastEvilDeedGameTime = Math.max(0L, lastEvilDeedGameTime);
        lastOrderDeedGameTime = Math.max(0L, lastOrderDeedGameTime);
        lastFreedomDeedGameTime = Math.max(0L, lastFreedomDeedGameTime);

        rawModifierId = rawModifierId == null || rawModifierId.isBlank()
                ? RecognitionIdentityHistoryModifier.NONE.id()
                : rawModifierId.trim();

        migrationSource = migrationSource == null
                ? ""
                : migrationSource.trim();

        validationIssues = validationIssues == null
                ? List.of()
                : List.copyOf(validationIssues);
    }

    public Optional<RecognitionIdentityHistoryModifier> knownModifier() {
        return RecognitionIdentityHistoryModifier.byId(rawModifierId);
    }

    public boolean valid() {
        return validationIssues.isEmpty()
                && migrationState != RecognitionIdentityHistoryService.MigrationState.INVALID_DATA;
    }

    public boolean initialized() {
        return storedVersion > 0;
    }

    public boolean futureVersion() {
        return storedVersion > currentVersion;
    }

    public double moralMomentumTotal() {
        return goodMomentum + evilMomentum;
    }

    public double temperamentMomentumTotal() {
        return orderMomentum + freedomMomentum;
    }

    private static double sanitize(
            double value
    ) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return 0.0D;
        }

        return Math.min(
                RecognitionIdentityHistoryService.MAXIMUM_MOMENTUM,
                value
        );
    }
}