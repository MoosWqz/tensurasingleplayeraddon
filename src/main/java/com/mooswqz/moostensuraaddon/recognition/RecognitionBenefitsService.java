package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.network.SyncRecognitionBenefitsPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Synchronizes the existing frozen reward without recalculating or changing it.
 */
public final class RecognitionBenefitsService {

    private RecognitionBenefitsService() {
    }

    public static void send(
            ServerPlayer player
    ) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        RecognitionData recognition = player.getData(
                AttachmentRegistry.RECOGNITION_DATA
        );
        AddonIncarnationState.Snapshot lifecycle =
                AddonIncarnationState.inspect(player);
        RecognitionStrengthRewardSnapshot snapshot =
                RecognitionStrengthRewardService.inspect(player);
        RecognitionStrengthRewardFormula.Reward reward =
                snapshot.expectedReward();

        String stateId = resolveStateId(snapshot);
        String recognitionIncarnation = recognition.getIncarnationId()
                .isBlank()
                ? lifecycle.lifeToken()
                : recognition.getIncarnationId();
        boolean nativeAnchored = !recognitionIncarnation.isBlank()
                && recognitionIncarnation.equals(
                lifecycle.nativeEndowmentIncarnation()
        );

        PacketDistributor.sendToPlayer(
                player,
                new SyncRecognitionBenefitsPayload(
                        stateId,
                        nativeAnchored,
                        snapshot.attributeStateMatches(),
                        snapshot.storedRewardProfileVersion(),
                        snapshot.frozenIdentityStrength(),
                        snapshot.identityStrengthMaximum(),
                        snapshot.totalStrength(),
                        reward == null
                                ? 0.0D
                                : reward.maxHealthMultiplier(),
                        reward == null
                                ? 0.0D
                                : reward.attackDamageMultiplier(),
                        reward == null
                                ? 0.0D
                                : reward.movementSpeedMultiplier(),
                        reward == null
                                ? 0.0D
                                : reward.attackSpeedMultiplier(),
                        reward == null
                                ? 0.0D
                                : reward.knockbackResistanceAddition()
                )
        );
    }

    private static String resolveStateId(
            RecognitionStrengthRewardSnapshot snapshot
    ) {
        if (snapshot == null
                || !snapshot.recognitionCommitted()) {
            return "not_recognized";
        }

        if (!snapshot.committedResultValid()) {
            return "invalid";
        }

        if (snapshot.futureProfilePreserved()) {
            return "future_profile";
        }

        if (!snapshot.rewardMetadataInitialized()) {
            return "synchronizing";
        }

        return "active";
    }
}