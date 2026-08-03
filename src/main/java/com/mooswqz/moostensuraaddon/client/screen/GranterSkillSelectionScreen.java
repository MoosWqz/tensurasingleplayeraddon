package com.mooswqz.moostensuraaddon.client.screen;

import com.mooswqz.moostensuraaddon.client.screen.skillui.GranterSkillUiEntryFactory;
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
import com.mooswqz.moostensuraaddon.network.SelectSkillPayload;
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
import java.util.Optional;

/**
 * Searchable, category-aware Granter skill selector.
 */
public class GranterSkillSelectionScreen extends Screen {

    private static final SkillUiTheme THEME =
            SkillUiTheme.GRANTER;
    private static final int FILTER_GAP = 4;
    private static final int FOOTER_BUTTON_GAP = 5;

    private final SkillUiFilterState filterState =
            new SkillUiFilterState();
    private final SkillUiSelectionModel selectionModel =
            new SkillUiSelectionModel(
                    SkillUiSelectionMode.SINGLE
            );
    private final EnumMap<
            SkillUiCategory,
            SkillUiButton
            > categoryButtons = new EnumMap<>(
            SkillUiCategory.class
    );

    private List<SkillUiEntry> entries = List.of();
    private SkillUiLayout layout;
    private SkillUiListModel listModel;
    private SkillListWidget listWidget;
    private EditBox searchBox;
    private SkillUiButton cancelButton;
    private SkillUiButton confirmButton;
    private int footerButtonsLeft;

    private SkillUiEntry focusedEntry;
    private String retainedQuery = "";
    private String currentSkillId = "";
    private Component authorityName =
            SkillUiText.component("authority.granter");
    private boolean evolvedAuthority;
    private boolean initialSelectionApplied;

    private Component statusMessage = Component.empty();
    private int statusColor = THEME.mutedTextColor();

    public GranterSkillSelectionScreen() {
        super(
                SkillUiText.component(
                        "legacy_active.title"
                )
        );
    }

    public static void open() {
        Minecraft.getInstance().setScreen(
                new GranterSkillSelectionScreen()
        );
    }

    @Override
    protected void init() {
        List<String> previousSelection =
                new ArrayList<>(
                        selectionModel.selectedSkillIds()
                );

        GranterSkillUiEntryFactory.BuildResult result =
                GranterSkillUiEntryFactory.build(
                        minecraft == null
                                ? null
                                : minecraft.player
                );

        entries = result.entries();
        evolvedAuthority = result.evolvedAuthority();
        authorityName = result.authorityName();
        currentSkillId = result.currentSelection()
                .orElse("");

        if (!initialSelectionApplied) {
            selectionModel.replaceSelection(
                    result.currentSelection()
                            .map(skillId -> List.of(skillId))
                            .orElseGet(List::of),
                    entries
            );
            initialSelectionApplied = true;
        } else {
            selectionModel.replaceSelection(
                    previousSelection,
                    entries
            );
        }

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
                THEME,
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

        selectionModel.selectedSkillIds()
                .stream()
                .findFirst()
                .filter(listModel::focusEntry)
                .flatMap(this::findEntry)
                .ifPresent(entry -> focusedEntry = entry);

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
                SkillUiText.component("common.search_skills")
        );
        searchBox.setMaxLength(80);
        searchBox.setHint(
                SkillUiText.component("common.search_skills_hint")
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
            int widthForButton = category == SkillUiCategory.OTHER
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
                    THEME,
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

            categoryButtons.put(
                    category,
                    button
            );
            addRenderableWidget(button);

            buttonX += widthForButton
                    + FILTER_GAP;
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
        int buttonWidth = Math.max(
                1,
                Math.min(
                        116,
                        Math.max(
                                1,
                                (footer.width()
                                        - FOOTER_BUTTON_GAP)
                                        / 2
                        )
                )
        );

        int confirmX = footer.right()
                - buttonWidth;
        int cancelX = confirmX
                - FOOTER_BUTTON_GAP
                - buttonWidth;
        footerButtonsLeft = cancelX;

        cancelButton = new SkillUiButton(
                cancelX,
                buttonY,
                buttonWidth,
                buttonHeight,
                SkillUiText.component("common.cancel"),
                THEME,
                SkillUiButton.Tone.NORMAL,
                this::onClose
        );
        addRenderableWidget(cancelButton);

        confirmButton = new SkillUiButton(
                confirmX,
                buttonY,
                buttonWidth,
                buttonHeight,
                SkillUiText.component("legacy_active.set_active"),
                THEME,
                SkillUiButton.Tone.PRIMARY,
                this::confirmSelection
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

        SkillUiButton button = categoryButtons.get(
                category
        );

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

        String previouslyFocused = focusedEntry == null
                ? ""
                : focusedEntry.skillId();

        listWidget.rebuildFilter();

        Optional<SkillUiEntry> nextFocus = Optional.empty();

        if (!previouslyFocused.isBlank()
                && listModel.focusEntry(previouslyFocused)) {
            nextFocus = findEntry(previouslyFocused);
        }

        if (nextFocus.isEmpty()) {
            nextFocus = listModel.focusedEntry();
        }

        focusedEntry = nextFocus.orElse(null);
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
                    SkillUiText.component("legacy_active.ready"),
                    THEME.successColor()
            );
            case DESELECTED -> setStatus(
                    SkillUiText.component("status.selection_cleared"),
                    THEME.mutedTextColor()
            );
            case NOT_SELECTABLE -> setStatus(
                    entry.hasDisabledReason()
                            ? entry.disabledReason()
                            : SkillUiText.component(
                            "error.skill_not_selectable"
                    ),
                    THEME.warningColor()
            );
            case LIMIT_REACHED -> setStatus(
                    SkillUiText.component(
                            "error.single_selection_only"
                    ),
                    THEME.warningColor()
            );
            case READ_ONLY,
                 NO_ENTRY -> clearStatus();
        }

