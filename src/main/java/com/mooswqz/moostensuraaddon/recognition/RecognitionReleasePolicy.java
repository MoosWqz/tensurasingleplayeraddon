package com.mooswqz.moostensuraaddon.recognition;

/**
 * Immutable release policy for recognition in Moos Tensura Addon 1.4.0.
 *
 * <p>Recognition is both an identity system and a permanent power milestone.
 * The reward is explicit, alignment-neutral and frozen for the incarnation.
 * Later deeds may shape the player's Legacy descriptor, but do not silently
 * rewrite or stack the base recognition-strength reward.</p>
 */
public final class RecognitionReleasePolicy {

    public static final int POLICY_VERSION =
            2;

    public static final String POLICY_ID =
            RecognitionStrengthRewardFormula.PROFILE_ID;

    public static final String DISPLAY_NAME =
            "Recognition Strength";

    public static final int EXPECTED_RESULT_VERSION =
            1;

    public static final int EXPECTED_RULES_VERSION =
            2;

    public static final int EXPECTED_REWARD_PROFILE_VERSION =
            RecognitionStrengthRewardFormula.PROFILE_VERSION;

    public static final int EXPECTED_PURE_PATHS =
            9;

    public static final int EXPECTED_REQUIRED_ADJACENT_CROSSES =
            24;

    public static final double MIN_FREEDOM_ORDER_RATIO =
            0.85D;

    public static final double MAX_FREEDOM_ORDER_RATIO =
            1.05D;

    private RecognitionReleasePolicy() {
    }

    public static boolean grantsAttributeModifiers() {
        return true;
    }

    public static boolean grantsCombatMultipliers() {
        return true;
    }

    public static boolean grantsHistoryModifierPower() {
        return false;
    }

    public static boolean requiresTrueHeroForPureGood() {
        return false;
    }

    public static boolean requiresTrueDemonLordForPureEvil() {
        return false;
    }

    public static boolean requiresAwakeningForTrueNeutral() {
        return false;
    }

    public static boolean awakeningActsAsDirectionalEvidence() {
        return true;
    }

    public static double guidanceProgress(
            double rewardAmount
    ) {
        double maximum = RecognitionStrengthRewardFormula
                .maximumReward(true);

        if (!Double.isFinite(rewardAmount)
                || rewardAmount <= 0.0D
                || maximum <= 0.0D) {
            return 0.0D;
        }

        return Math.min(
                100.0D,
                rewardAmount / maximum * 100.0D
        );
    }

    public static String playerFacingSummary(
            boolean recognitionCommitted
    ) {
        if (recognitionCommitted) {
            return "Your recognized name, title, path colour and strength are permanent for this incarnation. Legacy may continue to evolve, but it does not rewrite or stack the frozen base reward.";
        }

        return "Recognition bestows a name, title, path colour, lasting strength and an evolving legacy. Its power reflects the identity proven before the altar; Pure recognition earns a small bonus, while no alignment is inherently stronger.";
    }

    public static String rewardProfileSummary() {
        return "Reward profile 2 grants 10–20% permanent recognition strength from frozen Identity Strength, with a 2.5% Pure bonus for a maximum of 22.5%. Health and attack damage receive the full reward; movement and attack speed receive half; knockback resistance receives one quarter.";
    }

    public static String awakeningPolicySummary() {
        return "True Hero and True Demon Lord strengthen matching directional evidence, but they are not hard requirements for Pure Good or Pure Evil. True Neutral has no awakening gate.";
    }
}