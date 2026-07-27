package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.recognition.RecognitionIdentityHistoryService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * One-shot login migration for the independent identity-history namespace.
 *
 * <p>No path evaluation, decay or modifier resolution occurs here.</p>
 */
@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class RecognitionIdentityHistoryEvents {

    private RecognitionIdentityHistoryEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        RecognitionData data = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        RecognitionIdentityHistoryService.ensureCurrent(data);
    }
}