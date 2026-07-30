package com.mooswqz.moostensuraaddon.client.screen.skillui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Crisp rendering helpers that deliberately avoid the blurred menu background.
 */
public final class SkillUiRenderHelper {

    private SkillUiRenderHelper() {
    }

    public static void fillBackground(
            GuiGraphics guiGraphics,
            int width,
            int height,
            SkillUiTheme theme
    ) {
        guiGraphics.fill(
                0,
                0,
                Math.max(1, width),
                Math.max(1, height),
                theme.backgroundColor()
        );
    }

    public static void drawBorderedPanel(
            GuiGraphics guiGraphics,
            SkillUiLayout.Rect bounds,
            int borderColor,
            int fillColor,
            int borderThickness
    ) {
        if (bounds == null
                || bounds.emptyArea()) {
            return;
        }

        int safeThickness = Math.max(
                1,
                borderThickness
        );

        guiGraphics.fill(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                bounds.bottom(),
                borderColor
        );

        if (bounds.width() <= safeThickness * 2
                || bounds.height() <= safeThickness * 2) {
            return;
        }

        guiGraphics.fill(
                bounds.left() + safeThickness,
                bounds.top() + safeThickness,
                bounds.right() - safeThickness,
                bounds.bottom() - safeThickness,
                fillColor
        );
    }

    public static void drawDivider(
            GuiGraphics guiGraphics,
            int left,
            int right,
            int y,
            int color
    ) {
        if (right <= left) {
            return;
        }

        guiGraphics.fill(
                left,
                y,
                right,
                y + 1,
                color
        );
    }

    public static void drawText(
            GuiGraphics guiGraphics,
            Font font,
            Component text,
            int x,
            int y,
            int color
    ) {
        guiGraphics.drawString(
                font,
                text == null
                        ? Component.empty()
                        : text,
                x,
                y,
                SkillUiTheme.opaque(color),
                false
        );
    }

    public static void drawCenteredText(
            GuiGraphics guiGraphics,
            Font font,
            Component text,
            int centerX,
            int y,
            int color
    ) {
        Component safeText = text == null
                ? Component.empty()
                : text;

        drawText(
                guiGraphics,
                font,
                safeText,
                centerX - font.width(safeText) / 2,
                y,
                color
        );
    }

    public static void drawClippedText(
            GuiGraphics guiGraphics,
            Font font,
            Component text,
            int x,
            int y,
            int maximumWidth,
            int color
    ) {
        if (maximumWidth <= 0) {
            return;
        }

        String fullText = text == null
                ? ""
                : text.getString();

        if (font.width(fullText) <= maximumWidth) {
            drawText(
                    guiGraphics,
                    font,
                    Component.literal(fullText),
                    x,
                    y,
                    color
            );
            return;
        }

        String ellipsis = "…";
        int textWidth = Math.max(
                0,
                maximumWidth - font.width(ellipsis)
        );
        String clipped = font.plainSubstrByWidth(
                fullText,
                textWidth
        );

        drawText(
                guiGraphics,
                font,
                Component.literal(
                        clipped + ellipsis
                ),
                x,
                y,
                color
        );
    }

    public static void drawCheckbox(
            GuiGraphics guiGraphics,
            int left,
            int top,
            boolean checked,
            boolean enabled,
            SkillUiTheme theme
    ) {
        int borderColor = enabled
                ? theme.accentColor()
                : theme.disabledTextColor();
        int fillColor = checked
                ? theme.selectedRowFillColor()
                : theme.rowFillColor();

        drawBorderedPanel(
                guiGraphics,
                new SkillUiLayout.Rect(
                        left,
                        top,
                        11,
                        11
                ),
                borderColor,
                fillColor,
                1
        );

        if (!checked) {
            return;
        }

        guiGraphics.fill(
                left + 3,
                top + 5,
                left + 5,
                top + 8,
                enabled
                        ? theme.accentColor()
                        : theme.disabledTextColor()
        );
        guiGraphics.fill(
                left + 5,
                top + 3,
                left + 8,
                top + 7,
                enabled
                        ? theme.accentColor()
                        : theme.disabledTextColor()
        );
    }


