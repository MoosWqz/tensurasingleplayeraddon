package com.mooswqz.moostensuraaddon.client.screen.skillui;

import com.mooswqz.moostensuraaddon.network.OpenSubordinateOverviewScreenPayload;
import com.mooswqz.moostensuraaddon.util.SubordinateOverviewPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class SubordinateListWidget extends AbstractWidget {

    private static final int INNER_PADDING = 2;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int ROW_HEIGHT = 34;

    private final Font font;
    private final SkillUiTheme theme;
    private final List<OpenSubordinateOverviewScreenPayload.TargetEntry>
            allTargets = new ArrayList<>();
    private final List<OpenSubordinateOverviewScreenPayload.TargetEntry>
            filteredTargets = new ArrayList<>();

    private Consumer<OpenSubordinateOverviewScreenPayload.TargetEntry>
            selectionListener = target -> {
    };
    private String query = "";
    private String selectedUuid = "";
    private int focusedIndex;
    private double scrollOffset;
    private OpenSubordinateOverviewScreenPayload.TargetEntry hoveredTarget;

    public SubordinateListWidget(
            int x,
            int y,
            int width,
            int height,
            Font font,
            SkillUiTheme theme
    ) {
        super(
                x,
                y,
                Math.max(1, width),
                Math.max(1, height),
                Component.literal("Subordinate list")
        );
        this.font = font == null
                ? Minecraft.getInstance().font
                : font;
        this.theme = theme == null
                ? SkillUiTheme.GRANTER
                : theme;
    }

    public void setTargets(
            List<OpenSubordinateOverviewScreenPayload.TargetEntry> targets
    ) {
        allTargets.clear();

        if (targets != null) {
            allTargets.addAll(
                    targets.stream()
                            .filter(target -> target != null)
                            .toList()
            );
        }

        rebuildFilter();
    }

    public void setQuery(
            String query
    ) {
        this.query = query == null ? "" : query;
        rebuildFilter();
    }

    public void setSelectionListener(
            Consumer<OpenSubordinateOverviewScreenPayload.TargetEntry>
                    selectionListener
    ) {
        this.selectionListener = selectionListener == null
                ? target -> {
        }
                : selectionListener;
    }

    public void select(
            String targetUuid
    ) {
        selectedUuid = targetUuid == null ? "" : targetUuid;

        for (int index = 0; index < filteredTargets.size(); index++) {
            if (filteredTargets.get(index).targetUuid()
                    .equals(selectedUuid)) {
                focusedIndex = index;
                ensureFocusedVisible();
                return;
            }
        }

        if (!filteredTargets.isEmpty()) {
            focusedIndex = Math.min(
                    focusedIndex,
                    filteredTargets.size() - 1
            );
        } else {
            focusedIndex = 0;
        }
    }

    public Optional<OpenSubordinateOverviewScreenPayload.TargetEntry>
    selectedTarget() {
        return allTargets.stream()
                .filter(target -> target.targetUuid().equals(selectedUuid))
                .findFirst();
    }

    public int visibleTargetCount() {
        return filteredTargets.size();
    }

    public Optional<OpenSubordinateOverviewScreenPayload.TargetEntry>
    hoveredTarget() {
        return Optional.ofNullable(hoveredTarget);
    }

    @Override
    protected void renderWidget(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        SkillUiLayout.Rect bounds = new SkillUiLayout.Rect(
                getX(),
                getY(),
                getWidth(),
                getHeight()
        );
        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                bounds,
                isFocused()
                        ? theme.accentColor()
                        : theme.panelBorderColor(),
                theme.panelFillColor(),
                isFocused() ? 2 : 1
        );

        int contentLeft = getX() + INNER_PADDING;
        int contentTop = getY() + INNER_PADDING;
        int contentRight = getX() + getWidth() - INNER_PADDING;
        int contentBottom = getY() + getHeight() - INNER_PADDING;
        hoveredTarget = null;

        if (filteredTargets.isEmpty()) {
            SkillUiRenderHelper.drawCenteredText(
                    guiGraphics,
                    font,
                    Component.literal("No matching subordinates"),
                    getX() + getWidth() / 2,
                    getY() + getHeight() / 2 - 4,
                    theme.mutedTextColor()
            );
            return;
        }

        guiGraphics.enableScissor(
                contentLeft,
                contentTop,
                contentRight,
                contentBottom
        );

        int firstIndex = Math.max(
                0,
                (int) Math.floor(scrollOffset / ROW_HEIGHT)
        );
        int offsetInsideRow = (int) Math.floor(scrollOffset)
                - firstIndex * ROW_HEIGHT;
        int visibleRows = getHeight() / ROW_HEIGHT + 2;

        for (int index = firstIndex;
             index < filteredTargets.size()
                     && index < firstIndex + visibleRows;
             index++) {
            int rowTop = contentTop
                    + (index - firstIndex) * ROW_HEIGHT
                    - offsetInsideRow;
            int rowBottom = rowTop + ROW_HEIGHT - 2;

            if (rowBottom <= contentTop || rowTop >= contentBottom) {
                continue;
            }

            OpenSubordinateOverviewScreenPayload.TargetEntry target =
                    filteredTargets.get(index);
            boolean hovered = mouseX >= contentLeft
                    && mouseX < contentRight - SCROLLBAR_WIDTH
                    && mouseY >= rowTop
                    && mouseY < rowBottom;

            if (hovered) {
                hoveredTarget = target;
            }

            renderTargetRow(
                    guiGraphics,
                    target,
                    index,
                    contentLeft,
                    rowTop,
                    contentRight - SCROLLBAR_WIDTH,
                    hovered
            );
        }

        guiGraphics.disableScissor();

        SkillUiRenderHelper.drawScrollbar(
                guiGraphics,
                bounds.inset(INNER_PADDING),
                scrollOffset,
                maximumScroll(),
                filteredTargets.size() * ROW_HEIGHT,
                theme
        );
    }

    private void renderTargetRow(
            GuiGraphics guiGraphics,
            OpenSubordinateOverviewScreenPayload.TargetEntry target,
            int index,
            int left,
            int top,
            int right,
            boolean hovered
    ) {
        boolean selected = target.targetUuid().equals(selectedUuid);
        boolean focused = index == focusedIndex;
        int fillColor = selected
                ? theme.selectedRowFillColor()
                : hovered
                  ? theme.hoveredRowFillColor()
                  : theme.rowFillColor();
        int borderColor = selected || focused
                ? theme.accentColor()
                : theme.panelBorderColor();

        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                new SkillUiLayout.Rect(
                        left,
                        top,
                        Math.max(1, right - left),
                        ROW_HEIGHT - 2
                ),
                borderColor,
                fillColor,
                focused ? 2 : 1
        );

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(target.targetName()),
                left + 6,
                top + 4,
                Math.max(1, right - left - 12),
                selected
                        ? theme.accentColor()
                        : theme.primaryTextColor()
        );
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(
                        target.typeName()
                                + " • "
                                + formatDistance(target.distance())
                ),
                left + 6,
                top + 15,
                Math.max(1, right - left - 12),
                theme.secondaryTextColor()
        );
        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                Component.literal(
                        target.skills().size()
                                + (target.skills().size() == 1
                                ? " skill"
                                : " skills")
                ),
                left + 6,
                top + 25,
                Math.max(1, right - left - 12),
                theme.mutedTextColor()
        );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (!active
                || !visible
                || button != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int localY = (int) Math.floor(mouseY)
                - getY()
                - INNER_PADDING
                + (int) Math.floor(scrollOffset);
        int index = localY / ROW_HEIGHT;

        if (index < 0 || index >= filteredTargets.size()) {
            return true;
        }

        setFocused(true);
        focusedIndex = index;
        OpenSubordinateOverviewScreenPayload.TargetEntry target =
                filteredTargets.get(index);
        selectedUuid = target.targetUuid();
        selectionListener.accept(target);
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (!visible
                || !isMouseOver(mouseX, mouseY)
                || maximumScroll() <= 0.0D) {
            return false;
        }

        scrollOffset = clampScroll(
                scrollOffset - scrollY * ROW_HEIGHT * 0.8D
        );
        return true;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (!active || !visible || !isFocused()) {
            return false;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                moveFocus(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveFocus(1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                focusedIndex = 0;
                selectFocused();
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                focusedIndex = Math.max(
                        0,
                        filteredTargets.size() - 1
                );
                selectFocused();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER,
                 GLFW.GLFW_KEY_KP_ENTER,
                 GLFW.GLFW_KEY_SPACE -> {
                selectFocused();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void moveFocus(
            int direction
    ) {
        if (filteredTargets.isEmpty()) {
            return;
        }

        focusedIndex = Math.max(
                0,
                Math.min(
                        filteredTargets.size() - 1,
                        focusedIndex + direction
                )
        );
        selectFocused();
    }

    private void selectFocused() {
        if (filteredTargets.isEmpty()) {
            return;
        }

        OpenSubordinateOverviewScreenPayload.TargetEntry target =
                filteredTargets.get(focusedIndex);
        selectedUuid = target.targetUuid();
        ensureFocusedVisible();
        selectionListener.accept(target);
    }

    private void rebuildFilter() {
        filteredTargets.clear();

        for (OpenSubordinateOverviewScreenPayload.TargetEntry target :
                allTargets) {
            List<String> skillSearchTexts = target.skills()
                    .stream()
                    .map(skill -> skill.displayName()
                            + " "
                            + skill.skillId()
                            + " "
                            + skill.categoryName())
                    .toList();

            if (SubordinateOverviewPolicy.matchesTarget(
                    query,
                    target.targetName(),
                    target.typeName(),
                    skillSearchTexts
            )) {
                filteredTargets.add(target);
            }
        }

        scrollOffset = clampScroll(scrollOffset);
        focusedIndex = Math.max(
                0,
                Math.min(
                        focusedIndex,
                        Math.max(0, filteredTargets.size() - 1)
                )
        );
    }

    private void ensureFocusedVisible() {
        int rowTop = focusedIndex * ROW_HEIGHT;
        int rowBottom = rowTop + ROW_HEIGHT;

        if (rowTop < scrollOffset) {
            scrollOffset = rowTop;
        } else if (rowBottom > scrollOffset + getHeight()) {
            scrollOffset = rowBottom - getHeight();
        }

        scrollOffset = clampScroll(scrollOffset);
    }

    private double maximumScroll() {
        return Math.max(
                0.0D,
                filteredTargets.size() * ROW_HEIGHT
                        - Math.max(1, getHeight() - INNER_PADDING * 2)
        );
    }

    private double clampScroll(
            double value
    ) {
        return Math.max(
                0.0D,
                Math.min(maximumScroll(), value)
        );
    }

    private static String formatDistance(
            double distance
    ) {
        return String.format(
                java.util.Locale.US,
                distance < 10.0D ? "%.1f m" : "%.0f m",
                distance
        );
    }

    @Override
    protected void updateWidgetNarration(
            NarrationElementOutput narrationElementOutput
    ) {
        narrationElementOutput.add(
                NarratedElementType.TITLE,
                getMessage()
        );

        if (!filteredTargets.isEmpty()) {
            OpenSubordinateOverviewScreenPayload.TargetEntry focused =
                    filteredTargets.get(
                            Math.min(
                                    focusedIndex,
                                    filteredTargets.size() - 1
                            )
                    );
            narrationElementOutput.add(
                    NarratedElementType.POSITION,
                    Component.literal(focused.targetName())
            );
        }

        narrationElementOutput.add(
                NarratedElementType.USAGE,
                Component.literal(
                        "Use arrow keys to move, Enter to select, and the mouse wheel to scroll."
                )
        );
    }
}