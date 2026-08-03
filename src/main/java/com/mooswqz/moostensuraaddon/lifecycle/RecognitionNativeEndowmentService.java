package com.mooswqz.moostensuraaddon.lifecycle;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.recognition.RecognitionDisplayNameSyncService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionProgressScreenService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStatKeys;
import com.mooswqz.moostensuraaddon.util.AddonAdvancementHelper;
import com.mooswqz.moostensuraaddon.util.TensuraPlayerStateHelper;
import io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridges a committed recognition result into Tensura's native high-grade
 * naming/endowment route exactly once per recognition incarnation.
 */
public final class RecognitionNativeEndowmentService {

    private RecognitionNativeEndowmentService() {
    }

    public static void synchronize(
            ServerPlayer player
    ) {
        if (player == null
                || player.level().isClientSide()) {
            return;
        }

        long now = System.currentTimeMillis();
        AddonIncarnationState state =
                AddonIncarnationState.load(player);
        RecognitionData recognition = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );
        boolean committed = recognition.isNamingCommitted();
        String recognitionIncarnation = recognition.getString(
                RecognitionStatKeys.INCARNATION_ID
        );

        if (recognitionIncarnation.isBlank()) {
            recognitionIncarnation = state.getLifeToken();
        }

        boolean nativeNamed =
                TensuraPlayerStateHelper.isNamedOrEndowed(player);
        boolean markerMatches = !recognitionIncarnation.isBlank()
                && recognitionIncarnation.equals(
                state.getNativeEndowmentIncarnation()
        );
        boolean guardActive = state.isResetGuardActive(now);

        if (committed && nativeNamed && !markerMatches) {
            state.markNativeEndowmentApplied(
                    recognitionIncarnation
            );
            RecognitionDisplayNameSyncService
                    .refreshAndBroadcast(player);
            RecognitionProgressScreenService.invalidate(player);
            AddonAdvancementHelper.awardNameAnchor(player);
            return;
        }

        if (!AddonLifecyclePolicy.shouldAttemptNativeEndowment(
                committed,
                nativeNamed,
                markerMatches,
                guardActive,
                state.getNativeEndowmentNextAttemptEpochMillis(),
                now
        )) {
            return;
        }

        try {
            RequestNamingMenuPacket.name(
                    player,
                    null,
                    RequestNamingMenuPacket.NamingType.HIGH,
                    player.getGameProfile().getName()
            );
        } catch (RuntimeException exception) {
            state.recordNativeEndowmentFailure(now);
            return;
        }

        if (!TensuraPlayerStateHelper.isNamedOrEndowed(player)) {
            state.recordNativeEndowmentFailure(now);
            return;
        }

        state.markNativeEndowmentApplied(
                recognitionIncarnation
        );
        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(player);
        RecognitionProgressScreenService.invalidate(player);
        AddonAdvancementHelper.awardNameAnchor(player);
    }
}