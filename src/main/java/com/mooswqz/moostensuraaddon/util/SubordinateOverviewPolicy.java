package com.mooswqz.moostensuraaddon.util;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SubordinateOverviewPolicy {

    public static final double NEARBY_RADIUS = 64.0D;
    public static final int MAX_TARGETS_PER_PAYLOAD = 512;
    public static final int MAX_SKILLS_PER_TARGET = 512;
    public static final int AUTO_REFRESH_TICKS = 100;
    public static final long MINIMUM_REFRESH_INTERVAL_NANOS =
            500_000_000L;

    private SubordinateOverviewPolicy() {
    }

    public static String normalizeQuery(
            String query
    ) {
        return query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matchesTarget(
            String query,
            String targetName,
            String typeName,
            List<String> skillSearchTexts
    ) {
        String normalized = normalizeQuery(query);

        if (normalized.isBlank()) {
            return true;
        }

        if (contains(targetName, normalized)
                || contains(typeName, normalized)) {
            return true;
        }

        if (skillSearchTexts == null) {
            return false;
        }

        for (String searchText : skillSearchTexts) {
            if (contains(searchText, normalized)) {
                return true;
            }
        }

        return false;
    }

    public static Comparator<Candidate> candidateOrder() {
        return Comparator
                .comparingDouble(Candidate::distanceSquared)
                .thenComparing(
                        Candidate::displayName,
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(Candidate::uuid);
    }

    private static boolean contains(
            String value,
            String normalizedQuery
    ) {
        return value != null
                && value.toLowerCase(Locale.ROOT)
                .contains(normalizedQuery);
    }

    public record Candidate(
            String uuid,
            String displayName,
            double distanceSquared
    ) {

        public Candidate {
            uuid = uuid == null ? "" : uuid;
            displayName = displayName == null
                    ? ""
                    : displayName;
            distanceSquared = Double.isFinite(distanceSquared)
                    && distanceSquared >= 0.0D
                    ? distanceSquared
                    : Double.MAX_VALUE;
        }
    }
}