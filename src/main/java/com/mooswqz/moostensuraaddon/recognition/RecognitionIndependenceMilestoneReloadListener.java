package com.mooswqz.moostensuraaddon.recognition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads additive independence milestones from:
 *
 * <pre>
 * data/&lt;namespace&gt;/recognition_independence_milestones/*.json
 * </pre>
 *
 * <p>Normal datapack priority resolves duplicate resource locations before
 * this listener runs. Different files may add milestones, but semantic IDs and
 * advancement IDs must remain globally unique after pack resolution.</p>
 */
public final class RecognitionIndependenceMilestoneReloadListener
        extends SimpleJsonResourceReloadListener {

    private static final Gson GSON =
            new GsonBuilder()
                    .disableHtmlEscaping()
                    .create();

    public static final RecognitionIndependenceMilestoneReloadListener
            INSTANCE =
            new RecognitionIndependenceMilestoneReloadListener();

    private static final Logger LOGGER =
            LogUtils.getLogger();

    private static final String DIRECTORY =
            "recognition_independence_milestones";

    private static final int SCHEMA_VERSION =
            1;

    private static final int MAX_SOURCE_FILES =
            256;

    private RecognitionIndependenceMilestoneReloadListener() {
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
        List<Map.Entry<ResourceLocation, JsonElement>> sortedResources =
                resources.entrySet()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        entry ->
                                                entry.getKey()
                                                        .toString()
                                )
                        )
                        .limit(
                                MAX_SOURCE_FILES
                        )
                        .toList();

        if (resources.size()
                > MAX_SOURCE_FILES) {
            LOGGER.warn(
                    "Found {} independence milestone files, but only the first {} will be processed.",
                    resources.size(),
                    MAX_SOURCE_FILES
            );
        }

        Map<ResourceLocation,
                RecognitionIndependenceMilestoneManager.Milestone>
                milestonesById =
                new LinkedHashMap<>();

        Set<ResourceLocation> advancementIds =
                new HashSet<>();

        int acceptedFiles =
                0;

        double configuredScore =
                0.0D;

        for (Map.Entry<ResourceLocation, JsonElement> entry :
                sortedResources) {

            ParsedFile parsed =
                    parseFile(
                            entry.getKey(),
                            entry.getValue()
                    );

            if (parsed == null) {
                continue;
            }

            acceptedFiles++;

            for (RecognitionIndependenceMilestoneManager.Milestone milestone :
                    parsed.milestones()) {

                if (milestonesById.size()
                        >= RecognitionIndependenceMilestoneManager
                        .MAX_MILESTONES) {
                    LOGGER.warn(
                            "Ignoring remaining independence milestones after reaching the {} entry safety cap.",
                            RecognitionIndependenceMilestoneManager
                                    .MAX_MILESTONES
                    );
                    break;
                }

                if (milestonesById.containsKey(
                        milestone.id()
                )) {
                    LOGGER.warn(
                            "Ignoring duplicate independence milestone ID {} from {}.",
                            milestone.id(),
                            entry.getKey()
                    );
                    continue;
                }

                if (advancementIds.contains(
                        milestone.advancementId()
                )) {
                    LOGGER.warn(
                            "Ignoring duplicate independence advancement ID {} from {}.",
                            milestone.advancementId(),
                            entry.getKey()
                    );
                    continue;
                }

                double nextScore =
                        configuredScore
                                + milestone.points();

                if (!Double.isFinite(
                        nextScore
                ) || nextScore
                        > RecognitionIndependenceMilestoneManager
                        .MAXIMUM_TOTAL_SCORE) {
                    LOGGER.warn(
                            "Ignoring independence milestone {} from {} because the total configured score would exceed {}.",
                            milestone.id(),
                            entry.getKey(),
                            RecognitionIndependenceMilestoneManager
                                    .MAXIMUM_TOTAL_SCORE
                    );
                    continue;
                }

                milestonesById.put(
                        milestone.id(),
                        milestone
                );

                advancementIds.add(
                        milestone.advancementId()
                );

                configuredScore =
                        nextScore;
            }
        }

        if (!resources.isEmpty()
                && acceptedFiles == 0) {
            LOGGER.error(
                    "All independence milestone files were invalid. Retaining revision {} with fingerprint {}.",
                    RecognitionIndependenceMilestoneManager
                            .getRevision(),
                    shortFingerprint(
                            RecognitionIndependenceMilestoneManager
                                    .getFingerprint()
                    )
            );
            return;
        }

        RecognitionIndependenceMilestoneManager.State installed =
                RecognitionIndependenceMilestoneManager
                        .install(
                                new ArrayList<>(
                                        milestonesById.values()
                                ),
                                acceptedFiles
                        );

        LOGGER.info(
                "Loaded {} independence milestones from {} file(s): maximum {} Freedom points, revision {}, fingerprint {}.",
                installed.milestones().size(),
                installed.sourceFileCount(),
                installed.maximumScore(),
                installed.revision(),
                shortFingerprint(
                        installed.fingerprint()
                )
        );
    }

    private static ParsedFile parseFile(
            ResourceLocation sourceId,
            JsonElement rootElement
    ) {
        if (rootElement == null
                || !rootElement.isJsonObject()) {
            LOGGER.warn(
                    "Ignoring independence milestone file {} because its root is not a JSON object.",
                    sourceId
            );
            return null;
        }

        JsonObject root =
                rootElement.getAsJsonObject();

        Integer schemaVersion =
                readInteger(
                        root,
                        "schema_version"
                );

        if (schemaVersion == null
                || schemaVersion
                != SCHEMA_VERSION) {
            LOGGER.warn(
                    "Ignoring independence milestone file {} because schema_version must be {}.",
                    sourceId,
                    SCHEMA_VERSION
            );
            return null;
        }

        JsonElement milestonesElement =
                root.get(
                        "milestones"
                );

        if (milestonesElement == null
                || !milestonesElement.isJsonArray()) {
            LOGGER.warn(
                    "Ignoring independence milestone file {} because 'milestones' is not an array.",
                    sourceId
            );
            return null;
        }

        JsonArray array =
                milestonesElement.getAsJsonArray();

        List<RecognitionIndependenceMilestoneManager.Milestone>
                milestones =
                new ArrayList<>();

        for (int index = 0;
             index < array.size();
             index++) {

            JsonElement element =
                    array.get(index);

            RecognitionIndependenceMilestoneManager.Milestone milestone =
                    parseMilestone(
                            sourceId,
                            index,
                            element
                    );

            if (milestone != null) {
                milestones.add(
                        milestone
                );
            }
        }

        return new ParsedFile(
                List.copyOf(
                        milestones
                )
        );
    }

    private static RecognitionIndependenceMilestoneManager.Milestone
    parseMilestone(
            ResourceLocation sourceId,
            int index,
            JsonElement element
    ) {
        if (element == null
                || !element.isJsonObject()) {
            LOGGER.warn(
                    "Ignoring independence milestone #{} in {} because it is not an object.",
                    index,
                    sourceId
            );
            return null;
        }

        JsonObject object =
                element.getAsJsonObject();

        String rawId =
                readString(
                        object,
                        "id"
                );

        String rawAdvancement =
                readString(
                        object,
                        "advancement"
                );

        Double points =
                readDouble(
                        object,
                        "points"
                );

        ResourceLocation id =
                ResourceLocation.tryParse(
                        rawId
                );

        ResourceLocation advancementId =
                ResourceLocation.tryParse(
                        rawAdvancement
                );

        if (id == null) {
            LOGGER.warn(
                    "Ignoring independence milestone #{} in {} because 'id' is invalid.",
                    index,
                    sourceId
            );
            return null;
        }

        if (advancementId == null) {
            LOGGER.warn(
                    "Ignoring independence milestone {} in {} because 'advancement' is invalid.",
                    id,
                    sourceId
            );
            return null;
        }

        if (points == null
                || !Double.isFinite(points)
                || points <= 0.0D
                || points
                > RecognitionIndependenceMilestoneManager
                .MAX_POINTS_PER_MILESTONE) {
            LOGGER.warn(
                    "Ignoring independence milestone {} in {} because points must be positive and at most {}.",
                    id,
                    sourceId,
                    RecognitionIndependenceMilestoneManager
                            .MAX_POINTS_PER_MILESTONE
            );
            return null;
        }

        return new RecognitionIndependenceMilestoneManager.Milestone(
                id,
                advancementId,
                points
        );
    }

    private static String readString(
            JsonObject object,
            String key
    ) {
        JsonElement element =
                object.get(key);

        if (element == null
                || !element.isJsonPrimitive()) {
            return "";
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        return primitive.isString()
                ? primitive.getAsString()
                .trim()
                : "";
    }

    private static Integer readInteger(
            JsonObject object,
            String key
    ) {
        JsonElement element =
                object.get(key);

        if (element == null
                || !element.isJsonPrimitive()) {
            return null;
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isNumber()) {
            return null;
        }

        try {
            return primitive.getAsInt();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Double readDouble(
            JsonObject object,
            String key
    ) {
        JsonElement element =
                object.get(key);

        if (element == null
                || !element.isJsonPrimitive()) {
            return null;
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isNumber()) {
            return null;
        }

        try {
            return primitive.getAsDouble();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String shortFingerprint(
            String fingerprint
    ) {
        if (fingerprint == null
                || fingerprint.isBlank()) {
            return "none";
        }

        return fingerprint.length() <= 12
                ? fingerprint
                : fingerprint.substring(
                0,
                12
        );
    }

    private record ParsedFile(
            List<RecognitionIndependenceMilestoneManager.Milestone>
            milestones
    ) {

        private ParsedFile {
            milestones =
                    milestones == null
                            ? List.of()
                            : List.copyOf(
                            milestones
                    );
        }
    }
}