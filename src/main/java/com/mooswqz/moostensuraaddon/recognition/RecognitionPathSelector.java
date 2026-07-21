package com.mooswqz.moostensuraaddon.recognition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class RecognitionPathSelector {

    private RecognitionPathSelector() {
    }

    /**
     * Original final-score-only selector.
     *
     * <p>Retained for compatibility and isolated tests. Production recognition
     * uses {@link #selectWithComponentPureGate(Map, Map,
     * RecognitionPathComponents, RecognitionBalanceSnapshot.Selection)}.</p>
     */
    public static Optional<RecognitionPathSelection> select(
            Map<RecognitionPath, Double> scores,
            double establishedThreshold,
            double pureThreshold,
            double dominanceRatio
    ) {
        validateSettings(
                establishedThreshold,
                pureThreshold,
                dominanceRatio
        );

        List<RecognitionPath> rankedPaths =
                rankPaths(
                        scores,
                        path -> true
                );

        RecognitionPath primaryPath =
                rankedPaths.get(0);

        RecognitionPath secondaryPath =
                rankedPaths.get(1);

        double primaryScore =
                getScore(
                        scores,
                        primaryPath
                );

        double secondaryScore =
                getScore(
                        scores,
                        secondaryPath
                );

        if (isPureResult(
                primaryScore,
                secondaryScore,
                pureThreshold,
                dominanceRatio
        )) {
            return Optional.of(
                    new RecognitionPathSelection(
                            primaryPath,
                            null,
                            true,
                            primaryScore,
                            secondaryScore
                    )
            );
        }

        return createEstablishedSelection(
                primaryPath,
                secondaryPath,
                primaryScore,
                secondaryScore,
                establishedThreshold
        );
    }

    /**
     * Former production selector.
     *
     * <p>Retained unchanged as a compatibility surface for integrations and
     * regression tests that still need the complete-path dominance model.</p>
     */
    public static Optional<RecognitionPathSelection>
    selectWithRawPureGate(
            Map<RecognitionPath, Double> finalScores,
            Map<RecognitionPath, Double> rawScores,
            double establishedThreshold,
            double pureThreshold,
            double finalDominanceRatio,
            double rawPureThreshold,
            double rawDominanceRatio
    ) {
        validateSettings(
                establishedThreshold,
                pureThreshold,
                finalDominanceRatio
        );

        validateRawSettings(
                rawPureThreshold,
                rawDominanceRatio
        );

        List<RecognitionPath> rankedFinalPaths =
                rankPaths(
                        finalScores,
                        path -> true
                );

        RecognitionPath primaryPath =
                rankedFinalPaths.get(0);

        RecognitionPath secondaryPath =
                rankedFinalPaths.get(1);

        double primaryFinalScore =
                getScore(
                        finalScores,
                        primaryPath
                );

        double secondaryFinalScore =
                getScore(
                        finalScores,
                        secondaryPath
                );

        double primaryRawScore =
                getScore(
                        rawScores,
                        primaryPath
                );

        double strongestCompetingRawScore =
                getStrongestCompetingScore(
                        rawScores,
                        primaryPath
                );

        boolean finalPure =
                isPureResult(
                        primaryFinalScore,
                        secondaryFinalScore,
                        pureThreshold,
                        finalDominanceRatio
                );

        boolean rawPure =
                isRawPureResult(
                        primaryRawScore,
                        strongestCompetingRawScore,
                        rawPureThreshold,
                        rawDominanceRatio
                );

        if (finalPure && rawPure) {
            return Optional.of(
                    new RecognitionPathSelection(
                            primaryPath,
                            null,
                            true,
                            primaryFinalScore,
                            secondaryFinalScore
                    )
            );
        }

        return createEstablishedSelection(
                primaryPath,
                secondaryPath,
                primaryFinalScore,
                secondaryFinalScore,
                establishedThreshold
        );
    }

    /**
     * Production selector for recognition rules version 2.
     *
     * <p>The highest component-established path remains the primary candidate.
     * Pure recognition then requires:</p>
     *
     * <ol>
     *     <li>The normal final path threshold.</li>
     *     <li>The normal raw path threshold.</li>
     *     <li>Meaningful evidence on both of the path's defining axes.</li>
     *     <li>Morality dominance against the other morality components.</li>
     *     <li>Temperament dominance against the other temperament
     *     components.</li>
     * </ol>
     *
     * <p>Complete-path dominance is deliberately not required here. Adjacent
     * paths share one axis by design, so comparing complete path totals made
     * Neutral Good and Neutral Evil disproportionately difficult.</p>
     */
    public static Optional<RecognitionPathSelection>
    selectWithComponentPureGate(
            Map<RecognitionPath, Double> finalScores,
            Map<RecognitionPath, Double> rawScores,
            RecognitionPathComponents components,
            RecognitionBalanceSnapshot.Selection selection
    ) {
        RecognitionBalanceSnapshot.Selection safeSelection =
                selection == null
                        ? RecognitionBalanceSnapshot
                        .createDefaults()
                        .selection()
                        : selection;

        validateSettings(
                safeSelection.establishedThreshold(),
                safeSelection.pureThreshold(),
                safeSelection.dominanceRatio()
        );

        validateRawSettings(
                safeSelection.rawPureThreshold(),
                safeSelection.rawDominanceRatio()
        );

        RecognitionPathComponents safeComponents =
                components == null
                        ? RecognitionPathComponents.empty()
                        : components;

        RecognitionComponentQualificationRules componentRules =
                RecognitionComponentQualificationRules
                        .from(
                                safeSelection
                        );

        Predicate<RecognitionPath> establishedEvidence =
                path -> safeComponents
                        .hasEstablishedEvidence(
                                path,
                                componentRules
                        );

        List<RecognitionPath> rankedEligiblePaths =
                rankPaths(
                        finalScores,
                        establishedEvidence
                );

        if (rankedEligiblePaths.isEmpty()) {
            return Optional.empty();
        }

        RecognitionPath primaryPath =
                rankedEligiblePaths.get(0);

        double primaryFinalScore =
                getScore(
                        finalScores,
                        primaryPath
                );

        double primaryRawScore =
                getScore(
                        rawScores,
                        primaryPath
                );

        RecognitionPath secondaryPath =
                rankedEligiblePaths.size() > 1
                        ? rankedEligiblePaths.get(1)
                        : null;

        double secondaryFinalScore =
                getScore(
                        finalScores,
                        secondaryPath
                );

        boolean pathThresholdsMet =
                primaryFinalScore
                        >= safeSelection.pureThreshold()
                        && primaryRawScore
                        >= safeSelection.rawPureThreshold();

        boolean componentPurityMet =
                safeComponents.hasPureEvidence(
                        primaryPath,
                        componentRules
                );

        if (pathThresholdsMet
                && componentPurityMet) {

            return Optional.of(
                    new RecognitionPathSelection(
                            primaryPath,
                            null,
                            true,
                            primaryFinalScore,
                            secondaryFinalScore
                    )
            );
        }

        if (secondaryPath == null) {
            return Optional.empty();
        }

        return createEstablishedSelection(
                primaryPath,
                secondaryPath,
                primaryFinalScore,
                secondaryFinalScore,
                safeSelection.establishedThreshold()
        );
    }

    private static Optional<RecognitionPathSelection>
    createEstablishedSelection(
            RecognitionPath primaryPath,
            RecognitionPath secondaryPath,
            double primaryScore,
            double secondaryScore,
            double establishedThreshold
    ) {
        if (primaryPath == null
                || secondaryPath == null
                || primaryScore < establishedThreshold
                || secondaryScore < establishedThreshold) {

            return Optional.empty();
        }

        return Optional.of(
                new RecognitionPathSelection(
                        primaryPath,
                        secondaryPath,
                        false,
                        primaryScore,
                        secondaryScore
                )
        );
    }

    private static boolean isPureResult(
            double primaryScore,
            double secondaryScore,
            double pureThreshold,
            double dominanceRatio
    ) {
        if (primaryScore < pureThreshold) {
            return false;
        }

        if (secondaryScore <= 0.0D) {
            return primaryScore > 0.0D;
        }

        return primaryScore
                >= secondaryScore
                * dominanceRatio;
    }

    private static boolean isRawPureResult(
            double primaryRawScore,
            double competingRawScore,
            double rawPureThreshold,
            double rawDominanceRatio
    ) {
        if (primaryRawScore < rawPureThreshold) {
            return false;
        }

        if (competingRawScore <= 0.0D) {
            return primaryRawScore > 0.0D;
        }

        return primaryRawScore
                >= competingRawScore
                * rawDominanceRatio;
    }

    private static double getStrongestCompetingScore(
            Map<RecognitionPath, Double> scores,
            RecognitionPath primaryPath
    ) {
        double strongestScore = 0.0D;

        for (RecognitionPath path :
                RecognitionPath.values()) {

            if (path == primaryPath) {
                continue;
            }

            strongestScore =
                    Math.max(
                            strongestScore,
                            getScore(
                                    scores,
                                    path
                            )
                    );
        }

        return strongestScore;
    }

    private static List<RecognitionPath> rankPaths(
            Map<RecognitionPath, Double> scores,
            Predicate<RecognitionPath> eligibility
    ) {
        Predicate<RecognitionPath> safeEligibility =
                eligibility == null
                        ? path -> true
                        : eligibility;

        List<RecognitionPath> rankedPaths =
                new ArrayList<>();

        for (RecognitionPath path :
                RecognitionPath.values()) {

            if (safeEligibility.test(path)) {
                rankedPaths.add(path);
            }
        }

        rankedPaths.sort((first, second) -> {
            double firstScore =
                    getScore(
                            scores,
                            first
                    );

            double secondScore =
                    getScore(
                            scores,
                            second
                    );

            int scoreComparison =
                    Double.compare(
                            secondScore,
                            firstScore
                    );

            if (scoreComparison != 0) {
                return scoreComparison;
            }

            return Integer.compare(
                    first.ordinal(),
                    second.ordinal()
            );
        });

        return rankedPaths;
    }

    private static double getScore(
            Map<RecognitionPath, Double> scores,
            RecognitionPath path
    ) {
        if (scores == null
                || path == null) {

            return 0.0D;
        }

        Double score =
                scores.get(path);

        if (score == null
                || !Double.isFinite(score)
                || score < 0.0D) {

            return 0.0D;
        }

        return score;
    }

    private static void validateRawSettings(
            double rawPureThreshold,
            double rawDominanceRatio
    ) {
        if (!Double.isFinite(rawPureThreshold)
                || rawPureThreshold < 0.0D) {

            throw new IllegalArgumentException(
                    "rawPureThreshold must be finite and non-negative."
            );
        }

        if (!Double.isFinite(rawDominanceRatio)
                || rawDominanceRatio < 1.0D) {

            throw new IllegalArgumentException(
                    "rawDominanceRatio must be finite and at least 1.0."
            );
        }
    }

    private static void validateSettings(
            double establishedThreshold,
            double pureThreshold,
            double dominanceRatio
    ) {
        if (!Double.isFinite(establishedThreshold)
                || establishedThreshold < 0.0D) {

            throw new IllegalArgumentException(
                    "establishedThreshold must be finite and non-negative."
            );
        }

        if (!Double.isFinite(pureThreshold)
                || pureThreshold < establishedThreshold) {

            throw new IllegalArgumentException(
                    "pureThreshold must be finite and at least the established threshold."
            );
        }

        if (!Double.isFinite(dominanceRatio)
                || dominanceRatio < 1.0D) {

            throw new IllegalArgumentException(
                    "dominanceRatio must be finite and at least 1.0."
            );
        }
    }
}