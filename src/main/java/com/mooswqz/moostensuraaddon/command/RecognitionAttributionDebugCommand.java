package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.recognition.CivilianDefenseTracker;
import com.mooswqz.moostensuraaddon.recognition.RecognitionAttributionValidationHarness;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCombatCreditTracker;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStatKeys;
import com.mooswqz.moostensuraaddon.recognition.RecognitionSubordinateCombatTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Controlled attribution and new-incarnation regression fixtures. */
public final class RecognitionAttributionDebugCommand {

    private static final int MAX_PENDING_RESET_FIXTURES = 64;
    private static final long RESET_FIXTURE_LIFETIME_NANOS =
            10L * 60L * 1_000_000_000L;

    private static final String[] RESET_COUNTER_KEYS = {
            RecognitionStatKeys.CIVILIANS_DEFENDED,
            RecognitionStatKeys.CIVILIAN_KILLS,
            RecognitionStatKeys.PASSIVE_BABY_KILLS,
            RecognitionStatKeys.OWNED_COMPANION_KILLS,
            RecognitionStatKeys.OWNED_SUBORDINATE_KILLS,
            RecognitionStatKeys.SUBORDINATE_ASSISTED_MAJOR_VICTORIES
    };

    private static final String[] RESET_COLLECTION_KEYS = {
            RecognitionStatKeys.MAJOR_ENEMY_TYPES_DEFEATED,
            RecognitionStatKeys.SOLO_MAJOR_ENEMY_TYPES_DEFEATED,
            RecognitionStatKeys.MALEVOLENT_BOSS_TYPES_DEFEATED,
            RecognitionStatKeys.BENEVOLENT_BOSS_TYPES_KILLED
    };

    private static final Map<UUID, ResetFixture>
            RESET_FIXTURES = new ConcurrentHashMap<>();

    private RecognitionAttributionDebugCommand() {
    }

    public static void forgetResetFixture(UUID playerUuid) {
        if (playerUuid != null) {
            RESET_FIXTURES.remove(playerUuid);
        }
    }

    public static void clearResetFixtures() {
        RESET_FIXTURES.clear();
    }

