package com.mooswqz.moostensuraaddon.client.screen.skillui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Translation and number-formatting helpers shared by the Skill UI family.
 */
public final class SkillUiText {

    private static final String SCREEN_PREFIX =
            "screen.moostensuraaddon.skill_ui.";
    private static final String MESSAGE_PREFIX =
            "message.moostensuraaddon.";

    private SkillUiText() {
    }

    public static MutableComponent component(
            String key,
            Object... arguments
    ) {
        return Component.translatable(
                SCREEN_PREFIX + cleanKey(key),
                arguments == null ? new Object[0] : arguments
        );
    }

    public static String string(
            String key,
            Object... arguments
    ) {
        return component(key, arguments).getString();
    }

    public static MutableComponent message(
            String key,
            Object... arguments
    ) {
        return Component.translatable(
                MESSAGE_PREFIX + cleanKey(key),
                arguments == null ? new Object[0] : arguments
        );
    }

    public static String messageString(
            String key,
            Object... arguments
    ) {
        return message(key, arguments).getString();
    }

    public static String formatNumber(
            double value
    ) {
        return String.format(
                Locale.US,
                "%,.0f",
                sanitize(value)
        );
    }

    public static String formatDecimal(
            double value,
            int decimalPlaces
    ) {
        int safePlaces = Math.max(
                0,
                Math.min(4, decimalPlaces)
        );

        return String.format(
                Locale.US,
                "%,." + safePlaces + "f",
                sanitize(value)
        );
    }

    public static String formatPercent(
            double fraction
    ) {
        return String.format(
                Locale.US,
                "%.1f%%",
                Math.max(0.0D, Math.min(1.0D, fraction)) * 100.0D
        );
    }

    private static double sanitize(
            double value
    ) {
        return Double.isFinite(value)
                ? Math.max(0.0D, value)
                : 0.0D;
    }

    private static String cleanKey(
            String key
    ) {
        if (key == null || key.isBlank()) {
            return "unknown";
        }

        return key.trim();
    }
}