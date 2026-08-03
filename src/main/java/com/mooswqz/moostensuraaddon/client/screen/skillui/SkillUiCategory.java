package com.mooswqz.moostensuraaddon.client.screen.skillui;

import com.mooswqz.moostensuraaddon.util.UiFinalPolicy;
import net.minecraft.network.chat.Component;

import java.util.Comparator;

/**
 * Canonical category order shared by every skill-facing screen.
 */
public enum SkillUiCategory {

    UNIQUE(
            0,
            UiFinalPolicy.CATEGORY_UNIQUE,
            "category.unique",
            0xD6A5FF
    ),

    EXTRA(
            1,
            UiFinalPolicy.CATEGORY_EXTRA,
            "category.extra",
            0x71C7FF
    ),

    BASIC(
            2,
            UiFinalPolicy.CATEGORY_BASIC,
            "category.basic",
            0xD0D0D0
    ),

    RESISTANCE(
            3,
            UiFinalPolicy.CATEGORY_RESISTANCE,
            "category.resistance",
            0x71E0B8
    ),

    OTHER(
            4,
            UiFinalPolicy.CATEGORY_OTHER,
            "category.other",
            0xA0A0A0
    );

    public static final Comparator<SkillUiCategory> DISPLAY_ORDER =
            Comparator.comparingInt(
                    SkillUiCategory::order
            );

    private final int order;
    private final String id;
    private final String translationKey;
    private final int defaultAccentColor;

    SkillUiCategory(
            int order,
            String id,
            String translationKey,
            int defaultAccentColor
    ) {
        this.order = order;
        this.id = id;
        this.translationKey = translationKey;
        this.defaultAccentColor = defaultAccentColor;
    }

    public int order() {
        return order;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return SkillUiText.component(translationKey);
    }

    public int defaultAccentColor() {
        return defaultAccentColor;
    }

    public static SkillUiCategory fromRaw(
            String rawCategory
    ) {
        return switch (UiFinalPolicy.canonicalCategoryId(rawCategory)) {
            case UiFinalPolicy.CATEGORY_UNIQUE -> UNIQUE;
            case UiFinalPolicy.CATEGORY_EXTRA -> EXTRA;
            case UiFinalPolicy.CATEGORY_BASIC -> BASIC;
            case UiFinalPolicy.CATEGORY_RESISTANCE -> RESISTANCE;
            default -> OTHER;
        };
    }
}