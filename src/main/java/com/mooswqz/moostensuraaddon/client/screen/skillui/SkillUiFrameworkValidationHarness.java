package com.mooswqz.moostensuraaddon.client.screen.skillui;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic checks for the shared skill UI data and interaction model.
 */
public final class SkillUiFrameworkValidationHarness {

    private SkillUiFrameworkValidationHarness() {
    }

    public static ValidationResult validate() {
        List<String> issues = new ArrayList<>();
        int passed = 0;
        int total = 0;

        total++;
        if (SkillUiCategory.fromRaw("Unique Skill")
                == SkillUiCategory.UNIQUE) {
            passed++;
        } else {
            issues.add("Unique category alias did not resolve.");
        }

        total++;
        if (SkillUiCategory.fromRaw("ultimate")
                == SkillUiCategory.UNIQUE) {
            passed++;
        } else {
            issues.add("Ultimate category alias did not resolve as Unique.");
        }

        total++;
        if (SkillUiCategory.fromRaw("Resistance")
                == SkillUiCategory.RESISTANCE) {
            passed++;
        } else {
            issues.add("Resistance category alias did not resolve.");
        }

        total++;
        if (SkillUiCategory.fromRaw("unknown")
                == SkillUiCategory.OTHER) {
            passed++;
        } else {
            issues.add("Unknown category did not fall back to Other.");
        }

        List<SkillUiEntry> entries = sampleEntries();

        SkillUiFilterState filterState =
                new SkillUiFilterState();
        SkillUiListModel listModel =
                new SkillUiListModel(filterState);
        listModel.setViewportHeight(50);
        listModel.setEntries(entries);

        total++;
        if (listModel.filteredEntries().size() == 4) {
            passed++;
        } else {
            issues.add("Initial list did not contain every sample skill.");
        }

        total++;
        if (listModel.rows().stream()
                .filter(SkillUiListModel.Row::categoryHeader)
                .count() == 4L) {
            passed++;
        } else {
            issues.add("Category headers were not created deterministically.");
        }

        filterState.setQuery("fire");
        listModel.rebuild();

        total++;
        if (listModel.filteredEntries().size() == 1
                && "example:fire".equals(
                listModel.filteredEntries()
                        .getFirst()
                        .skillId()
        )) {
            passed++;
        } else {
            issues.add("Search filtering did not match display name and ID.");
        }

        filterState.setQuery("");
        filterState.setCategoryVisible(
                SkillUiCategory.BASIC,
                false
        );
        listModel.rebuild();

        total++;
        if (listModel.filteredEntries().stream()
                .noneMatch(entry -> entry.category()
                        == SkillUiCategory.BASIC)) {
            passed++;
        } else {
            issues.add("Category visibility filter did not exclude Basic skills.");
        }

        filterState.showAllCategories();
        filterState.setShowUnavailable(false);
        listModel.rebuild();

        total++;
        if (listModel.filteredEntries().stream()
                .noneMatch(entry -> !entry.selectable())) {
            passed++;
        } else {
            issues.add("Unavailable-skill filter did not hide disabled entries.");
        }

        filterState.setShowUnavailable(true);
        listModel.rebuild();
        listModel.setScrollOffset(10_000.0D);

        total++;
        if (listModel.scrollOffset()
                == listModel.maximumScroll()) {
            passed++;
        } else {
            issues.add("Scroll offset was not clamped to its maximum.");
        }

        listModel.setScrollOffset(-25.0D);

        total++;
        if (listModel.scrollOffset() == 0.0D) {
            passed++;
        } else {
            issues.add("Negative scroll offset was not clamped to zero.");
        }

        total++;
        if (listModel.focusFirst().isPresent()
                && listModel.focusedEntry().isPresent()) {
            passed++;
        } else {
            issues.add("Keyboard focus could not select the first skill row.");
        }

        SkillUiSelectionModel singleSelection =
                new SkillUiSelectionModel(
                        SkillUiSelectionMode.SINGLE
                );

        singleSelection.toggle(entries.get(0));
        singleSelection.toggle(entries.get(1));

        total++;
        if (singleSelection.selectedCount() == 1
                && singleSelection.isSelected(
                entries.get(1).skillId()
        )) {
            passed++;
        } else {
            issues.add("Single selection retained more than one skill.");
        }

        SkillUiSelectionModel multiSelection =
                new SkillUiSelectionModel(
                        SkillUiSelectionMode.MULTI,
                        2
                );

        multiSelection.toggle(entries.get(0));
        multiSelection.toggle(entries.get(1));
        SkillUiSelectionModel.ToggleResult limitResult =
                multiSelection.toggle(entries.get(2));

        total++;
        if (multiSelection.selectedCount() == 2
                && limitResult
                == SkillUiSelectionModel.ToggleResult.LIMIT_REACHED) {
            passed++;
        } else {
            issues.add("Multi-selection ceiling was not enforced.");
        }

        SkillUiSelectionModel disabledSelection =
                new SkillUiSelectionModel(
                        SkillUiSelectionMode.MULTI
                );

        total++;
        if (disabledSelection.toggle(entries.get(3))
                == SkillUiSelectionModel.ToggleResult.NOT_SELECTABLE
                && disabledSelection.selectedCount() == 0) {
            passed++;
        } else {
            issues.add("Disabled skill could be selected.");
        }

        SkillUiSelectionModel readOnlySelection =
                new SkillUiSelectionModel(
                        SkillUiSelectionMode.READ_ONLY
                );

        total++;
        if (readOnlySelection.toggle(entries.get(0))
                == SkillUiSelectionModel.ToggleResult.READ_ONLY) {
            passed++;
        } else {
            issues.add("Read-only selection accepted a toggle.");
        }

        SkillUiLayout wideLayout =
                SkillUiLayout.calculate(
                        960,
                        540,
                        true
                );

        total++;
        if (!wideLayout.compact()
                && wideLayout.detailsVisible()
                && wideLayout.list().right()
                <= wideLayout.details().left()) {
            passed++;
        } else {
            issues.add("Wide layout did not create separate list and details columns.");
        }

        SkillUiLayout narrowLayout =
                SkillUiLayout.calculate(
                        360,
                        300,
                        true
                );

        total++;
        if (narrowLayout.compact()
                && narrowLayout.detailsVisible()
                && narrowLayout.list().bottom()
                <= narrowLayout.details().top()) {
            passed++;
        } else {
            issues.add("Compact layout did not stack list and details panels.");
        }

        SkillUiLayout tinyLayout =
                SkillUiLayout.calculate(
                        180,
                        120,
                        true
                );

        total++;
        if (tinyLayout.panel().width() > 0
                && tinyLayout.panel().height() > 0
                && tinyLayout.list().width() > 0
                && tinyLayout.list().height() > 0) {
            passed++;
        } else {
            issues.add("Tiny layout produced a non-positive required area.");
        }

        total++;
        if (SkillUiTheme.BENEVOLENT.accentColor()
                != SkillUiTheme.GOVERNANCE.accentColor()
                && SkillUiTheme.GRANTER.backgroundColor()
                == SkillUiTheme.BENEVOLENT.backgroundColor()) {
            passed++;
        } else {
            issues.add("Theme variants did not preserve structure while changing accents.");
        }

        total++;
        if (entries.getFirst().detailLines().getClass()
                .getName().contains("Immutable")
                || isUnmodifiable(entries.getFirst().detailLines())) {
            passed++;
        } else {
            issues.add("Skill detail lines were not defensively copied.");
        }

        return new ValidationResult(
                passed,
                total - passed,
                total,
                List.copyOf(issues)
        );
    }

    private static List<SkillUiEntry> sampleEntries() {
        return List.of(
                new SkillUiEntry(
                        "example:unique",
                        Component.literal("Authority"),
                        SkillUiCategory.UNIQUE,
                        true,
                        true,
                        Component.empty(),
                        Component.empty(),
                        List.of(
                                Component.literal("Unique example")
                        ),
                        0
                ),
                SkillUiEntry.simple(
                        "example:extra",
                        "Awareness",
                        SkillUiCategory.EXTRA,
                        true
                ),
                SkillUiEntry.simple(
                        "example:fire",
                        "Fire Manipulation",
                        SkillUiCategory.BASIC,
                        true
                ),
                new SkillUiEntry(
                        "example:resistance",
                        Component.literal("Heat Resistance"),
                        SkillUiCategory.RESISTANCE,
                        false,
                        false,
                        Component.literal("Not mastered"),
                        Component.empty(),
                        List.of(),
                        0
                )
        );
    }

    private static boolean isUnmodifiable(
            List<Component> lines
    ) {
        try {
            lines.add(
                    Component.literal("mutation")
            );
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }

    public record ValidationResult(
            int passed,
            int failed,
            int total,
            List<String> issues
    ) {

        public boolean successful() {
            return failed == 0;
        }
    }
}