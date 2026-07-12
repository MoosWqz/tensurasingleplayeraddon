package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.UltimateSkillActions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ExecuteUltimateConfirmationPayload(
        boolean massGrant,
        boolean benevolent
) implements CustomPacketPayload {
    public static final Type<ExecuteUltimateConfirmationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoosTensuraAddon.MODID, "execute_ultimate_confirmation")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ExecuteUltimateConfirmationPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ExecuteUltimateConfirmationPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new ExecuteUltimateConfirmationPayload(
                            buffer.readBoolean(),
                            buffer.readBoolean()
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ExecuteUltimateConfirmationPayload payload) {
                    buffer.writeBoolean(payload.massGrant());
                    buffer.writeBoolean(payload.benevolent());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExecuteUltimateConfirmationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            if (payload.massGrant()) {
                UltimateSkillActions.executeConfirmedMassGrant(player, payload.benevolent());
            } else {
                UltimateSkillActions.executeConfirmedRangedTakeBack(player, payload.benevolent());
            }
        });
    }
}