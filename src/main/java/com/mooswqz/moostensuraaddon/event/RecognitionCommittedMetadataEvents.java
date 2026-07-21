package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.recognition
        .RecognitionCommittedMetadataService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Performs the one-time player-context completion for migrated recognition
 * results when the owning player joins the logical server.
 *
 * <p>No ticking, world scanning or recognition recalculation is involved.</p>
 */
@EventBusSubscriber(
        modid = MoosTensuraAddon.MODID
)
public final class RecognitionCommittedMetadataEvents {

    private RecognitionCommittedMetadataEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        RecognitionCommittedMetadataService.complete(
                player
        );
    }
}