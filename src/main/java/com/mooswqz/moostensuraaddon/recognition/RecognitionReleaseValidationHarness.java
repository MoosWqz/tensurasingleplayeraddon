package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Debug-only release audit for the recognition system.
 *
 * <p>The audit composes the permanent balance validator and Freedom validator
 * with the explicit 1.4.0 release policy. It uses temporary data only and
 * never mutates a player, world, commitment, datapack definition or cache
 * entry outside the validators' existing read-only caches.</p>
 */
public final class RecognitionReleaseValidationHarness {

    private static final double EPSILON =
            0.000_001D;

    private RecognitionReleaseValidationHarness() {
    }

    public static Report validate() {
        RecognitionBalanceManager.State balanceState =
                RecognitionBalanceManager.getState();

        RecognitionBalanceSnapshot balance =
                balanceState.snapshot();

        RecognitionBalanceValidationHarness.Report recognitionReport =
                RecognitionBalanceValidationHarness.validate();

        RecognitionFreedomValidationHarness.Report freedomReport =
                RecognitionFreedomValidationHarness.validate();

        RecognitionStrengthRewardValidationHarness.Report strengthReport =
                RecognitionStrengthRewardValidationHarness.validate();

        List<Check> checks =
                new ArrayList<>();

        List<String> warnings =
                new ArrayList<>();

        checks.add(
                check(
                        "Release policy identifier",
                        RecognitionReleasePolicy.POLICY_VERSION == 2
                                && RecognitionStrengthRewardFormula
                                .PROFILE_ID
                                .equals(
                                        RecognitionReleasePolicy.POLICY_ID
                                ),
                        "version "
                                + RecognitionReleasePolicy.POLICY_VERSION
                                + " / "
                                + RecognitionReleasePolicy.POLICY_ID
                )
        );

        checks.add(
                check(
                        "Explicit permanent strength reward policy",
                        RecognitionReleasePolicy
                                .grantsAttributeModifiers()
                                && RecognitionReleasePolicy
                                .grantsCombatMultipliers()
                                && !RecognitionReleasePolicy
                                .grantsHistoryModifierPower(),
                        RecognitionReleasePolicy
                                .rewardProfileSummary()
                )
        );

        checks.add(
                check(
                        "Recognition result version",
                        RecognitionCommitRecord
                                .CURRENT_RESULT_VERSION
                                == RecognitionReleasePolicy
                                .EXPECTED_RESULT_VERSION,
                        RecognitionCommitRecord
                                .CURRENT_RESULT_VERSION
                                + " / expected "
                                + RecognitionReleasePolicy
                                .EXPECTED_RESULT_VERSION
                )
        );

        checks.add(
                check(
                        "Recognition rules version",
                        RecognitionCommitRecord
                                .CURRENT_RULES_VERSION
                                == RecognitionReleasePolicy
                                .EXPECTED_RULES_VERSION,
                        RecognitionCommitRecord
                                .CURRENT_RULES_VERSION
                                + " / expected "
                                + RecognitionReleasePolicy
                                .EXPECTED_RULES_VERSION
                )
        );

        checks.add(
                check(
                        "Reward profile version",
                        RecognitionCommitRecord
                                .CURRENT_REWARD_PROFILE_VERSION
                                == RecognitionReleasePolicy
                                .EXPECTED_REWARD_PROFILE_VERSION,
                        RecognitionCommitRecord
                                .CURRENT_REWARD_PROFILE_VERSION
                                + " / expected "
                                + RecognitionReleasePolicy
                                .EXPECTED_REWARD_PROFILE_VERSION
                )
        );

        checks.add(
                check(
                        "Recognition strength formula validator",
                        strengthReport.passed(),
                        strengthReport.passedChecks()
                                + " passed / "
                                + strengthReport.failedChecks()
                                + " failed"
                )
        );

        checks.add(
                check(
                        "Recognition strength release caps",
                        approximatelyEqual(
                                RecognitionStrengthRewardFormula
                                        .maximumReward(false),
                                0.20D
                        )
                                && approximatelyEqual(
                                RecognitionStrengthRewardFormula
                                        .maximumReward(true),
                                0.225D
                        ),
                        "combined max "
                                + formatPercent(
                                RecognitionStrengthRewardFormula
                                        .maximumReward(false)
                        )
                                + " / Pure max "
                                + formatPercent(
                                RecognitionStrengthRewardFormula
                                        .maximumReward(true)
                        )
                )
        );

        checks.add(
                check(
                        "Awakening is evidence, not a hard gate",
                        RecognitionReleasePolicy
                                .awakeningActsAsDirectionalEvidence()
                                && !RecognitionReleasePolicy
                                .requiresTrueHeroForPureGood()
                                && !RecognitionReleasePolicy
                                .requiresTrueDemonLordForPureEvil()
                                && !RecognitionReleasePolicy
                                .requiresAwakeningForTrueNeutral(),
                        RecognitionReleasePolicy
                                .awakeningPolicySummary()
                )
        );

        double trueHeroModifier =
                balance.good()
                        .trueHeroModifier();

        double trueDemonLordModifier =
                balance.evil()
                        .trueDemonLordModifier();

        checks.add(
                check(
                        "TH/TDL directional modifiers are positive and symmetric",
                        trueHeroModifier > 0.0D
                                && trueDemonLordModifier > 0.0D
                                && approximatelyEqual(
                                trueHeroModifier,
                                trueDemonLordModifier
                        ),
                        "TH "
                                + format(trueHeroModifier)
                                + " / TDL "
                                + format(trueDemonLordModifier)
                )
        );

        RecognitionEvaluation emptyEvaluation =
                RecognitionPathEvaluator.evaluate(
                        new RecognitionData()
                );

        checks.add(
                check(
                        "Empty deed profile has no recognition selection",
                        emptyEvaluation.getSelection()
                                .isEmpty(),
                        emptyEvaluation.getSelection()
                                .map(selection ->
                                        selection.primaryPath()
                                                .getId()
                                )
                                .orElse("none")
                )
        );

        checks.add(
                check(
                        "All Pure paths remain reachable",
                        recognitionReport.exactPurePaths()
                                == RecognitionReleasePolicy
                                .EXPECTED_PURE_PATHS
                                && recognitionReport
                                .exactPurePaths()
                                == recognitionReport
                                .totalPurePaths(),
                        recognitionReport.exactPurePaths()
                                + " / "
                                + recognitionReport.totalPurePaths()
                )
        );

        checks.add(
                check(
                        "All required adjacent crossings remain reachable",
                        recognitionReport.exactAdjacentPairs()
                                == RecognitionReleasePolicy
                                .EXPECTED_REQUIRED_ADJACENT_CROSSES
                                && recognitionReport
                                .exactAdjacentPairs()
                                == recognitionReport
                                .totalAdjacentPairs(),
                        recognitionReport.exactAdjacentPairs()
                                + " / "
                                + recognitionReport.totalAdjacentPairs()
                )
        );

        PathCoverage coverage =
                pathCoverage(
                        recognitionReport
                );

        checks.add(
                check(
                        "Pure morality coverage",
                        coverage.goodPurePaths() == 3
                                && coverage.neutralPurePaths() == 3
                                && coverage.evilPurePaths() == 3,
                        "Good "
                                + coverage.goodPurePaths()
                                + "/3, Neutral "
                                + coverage.neutralPurePaths()
                                + "/3, Evil "
                                + coverage.evilPurePaths()
                                + "/3"
                )
        );

        checks.add(
                check(
                        "Pure results are not identity-heavy",
                        coverage.identityHeavyPurePaths() == 0,
                        coverage.identityHeavyPurePaths()
                                + " identity-heavy Pure result(s)"
                )
        );

        checks.add(
                check(
                        "Freedom expansion validator",
                        freedomReport.passed(),
                        freedomReport.passed()
                                ? "PASS"
                                : "FAIL"
                )
        );

        double freedomOrderRatio =
                ratio(
                        freedomReport.expandedFreedomCeiling(),
                        freedomReport.orderCeiling()
                );

        checks.add(
                check(
                        "Freedom and Order ceiling parity",
                        freedomOrderRatio
                                >= RecognitionReleasePolicy
                                .MIN_FREEDOM_ORDER_RATIO
                                && freedomOrderRatio
                                <= RecognitionReleasePolicy
                                .MAX_FREEDOM_ORDER_RATIO,
                        formatPercent(freedomOrderRatio)
                                + " within "
                                + formatPercent(
                                RecognitionReleasePolicy
                                        .MIN_FREEDOM_ORDER_RATIO
                        )
                                + "–"
                                + formatPercent(
                                RecognitionReleasePolicy
                                        .MAX_FREEDOM_ORDER_RATIO
                        )
                )
        );

        checks.add(
                check(
                        "Self-reliance contributes meaningful Freedom",
                        freedomReport.configuredMilestonePoints()
                                > 0.0D
                                && freedomReport
                                .expandedFreedomCeiling()
                                > freedomReport
                                .legacyFreedomCeiling(),
                        format(
                                freedomReport
                                        .configuredMilestonePoints()
                        )
                                + " configured points; ceiling "
                                + format(
                                freedomReport
                                        .legacyFreedomCeiling()
                        )
                                + " -> "
                                + format(
                                freedomReport
                                        .expandedFreedomCeiling()
                        )
                )
        );

        boolean canonicalDefaults =
                isCanonicalDefaultSource(
                        balance.sourceId()
                );

        addCanonicalBalanceChecks(
                checks,
                balance,
                canonicalDefaults
        );

        if (!canonicalDefaults) {
            warnings.add(
                    "A custom recognition-balance source is active. Reachability and safety were validated, but canonical 1.4.0 numeric defaults were not enforced."
            );
        }

        warnings.addAll(
                recognitionReport.warnings()
        );

        warnings.addAll(
                freedomReport.warnings()
        );

        int passedChecks =
                0;

        for (Check check : checks) {
            if (check.passed()) {
                passedChecks++;
            }
        }

        return new Report(
                passedChecks == checks.size(),
                RecognitionReleasePolicy.POLICY_VERSION,
                RecognitionReleasePolicy.POLICY_ID,
                RecognitionReleasePolicy.DISPLAY_NAME,
                balance.sourceId(),
                balanceState.revision(),
                canonicalDefaults,
                RecognitionCommitRecord
                        .CURRENT_RESULT_VERSION,
                RecognitionCommitRecord
                        .CURRENT_RULES_VERSION,
                RecognitionCommitRecord
                        .CURRENT_REWARD_PROFILE_VERSION,
                recognitionReport.exactPurePaths(),
                recognitionReport.totalPurePaths(),
                recognitionReport.exactAdjacentPairs(),
                recognitionReport.totalAdjacentPairs(),
                coverage.goodPurePaths(),
                coverage.neutralPurePaths(),
                coverage.evilPurePaths(),
                coverage.identityHeavyPurePaths(),
                freedomReport.legacyFreedomCeiling(),
                freedomReport.expandedFreedomCeiling(),
                freedomReport.orderCeiling(),
                freedomOrderRatio,
                freedomReport.configuredMilestonePoints(),
                passedChecks,
                checks.size() - passedChecks,
                List.copyOf(checks),
                List.copyOf(warnings)
        );
    }

