package com.mooswqz.moostensuraaddon.client.screen.skillui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared read-only details panel for focused or hovered skills.
 */
public final class SkillDetailsPanel {

    private static final int PADDING = 7;
    private static final int LINE_HEIGHT = 11;

    private SkillDetailsPanel() {
    }

    public static void render(
            GuiGraphics guiGraphics,
            Font font,
            SkillUiLayout.Rect bounds,
            SkillUiTheme theme,
            SkillUiEntry entry,
            SkillUiSelectionModel selectionModel
    ) {
        if (bounds == null
                || bounds.emptyArea()) {
            return;
        }

        int borderColor = entry == null
                ? theme.panelBorderColor()
                : SkillUiTheme.opaque(
                entry.accentColor()
        );

        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                bounds,
                borderColor,
                theme.panelFillColor(),
                1
        );

        int left = bounds.left() + PADDING;
        int top = bounds.top() + PADDING;
        int maximumWidth = Math.max(
                1,
                bounds.width() - PADDING * 2
        );

        if (entry == null) {
            SkillUiRenderHelper.drawCenteredText(
                    guiGraphics,
                    font,
                    Component.literal(
                            "Select a skill to view details"
                    ),
                    bounds.centerX(),
                    bounds.centerY() - 4,
                    theme.mutedTextColor()
            );
            return;
        }

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                entry.displayName(),
                left,
                top,
                maximumWidth,
                entry.accentColor()
        );

        int currentY = top + 14;

        SkillUiRenderHelper.drawText(
                guiGraphics,
                font,
                entry.category().displayName(),
                left,
                currentY,
                theme.secondaryTextColor()
        );
        currentY += LINE_HEIGHT;

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(entry.skillId()),
                left,
                currentY,
                maximumWidth,
                theme.mutedTextColor()
        );
        currentY += LINE_HEIGHT + 2;

        if (entry.hasSourceName()) {
            SkillUiRenderHelper.drawClippedText(
                    guiGraphics,
                    font,
                    Component.literal("Source: ")
                            .append(entry.sourceName()),
                    left,
                    currentY,
                    maximumWidth,
                    theme.secondaryTextColor()
            );
            currentY += LINE_HEIGHT;
        }

        String masteryText = entry.mastered()
                ? "Mastered"
                : "Not mastered";
        SkillUiRenderHelper.drawText(
                guiGraphics,
                font,
                Component.literal(masteryText),
                left,
                currentY,
                entry.mastered()
                        ? theme.successColor()
                        : theme.mutedTextColor()
        );
        currentY += LINE_HEIGHT;

        if (selectionModel != null
                && selectionModel.mode()
                != SkillUiSelectionMode.READ_ONLY) {
            boolean selected = selectionModel.isSelected(
                    entry.skillId()
            );

            SkillUiRenderHelper.drawText(
                    guiGraphics,
                    font,
                    Component.literal(
                            selected
                                    ? "Selected"
                                    : "Not selected"
                    ),
                    left,
                    currentY,
                    selected
                            ? theme.accentColor()
                            : theme.mutedTextColor()
            );
            currentY += LINE_HEIGHT;
        }

        if (!entry.selectable()
                && entry.hasDisabledReason()) {
            currentY += 2;
            currentY = drawWrapped(
                    guiGraphics,
                    font,
                    entry.disabledReason().getString(),
                    left,
                    currentY,
                    maximumWidth,
                    bounds.bottom() - PADDING,
                    theme.warningColor()
            );
        }

        for (Component detailLine : entry.detailLines()) {
            if (currentY + LINE_HEIGHT
                    > bounds.bottom() - PADDING) {
                break;
            }

            currentY += 2;
            currentY = drawWrapped(
                    guiGraphics,
                    font,
                    detailLine.getString(),
                    left,
                    currentY,
                    maximumWidth,
                    bounds.bottom() - PADDING,
                    theme.secondaryTextColor()
            );
        }
    }

    private static int drawWrapped(
            GuiGraphics guiGraphics,
            Font font,
            String text,
            int left,
            int top,
            int maximumWidth,
            int bottom,
            int color
    ) {
        int currentY = top;

        for (String line : wrap(
                font,
                text,
                maximumWidth
        )) {
            if (currentY + LINE_HEIGHT > bottom) {
                break;
            }

            SkillUiRenderHelper.drawText(
                    guiGraphics,
                    font,
                    Component.literal(line),
                    left,
                    currentY,
                    color
            );
            currentY += LINE_HEIGHT;
        }

        return currentY;
    }

    private static List<String> wrap(
            Font font,
            String text,
            int maximumWidth
    ) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        String remaining = text.trim();

        while (!remaining.isBlank()) {
            String fitting = font.plainSubstrByWidth(
                    remaining,
                    Math.max(1, maximumWidth)
            );

            if (fitting.isBlank()) {
                break;
            }

            if (fitting.length() < remaining.length()) {
                int lastSpace = fitting.lastIndexOf(' ');

                if (lastSpace > 0) {
                    fitting = fitting.substring(
                            0,
                            lastSpace
                    );
                }
            }

            result.add(fitting.trim());
            remaining = remaining.substring(
                    Math.min(
                            remaining.length(),
                            fitting.length()
                    )
            ).trim();
        }

        return List.copyOf(result);
    }
}