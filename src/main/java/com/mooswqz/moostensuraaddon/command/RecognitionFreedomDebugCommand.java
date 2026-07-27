package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionFreedomProgressSnapshot;
import com.mooswqz.moostensuraaddon.recognition.RecognitionFreedomValidationHarness;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIndependenceMilestoneManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Debug-only, read-only diagnostics for Packet 6G.5 Freedom progression.
 */
public final class RecognitionFreedomDebugCommand {

    private static final int MAX_MILESTONES_SHOWN =
            40;

    private static final int MAX_INACTIVE_IDS_SHOWN =
            20;

    private RecognitionFreedomDebugCommand() {
    }

    /**
     * /moostensura debug freedom
     * /moostensura debug freedom &lt;player&gt;
     * /moostensura debug freedom validate
     */
    public static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return Commands.literal("freedom")
                .requires(
                        DebugModeService::canUseDebugTools
                )
                .executes(context -> inspect(
                        context.getSource(),
                        context.getSource()
                                .getPlayerOrException()
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
        RecognitionData data =
                target.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        RecognitionFreedomProgressSnapshot snapshot =
                RecognitionFreedomProgressSnapshot.inspect(data);

        RecognitionIndependenceMilestoneManager.State milestoneState =
                RecognitionIndependenceMilestoneManager.getState();

        sendHeader(
                source,
                "Freedom Progress: "
                        + target.getGameProfile().getName(),
                ChatFormatting.LIGHT_PURPLE
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Read-only inspection. No advancements were synchronized and no recognition data was modified."
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        sendHeader(
                source,
                "Production score",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Total Freedom",
                format(snapshot.totalFreedomScore())
                        + " / "
                        + format(snapshot.totalFreedomMaximum()),
                ChatFormatting.AQUA
        );

        sendValue(
                source,
                "Solo major victories",
                snapshot.soloMajorVictoryCount()
                        + " unique type(s) -> "
                        + format(snapshot.soloMajorVictoryScore())
                        + " / "
                        + format(snapshot.soloMajorVictoryMaximum()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Discovery milestones",
                snapshot.discoveryMilestoneCount()
                        + " unique milestone(s) -> "
                        + format(snapshot.discoveryMilestoneScore())
                        + " / "
                        + format(snapshot.discoveryMilestoneMaximum()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Self-reliance milestones",
                snapshot.activeIndependenceMilestoneCount()
                        + " active / "
                        + snapshot.storedIndependenceMilestoneCount()
                        + " stored -> "
                        + format(snapshot.activeIndependenceScore())
                        + " / "
                        + format(snapshot.activeIndependenceMaximum()),
                ChatFormatting.WHITE
        );

        sendHeader(
                source,
                "Definition state",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Balance source",
                snapshot.balanceSource(),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Balance revision",
                Long.toString(snapshot.balanceRevision()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Milestone revision",
                Long.toString(snapshot.milestoneRevision()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Definition files",
                Integer.toString(snapshot.milestoneSourceFileCount()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Configured milestones",
                Integer.toString(snapshot.configuredMilestoneCount()),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Definition fingerprint",
                shortFingerprint(snapshot.milestoneFingerprint()),
                ChatFormatting.DARK_AQUA
        );

        sendValue(
                source,
                "Player fingerprint",
                shortFingerprint(snapshot.storedMilestoneFingerprint()),
                snapshot.milestoneFingerprintCurrent()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW
        );

        sendValue(
                source,
                "Backfill state",
                snapshot.milestoneFingerprintCurrent()
                        ? "current"
                        : "pending configured-ID check on the next synchronization route",
                snapshot.milestoneFingerprintCurrent()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW
        );

        sendHeader(
                source,
                "Configured self-reliance milestones",
                ChatFormatting.GOLD
        );

        Set<String> earnedIds =
                new HashSet<>(
                        snapshot.activeMilestoneIds()
                );

        List<RecognitionIndependenceMilestoneManager.Milestone> milestones =
                milestoneState.milestones();

        int shown = Math.min(
                MAX_MILESTONES_SHOWN,
                milestones.size()
        );

        for (int index = 0; index < shown; index++) {
            RecognitionIndependenceMilestoneManager.Milestone milestone =
                    milestones.get(index);

            boolean earned =
                    earnedIds.contains(
                            milestone.id().toString()
                    );

            MutableComponent line =
                    Component.literal(
                                    earned
                                            ? "[earned] "
                                            : "[missing] "
                            )
                            .withStyle(
                                    earned
                                            ? ChatFormatting.GREEN
                                            : ChatFormatting.GRAY
                            );

            line.append(
                    Component.literal(
                                    milestone.id().toString()
                                            + " — "
                                            + format(milestone.points())
                                            + " points — advancement "
                                            + milestone.advancementId()
                            )
                            .withStyle(ChatFormatting.WHITE)
            );

            source.sendSuccess(
                    () -> line,
                    false
            );
        }

        if (milestones.size() > shown) {
            int hidden = milestones.size() - shown;

            source.sendSuccess(
                    () -> Component.literal(
                                    "... "
                                            + hidden
                                            + " additional configured milestone(s) omitted."
                            )
                            .withStyle(ChatFormatting.DARK_GRAY),
                    false
            );
        }

        if (milestones.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "No self-reliance milestones are currently configured."
                            )
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
        }

        if (!snapshot.inactiveMilestoneIds().isEmpty()) {
            sendHeader(
                    source,
                    "Inactive historical milestone IDs",
                    ChatFormatting.GOLD
            );

            int inactiveShown = Math.min(
                    MAX_INACTIVE_IDS_SHOWN,
                    snapshot.inactiveMilestoneIds().size()
            );

            for (int index = 0; index < inactiveShown; index++) {
                String id = snapshot.inactiveMilestoneIds().get(index);

                source.sendSuccess(
                        () -> Component.literal("- " + id)
                                .withStyle(ChatFormatting.YELLOW),
                        false
                );
            }

            if (snapshot.inactiveMilestoneIds().size()
                    > inactiveShown) {
                int hidden = snapshot.inactiveMilestoneIds().size()
                        - inactiveShown;

                source.sendSuccess(
                        () -> Component.literal(
                                        "... "
                                                + hidden
                                                + " additional inactive ID(s) omitted."
                                )
                                .withStyle(ChatFormatting.DARK_GRAY),
                        false
                );
            }

            source.sendSuccess(
                    () -> Component.literal(
                                    "Inactive IDs remain historical but contribute no score while their definitions are absent."
                            )
                            .withStyle(ChatFormatting.DARK_GRAY),
                    false
            );
        }

        return 1;
    }

    private static int validate(
            CommandSourceStack source
    ) {
        RecognitionFreedomValidationHarness.Report report =
                RecognitionFreedomValidationHarness.validate();

        sendHeader(
                source,
                "Freedom Progression Validation",
                ChatFormatting.LIGHT_PURPLE
        );

        sendValue(
                source,
                "Overall result",
                report.passed()
                        ? "PASS"
                        : "REVIEW REQUIRED",
                report.passed()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.RED
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
                "Milestone definition",
                "revision "
                        + report.milestoneRevision()
                        + ", "
                        + report.configuredMilestones()
                        + " milestone(s) from "
                        + report.sourceFileCount()
                        + " file(s), "
                        + format(
                        report.configuredMilestonePoints()
                )
                        + " points",
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Milestone fingerprint",
                shortFingerprint(
                        report.milestoneFingerprint()
                ),
                ChatFormatting.DARK_AQUA
        );

        sendHeader(
                source,
                "Freedom ceiling",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Freedom maximum",
                format(
                        report.legacyFreedomCeiling()
                )
                        + " legacy -> "
                        + format(
                        report.expandedFreedomCeiling()
                )
                        + " expanded",
                ChatFormatting.AQUA
        );

        sendValue(
                source,
                "Order maximum",
                format(
                        report.orderCeiling()
                ),
                ChatFormatting.GREEN
        );

        double ceilingRatio =
                report.orderCeiling() <= 0.0D
                        ? 0.0D
                        : report.expandedFreedomCeiling()
                          / report.orderCeiling();

        sendValue(
                source,
                "Freedom / Order ceiling",
                format(ceilingRatio * 100.0D)
                        + "%",
                ChatFormatting.WHITE
        );

        sendHeader(
                source,
                "Production-source checks",
                ChatFormatting.GOLD
        );

        for (RecognitionFreedomValidationHarness.Check check :
                report.checks()) {

            String line =
                    (check.passed()
                            ? "[PASS] "
                            : "[FAIL] ")
                            + check.name()
                            + (check.detail().isBlank()
                            ? ""
                            : " — " + check.detail());

            source.sendSuccess(
                    () -> Component.literal(line)
                            .withStyle(
                                    check.passed()
                                            ? ChatFormatting.GREEN
                                            : ChatFormatting.RED
                            ),
                    false
            );
        }

        sendHeader(
                source,
                "Reachability comparison",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Pure paths on coarse grid",
                report.baselinePureCoarse()
                        + " legacy -> "
                        + report.observedExpandedPure()
                        + " expanded / "
                        + report.totalPurePaths(),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Pure paths after refinement",
                report.refinedPurePaths()
                        + " / "
                        + report.totalPurePaths(),
                report.refinedPurePaths()
                        == report.totalPurePaths()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW
        );

        sendValue(
                source,
                "Required crosses on coarse grid",
                report.baselineAdjacentCoarse()
                        + " legacy -> "
                        + report.observedExpandedAdjacent()
                        + " expanded / "
                        + report.totalAdjacentPairs(),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Required crosses after refinement",
                report.refinedAdjacentPairs()
                        + " / "
                        + report.totalAdjacentPairs(),
                report.refinedAdjacentPairs()
                        == report.totalAdjacentPairs()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW
        );

        sendValue(
                source,
                "Expanded observation profiles",
                Integer.toString(
                        report.expandedProfilesEvaluated()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Baseline comparison profiles",
                Integer.toString(
                        report.comparisonProfilesEvaluated()
                ),
                ChatFormatting.WHITE
        );

        sendValue(
                source,
                "Profiles gaining Freedom",
                Integer.toString(
                        report.freedomGainProfiles()
                ),
                report.freedomGainProfiles() > 0
                        ? ChatFormatting.AQUA
                        : ChatFormatting.YELLOW
        );

        sendValue(
                source,
                "New Chaotic selections",
                Integer.toString(
                        report.newChaoticSelectionProfiles()
                ),
                report.newChaoticSelectionProfiles() > 0
                        ? ChatFormatting.LIGHT_PURPLE
                        : ChatFormatting.YELLOW
        );

        sendValue(
                source,
                "Chaotic Pure paths observed",
                report.chaoticPurePaths()
                        + " / "
                        + report.totalChaoticPurePaths(),
                report.chaoticPurePaths()
                        == report.totalChaoticPurePaths()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW
        );

        sendValue(
                source,
                "Chaotic adjacent crossings observed",
                report.chaoticAdjacentPairs()
                        + " / "
                        + report.totalChaoticAdjacentPairs(),
                report.chaoticAdjacentPairs() > 0
                        ? ChatFormatting.GREEN
                        : ChatFormatting.YELLOW
        );

        if (!report.warnings().isEmpty()) {
            sendHeader(
                    source,
                    "Validation notes",
                    ChatFormatting.GOLD
            );

            for (String warning : report.warnings()) {
                source.sendSuccess(
                        () -> Component.literal(
                                        "- " + warning
                                )
                                .withStyle(
                                        ChatFormatting.YELLOW
                                ),
                        false
                );
            }
        }

        source.sendSuccess(
                () -> Component.literal(
                                "All Freedom simulations used temporary data; no player attachment was read or modified."
                        )
                        .withStyle(
                                ChatFormatting.DARK_GRAY
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
                Component.literal(label + ": ")
                        .withStyle(ChatFormatting.GRAY);

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

    private static String shortFingerprint(
            String fingerprint
    ) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return "none";
        }

        return fingerprint.length() <= 12
                ? fingerprint
                : fingerprint.substring(0, 12);
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
}