package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class RecognitionDisplayNameService {

    public static final int MAX_TITLE_LENGTH =
            64;

    public static final int MAX_NATIVE_NAME_LENGTH =
            64;

    private static final int FALLBACK_COLOR =
            0x5DD9E8;

    private RecognitionDisplayNameService() {
    }

    /**
     * Returns the complete, server-authoritative recognition display state.
     *
     * The stored committed paths are used instead of recalculating the
     * player's current alignment. Therefore, the recognition color remains
     * fixed after the naming ritual.
     */
    public static Optional<VisibleRecognitionName>
    getVisibleRecognition(
            ServerPlayer player
    ) {
        if (player == null) {
            return Optional.empty();
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        return getVisibleRecognition(data);
    }

    public static Optional<VisibleRecognitionName>
    getVisibleRecognition(
            RecognitionData data
    ) {
        if (data == null) {
            return Optional.empty();
        }

        boolean committed =
                data.getFlag(
                        RecognitionStatKeys.NAMING_COMMITTED
                );

        boolean revealPending =
                data.getFlag(
                        RecognitionStatKeys.REVEAL_PENDING
                );

        /*
         * Never reveal the recognition name or its styling before the ritual
         * has been successfully presented.
         */
        if (!committed || revealPending) {
            return Optional.empty();
        }

        String title =
                sanitizeTitle(
                        data.getString(
                                RecognitionStatKeys.BESTOWED_TITLE
                        )
                );

        if (title.isBlank()) {
            return Optional.empty();
        }

        RecognitionPath primaryPath =
                findPathById(
                        data.getString(
                                RecognitionStatKeys.PRIMARY_PATH
                        )
                );

        RecognitionPath secondaryPath =
                findPathById(
                        data.getString(
                                RecognitionStatKeys.SECONDARY_PATH
                        )
                );

        boolean pure =
                data.getFlag(
                        RecognitionStatKeys.PURE_RECOGNITION
                );

        int rgbColor =
                RecognitionNameColorService
                        .getRecognitionColor(
                                primaryPath,
                                secondaryPath,
                                pure
                        );

        return Optional.of(
                new VisibleRecognitionName(
                        title,
                        rgbColor,
                        pure
                )
        );
    }

    /**
     * Retained for callers that only need the visible title text.
     */
    public static Optional<String> getVisibleTitle(
            ServerPlayer player
    ) {
        return getVisibleRecognition(player)
                .map(
                        VisibleRecognitionName::title
                );
    }

    public static Optional<String> getVisibleTitle(
            RecognitionData data
    ) {
        return getVisibleRecognition(data)
                .map(
                        VisibleRecognitionName::title
                );
    }

    /**
     * Backwards-compatible overload using the neutral fallback color.
     */
    public static MutableComponent appendTitle(
            Component existingDisplayName,
            String rawTitle
    ) {
        return appendTitle(
                existingDisplayName,
                rawTitle,
                FALLBACK_COLOR,
                false
        );
    }

    /**
     * Builds one uniformly styled recognition display name.
     *
     * The existing visible text is rebuilt as one component so an explicit
     * white color on the original username cannot override the recognition
     * color.
     */
    public static MutableComponent appendTitle(
            Component existingDisplayName,
            String rawTitle,
            int rgbColor,
            boolean bold
    ) {
        String existingText =
                existingDisplayName == null
                        ? ""
                        : normalizeWhitespace(
                        existingDisplayName.getString()
                );

        String title =
                sanitizeTitle(rawTitle);

        if (title.isBlank()) {
            return existingDisplayName == null
                    ? Component.empty()
                    : existingDisplayName.copy();
        }

        String expectedSuffix =
                " " + title;

        /*
         * Display names can be refreshed repeatedly. Remove an already
         * appended title before reconstructing the complete component.
         */
        if (existingText.endsWith(
                expectedSuffix
        )) {
            existingText =
                    existingText.substring(
                                    0,
                                    existingText.length()
                                            - expectedSuffix.length()
                            )
                            .trim();
        }

        String completeName =
                existingText.isBlank()
                        ? title
                        : existingText
                          + " "
                          + title;

        int safeColor =
                rgbColor
                        & 0xFFFFFF;

        MutableComponent result =
                Component.literal(
                                completeName
                        )
                        .withColor(
                                safeColor
                        );

        if (bold) {
            result.withStyle(
                    ChatFormatting.BOLD
            );
        }

        return result;
    }

    /**
     * Creates the complete native Tensura name used by Tensura's menus.
     *
     * Tensura stores this value as plain text, so the RGB formatting applies
     * to Minecraft display components but not to Tensura's stored String.
     */
    public static String buildNativeTensuraName(
            String accountName,
            String rawTitle
    ) {
        String safeAccountName =
                sanitizeAccountName(
                        accountName
                );

        String safeTitle =
                sanitizeTitle(
                        rawTitle
                );

        String result;

        if (safeAccountName.isBlank()) {
            result = safeTitle;
        } else if (safeTitle.isBlank()) {
            result = safeAccountName;
        } else {
            result =
                    safeAccountName
                            + " "
                            + safeTitle;
        }

        return truncate(
                result,
                MAX_NATIVE_NAME_LENGTH
        );
    }

    public static String sanitizeTitle(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        for (int index = 0;
             index < value.length();
             index++) {

            char character =
                    value.charAt(index);

            /*
             * Remove legacy formatting injection, control characters and
             * multiline content before the value reaches another client.
             */
            if (character == '\u00A7') {
                continue;
            }

            if (Character.isISOControl(
                    character
            )) {
                continue;
            }

            result.append(
                    character
            );

            if (result.length()
                    >= MAX_TITLE_LENGTH) {

                break;
            }
        }

        return normalizeWhitespace(
                result.toString()
        );
    }

    private static RecognitionPath findPathById(
            String rawPathId
    ) {
        if (rawPathId == null
                || rawPathId.isBlank()) {

            return null;
        }

        String normalized =
                rawPathId.trim();

        for (RecognitionPath path :
                RecognitionPath.values()) {

            if (path.getId()
                    .equalsIgnoreCase(
                            normalized
                    )) {

                return path;
            }
        }

        return null;
    }

    private static String sanitizeAccountName(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        for (int index = 0;
             index < value.length();
             index++) {

            char character =
                    value.charAt(index);

            if (character == '\u00A7'
                    || Character.isISOControl(
                    character
            )) {
                continue;
            }

            result.append(
                    character
            );
        }

        return normalizeWhitespace(
                result.toString()
        );
    }

    private static String normalizeWhitespace(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        boolean previousWasWhitespace =
                false;

        for (int index = 0;
             index < value.length();
             index++) {

            char character =
                    value.charAt(index);

            if (Character.isWhitespace(
                    character
            )) {
                if (!previousWasWhitespace
                        && !result.isEmpty()) {

                    result.append(' ');
                }

                previousWasWhitespace = true;
                continue;
            }

            result.append(
                    character
            );

            previousWasWhitespace = false;
        }

        return result.toString()
                .trim();
    }

    private static String truncate(
            String value,
            int maximumLength
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }

        int safeMaximum =
                Math.max(
                        1,
                        maximumLength
                );

        if (value.length()
                <= safeMaximum) {

            return value;
        }

        return value.substring(
                        0,
                        safeMaximum
                )
                .trim();
    }

    public record VisibleRecognitionName(
            String title,
            int rgbColor,
            boolean bold
    ) {

        public VisibleRecognitionName {
            title =
                    RecognitionDisplayNameService
                            .sanitizeTitle(
                                    title
                            );

            rgbColor =
                    rgbColor
                            & 0xFFFFFF;
        }
    }
}