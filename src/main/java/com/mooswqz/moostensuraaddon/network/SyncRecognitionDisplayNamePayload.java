package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.recognition
        .RecognitionDisplayNameService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom
        .CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record SyncRecognitionDisplayNamePayload(
        UUID playerUuid,
        boolean active,
        String title,
        int rgbColor,
        boolean bold
) implements CustomPacketPayload {

    private static final int CLEARED_COLOR =
            0xFFFFFF;

    public static final Type<SyncRecognitionDisplayNamePayload>
            TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "sync_recognition_display_name"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SyncRecognitionDisplayNamePayload
            > STREAM_CODEC =
            new StreamCodec<>() {

                @Override
                public SyncRecognitionDisplayNamePayload decode(
                        RegistryFriendlyByteBuf buffer
                ) {
                    UUID playerUuid =
                            buffer.readUUID();

                    boolean active =
                            buffer.readBoolean();

                    String title =
                            buffer.readUtf(
                                    RecognitionDisplayNameService
                                            .MAX_TITLE_LENGTH
                            );

                    int rgbColor =
                            buffer.readInt();

                    boolean bold =
                            buffer.readBoolean();

                    return new SyncRecognitionDisplayNamePayload(
                            playerUuid,
                            active,
                            title,
                            rgbColor,
                            bold
                    );
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        SyncRecognitionDisplayNamePayload payload
                ) {
                    buffer.writeUUID(
                            payload.playerUuid()
                    );

                    buffer.writeBoolean(
                            payload.active()
                    );

                    buffer.writeUtf(
                            RecognitionDisplayNameService
                                    .sanitizeTitle(
                                            payload.title()
                                    )
                    );

                    buffer.writeInt(
                            payload.rgbColor()
                                    & 0xFFFFFF
                    );

                    buffer.writeBoolean(
                            payload.bold()
                    );
                }
            };

    public SyncRecognitionDisplayNamePayload {
        playerUuid =
                Objects.requireNonNull(
                        playerUuid,
                        "A player UUID is required."
                );

        title =
                active
                        ? RecognitionDisplayNameService
                        .sanitizeTitle(
                                title
                        )
                        : "";

        if (title.isBlank()) {
            active = false;
        }

        if (active) {
            rgbColor =
                    rgbColor
                            & 0xFFFFFF;
        } else {
            title = "";
            rgbColor = CLEARED_COLOR;
            bold = false;
        }
    }

    public static SyncRecognitionDisplayNamePayload clear(
            UUID playerUuid
    ) {
        return new SyncRecognitionDisplayNamePayload(
                playerUuid,
                false,
                "",
                CLEARED_COLOR,
                false
        );
    }

    @Override
    public Type<SyncRecognitionDisplayNamePayload> type() {
        return TYPE;
    }
}