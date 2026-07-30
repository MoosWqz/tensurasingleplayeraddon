package com.mooswqz.moostensuraaddon.client.screen.skillui;

import java.util.ArrayList;
import java.util.List;

public final class UltimateConfirmationValidationHarness {

    private UltimateConfirmationValidationHarness() {
    }

    public static ValidationResult validate() {
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        check(
                "borrow total cost",
                UltimateConfirmationPolicy.totalCost(
                        50_000.0D,
                        4
                ) == 200_000.0D,
                passed,
                failed
        );
        check(
                "negative costs sanitised",
                UltimateConfirmationPolicy.totalCost(
                        -1.0D,
                        4
                ) == 0.0D,
                passed,
                failed
        );
        check(
                "seize risk combines",
                UltimateConfirmationPolicy.combinedSeizeRisk(
                        0.05D,
                        0.50D,
                        4
                ) == 0.20D,
                passed,
                failed
        );
        check(
                "seize risk capped",
                UltimateConfirmationPolicy.combinedSeizeRisk(
                        0.20D,
                        0.50D,
                        4
                ) == 0.50D,
                passed,
                failed
        );
        check(
                "chance range minimum",
                UltimateConfirmationPolicy.chanceRange(
                        List.of(0.30D, 0.10D, 0.20D)
                ).minimum() == 0.10D,
                passed,
                failed
        );
        check(
                "chance range maximum",
                UltimateConfirmationPolicy.chanceRange(
                        List.of(0.30D, 0.10D, 0.20D)
                ).maximum() == 0.30D,
                passed,
                failed
        );
        check(
                "affordable equality",
                UltimateConfirmationPolicy.affordable(
                        200_000.0D,
                        200_000.0D
                ),
                passed,
                failed
        );
        check(
                "insufficient resources",
                !UltimateConfirmationPolicy.affordable(
                        199_999.0D,
                        200_000.0D
                ),
                passed,
                failed
        );
        check(
                "compact rows bounded",
                UltimateConfirmationPolicy.visibleSkillRows(
                        60,
                        true
                ) == 5,
                passed,
                failed
        );
        check(
                "normal rows bounded",
                UltimateConfirmationPolicy.visibleSkillRows(
                        500,
                        false
                ) == 10,
                passed,
                failed
        );
        check(
                "chance clamp lower",
                UltimateConfirmationPolicy.clampChance(-1.0D)
                        == 0.0D,
                passed,
                failed
        );
        check(
                "chance clamp upper",
                UltimateConfirmationPolicy.clampChance(2.0D)
                        == 1.0D,
                passed,
                failed
        );

        return new ValidationResult(
                passed.size(),
                failed.size(),
                List.copyOf(passed),
                List.copyOf(failed)
        );
    }

    private static void check(
            String name,
            boolean condition,
            List<String> passed,
            List<String> failed
    ) {
        (condition ? passed : failed).add(name);
    }

    public record ValidationResult(
            int passed,
            int failed,
            List<String> passedChecks,
            List<String> failedChecks
    ) {
    }
}