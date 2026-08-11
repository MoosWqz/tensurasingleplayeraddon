package com.mooswqz.moostensuraaddon.lifecycle;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.recognition.RecognitionDisplayNameService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionDisplayNameSyncService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionEndowmentEffortRewardService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionNativeNameStorageService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionProgressScreenService;
import com.mooswqz.moostensuraaddon.recognition.RecognitionStatKeys;
import com.mooswqz.moostensuraaddon.util.AddonAdvancementHelper;
import com.mooswqz.moostensuraaddon.util.TensuraPlayerStateHelper;
import io.github.manasmods.tensura.network.c2s.RequestNamingMenuPacket;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.network.chat.Component;
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
        RecognitionData recognition = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );

        if (recognition.isWriteBlockedByFutureVersion()) {
            return;
        }

        AddonIncarnationState state =
                AddonIncarnationState.load(player);
        boolean committed = recognition.isNamingCommitted();
        boolean revealPending = committed
                && recognition.getFlag(
                RecognitionStatKeys.REVEAL_PENDING
        );
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

        /*
         * Reconcile on every lifecycle synchronization. This repairs a missing
         * capacity modifier without rewriting current energy when the stable
         * modifiers already match.
         */
        RecognitionEndowmentEffortRewardService.reconcile(
                player
        );

        /*
         * Commitment is intentionally earlier than presentation so an
         * interrupted ritual cannot reroll. Native naming must observe the
         * opposite boundary: neither Tensura's stored name nor Minecraft's
         * custom-name field may reveal the frozen title until presentation
         * actually finishes.
         */
        if (revealPending) {
            return;
        }

        String nativeRecognitionName =
                RecognitionDisplayNameService
                        .buildNativeTensuraName(
                                player.getGameProfile()
                                        .getName(),
                                recognition.getString(
                                        RecognitionStatKeys.BESTOWED_TITLE
                                )
                        );

        if (committed && nativeNamed) {
            IExistence existence =
                    TensuraStorages.getExistenceFrom(
                            player
                    );

            String storedNativeName =
                    existence == null
                            || existence.getName() == null
                            ? ""
                            : existence.getName().trim();

            Component customName =
                    player.getCustomName();

            String storedCustomName =
                    customName == null
                            ? ""
                            : customName.getString().trim();

            boolean nativeNameMatches =
                    storedNativeName.equals(
                            nativeRecognitionName
                    );

            boolean customNameMatches =
                    storedCustomName.equals(
                            nativeRecognitionName
                    );

            if (!nativeNameMatches
                    || !customNameMatches) {
                boolean retryBlocked =
                        guardActive
                                || now < state
                                .getNativeEndowmentNextAttemptEpochMillis();

                if (retryBlocked) {
                    return;
                }

                RecognitionNativeNameStorageService.Result
                        nameSync =
                        RecognitionNativeNameStorageService.write(
                                player,
                                existence,
                                nativeRecognitionName
                        );

                if (!nameSync.success()) {
                    state.recordNativeEndowmentFailure(now);
                    return;
                }
            }

            boolean stateChanged =
                    !markerMatches
                            || !nativeNameMatches
                            || !customNameMatches;

            if (!markerMatches) {
                state.markNativeEndowmentApplied(
                        recognitionIncarnation
                );
            }

            if (stateChanged) {
                RecognitionDisplayNameSyncService
                        .refreshAndBroadcast(player);
                RecognitionProgressScreenService.invalidate(player);
                AddonAdvancementHelper.awardNameAnchor(player);
            }

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
                    nativeRecognitionName
            );
        } catch (RuntimeException exception) {
            state.recordNativeEndowmentFailure(now);
            return;
        }

        if (!TensuraPlayerStateHelper.isNamedOrEndowed(player)) {
            state.recordNativeEndowmentFailure(now);
            return;
        }

        IExistence refreshedExistence =
                TensuraStorages.getExistenceFrom(
                        player
                );

        RecognitionNativeNameStorageService.Result
                nameSync =
                RecognitionNativeNameStorageService.write(
                        player,
                        refreshedExistence,
                        nativeRecognitionName
                );

        if (!nameSync.success()) {
            state.recordNativeEndowmentFailure(now);
            return;
        }

        state.markNativeEndowmentApplied(
                recognitionIncarnation
        );
        RecognitionEndowmentEffortRewardService.reconcile(
                player
        );
        RecognitionDisplayNameSyncService
                .refreshAndBroadcast(player);
        RecognitionProgressScreenService.invalidate(player);
        AddonAdvancementHelper.awardNameAnchor(player);
    }
}
