package com.mooswqz.moostensuraaddon.lifecycle;

/**
 * Pure policy rules used by the player-incarnation lifecycle.
 *
 * <p>The class has no Minecraft dependencies so the edge cases can be
 * validated with a small standalone harness.</p>
 */
public final class AddonLifecyclePolicy {

    public static final int CURRENT_STATE_REVISION = 1;

    public static final long CHARACTER_RESET_GUARD_MILLIS = 20_000L;
    public static final long ADMIN_RESET_GUARD_MILLIS = 2_000L;
    public static final long DUPLICATE_RESET_WINDOW_MILLIS = 2_000L;

    public static final long ENDOWMENT_INITIAL_RETRY_MILLIS = 1_000L;
    public static final long ENDOWMENT_MAX_RETRY_MILLIS = 60_000L;

    private AddonLifecyclePolicy() {
    }

    public static boolean isGuardActive(
            long guardUntilEpochMillis,
            long nowEpochMillis
    ) {
        return guardUntilEpochMillis > Math.max(0L, nowEpochMillis);
    }

    public static boolean shouldSuppressDuplicateReset(
            String previousReason,
            long previousResetEpochMillis,
            String requestedReason,
            long nowEpochMillis
    ) {
        if (previousReason == null
                || requestedReason == null
                || !previousReason.equals(requestedReason)) {
            return false;
        }

        long elapsed = Math.max(0L, nowEpochMillis)
                - Math.max(0L, previousResetEpochMillis);

        return elapsed >= 0L
                && elapsed < DUPLICATE_RESET_WINDOW_MILLIS;
    }

    public static boolean shouldAttemptNativeEndowment(
            boolean recognitionCommitted,
            boolean nativeNamed,
            boolean markerMatchesIncarnation,
            boolean resetGuardActive,
            long nextAttemptEpochMillis,
            long nowEpochMillis
    ) {
        if (!recognitionCommitted
                || nativeNamed
                || markerMatchesIncarnation
                || resetGuardActive) {
            return false;
        }

        return Math.max(0L, nowEpochMillis)
                >= Math.max(0L, nextAttemptEpochMillis);
    }

    public static long nextEndowmentAttemptEpochMillis(
            long nowEpochMillis,
            int failedAttempts
    ) {
        int safeAttempts = Math.max(1, failedAttempts);
        int shift = Math.min(6, safeAttempts - 1);
        long delay = ENDOWMENT_INITIAL_RETRY_MILLIS << shift;
        delay = Math.min(delay, ENDOWMENT_MAX_RETRY_MILLIS);

        return Math.max(0L, nowEpochMillis) + delay;
    }
}