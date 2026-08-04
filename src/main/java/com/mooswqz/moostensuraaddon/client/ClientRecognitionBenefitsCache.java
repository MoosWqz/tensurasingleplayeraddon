package com.mooswqz.moostensuraaddon.client;

import com.mooswqz.moostensuraaddon.network.SyncRecognitionBenefitsPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Holds the most recently synchronized benefit readout for the local player.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientRecognitionBenefitsCache {

    private static volatile Snapshot snapshot = Snapshot.empty();

    private ClientRecognitionBenefitsCache() {
    }

    public static void apply(
            SyncRecognitionBenefitsPayload payload
    ) {
        snapshot = payload == null
                ? Snapshot.empty()
                : new Snapshot(
                payload.stateId(),
                payload.nativeEndowmentAnchored(),
                payload.attributeStateMatches(),
                payload.rewardProfileVersion(),
                payload.frozenIdentityStrength(),
                payload.identityStrengthMaximum(),
                payload.totalStrength(),
                payload.maxHealthMultiplier(),
                payload.attackDamageMultiplier(),
                payload.movementSpeedMultiplier(),
                payload.attackSpeedMultiplier(),
                payload.knockbackResistanceAddition()
        );
    }

    public static Snapshot current() {
        return snapshot;
    }

    public record Snapshot(
            String stateId,
            boolean nativeEndowmentAnchored,
            boolean attributeStateMatches,
            int rewardProfileVersion,
            double frozenIdentityStrength,
            double identityStrengthMaximum,
            double totalStrength,
            double maxHealthMultiplier,
            double attackDamageMultiplier,
            double movementSpeedMultiplier,
            double attackSpeedMultiplier,
            double knockbackResistanceAddition
    ) {
        public Snapshot {
            stateId = stateId == null || stateId.isBlank()
                    ? "synchronizing"
                    : stateId.trim();
        }

        public static Snapshot empty() {
            return new Snapshot(
                    "synchronizing",
                    false,
                    true,
                    0,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }

        public boolean active() {
            return "active".equals(stateId);
        }
    }
}