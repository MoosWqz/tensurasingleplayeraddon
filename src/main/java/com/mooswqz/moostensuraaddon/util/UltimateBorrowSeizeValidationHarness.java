package com.mooswqz.moostensuraaddon.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UltimateBorrowSeizeValidationHarness {

    private UltimateBorrowSeizeValidationHarness() {
    }

    public static ValidationResult validate() {
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        check(
                "empty request",
                UltimateBorrowSeizePolicy.analyseRequest(
                        List.of()
                ).uniqueSkillIds().isEmpty(),
                passed,
                failed
        );
        check(
                "duplicates removed",
                UltimateBorrowSeizePolicy.analyseRequest(
                        List.of("a:b", "a:b", "c:d")
                ).uniqueSkillIds().equals(
                        List.of("a:b", "c:d")
                ),
                passed,
                failed
        );
        check(
                "blank rejected",
                UltimateBorrowSeizePolicy.analyseRequest(
                        List.of("a:b", " ", "c:d")
                ).rejectedCount() == 1,
                passed,
                failed
        );

        List<String> overLimit = new ArrayList<>();

        for (int index = 0;
             index < UltimateBorrowSeizePolicy.MAX_SELECTED_SKILLS + 1;
             index++) {
            overLimit.add("test:skill_" + index);
        }

        check(
                "selection limit",
                UltimateBorrowSeizePolicy.analyseRequest(
                        overLimit
                ).overLimit(),
                passed,
                failed
        );
        check(
                "total cost",
                UltimateBorrowSeizePolicy.calculateTotalCost(
                        150_000.0D,
                        4
                ) == 600_000.0D,
                passed,
                failed
        );
        check(
                "negative cost sanitised",
                UltimateBorrowSeizePolicy.calculateTotalCost(
                        -10.0D,
                        4
                ) == 0.0D,
                passed,
                failed
        );
        check(
                "seize risk scales",
                UltimateBorrowSeizePolicy.calculateSeizeDeathChance(
                        0.05D,
                        0.50D,
                        4
                ) == 0.20D,
                passed,
                failed
        );
        check(
                "seize risk capped",
                UltimateBorrowSeizePolicy.calculateSeizeDeathChance(
                        0.20D,
                        0.50D,
                        4
                ) == 0.50D,
                passed,
                failed
        );

        Map<String, Double> chances = new LinkedHashMap<>();
        chances.put("a:b", 0.05D);
        chances.put("c:d", 0.25D);

        check(
                "highest borrow chance",
                UltimateBorrowSeizePolicy.highestBorrowChance(
                        List.of("a:b", "c:d"),
                        chances
                ) == 0.25D,
                passed,
                failed
        );
        check(
                "chance clamped",
                UltimateBorrowSeizePolicy.highestBorrowChance(
                        List.of("broken:chance"),
                        Map.of("broken:chance", 4.0D)
                ) == 1.0D,
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