package com.mooswqz.moostensuraaddon.client.screen.skillui;

import com.mooswqz.moostensuraaddon.util.SkillCategoryHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic checks for the Granter selector's presentation policy.
 */
public final class GranterSkillUiValidationHarness {

    private GranterSkillUiValidationHarness() {
    }

    public static Report validate() {
        List<Check> checks = new ArrayList<>();

        check(
                checks,
                "mastered base skill is included",
                GranterSkillUiEntryFactory
                        .shouldIncludeForSelection(
                                true,
                                false,
                                true,
                                false
                        )
        );
        check(
                checks,
                "unmastered base skill is excluded",
                !GranterSkillUiEntryFactory
                        .shouldIncludeForSelection(
                                true,
                                false,
                                false,
                                false
                        )
        );
        check(
                checks,
                "unmastered skill is included for an Ultimate authority",
                GranterSkillUiEntryFactory
                        .shouldIncludeForSelection(
                                true,
                                false,
                                false,
                                true
                        )
        );
        check(
                checks,
                "intrinsic skill remains excluded",
                !GranterSkillUiEntryFactory
                        .shouldIncludeForSelection(
                                true,
                                true,
                                true,
                                true
                        )
        );
        check(
                checks,
                "non-grantable skill remains excluded",
                !GranterSkillUiEntryFactory
                        .shouldIncludeForSelection(
                                false,
                                false,
                                true,
                                true
                        )
        );

        checkCategory(
                checks,
                SkillCategoryHelper.SkillCategory.UNIQUE,
                SkillUiCategory.UNIQUE
        );
        checkCategory(
                checks,
                SkillCategoryHelper.SkillCategory.EXTRA,
                SkillUiCategory.EXTRA
        );
        checkCategory(
                checks,
                SkillCategoryHelper.SkillCategory.BASIC,
                SkillUiCategory.BASIC
        );
        checkCategory(
                checks,
                SkillCategoryHelper.SkillCategory.RESISTANCE,
                SkillUiCategory.RESISTANCE
        );
        checkCategory(
                checks,
                SkillCategoryHelper.SkillCategory.OTHER,
                SkillUiCategory.OTHER
        );
        check(
                checks,
                "null category maps to Other",
                GranterSkillUiEntryFactory
                        .toUiCategory(null)
                        == SkillUiCategory.OTHER
        );

        SkillUiFilterState filterState =
                new SkillUiFilterState();
        SkillUiListModel listModel =
                new SkillUiListModel(filterState);
        SkillUiSelectionModel selectionModel =
                new SkillUiSelectionModel(
                        SkillUiSelectionMode.SINGLE
                );

        SkillUiEntry unique = SkillUiEntry.simple(
                "example:unique",
                "Example Unique",
                SkillUiCategory.UNIQUE,
                true
        );
        SkillUiEntry extra = SkillUiEntry.simple(
                "example:extra",
                "Example Extra",
                SkillUiCategory.EXTRA,
                true
        );

        listModel.setEntries(
                List.of(extra, unique)
        );
        check(
                checks,
                "shared list sorts Unique before Extra",
                listModel.filteredEntries().equals(
                        List.of(unique, extra)
                )
        );

        selectionModel.toggle(unique);
        selectionModel.toggle(extra);
        check(
                checks,
                "single selection replaces the previous skill",
                selectionModel.selectedCount() == 1
                        && selectionModel.isSelected(
                        extra.skillId()
                )
        );

        filterState.setQuery("unique");
        listModel.rebuild();
        check(
                checks,
                "search narrows the shared list",
                listModel.filteredEntries().equals(
                        List.of(unique)
                )
        );

        filterState.setQuery("");
        filterState.setCategoryVisible(
                SkillUiCategory.UNIQUE,
                false
        );
        listModel.rebuild();
        check(
                checks,
                "category toggle hides only that category",
                listModel.filteredEntries().equals(
                        List.of(extra)
                )
        );

        long passed = checks.stream()
                .filter(Check::passed)
                .count();

        return new Report(
                List.copyOf(checks),
                (int) passed,
                checks.size() - (int) passed
        );
    }

    private static void checkCategory(
            List<Check> checks,
            SkillCategoryHelper.SkillCategory source,
            SkillUiCategory expected
    ) {
        check(
                checks,
                source.name() + " category mapping",
                GranterSkillUiEntryFactory
                        .toUiCategory(source)
                        == expected
        );
    }

    private static void check(
            List<Check> checks,
            String name,
            boolean passed
    ) {
        checks.add(
                new Check(name, passed)
        );
    }

    public record Check(
            String name,
            boolean passed
    ) {
    }

    public record Report(
            List<Check> checks,
            int passed,
            int failed
    ) {

        public boolean success() {
            return failed == 0;
        }
    }
}