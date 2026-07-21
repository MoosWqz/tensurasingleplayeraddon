package com.mooswqz.moostensuraaddon.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

public final class CureAttributionData {

    public static final Codec<CureAttributionData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING
                            .optionalFieldOf("starter_uuid", "")
                            .forGetter(
                                    CureAttributionData::getStarterUuidString
                            ),
                    Codec.LONG
                            .optionalFieldOf(
                                    "started_at_game_time",
                                    0L
                            )
                            .forGetter(
                                    CureAttributionData::getStartedAtGameTime
                            )
            ).apply(instance, CureAttributionData::new));

    private String starterUuid;
    private long startedAtGameTime;

    public CureAttributionData() {
        this("", 0L);
    }

    public CureAttributionData(
            String starterUuid,
            long startedAtGameTime
    ) {
        this.starterUuid =
                starterUuid == null
                        ? ""
                        : starterUuid;

        this.startedAtGameTime =
                Math.max(0L, startedAtGameTime);
    }

    public void arm(
            UUID playerUuid,
            long gameTime
    ) {
        if (playerUuid == null) {
            return;
        }

        starterUuid = playerUuid.toString();
        startedAtGameTime = Math.max(0L, gameTime);
    }

    public void clear() {
        starterUuid = "";
        startedAtGameTime = 0L;
    }

    public boolean isArmed() {
        return getStarterUuid().isPresent();
    }

    public Optional<UUID> getStarterUuid() {
        if (starterUuid == null || starterUuid.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    UUID.fromString(starterUuid)
            );
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public String getStarterUuidString() {
        return starterUuid == null
                ? ""
                : starterUuid;
    }

    public long getStartedAtGameTime() {
        return Math.max(0L, startedAtGameTime);
    }
}