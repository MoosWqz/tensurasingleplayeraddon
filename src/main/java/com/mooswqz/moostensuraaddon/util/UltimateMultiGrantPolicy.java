package com.mooswqz.moostensuraaddon.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class UltimateMultiGrantPolicy {

    public static final int MAX_SELECTED_SKILLS = 32;

    private UltimateMultiGrantPolicy() {
    }

    public static RequestAnalysis analyseRequest(
            List<String> requestedSkillIds
    ) {
        if (requestedSkillIds == null
                || requestedSkillIds.isEmpty()) {
            return RequestAnalysis.empty();
        }

        boolean overLimit = requestedSkillIds.size()
                > MAX_SELECTED_SKILLS;
        LinkedHashSet<String> unique =
                new LinkedHashSet<>();
        int rejected = 0;

        for (String rawSkillId : requestedSkillIds) {
            String skillId = rawSkillId == null
                    ? ""
                    : rawSkillId.trim();

            if (skillId.isBlank()) {
                rejected++;
                continue;
            }

            if (!unique.add(skillId)) {
                rejected++;
            }
        }

        return new RequestAnalysis(
                List.copyOf(
                        new ArrayList<>(unique)
                ),
                requestedSkillIds.size(),
                rejected,
                overLimit
        );
    }

    public static double calculateCharge(
            List<Double> successfulCosts
    ) {
        if (successfulCosts == null
                || successfulCosts.isEmpty()) {
            return 0.0D;
        }

        double total = 0.0D;

        for (Double value : successfulCosts) {
            if (value == null
                    || !Double.isFinite(value)
                    || value <= 0.0D) {
                continue;
            }

            total += value;
        }

        return Double.isFinite(total)
                ? Math.max(0.0D, total)
                : 0.0D;
    }

    public record RequestAnalysis(
            List<String> uniqueSkillIds,
            int submittedCount,
            int rejectedCount,
            boolean overLimit
    ) {

        public RequestAnalysis {
            uniqueSkillIds = uniqueSkillIds == null
                    ? List.of()
                    : List.copyOf(uniqueSkillIds);
            submittedCount = Math.max(0, submittedCount);
            rejectedCount = Math.max(0, rejectedCount);
        }

        public static RequestAnalysis empty() {
            return new RequestAnalysis(
                    List.of(),
                    0,
                    0,
                    false
            );
        }
    }
}