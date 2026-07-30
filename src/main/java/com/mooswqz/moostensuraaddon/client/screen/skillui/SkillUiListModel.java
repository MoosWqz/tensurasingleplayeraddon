package com.mooswqz.moostensuraaddon.client.screen.skillui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Shared filtered, grouped and scrollable list state.
 */
public final class SkillUiListModel {

    public static final int CATEGORY_ROW_HEIGHT = 18;
    public static final int SKILL_ROW_HEIGHT = 26;

    private static final Comparator<SkillUiEntry> ENTRY_ORDER =
            Comparator.comparing(
                            SkillUiEntry::category,
                            SkillUiCategory.DISPLAY_ORDER
                    )
                    .thenComparing(
                            entry -> entry
                                    .displayName()
                                    .getString(),
                            String.CASE_INSENSITIVE_ORDER
                    )
                    .thenComparing(
                            SkillUiEntry::skillId,
                            String.CASE_INSENSITIVE_ORDER
                    );

    private final SkillUiFilterState filterState;
    private final List<SkillUiEntry> allEntries =
            new ArrayList<>();
    private final List<Row> rows =
            new ArrayList<>();

    private int viewportHeight;
    private double scrollOffset;
    private int focusedRowIndex = -1;

    public SkillUiListModel(
            SkillUiFilterState filterState
    ) {
        this.filterState = filterState == null
                ? new SkillUiFilterState()
                : filterState;
    }

    public SkillUiFilterState filterState() {
        return filterState;
    }

    public void setEntries(
            List<SkillUiEntry> entries
    ) {
        allEntries.clear();

        if (entries != null) {
            entries.stream()
                    .filter(entry -> entry != null)
                    .sorted(ENTRY_ORDER)
                    .forEach(allEntries::add);
        }

        rebuild();
    }

    public List<SkillUiEntry> allEntries() {
        return List.copyOf(allEntries);
    }

    public List<SkillUiEntry> filteredEntries() {
        return rows.stream()
                .filter(row -> row.entry() != null)
                .map(Row::entry)
                .toList();
    }

    public List<Row> rows() {
        return List.copyOf(rows);
    }

    public void rebuild() {
        String focusedSkillId = focusedEntry()
                .map(SkillUiEntry::skillId)
                .orElse("");

        rows.clear();

        SkillUiCategory currentCategory = null;

        for (SkillUiEntry entry : allEntries) {
            if (!filterState.accepts(entry)) {
                continue;
            }

            if (currentCategory != entry.category()) {
                currentCategory = entry.category();
                rows.add(
                        Row.category(
                                currentCategory
                        )
                );
            }

            rows.add(
                    Row.skill(entry)
            );
        }

        focusedRowIndex = findSkillRow(
                focusedSkillId
        );

        if (focusedRowIndex < 0) {
            focusedRowIndex = findNextSkillRow(
                    -1,
                    1
            );
        }

        clampScroll();
        ensureFocusedVisible();
    }

    public int viewportHeight() {
        return viewportHeight;
    }

    public void setViewportHeight(
            int viewportHeight
    ) {
        this.viewportHeight = Math.max(
                0,
                viewportHeight
        );
        clampScroll();
    }

    public int contentHeight() {
        int height = 0;

        for (Row row : rows) {
            height += row.height();
        }

        return height;
    }

    public double scrollOffset() {
        return scrollOffset;
    }

    public double maximumScroll() {
        return Math.max(
                0.0D,
                contentHeight()
                        - viewportHeight
        );
    }

    public boolean canScroll() {
        return maximumScroll() > 0.0D;
    }

    public void setScrollOffset(
            double scrollOffset
    ) {
        this.scrollOffset = scrollOffset;
        clampScroll();
    }

    public void scrollBy(
            double pixels
    ) {
        setScrollOffset(
                scrollOffset + pixels
        );
    }

    public void scrollRows(
            double rowDelta
    ) {
        scrollBy(
                rowDelta * SKILL_ROW_HEIGHT
        );
    }

    public List<PositionedRow> visibleRows() {
        List<PositionedRow> result =
                new ArrayList<>();

        int contentY = 0;
        int viewportTop = (int) Math.floor(
                scrollOffset
        );
        int viewportBottom = viewportTop
                + viewportHeight;

        for (int index = 0;
             index < rows.size();
             index++) {

            Row row = rows.get(index);
            int rowTop = contentY;
            int rowBottom = rowTop
                    + row.height();

            if (rowBottom > viewportTop
                    && rowTop < viewportBottom) {
                result.add(
                        new PositionedRow(
                                index,
                                row,
                                rowTop - viewportTop
                        )
                );
            }

            contentY = rowBottom;
        }

        return List.copyOf(result);
    }

