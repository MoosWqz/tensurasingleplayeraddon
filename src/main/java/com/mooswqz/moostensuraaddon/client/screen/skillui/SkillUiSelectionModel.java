package com.mooswqz.moostensuraaddon.client.screen.skillui;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared single- and multi-selection state with a strict selection ceiling.
 */
public final class SkillUiSelectionModel {

    public static final int DEFAULT_MAX_MULTI_SELECTION = 64;

    private final SkillUiSelectionMode mode;
    private final int maximumSelection;
    private final LinkedHashSet<String> selectedSkillIds =
            new LinkedHashSet<>();

    public SkillUiSelectionModel(
            SkillUiSelectionMode mode
    ) {
        this(
                mode,
                mode == SkillUiSelectionMode.SINGLE
                        ? 1
                        : DEFAULT_MAX_MULTI_SELECTION
        );
    }

    public SkillUiSelectionModel(
            SkillUiSelectionMode mode,
            int maximumSelection
    ) {
        this.mode = mode == null
                ? SkillUiSelectionMode.READ_ONLY
                : mode;

        this.maximumSelection = switch (this.mode) {
            case READ_ONLY -> 0;
            case SINGLE -> 1;
            case MULTI -> Math.max(
                    1,
                    maximumSelection
            );
        };
    }

    public SkillUiSelectionMode mode() {
        return mode;
    }

    public int maximumSelection() {
        return maximumSelection;
    }

    public int selectedCount() {
        return selectedSkillIds.size();
    }

    public boolean isSelected(
            String skillId
    ) {
        return skillId != null
                && selectedSkillIds.contains(skillId);
    }

    public Set<String> selectedSkillIds() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(
                        selectedSkillIds
                )
        );
    }

    public ToggleResult toggle(
            SkillUiEntry entry
    ) {
        if (entry == null) {
            return ToggleResult.NO_ENTRY;
        }

        if (mode == SkillUiSelectionMode.READ_ONLY) {
            return ToggleResult.READ_ONLY;
        }

        if (!entry.selectable()) {
            return ToggleResult.NOT_SELECTABLE;
        }

        String skillId = entry.skillId();

        if (selectedSkillIds.remove(skillId)) {
            return ToggleResult.DESELECTED;
        }

        if (mode == SkillUiSelectionMode.SINGLE) {
            selectedSkillIds.clear();
            selectedSkillIds.add(skillId);
            return ToggleResult.SELECTED;
        }

        if (selectedSkillIds.size() >= maximumSelection) {
            return ToggleResult.LIMIT_REACHED;
        }

        selectedSkillIds.add(skillId);
        return ToggleResult.SELECTED;
    }

    public int selectAllVisible(
            Collection<SkillUiEntry> visibleEntries
    ) {
        if (mode != SkillUiSelectionMode.MULTI
                || visibleEntries == null
                || visibleEntries.isEmpty()) {
            return 0;
        }

        int previousCount = selectedSkillIds.size();

        for (SkillUiEntry entry : visibleEntries) {
            if (entry == null
                    || !entry.selectable()
                    || selectedSkillIds.contains(
                    entry.skillId()
            )) {
                continue;
            }

            if (selectedSkillIds.size()
                    >= maximumSelection) {
                break;
            }

            selectedSkillIds.add(
                    entry.skillId()
            );
        }

        return selectedSkillIds.size()
                - previousCount;
    }

    public void replaceSelection(
            Collection<String> skillIds,
            Collection<SkillUiEntry> availableEntries
    ) {
        selectedSkillIds.clear();

        if (mode == SkillUiSelectionMode.READ_ONLY
                || skillIds == null
                || skillIds.isEmpty()
                || availableEntries == null
                || availableEntries.isEmpty()) {
            return;
        }

        List<String> selectableIds = availableEntries
                .stream()
                .filter(entry -> entry != null
                        && entry.selectable())
                .map(SkillUiEntry::skillId)
                .toList();

        for (String skillId : skillIds) {
            if (skillId == null
                    || !selectableIds.contains(skillId)) {
                continue;
            }

            selectedSkillIds.add(skillId);

            if (selectedSkillIds.size()
                    >= maximumSelection) {
                break;
            }
        }
    }

    public void retainAvailable(
            Collection<SkillUiEntry> availableEntries
    ) {
        if (availableEntries == null) {
            selectedSkillIds.clear();
            return;
        }

        Set<String> selectableIds = availableEntries
                .stream()
                .filter(entry -> entry != null
                        && entry.selectable())
                .map(SkillUiEntry::skillId)
                .collect(
                        java.util.stream.Collectors.toSet()
                );

        selectedSkillIds.retainAll(
                selectableIds
        );
    }

    public void clear() {
        selectedSkillIds.clear();
    }

    public enum ToggleResult {
        SELECTED,
        DESELECTED,
        READ_ONLY,
        NOT_SELECTABLE,
        LIMIT_REACHED,
        NO_ENTRY
    }
}