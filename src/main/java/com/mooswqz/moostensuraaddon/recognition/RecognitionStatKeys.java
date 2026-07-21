package com.mooswqz.moostensuraaddon.recognition;

public final class RecognitionStatKeys {

    /*
     * Integer counters
     */

    public static final String HIGHEST_EXPERIENCE_LEVEL =
            "highest_experience_level";

    public static final String MASTERED_SKILLS =
            "mastered_skills";

    public static final String MASTERED_SKILL_CATEGORIES =
            "mastered_skill_categories";

    public static final String RAID_VICTORIES =
            "raid_victories";

    public static final String VILLAGERS_CURED =
            "villagers_cured";

    public static final String CIVILIANS_DEFENDED =
            "civilians_defended";

    public static final String CIVILIAN_KILLS =
            "civilian_kills";

    public static final String PASSIVE_BABY_KILLS =
            "passive_baby_kills";

    public static final String OWNED_COMPANION_KILLS =
            "owned_companion_kills";

    public static final String OWNED_SUBORDINATE_KILLS =
            "owned_subordinate_kills";

    public static final String CURRENT_SUBORDINATES =
            "current_subordinates";

    public static final String HIGHEST_SUBORDINATES =
            "highest_subordinates";

    public static final String SUBORDINATE_ASSISTED_MAJOR_VICTORIES =
            "subordinate_assisted_major_victories";

    public static final String MASS_GRANTS_PERFORMED =
            "mass_grants_performed";

    public static final String GLOBAL_TAKE_BACKS_PERFORMED =
            "global_take_backs_performed";

    public static final String SKILLS_SEIZED =
            "skills_seized";

    /*
     * Frozen recognition-result versions
     */

    public static final String RECOGNITION_RESULT_VERSION =
            "recognition_result_version";

    public static final String RECOGNITION_RULES_VERSION =
            "recognition_rules_version";

    public static final String REWARD_PROFILE_VERSION =
            "reward_profile_version";

    /*
     * Decimal measurements
     */

    public static final String CURRENT_EP =
            "current_ep";

    public static final String HIGHEST_EP =
            "highest_ep";

    public static final String PRIMARY_SCORE_AT_COMMIT =
            "primary_score_at_commit";

    public static final String SECONDARY_SCORE_AT_COMMIT =
            "secondary_score_at_commit";

    /*
     * Boolean flags
     */

    public static final String TRUE_HERO =
            "true_hero";

    public static final String TRUE_DEMON_LORD =
            "true_demon_lord";

    public static final String NAMING_COMMITTED =
            "naming_committed";

    public static final String PURE_RECOGNITION =
            "pure_recognition";

    public static final String REVEAL_PENDING =
            "reveal_pending";

    /*
     * String values
     */

    public static final String INCARNATION_ID =
            "incarnation_id";

    public static final String PRIMARY_PATH =
            "primary_path";

    public static final String SECONDARY_PATH =
            "secondary_path";

    public static final String BESTOWED_TITLE =
            "bestowed_title";

    public static final String FROZEN_DISPLAY_NAME =
            "frozen_display_name";

    public static final String CONTRADICTION_MODIFIER =
            "contradiction_modifier";

    public static final String BALANCE_SOURCE_AT_COMMIT =
            "balance_source_at_commit";

    /*
     * Long values are encoded as decimal strings so values such as epoch
     * milliseconds and revisions never pass through lossy floating point.
     */

    public static final String BALANCE_REVISION_AT_COMMIT =
            "balance_revision_at_commit";

    public static final String COMMIT_TIMESTAMP_EPOCH_MILLIS =
            "commit_timestamp_epoch_millis";

    public static final String RECOGNITION_MIGRATION_SOURCE =
            "recognition_migration_source";

    /*
     * Unique registry ID / UUID collections
     */

    public static final String MALEVOLENT_BOSS_TYPES_DEFEATED =
            "malevolent_boss_types_defeated";

    public static final String BENEVOLENT_BOSS_TYPES_KILLED =
            "benevolent_boss_types_killed";

    public static final String MAJOR_ENEMY_TYPES_DEFEATED =
            "major_enemy_types_defeated";

    public static final String SOLO_MAJOR_ENEMY_TYPES_DEFEATED =
            "solo_major_enemy_types_defeated";

    public static final String DISCOVERY_MILESTONES =
            "discovery_milestones";

    public static final String UNIQUE_SUBORDINATES_EMPOWERED =
            "unique_subordinates_empowered";

    private RecognitionStatKeys() {
    }
}