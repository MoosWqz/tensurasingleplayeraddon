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
        double availableMagicules,
        double costPerSkill,
        double seizeDeathChancePerSkill,
        double seizeDeathChanceMax,
        List<SkillEntry> skills
) implements CustomPacketPayload {

    public static final int MAX_SKILLS = 256;
    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_CATEGORY_LENGTH = 48;

    public static final CustomPacketPayload.Type<OpenUltimateSubordinateSkillScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "open_ultimate_subordinate_skill_screen"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenUltimateSubordinateSkillScreenPayload
            > STREAM_CODEC = new StreamCodec<>() {

        @Override
        public OpenUltimateSubordinateSkillScreenPayload decode(
                RegistryFriendlyByteBuf buffer
        ) {
            boolean seize = buffer.readBoolean();
            String targetUuid = buffer.readUtf(MAX_ID_LENGTH);
            String targetName = buffer.readUtf(MAX_NAME_LENGTH);
            double availableMagicules = buffer.readDouble();
            double costPerSkill = buffer.readDouble();
            double seizeDeathChancePerSkill = buffer.readDouble();
            double seizeDeathChanceMax = buffer.readDouble();
            int encodedCount = Math.max(0, buffer.readVarInt());
            int retainedCount = Math.min(
                    encodedCount,
                    MAX_SKILLS
            );
            List<SkillEntry> skills = new ArrayList<>(
                    retainedCount
            );

            for (int index = 0;
                 index < encodedCount;
                 index++) {
                SkillEntry entry = SkillEntry.decode(buffer);

                if (index < retainedCount) {
                    skills.add(entry);
                }
            }

            return new OpenUltimateSubordinateSkillScreenPayload(
                    seize,
                    targetUuid,
                    targetName,
                    availableMagicules,
                    costPerSkill,
                    seizeDeathChancePerSkill,
                    seizeDeathChanceMax,
                    skills
            );
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer,
                OpenUltimateSubordinateSkillScreenPayload payload
        ) {
            buffer.writeBoolean(payload.seize());
            buffer.writeUtf(payload.targetUuid(), MAX_ID_LENGTH);
            buffer.writeUtf(payload.targetName(), MAX_NAME_LENGTH);
            buffer.writeDouble(payload.availableMagicules());
            buffer.writeDouble(payload.costPerSkill());
            buffer.writeDouble(payload.seizeDeathChancePerSkill());
            buffer.writeDouble(payload.seizeDeathChanceMax());

            List<SkillEntry> safeSkills = payload.skills()
                    .stream()
                    .limit(MAX_SKILLS)
                    .toList();

            buffer.writeVarInt(safeSkills.size());

            for (SkillEntry entry : safeSkills) {
                entry.encode(buffer);
            }
        }
    };

    public OpenUltimateSubordinateSkillScreenPayload {
        targetUuid = clean(targetUuid);
        targetName = clean(targetName);
        availableMagicules = sanitizeNonNegative(availableMagicules);
        costPerSkill = sanitizeNonNegative(costPerSkill);
        seizeDeathChancePerSkill = clampChance(
                seizeDeathChancePerSkill
        );
        seizeDeathChanceMax = clampChance(
                seizeDeathChanceMax
        );
        skills = skills == null
                ? List.of()
                : List.copyOf(
                skills.stream()
                        .filter(entry -> entry != null)
                        .limit(MAX_SKILLS)
                        .toList()
        );
    }


    public OpenUltimateSubordinateSkillScreenPayload(
            boolean seize,
            String targetUuid,
            String targetName,
            double costPerSkill,
            double seizeDeathChancePerSkill,
            double seizeDeathChanceMax,
            List<SkillEntry> skills
    ) {
        this(
                seize,
                targetUuid,
                targetName,
                0.0D,
                costPerSkill,
                seizeDeathChancePerSkill,
                seizeDeathChanceMax,
                skills
        );
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String clean(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private static double sanitizeNonNegative(
            double value
    ) {
        return !Double.isFinite(value)
                || value < 0.0D
                ? 0.0D
                : value;
    }

    private static double clampChance(
            double value
    ) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(
                0.0D,
                Math.min(1.0D, value)
        );
    }

    public record SkillEntry(
            String skillId,
            String displayName,
            String category,
            boolean mastered,
            double borrowPermanentChance
    ) {

        public SkillEntry {
            skillId = clean(skillId);
            displayName = clean(displayName);
            category = clean(category);
            borrowPermanentChance = clampChance(
                    borrowPermanentChance
            );
        }

        private static SkillEntry decode(
                RegistryFriendlyByteBuf buffer
        ) {
            return new SkillEntry(
                    buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_CATEGORY_LENGTH),
                    buffer.readBoolean(),
                    buffer.readDouble()
            );
        }

        private void encode(
                RegistryFriendlyByteBuf buffer
        ) {
            buffer.writeUtf(skillId, MAX_ID_LENGTH);
            buffer.writeUtf(displayName, MAX_NAME_LENGTH);
            buffer.writeUtf(category, MAX_CATEGORY_LENGTH);
            buffer.writeBoolean(mastered);
            buffer.writeDouble(borrowPermanentChance);
        }
    }
}