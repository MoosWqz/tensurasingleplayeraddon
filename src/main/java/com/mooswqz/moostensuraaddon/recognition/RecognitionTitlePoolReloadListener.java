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
import net.minecraft.server.packs.resources
        .SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads recognition titles from:
 *
 * data/<namespace>/recognition_titles/*.json
 *
 * Multiple files can contribute to the same recognition path. Files are
 * processed in deterministic resource-location order, and title order inside
 * each JSON array is preserved.
 *
 * Normal datapack priority applies when two packs provide the exact same
 * ResourceLocation. Therefore, overriding:
 *
 * data/moostensuraaddon/recognition_titles/lawful_good.json
 *
 * replaces the addon's default lawful-good definition.
 */
public final class RecognitionTitlePoolReloadListener
        extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    /*
     * These fields must be initialized before INSTANCE.
     *
     * Java initializes static fields in source order. Creating INSTANCE before
     * GSON or DIRECTORY would call the superclass constructor with null values.
     */
    private static final Gson GSON =
            new GsonBuilder()
                    .disableHtmlEscaping()
                    .create();

    private static final String DIRECTORY =
            "recognition_titles";

    private static final int MAX_SOURCE_FILES =
            512;

    public static final RecognitionTitlePoolReloadListener
            INSTANCE =
            new RecognitionTitlePoolReloadListener();

    private RecognitionTitlePoolReloadListener() {
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
        EnumMap<
                RecognitionPath,
                LinkedHashSet<String>
                > standardTitles =
                createEmptyPoolSets();

        EnumMap<
                RecognitionPath,
                LinkedHashSet<String>
                > pureTitles =
                createEmptyPoolSets();

        List<Map.Entry<ResourceLocation, JsonElement>>
                sortedResources =
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
                    "Found {} recognition title files, but only the first {} "
                            + "will be processed.",
                    resources.size(),
                    MAX_SOURCE_FILES
            );
        }

        int acceptedFiles =
                0;

        for (Map.Entry<ResourceLocation, JsonElement> entry :
                sortedResources) {

            ParsedTitleFile parsed =
                    parseTitleFile(
                            entry.getKey(),
                            entry.getValue()
                    );

            if (parsed == null) {
                continue;
            }

            addTitles(
                    standardTitles.get(
                            parsed.path()
                    ),
                    parsed.standardTitles()
            );

            addTitles(
                    pureTitles.get(
                            parsed.path()
                    ),
                    parsed.pureTitles()
            );

            acceptedFiles++;
        }

        int removedDuplicates =
                removeGlobalDuplicates(
                        standardTitles,
                        pureTitles
                );

        EnumMap<RecognitionPath, List<String>>
                finalStandardTitles =
                freezePools(
                        standardTitles
                );

        EnumMap<RecognitionPath, List<String>>
                finalPureTitles =
                freezePools(
                        pureTitles
                );

        RecognitionTitlePoolManager.install(
                finalStandardTitles,
                finalPureTitles,
                acceptedFiles
        );

        if (removedDuplicates > 0) {
            LOGGER.warn(
                    "Removed {} duplicate recognition titles across the "
                            + "loaded title pools.",
                    removedDuplicates
            );
        }

        LOGGER.info(
                "Loaded {} recognition title files: {} standard titles and "
                        + "{} pure titles.",
                acceptedFiles,
                RecognitionTitlePoolManager
                        .getTotalStandardTitleCount(),
                RecognitionTitlePoolManager
                        .getTotalPureTitleCount()
        );
    }

    private static ParsedTitleFile parseTitleFile(
            ResourceLocation resourceId,
            JsonElement rootElement
    ) {
        if (rootElement == null
                || !rootElement.isJsonObject()) {

            LOGGER.warn(
                    "Ignoring recognition title file {} because its root "
                            + "element is not a JSON object.",
                    resourceId
            );

            return null;
        }

        JsonObject root =
                rootElement.getAsJsonObject();

        String rawPathId =
                readString(
                        root,
                        "path"
                );

        RecognitionPath path =
                RecognitionPath.byId(
                                rawPathId
                        )
                        .orElse(null);

        if (path == null) {
            LOGGER.warn(
                    "Ignoring recognition title file {} because '{}' is not "
                            + "a valid recognition path.",
                    resourceId,
                    rawPathId
            );

            return null;
        }

        List<String> standardTitles =
                readTitleArray(
                        resourceId,
                        root,
                        "standard"
                );

        List<String> pureTitles =
                readTitleArray(
                        resourceId,
                        root,
                        "pure"
                );

        if (standardTitles.isEmpty()
                && pureTitles.isEmpty()) {

            LOGGER.warn(
                    "Ignoring recognition title file {} because it contains "
                            + "no valid standard or pure titles.",
                    resourceId
            );

            return null;
        }

        return new ParsedTitleFile(
                path,
                standardTitles,
                pureTitles
        );
    }

    private static String readString(
            JsonObject root,
            String key
    ) {
        if (root == null
                || key == null
                || !root.has(key)) {

            return "";
        }

        JsonElement element =
                root.get(key);

        if (element == null
                || !element.isJsonPrimitive()) {

            return "";
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isString()) {
            return "";
        }

        return primitive.getAsString()
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private static List<String> readTitleArray(
            ResourceLocation resourceId,
            JsonObject root,
            String key
    ) {
        if (root == null
                || key == null
                || !root.has(key)) {

            return List.of();
        }

        JsonElement element =
                root.get(key);

        if (element == null
                || !element.isJsonArray()) {

            LOGGER.warn(
                    "Recognition title file {} has a '{}' field that is not "
                            + "a JSON array. That field will be ignored.",
                    resourceId,
                    key
            );

            return List.of();
        }

        JsonArray array =
                element.getAsJsonArray();

        List<String> titles =
                new ArrayList<>(
                        Math.min(
                                array.size(),
                                RecognitionTitlePoolManager
                                        .MAX_TITLES_PER_POOL
                        )
                );

        for (JsonElement titleElement :
                array) {

            if (titles.size()
                    >= RecognitionTitlePoolManager
                    .MAX_TITLES_PER_POOL) {

                LOGGER.warn(
                        "Recognition title file {} contains more than {} "
                                + "entries in '{}'. Remaining entries were "
                                + "ignored.",
                        resourceId,
                        RecognitionTitlePoolManager
                                .MAX_TITLES_PER_POOL,
                        key
                );

                break;
            }

            if (titleElement == null
                    || !titleElement.isJsonPrimitive()) {

                continue;
            }

            JsonPrimitive primitive =
                    titleElement.getAsJsonPrimitive();

            if (!primitive.isString()) {
                continue;
            }

            String safeTitle =
                    RecognitionDisplayNameService
                            .sanitizeTitle(
                                    primitive.getAsString()
                            );

            if (safeTitle.isBlank()) {
                continue;
            }

            titles.add(
                    safeTitle
            );
        }

        return List.copyOf(
                titles
        );
    }

    private static EnumMap<
            RecognitionPath,
            LinkedHashSet<String>
            > createEmptyPoolSets() {

        EnumMap<
                RecognitionPath,
                LinkedHashSet<String>
                > result =
                new EnumMap<>(
                        RecognitionPath.class
                );

        for (RecognitionPath path :
                RecognitionPath.values()) {

            result.put(
                    path,
                    new LinkedHashSet<>()
            );
        }

        return result;
    }

    private static void addTitles(
            LinkedHashSet<String> target,
            List<String> additions
    ) {
        if (target == null
                || additions == null
                || additions.isEmpty()) {

            return;
        }

        for (String title : additions) {
            if (target.size()
                    >= RecognitionTitlePoolManager
                    .MAX_TITLES_PER_POOL) {

                break;
            }

            String safeTitle =
                    RecognitionDisplayNameService
                            .sanitizeTitle(
                                    title
                            );

            if (!safeTitle.isBlank()) {
                target.add(
                        safeTitle
                );
            }
        }
    }

    /**
     * Prevents one title from appearing in multiple path or purity pools.
     *
     * The first occurrence in the deterministic path and resource order is
     * kept. Later case-insensitive duplicates are discarded.
     */
    private static int removeGlobalDuplicates(
            EnumMap<
                    RecognitionPath,
                    LinkedHashSet<String>
                    > standardTitles,
            EnumMap<
                    RecognitionPath,
                    LinkedHashSet<String>
                    > pureTitles
    ) {
        Set<String> globallySeen =
                new HashSet<>();

        int removed =
                0;

        for (RecognitionPath path :
                RecognitionPath.values()) {

            removed += removeDuplicates(
                    standardTitles.get(path),
                    globallySeen
            );

            removed += removeDuplicates(
                    pureTitles.get(path),
                    globallySeen
            );
        }

        return removed;
    }

    private static int removeDuplicates(
            LinkedHashSet<String> titles,
            Set<String> globallySeen
    ) {
        if (titles == null || titles.isEmpty()) {
            return 0;
        }

        int removed =
                0;

        Iterator<String> iterator =
                titles.iterator();

        while (iterator.hasNext()) {
            String title =
                    iterator.next();

            String normalized =
                    title.toLowerCase(
                            Locale.ROOT
                    );

            if (!globallySeen.add(
                    normalized
            )) {
                iterator.remove();
                removed++;
            }
        }

        return removed;
    }

    private static EnumMap<RecognitionPath, List<String>>
    freezePools(
            EnumMap<
                    RecognitionPath,
                    LinkedHashSet<String>
                    > source
    ) {
        EnumMap<RecognitionPath, List<String>> result =
                new EnumMap<>(
                        RecognitionPath.class
                );

        for (RecognitionPath path :
                RecognitionPath.values()) {

            LinkedHashSet<String> titles =
                    source.get(path);

            if (titles == null || titles.isEmpty()) {
                result.put(
                        path,
                        List.of()
                );
            } else {
                result.put(
                        path,
                        List.copyOf(
                                titles
                        )
                );
            }
        }

        return result;
    }

    private record ParsedTitleFile(
            RecognitionPath path,
            List<String> standardTitles,
            List<String> pureTitles
    ) {

        private ParsedTitleFile {
            standardTitles =
                    standardTitles == null
                            ? List.of()
                            : List.copyOf(
                            standardTitles
                    );

            pureTitles =
                    pureTitles == null
                            ? List.of()
                            : List.copyOf(
                            pureTitles
                    );
        }
    }
}