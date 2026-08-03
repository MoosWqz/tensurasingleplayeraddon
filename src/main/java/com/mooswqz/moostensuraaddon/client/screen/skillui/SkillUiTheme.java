package com.mooswqz.moostensuraaddon.client.screen.skillui;

/**
 * Shared colour contract for skill-facing screens.
 */
public record SkillUiTheme(
        int backgroundColor,
        int panelFillColor,
        int panelBorderColor,
        int accentColor,
        int secondaryAccentColor,
        int primaryTextColor,
        int secondaryTextColor,
        int mutedTextColor,
        int disabledTextColor,
        int rowFillColor,
        int hoveredRowFillColor,
        int selectedRowFillColor,
        int categoryFillColor,
        int scrollbarTrackColor,
        int scrollbarThumbColor,
        int successColor,
        int warningColor,
        int dangerColor
) {

    public static final SkillUiTheme GRANTER = new SkillUiTheme(
            0xFF09100E,
            0xF0121816,
            0xFF355246,
            0xFF75D6A8,
            0xFFB8E7D1,
            0xFFF4FFF9,
            0xFFB8C8C0,
            0xFF84948C,
            0xFF66726C,
            0xF0141C18,
            0xF0202D27,
            0xF02A4638,
            0xF018241F,
            0xFF25332D,
            0xFF75D6A8,
            0xFF7BE0B2,
            0xFFFFD36A,
            0xFFFF8A8A
    );

    public static final SkillUiTheme BENEVOLENT = new SkillUiTheme(
            0xFF100E08,
            0xF018160F,
            0xFF5B4A24,
            0xFFFFD36A,
            0xFFFFF0B5,
            0xFFFFFDF4,
            0xFFD8CCAA,
            0xFFA59772,
            0xFF71684F,
            0xF01C1911,
            0xF02B2516,
            0xF0483A19,
            0xF0252014,
            0xFF3B321D,
            0xFFFFD36A,
            0xFF8DE0B5,
            0xFFFFC85A,
            0xFFFF8A8A
    );

    public static final SkillUiTheme GOVERNANCE = new SkillUiTheme(
            0xFF08050D,
            0xF0110A19,
            0xFF3C2452,
            0xFF7436A6,
            0xFFB88ADA,
            0xFFFBF7FF,
            0xFFC5B1D4,
            0xFF8A7898,
            0xFF655A6D,
            0xF0140C1D,
            0xF020122D,
            0xF039164F,
            0xF01A1024,
            0xFF291A35,
            0xFF7436A6,
            0xFF78D9B0,
            0xFFFFC85A,
            0xFFFF7085
    );

    public static final SkillUiTheme SEIZE = new SkillUiTheme(
            GOVERNANCE.backgroundColor(),
            GOVERNANCE.panelFillColor(),
            0xFF66303A,
            0xFFFF5D72,
            0xFF7436A6,
            GOVERNANCE.primaryTextColor(),
            GOVERNANCE.secondaryTextColor(),
            GOVERNANCE.mutedTextColor(),
            GOVERNANCE.disabledTextColor(),
            GOVERNANCE.rowFillColor(),
            0xF0321B24,
            0xF0532430,
            0xF0291720,
            0xFF3D252D,
            0xFFFF5D72,
            GOVERNANCE.successColor(),
            0xFFFFC85A,
            0xFFFF5D72
    );

    public SkillUiTheme withAccents(
            int newAccentColor,
            int newSecondaryAccentColor
    ) {
        return new SkillUiTheme(
                backgroundColor,
                panelFillColor,
                panelBorderColor,
                opaque(newAccentColor),
                opaque(newSecondaryAccentColor),
                primaryTextColor,
                secondaryTextColor,
                mutedTextColor,
                disabledTextColor,
                rowFillColor,
                hoveredRowFillColor,
                selectedRowFillColor,
                categoryFillColor,
                scrollbarTrackColor,
                opaque(newAccentColor),
                successColor,
                warningColor,
                dangerColor
        );
    }

    public static int opaque(
            int color
    ) {
        return 0xFF000000
                | color
                & 0x00FFFFFF;
    }
}