package com.mooswqz.moostensuraaddon.recognition;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum RecognitionPath {

    LAWFUL_GOOD(
            "lawful_good",
            Temperament.LAWFUL,
            Morality.GOOD
    ),

    NEUTRAL_GOOD(
            "neutral_good",
            Temperament.NEUTRAL,
            Morality.GOOD
    ),

    CHAOTIC_GOOD(
            "chaotic_good",
            Temperament.CHAOTIC,
            Morality.GOOD
    ),

    LAWFUL_NEUTRAL(
            "lawful_neutral",
            Temperament.LAWFUL,
            Morality.NEUTRAL
    ),

    TRUE_NEUTRAL(
            "true_neutral",
            Temperament.NEUTRAL,
            Morality.NEUTRAL
    ),

    CHAOTIC_NEUTRAL(
            "chaotic_neutral",
            Temperament.CHAOTIC,
            Morality.NEUTRAL
    ),

    LAWFUL_EVIL(
            "lawful_evil",
            Temperament.LAWFUL,
            Morality.EVIL
    ),

    NEUTRAL_EVIL(
            "neutral_evil",
            Temperament.NEUTRAL,
            Morality.EVIL
    ),

    CHAOTIC_EVIL(
            "chaotic_evil",
            Temperament.CHAOTIC,
            Morality.EVIL
    );

    private static final Map<String, RecognitionPath>
            CANONICAL_PATHS = createCanonicalPaths();

    /*
     * Deliberately empty for now.
     *
     * Future path-ID renames must add explicit old -> new entries here before
     * the old identifier disappears from released code. We do not guess
     * historical meanings such as "true_good".
     */
    private static final Map<String, RecognitionPath>
            LEGACY_ALIASES = createLegacyAliases();

    private final String id;
    private final Temperament temperament;
    private final Morality morality;

    RecognitionPath(
            String id,
            Temperament temperament,
            Morality morality
    ) {
        this.id = id;
        this.temperament = temperament;
        this.morality = morality;
    }

    public String getId() {
        return id;
    }

    public Temperament getTemperament() {
        return temperament;
    }

    public Morality getMorality() {
        return morality;
    }

    public String getTranslationKey() {
        return "recognition_path.moostensuraaddon." + id;
    }

    /**
     * Resolves canonical IDs and explicitly registered legacy aliases.
     */
    public static Optional<RecognitionPath> byId(
            String rawId
    ) {
        String normalized =
                normalizeStoredId(rawId);

        if (normalized.isBlank()) {
            return Optional.empty();
        }

        RecognitionPath canonical =
                CANONICAL_PATHS.get(normalized);

        if (canonical != null) {
            return Optional.of(canonical);
        }

        return Optional.ofNullable(
                LEGACY_ALIASES.get(normalized)
        );
    }

    /**
     * Returns the current canonical ID when the supplied value is recognized.
     *
     * Unknown values are preserved instead of being replaced with a live
     * evaluator result. This is important for forward-compatible save
     * handling.
     */
    public static String canonicalizeStoredId(
            String rawId
    ) {
        if (rawId == null || rawId.isBlank()) {
            return "";
        }

        return byId(rawId)
                .map(RecognitionPath::getId)
                .orElse(
                        rawId.trim()
                );
    }

    public static boolean isCanonicalId(
            String rawId
    ) {
        String normalized =
                normalizeStoredId(rawId);

        return CANONICAL_PATHS.containsKey(
                normalized
        );
    }

    public static boolean isKnownStoredId(
            String rawId
    ) {
        return byId(rawId).isPresent();
    }

    private static String normalizeStoredId(
            String rawId
    ) {
        if (rawId == null || rawId.isBlank()) {
            return "";
        }

        String normalized =
                rawId.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace('-', '_')
                        .replace(' ', '_');

        while (normalized.contains("__")) {
            normalized =
                    normalized.replace(
                            "__",
                            "_"
                    );
        }

        return normalized;
    }

    private static Map<String, RecognitionPath>
    createCanonicalPaths() {
        Map<String, RecognitionPath> result =
                new LinkedHashMap<>();

        for (RecognitionPath path : values()) {
            result.put(
                    path.id,
                    path
            );
        }

        return Map.copyOf(result);
    }

    private static Map<String, RecognitionPath>
    createLegacyAliases() {
        Map<String, RecognitionPath> aliases =
                new LinkedHashMap<>();

        /*
         * Example for a future deliberate migration:
         *
         * aliases.put(
         *         "old_path_id",
         *         RecognitionPath.NEW_PATH
         * );
         */

        return Map.copyOf(aliases);
    }

    public enum Temperament {
        LAWFUL,
        NEUTRAL,
        CHAOTIC
    }

    public enum Morality {
        GOOD,
        NEUTRAL,
        EVIL
    }
}