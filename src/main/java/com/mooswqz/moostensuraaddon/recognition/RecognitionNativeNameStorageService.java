package com.mooswqz.moostensuraaddon.recognition;

import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Writes Tensura's stored existence name and the matching Minecraft player
 * custom name without invoking a naming packet.
 *
 * <p>This is intentionally separate from native endowment. It lets Soul
 * Recognition publish its frozen title for a player who was already named,
 * while avoiding a second HIGH naming request and its EP gain.</p>
 */
public final class RecognitionNativeNameStorageService {

    private RecognitionNativeNameStorageService() {
    }

    public static Result write(
            ServerPlayer player,
            IExistence existence,
            String requestedName
    ) {
        if (player == null) {
            return Result.failed(
                    "",
                    "",
                    "A server player is required."
            );
        }

        if (existence == null) {
            return Result.failed(
                    "",
                    currentCustomName(player),
                    "Tensura existence storage could not be found."
            );
        }

        String previousName = sanitize(
                existence.getName()
        );
        String previousCustomName =
                currentCustomName(player);
        String expectedName = sanitize(requestedName);

        if (previousName.equals(expectedName)
                && previousCustomName.equals(expectedName)) {
            return Result.succeeded(
                    false,
                    previousName,
                    previousName,
                    previousCustomName,
                    previousCustomName
            );
        }

        String storedName = previousName;

        if (!previousName.equals(expectedName)) {
            Method setter = findStringNameSetter(
                    existence.getClass()
            );

            if (setter == null) {
                return Result.failed(
                        previousName,
                        previousCustomName,
                        "The current Tensura existence implementation does not expose setName(String)."
                );
            }

            try {
                if (!setter.canAccess(existence)) {
                    setter.setAccessible(true);
                }

                setter.invoke(
                        existence,
                        expectedName
                );

                existence.markDirty();

                storedName = sanitize(
                        existence.getName()
                );

                if (!storedName.equals(expectedName)) {
                    return Result.failed(
                            storedName,
                            previousCustomName,
                            "Tensura accepted the operation but stored '"
                                    + storedName
                                    + "' instead of '"
                                    + expectedName
                                    + "'."
                    );
                }
            } catch (IllegalAccessException exception) {
                return Result.failed(
                        previousName,
                        previousCustomName,
                        "The Tensura name setter could not be accessed: "
                                + exception.getMessage()
                );
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause() == null
                        ? exception
                        : exception.getCause();

                return Result.failed(
                        previousName,
                        previousCustomName,
                        "Tensura rejected the name change: "
                                + cause.getClass().getSimpleName()
                                + formatMessage(cause.getMessage())
                );
            } catch (RuntimeException exception) {
                return Result.failed(
                        previousName,
                        previousCustomName,
                        "The native name could not be changed: "
                                + exception.getClass().getSimpleName()
                                + formatMessage(exception.getMessage())
                );
            }
        }

        try {
            if (!previousCustomName.equals(expectedName)) {
                player.setCustomName(
                        expectedName.isBlank()
                                ? null
                                : Component.literal(expectedName)
                );
            }

            String storedCustomName =
                    currentCustomName(player);

            if (!storedCustomName.equals(expectedName)) {
                return Result.failed(
                        storedName,
                        storedCustomName,
                        "Minecraft stored the player custom name as '"
                                + storedCustomName
                                + "' instead of '"
                                + expectedName
                                + "'."
                );
            }

            return Result.succeeded(
                    !previousName.equals(storedName)
                            || !previousCustomName.equals(
                            storedCustomName
                    ),
                    previousName,
                    storedName,
                    previousCustomName,
                    storedCustomName
            );
        } catch (RuntimeException exception) {
            return Result.failed(
                    storedName,
                    currentCustomName(player),
                    "The Minecraft custom name could not be changed: "
                            + exception.getClass().getSimpleName()
                            + formatMessage(exception.getMessage())
            );
        }
    }

    private static Method findStringNameSetter(
            Class<?> implementationClass
    ) {
        Class<?> currentClass = implementationClass;

        while (currentClass != null) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (isStringNameSetter(method)) {
                    return method;
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        for (Method method : implementationClass.getMethods()) {
            if (isStringNameSetter(method)) {
                return method;
            }
        }

        return null;
    }

    private static boolean isStringNameSetter(
            Method method
    ) {
        return method != null
                && method.getName().equals("setName")
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == String.class;
    }

    private static String sanitize(
            String value
    ) {
        return value == null ? "" : value.trim();
    }

    private static String currentCustomName(
            ServerPlayer player
    ) {
        if (player == null
                || player.getCustomName() == null) {
            return "";
        }

        return sanitize(
                player.getCustomName()
                        .getString()
        );
    }

    private static String formatMessage(
            String message
    ) {
        return message == null || message.isBlank()
                ? ""
                : ": " + message;
    }

    public record Result(
            boolean success,
            boolean changed,
            String previousName,
            String storedName,
            String previousCustomName,
            String storedCustomName,
            String errorMessage
    ) {

        public Result {
            previousName = sanitize(previousName);
            storedName = sanitize(storedName);
            previousCustomName = sanitize(previousCustomName);
            storedCustomName = sanitize(storedCustomName);
            errorMessage = sanitize(errorMessage);
        }

        private static Result succeeded(
                boolean changed,
                String previousName,
                String storedName,
                String previousCustomName,
                String storedCustomName
        ) {
            return new Result(
                    true,
                    changed,
                    previousName,
                    storedName,
                    previousCustomName,
                    storedCustomName,
                    ""
            );
        }

        private static Result failed(
                String previousName,
                String previousCustomName,
                String errorMessage
        ) {
            return new Result(
                    false,
                    false,
                    previousName,
                    previousName,
                    previousCustomName,
                    previousCustomName,
                    errorMessage
            );
        }
    }
}
