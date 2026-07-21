package com.mooswqz.moostensuraaddon.client;

import com.mooswqz.moostensuraaddon.network
        .SyncRecognitionDisplayNamePayload;
import com.mooswqz.moostensuraaddon.recognition
        .RecognitionDisplayNameService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class ClientRecognitionDisplayNameCache {

    private static final Map<
            UUID,
            CachedRecognitionName
            > VISIBLE_NAMES =
            new ConcurrentHashMap<>();

    private ClientRecognitionDisplayNameCache() {
    }

    public static void apply(
            SyncRecognitionDisplayNamePayload payload
    ) {
        if (payload == null) {
            return;
        }

        UUID playerUuid =
                payload.playerUuid();

        String title =
                RecognitionDisplayNameService
                        .sanitizeTitle(
                                payload.title()
                        );

        if (!payload.active()
                || title.isBlank()) {

            VISIBLE_NAMES.remove(
                    playerUuid
            );

            return;
        }

        VISIBLE_NAMES.put(
                playerUuid,
                new CachedRecognitionName(
                        title,
                        payload.rgbColor(),
                        payload.bold()
                )
        );
    }

    public static Optional<CachedRecognitionName> get(
            UUID playerUuid
    ) {
        if (playerUuid == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                VISIBLE_NAMES.get(
                        playerUuid
                )
        );
    }

    /**
     * Retained for code that only needs the title text.
     */
    public static Optional<String> getTitle(
            UUID playerUuid
    ) {
        return get(playerUuid)
                .map(
                        CachedRecognitionName::title
                );
    }

    public static void clear() {
        VISIBLE_NAMES.clear();
    }

    public record CachedRecognitionName(
            String title,
            int rgbColor,
            boolean bold
    ) {

        public CachedRecognitionName {
            title =
                    RecognitionDisplayNameService
                            .sanitizeTitle(
                                    title
                            );

            rgbColor =
                    rgbColor
                            & 0xFFFFFF;
        }
    }
}