package com.mooswqz.moostensuraaddon.command;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class ModCommandRegistry {

    private ModCommandRegistry() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event
    ) {
        /*
         * Register the canonical /moostensura root first.
         */
        MoosTensuraCommand.register(
                event.getDispatcher()
        );

        /*
         * Attach /moostensura debug directly to the root that was just
         * registered.
         *
         * Registering a second independent /moostensura literal proved
         * unreliable in the current command layout.
         */
        DebugCommand.attachToMoosTensuraRoot(
                event.getDispatcher()
        );

        /*
         * Keep the old development roots as temporary compatibility aliases.
         * Their own requirements make them invisible and unusable whenever
         * debug mode is disabled.
         */
        UpgradeSageCommand.registerLegacyAlias(
                event.getDispatcher()
        );

        CheckNamedCommand.registerLegacyAlias(
                event.getDispatcher()
        );

        RecognitionDebugCommand.registerLegacyAlias(
                event.getDispatcher()
        );

        /*
         * /getnamed remains a normal player-facing legacy progression command.
         * It is controlled by its existing server config and requirements, not
         * by developer debug mode.
         */
        GetNamedCommand.register(
                event.getDispatcher()
        );
    }
}