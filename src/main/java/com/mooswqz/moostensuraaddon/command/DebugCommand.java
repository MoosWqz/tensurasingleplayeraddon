package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionNameColorService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionPath;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Locale;

public final class DebugCommand {

    private DebugCommand() {
    }

    /**
     * Attaches the debug command directly to the canonical /moostensura root.
     *
     * MoosTensuraCommand.register(...) must run before this method.
     */
    public static void attachToMoosTensuraRoot(
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

        if (moosTensuraRoot == null) {
            throw new IllegalStateException(
                    "Cannot attach the debug command because "
                            + "/moostensura has not been registered."
            );
        }

        /*
         * Protect against accidental duplicate attachment if command
         * registration is invoked more than once.
         */
        if (moosTensuraRoot.getChild("debug") != null) {
            return;
        }

        moosTensuraRoot.addChild(
                createDebugNode().build()
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return Commands.literal("debug")
                .requires(
                        DebugModeService
                                ::canViewDebugStatus
                )

                /*
                 * /moostensura debug
                 */
                .executes(context ->
                        showStatus(
                                context.getSource()
                        )
                )

                /*
                 * /moostensura debug status
                 */
                .then(
                        Commands.literal("status")
                                .executes(context ->
                                        showStatus(
                                                context.getSource()
                                        )
                                )
                )

                /*
                 * /moostensura debug enable
                 * /moostensura debug enable confirm
                 */
                .then(
                        Commands.literal("enable")
                                .requires(
                                        DebugModeService
                                                ::canControlDebugMode
                                )
                                .executes(context ->
                                        requestEnable(
                                                context.getSource()
                                        )
                                )
                                .then(
                                        Commands.literal("confirm")
                                                .executes(context ->
                                                        confirmEnable(
                                                                context.getSource()
                                                        )
                                                )
                                )
                )

                /*
                 * /moostensura debug disable
                 */
                .then(
                        Commands.literal("disable")
                                .requires(
                                        DebugModeService
                                                ::canControlDebugMode
                                )
                                .executes(context ->
                                        disable(
                                                context.getSource()
                                        )
                                )
                )

                /*
                 * Canonical nested developer tools.
                 */
                .then(
                        RecognitionDebugCommand
                                .createDebugNode()
                )
                .then(
                        RecognitionCommittedResultCommand
                                .createDebugNode()
                )
                .then(
                        RecognitionFreedomDebugCommand
                                .createDebugNode()
                )
                .then(
                        RecognitionIdentityHistoryDebugCommand
                                .createDebugNode()
                )
                .then(
                        RecognitionReleaseDebugCommand
                                .createDebugNode()
                )
                .then(
                        RecognitionStrengthRewardDebugCommand
                                .createDebugNode()
                )
                .then(
                        RecognitionTechnicalHardeningDebugCommand
                                .createDebugNode()
                )
                .then(
                        CheckNamedCommand
                                .createDebugNode()
                )
                .then(
                        UpgradeSageCommand
                                .createDebugNode()
                )

                /*
                 * /moostensura debug namecolors
                 *
                 * This branch only exists in the command tree presented to
                 * administrators while debug mode is enabled.
                 */
                .then(
                        Commands.literal("namecolors")
                                .requires(
                                        DebugModeService
                                                ::canUseDebugTools
                                )
                                .executes(context ->
                                        showBaseColors(
                                                context.getSource()
                                        )
                                )
                                .then(
                                        Commands.literal("crossings")
                                                .then(
                                                        Commands.argument(
                                                                        "primaryPath",
                                                                        StringArgumentType.word()
                                                                )
                                                                .suggests(
                                                                        (
                                                                                context,
                                                                                builder
                                                                        ) ->
                                                                                SharedSuggestionProvider.suggest(
                                                                                        Arrays.stream(
                                                                                                        RecognitionPath.values()
                                                                                                )
                                                                                                .map(
                                                                                                        RecognitionPath
                                                                                                                ::getId
                                                                                                ),
                                                                                        builder
                                                                                )
                                                                )
                                                                .executes(context ->
                                                                        showCrossings(
                                                                                context.getSource(),
                                                                                StringArgumentType.getString(
                                                                                        context,
                                                                                        "primaryPath"
                                                                                )
                                                                        )
                                                                )
                                                )
                                )
                );
    }

    private static int showStatus(
            CommandSourceStack source
    ) {
        boolean enabled =
                DebugModeService.isEnabled();

        source.sendSuccess(
                () -> Component.literal(
                                "Moos Tensura Addon Debug Mode"
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "State: "
                                        + (
                                        enabled
                                                ? "ENABLED"
                                                : "DISABLED"
                                )
                        )
                        .withStyle(
                                enabled
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.GRAY
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Config authority: server"
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Standard debug permission: level "
                                        + DebugModeService
                                        .STANDARD_DEBUG_PERMISSION_LEVEL
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Dangerous debug permission: level "
                                        + DebugModeService
                                        .DANGEROUS_DEBUG_PERMISSION_LEVEL
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        ),
                false
        );

        if (!enabled
                && source.hasPermission(
                DebugModeService
                        .DANGEROUS_DEBUG_PERMISSION_LEVEL
        )) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "Use /moostensura debug enable to begin the confirmation process."
                            )
                            .withStyle(
                                    ChatFormatting.YELLOW
                            ),
                    false
            );
        }

        if (enabled) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "Available tools: recognition, committed, freedom, history, release, strength, hardening, named, sage and namecolors."
                            )
                            .withStyle(
                                    ChatFormatting.AQUA
                            ),
                    false
            );

            source.sendSuccess(
                    () -> Component.literal(
                                    "Use /moostensura debug <tool> to open a developer branch."
                            )
                            .withStyle(
                                    ChatFormatting.DARK_AQUA
                            ),
                    false
            );
        }

        return 1;
    }

    private static int requestEnable(
            CommandSourceStack source
    ) {
        if (DebugModeService.isEnabled()) {
            AdminConfirmationTracker.clear(
                    source
            );

            source.sendSuccess(
                    () -> Component.literal(
                                    "Debug mode is already enabled."
                            )
                            .withStyle(
                                    ChatFormatting.YELLOW
                            ),
                    false
            );

            return 1;
        }

        AdminConfirmationTracker.arm(
                source,
                AdminConfirmationTracker.Action
                        .ENABLE_DEBUG_MODE
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Debug mode confirmation required"
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Enabling debug mode exposes developer diagnostics and testing commands to authorized administrators."
                        )
                        .withStyle(
                                ChatFormatting.YELLOW
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Normal players remain blocked by server command permissions."
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Run /moostensura debug enable confirm within "
                                        + AdminConfirmationTracker
                                        .CONFIRMATION_WINDOW_SECONDS
                                        + " seconds."
                        )
                        .withStyle(
                                ChatFormatting.RED,
                                ChatFormatting.BOLD
                        ),
                false
        );

        return 1;
    }

    private static int confirmEnable(
            CommandSourceStack source
    ) {
        if (DebugModeService.isEnabled()) {
            AdminConfirmationTracker.clear(
                    source
            );

            source.sendSuccess(
                    () -> Component.literal(
                                    "Debug mode is already enabled."
                            )
                            .withStyle(
                                    ChatFormatting.YELLOW
                            ),
                    false
            );

            refreshCommandTrees(source);
            return 1;
        }

        AdminConfirmationTracker.Result confirmation =
                AdminConfirmationTracker.consume(
                        source,
                        AdminConfirmationTracker.Action
                                .ENABLE_DEBUG_MODE
                );

        if (!confirmation.confirmed()) {
            return sendConfirmationFailure(
                    source,
                    confirmation
            );
        }

        boolean changed =
                DebugModeService.setEnabled(
                        true
                );

        if (!changed) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "Debug mode was already enabled."
                            )
                            .withStyle(
                                    ChatFormatting.YELLOW
                            ),
                    false
            );

            refreshCommandTrees(source);
            return 1;
        }

        source.sendSuccess(
                () -> Component.literal(
                                "Debug mode enabled."
                        )
                        .withStyle(
                                ChatFormatting.GREEN,
                                ChatFormatting.BOLD
                        ),
                true
        );

        source.sendSuccess(
                () -> Component.literal(
                                "The server config was saved immediately."
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Use /moostensura debug disable when testing is finished."
                        )
                        .withStyle(
                                ChatFormatting.AQUA
                        ),
                false
        );

        /*
         * Resend the command tree so debug-only branches become visible
         * immediately without requiring a reconnect.
         */
        refreshCommandTrees(source);

        return 1;
    }

    private static int disable(
            CommandSourceStack source
    ) {
        AdminConfirmationTracker.clear(
                source
        );

        boolean changed =
                DebugModeService.setEnabled(
                        false
                );

        if (!changed) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "Debug mode is already disabled."
                            )
                            .withStyle(
                                    ChatFormatting.GRAY
                            ),
                    false
            );

            refreshCommandTrees(source);
            return 1;
        }

        source.sendSuccess(
                () -> Component.literal(
                                "Debug mode disabled."
                        )
                        .withStyle(
                                ChatFormatting.GREEN,
                                ChatFormatting.BOLD
                        ),
                true
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Developer tools are no longer available."
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        ),
                false
        );

        /*
         * Resend the command tree so debug-only branches disappear
         * immediately.
         */
        refreshCommandTrees(source);

        return 1;
    }

    private static int showBaseColors(
            CommandSourceStack source
    ) {
        source.sendSuccess(
                () -> Component.literal(
                                "Recognition Name Colors"
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Normal paths use the base RGB color. Pure paths use the same color in bold."
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        ),
                false
        );

        for (RecognitionPath path :
                RecognitionPath.values()) {

            MutableComponent normalPreview =
                    RecognitionNameColorService
                            .createBasePreview(
                                    path,
                                    false
                            );

            source.sendSuccess(
                    () -> normalPreview,
                    false
            );

            MutableComponent purePreview =
                    RecognitionNameColorService
                            .createBasePreview(
                                    path,
                                    true
                            );

            source.sendSuccess(
                    () -> purePreview,
                    false
            );
        }

        source.sendSuccess(
                () -> Component.literal(
                                "Crossing preview: /moostensura debug namecolors crossings <primaryPath>"
                        )
                        .withStyle(
                                ChatFormatting.AQUA
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Example: /moostensura debug namecolors crossings lawful_evil"
                        )
                        .withStyle(
                                ChatFormatting.DARK_AQUA
                        ),
                false
        );

        return 1;
    }

    private static int showCrossings(
            CommandSourceStack source,
            String rawPrimaryPath
    ) {
        RecognitionPath primaryPath =
                findPath(
                        rawPrimaryPath
                );

        if (primaryPath == null) {
            source.sendFailure(
                    Component.literal(
                                    "Unknown recognition path: "
                                            + rawPrimaryPath
                            )
                            .withStyle(
                                    ChatFormatting.RED
                            )
            );

            source.sendSuccess(
                    () -> Component.literal(
                                    "Valid paths: "
                                            + String.join(
                                            ", ",
                                            Arrays.stream(
                                                            RecognitionPath.values()
                                                    )
                                                    .map(
                                                            RecognitionPath
                                                                    ::getId
                                                    )
                                                    .toList()
                                    )
                            )
                            .withStyle(
                                    ChatFormatting.GRAY
                            ),
                    false
            );

            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                                "Recognition Crossings — Primary: "
                                        + RecognitionNameColorService
                                        .getDisplayName(
                                                primaryPath
                                        )
                        )
                        .withStyle(
                                ChatFormatting.LIGHT_PURPLE,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "Every result below is 70% primary and 30% secondary."
                        )
                        .withStyle(
                                ChatFormatting.GRAY
                        ),
                false
        );

        for (RecognitionPath secondaryPath :
                RecognitionPath.values()) {

            if (secondaryPath == primaryPath) {
                continue;
            }

            MutableComponent preview =
                    RecognitionNameColorService
                            .createCrossingPreview(
                                    primaryPath,
                                    secondaryPath
                            );

            source.sendSuccess(
                    () -> preview,
                    false
            );
        }

        source.sendSuccess(
                () -> Component.literal(
                                "Reverse the primary path to preview the opposite 70/30 weighting."
                        )
                        .withStyle(
                                ChatFormatting.DARK_AQUA
                        ),
                false
        );

        return 1;
    }

    private static RecognitionPath findPath(
            String rawPath
    ) {
        if (rawPath == null
                || rawPath.isBlank()) {

            return null;
        }

        String normalized =
                rawPath.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        for (RecognitionPath path :
                RecognitionPath.values()) {

            if (path.getId()
                    .equals(normalized)) {

                return path;
            }
        }

        return null;
    }

    private static int sendConfirmationFailure(
            CommandSourceStack source,
            AdminConfirmationTracker.Result confirmation
    ) {
        String explanation =
                switch (confirmation.status()) {
                    case EXPIRED ->
                            "The debug-mode confirmation expired.";

                    case DIFFERENT_ACTION ->
                            "A different administrative action was awaiting confirmation.";

                    case DIFFERENT_TARGET ->
                            "The pending confirmation belonged to a different target.";

                    case MISSING, CONFIRMED ->
                            "No debug-mode confirmation request exists.";
                };

        source.sendFailure(
                Component.literal(
                                explanation
                                        + " Run /moostensura debug enable first, then confirm within "
                                        + AdminConfirmationTracker
                                        .CONFIRMATION_WINDOW_SECONDS
                                        + " seconds."
                        )
                        .withStyle(
                                ChatFormatting.RED
                        )
        );

        return 0;
    }

    /**
     * Command requirements are used when Minecraft constructs the client-side
     * command tree.
     *
     * Resending that tree makes commands controlled by debug mode appear or
     * disappear immediately after the setting changes.
     */
    private static void refreshCommandTrees(
            CommandSourceStack source
    ) {
        if (source == null) {
            return;
        }

        MinecraftServer server =
                source.getServer();

        if (server == null) {
            return;
        }

        for (ServerPlayer player :
                server.getPlayerList()
                        .getPlayers()) {

            server.getCommands()
                    .sendCommands(player);
        }
    }
}