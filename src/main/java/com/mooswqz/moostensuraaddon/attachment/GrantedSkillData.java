package com.mooswqz.moostensuraaddon.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class GrantedSkillData {
    public static final int CURRENT_DATA_VERSION = 2;

    public static final Codec<GrantedSkillData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT
                    .optionalFieldOf("data_version", 1)
                    .forGetter(GrantedSkillData::getDataVersion),

            GrantedSkillRecord.CODEC
                    .listOf()
                    .optionalFieldOf("granted_skills", List.of())
                    .forGetter(GrantedSkillData::getGrantedSkills)
    ).apply(instance, GrantedSkillData::new));

    private int dataVersion;
    private final List<GrantedSkillRecord> grantedSkills;

    public GrantedSkillData() {
        this(CURRENT_DATA_VERSION, List.of());
    }

    public GrantedSkillData(int dataVersion, List<GrantedSkillRecord> grantedSkills) {
        this.dataVersion = Math.max(1, dataVersion);
        this.grantedSkills = new ArrayList<>();

        if (grantedSkills != null) {
            for (GrantedSkillRecord record : grantedSkills) {
                if (record == null || isBlank(record.skillId()) || record.granterUuid() == null) {
                    continue;
                }

                this.grantedSkills.add(record);
            }
        }
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(int dataVersion) {
        this.dataVersion = Math.max(1, dataVersion);
    }

    public List<GrantedSkillRecord> getGrantedSkills() {
        return grantedSkills;
    }

    public boolean hasGrant(String skillId, UUID granterUuid) {
        return getGrant(skillId, granterUuid).isPresent();
    }

    public Optional<GrantedSkillRecord> getGrant(String skillId, UUID granterUuid) {
        if (isBlank(skillId) || granterUuid == null) {
            return Optional.empty();
        }

        return grantedSkills.stream()
                .filter(record -> record != null)
                .filter(record -> skillId.equals(record.skillId()))
                .filter(record -> granterUuid.equals(record.granterUuid()))
                .findFirst();
    }

    public void addGrant(String skillId, UUID granterUuid, boolean targetHadSkillBefore) {
        if (isBlank(skillId) || granterUuid == null) {
            return;
        }

        if (!hasGrant(skillId, granterUuid)) {
            grantedSkills.add(new GrantedSkillRecord(skillId, granterUuid, targetHadSkillBefore));
            markCurrentVersion();
        }
    }

    public boolean removeGrant(String skillId, UUID granterUuid) {
        if (isBlank(skillId) || granterUuid == null) {
            return false;
        }

        boolean changed = grantedSkills.removeIf(record ->
                record != null
                        && skillId.equals(record.skillId())
                        && granterUuid.equals(record.granterUuid())
        );

        if (changed) {
            markCurrentVersion();
        }

        return changed;
    }

    public boolean cleanup(Predicate<String> validSkillPredicate) {
        boolean changed = false;
        Set<String> seenKeys = new HashSet<>();
        List<GrantedSkillRecord> cleaned = new ArrayList<>();

        for (GrantedSkillRecord record : grantedSkills) {
            if (record == null || isBlank(record.skillId()) || record.granterUuid() == null) {
                changed = true;
                continue;
            }

            if (validSkillPredicate != null && !validSkillPredicate.test(record.skillId())) {
                changed = true;
                continue;
            }

            String key = record.skillId() + "|" + record.granterUuid();

            if (!seenKeys.add(key)) {
                changed = true;
                continue;
            }

            cleaned.add(record);
        }

        if (changed) {
            grantedSkills.clear();
            grantedSkills.addAll(cleaned);
            markCurrentVersion();
        } else if (dataVersion < CURRENT_DATA_VERSION) {
            markCurrentVersion();
            changed = true;
        }

        return changed;
    }

    private void markCurrentVersion() {
        this.dataVersion = CURRENT_DATA_VERSION;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record GrantedSkillRecord(
            String skillId,
            UUID granterUuid,
            boolean targetHadSkillBefore
    ) {
        private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
                value -> {
                    try {
                        return DataResult.success(UUID.fromString(value));
                    } catch (IllegalArgumentException exception) {
                        return DataResult.error(() -> "Invalid UUID: " + value);
                    }
                },
                UUID::toString
        );

        public static final Codec<GrantedSkillRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING
                        .fieldOf("skill_id")
                        .forGetter(GrantedSkillRecord::skillId),

                UUID_CODEC
                        .fieldOf("granter_uuid")
                        .forGetter(GrantedSkillRecord::granterUuid),

                Codec.BOOL
                        .optionalFieldOf("target_had_skill_before", false)
                        .forGetter(GrantedSkillRecord::targetHadSkillBefore)
        ).apply(instance, GrantedSkillRecord::new));
    }
}