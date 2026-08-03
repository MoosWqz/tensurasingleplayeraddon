package com.mooswqz.moostensuraaddon.client.screen;

import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiButton;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiEntry;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiLayout;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiRenderHelper;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiTheme;
import com.mooswqz.moostensuraaddon.client.screen.skillui.SkillUiText;
import com.mooswqz.moostensuraaddon.client.screen.skillui.UltimateConfirmationPolicy;
import com.mooswqz.moostensuraaddon.client.screen.skillui.UltimateMultiGrantUiEntryFactory;
import com.mooswqz.moostensuraaddon.network.ExecuteUltimateConfirmationPayload;
import com.mooswqz.moostensuraaddon.network.ExecuteUltimateMultiGrantPayload;
import com.mooswqz.moostensuraaddon.network.ExecuteUltimateSubordinateSkillPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateConfirmationScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateMultiGrantScreenPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateSubordinateSkillScreenPayload;
import com.mooswqz.moostensuraaddon.util.AuthorityActionMode;
import com.mooswqz.moostensuraaddon.util.UltimateBorrowSeizePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UltimateConfirmationScreen extends Screen {

    private final Screen parent;
    private final ConfirmationRequest request;

    private Layout layout;
    private SkillUiButton backButton;
    private SkillUiButton confirmButton;
    private boolean submitted;

    private UltimateConfirmationScreen(
            Screen parent,
            ConfirmationRequest request
    ) {
        super(
                request == null
                        ? SkillUiText.component("confirmation.default_title")
                        : Component.literal(request.title())
        );
        this.parent = parent;
        this.request = request == null
                ? ConfirmationRequest.empty()
                : request;
    }

    public UltimateConfirmationScreen(
            OpenUltimateConfirmationScreenPayload payload
    ) {
        this(
                null,
                legacyRequest(payload)
        );
    }

    public static UltimateConfirmationScreen forAuthorityAction(
            Screen parent,
            OpenUltimateMultiGrantScreenPayload payload,
            AuthorityActionMode mode,
            List<SkillUiEntry> selectedEntries,
            Map<String, UltimateMultiGrantUiEntryFactory.CostBreakdown> costs,
            boolean allEligible
    ) {
        AuthorityActionMode safeMode = mode == null
                ? AuthorityActionMode.BENEVOLENT_BESTOW
                : mode;
        OpenUltimateMultiGrantScreenPayload safePayload = payload == null
                ? new OpenUltimateMultiGrantScreenPayload(
                safeMode.id(),
                "",
                SkillUiText.string("common.unknown_subordinate"),
                0.0D,
                0,
                false,
                false,
                List.of()
        )
                : payload;
        List<SkillUiEntry> safeEntries = selectedEntries == null
                ? List.of()
                : List.copyOf(selectedEntries);
        Map<String, UltimateMultiGrantUiEntryFactory.CostBreakdown>
                safeCosts = costs == null
                ? Map.of()
                : Map.copyOf(costs);
        List<String> selectedIds = safeEntries.stream()
                .map(SkillUiEntry::skillId)
                .filter(skillId -> skillId != null
                        && !skillId.isBlank())
                .toList();
        UltimateMultiGrantUiEntryFactory.SelectionSummary summary =
                UltimateMultiGrantUiEntryFactory.summarizeSelection(
                        selectedIds,
                        safeCosts
                );
        int affectedTargets = safeMode.directGrant()
                ? 1
                : Math.max(0, summary.affectedTargets());
        String subjectLabel;
        String subjectValue;

        if (safeMode.massGrant()) {
            subjectLabel = SkillUiText.string("common.scope_label");
            subjectValue = SkillUiText.string(
                    affectedTargets == 1
                            ? "confirmation.eligible_subordinate_one"
                            : "confirmation.eligible_subordinate_many",
                    affectedTargets
            );
        } else if (safeMode.takeBack() && allEligible) {
            subjectLabel = SkillUiText.string("common.scope_label");
            subjectValue = SkillUiText.string(
                    affectedTargets == 1
                            ? "confirmation.eligible_grant_one"
                            : "confirmation.eligible_grant_many",
                    affectedTargets
            );
        } else {
            subjectLabel = SkillUiText.string("common.target_label");
            subjectValue = safePayload.targetName().isBlank()
                    ? SkillUiText.string("common.unknown_subordinate")
                    : safePayload.targetName();
        }

        List<SkillLine> skillLines = new ArrayList<>();

        for (SkillUiEntry entry : safeEntries) {
            UltimateMultiGrantUiEntryFactory.CostBreakdown cost =
                    safeCosts.get(entry.skillId());
            String secondary;

            if (safeMode.takeBack()) {
                secondary = SkillUiText.string(
                        "confirmation.affected_no_cost",
                        affectedTargets
                );
            } else if (cost != null && !cost.mastered()) {
                secondary = SkillUiText.string(
                        "confirmation.unmastered_cost",
                        formatNumber(cost.finalCost())
                );
            } else {
                secondary = SkillUiText.string(
                        "confirmation.mastered_cost",
                        formatNumber(
                                cost == null ? 0.0D : cost.finalCost()
                        )
                );
            }

            skillLines.add(
                    new SkillLine(
                            entry.displayName().getString(),
                            secondary,
                            cost == null ? 0.0D : cost.finalCost(),
                            cost != null && !cost.mastered()
                    )
            );
        }

        SkillUiTheme theme = resolveTheme(safeMode);
        String warning = SkillUiText.string(
                switch (safeMode) {
                    case BENEVOLENT_BESTOW ->
                            "confirmation.warning_bestow";
                    case GOVERNANCE_INVEST ->
                            "confirmation.warning_invest";
                    case BENEVOLENT_MASS_GRANT,
                         GOVERNANCE_MASS_GRANT ->
                            "confirmation.warning_mass_grant";
                    case BENEVOLENT_TAKE_BACK,
                         GOVERNANCE_TAKE_BACK ->
                            "confirmation.warning_take_back";
                    case GRANTER_GRANT,
                         GRANTER_TAKE_BACK ->
                            "confirmation.warning_revalidate";
                }
        );
        String outcome = summary.unmastered() > 0
                ? SkillUiText.string(
                summary.unmastered() == 1
                ? "confirmation.outcome_unmastered_one"
                : "confirmation.outcome_unmastered_many",
                summary.unmastered()
        )
                : SkillUiText.string(
                "confirmation.outcome_standard_cost"
        );
        boolean ready = !safeEntries.isEmpty()
                && affectedTargets > 0
                && safePayload.cooldownTicks() <= 0
                && UltimateConfirmationPolicy.affordable(
                safePayload.availableMagicules(),
                summary.totalCost()
        );
        Runnable confirmAction = () ->
                PacketDistributor.sendToServer(
                        new ExecuteUltimateMultiGrantPayload(
                                safeMode.id(),
                                safePayload.targetUuid(),
                                allEligible,
                                selectedIds
                        )
                );

        return new UltimateConfirmationScreen(
                parent,
                new ConfirmationRequest(
                        SkillUiText.string(
                                "confirmation.confirm_action",
                                safeMode.titleComponent()
                        ),
                        safeMode.badge(),
                        authorityName(safeMode),
                        subjectLabel,
                        subjectValue,
                        List.copyOf(skillLines),
                        safeEntries.size(),
                        affectedTargets,
                        summary.mastered(),
                        summary.unmastered(),
                        summary.standardCost(),
                        summary.surcharge(),
                        summary.totalCost(),
                        safePayload.availableMagicules(),
                        outcome,
                        warning,
                        confirmLabel(safeMode),
                        theme,
                        false,
                        ready,
                        confirmAction
                )
        );
    }

    public static UltimateConfirmationScreen forBorrowOrSeize(
            Screen parent,
            OpenUltimateSubordinateSkillScreenPayload payload,
            List<SkillUiEntry> selectedEntries,
            Map<String, Double> borrowChances
    ) {
        OpenUltimateSubordinateSkillScreenPayload safePayload = payload == null
                ? new OpenUltimateSubordinateSkillScreenPayload(
                false,
                "",
                SkillUiText.string("common.unknown_subordinate"),
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                List.of()
        )
                : payload;
        List<SkillUiEntry> safeEntries = selectedEntries == null
                ? List.of()
                : List.copyOf(selectedEntries);
        Map<String, Double> safeBorrowChances = borrowChances == null
                ? Map.of()
                : Map.copyOf(borrowChances);
        List<String> selectedIds = safeEntries.stream()
                .map(SkillUiEntry::skillId)
                .filter(skillId -> skillId != null
                        && !skillId.isBlank())
                .toList();
        double totalCost = UltimateConfirmationPolicy.totalCost(
                safePayload.costPerSkill(),
                selectedIds.size()
        );
        double combinedRisk = safePayload.seize()
                ? UltimateConfirmationPolicy.combinedSeizeRisk(
                safePayload.seizeDeathChancePerSkill(),
                safePayload.seizeDeathChanceMax(),
                selectedIds.size()
        )
                : 0.0D;
        List<Double> selectedChances = selectedIds.stream()
                .map(skillId -> safeBorrowChances.getOrDefault(
                        skillId,
                        0.0D
                ))
                .toList();
        UltimateConfirmationPolicy.ChanceRange chanceRange =
                UltimateConfirmationPolicy.chanceRange(
                        selectedChances
                );
        List<SkillLine> skillLines = new ArrayList<>();

        for (SkillUiEntry entry : safeEntries) {
            double chance = safeBorrowChances.getOrDefault(
                    entry.skillId(),
                    0.0D
            );
            String masteryLabel = SkillUiText.string(
                    entry.mastered()
                            ? "confirmation.target_mastered"
                            : "state.not_mastered"
            );
            String secondary = safePayload.seize()
                    ? SkillUiText.string(
                    "confirmation.seize_skill_line",
                    masteryLabel,
                    formatNumber(safePayload.costPerSkill())
            )
                    : SkillUiText.string(
                    "confirmation.borrow_skill_line",
                    masteryLabel,
                    UltimateBorrowSeizePolicy.formatPercent(chance),
                    formatNumber(safePayload.costPerSkill())
            );

            skillLines.add(
                    new SkillLine(
                            entry.displayName().getString(),
                            secondary,
                            safePayload.costPerSkill(),
                            safePayload.seize()
                    )
            );
        }

        String outcome;

        if (safePayload.seize()) {
            outcome = SkillUiText.string(
                    "confirmation.combined_death_risk",
                    UltimateBorrowSeizePolicy.formatPercent(combinedRisk)
            );
        } else if (chanceRange.minimum() == chanceRange.maximum()) {
            outcome = SkillUiText.string(
                    "confirmation.permanent_chance_single",
                    UltimateBorrowSeizePolicy.formatPercent(
                            chanceRange.minimum()
                    )
            );
        } else {
            outcome = SkillUiText.string(
                    "confirmation.permanent_chance_range",
                    UltimateBorrowSeizePolicy.formatPercent(
                            chanceRange.minimum()
                    ),
                    UltimateBorrowSeizePolicy.formatPercent(
                            chanceRange.maximum()
                    )
            );
        }

        String warning = SkillUiText.string(
                safePayload.seize()
                        ? "confirmation.warning_seize"
                        : "confirmation.warning_borrow"
        );
        boolean ready = !selectedIds.isEmpty()
                && UltimateConfirmationPolicy.affordable(
                safePayload.availableMagicules(),
                totalCost
        );
        Runnable confirmAction = () ->
                PacketDistributor.sendToServer(
                        new ExecuteUltimateSubordinateSkillPayload(
                                safePayload.seize(),
                                safePayload.targetUuid(),
                                selectedIds
                        )
                );

        return new UltimateConfirmationScreen(
                parent,
                new ConfirmationRequest(
                        SkillUiText.string(
                                safePayload.seize()
                                        ? "confirmation.title_seize"
                                        : "confirmation.title_borrow"
                        ),
                        SkillUiText.string(
                                safePayload.seize()
                                        ? "seize.badge"
                                        : "borrow.badge"
                        ),
                        SkillUiText.string(
                                safePayload.seize()
                                        ? "authority.governance"
                                        : "authority.benevolent"
                        ),
                        SkillUiText.string("common.target_label"),
                        safePayload.targetName().isBlank()
                                ? SkillUiText.string("common.unknown_subordinate")
                                : safePayload.targetName(),
                        List.copyOf(skillLines),
                        safeEntries.size(),
                        safeEntries.isEmpty() ? 0 : 1,
                        0,
                        0,
                        totalCost,
                        0.0D,
                        totalCost,
                        safePayload.availableMagicules(),
                        outcome,
                        warning,
                        SkillUiText.string(
                                safePayload.seize()
                                        ? "confirmation.confirm_seize"
                                        : "confirmation.confirm_borrow"
                        ),
                        safePayload.seize()
                                ? SkillUiTheme.SEIZE
                                : SkillUiTheme.BENEVOLENT,
                        safePayload.seize(),
                        ready,
                        confirmAction
                )
        );
    }

    public static void open(
            OpenUltimateConfirmationScreenPayload payload
    ) {
        Minecraft.getInstance().setScreen(
                new UltimateConfirmationScreen(payload)
        );
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        int gap = 6;
        int buttonWidth = Math.max(
                80,
                (layout.footer().width() - gap) / 2
        );

        backButton = addRenderableWidget(
                new SkillUiButton(
                        layout.footer().left(),
                        layout.footer().top(),
                        buttonWidth,
                        layout.footer().height(),
                        parent == null
                                ? SkillUiText.component("common.cancel")
                                : SkillUiText.component("common.back"),
                        request.theme(),
                        SkillUiButton.Tone.NORMAL,
                        this::goBack
                )
        );
        confirmButton = addRenderableWidget(
                new SkillUiButton(
                        layout.footer().right() - buttonWidth,
                        layout.footer().top(),
                        buttonWidth,
                        layout.footer().height(),
                        Component.literal(request.confirmLabel()),
                        request.theme(),
                        request.danger()
                                ? SkillUiButton.Tone.DANGER
                                : SkillUiButton.Tone.PRIMARY,
                        this::confirm
                )
        );
        confirmButton.active = request.ready() && !submitted;
    }

    private void confirm() {
        if (submitted || !request.ready()) {
            return;
        }

        submitted = true;

        if (confirmButton != null) {
            confirmButton.active = false;
        }

        request.confirmAction().run();
        Minecraft.getInstance().setScreen(null);
    }

    private void goBack() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        goBack();
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            goBack();
            return true;
        }

        return super.keyPressed(
                keyCode,
                scanCode,
                modifiers
        );
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        SkillUiTheme theme = request.theme();
        SkillUiRenderHelper.fillBackground(
                guiGraphics,
                width,
                height,
                theme
        );
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                layout.panel(),
                request.danger()
                        ? theme.dangerColor()
                        : theme.accentColor(),
                theme.panelFillColor(),
                2
        );
        renderHeader(guiGraphics);
        renderSubject(guiGraphics);
        renderSummary(guiGraphics);
        renderSkills(guiGraphics);
        renderWarning(guiGraphics);
        renderButtonsWithoutParentBlur(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private void renderHeader(
            GuiGraphics guiGraphics
    ) {
        SkillUiTheme theme = request.theme();
        SkillUiLayout.Rect header = layout.header();
        int badgeLeft = SkillUiRenderHelper.drawModeBadge(
                guiGraphics,
                font,
                Component.literal(request.badge()),
                header.right() - 2,
                header.top() + 2,
                theme
        );
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(request.title()),
                header.left() + 2,
                header.top() + 3,
                Math.max(
                        1,
                        badgeLeft - header.left() - 8
                ),
                theme.primaryTextColor()
        );
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(request.authorityName()),
                header.left() + 2,
                header.top() + 17,
                Math.max(1, header.width() - 4),
                theme.secondaryTextColor()
        );
        SkillUiRenderHelper.drawDivider(
                guiGraphics,
                header.left(),
                header.right(),
                header.bottom() - 1,
                theme.panelBorderColor()
        );
    }

    private void renderSubject(
            GuiGraphics guiGraphics
    ) {
        SkillUiTheme theme = request.theme();
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                layout.subject(),
                theme.panelBorderColor(),
                theme.categoryFillColor(),
                1
        );
        SkillUiRenderHelper.drawText(
                guiGraphics,
                font,
                Component.literal(request.subjectLabel()),
                layout.subject().left() + 5,
                layout.subject().top() + 4,
                theme.accentColor()
        );
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(request.subjectValue()),
                layout.subject().left() + 58,
                layout.subject().top() + 4,
                Math.max(1, layout.subject().width() - 64),
                theme.primaryTextColor()
        );
    }

    private void renderSummary(
            GuiGraphics guiGraphics
    ) {
        SkillUiTheme theme = request.theme();
        SkillUiLayout.Rect bounds = layout.summary();
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                bounds,
                theme.panelBorderColor(),
                theme.rowFillColor(),
                1
        );
        int x = bounds.left() + 6;
        int y = bounds.top() + 5;
        int lineHeight = layout.compact() ? 10 : 12;

        guiGraphics.enableScissor(
                bounds.left() + 2,
                bounds.top() + 2,
                bounds.right() - 2,
                bounds.bottom() - 2
        );

        if (layout.compact()) {
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "confirmation.compact_selected_affected",
                            request.selectedSkills(),
                            request.affectedTargets()
                    ),
                    x,
                    y,
                    Math.max(1, bounds.width() - 12),
                    theme.primaryTextColor()
            );
            y += lineHeight;

            if (request.unmasteredSkills() > 0) {
                SkillUiRenderHelper.drawClippedText(
                        guiGraphics,
                        font,
                        SkillUiText.component(
                                "confirmation.compact_unmastered",
                                request.unmasteredSkills()
                        ),
                        x,
                        y,
                        Math.max(1, bounds.width() - 12),
                        theme.warningColor()
                );
                y += lineHeight;
            }

            String compactCostLine = request.availableMagicules() >= 0.0D
                    ? SkillUiText.string(
                    "confirmation.compact_required_available",
                    formatNumber(request.totalCost()),
                    formatNumber(request.availableMagicules())
            )
                    : SkillUiText.string(
                    "confirmation.compact_required",
                    formatNumber(request.totalCost())
            );
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    Component.literal(compactCostLine),
                    x,
                    y,
                    Math.max(1, bounds.width() - 12),
                    request.availableMagicules() >= 0.0D
                            && request.totalCost()
                            > request.availableMagicules()
                            ? theme.dangerColor()
                            : theme.primaryTextColor()
            );
            y += lineHeight;

            if (y < bounds.bottom() - 8) {
                SkillUiRenderHelper.drawClippedText(
                        guiGraphics,
                        font,
                        Component.literal(request.outcomeLine()),
                        x,
                        y,
                        Math.max(1, bounds.width() - 12),
                        request.danger()
                                ? theme.dangerColor()
                                : theme.accentColor()
                );
            }

            guiGraphics.disableScissor();
            return;
        }

        drawSummaryLine(
                guiGraphics,
                SkillUiText.string("confirmation.label_selected_skills"),
                Integer.toString(request.selectedSkills()),
                x,
                y,
                bounds.width() - 12,
                theme.primaryTextColor()
        );
        y += lineHeight;
        drawSummaryLine(
                guiGraphics,
                SkillUiText.string("confirmation.label_affected"),
                Integer.toString(request.affectedTargets()),
                x,
                y,
                bounds.width() - 12,
                theme.primaryTextColor()
        );
        y += lineHeight;

        if (request.unmasteredSkills() > 0) {
            drawSummaryLine(
                    guiGraphics,
                    SkillUiText.string("confirmation.label_unmastered"),
                    Integer.toString(request.unmasteredSkills()),
                    x,
                    y,
                    bounds.width() - 12,
                    theme.warningColor()
            );
            y += lineHeight;
        }

        if (request.surcharge() > 0.0D) {
            drawSummaryLine(
                    guiGraphics,
                    SkillUiText.string("confirmation.label_base_cost"),
                    formatNumber(request.standardCost()),
                    x,
                    y,
                    bounds.width() - 12,
                    theme.secondaryTextColor()
            );
            y += lineHeight;
            drawSummaryLine(
                    guiGraphics,
                    SkillUiText.string("confirmation.label_surcharge"),
                    "+" + formatNumber(request.surcharge()),
                    x,
                    y,
                    bounds.width() - 12,
                    theme.warningColor()
            );
            y += lineHeight;
        }

        drawSummaryLine(
                guiGraphics,
                SkillUiText.string("confirmation.label_required"),
                formatNumber(request.totalCost()),
                x,
                y,
                bounds.width() - 12,
                request.availableMagicules() >= 0.0D
                        && request.totalCost() > request.availableMagicules()
                        ? theme.dangerColor()
                        : theme.primaryTextColor()
        );
        y += lineHeight;

        if (request.availableMagicules() >= 0.0D) {
            drawSummaryLine(
                    guiGraphics,
                    SkillUiText.string("confirmation.label_available"),
                    formatNumber(request.availableMagicules()),
                    x,
                    y,
                    bounds.width() - 12,
                    theme.secondaryTextColor()
            );
            y += lineHeight;
        }

        if (y < bounds.bottom() - 10) {
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    Component.literal(request.outcomeLine()),
                    x,
                    y + 2,
                    Math.max(1, bounds.width() - 12),
                    request.danger()
                            ? theme.dangerColor()
                            : theme.accentColor()
            );
        }

        guiGraphics.disableScissor();
    }

    private void drawSummaryLine(
            GuiGraphics guiGraphics,
            String label,
            String value,
            int x,
            int y,
            int width,
            int valueColor
    ) {
        SkillUiTheme theme = request.theme();
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(label + ":"),
                x,
                y,
                Math.max(1, width / 2),
                theme.mutedTextColor()
        );
        int valueX = x + width / 2;
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(value),
                valueX,
                y,
                Math.max(1, width - width / 2),
                valueColor
        );
    }

    private void renderSkills(
            GuiGraphics guiGraphics
    ) {
        SkillUiTheme theme = request.theme();
        SkillUiLayout.Rect bounds = layout.skills();
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                bounds,
                theme.panelBorderColor(),
                theme.rowFillColor(),
                1
        );
        SkillUiRenderHelper.drawText(
                guiGraphics,
                font,
                SkillUiText.component("confirmation.selected_skills_header"),
                bounds.left() + 5,
                bounds.top() + 4,
                theme.accentColor()
        );
        int top = bounds.top() + 17;
        int availableHeight = Math.max(
                1,
                bounds.bottom() - top - 4
        );
        int visibleRows = UltimateConfirmationPolicy.visibleSkillRows(
                availableHeight,
                layout.compact()
        );
        int rowHeight = layout.compact() ? 11 : 13;
        int displayed = Math.min(
                visibleRows,
                request.skills().size()
        );

        guiGraphics.enableScissor(
                bounds.left() + 2,
                top,
                bounds.right() - 2,
                bounds.bottom() - 2
        );

        for (int index = 0; index < displayed; index++) {
            SkillLine skill = request.skills().get(index);
            int y = top + index * rowHeight;
            int nameWidth = Math.max(1, bounds.width() * 56 / 100);
            int costWidth = Math.max(1, bounds.width() - nameWidth - 14);

            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    Component.literal(skill.displayName()),
                    bounds.left() + 6,
                    y,
                    nameWidth,
                    skill.warning()
                            ? theme.warningColor()
                            : theme.primaryTextColor()
            );
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    Component.literal(skill.secondary()),
                    bounds.left() + 6 + nameWidth,
                    y,
                    costWidth,
                    theme.mutedTextColor()
            );
        }

        if (request.skills().size() > displayed) {
            int remaining = request.skills().size() - displayed;
            int y = top + displayed * rowHeight;
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    SkillUiText.component(
                            "confirmation.more_selected",
                            remaining
                    ),
                    bounds.left() + 6,
                    y,
                    Math.max(1, bounds.width() - 12),
                    theme.secondaryTextColor()
            );
        }

        guiGraphics.disableScissor();
    }

    private void renderWarning(
            GuiGraphics guiGraphics
    ) {
        SkillUiTheme theme = request.theme();
        SkillUiLayout.Rect bounds = layout.warning();
        int borderColor = request.danger()
                ? theme.dangerColor()
                : request.unmasteredSkills() > 0
                  ? theme.warningColor()
                  : theme.panelBorderColor();
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                bounds,
                borderColor,
                theme.categoryFillColor(),
                1
        );
        int textColor = request.danger()
                ? theme.dangerColor()
                : request.unmasteredSkills() > 0
                  ? theme.warningColor()
                  : theme.secondaryTextColor();
        List<String> lines = wrapText(
                request.warning(),
                Math.max(1, bounds.width() - 12),
                layout.compact() ? 2 : 3
        );
        int y = bounds.top() + 5;

        for (String line : lines) {
            SkillUiRenderHelper.drawText(
                    guiGraphics,
                    font,
                    Component.literal(line),
                    bounds.left() + 6,
                    y,
                    textColor
            );
            y += 10;
        }
    }

    private List<String> wrapText(
            String text,
            int maximumWidth,
            int maximumLines
    ) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.trim().split("\\s+")) {
            String candidate = current.isEmpty()
                    ? word
                    : current + " " + word;

            if (font.width(candidate) <= maximumWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }

            if (!current.isEmpty()) {
                lines.add(current.toString());
            }

            current.setLength(0);
            current.append(word);

            if (lines.size() >= maximumLines - 1) {
                break;
            }
        }

        if (!current.isEmpty() && lines.size() < maximumLines) {
            lines.add(current.toString());
        }

        if (lines.size() == maximumLines
                && font.width(lines.getLast()) > maximumWidth) {
            String clipped = font.plainSubstrByWidth(
                    lines.getLast(),
                    Math.max(0, maximumWidth - font.width("…"))
            );
            lines.set(
                    lines.size() - 1,
                    clipped + "…"
            );
        }

        return List.copyOf(lines);
    }

    private void renderButtonsWithoutParentBlur(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (backButton != null) {
            backButton.render(
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

    private static ConfirmationRequest legacyRequest(
            OpenUltimateConfirmationScreenPayload payload
    ) {
        if (payload == null) {
            return ConfirmationRequest.empty();
        }

        SkillUiTheme theme = payload.benevolent()
                ? SkillUiTheme.BENEVOLENT
                : SkillUiTheme.GOVERNANCE;
        String action = SkillUiText.string(
                payload.massGrant()
                        ? "action.legacy_mass_grant.title"
                        : payload.benevolent()
                          ? "action.benevolent_take_back.title"
                          : "action.governance_take_back.title"
        );
        Runnable confirmAction = () ->
                PacketDistributor.sendToServer(
                        new ExecuteUltimateConfirmationPayload(
                                payload.massGrant(),
                                payload.benevolent()
                        )
                );

        return new ConfirmationRequest(
                SkillUiText.string(
                        "confirmation.confirm_action",
                        action
                ),
                SkillUiText.string(
                        payload.massGrant()
                                ? "action.legacy_mass_grant.badge"
                                : "confirmation.take_back_badge"
                ),
                SkillUiText.string(
                        payload.benevolent()
                                ? "authority.benevolent"
                                : "authority.governance"
                ),
                SkillUiText.string("common.scope_label"),
                SkillUiText.string(
                        payload.affectedTargets() == 1
                                ? "confirmation.affected_subordinate_one"
                                : "confirmation.affected_subordinate_many",
                        payload.affectedTargets()
                ),
                List.of(
                        new SkillLine(
                                payload.selectedSkillName(),
                                SkillUiText.string(
                                        payload.massGrant()
                                                ? "state.mastered"
                                                : "overview.skill_granted_by_you"
                                ),
                                payload.totalCost(),
                                false
                        )
                ),
                1,
                payload.affectedTargets(),
                payload.massGrant() ? 1 : 0,
                0,
                payload.totalCost(),
                0.0D,
                payload.totalCost(),
                -1.0D,
                SkillUiText.string(
                        payload.massGrant()
                                ? "confirmation.legacy_outcome_mass"
                                : "confirmation.legacy_outcome_take_back"
                ),
                SkillUiText.string(
                        payload.massGrant()
                                ? "confirmation.legacy_warning_mass"
                                : "confirmation.legacy_warning_take_back"
                ),
                SkillUiText.string(
                        payload.massGrant()
                                ? "confirmation.confirm_grant"
                                : "confirmation.confirm_take_back"
                ),
                theme,
                false,
                payload.affectedTargets() > 0,
                confirmAction
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

    private static String authorityName(
            AuthorityActionMode mode
    ) {
        if (mode == null || mode.granter()) {
            return SkillUiText.string("authority.granter");
        }

        return SkillUiText.string(
                mode.benevolent()
                        ? "authority.benevolent"
                        : "authority.governance"
        );
    }

    private static String confirmLabel(
            AuthorityActionMode mode
    ) {
        return SkillUiText.string(
                switch (mode) {
                    case BENEVOLENT_BESTOW ->
                            "confirmation.confirm_bestow";
                    case GOVERNANCE_INVEST ->
                            "confirmation.confirm_invest";
                    case BENEVOLENT_MASS_GRANT,
                         GOVERNANCE_MASS_GRANT ->
                            "confirmation.confirm_mass_grant";
                    case BENEVOLENT_TAKE_BACK,
                         GOVERNANCE_TAKE_BACK,
                         GRANTER_TAKE_BACK ->
                            "confirmation.confirm_take_back";
                    case GRANTER_GRANT ->
                            "confirmation.confirm_grant";
                }
        );
    }

    private static String formatNumber(
            double value
    ) {
        return String.format(
                Locale.US,
                "%,.0f",
                Math.max(0.0D, value)
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public record SkillLine(
            String displayName,
            String secondary,
            double cost,
            boolean warning
    ) {
        public SkillLine {
            displayName = displayName == null
                    ? ""
                    : displayName.trim();
            secondary = secondary == null
                    ? ""
                    : secondary.trim();
            cost = UltimateConfirmationPolicy.sanitizeCost(cost);
        }
    }

    public record ConfirmationRequest(
            String title,
            String badge,
            String authorityName,
            String subjectLabel,
            String subjectValue,
            List<SkillLine> skills,
            int selectedSkills,
            int affectedTargets,
            int masteredSkills,
            int unmasteredSkills,
            double standardCost,
            double surcharge,
            double totalCost,
            double availableMagicules,
            String outcomeLine,
            String warning,
            String confirmLabel,
            SkillUiTheme theme,
            boolean danger,
            boolean ready,
            Runnable confirmAction
    ) {
        public ConfirmationRequest {
            title = clean(title, SkillUiText.string("confirmation.default_title"));
            badge = clean(badge, SkillUiText.string("confirmation.default_badge"));
            authorityName = clean(authorityName, SkillUiText.string("confirmation.default_authority"));
            subjectLabel = clean(subjectLabel, SkillUiText.string("common.target_label"));
            subjectValue = clean(subjectValue, SkillUiText.string("common.unknown"));
            skills = skills == null
                    ? List.of()
                    : List.copyOf(skills);
            selectedSkills = Math.max(0, selectedSkills);
            affectedTargets = Math.max(0, affectedTargets);
            masteredSkills = Math.max(0, masteredSkills);
            unmasteredSkills = Math.max(0, unmasteredSkills);
            standardCost = UltimateConfirmationPolicy.sanitizeCost(
                    standardCost
            );
            surcharge = UltimateConfirmationPolicy.sanitizeCost(surcharge);
            totalCost = UltimateConfirmationPolicy.sanitizeCost(totalCost);
            availableMagicules = availableMagicules < 0.0D
                    ? -1.0D
                    : UltimateConfirmationPolicy.sanitizeCost(
                    availableMagicules
            );
            outcomeLine = clean(outcomeLine, "");
            warning = clean(warning, "");
            confirmLabel = clean(confirmLabel, SkillUiText.string("common.confirm"));
            theme = theme == null
                    ? SkillUiTheme.GOVERNANCE
                    : theme;
            confirmAction = confirmAction == null
                    ? () -> {
            }
                    : confirmAction;
        }

        public static ConfirmationRequest empty() {
            return new ConfirmationRequest(
                    SkillUiText.string("confirmation.default_title"),
                    SkillUiText.string("confirmation.default_badge"),
                    SkillUiText.string("confirmation.default_authority"),
                    SkillUiText.string("common.target_label"),
                    SkillUiText.string("common.unknown"),
                    List.of(),
                    0,
                    0,
                    0,
                    0,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    "",
                    SkillUiText.string("confirmation.no_valid_action"),
                    SkillUiText.string("common.confirm"),
                    SkillUiTheme.GOVERNANCE,
                    false,
                    false,
                    () -> {
                    }
            );
        }

        private static String clean(
                String value,
                String fallback
        ) {
            return value == null || value.isBlank()
                    ? fallback
                    : value.trim();
        }
    }

    private record Layout(
            SkillUiLayout.Rect panel,
            SkillUiLayout.Rect header,
            SkillUiLayout.Rect subject,
            SkillUiLayout.Rect summary,
            SkillUiLayout.Rect skills,
            SkillUiLayout.Rect warning,
            SkillUiLayout.Rect footer,
            boolean compact
    ) {
        private static Layout calculate(
                int screenWidth,
                int screenHeight
        ) {
            boolean compact = screenHeight < 300;
            int marginX = Math.max(
                    compact ? 6 : 10,
                    screenWidth / 11
            );
            int marginY = Math.max(
                    compact ? 5 : 8,
                    screenHeight / 12
            );
            int panelWidth = Math.max(
                    1,
                    screenWidth - marginX * 2
            );
            int panelHeight = Math.max(
                    1,
                    screenHeight - marginY * 2
            );
            int inset = compact ? 5 : 8;
            int headerHeight = compact ? 31 : 39;
            int subjectHeight = compact ? 19 : 23;
            int footerHeight = compact ? 18 : 22;
            int warningHeight = compact ? 29 : 39;
            int gap = compact ? 3 : 5;
            SkillUiLayout.Rect panel = new SkillUiLayout.Rect(
                    marginX,
                    marginY,
                    panelWidth,
                    panelHeight
            );
            SkillUiLayout.Rect header = new SkillUiLayout.Rect(
                    panel.left() + inset,
                    panel.top() + inset,
                    Math.max(1, panel.width() - inset * 2),
                    headerHeight
            );
            SkillUiLayout.Rect subject = new SkillUiLayout.Rect(
                    header.left(),
                    header.bottom() + gap,
                    header.width(),
                    subjectHeight
            );
            SkillUiLayout.Rect footer = new SkillUiLayout.Rect(
                    header.left(),
                    panel.bottom() - inset - footerHeight,
                    header.width(),
                    footerHeight
            );
            SkillUiLayout.Rect warning = new SkillUiLayout.Rect(
                    header.left(),
                    footer.top() - gap - warningHeight,
                    header.width(),
                    warningHeight
            );
            int bodyTop = subject.bottom() + gap;
            int bodyHeight = Math.max(
                    1,
                    warning.top() - gap - bodyTop
            );
            boolean twoColumns = panelWidth >= 520;
            SkillUiLayout.Rect summary;
            SkillUiLayout.Rect skills;

            if (twoColumns) {
                int summaryWidth = Math.max(
                        170,
                        header.width() * 36 / 100
                );
                summaryWidth = Math.min(
                        summaryWidth,
                        Math.max(1, header.width() - gap - 220)
                );
                summary = new SkillUiLayout.Rect(
                        header.left(),
                        bodyTop,
                        summaryWidth,
                        bodyHeight
                );
                skills = new SkillUiLayout.Rect(
                        summary.right() + gap,
                        bodyTop,
                        Math.max(1, header.right() - summary.right() - gap),
                        bodyHeight
                );
            } else {
                int summaryHeight = Math.max(
                        compact ? 50 : 72,
                        bodyHeight * 46 / 100
                );
                summaryHeight = Math.min(
                        summaryHeight,
                        Math.max(
                                1,
                                bodyHeight
                                        - gap
                                        - (compact ? 30 : 38)
                        )
                );
                summary = new SkillUiLayout.Rect(
                        header.left(),
                        bodyTop,
                        header.width(),
                        summaryHeight
                );
                skills = new SkillUiLayout.Rect(
                        header.left(),
                        summary.bottom() + gap,
                        header.width(),
                        Math.max(1, bodyHeight - summaryHeight - gap)
                );
            }

            return new Layout(
                    panel,
                    header,
                    subject,
                    summary,
                    skills,
                    warning,
                    footer,
                    compact
            );
        }
    }
}