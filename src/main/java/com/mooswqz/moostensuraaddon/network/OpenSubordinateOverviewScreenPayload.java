package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record OpenSubordinateOverviewScreenPayload(
        List<TargetEntry> targets
) implements CustomPacketPayload {
    public static final Type<OpenSubordinateOverviewScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoosTensuraAddon.MODID, "open_subordinate_overview_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSubordinateOverviewScreenPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenSubordinateOverviewScreenPayload decode(RegistryFriendlyByteBuf buffer) {
                    int targetCount = buffer.readVarInt();
                    List<TargetEntry> targets = new ArrayList<>();

                    for (int index = 0; index < targetCount; index++) {
                        targets.add(TargetEntry.decode(buffer));
                    }

                    return new OpenSubordinateOverviewScreenPayload(targets);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, OpenSubordinateOverviewScreenPayload payload) {
                    List<TargetEntry> targets = payload.targets() == null ? List.of() : payload.targets();

                    buffer.writeVarInt(targets.size());

                    for (TargetEntry target : targets) {
                        target.encode(buffer);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record TargetEntry(
            String targetUuid,
            String targetName,
            float health,
            float maxHealth,
            double magicules,
            double ep,
            List<SkillEntry> skills
    ) {
        private static TargetEntry decode(RegistryFriendlyByteBuf buffer) {
            String targetUuid = buffer.readUtf();
            String targetName = buffer.readUtf();
            float health = buffer.readFloat();
            float maxHealth = buffer.readFloat();
            double magicules = buffer.readDouble();
            double ep = buffer.readDouble();

            int skillCount = buffer.readVarInt();
            List<SkillEntry> skills = new ArrayList<>();

            for (int index = 0; index < skillCount; index++) {
                skills.add(SkillEntry.decode(buffer));
            }

            return new TargetEntry(
                    targetUuid,
                    targetName,
                    health,
                    maxHealth,
                    magicules,
                    ep,
                    skills
            );
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(targetUuid == null ? "" : targetUuid);
            buffer.writeUtf(targetName == null ? "Unknown Subordinate" : targetName);
            buffer.writeFloat(health);
            buffer.writeFloat(maxHealth);
            buffer.writeDouble(magicules);
            buffer.writeDouble(ep);

            List<SkillEntry> safeSkills = skills == null ? List.of() : skills;

            buffer.writeVarInt(safeSkills.size());

            for (SkillEntry skill : safeSkills) {
                skill.encode(buffer);
            }
        }
    }

    public record SkillEntry(
            String skillId,
            String displayName,
            String categoryName,
            int categoryOrder,
            boolean mastered,
            boolean grantedByViewer
    ) {
        private static SkillEntry decode(RegistryFriendlyByteBuf buffer) {
            return new SkillEntry(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            );
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(skillId == null ? "" : skillId);
            buffer.writeUtf(displayName == null ? "Unknown Skill" : displayName);
            buffer.writeUtf(categoryName == null ? "Other" : categoryName);
            buffer.writeVarInt(categoryOrder);
            buffer.writeBoolean(mastered);
            buffer.writeBoolean(grantedByViewer);
        }
    }
}