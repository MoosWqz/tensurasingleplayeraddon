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
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiText;
import com.mooswqz.moostensuraaddon.client.screen.skillui.UltimateMultiGrantUiEntryFactory;
import com.mooswqz.moostensuraaddon.network.ExecuteUltimateMultiGrantPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateMultiGrantScreenPayload;
import com.mooswqz.moostensuraaddon.util.AuthorityActionMode;
import com.mooswqz.moostensuraaddon.util.UiTranslationToken;
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

public final class UltimateMultiGrantScreen extends Screen {

    private static final int FILTER_GAP = 4;
    private static final int FOOTER_GAP = 4;

    private final OpenUltimateMultiGrantScreenPayload payload;
    private final AuthorityActionMode mode;
    private final SkillUiTheme theme;
    private final SkillUiFilterState filterState =
            new SkillUiFilterState();
    private final SkillUiSelectionModel selectionModel;
    private final EnumMap<SkillUiCategory, SkillUiButton>
            categoryButtons = new EnumMap<>(SkillUiCategory.class);
    private final List<SkillUiButton> footerButtons =
            new ArrayList<>();

    private List<SkillUiEntry> entries = List.of();
    private Map<String, UltimateMultiGrantUiEntryFactory.CostBreakdown>
            costs = Map.of();
    private SkillUiLayout layout;
    private SkillUiListModel listModel;
    private SkillListWidget listWidget;
    private EditBox searchBox;
    private SkillUiButton selectShownButton;
    private SkillUiButton clearButton;
    private SkillUiButton allEligibleButton;
    private SkillUiButton actionButton;
    private SkillUiEntry focusedEntry;
    private String retainedQuery = "";
    private boolean allEligible;
    private Component statusMessage = Component.empty();
    private int statusColor;

    public UltimateMultiGrantScreen(
            OpenUltimateMultiGrantScreenPayload payload
    ) {
        super(
                payload == null
                        ? SkillUiText.component("common.authority_action")
                        : payload.actionMode().titleComponent()
        );
        this.payload = payload == null
                ? new OpenUltimateMultiGrantScreenPayload(
                AuthorityActionMode.BENEVOLENT_BESTOW.id(),
                "",
                SkillUiText.string("common.unknown_subordinate"),
                0.0D,
                0,
                false,
                false,
                List.of()
        )
                : payload;
        this.mode = this.payload.actionMode();
        this.theme = resolveTheme(mode);
        this.selectionModel = new SkillUiSelectionModel(
                mode.selectionLimit() == 1
                        ? SkillUiSelectionMode.SINGLE
                        : SkillUiSelectionMode.MULTI,
                mode.selectionLimit()
        );
        this.allEligible = this.payload.allEligibleByDefault();
        this.statusColor = this.theme.mutedTextColor();
    }

