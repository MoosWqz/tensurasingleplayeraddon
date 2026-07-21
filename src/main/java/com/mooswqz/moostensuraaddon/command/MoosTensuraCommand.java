package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.BorrowedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GrantedSkillData;
import com.mooswqz.moostensuraaddon.attachment.GranterProgressData;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionDisplayNameSyncService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionProgressScreenService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionUnnameService;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
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
                        .executes(context -> sendHelp(
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
                                Commands.literal("paths")
                                        .executes(context -> openRecognitionPaths(
                                                context.getSource()
                                        ))
                        )

                        .then(
                                Commands.literal("help")
                                        .executes(context -> sendHelp(
                                                context.getSource()
                                        ))
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
        ServerPlayer player =
                source.getPlayerOrException();

        RecognitionProgressScreenService.open(
                player
        );

        return 1;
    }

    private static int sendGuide(
            CommandSourceStack source
    ) {
        source.sendSuccess(
                () -> Component.literal(
                                "Moos Tensura Addon Guide"
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "1. Become named or endowed to anchor your soul."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "2. Obtain Sage and listen for the pull of crystallized soul data."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "3. Craft a Soul Resonator and attune it to a Great Crystal Shrine."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "4. Follow the resonator needle, meet the altar requirements, and begin the ritual."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "5. Awaken Great Sage, then continue mastering skills and gathering subordinates."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "6. If your authority matures, the Unique Skill Granter may awaken."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "7. Granter can bestow mastered skills, reclaim what was granted, and eventually evolve into an Ultimate Skill path."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Use /moostensura guide sage for detailed Sage -> Great Sage guidance."
                        )
                        .withStyle(ChatFormatting.DARK_AQUA),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Hint: Some awakenings do not reveal themselves through exact numbers. Experiment, grow, and guide your subordinates."
                        )
                        .withStyle(ChatFormatting.DARK_AQUA),
                false
        );

        return 1;
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

        source.sendSuccess(
                () -> Component.literal(
                                "Sage Progression"
                        )
                        .withStyle(
                                ChatFormatting.AQUA,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Goal: evolve Sage into Great Sage through a Great Crystal Altar ritual."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "1. Obtain Sage."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "2. Craft a Soul Resonator:"
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "   Corners: medium quality magic crystals"
                        )
                        .withStyle(ChatFormatting.DARK_AQUA),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "   Edges: magic stones"
                        )
                        .withStyle(ChatFormatting.DARK_AQUA),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "   Center: compass"
                        )
                        .withStyle(ChatFormatting.DARK_AQUA),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "3. Right-click the Soul Resonator while you have Sage."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "4. If a shrine answers, follow the needle to the Great Crystal Shrine."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "5. Meet the ritual requirements and interact with the Great Crystal Altar."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "6. Endure the ritual while Sage analyzes the altar, deciphers its data, and petitions for evolution."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "7. If the ritual completes, Sage awakens into Great Sage."
                        )
                        .withStyle(ChatFormatting.GRAY),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Current ritual requirements:"
                        )
                        .withStyle(ChatFormatting.GOLD),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "   XP level: "
                                        + requiredXpLevel
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "   XP cost on success: "
                                        + relativeLevelDeduction
                                        + " relative levels"
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "   EP: "
                                        + formatWholeNumber(
                                        requiredEp
                                )
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "   Mastered skills: "
                                        + requiredMasteredSkills
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "   Named/endowed required: "
                                        + (
                                        requireNamed
                                                ? "yes"
                                                : "no"
                                )
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "   Ritual duration: about "
                                        + ritualDurationSeconds
                                        + " seconds"
                        )
                        .withStyle(ChatFormatting.YELLOW),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Tip: Normal progression happens through the shrine ritual. Direct Sage upgrade tools are available only to authorized administrators in debug mode."
                        )
                        .withStyle(ChatFormatting.DARK_AQUA),
                false
        );

        return 1;
    }

    private static int sendHelp(
            CommandSourceStack source
    ) {
        source.sendSuccess(
                () -> Component.literal(
                                "Moos Tensura Addon Commands"
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                false
        );

        sendHelpLine(
                source,
                "/moostensura guide",
                "Shows the intended progression path without exact spoilers.",
                ChatFormatting.AQUA
        );

        sendHelpLine(
                source,
                "/moostensura guide sage",
                "Shows Sage -> Great Sage progression guidance.",
                ChatFormatting.AQUA
        );

        sendHelpLine(
                source,
                "/moostensura paths",
                "Opens your soul-recognition progress screen.",
                ChatFormatting.AQUA
        );

        sendHelpLine(
                source,
                "/moostensura help",
                "Shows this command list.",
                ChatFormatting.AQUA
        );

        sendHelpLine(
                source,
                "/getnamed",
                "Attempts legacy self-endowment progression when enabled in the server config.",
                ChatFormatting.AQUA
        );

        if (source.hasPermission(2)) {
            sendHelpLine(
                    source,
                    "/moostensura unname",
                    "Admin-only. Arms native unname confirmation for 30 seconds.",
                    ChatFormatting.RED
            );

            sendHelpLine(
                    source,
                    "/moostensura unname confirm",
                    "Admin-only. Requires /moostensura unname first.",
                    ChatFormatting.RED
            );

            sendHelpLine(
                    source,
                    "/moostensura resetconfig",
                    "Admin-only. Arms config reset confirmation for 30 seconds.",
                    ChatFormatting.RED
            );

            sendHelpLine(
                    source,
                    "/moostensura resetconfig confirm",
                    "Admin-only. Requires /moostensura resetconfig first.",
                    ChatFormatting.RED
            );

            sendHelpLine(
                    source,
                    "/moostensura reset <player>",
                    "Admin-only. Arms player-data reset for that player.",
                    ChatFormatting.RED
            );

            sendHelpLine(
                    source,
                    "/moostensura reset <player> confirm",
                    "Admin-only. Requires the matching reset request first.",
                    ChatFormatting.RED
            );

            sendHelpLine(
                    source,
                    "/moostensura debug status",
                    "Shows whether server debug mode is enabled.",
                    ChatFormatting.YELLOW
            );
        }

        if (source.hasPermission(
                DebugModeService
                        .DANGEROUS_DEBUG_PERMISSION_LEVEL
        )) {
            sendHelpLine(
                    source,
                    "/moostensura debug enable",
                    "Arms the confirmation required to enable server debug mode.",
                    ChatFormatting.YELLOW
            );

            sendHelpLine(
                    source,
                    "/moostensura debug disable",
                    "Immediately disables server debug mode.",
                    ChatFormatting.YELLOW
            );
        }

        if (DebugModeService.isEnabled()
                && source.hasPermission(
                DebugModeService
                        .STANDARD_DEBUG_PERMISSION_LEVEL
        )) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "Enabled Debug Tools"
                            )
                            .withStyle(
                                    ChatFormatting.LIGHT_PURPLE,
                                    ChatFormatting.BOLD
                            ),
                    false
            );

            sendHelpLine(
                    source,
                    "/moostensura debug recognition [player]",
                    "Shows recognition scores, selection and naming eligibility.",
                    ChatFormatting.LIGHT_PURPLE
            );

            sendHelpLine(
                    source,
                    "/moostensura debug recognition probe [player]",
                    "Shows detailed TH/TDL detection evidence.",
                    ChatFormatting.LIGHT_PURPLE
            );

            sendHelpLine(
                    source,
                    "/moostensura debug named",
                    "Shows native naming and Granter awakening diagnostics.",
                    ChatFormatting.LIGHT_PURPLE
            );

            sendHelpLine(
                    source,
                    "/moostensura debug sage upgrade",
                    "Attempts the normal Sage -> Great Sage upgrade directly.",
                    ChatFormatting.LIGHT_PURPLE
            );

            sendHelpLine(
                    source,
                    "/moostensura debug namecolors",
                    "Previews all recognition-name colors and pure styling.",
                    ChatFormatting.LIGHT_PURPLE
            );

            sendHelpLine(
                    source,
                    "/moostensura debug namecolors crossings <primaryPath>",
                    "Previews all 70/30 crossings for one primary path.",
                    ChatFormatting.LIGHT_PURPLE
            );

            if (source.hasPermission(
                    DebugModeService
                            .DANGEROUS_DEBUG_PERMISSION_LEVEL
            )) {
                sendHelpLine(
                        source,
                        "/moostensura debug sage force confirm",
                        "Dangerous debug tool. Forces Great Sage for testing.",
                        ChatFormatting.RED
                );
            }

            source.sendSuccess(
                    () -> Component.literal(
                                    "Temporary aliases /checkrecognition, /checknamed and /upgradesage are also active while debug mode is enabled."
                            )
                            .withStyle(
                                    ChatFormatting.DARK_GRAY
                            ),
                    false
            );
        }

        source.sendSuccess(
                () -> Component.literal(
                                "Tip: Use /moostensura guide if you want progression hints instead of command details."
                        )
                        .withStyle(
                                ChatFormatting.DARK_AQUA
                        ),
                false
        );

        return 1;
    }

    private static void sendHelpLine(
            CommandSourceStack source,
            String command,
            String description,
            ChatFormatting commandColor
    ) {
        source.sendSuccess(
                () -> Component.literal(command)
                        .withStyle(commandColor)
                        .append(
                                Component.literal(
                                                " - "
                                                        + description
                                        )
                                        .withStyle(
                                                ChatFormatting.GRAY
                                        )
                        ),
                false
        );
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
                            + target.getGameProfile()
                            .getName()
            );
        }

        target.setData(
                AttachmentRegistry.GRANTED_SKILL_DATA,
                new GrantedSkillData()
        );

        target.setData(
                AttachmentRegistry.GRANTER_PROGRESS_DATA,
                new GranterProgressData()
        );

        target.setData(
                AttachmentRegistry.BORROWED_SKILL_DATA,
                new BorrowedSkillData()
        );

        target.setData(
                AttachmentRegistry.RECOGNITION_DATA,
                new RecognitionData()
        );

        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(target);

        String targetName =
                target.getGameProfile()
                        .getName();

        source.sendSuccess(
                () -> Component.literal(
                                "Reset Moos Tensura Addon data for "
                                        + targetName
                                        + ". The addon configuration was not changed."
                        )
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        target.sendSystemMessage(
                Component.literal(
                                "Your Moos Tensura Addon data was reset by an administrator."
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