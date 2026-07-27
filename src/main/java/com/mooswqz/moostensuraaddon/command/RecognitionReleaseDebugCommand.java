package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionBalanceManager;
import com.mooswqz.moostensuraaddon.recognition.RecognitionBalanceSnapshot;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCommitRecord;
import com.mooswqz.moostensuraaddon.recognition.RecognitionReleasePolicy;
import com.mooswqz.moostensuraaddon.recognition.RecognitionReleaseValidationHarness;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStrengthRewardFormula;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Debug-only inspection and validation of the locked 1.4.0 recognition-strength policy.
 */
public final class RecognitionReleaseDebugCommand {

    private RecognitionReleaseDebugCommand() {
    }

    /**
     * /moostensura debug release
     * /moostensura debug release validate
     */
    public static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return Commands.literal("release")
                .requires(
                        DebugModeService::canUseDebugTools
                )
                .executes(context -> showPolicy(
                        context.getSource()
                ))
                .then(
                        Commands.literal("validate")
                                .executes(context -> validate(
                                        context.getSource()
                                ))
                );
    }

    private static int showPolicy(
            CommandSourceStack source
    ) {
        RecognitionBalanceManager.State state =
                RecognitionBalanceManager.getState();

        RecognitionBalanceSnapshot balance =
                state.snapshot();

        sendHeader(
                source,
                "Recognition Release Policy",
                ChatFormatting.LIGHT_PURPLE
        );

        sendValue(
                source,
                "Policy",
                RecognitionReleasePolicy.DISPLAY_NAME
                        + " ("
                        + RecognitionReleasePolicy.POLICY_ID
                        + ", version "
                        + RecognitionReleasePolicy.POLICY_VERSION
                        + ")",
                ChatFormatting.AQUA
        );

        sendValue(
                source,
                "Result / rules / reward versions",
                RecognitionCommitRecord.CURRENT_RESULT_VERSION
                        + " / "
                        + RecognitionCommitRecord.CURRENT_RULES_VERSION
                        + " / "
                        + RecognitionCommitRecord
                        .CURRENT_REWARD_PROFILE_VERSION,
                ChatFormatting.WHITE
        );

        sendHeader(
                source,
                "Reward decision",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Attribute modifiers",
                yesNo(
                        RecognitionReleasePolicy
                                .grantsAttributeModifiers()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Combat multipliers",
                yesNo(
                        RecognitionReleasePolicy
                                .grantsCombatMultipliers()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Legacy-based power",
                yesNo(
                        RecognitionReleasePolicy
                                .grantsHistoryModifierPower()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Base / maximum combined strength",
                formatPercent(
                        RecognitionStrengthRewardFormula.BASE_REWARD
                )
                        + " / "
                        + formatPercent(
                        RecognitionStrengthRewardFormula
                                .maximumReward(false)
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Pure maximum strength",
                formatPercent(
                        RecognitionStrengthRewardFormula
                                .maximumReward(true)
                ),
                ChatFormatting.WHITE
        );

        source.sendSuccess(
                () -> Component.literal(
                                RecognitionReleasePolicy
                                        .rewardProfileSummary()
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        sendHeader(
                source,
                "Awakening decision",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "True Hero required for Pure Good",
                yesNo(
                        RecognitionReleasePolicy
                                .requiresTrueHeroForPureGood()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "True Demon Lord required for Pure Evil",
                yesNo(
                        RecognitionReleasePolicy
                                .requiresTrueDemonLordForPureEvil()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Awakening required for True Neutral",
                yesNo(
                        RecognitionReleasePolicy
                                .requiresAwakeningForTrueNeutral()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "True Hero directional modifier",
                format(
                        balance.good()
                                .trueHeroModifier()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "True Demon Lord directional modifier",
                format(
                        balance.evil()
                                .trueDemonLordModifier()
                ),
                ChatFormatting.WHITE
        );

        source.sendSuccess(
                () -> Component.literal(
                                RecognitionReleasePolicy
                                        .awakeningPolicySummary()
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        sendHeader(
                source,
                "Active balance",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Source",
                balance.sourceId(),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Revision",
                Long.toString(state.revision()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Established / Pure",
                format(
                        balance.selection()
                                .establishedThreshold()
                )
                        + " / "
                        + format(
                        balance.selection()
                                .pureThreshold()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Final / raw dominance",
                format(
                        balance.selection()
                                .dominanceRatio()
                )
                        + "x / "
                        + format(
                        balance.selection()
                                .rawDominanceRatio()
                )
                        + "x",
                ChatFormatting.WHITE
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Use /moostensura debug release validate to run the complete release audit."
                        )
                        .withStyle(ChatFormatting.DARK_AQUA),
                false
        );

        return 1;
    }

    private static int validate(
            CommandSourceStack source
    ) {
        RecognitionReleaseValidationHarness.Report report =
                RecognitionReleaseValidationHarness.validate();

        sendHeader(
                source,
                "Recognition Release Validation",
                ChatFormatting.LIGHT_PURPLE
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Read-only audit. No player, world, commitment, reward or datapack data was modified."
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        sendValue(
                source,
                "Policy",
                report.policyDisplayName()
                        + " ("
                        + report.policyId()
                        + ", version "
                        + report.policyVersion()
                        + ")",
                ChatFormatting.AQUA
        );

        sendValue(
                source,
                "Balance",
                report.balanceSource()
                        + " (revision "
                        + report.balanceRevision()
                        + ")",
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Canonical defaults",
                Boolean.toString(
                        report.canonicalDefaults()
                ),
                report.canonicalDefaults()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW
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

        sendHeader(
                source,
                "Release invariants",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Pure paths",
                report.purePaths()
                        + " / "
                        + report.totalPurePaths(),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Required adjacent crossings",
                report.requiredAdjacentCrosses()
                        + " / "
                        + report.totalRequiredAdjacentCrosses(),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Pure morality coverage",
                "Good "
                        + report.goodPurePaths()
                        + "/3, Neutral "
                        + report.neutralPurePaths()
                        + "/3, Evil "
                        + report.evilPurePaths()
                        + "/3",
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Identity-heavy Pure results",
                Integer.toString(
                        report.identityHeavyPurePaths()
                ),
                report.identityHeavyPurePaths() == 0
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW
        );

        sendValue(
                source,
                "Freedom ceiling",
                format(report.legacyFreedomCeiling())
                        + " -> "
                        + format(report.expandedFreedomCeiling()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Order ceiling",
                format(report.orderCeiling()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Freedom / Order ratio",
                formatPercent(
                        report.freedomOrderRatio()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Configured self-reliance",
                format(
                        report.configuredSelfReliancePoints()
                )
                        + " points",
                ChatFormatting.WHITE
        );

        sendHeader(
                source,
                "Checks",
                ChatFormatting.GOLD
        );

        for (RecognitionReleaseValidationHarness.Check check :
                report.checks()) {
            MutableComponent line =
                    Component.literal(
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
                    Component.literal(
                                    check.name()
                                            + " — "
                                            + check.detail()
                            )
                            .withStyle(ChatFormatting.WHITE)
            );

            source.sendSuccess(
                    () -> line,
                    false
            );
        }

        if (!report.warnings().isEmpty()) {
            sendHeader(
                    source,
                    "Audit notes",
                    ChatFormatting.GOLD
            );

            for (String warning : report.warnings()) {
                source.sendSuccess(
                        () -> Component.literal(
                                        "- " + warning
                                )
                                .withStyle(ChatFormatting.YELLOW),
                        false
                );
            }
        }

        source.sendSuccess(
                () -> Component.literal(
                                report.passed()
                                        ? "Recognition release validation PASS."
                                        : "Recognition release validation FAIL. Review the failed checks before release."
                        )
                        .withStyle(
                                report.passed()
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return report.passed()
                ? 1
                : 0;
    }

    private static void sendHeader(
            CommandSourceStack source,
            String text,
            ChatFormatting color
    ) {
        MutableComponent header =
                Component.literal(text)
                        .withStyle(
                                color,
                                ChatFormatting.BOLD
                        );

        source.sendSuccess(
                () -> header,
                false
        );
    }

    private static void sendValue(
            CommandSourceStack source,
            String label,
            String value,
            ChatFormatting valueColor
    ) {
        MutableComponent line =
                Component.literal(label + ": ")
                        .withStyle(ChatFormatting.GRAY);

        line.append(
                Component.literal(value)
                        .withStyle(valueColor)
        );

        source.sendSuccess(
                () -> line,
                false
        );
    }

    private static String yesNo(
            boolean value
    ) {
        return value
                ? "yes"
                : "no";
    }

    private static String format(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.2f",
                Double.isFinite(value)
                        ? value
                        : 0.0D
        );
    }

    private static String formatPercent(
            double value
    ) {
        return String.format(
                Locale.US,
                "%.1f%%",
                Double.isFinite(value)
                        ? value * 100.0D
                        : 0.0D
        );
    }
}