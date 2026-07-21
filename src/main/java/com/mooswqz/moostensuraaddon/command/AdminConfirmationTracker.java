package com.mooswqz.moostensuraaddon.command;

import net.minecraft.commands.CommandSourceStack;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AdminConfirmationTracker {

    public static final int CONFIRMATION_WINDOW_SECONDS =
            30;

    private static final long CONFIRMATION_WINDOW_NANOS =
            CONFIRMATION_WINDOW_SECONDS
                    * 1_000_000_000L;

    /*
     * Only one destructive or privileged confirmation may be pending for a
     * command source at a time.
     *
     * Requesting another action replaces the previous pending confirmation.
     */
    private static final ConcurrentMap<String, PendingConfirmation>
            PENDING_CONFIRMATIONS =
            new ConcurrentHashMap<>();

    private AdminConfirmationTracker() {
    }

    public static void arm(
            CommandSourceStack source,
            Action action
    ) {
        arm(
                source,
                action,
                null
        );
    }

    public static void arm(
            CommandSourceStack source,
            Action action,
            UUID targetUuid
    ) {
        if (source == null || action == null) {
            return;
        }

        long expiresAtNanos =
                System.nanoTime()
                        + CONFIRMATION_WINDOW_NANOS;

        PENDING_CONFIRMATIONS.put(
                getSourceKey(source),
                new PendingConfirmation(
                        action,
                        targetUuid,
                        expiresAtNanos
                )
        );
    }

    public static Result consume(
            CommandSourceStack source,
            Action expectedAction
    ) {
        return consume(
                source,
                expectedAction,
                null
        );
    }

    /**
     * Consumes one pending confirmation.
     *
     * Confirmations are deliberately one-use. A mismatched action or target
     * also consumes the pending entry, forcing the administrator to issue the
     * warning-stage command again.
     */
    public static Result consume(
            CommandSourceStack source,
            Action expectedAction,
            UUID expectedTargetUuid
    ) {
        if (source == null || expectedAction == null) {
            return new Result(
                    Status.MISSING
            );
        }

        PendingConfirmation pending =
                PENDING_CONFIRMATIONS.remove(
                        getSourceKey(source)
                );

        if (pending == null) {
            return new Result(
                    Status.MISSING
            );
        }

        if (System.nanoTime()
                > pending.expiresAtNanos()) {

            return new Result(
                    Status.EXPIRED
            );
        }

        if (pending.action()
                != expectedAction) {

            return new Result(
                    Status.DIFFERENT_ACTION
            );
        }

        if (!Objects.equals(
                pending.targetUuid(),
                expectedTargetUuid
        )) {
            return new Result(
                    Status.DIFFERENT_TARGET
            );
        }

        return new Result(
                Status.CONFIRMED
        );
    }

    public static void clear(
            CommandSourceStack source
    ) {
        if (source == null) {
            return;
        }

        PENDING_CONFIRMATIONS.remove(
                getSourceKey(source)
        );
    }

    private static String getSourceKey(
            CommandSourceStack source
    ) {
        if (source.getEntity() != null) {
            return "entity:"
                    + source.getEntity()
                    .getUUID();
        }

        /*
         * The dedicated-server console and integrated-server console use one
         * shared confirmation slot.
         */
        return "server_console";
    }

    public enum Action {
        UNNAME,
        RESET_CONFIG,
        RESET_PLAYER_DATA,
        ENABLE_DEBUG_MODE
    }

    public enum Status {
        CONFIRMED,
        MISSING,
        EXPIRED,
        DIFFERENT_ACTION,
        DIFFERENT_TARGET
    }

    public record Result(
            Status status
    ) {

        public Result {
            status =
                    status == null
                            ? Status.MISSING
                            : status;
        }

        public boolean confirmed() {
            return status
                    == Status.CONFIRMED;
        }
    }

    private record PendingConfirmation(
            Action action,
            UUID targetUuid,
            long expiresAtNanos
    ) {
    }
}