    public static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return Commands.literal("attribution")
                .requires(DebugModeService::canUseDebugTools)
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
                        Commands.literal("reset")
                                .requires(
                                        DebugModeService
                                                ::canUseDangerousDebugTools
                                )
                                .executes(context -> requestResetFixture(
                                        context.getSource()
                                ))
                                .then(
                                        Commands.literal("confirm")
                                                .executes(context -> installResetFixture(
                                                        context.getSource()
                                                ))
                                )
                                .then(
                                        Commands.literal("probe")
                                                .executes(context -> probeResetFixture(
                                                        context.getSource()
                                                ))
                                )
                );
    }

    private static int inspect(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        RecognitionCombatCreditTracker.RuntimeSnapshot combat =
                RecognitionCombatCreditTracker.inspect(
                        source.getServer(),
                        player.getUUID()
                );

        RecognitionSubordinateCombatTracker.RuntimeSnapshot subordinate =
                RecognitionSubordinateCombatTracker.inspect(
                        player.getUUID()
                );

        CivilianDefenseTracker.RuntimeSnapshot civilian =
                CivilianDefenseTracker.inspect(
                        source.getServer()
                );

        sendHeader(source, "Recognition Attribution Runtime");
        sendValue(
                source,
                "Recent combat credits",
                combat.recentCredits()
                        + " / "
                        + combat.maximumRecentCredits()
        );
        sendValue(
                source,
                "Selected-player credits",
                Integer.toString(combat.selectedPlayerCredits())
        );
        sendValue(
                source,
                "Processed death guards",
                combat.processedDeaths()
                        + " / "
                        + combat.maximumProcessedDeaths()
        );
        sendValue(
                source,
                "Subordinate participation",
                subordinate.activeMajorEnemies()
                        + " / "
                        + subordinate.maximumActiveMajorEnemies()
                        + " (player: "
                        + subordinate.selectedOwnerRecords()
                        + ")"
        );
        sendValue(
                source,
                "Civilian encounters",
                civilian.activeAggressors()
                        + " / "
                        + civilian.maximumActiveAggressors()
        );
        sendValue(
                source,
                "Environmental credit window",
                RecognitionCombatCreditTracker.creditWindowTicks()
                        + " ticks"
        );
        sendValue(
                source,
                "Duplicate-death window",
                RecognitionCombatCreditTracker.duplicateWindowTicks()
                        + " ticks"
        );

        return 1;
    }

    private static int validate(
            CommandSourceStack source
    ) {
        RecognitionAttributionValidationHarness.Report report =
                RecognitionAttributionValidationHarness.validate();

        sendHeader(source, "Recognition Attribution Validation");

        for (RecognitionAttributionValidationHarness.Check check :
                report.checks()) {
            sendCheck(
                    source,
                    check.passed(),
                    check.name(),
                    check.detail()
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

        return report.passed() ? 1 : 0;
    }

    private static int requestResetFixture(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        AdminConfirmationTracker.arm(
                source,
                AdminConfirmationTracker.Action
                        .INSTALL_ATTRIBUTION_RESET_FIXTURE,
                player.getUUID()
        );

        source.sendFailure(
                Component.literal(
                                "This installs artificial life-bound recognition counters and runtime attribution state. Use only in a disposable test world."
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        )
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Run /moostensura debug attribution reset confirm within 30 seconds."
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        return 1;
    }

    private static int installResetFixture(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AdminConfirmationTracker.Result confirmation =
                AdminConfirmationTracker.consume(
                        source,
                        AdminConfirmationTracker.Action
                                .INSTALL_ATTRIBUTION_RESET_FIXTURE,
                        player.getUUID()
                );

        if (!confirmation.confirmed()) {
            return rejectConfirmation(source, confirmation);
        }

        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        if (data.isWriteBlockedByFutureVersion()) {
            source.sendFailure(
                    Component.literal(
                                    "Future recognition data is preserved read-only."
                            )
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        AddonIncarnationState.Snapshot lifecycle =
                AddonIncarnationState.inspect(player);

        for (int index = 0;
             index < RESET_COUNTER_KEYS.length;
             index++) {
            data.setCounter(
                    RESET_COUNTER_KEYS[index],
                    index + 2
            );
        }

        for (String key : RESET_COLLECTION_KEYS) {
            data.addUniqueValue(
                    key,
                    "moostensuraaddon:reset_fixture"
            );
        }

        data.setString(
                RecognitionStatKeys.LAST_EVIL_DEED_GAME_TIME,
                Long.toString(
                        player.getServer().overworld().getGameTime()
                )
        );

        RecognitionCombatCreditTracker.installResetFixture(player);
        RecognitionSubordinateCombatTracker.installResetFixture(player);
        CivilianDefenseTracker.installResetFixture(
                player.getServer()
        );

        pruneResetFixtures();

        RESET_FIXTURES.put(
                player.getUUID(),
                new ResetFixture(
                        lifecycle.lifeToken(),
                        lifecycle.resetSequence(),
                        System.nanoTime()
                )
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Attribution reset fixture installed. Use Tensura's Character Reset Scroll normally, then run /moostensura debug attribution reset probe."
                        )
                        .withStyle(
                                ChatFormatting.GREEN,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return 1;
    }

    private static int probeResetFixture(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResetFixture fixture = RESET_FIXTURES.get(
                player.getUUID()
        );

        if (fixture == null) {
            source.sendFailure(
                    Component.literal(
                                    "No attribution reset fixture is awaiting a probe."
                            )
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );
        AddonIncarnationState.Snapshot lifecycle =
                AddonIncarnationState.inspect(player);
        RecognitionCombatCreditTracker.RuntimeSnapshot combat =
                RecognitionCombatCreditTracker.inspect(
                        source.getServer(),
                        player.getUUID()
                );
        RecognitionSubordinateCombatTracker.RuntimeSnapshot subordinate =
                RecognitionSubordinateCombatTracker.inspect(
                        player.getUUID()
                );
        CivilianDefenseTracker.RuntimeSnapshot civilian =
                CivilianDefenseTracker.inspect(
                        source.getServer()
                );

        List<ProbeCheck> checks = new ArrayList<>();

        checks.add(new ProbeCheck(
                "Life token changed",
                !fixture.lifeToken().equals(lifecycle.lifeToken())
        ));
        checks.add(new ProbeCheck(
                "Reset sequence increased",
                lifecycle.resetSequence() > fixture.resetSequence()
        ));
        checks.add(new ProbeCheck(
                "Recognition incarnation matches new life",
                lifecycle.lifeToken().equals(data.getIncarnationId())
        ));
        checks.add(new ProbeCheck(
                "Life-bound counters cleared",
                allCountersAreZero(data)
        ));
        checks.add(new ProbeCheck(
                "Life-bound collections cleared",
                allCollectionsAreEmpty(data)
        ));
        checks.add(new ProbeCheck(
                "Identity-history timestamps cleared",
                data.getString(
                        RecognitionStatKeys.LAST_EVIL_DEED_GAME_TIME
                ).isBlank()
        ));
        checks.add(new ProbeCheck(
                "Recent player credit cleared",
                combat.selectedPlayerCredits() == 0
        ));
        checks.add(new ProbeCheck(
                "Subordinate participation cleared",
                subordinate.selectedOwnerRecords() == 0
        ));
        checks.add(new ProbeCheck(
                "Civilian encounter window cleared",
                civilian.activeAggressors() == 0
        ));

        boolean cleanBeforeNewLifeWrite = checks.stream()
                .allMatch(ProbeCheck::passed);
        boolean newLifeIsolated = false;

        if (cleanBeforeNewLifeWrite) {
            data.incrementCounter(
                    RecognitionStatKeys.CIVILIANS_DEFENDED
            );

            newLifeIsolated = data.getCounter(
                    RecognitionStatKeys.CIVILIANS_DEFENDED
            ) == 1
                    && data.getCounter(
                    RecognitionStatKeys.CIVILIAN_KILLS
            ) == 0
                    && allCollectionsAreEmpty(data);

            data.setCounter(
                    RecognitionStatKeys.CIVILIANS_DEFENDED,
                    0
            );
        }

        checks.add(new ProbeCheck(
                "New-life progress remains isolated",
                newLifeIsolated
        ));

        boolean passed = checks.stream()
                .allMatch(ProbeCheck::passed);

        sendHeader(source, "Attribution Character Reset Probe");

        for (ProbeCheck check : checks) {
            sendCheck(
                    source,
                    check.passed(),
                    check.name(),
                    check.passed()
                            ? "Invariant satisfied."
                            : "Invariant failed."
            );
        }

        source.sendSuccess(
                () -> Component.literal(
                                passed
                                        ? "Attribution and reincarnation fixture PASS."
                                        : "Attribution and reincarnation fixture FAIL."
                        )
                        .withStyle(
                                passed
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        if (passed) {
            RESET_FIXTURES.remove(player.getUUID());
        }

        return passed ? 1 : 0;
    }

    private static boolean allCountersAreZero(
            RecognitionData data
    ) {
        for (String key : RESET_COUNTER_KEYS) {
            if (data.getCounter(key) != 0) {
                return false;
            }
        }

        return true;
    }

    private static void pruneResetFixtures() {
        long now = System.nanoTime();

        RESET_FIXTURES.entrySet().removeIf(
                entry -> now - entry.getValue().installedNanos()
                        > RESET_FIXTURE_LIFETIME_NANOS
        );

        while (RESET_FIXTURES.size()
                >= MAX_PENDING_RESET_FIXTURES) {
            UUID oldest = RESET_FIXTURES.entrySet()
                    .stream()
                    .min(
                            java.util.Comparator.comparingLong(
                                    entry -> entry.getValue()
                                            .installedNanos()
                            )
                    )
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (oldest == null) {
                break;
            }

            RESET_FIXTURES.remove(oldest);
        }
    }

    private static boolean allCollectionsAreEmpty(
            RecognitionData data
    ) {
        for (String key : RESET_COLLECTION_KEYS) {
            if (data.getUniqueValueCount(key) != 0) {
                return false;
            }
        }

        return true;
    }

    private static int rejectConfirmation(
            CommandSourceStack source,
            AdminConfirmationTracker.Result confirmation
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
                                        + " Run /moostensura debug attribution reset first."
                        )
                        .withStyle(ChatFormatting.RED)
        );

        return 0;
    }

    private static void sendCheck(
            CommandSourceStack source,
            boolean passed,
            String name,
            String detail
    ) {
        source.sendSuccess(
                () -> Component.literal(
                                (passed ? "[PASS] " : "[FAIL] ")
                                        + name
                                        + " — "
                                        + detail
                        )
                        .withStyle(
                                passed
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.RED
                        ),
                false
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

    private record ResetFixture(
            String lifeToken,
            int resetSequence,
            long installedNanos
    ) {
    }

    private record ProbeCheck(
            String name,
            boolean passed
    ) {
    }
}
