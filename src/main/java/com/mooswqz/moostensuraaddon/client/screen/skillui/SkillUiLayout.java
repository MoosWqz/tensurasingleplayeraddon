package com.mooswqz.moostensuraaddon.client.screen.skillui;

/**
 * Responsive screen geometry shared by skill-facing menus.
 */
public record SkillUiLayout(
        Rect panel,
        Rect header,
        Rect filters,
        Rect list,
        Rect details,
        Rect footer,
        boolean compact,
        boolean detailsVisible
) {

    public static SkillUiLayout calculate(
            int screenWidth,
            int screenHeight,
            boolean requestDetailsPanel
    ) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);

        int horizontalMargin = safeWidth < 360
                ? 6
                : 12;
        int verticalMargin = safeHeight < 260
                ? 6
                : 12;

        int panelWidth = Math.min(
                760,
                Math.max(
                        1,
                        safeWidth - horizontalMargin * 2
                )
        );
        int panelHeight = Math.max(
                1,
                safeHeight - verticalMargin * 2
        );

        int panelLeft = safeWidth / 2
                - panelWidth / 2;
        Rect panel = new Rect(
                panelLeft,
                verticalMargin,
                panelWidth,
                panelHeight
        );

        int innerLeft = panel.left() + 8;
        int innerWidth = Math.max(
                1,
                panel.width() - 16
        );

        int headerHeight = panelHeight < 220
                ? 34
                : 44;
        int filterHeight = panelHeight < 220
                ? 20
                : 24;
        int footerHeight = 28;
        int gap = 5;

        Rect header = new Rect(
                innerLeft,
                panel.top() + 7,
                innerWidth,
                headerHeight
        );

        Rect filters = new Rect(
                innerLeft,
                header.bottom() + gap,
                innerWidth,
                filterHeight
        );

        int bodyTop = filters.bottom() + gap;
        int bodyBottom = Math.max(
                bodyTop,
                panel.bottom()
                        - footerHeight
                        - 7
                        - gap
        );
        int bodyHeight = Math.max(
                1,
                bodyBottom - bodyTop
        );

        Rect footer = new Rect(
                innerLeft,
                bodyBottom + gap,
                innerWidth,
                footerHeight
        );

        boolean compact = innerWidth < 560;
        boolean detailsVisible = requestDetailsPanel
                && bodyHeight >= 130
                && innerWidth >= 300;

        Rect list;
        Rect details;

        if (!detailsVisible) {
            list = new Rect(
                    innerLeft,
                    bodyTop,
                    innerWidth,
                    bodyHeight
            );
            details = Rect.empty();
        } else if (!compact) {
            int bodyGap = 6;
            int detailsWidth = Math.max(
                    190,
                    Math.min(
                            260,
                            innerWidth * 38 / 100
                    )
            );
            int listWidth = Math.max(
                    1,
                    innerWidth
                            - detailsWidth
                            - bodyGap
            );

            list = new Rect(
                    innerLeft,
                    bodyTop,
                    listWidth,
                    bodyHeight
            );
            details = new Rect(
                    list.right() + bodyGap,
                    bodyTop,
                    detailsWidth,
                    bodyHeight
            );
        } else {
            int bodyGap = 5;
            int detailsHeight = Math.max(
                    74,
                    Math.min(
                            112,
                            bodyHeight * 36 / 100
                    )
            );
            int listHeight = Math.max(
                    1,
                    bodyHeight
                            - detailsHeight
                            - bodyGap
            );

            list = new Rect(
                    innerLeft,
                    bodyTop,
                    innerWidth,
                    listHeight
            );
            details = new Rect(
                    innerLeft,
                    list.bottom() + bodyGap,
                    innerWidth,
                    detailsHeight
            );
        }

        return new SkillUiLayout(
                panel,
                header,
                filters,
                list,
                details,
                footer,
                compact,
                detailsVisible
        );
    }

    public record Rect(
            int left,
            int top,
            int width,
            int height
    ) {

        public Rect {
            width = Math.max(0, width);
            height = Math.max(0, height);
        }

        public static Rect empty() {
            return new Rect(
                    0,
                    0,
                    0,
                    0
            );
        }

        public int right() {
            return left + width;
        }

        public int bottom() {
            return top + height;
        }

        public int centerX() {
            return left + width / 2;
        }

        public int centerY() {
            return top + height / 2;
        }

        public boolean emptyArea() {
            return width <= 0
                    || height <= 0;
        }

        public boolean contains(
                double x,
                double y
        ) {
            return x >= left
                    && x < right()
                    && y >= top
                    && y < bottom();
        }

        public Rect inset(
                int amount
        ) {
            int safeAmount = Math.max(
                    0,
                    amount
            );

            return new Rect(
                    left + safeAmount,
                    top + safeAmount,
                    Math.max(
                            0,
                            width - safeAmount * 2
                    ),
                    Math.max(
                            0,
                            height - safeAmount * 2
                    )
            );
        }
    }
}