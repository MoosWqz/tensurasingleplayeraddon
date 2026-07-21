package com.mooswqz.moostensuraaddon.client.screen;

import com.mooswqz.moostensuraaddon.network
        .OpenRecognitionProgressScreenPayload;
import com.mooswqz.moostensuraaddon.network
        .RequestRecognitionProgressScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class RecognitionProgressScreen extends Screen {

    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private static final int MATRIX_TOP = 60;
    private static final int CELL_HEIGHT = 31;
    private static final int CELL_GAP = 3;

    private static final int GUIDANCE_TOP = 58;
    private static final int GUIDANCE_CARD_HEIGHT = 45;
    private static final int GUIDANCE_GAP = 4;

    private static final long CLIENT_REFRESH_COOLDOWN_NANOS =
            TimeUnit.SECONDS.toNanos(2L);

    private static final long NOTICE_DURATION_NANOS =
            TimeUnit.SECONDS.toNanos(3L);

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

    private OpenRecognitionProgressScreenPayload payload;

    private Button viewButton;
    private Button refreshButton;
    private Button closeButton;
    private Button debugButton;

    private boolean guidanceView;
    private boolean debugDetailsVisible;

    private long lastRefreshRequestNanos;
    private long noticeExpiresNanos;

    private String noticeText = "";

    private OpenRecognitionProgressScreenPayload.PathEntry
            hoveredPath;

    private OpenRecognitionProgressScreenPayload.GuidanceEntry
            hoveredGuidance;

    public RecognitionProgressScreen(
            OpenRecognitionProgressScreenPayload payload
    ) {
        super(Component.literal("Soul Recognition"));

        this.payload = payload;
    }

    /**
     * Applies a server refresh without recreating the screen.
     */
    public void applyPayload(
            OpenRecognitionProgressScreenPayload newPayload
    ) {
        if (newPayload == null) {
            return;
        }

        this.payload = newPayload;

        if (!newPayload.debugDetailsAvailable()) {
            this.debugDetailsVisible = false;
        }

        setNotice("Recognition data updated.");
        updateButtonStates();
    }

    @Override
    protected void init() {
        int buttonY =
                Math.max(
                        0,
                        this.height - 28
                );

        int totalWidth =
                BUTTON_WIDTH * 3
                        + BUTTON_GAP * 2;

        int buttonLeft =
                this.width / 2
                        - totalWidth / 2;

        this.viewButton =
                Button.builder(
                                getViewButtonText(),
                                button -> toggleView()
                        )
                        .pos(
                                buttonLeft,
                                buttonY
                        )
                        .size(
                                BUTTON_WIDTH,
                                BUTTON_HEIGHT
                        )
                        .build();

        this.refreshButton =
                Button.builder(
                                Component.literal("Refresh"),
                                button -> requestRefresh()
                        )
                        .pos(
                                buttonLeft
                                        + BUTTON_WIDTH
                                        + BUTTON_GAP,
                                buttonY
                        )
                        .size(
                                BUTTON_WIDTH,
                                BUTTON_HEIGHT
                        )
                        .build();

        this.closeButton =
                Button.builder(
                                Component.literal("Close"),
                                button -> onClose()
                        )
                        .pos(
                                buttonLeft
                                        + (BUTTON_WIDTH
                                        + BUTTON_GAP) * 2,
                                buttonY
                        )
                        .size(
                                BUTTON_WIDTH,
                                BUTTON_HEIGHT
                        )
                        .build();

        addRenderableWidget(this.viewButton);
        addRenderableWidget(this.refreshButton);
        addRenderableWidget(this.closeButton);

        if (payload != null
                && payload.debugDetailsAvailable()) {

            this.debugButton =
                    Button.builder(
                                    getDebugButtonText(),
                                    button -> toggleDebugDetails()
                            )
                            .pos(
                                    Math.max(
                                            4,
                                            this.width - 86
                                    ),
                                    6
                            )
                            .size(
                                    80,
                                    BUTTON_HEIGHT
                            )
                            .build();

            addRenderableWidget(this.debugButton);
        }

        updateButtonStates();
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonStates();

        if (noticeExpiresNanos != 0L
                && System.nanoTime()
                >= noticeExpiresNanos) {

            noticeText = "";
            noticeExpiresNanos = 0L;
        }
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        /*
         * Do not call Screen#renderBackground or Screen#render here.
         *
         * The working 1.21.1 implementation renders the fully opaque
         * background, custom content and registered buttons in one final
         * sharp pass. Calling the parent render path after the custom content
         * reintroduces the post-process blur that was fixed in Packet 6E.1b.
         */
        guiGraphics.fill(
                0,
                0,
                this.width,
                this.height,
                0xFF0D0F14
        );

        hoveredPath = null;
        hoveredGuidance = null;

        renderHeader(guiGraphics);

        int contentBottom;

        if (guidanceView) {
            contentBottom = renderGuidanceView(
                    guiGraphics,
                    mouseX,
                    mouseY
            );
        } else {
            contentBottom = renderPathsView(
                    guiGraphics,
                    mouseX,
                    mouseY
            );
        }

        renderStatusPanel(
                guiGraphics,
                contentBottom
        );

        renderNotice(guiGraphics);
        renderButtons(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        renderHoveredTooltip(
                guiGraphics,
                mouseX,
                mouseY
        );
    }

    private void renderHeader(
            GuiGraphics guiGraphics
    ) {
        int centerX =
                this.width / 2;

        drawCenteredText(
                guiGraphics,
                this.title.copy()
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                centerX,
                10,
                0xFFFFFF
        );

        if (payload == null) {
            drawCenteredText(
                    guiGraphics,
                    "No recognition data received.",
                    centerX,
                    28,
                    0xFF8A8A
            );
            return;
        }

        MutableComponent identity =
                Component.literal(
                                payload.identityLine()
                        )
                        .withColor(
                                payload.recognitionColor()
                        );

        if (payload.recognitionBold()) {
            identity.withStyle(
                    ChatFormatting.BOLD
            );
        }

        drawCenteredText(
                guiGraphics,
                identity,
                centerX,
                25,
                payload.recognitionColor()
        );

        MutableComponent pathSummary =
                Component.literal(
                                payload.pathSummary()
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        );

        if (payload.revealPending()) {
            pathSummary.append(
                    Component.literal("  •  PENDING")
                            .withStyle(
                                    ChatFormatting.LIGHT_PURPLE,
                                    ChatFormatting.BOLD
                            )
            );
        } else if (payload.recognitionCommitted()) {
            pathSummary.append(
                    Component.literal("  •  LOCKED")
                            .withStyle(
                                    ChatFormatting.GOLD,
                                    ChatFormatting.BOLD
                            )
            );
        }

        drawCenteredText(
                guiGraphics,
                pathSummary,
                centerX,
                39,
                0xA0A0A0
        );
    }

    private int renderPathsView(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {
        MatrixLayout layout =
                createMatrixLayout();

        renderColumnLabels(
                guiGraphics,
                layout
        );

        renderPathMatrix(
                guiGraphics,
                layout,
                mouseX,
                mouseY
        );

        return layout.matrixBottom();
    }

    private MatrixLayout createMatrixLayout() {
        int matrixWidth =
                Math.max(
                        180,
                        Math.min(
                                390,
                                this.width - 38
                        )
                );

        int cellWidth =
                Math.max(
                        50,
                        (
                                matrixWidth
                                        - CELL_GAP * 2
                        ) / 3
                );

        int realMatrixWidth =
                cellWidth * 3
                        + CELL_GAP * 2;

        int matrixLeft =
                this.width / 2
                        - realMatrixWidth / 2;

        int matrixBottom =
                MATRIX_TOP
                        + CELL_HEIGHT * 3
                        + CELL_GAP * 2;

        return new MatrixLayout(
                matrixLeft,
                realMatrixWidth,
                cellWidth,
                matrixBottom
        );
    }

    private void renderColumnLabels(
            GuiGraphics guiGraphics,
            MatrixLayout layout
    ) {
        for (int column = 0;
             column < COLUMN_LABELS.length;
             column++) {

            int cellLeft =
                    layout.matrixLeft()
                            + column
                            * (
                            layout.cellWidth()
                                    + CELL_GAP
                    );

            drawCenteredText(
                    guiGraphics,
                    COLUMN_LABELS[column],
                    cellLeft
                            + layout.cellWidth() / 2,
                    50,
                    0xA0A0A0
            );
        }
    }

    private void renderPathMatrix(
            GuiGraphics guiGraphics,
            MatrixLayout layout,
            int mouseX,
            int mouseY
    ) {
        for (int index = 0;
             index < MATRIX_PATH_IDS.length;
             index++) {

            int row = index / 3;
            int column = index % 3;

            int cellLeft =
                    layout.matrixLeft()
                            + column
                            * (
                            layout.cellWidth()
                                    + CELL_GAP
                    );

            int cellTop =
                    MATRIX_TOP
                            + row
                            * (
                            CELL_HEIGHT
                                    + CELL_GAP
                    );

            if (column == 0) {
                drawCenteredText(
                        guiGraphics,
                        ROW_LABELS[row],
                        layout.matrixLeft() - 9,
                        cellTop + 11,
                        0x707070
                );
            }

            OpenRecognitionProgressScreenPayload.PathEntry path =
                    payload == null
                            ? null
                            : payload.findPath(
                            MATRIX_PATH_IDS[index]
                    );

            if (path == null) {
                renderMissingCell(
                        guiGraphics,
                        cellLeft,
                        cellTop,
                        layout.cellWidth()
                );

                continue;
            }

            renderPathCell(
                    guiGraphics,
                    path,
                    cellLeft,
                    cellTop,
                    layout.cellWidth()
            );

            if (isMouseInside(
                    mouseX,
                    mouseY,
                    cellLeft,
                    cellTop,
                    cellLeft + layout.cellWidth(),
                    cellTop + CELL_HEIGHT
            )) {
                hoveredPath = path;
            }
        }
    }

    private void renderMissingCell(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int width
    ) {
        drawBorderedPanel(
                guiGraphics,
                left,
                top,
                left + width,
                top + CELL_HEIGHT,
                0xFF333333,
                0xCC151515,
                1
        );

        drawCenteredText(
                guiGraphics,
                "Unavailable",
                left + width / 2,
                top + 11,
                0x707070
        );
    }

    private void renderPathCell(
            GuiGraphics guiGraphics,
            OpenRecognitionProgressScreenPayload.PathEntry path,
            int left,
            int top,
            int width
    ) {
        int borderColor =
                path.primary()
                        || path.secondary()
                        ? opaque(path.color())
                        : 0xFF353535;

        int borderThickness =
                path.primary()
                        ? 2
                        : 1;

        int fillColor =
                path.primary()
                        ? 0xE01A201A
                        : path.secondary()
                          ? 0xDC18181E
                          : 0xD0161616;

        drawBorderedPanel(
                guiGraphics,
                left,
                top,
                left + width,
                top + CELL_HEIGHT,
                borderColor,
                fillColor,
                borderThickness
        );

        if (path.primary()
                && payload.pureRecognition()) {
            drawOutline(
                    guiGraphics,
                    left + 3,
                    top + 3,
                    left + width - 3,
                    top + CELL_HEIGHT - 3,
                    0xFFFFD36A
            );
        }

        MutableComponent displayName =
                Component.literal(
                                path.displayName()
                        )
                        .withColor(
                                path.color()
                        );

        if (path.primary()
                && payload.pureRecognition()) {
            displayName.withStyle(
                    ChatFormatting.BOLD
            );
        }

        drawCenteredText(
                guiGraphics,
                displayName,
                left + width / 2,
                top + 4,
                path.color()
        );

        drawCenteredText(
                guiGraphics,
                path.stageLabel(),
                left + width / 2,
                top + 15,
                path.primary()
                        || path.secondary()
                        ? 0xD0D0D0
                        : 0x858585
        );

        if (path.primary()
                || path.secondary()) {
            guiGraphics.drawString(
                    this.font,
                    path.primary()
                            ? "★"
                            : "◆",
                    left + width - 9,
                    top + 3,
                    opaque(path.color()),
                    false
            );
        }

        renderProgressBar(
                guiGraphics,
                path.progress(),
                path.color(),
                left + 4,
                top + CELL_HEIGHT - 6,
                Math.max(1, width - 8),
                true
        );
    }

    private int renderGuidanceView(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {
        if (payload == null
                || payload.guidanceEntries().isEmpty()) {

            drawCenteredText(
                    guiGraphics,
                    "No guidance data is available.",
                    this.width / 2,
                    78,
                    0xA0A0A0
            );

            return 100;
        }

        int contentWidth =
                Math.max(
                        220,
                        Math.min(
                                500,
                                this.width - 32
                        )
                );

        int cardWidth =
                Math.max(
                        100,
                        (contentWidth
                                - GUIDANCE_GAP) / 2
                );

        int realWidth =
                cardWidth * 2
                        + GUIDANCE_GAP;

        int contentLeft =
                this.width / 2
                        - realWidth / 2;

        List<OpenRecognitionProgressScreenPayload.GuidanceEntry>
                guidanceEntries =
                payload.guidanceEntries();

        for (int index = 0;
             index < guidanceEntries.size();
             index++) {

            OpenRecognitionProgressScreenPayload.GuidanceEntry
                    entry =
                    guidanceEntries.get(index);

            int row = index / 2;
            int column = index % 2;

            int left =
                    contentLeft
                            + column
                            * (
                            cardWidth
                                    + GUIDANCE_GAP
                    );

            int top =
                    GUIDANCE_TOP
                            + row
                            * (
                            GUIDANCE_CARD_HEIGHT
                                    + GUIDANCE_GAP
                    );

            renderGuidanceCard(
                    guiGraphics,
                    entry,
                    left,
                    top,
                    cardWidth
            );

            if (isMouseInside(
                    mouseX,
                    mouseY,
                    left,
                    top,
                    left + cardWidth,
                    top + GUIDANCE_CARD_HEIGHT
            )) {
                hoveredGuidance = entry;
            }
        }

        int rows =
                (guidanceEntries.size() + 1) / 2;

        return GUIDANCE_TOP
                + rows * GUIDANCE_CARD_HEIGHT
                + Math.max(0, rows - 1)
                * GUIDANCE_GAP;
    }

    private void renderGuidanceCard(
            GuiGraphics guiGraphics,
            OpenRecognitionProgressScreenPayload.GuidanceEntry entry,
            int left,
            int top,
            int width
    ) {
        drawBorderedPanel(
                guiGraphics,
                left,
                top,
                left + width,
                top + GUIDANCE_CARD_HEIGHT,
                opaque(entry.color()),
                0xD0161616,
                1
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(entry.displayName())
                        .withColor(entry.color()),
                left + 5,
                top + 5,
                opaque(entry.color()),
                false
        );

        int stageWidth =
                this.font.width(entry.stageLabel());

        guiGraphics.drawString(
                this.font,
                entry.stageLabel(),
                left + width - stageWidth - 5,
                top + 5,
                0xFFC0C0C0,
                false
        );

        List<String> guidanceLines =
                wrapText(
                        entry.guidanceText(),
                        Math.max(
                                40,
                                width - 10
                        ),
                        2
                );

        for (int index = 0;
             index < guidanceLines.size();
             index++) {

            guiGraphics.drawString(
                    this.font,
                    guidanceLines.get(index),
                    left + 5,
                    top + 17 + index * 9,
                    0xFF909090,
                    false
            );
        }

        renderProgressBar(
                guiGraphics,
                entry.progress(),
                entry.color(),
                left + 5,
                top + GUIDANCE_CARD_HEIGHT - 6,
                Math.max(1, width - 10),
                false
        );
    }

    private void renderProgressBar(
            GuiGraphics guiGraphics,
            double progress,
            int color,
            int left,
            int top,
            int width,
            boolean showEstablishedMarker
    ) {
        guiGraphics.fill(
                left,
                top,
                left + width,
                top + 3,
                0xFF272727
        );

        if (showEstablishedMarker) {
            int establishedMarker =
                    left + width / 2;

            guiGraphics.fill(
                    establishedMarker,
                    top,
                    establishedMarker + 1,
                    top + 3,
                    0xFF707070
            );
        }

        int filledWidth =
                (int) Math.round(
                        width
                                * Math.max(
                                0.0D,
                                Math.min(
                                        1.0D,
                                        progress
                                )
                        )
                );

        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.fill(
                left,
                top,
                left + Math.min(
                        width,
                        filledWidth
                ),
                top + 3,
                opaque(color)
        );
    }

    private void renderStatusPanel(
            GuiGraphics guiGraphics,
            int contentBottom
    ) {
        if (payload == null) {
            return;
        }

        int panelWidth =
                Math.max(
                        200,
                        Math.min(
                                500,
                                this.width - 24
                        )
                );

        int panelLeft =
                this.width / 2
                        - panelWidth / 2;

        int panelRight =
                panelLeft + panelWidth;

        int panelTop =
                contentBottom + 6;

        int buttonTop =
                Math.max(
                        0,
                        this.height - 28
                );

        int reservedNoticeSpace =
                noticeText.isBlank()
                        ? 5
                        : 16;

        int panelBottom =
                Math.min(
                        panelTop + 44,
                        buttonTop
                                - reservedNoticeSpace
                );

        if (panelBottom <= panelTop + 10) {
            return;
        }

        int statusColor =
                getStatusColor();

        drawBorderedPanel(
                guiGraphics,
                panelLeft,
                panelTop,
                panelRight,
                panelBottom,
                opaque(statusColor),
                0xD0141414,
                1
        );

        drawCenteredText(
                guiGraphics,
                Component.literal(
                                payload.statusHeading()
                        )
                        .withColor(statusColor),
                this.width / 2,
                panelTop + 5,
                statusColor
        );

        if (panelBottom >= panelTop + 28) {
            drawCenteredText(
                    guiGraphics,
                    payload.statusDetail(),
                    this.width / 2,
                    panelTop + 17,
                    0xB0B0B0
            );
        }

        if (panelBottom >= panelTop + 40) {
            drawCenteredText(
                    guiGraphics,
                    payload.levelLine(),
                    this.width / 2,
                    panelTop + 29,
                    0x808080
            );
        }
    }

    private void renderNotice(
            GuiGraphics guiGraphics
    ) {
        if (noticeText.isBlank()) {
            return;
        }

        int buttonTop =
                Math.max(
                        0,
                        this.height - 28
                );

        drawCenteredText(
                guiGraphics,
                noticeText,
                this.width / 2,
                Math.max(2, buttonTop - 11),
                0x9FA7B3
        );
    }

    private void renderButtons(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderButton(
                viewButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        renderButton(
                refreshButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        renderButton(
                closeButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        renderButton(
                debugButton,
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private static void renderButton(
            Button button,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (button == null) {
            return;
        }

        button.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private void renderHoveredTooltip(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {
        if (hoveredPath != null) {
            renderTooltipPanel(
                    guiGraphics,
                    buildPathTooltip(hoveredPath),
                    mouseX,
                    mouseY,
                    hoveredPath.color()
            );
            return;
        }

        if (hoveredGuidance != null) {
            renderTooltipPanel(
                    guiGraphics,
                    buildGuidanceTooltip(
                            hoveredGuidance
                    ),
                    mouseX,
                    mouseY,
                    hoveredGuidance.color()
            );
        }
    }

    private List<TooltipLine> buildPathTooltip(
            OpenRecognitionProgressScreenPayload.PathEntry path
    ) {
        List<TooltipLine> lines =
                new ArrayList<>();

        lines.add(
                new TooltipLine(
                        path.displayName(),
                        path.color()
                )
        );

        for (String descriptionLine :
                wrapText(
                        getPathDescription(path.pathId()),
                        210,
                        3
                )) {

            lines.add(
                    new TooltipLine(
                            descriptionLine,
                            0xB0B0B0
                    )
            );
        }

        lines.add(
                new TooltipLine(
                        "Stage: " + path.stageLabel(),
                        0xD0D0D0
                )
        );

        if (path.primary()) {
            lines.add(
                    new TooltipLine(
                            payload.pureRecognition()
                                    ? "Primary path • Pure recognition"
                                    : "Primary recognition path",
                            0xFFD36A
                    )
            );
        } else if (path.secondary()) {
            lines.add(
                    new TooltipLine(
                            "Secondary recognition path",
                            0xA9B8FF
                    )
            );
        }

        if (payload.recognitionCommitted()
                && (path.primary()
                || path.secondary())) {

            lines.add(
                    new TooltipLine(
                            "Locked for this incarnation",
                            0xFFD36A
                    )
            );
        }

        if (payload.debugDetailsAvailable()
                && debugDetailsVisible) {

            lines.add(
                    new TooltipLine(
                            "",
                            0xFFFFFF
                    )
            );

            lines.add(
                    new TooltipLine(
                            "Final score: "
                                    + format(path.finalScore()),
                            0x7EE7FF
                    )
            );

            lines.add(
                    new TooltipLine(
                            "Raw affinity: "
                                    + format(path.rawScore()),
                            0x7EE7FF
                    )
            );

            lines.add(
                    new TooltipLine(
                            "Identity contribution: "
                                    + format(path.identityBoost()),
                            0x7EE7FF
                    )
            );

            lines.add(
                    new TooltipLine(
                            "Established / Pure: "
                                    + format(
                                    payload.establishedThreshold()
                            )
                                    + " / "
                                    + format(
                                    payload.pureThreshold()
                            ),
                            0x9FA7B3
                    )
            );

            lines.add(
                    new TooltipLine(
                            "Raw Pure requirement: "
                                    + format(
                                    payload.rawPureThreshold()
                            ),
                            0x9FA7B3
                    )
            );
        }

        return List.copyOf(lines);
    }

    private List<TooltipLine> buildGuidanceTooltip(
            OpenRecognitionProgressScreenPayload.GuidanceEntry entry
    ) {
        List<TooltipLine> lines =
                new ArrayList<>();

        lines.add(
                new TooltipLine(
                        entry.displayName(),
                        entry.color()
                )
        );

        for (String guidanceLine :
                wrapText(
                        entry.guidanceText(),
                        220,
                        4
                )) {

            lines.add(
                    new TooltipLine(
                            guidanceLine,
                            0xB0B0B0
                    )
            );
        }

        lines.add(
                new TooltipLine(
                        "Stage: " + entry.stageLabel(),
                        0xD0D0D0
                )
        );

        if (payload.debugDetailsAvailable()
                && debugDetailsVisible) {

            lines.add(
                    new TooltipLine(
                            "Debug value: "
                                    + format(entry.debugValue()),
                            0x7EE7FF
                    )
            );
        }

        return List.copyOf(lines);
    }

    private void renderTooltipPanel(
            GuiGraphics guiGraphics,
            List<TooltipLine> lines,
            int mouseX,
            int mouseY,
            int borderColor
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }

        int contentWidth = 0;

        for (TooltipLine line : lines) {
            contentWidth = Math.max(
                    contentWidth,
                    this.font.width(line.text())
            );
        }

        int panelWidth =
                contentWidth + 10;

        int panelHeight =
                lines.size() * 10 + 6;

        int left = mouseX + 10;
        int top = mouseY + 10;

        if (left + panelWidth
                > this.width - 4) {
            left = Math.max(
                    4,
                    mouseX - panelWidth - 10
            );
        }

        if (top + panelHeight
                > this.height - 4) {
            top = Math.max(
                    4,
                    this.height - panelHeight - 4
            );
        }

        drawBorderedPanel(
                guiGraphics,
                left,
                top,
                left + panelWidth,
                top + panelHeight,
                opaque(borderColor),
                0xF0101116,
                1
        );

        for (int index = 0;
             index < lines.size();
             index++) {

            TooltipLine line = lines.get(index);

            guiGraphics.drawString(
                    this.font,
                    line.text(),
                    left + 5,
                    top + 4 + index * 10,
                    opaque(line.color()),
                    false
            );
        }
    }

    private void toggleView() {
        guidanceView = !guidanceView;

        if (viewButton != null) {
            viewButton.setMessage(
                    getViewButtonText()
            );
        }
    }

    private void toggleDebugDetails() {
        if (payload == null
                || !payload.debugDetailsAvailable()) {
            debugDetailsVisible = false;
            return;
        }

        debugDetailsVisible =
                !debugDetailsVisible;

        if (debugButton != null) {
            debugButton.setMessage(
                    getDebugButtonText()
            );
        }
    }

    private void requestRefresh() {
        if (!canRequestRefresh()) {
            setNotice("Refresh is cooling down.");
            return;
        }

        lastRefreshRequestNanos =
                System.nanoTime();

        setNotice("Refresh requested...");
        updateButtonStates();

        PacketDistributor.sendToServer(
                RequestRecognitionProgressScreenPayload
                        .INSTANCE
        );
    }

    private boolean canRequestRefresh() {
        if (lastRefreshRequestNanos == 0L) {
            return true;
        }

        return System.nanoTime()
                - lastRefreshRequestNanos
                >= CLIENT_REFRESH_COOLDOWN_NANOS;
    }

    private void updateButtonStates() {
        if (refreshButton != null) {
            refreshButton.active = canRequestRefresh();
        }

        if (viewButton != null) {
            viewButton.setMessage(
                    getViewButtonText()
            );
        }

        if (debugButton != null) {
            boolean available =
                    payload != null
                            && payload
                            .debugDetailsAvailable();

            debugButton.active = available;
            debugButton.setMessage(
                    getDebugButtonText()
            );
        }
    }

    private Component getViewButtonText() {
        return Component.literal(
                guidanceView
                        ? "Paths"
                        : "Guidance"
        );
    }

    private Component getDebugButtonText() {
        return Component.literal(
                debugDetailsVisible
                        ? "Debug: ON"
                        : "Debug: OFF"
        );
    }

    private void setNotice(
            String text
    ) {
        noticeText = text == null
                ? ""
                : text.trim();

        noticeExpiresNanos =
                noticeText.isBlank()
                        ? 0L
                        : System.nanoTime()
                          + NOTICE_DURATION_NANOS;
    }

    private void drawCenteredText(
            GuiGraphics guiGraphics,
            Component text,
            int centerX,
            int y,
            int color
    ) {
        Component safeText =
                text == null
                        ? Component.empty()
                        : text;

        int left =
                centerX
                        - this.font.width(
                        safeText
                ) / 2;

        guiGraphics.drawString(
                this.font,
                safeText,
                left,
                y,
                opaque(color),
                false
        );
    }

    private void drawCenteredText(
            GuiGraphics guiGraphics,
            String text,
            int centerX,
            int y,
            int color
    ) {
        drawCenteredText(
                guiGraphics,
                Component.literal(
                        text == null
                                ? ""
                                : text
                ),
                centerX,
                y,
                color
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

        return switch (
                payload.eligibilityStatusId()
                ) {
            case "ready" -> 0x71E0B8;
            case "not_enough_level" -> 0xFFD36A;
            case "already_named" -> 0xFF8A8A;
            default -> 0x9FA7B3;
        };
    }

    private List<String> wrapText(
            String text,
            int maximumPixelWidth,
            int maximumLines
    ) {
        if (text == null
                || text.isBlank()
                || maximumLines <= 0) {
            return List.of();
        }

        String[] words =
                text.trim().split("\\s+");

        List<String> lines =
                new ArrayList<>();

        StringBuilder currentLine =
                new StringBuilder();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            String candidate =
                    currentLine.isEmpty()
                            ? word
                            : currentLine
                              + " "
                              + word;

            if (!currentLine.isEmpty()
                    && this.font.width(candidate)
                    > maximumPixelWidth) {

                lines.add(currentLine.toString());
                currentLine.setLength(0);
                currentLine.append(word);

                if (lines.size()
                        >= maximumLines) {
                    break;
                }
            } else {
                if (!currentLine.isEmpty()) {
                    currentLine.append(' ');
                }

                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()
                && lines.size() < maximumLines) {
            lines.add(currentLine.toString());
        }

        return List.copyOf(lines);
    }

    private static String getPathDescription(
            String pathId
    ) {
        if (pathId == null) {
            return "This path has no description.";
        }

        return switch (pathId) {
            case "lawful_good" ->
                    "Protection guided by duty, structure and responsibility.";

            case "neutral_good" ->
                    "Compassion and protection without strict allegiance to order.";

            case "chaotic_good" ->
                    "Freedom used to protect others and reject oppressive control.";

            case "lawful_neutral" ->
                    "Authority, hierarchy and stability without a moral extreme.";

            case "true_neutral" ->
                    "Balance, growth and identity beyond moral or behavioural extremes.";

            case "chaotic_neutral" ->
                    "Independence, self-direction and rejection of imposed limits.";

            case "lawful_evil" ->
                    "Domination and control imposed through disciplined order.";

            case "neutral_evil" ->
                    "Ambition and self-interest unconstrained by duty or chaos.";

            case "chaotic_evil" ->
                    "Destruction, cruelty and freedom pursued without restraint.";

            default ->
                    "A developing expression of this incarnation's identity.";
        };
    }

    private static boolean isMouseInside(
            int mouseX,
            int mouseY,
            int left,
            int top,
            int right,
            int bottom
    ) {
        return mouseX >= left
                && mouseX < right
                && mouseY >= top
                && mouseY < bottom;
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
        int safeThickness =
                Math.max(
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

    private static void drawOutline(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {
        if (right <= left || bottom <= top) {
            return;
        }

        guiGraphics.fill(
                left,
                top,
                right,
                top + 1,
                color
        );

        guiGraphics.fill(
                left,
                bottom - 1,
                right,
                bottom,
                color
        );

        guiGraphics.fill(
                left,
                top,
                left + 1,
                bottom,
                color
        );

        guiGraphics.fill(
                right - 1,
                top,
                right,
                bottom,
                color
        );
    }

    private static int opaque(
            int rgb
    ) {
        return 0xFF000000
                | rgb
                & 0xFFFFFF;
    }

    private static String format(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.1f",
                Double.isFinite(value)
                        ? Math.max(0.0D, value)
                        : 0.0D
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record MatrixLayout(
            int matrixLeft,
            int matrixWidth,
            int cellWidth,
            int matrixBottom
    ) {
    }

    private record TooltipLine(
            String text,
            int color
    ) {

        private TooltipLine {
            text = text == null
                    ? ""
                    : text;

            color &= 0xFFFFFF;
        }
    }
}