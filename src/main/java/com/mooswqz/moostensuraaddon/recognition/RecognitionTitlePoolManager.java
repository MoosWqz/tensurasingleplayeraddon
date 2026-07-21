package com.mooswqz.moostensuraaddon.recognition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Holds the immutable recognition-title snapshot produced by the most recent
 * server datapack reload.
 *
 * Reads are lock-free. The reload listener creates a completely new snapshot
 * and atomically publishes it through the volatile field.
 */
public final class RecognitionTitlePoolManager {

    public static final int MAX_TITLES_PER_POOL =
            128;

    private static volatile Snapshot currentSnapshot =
            Snapshot.fallbackOnly();

    private RecognitionTitlePoolManager() {
    }

    public static List<String> getTitles(
            RecognitionPath path,
            boolean pure
    ) {
        if (path == null) {
            return List.of();
        }

        Snapshot snapshot =
                currentSnapshot;

        List<String> titles =
                pure
                        ? snapshot.pureTitles()
                        .get(path)
                        : snapshot.standardTitles()
                        .get(path);

        if (titles == null || titles.isEmpty()) {
            return List.of(
                    getFallbackTitle(
                            path,
                            pure
                    )
            );
        }

        return titles;
    }

    public static int getLoadedSourceFileCount() {
        return currentSnapshot.sourceFileCount();
    }

    public static int getTitleCount(
            RecognitionPath path,
            boolean pure
    ) {
        return getTitles(
                path,
                pure
        ).size();
    }

    public static int getTotalStandardTitleCount() {
        int total =
                0;

        for (RecognitionPath path :
                RecognitionPath.values()) {

            total += getTitleCount(
                    path,
                    false
            );
        }

        return total;
    }

    public static int getTotalPureTitleCount() {
        int total =
                0;

        for (RecognitionPath path :
                RecognitionPath.values()) {

            total += getTitleCount(
                    path,
                    true
            );
        }

        return total;
    }

    /**
     * Installs an entirely new immutable title snapshot.
     *
     * Package-private because only the server reload listener should replace
     * the active datapack state.
     */
    static void install(
            Map<RecognitionPath, List<String>> standardTitles,
            Map<RecognitionPath, List<String>> pureTitles,
            int sourceFileCount
    ) {
        currentSnapshot =
                Snapshot.create(
                        standardTitles,
                        pureTitles,
                        sourceFileCount
                );
    }

    /**
     * Every recognition path retains a single failsafe title.
     *
     * These are not the normal title pools. They are only used when a path has
     * no valid datapack title after a reload, such as when a pack contains a
     * malformed or intentionally empty definition.
     */
    public static String getFallbackTitle(
            RecognitionPath path,
            boolean pure
    ) {
        if (path == null) {
            return pure
                    ? "the Ascendant"
                    : "the Recognized";
        }

        return switch (path) {
            case LAWFUL_GOOD ->
                    pure
                            ? "the Radiant Sovereign"
                            : "the Steadfast Guardian";

            case NEUTRAL_GOOD ->
                    pure
                            ? "the Benevolent Saint"
                            : "the Gentle Hand";

            case CHAOTIC_GOOD ->
                    pure
                            ? "the Liberating Dawn"
                            : "the Unbound Savior";

            case LAWFUL_NEUTRAL ->
                    pure
                            ? "the Absolute Crown"
                            : "the Crowned Will";

            case TRUE_NEUTRAL ->
                    pure
                            ? "the Perfect Equilibrium"
                            : "the Soul Ascendant";

            case CHAOTIC_NEUTRAL ->
                    pure
                            ? "the Infinite Wanderer"
                            : "the Unbound Seeker";

            case LAWFUL_EVIL ->
                    pure
                            ? "the Eternal Tyrant"
                            : "the Dread Sovereign";

            case NEUTRAL_EVIL ->
                    pure
                            ? "the Supreme Usurper"
                            : "the Ambitious One";

            case CHAOTIC_EVIL ->
                    pure
                            ? "the End of Order"
                            : "the Unchained Calamity";
        };
    }

    private static Map<RecognitionPath, List<String>>
    sanitizePools(
            Map<RecognitionPath, List<String>> source,
            boolean pure
    ) {
        EnumMap<RecognitionPath, List<String>> result =
                new EnumMap<>(
                        RecognitionPath.class
                );

        for (RecognitionPath path :
                RecognitionPath.values()) {

            List<String> rawTitles =
                    source == null
                            ? null
                            : source.get(path);

            List<String> safeTitles =
                    sanitizeTitleList(
                            rawTitles
                    );

            if (safeTitles.isEmpty()) {
                safeTitles =
                        List.of(
                                getFallbackTitle(
                                        path,
                                        pure
                                )
                        );
            }

            result.put(
                    path,
                    safeTitles
            );
        }

        return Map.copyOf(
                result
        );
    }

    private static List<String> sanitizeTitleList(
            List<String> source
    ) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> uniqueTitles =
                new LinkedHashSet<>();

        for (String rawTitle : source) {
            String safeTitle =
                    RecognitionDisplayNameService
                            .sanitizeTitle(
                                    rawTitle
                            );

            if (safeTitle.isBlank()) {
                continue;
            }

            uniqueTitles.add(
                    safeTitle
            );

            if (uniqueTitles.size()
                    >= MAX_TITLES_PER_POOL) {
                break;
            }
        }

        return List.copyOf(
                new ArrayList<>(
                        uniqueTitles
                )
        );
    }

    private record Snapshot(
            Map<RecognitionPath, List<String>> standardTitles,
            Map<RecognitionPath, List<String>> pureTitles,
            int sourceFileCount
    ) {

        private Snapshot {
            standardTitles =
                    Map.copyOf(
                            standardTitles
                    );

            pureTitles =
                    Map.copyOf(
                            pureTitles
                    );

            sourceFileCount =
                    Math.max(
                            0,
                            sourceFileCount
                    );
        }

        private static Snapshot create(
                Map<RecognitionPath, List<String>> standardTitles,
                Map<RecognitionPath, List<String>> pureTitles,
                int sourceFileCount
        ) {
            return new Snapshot(
                    sanitizePools(
                            standardTitles,
                            false
                    ),
                    sanitizePools(
                            pureTitles,
                            true
                    ),
                    sourceFileCount
            );
        }

        private static Snapshot fallbackOnly() {
            return create(
                    Map.of(),
                    Map.of(),
                    0
            );
        }
    }
}