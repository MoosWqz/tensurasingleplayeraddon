package com.mooswqz.moostensuraaddon.client.screen.skillui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Shared grouped skill list with bounded scrolling and keyboard navigation.
 */
public final class SkillListWidget extends AbstractWidget {

    private static final int INNER_PADDING = 2;
    private static final int SCROLLBAR_WIDTH = 7;

    private final Font font;
    private final SkillUiTheme theme;
    private final SkillUiListModel listModel;
    private final SkillUiSelectionModel selectionModel;

    private Consumer<SkillUiEntry> focusListener =
            entry -> {
            };
    private BiConsumer<
            SkillUiEntry,
            SkillUiSelectionModel.ToggleResult
            > selectionListener =
            (entry, result) -> {
            };

    private SkillUiEntry hoveredEntry;

    public SkillListWidget(
            int x,
            int y,
            int width,
            int height,
            Font font,
            SkillUiTheme theme,
            SkillUiListModel listModel,
            SkillUiSelectionModel selectionModel
    ) {
        super(
                x,
                y,
                Math.max(1, width),
                Math.max(1, height),
                Component.literal("Skill list")
        );

        this.font = font == null
                ? Minecraft.getInstance().font
                : font;
        this.theme = theme == null
                ? SkillUiTheme.GRANTER
                : theme;
        this.listModel = listModel == null
                ? new SkillUiListModel(
                new SkillUiFilterState()
        )
                : listModel;
        this.selectionModel = selectionModel == null
                ? new SkillUiSelectionModel(
                SkillUiSelectionMode.READ_ONLY
        )
                : selectionModel;

        updateViewportHeight();
    }

    public SkillUiListModel listModel() {
        return listModel;
    }

    public SkillUiSelectionModel selectionModel() {
        return selectionModel;
    }

    public void setEntries(
            List<SkillUiEntry> entries
    ) {
        listModel.setEntries(entries);
        selectionModel.retainAvailable(
                listModel.allEntries()
        );
    }

    public void rebuildFilter() {
        listModel.rebuild();
        selectionModel.retainAvailable(
                listModel.allEntries()
        );
    }

    public void setFocusListener(
            Consumer<SkillUiEntry> focusListener
    ) {
        this.focusListener = focusListener == null
                ? entry -> {
        }
                : focusListener;
    }

    public void setSelectionListener(
            BiConsumer<
                    SkillUiEntry,
                    SkillUiSelectionModel.ToggleResult
                    > selectionListener
    ) {
        this.selectionListener = selectionListener == null
                ? (entry, result) -> {
        }
                : selectionListener;
    }

    public Optional<SkillUiEntry> hoveredEntry() {
        return Optional.ofNullable(
                hoveredEntry
        );
    }

    public Optional<SkillUiEntry> focusedEntry() {
        return listModel.focusedEntry();
    }

    @Override
    protected void renderWidget(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        updateViewportHeight();

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
                isFocused()
                        ? 2
                        : 1
        );

        int contentLeft = getX()
                + INNER_PADDING;
        int contentTop = getY()
                + INNER_PADDING;
        int contentRight = getX()
                + getWidth()
                - INNER_PADDING;
        int contentBottom = getY()
                + getHeight()
                - INNER_PADDING;

        hoveredEntry = null;

