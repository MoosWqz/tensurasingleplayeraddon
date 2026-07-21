package com.mooswqz.moostensuraaddon.recognition;

/**
 *
 *     The Established component minimum uses the existing directional
 *     evidence minimum.
 *     The Pure component minimum is at least twice that value and never
 *     below 12.
 *     Morality dominance uses the existing raw dominance ratio.
 *     Temperament dominance uses the square root of the existing final
 *     dominance ratio, converting the former whole-path requirement into an
 *     axis-level requirement.
 *
 */
public record RecognitionComponentQualificationRules(
        double establishedComponentMinimum,
        double pureComponentMinimum,
        double moralityDominanceRatio,
        double temperamentDominanceRatio
) {

    public static final double MINIMUM_PURE_COMPONENT_EVIDENCE =
            12.0D;

    public RecognitionComponentQualificationRules {
        establishedComponentMinimum =
                sanitizeMinimum(
                        establishedComponentMinimum,
                        RecognitionPathEvaluator
                                .MIN_DIRECTIONAL_MORALITY_EVIDENCE
                );

        pureComponentMinimum =
                Math.max(
                        establishedComponentMinimum,
                        sanitizeMinimum(
                                pureComponentMinimum,
                                Math.max(
                                        MINIMUM_PURE_COMPONENT_EVIDENCE,
                                        establishedComponentMinimum * 2.0D
                                )
                        )
                );

        moralityDominanceRatio =
                sanitizeRatio(
                        moralityDominanceRatio,
                        RecognitionPathEvaluator
                                .DEFAULT_RAW_DOMINANCE_RATIO
                );

        temperamentDominanceRatio =
                sanitizeRatio(
                        temperamentDominanceRatio,
                        Math.sqrt(
                                RecognitionPathEvaluator
                                        .DEFAULT_DOMINANCE_RATIO
                        )
                );
    }

    public static RecognitionComponentQualificationRules from(
            RecognitionBalanceSnapshot.Selection selection
    ) {
        RecognitionBalanceSnapshot.Selection safeSelection =
                selection == null
                        ? RecognitionBalanceSnapshot
                        .createDefaults()
                        .selection()
                        : selection;

        double establishedMinimum =
                safeSelection
                        .minimumDirectionalMoralityEvidence();

        return new RecognitionComponentQualificationRules(
                establishedMinimum,
                Math.max(
                        MINIMUM_PURE_COMPONENT_EVIDENCE,
                        establishedMinimum * 2.0D
                ),
                safeSelection.rawDominanceRatio(),
                Math.sqrt(
                        Math.max(
                                1.0D,
                                safeSelection.dominanceRatio()
                        )
                )
        );
    }

    public static RecognitionComponentQualificationRules defaults() {
        return from(
                RecognitionBalanceSnapshot
                        .createDefaults()
                        .selection()
        );
    }

    private static double sanitizeMinimum(
            double value,
            double fallback
    ) {
        if (!Double.isFinite(value)
                || value < 0.0D) {

            return Math.max(
                    0.0D,
                    fallback
            );
        }

        return value;
    }

    private static double sanitizeRatio(
            double value,
            double fallback
    ) {
        if (!Double.isFinite(value)
                || value < 1.0D) {

            return Math.max(
                    1.0D,
                    fallback
            );
        }

        return value;
    }
}