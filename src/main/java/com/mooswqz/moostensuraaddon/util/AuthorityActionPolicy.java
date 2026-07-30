package com.mooswqz.moostensuraaddon.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class AuthorityActionPolicy {

    public static final int MAX_SELECTED_SKILLS = 32;
    public static final int MAX_SCREEN_SKILLS = 256;
    public static final int MAX_TARGETS = 256;

    private AuthorityActionPolicy() {
    }

    public static RequestAnalysis analyseRequest(
            AuthorityActionMode mode,
            List<String> rawSkillIds
    ) {
        int limit = mode == null
                ? 1
                : Math.min(
                MAX_SELECTED_SKILLS,
                mode.selectionLimit()
        );

        if (rawSkillIds == null || rawSkillIds.isEmpty()) {
            return new RequestAnalysis(
                    List.of(),
                    0,
                    false,
                    false
            );
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        int rejected = 0;
        boolean duplicate = false;

        for (String rawId : rawSkillIds) {
            if (rawId == null || rawId.isBlank()) {
                rejected++;
                continue;
            }

            String cleaned = rawId.trim();

            if (!unique.add(cleaned)) {
                rejected++;
                duplicate = true;
            }
        }

        boolean overLimit = unique.size() > limit;
        List<String> retained = new ArrayList<>(unique);

        if (retained.size() > MAX_SELECTED_SKILLS + 1) {
            retained = retained.subList(
                    0,
                    MAX_SELECTED_SKILLS + 1
            );
            overLimit = true;
        }

        return new RequestAnalysis(
                List.copyOf(retained),
                rejected,
                duplicate,
                overLimit
        );
    }

    public static double sanitizeCost(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? value
                : 0.0D;
    }

    public static double sumCosts(List<Double> costs) {
        if (costs == null || costs.isEmpty()) {
            return 0.0D;
        }

        double total = 0.0D;

        for (Double cost : costs) {
            if (cost == null) {
                continue;
            }

            total += sanitizeCost(cost);
        }

        return Double.isFinite(total)
                ? Math.max(0.0D, total)
                : Double.MAX_VALUE;
    }

    public record RequestAnalysis(
            List<String> uniqueSkillIds,
            int rejectedCount,
            boolean duplicateFound,
            boolean overLimit
    ) {
        public RequestAnalysis {
            uniqueSkillIds = uniqueSkillIds == null
                    ? List.of()
                    : List.copyOf(uniqueSkillIds);
            rejectedCount = Math.max(0, rejectedCount);
        }

        public boolean malformed() {
            return rejectedCount > 0
                    || duplicateFound
                    || overLimit;
        }
    }
}