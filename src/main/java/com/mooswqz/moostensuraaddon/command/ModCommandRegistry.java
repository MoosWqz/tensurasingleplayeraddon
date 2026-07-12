package com.mooswqz.moostensuraaddon.command;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public class ModCommandRegistry {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MoosTensuraCommand.register(event.getDispatcher());

        UpgradeSageCommand.register(event.getDispatcher());
        GetNamedCommand.register(event.getDispatcher());
        CheckNamedCommand.register(event.getDispatcher());
    }
}