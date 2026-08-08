package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.recognition.RecognitionBalanceSnapshot;
import com.mooswqz.moostensuraaddon.recognition.RecognitionEvidenceBreakdown;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStatKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Focused policy validation for the read-only recognition evidence inspector.
 */
public final class RecognitionEvidenceValidationHarness {

    private static final double EPSILON = 0.000_001D;

    private RecognitionEvidenceValidationHarness() {
    }

    public static void main(String[] args) {
        List<Check> checks = new ArrayList<>();

        validateEmptyData(checks);
        validateMoralitySources(checks);
        validateCaps(checks);
        validateTemperamentSources(checks);
        validateMasteryAndIdentity(checks);
        validateNoMutation(checks);

        long passed = checks.stream()
                .filter(Check::passed)
                .count();

        System.out.println(
                "Recognition evidence breakdown validation: "
                        + passed
                        + " / "
                        + checks.size()
        );

        for (Check check : checks) {
            System.out.println(
                    (check.passed() ? "[PASS] " : "[FAIL] ")
                            + check.name()
                            + " — "
                            + check.detail()
            );
        }

        if (passed != checks.size()) {
            throw new IllegalStateException(
                    "Recognition evidence validation failed."
            );
        }
    }

    private static void validateEmptyData(
            List<Check> checks
    ) {
        RecognitionEvidenceBreakdown breakdown =
                RecognitionEvidenceBreakdown.calculate(
                        new RecognitionData(),
                        RecognitionBalanceSnapshot.createDefaults()
                );

        add(
                checks,
                "Empty data has zero Good",
                approximately(
                        breakdown.good().total(),
                        0.0D
                ),
                Double.toString(
                        breakdown.good().total()
                )
        );

        add(
                checks,
                "Empty data has zero Evil",
                approximately(
                        breakdown.evil().total(),
                        0.0D
                ),
                Double.toString(
                        breakdown.evil().total()
                )
        );

        add(
                checks,
                "Empty data has zero Identity Strength",
                approximately(
                        breakdown.identityStrength().total(),
                        0.0D
                ),
                Double.toString(
                        breakdown.identityStrength().total()
                )
        );
    }

    private static void validateMoralitySources(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.setFlag(
                RecognitionStatKeys.TRUE_HERO,
                true
        );
        data.setCounter(
                RecognitionStatKeys.RAID_VICTORIES,
                2
        );
        data.setCounter(
                RecognitionStatKeys.VILLAGERS_CURED,
                2
        );
        data.setCounter(
                RecognitionStatKeys.CIVILIANS_DEFENDED,
                3
        );
        data.addUniqueValue(
                RecognitionStatKeys
                        .MALEVOLENT_BOSS_TYPES_DEFEATED,
                "test:boss_a"
        );
        data.addUniqueValue(
                RecognitionStatKeys
                        .MALEVOLENT_BOSS_TYPES_DEFEATED,
                "test:boss_b"
        );

        data.setFlag(
                RecognitionStatKeys.TRUE_DEMON_LORD,
                true
        );
        data.setCounter(
                RecognitionStatKeys.CIVILIAN_KILLS,
                2
        );
        data.setCounter(
                RecognitionStatKeys.PASSIVE_BABY_KILLS,
                3
        );
        data.setCounter(
                RecognitionStatKeys.OWNED_COMPANION_KILLS,
                1
        );
        data.setCounter(
                RecognitionStatKeys.OWNED_SUBORDINATE_KILLS,
                1
        );
        data.addUniqueValue(
                RecognitionStatKeys
                        .BENEVOLENT_BOSS_TYPES_KILLED,
                "test:benevolent"
        );

        RecognitionEvidenceBreakdown breakdown =
                RecognitionEvidenceBreakdown.calculate(
                        data,
                        RecognitionBalanceSnapshot.createDefaults()
                );

        add(
                checks,
                "Good sources use default balance exactly",
                approximately(
                        breakdown.good().total(),
                        58.0D
                ),
                Double.toString(
                        breakdown.good().total()
                )
        );

        add(
                checks,
                "Evil sources use default balance exactly",
                approximately(
                        breakdown.evil().total(),
                        54.0D
                ),
                Double.toString(
                        breakdown.evil().total()
                )
        );
    }

