package com.mooswqz.moostensuraaddon.recognition;

import java.util.Locale;
import java.util.Optional;

/**
 * Stable semantic identifiers reserved for contradiction-history outcomes.
 *
 * <p>The identifiers are serialized as strings. Enum ordinals are never
 * persisted, so future reordering cannot reinterpret an existing save.</p>
 */
public enum RecognitionIdentityHistoryModifier {

    NONE("none", "None"),
    FALLEN("fallen", "Fallen"),
    REDEEMED("redeemed", "Redeemed"),
    OATHBREAKER("oathbreaker", "Oathbreaker"),
    CROWNED_OR_BOUND("crowned_or_bound", "Crowned or Bound"),
    DEFIANT("defiant", "Defiant"),
    FRACTURED("fractured", "Fractured"),
    RECONCILED("reconciled", "Reconciled");

    private final String id;
    private final String displayName;

    RecognitionIdentityHistoryModifier(
            String id,
            String displayName
    ) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<RecognitionIdentityHistoryModifier> byId(
            String rawId
    ) {
        String normalized = normalize(rawId);

        if (normalized.isBlank()) {
            return Optional.empty();
        }

        for (RecognitionIdentityHistoryModifier modifier : values()) {
            if (modifier.id.equals(normalized)) {
                return Optional.of(modifier);
            }
        }

        return Optional.empty();
    }

    public static String canonicalOrPreserved(
            String rawId
    ) {
        if (rawId == null || rawId.isBlank()) {
            return NONE.id;
        }

        String preserved = rawId.trim();

        return byId(preserved)
                .map(RecognitionIdentityHistoryModifier::id)
                .orElse(preserved);
    }

    private static String normalize(
            String rawId
    ) {
        if (rawId == null) {
            return "";
        }

        String normalized = rawId.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }

        return normalized;
    }
}