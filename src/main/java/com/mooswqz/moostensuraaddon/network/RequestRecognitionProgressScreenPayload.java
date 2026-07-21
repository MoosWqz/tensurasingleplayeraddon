package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.recognition
        .RecognitionProgressScreenService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom
        .CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling
        .IPayloadContext;

/**
 * Empty client-to-server request used by the Refresh button.
 *
 * The client cannot request a forced recalculation or provide recognition
 * values. The server applies its own cache and rate limits before deciding
 * whether to send a fresh or cached authoritative snapshot.
 */
public record RequestRecognitionProgressScreenPayload()
        implements CustomPacketPayload {

    public static final RequestRecognitionProgressScreenPayload
            INSTANCE =
            new RequestRecognitionProgressScreenPayload();

    public static final Type<
            RequestRecognitionProgressScreenPayload
            > TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "request_recognition_progress_screen"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RequestRecognitionProgressScreenPayload
            > STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestRecognitionProgressScreenPayload decode(
                        RegistryFriendlyByteBuf buffer
                ) {
                    return INSTANCE;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        RequestRecognitionProgressScreenPayload payload
                ) {
                    /*
                     * The request intentionally contains no client-controlled
                     * data.
                     */
                }
            };

    public static void handle(
            RequestRecognitionProgressScreenPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player()
                instanceof ServerPlayer player)) {
            return;
        }

        RecognitionProgressScreenService.requestRefresh(
                player
        );
    }

    @Override
    public Type<RequestRecognitionProgressScreenPayload> type() {
        return TYPE;
    }
}