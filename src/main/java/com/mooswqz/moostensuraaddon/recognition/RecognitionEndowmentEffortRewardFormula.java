package com.mooswqz.moostensuraaddon.recognition;

/**
 * Alignment-neutral extension to Tensura's native HIGH endowment ceiling.
 *
 * <p>The native 900% calculation and its 1,000,000 EP cap remain untouched.
 * Soul Recognition adds only a second, frozen capacity allowance derived from
 * the same Identity Strength snapshot as the permanent attribute reward.</p>
 */
public final class RecognitionEndowmentEffortRewardFormula {

    public static final double MAXIMUM_EXTRA_EP =
            1_000_000.0D;

    public static final double DEFAULT_IDENTITY_STRENGTH_MAXIMUM =
            40.0D;

    private RecognitionEndowmentEffortRewardFormula() {
    }

    public static Reward calculate(
            double identityStrength,
            double identityStrengthMaximum
    ) {
        double safeMaximum =
                sanitizeMaximum(
                        identityStrengthMaximum
                );

        double safeIdentity =
                clamp(
                        identityStrength,
                        0.0D,
                        safeMaximum
                );

        double identityRatio =
                safeMaximum <= 0.0D
                        ? 0.0D
                        : safeIdentity / safeMaximum;

        double extraEp =
                clamp(
                        identityRatio * MAXIMUM_EXTRA_EP,
                        0.0D,
                        MAXIMUM_EXTRA_EP
                );

        return new Reward(
                safeIdentity,
                safeMaximum,
                identityRatio,
                extraEp,
                extraEp / 2.0D
        );
    }

    public static Reward calculateDefault(
            double identityStrength
    ) {
        return calculate(
                identityStrength,
                DEFAULT_IDENTITY_STRENGTH_MAXIMUM
        );
    }

    private static double sanitizeMaximum(
            double value
    ) {
        return Double.isFinite(value)
                && value > 0.0D
                ? value
                : DEFAULT_IDENTITY_STRENGTH_MAXIMUM;
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        if (!Double.isFinite(value)) {
            return minimum;
        }

        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }

    public record Reward(
            double frozenIdentityStrength,
            double identityStrengthMaximum,
            double identityRatio,
            double extraEpAllowance,
            double energyIncreasePerPool
    ) {
        public Reward {
            frozenIdentityStrength =
                    sanitizeNonNegative(
                            frozenIdentityStrength
                    );

            identityStrengthMaximum =
                    sanitizeNonNegative(
                            identityStrengthMaximum
                    );

            identityRatio =
                    clamp(
                            identityRatio,
                            0.0D,
                            1.0D
                    );

            extraEpAllowance =
                    clamp(
                            extraEpAllowance,
                            0.0D,
                            MAXIMUM_EXTRA_EP
                    );

            energyIncreasePerPool =
                    clamp(
                            energyIncreasePerPool,
                            0.0D,
                            MAXIMUM_EXTRA_EP / 2.0D
                    );
        }

        private static double sanitizeNonNegative(
                double value
        ) {
            return Double.isFinite(value)
                    && value > 0.0D
                    ? value
                    : 0.0D;
        }
    }
}
