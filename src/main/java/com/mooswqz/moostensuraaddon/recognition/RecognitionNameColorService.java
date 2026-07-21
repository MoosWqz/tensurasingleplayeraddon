package com.mooswqz.moostensuraaddon.recognition;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class RecognitionNameColorService {

    public static final double PRIMARY_COLOR_WEIGHT =
            0.70D;

    public static final double SECONDARY_COLOR_WEIGHT =
            0.30D;

    private static final Map<RecognitionPath, Integer>
            BASE_COLORS =
            createBaseColors();

    private RecognitionNameColorService() {
    }

    public static int getBaseColor(
            RecognitionPath path
    ) {
        if (path == null) {
            return 0x5DD9E8;
        }

        return BASE_COLORS.getOrDefault(
                path,
                0x5DD9E8
        );
    }

    /**
     * Pure recognitions use the exact primary color.
     *
     * Crossed recognitions blend 70% primary with 30% secondary, making the
     * order of the paths visually meaningful.
     */
    public static int getRecognitionColor(
            RecognitionPath primaryPath,
            RecognitionPath secondaryPath,
            boolean pure
    ) {
        int primaryColor =
                getBaseColor(primaryPath);

        if (pure
                || secondaryPath == null
                || secondaryPath == primaryPath) {

            return primaryColor;
        }

        int secondaryColor =
                getBaseColor(secondaryPath);

        return blendRgb(
                primaryColor,
                secondaryColor,
                PRIMARY_COLOR_WEIGHT
        );
    }

    public static MutableComponent createBasePreview(
            RecognitionPath path,
            boolean pure
    ) {
        int color =
                getRecognitionColor(
                        path,
                        null,
                        pure
                );

        String prefix =
                pure
                        ? "Pure "
                        : "";

        MutableComponent component =
                Component.literal(
                                prefix
                                        + getDisplayName(path)
                                        + "  "
                                        + formatHex(color)
                        )
                        .withColor(color);

        if (pure) {
            component.withStyle(
                    ChatFormatting.BOLD
            );
        }

        return component;
    }

    public static MutableComponent createCrossingPreview(
            RecognitionPath primaryPath,
            RecognitionPath secondaryPath
    ) {
        int color =
                getRecognitionColor(
                        primaryPath,
                        secondaryPath,
                        false
                );

        return Component.literal(
                        getDisplayName(primaryPath)
                                + " + "
                                + getDisplayName(secondaryPath)
                                + "  "
                                + formatHex(color)
                )
                .withColor(color);
    }

    public static String getDisplayName(
            RecognitionPath path
    ) {
        if (path == null) {
            return "Unknown";
        }

        String[] parts =
                path.getId()
                        .split("_");

        StringBuilder result =
                new StringBuilder();

        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );

            if (part.length() > 1) {
                result.append(
                        part.substring(1)
                                .toLowerCase(
                                        Locale.ROOT
                                )
                );
            }
        }

        return result.toString();
    }

    public static String formatHex(
            int rgb
    ) {
        return String.format(
                Locale.ROOT,
                "#%06X",
                rgb & 0xFFFFFF
        );
    }

    private static int blendRgb(
            int primaryColor,
            int secondaryColor,
            double primaryWeight
    ) {
        double safePrimaryWeight =
                Math.max(
                        0.0D,
                        Math.min(
                                1.0D,
                                primaryWeight
                        )
                );

        double secondaryWeight =
                1.0D
                        - safePrimaryWeight;

        int primaryRed =
                primaryColor >> 16
                        & 0xFF;

        int primaryGreen =
                primaryColor >> 8
                        & 0xFF;

        int primaryBlue =
                primaryColor
                        & 0xFF;

        int secondaryRed =
                secondaryColor >> 16
                        & 0xFF;

        int secondaryGreen =
                secondaryColor >> 8
                        & 0xFF;

        int secondaryBlue =
                secondaryColor
                        & 0xFF;

        int red =
                clampChannel(
                        (int) Math.round(
                                primaryRed
                                        * safePrimaryWeight
                                        + secondaryRed
                                        * secondaryWeight
                        )
                );

        int green =
                clampChannel(
                        (int) Math.round(
                                primaryGreen
                                        * safePrimaryWeight
                                        + secondaryGreen
                                        * secondaryWeight
                        )
                );

        int blue =
                clampChannel(
                        (int) Math.round(
                                primaryBlue
                                        * safePrimaryWeight
                                        + secondaryBlue
                                        * secondaryWeight
                        )
                );

        return red << 16
                | green << 8
                | blue;
    }

    private static int clampChannel(
            int value
    ) {
        return Math.max(
                0,
                Math.min(
                        255,
                        value
                )
        );
    }

    private static Map<RecognitionPath, Integer>
    createBaseColors() {
        EnumMap<RecognitionPath, Integer> colors =
                new EnumMap<>(
                        RecognitionPath.class
                );

        /*
         * Palette logic:
         *
         * Columns:
         * - Lawful  -> green-based
         * - Neutral -> blue / aqua based
         * - Chaotic -> purple-based
         *
         * Rows:
         * - Good    -> brighter / cleaner
         * - Neutral -> calmer / cooler
         * - Evil    -> red / crimson leaning
         */

        colors.put(
                RecognitionPath.LAWFUL_GOOD,
                0xBFF57A
        ); // bright light green

        colors.put(
                RecognitionPath.NEUTRAL_GOOD,
                0x71E0B8
        ); // calm aqua-green

        colors.put(
                RecognitionPath.CHAOTIC_GOOD,
                0xA98CFF
        ); // lighter purple for chaotic good

        colors.put(
                RecognitionPath.LAWFUL_NEUTRAL,
                0x73D66E
        ); // stable green

        colors.put(
                RecognitionPath.TRUE_NEUTRAL,
                0x5DD9E8
        ); // aqua instead of grey

        colors.put(
                RecognitionPath.CHAOTIC_NEUTRAL,
                0x7F86FF
        ); // blue-purple

        colors.put(
                RecognitionPath.LAWFUL_EVIL,
                0xD9644F
        ); // disciplined ember red

        colors.put(
                RecognitionPath.NEUTRAL_EVIL,
                0xE05276
        ); // ruby-magenta

        colors.put(
                RecognitionPath.CHAOTIC_EVIL,
                0xC03E86
        ); // crimson-purple

        return Map.copyOf(colors);
    }
}