        if (listModel.rows().isEmpty()) {
            SkillUiRenderHelper.drawCenteredText(
                    guiGraphics,
                    font,
                    Component.literal(
                            "No matching skills"
                    ),
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

        boolean pointerInsideRows = mouseX >= contentLeft
                && mouseX < contentRight
                - SCROLLBAR_WIDTH
                && mouseY >= contentTop
                && mouseY < contentBottom;

        for (SkillUiListModel.PositionedRow positioned :
                listModel.visibleRows()) {

            int rowTop = contentTop
                    + positioned.y();
            SkillUiListModel.Row row = positioned.row();

            if (row.categoryHeader()) {
                renderCategoryHeader(
                        guiGraphics,
                        row,
                        contentLeft,
                        rowTop,
                        contentRight
                );
                continue;
            }

            boolean hovered = pointerInsideRows
                    && mouseY >= rowTop
                    && mouseY < rowTop + row.height();

            if (hovered) {
                hoveredEntry = row.entry();
            }

            renderSkillRow(
                    guiGraphics,
                    row.entry(),
                    positioned.rowIndex(),
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
                listModel.scrollOffset(),
                listModel.maximumScroll(),
                listModel.contentHeight(),
                theme
        );
    }

    private void renderCategoryHeader(
            GuiGraphics guiGraphics,
            SkillUiListModel.Row row,
            int left,
            int top,
            int right
    ) {
        guiGraphics.fill(
                left,
                top,
                right - SCROLLBAR_WIDTH,
                top + row.height(),
                theme.categoryFillColor()
        );

        SkillUiRenderHelper.drawText(
                guiGraphics,
                font,
                row.category().displayName(),
                left + 5,
                top + 5,
                row.category().defaultAccentColor()
        );
    }

    private void renderSkillRow(
            GuiGraphics guiGraphics,
            SkillUiEntry entry,
            int rowIndex,
            int left,
            int top,
            int right,
            boolean hovered
    ) {
        boolean selected = selectionModel.isSelected(
                entry.skillId()
        );
        boolean focused = rowIndex
                == listModel.focusedRowIndex();

        int fillColor = selected
                ? theme.selectedRowFillColor()
                : hovered
                  ? theme.hoveredRowFillColor()
                  : theme.rowFillColor();

        int borderColor = selected
                || focused
                ? SkillUiTheme.opaque(
                entry.accentColor()
        )
                : theme.panelBorderColor();

        SkillUiRenderHelper.drawBorderedPanel(
                guiGraphics,
                new SkillUiLayout.Rect(
                        left,
                        top,
                        Math.max(1, right - left),
                        SkillUiListModel.SKILL_ROW_HEIGHT - 1
                ),
                borderColor,
                fillColor,
                focused
                        ? 2
                        : 1
        );

        int textLeft = left + 6;

        if (selectionModel.mode()
                != SkillUiSelectionMode.READ_ONLY) {
            if (selectionModel.mode()
                    == SkillUiSelectionMode.SINGLE) {
                SkillUiRenderHelper.drawRadioIndicator(
                        guiGraphics,
                        left + 5,
                        top + 7,
                        selected,
                        entry.selectable(),
                        theme
                );
            } else {
                SkillUiRenderHelper.drawCheckbox(
                        guiGraphics,
                        left + 5,
                        top + 7,
                        selected,
                        entry.selectable(),
                        theme
                );
            }
            textLeft += 16;
        }

        int rightPadding = 8;
        boolean showMasteredBadge = entry.mastered()
                && right - left >= 150;
        int badgeWidth = showMasteredBadge
                ? 42
                : 0;
        int maximumNameWidth = Math.max(
                1,
                right
                        - rightPadding
                        - badgeWidth
                        - textLeft
        );

        int nameColor = entry.selectable()
                ? entry.accentColor()
                : theme.disabledTextColor();

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                entry.displayName(),
                textLeft,
                top + 4,
                maximumNameWidth,
                nameColor
        );

        Component secondaryLine;
        int secondaryColor;

        if (!entry.selectable()
                && entry.hasDisabledReason()) {
            secondaryLine = entry.disabledReason();
            secondaryColor = theme.warningColor();
        } else if (entry.hasSourceName()) {
            secondaryLine = entry.sourceName();
            secondaryColor = theme.secondaryTextColor();
        } else {
            secondaryLine = Component.literal(
                    entry.skillId()
            );
            secondaryColor = theme.mutedTextColor();
        }

        SkillUiRenderHelper.drawClippedText(
                guiGraphics,
                font,
                secondaryLine,
                textLeft,
                top + 15,
                Math.max(
                        1,
                        right - rightPadding - textLeft
                ),
                secondaryColor
        );

        if (showMasteredBadge) {
            SkillUiRenderHelper.drawText(
                    guiGraphics,
                    font,
                    Component.literal("Mastered"),
                    right - 45,
                    top + 4,
                    theme.successColor()
            );
        }
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
                - INNER_PADDING;

        Optional<SkillUiEntry> clickedEntry =
                listModel.entryAt(localY);

        if (clickedEntry.isEmpty()) {
            return true;
        }

        setFocused(true);

        SkillUiEntry entry = clickedEntry.get();
        listModel.focusEntry(
                entry.skillId()
        );
        focusListener.accept(entry);

        SkillUiSelectionModel.ToggleResult result =
                selectionModel.toggle(entry);

        selectionListener.accept(
                entry,
                result
        );

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
                || !listModel.canScroll()) {
            return false;
        }

        listModel.scrollRows(
                -scrollY * 2.5D
        );
        return true;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (!active
                || !visible
                || !isFocused()) {
            return false;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                notifyFocus(
                        listModel.moveFocus(-1)
                );
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                notifyFocus(
                        listModel.moveFocus(1)
                );
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                notifyFocus(
                        listModel.focusFirst()
                );
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                notifyFocus(
                        listModel.focusLast()
                );
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                listModel.scrollBy(
                        -Math.max(
                                1,
                                listModel.viewportHeight() - 24
                        )
                );
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                listModel.scrollBy(
                        Math.max(
                                1,
                                listModel.viewportHeight() - 24
                        )
                );
                return true;
            }
            case GLFW.GLFW_KEY_ENTER,
                 GLFW.GLFW_KEY_KP_ENTER,
                 GLFW.GLFW_KEY_SPACE -> {
                toggleFocusedEntry();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void toggleFocusedEntry() {
        Optional<SkillUiEntry> focused =
                listModel.focusedEntry();

        if (focused.isEmpty()) {
            return;
        }

        SkillUiEntry entry = focused.get();
        SkillUiSelectionModel.ToggleResult result =
                selectionModel.toggle(entry);

        selectionListener.accept(
                entry,
                result
        );
    }

    private void notifyFocus(
            Optional<SkillUiEntry> entry
    ) {
        entry.ifPresent(
                focusListener
        );
    }

    private void updateViewportHeight() {
        listModel.setViewportHeight(
                Math.max(
                        0,
                        getHeight()
                                - INNER_PADDING * 2
                )
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

        listModel.focusedEntry()
                .ifPresent(entry ->
                        narrationElementOutput.add(
                                NarratedElementType.POSITION,
                                entry.displayName()
                        )
                );

        narrationElementOutput.add(
                NarratedElementType.USAGE,
                Component.literal(
                        "Use arrow keys to move, Enter to select, and the mouse wheel to scroll."
                )
        );
    }
}