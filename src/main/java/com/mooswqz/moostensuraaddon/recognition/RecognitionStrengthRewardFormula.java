package com.mooswqz.moostensuraaddon.recognition;

/**
 * Alignment-neutral reward formula for recognition strength profile 2.
 *
 * <p>The reward is frozen when recognition is committed. Identity strength
 * controls the scalable part, while Pure recognition receives a small bonus
 * for meeting the stricter one-path qualification.</p>
 */
public final class RecognitionStrengthRewardFormula {

    public static final int PROFILE_VERSION = 2;
    public static final String PROFILE_ID = "recognition_strength_v2";

    public static final double BASE_REWARD = 0.10D;
    public static final double MAXIMUM_IDENTITY_BONUS = 0.10D;
    public static final double PURE_RECOGNITION_BONUS = 0.025D;
    public static final double DEFAULT_IDENTITY_STRENGTH_MAXIMUM = 40.0D;

    public static final double MOVEMENT_AND_ATTACK_SPEED_SHARE = 0.50D;
    public static final double KNOCKBACK_RESISTANCE_SHARE = 0.25D;

    private RecognitionStrengthRewardFormula() {
    }

    public static Reward calculate(
            double identityStrength,
            double identityStrengthMaximum,
            boolean pure
    ) {
        double safeMaximum = sanitizeMaximum(identityStrengthMaximum);
        double safeIdentity = clamp(identityStrength, 0.0D, safeMaximum);
        double identityRatio = safeMaximum <= 0.0D
                ? 0.0D
                : safeIdentity / safeMaximum;

        double total = BASE_REWARD
                + identityRatio * MAXIMUM_IDENTITY_BONUS
                + (pure ? PURE_RECOGNITION_BONUS : 0.0D);

        double maximum = maximumReward(pure);
        total = clamp(total, BASE_REWARD, maximum);

        return new Reward(
                PROFILE_VERSION,
                PROFILE_ID,
                safeIdentity,
                safeMaximum,
                pure,
                total,
                total,
                total,
                total * MOVEMENT_AND_ATTACK_SPEED_SHARE,
                total * MOVEMENT_AND_ATTACK_SPEED_SHARE,
                total * KNOCKBACK_RESISTANCE_SHARE
        );
    }

    public static Reward calculateDefault(
            double identityStrength,
            boolean pure
    ) {
        return calculate(
                identityStrength,
                DEFAULT_IDENTITY_STRENGTH_MAXIMUM,
                pure
        );
    }

    public static double maximumReward(boolean pure) {
        return BASE_REWARD
                + MAXIMUM_IDENTITY_BONUS
                + (pure ? PURE_RECOGNITION_BONUS : 0.0D);
    }

    private static double sanitizeMaximum(double value) {
        return Double.isFinite(value) && value > 0.0D
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
            int profileVersion,
            String profileId,
            double frozenIdentityStrength,
            double identityStrengthMaximum,
            boolean pure,
            double totalStrength,
            double maxHealthMultiplier,
            double attackDamageMultiplier,
            double movementSpeedMultiplier,
            double attackSpeedMultiplier,
            double knockbackResistanceAddition
    ) {
        public Reward {
            profileId = profileId == null ? "" : profileId.trim();
            frozenIdentityStrength = sanitizeNonNegative(frozenIdentityStrength);
            identityStrengthMaximum = sanitizeNonNegative(identityStrengthMaximum);
            totalStrength = sanitizeNonNegative(totalStrength);
            maxHealthMultiplier = sanitizeNonNegative(maxHealthMultiplier);
            attackDamageMultiplier = sanitizeNonNegative(attackDamageMultiplier);
            movementSpeedMultiplier = sanitizeNonNegative(movementSpeedMultiplier);
            attackSpeedMultiplier = sanitizeNonNegative(attackSpeedMultiplier);
            knockbackResistanceAddition = sanitizeNonNegative(knockbackResistanceAddition);
        }

        public double totalStrengthPercent() {
            return totalStrength * 100.0D;
        }

        private static double sanitizeNonNegative(double value) {
            return Double.isFinite(value) && value > 0.0D
                    ? value
                    : 0.0D;
        }
    }
}