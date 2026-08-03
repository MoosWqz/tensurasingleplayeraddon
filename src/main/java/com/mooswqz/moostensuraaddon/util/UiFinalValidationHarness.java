package com.mooswqz.moostensuraaddon.util;

import java.util.List;

public final class UiFinalValidationHarness {

    private UiFinalValidationHarness() {
    }

    public static Result runAll() {
        int passed = 0;
        int total = 0;

        total++;
        if (UiFinalPolicy.canonicalCategoryId("Unique Skills")
                .equals(UiFinalPolicy.CATEGORY_UNIQUE)) {
            passed++;
        }

        total++;
        if (UiFinalPolicy.canonicalCategoryId("ULTIMATE")
                .equals(UiFinalPolicy.CATEGORY_UNIQUE)) {
            passed++;
        }

        total++;
        if (UiFinalPolicy.canonicalCategoryId("Extra")
                .equals(UiFinalPolicy.CATEGORY_EXTRA)) {
            passed++;
        }

        total++;
        if (UiFinalPolicy.canonicalCategoryId("Common Skills")
                .equals(UiFinalPolicy.CATEGORY_BASIC)) {
            passed++;
        }

        total++;
        if (UiFinalPolicy.canonicalCategoryId("Resistances")
                .equals(UiFinalPolicy.CATEGORY_RESISTANCE)) {
            passed++;
        }

        total++;
        if (UiFinalPolicy.canonicalCategoryId("mystery")
                .equals(UiFinalPolicy.CATEGORY_OTHER)) {
            passed++;
        }

        total++;
        if (List.of(
                UiFinalPolicy.categoryOrder("unique"),
                UiFinalPolicy.categoryOrder("extra"),
                UiFinalPolicy.categoryOrder("basic"),
                UiFinalPolicy.categoryOrder("resistance"),
                UiFinalPolicy.categoryOrder("other")
        ).equals(List.of(0, 1, 2, 3, 4))) {
            passed++;
        }

        total++;
        if (UiFinalPolicy.shouldShowOnboarding(0, 1)) {
            passed++;
        }

        total++;
        if (!UiFinalPolicy.shouldShowOnboarding(1, 1)) {
            passed++;
        }

        total++;
        if (UiFinalPolicy.shouldShowOnboarding(1, 2)) {
            passed++;
        }

        return new Result(passed, total);
    }

    public record Result(
            int passed,
            int total
    ) {
        public boolean successful() {
            return passed == total;
        }
    }
}