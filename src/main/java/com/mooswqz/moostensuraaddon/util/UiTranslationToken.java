package com.mooswqz.moostensuraaddon.util;

import net.minecraft.network.chat.Component;

public final class UiTranslationToken {

    private static final String PREFIX =
            "@moostensura:translation:";

    private UiTranslationToken() {
    }

    public static String encode(
            String translationKey
    ) {
        if (translationKey == null || translationKey.isBlank()) {
            return "";
        }

        return PREFIX + translationKey.trim();
    }

    public static boolean isEncoded(
            String value
    ) {
        return value != null && value.startsWith(PREFIX);
    }

    public static Component toComponent(
            String value
    ) {
        if (!isEncoded(value)) {
            return Component.literal(value == null ? "" : value);
        }

        String translationKey = value.substring(PREFIX.length()).trim();

        return translationKey.isBlank()
                ? Component.empty()
                : Component.translatable(translationKey);
    }
}