package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.lifecycle.AddonPlayerDataResetService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionBenefitsService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionProgressScreenService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionUnnameService;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class MoosTensuraCommand {

    private MoosTensuraCommand() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
                Commands.literal("moostensura")
                        .requires(source ->
                                source.hasPermission(0)
                        )
                        .executes(context -> sendGuide(
                                context.getSource()
                        ))

                        .then(
                                Commands.literal("guide")
                                        .executes(context -> sendGuide(
                                                context.getSource()
                                        ))
                                        .then(
                                                Commands.literal("sage")
                                                        .executes(context -> sendSageGuide(
                                                                context.getSource()
                                                        ))
                                        )
                        )

                        .then(
                                Commands.literal("help")
                                        .executes(context -> sendHelp(
                                                context.getSource()
                                        ))
                        )

                        .then(
                                Commands.literal("paths")
                                        .executes(context -> openRecognitionPaths(
                                                context.getSource()
                                        ))
                        )

                        .then(
                                Commands.literal("lifecycle")
                                        .requires(source ->
                                                source.hasPermission(2)
                                        )
                                        .executes(context -> inspectLifecycle(
                                                context.getSource(),
                                                context.getSource()
                                                        .getPlayerOrException()
                                        ))
                                        .then(
                                                Commands.argument(
                                                                "player",
                                                                EntityArgument.player()
                                                        )
                                                        .executes(context -> inspectLifecycle(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(
                                                                        context,
                                                                        "player"
                                                                )
                                                        ))
                                        )
                        )

                        /*
                         * Stage one:
                         *
                         * /moostensura unname
                         *
                         * Arms a one-use confirmation for 30 seconds.
                         *
                         * Stage two:
                         *
                         * /moostensura unname confirm
                         *
                         * Only succeeds if stage one was entered first by the
                         * same administrator.
                         */
                        .then(
                                Commands.literal("unname")
                                        .requires(source ->
                                                source.hasPermission(2)
                                        )
                                        .executes(context -> requestUnname(
                                                context.getSource()
                                        ))
                                        .then(
                                                Commands.literal("confirm")
                                                        .executes(context -> confirmUnname(
                                                                context.getSource()
                                                        ))
                                        )
                        )

                        /*
                         * Config reset uses the same real two-stage
                         * confirmation system.
                         */
                        .then(
                                Commands.literal("resetconfig")
                                        .requires(source ->
                                                source.hasPermission(2)
                                        )
                                        .executes(context -> requestConfigReset(
                                                context.getSource()
                                        ))
                                        .then(
                                                Commands.literal("confirm")
                                                        .executes(context -> resetConfig(
                                                                context.getSource()
                                                        ))
                                        )
                        )

                        /*
                         * Player-data reset is also bound to the selected
                         * target UUID.
                         *
                         * Requesting a reset for one player cannot be used to
                         * confirm a reset for another player.
                         */
                        .then(
                                Commands.literal("reset")
                                        .requires(source ->
                                                source.hasPermission(2)
                                        )
                                        .then(
                                                Commands.argument(
                                                                "player",
                                                                EntityArgument.player()
                                                        )
                                                        .executes(context -> requestAddonDataReset(
                                                                context.getSource(),
                                                                EntityArgument.getPlayer(
                                                                        context,
                                                                        "player"
                                                                )
                                                        ))
                                                        .then(
                                                                Commands.literal("confirm")
                                                                        .executes(context -> resetAddonData(
                                                                                context.getSource(),
                                                                                EntityArgument.getPlayer(
                                                                                        context,
                                                                                        "player"
                                                                                )
                                                                        ))
                                                        )
                                        )
                        )
        );
    }

    private static int openRecognitionPaths(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        RecognitionBenefitsService.send(player);
        RecognitionProgressScreenService.open(player);
        return 1;
    }

    private static int inspectLifecycle(
            CommandSourceStack source,
            ServerPlayer target
    ) {
        if (target == null) {
            source.sendFailure(
                    Component.literal("Target player could not be found.")
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        AddonIncarnationState.Snapshot snapshot =
                AddonIncarnationState.inspect(target);
        long now = System.currentTimeMillis();
        long epochGuardRemainingMillis = Math.max(
                0L,
                snapshot.resetGuardUntilEpochMillis() - now
        );
        long tickGuardRemainingMillis = Math.max(
                0L,
                snapshot.resetGuardUntilGameTime()
                        - target.serverLevel().getGameTime()
        ) * 50L;
        long guardRemainingMillis = Math.max(
                epochGuardRemainingMillis,
                tickGuardRemainingMillis
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Addon Lifecycle: "
                                        + target.getGameProfile().getName()
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "State revision: " + snapshot.revision()
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Life token: " + snapshot.lifeToken()
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Reset sequence: "
                                        + snapshot.resetSequence()
                                        + " | Last reason: "
                                        + (snapshot.lastResetReason().isBlank()
                                        ? "none"
                                        : snapshot.lastResetReason())
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Reset guard: "
                                        + (snapshot.resetGuardActive()
                                        ? "active for about "
                                          + String.format(
                                        Locale.US,
                                        "%.1f",
                                        guardRemainingMillis / 1000.0D
                                )
                                          + " seconds"
                                        : "inactive")
                        )
                        .withStyle(
                                snapshot.resetGuardActive()
                                        ? ChatFormatting.YELLOW
                                        : ChatFormatting.GREEN
                        ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Native endowment marker: "
                                        + (snapshot.nativeEndowmentIncarnation()
                                        .isBlank()
                                        ? "none"
                                        : snapshot.nativeEndowmentIncarnation())
                                        + " | Failed attempts: "
                                        + snapshot.nativeEndowmentAttempts()
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                                "Authority observation: "
                                        + (snapshot.authorityObservationInitialized()
                                        ? "initialized"
                                        : "not initialized")
                                        + " | Last owned: "
                                        + snapshot.authorityLastOwned()
                                        + " | Acquired this life: "
                                        + snapshot.authorityAcquiredThisLife()
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        return 1;
    }

    private static int sendGuide(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        return PlayerGuidanceService.sendGuide(
                source,
                source.getPlayerOrException()
        );
    }

    private static int sendSageGuide(
            CommandSourceStack source
    ) {
        int requiredXpLevel =
                MoosTensuraConfig
                        .SAGE_UPGRADE_REQUIRED_XP_LEVEL
                        .get();

        int relativeLevelDeduction =
                MoosTensuraConfig
                        .SAGE_UPGRADE_RELATIVE_LEVEL_DEDUCTION
                        .get();

        double requiredEp =
                MoosTensuraConfig
                        .GREAT_SAGE_RITUAL_REQUIRED_EP
                        .get();

        int requiredMasteredSkills =
                MoosTensuraConfig
                        .GREAT_SAGE_RITUAL_REQUIRED_MASTERED_SKILLS
                        .get();

        boolean requireNamed =
                MoosTensuraConfig
                        .GREAT_SAGE_RITUAL_REQUIRE_NAMED
                        .get();

        int ritualDurationTicks =
                MoosTensuraConfig
                        .GREAT_SAGE_RITUAL_DURATION_TICKS
                        .get();

        int ritualDurationSeconds =
                Math.max(
                        1,
                        ritualDurationTicks / 20
                );

        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.header",
                ChatFormatting.AQUA,
                true
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.goal",
                ChatFormatting.GRAY,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.step.obtain",
                ChatFormatting.GRAY,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.step.craft",
                ChatFormatting.GRAY,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.recipe.corners",
                ChatFormatting.DARK_AQUA,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.recipe.edges",
                ChatFormatting.DARK_AQUA,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.recipe.center",
                ChatFormatting.DARK_AQUA,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.step.attune",
                ChatFormatting.GRAY,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.step.follow",
                ChatFormatting.GRAY,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.step.interact",
                ChatFormatting.GRAY,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.step.endure",
                ChatFormatting.GRAY,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.step.complete",
                ChatFormatting.GRAY,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.requirements",
                ChatFormatting.GOLD,
                false
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.requirement.level",
                ChatFormatting.YELLOW,
                false,
                requiredXpLevel
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.requirement.xp_cost",
                ChatFormatting.YELLOW,
                false,
                relativeLevelDeduction
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.requirement.ep",
                ChatFormatting.YELLOW,
                false,
                formatWholeNumber(requiredEp)
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.requirement.mastered",
                ChatFormatting.YELLOW,
                false,
                requiredMasteredSkills
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.requirement.named",
                ChatFormatting.YELLOW,
                false,
                Component.translatable(
                        requireNamed
                                ? "gui.yes"
                                : "gui.no"
                )
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.requirement.duration",
                ChatFormatting.YELLOW,
                false,
                ritualDurationSeconds
        );
        sendTranslatedLine(
                source,
                "message.moostensuraaddon.guide.sage.tip",
                ChatFormatting.DARK_AQUA,
                false
        );

        return 1;
    }

    private static void sendTranslatedLine(
            CommandSourceStack source,
            String translationKey,
            ChatFormatting color,
            boolean bold,
            Object... arguments
    ) {
        MutableComponent line = Component.translatable(
                translationKey,
                arguments
        );

        if (bold) {
            line.withStyle(
                    color,
                    ChatFormatting.BOLD
            );
        } else {
            line.withStyle(color);
        }

        source.sendSuccess(() -> line, false);
    }

    private static int sendHelp(
            CommandSourceStack source
    ) {
        return PlayerGuidanceService.sendHelp(source);
    }

    private static int requestUnname(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player =
                source.getPlayerOrException();

        IExistence existence =
                TensuraStorages.getExistenceFrom(
                        player
                );

        String storedName =
                existence == null
                        || existence.getName() == null
                        || existence.getName().isBlank()
                        ? "none"
                        : existence.getName();

        AdminConfirmationTracker.arm(
                source,
                AdminConfirmationTracker.Action.UNNAME,
                player.getUUID()
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Unname confirmation required"
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Current native Tensura name: "
                                        + storedName
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "This will clear your native Tensura name and this addon's committed naming result."
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Recognition deeds, TH/TDL status, skills, EP, magicules, mastery and subordinate progress will be preserved."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Run /moostensura unname confirm within "
                                        + AdminConfirmationTracker
                                        .CONFIRMATION_WINDOW_SECONDS
                                        + " seconds to continue."
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return 1;
    }

    private static int confirmUnname(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player =
                source.getPlayerOrException();

        AdminConfirmationTracker.Result confirmation =
                AdminConfirmationTracker.consume(
                        source,
                        AdminConfirmationTracker.Action.UNNAME,
                        player.getUUID()
                );

        if (!confirmation.confirmed()) {
            return sendConfirmationFailure(
                    source,
                    confirmation,
                    "/moostensura unname"
            );
        }

        RecognitionUnnameService.Result result =
                RecognitionUnnameService.unname(
                        player
                );

        if (!result.success()) {
            source.sendFailure(
                    Component.literal(
                                    "Could not unname the player: "
                                            + result.errorMessage()
                            )
                            .withStyle(ChatFormatting.RED)
            );

            return 0;
        }

        String previousName =
                result.previouslyNamed()
                        ? result.previousNativeName()
                        : "none";

        source.sendSuccess(
                () -> Component.literal(
                                "Native Tensura naming state cleared successfully."
                        )
                        .withStyle(
                                ChatFormatting.GREEN,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Previous native name: "
                                        + previousName
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Run /checkrecognition to verify that naming eligibility is now ready."
                        )
                        .withStyle(ChatFormatting.AQUA),
                false
        );

        return 1;
    }

    private static int requestConfigReset(
            CommandSourceStack source
    ) {
        AdminConfirmationTracker.arm(
                source,
                AdminConfirmationTracker.Action.RESET_CONFIG
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Config reset confirmation required"
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "This will restore every Moos Tensura Addon config value to its addon default."
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Run /moostensura resetconfig confirm within "
                                        + AdminConfirmationTracker
                                        .CONFIRMATION_WINDOW_SECONDS
                                        + " seconds to continue."
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return 1;
    }

    private static int resetConfig(
            CommandSourceStack source
    ) {
        AdminConfirmationTracker.Result confirmation =
                AdminConfirmationTracker.consume(
                        source,
                        AdminConfirmationTracker.Action.RESET_CONFIG
                );

        if (!confirmation.confirmed()) {
            return sendConfirmationFailure(
                    source,
                    confirmation,
                    "/moostensura resetconfig"
            );
        }

        MoosTensuraConfig.resetToAddonDefaults();

        source.sendSuccess(
                () -> Component.literal(
                                "Moos Tensura Addon config was reset to addon defaults."
                        )
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        return 1;
    }

    private static int requestAddonDataReset(
            CommandSourceStack source,
            ServerPlayer target
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

        AdminConfirmationTracker.arm(
                source,
                AdminConfirmationTracker.Action
                        .RESET_PLAYER_DATA,
                target.getUUID()
        );

        String targetName =
                target.getGameProfile()
                        .getName();

        source.sendSuccess(
                () -> Component.literal(
                                "Player-data reset confirmation required"
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Target: "
                                        + targetName
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "This will reset the addon's stored Granter, borrowed-skill and recognition data for that player."
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "The global addon configuration will not be changed."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Run /moostensura reset "
                                        + targetName
                                        + " confirm within "
                                        + AdminConfirmationTracker
                                        .CONFIRMATION_WINDOW_SECONDS
                                        + " seconds to continue."
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return 1;
    }

    private static int resetAddonData(
            CommandSourceStack source,
            ServerPlayer target
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

        AdminConfirmationTracker.Result confirmation =
                AdminConfirmationTracker.consume(
                        source,
                        AdminConfirmationTracker.Action
                                .RESET_PLAYER_DATA,
                        target.getUUID()
                );

        if (!confirmation.confirmed()) {
            return sendConfirmationFailure(
                    source,
                    confirmation,
                    "/moostensura reset "
                            + target.getGameProfile().getName()
            );
        }

        AddonPlayerDataResetService.ResetResult result =
                AddonPlayerDataResetService.resetForNewIncarnation(
                        target,
                        AddonPlayerDataResetService.ResetReason.ADMIN_COMMAND
                );

        if (!result.successful()) {
            source.sendFailure(
                    Component.literal(result.message())
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        String targetName = target.getGameProfile().getName();

        source.sendSuccess(
                () -> Component.literal(
                                "Reset Moos Tensura Addon life-bound data for "
                                        + targetName
                                        + ". The addon configuration was not changed."
                        )
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        target.sendSystemMessage(
                Component.literal(
                                "Your Moos Tensura Addon life-bound data was reset by an administrator."
                        )
                        .withStyle(ChatFormatting.YELLOW)
        );

        return 1;
    }

    private static int sendConfirmationFailure(
            CommandSourceStack source,
            AdminConfirmationTracker.Result result,
            String requestCommand
    ) {
        String explanation = switch (result.status()) {
            case EXPIRED ->
                    "The confirmation expired.";

            case DIFFERENT_ACTION ->
                    "A different destructive action was awaiting confirmation.";

            case DIFFERENT_TARGET ->
                    "The pending confirmation was created for a different player.";

            case MISSING, CONFIRMED ->
                    "No matching confirmation request exists.";
        };

        source.sendFailure(
                Component.literal(
                                explanation
                                        + " Run "
                                        + requestCommand
                                        + " first, then use its confirm command within "
                                        + AdminConfirmationTracker
                                        .CONFIRMATION_WINDOW_SECONDS
                                        + " seconds."
                        )
                        .withStyle(ChatFormatting.RED)
        );

        return 0;
    }

    private static String formatWholeNumber(
            double value
    ) {
        return String.format(
                Locale.US,
                "%,.0f",
                value
        );
    }
}