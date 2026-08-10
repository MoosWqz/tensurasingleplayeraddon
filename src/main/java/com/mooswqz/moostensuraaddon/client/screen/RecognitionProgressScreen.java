package com.mooswqz.moostensuraaddon.client.screen;

import com.mooswqz.moostensuraaddon.client.ClientRecognitionBenefitsCache;
import com.mooswqz.moostensuraaddon.network.OpenRecognitionProgressScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;

/**
 * Unified player-facing view of recognition guidance, paths and benefits.
 *
 * <p>The server remains authoritative for every displayed value. This screen
 * only presents the sanitized progress payload and the separately synchronized
 * recognition-benefit snapshot.</p>
 */
public final class RecognitionProgressScreen extends Screen {

    private static final int MARGIN = 12;
    private static final int HEADER_BOTTOM = 47;
    private static final int TAB_TOP = 49;
    private static final int TAB_HEIGHT = 20;
    private static final int CONTENT_TOP = 74;
    private static final int FOOTER_HEIGHT = 30;
    private static final int CELL_GAP = 3;
    private static final int PANEL_GAP = 6;

    private static final String[] MATRIX_PATH_IDS = {
            "lawful_good",
            "lawful_neutral",
            "lawful_evil",
            "neutral_good",
            "true_neutral",
            "neutral_evil",
            "chaotic_good",
            "chaotic_neutral",
            "chaotic_evil"
    };

    private static final String[] COLUMN_LABELS = {
            "Good",
            "Neutral",
            "Evil"
    };

    private static final String[] ROW_LABELS = {
            "L",
            "N",
            "C"
    };

    private final OpenRecognitionProgressScreenPayload payload;

    private View activeView;
    private int overviewFirstRow;

    private Button overviewTabButton;
    private Button pathsTabButton;
    private Button benefitsTabButton;
    private Button previousPageButton;
    private Button nextPageButton;
    private Button closeButton;

    public RecognitionProgressScreen(
            OpenRecognitionProgressScreenPayload payload
    ) {
        super(Component.translatable(
                "screen.moostensuraaddon.recognition.title"
        ));

        this.payload = payload;
        this.activeView = payload != null
                && payload.recognitionCommitted()
                ? View.PATHS
                : View.OVERVIEW;
    }