    private static void addCanonicalBalanceChecks(
            List<Check> checks,
            RecognitionBalanceSnapshot balance,
            boolean canonicalDefaults
    ) {
        RecognitionBalanceSnapshot.Selection selection =
                balance.selection();

        RecognitionBalanceSnapshot.IdentityDistribution identity =
                balance.identityDistribution();

        checks.add(
                policyOrDefaultCheck(
                        "Established threshold",
                        canonicalDefaults,
                        selection.establishedThreshold(),
                        35.0D
                )
        );

        checks.add(
                policyOrDefaultCheck(
                        "Pure threshold",
                        canonicalDefaults,
                        selection.pureThreshold(),
                        70.0D
                )
        );

        checks.add(
                policyOrDefaultCheck(
                        "Final dominance ratio",
                        canonicalDefaults,
                        selection.dominanceRatio(),
                        2.0D
                )
        );

        checks.add(
                policyOrDefaultCheck(
                        "Raw Pure threshold",
                        canonicalDefaults,
                        selection.rawPureThreshold(),
                        35.0D
                )
        );

        checks.add(
                policyOrDefaultCheck(
                        "Raw dominance ratio",
                        canonicalDefaults,
                        selection.rawDominanceRatio(),
                        1.6D
                )
        );

        checks.add(
                policyOrDefaultCheck(
                        "Directional morality minimum",
                        canonicalDefaults,
                        selection.minimumDirectionalMoralityEvidence(),
                        6.0D
                )
        );

        checks.add(
                policyOrDefaultCheck(
                        "Universal identity share",
                        canonicalDefaults,
                        identity.universalShare(),
                        0.15D
                )
        );

        checks.add(
                policyOrDefaultCheck(
                        "Focused identity share",
                        canonicalDefaults,
                        identity.focusedShare(),
                        0.60D
                )
        );

        checks.add(
                policyOrDefaultCheck(
                        "True Hero modifier",
                        canonicalDefaults,
                        balance.good()
                                .trueHeroModifier(),
                        15.0D
                )
        );

        checks.add(
                policyOrDefaultCheck(
                        "True Demon Lord modifier",
                        canonicalDefaults,
                        balance.evil()
                                .trueDemonLordModifier(),
                        15.0D
                )
        );
    }

