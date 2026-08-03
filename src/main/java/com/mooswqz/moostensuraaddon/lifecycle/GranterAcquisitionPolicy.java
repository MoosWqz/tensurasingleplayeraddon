package com.mooswqz.moostensuraaddon.lifecycle;

/**
 * Pure state-transition rules for the Granter acquisition advancement.
 */
public final class GranterAcquisitionPolicy {

    private GranterAcquisitionPolicy() {
    }

    public static Observation evaluate(
            boolean observationInitialized,
            boolean previouslyOwnedAuthority,
            boolean acquisitionConfirmedThisLife,
            boolean currentlyOwnsAuthority,
            boolean advancementAlreadyDone,
            boolean confirmedAuthorityEvidence
    ) {
        if (!observationInitialized) {
            boolean confirmed = acquisitionConfirmedThisLife
                    || advancementAlreadyDone
                    || (currentlyOwnsAuthority && confirmedAuthorityEvidence);

            return new Observation(
                    true,
                    currentlyOwnsAuthority,
                    confirmed,
                    currentlyOwnsAuthority
                            && confirmed
                            && !advancementAlreadyDone
            );
        }

        boolean acquiredNow = !previouslyOwnedAuthority
                && currentlyOwnsAuthority;
        boolean confirmed = acquisitionConfirmedThisLife
                || advancementAlreadyDone
                || acquiredNow
                || (currentlyOwnsAuthority && confirmedAuthorityEvidence);
        boolean shouldAward = currentlyOwnsAuthority
                && confirmed
                && !advancementAlreadyDone;

        return new Observation(
                true,
                currentlyOwnsAuthority,
                confirmed,
                shouldAward
        );
    }

    public record Observation(
            boolean initialized,
            boolean lastOwnedAuthority,
            boolean acquisitionConfirmedThisLife,
            boolean shouldAwardAdvancement
    ) {
    }
}