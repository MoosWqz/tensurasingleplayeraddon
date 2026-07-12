package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenGranterScreenPayload() implements CustomPacketPayload {
    public static final OpenGranterScreenPayload INSTANCE = new OpenGranterScreenPayload();

    public static final Type<OpenGranterScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoosTensuraAddon.MODID, "open_granter_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGranterScreenPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}