    public Optional<Row> rowAt(
            int viewportY
    ) {
        if (viewportY < 0
                || viewportY >= viewportHeight) {
            return Optional.empty();
        }

        int contentY = (int) Math.floor(
                scrollOffset
        ) + viewportY;
        int currentTop = 0;

        for (Row row : rows) {
            int bottom = currentTop
                    + row.height();

            if (contentY >= currentTop
                    && contentY < bottom) {
                return Optional.of(row);
            }

            currentTop = bottom;
        }

        return Optional.empty();
    }

    public Optional<SkillUiEntry> entryAt(
            int viewportY
    ) {
        return rowAt(viewportY)
                .map(Row::entry)
                .filter(entry -> entry != null);
    }

    public Optional<SkillUiEntry> focusedEntry() {
        if (focusedRowIndex < 0
                || focusedRowIndex >= rows.size()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                rows.get(focusedRowIndex)
                        .entry()
        );
    }

    public int focusedRowIndex() {
        return focusedRowIndex;
    }

    public boolean focusEntry(
            String skillId
    ) {
        int rowIndex = findSkillRow(skillId);

        if (rowIndex < 0) {
            return false;
        }

        focusedRowIndex = rowIndex;
        ensureFocusedVisible();
        return true;
    }

    public Optional<SkillUiEntry> moveFocus(
            int direction
    ) {
        if (rows.isEmpty()) {
            focusedRowIndex = -1;
            return Optional.empty();
        }

        int normalizedDirection = direction < 0
                ? -1
                : 1;

        int start = focusedRowIndex < 0
                ? normalizedDirection > 0
                  ? -1
                  : rows.size()
                : focusedRowIndex;

        int next = findNextSkillRow(
                start,
                normalizedDirection
        );

        if (next >= 0) {
            focusedRowIndex = next;
            ensureFocusedVisible();
        }

        return focusedEntry();
    }

    public Optional<SkillUiEntry> focusFirst() {
        int next = findNextSkillRow(
                -1,
                1
        );

        if (next >= 0) {
            focusedRowIndex = next;
            ensureFocusedVisible();
        }

        return focusedEntry();
    }

    public Optional<SkillUiEntry> focusLast() {
        int next = findNextSkillRow(
                rows.size(),
                -1
        );

        if (next >= 0) {
            focusedRowIndex = next;
            ensureFocusedVisible();
        }

        return focusedEntry();
    }

    private int findSkillRow(
            String skillId
    ) {
        if (skillId == null
                || skillId.isBlank()) {
            return -1;
        }

        for (int index = 0;
             index < rows.size();
             index++) {

            SkillUiEntry entry = rows.get(index)
                    .entry();

            if (entry != null
                    && skillId.equals(
                    entry.skillId()
            )) {
                return index;
            }
        }

        return -1;
    }

    private int findNextSkillRow(
            int start,
            int direction
    ) {
        int index = start + direction;

        while (index >= 0
                && index < rows.size()) {
            if (rows.get(index).entry() != null) {
                return index;
            }

            index += direction;
        }

        return -1;
    }

    private void ensureFocusedVisible() {
        if (focusedRowIndex < 0
                || focusedRowIndex >= rows.size()
                || viewportHeight <= 0) {
            return;
        }

        int rowTop = 0;

        for (int index = 0;
             index < focusedRowIndex;
             index++) {
            rowTop += rows.get(index).height();
        }

        int rowBottom = rowTop
                + rows.get(focusedRowIndex).height();

        if (rowTop < scrollOffset) {
            setScrollOffset(rowTop);
            return;
        }

        if (rowBottom > scrollOffset
                + viewportHeight) {
            setScrollOffset(
                    rowBottom - viewportHeight
            );
        }
    }

    private void clampScroll() {
        scrollOffset = Math.max(
                0.0D,
                Math.min(
                        maximumScroll(),
                        scrollOffset
                )
        );
    }

    public record Row(
            SkillUiCategory category,
            SkillUiEntry entry,
            int height
    ) {

        public static Row category(
                SkillUiCategory category
        ) {
            return new Row(
                    category,
                    null,
                    CATEGORY_ROW_HEIGHT
            );
        }

        public static Row skill(
                SkillUiEntry entry
        ) {
            return new Row(
                    entry.category(),
                    entry,
                    SKILL_ROW_HEIGHT
            );
        }

        public boolean categoryHeader() {
            return entry == null;
        }
    }

    public record PositionedRow(
            int rowIndex,
            Row row,
            int y
    ) {
    }
}