    @Override
    protected void init() {
        List<String> previousSelection = new ArrayList<>(
                selectionModel.selectedSkillIds()
        );
        UltimateMultiGrantUiEntryFactory.BuildResult result =
                UltimateMultiGrantUiEntryFactory.build(payload);

        entries = result.entries();
        costs = result.costs();
        selectionModel.replaceSelection(previousSelection, entries);
        filterState.setQuery(retainedQuery);
        layout = SkillUiLayout.calculate(width, height, true);
        listModel = new SkillUiListModel(filterState);
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
        listWidget.setSelectionListener(this::onSelectionChanged);

        if (focusedEntry != null
                && listModel.focusEntry(focusedEntry.skillId())) {
            focusedEntry = findEntry(
                    focusedEntry.skillId()
            ).orElse(null);
        }

        if (focusedEntry == null) {
            listModel.focusFirst().ifPresent(entry ->
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
                Math.min(210, filters.width() * 31 / 100)
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
                Math.min(desiredSearchWidth, maximumSearchWidth)
        );

        searchBox = new EditBox(
                font,
                filters.left(),
                controlY,
                searchWidth,
                buttonHeight,
                SkillUiText.component("common.search_skills")
        );
        searchBox.setMaxLength(80);
        searchBox.setHint(SkillUiText.component("common.search_skills_hint"));
        searchBox.setValue(retainedQuery);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        int categoriesLeft = filters.left()
                + searchWidth
                + FILTER_GAP;
        int categoriesWidth = Math.max(
                1,
                filters.right() - categoriesLeft
        );
        int totalGaps = FILTER_GAP * (categoryCount - 1);
        int categoryButtonWidth = Math.max(
                1,
                (categoriesWidth - totalGaps) / categoryCount
        );
        int buttonX = categoriesLeft;

        for (SkillUiCategory category : SkillUiCategory.values()) {
            int availableRight = filters.right() - buttonX;
            int widthForButton = category == SkillUiCategory.OTHER
                    ? Math.max(1, availableRight)
                    : categoryButtonWidth;
            SkillUiButton button = new SkillUiButton(
                    buttonX,
                    controlY,
                    widthForButton,
                    buttonHeight,
                    categoryButtonLabel(category, widthForButton),
                    theme,
                    SkillUiButton.Tone.NORMAL,
                    () -> toggleCategory(category)
            );
            button.setHighlighted(
                    filterState.isCategoryVisible(category)
            );
            button.setTooltip(
                    Tooltip.create(category.displayName())
            );
            categoryButtons.put(category, button);
            addRenderableWidget(button);
            buttonX += widthForButton + FILTER_GAP;
        }
    }

    private void createFooterControls() {
        footerButtons.clear();
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

        int buttonCount = 3;

        if (mode.selectionLimit() > 1) {
            buttonCount++;
        }

        if (payload.allowAllEligible()) {
            buttonCount++;
        }

        int buttonWidth = Math.max(
                1,
                (footer.width() - FOOTER_GAP * (buttonCount - 1))
                        / buttonCount
        );
        boolean compact = buttonWidth < 75;
        int buttonX = footer.left();

        if (mode.selectionLimit() > 1) {
            selectShownButton = addFooterButton(
                    buttonX,
                    buttonY,
                    buttonWidth,
                    buttonHeight,
                    compact
                            ? SkillUiText.component("common.all")
                            : SkillUiText.component("common.select_shown"),
                    SkillUiButton.Tone.NORMAL,
                    this::selectShown
            );
            selectShownButton.setTooltip(
                    Tooltip.create(
                            SkillUiText.component(
                                    "tooltip.select_visible_limit"
                            )
                    )
            );
            buttonX += buttonWidth + FOOTER_GAP;
        }

        clearButton = addFooterButton(
                buttonX,
                buttonY,
                buttonWidth,
                buttonHeight,
                SkillUiText.component("common.clear"),
                SkillUiButton.Tone.NORMAL,
                this::clearSelection
        );
        buttonX += buttonWidth + FOOTER_GAP;

        if (payload.allowAllEligible()) {
            allEligibleButton = addFooterButton(
                    buttonX,
                    buttonY,
                    buttonWidth,
                    buttonHeight,
                    compact
                            ? SkillUiText.component("common.all_targets")
                            : SkillUiText.component("common.all_eligible"),
                    SkillUiButton.Tone.NORMAL,
                    this::toggleAllEligible
            );
            allEligibleButton.setHighlighted(allEligible);
            allEligibleButton.setTooltip(
                    Tooltip.create(
                            SkillUiText.component(
                                    "tooltip.all_eligible_take_back"
                            )
                    )
            );
            buttonX += buttonWidth + FOOTER_GAP;
        }

        addFooterButton(
                buttonX,
                buttonY,
                buttonWidth,
                buttonHeight,
                SkillUiText.component("common.cancel"),
                SkillUiButton.Tone.NORMAL,
                this::onClose
        );
        buttonX += buttonWidth + FOOTER_GAP;

        actionButton = addFooterButton(
                buttonX,
                buttonY,
                Math.max(1, footer.right() - buttonX),
                buttonHeight,
                mode.actionButtonComponent(0),
                mode == AuthorityActionMode.GOVERNANCE_INVEST
                        ? SkillUiButton.Tone.DANGER
                        : SkillUiButton.Tone.PRIMARY,
                this::performAction
        );
    }

    private SkillUiButton addFooterButton(
            int x,
            int y,
            int width,
            int height,
            Component label,
            SkillUiButton.Tone tone,
            Runnable action
    ) {
        SkillUiButton button = new SkillUiButton(
                x,
                y,
                width,
                height,
                label,
                theme,
                tone,
                action
        );
        footerButtons.add(button);
        addRenderableWidget(button);
        return button;
    }

    private void onSearchChanged(String query) {
        retainedQuery = query == null ? "" : query;
        filterState.setQuery(retainedQuery);
        rebuildVisibleEntries();
    }

    private void toggleCategory(SkillUiCategory category) {
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
            focusedEntry = findEntry(previousFocus).orElse(null);
        } else {
            focusedEntry = listModel.focusedEntry().orElse(null);
        }

        clearStatus();
        updateControls();
    }

