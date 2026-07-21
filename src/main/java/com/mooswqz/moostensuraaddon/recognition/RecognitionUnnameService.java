package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class RecognitionUnnameService {

    private RecognitionUnnameService() {
    }

    /**
     * Removes the player's native Tensura name and clears only the naming
     * result stored by this addon.
     *
     * Recognition deeds, awakening flags, path progress, mastery, EP,
     * magicules, skills, subordinate history and incarnation identity are
     * deliberately preserved.
     */
    public static Result unname(
            ServerPlayer player
    ) {
        if (player == null) {
            return Result.failed(
                    "",
                    "A player is required."
            );
        }

        IExistence existence =
                TensuraStorages.getExistenceFrom(player);

        if (existence == null) {
            return Result.failed(
                    "",
                    "Tensura existence storage could not be found."
            );
        }

        String previousNativeName =
                sanitizeName(
                        existence.getName()
                );

        NativeNameClearResult nativeClearResult =
                clearNativeName(existence);

        /*
         * Do not clear the addon's commitment data unless the native Tensura
         * name was successfully cleared. This prevents the two systems from
         * becoming desynchronized after a failed debug operation.
         */
        if (!nativeClearResult.success()) {
            return Result.failed(
                    previousNativeName,
                    nativeClearResult.errorMessage()
            );
        }

        RecognitionData recognitionData =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        clearRecognitionNamingState(
                recognitionData
        );

        /*
         * Reassigning the mutable attachment makes the changed state explicit
         * to NeoForge and keeps the command safe if attachment handling changes
         * later.
         */
        player.setData(
                AttachmentRegistry.RECOGNITION_DATA,
                recognitionData
        );

        /*
         * Immediately remove the title from the nametag, tab list and all
         * server-side display-name consumers.
         */
        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(player);

        return Result.succeeded(
                previousNativeName
        );
    }

    private static void clearRecognitionNamingState(
            RecognitionData data
    ) {
        if (data == null) {
            return;
        }

        data.setFlag(
                RecognitionStatKeys.NAMING_COMMITTED,
                false
        );

        data.setFlag(
                RecognitionStatKeys.PURE_RECOGNITION,
                false
        );

        data.setFlag(
                RecognitionStatKeys.REVEAL_PENDING,
                false
        );

        data.setString(
                RecognitionStatKeys.PRIMARY_PATH,
                ""
        );

        data.setString(
                RecognitionStatKeys.SECONDARY_PATH,
                ""
        );

        data.setString(
                RecognitionStatKeys.BESTOWED_TITLE,
                ""
        );

        /*
         * INCARNATION_ID is intentionally preserved.
         *
         * This keeps deterministic title generation stable while repeatedly
         * testing the naming ritual in the same incarnation.
         */
    }

    /**
     * Uses Tensura's runtime name setter without hard-linking this debug
     * utility to a potentially changing concrete existence implementation.
     *
     * IExistence#getName remains the verification source. The operation is
     * considered successful only when the stored name is blank afterward.
     */
    private static NativeNameClearResult clearNativeName(
            IExistence existence
    ) {
        String currentName =
                sanitizeName(
                        existence.getName()
                );

        if (currentName.isBlank()) {
            return NativeNameClearResult.succeeded();
        }

        Method setter =
                findStringNameSetter(
                        existence.getClass()
                );

        if (setter == null) {
            return NativeNameClearResult.failed(
                    "The current Tensura existence implementation does not expose setName(String)."
            );
        }

        try {
            if (!setter.canAccess(existence)) {
                setter.setAccessible(true);
            }

            setter.invoke(
                    existence,
                    ""
            );

            existence.markDirty();

            String nameAfterClearing =
                    sanitizeName(
                            existence.getName()
                    );

            if (!nameAfterClearing.isBlank()) {
                return NativeNameClearResult.failed(
                        "Tensura accepted the operation but the stored name remained '"
                                + nameAfterClearing
                                + "'."
                );
            }

            return NativeNameClearResult.succeeded();
        } catch (IllegalAccessException exception) {
            return NativeNameClearResult.failed(
                    "The Tensura name setter could not be accessed: "
                            + exception.getMessage()
            );
        } catch (InvocationTargetException exception) {
            Throwable cause =
                    exception.getCause() == null
                            ? exception
                            : exception.getCause();

            return NativeNameClearResult.failed(
                    "Tensura rejected the name change: "
                            + cause.getClass().getSimpleName()
                            + formatMessage(cause.getMessage())
            );
        } catch (RuntimeException exception) {
            return NativeNameClearResult.failed(
                    "The native name could not be cleared: "
                            + exception.getClass().getSimpleName()
                            + formatMessage(exception.getMessage())
            );
        }
    }

    private static Method findStringNameSetter(
            Class<?> implementationClass
    ) {
        Class<?> currentClass =
                implementationClass;

        while (currentClass != null) {
            for (Method method :
                    currentClass.getDeclaredMethods()) {

                if (isStringNameSetter(method)) {
                    return method;
                }
            }

            currentClass =
                    currentClass.getSuperclass();
        }

        /*
         * Public inherited/interface methods are checked separately because
         * they may not appear in a concrete class's declared-method array.
         */
        for (Method method :
                implementationClass.getMethods()) {

            if (isStringNameSetter(method)) {
                return method;
            }
        }

        return null;
    }

    private static boolean isStringNameSetter(
            Method method
    ) {
        if (method == null) {
            return false;
        }

        if (!method.getName().equals("setName")) {
            return false;
        }

        if (method.getParameterCount() != 1) {
            return false;
        }

        return method.getParameterTypes()[0]
                == String.class;
    }

    private static String sanitizeName(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private static String formatMessage(
            String message
    ) {
        if (message == null
                || message.isBlank()) {
            return "";
        }

        return ": " + message;
    }

    public record Result(
            boolean success,
            String previousNativeName,
            String errorMessage
    ) {

        public Result {
            previousNativeName =
                    previousNativeName == null
                            ? ""
                            : previousNativeName.trim();

            errorMessage =
                    errorMessage == null
                            ? ""
                            : errorMessage.trim();
        }

        public static Result succeeded(
                String previousNativeName
        ) {
            return new Result(
                    true,
                    previousNativeName,
                    ""
            );
        }

        public static Result failed(
                String previousNativeName,
                String errorMessage
        ) {
            return new Result(
                    false,
                    previousNativeName,
                    errorMessage
            );
        }

        public boolean previouslyNamed() {
            return !previousNativeName.isBlank();
        }
    }

    private record NativeNameClearResult(
            boolean success,
            String errorMessage
    ) {

        private static NativeNameClearResult succeeded() {
            return new NativeNameClearResult(
                    true,
                    ""
            );
        }

        private static NativeNameClearResult failed(
                String errorMessage
        ) {
            return new NativeNameClearResult(
                    false,
                    errorMessage == null
                            ? ""
                            : errorMessage
            );
        }
    }
}