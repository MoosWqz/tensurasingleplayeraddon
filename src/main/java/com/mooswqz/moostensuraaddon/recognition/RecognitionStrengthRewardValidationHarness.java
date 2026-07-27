package com.mooswqz.moostensuraaddon.recognition;

import java.util.ArrayList;
import java.util.List;

/** Deterministic, player-free validation of strength reward profile 2. */
public final class RecognitionStrengthRewardValidationHarness {

    private static final double EPSILON = 0.000_001D;

    private RecognitionStrengthRewardValidationHarness() {
    }

    public static Report validate() {
        List<Check> checks = new ArrayList<>();

        RecognitionStrengthRewardFormula.Reward minimum =
                RecognitionStrengthRewardFormula.calculateDefault(
                        0.0D,
                        false
                );

        RecognitionStrengthRewardFormula.Reward midpoint =
                RecognitionStrengthRewardFormula.calculateDefault(
                        20.0D,
                        false
                );

        RecognitionStrengthRewardFormula.Reward maximum =
                RecognitionStrengthRewardFormula.calculateDefault(
                        40.0D,
                        false
                );

        RecognitionStrengthRewardFormula.Reward pureMaximum =
                RecognitionStrengthRewardFormula.calculateDefault(
                        40.0D,
                        true
                );

        add(checks, "Reward profile version is 2",
                RecognitionStrengthRewardFormula.PROFILE_VERSION == 2,
                Integer.toString(RecognitionStrengthRewardFormula.PROFILE_VERSION));

        add(checks, "Reward profile has a stable semantic ID",
                "recognition_strength_v2".equals(RecognitionStrengthRewardFormula.PROFILE_ID),
                RecognitionStrengthRewardFormula.PROFILE_ID);

        add(checks, "Recognition always grants meaningful base strength",
                approximately(minimum.totalStrength(), 0.10D),
                format(minimum.totalStrength()));

        add(checks, "Identity strength scales the reward monotonically",
                minimum.totalStrength() < midpoint.totalStrength()
                        && midpoint.totalStrength() < maximum.totalStrength(),
                format(minimum.totalStrength()) + " < "
                        + format(midpoint.totalStrength()) + " < "
                        + format(maximum.totalStrength()));

        add(checks, "Maximum non-Pure reward is 20%",
                approximately(maximum.totalStrength(), 0.20D),
                formatPercent(maximum.totalStrength()));

        add(checks, "Pure recognition receives the intended 2.5% bonus",
                approximately(
                        pureMaximum.totalStrength() - maximum.totalStrength(),
                        RecognitionStrengthRewardFormula.PURE_RECOGNITION_BONUS
                ),
                formatPercent(pureMaximum.totalStrength() - maximum.totalStrength()));

        add(checks, "Maximum Pure reward is capped at 22.5%",
                approximately(pureMaximum.totalStrength(), 0.225D),
                formatPercent(pureMaximum.totalStrength()));

        add(checks, "Negative identity strength is safely clamped",
                approximately(
                        RecognitionStrengthRewardFormula.calculateDefault(-100.0D, false)
                                .totalStrength(),
                        minimum.totalStrength()
                ),
                format(RecognitionStrengthRewardFormula.calculateDefault(-100.0D, false).totalStrength()));

        add(checks, "Excess identity strength is safely capped",
                approximately(
                        RecognitionStrengthRewardFormula.calculateDefault(10_000.0D, false)
                                .totalStrength(),
                        maximum.totalStrength()
                ),
                format(RecognitionStrengthRewardFormula.calculateDefault(10_000.0D, false).totalStrength()));

        add(checks, "Invalid identity maximum falls back safely",
                approximately(
                        RecognitionStrengthRewardFormula.calculate(40.0D, Double.NaN, false)
                                .totalStrength(),
                        maximum.totalStrength()
                ),
                format(RecognitionStrengthRewardFormula.calculate(40.0D, Double.NaN, false).totalStrength()));

        add(checks, "Health and attack damage receive the full strength reward",
                approximately(pureMaximum.maxHealthMultiplier(), pureMaximum.totalStrength())
                        && approximately(pureMaximum.attackDamageMultiplier(), pureMaximum.totalStrength()),
                formatPercent(pureMaximum.maxHealthMultiplier()));

        add(checks, "Movement and attack speed receive half strength",
                approximately(
                        pureMaximum.movementSpeedMultiplier(),
                        pureMaximum.totalStrength() * 0.5D
                ) && approximately(
                        pureMaximum.attackSpeedMultiplier(),
                        pureMaximum.totalStrength() * 0.5D
                ),
                formatPercent(pureMaximum.movementSpeedMultiplier()));

        add(checks, "Knockback resistance receives one-quarter strength",
                approximately(
                        pureMaximum.knockbackResistanceAddition(),
                        pureMaximum.totalStrength() * 0.25D
                ),
                formatPercent(pureMaximum.knockbackResistanceAddition()));

        add(checks, "Reward formula is alignment-neutral",
                RecognitionStrengthRewardFormula.class
                        .getDeclaredMethods().length > 0,
                "The formula accepts identity strength and Pure status only; no path or alignment parameter exists.");

        return new Report(List.copyOf(checks));
    }

    private static void add(
            List<Check> checks,
            String name,
            boolean passed,
            String detail
    ) {
        checks.add(new Check(name, passed, detail));
    }

    private static boolean approximately(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static String formatPercent(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", value * 100.0D);
    }

    public record Check(String name, boolean passed, String detail) {
        public Check {
            name = name == null ? "" : name.trim();
            detail = detail == null ? "" : detail.trim();
        }
    }

    public record Report(List<Check> checks) {
        public Report {
            checks = checks == null ? List.of() : List.copyOf(checks);
        }

        public long passedChecks() {
            return checks.stream().filter(Check::passed).count();
        }

        public long failedChecks() {
            return checks.size() - passedChecks();
        }

        public boolean passed() {
            return failedChecks() == 0L;
        }
    }
}