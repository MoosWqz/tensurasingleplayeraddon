package com.mooswqz.moostensuraaddon.client.screen;

import com.mooswqz.moostensuraaddon.network.ExecuteUltimateSubordinateSkillPayload;
import com.mooswqz.moostensuraaddon.network.OpenUltimateSubordinateSkillScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class UltimateSubordinateSkillSelectionScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int TOP_Y = 78;
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 20;

    private final OpenUltimateSubordinateSkillScreenPayload payload;
    private final Set<String> selectedSkillIds = new LinkedHashSet<>();

    private int page = 0;
    private int maxPage = 0;

    public UltimateSubordinateSkillSelectionScreen(OpenUltimateSubordinateSkillScreenPayload payload) {
        super(Component.literal(payload.seize()
                ? "Seize Skills from " + payload.targetName()
                : "Borrow Skills from " + payload.targetName()));
        this.payload = payload;
    }

    public static void open(OpenUltimateSubordinateSkillScreenPayload payload) {
        Minecraft.getInstance().setScreen(new UltimateSubordinateSkillSelectionScreen(payload));
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        updateMaxPage();

        int centerX = this.width / 2;

        if (payload.skills() == null || payload.skills().isEmpty()) {
            Button noSkillsButton = Button.builder(
                    Component.literal("No selectable skills found.")
                            .withStyle(ChatFormatting.GRAY),
                    button -> {
                    }
            ).pos(centerX - 130, TOP_Y + 22).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();

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
        int endIndex = Math.min(startIndex + rowsPerPage, payload.skills().size());

        int visibleRow = 0;

        for (int index = startIndex; index < endIndex; index++) {
            OpenUltimateSubordinateSkillScreenPayload.SkillEntry entry = payload.skills().get(index);
            int y = TOP_Y + visibleRow * ROW_HEIGHT;

            addRenderableWidget(Button.builder(
                    getSkillButtonText(entry),
                    button -> toggleSkill(entry.skillId())
            ).pos(centerX - 130, y).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

            visibleRow++;
        }

        addRenderableWidget(Button.builder(
                Component.literal("Previous"),
                button -> {
                    if (page > 0) {
                        page--;
                        rebuildButtons();
                    }
                }
        ).pos(centerX - 130, this.height - 32).size(80, BUTTON_HEIGHT).build()).active = page > 0;

        Button pageButton = Button.builder(
                Component.literal((page + 1) + " / " + (maxPage + 1))
                        .withStyle(ChatFormatting.GRAY),
                button -> {
                }
        ).pos(centerX - 42, this.height - 32).size(84, BUTTON_HEIGHT).build();

        pageButton.active = false;
        addRenderableWidget(pageButton);

        addRenderableWidget(Button.builder(
                Component.literal("Next"),
                button -> {
                    if (page < maxPage) {
                        page++;
                        rebuildButtons();
                    }
                }
        ).pos(centerX + 50, this.height - 32).size(80, BUTTON_HEIGHT).build()).active = page < maxPage;

        addRenderableWidget(Button.builder(
                Component.literal(payload.seize() ? "Confirm Seize" : "Confirm Borrow"),
                button -> confirmSelection()
        ).pos(centerX - 130, this.height - 56).size(125, BUTTON_HEIGHT).build()).active = !selectedSkillIds.isEmpty();

        addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                button -> onClose()
        ).pos(centerX + 5, this.height - 56).size(125, BUTTON_HEIGHT).build());
    }

    private void updateMaxPage() {
        int rowsPerPage = getRowsPerPage();
        int skillCount = payload.skills() == null ? 0 : payload.skills().size();

        maxPage = skillCount <= 0 ? 0 : Math.max(0, (skillCount - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, maxPage));
    }

    private int getRowsPerPage() {
        int bottomReservedY = this.height - 88;
        int availableHeight = Math.max(ROW_HEIGHT * 3, bottomReservedY - TOP_Y);
        int calculatedRows = availableHeight / ROW_HEIGHT;

        return Math.max(3, calculatedRows);
    }

    private Component getSkillButtonText(OpenUltimateSubordinateSkillScreenPayload.SkillEntry entry) {
        boolean selected = selectedSkillIds.contains(entry.skillId());

        Component checkbox = Component.literal(selected ? "[x] " : "[ ] ")
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GRAY);

        Component name = Component.literal(entry.displayName())
                .withStyle(selected ? ChatFormatting.AQUA : ChatFormatting.WHITE);

        if (!payload.seize()) {
            Component chance = Component.literal(" (" + formatPercent(entry.borrowPermanentChance()) + ")")
                    .withStyle(ChatFormatting.LIGHT_PURPLE);

            return Component.empty()
                    .append(checkbox)
                    .append(name)
                    .append(chance);
        }

        return Component.empty()
                .append(checkbox)
                .append(name);
    }

    private void toggleSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return;
        }

        if (selectedSkillIds.contains(skillId)) {
            selectedSkillIds.remove(skillId);
        } else {
            selectedSkillIds.add(skillId);
        }

        rebuildButtons();
    }

    private void confirmSelection() {
        if (selectedSkillIds.isEmpty()) {
            return;
        }

        PacketDistributor.sendToServer(new ExecuteUltimateSubordinateSkillPayload(
                payload.seize(),
                payload.targetUuid(),
                new ArrayList<>(selectedSkillIds)
        ));

        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;

        guiGraphics.drawCenteredString(
                this.font,
                this.title,
                centerX,
                16,
                0xFFFFFF
        );

        guiGraphics.drawCenteredString(
                this.font,
                getInfoLine(),
                centerX,
                34,
                0xA0A0A0
        );

        guiGraphics.drawCenteredString(
                this.font,
                getRiskOrChanceLine(),
                centerX,
                48,
                payload.seize() ? 0xFF5555 : 0xFFAAFF
        );

        guiGraphics.drawCenteredString(
                this.font,
                getHintLine(),
                centerX,
                62,
                0x707070
        );
    }

    private Component getInfoLine() {
        int selectedCount = selectedSkillIds.size();
        double totalCost = selectedCount * payload.costPerSkill();

        return Component.literal("Selected: " + selectedCount
                + " | Cost: " + formatNumber(totalCost) + " magicules");
    }

    private Component getRiskOrChanceLine() {
        if (payload.seize()) {
            double deathChance = getSelectedSeizeDeathChance();

            return Component.literal("Soul strain: " + formatPercent(deathChance));
        }

        double highestChance = getHighestSelectedBorrowChance();

        if (selectedSkillIds.isEmpty()) {
            return Component.literal("Permanence chance shown per skill.");
        }

        return Component.literal("Highest permanence chance: " + formatPercent(highestChance));
    }

    private Component getHintLine() {
        if (payload.seize()) {
            return Component.literal("Forced extraction becomes riskier with each selected skill.");
        }

        return Component.literal("Repeatedly borrowing the same skill increases its permanence chance.");
    }

    private double getSelectedSeizeDeathChance() {
        if (selectedSkillIds.isEmpty()) {
            return 0.0D;
        }

        double chance = selectedSkillIds.size() * payload.seizeDeathChancePerSkill();
        return Math.max(0.0D, Math.min(payload.seizeDeathChanceMax(), chance));
    }

    private double getHighestSelectedBorrowChance() {
        double highestChance = 0.0D;

        if (payload.skills() == null || payload.skills().isEmpty()) {
            return highestChance;
        }

        for (OpenUltimateSubordinateSkillScreenPayload.SkillEntry entry : payload.skills()) {
            if (entry == null || entry.skillId() == null) {
                continue;
            }

            if (!selectedSkillIds.contains(entry.skillId())) {
                continue;
            }

            highestChance = Math.max(highestChance, entry.borrowPermanentChance());
        }

        return highestChance;
    }

    private String formatNumber(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    private String formatPercent(double value) {
        return String.format(Locale.US, "%.1f%%", value * 100.0D);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}