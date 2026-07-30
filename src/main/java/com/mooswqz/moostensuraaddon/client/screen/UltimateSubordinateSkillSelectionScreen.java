package com.mooswqz.moostensuraaddon.client.screen;

import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillDetailsPanel;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillListWidget;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiButton;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiCategory;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiEntry;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiFilterState;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiLayout;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiListModel;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiRenderHelper;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiSelectionMode;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiSelectionModel;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiTheme;
import com.mooswqz.moostensuraaddon.client.screen.skillui.UltimateBorrowSeizeUiEntryFactory;
import com.mooswqz.moostensuraaddon.network.ExecuteUltimateSubordinateSkillPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateSubordinateSkillScreenPayload;
import com.mooswqz.moostensuraaddon.util.UltimateBorrowSeizePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UltimateSubordinateSkillSelectionScreen
        extends Screen {

    private static final int FILTER_GAP = 4;
    private static final int FOOTER_GAP = 4;

    private final OpenUltimateSubordinateSkillScreenPayload payload;
    private final SkillUiTheme theme;
    private final SkillUiFilterState filterState =
            new SkillUiFilterState();
    private final SkillUiSelectionModel selectionModel =
            new SkillUiSelectionModel(
                    SkillUiSelectionMode.MULTI,
                    UltimateBorrowSeizePolicy.MAX_SELECTED_SKILLS
            );
    private final EnumMap<SkillUiCategory, SkillUiButton>
            categoryButtons = new EnumMap<>(
            SkillUiCategory.class
    );

    private List<SkillUiEntry> entries = List.of();
    private Map<String, Double> borrowChances = Map.of();
    private SkillUiLayout layout;
    private SkillUiListModel listModel;
    private SkillListWidget listWidget;
    private EditBox searchBox;
    private SkillUiButton selectShownButton;
    private SkillUiButton clearButton;
    private SkillUiButton cancelButton;
    private SkillUiButton confirmButton;
    private SkillUiEntry focusedEntry;
    private String retainedQuery = "";
    private Component statusMessage = Component.empty();
    private int statusColor;

    public UltimateSubordinateSkillSelectionScreen(
            OpenUltimateSubordinateSkillScreenPayload payload
    ) {
        super(
                Component.literal(
                        payload != null && payload.seize()
                                ? "Seize Skills"
                                : "Borrow Skills"
                )
        );

        this.payload = payload == null
                ? new OpenUltimateSubordinateSkillScreenPayload(
                false,
                "",
                "Unknown subordinate",
                0.0D,
                0.0D,
                0.0D,
                List.of()
        )
                : payload;
        this.theme = this.payload.seize()
                ? SkillUiTheme.SEIZE
                : SkillUiTheme.BENEVOLENT;
        this.statusColor = this.theme.mutedTextColor();
    }

    public static void open(
            OpenUltimateSubordinateSkillScreenPayload payload
    ) {
        Minecraft.getInstance().setScreen(
                new UltimateSubordinateSkillSelectionScreen(
                        payload
                )
        );
    }

    @Override
    protected void init() {
        List<String> previousSelection = new ArrayList<>(
                selectionModel.selectedSkillIds()
        );
        UltimateBorrowSeizeUiEntryFactory.BuildResult result =
                UltimateBorrowSeizeUiEntryFactory.build(
                        payload
                );

        entries = result.entries();
        borrowChances = result.borrowChances();
        selectionModel.replaceSelection(
                previousSelection,
                entries
        );
        filterState.setQuery(retainedQuery);
        layout = SkillUiLayout.calculate(
                width,
                height,
                true
        );
        listModel = new SkillUiListModel(
                filterState
        );
        listWidget = new SkillListWidget(
                layout.list().left(),
                layout.list().top(),
                layout.list().width(),
                layout.list().height(),
                font,
                theme,
                listModel,
                selectionModel
        );
        listWidget.setEntries(entries);
        listWidget.setFocusListener(entry -> {
            focusedEntry = entry;
            clearStatus();
        });
        listWidget.setSelectionListener(
                this::onSelectionChanged
        );

        if (focusedEntry != null
                && listModel.focusEntry(
                focusedEntry.skillId()
        )) {
            focusedEntry = findEntry(
                    focusedEntry.skillId()
            ).orElse(null);
        }

        if (focusedEntry == null) {
            listModel.focusFirst()
                    .ifPresent(entry ->
                            focusedEntry = entry
                    );
        }

        addRenderableWidget(listWidget);
        createFilterControls();
        createFooterControls();
        updateControls();
    }

    private void createFilterControls() {
        categoryButtons.clear();

        SkillUiLayout.Rect filters = layout.filters();
        int buttonHeight = Math.min(
                20,
                Math.max(16, filters.height())
        );
        int controlY = filters.top()
                + Math.max(
                0,
                (filters.height() - buttonHeight) / 2
        );
        int categoryCount = SkillUiCategory.values().length;
        int desiredSearchWidth = Math.max(
                60,
                Math.min(
                        210,
                        filters.width() * 31 / 100
                )
        );
        int minimumCategoryWidth = filters.width() < 390
                ? 18
                : 42;
        int maximumSearchWidth = filters.width()
                - FILTER_GAP
                - categoryCount * minimumCategoryWidth
                - (categoryCount - 1) * FILTER_GAP;
        int searchWidth = Math.max(
                50,
                Math.min(
                        desiredSearchWidth,
                        maximumSearchWidth
                )
        );

        searchBox = new EditBox(
                font,
                filters.left(),
                controlY,
                searchWidth,
                buttonHeight,
                Component.literal("Search skills")
        );
        searchBox.setMaxLength(80);
        searchBox.setHint(
                Component.literal("Search skills…")
        );
        searchBox.setValue(retainedQuery);
        searchBox.setResponder(
                this::onSearchChanged
        );
        addRenderableWidget(searchBox);

        int categoriesLeft = filters.left()
                + searchWidth
                + FILTER_GAP;
        int categoriesWidth = Math.max(
                1,
                filters.right() - categoriesLeft
        );
        int totalGaps = FILTER_GAP
                * (categoryCount - 1);
        int categoryButtonWidth = Math.max(
                1,
                (categoriesWidth - totalGaps)
                        / categoryCount
        );
        int buttonX = categoriesLeft;

        for (SkillUiCategory category :
                SkillUiCategory.values()) {
            int availableRight = filters.right()
                    - buttonX;
            int widthForButton = category
                    == SkillUiCategory.OTHER
                    ? Math.max(1, availableRight)
                    : categoryButtonWidth;
            SkillUiButton button = new SkillUiButton(
                    buttonX,
                    controlY,
                    widthForButton,
                    buttonHeight,
                    categoryButtonLabel(
                            category,
                            widthForButton
                    ),
                    theme,
                    SkillUiButton.Tone.NORMAL,
                    () -> toggleCategory(category)
            );

            button.setHighlighted(
                    filterState.isCategoryVisible(category)
            );
            button.setTooltip(
                    Tooltip.create(
                            category.displayName()
                    )
            );
            categoryButtons.put(category, button);
            addRenderableWidget(button);
            buttonX += widthForButton + FILTER_GAP;
        }
    }

    private void createFooterControls() {
        SkillUiLayout.Rect footer = layout.footer();
        int buttonHeight = Math.min(
                20,
                Math.max(16, footer.height() - 4)
        );
        int buttonY = footer.top()
                + Math.max(
                0,
                (footer.height() - buttonHeight) / 2
        );
        int buttonCount = 4;
        int buttonWidth = Math.max(
                1,
                (footer.width()
                        - FOOTER_GAP * (buttonCount - 1))
                        / buttonCount
        );
        boolean compactLabels = buttonWidth < 82;
        int buttonX = footer.left();

        selectShownButton = new SkillUiButton(
                buttonX,
                buttonY,
                buttonWidth,
                buttonHeight,
                Component.literal(
                        compactLabels
                                ? "All Shown"
                                : "Select Shown"
                ),
                theme,
                SkillUiButton.Tone.NORMAL,
                this::selectShown
        );
        selectShownButton.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Select every filtered skill, up to the 32-skill safety limit."
                        )
                )
        );
        addRenderableWidget(selectShownButton);
        buttonX += buttonWidth + FOOTER_GAP;

        clearButton = new SkillUiButton(
                buttonX,
                buttonY,
                buttonWidth,
                buttonHeight,
                Component.literal("Clear"),
                theme,
                SkillUiButton.Tone.NORMAL,
                this::clearSelection
        );
        addRenderableWidget(clearButton);
        buttonX += buttonWidth + FOOTER_GAP;

        cancelButton = new SkillUiButton(
                buttonX,
                buttonY,
                buttonWidth,
                buttonHeight,
                Component.literal("Cancel"),
                theme,
                SkillUiButton.Tone.NORMAL,
                this::onClose
        );
        addRenderableWidget(cancelButton);
        buttonX += buttonWidth + FOOTER_GAP;

        confirmButton = new SkillUiButton(
                buttonX,
                buttonY,
                Math.max(1, footer.right() - buttonX),
                buttonHeight,
                Component.literal(
                        payload.seize()
                                ? "Seize Skills"
                                : "Borrow Skills"
                ),
                theme,
                payload.seize()
                        ? SkillUiButton.Tone.DANGER
                        : SkillUiButton.Tone.PRIMARY,
                this::confirmSelection
        );
        confirmButton.setTooltip(
                Tooltip.create(
                        Component.literal(
                                payload.seize()
                                        ? "Immediately take the selected skills from the displayed target."
                                        : "Immediately copy the selected skills from the displayed target."
                        )
                )
        );
        addRenderableWidget(confirmButton);
    }

    private void onSearchChanged(
            String query
    ) {
        retainedQuery = query == null
                ? ""
                : query;
        filterState.setQuery(retainedQuery);
        rebuildVisibleEntries();
    }

    private void toggleCategory(
            SkillUiCategory category
    ) {
        filterState.toggleCategory(category);

        SkillUiButton button = categoryButtons.get(category);

        if (button != null) {
            button.setHighlighted(
                    filterState.isCategoryVisible(category)
            );
        }

        rebuildVisibleEntries();
    }

    private void rebuildVisibleEntries() {
        if (listWidget == null) {
            return;
        }

        String previousFocus = focusedEntry == null
                ? ""
                : focusedEntry.skillId();

        listWidget.rebuildFilter();

        if (!previousFocus.isBlank()
                && listModel.focusEntry(previousFocus)) {
            focusedEntry = findEntry(previousFocus)
                    .orElse(null);
        } else {
            focusedEntry = listModel.focusedEntry()
                    .orElse(null);
        }

        clearStatus();
        updateControls();
    }

    private void onSelectionChanged(
            SkillUiEntry entry,
            SkillUiSelectionModel.ToggleResult result
    ) {
        focusedEntry = entry;
        String action = payload.seize()
                ? "seizure"
                : "borrow";

        switch (result) {
            case SELECTED -> setStatus(
                    Component.literal(
                            entry.displayName().getString()
                                    + " added to the "
                                    + action
                                    + "."
                    ),
                    theme.successColor()
            );
            case DESELECTED -> setStatus(
                    Component.literal(
                            entry.displayName().getString()
                                    + " removed from the "
                                    + action
                                    + "."
                    ),
                    theme.mutedTextColor()
            );
            case LIMIT_REACHED -> setStatus(
                    Component.literal(
                            "A maximum of 32 skills can be submitted at once."
                    ),
                    theme.warningColor()
            );
            case NOT_SELECTABLE -> setStatus(
                    entry.hasDisabledReason()
                            ? entry.disabledReason()
                            : Component.literal(
                            "This skill cannot be selected."
                    ),
                    theme.warningColor()
            );
            case READ_ONLY,
                 NO_ENTRY -> clearStatus();
        }

        updateControls();
    }

    private void selectShown() {
        int added = selectionModel.selectAllVisible(
                listModel.filteredEntries()
        );

        setStatus(
                Component.literal(
                        added > 0
                                ? added
                                  + " visible skill(s) selected."
                                : "No additional visible skills could be selected."
                ),
                added > 0
                        ? theme.successColor()
                        : theme.mutedTextColor()
        );
        updateControls();
    }

    private void clearSelection() {
        selectionModel.clear();
        setStatus(
                Component.literal("Selection cleared."),
                theme.mutedTextColor()
        );
        updateControls();
    }

    private void confirmSelection() {
        if (selectionModel.selectedCount() <= 0) {
            setStatus(
                    Component.literal(
                            "Select at least one skill first."
                    ),
                    theme.warningColor()
            );
            updateControls();
            return;
        }

        List<SkillUiEntry> selectedEntries =
                selectionModel.selectedSkillIds()
                        .stream()
                        .map(this::findEntry)
                        .flatMap(Optional::stream)
                        .toList();

        minecraft.setScreen(
                UltimateConfirmationScreen.forBorrowOrSeize(
                        this,
                        payload,
                        selectedEntries,
                        borrowChances
                )
        );
    }

    private void updateControls() {
        int selectedCount = selectionModel.selectedCount();

        if (selectShownButton != null) {
            selectShownButton.active = listModel != null
                    && listModel.filteredEntries()
                    .stream()
                    .anyMatch(entry -> entry.selectable()
                            && !selectionModel.isSelected(
                            entry.skillId()
                    ));
        }

        if (clearButton != null) {
            clearButton.active = selectedCount > 0;
        }

        if (confirmButton != null) {
            confirmButton.active = selectedCount > 0;
            confirmButton.setMessage(
                    Component.literal(
                            selectedCount > 0
                                    ? actionVerb()
                                      + " "
                                      + selectedCount
                                      + (selectedCount == 1
                                         ? " Skill"
                                         : " Skills")
                                    : actionVerb() + " Skills"
                    )
            );
        }
    }

    private String actionVerb() {
        return payload.seize()
                ? "Seize"
                : "Borrow";
    }

    private Optional<SkillUiEntry> findEntry(
            String skillId
    ) {
        if (skillId == null
                || skillId.isBlank()) {
            return Optional.empty();
        }

        return entries.stream()
                .filter(entry -> skillId.equals(
                        entry.skillId()
                ))
                .findFirst();
    }

    private Component categoryButtonLabel(
            SkillUiCategory category,
            int buttonWidth
    ) {
        if (buttonWidth >= 58) {
            return switch (category) {
                case UNIQUE -> Component.literal("Unique");
                case EXTRA -> Component.literal("Extra");
                case BASIC -> Component.literal("Basic");
                case RESISTANCE -> Component.literal("Resist");
                case OTHER -> Component.literal("Other");
            };
        }

        return Component.literal(
                switch (category) {
                    case UNIQUE -> "U";
                    case EXTRA -> "E";
                    case BASIC -> "B";
                    case RESISTANCE -> "R";
                    case OTHER -> "O";
                }
        );
    }

    private void setStatus(
            Component message,
            int color
    ) {
        statusMessage = message == null
                ? Component.empty()
                : message;
        statusColor = color;
    }

    private void clearStatus() {
        statusMessage = Component.empty();
        statusColor = theme.mutedTextColor();
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        SkillUiRenderHelper.fillBackground(
                guiGraphics,
                width,
                height,
                theme
        );
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                layout.panel(),
                payload.seize()
                        ? theme.dangerColor()
                        : theme.accentColor(),
                theme.panelFillColor(),
                2
        );

        renderHeader(guiGraphics);
        SkillUiRenderHelper.drawDivider(
                guiGraphics,
                layout.footer().left(),
                layout.footer().right(),
                layout.footer().top(),
                theme.panelBorderColor()
        );
        renderWidgetsWithoutParentBlur(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        SkillUiEntry detailsEntry = listWidget == null
                ? focusedEntry
                : listWidget.hoveredEntry()
                .orElse(focusedEntry);

        SkillDetailsPanel.render(
                guiGraphics,
                font,
                layout.details(),
                theme,
                detailsEntry,
                selectionModel
        );
    }

    private void renderWidgetsWithoutParentBlur(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (searchBox != null) {
            searchBox.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        for (SkillUiButton button : categoryButtons.values()) {
            if (button != null) {
                button.render(
                        guiGraphics,
                        mouseX,
                        mouseY,
                        partialTick
                );
            }
        }

        if (listWidget != null) {
            listWidget.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        if (selectShownButton != null) {
            selectShownButton.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        if (clearButton != null) {
            clearButton.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        if (cancelButton != null) {
            cancelButton.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        if (confirmButton != null) {
            confirmButton.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }
    }

    private void renderHeader(
            GuiGraphics guiGraphics
    ) {
        SkillUiLayout.Rect header = layout.header();
        int textLeft = header.left() + 2;
        String modeName = payload.seize()
                ? "SEIZE"
                : "BORROW";
        int badgeLeft = SkillUiRenderHelper.drawModeBadge(
                guiGraphics,
                font,
                Component.literal(modeName),
                header.right() - 2,
                header.top() + 1,
                payload.seize()
                        ? theme.withAccents(
                        theme.dangerColor(),
                        theme.secondaryAccentColor()
                )
                        : theme
        );

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                title,
                textLeft,
                header.top() + 2,
                Math.max(
                        1,
                        badgeLeft - textLeft - 6
                ),
                theme.primaryTextColor()
        );

        SkillUiLayout.Rect targetStrip = new SkillUiLayout.Rect(
                header.left() + 1,
                header.top() + 14,
                Math.max(1, header.width() - 2),
                13
        );
        int targetAccent = payload.seize()
                ? theme.dangerColor()
                : theme.secondaryAccentColor();

        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                targetStrip,
                targetAccent,
                theme.categoryFillColor(),
                1
        );
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(
                        "TARGET • " + payload.targetName()
                ),
                targetStrip.left() + 5,
                targetStrip.top() + 3,
                Math.max(1, targetStrip.width() - 10),
                targetAccent
        );

        Component summary = statusMessage;
        int summaryColor = statusColor;

        if (summary.getString().isBlank()) {
            summary = buildSelectionSummary();
            summaryColor = selectionModel.selectedCount() > 0
                    ? payload.seize()
                      ? theme.dangerColor()
                      : theme.accentColor()
                    : theme.mutedTextColor();
        }

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                summary,
                textLeft,
                header.top() + 29,
                Math.max(1, header.width() - 4),
                summaryColor
        );
        SkillUiRenderHelper.drawDivider(
                guiGraphics,
                header.left(),
                header.right(),
                header.bottom() - 1,
                theme.panelBorderColor()
        );
    }

    private Component buildSelectionSummary() {
        int selectedCount = selectionModel.selectedCount();
        double totalCost = UltimateBorrowSeizePolicy
                .calculateTotalCost(
                        payload.costPerSkill(),
                        selectedCount
                );

        if (payload.seize()) {
            double deathChance = UltimateBorrowSeizePolicy
                    .calculateSeizeDeathChance(
                            payload.seizeDeathChancePerSkill(),
                            payload.seizeDeathChanceMax(),
                            selectedCount
                    );

            return Component.literal(
                    "Absolute Governance • "
                            + selectedCount
                            + " selected • "
                            + UltimateBorrowSeizePolicy.formatNumber(
                            totalCost
                    )
                            + " magicules • target death risk "
                            + UltimateBorrowSeizePolicy.formatPercent(
                            deathChance
                    )
                            + " • selected skills are removed permanently"
            );
        }

        double highestChance = UltimateBorrowSeizePolicy
                .highestBorrowChance(
                        selectionModel.selectedSkillIds(),
                        borrowChances
                );

        return Component.literal(
                "Benevolent Empowerment • "
                        + selectedCount
                        + " selected • "
                        + UltimateBorrowSeizePolicy.formatNumber(
                        totalCost
                )
                        + " magicules • highest permanent chance "
                        + UltimateBorrowSeizePolicy.formatPercent(
                        highestChance
                )
                        + " • target keeps every skill"
        );
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (super.keyPressed(
                keyCode,
                scanCode,
                modifiers
        )) {
            updateControls();
            return true;
        }

        if ((keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && confirmButton != null
                && confirmButton.active) {
            confirmSelection();
            return true;
        }

        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}