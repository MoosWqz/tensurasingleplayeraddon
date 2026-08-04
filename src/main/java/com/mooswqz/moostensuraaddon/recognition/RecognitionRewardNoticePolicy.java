package com.mooswqz.moostensuraaddon.recognition;

/**
 * Pure lifecycle rules for the one-time recognition-benefit notice.
 */
public final class RecognitionRewardNoticePolicy {

    public static final int CURRENT_NOTICE_REVISION = 1;

    private RecognitionRewardNoticePolicy() {
    }

    public static String createIdentity(
            String recognitionIncarnationId,
            String lifecycleToken,
            int rewardProfileVersion
    ) {
        String incarnation = clean(recognitionIncarnationId);

        if (incarnation.isBlank()) {
            incarnation = clean(lifecycleToken);
        }

        if (incarnation.isBlank()) {
            return "";
        }

        return incarnation
                + "|profile="
                + Math.max(0, rewardProfileVersion)
                + "|notice="
                + CURRENT_NOTICE_REVISION;
    }

    public static boolean shouldShow(
            boolean recognitionCommitted,
            boolean committedResultValid,
            boolean rewardMetadataInitialized,
            boolean futureProfilePreserved,
            boolean resetGuardActive,
            boolean nativeEndowmentMarkerMatches,
            String currentIdentity,
            String shownIdentity
    ) {
        if (!recognitionCommitted
                || !committedResultValid
                || !rewardMetadataInitialized
                || futureProfilePreserved
                || resetGuardActive
                || !nativeEndowmentMarkerMatches) {
            return false;
        }

        String current = clean(currentIdentity);

        return !current.isBlank()
                && !current.equals(clean(shownIdentity));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}