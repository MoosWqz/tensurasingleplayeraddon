package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.SubordinateOverviewPolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record OpenSubordinateOverviewScreenPayload(
        int themeId,
        double radius,
        boolean refreshAllowed,
        boolean truncated,
        List<TargetEntry> targets
) implements CustomPacketPayload {

    public static final int THEME_GRANTER = 0;
    public static final int THEME_BENEVOLENT = 1;
    public static final int THEME_GOVERNANCE = 2;

    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_TYPE_LENGTH = 120;
    private static final int MAX_CATEGORY_LENGTH = 64;

    public static final Type<OpenSubordinateOverviewScreenPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "open_subordinate_overview_screen"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenSubordinateOverviewScreenPayload
            > STREAM_CODEC = new StreamCodec<>() {

        @Override
        public OpenSubordinateOverviewScreenPayload decode(
                RegistryFriendlyByteBuf buffer
        ) {
            int themeId = buffer.readVarInt();
            double radius = buffer.readDouble();
            boolean refreshAllowed = buffer.readBoolean();
            boolean truncated = buffer.readBoolean();
            int encodedCount = Math.max(0, buffer.readVarInt());
            int retainedCount = Math.min(
                    encodedCount,
                    SubordinateOverviewPolicy.MAX_TARGETS_PER_PAYLOAD
            );
            List<TargetEntry> targets =
                    new ArrayList<>(retainedCount);

            for (int index = 0; index < encodedCount; index++) {
                TargetEntry entry = TargetEntry.decode(buffer);

                if (index < retainedCount) {
                    targets.add(entry);
                }
            }

            return new OpenSubordinateOverviewScreenPayload(
                    themeId,
                    radius,
                    refreshAllowed,
                    truncated || encodedCount > retainedCount,
                    targets
            );
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer,
                OpenSubordinateOverviewScreenPayload payload
        ) {
            buffer.writeVarInt(payload.themeId());
            buffer.writeDouble(payload.radius());
            buffer.writeBoolean(payload.refreshAllowed());
            buffer.writeBoolean(payload.truncated());

            List<TargetEntry> safeTargets = payload.targets()
                    .stream()
                    .limit(
                            SubordinateOverviewPolicy
                                    .MAX_TARGETS_PER_PAYLOAD
                    )
                    .toList();
            buffer.writeVarInt(safeTargets.size());

            for (TargetEntry target : safeTargets) {
                target.encode(buffer);
            }
        }
    };

    public OpenSubordinateOverviewScreenPayload {
        themeId = normalizeThemeId(themeId);
        radius = Double.isFinite(radius) && radius > 0.0D
                ? radius
                : SubordinateOverviewPolicy.NEARBY_RADIUS;
        targets = targets == null
                ? List.of()
                : List.copyOf(
                targets.stream()
                        .filter(target -> target != null)
                        .limit(
                                SubordinateOverviewPolicy
                                .MAX_TARGETS_PER_PAYLOAD
                        )
                        .toList()
        );
    }

    public OpenSubordinateOverviewScreenPayload(
            List<TargetEntry> targets
    ) {
        this(
                THEME_GRANTER,
                16.0D,
                false,
                false,
                targets
        );
    }

    public boolean benevolent() {
        return themeId == THEME_BENEVOLENT;
    }

    public boolean governance() {
        return themeId == THEME_GOVERNANCE;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static int normalizeThemeId(
            int themeId
    ) {
        return switch (themeId) {
            case THEME_BENEVOLENT,
                 THEME_GOVERNANCE -> themeId;
            default -> THEME_GRANTER;
        };
    }

    private static String clean(
            String value,
            String fallback
    ) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }

    private static double sanitizeNumber(
            double value
    ) {
        return Double.isFinite(value) && value >= 0.0D
                ? value
                : 0.0D;
    }

    public record TargetEntry(
            String targetUuid,
            String targetName,
            String typeName,
            float health,
            float maxHealth,
            double magicules,
            double ep,
            double distance,
            List<SkillEntry> skills
    ) {

        public TargetEntry {
            targetUuid = clean(targetUuid, "");
            targetName = clean(
                    targetName,
                    "Unknown Subordinate"
            );
            typeName = clean(typeName, "Subordinate");
            health = Float.isFinite(health)
                    ? Math.max(0.0F, health)
                    : 0.0F;
            maxHealth = Float.isFinite(maxHealth)
                    ? Math.max(0.0F, maxHealth)
                    : 0.0F;
            magicules = sanitizeNumber(magicules);
            ep = sanitizeNumber(ep);
            distance = sanitizeNumber(distance);
            skills = skills == null
                    ? List.of()
                    : List.copyOf(
                    skills.stream()
                            .filter(skill -> skill != null)
                            .limit(
                                    SubordinateOverviewPolicy
                                    .MAX_SKILLS_PER_TARGET
                            )
                            .toList()
            );
        }

        public TargetEntry(
                String targetUuid,
                String targetName,
                float health,
                float maxHealth,
                double magicules,
                double ep,
                List<SkillEntry> skills
        ) {
            this(
                    targetUuid,
                    targetName,
                    "Subordinate",
                    health,
                    maxHealth,
                    magicules,
                    ep,
                    0.0D,
                    skills
            );
        }

        private static TargetEntry decode(
                RegistryFriendlyByteBuf buffer
        ) {
            String targetUuid = buffer.readUtf(MAX_ID_LENGTH);
            String targetName = buffer.readUtf(MAX_NAME_LENGTH);
            String typeName = buffer.readUtf(MAX_TYPE_LENGTH);
            float health = buffer.readFloat();
            float maxHealth = buffer.readFloat();
            double magicules = buffer.readDouble();
            double ep = buffer.readDouble();
            double distance = buffer.readDouble();
            int encodedSkillCount = Math.max(
                    0,
                    buffer.readVarInt()
            );
            int retainedSkillCount = Math.min(
                    encodedSkillCount,
                    SubordinateOverviewPolicy.MAX_SKILLS_PER_TARGET
            );
            List<SkillEntry> skills =
                    new ArrayList<>(retainedSkillCount);

            for (int index = 0;
                 index < encodedSkillCount;
                 index++) {
                SkillEntry skill = SkillEntry.decode(buffer);

                if (index < retainedSkillCount) {
                    skills.add(skill);
                }
            }

            return new TargetEntry(
                    targetUuid,
                    targetName,
                    typeName,
                    health,
                    maxHealth,
                    magicules,
                    ep,
                    distance,
                    skills
            );
        }

        private void encode(
                RegistryFriendlyByteBuf buffer
        ) {
            buffer.writeUtf(targetUuid, MAX_ID_LENGTH);
            buffer.writeUtf(targetName, MAX_NAME_LENGTH);
            buffer.writeUtf(typeName, MAX_TYPE_LENGTH);
            buffer.writeFloat(health);
            buffer.writeFloat(maxHealth);
            buffer.writeDouble(magicules);
            buffer.writeDouble(ep);
            buffer.writeDouble(distance);

            List<SkillEntry> safeSkills = skills.stream()
                    .limit(
                            SubordinateOverviewPolicy
                                    .MAX_SKILLS_PER_TARGET
                    )
                    .toList();
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

        public SkillEntry {
            skillId = clean(skillId, "unknown");
            displayName = clean(displayName, skillId);
            categoryName = clean(categoryName, "Other Skills");
            categoryOrder = Math.max(0, categoryOrder);
        }

        private static SkillEntry decode(
                RegistryFriendlyByteBuf buffer
        ) {
            return new SkillEntry(
                    buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(MAX_CATEGORY_LENGTH),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            );
        }

        private void encode(
                RegistryFriendlyByteBuf buffer
        ) {
            buffer.writeUtf(skillId, MAX_ID_LENGTH);
            buffer.writeUtf(displayName, MAX_NAME_LENGTH);
            buffer.writeUtf(categoryName, MAX_CATEGORY_LENGTH);
            buffer.writeVarInt(categoryOrder);
            buffer.writeBoolean(mastered);
            buffer.writeBoolean(grantedByViewer);
        }
    }
}