    private void onSelectionChanged(
            SkillUiEntry entry,
            SkillUiSelectionModel.ToggleResult result
    ) {
        focusedEntry = entry;

        switch (result) {
            case SELECTED -> setStatus(
                    entry.mastered()
                            ? SkillUiText.component("status.skill_selected")
                            : SkillUiText.component(
                            "warning.unmastered_selected"
                    ),
                    entry.mastered()
                            ? theme.successColor()
                            : theme.warningColor()
            );
            case DESELECTED -> setStatus(
                    SkillUiText.component("status.selection_removed"),
                    theme.mutedTextColor()
            );
            case NOT_SELECTABLE -> setStatus(
                    entry.hasDisabledReason()
                            ? entry.disabledReason()
                            : SkillUiText.component(
                            "error.skill_invalid_for_action"
                    ),
                    theme.warningColor()
            );
            case LIMIT_REACHED -> setStatus(
                    SkillUiText.component(
                            "error.selection_limit_reached",
                            mode.selectionLimit()
                    ),
                    theme.warningColor()
            );
            case READ_ONLY,
                 NO_ENTRY -> clearStatus();
        }

        updateControls();
    }

    private void selectShown() {
        if (listModel == null) {
            return;
        }

        int added = selectionModel.selectAllVisible(
                listModel.filteredEntries()
        );
        boolean limitReached = selectionModel.selectedCount()
                >= selectionModel.maximumSelection()
                && listModel.filteredEntries()
                .stream()
                .anyMatch(entry -> entry.selectable()
                        && !selectionModel.isSelected(
                        entry.skillId()
                ));

        if (limitReached) {
            setStatus(
                    SkillUiText.component(
                            "status.selected_limit",
                            selectionModel.selectedCount(),
                            mode.selectionLimit()
                    ),
                    theme.warningColor()
            );
        } else {
            setStatus(
                    added > 0
                            ? SkillUiText.component(
                            "status.added_visible_skills",
                            added
                    )
                            : SkillUiText.component(
                            "status.all_visible_selected"
                    ),
                    added > 0
                            ? theme.successColor()
                            : theme.mutedTextColor()
            );
        }

        updateControls();
    }

    private void clearSelection() {
        selectionModel.clear();
        setStatus(
                SkillUiText.component("status.selection_cleared"),
                theme.mutedTextColor()
        );
        updateControls();
    }

    private void toggleAllEligible() {
        allEligible = !allEligible;

        if (allEligibleButton != null) {
            allEligibleButton.setHighlighted(allEligible);
        }

        setStatus(
                allEligible
                        ? SkillUiText.component(
                        "status.all_eligible_affected"
                )
                        : SkillUiText.component(
                        "status.single_target_affected"
                ),
                allEligible
                        ? theme.accentColor()
                        : theme.mutedTextColor()
        );
        updateControls();
    }

    private void performAction() {
        List<String> selected = new ArrayList<>(
                selectionModel.selectedSkillIds()
        );

        if (selected.isEmpty()) {
            setStatus(
                    SkillUiText.component("error.select_skill_first"),
                    theme.warningColor()
            );
            return;
        }

        if (payload.cooldownTicks() > 0) {
            setStatus(
                    SkillUiText.component(
                            "error.action_cooling_down"
                    ),
                    theme.warningColor()
            );
            return;
        }

        double required = selectedCost();

        if (required > payload.availableMagicules()) {
            setStatus(
                    SkillUiText.component(
                            "error.insufficient_magicules_required",
                            UltimateMultiGrantUiEntryFactory
                                    .formatNumber(required)
                    ),
                    theme.warningColor()
            );
            return;
        }

        if (payload.allowAllEligible()
                && !allEligible
                && payload.targetUuid().isBlank()) {
            setStatus(
                    SkillUiText.component(
                            "error.enable_all_eligible"
                    ),
                    theme.warningColor()
            );
            return;
        }

        if (mode.granter()) {
            PacketDistributor.sendToServer(
                    new ExecuteUltimateMultiGrantPayload(
                            mode.id(),
                            payload.targetUuid(),
                            allEligible,
                            selected
                    )
            );
            onClose();
            return;
        }

        List<SkillUiEntry> selectedEntries = selected.stream()
                .map(this::findEntry)
                .flatMap(Optional::stream)
                .toList();

        minecraft.setScreen(
                UltimateConfirmationScreen.forAuthorityAction(
                        this,
                        payload,
                        mode,
                        selectedEntries,
                        costs,
                        allEligible
                )
        );
    }

    private void updateControls() {
        int selectedCount = selectionModel.selectedCount();
        double required = selectedCost();
        boolean hasTarget = !payload.allowAllEligible()
                || allEligible
                || !payload.targetUuid().isBlank();
        boolean affordable = required <= payload.availableMagicules();
        boolean ready = selectedCount > 0
                && payload.cooldownTicks() <= 0
                && affordable
                && hasTarget;

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

        if (actionButton != null) {
            actionButton.active = ready;
            actionButton.setMessage(
                    mode.actionButtonComponent(
                            selectedCount
                    )
            );
        }
    }

    private double selectedCost() {
        return UltimateMultiGrantUiEntryFactory.calculateTotalCost(
                new ArrayList<>(selectionModel.selectedSkillIds()),
                costs
        );
    }

    private Optional<SkillUiEntry> findEntry(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }

