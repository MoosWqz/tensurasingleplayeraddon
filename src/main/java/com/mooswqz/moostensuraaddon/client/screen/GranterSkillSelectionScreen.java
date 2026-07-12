package com.mooswqz.moostensuraaddon.client.screen;

import com.mooswqz.moostensuraaddon.network.SelectSkillPayload;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import com.mooswqz.moostensuraaddon.util.GranterActions;
import com.mooswqz.moostensuraaddon.util.SkillCategoryHelper;
import com.mooswqz.moostensuraaddon.util.SkillCategoryHelper.SkillCategory;
import com.mooswqz.moostensuraaddon.util.SkillCategoryHelper.SkillDisplayEntry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class GranterSkillSelectionScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int TOP_Y = 54;
    private static final int BUTTON_WIDTH = 240;
    private static final int BUTTON_HEIGHT = 20;

    private final List<DisplayRow> rows = new ArrayList<>();

    private int page = 0;
    private int maxPage = 0;

    public GranterSkillSelectionScreen() {
        super(Component.translatable("moostensuraaddon.screen.granter.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GranterSkillSelectionScreen());
    }

    @Override
    protected void init() {
        rebuildRows();
        rebuildSkillButtons();
    }

    private void rebuildRows() {
        rows.clear();

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            maxPage = 0;
            page = 0;
            return;
        }

        boolean hasEvolvedGranterSkill = SkillAPI.getSkillsFrom(minecraft.player)
                .getSkill(SkillRegistry.BENEVOLENT_EMPOWERMENT.get().getRegistryName())
                .isPresent()
                || SkillAPI.getSkillsFrom(minecraft.player)
                .getSkill(SkillRegistry.ABSOLUTE_GOVERNANCE.get().getRegistryName())
                .isPresent();

        List<SkillDisplayEntry> skills = new ArrayList<>();

        for (ManasSkillInstance instance : SkillAPI.getSkillsFrom(minecraft.player).getLearnedSkills()) {
            if (instance == null) {
                continue;
            }

            ResourceLocation skillId = instance.getSkillId();

            if (skillId == null) {
                continue;
            }

            Component displayName = instance.getDisplayName();

            if (!GranterActions.isGrantableSkill(skillId)) {
                continue;
            }

            if (SkillCategoryHelper.isIntrinsic(instance, skillId, displayName)) {
                continue;
            }

            boolean mastered = instance.isMastered(minecraft.player);

            if (!hasEvolvedGranterSkill && !mastered) {
                continue;
            }

            SkillCategory category = SkillCategoryHelper.getCategory(instance, skillId, displayName);

            skills.add(new SkillDisplayEntry(
                    skillId,
                    displayName,
                    category,
                    mastered
            ));
        }

        skills.sort(SkillCategoryHelper.skillDisplaySorter());

        SkillCategory currentCategory = null;

        for (SkillDisplayEntry entry : skills) {
            if (entry.category() != currentCategory) {
                rows.add(DisplayRow.header(entry.category()));
                currentCategory = entry.category();
            }

            rows.add(DisplayRow.skill(entry));
        }

        updateMaxPage();
    }

    private void updateMaxPage() {
        int rowsPerPage = getRowsPerPage();

        maxPage = rows.isEmpty() ? 0 : Math.max(0, (rows.size() - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, maxPage));
    }

    private int getRowsPerPage() {
        /*
         * Minecraft already applies the user's GUI scale to this.width/this.height.
         * So instead of using a fixed 8 rows, we calculate how many rows actually fit
         * between the title area and the bottom control buttons.
         */
        int bottomReservedY = this.height - 82;
        int availableHeight = Math.max(ROW_HEIGHT * 3, bottomReservedY - TOP_Y);
        int calculatedRows = availableHeight / ROW_HEIGHT;

        return Math.max(3, calculatedRows);
    }

    public void rebuildSkillButtons() {
        clearWidgets();
        updateMaxPage();

        int centerX = this.width / 2;

        if (rows.isEmpty()) {
            Button noSkillsButton = Button.builder(
                    Component.translatable("moostensuraaddon.screen.granter.no_skills")
                            .withStyle(ChatFormatting.GRAY),
                    button -> {
                    }
            ).pos(centerX - 120, TOP_Y + 22).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();

            noSkillsButton.active = false;
            addRenderableWidget(noSkillsButton);

            addRenderableWidget(Button.builder(
                    Component.literal("Cancel"),
                    button -> onClose()
            ).pos(centerX - 60, this.height - 32).size(120, BUTTON_HEIGHT).build());

            return;
        }

        int rowsPerPage = getRowsPerPage();
        int startIndex = page * rowsPerPage;
        int endIndex = Math.min(startIndex + rowsPerPage, rows.size());

        int visibleRow = 0;

        for (int index = startIndex; index < endIndex; index++) {
            DisplayRow row = rows.get(index);
            int y = TOP_Y + visibleRow * ROW_HEIGHT;

            if (row.isSkill()) {
                SkillDisplayEntry entry = row.skillEntry();

                addRenderableWidget(Button.builder(
                        entry.getFormattedDisplayName(),
                        button -> selectSkill(entry)
                ).pos(centerX - 120, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
            }

            visibleRow++;
        }

        addRenderableWidget(Button.builder(
                Component.literal("Previous"),
                button -> {
                    if (page > 0) {
                        page--;
                        rebuildSkillButtons();
                    }
                }
        ).pos(centerX - 120, this.height - 32).size(75, BUTTON_HEIGHT).build()).active = page > 0;

        Button pageButton = Button.builder(
                Component.literal((page + 1) + " / " + (maxPage + 1))
                        .withStyle(ChatFormatting.GRAY),
                button -> {
                }
        ).pos(centerX - 37, this.height - 32).size(74, BUTTON_HEIGHT).build();

        pageButton.active = false;
        addRenderableWidget(pageButton);

        addRenderableWidget(Button.builder(
                Component.literal("Next"),
                button -> {
                    if (page < maxPage) {
                        page++;
                        rebuildSkillButtons();
                    }
                }
        ).pos(centerX + 45, this.height - 32).size(75, BUTTON_HEIGHT).build()).active = page < maxPage;

        addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                button -> onClose()
        ).pos(centerX - 60, this.height - 56).size(120, BUTTON_HEIGHT).build());
    }

    private void selectSkill(SkillDisplayEntry entry) {
        if (entry == null || entry.skillId() == null) {
            return;
        }

        PacketDistributor.sendToServer(new SelectSkillPayload(entry.skillId().toString()));
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                18,
                0xFFFFFF
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("moostensuraaddon.screen.granter.mastery_hint")
                        .withStyle(ChatFormatting.GRAY),
                this.width / 2,
                32,
                0xA0A0A0
        );

        renderCategoryHeaders(guiGraphics);
    }

    private void renderCategoryHeaders(GuiGraphics guiGraphics) {
        if (rows.isEmpty()) {
            return;
        }

        int centerX = this.width / 2;
        int rowsPerPage = getRowsPerPage();

        int startIndex = page * rowsPerPage;
        int endIndex = Math.min(startIndex + rowsPerPage, rows.size());

        int visibleRow = 0;

        for (int index = startIndex; index < endIndex; index++) {
            DisplayRow row = rows.get(index);
            int y = TOP_Y + visibleRow * ROW_HEIGHT + 6;

            if (row.isHeader()) {
                guiGraphics.drawCenteredString(
                        this.font,
                        SkillCategoryHelper.getCategoryHeader(row.category()),
                        centerX,
                        y,
                        0xFFFFFF
                );
            }

            visibleRow++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record DisplayRow(
            SkillCategory category,
            SkillDisplayEntry skillEntry
    ) {
        private static DisplayRow header(SkillCategory category) {
            return new DisplayRow(category, null);
        }

        private static DisplayRow skill(SkillDisplayEntry skillEntry) {
            return new DisplayRow(null, skillEntry);
        }

        private boolean isHeader() {
            return category != null;
        }

        private boolean isSkill() {
            return skillEntry != null;
        }
    }
}