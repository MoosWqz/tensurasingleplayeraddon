package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenUltimateConfirmationScreenPayload(
        boolean massGrant,
        boolean benevolent,
        String selectedSkillId,
        String selectedSkillName,
        int affectedTargets,
        double totalCost
) implements CustomPacketPayload {
    public static final Type<OpenUltimateConfirmationScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoosTensuraAddon.MODID, "open_ultimate_confirmation_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenUltimateConfirmationScreenPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenUltimateConfirmationScreenPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new OpenUltimateConfirmationScreenPayload(
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readVarInt(),
                            buffer.readDouble()
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, OpenUltimateConfirmationScreenPayload payload) {
                    buffer.writeBoolean(payload.massGrant());
                    buffer.writeBoolean(payload.benevolent());
                    buffer.writeUtf(payload.selectedSkillId() == null ? "" : payload.selectedSkillId());
                    buffer.writeUtf(payload.selectedSkillName() == null ? "Unknown Skill" : payload.selectedSkillName());
                    buffer.writeVarInt(Math.max(0, payload.affectedTargets()));
                    buffer.writeDouble(Math.max(0.0D, payload.totalCost()));
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}