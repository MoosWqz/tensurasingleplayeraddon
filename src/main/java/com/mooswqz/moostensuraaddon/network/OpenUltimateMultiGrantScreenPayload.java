package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.util.AuthorityActionMode;
import com.mooswqz.moostensuraaddon.util.AuthorityActionPolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record OpenUltimateMultiGrantScreenPayload(
        String actionId,
        String targetUuid,
        String targetName,
        double availableMagicules,
        int cooldownTicks,
        boolean allowAllEligible,
        boolean allEligibleByDefault,
        List<SkillEntry> skills
) implements CustomPacketPayload {

    public static final int MAX_SKILLS =
            AuthorityActionPolicy.MAX_SCREEN_SKILLS;
    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_REASON_LENGTH = 256;

    public static final CustomPacketPayload.Type<OpenUltimateMultiGrantScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "open_ultimate_multi_grant_screen"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenUltimateMultiGrantScreenPayload
            > STREAM_CODEC = new StreamCodec<>() {

        @Override
        public OpenUltimateMultiGrantScreenPayload decode(
                RegistryFriendlyByteBuf buffer
        ) {
            String actionId = buffer.readUtf(64);
            String targetUuid = buffer.readUtf(MAX_ID_LENGTH);
            String targetName = buffer.readUtf(MAX_NAME_LENGTH);
            double availableMagicules = buffer.readDouble();
            int cooldownTicks = Math.max(0, buffer.readVarInt());
            boolean allowAllEligible = buffer.readBoolean();
            boolean allEligibleByDefault = buffer.readBoolean();
            int encodedCount = Math.max(0, buffer.readVarInt());
            int retainedCount = Math.min(encodedCount, MAX_SKILLS);
            List<SkillEntry> skills = new ArrayList<>(retainedCount);

            for (int index = 0; index < encodedCount; index++) {
                SkillEntry entry = SkillEntry.decode(buffer);

                if (index < retainedCount) {
                    skills.add(entry);
                }
            }

            return new OpenUltimateMultiGrantScreenPayload(
                    actionId,
                    targetUuid,
                    targetName,
                    availableMagicules,
                    cooldownTicks,
                    allowAllEligible,
                    allEligibleByDefault,
                    skills
            );
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer,
                OpenUltimateMultiGrantScreenPayload payload
        ) {
            buffer.writeUtf(payload.actionId(), 64);
            buffer.writeUtf(payload.targetUuid(), MAX_ID_LENGTH);
            buffer.writeUtf(payload.targetName(), MAX_NAME_LENGTH);
            buffer.writeDouble(payload.availableMagicules());
            buffer.writeVarInt(payload.cooldownTicks());
            buffer.writeBoolean(payload.allowAllEligible());
            buffer.writeBoolean(payload.allEligibleByDefault());

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

    public OpenUltimateMultiGrantScreenPayload {
        actionId = clean(actionId);
        targetUuid = clean(targetUuid);
        targetName = clean(targetName);
        availableMagicules = sanitizeCost(availableMagicules);
        cooldownTicks = Math.max(0, cooldownTicks);
        skills = skills == null
                ? List.of()
                : List.copyOf(
                skills.stream()
                        .filter(entry -> entry != null)
                        .limit(MAX_SKILLS)
                        .toList()
        );
    }

    public OpenUltimateMultiGrantScreenPayload(
            boolean benevolent,
            String targetUuid,
            String targetName,
            String currentActiveSkillId,
            List<SkillEntry> skills
    ) {
        this(
                benevolent
                        ? AuthorityActionMode.BENEVOLENT_BESTOW.id()
                        : AuthorityActionMode.GOVERNANCE_INVEST.id(),
                targetUuid,
                targetName,
                0.0D,
                0,
                false,
                false,
                skills
        );
    }

    public AuthorityActionMode actionMode() {
        return AuthorityActionMode.fromId(actionId)
                .orElse(AuthorityActionMode.BENEVOLENT_BESTOW);
    }

    public boolean benevolent() {
        return actionMode().benevolent();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static double sanitizeCost(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? value
                : 0.0D;
    }

    public record SkillEntry(
            String skillId,
            String displayName,
            String category,
            boolean mastered,
            boolean selectable,
            String disabledReason,
            double standardCost,
            double surcharge,
            double finalCost,
            int affectedTargets
    ) {

        public SkillEntry {
            skillId = clean(skillId);
            displayName = clean(displayName);
            category = clean(category);
            disabledReason = clean(disabledReason);
            standardCost = sanitizeCost(standardCost);
            surcharge = sanitizeCost(surcharge);
            finalCost = sanitizeCost(finalCost);
            affectedTargets = Math.max(0, affectedTargets);
        }

        public SkillEntry(
                String skillId,
                String displayName,
                String category,
                boolean mastered,
                double cost
        ) {
            this(
                    skillId,
                    displayName,
                    category,
                    mastered,
                    true,
                    "",
                    cost,
                    0.0D,
                    cost,
                    1
            );
        }

        private static SkillEntry decode(
                RegistryFriendlyByteBuf buffer
        ) {
            return new SkillEntry(
                    buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readUtf(MAX_NAME_LENGTH),
                    buffer.readUtf(48),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readUtf(MAX_REASON_LENGTH),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    Math.max(0, buffer.readVarInt())
            );
        }

        private void encode(
                RegistryFriendlyByteBuf buffer
        ) {
            buffer.writeUtf(skillId, MAX_ID_LENGTH);
            buffer.writeUtf(displayName, MAX_NAME_LENGTH);
            buffer.writeUtf(category, 48);
            buffer.writeBoolean(mastered);
            buffer.writeBoolean(selectable);
            buffer.writeUtf(disabledReason, MAX_REASON_LENGTH);
            buffer.writeDouble(standardCost);
            buffer.writeDouble(surcharge);
            buffer.writeDouble(finalCost);
            buffer.writeVarInt(affectedTargets);
        }
    }
}