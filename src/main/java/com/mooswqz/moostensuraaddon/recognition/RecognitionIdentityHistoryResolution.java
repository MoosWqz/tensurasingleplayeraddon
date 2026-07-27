package com.mooswqz.moostensuraaddon.recognition;

import java.util.List;

/**
 * Immutable, read-only explanation of the modifier currently implied by a
 * player's committed recognition and later identity history.
 *
 * <p>The record contains only derived information. Creating one never mutates
 * recognition data, applies decay, records a deed or rewrites a committed
 * result.</p>
 */
public record RecognitionIdentityHistoryResolution(
        RecognitionIdentityHistoryModifier modifier,
        boolean committedIdentityAvailable,
        String originalMoralDirection,
        String originalTemperamentDirection,
        String currentMoralDirection,
        String currentTemperamentDirection,
        String storedMoralDirection,
        String storedTemperamentDirection,
        boolean moralContradiction,
        boolean temperamentContradiction,
        boolean moralReturned,
        boolean temperamentReturned,
        boolean moralContested,
        boolean temperamentContested,
        int guidanceProgress,
        int color,
        String summary,
        String reason,
        List<String> evidence
) {

    public RecognitionIdentityHistoryResolution {
        modifier = modifier == null
                ? RecognitionIdentityHistoryModifier.NONE
                : modifier;

        originalMoralDirection = clean(originalMoralDirection);
        originalTemperamentDirection = clean(originalTemperamentDirection);
        currentMoralDirection = clean(currentMoralDirection);
        currentTemperamentDirection = clean(currentTemperamentDirection);
        storedMoralDirection = clean(storedMoralDirection);
        storedTemperamentDirection = clean(storedTemperamentDirection);

        guidanceProgress = Math.max(
                0,
                Math.min(100, guidanceProgress)
        );

        color &= 0xFFFFFF;

        summary = summary == null
                ? ""
                : summary.trim();

        reason = reason == null
                ? ""
                : reason.trim();

        evidence = evidence == null
                ? List.of()
                : List.copyOf(evidence);
    }

    public String modifierId() {
        return modifier.id();
    }

    public String modifierDisplayName() {
        return modifier.displayName();
    }

    public boolean hasModifier() {
        return modifier != RecognitionIdentityHistoryModifier.NONE;
    }

    public boolean anyReturnedAxis() {
        return moralReturned || temperamentReturned;
    }

    public boolean anyContestedAxis() {
        return moralContested || temperamentContested;
    }

    private static String clean(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }
}