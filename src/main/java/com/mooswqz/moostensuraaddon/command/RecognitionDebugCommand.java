package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionAuthorityProgress;
import com.mooswqz.moostensuraaddon.recognition.RecognitionBalanceSnapshot;
import com.mooswqz.moostensuraaddon.recognition.RecognitionBalanceValidationHarness;
import com.mooswqz.moostensuraaddon.recognition.RecognitionDimensions;
import com.mooswqz.moostensuraaddon.recognition.RecognitionEvaluation;
import com.mooswqz.moostensuraaddon.recognition.RecognitionNamingCandidate;
import com.mooswqz.moostensuraaddon.recognition.RecognitionNamingEligibility;
import com.mooswqz.moostensuraaddon.recognition.RecognitionNamingService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionPath;
import com.mooswqz.moostensuraaddon.recognition.RecognitionPathEvaluator;
import com.mooswqz.moostensuraaddon.recognition.RecognitionPathSelection;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStatKeys;
import com.mooswqz.moostensuraaddon.recognition.TensuraRecognitionStateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RecognitionDebugCommand {

    private RecognitionDebugCommand() {
    }

    /**
     * Creates the canonical nested debug branch:
     *
     * /moostensura debug recognition
     * /moostensura debug recognition <player>
     * /moostensura debug recognition probe
     * /moostensura debug recognition probe <player>
     * /moostensura debug recognition validate
     * /moostensura debug recognition simulate <path>
     * /moostensura debug recognition simulate-cross <primary> <secondary>
     */
    public static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return createInspectionNode(
                "recognition"
        );
    }

    /**
     * Temporary compatibility alias for the old development command.
     *
     * The root is always registered server-side, but its requirement hides it
     * from command suggestions and rejects execution unless debug mode is on
     * and the source has the standard debug permission level.
     */
    public static void registerLegacyAlias(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
                createInspectionNode(
                        "checkrecognition"
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    createInspectionNode(
            String literal
    ) {
        return Commands.literal(literal)
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
                        Commands.literal("probe")
                                .executes(context -> probe(
                                        context.getSource(),
                                        context.getSource()
                                                .getPlayerOrException()
                                ))
                                .then(
                                        Commands.argument(
                                                        "player",
                                                        EntityArgument.player()
                                                )
                                                .executes(context -> probe(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(
                                                                context,
                                                                "player"
                                                        )
                                                ))
                                )
                )
                .then(
                        Commands.literal("validate")
                                .executes(context ->
                                        validateBalance(
                                                context.getSource()
                                        )
                                )
                )
                .then(
                        Commands.literal("simulate")
                                .then(
                                        Commands.argument(
                                                        "path",
                                                        StringArgumentType.word()
                                                )
                                                .suggests(
                                                        (context, builder) ->
                                                                SharedSuggestionProvider
                                                                        .suggest(
                                                                                RecognitionBalanceValidationHarness
                                                                                        .pathIds(),
                                                                                builder
                                                                        )
                                                )
                                                .executes(context ->
                                                        simulatePure(
                                                                context.getSource(),
                                                                StringArgumentType
                                                                        .getString(
                                                                                context,
                                                                                "path"
                                                                        )
                                                        )
                                                )
                                )
                )
                .then(
                        Commands.literal("simulate-cross")
                                .then(
                                        Commands.argument(
                                                        "primary",
                                                        StringArgumentType.word()
                                                )
                                                .suggests(
                                                        (context, builder) ->
                                                                SharedSuggestionProvider
                                                                        .suggest(
                                                                                RecognitionBalanceValidationHarness
                                                                                        .pathIds(),
                                                                                builder
                                                                        )
                                                )
                                                .then(
                                                        Commands.argument(
                                                                        "secondary",
                                                                        StringArgumentType.word()
                                                                )
                                                                .suggests(
                                                                        (context, builder) ->
                                                                                SharedSuggestionProvider
                                                                                        .suggest(
                                                                                                RecognitionBalanceValidationHarness
                                                                                                        .pathIds(),
                                                                                                builder
                                                                                        )
                                                                )
                                                                .executes(context ->
                                                                        simulateCross(
                                                                                context.getSource(),
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                context,
                                                                                                "primary"
                                                                                        ),
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                context,
                                                                                                "secondary"
                                                                                        )
                                                                        )
                                                                )
                                                )
                                )
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

    private static int validateBalance(
            CommandSourceStack source
    ) {
        RecognitionBalanceValidationHarness.Report report =
                RecognitionBalanceValidationHarness.validate();

        sendHeader(
                source,
                "Recognition Balance Validation",
                ChatFormatting.LIGHT_PURPLE
        );

        sendValue(
                source,
                "Balance source",
                report.balanceSource()
        );

        sendValue(
                source,
                "Balance revision",
                Long.toString(
                        report.balanceRevision()
                )
        );

        sendValue(
                source,
                "Coarse profiles evaluated",
                Integer.toString(
                        report.coarseProfilesEvaluated()
                )
        );

        sendValue(
                source,
                "Refined profiles evaluated",
                Integer.toString(
                        report.refinedProfilesEvaluated()
                )
        );

        sendValue(
                source,
                "Total synthetic profiles",
                Integer.toString(
                        report.evaluatedProfiles()
                )
        );

        sendValue(
                source,
                "Pure paths on coarse grid",
                report.exactPureCoarse()
                        + " / "
                        + report.totalPurePaths()
        );

        sendValue(
                source,
                "Pure paths after refinement",
                report.exactPurePaths()
                        + " / "
                        + report.totalPurePaths()
        );

        sendValue(
                source,
                "Required adjacent crosses on coarse grid",
                report.exactAdjacentCoarse()
                        + " / "
                        + report.totalAdjacentPairs()
        );

        sendValue(
                source,
                "Required adjacent crosses after refinement",
                report.exactAdjacentPairs()
                        + " / "
                        + report.totalAdjacentPairs()
        );

        sendValue(
                source,
                "All exact ordered crosses",
                report.exactCrossPairs()
                        + " / "
                        + report.totalCrossPairs()
                        + " (observational)"
        );

        sendHeader(
                source,
                "Cross-pair classes",
                ChatFormatting.GOLD
        );

        for (RecognitionBalanceValidationHarness.CrossClassSummary summary :
                report.crossClassSummaries()) {

            RecognitionBalanceValidationHarness.CrossClass pairClass =
                    summary.pairClass();

            String suffix =
                    pairClass.required()
                            ? " [required]"
                            : pairClass.contradictory()
                              ? " [contradiction]"
                              : " [optional]";

            sendValue(
                    source,
                    pairClass.displayName(),
                    summary.exactPairs()
                            + " / "
                            + summary.totalPairs()
                            + suffix
            );
        }

        sendHeader(
                source,
                "Pure-path results",
                ChatFormatting.GOLD
        );

        for (RecognitionBalanceValidationHarness.PathResult pathResult :
                report.paths()) {

            RecognitionBalanceValidationHarness.Result result =
                    pathResult.result();

            RecognitionPath path =
                    pathResult.path();

            String marker;
            ChatFormatting color;

            if (!result.exact()) {
                marker = "[MISS] ";
                color = ChatFormatting.YELLOW;
            } else if (result.searchStage()
                    == RecognitionBalanceValidationHarness
                    .SearchStage.REFINED) {
                marker = "[REFINED] ";
                color = ChatFormatting.AQUA;
            } else {
                marker = "[COARSE] ";
                color = ChatFormatting.GREEN;
            }

            String line =
                    marker
                            + path.getId()
                            + " -> "
                            + result.actualSelection()
                            + " | final "
                            + format(
                            result.evaluation()
                                    .getPathScore(path)
                    )
                            + ", raw "
                            + format(
                            result.evaluation()
                                    .getRawPathScore(path)
                    )
                            + ", identity "
                            + format(
                            result.evaluation()
                                    .getIdentityBoost(path)
                    )
                            + (
                            pathResult.identityHeavy()
                                    ? " [identity-heavy]"
                                    : ""
                    );

            source.sendSuccess(
                    () -> Component.literal(line)
                            .withStyle(color),
                    false
            );

            source.sendSuccess(
                    () -> Component.literal(
                                    "  Morality: "
                                            + result.components()
                                            .moralitySummary()
                            )
                            .withStyle(
                                    ChatFormatting.GRAY
                            ),
                    false
            );

            source.sendSuccess(
                    () -> Component.literal(
                                    "  Temperament: "
                                            + result.components()
                                            .temperamentSummary()
                            )
                            .withStyle(
                                    ChatFormatting.GRAY
                            ),
                    false
            );

            if (!result.exact()) {
                source.sendSuccess(
                        () -> Component.literal(
                                        "  Diagnosis: "
                                                + result.diagnosis()
                                )
                                .withStyle(
                                        ChatFormatting.DARK_GRAY
                                ),
                        false
                );
            }
        }

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

        source.sendSuccess(
                () -> Component.literal(
                                "All simulations were temporary; no player data was modified."
                        )
                        .withStyle(
                                ChatFormatting.DARK_GRAY
                        ),
                false
        );

        return 1;
    }

    private static int simulatePure(
            CommandSourceStack source,
            String rawPath
    ) {
        RecognitionPath path =
                parsePath(
                        source,
                        rawPath
                );

        if (path == null) {
            return 0;
        }

        RecognitionBalanceValidationHarness.Result result =
                RecognitionBalanceValidationHarness
                        .simulatePure(path);

        sendSimulation(
                source,
                result
        );

        return 1;
    }

    private static int simulateCross(
            CommandSourceStack source,
            String rawPrimary,
            String rawSecondary
    ) {
        RecognitionPath primary =
                parsePath(
                        source,
                        rawPrimary
                );

        RecognitionPath secondary =
                parsePath(
                        source,
                        rawSecondary
                );

        if (primary == null
                || secondary == null) {
            return 0;
        }

        if (primary == secondary) {
            source.sendFailure(
                    Component.literal(
                                    "Primary and secondary paths must differ."
                            )
                            .withStyle(
                                    ChatFormatting.RED
                            )
            );

            return 0;
        }

        RecognitionBalanceValidationHarness.Result result =
                RecognitionBalanceValidationHarness
                        .simulateCross(
                                primary,
                                secondary
                        );

        sendSimulation(
                source,
                result
        );

        return 1;
    }

    private static RecognitionPath parsePath(
            CommandSourceStack source,
            String rawPath
    ) {
        RecognitionPath path =
                RecognitionPath.byId(
                                rawPath
                        )
                        .orElse(null);

        if (path != null) {
            return path;
        }

        source.sendFailure(
                Component.literal(
                                "Unknown recognition path '"
                                        + rawPath
                                        + "'. Valid paths: "
                                        + String.join(
                                        ", ",
                                        RecognitionBalanceValidationHarness
                                                .pathIds()
                                )
                        )
                        .withStyle(
                                ChatFormatting.RED
                        )
        );

        return null;
    }

    private static void sendSimulation(
            CommandSourceStack source,
            RecognitionBalanceValidationHarness.Result result
    ) {
        RecognitionPath primary =
                result.requestedPrimary();

        RecognitionPath secondary =
                result.requestedSecondary();

        String requested =
                secondary == null
                        ? "Pure "
                          + primary.getId()
                        : primary.getId()
                          + " / "
                          + secondary.getId();

        sendHeader(
                source,
                "Recognition Simulation: " + requested,
                ChatFormatting.LIGHT_PURPLE
        );

        sendValue(
                source,
                "Exact requested result",
                Boolean.toString(
                        result.exact()
                )
        );

        sendValue(
                source,
                "Search result",
                result.exact()
                        ? result.searchStage()
                        .displayName()
                        : "not found; best candidate from "
                          + result.searchStage()
                        .displayName()
        );

        if (result.mode()
                == RecognitionBalanceValidationHarness.Mode.CROSS) {
            sendValue(
                    source,
                    "Pair class",
                    result.pairClass()
                            .displayName()
                            + (
                            result.pairClass()
                                    .required()
                                    ? " [required]"
                                    : result.pairClass()
                                    .contradictory()
                                      ? " [contradiction]"
                                      : " [optional]"
                    )
            );
        }

        sendValue(
                source,
                "Actual selection",
                result.actualSelection()
        );

        sendValue(
                source,
                "Diagnosis",
                result.diagnosis()
        );

        sendValue(
                source,
                "Balance",
                result.balanceSource()
                        + " (revision "
                        + result.balanceRevision()
                        + ")"
        );

        sendValue(
                source,
                "Coarse profiles searched",
                Integer.toString(
                        result.coarseProfilesEvaluated()
                )
        );

        sendValue(
                source,
                "Refined profiles searched",
                Integer.toString(
                        result.refinedProfilesEvaluated()
                )
        );

        sendValue(
                source,
                "Total profiles searched",
                Integer.toString(
                        result.evaluatedProfiles()
                )
        );

        sendValue(
                source,
                "Synthetic profile",
                result.profile()
                        .describe()
        );

        RecognitionEvaluation evaluation =
                result.evaluation();

        RecognitionDimensions dimensions =
                evaluation.getDimensions();

        sendValue(
                source,
                "Dimensions",
                "good "
                        + format(dimensions.good())
                        + ", evil "
                        + format(dimensions.evil())
                        + ", order "
                        + format(dimensions.order())
                        + ", freedom "
                        + format(dimensions.freedom())
                        + ", mastery "
                        + format(dimensions.mastery())
                        + ", discovery "
                        + format(dimensions.discovery())
                        + ", identity "
                        + format(
                        dimensions.identityStrength()
                )
        );

        sendValue(
                source,
                "Morality components",
                result.components()
                        .moralitySummary()
        );

        sendValue(
                source,
                "Temperament components",
                result.components()
                        .temperamentSummary()
        );

        sendValue(
                source,
                "Neutral evidence breakdown",
                result.components()
                        .neutralBreakdown()
        );

        sendSimulationPathScore(
                source,
                "Requested primary",
                primary,
                evaluation
        );

        if (secondary != null) {
            sendSimulationPathScore(
                    source,
                    "Requested secondary",
                    secondary,
                    evaluation
            );
        }

        if (result.blockers().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "No selection blockers were detected."
                            )
                            .withStyle(
                                    ChatFormatting.GREEN
                            ),
                    false
            );
        } else {
            sendHeader(
                    source,
                    "Blocking conditions",
                    ChatFormatting.GOLD
            );

            for (String blocker : result.blockers()) {
                source.sendSuccess(
                        () -> Component.literal(
                                        "- " + blocker
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
                                "Simulation only; stored recognition data was not changed."
                        )
                        .withStyle(
                                ChatFormatting.DARK_GRAY
                        ),
                false
        );
    }

    private static void sendSimulationPathScore(
            CommandSourceStack source,
            String label,
            RecognitionPath path,
            RecognitionEvaluation evaluation
    ) {
        sendValue(
                source,
                label,
                path.getId()
                        + ": final "
                        + format(
                        evaluation.getPathScore(path)
                )
                        + " = raw "
                        + format(
                        evaluation.getRawPathScore(path)
                )
                        + " + identity "
                        + format(
                        evaluation.getIdentityBoost(path)
                )
        );
    }

    private static int inspect(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        RecognitionData data = target.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        TensuraRecognitionStateHelper.Snapshot liveSnapshot =
                TensuraRecognitionStateHelper.synchronize(
                        target,
                        data
                );

        RecognitionAuthorityProgress.synchronize(
                target,
                data
        );

        RecognitionEvaluation evaluation =
                RecognitionPathEvaluator.evaluate(data);

        RecognitionBalanceSnapshot balance =
                evaluation.getBalance();

        RecognitionNamingEligibility namingEligibility =
                RecognitionNamingService.evaluate(
                        target,
                        data,
                        evaluation
                );

        RecognitionDimensions dimensions =
                evaluation.getDimensions();

        String playerName =
                target.getGameProfile().getName();

        sendHeader(
                source,
                "Recognition Debug: " + playerName,
                ChatFormatting.LIGHT_PURPLE
        );

        sendActiveBalance(
                source,
                evaluation
        );

        sendHeader(
                source,
                "Baseline data",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Highest XP level",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys
                                        .HIGHEST_EXPERIENCE_LEVEL
                        )
                )
        );

        sendValue(
                source,
                "Current EP",
                format(
                        data.getMeasurement(
                                RecognitionStatKeys.CURRENT_EP
                        )
                )
        );

        sendValue(
                source,
                "Highest EP",
                format(
                        data.getMeasurement(
                                RecognitionStatKeys.HIGHEST_EP
                        )
                )
        );

        sendValue(
                source,
                "Mastered skills",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys.MASTERED_SKILLS
                        )
                )
        );

        sendValue(
                source,
                "Skill categories",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys
                                        .MASTERED_SKILL_CATEGORIES
                        )
                )
        );

        sendValue(
                source,
                "TH stored",
                Boolean.toString(
                        data.getFlag(
                                RecognitionStatKeys.TRUE_HERO
                        )
                )
        );

        sendValue(
                source,
                "TH detected now",
                liveSnapshot.detectedTrueHero()
                        + formatSource(
                        liveSnapshot.trueHeroSource()
                )
        );

        sendValue(
                source,
                "TDL stored",
                Boolean.toString(
                        data.getFlag(
                                RecognitionStatKeys.TRUE_DEMON_LORD
                        )
                )
        );

        sendValue(
                source,
                "TDL detected now",
                liveSnapshot.detectedTrueDemonLord()
                        + formatSource(
                        liveSnapshot.trueDemonLordSource()
                )
        );

        sendHeader(
                source,
                "Tracked deeds",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Raid victories",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys.RAID_VICTORIES
                        )
                )
        );

        sendValue(
                source,
                "Villagers cured",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys.VILLAGERS_CURED
                        )
                )
        );

        sendValue(
                source,
                "Civilians defended",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys.CIVILIANS_DEFENDED
                        )
                )
        );

        sendValue(
                source,
                "Civilian kills",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys.CIVILIAN_KILLS
                        )
                )
        );

        sendValue(
                source,
                "Passive baby kills",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys.PASSIVE_BABY_KILLS
                        )
                )
        );

        sendValue(
                source,
                "Owned companion kills",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys
                                        .OWNED_COMPANION_KILLS
                        )
                )
        );

        sendValue(
                source,
                "Owned subordinate kills",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys
                                        .OWNED_SUBORDINATE_KILLS
                        )
                )
        );

        sendValue(
                source,
                "Distinct major enemies",
                Integer.toString(
                        data.getUniqueValueCount(
                                RecognitionStatKeys
                                        .MAJOR_ENEMY_TYPES_DEFEATED
                        )
                )
        );

        sendValue(
                source,
                "Solo major enemy types",
                Integer.toString(
                        data.getUniqueValueCount(
                                RecognitionStatKeys
                                        .SOLO_MAJOR_ENEMY_TYPES_DEFEATED
                        )
                )
        );

        sendValue(
                source,
                "Subordinate-assisted major victories",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys
                                        .SUBORDINATE_ASSISTED_MAJOR_VICTORIES
                        )
                )
        );

        sendValue(
                source,
                "Current subordinate roster",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys.CURRENT_SUBORDINATES
                        )
                )
        );

        sendValue(
                source,
                "Highest subordinate count",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys.HIGHEST_SUBORDINATES
                        )
                )
        );

        sendValue(
                source,
                "Unique subordinates empowered",
                Integer.toString(
                        data.getUniqueValueCount(
                                RecognitionStatKeys
                                        .UNIQUE_SUBORDINATES_EMPOWERED
                        )
                )
        );

        sendValue(
                source,
                "Mass Grants performed",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys
                                        .MASS_GRANTS_PERFORMED
                        )
                )
        );

        sendValue(
                source,
                "Global Take Backs performed",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys
                                        .GLOBAL_TAKE_BACKS_PERFORMED
                        )
                )
        );

        sendValue(
                source,
                "Skills seized",
                Integer.toString(
                        data.getCounter(
                                RecognitionStatKeys.SKILLS_SEIZED
                        )
                )
        );

        sendValue(
                source,
                "Discovery milestones",
                Integer.toString(
                        data.getUniqueValueCount(
                                RecognitionStatKeys.DISCOVERY_MILESTONES
                        )
                )
        );

        sendHeader(
                source,
                "Calculated dimensions",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Good",
                format(dimensions.good())
        );

        sendValue(
                source,
                "Evil",
                format(dimensions.evil())
        );

        sendValue(
                source,
                "Order",
                format(dimensions.order())
        );

        sendValue(
                source,
                "Freedom",
                format(dimensions.freedom())
        );

        sendValue(
                source,
                "Mastery",
                format(dimensions.mastery())
        );

        sendValue(
                source,
                "Discovery",
                format(dimensions.discovery())
        );

        sendValue(
                source,
                "Identity strength",
                format(dimensions.identityStrength())
        );

        sendPathRanking(
                source,
                evaluation
        );

        sendCurrentSelection(
                source,
                evaluation,
                balance
        );

        sendNamingEligibility(
                source,
                target,
                namingEligibility
        );

        return 1;
    }

    private static void sendActiveBalance(
            CommandSourceStack source,
            RecognitionEvaluation evaluation
    ) {
        RecognitionBalanceSnapshot balance =
                evaluation.getBalance();

        RecognitionBalanceSnapshot.Selection selection =
                balance.selection();

        sendHeader(
                source,
                "Active datapack balance",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Source",
                balance.sourceId()
        );

        sendValue(
                source,
                "Revision",
                Long.toString(
                        evaluation.getBalanceRevision()
                )
        );

        sendValue(
                source,
                "Established threshold",
                format(
                        selection.establishedThreshold()
                )
        );

        sendValue(
                source,
                "Pure threshold",
                format(
                        selection.pureThreshold()
                )
        );

        sendValue(
                source,
                "Final dominance ratio",
                format(
                        selection.dominanceRatio()
                )
        );

        sendValue(
                source,
                "Raw pure threshold",
                format(
                        selection.rawPureThreshold()
                )
        );

        sendValue(
                source,
                "Raw dominance ratio",
                format(
                        selection.rawDominanceRatio()
                )
        );

        sendValue(
                source,
                "Directional morality minimum",
                format(
                        selection
                                .minimumDirectionalMoralityEvidence()
                )
        );

        sendValue(
                source,
                "TH / TDL modifiers",
                format(
                        balance.good()
                                .trueHeroModifier()
                )
                        + " / "
                        + format(
                        balance.evil()
                                .trueDemonLordModifier()
                )
        );

        sendValue(
                source,
                "Identity shares",
                "universal "
                        + format(
                        balance.identityDistribution()
                                .universalShare()
                )
                        + ", focused "
                        + format(
                        balance.identityDistribution()
                                .focusedShare()
                )
        );
    }

    private static void sendPathRanking(
            CommandSourceStack source,
            RecognitionEvaluation evaluation
    ) {
        sendHeader(
                source,
                "Path ranking",
                ChatFormatting.GOLD
        );

        List<Map.Entry<RecognitionPath, Double>> rankedPaths =
                new ArrayList<>(
                        evaluation.getPathScores().entrySet()
                );

        rankedPaths.sort(
                Map.Entry.<RecognitionPath, Double>comparingByValue()
                        .reversed()
                        .thenComparing(
                                entry -> entry.getKey().ordinal()
                        )
        );

        for (int index = 0;
             index < rankedPaths.size();
             index++) {

            Map.Entry<RecognitionPath, Double> entry =
                    rankedPaths.get(index);

            int position = index + 1;

            ChatFormatting color;

            if (position == 1) {
                color = ChatFormatting.GREEN;
            } else if (position == 2) {
                color = ChatFormatting.AQUA;
            } else {
                color = ChatFormatting.GRAY;
            }

            RecognitionPath path = entry.getKey();

            double finalScore = entry.getValue();

            double rawScore =
                    evaluation.getRawPathScore(path);

            double identityBoost =
                    evaluation.getIdentityBoost(path);

            String line =
                    position
                            + ". "
                            + path.getId()
                            + ": "
                            + format(finalScore)
                            + " (raw "
                            + format(rawScore)
                            + " + identity "
                            + format(identityBoost)
                            + ")";

            source.sendSuccess(
                    () -> Component.literal(line)
                            .withStyle(color),
                    false
            );
        }
    }

    private static void sendCurrentSelection(
            CommandSourceStack source,
            RecognitionEvaluation evaluation,
            RecognitionBalanceSnapshot balance
    ) {
        sendHeader(
                source,
                "Current selection",
                ChatFormatting.GOLD
        );

        if (evaluation.getSelection().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "No valid selection yet. Two final paths must reach "
                                            + format(
                                            balance.selection()
                                                    .establishedThreshold()
                                    )
                                            + ", or one path must reach "
                                            + format(
                                            balance.selection()
                                                    .pureThreshold()
                                    )
                                            + " with at least "
                                            + format(
                                            balance.selection()
                                                    .rawPureThreshold()
                                    )
                                            + " raw affinity and sufficient dominance."
                            )
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );

            return;
        }

        RecognitionPathSelection selection =
                evaluation.getSelection().orElseThrow();

        if (selection.pure()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "Pure "
                                            + selection.primaryPath().getId()
                                            + " ("
                                            + format(
                                            selection.primaryScore()
                                    )
                                            + ")"
                            )
                            .withStyle(
                                    ChatFormatting.GREEN,
                                    ChatFormatting.BOLD
                            ),
                    false
            );

            return;
        }

        source.sendSuccess(
                () -> Component.literal(
                                "Primary: "
                                        + selection.primaryPath().getId()
                                        + " ("
                                        + format(
                                        selection.primaryScore()
                                )
                                        + ")"
                        )
                        .withStyle(ChatFormatting.GREEN),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Secondary: "
                                        + selection.secondaryPath().getId()
                                        + " ("
                                        + format(
                                        selection.secondaryScore()
                                )
                                        + ")"
                        )
                        .withStyle(ChatFormatting.AQUA),
                false
        );
    }

    private static void sendNamingEligibility(
            CommandSourceStack source,
            ServerPlayer target,
            RecognitionNamingEligibility eligibility
    ) {
        sendHeader(
                source,
                "Naming eligibility",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "State",
                eligibility.status().getId()
        );

        sendValue(
                source,
                "Native named",
                Boolean.toString(
                        eligibility.nativeNamed()
                )
        );

        sendValue(
                source,
                "Native name",
                eligibility.nativeName().isBlank()
                        ? "none"
                        : eligibility.nativeName()
        );

        sendValue(
                source,
                "Recognition committed",
                Boolean.toString(
                        eligibility.recognitionCommitted()
                )
        );

        sendValue(
                source,
                "Level requirement",
                eligibility.currentLevel()
                        + " / "
                        + eligibility.requiredLevel()
        );

        sendValue(
                source,
                "Eligible now",
                Boolean.toString(
                        eligibility.eligible()
                )
        );

        RecognitionNamingCandidate candidate =
                eligibility.candidate();

        if (candidate == null) {
            sendValue(
                    source,
                    "Candidate paths",
                    "none"
            );

            sendValue(
                    source,
                    "Candidate title",
                    "none"
            );
        } else {
            sendValue(
                    source,
                    "Candidate paths",
                    candidate.getPathSummary()
            );

            sendValue(
                    source,
                    "Candidate title",
                    candidate.formatDisplayName(
                            target.getGameProfile().getName()
                    )
            );
        }

        String explanation = switch (eligibility.status()) {
            case READY ->
                    "The player can begin the naming ritual.";

            case ALREADY_COMMITTED ->
                    "A naming result has already been committed for this incarnation.";

            case ALREADY_NAMED ->
                    "Tensura already considers this player named or endowed.";

            case NOT_ENOUGH_LEVEL ->
                    "The player has not reached the minimum level requirement.";

            case NO_RECOGNITION_SELECTION ->
                    "The recognition paths have not formed a valid selection yet.";
        };

        source.sendSuccess(
                () -> Component.literal(explanation)
                        .withStyle(
                                eligibility.eligible()
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.YELLOW
                        ),
                false
        );
    }

    private static int probe(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        TensuraRecognitionStateHelper.Snapshot snapshot =
                TensuraRecognitionStateHelper.inspect(target);

        sendHeader(
                source,
                "Tensura Awakening Probe: "
                        + target.getGameProfile().getName(),
                ChatFormatting.LIGHT_PURPLE
        );

        sendValue(
                source,
                "Detected TH",
                Boolean.toString(
                        snapshot.detectedTrueHero()
                )
        );

        sendValue(
                source,
                "TH source",
                snapshot.trueHeroSource().isBlank()
                        ? "none"
                        : snapshot.trueHeroSource()
        );

        sendValue(
                source,
                "Detected TDL",
                Boolean.toString(
                        snapshot.detectedTrueDemonLord()
                )
        );

        sendValue(
                source,
                "TDL source",
                snapshot.trueDemonLordSource().isBlank()
                        ? "none"
                        : snapshot.trueDemonLordSource()
        );

        if (snapshot.evidence().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "No recognized TH/TDL evidence was exposed by the inspected Tensura storages."
                            )
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );

            return 1;
        }

        sendHeader(
                source,
                "Detected evidence",
                ChatFormatting.GOLD
        );

        for (String evidence : snapshot.evidence()) {
            source.sendSuccess(
                    () -> Component.literal("- " + evidence)
                            .withStyle(ChatFormatting.GRAY),
                    false
            );
        }

        return 1;
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
            String value
    ) {
        source.sendSuccess(
                () -> Component.literal(label + ": ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(
                                Component.literal(value)
                                        .withStyle(
                                                ChatFormatting.WHITE
                                        )
                        ),
                false
        );
    }

    private static String formatSource(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }

        return " [" + source + "]";
    }

    private static String format(double value) {
        return String.format(
                Locale.US,
                "%.1f",
                value
        );
    }
}