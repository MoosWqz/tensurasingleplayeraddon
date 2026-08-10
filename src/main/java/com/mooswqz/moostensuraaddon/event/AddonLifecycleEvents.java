package com.mooswqz.moostensuraaddon.event;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.lifecycle.AddonPlayerDataResetService;
import com.mooswqz.moostensuraaddon.lifecycle.GranterAcquisitionTracker;
import com.mooswqz.moostensuraaddon.lifecycle.RecognitionNativeEndowmentService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionProgressScreenService;
import com.mooswqz.moostensuraaddon.util.SubordinateOverviewService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Connects actual player lifecycle events to the bounded incarnation state.
 */
@EventBusSubscriber(modid = MoosTensuraAddon.MODID)
public final class AddonLifecycleEvents {

    private static final ResourceLocation CHARACTER_RESET_SCROLL =
            ResourceLocation.fromNamespaceAndPath(
                    "tensura",
                    "character_reset_scroll"
            );

    private static final ResourceLocation REWIND_TIME_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(
                    "tensura",
                    "rewind_time"
            );

    /*
     * Tensura's reset scroll performs its real action from releaseUsing(...),
     * so LivingEntityUseItemEvent.Finish is never the authoritative success
     * signal. Arm on Stop, then confirm success when Tensura re-awards its
     * rewind_time advancement after resetEverything(...) has completed.
     */
    private static final long CHARACTER_RESET_CONFIRMATION_WINDOW_TICKS = 5L;

    private static final Map<UUID, Long>
            PENDING_CHARACTER_RESETS = new HashMap<>();

    private AddonLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onStoppedUsingItem(
            LivingEntityUseItemEvent.Stop event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                event.getItem().getItem()
        );

        if (!CHARACTER_RESET_SCROLL.equals(itemId)) {
            return;
        }

        PENDING_CHARACTER_RESETS.put(
                player.getUUID(),
                player.serverLevel().getGameTime()
        );
    }

    @SubscribeEvent
    public static void onAdvancementEarned(
            AdvancementEvent.AdvancementEarnEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !REWIND_TIME_ADVANCEMENT.equals(
                event.getAdvancement().id()
        )) {
            return;
        }

        Long armedGameTime = PENDING_CHARACTER_RESETS.remove(
                player.getUUID()
        );

        if (armedGameTime == null) {
            return;
        }

        long currentGameTime = player.serverLevel().getGameTime();
        long age = currentGameTime - armedGameTime;

        if (age < 0L
                || age > CHARACTER_RESET_CONFIRMATION_WINDOW_TICKS) {
            return;
        }

        AddonPlayerDataResetService.resetForNewIncarnation(
                player,
                AddonPlayerDataResetService.ResetReason.CHARACTER_RESET
        );
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Long armedGameTime = PENDING_CHARACTER_RESETS.get(
                player.getUUID()
        );

        if (armedGameTime == null) {
            return;
        }

        long currentGameTime = player.serverLevel().getGameTime();

        if (currentGameTime < armedGameTime
                || currentGameTime - armedGameTime
                > CHARACTER_RESET_CONFIRMATION_WINDOW_TICKS) {
            PENDING_CHARACTER_RESETS.remove(
                    player.getUUID()
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        AddonIncarnationState.load(player);
        GranterAcquisitionTracker.initializeOnLogin(player);
        RecognitionNativeEndowmentService.synchronize(player);
    }

    @SubscribeEvent
    public static void onPlayerClone(
            PlayerEvent.Clone event
    ) {
        AddonIncarnationState.copyPersistentState(
                event.getOriginal(),
                event.getEntity()
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PENDING_CHARACTER_RESETS.remove(player.getUUID());
        RecognitionProgressScreenService.clear(player.getUUID());
        SubordinateOverviewService.forget(player.getUUID());
    }
}