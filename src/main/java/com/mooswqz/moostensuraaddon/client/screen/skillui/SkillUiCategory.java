package com.mooswqz.moostensuraaddon.client.screen.skillui;

import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.Locale;

/**
 * Canonical category order shared by every skill-facing screen.
 */
public enum SkillUiCategory {

    UNIQUE(
            0,
            "unique",
            Component.literal("Unique Skills"),
            0xD6A5FF
    ),

    EXTRA(
            1,
            "extra",
            Component.literal("Extra Skills"),
            0x71C7FF
    ),

    BASIC(
            2,
            "basic",
            Component.literal("Basic Skills"),
            0xD0D0D0
    ),

    RESISTANCE(
            3,
            "resistance",
            Component.literal("Resistances"),
            0x71E0B8
    ),

    OTHER(
            4,
            "other",
            Component.literal("Other Skills"),
            0xA0A0A0
    );

    public static final Comparator<SkillUiCategory> DISPLAY_ORDER =
            Comparator.comparingInt(
                    SkillUiCategory::order
            );

    private final int order;
    private final String id;
    private final Component displayName;
    private final int defaultAccentColor;

    SkillUiCategory(
            int order,
            String id,
            Component displayName,
            int defaultAccentColor
    ) {
        this.order = order;
        this.id = id;
        this.displayName = displayName;
        this.defaultAccentColor = defaultAccentColor;
    }

    public int order() {
        return order;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return displayName;
    }

    public int defaultAccentColor() {
        return defaultAccentColor;
    }

    public static SkillUiCategory fromRaw(
            String rawCategory
    ) {
        if (rawCategory == null
                || rawCategory.isBlank()) {
            return OTHER;
        }

        String normalized = rawCategory
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        if (normalized.contains("unique")
                || normalized.contains("ultimate")) {
            return UNIQUE;
        }

        if (normalized.contains("extra")) {
            return EXTRA;
        }

        if (normalized.contains("basic")
                || normalized.contains("common")) {
            return BASIC;
        }

        if (normalized.contains("resistance")
                || normalized.contains("resist")) {
            return RESISTANCE;
        }

        return OTHER;
    }
}