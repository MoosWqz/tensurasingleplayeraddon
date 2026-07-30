package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.AuthorityActionMode;
import com.mooswqz.moostensuraaddon.util.AuthorityActionPolicy;
import com.mooswqz.moostensuraaddon.util.AuthorityActionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ExecuteUltimateMultiGrantPayload(
        String actionId,
        String targetUuid,
        boolean allEligible,
        List<String> skillIds
) implements CustomPacketPayload {

    public static final int MAX_SELECTED_SKILLS =
            AuthorityActionPolicy.MAX_SELECTED_SKILLS;
    private static final int MAX_ID_LENGTH = 256;

    public static final CustomPacketPayload.Type<ExecuteUltimateMultiGrantPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "execute_ultimate_multi_grant"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ExecuteUltimateMultiGrantPayload
            > STREAM_CODEC = new StreamCodec<>() {

        @Override
        public ExecuteUltimateMultiGrantPayload decode(
                RegistryFriendlyByteBuf buffer
        ) {
            String actionId = buffer.readUtf(64);
            String targetUuid = buffer.readUtf(MAX_ID_LENGTH);
            boolean allEligible = buffer.readBoolean();
            int encodedCount = Math.max(0, buffer.readVarInt());
            int retainedCount = Math.min(
                    encodedCount,
                    MAX_SELECTED_SKILLS + 1
            );
            List<String> skillIds = new ArrayList<>(retainedCount);

            for (int index = 0; index < encodedCount; index++) {
                String skillId = buffer.readUtf(MAX_ID_LENGTH);

                if (index < retainedCount) {
                    skillIds.add(skillId);
                }
            }

            return new ExecuteUltimateMultiGrantPayload(
                    actionId,
                    targetUuid,
                    allEligible,
                    skillIds
            );
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer,
                ExecuteUltimateMultiGrantPayload payload
        ) {
            buffer.writeUtf(payload.actionId(), 64);
            buffer.writeUtf(payload.targetUuid(), MAX_ID_LENGTH);
            buffer.writeBoolean(payload.allEligible());
            buffer.writeVarInt(payload.skillIds().size());

            for (String skillId : payload.skillIds()) {
                buffer.writeUtf(skillId, MAX_ID_LENGTH);
            }
        }
    };

    public ExecuteUltimateMultiGrantPayload {
        actionId = actionId == null ? "" : actionId.trim();
        targetUuid = targetUuid == null ? "" : targetUuid.trim();
        skillIds = skillIds == null
                ? List.of()
                : List.copyOf(
                skillIds.stream()
                        .filter(skillId -> skillId != null
                                           && !skillId.isBlank())
                        .map(String::trim)
                        .limit(MAX_SELECTED_SKILLS + 1L)
                        .toList()
        );
    }

    public ExecuteUltimateMultiGrantPayload(
            boolean benevolent,
            String targetUuid,
            List<String> skillIds
    ) {
        this(
                benevolent
                        ? AuthorityActionMode.BENEVOLENT_BESTOW.id()
                        : AuthorityActionMode.GOVERNANCE_INVEST.id(),
                targetUuid,
                false,
                skillIds
        );
    }

    public static void handle(
            ExecuteUltimateMultiGrantPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AuthorityActionService.execute(
                        player,
                        payload.actionId(),
                        payload.targetUuid(),
                        payload.allEligible(),
                        payload.skillIds()
                );
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}