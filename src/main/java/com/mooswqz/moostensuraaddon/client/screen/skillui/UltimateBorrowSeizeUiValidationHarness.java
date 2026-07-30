package com.mooswqz.moostensuraaddon.client.screen.skillui;

import com.mooswqz.moostensuraaddon.network.OpenUltimateSubordinateSkillScreenPayload;

import java.util.ArrayList;
import java.util.List;

public final class UltimateBorrowSeizeUiValidationHarness {

    private UltimateBorrowSeizeUiValidationHarness() {
    }

    public static ValidationResult validate() {
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        OpenUltimateSubordinateSkillScreenPayload borrowPayload =
                new OpenUltimateSubordinateSkillScreenPayload(
                        false,
                        "00000000-0000-0000-0000-000000000001",
                        "Ranga",
                        600_000.0D,
                        150_000.0D,
                        0.05D,
                        0.50D,
                        List.of(
                                new OpenUltimateSubordinateSkillScreenPayload.SkillEntry(
                                        "tensura:great_sage",
                                        "Great Sage",
                                        "UNIQUE",
                                        true,
                                        0.20D
                                ),
                                new OpenUltimateSubordinateSkillScreenPayload.SkillEntry(
                                        "tensura:all_seeing_eye",
                                        "All-seeing Eye",
                                        "EXTRA",
                                        false,
                                        0.10D
                                )
                        )
                );
        UltimateBorrowSeizeUiEntryFactory.BuildResult borrow =
                UltimateBorrowSeizeUiEntryFactory.build(
                        borrowPayload
                );

        check(
                "available magicules retained",
                borrowPayload.availableMagicules() == 600_000.0D,
                passed,
                failed
        );

        check(
                "borrow entries retained",
                borrow.entries().size() == 2,
                passed,
                failed
        );
        check(
                "unique category mapped",
                borrow.entries().getFirst().category()
                        == SkillUiCategory.UNIQUE,
                passed,
                failed
        );
        check(
                "mastery retained",
                borrow.entries().getFirst().mastered(),
                passed,
                failed
        );
        check(
                "borrow chance retained",
                borrow.borrowChances().get(
                        "tensura:great_sage"
                ) == 0.20D,
                passed,
                failed
        );
        check(
                "borrow target remains owner",
                borrow.entries().getFirst()
                        .detailLines()
                        .stream()
                        .anyMatch(line -> line.getString()
                                .contains("keeps this skill")),
                passed,
                failed
        );

        OpenUltimateSubordinateSkillScreenPayload seizePayload =
                new OpenUltimateSubordinateSkillScreenPayload(
                        true,
                        "00000000-0000-0000-0000-000000000001",
                        "Ranga",
                        600_000.0D,
                        250_000.0D,
                        0.05D,
                        0.50D,
                        borrowPayload.skills()
                );
        UltimateBorrowSeizeUiEntryFactory.BuildResult seize =
                UltimateBorrowSeizeUiEntryFactory.build(
                        seizePayload
                );

        check(
                "seize chance map harmless",
                seize.borrowChances().size() == 2,
                passed,
                failed
        );
        check(
                "seize removal warning",
                seize.entries().getFirst()
                        .detailLines()
                        .stream()
                        .anyMatch(line -> line.getString()
                                .contains("permanently removed")),
                passed,
                failed
        );
        check(
                "source subordinate retained",
                seize.entries().getFirst()
                        .sourceName()
                        .getString()
                        .equals("Ranga"),
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