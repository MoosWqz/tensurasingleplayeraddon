package com.mooswqz.moostensuraaddon.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared, side-safe limits and calculations for Ultimate borrow/seize menus.
 */
public final class UltimateBorrowSeizePolicy {

    public static final int MAX_SELECTED_SKILLS = 32;
    public static final int MAX_SKILL_ID_LENGTH = 256;

    private UltimateBorrowSeizePolicy() {
    }

    public static RequestAnalysis analyseRequest(
            Collection<String> requestedSkillIds
    ) {
        if (requestedSkillIds == null
                || requestedSkillIds.isEmpty()) {
            return new RequestAnalysis(
                    List.of(),
                    0,
                    false
            );
        }

        Set<String> unique = new LinkedHashSet<>();
        int rejected = 0;
        boolean overLimit = false;

        for (String rawSkillId : requestedSkillIds) {
            String cleaned = cleanSkillId(rawSkillId);

            if (cleaned.isBlank()) {
                rejected++;
                continue;
            }

            if (!unique.add(cleaned)) {
                rejected++;
                continue;
            }

            if (unique.size() > MAX_SELECTED_SKILLS) {
                overLimit = true;
                break;
            }
        }

        return new RequestAnalysis(
                List.copyOf(unique),
                rejected,
                overLimit
        );
    }

    public static double calculateTotalCost(
            double costPerSkill,
            int selectedCount
    ) {
        double safeCost = sanitizeNonNegative(costPerSkill);
        int safeCount = Math.max(0, selectedCount);
        double result = safeCost * safeCount;

        return Double.isFinite(result)
                ? result
                : Double.MAX_VALUE;
    }

    public static double calculateSeizeDeathChance(
            double chancePerSkill,
            double maximumChance,
            int selectedCount
    ) {
        double safePerSkill = clampChance(chancePerSkill);
        double safeMaximum = clampChance(maximumChance);
        int safeCount = Math.max(0, selectedCount);

        return Math.min(
                safeMaximum,
                safePerSkill * safeCount
        );
    }

    public static double highestBorrowChance(
            Collection<String> selectedSkillIds,
            Map<String, Double> chancesBySkillId
    ) {
        if (selectedSkillIds == null
                || selectedSkillIds.isEmpty()
                || chancesBySkillId == null
                || chancesBySkillId.isEmpty()) {
            return 0.0D;
        }

        double highest = 0.0D;

        for (String skillId : selectedSkillIds) {
            highest = Math.max(
                    highest,
                    clampChance(
                            chancesBySkillId.getOrDefault(
                                    skillId,
                                    0.0D
                            )
                    )
            );
        }

        return highest;
    }

    public static String formatNumber(
            double value
    ) {
        return String.format(
                Locale.US,
                "%,.0f",
                sanitizeNonNegative(value)
        );
    }

    public static String formatPercent(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.1f%%",
                clampChance(value) * 100.0D
        );
    }

    private static String cleanSkillId(
            String rawSkillId
    ) {
        if (rawSkillId == null) {
            return "";
        }

        String cleaned = rawSkillId.trim();

        if (cleaned.length() > MAX_SKILL_ID_LENGTH) {
            return "";
        }

        return cleaned;
    }

    private static double sanitizeNonNegative(
            double value
    ) {
        return !Double.isFinite(value)
                || value < 0.0D
                ? 0.0D
                : value;
    }

    private static double clampChance(
            double value
    ) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(
                0.0D,
                Math.min(1.0D, value)
        );
    }

    public record RequestAnalysis(
            List<String> uniqueSkillIds,
            int rejectedCount,
            boolean overLimit
    ) {

        public RequestAnalysis {
            uniqueSkillIds = uniqueSkillIds == null
                    ? List.of()
                    : List.copyOf(
                    new ArrayList<>(uniqueSkillIds)
            );
            rejectedCount = Math.max(0, rejectedCount);
        }
    }
}