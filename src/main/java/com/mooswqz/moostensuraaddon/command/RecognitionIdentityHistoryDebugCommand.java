package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIdentityHistoryModifier;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIdentityHistoryResolution;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIdentityHistoryResolver;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIdentityHistoryService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIdentityHistorySnapshot;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIdentityHistoryValidationHarness;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Debug-only diagnostics for versioned identity-history persistence.
 */
public final class RecognitionIdentityHistoryDebugCommand {

    private RecognitionIdentityHistoryDebugCommand() {
    }

    /**
     * /moostensura debug history
     * /moostensura debug history &lt;player&gt;
     * /moostensura debug history validate
     */
    public static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return Commands.literal("history")
                .requires(
                        DebugModeService::canUseDebugTools
                )
                .executes(context -> inspect(
                        context.getSource(),
                        context.getSource().getPlayerOrException()
                ))
                .then(
                        Commands.literal("validate")
                                .executes(context -> validate(
                                        context.getSource()
                                ))
                )
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
        RecognitionData data = target.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        RecognitionIdentityHistorySnapshot snapshot =
                RecognitionIdentityHistoryService.inspect(data);

        RecognitionIdentityHistoryResolution resolution =
                RecognitionIdentityHistoryResolver.resolve(data);

        sendHeader(
                source,
                "Identity History: "
                        + target.getGameProfile().getName(),
                ChatFormatting.LIGHT_PURPLE
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Read-only inspection. No decay, migration, deed recording or modifier storage was performed."
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        sendHeader(
                source,
                "Persistence",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "History version",
                snapshot.storedVersion()
                        + " / current "
                        + snapshot.currentVersion(),
                snapshot.futureVersion()
                        ? ChatFormatting.YELLOW
                        : snapshot.initialized()
                          ? ChatFormatting.GREEN
                          : ChatFormatting.GRAY
        );

        sendValue(
                source,
                "Migration state",
                snapshot.migrationState().displayName(),
                switch (snapshot.migrationState()) {
                    case CURRENT -> ChatFormatting.GREEN;
                    case UNINITIALIZED, UNVERSIONED_PAYLOAD,
                         LEGACY_VERSION ->
                            ChatFormatting.YELLOW;
                    case FUTURE_VERSION -> ChatFormatting.AQUA;
                    case INVALID_DATA -> ChatFormatting.RED;
                }
        );

        sendValue(
                source,
                "Migration source",
                displayStoredText(snapshot.migrationSource()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "History modifier",
                displayModifier(snapshot),
                snapshot.knownModifier().isPresent()
                        ? ChatFormatting.AQUA
                        : ChatFormatting.YELLOW
        );

        sendValue(
                source,
                "Frozen committed modifier",
                data.getContradictionModifier(),
                ChatFormatting.DARK_AQUA
        );

        source.sendSuccess(
                () -> Component.literal(
                                "History and frozen committed modifiers are intentionally separate. Identity-history events never rewrite committed recognition."
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        sendHeader(
                source,
                "Current resolution",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Resolved candidate",
                resolution.modifierId()
                        + " ("
                        + resolution.modifierDisplayName()
                        + ")",
                resolution.hasModifier()
                        ? ChatFormatting.AQUA
                        : ChatFormatting.GRAY
        );

        sendValue(
                source,
                "Original axes",
                "moral="
                        + displayDirection(
                        resolution.originalMoralDirection()
                )
                        + ", temperament="
                        + displayDirection(
                        resolution.originalTemperamentDirection()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Current clear axes",
                "moral="
                        + displayDirection(
                        resolution.currentMoralDirection()
                )
                        + ", temperament="
                        + displayDirection(
                        resolution.currentTemperamentDirection()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Stored established axes",
                "moral="
                        + displayDirection(
                        resolution.storedMoralDirection()
                )
                        + ", temperament="
                        + displayDirection(
                        resolution.storedTemperamentDirection()
                ),
                ChatFormatting.DARK_AQUA
        );

        sendValue(
                source,
                "Resolution flags",
                "moral contradiction="
                        + resolution.moralContradiction()
                        + ", temperament contradiction="
                        + resolution.temperamentContradiction()
                        + ", returned="
                        + resolution.anyReturnedAxis()
                        + ", contested="
                        + resolution.anyContestedAxis(),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Reason",
                resolution.reason(),
                resolution.hasModifier()
                        ? ChatFormatting.YELLOW
                        : ChatFormatting.GRAY
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Player guidance: "
                                        + resolution.summary()
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        if (!snapshot.rawModifierId().equals(
                resolution.modifierId()
        )) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "Stored and resolved modifiers differ. A known candidate is stored only after the next qualifying deed; unknown future IDs remain preserved."
                            )
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        for (String evidenceLine : resolution.evidence()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "- " + evidenceLine
                            )
                            .withStyle(ChatFormatting.DARK_GRAY),
                    false
            );
        }

        sendHeader(
                source,
                "Moral history",
                ChatFormatting.GOLD
        );

        sendMomentum(
                source,
                "Good",
                snapshot.goodMomentum(),
                snapshot.highestGoodCommitment(),
                snapshot.lastGoodDeedGameTime(),
                ChatFormatting.GREEN
        );

        sendMomentum(
                source,
                "Evil",
                snapshot.evilMomentum(),
                snapshot.highestEvilCommitment(),
                snapshot.lastEvilDeedGameTime(),
                ChatFormatting.RED
        );

        sendValue(
                source,
                "Moral reversals",
                Integer.toString(snapshot.moralReversalCount()),
                ChatFormatting.WHITE
        );

        sendHeader(
                source,
                "Temperament history",
                ChatFormatting.GOLD
        );

        sendMomentum(
                source,
                "Order",
                snapshot.orderMomentum(),
                snapshot.highestOrderCommitment(),
                snapshot.lastOrderDeedGameTime(),
                ChatFormatting.GREEN
        );

        sendMomentum(
                source,
                "Freedom",
                snapshot.freedomMomentum(),
                snapshot.highestFreedomCommitment(),
                snapshot.lastFreedomDeedGameTime(),
                ChatFormatting.AQUA
        );

        sendValue(
                source,
                "Temperament reversals",
                Integer.toString(snapshot.temperamentReversalCount()),
                ChatFormatting.WHITE
        );

        sendHeader(
                source,
                "Lazy decay rules",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Momentum cap",
                format(RecognitionIdentityHistoryService.MAXIMUM_MOMENTUM),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Maximum deed weight",
                format(RecognitionIdentityHistoryService.MAXIMUM_DEED_WEIGHT),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Decay grace",
                formatTicks(
                        RecognitionIdentityHistoryService.DECAY_GRACE_TICKS
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Decay rate",
                format(RecognitionIdentityHistoryService.DECAY_PER_INTERVAL)
                        + " per "
                        + formatTicks(
                        RecognitionIdentityHistoryService.DECAY_INTERVAL_TICKS
                ),
                ChatFormatting.WHITE
        );

        source.sendSuccess(
                () -> Component.literal(
                                "No tick handler exists. Moral deeds decay Good/Evil; temperament deeds decay Order/Freedom."
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        if (!snapshot.validationIssues().isEmpty()) {
            sendHeader(
                    source,
                    "Validation issues",
                    ChatFormatting.RED
            );

            for (String issue : snapshot.validationIssues()) {
                source.sendFailure(
                        Component.literal("- " + issue)
                                .withStyle(ChatFormatting.RED)
                );
            }
        } else {
            source.sendSuccess(
                    () -> Component.literal(
                                    "No identity-history validation issues detected."
                            )
                            .withStyle(ChatFormatting.GREEN),
                    false
            );
        }

        if (!snapshot.initialized()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "The login migration hook initializes this namespace once. Missing version 0 values are safe defaults."
                            )
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        return snapshot.valid() ? 1 : 0;
    }

    private static int validate(
            CommandSourceStack source
    ) {
        RecognitionIdentityHistoryValidationHarness.Report report =
                RecognitionIdentityHistoryValidationHarness.run();

        sendHeader(
                source,
                "Identity History Validation",
                ChatFormatting.LIGHT_PURPLE
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Temporary RecognitionData only. No player or world data was modified."
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        sendValue(
                source,
                "History schema",
                Integer.toString(report.historyVersion()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Checks",
                report.passedChecks()
                        + " passed / "
                        + report.failedChecks()
                        + " failed",
                report.passed()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.RED
        );

        for (RecognitionIdentityHistoryValidationHarness.Check check :
                report.checks()) {
            MutableComponent line = Component.literal(
                            check.passed()
                                    ? "[PASS] "
                                    : "[FAIL] "
                    )
                    .withStyle(
                            check.passed()
                                    ? ChatFormatting.GREEN
                                    : ChatFormatting.RED
                    );

            line.append(
                    Component.literal(check.name())
                            .withStyle(ChatFormatting.WHITE)
            );

            if (!check.detail().isBlank()) {
                line.append(
                        Component.literal(" — " + check.detail())
                                .withStyle(ChatFormatting.DARK_GRAY)
                );
            }

            source.sendSuccess(
                    () -> line,
                    false
            );
        }

        for (String warning : report.warnings()) {
            source.sendSuccess(
                    () -> Component.literal("Warning: " + warning)
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        source.sendSuccess(
                () -> Component.literal(
                                report.passed()
                                        ? "Identity-history validation PASS."
                                        : "Identity-history validation FAILED."
                        )
                        .withStyle(
                                report.passed()
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return report.passed() ? 1 : 0;
    }

    private static void sendMomentum(
            CommandSourceStack source,
            String label,
            double current,
            double highest,
            long lastGameTime,
            ChatFormatting color
    ) {
        sendValue(
                source,
                label,
                format(current)
                        + " current / "
                        + format(highest)
                        + " historical peak / last deed "
                        + formatStoredGameTime(lastGameTime),
                color
        );
    }

    private static String displayModifier(
            RecognitionIdentityHistorySnapshot snapshot
    ) {
        return snapshot.knownModifier()
                .map(modifier ->
                        modifier.id()
                                + " ("
                                + modifier.displayName()
                                + ")"
                )
                .orElse(
                        snapshot.rawModifierId()
                                + " [unknown preserved]"
                );
    }

    private static String displayDirection(
            String value
    ) {
        return value == null || value.isBlank()
                ? "neutral/none"
                : value;
    }

    private static String displayStoredText(
            String value
    ) {
        return value == null || value.isBlank()
                ? "none"
                : value;
    }

    private static String formatStoredGameTime(
            long gameTime
    ) {
        return gameTime <= 0L
                ? "never"
                : gameTime
                  + " ticks ("
                  + String.format(
                Locale.US,
                "%.2f Minecraft days",
                (double) gameTime / 24_000.0D
        )
                  + ")";
    }

    private static String formatTicks(
            long ticks
    ) {
        return ticks
                + " ticks ("
                + String.format(
                Locale.US,
                "%.2f Minecraft days",
                (double) ticks / 24_000.0D
        )
                + ")";
    }

    private static String format(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.1f",
                !Double.isFinite(value) || value < 0.0D
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
        MutableComponent message = Component.literal(label + ": ")
                .withStyle(ChatFormatting.GRAY);

        message.append(
                Component.literal(value == null ? "" : value)
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