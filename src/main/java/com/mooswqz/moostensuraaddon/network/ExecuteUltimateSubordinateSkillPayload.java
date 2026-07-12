package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.UltimateSkillActions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ExecuteUltimateSubordinateSkillPayload(
        boolean seize,
        String targetUuid,
        List<String> skillIds
) implements CustomPacketPayload {
    public static final Type<ExecuteUltimateSubordinateSkillPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoosTensuraAddon.MODID, "execute_ultimate_subordinate_skill")
    );

    public static final StreamCodec<ByteBuf, ExecuteUltimateSubordinateSkillPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ExecuteUltimateSubordinateSkillPayload decode(ByteBuf buffer) {
                    boolean seize = ByteBufCodecs.BOOL.decode(buffer);
                    String targetUuid = ByteBufCodecs.STRING_UTF8.decode(buffer);

                    int size = ByteBufCodecs.VAR_INT.decode(buffer);
                    List<String> skillIds = new ArrayList<>();

                    for (int i = 0; i < size; i++) {
                        skillIds.add(ByteBufCodecs.STRING_UTF8.decode(buffer));
                    }

                    return new ExecuteUltimateSubordinateSkillPayload(seize, targetUuid, skillIds);
                }

                @Override
                public void encode(ByteBuf buffer, ExecuteUltimateSubordinateSkillPayload payload) {
                    ByteBufCodecs.BOOL.encode(buffer, payload.seize());
                    ByteBufCodecs.STRING_UTF8.encode(buffer, payload.targetUuid());

                    ByteBufCodecs.VAR_INT.encode(buffer, payload.skillIds().size());

                    for (String skillId : payload.skillIds()) {
                        ByteBufCodecs.STRING_UTF8.encode(buffer, skillId);
                    }
                }
            };

    public static void handle(ExecuteUltimateSubordinateSkillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            UUID targetUuid;

            try {
                targetUuid = UUID.fromString(payload.targetUuid());
            } catch (IllegalArgumentException exception) {
                return;
            }

            List<ResourceLocation> skillIds = payload.skillIds()
                    .stream()
                    .map(ResourceLocation::tryParse)
                    .filter(id -> id != null)
                    .toList();

            UltimateSkillActions.executeBorrowOrSeizeSelection(
                    player,
                    payload.seize(),
                    targetUuid,
                    skillIds
            );
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}