package com.mooswqz.moostensuraaddon.client.screen.skillui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Small shared button matching the crisp bordered skill-screen style.
 */
public final class SkillUiButton extends AbstractButton {

    private final SkillUiTheme theme;
    private final Runnable action;
    private final Tone tone;

    private boolean highlighted;

    public SkillUiButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            SkillUiTheme theme,
            Tone tone,
            Runnable action
    ) {
        super(
                x,
                y,
                Math.max(1, width),
                Math.max(1, height),
                message == null
                        ? Component.empty()
                        : message
        );

        this.theme = theme == null
                ? SkillUiTheme.GRANTER
                : theme;
        this.tone = tone == null
                ? Tone.NORMAL
                : tone;
        this.action = action == null
                ? () -> {
        }
                : action;
    }

    public void setHighlighted(
            boolean highlighted
    ) {
        this.highlighted = highlighted;
    }

    public boolean highlighted() {
        return highlighted;
    }

    @Override
    public void onPress() {
        if (active) {
            action.run();
        }
    }

    @Override
    protected void renderWidget(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        boolean hoveredOrFocused = isHoveredOrFocused();

        int borderColor;
        int fillColor;
        int textColor;

        if (!active) {
            borderColor = theme.panelBorderColor();
            fillColor = theme.rowFillColor();
            textColor = theme.disabledTextColor();
        } else {
            borderColor = switch (tone) {
                case PRIMARY -> theme.accentColor();
                case DANGER -> theme.dangerColor();
                case NORMAL -> highlighted
                        ? theme.accentColor()
                        : hoveredOrFocused
                          ? theme.secondaryAccentColor()
                          : theme.panelBorderColor();
            };

            fillColor = highlighted
                    || tone == Tone.PRIMARY
                    ? theme.selectedRowFillColor()
                    : hoveredOrFocused
                      ? theme.hoveredRowFillColor()
                      : theme.rowFillColor();

            textColor = switch (tone) {
                case PRIMARY -> theme.primaryTextColor();
                case DANGER -> theme.dangerColor();
                case NORMAL -> highlighted
                        ? theme.accentColor()
                        : theme.primaryTextColor();
            };
        }

        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                new SkillUiLayout.Rect(
                        getX(),
                        getY(),
                        getWidth(),
                        getHeight()
                ),
                borderColor,
                fillColor,
                hoveredOrFocused && active
                        ? 2
                        : 1
        );

        SkillUiRenderHelper.drawCenteredText(
                guiGraphics,
                Minecraft.getInstance().font,
                getMessage(),
                getX() + getWidth() / 2,
                getY() + getHeight() / 2 - 4,
                textColor
        );
    }

    @Override
    protected void updateWidgetNarration(
            NarrationElementOutput narrationElementOutput
    ) {
        defaultButtonNarrationText(
                narrationElementOutput
        );
    }

    public enum Tone {
        NORMAL,
        PRIMARY,
        DANGER
    }
}