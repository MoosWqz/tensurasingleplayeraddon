package com.mooswqz.moostensuraaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mooswqz.moostensuraaddon.util.GreatSageEvolutionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class UpgradeSageCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("upgradesage")
                .requires(source -> source.hasPermission(2))

                .executes(context -> executeNormalUpgrade(context.getSource()))

                .then(Commands.literal("forceupgrade")
                        .then(Commands.literal("confirm")
                                .executes(context -> executeForceUpgrade(context.getSource()))
                        )
                )

                .then(Commands.literal("force")
                        .then(Commands.literal("confirm")
                                .executes(context -> executeForceUpgrade(context.getSource()))
                        )
                )
        );
    }

    private static int executeNormalUpgrade(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        GreatSageEvolutionService.EvolutionResult result =
                GreatSageEvolutionService.attemptNormalUpgrade(player);

        player.sendSystemMessage(result.message());

        return result.successful() ? 1 : 0;
    }

    private static int executeForceUpgrade(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        GreatSageEvolutionService.EvolutionResult result =
                GreatSageEvolutionService.forceUpgrade(player);

        player.sendSystemMessage(result.message());

        return result.successful() ? 1 : 0;
    }
}