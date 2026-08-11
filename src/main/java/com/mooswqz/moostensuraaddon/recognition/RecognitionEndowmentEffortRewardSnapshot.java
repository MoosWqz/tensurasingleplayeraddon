package com.mooswqz.moostensuraaddon.recognition;

import java.util.List;

/** Read-only diagnostic snapshot of the effort-scaled endowment extension. */
public record RecognitionEndowmentEffortRewardSnapshot(
        boolean recognitionCommitted,
        boolean committedResultValid,
        boolean nativeNamed,
        boolean rewardMetadataInitialized,
        boolean futureProfilePreserved,
        boolean attributeStateMatches,
        List<String> mismatchedAttributes,
        RecognitionEndowmentEffortRewardFormula.Reward expectedReward
) {
    public RecognitionEndowmentEffortRewardSnapshot {
        mismatchedAttributes =
                mismatchedAttributes == null
                        ? List.of()
                        : List.copyOf(
                                mismatchedAttributes
                        );
    }

    public boolean active() {
        return recognitionCommitted
                && committedResultValid
                && nativeNamed
                && rewardMetadataInitialized
                && !futureProfilePreserved;
    }
}
