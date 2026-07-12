package com.mooswqz.moostensuraaddon.client.screen;

import com.mooswqz.moostensuraaddon.network.OpenSubordinateOverviewScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SubordinateOverviewScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int TOP_Y = 92;
    private static final int BUTTON_WIDTH = 280;
    private static final int BUTTON_HEIGHT = 20;

    private final OpenSubordinateOverviewScreenPayload payload;

    private int targetIndex = 0;
    private int skillPage = 0;
    private int maxSkillPage = 0;

    public SubordinateOverviewScreen(OpenSubordinateOverviewScreenPayload payload) {
        super(Component.literal("Subordinate Overview"));
        this.payload = payload;
    }

    public static void open(OpenSubordinateOverviewScreenPayload payload) {
        Minecraft.getInstance().setScreen(new SubordinateOverviewScreen(payload));
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        clampTargetIndex();
        updateMaxSkillPage();

        int centerX = this.width / 2;

        if (getCurrentTarget() == null) {
            Button emptyButton = Button.builder(
                    Component.literal("No subordinate data found.").withStyle(ChatFormatting.GRAY),
                    button -> {
                    }
            ).pos(centerX - 140, TOP_Y + 22).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();

            emptyButton.active = false;
            addRenderableWidget(emptyButton);

            addRenderableWidget(Button.builder(
                    Component.literal("Close"),
                    button -> onClose()
            ).pos(centerX - 60, this.height - 32).size(120, BUTTON_HEIGHT).build());

            return;
        }

        List<DisplayRow> rows = getDisplayRowsForCurrentTarget();

        if (rows.isEmpty()) {
            Button noSkillsButton = Button.builder(
                    Component.literal("This subordinate has no visible skills.")
                            .withStyle(ChatFormatting.GRAY),
                    button -> {
                    }
            ).pos(centerX - 140, TOP_Y + 22).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();

            noSkillsButton.active = false;
            addRenderableWidget(noSkillsButton);
        } else {
            int rowsPerPage = getRowsPerPage();
            int startIndex = skillPage * rowsPerPage;
            int endIndex = Math.min(startIndex + rowsPerPage, rows.size());

            int visibleRow = 0;

            for (int index = startIndex; index < endIndex; index++) {
                DisplayRow row = rows.get(index);

                if (!row.isSkill()) {
                    visibleRow++;
                    continue;
                }

                OpenSubordinateOverviewScreenPayload.SkillEntry entry = row.skillEntry();
                int y = TOP_Y + visibleRow * ROW_HEIGHT;

                Button skillButton = Button.builder(
                        getSkillButtonText(entry),
                        button -> {
                        }
                ).pos(centerX - 140, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();

                skillButton.active = false;
                addRenderableWidget(skillButton);

                visibleRow++;
            }
        }

        addRenderableWidget(Button.builder(
                Component.literal("Prev Target"),
                button -> {
                    if (targetIndex > 0) {
                        targetIndex--;
                        skillPage = 0;
                        rebuildButtons();
                    }
                }
        ).pos(centerX - 145, this.height - 80).size(95, BUTTON_HEIGHT).build()).active = targetIndex > 0;

        Button targetPageButton = Button.builder(
                Component.literal(getTargetPageText()).withStyle(ChatFormatting.GRAY),
                button -> {
                }
        ).pos(centerX - 45, this.height - 80).size(90, BUTTON_HEIGHT).build();

        targetPageButton.active = false;
        addRenderableWidget(targetPageButton);

        addRenderableWidget(Button.builder(
                Component.literal("Next Target"),
                button -> {
                    if (targetIndex < getTargetCount() - 1) {
                        targetIndex++;
                        skillPage = 0;
                        rebuildButtons();
                    }
                }
        ).pos(centerX + 50, this.height - 80).size(95, BUTTON_HEIGHT).build()).active = targetIndex < getTargetCount() - 1;

        addRenderableWidget(Button.builder(
                Component.literal("Prev Skills"),
                button -> {
                    if (skillPage > 0) {
                        skillPage--;
                        rebuildButtons();
                    }
                }
        ).pos(centerX - 145, this.height - 56).size(95, BUTTON_HEIGHT).build()).active = skillPage > 0;

        Button skillPageButton = Button.builder(
                Component.literal((skillPage + 1) + " / " + (maxSkillPage + 1))
                        .withStyle(ChatFormatting.GRAY),
                button -> {
                }
        ).pos(centerX - 45, this.height - 56).size(90, BUTTON_HEIGHT).build();

        skillPageButton.active = false;
        addRenderableWidget(skillPageButton);

        addRenderableWidget(Button.builder(
                Component.literal("Next Skills"),
                button -> {
                    if (skillPage < maxSkillPage) {
                        skillPage++;
                        rebuildButtons();
                    }
                }
        ).pos(centerX + 50, this.height - 56).size(95, BUTTON_HEIGHT).build()).active = skillPage < maxSkillPage;

        addRenderableWidget(Button.builder(
                Component.literal("Close"),
                button -> onClose()
        ).pos(centerX - 60, this.height - 32).size(120, BUTTON_HEIGHT).build());
    }

    private Component getSkillButtonText(OpenSubordinateOverviewScreenPayload.SkillEntry entry) {
        if (entry == null) {
            return Component.literal("Unknown Skill").withStyle(ChatFormatting.GRAY);
        }

        Component crown = Component.literal(entry.mastered() ? "♛ " : "")
                .withStyle(ChatFormatting.GOLD);

        Component name = Component.literal(entry.displayName())
                .withStyle(entry.mastered() ? ChatFormatting.AQUA : ChatFormatting.WHITE);

        Component granted = entry.grantedByViewer()
                ? Component.literal("  [Granted]").withStyle(ChatFormatting.GREEN)
                : Component.empty();

        return Component.empty()
                .append(crown)
                .append(name)
                .append(granted);
    }

    private void updateMaxSkillPage() {
        List<DisplayRow> rows = getDisplayRowsForCurrentTarget();
        int rowsPerPage = getRowsPerPage();

        maxSkillPage = rows.isEmpty() ? 0 : Math.max(0, (rows.size() - 1) / rowsPerPage);
        skillPage = Math.max(0, Math.min(skillPage, maxSkillPage));
    }

    private int getRowsPerPage() {
        int bottomReservedY = this.height - 100;
        int availableHeight = Math.max(ROW_HEIGHT * 3, bottomReservedY - TOP_Y);
        int calculatedRows = availableHeight / ROW_HEIGHT;

        return Math.max(3, calculatedRows);
    }

    private List<DisplayRow> getDisplayRowsForCurrentTarget() {
        OpenSubordinateOverviewScreenPayload.TargetEntry target = getCurrentTarget();

        if (target == null || target.skills() == null || target.skills().isEmpty()) {
            return List.of();
        }

        List<OpenSubordinateOverviewScreenPayload.SkillEntry> sortedSkills = new ArrayList<>(target.skills());

        sortedSkills.sort(Comparator
                .comparingInt(OpenSubordinateOverviewScreenPayload.SkillEntry::categoryOrder)
                .thenComparing(OpenSubordinateOverviewScreenPayload.SkillEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OpenSubordinateOverviewScreenPayload.SkillEntry::skillId));

        List<DisplayRow> rows = new ArrayList<>();
        String currentCategory = null;

        for (OpenSubordinateOverviewScreenPayload.SkillEntry skill : sortedSkills) {
            if (skill == null) {
                continue;
            }

            String categoryName = skill.categoryName() == null || skill.categoryName().isBlank()
                    ? "Other"
                    : skill.categoryName();

            if (!categoryName.equals(currentCategory)) {
                rows.add(DisplayRow.header(categoryName));
                currentCategory = categoryName;
            }

            rows.add(DisplayRow.skill(skill));
        }

        return rows;
    }

    private OpenSubordinateOverviewScreenPayload.TargetEntry getCurrentTarget() {
        if (payload == null || payload.targets() == null || payload.targets().isEmpty()) {
            return null;
        }

        clampTargetIndex();

        return payload.targets().get(targetIndex);
    }

    private void clampTargetIndex() {
        int count = getTargetCount();

        if (count <= 0) {
            targetIndex = 0;
            return;
        }

        targetIndex = Math.max(0, Math.min(targetIndex, count - 1));
    }

    private int getTargetCount() {
        if (payload == null || payload.targets() == null) {
            return 0;
        }

        return payload.targets().size();
    }

    private String getTargetPageText() {
        int targetCount = getTargetCount();

        if (targetCount <= 0) {
            return "0 / 0";
        }

        return (targetIndex + 1) + " / " + targetCount;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        OpenSubordinateOverviewScreenPayload.TargetEntry target = getCurrentTarget();

        int centerX = this.width / 2;

        guiGraphics.drawCenteredString(
                this.font,
                this.title,
                centerX,
                14,
                0xFFFFFF
        );

        if (target == null) {
            return;
        }

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(target.targetName())
                        .withStyle(ChatFormatting.GOLD),
                centerX,
                30,
                0xFFFFFF
        );

        guiGraphics.drawCenteredString(
                this.font,
                getStatsLine(target),
                centerX,
                44,
                0xA0A0A0
        );

        guiGraphics.drawCenteredString(
                this.font,
                getSkillSummaryLine(target),
                centerX,
                58,
                0x808080
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal("♛ = mastered | [Granted] = granted by you")
                        .withStyle(ChatFormatting.DARK_GRAY),
                centerX,
                72,
                0x707070
        );

        renderCategoryHeaders(guiGraphics);
    }

    private void renderCategoryHeaders(GuiGraphics guiGraphics) {
        List<DisplayRow> rows = getDisplayRowsForCurrentTarget();

        if (rows.isEmpty()) {
            return;
        }

        int centerX = this.width / 2;
        int rowsPerPage = getRowsPerPage();

        int startIndex = skillPage * rowsPerPage;
        int endIndex = Math.min(startIndex + rowsPerPage, rows.size());

        int visibleRow = 0;

        for (int index = startIndex; index < endIndex; index++) {
            DisplayRow row = rows.get(index);
            int y = TOP_Y + visibleRow * ROW_HEIGHT + 6;

            if (row.isHeader()) {
                guiGraphics.drawCenteredString(
                        this.font,
                        Component.literal(row.categoryName())
                                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                        centerX,
                        y,
                        0xFFFFFF
                );
            }

            visibleRow++;
        }
    }

    private Component getStatsLine(OpenSubordinateOverviewScreenPayload.TargetEntry target) {
        return Component.literal(
                "HP: " + formatNumber(target.health()) + " / " + formatNumber(target.maxHealth())
                        + " | Magicules: " + formatNumber(target.magicules())
                        + " | EP: " + formatNumber(target.ep())
        );
    }

    private Component getSkillSummaryLine(OpenSubordinateOverviewScreenPayload.TargetEntry target) {
        int skillCount = target.skills() == null ? 0 : target.skills().size();
        int mastered = 0;
        int granted = 0;

        if (target.skills() != null) {
            for (OpenSubordinateOverviewScreenPayload.SkillEntry skill : target.skills()) {
                if (skill == null) {
                    continue;
                }

                if (skill.mastered()) {
                    mastered++;
                }

                if (skill.grantedByViewer()) {
                    granted++;
                }
            }
        }

        return Component.literal("Skills: " + skillCount
                + " | Mastered: " + mastered
                + " | Granted by you: " + granted);
    }

    private String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record DisplayRow(
            String categoryName,
            OpenSubordinateOverviewScreenPayload.SkillEntry skillEntry
    ) {
        private static DisplayRow header(String categoryName) {
            return new DisplayRow(categoryName, null);
        }

        private static DisplayRow skill(OpenSubordinateOverviewScreenPayload.SkillEntry skillEntry) {
            return new DisplayRow(null, skillEntry);
        }

        private boolean isHeader() {
            return categoryName != null;
        }

        private boolean isSkill() {
            return skillEntry != null;
        }
    }
}