package com.mooswqz.moostensuraaddon.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class GranterProgressData {
    public static final int CURRENT_DATA_VERSION = 2;

    public static final Codec<GranterProgressData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT
                    .optionalFieldOf("data_version", 1)
                    .forGetter(GranterProgressData::getDataVersion),

            Codec.INT
                    .optionalFieldOf("successful_grants", 0)
                    .forGetter(GranterProgressData::getSuccessfulGrants),

            Codec.INT
                    .optionalFieldOf("successful_take_backs", 0)
                    .forGetter(GranterProgressData::getSuccessfulTakeBacks),

            Codec.INT
                    .optionalFieldOf("earned_granter_mastery", 0)
                    .forGetter(GranterProgressData::getEarnedGranterMastery),

            Codec.BOOL
                    .optionalFieldOf("awakened_granter_naturally", false)
                    .forGetter(GranterProgressData::hasAwakenedGranterNaturally),

            Codec.STRING
                    .optionalFieldOf("granter_ultimate_evolution", "")
                    .forGetter(GranterProgressData::getGranterUltimateEvolution),

            Codec.STRING
                    .listOf()
                    .optionalFieldOf("recognized_subordinates", List.of())
                    .forGetter(GranterProgressData::getRecognizedSubordinates),

            Codec.STRING
                    .listOf()
                    .optionalFieldOf("granted_skill_types", List.of())
                    .forGetter(GranterProgressData::getGrantedSkillTypes)
    ).apply(instance, GranterProgressData::new));

    private int dataVersion;
    private int successfulGrants;
    private int successfulTakeBacks;
    private int earnedGranterMastery;
    private boolean awakenedGranterNaturally;
    private String granterUltimateEvolution;
    private final List<String> recognizedSubordinates;
    private final List<String> grantedSkillTypes;

    public GranterProgressData() {
        this(
                CURRENT_DATA_VERSION,
                0,
                0,
                0,
                false,
                "",
                List.of(),
                List.of()
        );
    }

    public GranterProgressData(
            int dataVersion,
            int successfulGrants,
            int successfulTakeBacks,
            int earnedGranterMastery,
            boolean awakenedGranterNaturally,
            String granterUltimateEvolution,
            List<String> recognizedSubordinates,
            List<String> grantedSkillTypes
    ) {
        this.dataVersion = Math.max(1, dataVersion);
        this.successfulGrants = Math.max(0, successfulGrants);
        this.successfulTakeBacks = Math.max(0, successfulTakeBacks);
        this.earnedGranterMastery = Math.max(0, earnedGranterMastery);
        this.awakenedGranterNaturally = awakenedGranterNaturally;
        this.granterUltimateEvolution = granterUltimateEvolution == null ? "" : granterUltimateEvolution;
        this.recognizedSubordinates = new ArrayList<>();
        this.grantedSkillTypes = new ArrayList<>();

        if (recognizedSubordinates != null) {
            for (String subordinate : recognizedSubordinates) {
                if (subordinate == null || subordinate.isBlank()) {
                    continue;
                }

                this.recognizedSubordinates.add(subordinate);
            }
        }

        if (grantedSkillTypes != null) {
            for (String skillType : grantedSkillTypes) {
                if (skillType == null || skillType.isBlank()) {
                    continue;
                }

                this.grantedSkillTypes.add(skillType);
            }
        }
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(int dataVersion) {
        this.dataVersion = Math.max(1, dataVersion);
    }

    public int getSuccessfulGrants() {
        return successfulGrants;
    }

    public int getSuccessfulTakeBacks() {
        return successfulTakeBacks;
    }

    public int getEarnedGranterMastery() {
        return earnedGranterMastery;
    }

    public boolean hasAwakenedGranterNaturally() {
        return awakenedGranterNaturally;
    }

    public String getGranterUltimateEvolution() {
        return granterUltimateEvolution;
    }

    public boolean hasEvolvedGranterUltimate() {
        return granterUltimateEvolution != null && !granterUltimateEvolution.isBlank();
    }

    public List<String> getRecognizedSubordinates() {
        return recognizedSubordinates;
    }

    public List<String> getGrantedSkillTypes() {
        return grantedSkillTypes;
    }

    public void incrementSuccessfulGrants() {
        successfulGrants++;
        markCurrentVersion();
    }

    public void incrementSuccessfulTakeBacks() {
        successfulTakeBacks++;
        markCurrentVersion();
    }

    public void setAwakenedGranterNaturally(boolean awakenedGranterNaturally) {
        this.awakenedGranterNaturally = awakenedGranterNaturally;
        markCurrentVersion();
    }

    public void setGranterUltimateEvolution(String granterUltimateEvolution) {
        this.granterUltimateEvolution = granterUltimateEvolution == null ? "" : granterUltimateEvolution;
        markCurrentVersion();
    }

    public void setEarnedGranterMastery(int earnedGranterMastery) {
        this.earnedGranterMastery = Math.max(0, earnedGranterMastery);
        markCurrentVersion();
    }

    public void addEarnedGranterMastery(int amount, int currentRealMastery, int maxMastery) {
        if (amount <= 0) {
            return;
        }

        int safeMaxMastery = Math.max(0, maxMastery);
        int baseline = Math.max(earnedGranterMastery, Math.max(0, currentRealMastery));
        int newValue = baseline + amount;

        if (safeMaxMastery > 0) {
            newValue = Math.min(newValue, safeMaxMastery);
        }

        earnedGranterMastery = Math.max(0, newValue);
        markCurrentVersion();
    }

    public boolean recognizeSubordinate(UUID subordinateUuid) {
        if (subordinateUuid == null) {
            return false;
        }

        String uuid = subordinateUuid.toString();

        if (recognizedSubordinates.contains(uuid)) {
            return false;
        }

        recognizedSubordinates.add(uuid);
        markCurrentVersion();

        return true;
    }

    public boolean hasRecognizedSubordinate(UUID subordinateUuid) {
        if (subordinateUuid == null) {
            return false;
        }

        return recognizedSubordinates.contains(subordinateUuid.toString());
    }

    public boolean recordGrantedSkillType(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return false;
        }

        if (grantedSkillTypes.contains(skillId)) {
            return false;
        }

        grantedSkillTypes.add(skillId);
        markCurrentVersion();

        return true;
    }

    public boolean hasGrantedSkillType(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return false;
        }

        return grantedSkillTypes.contains(skillId);
    }

    public int getRecognizedSubordinateCount() {
        return recognizedSubordinates.size();
    }

    public int getGrantedSkillTypeCount() {
        return grantedSkillTypes.size();
    }

    public boolean cleanup(Predicate<String> validSkillPredicate) {
        boolean changed = false;

        if (successfulGrants < 0) {
            successfulGrants = 0;
            changed = true;
        }

        if (successfulTakeBacks < 0) {
            successfulTakeBacks = 0;
            changed = true;
        }

        if (earnedGranterMastery < 0) {
            earnedGranterMastery = 0;
            changed = true;
        }

        if (granterUltimateEvolution == null) {
            granterUltimateEvolution = "";
            changed = true;
        }

        changed |= cleanupRecognizedSubordinates();
        changed |= cleanupGrantedSkillTypes(validSkillPredicate);

        if (changed) {
            markCurrentVersion();
        } else if (dataVersion < CURRENT_DATA_VERSION) {
            markCurrentVersion();
            changed = true;
        }

        return changed;
    }

    private boolean cleanupRecognizedSubordinates() {
        boolean changed = false;
        Set<String> seen = new HashSet<>();
        List<String> cleaned = new ArrayList<>();

        for (String rawUuid : recognizedSubordinates) {
            if (rawUuid == null || rawUuid.isBlank()) {
                changed = true;
                continue;
            }

            try {
                UUID.fromString(rawUuid);
            } catch (IllegalArgumentException exception) {
                changed = true;
                continue;
            }

            if (!seen.add(rawUuid)) {
                changed = true;
                continue;
            }

            cleaned.add(rawUuid);
        }

        if (changed) {
            recognizedSubordinates.clear();
            recognizedSubordinates.addAll(cleaned);
        }

        return changed;
    }

    private boolean cleanupGrantedSkillTypes(Predicate<String> validSkillPredicate) {
        boolean changed = false;
        Set<String> seen = new HashSet<>();
        List<String> cleaned = new ArrayList<>();

        for (String skillId : grantedSkillTypes) {
            if (skillId == null || skillId.isBlank()) {
                changed = true;
                continue;
            }

            if (validSkillPredicate != null && !validSkillPredicate.test(skillId)) {
                changed = true;
                continue;
            }

            if (!seen.add(skillId)) {
                changed = true;
                continue;
            }

            cleaned.add(skillId);
        }

        if (changed) {
            grantedSkillTypes.clear();
            grantedSkillTypes.addAll(cleaned);
        }

        return changed;
    }

    private void markCurrentVersion() {
        this.dataVersion = CURRENT_DATA_VERSION;
    }
}