    private static Check policyOrDefaultCheck(
            String name,
            boolean canonicalDefaults,
            double actual,
            double expected
    ) {
        if (!canonicalDefaults) {
            return check(
                    name,
                    true,
                    "custom datapack value "
                            + format(actual)
                            + "; canonical default check skipped"
            );
        }

        return check(
                name,
                approximatelyEqual(
                        actual,
                        expected
                ),
                format(actual)
                        + " / expected "
                        + format(expected)
        );
    }

    private static PathCoverage pathCoverage(
            RecognitionBalanceValidationHarness.Report report
    ) {
        int good = 0;
        int neutral = 0;
        int evil = 0;
        int identityHeavy = 0;

        for (RecognitionBalanceValidationHarness.PathResult result :
                report.paths()) {
            if (result == null
                    || result.path() == null
                    || result.result() == null
                    || !result.result().exact()) {
                continue;
            }

            switch (result.path().getMorality()) {
                case GOOD -> good++;
                case NEUTRAL -> neutral++;
                case EVIL -> evil++;
            }

            if (result.identityHeavy()) {
                identityHeavy++;
            }
        }

        return new PathCoverage(
                good,
                neutral,
                evil,
                identityHeavy
        );
    }

    private static boolean isCanonicalDefaultSource(
            String sourceId
    ) {
        if (sourceId == null) {
            return false;
        }

        String cleaned =
                sourceId.trim();

        return "moostensuraaddon:default".equals(cleaned)
                || "built-in defaults".equalsIgnoreCase(cleaned);
    }