    @Override
    protected void init() {
        int tabAreaWidth = Math.min(
                390,
                Math.max(
                        210,
                        this.width - MARGIN * 2
                )
        );

        int tabGap = 3;
        int tabWidth = Math.max(
                64,
                (tabAreaWidth - tabGap * 2) / 3
        );
        int realTabWidth = tabWidth * 3 + tabGap * 2;
        int tabLeft = this.width / 2 - realTabWidth / 2;

        this.overviewTabButton = Button.builder(
                        Component.translatable(
                                "screen.moostensuraaddon.recognition.tab.overview"
                        ),
                        button -> setActiveView(View.OVERVIEW)
                )
                .pos(tabLeft, TAB_TOP)
                .size(tabWidth, TAB_HEIGHT)
                .build();

        this.pathsTabButton = Button.builder(
                        Component.translatable(
                                "screen.moostensuraaddon.recognition.tab.paths"
                        ),
                        button -> setActiveView(View.PATHS)
                )
                .pos(
                        tabLeft + tabWidth + tabGap,
                        TAB_TOP
                )
                .size(tabWidth, TAB_HEIGHT)
                .build();

        this.benefitsTabButton = Button.builder(
                        Component.translatable(
                                "screen.moostensuraaddon.recognition.tab.benefits"
                        ),
                        button -> setActiveView(View.BENEFITS)
                )
                .pos(
                        tabLeft + (tabWidth + tabGap) * 2,
                        TAB_TOP
                )
                .size(tabWidth, TAB_HEIGHT)
                .build();

        int footerY = Math.max(
                CONTENT_TOP,
                this.height - 25
        );

        this.previousPageButton = Button.builder(
                        Component.translatable(
                                "screen.moostensuraaddon.recognition.previous"
                        ),
                        button -> changeOverviewPage(-1)
                )
                .pos(MARGIN, footerY)
                .size(
                        Math.min(92, Math.max(66, this.width / 5)),
                        20
                )
                .build();

        this.nextPageButton = Button.builder(
                        Component.translatable(
                                "screen.moostensuraaddon.recognition.next"
                        ),
                        button -> changeOverviewPage(1)
                )
                .size(
                        Math.min(92, Math.max(66, this.width / 5)),
                        20
                )
                .build();

        this.nextPageButton.setX(
                Math.max(
                        MARGIN,
                        this.width
                                - MARGIN
                                - this.nextPageButton.getWidth()
                )
        );
        this.nextPageButton.setY(footerY);

        int closeWidth = Math.min(
                120,
                Math.max(80, this.width / 3)
        );

        this.closeButton = Button.builder(
                        Component.translatable(
                                "screen.moostensuraaddon.recognition.close"
                        ),
                        button -> onClose()
                )
                .pos(
                        this.width / 2 - closeWidth / 2,
                        footerY
                )
                .size(closeWidth, 20)
                .build();

        addRenderableWidget(this.overviewTabButton);
        addRenderableWidget(this.pathsTabButton);
        addRenderableWidget(this.benefitsTabButton);
        addRenderableWidget(this.previousPageButton);
        addRenderableWidget(this.nextPageButton);
        addRenderableWidget(this.closeButton);

        clampOverviewPage();
        updateWidgetStates();
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        /*
         * Do not call Screen#renderBackground or Screen#render.
         *
         * The custom opaque background avoids Minecraft's menu blur path.
         * Registered widgets are rendered explicitly after all custom panels
         * so labels, progress bars and buttons remain equally sharp.
         */
        guiGraphics.fill(
                0,
                0,
                this.width,
                this.height,
                0xFF0D0F14
        );

        renderHeader(guiGraphics);

        PanelRect content = createContentPanel();

        switch (activeView) {
            case OVERVIEW ->
                    renderOverview(guiGraphics, content);
            case PATHS ->
                    renderPaths(guiGraphics, content);
            case BENEFITS ->
                    renderBenefitsPanel(guiGraphics, content);
        }

        renderWidget(
                this.overviewTabButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
        renderWidget(
                this.pathsTabButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
        renderWidget(
                this.benefitsTabButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
        renderWidget(
                this.previousPageButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
        renderWidget(
                this.nextPageButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
        renderWidget(
                this.closeButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    /**
     * Minecraft 1.21.x supplies horizontal and vertical wheel deltas.
     * The page buttons remain available when a platform does not forward a
     * wheel event to this method.
     */
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (activeView != View.OVERVIEW
                || scrollY == 0.0D
                || !createContentPanel().contains(
                mouseX,
                mouseY
        )) {
            return false;
        }

        changeOverviewPage(
                scrollY > 0.0D ? -1 : 1
        );
        return true;
    }

    private void setActiveView(
            View view
    ) {
        if (view == null || activeView == view) {
            return;
        }

        activeView = view;

        if (activeView == View.OVERVIEW) {
            clampOverviewPage();
        }

        updateWidgetStates();
    }

    private void changeOverviewPage(
            int delta
    ) {
        OverviewLayout layout = createOverviewLayout(
                createContentPanel()
        );

        int maximumFirstRow = Math.max(
                0,
                layout.totalRows()
                        - layout.visibleRows()
        );

        overviewFirstRow = Math.max(
                0,
                Math.min(
                        maximumFirstRow,
                        overviewFirstRow + delta
                )
        );

        updateWidgetStates();
    }

    private void clampOverviewPage() {
        OverviewLayout layout = createOverviewLayout(
                createContentPanel()
        );

        overviewFirstRow = Math.max(
                0,
                Math.min(
                        Math.max(
                                0,
                                layout.totalRows()
                                        - layout.visibleRows()
                        ),
                        overviewFirstRow
                )
        );
    }

    private void updateWidgetStates() {
        if (this.overviewTabButton != null) {
            this.overviewTabButton.active =
                    activeView != View.OVERVIEW;
        }

        if (this.pathsTabButton != null) {
            this.pathsTabButton.active =
                    activeView != View.PATHS;
        }

        if (this.benefitsTabButton != null) {
            this.benefitsTabButton.active =
                    activeView != View.BENEFITS;
        }

        OverviewLayout layout = createOverviewLayout(
                createContentPanel()
        );

        boolean paginated = activeView == View.OVERVIEW
                && layout.totalRows() > layout.visibleRows();

        if (this.previousPageButton != null) {
            this.previousPageButton.visible = paginated;
            this.previousPageButton.active =
                    overviewFirstRow > 0;
        }

        if (this.nextPageButton != null) {
            this.nextPageButton.visible = paginated;
            this.nextPageButton.active =
                    overviewFirstRow
                            + layout.visibleRows()
                            < layout.totalRows();
        }
    }

    private void renderHeader(
            GuiGraphics guiGraphics
    ) {
        drawCenteredClipped(
                guiGraphics,
                this.title.copy().withStyle(
                        ChatFormatting.LIGHT_PURPLE,
                        ChatFormatting.BOLD
                ),
                this.width / 2,
                6,
                this.width - MARGIN * 2,
                0xFFFFFF
        );

        MutableComponent identity = Component.literal(
                        safeText(payload == null
                                ? ""
                                : payload.identityLine())
                )
                .withColor(
                        payload == null
                                ? 0xFFFFFF
                                : payload.recognitionColor()
                );

        if (payload != null
                && payload.recognitionBold()) {
            identity.withStyle(ChatFormatting.BOLD);
        }

        drawCenteredClipped(
                guiGraphics,
                identity,
                this.width / 2,
                20,
                this.width - MARGIN * 2,
                payload == null
                        ? 0xFFFFFF
                        : payload.recognitionColor()
        );

        drawCenteredClipped(
                guiGraphics,
                Component.literal(
                                safeText(
                                        payload == null
                                                ? ""
                                                : payload.pathSummary()
                                )
                        )
                        .withStyle(ChatFormatting.GRAY),
                this.width / 2,
                34,
                this.width - MARGIN * 2,
                0xA0A0A0
        );

        guiGraphics.fill(
                MARGIN,
                HEADER_BOTTOM,
                Math.max(MARGIN, this.width - MARGIN),
                HEADER_BOTTOM + 1,
                0xFF2B2633
        );
    }

    private PanelRect createContentPanel() {
        int bottom = Math.max(
                CONTENT_TOP + 1,
                this.height - FOOTER_HEIGHT
        );

        return new PanelRect(
                MARGIN,
                CONTENT_TOP,
                Math.max(1, this.width - MARGIN * 2),
                Math.max(1, bottom - CONTENT_TOP)
        );
    }

    private void renderOverview(
            GuiGraphics guiGraphics,
            PanelRect panel
    ) {
        OverviewLayout layout =
                createOverviewLayout(panel);

        renderStatusPanel(
                guiGraphics,
                layout.statusPanel()
        );

        List<OpenRecognitionProgressScreenPayload.GuidanceEntry>
                entries =
                guidanceEntries();

        if (entries.isEmpty()) {
            drawBorderedPanel(
                    guiGraphics,
                    layout.guidancePanel().left(),
                    layout.guidancePanel().top(),
                    layout.guidancePanel().right(),
                    layout.guidancePanel().bottom(),
                    0xFF3A3148,
                    0xD014141A,
                    1
            );

            drawCenteredClipped(
                    guiGraphics,
                    Component.translatable(
                            "screen.moostensuraaddon.recognition.overview.empty"
                    ),
                    layout.guidancePanel().left()
                            + layout.guidancePanel().width() / 2,
                    layout.guidancePanel().top()
                            + Math.max(
                            5,
                            layout.guidancePanel().height() / 2 - 4
                    ),
                    layout.guidancePanel().width() - 12,
                    0x9FA7B3
            );
            return;
        }

        int firstEntry =
                overviewFirstRow * layout.columns();

        int lastEntryExclusive = Math.min(
                entries.size(),
                firstEntry
                        + layout.visibleRows()
                        * layout.columns()
        );

        for (int entryIndex = firstEntry;
             entryIndex < lastEntryExclusive;
             entryIndex++) {

            int localIndex = entryIndex - firstEntry;
            int row = localIndex / layout.columns();
            int column = localIndex % layout.columns();

            int left = layout.guidancePanel().left()
                    + column
                    * (
                    layout.cardWidth()
                            + PANEL_GAP
            );

            int top = layout.guidancePanel().top()
                    + row
                    * (
                    layout.cardHeight()
                            + PANEL_GAP
            );

            renderGuidanceCard(
                    guiGraphics,
                    entries.get(entryIndex),
                    new PanelRect(
                            left,
                            top,
                            layout.cardWidth(),
                            layout.cardHeight()
                    )
            );
        }

        if (layout.totalRows()
                > layout.visibleRows()) {
            int firstVisibleRow = Math.min(
                    layout.totalRows(),
                    overviewFirstRow + 1
            );

            int lastVisibleRow = Math.min(
                    layout.totalRows(),
                    overviewFirstRow
                            + layout.visibleRows()
            );

            Component rowIndicator =
                    firstVisibleRow == lastVisibleRow
                            ? Component.translatable(
                            "screen.moostensuraaddon.recognition.page",
                            firstVisibleRow,
                            layout.totalRows()
                    )
                            : Component.translatable(
                            "screen.moostensuraaddon.recognition.rows",
                            firstVisibleRow,
                            lastVisibleRow,
                            layout.totalRows()
                    );

            drawCenteredClipped(
                    guiGraphics,
                    rowIndicator,
                    panel.left() + panel.width() / 2,
                    panel.bottom() - 10,
                    Math.max(1, panel.width() - 180),
                    0x747B86
            );
        }
    }

    private OverviewLayout createOverviewLayout(
            PanelRect panel
    ) {
        int statusHeight = Math.min(
                72,
                Math.max(
                        56,
                        panel.height() / 4
                )
        );

        PanelRect statusPanel = new PanelRect(
                panel.left(),
                panel.top(),
                panel.width(),
                Math.min(
                        panel.height(),
                        statusHeight
                )
        );

        int guidanceTop = Math.min(
                panel.bottom(),
                statusPanel.bottom() + PANEL_GAP
        );

        int pageReserve = 14;
        int guidanceHeight = Math.max(
                1,
                panel.bottom()
                        - guidanceTop
                        - pageReserve
        );

        PanelRect guidancePanel = new PanelRect(
                panel.left(),
                guidanceTop,
                panel.width(),
                guidanceHeight
        );

        int columns = panel.width() >= 520
                ? 2
                : 1;

        int cardWidth = Math.max(
                1,
                (
                        guidancePanel.width()
                                - PANEL_GAP
                                * (columns - 1)
                ) / columns
        );

        int cardHeight;

        if (guidancePanel.height() >= 260) {
            cardHeight = 64;
        } else if (guidancePanel.height() >= 190) {
            cardHeight = 57;
        } else {
            cardHeight = 50;
        }

        int visibleRows = Math.max(
                1,
                (
                        guidancePanel.height()
                                + PANEL_GAP
                ) / (
                        cardHeight
                                + PANEL_GAP
                )
        );

        int totalRows = Math.max(
                1,
                (
                        guidanceEntries().size()
                                + columns - 1
                ) / columns
        );

        return new OverviewLayout(
                statusPanel,
                guidancePanel,
                columns,
                cardWidth,
                cardHeight,
                visibleRows,
                totalRows
        );
    }

    private void renderStatusPanel(
            GuiGraphics guiGraphics,
            PanelRect panel
    ) {
        int accent = getStatusColor();

        drawBorderedPanel(
                guiGraphics,
                panel.left(),
                panel.top(),
                panel.right(),
                panel.bottom(),
                opaque(accent),
                0xD014141A,
                1
        );

        int left = panel.left() + 7;
        int width = Math.max(
                1,
                panel.width() - 14
        );
        int y = panel.top() + 5;

        drawLineClipped(
                guiGraphics,
                Component.translatable(
                                "screen.moostensuraaddon.recognition.overview"
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                left,
                y,
                width,
                0xD7B5FF
        );

        y += 12;

        drawLineClipped(
                guiGraphics,
                Component.literal(
                                safeText(
                                        payload == null
                                                ? ""
                                                : payload.statusHeading()
                                )
                        )
                        .withStyle(ChatFormatting.BOLD),
                left,
                y,
                width,
                accent
        );

        y += 11;

        int levelY = panel.bottom()
                - this.font.lineHeight - 4;

        int detailHeight = Math.max(
                0,
                levelY - y - 2
        );

        drawWrappedLines(
                guiGraphics,
                Component.literal(
                        safeText(
                                payload == null
                                        ? ""
                                        : payload.statusDetail()
                        )
                ),
                left,
                y,
                width,
                detailHeight,
                0xC7C9CE
        );

        if (levelY >= panel.top() + 4) {
            drawLineClipped(
                    guiGraphics,
                    Component.literal(
                            safeText(
                                    payload == null
                                            ? ""
                                            : payload.levelLine()
                            )
                    ),
                    left,
                    levelY,
                    width,
                    0x8F96A1
            );
        }
    }

    private void renderGuidanceCard(
            GuiGraphics guiGraphics,
            OpenRecognitionProgressScreenPayload.GuidanceEntry entry,
            PanelRect card
    ) {
        if (entry == null) {
            return;
        }

        int color = entry.color();

        drawBorderedPanel(
                guiGraphics,
                card.left(),
                card.top(),
                card.right(),
                card.bottom(),
                opaque(color),
                0xD014141A,
                1
        );

        int left = card.left() + 6;
        int width = Math.max(
                1,
                card.width() - 12
        );

        int stageWidth = Math.min(
                Math.max(42, card.width() / 3),
                Math.max(42, width)
        );

        drawLineClipped(
                guiGraphics,
                Component.literal(
                                safeText(entry.displayName())
                        )
                        .withColor(color)
                        .withStyle(ChatFormatting.BOLD),
                left,
                card.top() + 4,
                Math.max(1, width - stageWidth - 3),
                color
        );

        drawRightAlignedClipped(
                guiGraphics,
                Component.literal(
                                safeText(entry.stageLabel())
                        )
                        .withStyle(ChatFormatting.GRAY),
                card.right() - 6,
                card.top() + 4,
                stageWidth,
                stageColor(entry.stageLabel())
        );

        int textTop = card.top() + 16;
        int barTop = card.bottom() - 7;
        int textHeight = Math.max(
                0,
                barTop - textTop - 2
        );

        drawWrappedLines(
                guiGraphics,
                Component.literal(
                        safeText(entry.guidanceText())
                ),
                left,
                textTop,
                width,
                textHeight,
                0xB6BAC2
        );

        guiGraphics.fill(
                left,
                barTop,
                left + width,
                barTop + 3,
                0xFF272727
        );

        int filledWidth = (int) Math.round(
                width * clampProgress(entry.progress())
        );

        if (filledWidth > 0) {
            guiGraphics.fill(
                    left,
                    barTop,
                    left + Math.min(width, filledWidth),
                    barTop + 3,
                    opaque(color)
            );
        }
    }

    private void renderPaths(
            GuiGraphics guiGraphics,
            PanelRect panel
    ) {
        int statusHeight = Math.min(
                68,
                Math.max(
                        52,
                        panel.height() / 4
                )
        );

        int matrixHeight = Math.max(
                1,
                panel.height()
                        - statusHeight
                        - PANEL_GAP
        );

        PanelRect matrixPanel = new PanelRect(
                panel.left(),
                panel.top(),
                panel.width(),
                matrixHeight
        );

        PanelRect summaryPanel = new PanelRect(
                panel.left(),
                matrixPanel.bottom() + PANEL_GAP,
                panel.width(),
                Math.max(
                        1,
                        panel.bottom()
                                - matrixPanel.bottom()
                                - PANEL_GAP
                )
        );

        renderPathMatrix(
                guiGraphics,
                matrixPanel
        );
        renderPathSummary(
                guiGraphics,
                summaryPanel
        );
    }

    private void renderPathMatrix(
            GuiGraphics guiGraphics,
            PanelRect panel
    ) {
        drawBorderedPanel(
                guiGraphics,
                panel.left(),
                panel.top(),
                panel.right(),
                panel.bottom(),
                0xFF3A3148,
                0xD014141A,
                1
        );

        int innerLeft = panel.left() + 14;
        int innerRight = panel.right() - 5;
        int labelsTop = panel.top() + 16;
        int innerTop = labelsTop + 12;
        int innerBottom = panel.bottom() - 5;
        int matrixWidth = Math.max(
                3,
                innerRight - innerLeft
        );
        int cellWidth = Math.max(
                1,
                (
                        matrixWidth
                                - CELL_GAP * 2
                ) / 3
        );
        int rowHeight = Math.max(
                19,
                (
                        Math.max(
                                57,
                                innerBottom - innerTop
                        )
                                - CELL_GAP * 2
                ) / 3
        );

        drawLineClipped(
                guiGraphics,
                Component.translatable(
                                "screen.moostensuraaddon.recognition.paths"
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                panel.left() + 6,
                panel.top() + 4,
                Math.max(1, panel.width() - 12),
                0xD7B5FF
        );

        for (int column = 0;
             column < COLUMN_LABELS.length;
             column++) {

            int cellLeft = innerLeft
                    + column
                    * (
                    cellWidth
                            + CELL_GAP
            );

            drawCenteredClipped(
                    guiGraphics,
                    Component.literal(
                            COLUMN_LABELS[column]
                    ),
                    cellLeft + cellWidth / 2,
                    labelsTop,
                    cellWidth,
                    0x9096A0
            );
        }

        for (int index = 0;
             index < MATRIX_PATH_IDS.length;
             index++) {

            int row = index / 3;
            int column = index % 3;
            int left = innerLeft
                    + column
                    * (
                    cellWidth
                            + CELL_GAP
            );
            int top = innerTop
                    + row
                    * (
                    rowHeight
                            + CELL_GAP
            );

            if (column == 0) {
                drawCenteredClipped(
                        guiGraphics,
                        Component.literal(
                                ROW_LABELS[row]
                        ),
                        panel.left() + 7,
                        top + Math.max(
                                4,
                                rowHeight / 2 - 4
                        ),
                        10,
                        0x707782
                );
            }

            OpenRecognitionProgressScreenPayload.PathEntry
                    path =
                    payload == null
                            ? null
                            : payload.findPath(
                            MATRIX_PATH_IDS[index]
                    );

            if (path == null) {
                drawBorderedPanel(
                        guiGraphics,
                        left,
                        top,
                        left + cellWidth,
                        Math.min(
                                panel.bottom() - 3,
                                top + rowHeight
                        ),
                        0xFF333333,
                        0xCC151515,
                        1
                );
                continue;
            }

            renderPathCell(
                    guiGraphics,
                    path,
                    left,
                    top,
                    cellWidth,
                    Math.min(
                            rowHeight,
                            panel.bottom() - top - 3
                    )
            );
        }
    }

    private void renderPathCell(
            GuiGraphics guiGraphics,
            OpenRecognitionProgressScreenPayload.PathEntry path,
            int left,
            int top,
            int width,
            int height
    ) {
        int safeHeight = Math.max(
                1,
                height
        );

        int borderColor =
                path.primary()
                        || path.secondary()
                        ? opaque(path.color())
                        : 0xFF353535;

        int borderThickness =
                path.primary()
                        ? 2
                        : 1;

        drawBorderedPanel(
                guiGraphics,
                left,
                top,
                left + width,
                top + safeHeight,
                borderColor,
                0xD0161616,
                borderThickness
        );

        MutableComponent displayName =
                Component.literal(
                                safeText(path.displayName())
                        )
                        .withColor(path.color());

        if (path.primary()
                && payload != null
                && payload.pureRecognition()) {
            displayName.withStyle(
                    ChatFormatting.BOLD
            );
        }

        drawCenteredClipped(
                guiGraphics,
                displayName,
                left + width / 2,
                top + 3,
                Math.max(1, width - 8),
                path.color()
        );

        if (safeHeight >= 25) {
            drawCenteredClipped(
                    guiGraphics,
                    Component.literal(
                            safeText(path.stageLabel())
                    ),
                    left + width / 2,
                    top + 14,
                    Math.max(1, width - 8),
                    path.primary()
                            || path.secondary()
                            ? 0xD0D0D0
                            : 0x858585
            );
        }

        if (path.primary()
                || path.secondary()) {
            guiGraphics.drawString(
                    this.font,
                    path.primary()
                            ? "P"
                            : "S",
                    left + width - 8,
                    top + 3,
                    opaque(path.color()),
                    false
            );
        }

        if (safeHeight >= 8) {
            int barLeft = left + 4;
            int barWidth = Math.max(
                    1,
                    width - 8
            );
            int barTop = top + safeHeight - 5;

            guiGraphics.fill(
                    barLeft,
                    barTop,
                    barLeft + barWidth,
                    barTop + 2,
                    0xFF272727
            );

            int filledWidth = (int) Math.round(
                    barWidth
                            * clampProgress(path.progress())
            );

            if (filledWidth > 0) {
                guiGraphics.fill(
                        barLeft,
                        barTop,
                        barLeft
                                + Math.min(
                                barWidth,
                                filledWidth
                        ),
                        barTop + 2,
                        opaque(path.color())
                );
            }
        }
    }

    private void renderPathSummary(
            GuiGraphics guiGraphics,
            PanelRect panel
    ) {
        int accent = getStatusColor();

        drawBorderedPanel(
                guiGraphics,
                panel.left(),
                panel.top(),
                panel.right(),
                panel.bottom(),
                opaque(accent),
                0xD014141A,
                1
        );

        int left = panel.left() + 7;
        int width = Math.max(
                1,
                panel.width() - 14
        );
        int y = panel.top() + 5;

        drawLineClipped(
                guiGraphics,
                Component.literal(
                                safeText(
                                        payload == null
                                                ? ""
                                                : payload.statusHeading()
                                )
                        )
                        .withStyle(ChatFormatting.BOLD),
                left,
                y,
                width,
                accent
        );

        y += 12;

        int levelWidth = Math.min(
                Math.max(90, width / 3),
                width
        );

        drawRightAlignedClipped(
                guiGraphics,
                Component.literal(
                        safeText(
                                payload == null
                                        ? ""
                                        : payload.levelLine()
                        )
                ),
                panel.right() - 7,
                y,
                levelWidth,
                0x8F96A1
        );

        drawWrappedLines(
                guiGraphics,
                Component.literal(
                        safeText(
                                payload == null
                                        ? ""
                                        : payload.statusDetail()
                        )
                ),
                left,
                y,
                Math.max(
                        1,
                        width - levelWidth - 6
                ),
                Math.max(
                        1,
                        panel.bottom() - y - 4
                ),
                0xC7C9CE
        );
    }

    private void renderBenefitsPanel(
            GuiGraphics guiGraphics,
            PanelRect panel
    ) {
        int accent = payload != null
                && payload.recognitionCommitted()
                ? payload.recognitionColor()
                : getStatusColor();

        drawBorderedPanel(
                guiGraphics,
                panel.left(),
                panel.top(),
                panel.right(),
                panel.bottom(),
                opaque(accent),
                0xD014141A,
                1
        );

        int textLeft = panel.left() + 8;
        int maxWidth = Math.max(
                1,
                panel.width() - 16
        );
        int y = panel.top() + 6;

        drawLineClipped(
                guiGraphics,
                Component.translatable(
                                "screen.moostensuraaddon.recognition.benefits"
                        )
                        .withStyle(
                                ChatFormatting.GOLD,
                                ChatFormatting.BOLD
                        ),
                textLeft,
                y,
                maxWidth,
                0xFFD36A
        );

        y += 14;

        ClientRecognitionBenefitsCache.Snapshot benefits =
                ClientRecognitionBenefitsCache.current();

        if (!benefits.active()) {
            drawLineClipped(
                    guiGraphics,
                    Component.translatable(
                            "screen.moostensuraaddon.recognition.benefit_state."
                                    + benefits.stateId()
                    ),
                    textLeft,
                    y,
                    maxWidth,
                    benefitsStateColor(
                            benefits.stateId()
                    )
            );

            y += 14;

            drawWrappedLines(
                    guiGraphics,
                    Component.literal(
                            safeText(
                                    payload == null
                                            ? ""
                                            : payload.statusHeading()
                            )
                    ),
                    textLeft,
                    y,
                    maxWidth,
                    Math.max(
                            1,
                            panel.bottom() - y - 6
                    ),
                    0xC7C9CE
            );
            return;
        }

        int lineStep = panel.height() < 105
                ? 11
                : 14;

        drawBenefitLine(
                guiGraphics,
                textLeft,
                y,
                maxWidth,
                "screen.moostensuraaddon.recognition.native_endowment",
                Component.translatable(
                        benefits.nativeEndowmentAnchored()
                                ? "screen.moostensuraaddon.recognition.anchored"
                                : "screen.moostensuraaddon.recognition.pending"
                ),
                benefits.nativeEndowmentAnchored()
                        ? 0xD7B5FF
                        : 0xFFD36A
        );
        y += lineStep;

        if (!hasLineSpace(panel, y)) {
            return;
        }

        drawBenefitLine(
                guiGraphics,
                textLeft,
                y,
                maxWidth,
                "screen.moostensuraaddon.recognition.permanent_strength",
                Component.literal(
                        formatPercent(
                                benefits.totalStrength()
                        )
                ),
                0xFFD36A
        );
        y += lineStep;

        if (!hasLineSpace(panel, y)) {
            return;
        }

        drawBenefitLine(
                guiGraphics,
                textLeft,
                y,
                maxWidth,
                "screen.moostensuraaddon.recognition.health_damage",
                Component.literal(
                        formatPercent(
                                benefits.maxHealthMultiplier()
                        )
                                + " / "
                                + formatPercent(
                                benefits.attackDamageMultiplier()
                        )
                ),
                0xFF8A8A
        );
        y += lineStep;

        if (!hasLineSpace(panel, y)) {
            return;
        }

        drawBenefitLine(
                guiGraphics,
                textLeft,
                y,
                maxWidth,
                "screen.moostensuraaddon.recognition.speed",
                Component.literal(
                        formatPercent(
                                benefits.movementSpeedMultiplier()
                        )
                                + " / "
                                + formatPercent(
                                benefits.attackSpeedMultiplier()
                        )
                ),
                0x73DCE8
        );
        y += lineStep;

        if (!hasLineSpace(panel, y)) {
            return;
        }

        drawBenefitLine(
                guiGraphics,
                textLeft,
                y,
                maxWidth,
                "screen.moostensuraaddon.recognition.knockback",
                Component.literal(
                        formatPercent(
                                benefits.knockbackResistanceAddition()
                        )
                ),
                0x71E0B8
        );
        y += lineStep;

        if (hasLineSpace(panel, y)) {
            drawBenefitLine(
                    guiGraphics,
                    textLeft,
                    y,
                    maxWidth,
                    "screen.moostensuraaddon.recognition.identity_strength",
                    Component.literal(
                            formatDecimal(
                                    benefits.frozenIdentityStrength()
                            )
                                    + " / "
                                    + formatDecimal(
                                    benefits.identityStrengthMaximum()
                            )
                    ),
                    0xC8A5FF
            );
            y += lineStep;
        }

        if (!benefits.attributeStateMatches()
                && hasLineSpace(panel, y)) {
            drawLineClipped(
                    guiGraphics,
                    Component.translatable(
                            "screen.moostensuraaddon.recognition.reconciling"
                    ),
                    textLeft,
                    y,
                    maxWidth,
                    0xFFD36A
            );
        }
    }

    private List<
            OpenRecognitionProgressScreenPayload.GuidanceEntry
            > guidanceEntries() {
        if (payload == null
                || payload.guidanceEntries() == null) {
            return List.of();
        }

        return payload.guidanceEntries();
    }

    private boolean hasLineSpace(
            PanelRect panel,
            int y
    ) {
        return panel != null
                && y + this.font.lineHeight
                <= panel.bottom() - 3;
    }

    private void drawBenefitLine(
            GuiGraphics guiGraphics,
            int left,
            int y,
            int maxWidth,
            String labelKey,
            Component value,
            int valueColor
    ) {
        Component line =
                Component.translatable(labelKey)
                        .withStyle(
                                ChatFormatting.GRAY
                        )
                        .append(
                                Component.literal(": ")
                        )
                        .append(
                                value.copy()
                                        .withColor(
                                                valueColor
                                        )
                        );

        drawLineClipped(
                guiGraphics,
                line,
                left,
                y,
                maxWidth,
                0xD7D9DE
        );
    }

    private void drawWrappedLines(
            GuiGraphics guiGraphics,
            Component text,
            int left,
            int top,
            int maxWidth,
            int maxHeight,
            int color
    ) {
        int y = top;
        int bottom = top
                + Math.max(
                0,
                maxHeight
        );

        for (var line :
                this.font.split(
                        text == null
                                ? Component.empty()
                                : text,
                        Math.max(1, maxWidth)
                )) {

            if (y + this.font.lineHeight > bottom) {
                break;
            }

            guiGraphics.drawString(
                    this.font,
                    line,
                    left,
                    y,
                    opaque(color),
                    false
            );

            y += this.font.lineHeight + 1;
        }
    }

    private void drawCenteredClipped(
            GuiGraphics guiGraphics,
            Component text,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        String clipped =
                this.font.plainSubstrByWidth(
                        text == null
                                ? ""
                                : text.getString(),
                        Math.max(1, maxWidth)
                );

        int left = centerX
                - this.font.width(clipped) / 2;

        guiGraphics.drawString(
                this.font,
                clipped,
                left,
                y,
                opaque(color),
                false
        );
    }

    private void drawRightAlignedClipped(
            GuiGraphics guiGraphics,
            Component text,
            int right,
            int y,
            int maxWidth,
            int color
    ) {
        String clipped =
                this.font.plainSubstrByWidth(
                        text == null
                                ? ""
                                : text.getString(),
                        Math.max(1, maxWidth)
                );

        guiGraphics.drawString(
                this.font,
                clipped,
                right - this.font.width(clipped),
                y,
                opaque(color),
                false
        );
    }

    private void drawLineClipped(
            GuiGraphics guiGraphics,
            Component text,
            int left,
            int y,
            int maxWidth,
            int color
    ) {
        String clipped =
                this.font.plainSubstrByWidth(
                        text == null
                                ? ""
                                : text.getString(),
                        Math.max(1, maxWidth)
                );

        guiGraphics.drawString(
                this.font,
                clipped,
                left,
                y,
                opaque(color),
                false
        );
    }

    private void renderWidget(
            Button button,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (button == null || !button.visible) {
            return;
        }

        button.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private int getStatusColor() {
        if (payload == null) {
            return 0x9FA7B3;
        }

        if (payload.revealPending()) {
            return 0xD6A5FF;
        }

        if (payload.recognitionCommitted()) {
            return payload.recognitionColor();
        }

        return switch (payload.eligibilityStatusId()) {
            case "ready" -> 0x71E0B8;
            case "not_enough_level" -> 0xFFD36A;
            case "already_named" -> 0xFF8A8A;
            default -> 0x9FA7B3;
        };
    }

    private static int stageColor(
            String stage
    ) {
        return switch (safeText(stage)) {
            case "Defining", "Dominant" -> 0xFFD36A;
            case "Strong", "Established" -> 0x71E0B8;
            case "Developing" -> 0x73DCE8;
            case "Faint" -> 0xA98CFF;
            default -> 0x777E89;
        };
    }

    private static int benefitsStateColor(
            String stateId
    ) {
        return switch (
                stateId == null
                        ? ""
                        : stateId
        ) {
            case "invalid" -> 0xFF8A8A;
            case "future_profile" -> 0x73DCE8;
            case "synchronizing" -> 0xFFD36A;
            default -> 0xA9AFB8;
        };
    }

    private static String formatPercent(
            double fraction
    ) {
        double safe = Double.isFinite(fraction)
                ? Math.max(
                0.0D,
                fraction
        )
                : 0.0D;

        return String.format(
                Locale.US,
                "+%.1f%%",
                safe * 100.0D
        );
    }

    private static String formatDecimal(
            double value
    ) {
        double safe = Double.isFinite(value)
                ? Math.max(
                0.0D,
                value
        )
                : 0.0D;

        return String.format(
                Locale.US,
                "%.2f",
                safe
        );
    }

    private static double clampProgress(
            double value
    ) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        value
                )
        );
    }

    private static String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    private static void drawBorderedPanel(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int right,
            int bottom,
            int borderColor,
            int fillColor,
            int borderThickness
    ) {
        if (right <= left || bottom <= top) {
            return;
        }

        int safeThickness = Math.max(
                1,
                borderThickness
        );

        guiGraphics.fill(
                left,
                top,
                right,
                bottom,
                borderColor
        );

        if (right - left
                <= safeThickness * 2
                || bottom - top
                <= safeThickness * 2) {
            return;
        }

        guiGraphics.fill(
                left + safeThickness,
                top + safeThickness,
                right - safeThickness,
                bottom - safeThickness,
                fillColor
        );
    }

    private static int opaque(
            int rgb
    ) {
        return 0xFF000000
                | rgb
                & 0xFFFFFF;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum View {
        OVERVIEW,
        PATHS,
        BENEFITS
    }

    private record OverviewLayout(
            PanelRect statusPanel,
            PanelRect guidancePanel,
            int columns,
            int cardWidth,
            int cardHeight,
            int visibleRows,
            int totalRows
    ) {
    }

    private record PanelRect(
            int left,
            int top,
            int width,
            int height
    ) {

        private int right() {
            return left
                    + Math.max(
                    0,
                    width
            );
        }

        private int bottom() {
            return top
                    + Math.max(
                    0,
                    height
            );
        }

        private boolean contains(
                double x,
                double y
        ) {
            return x >= left
                    && x < right()
                    && y >= top
                    && y < bottom();
        }
    }
}