    public static void drawRadioIndicator(
            GuiGraphics guiGraphics,
            int left,
            int top,
            boolean selected,
            boolean enabled,
            SkillUiTheme theme
    ) {
        int outlineColor = enabled
                ? theme.accentColor()
                : theme.disabledTextColor();
        int innerColor = enabled
                ? theme.accentColor()
                : theme.disabledTextColor();

        guiGraphics.fill(
                left + 3,
                top,
                left + 8,
                top + 1,
                outlineColor
        );
        guiGraphics.fill(
                left + 1,
                top + 2,
                left + 10,
                top + 9,
                outlineColor
        );
        guiGraphics.fill(
                left,
                top + 3,
                left + 11,
                top + 8,
                outlineColor
        );

        guiGraphics.fill(
                left + 3,
                top + 1,
                left + 8,
                top + 2,
                theme.rowFillColor()
        );
        guiGraphics.fill(
                left + 2,
                top + 3,
                left + 9,
                top + 8,
                theme.rowFillColor()
        );
        guiGraphics.fill(
                left + 3,
                top + 8,
                left + 8,
                top + 10,
                theme.rowFillColor()
        );

        if (!selected) {
            return;
        }

        guiGraphics.fill(
                left + 4,
                top + 4,
                left + 7,
                top + 7,
                innerColor
        );
    }

    public static int drawModeBadge(
            GuiGraphics guiGraphics,
            Font font,
            Component label,
            int right,
            int top,
            SkillUiTheme theme
    ) {
        Component safeLabel = label == null
                ? Component.empty()
                : label;
        int badgeWidth = Math.max(
                38,
                font.width(safeLabel) + 10
        );
        SkillUiLayout.Rect badge = new SkillUiLayout.Rect(
                right - badgeWidth,
                top,
                badgeWidth,
                13
        );

        drawBorderedPanel(
                guiGraphics,
                badge,
                theme.accentColor(),
                theme.selectedRowFillColor(),
                1
        );
        drawCenteredText(
                guiGraphics,
                font,
                safeLabel,
                badge.centerX(),
                badge.top() + 3,
                theme.accentColor()
        );

        return badge.left();
    }

    public static void drawScrollbar(
            GuiGraphics guiGraphics,
            SkillUiLayout.Rect bounds,
            double scrollOffset,
            double maximumScroll,
            int contentHeight,
            SkillUiTheme theme
    ) {
        if (bounds == null
                || bounds.emptyArea()
                || maximumScroll <= 0.0D
                || contentHeight <= 0) {
            return;
        }

        int trackLeft = bounds.right() - 5;
        int trackTop = bounds.top() + 2;
        int trackBottom = bounds.bottom() - 2;
        int trackHeight = Math.max(
                1,
                trackBottom - trackTop
        );

        guiGraphics.fill(
                trackLeft,
                trackTop,
                trackLeft + 3,
                trackBottom,
                theme.scrollbarTrackColor()
        );

        int thumbHeight = Math.max(
                12,
                (int) Math.round(
                        trackHeight
                                * Math.min(
                                1.0D,
                                (double) bounds.height()
                                        / contentHeight
                        )
                )
        );
        thumbHeight = Math.min(
                trackHeight,
                thumbHeight
        );

        double ratio = maximumScroll <= 0.0D
                ? 0.0D
                : scrollOffset / maximumScroll;
        int travel = Math.max(
                0,
                trackHeight - thumbHeight
        );
        int thumbTop = trackTop
                + (int) Math.round(
                travel * ratio
        );

        guiGraphics.fill(
                trackLeft,
                thumbTop,
                trackLeft + 3,
                thumbTop + thumbHeight,
                theme.scrollbarThumbColor()
        );
    }
}