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
     * Versioned contradiction-history counters
     */

    public static final String IDENTITY_HISTORY_VERSION =
            "identity_history_version";

    public static final String MORAL_REVERSAL_COUNT =
            "moral_reversal_count";

    public static final String TEMPERAMENT_REVERSAL_COUNT =
            "temperament_reversal_count";

    /*
     * Persisted authority-counter baselines for event-delta integration.
     */

    public static final String HISTORY_OBSERVED_UNIQUE_SUBORDINATES_EMPOWERED =
            "history_observed_unique_subordinates_empowered";

    public static final String HISTORY_OBSERVED_MASS_GRANTS_PERFORMED =
            "history_observed_mass_grants_performed";

    public static final String HISTORY_OBSERVED_GLOBAL_TAKE_BACKS_PERFORMED =
            "history_observed_global_take_backs_performed";

    public static final String HISTORY_OBSERVED_SKILLS_SEIZED =
            "history_observed_skills_seized";

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
     * Frozen recognition-strength reward profile 2
     */

    public static final String IDENTITY_STRENGTH_AT_COMMIT =
            "identity_strength_at_commit";

    public static final String IDENTITY_STRENGTH_MAXIMUM_AT_COMMIT =
            "identity_strength_maximum_at_commit";

    public static final String RECOGNITION_STRENGTH_REWARD =
            "recognition_strength_reward";

    /*
     * Lazy contradiction-history momentum and historical peaks
     */

    public static final String GOOD_MOMENTUM =
            "good_momentum";

    public static final String EVIL_MOMENTUM =
            "evil_momentum";

    public static final String ORDER_MOMENTUM =
            "order_momentum";

    public static final String FREEDOM_MOMENTUM =
            "freedom_momentum";

    public static final String HIGHEST_GOOD_COMMITMENT =
            "highest_good_commitment";

    public static final String HIGHEST_EVIL_COMMITMENT =
            "highest_evil_commitment";

    public static final String HIGHEST_ORDER_COMMITMENT =
            "highest_order_commitment";

    public static final String HIGHEST_FREEDOM_COMMITMENT =
            "highest_freedom_commitment";

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

    public static final String IDENTITY_HISTORY_AUTHORITY_BASELINE_INITIALIZED =
            "identity_history_authority_baseline_initialized";

    public static final String RECOGNITION_REWARD_INITIALIZED =
            "recognition_reward_initialized";

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

    public static final String INDEPENDENCE_DEFINITION_FINGERPRINT =
            "independence_definition_fingerprint";

    public static final String IDENTITY_HISTORY_MODIFIER =
            "identity_history_modifier";

    public static final String IDENTITY_HISTORY_MIGRATION_SOURCE =
            "identity_history_migration_source";

    public static final String RECOGNITION_REWARD_MIGRATION_SOURCE =
            "recognition_reward_migration_source";

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

    public static final String LAST_GOOD_DEED_GAME_TIME =
            "last_good_deed_game_time";

    public static final String LAST_EVIL_DEED_GAME_TIME =
            "last_evil_deed_game_time";

    public static final String LAST_ORDER_DEED_GAME_TIME =
            "last_order_deed_game_time";

    public static final String LAST_FREEDOM_DEED_GAME_TIME =
            "last_freedom_deed_game_time";

    /*
     * Incremental lazy-decay anchors.
     *
     * These remain separate from the last-deed timestamps so repeatedly
     * evaluating decay cannot subtract the same elapsed time more than once.
     */

    public static final String GOOD_DECAY_ANCHOR_GAME_TIME =
            "good_decay_anchor_game_time";

    public static final String EVIL_DECAY_ANCHOR_GAME_TIME =
            "evil_decay_anchor_game_time";

    public static final String ORDER_DECAY_ANCHOR_GAME_TIME =
            "order_decay_anchor_game_time";

    public static final String FREEDOM_DECAY_ANCHOR_GAME_TIME =
            "freedom_decay_anchor_game_time";

    /*
     * Last clearly established direction on each contradiction axis.
     *
     * Values are semantic strings rather than enum ordinals.
     */

    public static final String MORAL_ESTABLISHED_DIRECTION =
            "moral_established_direction";

    public static final String TEMPERAMENT_ESTABLISHED_DIRECTION =
            "temperament_established_direction";

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

    public static final String INDEPENDENCE_MILESTONES =
            "independence_milestones";

    public static final String UNIQUE_SUBORDINATES_EMPOWERED =
            "unique_subordinates_empowered";

    private RecognitionStatKeys() {
    }
}
