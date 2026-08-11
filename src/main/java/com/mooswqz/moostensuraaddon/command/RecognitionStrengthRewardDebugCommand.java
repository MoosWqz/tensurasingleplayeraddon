package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionEndowmentEffortRewardSnapshot;
import com.mooswqz.moostensuraaddon.recognition.RecognitionEndowmentEffortRewardService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStrengthRewardSnapshot;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStrengthRewardService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStrengthRewardValidationHarness;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** Debug-only readout and deterministic validation for reward profile 2. */
public final class RecognitionStrengthRewardDebugCommand {

    private RecognitionStrengthRewardDebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createDebugNode() {
        return Commands.literal("strength")
                .requires(DebugModeService::canUseDebugTools)
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
                        Commands.argument("player", EntityArgument.player())
                                .executes(context -> inspect(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player")
                                ))
                );
    }

    private static int inspect(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        RecognitionStrengthRewardSnapshot snapshot =
                RecognitionStrengthRewardService.inspect(target);

        RecognitionEndowmentEffortRewardSnapshot endowment =
                RecognitionEndowmentEffortRewardService.inspect(
                        target
                );

        sendHeader(source, "Recognition Strength: "
                + target.getGameProfile().getName(), ChatFormatting.LIGHT_PURPLE);

        sendValue(source, "Committed", yesNo(snapshot.recognitionCommitted()));
        sendValue(source, "Committed result valid", yesNo(snapshot.committedResultValid()));
        sendValue(source, "Reward profile", snapshot.storedRewardProfileVersion() + " / current 2");
        sendValue(source, "Metadata initialized", yesNo(snapshot.rewardMetadataInitialized()));
        sendValue(source, "Migration source", snapshot.migrationSource().isBlank() ? "none" : snapshot.migrationSource());
        sendValue(source, "Frozen identity strength",
                format(snapshot.frozenIdentityStrength()) + " / "
                        + format(snapshot.identityStrengthMaximum()));
        sendValue(source, "Permanent strength", formatPercent(snapshot.totalStrength()));
        sendValue(source, "Max health / attack damage",
                formatPercent(snapshot.expectedReward().maxHealthMultiplier()));
        sendValue(source, "Movement / attack speed",
                formatPercent(snapshot.expectedReward().movementSpeedMultiplier()));
        sendValue(source, "Knockback resistance",
                "+" + formatPercent(snapshot.expectedReward().knockbackResistanceAddition()));
        sendValue(source, "Attribute state matches", yesNo(snapshot.attributeStateMatches()));
        sendValue(source, "Endowment effort extension",
                formatWhole(endowment.expectedReward().extraEpAllowance()) + " EP");
        sendValue(source, "Magicule / aura capacity",
                "+" + formatWhole(endowment.expectedReward().energyIncreasePerPool()) + " each");
        sendValue(source, "Endowment attributes match",
                yesNoTitle(endowment.attributeStateMatches()));

        if (snapshot.futureProfilePreserved()) {
            source.sendSuccess(
                    () -> Component.literal("A future reward profile is stored and was preserved without mutation.")
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        for (String mismatch : snapshot.mismatchedAttributes()) {
            source.sendSuccess(
                    () -> Component.literal("- " + mismatch)
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        for (String mismatch : endowment.mismatchedAttributes()) {
            source.sendSuccess(
                    () -> Component.literal("- endowment " + mismatch)
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        source.sendSuccess(
                () -> Component.literal("Read-only inspection. The normal 40-tick synchronizer performs reconciliation.")
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        return 1;
    }

    private static int validate(CommandSourceStack source) {
        RecognitionStrengthRewardValidationHarness.Report report =
                RecognitionStrengthRewardValidationHarness.validate();

        sendHeader(source, "Recognition Strength Validation", ChatFormatting.LIGHT_PURPLE);

        for (RecognitionStrengthRewardValidationHarness.Check check : report.checks()) {
            source.sendSuccess(
                    () -> Component.literal((check.passed() ? "[PASS] " : "[FAIL] ")
                                    + check.name() + " — " + check.detail())
                            .withStyle(check.passed() ? ChatFormatting.GREEN : ChatFormatting.RED),
                    false
            );
        }

        sendValue(source, "Checks",
                report.passedChecks() + " passed / " + report.failedChecks() + " failed");

        source.sendSuccess(
                () -> Component.literal(report.passed()
                                ? "Recognition strength validation PASS."
                                : "Recognition strength validation FAIL.")
                        .withStyle(report.passed() ? ChatFormatting.GREEN : ChatFormatting.RED,
                                ChatFormatting.BOLD),
                false
        );

        return report.passed() ? 1 : 0;
    }

    private static void sendHeader(
            CommandSourceStack source,
            String text,
            ChatFormatting colour
    ) {
        source.sendSuccess(
                () -> Component.literal(text)
                        .withStyle(colour, ChatFormatting.BOLD),
                false
        );
    }

    private static void sendValue(
            CommandSourceStack source,
            String label,
            String value
    ) {
        MutableComponent message = Component.literal(label + ": ")
                .withStyle(ChatFormatting.GRAY);
        message.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
        source.sendSuccess(() -> message, false);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String yesNoTitle(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0D);
    }

    private static String formatWhole(double value) {
        return String.format(Locale.ROOT, "%,.0f", value);
    }
}
