package com.mooswqz.moostensuraaddon.client.screen.skillui;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Sanitised client-side presentation model for one skill row.
 */
public record SkillUiEntry(
        String skillId,
        Component displayName,
        SkillUiCategory category,
        boolean selectable,
        boolean mastered,
        Component disabledReason,
        Component sourceName,
        List<Component> detailLines,
        int accentColor
) {

    public SkillUiEntry {
        skillId = cleanId(skillId);
        displayName = displayName == null
                ? Component.literal(skillId)
                : displayName;
        category = category == null
                ? SkillUiCategory.OTHER
                : category;
        disabledReason = disabledReason == null
                ? Component.empty()
                : disabledReason;
        sourceName = sourceName == null
                ? Component.empty()
                : sourceName;
        detailLines = detailLines == null
                ? List.of()
                : List.copyOf(
                detailLines.stream()
                        .filter(line -> line != null)
                        .toList()
        );
        accentColor = accentColor == 0
                ? category.defaultAccentColor()
                : SkillUiTheme.opaque(accentColor);
    }

    public static SkillUiEntry simple(
            String skillId,
            String displayName,
            SkillUiCategory category,
            boolean selectable
    ) {
        return new SkillUiEntry(
                skillId,
                Component.literal(
                        displayName == null
                                ? skillId
                                : displayName
                ),
                category,
                selectable,
                false,
                Component.empty(),
                Component.empty(),
                List.of(),
                category == null
                        ? SkillUiCategory.OTHER
                        .defaultAccentColor()
                        : category.defaultAccentColor()
        );
    }

    public boolean hasDisabledReason() {
        return !disabledReason.getString().isBlank();
    }

    public boolean hasSourceName() {
        return !sourceName.getString().isBlank();
    }

    public String normalizedSearchText() {
        return (
                displayName.getString()
                        + " "
                        + skillId
                        + " "
                        + category.displayName().getString()
                        + " "
                        + sourceName.getString()
        )
                .toLowerCase(Locale.ROOT);
    }

    private static String cleanId(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value.trim();
    }
}