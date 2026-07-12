package com.mooswqz.moostensuraaddon.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class BorrowedSkillData {
    public static final int CURRENT_DATA_VERSION = 2;

    public static final Codec<BorrowedSkillData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT
                    .optionalFieldOf("data_version", 1)
                    .forGetter(BorrowedSkillData::getDataVersion),

            BorrowedSkillRecord.CODEC
                    .listOf()
                    .optionalFieldOf("borrowed_skills", List.of())
                    .forGetter(BorrowedSkillData::getBorrowedSkills),

            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("borrow_history", Map.of())
                    .forGetter(BorrowedSkillData::getBorrowHistory)
    ).apply(instance, BorrowedSkillData::new));

    private int dataVersion;
    private final List<BorrowedSkillRecord> borrowedSkills;
    private final Map<String, Integer> borrowHistory;

    public BorrowedSkillData() {
        this(CURRENT_DATA_VERSION, List.of(), Map.of());
    }

    public BorrowedSkillData(
            int dataVersion,
            List<BorrowedSkillRecord> borrowedSkills,
            Map<String, Integer> borrowHistory
    ) {
        this.dataVersion = Math.max(1, dataVersion);
        this.borrowedSkills = new ArrayList<>();
        this.borrowHistory = new HashMap<>();

        if (borrowedSkills != null) {
            for (BorrowedSkillRecord record : borrowedSkills) {
                if (record == null || isBlank(record.skillId())) {
                    continue;
                }

                this.borrowedSkills.add(record);
            }
        }

        if (borrowHistory != null) {
            for (Map.Entry<String, Integer> entry : borrowHistory.entrySet()) {
                if (entry == null || isBlank(entry.getKey())) {
                    continue;
                }

                int count = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());

                if (count <= 0) {
                    continue;
                }

                this.borrowHistory.put(entry.getKey(), count);
            }
        }
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(int dataVersion) {
        this.dataVersion = Math.max(1, dataVersion);
    }

    public List<BorrowedSkillRecord> getBorrowedSkills() {
        return borrowedSkills;
    }

    public Map<String, Integer> getBorrowHistory() {
        return borrowHistory;
    }

    public void addBorrowedSkill(String skillId, long expiresAtGameTime) {
        if (isBlank(skillId)) {
            return;
        }

        removeBorrowedSkill(skillId);
        borrowedSkills.add(new BorrowedSkillRecord(skillId, expiresAtGameTime));
        markCurrentVersion();
    }

    public boolean removeBorrowedSkill(String skillId) {
        if (isBlank(skillId)) {
            return false;
        }

        boolean changed = borrowedSkills.removeIf(record ->
                record != null && skillId.equals(record.skillId())
        );

        if (changed) {
            markCurrentVersion();
        }

        return changed;
    }

    public boolean isBorrowedSkill(String skillId) {
        if (isBlank(skillId)) {
            return false;
        }

        for (BorrowedSkillRecord record : borrowedSkills) {
            if (record != null && skillId.equals(record.skillId())) {
                return true;
            }
        }

        return false;
    }

    public List<BorrowedSkillRecord> getExpiredBorrowedSkills(long currentGameTime) {
        List<BorrowedSkillRecord> expired = new ArrayList<>();

        for (BorrowedSkillRecord record : borrowedSkills) {
            if (record == null) {
                continue;
            }

            if (record.expiresAtGameTime() <= currentGameTime) {
                expired.add(record);
            }
        }

        return expired;
    }

    public void removeExpiredBorrowedSkills(long currentGameTime) {
        boolean changed = borrowedSkills.removeIf(record ->
                record != null && record.expiresAtGameTime() <= currentGameTime
        );

        if (changed) {
            markCurrentVersion();
        }
    }

    public int getBorrowCount(String skillId) {
        if (isBlank(skillId)) {
            return 0;
        }

        return Math.max(0, borrowHistory.getOrDefault(skillId, 0));
    }

    public int incrementBorrowCount(String skillId) {
        if (isBlank(skillId)) {
            return 0;
        }

        int newCount = getBorrowCount(skillId) + 1;
        borrowHistory.put(skillId, newCount);
        markCurrentVersion();

        return newCount;
    }

    public void clearBorrowHistory() {
        if (borrowHistory.isEmpty()) {
            return;
        }

        borrowHistory.clear();
        markCurrentVersion();
    }

    public boolean cleanupTemporaryRecords(
            long currentGameTime,
            Predicate<String> validSkillPredicate,
            Predicate<String> playerHasSkillPredicate
    ) {
        boolean changed = false;
        Set<String> seenSkillIds = new HashSet<>();

        List<BorrowedSkillRecord> cleaned = new ArrayList<>();

        for (BorrowedSkillRecord record : borrowedSkills) {
            if (record == null || isBlank(record.skillId())) {
                changed = true;
                continue;
            }

            String skillId = record.skillId();

            if (validSkillPredicate != null && !validSkillPredicate.test(skillId)) {
                changed = true;
                continue;
            }

            if (record.expiresAtGameTime() <= currentGameTime) {
                changed = true;
                continue;
            }

            if (playerHasSkillPredicate != null && !playerHasSkillPredicate.test(skillId)) {
                changed = true;
                continue;
            }

            if (!seenSkillIds.add(skillId)) {
                changed = true;
                continue;
            }

            cleaned.add(record);
        }

        if (changed) {
            borrowedSkills.clear();
            borrowedSkills.addAll(cleaned);
            markCurrentVersion();
        } else if (dataVersion < CURRENT_DATA_VERSION) {
            markCurrentVersion();
            changed = true;
        }

        return changed;
    }

    public boolean cleanupBorrowHistory(Predicate<String> validSkillPredicate) {
        boolean changed = false;

        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : borrowHistory.entrySet()) {
            if (entry == null || isBlank(entry.getKey())) {
                changed = true;
                continue;
            }

            String skillId = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();

            if (count <= 0) {
                keysToRemove.add(skillId);
                changed = true;
                continue;
            }

            if (validSkillPredicate != null && !validSkillPredicate.test(skillId)) {
                keysToRemove.add(skillId);
                changed = true;
            }
        }

        for (String key : keysToRemove) {
            borrowHistory.remove(key);
        }

        if (changed) {
            markCurrentVersion();
        } else if (dataVersion < CURRENT_DATA_VERSION) {
            markCurrentVersion();
            changed = true;
        }

        return changed;
    }

    public boolean cleanupAll(
            long currentGameTime,
            Predicate<String> validSkillPredicate,
            Predicate<String> playerHasSkillPredicate
    ) {
        boolean changed = false;

        changed |= cleanupTemporaryRecords(currentGameTime, validSkillPredicate, playerHasSkillPredicate);
        changed |= cleanupBorrowHistory(validSkillPredicate);

        if (dataVersion < CURRENT_DATA_VERSION) {
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

    public record BorrowedSkillRecord(
            String skillId,
            long expiresAtGameTime
    ) {
        public static final Codec<BorrowedSkillRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING
                        .fieldOf("skill_id")
                        .forGetter(BorrowedSkillRecord::skillId),

                Codec.LONG
                        .fieldOf("expires_at_game_time")
                        .forGetter(BorrowedSkillRecord::expiresAtGameTime)
        ).apply(instance, BorrowedSkillRecord::new));
    }
}