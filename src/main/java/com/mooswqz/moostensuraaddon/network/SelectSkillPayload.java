package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.GranterActions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectSkillPayload(String skillId) implements CustomPacketPayload {
    public static final Type<SelectSkillPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoosTensuraAddon.MODID, "select_skill")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectSkillPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    SelectSkillPayload::skillId,
                    SelectSkillPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectSkillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ResourceLocation skillId = ResourceLocation.tryParse(payload.skillId());

            if (skillId == null) {
                return;
            }

            GranterActions.setSelectedSkill(player, skillId);
        });
    }
}