package com.mooswqz.moostensuraaddon.util;

import java.util.Locale;

/**
 * Pure, side-neutral rules used by the final Skill UI polish pass.
 */
public final class UiFinalPolicy {

    public static final String CATEGORY_UNIQUE = "unique";
    public static final String CATEGORY_EXTRA = "extra";
    public static final String CATEGORY_BASIC = "basic";
    public static final String CATEGORY_RESISTANCE = "resistance";
    public static final String CATEGORY_OTHER = "other";

    private UiFinalPolicy() {
    }

    public static String canonicalCategoryId(
            String rawCategory
    ) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return CATEGORY_OTHER;
        }

        String normalized = rawCategory
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        if (normalized.contains("unique")
                || normalized.contains("ultimate")) {
            return CATEGORY_UNIQUE;
        }

        if (normalized.contains("extra")) {
            return CATEGORY_EXTRA;
        }

        if (normalized.contains("basic")
                || normalized.contains("common")) {
            return CATEGORY_BASIC;
        }

        if (normalized.contains("resistance")
                || normalized.contains("resist")) {
            return CATEGORY_RESISTANCE;
        }

        return CATEGORY_OTHER;
    }

    public static int categoryOrder(
            String categoryId
    ) {
        return switch (canonicalCategoryId(categoryId)) {
            case CATEGORY_UNIQUE -> 0;
            case CATEGORY_EXTRA -> 1;
            case CATEGORY_BASIC -> 2;
            case CATEGORY_RESISTANCE -> 3;
            default -> 4;
        };
    }

    public static boolean shouldShowOnboarding(
            int seenRevision,
            int currentRevision
    ) {
        return Math.max(0, seenRevision)
                < Math.max(1, currentRevision);
    }
}