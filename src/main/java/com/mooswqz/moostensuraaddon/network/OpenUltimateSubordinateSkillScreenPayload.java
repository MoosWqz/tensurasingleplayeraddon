package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record OpenUltimateSubordinateSkillScreenPayload(
        boolean seize,
        String targetUuid,
        String targetName,
        double costPerSkill,
        double seizeDeathChancePerSkill,
        double seizeDeathChanceMax,
        List<SkillEntry> skills
) implements CustomPacketPayload {
    public static final Type<OpenUltimateSubordinateSkillScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoosTensuraAddon.MODID, "open_ultimate_subordinate_skill_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenUltimateSubordinateSkillScreenPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenUltimateSubordinateSkillScreenPayload decode(RegistryFriendlyByteBuf buffer) {
                    boolean seize = buffer.readBoolean();
                    String targetUuid = buffer.readUtf();
                    String targetName = buffer.readUtf();
                    double costPerSkill = buffer.readDouble();
                    double seizeDeathChancePerSkill = buffer.readDouble();
                    double seizeDeathChanceMax = buffer.readDouble();

                    int size = buffer.readVarInt();
                    List<SkillEntry> skills = new ArrayList<>();

                    for (int index = 0; index < size; index++) {
                        skills.add(SkillEntry.decode(buffer));
                    }

                    return new OpenUltimateSubordinateSkillScreenPayload(
                            seize,
                            targetUuid,
                            targetName,
                            costPerSkill,
                            seizeDeathChancePerSkill,
                            seizeDeathChanceMax,
                            skills
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, OpenUltimateSubordinateSkillScreenPayload payload) {
                    buffer.writeBoolean(payload.seize());
                    buffer.writeUtf(payload.targetUuid());
                    buffer.writeUtf(payload.targetName());
                    buffer.writeDouble(payload.costPerSkill());
                    buffer.writeDouble(payload.seizeDeathChancePerSkill());
                    buffer.writeDouble(payload.seizeDeathChanceMax());

                    List<SkillEntry> skills = payload.skills() == null ? List.of() : payload.skills();

                    buffer.writeVarInt(skills.size());

                    for (SkillEntry skill : skills) {
                        skill.encode(buffer);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record SkillEntry(
            String skillId,
            String displayName,
            double borrowPermanentChance
    ) {
        private static SkillEntry decode(RegistryFriendlyByteBuf buffer) {
            return new SkillEntry(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readDouble()
            );
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(skillId == null ? "" : skillId);
            buffer.writeUtf(displayName == null ? "Unknown Skill" : displayName);
            buffer.writeDouble(borrowPermanentChance);
        }
    }
}