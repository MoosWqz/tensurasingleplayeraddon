package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.attachment.RecognitionDataFixtureFactory;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.lifecycle.RecognitionNativeEndowmentService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCommittedResult;
import com.mooswqz.moostensuraaddon.recognition.RecognitionDisplayNameSyncService;
import com.mooswqz.moostensuraaddon.util.TensuraPlayerStateHelper;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** Permission-level-four artificial save and retry fixtures for B2B2. */
public final class RecognitionMigrationDebugCommand {

    private static final int RETRY_FIXTURE_ATTEMPTS = 7;

    private RecognitionMigrationDebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return Commands.literal("migration")
                .requires(
                        DebugModeService
                                ::canUseDangerousDebugTools
                )
                .executes(context -> inspect(
                        context.getSource()
                ))
                .then(
                        Commands.literal("validate")
                                .executes(context -> validate(
                                        context.getSource()
                                ))
                )
                .then(
                        Commands.literal("legacy")
                                .executes(context -> requestLegacyFixture(
                                        context.getSource()
                                ))
                                .then(
                                        Commands.literal("confirm")
                                                .executes(context -> installLegacyFixture(
                                                        context.getSource()
                                                ))
                                )
                )
                .then(
                        Commands.literal("future")
                                .executes(context -> requestFutureFixture(
                                        context.getSource()
                                ))
                                .then(
                                        Commands.literal("confirm")
                                                .executes(context -> installFutureFixture(
                                                        context.getSource()
                                                ))
                                )
                                .then(
                                        Commands.literal("status")
                                                .executes(context -> inspectFutureFixture(
                                                        context.getSource()
                                                ))
                                )
                )
                .then(
                        Commands.literal("retry")
                                .executes(context -> inspect(
                                        context.getSource()
                                ))
                                .then(
                                        Commands.literal("probe")
                                                .executes(context -> probeRetryIdempotence(
                                                        context.getSource()
                                                ))
                                )
                                .then(
                                        Commands.literal("due")
                                                .executes(context -> runDueRetry(
                                                        context.getSource()
                                                ))
                                )
                                .then(
                                        Commands.literal("hold")
                                                .executes(context -> holdRetryForReset(
                                                        context.getSource()
                                                ))
                                )
                );
    }

    private static int inspect(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );
        RecognitionCommittedResult committed =
                data.getCommittedResult();
        AddonIncarnationState.Snapshot lifecycle =
                AddonIncarnationState.inspect(player);
        long retryRemaining = Math.max(
                0L,
                lifecycle.nativeEndowmentNextAttemptEpochMillis()
                        - System.currentTimeMillis()
        );
        RecognitionDataFixtureFactory.FutureInspection future =
                RecognitionDataFixtureFactory.inspectFutureFixture(data);

        sendHeader(source, "Recognition Migration / Retry Fixture");
        sendValue(
                source,
                "Data version",
                Integer.toString(data.getDataVersion())
        );
        sendValue(
                source,
                "Write-blocked by future version",
                yesNo(data.isWriteBlockedByFutureVersion())
        );
        sendValue(
                source,
                "Committed migration state",
                committed.migrationState().displayName()
        );
        sendValue(
                source,
                "Path / title",
                committed.pathSummary()
                        + " / "
                        + display(committed.bestowedTitle())
        );
        sendValue(
                source,
                "Recognition incarnation",
                display(committed.incarnationId())
        );
        sendValue(
                source,
                "Payload fingerprint",
                future.actualFingerprint()
        );
        sendValue(
                source,
                "Life token / reset sequence",
                lifecycle.lifeToken()
                        + " / "
                        + lifecycle.resetSequence()
        );
        sendValue(
                source,
                "Native retry attempts / remaining",
                lifecycle.nativeEndowmentAttempts()
                        + " / "
                        + formatSeconds(retryRemaining)
        );
        sendValue(
                source,
                "Native marker",
                display(lifecycle.nativeEndowmentIncarnation())
        );
        sendValue(
                source,
                "Native named / EP",
                yesNo(
                        TensuraPlayerStateHelper
                                .isNamedOrEndowed(player)
                )
                        + " / "
                        + formatEp(readEp(player))
        );

        return 1;
    }

    private static int validate(
            CommandSourceStack source
    ) {
        RecognitionDataFixtureFactory.ValidationReport report =
                RecognitionDataFixtureFactory.validate();

        sendHeader(source, "Recognition Migration Fixture Validation");

        for (RecognitionDataFixtureFactory.Check check :
                report.checks()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    (check.passed()
                                            ? "[PASS] "
                                            : "[FAIL] ")
                                            + check.name()
                                            + " — "
                                            + check.detail()
                            )
                            .withStyle(
                                    check.passed()
                                            ? ChatFormatting.GREEN
                                            : ChatFormatting.RED
                            ),
                    false
            );
        }

        sendValue(
                source,
                "Checks",
                report.passedChecks()
                        + " passed / "
                        + report.failedChecks()
                        + " failed"
        );

        source.sendSuccess(
                () -> Component.literal(
                                report.passed()
                                        ? "Migration fixture validation PASS."
                                        : "Migration fixture validation FAIL."
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

    private static int requestLegacyFixture(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        AdminConfirmationTracker.arm(
                source,
                AdminConfirmationTracker.Action
                        .INSTALL_LEGACY_MIGRATION_FIXTURE,
                player.getUUID()
        );

        sendFixtureWarning(
                source,
                "LEGACY V1 + RETRY",
                "This replaces the addon's recognition attachment with an artificial committed v1 payload and a capped 60-second native-endowment retry.",
                "/moostensura debug migration legacy confirm"
        );

        return 1;
    }

    private static int installLegacyFixture(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AdminConfirmationTracker.Result confirmation =
                AdminConfirmationTracker.consume(
                        source,
                        AdminConfirmationTracker.Action
                                .INSTALL_LEGACY_MIGRATION_FIXTURE,
                        player.getUUID()
                );

        if (!confirmation.confirmed()) {
            return sendConfirmationFailure(
                    source,
                    confirmation,
                    "/moostensura debug migration legacy"
            );
        }

        if (TensuraPlayerStateHelper.isNamedOrEndowed(player)) {
            source.sendFailure(
                    Component.literal(
                                    "The legacy fixture requires an unnamed player in a disposable world. Clear native naming first."
                            )
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        RecognitionData fixture =
                RecognitionDataFixtureFactory
                        .createUnmigratedLegacyCommitted();

        player.setData(
                AttachmentRegistry.RECOGNITION_DATA,
                fixture
        );

        AddonIncarnationState.load(player)
                .prepareNativeEndowmentRetryFixture(
                        RETRY_FIXTURE_ATTEMPTS,
                        System.currentTimeMillis()
                );

        source.sendSuccess(
                () -> Component.literal(
                                "Installed unmigrated recognition v1 and capped retry fixture."
                        )
                        .withStyle(
                                ChatFormatting.GREEN,
                                ChatFormatting.BOLD
                        ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Immediately use Save and Quit to Title, then reopen this world before the 60-second retry deadline."
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "After reload: /moostensura debug migration and /moostensura debug migration retry probe"
                        )
                        .withStyle(ChatFormatting.AQUA),
                false
        );

        return 1;
    }

    private static int requestFutureFixture(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        AdminConfirmationTracker.arm(
                source,
                AdminConfirmationTracker.Action
                        .INSTALL_FUTURE_MIGRATION_FIXTURE,
                player.getUUID()
        );

        sendFixtureWarning(
                source,
                "UNKNOWN FUTURE VERSION",
                "This replaces the addon's recognition attachment with a future schema/result/rules/reward payload containing exact sentinels.",
                "/moostensura debug migration future confirm"
        );

        return 1;
    }

    private static int installFutureFixture(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AdminConfirmationTracker.Result confirmation =
                AdminConfirmationTracker.consume(
                        source,
                        AdminConfirmationTracker.Action
                                .INSTALL_FUTURE_MIGRATION_FIXTURE,
                        player.getUUID()
                );

        if (!confirmation.confirmed()) {
            return sendConfirmationFailure(
                    source,
                    confirmation,
                    "/moostensura debug migration future"
            );
        }

        player.setData(
                AttachmentRegistry.RECOGNITION_DATA,
                RecognitionDataFixtureFactory
                        .createFutureVersionFixture()
        );

        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(player);

        source.sendSuccess(
                () -> Component.literal(
                                "Installed the exact unknown-future recognition fixture."
                        )
                        .withStyle(
                                ChatFormatting.GREEN,
                                ChatFormatting.BOLD
                        ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Use /moostensura debug migration future status now and after a save/reload. Both fingerprints must match."
                        )
                        .withStyle(ChatFormatting.AQUA),
                false
        );

        return inspectFutureFixture(source);
    }

    private static int inspectFutureFixture(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );
        RecognitionDataFixtureFactory.FutureInspection inspection =
                RecognitionDataFixtureFactory
                        .inspectFutureFixture(data);

        sendHeader(source, "Unknown Future Recognition Fixture");
        sendValue(
                source,
                "Expected fingerprint",
                inspection.expectedFingerprint()
        );
        sendValue(
                source,
                "Actual fingerprint",
                inspection.actualFingerprint()
        );
        sendValue(
                source,
                "Exact payload match",
                yesNo(inspection.exactMatch())
        );
        sendValue(
                source,
                "Write-blocked",
                yesNo(inspection.writeBlocked())
        );

        source.sendSuccess(
                () -> Component.literal(
                                (inspection.passed()
                                        ? "[PASS] "
                                        : "[FAIL] ")
                                        + inspection.detail()
                        )
                        .withStyle(
                                inspection.passed()
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return inspection.passed() ? 1 : 0;
    }

    private static int probeRetryIdempotence(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AddonIncarnationState.Snapshot before =
                AddonIncarnationState.inspect(player);
        double beforeEp = readEp(player);

        for (int attempt = 0; attempt < 3; attempt++) {
            RecognitionNativeEndowmentService.synchronize(player);
        }

        AddonIncarnationState.Snapshot after =
                AddonIncarnationState.inspect(player);
        double afterEp = readEp(player);

        boolean unchanged = before.nativeEndowmentAttempts()
                == after.nativeEndowmentAttempts()
                && before.nativeEndowmentNextAttemptEpochMillis()
                == after.nativeEndowmentNextAttemptEpochMillis()
                && before.nativeEndowmentIncarnation().equals(
                after.nativeEndowmentIncarnation()
        )
                && Double.compare(beforeEp, afterEp) == 0;

        sendHeader(source, "Native Endowment Retry Probe");
        sendValue(
                source,
                "Attempts before / after",
                before.nativeEndowmentAttempts()
                        + " / "
                        + after.nativeEndowmentAttempts()
        );
        sendValue(
                source,
                "Marker before / after",
                display(before.nativeEndowmentIncarnation())
                        + " / "
                        + display(after.nativeEndowmentIncarnation())
        );
        sendValue(
                source,
                "EP delta across three synchronizations",
                formatSignedEp(afterEp - beforeEp)
        );
        source.sendSuccess(
                () -> Component.literal(
                                unchanged
                                        ? "[PASS] Repeated synchronization was idempotent and non-spamming."
                                        : "[FAIL] Retry state or EP changed during the probe."
                        )
                        .withStyle(
                                unchanged
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return unchanged ? 1 : 0;
    }

    private static int runDueRetry(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        if (!data.isNamingCommitted()
                || data.isWriteBlockedByFutureVersion()) {
            source.sendFailure(
                    Component.literal(
                                    "A current committed recognition fixture is required."
                            )
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        AddonIncarnationState state =
                AddonIncarnationState.load(player);
        double beforeEp = readEp(player);

        state.makeNativeEndowmentRetryDueForFixture();
        RecognitionNativeEndowmentService.synchronize(player);

        AddonIncarnationState.Snapshot after =
                AddonIncarnationState.inspect(player);
        double afterEp = readEp(player);
        String recognitionIncarnation = data.getIncarnationId().isBlank()
                ? after.lifeToken()
                : data.getIncarnationId();
        boolean completed = TensuraPlayerStateHelper
                .isNamedOrEndowed(player)
                && !recognitionIncarnation.isBlank()
                && recognitionIncarnation.equals(
                after.nativeEndowmentIncarnation()
        )
                && after.nativeEndowmentAttempts() == 0
                && after.nativeEndowmentNextAttemptEpochMillis() == 0L;

        sendHeader(source, "Forced-Due Native Endowment Retry");
        sendValue(
                source,
                "Native marker",
                display(after.nativeEndowmentIncarnation())
        );
        sendValue(
                source,
                "Failed attempts / next deadline",
                after.nativeEndowmentAttempts()
                        + " / "
                        + after.nativeEndowmentNextAttemptEpochMillis()
        );
        sendValue(
                source,
                "EP delta",
                formatSignedEp(afterEp - beforeEp)
        );
        source.sendSuccess(
                () -> Component.literal(
                                completed
                                        ? "[PASS] Retry completed and established the exactly-once marker."
                                        : "[FAIL] Retry did not reach the completed anchored state."
                        )
                        .withStyle(
                                completed
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return completed ? 1 : 0;
    }

    private static int holdRetryForReset(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        if (!data.isNamingCommitted()
                || data.isWriteBlockedByFutureVersion()) {
            source.sendFailure(
                    Component.literal(
                                    "A current committed recognition record is required."
                            )
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        AddonIncarnationState.load(player)
                .prepareNativeEndowmentRetryFixture(
                        RETRY_FIXTURE_ATTEMPTS,
                        System.currentTimeMillis()
                );

        source.sendSuccess(
                () -> Component.literal(
                                "Installed a capped pending retry and removed the old exactly-once marker."
                        )
                        .withStyle(
                                ChatFormatting.GREEN,
                                ChatFormatting.BOLD
                        ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Use Tensura's Character Reset Scroll now. After reset, lifecycle attempts/deadline/marker and the old recognition must all be cleared."
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        return inspect(source);
    }

    private static void sendFixtureWarning(
            CommandSourceStack source,
            String fixtureName,
            String description,
            String confirmationCommand
    ) {
        source.sendSuccess(
                () -> Component.literal(
                                fixtureName + " FIXTURE CONFIRMATION REQUIRED"
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(description)
                        .withStyle(ChatFormatting.YELLOW),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Use only in a backed-up disposable test world."
                        )
                        .withStyle(ChatFormatting.RED),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Run "
                                        + confirmationCommand
                                        + " within "
                                        + AdminConfirmationTracker
                                        .CONFIRMATION_WINDOW_SECONDS
                                        + " seconds."
                        )
                        .withStyle(ChatFormatting.AQUA),
                false
        );
    }

    private static int sendConfirmationFailure(
            CommandSourceStack source,
            AdminConfirmationTracker.Result confirmation,
            String requestCommand
    ) {
        String explanation = switch (confirmation.status()) {
            case EXPIRED -> "The fixture confirmation expired.";
            case DIFFERENT_ACTION ->
                    "A different destructive action was awaiting confirmation.";
            case DIFFERENT_TARGET ->
                    "The fixture confirmation belonged to another player.";
            case MISSING, CONFIRMED ->
                    "No matching fixture confirmation exists.";
        };

        source.sendFailure(
                Component.literal(
                                explanation
                                        + " Run "
                                        + requestCommand
                                        + " first."
                        )
                        .withStyle(ChatFormatting.RED)
        );
        return 0;
    }

    private static double readEp(
            ServerPlayer player
    ) {
        IExistence existence = TensuraStorages.getExistenceFrom(player);
        return existence == null
                ? 0.0D
                : Math.max(0.0D, existence.getEP());
    }

    private static String yesNo(
            boolean value
    ) {
        return value ? "Yes" : "No";
    }

    private static String display(
            String value
    ) {
        return value == null || value.isBlank()
                ? "none"
                : value.trim();
    }

    private static String formatSeconds(
            long millis
    ) {
        return String.format(
                Locale.US,
                "%.1f seconds",
                Math.max(0L, millis) / 1_000.0D
        );
    }

    private static String formatEp(
            double value
    ) {
        return String.format(
                Locale.US,
                "%,.0f EP",
                Math.max(0.0D, value)
        );
    }

    private static String formatSignedEp(
            double value
    ) {
        return String.format(
                Locale.US,
                "%+,.0f EP",
                value
        );
    }

    private static void sendHeader(
            CommandSourceStack source,
            String text
    ) {
        source.sendSuccess(
                () -> Component.literal(text)
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                false
        );
    }

    private static void sendValue(
            CommandSourceStack source,
            String label,
            String value
    ) {
        MutableComponent message =
                Component.literal(label + ": ")
                        .withStyle(ChatFormatting.GRAY);

        message.append(
                Component.literal(value)
                        .withStyle(ChatFormatting.WHITE)
        );

        source.sendSuccess(() -> message, false);
    }
}
