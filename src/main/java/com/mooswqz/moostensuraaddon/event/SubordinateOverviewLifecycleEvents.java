package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.network.RemoveSubordinateOverviewEntryPayload;
import com.mooswqz.moostensuraaddon.recognition.RecognitionSubordinateSupport;
import com.mooswqz.moostensuraaddon.util.SubordinateOverviewService;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class SubordinateOverviewLifecycleEvents {

    private SubordinateOverviewLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(
            LivingDeathEvent event
    ) {
        LivingEntity victim = event.getEntity();

        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Set<UUID> notifiedPlayers = new HashSet<>();
        ServerPlayer recognizedOwner =
                RecognitionSubordinateSupport.findOnlineOwner(
                        victim
                );

        if (recognizedOwner != null) {
            sendRemoval(recognizedOwner, victim);
            notifiedPlayers.add(recognizedOwner.getUUID());
        }

        for (ServerPlayer player :
                serverLevel.getServer().getPlayerList().getPlayers()) {
            if (notifiedPlayers.contains(player.getUUID())
                    || !SubordinateHelper.isSubordinate(player, victim)) {
                continue;
            }

            sendRemoval(player, victim);
        }
    }

    private static void sendRemoval(
            ServerPlayer player,
            LivingEntity victim
    ) {
        PacketDistributor.sendToPlayer(
                player,
                new RemoveSubordinateOverviewEntryPayload(
                        victim.getUUID().toString()
                )
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SubordinateOverviewService.forget(player.getUUID());
        }
    }
}