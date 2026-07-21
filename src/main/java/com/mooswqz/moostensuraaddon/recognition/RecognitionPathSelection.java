package com.mooswqz.moostensuraaddon.recognition;

import java.util.Objects;
import java.util.Optional;

public record RecognitionPathSelection(
        RecognitionPath primaryPath,
        RecognitionPath secondaryPath,
        boolean pure,
        double primaryScore,
        double secondaryScore
) {

    public RecognitionPathSelection {
        Objects.requireNonNull(
                primaryPath,
                "primaryPath cannot be null"
        );

        if (pure && secondaryPath != null) {
            throw new IllegalArgumentException(
                    "A Pure recognition result cannot have a secondary path."
            );
        }

        primaryScore = sanitizeScore(primaryScore);
        secondaryScore = sanitizeScore(secondaryScore);
    }

    public Optional<RecognitionPath> getSecondaryPath() {
        return Optional.ofNullable(secondaryPath);
    }

    public boolean hasSecondaryPath() {
        return secondaryPath != null;
    }

    private static double sanitizeScore(double score) {
        if (!Double.isFinite(score) || score < 0.0D) {
            return 0.0D;
        }

        return score;
    }
}