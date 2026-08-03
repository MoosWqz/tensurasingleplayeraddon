package com.mooswqz.moostensuraaddon.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class AuthorityText {

    private static final String PREFIX =
            "message.moostensuraaddon.authority.";

    private AuthorityText() {
    }

    public static MutableComponent component(
            String key,
            Object... arguments
    ) {
        return Component.translatable(
                PREFIX + cleanKey(key),
                arguments == null ? new Object[0] : arguments
        );
    }

    public static String string(
            String key,
            Object... arguments
    ) {
        return component(key, arguments).getString();
    }

    private static String cleanKey(
            String key
    ) {
        return key == null || key.isBlank()
                ? "unknown"
                : key.trim();
    }
}