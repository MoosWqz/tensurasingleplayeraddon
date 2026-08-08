package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionAuthorityProgress;
import com.mooswqz.moostensuraaddon.recognition.RecognitionBalanceSnapshot;
import com.mooswqz.moostensuraaddon.recognition.RecognitionDimensions;
import com.mooswqz.moostensuraaddon.recognition.RecognitionEvaluation;
import com.mooswqz.moostensuraaddon.recognition.RecognitionEvidenceBreakdown;
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
     * Registers the nested debug inspector and the temporary legacy alias.
     *
     * <p>This method intentionally supports both command-registration layouts
     * used during the recognition development line. When the canonical
     * {@code /moostensura debug} node already exists, the recognition branch is
     * attached once. The legacy {@code /checkrecognition} alias is also
     * registered once and remains protected by the normal debug-mode and
     * permission checks.</p>
     */
    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        if (dispatcher == null) {
            throw new IllegalArgumentException(
                    "A command dispatcher is required."
            );
        }

        CommandNode<CommandSourceStack> moosTensuraRoot =
                dispatcher.getRoot()
                        .getChild("moostensura");

        if (moosTensuraRoot != null) {
            CommandNode<CommandSourceStack> debugRoot =
                    moosTensuraRoot.getChild("debug");

            if (debugRoot != null
                    && debugRoot.getChild("recognition") == null) {
                debugRoot.addChild(
                        createDebugNode().build()
                );
            }
        }

        if (dispatcher.getRoot()
                .getChild("checkrecognition") == null) {
            registerLegacyAlias(dispatcher);
        }
    }

    /**
     * Creates the canonical nested debug branch:
     *
     * /moostensura debug recognition
     * /moostensura debug recognition <player>
     * /moostensura debug recognition probe
     * /moostensura debug recognition probe <player>
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
                        Commands.literal("evidence")
                                .executes(context -> inspectEvidence(
                                        context.getSource(),
                                        context.getSource()
                                                .getPlayerOrException(),
                                        false
                                ))
                                .then(
                                        Commands.literal("full")
                                                .executes(context -> inspectEvidence(
                                                        context.getSource(),
                                                        context.getSource()
                                                                .getPlayerOrException(),
                                                        true
                                                ))
                                                .then(
                                                        Commands.argument(
                                                                        "player",
                                                                        EntityArgument.player()
                                                                )
                                                                .executes(context -> inspectEvidence(
                                                                        context.getSource(),
                                                                        EntityArgument.getPlayer(
                                                                                context,
                                                                                "player"
                                                                        ),
                                                                        true
                                                                ))
                                                )
                                )
                                .then(
                                        Commands.argument(
                                                        "player",
                                                        EntityArgument.player()
                                                )
                                                .executes(context -> inspectEvidence(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(
                                                                context,
                                                                "player"
                                                        ),
                                                        false
                                                ))
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

    private static int inspectEvidence(
            CommandSourceStack source,
            ServerPlayer target,
            boolean full
    ) {
        if (target == null) {
            source.sendFailure(
                    Component.literal(
                                    "Target player could not be found."
                            )
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        RecognitionData data = target.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        /*
         * Refresh only the same authoritative snapshots already used by the
         * normal recognition debug command. The inspector never changes deed
         * counters, collections, committed paths or titles.
         */
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

        RecognitionEvidenceBreakdown breakdown =
                RecognitionEvidenceBreakdown.calculate(
                        data,
                        evaluation.getBalance()
                );

        sendHeader(
                source,
                "Recognition Evidence: "
                        + target.getGameProfile().getName(),
                ChatFormatting.LIGHT_PURPLE
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Read-only developer inspection. No recognition data is modified."
                        )
                        .withStyle(ChatFormatting.DARK_GRAY),
                false
        );

        sendHeader(
                source,
                "Incarnation and committed result",
                ChatFormatting.GOLD
        );

        sendValue(
                source,
                "Data version",
                Integer.toString(
                        data.getDataVersion()
                )
        );

        sendValue(
                source,
                "Recognition incarnation",
                emptyAsNone(
                        data.getString(
                                RecognitionStatKeys.INCARNATION_ID
                        )
                )
        );

        sendValue(
                source,
                "Committed",
                Boolean.toString(
                        data.isNamingCommitted()
                )
        );

        sendValue(
                source,
                "Pure",
                Boolean.toString(
                        data.isPureRecognition()
                )
        );

        sendValue(
                source,
                "Reveal pending",
                Boolean.toString(
                        data.isRevealPending()
                )
        );

        sendValue(
                source,
                "Committed primary",
                data.getCommittedPrimaryPath()
                        .map(RecognitionPath::getId)
                        .orElse("none")
        );

        sendValue(
                source,
                "Committed secondary",
                data.getCommittedSecondaryPath()
                        .map(RecognitionPath::getId)
                        .orElse("none")
        );

        sendValue(
                source,
                "Bestowed title",
                emptyAsNone(
                        data.getBestowedTitle()
                )
        );

        sendValue(
                source,
                "Balance source",
                evaluation.getBalance()
                        .sourceId()
        );

        sendValue(
                source,
                "Balance revision",
                Long.toString(
                        evaluation.getBalanceRevision()
                )
        );

        sendEvidenceDimension(
                source,
                breakdown.good(),
                ChatFormatting.GREEN,
                true
        );

        sendEvidenceDimension(
                source,
                breakdown.evil(),
                ChatFormatting.RED,
                true
        );

        if (full) {
            sendEvidenceDimension(
                    source,
                    breakdown.order(),
                    ChatFormatting.GOLD,
                    true
            );

            sendEvidenceDimension(
                    source,
                    breakdown.freedom(),
                    ChatFormatting.AQUA,
                    true
            );

            sendEvidenceDimension(
                    source,
                    breakdown.mastery(),
                    ChatFormatting.LIGHT_PURPLE,
                    true
            );

            sendEvidenceDimension(
                    source,
                    breakdown.discovery(),
                    ChatFormatting.BLUE,
                    true
            );

            sendEvidenceDimension(
                    source,
                    breakdown.identityStrength(),
                    ChatFormatting.WHITE,
                    true
            );
        } else {
            sendHeader(
                    source,
                    "Calculated dimension totals",
                    ChatFormatting.GOLD
            );

            sendValue(
                    source,
                    "Good",
                    format(
                            breakdown.good().total()
                    )
            );

            sendValue(
                    source,
                    "Evil",
                    format(
                            breakdown.evil().total()
                    )
            );

            sendValue(
                    source,
                    "Order",
                    format(
                            breakdown.order().total()
                    )
            );

            sendValue(
                    source,
                    "Freedom",
                    format(
                            breakdown.freedom().total()
                    )
            );

            sendValue(
                    source,
                    "Mastery",
                    format(
                            breakdown.mastery().total()
                    )
            );

            sendValue(
                    source,
                    "Discovery",
                    format(
                            breakdown.discovery().total()
                    )
            );

            sendValue(
                    source,
                    "Identity strength",
                    format(
                            breakdown.identityStrength().total()
                    )
            );
        }

        RecognitionEvidenceBreakdown.Consistency consistency =
                breakdown.compare(
                        evaluation.getDimensions()
                );

        if (!consistency.matches()) {
            source.sendFailure(
                    Component.literal(
                                    "Evidence breakdown mismatch: "
                                            + consistency.dimensionId()
                                            + " differs by "
                                            + format(
                                            consistency.difference()
                                    )
                            )
                            .withStyle(ChatFormatting.RED)
            );
        } else {
            source.sendSuccess(
                    () -> Component.literal(
                                    "Breakdown matches the active evaluator dimensions."
                            )
                            .withStyle(ChatFormatting.DARK_GREEN),
                    false
            );
        }

        sendTopPathCandidates(
                source,
                evaluation,
                3
        );

        sendCurrentSelection(
                source,
                evaluation,
                evaluation.getBalance()
        );

        if (!full) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "Use /moostensura debug recognition evidence full for every weighted source."
                            )
                            .withStyle(ChatFormatting.AQUA),
                    false
            );
        }

        return 1;
    }

    private static void sendEvidenceDimension(
            CommandSourceStack source,
            RecognitionEvidenceBreakdown.Dimension dimension,
            ChatFormatting color,
            boolean includeZeroEntries
    ) {
        sendHeader(
                source,
                dimension.displayName()
                        + " evidence — total "
                        + format(dimension.total()),
                color
        );

        for (RecognitionEvidenceBreakdown.Entry entry :
                dimension.entries()) {

            if (!includeZeroEntries
                    && entry.contribution() <= 0.0D) {
                continue;
            }

            StringBuilder value = new StringBuilder();

            value.append(entry.rawValue())
                    .append(" -> +")
                    .append(
                            format(entry.contribution())
                    );

            if (entry.maximum() > 0.0D) {
                value.append(" / ")
                        .append(
                                format(entry.maximum())
                        );
            }

            source.sendSuccess(
                    () -> Component.literal("- ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(
                                    Component.literal(
                                                    entry.label()
                                            )
                                            .withStyle(
                                                    ChatFormatting.GRAY
                                            )
                            )
                            .append(
                                    Component.literal(
                                                    " ["
                                                            + entry.statKey()
                                                            + "]: "
                                            )
                                            .withStyle(
                                                    ChatFormatting.DARK_GRAY
                                            )
                            )
                            .append(
                                    Component.literal(
                                                    value.toString()
                                            )
                                            .withStyle(color)
                            ),
                    false
            );
        }
    }

    private static void sendTopPathCandidates(
            CommandSourceStack source,
            RecognitionEvaluation evaluation,
            int maximumEntries
    ) {
        sendHeader(
                source,
                "Top path candidates",
                ChatFormatting.GOLD
        );

        List<Map.Entry<RecognitionPath, Double>> rankedPaths =
                new ArrayList<>(
                        evaluation.getPathScores()
                                .entrySet()
                );

        rankedPaths.sort(
                Map.Entry.<RecognitionPath, Double>comparingByValue()
                        .reversed()
                        .thenComparing(
                                entry -> entry.getKey().ordinal()
                        )
        );

        int count = Math.min(
                Math.max(1, maximumEntries),
                rankedPaths.size()
        );

        for (int index = 0;
             index < count;
             index++) {

            Map.Entry<RecognitionPath, Double> entry =
                    rankedPaths.get(index);

            RecognitionPath path = entry.getKey();

            String value =
                    format(entry.getValue())
                            + " (raw "
                            + format(
                            evaluation.getRawPathScore(path)
                    )
                            + " + identity "
                            + format(
                            evaluation.getIdentityBoost(path)
                    )
                            + ")";

            sendValue(
                    source,
                    (index + 1)
                            + ". "
                            + path.getId(),
                    value
            );
        }
    }

    private static String emptyAsNone(
            String value
    ) {
        return value == null || value.isBlank()
                ? "none"
                : value.trim();
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