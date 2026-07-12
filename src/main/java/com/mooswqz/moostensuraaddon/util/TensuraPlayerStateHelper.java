package com.mooswqz.moostensuraaddon.util;

import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

public final class TensuraPlayerStateHelper {
    private static final String[] BOOLEAN_NAMED_METHODS = new String[]{
            "isNamed",
            "isEndowed",
            "hasName",
            "hasNamed",
            "hasTensuraName",
            "hasStoredName",
            "hasGivenName",
            "wasNamed",
            "isNameGiven"
    };

    private static final String[] STRING_NAME_METHODS = new String[]{
            "getName",
            "getTensuraName",
            "getStoredName",
            "getGivenName",
            "getTrueName",
            "getNamedName",
            "getFullName"
    };

    private TensuraPlayerStateHelper() {
    }

    public static boolean isNamedOrEndowed(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return false;
        }

        if (readAnyBooleanFlag(existence)) {
            return true;
        }

        Optional<String> storedNameOptional = getStoredTensuraName(player);

        if (storedNameOptional.isEmpty()) {
            return false;
        }

        String storedName = storedNameOptional.get();

        return !storedName.isBlank()
                && !storedName.equalsIgnoreCase("null")
                && !storedName.equalsIgnoreCase("none");
    }

    public static Optional<String> getStoredTensuraName(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }

        IExistence existence = TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return Optional.empty();
        }

        return readAnyStringName(existence);
    }

    public static String getStoredTensuraNameOrFallback(ServerPlayer player) {
        return getStoredTensuraName(player)
                .orElseGet(() -> player == null ? "Unknown" : player.getGameProfile().getName());
    }

    private static boolean readAnyBooleanFlag(Object target) {
        for (String methodName : BOOLEAN_NAMED_METHODS) {
            Optional<Boolean> value = tryReadBoolean(target, methodName);

            if (value.isPresent() && value.get()) {
                return true;
            }
        }

        return false;
    }

    private static Optional<String> readAnyStringName(Object target) {
        for (String methodName : STRING_NAME_METHODS) {
            Optional<String> value = tryReadString(target, methodName);

            if (value.isPresent()) {
                String cleaned = value.get().trim();

                if (!cleaned.isBlank()) {
                    return Optional.of(cleaned);
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<Boolean> tryReadBoolean(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);

            if (method.getParameterCount() != 0) {
                return Optional.empty();
            }

            Object result = method.invoke(target);

            if (result instanceof Boolean booleanResult) {
                return Optional.of(booleanResult);
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static Optional<String> tryReadString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);

            if (method.getParameterCount() != 0) {
                return Optional.empty();
            }

            Object result = method.invoke(target);

            if (result == null) {
                return Optional.empty();
            }

            String value = result.toString().trim();

            if (value.isBlank()) {
                return Optional.empty();
            }

            String lowered = value.toLowerCase(Locale.ROOT);

            if (lowered.equals("optional.empty") || lowered.equals("null") || lowered.equals("none")) {
                return Optional.empty();
            }

            if (lowered.startsWith("optional[") && value.endsWith("]")) {
                value = value.substring("Optional[".length(), value.length() - 1).trim();
            }

            if (value.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(value);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }
    }
}