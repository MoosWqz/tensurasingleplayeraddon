package com.mooswqz.moostensuraaddon.recognition;

import java.util.EnumMap;
import java.util.Map;

public final class RecognitionPathEvidenceGate {

    /*
     * One tiny deed should not be enough to establish an entire moral
     * identity.
     *
     * Examples:
     * - One baby-animal kill gives only 1 Evil and cannot establish Evil.
     * - One civilian kill gives 6 Evil and can establish Evil.
     * - One raid victory gives 8 Good and can establish Good.
     * - TH and TDL provide 15 directional evidence and therefore qualify.
     */
    public static final double MIN_DIRECTIONAL_MORALITY_EVIDENCE =
            6.0D;

    private RecognitionPathEvidenceGate() {
    }

    public static Map<RecognitionPath, Double> apply(
            RecognitionDimensions dimensions,
            Map<RecognitionPath, Double> scores
    ) {
        EnumMap<RecognitionPath, Double> gatedScores =
                new EnumMap<>(RecognitionPath.class);

        for (RecognitionPath path : RecognitionPath.values()) {
            double score = getSafeScore(scores, path);

            if (!hasRequiredEvidence(path, dimensions)) {
                score = 0.0D;
            }

            gatedScores.put(path, score);
        }

        return Map.copyOf(gatedScores);
    }

    public static boolean hasRequiredEvidence(
            RecognitionPath path,
            RecognitionDimensions dimensions
    ) {
        if (path == null || dimensions == null) {
            return false;
        }

        String pathName = path.name();

        if (pathName.endsWith("_GOOD")) {
            return dimensions.good()
                    >= MIN_DIRECTIONAL_MORALITY_EVIDENCE;
        }

        if (pathName.endsWith("_EVIL")) {
            return dimensions.evil()
                    >= MIN_DIRECTIONAL_MORALITY_EVIDENCE;
        }

        /*
         * Lawful Neutral, True Neutral and Chaotic Neutral are not tied to
         * either directional morality and remain available.
         */
        return true;
    }

    private static double getSafeScore(
            Map<RecognitionPath, Double> scores,
            RecognitionPath path
    ) {
        if (scores == null || path == null) {
            return 0.0D;
        }

        Double value = scores.get(path);

        if (value == null
                || !Double.isFinite(value)
                || value <= 0.0D) {
            return 0.0D;
        }

        return value;
    }
}