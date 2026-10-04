package com.mooswqz.moostensuraaddon.recognition;

/**
 * Alignment-neutral extension to Tensura's native HIGH endowment ceiling.
 *
 * <p>The native 900% calculation and its 1,000,000 EP cap remain untouched.
 * Soul Recognition adds only a second capacity allowance derived from the
 * same Identity Strength snapshot as the permanent attribute reward. Its
 * server-configured ceiling is frozen with the recognition commitment.</p>
 */
public final class RecognitionEndowmentEffortRewardFormula {

    public static final double DEFAULT_MAXIMUM_EXTRA_EP =
            1_000_000.0D;

    /** Compatibility alias for validation code and existing integrations. */
    public static final double MAXIMUM_EXTRA_EP =
            DEFAULT_MAXIMUM_EXTRA_EP;

    public static final double DEFAULT_IDENTITY_STRENGTH_MAXIMUM =
            40.0D;

    private RecognitionEndowmentEffortRewardFormula() {
    }

    public static Reward calculate(
            double identityStrength,
            double identityStrengthMaximum
    ) {
        return calculate(
                identityStrength,
                identityStrengthMaximum,
                DEFAULT_MAXIMUM_EXTRA_EP
        );
    }

    public static Reward calculate(
            double identityStrength,
            double identityStrengthMaximum,
            double maximumExtraEp
    ) {
        double safeMaximum =
                sanitizeMaximum(
                        identityStrengthMaximum
                );

        double safeMaximumExtraEp =
                sanitizeMaximumExtraEp(
                        maximumExtraEp
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
                        identityRatio * safeMaximumExtraEp,
                        0.0D,
                        safeMaximumExtraEp
                );

        return new Reward(
                safeIdentity,
                safeMaximum,
                identityRatio,
                safeMaximumExtraEp,
                extraEp,
                extraEp / 2.0D
        );
    }

    public static Reward calculateDefault(
            double identityStrength
    ) {
        return calculate(
                identityStrength,
                DEFAULT_IDENTITY_STRENGTH_MAXIMUM,
                DEFAULT_MAXIMUM_EXTRA_EP
        );
    }

    public static double sanitizeMaximumExtraEp(
            double value
    ) {
        return Double.isFinite(value)
                && value > 0.0D
                ? value
                : 0.0D;
    }

    public static double resolveFrozenMaximumExtraEp(
            boolean snapshotInitialized,
            double storedMaximumExtraEp
    ) {
        return snapshotInitialized
                ? sanitizeMaximumExtraEp(
                        storedMaximumExtraEp
                )
                : DEFAULT_MAXIMUM_EXTRA_EP;
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
            double maximumExtraEp,
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

            maximumExtraEp =
                    sanitizeMaximumExtraEp(
                            maximumExtraEp
                    );

            extraEpAllowance =
                    clamp(
                            extraEpAllowance,
                            0.0D,
                            maximumExtraEp
                    );

            energyIncreasePerPool =
                    clamp(
                            energyIncreasePerPool,
                            0.0D,
                            maximumExtraEp / 2.0D
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
