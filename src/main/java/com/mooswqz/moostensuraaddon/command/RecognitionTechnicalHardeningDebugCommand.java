package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.CivilianDefenseTracker;
import com.mooswqz.moostensuraaddon.recognition.RecognitionProgressScreenService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionTechnicalHardeningValidationHarness;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Debug-only runtime-state inspector for Packet 6G.8. */
public final class RecognitionTechnicalHardeningDebugCommand {

    private RecognitionTechnicalHardeningDebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return Commands.literal("hardening")
                .requires(
                        DebugModeService::canUseDebugTools
                )
                .executes(context -> inspect(
                        context.getSource()
                ))
                .then(
                        Commands.literal("validate")
                                .executes(context -> validate(
                                        context.getSource()
                                ))
                );
    }

    private static int inspect(
            CommandSourceStack source
    ) {
        RecognitionProgressScreenService.RuntimeSnapshot ui =
                RecognitionProgressScreenService
                        .inspectRuntimeState();

        CivilianDefenseTracker.RuntimeSnapshot civilian =
                CivilianDefenseTracker.inspect(
                        source.getServer()
                );

        sendHeader(
                source,
                "Recognition Runtime Hardening"
        );

        sendValue(
                source,
                "Paths request states",
                ui.trackedPlayerStates()
                        + " / "
                        + ui.maximumTrackedPlayerStates()
        );

        sendValue(
                source,
                "Paths fresh builds in active window",
                ui.freshBuildsInCurrentWindow()
                        + " / "
                        + ui.maximumFreshBuildsPerWindow()
        );

        sendValue(
                source,
                "Civilian aggressor records",
                civilian.activeAggressors()
                        + " / "
                        + civilian.maximumActiveAggressors()
        );

        sendValue(
                source,
                "Damage-confirmed aggressors",
                Integer.toString(
                        civilian.damageConfirmedAggressors()
                )
        );

        sendValue(
                source,
                "Civilian tracker server states",
                Integer.toString(
                        civilian.trackedServerStates()
                )
        );

        sendValue(
                source,
                "Civilian last cleanup game time",
                Long.toString(
                        civilian.lastCleanupGameTime()
                )
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Runtime state is non-persistent and is cleared on logout/server lifecycle events."
                        )
                        .withStyle(
                                ChatFormatting.DARK_GRAY
                        ),
                false
        );

        return 1;
    }

    private static int validate(
            CommandSourceStack source
    ) {
        RecognitionTechnicalHardeningValidationHarness.Report report =
                RecognitionTechnicalHardeningValidationHarness
                        .validate();

        sendHeader(
                source,
                "Recognition Technical Hardening Validation"
        );

        for (RecognitionTechnicalHardeningValidationHarness.Check check :
                report.checks()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    (
                                            check.passed()
                                                    ? "[PASS] "
                                                    : "[FAIL] "
                                    )
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
                                        ? "Recognition technical hardening validation PASS."
                                        : "Recognition technical hardening validation FAIL."
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
                        .withStyle(
                                ChatFormatting.GRAY
                        );

        message.append(
                Component.literal(value)
                        .withStyle(
                                ChatFormatting.WHITE
                        )
        );

        source.sendSuccess(
                () -> message,
                false
        );
    }
}