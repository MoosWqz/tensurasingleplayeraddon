package com.mooswqz.moostensuraaddon.recognition;

public record RecognitionNamingCandidate(
        RecognitionPath primaryPath,
        RecognitionPath secondaryPath,
        boolean pure,
        double primaryScore,
        double secondaryScore,
        String bestowedTitle
) {

    public RecognitionNamingCandidate {
        if (primaryPath == null) {
            throw new IllegalArgumentException(
                    "A naming candidate requires a primary path."
            );
        }

        primaryScore = sanitizeScore(primaryScore);
        secondaryScore = sanitizeScore(secondaryScore);

        if (pure) {
            secondaryPath = null;
            secondaryScore = 0.0D;
        }

        bestowedTitle = bestowedTitle == null
                ? ""
                : bestowedTitle.trim();
    }

    public boolean hasSecondaryPath() {
        return !pure && secondaryPath != null;
    }

    public String formatDisplayName(String username) {
        String safeUsername = username == null
                ? ""
                : username.trim();

        if (bestowedTitle.isBlank()) {
            return safeUsername;
        }

        if (safeUsername.isBlank()) {
            return bestowedTitle;
        }

        return safeUsername + " " + bestowedTitle;
    }

    public String getPathSummary() {
        if (pure) {
            return "Pure " + primaryPath.getId();
        }

        if (secondaryPath == null) {
            return primaryPath.getId();
        }

        return primaryPath.getId()
                + " + "
                + secondaryPath.getId();
    }

    private static double sanitizeScore(double value) {
        if (!Double.isFinite(value)
                || value < 0.0D) {
            return 0.0D;
        }

        return value;
    }
}