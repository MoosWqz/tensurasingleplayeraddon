package com.mooswqz.moostensuraaddon.client;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.recognition
        .RecognitionDisplayNameService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event
        .ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(
        modid = MoosTensuraAddon.MODID,
        value = Dist.CLIENT
)
public final class ClientRecognitionDisplayNameEvents {

    private ClientRecognitionDisplayNameEvents() {
    }

    /**
     * Applies the server-provided recognition title and finalized style to
     * client-rendered player nametags.
     */
    @SubscribeEvent
    public static void onNameFormat(
            PlayerEvent.NameFormat event
    ) {
        if (!event.getEntity()
                .level()
                .isClientSide()) {

            return;
        }

        ClientRecognitionDisplayNameCache
                .get(
                        event.getEntity()
                                .getUUID()
                )
                .ifPresent(display ->
                        event.setDisplayname(
                                RecognitionDisplayNameService
                                        .appendTitle(
                                                event.getDisplayname(),
                                                display.title(),
                                                display.rgbColor(),
                                                display.bold()
                                        )
                        )
                );
    }

    @SubscribeEvent
    public static void onClientLogout(
            ClientPlayerNetworkEvent.LoggingOut event
    ) {
        ClientRecognitionDisplayNameCache.clear();
    }
}