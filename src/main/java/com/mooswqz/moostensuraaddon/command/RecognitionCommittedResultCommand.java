package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCommitRecord;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCommittedResult;
import com.mooswqz.moostensuraaddon.recognition.RecognitionPath;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Read-only debug inspector for the recognition result frozen into player data.
 *
 * <p>This command deliberately does not synchronize live Tensura state, run
 * recognition evaluation, invoke naming eligibility, perform migration, repair
 * metadata, or write any attachment value. It only asks RecognitionData for an
 * immutable diagnostic snapshot and renders that snapshot to the command
 * source.</p>
 */
public final class RecognitionCommittedResultCommand {

    private RecognitionCommittedResultCommand() {
    }

    /**
     * Creates the canonical debug branch:
     *
     * <pre>
     * /moostensura debug committed
     * /moostensura debug committed &lt;player&gt;
     * </pre>
     */
    public static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return Commands.literal("committed")
                .requires(
                        DebugModeService
                                ::canUseDebugTools
                )
                .executes(context -> inspect(
                        context.getSource(),
                        context.getSource()
                                .getPlayerOrException()
                ))
                .then(
                        Commands.argument(
                                        "player",
                                        EntityArgument.player()
                                )
                                .executes(context -> inspect(
                                        context.getSource(),
                                        EntityArgument.getPlayer(
                                                context,
                                                "player"
                                        )
                                ))
                );
    }

    private static int inspect(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        RecognitionData data =
                target.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        /*
         * getCommittedResult() is a read-only snapshot operation. Do not add
         * synchronize(), evaluate(), completeCommittedPlayerMetadata(), or any
         * repair call here; this command is intended to prove what is already
         * stored, not to improve it while it is being inspected.
         */
        RecognitionCommittedResult result =
                data.getCommittedResult();

        String accountName =
                target.getGameProfile()
                        .getName();

        sendHeader(
                source,
                "Committed Recognition Record: "
                        + accountName,
                ChatFormatting.LIGHT_PURPLE
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Read-only inspection. No recognition data was synchronized, migrated, repaired, or recalculated."
                        )
                        .withStyle(
                                ChatFormatting.DARK_GRAY
                        ),
                false
        );

        sendHeader(
                source,
                "Record state",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Committed",
                Boolean.toString(
                        result.committed()
                ),
                result.committed()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.GRAY
        );

        sendValue(
                source,
                "Valid frozen result",
                Boolean.toString(
                        result.valid()
                ),
                result.valid()
                        ? ChatFormatting.GREEN
                        : result.committed()
                          ? ChatFormatting.RED
                          : ChatFormatting.GRAY
        );

        RecognitionCommittedResult.MigrationState migrationState =
                result.migrationState();

        sendValue(
                source,
                "Migration state",
                migrationState.displayName(),
                migrationColor(
                        migrationState
                )
        );

        sendValue(
                source,
                "Reveal pending",
                Boolean.toString(
                        data.isRevealPending()
                ),
                data.isRevealPending()
                        ? ChatFormatting.YELLOW
                        : ChatFormatting.GRAY
        );

        sendHeader(
                source,
                "Stored versions",
                ChatFormatting.GOLD
        );

        sendVersion(
                source,
                "Data schema",
                result.dataVersion(),
                RecognitionData.CURRENT_DATA_VERSION
        );

        sendVersion(
                source,
                "Result format",
                result.resultVersion(),
                RecognitionCommitRecord
                        .CURRENT_RESULT_VERSION
        );

        sendVersion(
                source,
                "Recognition rules",
                result.rulesVersion(),
                RecognitionCommitRecord
                        .CURRENT_RULES_VERSION
        );

        sendVersion(
                source,
                "Reward profile",
                result.rewardProfileVersion(),
                RecognitionCommitRecord
                        .CURRENT_REWARD_PROFILE_VERSION
        );

        sendHeader(
                source,
                "Frozen paths",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Path summary",
                result.pathSummary(),
                result.committed()
                        ? ChatFormatting.AQUA
                        : ChatFormatting.GRAY
        );

        sendValue(
                source,
                "Raw primary path ID",
                displayStoredText(
                        result.primaryPathId()
                ),
                pathIdColor(
                        result.primaryPathId(),
                        result.committed()
                )
        );

        sendValue(
                source,
                "Resolved primary path",
                resolvedPath(
                        result.primaryPathId()
                ),
                resolvedPathColor(
                        result.primaryPathId(),
                        result.committed()
                )
        );

        sendValue(
                source,
                "Raw secondary path ID",
                displayStoredText(
                        result.secondaryPathId()
                ),
                pathIdColor(
                        result.secondaryPathId(),
                        !result.pure()
                                && result.committed()
                )
        );

        sendValue(
                source,
                "Resolved secondary path",
                resolvedPath(
                        result.secondaryPathId()
                ),
                resolvedPathColor(
                        result.secondaryPathId(),
                        !result.pure()
                                && result.committed()
                )
        );

        sendValue(
                source,
                "Pure recognition",
                Boolean.toString(
                        result.pure()
                ),
                result.pure()
                        ? ChatFormatting.LIGHT_PURPLE
                        : ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Contradiction modifier",
                displayStoredText(
                        result.contradictionModifier()
                ),
                RecognitionCommitRecord.NO_CONTRADICTION
                        .equals(
                                result.contradictionModifier()
                        )
                        ? ChatFormatting.GRAY
                        : ChatFormatting.LIGHT_PURPLE
        );

        sendHeader(
                source,
                "Frozen identity",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Bestowed title",
                displayStoredText(
                        result.bestowedTitle()
                ),
                result.bestowedTitle().isBlank()
                        && result.committed()
                        ? ChatFormatting.RED
                        : ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Frozen display name",
                displayStoredText(
                        result.frozenDisplayName()
                ),
                result.frozenDisplayName().isBlank()
                        && result.committed()
                        ? ChatFormatting.YELLOW
                        : ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Primary score at commitment",
                formatScore(
                        result.primaryScore()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Secondary score at commitment",
                result.pure()
                        ? "0.0 (Pure result)"
                        : formatScore(
                        result.secondaryScore()
                ),
                ChatFormatting.WHITE
        );

        sendHeader(
                source,
                "Commit provenance",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Balance source",
                displayStoredText(
                        result.balanceSource()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Balance revision",
                Long.toString(
                        result.balanceRevision()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Committed at",
                formatTimestamp(
                        result.committedAtEpochMillis()
                ),
                result.committedAtEpochMillis() > 0L
                        ? ChatFormatting.WHITE
                        : ChatFormatting.GRAY
        );

        sendValue(
                source,
                "Commit timestamp millis",
                Long.toString(
                        result.committedAtEpochMillis()
                ),
                ChatFormatting.DARK_GRAY
        );

        sendValue(
                source,
                "Incarnation ID",
                displayStoredText(
                        result.incarnationId()
                ),
                result.incarnationId().isBlank()
                        && result.committed()
                        ? ChatFormatting.YELLOW
                        : ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Migration source",
                displayStoredText(
                        result.migrationSource()
                ),
                migrationState
                        == RecognitionCommittedResult
                        .MigrationState.LEGACY_BACKFILLED
                        ? ChatFormatting.AQUA
                        : ChatFormatting.WHITE
        );

        sendHeader(
                source,
                "Validation",
                ChatFormatting.GOLD
        );

        if (result.validationIssues().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    result.committed()
                                            ? "No frozen-record validation issues detected."
                                            : "No committed recognition record is stored."
                            )
                            .withStyle(
                                    result.committed()
                                            ? ChatFormatting.GREEN
                                            : ChatFormatting.GRAY
                            ),
                    false
            );
        } else {
            for (String issue :
                    result.validationIssues()) {

                source.sendSuccess(
                        () -> Component.literal(
                                        "- " + issue
                                )
                                .withStyle(
                                        ChatFormatting.RED
                                ),
                        false
                );
            }
        }

        source.sendSuccess(
                () -> Component.literal(
                                "Inspection complete; the attachment was not modified."
                        )
                        .withStyle(
                                ChatFormatting.DARK_GRAY
                        ),
                false
        );

        return 1;
    }

    private static void sendVersion(
            CommandSourceStack source,
            String label,
            int stored,
            int current
    ) {
        ChatFormatting color;
        String suffix;

        if (stored > current) {
            color = ChatFormatting.YELLOW;
            suffix = " [future]";
        } else if (stored < current) {
            color = ChatFormatting.AQUA;
            suffix = " [legacy]";
        } else {
            color = ChatFormatting.GREEN;
            suffix = " [current]";
        }

        sendValue(
                source,
                label,
                stored
                        + " / current "
                        + current
                        + suffix,
                color
        );
    }

    private static String resolvedPath(
            String rawPathId
    ) {
        if (rawPathId == null
                || rawPathId.isBlank()) {
            return "none";
        }

        return RecognitionPath.byId(
                        rawPathId
                )
                .map(RecognitionPath::getId)
                .orElse(
                        "unresolved; raw ID preserved"
                );
    }

    private static ChatFormatting pathIdColor(
            String rawPathId,
            boolean required
    ) {
        if (rawPathId == null
                || rawPathId.isBlank()) {
            return required
                    ? ChatFormatting.RED
                    : ChatFormatting.GRAY;
        }

        return RecognitionPath.byId(rawPathId)
                .isPresent()
                ? ChatFormatting.WHITE
                : ChatFormatting.YELLOW;
    }

    private static ChatFormatting resolvedPathColor(
            String rawPathId,
            boolean required
    ) {
        if (rawPathId == null
                || rawPathId.isBlank()) {
            return required
                    ? ChatFormatting.RED
                    : ChatFormatting.GRAY;
        }

        return RecognitionPath.byId(rawPathId)
                .isPresent()
                ? ChatFormatting.GREEN
                : ChatFormatting.YELLOW;
    }

    private static ChatFormatting migrationColor(
            RecognitionCommittedResult.MigrationState state
    ) {
        return switch (state) {
            case CURRENT ->
                    ChatFormatting.GREEN;

            case LEGACY_BACKFILLED ->
                    ChatFormatting.AQUA;

            case FUTURE_VERSION ->
                    ChatFormatting.YELLOW;

            case INCOMPLETE ->
                    ChatFormatting.RED;

            case NOT_COMMITTED ->
                    ChatFormatting.GRAY;
        };
    }

    private static String displayStoredText(
            String value
    ) {
        return value == null
                || value.isBlank()
                ? "none"
                : value;
    }

    private static String formatTimestamp(
            long epochMillis
    ) {
        if (epochMillis <= 0L) {
            return "unknown";
        }

        try {
            return DateTimeFormatter.ISO_INSTANT
                    .format(
                            Instant.ofEpochMilli(
                                    epochMillis
                            )
                    );
        } catch (DateTimeException exception) {
            return "invalid timestamp";
        }
    }

    private static String formatScore(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.1f",
                !Double.isFinite(value)
                        || value < 0.0D
                        ? 0.0D
                        : value
        );
    }

    private static void sendHeader(
            CommandSourceStack source,
            String text,
            ChatFormatting color
    ) {
        source.sendSuccess(
                () -> Component.literal(text)
                        .withStyle(
                                color,
                                ChatFormatting.BOLD
                        ),
                false
        );
    }

    private static void sendValue(
            CommandSourceStack source,
            String label,
            String value,
            ChatFormatting valueColor
    ) {
        MutableComponent message =
                Component.literal(
                                label + ": "
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        );

        message.append(
                Component.literal(
                                value == null
                                        ? ""
                                        : value
                        )
                        .withStyle(
                                valueColor == null
                                        ? ChatFormatting.WHITE
                                        : valueColor
                        )
        );

        source.sendSuccess(
                () -> message,
                false
        );
    }
}