        return entries.stream()
                .filter(entry -> skillId.equals(entry.skillId()))
                .findFirst();
    }

    private Component categoryButtonLabel(
            SkillUiCategory category,
            int buttonWidth
    ) {
        if (buttonWidth >= 58) {
            return switch (category) {
                case UNIQUE -> SkillUiText.component("category.unique_short");
                case EXTRA -> SkillUiText.component("category.extra_short");
                case BASIC -> SkillUiText.component("category.basic_short");
                case RESISTANCE -> SkillUiText.component("category.resistance_short");
                case OTHER -> SkillUiText.component("category.other_short");
            };
        }

        return switch (category) {
            case UNIQUE -> SkillUiText.component("category.unique_compact");
            case EXTRA -> SkillUiText.component("category.extra_compact");
            case BASIC -> SkillUiText.component("category.basic_compact");
            case RESISTANCE -> SkillUiText.component("category.resistance_compact");
            case OTHER -> SkillUiText.component("category.other_compact");
        };
    }

    private void setStatus(Component message, int color) {
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
                theme.accentColor(),
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
                : listWidget.hoveredEntry().orElse(focusedEntry);

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
            searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        for (SkillUiButton button : categoryButtons.values()) {
            if (button != null) {
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        if (listWidget != null) {
            listWidget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        for (SkillUiButton button : footerButtons) {
            if (button != null) {
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        SkillUiLayout.Rect header = layout.header();
        int textLeft = header.left() + 2;
        int badgeLeft = SkillUiRenderHelper.drawModeBadge(
                guiGraphics,
                font,
                mode.badgeComponent(),
                header.right() - 2,
                header.top() + 1,
                theme
        );

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                mode.titleComponent(),
                textLeft,
                header.top() + 2,
                Math.max(1, badgeLeft - textLeft - 6),
                theme.primaryTextColor()
        );

        SkillUiLayout.Rect targetStrip = new SkillUiLayout.Rect(
                header.left() + 1,
                header.top() + 14,
                Math.max(1, header.width() - 2),
                13
        );
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                targetStrip,
                theme.secondaryAccentColor(),
                theme.categoryFillColor(),
                1
        );
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                SkillUiText.component(
                        mode.massGrant() || allEligible
                                ? "header.scope_value"
                                : "header.target_value",
                        displayedTargetOrScope()
                ),
                targetStrip.left() + 5,
                targetStrip.top() + 3,
                Math.max(1, targetStrip.width() - 10),
                theme.secondaryAccentColor()
        );

        Component summary = statusMessage;
        int summaryColor = statusColor;

        if (summary.getString().isBlank()) {
            List<String> selected = new ArrayList<>(
                    selectionModel.selectedSkillIds()
            );
            UltimateMultiGrantUiEntryFactory.SelectionSummary selection =
                    UltimateMultiGrantUiEntryFactory.summarizeSelection(
                            selected,
                            costs
                    );
            String text = SkillUiText.string(
                    "summary.selected_limit",
                    selectionModel.selectedCount(),
                    mode.selectionLimit()
            );

            if (selection.unmastered() > 0) {
                text = SkillUiText.string(
                        "summary.with_unmastered_warning",
                        text,
                        selection.unmastered()
                );
            }

            if (!mode.takeBack()) {
                text = SkillUiText.string(
                        "summary.with_magicules",
                        text,
                        UltimateMultiGrantUiEntryFactory.formatNumber(
                                selection.totalCost()
                        ),
                        UltimateMultiGrantUiEntryFactory.formatNumber(
                                payload.availableMagicules()
                        )
                );
            }

            if (payload.cooldownTicks() > 0) {
                text = SkillUiText.string(
                        "summary.with_cooldown",
                        text,
                        SkillUiText.formatDecimal(
                                payload.cooldownTicks() / 20.0D,
                                1
                        )
                );
            }

            summary = Component.literal(text);
            summaryColor = selection.unmastered() > 0
                    ? theme.warningColor()
                    : selection.totalCost()
                    > payload.availableMagicules()
                      ? theme.warningColor()
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


    private Component displayedTargetOrScope() {
        if (!allEligible) {
            return UiTranslationToken.toComponent(
                    payload.targetName()
            );
        }

        return SkillUiText.component(
                mode.governance()
                        ? "scope.governance_128"
                        : "scope.benevolent_32"
        );
    }

    private static SkillUiTheme resolveTheme(
            AuthorityActionMode mode
    ) {
        if (mode == null || mode.granter()) {
            return SkillUiTheme.GRANTER;
        }

        return mode.benevolent()
                ? SkillUiTheme.BENEVOLENT
                : SkillUiTheme.GOVERNANCE;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            updateControls();
            return true;
        }

        if ((keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && actionButton != null
                && actionButton.active) {
            performAction();
            return true;
        }

        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}