package com.mooswqz.moostensuraaddon.recognition;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the active recognition-balance snapshot.
 *
 * The snapshot and its revision are replaced together in one atomic operation.
 * Every evaluation therefore observes either the complete old definition or
 * the complete new definition, never a partially reloaded mixture.
 */
public final class RecognitionBalanceManager {

    private static final AtomicReference<State> ACTIVE_STATE =
            new AtomicReference<>(
                    new State(
                            RecognitionBalanceSnapshot.createDefaults(),
                            0L
                    )
            );

    private RecognitionBalanceManager() {
    }

    public static State getState() {
        return ACTIVE_STATE.get();
    }

    public static RecognitionBalanceSnapshot getActiveSnapshot() {
        return getState().snapshot();
    }

    public static long getRevision() {
        return getState().revision();
    }

    public static State install(
            RecognitionBalanceSnapshot snapshot
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "A recognition balance snapshot is required."
            );
        }

        while (true) {
            State current =
                    ACTIVE_STATE.get();

            State replacement =
                    new State(
                            snapshot,
                            current.revision() + 1L
                    );

            if (ACTIVE_STATE.compareAndSet(
                    current,
                    replacement
            )) {
                return replacement;
            }
        }
    }

    public record State(
            RecognitionBalanceSnapshot snapshot,
            long revision
    ) {

        public State {
            if (snapshot == null) {
                throw new IllegalArgumentException(
                        "Recognition balance state requires a snapshot."
                );
            }

            revision = Math.max(
                    0L,
                    revision
            );
        }
    }
}