package com.mooswqz.moostensuraaddon.lifecycle;

/**
 * Standalone validation for lifecycle and acquisition transition rules.
 */
public final class AddonLifecycleValidationHarness {

    private static int passed;
    private static int failed;

    private AddonLifecycleValidationHarness() {
    }

    public static void main(String[] arguments) {
        validateGuards();
        validateDuplicateSuppression();
        validateEndowmentPolicy();
        validateAcquisitionPolicy();

        System.out.println(
                "Lifecycle validation: "
                        + passed
                        + " passed, "
                        + failed
                        + " failed"
        );

        if (failed > 0) {
            throw new IllegalStateException(
                    "Lifecycle validation failed."
            );
        }
    }

    private static void validateGuards() {
        expect(
                "guard active before deadline",
                AddonLifecyclePolicy.isGuardActive(2_000L, 1_999L)
        );
        expect(
                "guard inactive at deadline",
                !AddonLifecyclePolicy.isGuardActive(2_000L, 2_000L)
        );
        expect(
                "negative time sanitized",
                !AddonLifecyclePolicy.isGuardActive(0L, -1L)
        );
    }

    private static void validateDuplicateSuppression() {
        expect(
                "same reset reason suppressed inside window",
                AddonLifecyclePolicy.shouldSuppressDuplicateReset(
                        "character_reset",
                        1_000L,
                        "character_reset",
                        2_000L
                )
        );
        expect(
                "same reason accepted outside window",
                !AddonLifecyclePolicy.shouldSuppressDuplicateReset(
                        "character_reset",
                        1_000L,
                        "character_reset",
                        3_000L
                )
        );
        expect(
                "different reset reason accepted",
                !AddonLifecyclePolicy.shouldSuppressDuplicateReset(
                        "admin_command",
                        1_000L,
                        "character_reset",
                        1_100L
                )
        );
    }

    private static void validateEndowmentPolicy() {
        expect(
                "eligible committed result attempts endowment",
                AddonLifecyclePolicy.shouldAttemptNativeEndowment(
                        true,
                        false,
                        false,
                        false,
                        0L,
                        5_000L
                )
        );
        expect(
                "guard suppresses endowment",
                !AddonLifecyclePolicy.shouldAttemptNativeEndowment(
                        true,
                        false,
                        false,
                        true,
                        0L,
                        5_000L
                )
        );
        expect(
                "native name suppresses endowment",
                !AddonLifecyclePolicy.shouldAttemptNativeEndowment(
                        true,
                        true,
                        false,
                        false,
                        0L,
                        5_000L
                )
        );
        expect(
                "matching marker suppresses endowment",
                !AddonLifecyclePolicy.shouldAttemptNativeEndowment(
                        true,
                        false,
                        true,
                        false,
                        0L,
                        5_000L
                )
        );
        expect(
                "retry deadline enforced",
                !AddonLifecyclePolicy.shouldAttemptNativeEndowment(
                        true,
                        false,
                        false,
                        false,
                        6_000L,
                        5_000L
                )
        );
        expect(
                "first retry is one second",
                AddonLifecyclePolicy.nextEndowmentAttemptEpochMillis(
                        10_000L,
                        1
                ) == 11_000L
        );
        expect(
                "retry delay is capped",
                AddonLifecyclePolicy.nextEndowmentAttemptEpochMillis(
                        10_000L,
                        20
                ) == 70_000L
        );
    }

    private static void validateAcquisitionPolicy() {
        GranterAcquisitionPolicy.Observation freshBaseline =
                GranterAcquisitionPolicy.evaluate(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false
                );
        expect(
                "fresh baseline initializes without award",
                freshBaseline.initialized()
                        && !freshBaseline.shouldAwardAdvancement()
        );

        GranterAcquisitionPolicy.Observation oldSaveBaseline =
                GranterAcquisitionPolicy.evaluate(
                        false,
                        false,
                        false,
                        true,
                        false,
                        false
                );
        expect(
                "old owned authority does not false-trigger",
                oldSaveBaseline.lastOwnedAuthority()
                        && !oldSaveBaseline.shouldAwardAdvancement()
        );

        GranterAcquisitionPolicy.Observation transition =
                GranterAcquisitionPolicy.evaluate(
                        true,
                        false,
                        false,
                        true,
                        false,
                        false
                );
        expect(
                "false-to-true transition awards",
                transition.acquisitionConfirmedThisLife()
                        && transition.shouldAwardAdvancement()
        );

        GranterAcquisitionPolicy.Observation relog =
                GranterAcquisitionPolicy.evaluate(
                        true,
                        true,
                        true,
                        true,
                        true,
                        false
                );
        expect(
                "relog does not duplicate completed advancement",
                !relog.shouldAwardAdvancement()
        );

        GranterAcquisitionPolicy.Observation repair =
                GranterAcquisitionPolicy.evaluate(
                        true,
                        true,
                        true,
                        true,
                        false,
                        false
                );
        expect(
                "confirmed acquisition repairs missing advancement",
                repair.shouldAwardAdvancement()
        );

        GranterAcquisitionPolicy.Observation evidenceRepair =
                GranterAcquisitionPolicy.evaluate(
                        true,
                        true,
                        false,
                        true,
                        false,
                        true
                );
        expect(
                "authority-use evidence confirms acquisition",
                evidenceRepair.acquisitionConfirmedThisLife()
                        && evidenceRepair.shouldAwardAdvancement()
        );

        GranterAcquisitionPolicy.Observation removal =
                GranterAcquisitionPolicy.evaluate(
                        true,
                        true,
                        true,
                        false,
                        true,
                        false
                );
        expect(
                "temporary removal retains life confirmation",
                removal.acquisitionConfirmedThisLife()
                        && !removal.lastOwnedAuthority()
        );
    }

    private static void expect(
            String name,
            boolean condition
    ) {
        if (condition) {
            passed++;
            return;
        }

        failed++;
        System.err.println("FAILED: " + name);
    }
}