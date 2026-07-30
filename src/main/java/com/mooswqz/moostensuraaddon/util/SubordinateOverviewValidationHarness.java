package com.mooswqz.moostensuraaddon.util;

import java.util.ArrayList;
import java.util.List;

public final class SubordinateOverviewValidationHarness {

    private SubordinateOverviewValidationHarness() {
    }

    public static ValidationResult run() {
        List<String> failures = new ArrayList<>();
        int checks = 0;

        checks++;
        expect(
                SubordinateOverviewPolicy.NEARBY_RADIUS == 64.0D,
                "The overview radius must remain 64 blocks.",
                failures
        );

        checks++;
        expect(
                SubordinateOverviewPolicy.MAX_TARGETS_PER_PAYLOAD > 10,
                "The old ten-target preview cap must be removed.",
                failures
        );

        checks++;
        expect(
                SubordinateOverviewPolicy.AUTO_REFRESH_TICKS >= 20,
                "Automatic refresh must not run more than once per second.",
                failures
        );

        checks++;
        expect(
                SubordinateOverviewPolicy.matchesTarget(
                        "carr",
                        "Carrion",
                        "Beastman",
                        List.of("extra skill analytical appraisal")
                ),
                "Name search failed.",
                failures
        );

        checks++;
        expect(
                SubordinateOverviewPolicy.matchesTarget(
                        "beast",
                        "Carrion",
                        "Beastman",
                        List.of()
                ),
                "Type search failed.",
                failures
        );

        checks++;
        expect(
                SubordinateOverviewPolicy.matchesTarget(
                        "analytical",
                        "Carrion",
                        "Beastman",
                        List.of("extra skill analytical appraisal")
                ),
                "Skill search failed.",
                failures
        );

        checks++;
        expect(
                !SubordinateOverviewPolicy.matchesTarget(
                        "missing",
                        "Carrion",
                        "Beastman",
                        List.of("analytical appraisal")
                ),
                "Unrelated search unexpectedly matched.",
                failures
        );

        List<SubordinateOverviewPolicy.Candidate> candidates =
                new ArrayList<>(List.of(
                        new SubordinateOverviewPolicy.Candidate(
                                "b",
                                "Zalario",
                                25.0D
                        ),
                        new SubordinateOverviewPolicy.Candidate(
                                "c",
                                "Albis",
                                25.0D
                        ),
                        new SubordinateOverviewPolicy.Candidate(
                                "a",
                                "Carrion",
                                9.0D
                        )
                ));
        candidates.sort(SubordinateOverviewPolicy.candidateOrder());

        checks++;
        expect(
                candidates.getFirst().uuid().equals("a"),
                "Distance must be the primary sort key.",
                failures
        );

        checks++;
        expect(
                candidates.get(1).uuid().equals("c"),
                "Name must break equal-distance ties.",
                failures
        );

        checks++;
        expect(
                SubordinateOverviewPolicy.normalizeQuery("  Skill  ")
                        .equals("skill"),
                "Query normalization failed.",
                failures
        );

        return new ValidationResult(
                checks,
                checks - failures.size(),
                List.copyOf(failures)
        );
    }

    public static void main(String[] args) {
        ValidationResult result = run();
        System.out.println(
                "Subordinate overview validation: "
                        + result.passed()
                        + " / "
                        + result.checks()
        );

        for (String failure : result.failures()) {
            System.out.println("FAIL: " + failure);
        }

        if (!result.failures().isEmpty()) {
            throw new IllegalStateException(
                    "Subordinate overview validation failed."
            );
        }
    }

    private static void expect(
            boolean condition,
            String failure,
            List<String> failures
    ) {
        if (!condition) {
            failures.add(failure);
        }
    }

    public record ValidationResult(
            int checks,
            int passed,
            List<String> failures
    ) {
    }
}