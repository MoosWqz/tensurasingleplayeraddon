package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.util.GreatSageEvolutionService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class UpgradeSageCommand {

    private UpgradeSageCommand() {
    }

    /**
     * Creates the canonical nested debug branch:
     *
     * /moostensura debug sage
     * /moostensura debug sage upgrade
     * /moostensura debug sage force confirm
     */
    public static LiteralArgumentBuilder<CommandSourceStack>
    createDebugNode() {
        return Commands.literal("sage")
                .requires(
                        DebugModeService
                                ::canUseDebugTools
                )
                .executes(context -> sendUsage(
                        context.getSource()
                ))
                .then(
                        Commands.literal("upgrade")
                                .executes(context -> executeNormalUpgrade(
                                        context.getSource()
                                ))
                )
                .then(
                        Commands.literal("force")
                                .requires(
                                        UpgradeSageCommand
                                                ::canUseDangerousDebugTool
                                )
                                .then(
                                        Commands.literal("confirm")
                                                .executes(context -> executeForceUpgrade(
                                                        context.getSource()
                                                ))
                                )
                );
    }

    /**
     * Temporary compatibility alias for /upgradesage.
     */
    public static void registerLegacyAlias(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
                Commands.literal("upgradesage")
                        .requires(
                                DebugModeService
                                        ::canUseDebugTools
                        )
                        .executes(context -> executeNormalUpgrade(
                                context.getSource()
                        ))
                        .then(
                                Commands.literal("forceupgrade")
                                        .requires(
                                                UpgradeSageCommand
                                                        ::canUseDangerousDebugTool
                                        )
                                        .then(
                                                Commands.literal("confirm")
                                                        .executes(context -> executeForceUpgrade(
                                                                context.getSource()
                                                        ))
                                        )
                        )
                        .then(
                                Commands.literal("force")
                                        .requires(
                                                UpgradeSageCommand
                                                        ::canUseDangerousDebugTool
                                        )
                                        .then(
                                                Commands.literal("confirm")
                                                        .executes(context -> executeForceUpgrade(
                                                                context.getSource()
                                                        ))
                                        )
                        )
        );
    }

    private static boolean canUseDangerousDebugTool(
            CommandSourceStack source
    ) {
        return DebugModeService.isEnabled()
                && source.hasPermission(
                DebugModeService
                        .DANGEROUS_DEBUG_PERMISSION_LEVEL
        );
    }

    private static int sendUsage(
            CommandSourceStack source
    ) {
        source.sendSuccess(
                () -> Component.literal(
                                "Sage Debug Tools"
                        )
                        .withStyle(
                                ChatFormatting.AQUA,
                                ChatFormatting.BOLD
                        ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                                "/moostensura debug sage upgrade"
                        )
                        .withStyle(
                                ChatFormatting.YELLOW
                        )
                        .append(
                                Component.literal(
                                                " - Attempts the normal Sage -> Great Sage upgrade with all requirements and costs."
                                        )
                                        .withStyle(
                                                ChatFormatting.GRAY
                                        )
                        ),
                false
        );

        if (source.hasPermission(
                DebugModeService
                        .DANGEROUS_DEBUG_PERMISSION_LEVEL
        )) {
            source.sendSuccess(
                    () -> Component.literal(
                                    "/moostensura debug sage force confirm"
                            )
                            .withStyle(
                                    ChatFormatting.RED
                            )
                            .append(
                                    Component.literal(
                                                    " - Forces the Great Sage upgrade for testing."
                                            )
                                            .withStyle(
                                                    ChatFormatting.GRAY
                                            )
                            ),
                    false
            );
        }

        return 1;
    }

    private static int executeNormalUpgrade(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player =
                source.getPlayerOrException();

        GreatSageEvolutionService.EvolutionResult result =
                GreatSageEvolutionService
                        .attemptNormalUpgrade(
                                player
                        );

        player.sendSystemMessage(
                result.message()
        );

        return result.successful()
                ? 1
                : 0;
    }

    private static int executeForceUpgrade(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player =
                source.getPlayerOrException();

        GreatSageEvolutionService.EvolutionResult result =
                GreatSageEvolutionService
                        .forceUpgrade(
                                player
                        );

        player.sendSystemMessage(
                result.message()
        );

        return result.successful()
                ? 1
                : 0;
    }
}