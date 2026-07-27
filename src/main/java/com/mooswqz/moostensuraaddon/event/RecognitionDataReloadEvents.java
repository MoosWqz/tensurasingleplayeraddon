package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.recognition.RecognitionBalanceReloadListener;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIndependenceMilestoneReloadListener;
import com.mooswqz.moostensuraaddon.recognition.RecognitionTitlePoolReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Registers all server-side recognition datapack reload listeners.
 */
@EventBusSubscriber(
        modid = MoosTensuraAddon.MODID
)
public final class RecognitionDataReloadEvents {

    private RecognitionDataReloadEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(
            AddReloadListenerEvent event
    ) {
        event.addListener(
                RecognitionBalanceReloadListener.INSTANCE
        );

        event.addListener(
                RecognitionTitlePoolReloadListener.INSTANCE
        );

        event.addListener(
                RecognitionIndependenceMilestoneReloadListener.INSTANCE
        );
    }
}