package com.mooswqz.moostensuraaddon.recognition;

import java.util.List;

/** Read-only diagnostic snapshot of one player's recognition strength. */
public record RecognitionStrengthRewardSnapshot(
        boolean recognitionCommitted,
        boolean committedResultValid,
        int storedRewardProfileVersion,
        boolean rewardMetadataInitialized,
        String migrationSource,
        double frozenIdentityStrength,
        double identityStrengthMaximum,
        double totalStrength,
        boolean futureProfilePreserved,
        boolean attributeStateMatches,
        List<String> mismatchedAttributes,
        RecognitionStrengthRewardFormula.Reward expectedReward
) {
    public RecognitionStrengthRewardSnapshot {
        migrationSource = migrationSource == null ? "" : migrationSource.trim();
        frozenIdentityStrength = sanitize(frozenIdentityStrength);
        identityStrengthMaximum = sanitize(identityStrengthMaximum);
        totalStrength = sanitize(totalStrength);
        mismatchedAttributes = mismatchedAttributes == null
                ? List.of()
                : List.copyOf(mismatchedAttributes);
    }

    public boolean active() {
        return recognitionCommitted
                && committedResultValid
                && rewardMetadataInitialized
                && !futureProfilePreserved;
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }
}