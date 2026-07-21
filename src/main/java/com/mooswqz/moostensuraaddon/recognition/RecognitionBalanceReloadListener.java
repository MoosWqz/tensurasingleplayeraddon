package com.mooswqz.moostensuraaddon.recognition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources
        .SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the canonical recognition balance definition from:
 *
 * data/moostensuraaddon/recognition_balance/default.json
 *
 * Datapacks override the built-in file by providing the exact same resource
 * location. A candidate is parsed and validated completely before it replaces
 * the active snapshot. Invalid reloads leave the previous valid balance active.
 */
public final class RecognitionBalanceReloadListener
        extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    /*
     * Every constructor dependency must be initialized before INSTANCE.
     */
    private static final Gson GSON =
            new GsonBuilder()
                    .disableHtmlEscaping()
                    .create();

    private static final String DIRECTORY =
            "recognition_balance";

    private static final ResourceLocation CANONICAL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(
                    MoosTensuraAddon.MODID,
                    "default"
            );

    private static final int MAX_SKILL_TIERS =
            16;

    public static final RecognitionBalanceReloadListener
            INSTANCE =
            new RecognitionBalanceReloadListener();

    private RecognitionBalanceReloadListener() {
        super(
                GSON,
                DIRECTORY
        );
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        JsonElement canonicalElement =
                resources.get(
                        CANONICAL_RESOURCE
                );

        if (canonicalElement == null) {
            RecognitionBalanceManager.State active =
                    RecognitionBalanceManager.getState();

            LOGGER.error(
                    "Recognition balance resource {} was not found. "
                            + "Keeping revision {} from {}.",
                    CANONICAL_RESOURCE,
                    active.revision(),
                    active.snapshot().sourceId()
            );

            return;
        }

        if (resources.size() > 1) {
            LOGGER.warn(
                    "Found {} recognition balance files. Only the canonical "
                            + "resource {} is used; {} additional file(s) were "
                            + "ignored.",
                    resources.size(),
                    CANONICAL_RESOURCE,
                    resources.size() - 1
            );
        }

        try {
            RecognitionBalanceSnapshot candidate =
                    parseSnapshot(
                            CANONICAL_RESOURCE,
                            canonicalElement
                    );

            RecognitionBalanceManager.State installed =
                    RecognitionBalanceManager.install(
                            candidate
                    );

            /*
             * Cached GUI payloads contain scores and threshold values from the
             * previous definition. Clear them only after a successful install.
             */
            RecognitionProgressScreenService.clearAll();

            LOGGER.info(
                    "Loaded recognition balance revision {} from {}.",
                    installed.revision(),
                    installed.snapshot().sourceId()
            );
        } catch (RuntimeException exception) {
            RecognitionBalanceManager.State active =
                    RecognitionBalanceManager.getState();

            LOGGER.error(
                    "Failed to load recognition balance {}. Keeping revision "
                            + "{} from {}.",
                    CANONICAL_RESOURCE,
                    active.revision(),
                    active.snapshot().sourceId(),
                    exception
            );
        }
    }

    private static RecognitionBalanceSnapshot parseSnapshot(
            ResourceLocation resourceId,
            JsonElement rootElement
    ) {
        JsonObject root =
                requireObject(
                        rootElement,
                        "root"
                );

        int schemaVersion =
                requireInteger(
                        root,
                        "schema_version",
                        1,
                        RecognitionBalanceSnapshot.SCHEMA_VERSION
                );

        if (schemaVersion
                != RecognitionBalanceSnapshot.SCHEMA_VERSION) {
            throw new JsonParseException(
                    "Unsupported recognition balance schema version "
                            + schemaVersion
                            + ". Expected "
                            + RecognitionBalanceSnapshot.SCHEMA_VERSION
                            + "."
            );
        }

        RecognitionBalanceSnapshot.Selection selection =
                parseSelection(
                        requireObject(
                                root,
                                "selection"
                        )
                );

        RecognitionBalanceSnapshot.IdentityDistribution
                identityDistribution =
                parseIdentityDistribution(
                        requireObject(
                                root,
                                "identity_distribution"
                        )
                );

        RecognitionBalanceSnapshot.IdentityStrength identityStrength =
                parseIdentityStrength(
                        requireObject(
                                root,
                                "identity_strength"
                        )
                );

        RecognitionBalanceSnapshot.Good good =
                parseGood(
                        requireObject(
                                root,
                                "good"
                        )
                );

        RecognitionBalanceSnapshot.Evil evil =
                parseEvil(
                        requireObject(
                                root,
                                "evil"
                        )
                );

        RecognitionBalanceSnapshot.Order order =
                parseOrder(
                        requireObject(
                                root,
                                "order"
                        )
                );

        RecognitionBalanceSnapshot.Freedom freedom =
                parseFreedom(
                        requireObject(
                                root,
                                "freedom"
                        )
                );

        RecognitionBalanceSnapshot.Mastery mastery =
                parseMastery(
                        requireObject(
                                root,
                                "mastery"
                        )
                );

        RecognitionBalanceSnapshot.Discovery discovery =
                parseDiscovery(
                        requireObject(
                                root,
                                "discovery"
                        )
                );

        RecognitionBalanceSnapshot.Neutrality neutrality =
                parseNeutrality(
                        requireObject(
                                root,
                                "neutrality"
                        )
                );

        RecognitionBalanceSnapshot.RawPaths rawPaths =
                parseRawPaths(
                        requireObject(
                                root,
                                "raw_paths"
                        )
                );

        validateCrossFieldRules(
                selection,
                identityDistribution
        );

        return new RecognitionBalanceSnapshot(
                resourceId.toString(),
                selection,
                identityDistribution,
                identityStrength,
                good,
                evil,
                order,
                freedom,
                mastery,
                discovery,
                neutrality,
                rawPaths
        );
    }

    private static RecognitionBalanceSnapshot.Selection
    parseSelection(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.Selection(
                requireDouble(
                        object,
                        "established_threshold",
                        1.0D,
                        10_000.0D
                ),
                requireDouble(
                        object,
                        "pure_threshold",
                        1.0D,
                        10_000.0D
                ),
                requireDouble(
                        object,
                        "dominance_ratio",
                        1.0D,
                        100.0D
                ),
                requireDouble(
                        object,
                        "raw_pure_threshold",
                        1.0D,
                        10_000.0D
                ),
                requireDouble(
                        object,
                        "raw_dominance_ratio",
                        1.0D,
                        100.0D
                ),
                requireDouble(
                        object,
                        "minimum_directional_morality_evidence",
                        0.0D,
                        10_000.0D
                )
        );
    }

    private static RecognitionBalanceSnapshot.IdentityDistribution
    parseIdentityDistribution(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.IdentityDistribution(
                requireDouble(
                        object,
                        "universal_share",
                        0.0D,
                        1.0D
                ),
                requireDouble(
                        object,
                        "focused_share",
                        0.0D,
                        1.0D
                ),
                requireDouble(
                        object,
                        "minimum_focus_affinity",
                        0.0D,
                        10_000.0D
                ),
                requireDouble(
                        object,
                        "secondary_focus_ratio",
                        0.0D,
                        1.0D
                )
        );
    }

    private static RecognitionBalanceSnapshot.IdentityStrength
    parseIdentityStrength(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.IdentityStrength(
                requireDouble(
                        object,
                        "maximum",
                        0.0D,
                        10_000.0D
                ),
                requireDouble(
                        object,
                        "mastery_weight",
                        0.0D,
                        100.0D
                ),
                requireDouble(
                        object,
                        "discovery_weight",
                        0.0D,
                        100.0D
                ),
                parseContribution(
                        requireObject(
                                object,
                                "major_enemy_types"
                        ),
                        "identity_strength.major_enemy_types"
                )
        );
    }

    private static RecognitionBalanceSnapshot.Good parseGood(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.Good(
                requireDouble(
                        object,
                        "true_hero_modifier",
                        0.0D,
                        10_000.0D
                ),
                parseContribution(
                        requireObject(
                                object,
                                "raid_victories"
                        ),
                        "good.raid_victories"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "villagers_cured"
                        ),
                        "good.villagers_cured"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "civilians_defended"
                        ),
                        "good.civilians_defended"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "malevolent_boss_types_defeated"
                        ),
                        "good.malevolent_boss_types_defeated"
                )
        );
    }

    private static RecognitionBalanceSnapshot.Evil parseEvil(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.Evil(
                requireDouble(
                        object,
                        "true_demon_lord_modifier",
                        0.0D,
                        10_000.0D
                ),
                parseContribution(
                        requireObject(
                                object,
                                "civilian_kills"
                        ),
                        "evil.civilian_kills"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "passive_baby_kills"
                        ),
                        "evil.passive_baby_kills"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "owned_companion_kills"
                        ),
                        "evil.owned_companion_kills"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "owned_subordinate_kills"
                        ),
                        "evil.owned_subordinate_kills"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "benevolent_boss_types_killed"
                        ),
                        "evil.benevolent_boss_types_killed"
                )
        );
    }

    private static RecognitionBalanceSnapshot.Order parseOrder(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.Order(
                parseContribution(
                        requireObject(
                                object,
                                "subordinate_roster_primary"
                        ),
                        "order.subordinate_roster_primary"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "subordinate_roster_established"
                        ),
                        "order.subordinate_roster_established"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "subordinate_assisted_major_victories"
                        ),
                        "order.subordinate_assisted_major_victories"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "unique_subordinates_empowered"
                        ),
                        "order.unique_subordinates_empowered"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "mass_grants_performed"
                        ),
                        "order.mass_grants_performed"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "global_take_backs_performed"
                        ),
                        "order.global_take_backs_performed"
                )
        );
    }

    private static RecognitionBalanceSnapshot.Freedom parseFreedom(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.Freedom(
                parseContribution(
                        requireObject(
                                object,
                                "solo_major_enemy_types_defeated"
                        ),
                        "freedom.solo_major_enemy_types_defeated"
                ),
                parseContribution(
                        requireObject(
                                object,
                                "discovery_milestones"
                        ),
                        "freedom.discovery_milestones"
                )
        );
    }

    private static RecognitionBalanceSnapshot.Mastery parseMastery(
            JsonObject object
    ) {
        JsonArray tierArray =
                requireArray(
                        object,
                        "skill_tiers"
                );

        if (tierArray.size() == 0) {
            throw new JsonParseException(
                    "mastery.skill_tiers must contain at least one tier."
            );
        }

        if (tierArray.size() > MAX_SKILL_TIERS) {
            throw new JsonParseException(
                    "mastery.skill_tiers may contain at most "
                            + MAX_SKILL_TIERS
                            + " tiers."
            );
        }

        List<RecognitionBalanceSnapshot.SkillTier> tiers =
                new ArrayList<>(
                        tierArray.size()
                );

        for (int index = 0;
             index < tierArray.size();
             index++) {

            JsonObject tierObject =
                    requireObject(
                            tierArray.get(index),
                            "mastery.skill_tiers["
                                    + index
                                    + "]"
                    );

            tiers.add(
                    new RecognitionBalanceSnapshot.SkillTier(
                            requireInteger(
                                    tierObject,
                                    "entries",
                                    1,
                                    1_000_000
                            ),
                            requireDouble(
                                    tierObject,
                                    "points_per_entry",
                                    0.0D,
                                    10_000.0D
                            )
                    )
            );
        }

        return new RecognitionBalanceSnapshot.Mastery(
                tiers,
                requireDouble(
                        object,
                        "skill_maximum",
                        0.0D,
                        100_000.0D
                ),
                parseContribution(
                        requireObject(
                                object,
                                "mastered_skill_categories"
                        ),
                        "mastery.mastered_skill_categories"
                ),
                parseEpContribution(
                        requireObject(
                                object,
                                "highest_ep"
                        )
                ),
                parseContribution(
                        requireObject(
                                object,
                                "major_enemy_types_defeated"
                        ),
                        "mastery.major_enemy_types_defeated"
                )
        );
    }

    private static RecognitionBalanceSnapshot.Discovery
    parseDiscovery(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.Discovery(
                parseContribution(
                        requireObject(
                                object,
                                "milestones"
                        ),
                        "discovery.milestones"
                )
        );
    }

    private static RecognitionBalanceSnapshot.Neutrality
    parseNeutrality(
            JsonObject object
    ) {
        JsonObject morality =
                requireObject(
                        object,
                        "morality"
                );

        JsonObject behaviour =
                requireObject(
                        object,
                        "behaviour"
                );

        return new RecognitionBalanceSnapshot.Neutrality(
                new RecognitionBalanceSnapshot.NeutralMorality(
                        requireDouble(
                                morality,
                                "balance_weight",
                                0.0D,
                                100.0D
                        ),
                        requireDouble(
                                morality,
                                "discovery_weight",
                                0.0D,
                                100.0D
                        ),
                        requireDouble(
                                morality,
                                "moral_volume_divisor",
                                0.000_001D,
                                1_000_000.0D
                        ),
                        requireDouble(
                                morality,
                                "maximum",
                                0.0D,
                                100_000.0D
                        )
                ),
                new RecognitionBalanceSnapshot.NeutralBehaviour(
                        requireDouble(
                                behaviour,
                                "balance_weight",
                                0.0D,
                                100.0D
                        ),
                        requireDouble(
                                behaviour,
                                "posture_base",
                                0.0D,
                                100_000.0D
                        ),
                        requireDouble(
                                behaviour,
                                "behaviour_volume_divisor",
                                0.000_001D,
                                1_000_000.0D
                        ),
                        requireDouble(
                                behaviour,
                                "maximum",
                                0.0D,
                                100_000.0D
                        )
                )
        );
    }

    private static RecognitionBalanceSnapshot.RawPaths parseRawPaths(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.RawPaths(
                requireDouble(
                        object,
                        "moral_weight",
                        0.0D,
                        100.0D
                ),
                requireDouble(
                        object,
                        "temperament_weight",
                        0.0D,
                        100.0D
                ),
                requireDouble(
                        object,
                        "overlap_weight",
                        0.0D,
                        100.0D
                ),
                requireDouble(
                        object,
                        "contradiction_factor",
                        0.0D,
                        100.0D
                ),
                requireDouble(
                        object,
                        "contradiction_maximum",
                        0.0D,
                        100_000.0D
                ),
                requireDouble(
                        object,
                        "true_neutral_discovery_factor",
                        0.0D,
                        100.0D
                ),
                requireDouble(
                        object,
                        "true_neutral_discovery_maximum",
                        0.0D,
                        100_000.0D
                ),
                requireDouble(
                        object,
                        "chaotic_neutral_discovery_factor",
                        0.0D,
                        100.0D
                ),
                requireDouble(
                        object,
                        "chaotic_neutral_discovery_maximum",
                        0.0D,
                        100_000.0D
                )
        );
    }

    private static RecognitionBalanceSnapshot.Contribution
    parseContribution(
            JsonObject object,
            String path
    ) {
        double pointsPerEntry =
                requireDouble(
                        object,
                        "points_per_entry",
                        0.0D,
                        100_000.0D
                );

        double maximum =
                requireDouble(
                        object,
                        "maximum",
                        0.0D,
                        100_000.0D
                );

        if (pointsPerEntry > 0.0D
                && maximum <= 0.0D) {
            throw new JsonParseException(
                    path
                            + ".maximum must be positive when "
                            + "points_per_entry is positive."
            );
        }

        return new RecognitionBalanceSnapshot.Contribution(
                pointsPerEntry,
                maximum
        );
    }

    private static RecognitionBalanceSnapshot.EpContribution
    parseEpContribution(
            JsonObject object
    ) {
        return new RecognitionBalanceSnapshot.EpContribution(
                requireDouble(
                        object,
                        "ep_per_unit",
                        0.000_001D,
                        1_000_000_000_000.0D
                ),
                requireDouble(
                        object,
                        "points_per_unit",
                        0.0D,
                        100_000.0D
                ),
                requireDouble(
                        object,
                        "maximum",
                        0.0D,
                        100_000.0D
                )
        );
    }

    private static void validateCrossFieldRules(
            RecognitionBalanceSnapshot.Selection selection,
            RecognitionBalanceSnapshot.IdentityDistribution
                    identityDistribution
    ) {
        if (selection.pureThreshold()
                < selection.establishedThreshold()) {

            throw new JsonParseException(
                    "selection.pure_threshold must be greater than or equal "
                            + "to selection.established_threshold."
            );
        }

        double convertedIdentityShare =
                identityDistribution.universalShare()
                        + identityDistribution.focusedShare();

        if (convertedIdentityShare > 1.0D + 0.000_001D) {
            throw new JsonParseException(
                    "identity_distribution universal_share + focused_share "
                            + "cannot exceed 1.0."
            );
        }
    }

    private static JsonObject requireObject(
            JsonObject parent,
            String key
    ) {
        if (parent == null
                || key == null
                || !parent.has(key)) {

            throw new JsonParseException(
                    "Missing required object '"
                            + key
                            + "'."
            );
        }

        return requireObject(
                parent.get(key),
                key
        );
    }

    private static JsonObject requireObject(
            JsonElement element,
            String path
    ) {
        if (element == null
                || !element.isJsonObject()) {

            throw new JsonParseException(
                    "Expected JSON object at '"
                            + path
                            + "'."
            );
        }

        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(
            JsonObject parent,
            String key
    ) {
        if (parent == null
                || key == null
                || !parent.has(key)) {

            throw new JsonParseException(
                    "Missing required array '"
                            + key
                            + "'."
            );
        }

        JsonElement element =
                parent.get(key);

        if (element == null
                || !element.isJsonArray()) {

            throw new JsonParseException(
                    "Expected JSON array at '"
                            + key
                            + "'."
            );
        }

        return element.getAsJsonArray();
    }

    private static int requireInteger(
            JsonObject parent,
            String key,
            int minimum,
            int maximum
    ) {
        double rawValue =
                requireDouble(
                        parent,
                        key,
                        minimum,
                        maximum
                );

        if (rawValue != Math.rint(rawValue)) {
            throw new JsonParseException(
                    "Field '"
                            + key
                            + "' must be an integer."
            );
        }

        return (int) rawValue;
    }

    private static double requireDouble(
            JsonObject parent,
            String key,
            double minimum,
            double maximum
    ) {
        if (parent == null
                || key == null
                || !parent.has(key)) {

            throw new JsonParseException(
                    "Missing required numeric field '"
                            + key
                            + "'."
            );
        }

        JsonElement element =
                parent.get(key);

        if (element == null
                || !element.isJsonPrimitive()) {

            throw new JsonParseException(
                    "Field '"
                            + key
                            + "' must be numeric."
            );
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isNumber()) {
            throw new JsonParseException(
                    "Field '"
                            + key
                            + "' must be numeric."
            );
        }

        double value =
                primitive.getAsDouble();

        if (!Double.isFinite(value)
                || value < minimum
                || value > maximum) {

            throw new JsonParseException(
                    "Field '"
                            + key
                            + "' must be between "
                            + minimum
                            + " and "
                            + maximum
                            + "."
            );
        }

        return value;
    }
}