        updateControls();
    }

    private void updateControls() {
        if (confirmButton != null) {
            confirmButton.active =
                    selectionModel.selectedCount() == 1;
        }
    }

    private void confirmSelection() {
        Optional<String> selected = selectionModel
                .selectedSkillIds()
                .stream()
                .findFirst();

        if (selected.isEmpty()) {
            setStatus(
                    SkillUiText.component(
                            "error.select_skill_first"
                    ),
                    THEME.warningColor()
            );
            updateControls();
            return;
        }

        PacketDistributor.sendToServer(
                new SelectSkillPayload(
                        selected.get()
                )
        );
        onClose();
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
        statusColor = THEME.mutedTextColor();
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
                THEME
        );
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                layout.panel(),
                THEME.accentColor(),
                THEME.panelFillColor(),
                2
        );

        renderHeader(guiGraphics);
        renderFooterStatus(guiGraphics);

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
                THEME,
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
        int badgeLeft = SkillUiRenderHelper.drawModeBadge(
                guiGraphics,
                font,
                SkillUiText.component("legacy_active.badge"),
                header.right() - 2,
                header.top() + 1,
                THEME
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
                THEME.primaryTextColor()
        );

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                SkillUiText.component(
                        "legacy_active.subtitle"
                ),
                textLeft,
                header.top() + 15,
                Math.max(1, header.width() - 4),
                THEME.secondaryTextColor()
        );

        int visibleCount = listModel == null
                ? 0
                : listModel.filteredEntries().size();
        String stateLine = SkillUiText.string(
                evolvedAuthority
                        ? "legacy_active.state_bypass"
                        : "legacy_active.state_mastered",
                authorityName,
                visibleCount,
                entries.size()
        );

        if (!currentSkillId.isBlank()) {
            String currentName = findEntry(currentSkillId)
                    .map((SkillUiEntry entry) -> entry
                            .displayName()
                            .getString())
                    .orElse(currentSkillId);
            stateLine = SkillUiText.string(
                    "legacy_active.state_current",
                    stateLine,
                    currentName
            );
        }

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(stateLine),
                textLeft,
                header.top() + 28,
                Math.max(1, header.width() - 4),
                THEME.mutedTextColor()
        );

        SkillUiRenderHelper.drawDivider(
                guiGraphics,
                header.left(),
                header.right(),
                header.bottom() - 1,
                THEME.panelBorderColor()
        );
    }

    private void renderFooterStatus(
            GuiGraphics guiGraphics
    ) {
        SkillUiLayout.Rect footer = layout.footer();
        SkillUiRenderHelper.drawDivider(
                guiGraphics,
                footer.left(),
                footer.right(),
                footer.top(),
                THEME.panelBorderColor()
        );

        Component footerText = statusMessage;
        int footerColor = statusColor;

        if (footerText.getString().isBlank()) {
            footerText = selectionModel
                    .selectedSkillIds()
                    .stream()
                    .findFirst()
                    .flatMap(this::findEntry)
                    .map(entry -> SkillUiText.component(
                            "legacy_active.footer_selected",
                            entry.displayName()
                    ))
                    .orElseGet(() -> SkillUiText.component(
                            "legacy_active.footer_empty"
                    ));
            footerColor = selectionModel.selectedCount() == 1
                    ? THEME.accentColor()
                    : THEME.mutedTextColor();
        }

        int maximumWidth = Math.max(
                1,
                footerButtonsLeft
                        - footer.left()
                        - 6
        );

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                footerText,
                footer.left() + 2,
                footer.centerY() - 4,
                maximumWidth,
                footerColor
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