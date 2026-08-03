package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoveSubordinateOverviewEntryPayload(
        String targetUuid
) implements CustomPacketPayload {

    private static final int MAX_UUID_LENGTH = 64;

    public static final Type<RemoveSubordinateOverviewEntryPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "remove_subordinate_overview_entry"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RemoveSubordinateOverviewEntryPayload
            > STREAM_CODEC = new StreamCodec<>() {

        @Override
        public RemoveSubordinateOverviewEntryPayload decode(
                RegistryFriendlyByteBuf buffer
        ) {
            return new RemoveSubordinateOverviewEntryPayload(
                    buffer.readUtf(MAX_UUID_LENGTH)
            );
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer,
                RemoveSubordinateOverviewEntryPayload payload
        ) {
            buffer.writeUtf(
                    payload.targetUuid(),
                    MAX_UUID_LENGTH
            );
        }
    };

    public RemoveSubordinateOverviewEntryPayload {
        targetUuid = targetUuid == null
                ? ""
                : targetUuid.trim();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}