    private static double ratio(
            double numerator,
            double denominator
    ) {
        if (!Double.isFinite(numerator)
                || !Double.isFinite(denominator)
                || numerator < 0.0D
                || denominator <= 0.0D) {
            return 0.0D;
        }

        return numerator / denominator;
    }

    private static boolean approximatelyEqual(
            double first,
            double second
    ) {
        return Double.isFinite(first)
                && Double.isFinite(second)
                && Math.abs(first - second)
                <= EPSILON;
    }

    private static Check check(
            String name,
            boolean passed,
            String detail
    ) {
        return new Check(
                name,
                passed,
                detail
        );
    }

    private static String format(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.2f",
                Double.isFinite(value)
                        ? value
                        : 0.0D
        );
    }

    private static String formatPercent(
            double ratio
    ) {
        return String.format(
                Locale.US,
                "%.1f%%",
                Double.isFinite(ratio)
                        ? ratio * 100.0D
                        : 0.0D
        );
    }

    public record Check(
            String name,
            boolean passed,
            String detail
    ) {

        public Check {
            name = name == null || name.isBlank()
                    ? "Unnamed check"
                    : name.trim();

            detail = detail == null
                    ? ""
                    : detail.trim();
        }
    }

    public record Report(
            boolean passed,
            int policyVersion,
            String policyId,
            String policyDisplayName,
            String balanceSource,
            long balanceRevision,
            boolean canonicalDefaults,
            int resultVersion,
            int rulesVersion,
            int rewardProfileVersion,
            int purePaths,
            int totalPurePaths,
            int requiredAdjacentCrosses,
            int totalRequiredAdjacentCrosses,
            int goodPurePaths,
            int neutralPurePaths,
            int evilPurePaths,
            int identityHeavyPurePaths,
            double legacyFreedomCeiling,
            double expandedFreedomCeiling,
            double orderCeiling,
            double freedomOrderRatio,
            double configuredSelfReliancePoints,
            int passedChecks,
            int failedChecks,
            List<Check> checks,
            List<String> warnings
    ) {

        public Report {
            policyId = policyId == null
                    ? ""
                    : policyId.trim();

            policyDisplayName = policyDisplayName == null
                    ? ""
                    : policyDisplayName.trim();

            balanceSource = balanceSource == null
                    ? ""
                    : balanceSource.trim();

            checks = checks == null
                    ? List.of()
                    : List.copyOf(checks);

            warnings = warnings == null
                    ? List.of()
                    : List.copyOf(warnings);

            passedChecks = Math.max(
                    0,
                    passedChecks
            );

            failedChecks = Math.max(
                    0,
                    failedChecks
            );
        }
    }

    private record PathCoverage(
            int goodPurePaths,
            int neutralPurePaths,
            int evilPurePaths,
            int identityHeavyPurePaths
    ) {
    }
}