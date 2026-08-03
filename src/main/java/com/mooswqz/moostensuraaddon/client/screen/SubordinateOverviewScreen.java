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
import com.mooswqz.moostensuraaddon.client.screen.skillui.SubordinateListWidget;
import com.mooswqz.moostensuraaddon.network.OpenSubordinateOverviewScreenPayload;
import com.mooswqz.moostensuraaddon.network.RequestSubordinateOverviewPayload;
import com.mooswqz.moostensuraaddon.util.SubordinateOverviewPolicy;
import com.mooswqz.moostensuraaddon.util.UiTranslationToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SubordinateOverviewScreen extends Screen {

    private static final int GAP = 6;
    private static final int FILTER_GAP = 4;

    private OpenSubordinateOverviewScreenPayload payload;
    private SkillUiTheme theme;
    private OverviewLayout layout;

    private final SkillUiFilterState skillFilterState =
            new SkillUiFilterState();
    private final SkillUiSelectionModel readOnlySelection =
            new SkillUiSelectionModel(
                    SkillUiSelectionMode.READ_ONLY
            );
    private final EnumMap<SkillUiCategory, SkillUiButton>
            categoryButtons = new EnumMap<>(SkillUiCategory.class);
    private final List<SkillUiButton> footerButtons =
            new ArrayList<>();

    private SubordinateListWidget subordinateListWidget;
    private SkillUiListModel skillListModel;
    private SkillListWidget skillListWidget;
    private EditBox subordinateSearchBox;
    private EditBox skillSearchBox;
    private SkillUiButton refreshButton;
    private SkillUiButton closeButton;

    private String selectedTargetUuid = "";
    private String retainedSubordinateQuery = "";
    private String retainedSkillQuery = "";
    private String retainedFocusedSkillId = "";
    private OpenSubordinateOverviewScreenPayload.TargetEntry
            selectedTarget;
    private SkillUiEntry focusedSkill;
    private Component statusMessage = Component.empty();
    private int statusColor;
    private int refreshTicks =
            SubordinateOverviewPolicy.AUTO_REFRESH_TICKS;

    public SubordinateOverviewScreen(
            OpenSubordinateOverviewScreenPayload payload
    ) {
        super(SkillUiText.component("overview.title"));
        this.payload = payload == null
                ? emptyPayload()
                : payload;
        this.theme = resolveTheme(this.payload);
        this.statusColor = theme.mutedTextColor();
    }

    public static void open(
            OpenSubordinateOverviewScreenPayload payload
    ) {
        Minecraft.getInstance().setScreen(
                new SubordinateOverviewScreen(payload)
        );
    }

    public void applyPayload(
            OpenSubordinateOverviewScreenPayload updatedPayload
    ) {
        if (updatedPayload == null) {
            return;
        }

        rememberCurrentState();
        payload = updatedPayload;
        theme = resolveTheme(payload);
        statusMessage = SkillUiText.component("overview.status_refreshed");
        statusColor = theme.successColor();
        refreshTicks = SubordinateOverviewPolicy.AUTO_REFRESH_TICKS;

        clearWidgets();
        init();
    }

    public void removeTarget(
            String targetUuid
    ) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return;
        }

        Optional<OpenSubordinateOverviewScreenPayload.TargetEntry> removed =
                payload.targets().stream()
                        .filter(target -> target.targetUuid().equals(targetUuid))
                        .findFirst();

        if (removed.isEmpty()) {
            return;
        }

        rememberCurrentState();
        List<OpenSubordinateOverviewScreenPayload.TargetEntry> remaining =
                payload.targets().stream()
                        .filter(target -> !target.targetUuid().equals(targetUuid))
                        .toList();
        payload = new OpenSubordinateOverviewScreenPayload(
                payload.themeId(),
                payload.radius(),
                payload.refreshAllowed(),
                payload.truncated(),
                remaining
        );

        if (selectedTargetUuid.equals(targetUuid)) {
            selectedTargetUuid = "";
            selectedTarget = null;
            focusedSkill = null;
            retainedFocusedSkillId = "";
        }

        statusMessage = SkillUiText.component(
                "overview.status_subordinate_died",
                UiTranslationToken.toComponent(
                        removed.orElseThrow().targetName()
                )
        );
        statusColor = theme.warningColor();
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        layout = OverviewLayout.calculate(width, height);
        restoreSelectedTarget();
        buildSubordinateList();
        buildSkillList();
        buildSearchControls();
        buildFooterControls();
    }

    private void buildSubordinateList() {
        subordinateListWidget = new SubordinateListWidget(
                layout.targets().left(),
                layout.targets().top(),
                layout.targets().width(),
                layout.targets().height(),
                font,
                theme
        );
        subordinateListWidget.setTargets(payload.targets());
        subordinateListWidget.setQuery(retainedSubordinateQuery);
        subordinateListWidget.setSelectionListener(
                this::selectTarget
        );
        subordinateListWidget.select(selectedTargetUuid);
        addRenderableWidget(subordinateListWidget);
    }

    private void buildSkillList() {
        skillFilterState.setQuery(retainedSkillQuery);
        skillListModel = new SkillUiListModel(skillFilterState);
        skillListWidget = new SkillListWidget(
                layout.skills().left(),
                layout.skills().top(),
                layout.skills().width(),
                layout.skills().height(),
                font,
                theme,
                skillListModel,
                readOnlySelection
        );
        skillListWidget.setFocusListener(entry -> {
            focusedSkill = entry;
            retainedFocusedSkillId = entry.skillId();
        });
        updateSkillEntries();
        addRenderableWidget(skillListWidget);
    }

    private void buildSearchControls() {
        int targetControlHeight = Math.max(
                16,
                layout.targetFilter().height()
        );
        subordinateSearchBox = new EditBox(
                font,
                layout.targetFilter().left(),
                layout.targetFilter().top(),
                layout.targetFilter().width(),
                targetControlHeight,
                SkillUiText.component("overview.search_subordinates")
        );
        subordinateSearchBox.setMaxLength(80);
        subordinateSearchBox.setHint(
                fitSearchHint(
                        SkillUiText.component(
                                "overview.search_subordinates_hint"
                        ),
                        layout.targetFilter().width()
                )
        );
        subordinateSearchBox.setValue(retainedSubordinateQuery);
        subordinateSearchBox.setResponder(query -> {
            retainedSubordinateQuery = query;
            subordinateListWidget.setQuery(query);
        });
        addRenderableWidget(subordinateSearchBox);

        SkillUiLayout.Rect skillFilter = layout.skillFilter();
        int buttonHeight = Math.max(16, skillFilter.height());
        int categoryCount = SkillUiCategory.values().length;
        int desiredSearchWidth = Math.max(
                80,
                Math.min(190, skillFilter.width() * 38 / 100)
        );
        int minimumCategoryWidth = skillFilter.width() < 360
                ? 18
                : 38;
        int maximumSearchWidth = skillFilter.width()
                - FILTER_GAP
                - categoryCount * minimumCategoryWidth
                - (categoryCount - 1) * FILTER_GAP;
        int searchWidth = Math.max(
                60,
                Math.min(desiredSearchWidth, maximumSearchWidth)
        );

        skillSearchBox = new EditBox(
                font,
                skillFilter.left(),
                skillFilter.top(),
                searchWidth,
                buttonHeight,
                SkillUiText.component("common.search_skills")
        );
        skillSearchBox.setMaxLength(80);
        skillSearchBox.setHint(
                fitSearchHint(
                        SkillUiText.component(
                                "common.search_skills_hint"
                        ),
                        searchWidth
                )
        );
        skillSearchBox.setValue(retainedSkillQuery);
        skillSearchBox.setResponder(query -> {
            retainedSkillQuery = query;
            skillFilterState.setQuery(query);
            skillListWidget.rebuildFilter();
        });
        addRenderableWidget(skillSearchBox);

        categoryButtons.clear();
        int categoriesLeft = skillFilter.left()
                + searchWidth
                + FILTER_GAP;
        int categoriesWidth = Math.max(
                1,
                skillFilter.right() - categoriesLeft
        );
        int totalGaps = FILTER_GAP * (categoryCount - 1);
        int categoryWidth = Math.max(
                1,
                (categoriesWidth - totalGaps) / categoryCount
        );
        int buttonX = categoriesLeft;

        for (SkillUiCategory category : SkillUiCategory.values()) {
            int buttonWidth = category == SkillUiCategory.OTHER
                    ? Math.max(1, skillFilter.right() - buttonX)
                    : categoryWidth;
            SkillUiButton button = new SkillUiButton(
                    buttonX,
                    skillFilter.top(),
                    buttonWidth,
                    buttonHeight,
                    categoryLabel(category, buttonWidth),
                    theme,
                    SkillUiButton.Tone.NORMAL,
                    () -> toggleCategory(category)
            );
            button.setHighlighted(
                    skillFilterState.isCategoryVisible(category)
            );
            button.setTooltip(Tooltip.create(category.displayName()));
            categoryButtons.put(category, button);
            addRenderableWidget(button);
            buttonX += buttonWidth + FILTER_GAP;
        }
    }

    private void buildFooterControls() {
        footerButtons.clear();
        SkillUiLayout.Rect footer = layout.footer();
        int buttonHeight = Math.max(
                16,
                Math.min(20, footer.height() - 2)
        );
        int buttonY = footer.top()
                + Math.max(0, (footer.height() - buttonHeight) / 2);
        int buttonWidth = Math.max(
                68,
                Math.min(170, footer.width() / 6)
        );
        int closeWidth = buttonWidth;
        int refreshWidth = buttonWidth;

        refreshButton = new SkillUiButton(
                footer.right() - closeWidth - refreshWidth - GAP,
                buttonY,
                refreshWidth,
                buttonHeight,
                SkillUiText.component("common.refresh"),
                theme,
                SkillUiButton.Tone.NORMAL,
                this::requestRefresh
        );
        refreshButton.active = payload.refreshAllowed();
        refreshButton.setTooltip(
                Tooltip.create(
                        SkillUiText.component(
                                "overview.refresh_tooltip"
                        )
                )
        );
        footerButtons.add(refreshButton);
        addRenderableWidget(refreshButton);

        closeButton = new SkillUiButton(
                footer.right() - closeWidth,
                buttonY,
                closeWidth,
                buttonHeight,
                SkillUiText.component("common.close"),
                theme,
                SkillUiButton.Tone.NORMAL,
                this::onClose
        );
        footerButtons.add(closeButton);
        addRenderableWidget(closeButton);
    }

    @Override
    public void tick() {
        super.tick();

        if (!payload.refreshAllowed()) {
            return;
        }

        refreshTicks--;

        if (refreshTicks <= 0) {
            requestRefresh();
        }
    }

    private void requestRefresh() {
        if (!payload.refreshAllowed()) {
            return;
        }

        refreshTicks = SubordinateOverviewPolicy.AUTO_REFRESH_TICKS;
        statusMessage = SkillUiText.component("overview.status_refreshing");
        statusColor = theme.mutedTextColor();
        PacketDistributor.sendToServer(
                new RequestSubordinateOverviewPayload(
                        payload.benevolent()
                )
        );
    }

    private void selectTarget(
            OpenSubordinateOverviewScreenPayload.TargetEntry target
    ) {
        selectedTarget = target;
        selectedTargetUuid = target == null
                ? ""
                : target.targetUuid();
        focusedSkill = null;
        retainedFocusedSkillId = "";
        updateSkillEntries();
    }

    private void restoreSelectedTarget() {
        selectedTarget = findTarget(selectedTargetUuid).orElse(null);

        if (selectedTarget == null && !payload.targets().isEmpty()) {
            selectedTarget = payload.targets().getFirst();
            selectedTargetUuid = selectedTarget.targetUuid();
        }

        if (selectedTarget == null) {
            selectedTargetUuid = "";
        }
    }

    private void updateSkillEntries() {
        if (skillListWidget == null) {
            return;
        }

        List<SkillUiEntry> entries = selectedTarget == null
                ? List.of()
                : selectedTarget.skills()
                .stream()
                .map(this::toSkillUiEntry)
                .sorted(
                        Comparator
                                .comparingInt(
                                        (SkillUiEntry entry) ->
                                                entry.category().order()
                                )
                                .thenComparing(
                                        (SkillUiEntry entry) ->
                                                entry.displayName().getString(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(SkillUiEntry::skillId)
                )
                .toList();
        skillListWidget.setEntries(entries);

        if (!retainedFocusedSkillId.isBlank()
                && skillListModel.focusEntry(retainedFocusedSkillId)) {
            focusedSkill = entries.stream()
                    .filter(entry -> entry.skillId()
                            .equals(retainedFocusedSkillId))
                    .findFirst()
                    .orElse(null);
        }

        if (focusedSkill == null) {
            skillListModel.focusFirst().ifPresent(entry -> {
                focusedSkill = entry;
                retainedFocusedSkillId = entry.skillId();
            });
        }
    }

    private SkillUiEntry toSkillUiEntry(
            OpenSubordinateOverviewScreenPayload.SkillEntry entry
    ) {
        SkillUiCategory category = SkillUiCategory.fromRaw(
                entry.categoryId()
        );
        List<Component> details = new ArrayList<>();
        details.add(
                SkillUiText.component(
                        entry.grantedByViewer()
                                ? "overview.skill_granted_by_you"
                                : "overview.skill_not_your_grant"
                )
        );

        return new SkillUiEntry(
                entry.skillId(),
                Component.literal(entry.displayName()),
                category,
                true,
                entry.mastered(),
                Component.empty(),
                SkillUiText.component(
                        entry.grantedByViewer()
                                ? "overview.source_granted_by_you"
                                : "overview.source_other"
                ),
                details,
                category.defaultAccentColor()
        );
    }

    private void toggleCategory(
            SkillUiCategory category
    ) {
        skillFilterState.toggleCategory(category);
        skillListWidget.rebuildFilter();
        SkillUiButton button = categoryButtons.get(category);

        if (button != null) {
            button.setHighlighted(
                    skillFilterState.isCategoryVisible(category)
            );
        }
    }

    private void rememberCurrentState() {
        if (subordinateSearchBox != null) {
            retainedSubordinateQuery = subordinateSearchBox.getValue();
        }

        if (skillSearchBox != null) {
            retainedSkillQuery = skillSearchBox.getValue();
        }

        if (selectedTarget != null) {
            selectedTargetUuid = selectedTarget.targetUuid();
        }

        if (focusedSkill != null) {
            retainedFocusedSkillId = focusedSkill.skillId();
        }
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
        renderColumnLabels(guiGraphics);
        renderWidgetsWithoutParentBlur(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
        renderDetails(guiGraphics);
        renderFooterStatus(guiGraphics);
    }

    private void renderHeader(
            GuiGraphics guiGraphics
    ) {
        SkillUiLayout.Rect header = layout.header();
        int badgeLeft = SkillUiRenderHelper.drawModeBadge(
                guiGraphics,
                font,
                SkillUiText.component(
                        payload.governance()
                                ? "overview.badge_governance"
                                : payload.benevolent()
                                  ? "overview.badge_benevolent"
                                  : "overview.badge_granter"
                ),
                header.right() - 2,
                header.top() + 1,
                theme
        );
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                SkillUiText.component("overview.title"),
                header.left() + 2,
                header.top() + 2,
                Math.max(1, badgeLeft - header.left() - 10),
                theme.primaryTextColor()
        );

        String summary = SkillUiText.string(
                payload.targets().size() == 1
                        ? "overview.summary_one"
                        : "overview.summary_many",
                payload.targets().size(),
                formatRadius(payload.radius())
        );

        if (payload.truncated()) {
            summary = SkillUiText.string(
                    "overview.summary_truncated",
                    summary
            );
        }

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(summary),
                header.left() + 2,
                header.top() + (layout.compact() ? 14 : 18),
                Math.max(1, header.width() - 4),
                payload.truncated()
                        ? theme.warningColor()
                        : theme.mutedTextColor()
        );
        SkillUiRenderHelper.drawDivider(
                guiGraphics,
                header.left(),
                header.right(),
                header.bottom() - 1,
                theme.panelBorderColor()
        );
    }

    private void renderColumnLabels(
            GuiGraphics guiGraphics
    ) {
        SkillUiRenderHelper.drawText(
                guiGraphics,
                font,
                SkillUiText.component(
                        "overview.column_subordinates",
                        subordinateListWidget == null
                                ? 0
                                : subordinateListWidget.visibleTargetCount()
                ),
                layout.targetLabel().left(),
                layout.targetLabel().top(),
                theme.secondaryAccentColor()
        );
        SkillUiRenderHelper.drawText(
                guiGraphics,
                font,
                selectedTarget == null
                        ? SkillUiText.component("overview.column_skills")
                        : SkillUiText.component(
                        "overview.column_skills_count",
                        selectedTarget.skills().size()
                ),
                layout.skillLabel().left(),
                layout.skillLabel().top(),
                theme.secondaryAccentColor()
        );
        SkillUiRenderHelper.drawText(
                guiGraphics,
                font,
                SkillUiText.component("overview.column_details"),
                layout.detailLabel().left(),
                layout.detailLabel().top(),
                theme.secondaryAccentColor()
        );
    }

    private void renderWidgetsWithoutParentBlur(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (subordinateSearchBox != null) {
            subordinateSearchBox.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        if (skillSearchBox != null) {
            skillSearchBox.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        for (SkillUiButton button : categoryButtons.values()) {
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (subordinateListWidget != null) {
            subordinateListWidget.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        if (skillListWidget != null) {
            skillListWidget.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        for (SkillUiButton button : footerButtons) {
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderDetails(
            GuiGraphics guiGraphics
    ) {
        SkillUiLayout.Rect bounds = layout.details();
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                bounds,
                theme.panelBorderColor(),
                theme.panelFillColor(),
                1
        );

        if (selectedTarget == null) {
            SkillUiRenderHelper.drawCenteredText(
                    guiGraphics,
                    font,
                    SkillUiText.component("overview.no_subordinate_selected"),
                    bounds.centerX(),
                    bounds.centerY() - 4,
                    theme.mutedTextColor()
            );
            return;
        }

        int left = bounds.left() + 7;
        int top = bounds.top() + 7;
        int maximumWidth = Math.max(1, bounds.width() - 14);
        boolean compact = layout.compact();

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                UiTranslationToken.toComponent(selectedTarget.targetName()),
                left,
                top,
                maximumWidth,
                theme.accentColor()
        );

        int detailsTop;

        if (compact) {
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "overview.type_distance",
                            UiTranslationToken.toComponent(
                                    selectedTarget.typeName()
                            ),
                            formatDistance(selectedTarget.distance())
                    ),
                    left,
                    top + 11,
                    maximumWidth,
                    theme.secondaryTextColor()
            );
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "overview.health",
                            formatCompact(selectedTarget.health()),
                            formatCompact(selectedTarget.maxHealth())
                    ),
                    left,
                    top + 22,
                    maximumWidth,
                    theme.secondaryTextColor()
            );
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "overview.ep_magicules_compact",
                            formatNumber(selectedTarget.ep()),
                            formatNumber(selectedTarget.magicules())
                    ),
                    left,
                    top + 33,
                    maximumWidth,
                    theme.secondaryTextColor()
            );
            int grantedCount = grantedCount(selectedTarget);
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "overview.skill_grant_counts",
                            selectedTarget.skills().size(),
                            grantedCount
                    ),
                    left,
                    top + 44,
                    maximumWidth,
                    theme.mutedTextColor()
            );
            SkillUiRenderHelper.drawDivider(
                    guiGraphics,
                    bounds.left() + 5,
                    bounds.right() - 5,
                    top + 56,
                    theme.panelBorderColor()
            );
            detailsTop = top + 61;
        } else {
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    UiTranslationToken.toComponent(selectedTarget.typeName()),
                    left,
                    top + 12,
                    maximumWidth,
                    theme.secondaryTextColor()
            );
            SkillUiRenderHelper.drawText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "overview.distance",
                            formatDistance(selectedTarget.distance())
                    ),
                    left,
                    top + 24,
                    theme.mutedTextColor()
            );
            SkillUiRenderHelper.drawText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "overview.health",
                            formatCompact(selectedTarget.health()),
                            formatCompact(selectedTarget.maxHealth())
                    ),
                    left,
                    top + 35,
                    theme.secondaryTextColor()
            );
            SkillUiRenderHelper.drawText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "overview.ep",
                            formatNumber(selectedTarget.ep())
                    ),
                    left,
                    top + 46,
                    theme.secondaryTextColor()
            );
            SkillUiRenderHelper.drawText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "overview.magicules",
                            formatNumber(selectedTarget.magicules())
                    ),
                    left,
                    top + 57,
                    theme.secondaryTextColor()
            );
            int grantedCount = grantedCount(selectedTarget);
            SkillUiRenderHelper.drawText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "overview.skill_grant_counts",
                            selectedTarget.skills().size(),
                            grantedCount
                    ),
                    left,
                    top + 68,
                    theme.mutedTextColor()
            );
            SkillUiRenderHelper.drawDivider(
                    guiGraphics,
                    bounds.left() + 5,
                    bounds.right() - 5,
                    top + 81,
                    theme.panelBorderColor()
            );
            detailsTop = top + 86;
        }

        SkillUiLayout.Rect skillDetails = new SkillUiLayout.Rect(
                bounds.left() + 1,
                detailsTop,
                Math.max(1, bounds.width() - 2),
                Math.max(1, bounds.bottom() - detailsTop - 1)
        );
        guiGraphics.enableScissor(
                skillDetails.left(),
                skillDetails.top(),
                skillDetails.right(),
                skillDetails.bottom()
        );
        SkillDetailsPanel.render(
                guiGraphics,
                font,
                skillDetails,
                theme,
                skillListWidget == null
                        ? focusedSkill
                        : skillListWidget.hoveredEntry()
                        .orElse(focusedSkill),
                readOnlySelection
        );
        guiGraphics.disableScissor();
    }

    private void renderFooterStatus(
            GuiGraphics guiGraphics
    ) {
        SkillUiLayout.Rect footer = layout.footer();
        Component message = statusMessage;
        int color = statusColor;

        if (message.getString().isBlank()) {
            message = SkillUiText.component(
                    payload.refreshAllowed()
                            ? "overview.footer_auto_refresh"
                            : "overview.footer_read_only"
            );
            color = theme.mutedTextColor();
        }

        int rightLimit = refreshButton == null
                ? footer.right()
                : refreshButton.getX() - GAP;
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                message,
                footer.left() + 2,
                footer.top() + footer.height() / 2 - 4,
                Math.max(1, rightLimit - footer.left() - 4),
                color
        );
    }

    private static int grantedCount(
            OpenSubordinateOverviewScreenPayload.TargetEntry target
    ) {
        if (target == null) {
            return 0;
        }

        return (int) target.skills()
                .stream()
                .filter(OpenSubordinateOverviewScreenPayload.SkillEntry
                        ::grantedByViewer)
                .count();
    }

    private Optional<OpenSubordinateOverviewScreenPayload.TargetEntry>
    findTarget(
            String targetUuid
    ) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return Optional.empty();
        }

        return payload.targets().stream()
                .filter(target -> target.targetUuid().equals(targetUuid))
                .findFirst();
    }

    private static Component categoryLabel(
            SkillUiCategory category,
            int width
    ) {
        if (width < 34) {
            return SkillUiText.component(
                    "category." + category.id() + "_compact"
            );
        }

        if (width < 54) {
            return SkillUiText.component(
                    "category." + category.id() + "_short"
            );
        }

        return category.displayName();
    }

    private Component fitSearchHint(
            Component hint,
            int editBoxWidth
    ) {
        int maximumTextWidth = Math.max(
                1,
                editBoxWidth - 10
        );

        if (font.width(hint) <= maximumTextWidth) {
            return hint;
        }

        String ellipsis = "...";
        int clippedWidth = Math.max(
                1,
                maximumTextWidth - font.width(ellipsis)
        );
        String clippedText = font.plainSubstrByWidth(
                hint.getString(),
                clippedWidth
        );

        return Component.literal(clippedText + ellipsis);
    }

    private static SkillUiTheme resolveTheme(
            OpenSubordinateOverviewScreenPayload payload
    ) {
        if (payload == null) {
            return SkillUiTheme.GRANTER;
        }

        if (payload.benevolent()) {
            return SkillUiTheme.BENEVOLENT;
        }

        if (payload.governance()) {
            return SkillUiTheme.GOVERNANCE;
        }

        return SkillUiTheme.GRANTER;
    }

    private static OpenSubordinateOverviewScreenPayload emptyPayload() {
        return new OpenSubordinateOverviewScreenPayload(
                OpenSubordinateOverviewScreenPayload.THEME_GRANTER,
                SubordinateOverviewPolicy.NEARBY_RADIUS,
                false,
                false,
                List.of()
        );
    }

    private static String formatRadius(
            double radius
    ) {
        return String.format(Locale.US, "%.0f", radius);
    }

    private static String formatDistance(
            double distance
    ) {
        return String.format(
                Locale.US,
                distance < 10.0D ? "%.1f m" : "%.0f m",
                distance
        );
    }

    private static String formatCompact(
            double value
    ) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String formatNumber(
            double value
    ) {
        return String.format(Locale.US, "%,.0f", value);
    }

    private record OverviewLayout(
            SkillUiLayout.Rect panel,
            SkillUiLayout.Rect header,
            SkillUiLayout.Rect targetLabel,
            SkillUiLayout.Rect skillLabel,
            SkillUiLayout.Rect detailLabel,
            SkillUiLayout.Rect targetFilter,
            SkillUiLayout.Rect skillFilter,
            SkillUiLayout.Rect targets,
            SkillUiLayout.Rect skills,
            SkillUiLayout.Rect details,
            SkillUiLayout.Rect footer,
            boolean compact
    ) {

        private static OverviewLayout calculate(
                int screenWidth,
                int screenHeight
        ) {
            boolean compact = screenHeight < 310;
            int outerMargin = compact ? 3 : 8;
            int panelWidth = Math.max(
                    1,
                    screenWidth - outerMargin * 2
            );
            int panelHeight = Math.max(
                    1,
                    screenHeight - outerMargin * 2
            );
            int panelLeft = (screenWidth - panelWidth) / 2;
            int panelTop = (screenHeight - panelHeight) / 2;
            SkillUiLayout.Rect panel = new SkillUiLayout.Rect(
                    panelLeft,
                    panelTop,
                    panelWidth,
                    panelHeight
            );

            int inset = compact ? 5 : 8;
            int contentLeft = panel.left() + inset;
            int contentRight = panel.right() - inset;
            int headerHeight = compact ? 30 : 38;
            int labelHeight = compact ? 10 : 12;
            int filterHeight = compact ? 18 : 20;
            int footerHeight = compact ? 22 : 26;
            int headerTop = panel.top() + inset;
            SkillUiLayout.Rect header = new SkillUiLayout.Rect(
                    contentLeft,
                    headerTop,
                    Math.max(1, contentRight - contentLeft),
                    headerHeight
            );

            int labelsTop = header.bottom() + (compact ? 2 : 4);
            int filtersTop = labelsTop + labelHeight;
            int listsTop = filtersTop + filterHeight + 4;
            int footerTop = panel.bottom() - inset - footerHeight;
            int listHeight = Math.max(
                    1,
                    footerTop - listsTop - 4
            );
            int contentWidth = Math.max(
                    1,
                    contentRight - contentLeft
            );

            int targetMinimum = compact ? 118 : 165;
            int targetMaximum = compact ? 155 : 320;
            int targetWidth = Math.max(
                    targetMinimum,
                    Math.min(
                            targetMaximum,
                            contentWidth * 24 / 100
                    )
            );
            targetWidth = Math.min(
                    targetWidth,
                    Math.max(1, contentWidth - GAP * 2 - 220)
            );

            int remaining = Math.max(
                    2,
                    contentWidth - targetWidth - GAP * 2
            );
            int minimumDetailWidth = compact ? 108 : 180;
            int desiredSkillWidth = remaining * 55 / 100;
            int skillWidth = Math.max(
                    compact ? 145 : 210,
                    desiredSkillWidth
            );
            skillWidth = Math.min(
                    skillWidth,
                    Math.max(1, remaining - minimumDetailWidth)
            );
            int detailWidth = Math.max(
                    1,
                    remaining - skillWidth
            );

            int targetLeft = contentLeft;
            int skillLeft = targetLeft + targetWidth + GAP;
            int detailLeft = skillLeft + skillWidth + GAP;

            SkillUiLayout.Rect targetLabel = new SkillUiLayout.Rect(
                    targetLeft,
                    labelsTop,
                    targetWidth,
                    labelHeight
            );
            SkillUiLayout.Rect skillLabel = new SkillUiLayout.Rect(
                    skillLeft,
                    labelsTop,
                    skillWidth,
                    labelHeight
            );
            SkillUiLayout.Rect detailLabel = new SkillUiLayout.Rect(
                    detailLeft,
                    labelsTop,
                    detailWidth,
                    labelHeight
            );
            SkillUiLayout.Rect targetFilter = new SkillUiLayout.Rect(
                    targetLeft,
                    filtersTop,
                    targetWidth,
                    filterHeight
            );
            SkillUiLayout.Rect skillFilter = new SkillUiLayout.Rect(
                    skillLeft,
                    filtersTop,
                    Math.max(1, contentRight - skillLeft),
                    filterHeight
            );
            SkillUiLayout.Rect targets = new SkillUiLayout.Rect(
                    targetLeft,
                    listsTop,
                    targetWidth,
                    listHeight
            );
            SkillUiLayout.Rect skills = new SkillUiLayout.Rect(
                    skillLeft,
                    listsTop,
                    skillWidth,
                    listHeight
            );
            SkillUiLayout.Rect details = new SkillUiLayout.Rect(
                    detailLeft,
                    listsTop,
                    detailWidth,
                    listHeight
            );
            SkillUiLayout.Rect footer = new SkillUiLayout.Rect(
                    contentLeft,
                    footerTop,
                    contentWidth,
                    footerHeight
            );

            return new OverviewLayout(
                    panel,
                    header,
                    targetLabel,
                    skillLabel,
                    detailLabel,
                    targetFilter,
                    skillFilter,
                    targets,
                    skills,
                    details,
                    footer,
                    compact
            );
        }
    }
}