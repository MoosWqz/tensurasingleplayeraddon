package com.mooswqz.moostensuraaddon.network;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom
        .CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record OpenRecognitionProgressScreenPayload(
        String accountName,
        String identityLine,
        String bestowedTitle,
        String pathSummary,
        String eligibilityStatusId,
        String statusHeading,
        String statusDetail,
        String levelLine,
        int currentLevel,
        int requiredLevel,
        boolean nativeNamed,
        boolean recognitionCommitted,
        boolean revealPending,
        boolean pureRecognition,
        String primaryPathId,
        String secondaryPathId,
        int recognitionColor,
        boolean recognitionBold,
        boolean debugDetailsAvailable,
        double establishedThreshold,
        double pureThreshold,
        double rawPureThreshold,
        List<PathEntry> paths,
        List<GuidanceEntry> guidanceEntries
) implements CustomPacketPayload {

    private static final int MAX_TEXT_LENGTH = 256;
    private static final int MAX_PATH_ENTRIES = 9;
    private static final int MAX_GUIDANCE_ENTRIES = 6;

    public static final CustomPacketPayload.Type<
            OpenRecognitionProgressScreenPayload
            > TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MoosTensuraAddon.MODID,
                            "open_recognition_progress_screen"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenRecognitionProgressScreenPayload
            > STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenRecognitionProgressScreenPayload decode(
                        RegistryFriendlyByteBuf buffer
                ) {
                    String accountName =
                            readText(buffer);

                    String identityLine =
                            readText(buffer);

                    String bestowedTitle =
                            readText(buffer);

                    String pathSummary =
                            readText(buffer);

                    String eligibilityStatusId =
                            readText(buffer);

                    String statusHeading =
                            readText(buffer);

                    String statusDetail =
                            readText(buffer);

                    String levelLine =
                            readText(buffer);

                    int currentLevel =
                            buffer.readVarInt();

                    int requiredLevel =
                            buffer.readVarInt();

                    boolean nativeNamed =
                            buffer.readBoolean();

                    boolean recognitionCommitted =
                            buffer.readBoolean();

                    boolean revealPending =
                            buffer.readBoolean();

                    boolean pureRecognition =
                            buffer.readBoolean();

                    String primaryPathId =
                            readText(buffer);

                    String secondaryPathId =
                            readText(buffer);

                    int recognitionColor =
                            buffer.readInt();

                    boolean recognitionBold =
                            buffer.readBoolean();

                    boolean debugDetailsAvailable =
                            buffer.readBoolean();

                    double establishedThreshold =
                            buffer.readDouble();

                    double pureThreshold =
                            buffer.readDouble();

                    double rawPureThreshold =
                            buffer.readDouble();

                    int pathCount =
                            readBoundedCount(
                                    buffer,
                                    MAX_PATH_ENTRIES,
                                    "recognition path"
                            );

                    List<PathEntry> paths =
                            new ArrayList<>(pathCount);

                    for (int index = 0;
                         index < pathCount;
                         index++) {

                        paths.add(
                                PathEntry.decode(buffer)
                        );
                    }

                    int guidanceCount =
                            readBoundedCount(
                                    buffer,
                                    MAX_GUIDANCE_ENTRIES,
                                    "recognition guidance"
                            );

                    List<GuidanceEntry> guidanceEntries =
                            new ArrayList<>(guidanceCount);

                    for (int index = 0;
                         index < guidanceCount;
                         index++) {

                        guidanceEntries.add(
                                GuidanceEntry.decode(buffer)
                        );
                    }

                    return new OpenRecognitionProgressScreenPayload(
                            accountName,
                            identityLine,
                            bestowedTitle,
                            pathSummary,
                            eligibilityStatusId,
                            statusHeading,
                            statusDetail,
                            levelLine,
                            currentLevel,
                            requiredLevel,
                            nativeNamed,
                            recognitionCommitted,
                            revealPending,
                            pureRecognition,
                            primaryPathId,
                            secondaryPathId,
                            recognitionColor,
                            recognitionBold,
                            debugDetailsAvailable,
                            establishedThreshold,
                            pureThreshold,
                            rawPureThreshold,
                            paths,
                            guidanceEntries
                    );
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        OpenRecognitionProgressScreenPayload payload
                ) {
                    writeText(
                            buffer,
                            payload.accountName()
                    );

                    writeText(
                            buffer,
                            payload.identityLine()
                    );

                    writeText(
                            buffer,
                            payload.bestowedTitle()
                    );

                    writeText(
                            buffer,
                            payload.pathSummary()
                    );

                    writeText(
                            buffer,
                            payload.eligibilityStatusId()
                    );

                    writeText(
                            buffer,
                            payload.statusHeading()
                    );

                    writeText(
                            buffer,
                            payload.statusDetail()
                    );

                    writeText(
                            buffer,
                            payload.levelLine()
                    );

                    buffer.writeVarInt(
                            payload.currentLevel()
                    );

                    buffer.writeVarInt(
                            payload.requiredLevel()
                    );

                    buffer.writeBoolean(
                            payload.nativeNamed()
                    );

                    buffer.writeBoolean(
                            payload.recognitionCommitted()
                    );

                    buffer.writeBoolean(
                            payload.revealPending()
                    );

                    buffer.writeBoolean(
                            payload.pureRecognition()
                    );

                    writeText(
                            buffer,
                            payload.primaryPathId()
                    );

                    writeText(
                            buffer,
                            payload.secondaryPathId()
                    );

                    buffer.writeInt(
                            payload.recognitionColor()
                    );

                    buffer.writeBoolean(
                            payload.recognitionBold()
                    );

                    buffer.writeBoolean(
                            payload.debugDetailsAvailable()
                    );

                    buffer.writeDouble(
                            payload.establishedThreshold()
                    );

                    buffer.writeDouble(
                            payload.pureThreshold()
                    );

                    buffer.writeDouble(
                            payload.rawPureThreshold()
                    );

                    buffer.writeVarInt(
                            payload.paths().size()
                    );

                    for (PathEntry path : payload.paths()) {
                        path.encode(buffer);
                    }

                    buffer.writeVarInt(
                            payload.guidanceEntries().size()
                    );

                    for (GuidanceEntry guidanceEntry :
                            payload.guidanceEntries()) {

                        guidanceEntry.encode(buffer);
                    }
                }
            };

    public OpenRecognitionProgressScreenPayload {
        accountName = sanitizeText(accountName);
        identityLine = sanitizeText(identityLine);
        bestowedTitle = sanitizeText(bestowedTitle);
        pathSummary = sanitizeText(pathSummary);
        eligibilityStatusId = sanitizeText(
                eligibilityStatusId
        );
        statusHeading = sanitizeText(statusHeading);
        statusDetail = sanitizeText(statusDetail);
        levelLine = sanitizeText(levelLine);

        currentLevel = Math.max(0, currentLevel);
        requiredLevel = Math.max(0, requiredLevel);
        recognitionColor &= 0xFFFFFF;

        primaryPathId = sanitizeText(primaryPathId);
        secondaryPathId = sanitizeText(
                secondaryPathId
        );

        establishedThreshold =
                sanitizeNonNegative(
                        establishedThreshold
                );

        pureThreshold =
                sanitizeNonNegative(
                        pureThreshold
                );

        rawPureThreshold =
                sanitizeNonNegative(
                        rawPureThreshold
                );

        if (!debugDetailsAvailable) {
            establishedThreshold = 0.0D;
            pureThreshold = 0.0D;
            rawPureThreshold = 0.0D;
        }

        paths = sanitizePaths(paths);
        guidanceEntries = sanitizeGuidanceEntries(
                guidanceEntries
        );

        if (!debugDetailsAvailable) {
            paths = stripPathDebugValues(paths);
            guidanceEntries = stripGuidanceDebugValues(
                    guidanceEntries
            );
        }
    }

    @Override
    public CustomPacketPayload.Type<
            ? extends CustomPacketPayload
            > type() {
        return TYPE;
    }

    public PathEntry findPath(
            String pathId
    ) {
        String safePathId = sanitizeText(pathId);

        for (PathEntry path : paths) {
            if (path.pathId().equals(safePathId)) {
                return path;
            }
        }

        return null;
    }

    private static List<PathEntry> stripPathDebugValues(
            List<PathEntry> paths
    ) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }

        List<PathEntry> stripped =
                new ArrayList<>(paths.size());

        for (PathEntry path : paths) {
            stripped.add(
                    new PathEntry(
                            path.pathId(),
                            path.displayName(),
                            path.stageLabel(),
                            path.progress(),
                            path.color(),
                            path.primary(),
                            path.secondary(),
                            0.0D,
                            0.0D,
                            0.0D
                    )
            );
        }

        return List.copyOf(stripped);
    }

    private static List<GuidanceEntry>
    stripGuidanceDebugValues(
            List<GuidanceEntry> entries
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<GuidanceEntry> stripped =
                new ArrayList<>(entries.size());

        for (GuidanceEntry entry : entries) {
            stripped.add(
                    new GuidanceEntry(
                            entry.categoryId(),
                            entry.displayName(),
                            entry.stageLabel(),
                            entry.guidanceText(),
                            entry.progress(),
                            entry.color(),
                            0.0D
                    )
            );
        }

        return List.copyOf(stripped);
    }

    private static List<PathEntry> sanitizePaths(
            List<PathEntry> rawPaths
    ) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return List.of();
        }

        List<PathEntry> safePaths =
                new ArrayList<>(
                        Math.min(
                                MAX_PATH_ENTRIES,
                                rawPaths.size()
                        )
                );

        for (PathEntry path : rawPaths) {
            if (path == null) {
                continue;
            }

            safePaths.add(path);

            if (safePaths.size()
                    >= MAX_PATH_ENTRIES) {
                break;
            }
        }

        return List.copyOf(safePaths);
    }

    private static List<GuidanceEntry>
    sanitizeGuidanceEntries(
            List<GuidanceEntry> rawEntries
    ) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return List.of();
        }

        List<GuidanceEntry> safeEntries =
                new ArrayList<>(
                        Math.min(
                                MAX_GUIDANCE_ENTRIES,
                                rawEntries.size()
                        )
                );

        for (GuidanceEntry entry : rawEntries) {
            if (entry == null) {
                continue;
            }

            safeEntries.add(entry);

            if (safeEntries.size()
                    >= MAX_GUIDANCE_ENTRIES) {
                break;
            }
        }

        return List.copyOf(safeEntries);
    }

    private static int readBoundedCount(
            RegistryFriendlyByteBuf buffer,
            int maximum,
            String label
    ) {
        int count = buffer.readVarInt();

        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Invalid "
                            + label
                            + " entry count: "
                            + count
            );
        }

        return count;
    }

    private static String readText(
            RegistryFriendlyByteBuf buffer
    ) {
        return buffer.readUtf(MAX_TEXT_LENGTH);
    }

    private static void writeText(
            RegistryFriendlyByteBuf buffer,
            String value
    ) {
        buffer.writeUtf(
                sanitizeText(value),
                MAX_TEXT_LENGTH
        );
    }

    private static String sanitizeText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder result =
                new StringBuilder();

        boolean previousWhitespace = false;

        for (int index = 0;
             index < value.length();
             index++) {

            char character = value.charAt(index);

            if (character == '\u00A7'
                    || Character.isISOControl(character)) {
                continue;
            }

            if (Character.isWhitespace(character)) {
                if (!previousWhitespace
                        && !result.isEmpty()) {
                    result.append(' ');
                }

                previousWhitespace = true;
                continue;
            }

            result.append(character);
            previousWhitespace = false;

            if (result.length() >= MAX_TEXT_LENGTH) {
                break;
            }
        }

        return result.toString().trim();
    }

    private static double sanitizeNonNegative(
            double value
    ) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return 0.0D;
        }

        return value;
    }

    private static double clampProgress(
            double value
    ) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(
                0.0D,
                Math.min(
                        1.0D,
                        value
                )
        );
    }

    public record PathEntry(
            String pathId,
            String displayName,
            String stageLabel,
            double progress,
            int color,
            boolean primary,
            boolean secondary,
            double finalScore,
            double rawScore,
            double identityBoost
    ) {

        public PathEntry {
            pathId = sanitizeText(pathId);
            displayName = sanitizeText(displayName);
            stageLabel = sanitizeText(stageLabel);

            progress = clampProgress(progress);
            color &= 0xFFFFFF;

            finalScore = sanitizeNonNegative(finalScore);
            rawScore = sanitizeNonNegative(rawScore);
            identityBoost = sanitizeNonNegative(
                    identityBoost
            );
        }

        private static PathEntry decode(
                RegistryFriendlyByteBuf buffer
        ) {
            return new PathEntry(
                    readText(buffer),
                    readText(buffer),
                    readText(buffer),
                    buffer.readDouble(),
                    buffer.readInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );
        }

        private void encode(
                RegistryFriendlyByteBuf buffer
        ) {
            writeText(buffer, pathId);
            writeText(buffer, displayName);
            writeText(buffer, stageLabel);
            buffer.writeDouble(progress);
            buffer.writeInt(color);
            buffer.writeBoolean(primary);
            buffer.writeBoolean(secondary);
            buffer.writeDouble(finalScore);
            buffer.writeDouble(rawScore);
            buffer.writeDouble(identityBoost);
        }
    }

    public record GuidanceEntry(
            String categoryId,
            String displayName,
            String stageLabel,
            String guidanceText,
            double progress,
            int color,
            double debugValue
    ) {

        public GuidanceEntry {
            categoryId = sanitizeText(categoryId);
            displayName = sanitizeText(displayName);
            stageLabel = sanitizeText(stageLabel);
            guidanceText = sanitizeText(guidanceText);

            progress = clampProgress(progress);
            color &= 0xFFFFFF;
            debugValue = sanitizeNonNegative(debugValue);
        }

        private static GuidanceEntry decode(
                RegistryFriendlyByteBuf buffer
        ) {
            return new GuidanceEntry(
                    readText(buffer),
                    readText(buffer),
                    readText(buffer),
                    readText(buffer),
                    buffer.readDouble(),
                    buffer.readInt(),
                    buffer.readDouble()
            );
        }

        private void encode(
                RegistryFriendlyByteBuf buffer
        ) {
            writeText(buffer, categoryId);
            writeText(buffer, displayName);
            writeText(buffer, stageLabel);
            writeText(buffer, guidanceText);
            buffer.writeDouble(progress);
            buffer.writeInt(color);
            buffer.writeDouble(debugValue);
        }
    }
}