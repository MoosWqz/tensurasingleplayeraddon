package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client readout of the server-authoritative reward snapshot.
 */
public record SyncRecognitionBenefitsPayload(
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
) implements CustomPacketPayload {

    private static final int MAX_STATE_ID_LENGTH = 32;

    public static final Type<SyncRecognitionBenefitsPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "sync_recognition_benefits"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SyncRecognitionBenefitsPayload
            > STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncRecognitionBenefitsPayload decode(
                RegistryFriendlyByteBuf buffer
        ) {
            return new SyncRecognitionBenefitsPayload(
                    buffer.readUtf(MAX_STATE_ID_LENGTH),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer,
                SyncRecognitionBenefitsPayload payload
        ) {
            buffer.writeUtf(
                    payload.stateId(),
                    MAX_STATE_ID_LENGTH
            );
            buffer.writeBoolean(
                    payload.nativeEndowmentAnchored()
            );
            buffer.writeBoolean(
                    payload.attributeStateMatches()
            );
            buffer.writeVarInt(
                    payload.rewardProfileVersion()
            );
            buffer.writeDouble(payload.frozenIdentityStrength());
            buffer.writeDouble(payload.identityStrengthMaximum());
            buffer.writeDouble(payload.totalStrength());
            buffer.writeDouble(payload.maxHealthMultiplier());
            buffer.writeDouble(payload.attackDamageMultiplier());
            buffer.writeDouble(payload.movementSpeedMultiplier());
            buffer.writeDouble(payload.attackSpeedMultiplier());
            buffer.writeDouble(payload.knockbackResistanceAddition());
        }
    };

    public SyncRecognitionBenefitsPayload {
        stateId = sanitizeStateId(stateId);
        rewardProfileVersion = Math.max(0, rewardProfileVersion);
        frozenIdentityStrength = sanitize(frozenIdentityStrength);
        identityStrengthMaximum = sanitize(identityStrengthMaximum);
        totalStrength = sanitize(totalStrength);
        maxHealthMultiplier = sanitize(maxHealthMultiplier);
        attackDamageMultiplier = sanitize(attackDamageMultiplier);
        movementSpeedMultiplier = sanitize(movementSpeedMultiplier);
        attackSpeedMultiplier = sanitize(attackSpeedMultiplier);
        knockbackResistanceAddition = sanitize(
                knockbackResistanceAddition
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String sanitizeStateId(String value) {
        String safe = value == null
                ? "synchronizing"
                : value.trim().toLowerCase(java.util.Locale.ROOT);

        if (safe.isBlank()) {
            return "synchronizing";
        }

        return safe.length() <= MAX_STATE_ID_LENGTH
                ? safe
                : safe.substring(0, MAX_STATE_ID_LENGTH);
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? value
                : 0.0D;
    }
}