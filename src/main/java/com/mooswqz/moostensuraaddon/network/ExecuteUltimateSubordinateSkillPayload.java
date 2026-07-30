package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.UltimateBorrowSeizePolicy;
import com.mooswqz.moostensuraaddon.util.UltimateSkillActions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

    private static final int RETAINED_SELECTION_LIMIT =
            UltimateBorrowSeizePolicy.MAX_SELECTED_SKILLS + 1;

    public static final CustomPacketPayload.Type<ExecuteUltimateSubordinateSkillPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "execute_ultimate_subordinate_skill"
                    )
            );

    public static final StreamCodec<
            ByteBuf,
            ExecuteUltimateSubordinateSkillPayload
            > STREAM_CODEC = new StreamCodec<>() {

        @Override
        public ExecuteUltimateSubordinateSkillPayload decode(
                ByteBuf buffer
        ) {
            boolean seize = ByteBufCodecs.BOOL.decode(buffer);
            String targetUuid = ByteBufCodecs.STRING_UTF8.decode(
                    buffer
            );
            int encodedCount = Math.max(
                    0,
                    ByteBufCodecs.VAR_INT.decode(buffer)
            );
            int retainedCount = Math.min(
                    encodedCount,
                    RETAINED_SELECTION_LIMIT
            );
            List<String> skillIds = new ArrayList<>(
                    retainedCount
            );

            for (int index = 0;
                 index < encodedCount;
                 index++) {
                String skillId = ByteBufCodecs.STRING_UTF8.decode(
                        buffer
                );

                if (index < retainedCount) {
                    skillIds.add(skillId);
                }
            }

            return new ExecuteUltimateSubordinateSkillPayload(
                    seize,
                    targetUuid,
                    skillIds
            );
        }

        @Override
        public void encode(
                ByteBuf buffer,
                ExecuteUltimateSubordinateSkillPayload payload
        ) {
            ByteBufCodecs.BOOL.encode(
                    buffer,
                    payload.seize()
            );
            ByteBufCodecs.STRING_UTF8.encode(
                    buffer,
                    payload.targetUuid()
            );
            ByteBufCodecs.VAR_INT.encode(
                    buffer,
                    payload.skillIds().size()
            );

            for (String skillId : payload.skillIds()) {
                ByteBufCodecs.STRING_UTF8.encode(
                        buffer,
                        skillId
                );
            }
        }
    };

    public ExecuteUltimateSubordinateSkillPayload {
        targetUuid = targetUuid == null
                ? ""
                : targetUuid.trim();
        skillIds = skillIds == null
                ? List.of()
                : List.copyOf(
                skillIds.stream()
                        .filter(id -> id != null)
                        .map(String::trim)
                        .limit(RETAINED_SELECTION_LIMIT)
                        .toList()
        );
    }

    public static void handle(
            ExecuteUltimateSubordinateSkillPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player()
                    instanceof ServerPlayer player)) {
                return;
            }

            UUID targetUuid;

            try {
                targetUuid = UUID.fromString(
                        payload.targetUuid()
                );
            } catch (IllegalArgumentException exception) {
                return;
            }

            UltimateBorrowSeizePolicy.RequestAnalysis analysis =
                    UltimateBorrowSeizePolicy.analyseRequest(
                            payload.skillIds()
                    );

            if (analysis.overLimit()) {
                player.sendSystemMessage(
                        Component.literal(
                                        "Too many skills were submitted at once."
                                )
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }

            if (analysis.rejectedCount() > 0) {
                player.sendSystemMessage(
                        Component.literal(
                                        "The request contains an invalid or duplicate skill."
                                )
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }

            List<ResourceLocation> skillIds = new ArrayList<>();

            for (String rawSkillId : analysis.uniqueSkillIds()) {
                ResourceLocation skillId = ResourceLocation.tryParse(
                        rawSkillId
                );

                if (skillId == null) {
                    player.sendSystemMessage(
                            Component.literal(
                                            "One selected skill has an invalid registry ID."
                                    )
                                    .withStyle(ChatFormatting.RED)
                    );
                    return;
                }

                skillIds.add(skillId);
            }

            UltimateSkillActions.executeBorrowOrSeizeSelection(
                    player,
                    payload.seize(),
                    targetUuid,
                    skillIds
            );
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}