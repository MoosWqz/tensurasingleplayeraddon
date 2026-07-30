package com.mooswqz.moostensuraaddon.client.screen.skillui;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Search and category visibility shared by every skill list.
 */
public final class SkillUiFilterState {

    private final EnumSet<SkillUiCategory> visibleCategories =
            EnumSet.allOf(
                    SkillUiCategory.class
            );

    private String normalizedQuery = "";
    private boolean showUnavailable = true;

    public String query() {
        return normalizedQuery;
    }

    public void setQuery(
            String query
    ) {
        normalizedQuery = query == null
                ? ""
                : query.trim()
                .toLowerCase(Locale.ROOT);
    }

    public boolean showUnavailable() {
        return showUnavailable;
    }

    public void setShowUnavailable(
            boolean showUnavailable
    ) {
        this.showUnavailable = showUnavailable;
    }

    public Set<SkillUiCategory> visibleCategories() {
        return Set.copyOf(visibleCategories);
    }

    public boolean isCategoryVisible(
            SkillUiCategory category
    ) {
        return category != null
                && visibleCategories.contains(category);
    }

    public boolean setCategoryVisible(
            SkillUiCategory category,
            boolean visible
    ) {
        if (category == null) {
            return false;
        }

        boolean changed;

        if (visible) {
            changed = visibleCategories.add(category);
        } else {
            changed = visibleCategories.remove(category);
        }

        return changed;
    }

    public boolean toggleCategory(
            SkillUiCategory category
    ) {
        if (category == null) {
            return false;
        }

        if (visibleCategories.contains(category)) {
            visibleCategories.remove(category);
            return false;
        }

        visibleCategories.add(category);
        return true;
    }

    public void showAllCategories() {
        visibleCategories.clear();
        visibleCategories.addAll(
                EnumSet.allOf(
                        SkillUiCategory.class
                )
        );
    }

    public boolean accepts(
            SkillUiEntry entry
    ) {
        if (entry == null
                || !visibleCategories.contains(
                entry.category()
        )) {
            return false;
        }

        if (!showUnavailable
                && !entry.selectable()) {
            return false;
        }

        return normalizedQuery.isBlank()
                || entry.normalizedSearchText()
                .contains(normalizedQuery);
    }
}