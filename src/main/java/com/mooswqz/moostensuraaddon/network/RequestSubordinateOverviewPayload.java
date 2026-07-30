package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.SubordinateOverviewService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestSubordinateOverviewPayload(
        boolean benevolent
) implements CustomPacketPayload {

    public static final Type<RequestSubordinateOverviewPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "request_subordinate_overview"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RequestSubordinateOverviewPayload
            > STREAM_CODEC = new StreamCodec<>() {

        @Override
        public RequestSubordinateOverviewPayload decode(
                RegistryFriendlyByteBuf buffer
        ) {
            return new RequestSubordinateOverviewPayload(
                    buffer.readBoolean()
            );
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer,
                RequestSubordinateOverviewPayload payload
        ) {
            buffer.writeBoolean(payload.benevolent());
        }
    };

    public static void handle(
            RequestSubordinateOverviewPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SubordinateOverviewService.refresh(
                        player,
                        payload.benevolent()
                );
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}