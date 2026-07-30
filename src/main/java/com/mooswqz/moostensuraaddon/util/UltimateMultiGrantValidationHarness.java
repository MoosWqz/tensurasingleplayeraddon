package com.mooswqz.moostensuraaddon.util;

import java.util.ArrayList;
import java.util.List;

public final class UltimateMultiGrantValidationHarness {

    private UltimateMultiGrantValidationHarness() {
    }

    public static ValidationResult validate() {
        List<String> issues = new ArrayList<>();
        int passed = 0;
        int total = 0;

        total++;
        UltimateMultiGrantPolicy.RequestAnalysis empty =
                UltimateMultiGrantPolicy.analyseRequest(
                        List.of()
                );
        if (empty.uniqueSkillIds().isEmpty()
                && empty.submittedCount() == 0
                && !empty.overLimit()) {
            passed++;
        } else {
            issues.add("Empty request was not handled safely.");
        }

        total++;
        UltimateMultiGrantPolicy.RequestAnalysis ordered =
                UltimateMultiGrantPolicy.analyseRequest(
                        List.of(
                                "mod:first",
                                "mod:second",
                                "mod:third"
                        )
                );
        if (ordered.uniqueSkillIds().equals(
                List.of(
                        "mod:first",
                        "mod:second",
                        "mod:third"
                )
        )) {
            passed++;
        } else {
            issues.add("Request order was not preserved.");
        }

        total++;
        UltimateMultiGrantPolicy.RequestAnalysis duplicate =
                UltimateMultiGrantPolicy.analyseRequest(
                        List.of(
                                "mod:first",
                                "mod:first",
                                "mod:second"
                        )
                );
        if (duplicate.uniqueSkillIds().equals(
                List.of(
                        "mod:first",
                        "mod:second"
                )
        )
                && duplicate.rejectedCount() == 1) {
            passed++;
        } else {
            issues.add("Duplicate skill IDs were not collapsed once.");
        }

        total++;
        UltimateMultiGrantPolicy.RequestAnalysis whitespace =
                UltimateMultiGrantPolicy.analyseRequest(
                        List.of(
                                "  mod:first  ",
                                " ",
                                "mod:second"
                        )
                );
        if (whitespace.uniqueSkillIds().equals(
                List.of(
                        "mod:first",
                        "mod:second"
                )
        )
                && whitespace.rejectedCount() == 1) {
            passed++;
        } else {
            issues.add("Whitespace IDs were not normalised safely.");
        }

        List<String> exactLimit = new ArrayList<>();

        for (int index = 0;
             index < UltimateMultiGrantPolicy.MAX_SELECTED_SKILLS;
             index++) {
            exactLimit.add("mod:skill_" + index);
        }

        total++;
        UltimateMultiGrantPolicy.RequestAnalysis atLimit =
                UltimateMultiGrantPolicy.analyseRequest(
                        exactLimit
                );
        if (!atLimit.overLimit()
                && atLimit.uniqueSkillIds().size()
                == UltimateMultiGrantPolicy.MAX_SELECTED_SKILLS) {
            passed++;
        } else {
            issues.add("The exact 32-skill limit was rejected.");
        }

        List<String> aboveLimit = new ArrayList<>(
                exactLimit
        );
        aboveLimit.add("mod:skill_over_limit");

        total++;
        if (UltimateMultiGrantPolicy
                .analyseRequest(aboveLimit)
                .overLimit()) {
            passed++;
        } else {
            issues.add("A 33-skill request was not marked oversized.");
        }

        total++;
        double charge = UltimateMultiGrantPolicy.calculateCharge(
                List.of(
                        100.0D,
                        250.0D,
                        0.0D,
                        -50.0D,
                        Double.NaN
                )
        );
        if (Math.abs(charge - 350.0D) < 1.0E-9D) {
            passed++;
        } else {
            issues.add("Successful-cost charging was not sanitised.");
        }

        total++;
        if (UltimateMultiGrantPolicy.calculateCharge(null)
                == 0.0D) {
            passed++;
        } else {
            issues.add("Null cost input was not safe.");
        }

        total++;
        if (UltimateMultiGrantPolicy.MAX_SELECTED_SKILLS
                == com.mooswqz.moostensuraaddon.network
                .ExecuteUltimateMultiGrantPayload
                .MAX_SELECTED_SKILLS) {
            passed++;
        } else {
            issues.add("Network and server selection ceilings diverged.");
        }

        return new ValidationResult(
                passed,
                total,
                List.copyOf(issues)
        );
    }

    public record ValidationResult(
            int passed,
            int total,
            List<String> issues
    ) {

        public ValidationResult {
            issues = issues == null
                    ? List.of()
                    : List.copyOf(issues);
        }

        public boolean successful() {
            return passed == total
                    && issues.isEmpty();
        }
    }
}