    private static void validateCaps(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.setCounter(
                RecognitionStatKeys.CIVILIAN_KILLS,
                1_000
        );
        data.setCounter(
                RecognitionStatKeys.RAID_VICTORIES,
                1_000
        );

        RecognitionEvidenceBreakdown breakdown =
                RecognitionEvidenceBreakdown.calculate(
                        data,
                        RecognitionBalanceSnapshot.createDefaults()
                );

        add(
                checks,
                "Civilian-kill contribution respects its cap",
                approximately(
                        breakdown.evil().entries().get(1)
                                .contribution(),
                        30.0D
                ),
                Double.toString(
                        breakdown.evil().entries().get(1)
                                .contribution()
                )
        );

        add(
                checks,
                "Raid-victory contribution respects its cap",
                approximately(
                        breakdown.good().entries().get(1)
                                .contribution(),
                        24.0D
                ),
                Double.toString(
                        breakdown.good().entries().get(1)
                                .contribution()
                )
        );
    }

    private static void validateTemperamentSources(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.setCounter(
                RecognitionStatKeys.CURRENT_SUBORDINATES,
                2
        );
        data.setCounter(
                RecognitionStatKeys.HIGHEST_SUBORDINATES,
                5
        );
        data.setCounter(
                RecognitionStatKeys
                        .SUBORDINATE_ASSISTED_MAJOR_VICTORIES,
                2
        );
        data.addUniqueValue(
                RecognitionStatKeys
                        .UNIQUE_SUBORDINATES_EMPOWERED,
                "subordinate-a"
        );
        data.setCounter(
                RecognitionStatKeys.MASS_GRANTS_PERFORMED,
                1
        );
        data.setCounter(
                RecognitionStatKeys
                        .GLOBAL_TAKE_BACKS_PERFORMED,
                1
        );

        data.addUniqueValue(
                RecognitionStatKeys
                        .SOLO_MAJOR_ENEMY_TYPES_DEFEATED,
                "test:major-a"
        );
        data.addUniqueValue(
                RecognitionStatKeys.DISCOVERY_MILESTONES,
                "test:discovery-a"
        );

        RecognitionEvidenceBreakdown breakdown =
                RecognitionEvidenceBreakdown.calculate(
                        data,
                        RecognitionBalanceSnapshot.createDefaults()
                );

        add(
                checks,
                "Order uses the highest recognized subordinate roster",
                breakdown.order().entries().get(0)
                        .rawValue()
                        .startsWith("5 "),
                breakdown.order().entries().get(0)
                        .rawValue()
        );

        add(
                checks,
                "Freedom combines solo victories and discovery",
                breakdown.freedom().total() > 0.0D,
                Double.toString(
                        breakdown.freedom().total()
                )
        );
    }

    private static void validateMasteryAndIdentity(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.setCounter(
                RecognitionStatKeys.MASTERED_SKILLS,
                20
        );
        data.setCounter(
                RecognitionStatKeys
                        .MASTERED_SKILL_CATEGORIES,
                4
        );
        data.setMeasurement(
                RecognitionStatKeys.HIGHEST_EP,
                5_000_000.0D
        );
        data.addUniqueValue(
                RecognitionStatKeys
                        .MAJOR_ENEMY_TYPES_DEFEATED,
                "test:major-a"
        );
        data.addUniqueValue(
                RecognitionStatKeys.DISCOVERY_MILESTONES,
                "test:discovery-a"
        );

        RecognitionEvidenceBreakdown breakdown =
                RecognitionEvidenceBreakdown.calculate(
                        data,
                        RecognitionBalanceSnapshot.createDefaults()
                );

        add(
                checks,
                "Mastery is positive for developed data",
                breakdown.mastery().total() > 0.0D,
                Double.toString(
                        breakdown.mastery().total()
                )
        );

        add(
                checks,
                "Identity Strength is derived and capped",
                breakdown.identityStrength().total() > 0.0D
                        && breakdown.identityStrength().total()
                        <= RecognitionBalanceSnapshot.createDefaults()
                        .identityStrength()
                        .maximum(),
                Double.toString(
                        breakdown.identityStrength().total()
                )
        );
    }

    private static void validateNoMutation(
            List<Check> checks
    ) {
        RecognitionData data = new RecognitionData();

        data.setCounter(
                RecognitionStatKeys.CIVILIAN_KILLS,
                2
        );

        int before = data.getCounter(
                RecognitionStatKeys.CIVILIAN_KILLS
        );

        RecognitionEvidenceBreakdown.calculate(
                data,
                RecognitionBalanceSnapshot.createDefaults()
        );

        int after = data.getCounter(
                RecognitionStatKeys.CIVILIAN_KILLS
        );

        add(
                checks,
                "Inspector calculation is read-only",
                before == after,
                before + " -> " + after
        );
    }

    private static void add(
            List<Check> checks,
            String name,
            boolean passed,
            String detail
    ) {
        checks.add(
                new Check(
                        name,
                        passed,
                        detail
                )
        );
    }

    private static boolean approximately(
            double first,
            double second
    ) {
        return Math.abs(first - second)
                <= EPSILON;
    }

    private record Check(
            String name,
            boolean passed,
            String detail
    ) {
    }
}
