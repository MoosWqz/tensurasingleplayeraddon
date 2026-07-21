package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment
        .AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment
        .RecognitionData;
import com.mooswqz.moostensuraaddon.debug.DebugModeService;
import com.mooswqz.moostensuraaddon.network
        .OpenRecognitionProgressScreenPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class RecognitionProgressScreenService {

    /*
     * Normal UI protection:
     *
     * The client disables its Refresh button for two seconds, but the client
     * must never be trusted as the only protection. These server-side limits
     * are therefore applied to both the command and the network request.
     */
    private static final long MIN_RESPONSE_INTERVAL_NANOS =
            TimeUnit.MILLISECONDS.toNanos(750L);

    /*
     * The expensive synchronization and recognition evaluation can run at
     * most once per player during this interval. Repeated accepted requests
     * receive the already sanitized cached payload.
     */
    private static final long PAYLOAD_CACHE_NANOS =
            TimeUnit.SECONDS.toNanos(2L);

    /*
     * Hard per-player burst ceiling. A modified client can bypass the local
     * button cooldown, but it cannot make this service answer indefinitely.
     */
    private static final long REQUEST_WINDOW_NANOS =
            TimeUnit.SECONDS.toNanos(10L);

    private static final int MAX_REQUESTS_PER_WINDOW =
            8;

    private static final long FEEDBACK_INTERVAL_NANOS =
            TimeUnit.SECONDS.toNanos(2L);

    /*
     * A second, server-wide budget protects against many different players
     * requesting an uncached rebuild at the same time. Cached snapshots do
     * not consume this budget.
     */
    private static final long GLOBAL_BUILD_WINDOW_NANOS =
            TimeUnit.SECONDS.toNanos(1L);

    private static final int MAX_GLOBAL_FRESH_BUILDS_PER_WINDOW =
            16;

    private static final long STATE_EXPIRY_NANOS =
            TimeUnit.MINUTES.toNanos(10L);

    private static final long CLEANUP_INTERVAL_NANOS =
            TimeUnit.MINUTES.toNanos(1L);

    private static final Map<UUID, RequestState>
            REQUEST_STATES =
            new ConcurrentHashMap<>();

    private static final Object GLOBAL_BUILD_LOCK =
            new Object();

    private static volatile long nextCleanupNanos;

    private static long globalBuildWindowStartedNanos;
    private static int globalFreshBuildsInWindow;

    private RecognitionProgressScreenService() {
    }

    /**
     * Opens the screen from /moostensura paths.
     *
     * This route uses the same cache and server-side rate limiter as the
     * Refresh button, so command spam cannot repeatedly recalculate the
     * recognition graph.
     */
    public static void open(
            ServerPlayer player
    ) {
        requestOpen(
                player,
                RequestOrigin.COMMAND
        );
    }

    /**
     * Handles the empty client-to-server Refresh request.
     */
    public static void requestRefresh(
            ServerPlayer player
    ) {
        requestOpen(
                player,
                RequestOrigin.CLIENT_REFRESH
        );
    }

    /**
     * Invalidates only the cached snapshot for this player.
     *
     * Naming, unnaming, progression reset and similar server-authoritative
     * actions can call this method when they need the very next accepted
     * request to rebuild immediately.
     */
    public static void invalidate(
            ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        invalidate(
                player.getUUID()
        );
    }

    public static void invalidate(
            UUID playerUuid
    ) {
        if (playerUuid == null) {
            return;
        }

        RequestState state =
                REQUEST_STATES.get(playerUuid);

        if (state == null) {
            return;
        }

        synchronized (state) {
            state.cachedPayload = null;
            state.cacheExpiresNanos = 0L;
        }
    }

    public static void clear(
            UUID playerUuid
    ) {
        if (playerUuid == null) {
            return;
        }

        REQUEST_STATES.remove(playerUuid);
    }

    public static void clearAll() {
        REQUEST_STATES.clear();
    }

    private static void requestOpen(
            ServerPlayer player,
            RequestOrigin origin
    ) {
        if (player == null
                || player.isRemoved()) {
            return;
        }

        long now = System.nanoTime();

        cleanupExpiredStates(now);

        RequestState state =
                REQUEST_STATES.computeIfAbsent(
                        player.getUUID(),
                        ignored -> new RequestState(now)
                );

        OpenRecognitionProgressScreenPayload payload =
                null;

        boolean sendCooldownFeedback = false;

        synchronized (state) {
            state.lastActivityNanos = now;

            if (now - state.windowStartedNanos
                    >= REQUEST_WINDOW_NANOS) {
                state.windowStartedNanos = now;
                state.requestsInWindow = 0;
            }

            if (state.requestsInWindow
                    < Integer.MAX_VALUE) {
                state.requestsInWindow++;
            }

            boolean hardLimited =
                    state.requestsInWindow
                            > MAX_REQUESTS_PER_WINDOW;

            boolean responseTooSoon =
                    state.lastResponseNanos != 0L
                            && now - state.lastResponseNanos
                            < MIN_RESPONSE_INTERVAL_NANOS;

            if (hardLimited || responseTooSoon) {
                if (origin == RequestOrigin.COMMAND
                        && now - state.lastFeedbackNanos
                        >= FEEDBACK_INTERVAL_NANOS) {

                    state.lastFeedbackNanos = now;
                    sendCooldownFeedback = true;
                }
            } else {
                boolean debugDetailsAvailable =
                        DebugModeService.canUseDebugTools(
                                player.createCommandSourceStack()
                        );

                boolean cacheValid =
                        state.cachedPayload != null
                                && now
                                < state.cacheExpiresNanos
                                && state.cachedDebugDetails
                                == debugDetailsAvailable;

                if (cacheValid) {
                    payload = state.cachedPayload;
                } else if (tryReserveFreshBuild(now)) {
                    payload = buildPayload(
                            player,
                            debugDetailsAvailable
                    );

                    state.cachedPayload = payload;
                    state.cachedDebugDetails =
                            debugDetailsAvailable;
                    state.cacheExpiresNanos =
                            now + PAYLOAD_CACHE_NANOS;
                } else if (state.cachedPayload != null
                        && state.cachedDebugDetails
                        == debugDetailsAvailable) {
                    /*
                     * Under a short global burst, an older sanitized snapshot
                     * is safer than another expensive rebuild. The next
                     * accepted request can refresh it when the global budget
                     * is available again.
                     */
                    payload = state.cachedPayload;
                } else if (origin == RequestOrigin.COMMAND
                        && now - state.lastFeedbackNanos
                        >= FEEDBACK_INTERVAL_NANOS) {

                    state.lastFeedbackNanos = now;
                    sendCooldownFeedback = true;
                }

                if (payload != null) {
                    state.lastResponseNanos = now;
                }
            }
        }

        if (sendCooldownFeedback) {
            player.sendSystemMessage(
                    Component.literal(
                                    "Soul Recognition is cooling down. Please wait a moment."
                            )
                            .withStyle(
                                    ChatFormatting.GRAY
                            )
            );
        }

        if (payload == null) {
            return;
        }

        PacketDistributor.sendToPlayer(
                player,
                payload
        );
    }

    private static OpenRecognitionProgressScreenPayload
    buildPayload(
            ServerPlayer player,
            boolean debugDetailsAvailable
    ) {
        RecognitionNamingEligibility eligibility =
                RecognitionNamingService.evaluate(
                        player
                );

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        RecognitionEvaluation evaluation =
                eligibility.evaluation();

        RecognitionPathSelection liveSelection =
                evaluation.getSelection()
                        .orElse(null);

        boolean recognitionCommitted =
                data.isNamingCommitted();

        RecognitionPath primaryPath =
                resolvePrimaryPath(
                        data,
                        liveSelection,
                        recognitionCommitted
                );

        RecognitionPath secondaryPath =
                resolveSecondaryPath(
                        data,
                        liveSelection,
                        recognitionCommitted
                );

        boolean pureRecognition =
                resolvePureRecognition(
                        data,
                        liveSelection,
                        recognitionCommitted
                );

        int recognitionColor =
                primaryPath == null
                        ? 0xFFFFFF
                        : RecognitionNameColorService
                        .getRecognitionColor(
                                primaryPath,
                                secondaryPath,
                                pureRecognition
                        );

        String accountName =
                player.getGameProfile()
                        .getName();

        String identityLine =
                buildIdentityLine(
                        accountName,
                        eligibility.nativeName()
                );

        String pathSummary =
                buildPathSummary(
                        primaryPath,
                        secondaryPath,
                        pureRecognition
                );

        List<OpenRecognitionProgressScreenPayload.PathEntry>
                pathEntries =
                buildPathEntries(
                        evaluation,
                        primaryPath,
                        secondaryPath,
                        debugDetailsAvailable
                );

        List<OpenRecognitionProgressScreenPayload.GuidanceEntry>
                guidanceEntries =
                buildGuidanceEntries(
                        data,
                        evaluation,
                        debugDetailsAvailable
                );

        String statusHeading =
                buildStatusHeading(
                        eligibility,
                        data
                );

        String statusDetail =
                buildStatusDetail(
                        eligibility,
                        data,
                        pathSummary
                );

        String levelLine =
                buildLevelLine(eligibility);

        return new OpenRecognitionProgressScreenPayload(
                accountName,
                identityLine,
                recognitionCommitted
                        ? data.getBestowedTitle()
                        : "",
                pathSummary,
                eligibility.status().getId(),
                statusHeading,
                statusDetail,
                levelLine,
                eligibility.currentLevel(),
                eligibility.requiredLevel(),
                eligibility.nativeNamed(),
                recognitionCommitted,
                data.isRevealPending(),
                pureRecognition,
                pathId(primaryPath),
                pathId(secondaryPath),
                recognitionColor,
                primaryPath != null
                        && pureRecognition,
                debugDetailsAvailable,
                debugDetailsAvailable
                        ? RecognitionPathEvaluator
                          .DEFAULT_ESTABLISHED_THRESHOLD
                        : 0.0D,
                debugDetailsAvailable
                        ? RecognitionPathEvaluator
                          .DEFAULT_PURE_THRESHOLD
                        : 0.0D,
                debugDetailsAvailable
                        ? RecognitionPathEvaluator
                          .DEFAULT_RAW_PURE_THRESHOLD
                        : 0.0D,
                pathEntries,
                guidanceEntries
        );
    }

    private static RecognitionPath resolvePrimaryPath(
            RecognitionData data,
            RecognitionPathSelection liveSelection,
            boolean recognitionCommitted
    ) {
        if (recognitionCommitted) {
            RecognitionPath committedPrimary =
                    data.getCommittedPrimaryPath()
                            .orElse(null);

            if (committedPrimary != null) {
                return committedPrimary;
            }
        }

        return liveSelection == null
                ? null
                : liveSelection.primaryPath();
    }

    private static RecognitionPath resolveSecondaryPath(
            RecognitionData data,
            RecognitionPathSelection liveSelection,
            boolean recognitionCommitted
    ) {
        if (recognitionCommitted) {
            return data.getCommittedSecondaryPath()
                    .orElse(null);
        }

        if (liveSelection == null
                || liveSelection.pure()) {
            return null;
        }

        return liveSelection.secondaryPath();
    }

    private static boolean resolvePureRecognition(
            RecognitionData data,
            RecognitionPathSelection liveSelection,
            boolean recognitionCommitted
    ) {
        if (recognitionCommitted) {
            return data.isPureRecognition();
        }

        return liveSelection != null
                && liveSelection.pure();
    }

    private static String buildIdentityLine(
            String accountName,
            String nativeName
    ) {
        if (nativeName != null
                && !nativeName.isBlank()) {
            return nativeName.trim();
        }

        return accountName == null
                ? ""
                : accountName.trim();
    }

    private static String buildPathSummary(
            RecognitionPath primaryPath,
            RecognitionPath secondaryPath,
            boolean pureRecognition
    ) {
        if (primaryPath == null) {
            return "No dominant path yet";
        }

        String primaryName =
                RecognitionNameColorService
                        .getDisplayName(primaryPath);

        if (pureRecognition) {
            return "Pure " + primaryName;
        }

        if (secondaryPath == null) {
            return primaryName;
        }

        return primaryName
                + " / "
                + RecognitionNameColorService
                .getDisplayName(secondaryPath);
    }

    private static List<
            OpenRecognitionProgressScreenPayload.PathEntry
            > buildPathEntries(
            RecognitionEvaluation evaluation,
            RecognitionPath primaryPath,
            RecognitionPath secondaryPath,
            boolean debugDetailsAvailable
    ) {
        List<OpenRecognitionProgressScreenPayload.PathEntry>
                entries =
                new ArrayList<>(
                        RecognitionPath.values().length
                );

        for (RecognitionPath path :
                RecognitionPath.values()) {

            double finalScore =
                    evaluation.getPathScore(path);

            double rawScore =
                    evaluation.getRawPathScore(path);

            double identityBoost =
                    evaluation.getIdentityBoost(path);

            entries.add(
                    new OpenRecognitionProgressScreenPayload
                            .PathEntry(
                            path.getId(),
                            RecognitionNameColorService
                                    .getDisplayName(path),
                            getPathStageLabel(finalScore),
                            getPathProgress(finalScore),
                            RecognitionNameColorService
                                    .getBaseColor(path),
                            path == primaryPath,
                            path == secondaryPath,
                            debugDetailsAvailable
                                    ? finalScore
                                    : 0.0D,
                            debugDetailsAvailable
                                    ? rawScore
                                    : 0.0D,
                            debugDetailsAvailable
                                    ? identityBoost
                                    : 0.0D
                    )
            );
        }

        return List.copyOf(entries);
    }

    private static List<
            OpenRecognitionProgressScreenPayload.GuidanceEntry
            > buildGuidanceEntries(
            RecognitionData data,
            RecognitionEvaluation evaluation,
            boolean debugDetailsAvailable
    ) {
        RecognitionDimensions dimensions =
                evaluation.getDimensions();

        RecognitionPathComponents components =
                evaluation.getComponents();

        int recognizedSubordinates =
                Math.max(
                        data.getCounter(
                                RecognitionStatKeys
                                        .CURRENT_SUBORDINATES
                        ),
                        data.getCounter(
                                RecognitionStatKeys
                                        .HIGHEST_SUBORDINATES
                        )
                );

        double companionship =
                Math.min(
                        30.0D,
                        recognizedSubordinates * 1.5D
                )
                        + Math.min(
                        20.0D,
                        data.getUniqueValueCount(
                                RecognitionStatKeys
                                        .UNIQUE_SUBORDINATES_EMPOWERED
                        ) * 2.0D
                )
                        + Math.min(
                        20.0D,
                        data.getCounter(
                                RecognitionStatKeys
                                        .SUBORDINATE_ASSISTED_MAJOR_VICTORIES
                        ) * 4.0D
                );

        double exploration =
                dimensions.discovery()
                        + dimensions.freedom() * 0.5D;

        List<OpenRecognitionProgressScreenPayload.GuidanceEntry>
                entries =
                new ArrayList<>(7);

        addGuidanceEntry(
                entries,
                "protection",
                "Protection",
                "Defending civilians and overcoming malevolent threats shape this aspect.",
                dimensions.good(),
                70.0D,
                0x71E0B8,
                debugDetailsAvailable
        );

        addGuidanceEntry(
                entries,
                "authority",
                "Authority",
                "Leadership, hierarchy and influence over subordinates strengthen this aspect.",
                dimensions.order(),
                70.0D,
                0x73D66E,
                debugDetailsAvailable
        );

        addGuidanceEntry(
                entries,
                "balance",
                "Balance",
                "Active balance between authority and independence forms Neutral behaviour. Quiet posture is only an early hint and cannot establish recognition by itself.",
                components.displayNeutralBehaviour(),
                evaluation.getBalance()
                        .neutrality()
                        .behaviour()
                        .maximum(),
                0x5DD9E8,
                debugDetailsAvailable
        );

        addGuidanceEntry(
                entries,
                "mastery",
                "Mastery",
                "Mastered skills, broad experience and powerful victories deepen this aspect.",
                dimensions.mastery(),
                80.0D,
                0x5DD9E8,
                debugDetailsAvailable
        );

        addGuidanceEntry(
                entries,
                "exploration",
                "Exploration",
                "Discovery, independence and meaningful journeys develop this aspect.",
                exploration,
                50.0D,
                0x7F86FF,
                debugDetailsAvailable
        );

        addGuidanceEntry(
                entries,
                "companionship",
                "Companionship",
                "Lasting bonds, empowered subordinates and shared victories shape this aspect.",
                companionship,
                70.0D,
                0xBFF57A,
                debugDetailsAvailable
        );

        addGuidanceEntry(
                entries,
                "cruelty",
                "Cruelty",
                "Harm against innocents, companions and benevolent beings strengthens this aspect.",
                dimensions.evil(),
                70.0D,
                0xE05276,
                debugDetailsAvailable
        );

        return List.copyOf(entries);
    }

    private static void addGuidanceEntry(
            List<OpenRecognitionProgressScreenPayload.GuidanceEntry>
                    entries,
            String categoryId,
            String displayName,
            String guidanceText,
            double value,
            double definingValue,
            int color,
            boolean debugDetailsAvailable
    ) {
        double safeValue =
                sanitizeScore(value);

        double progress =
                definingValue <= 0.0D
                        ? 0.0D
                        : Math.min(
                        1.0D,
                        safeValue / definingValue
                );

        entries.add(
                new OpenRecognitionProgressScreenPayload
                        .GuidanceEntry(
                        categoryId,
                        displayName,
                        getGuidanceStageLabel(progress),
                        guidanceText,
                        progress,
                        color,
                        debugDetailsAvailable
                                ? safeValue
                                : 0.0D
                )
        );
    }

    private static double getPathProgress(
            double finalScore
    ) {
        double safeScore =
                sanitizeScore(finalScore);

        return Math.min(
                1.0D,
                safeScore
                        / RecognitionPathEvaluator
                        .DEFAULT_PURE_THRESHOLD
        );
    }

    private static String getPathStageLabel(
            double finalScore
    ) {
        double safeScore =
                sanitizeScore(finalScore);

        if (safeScore < 0.5D) {
            return "Dormant";
        }

        if (safeScore < 12.0D) {
            return "Faint";
        }

        if (safeScore
                < RecognitionPathEvaluator
                .DEFAULT_ESTABLISHED_THRESHOLD) {
            return "Developing";
        }

        if (safeScore
                < RecognitionPathEvaluator
                .DEFAULT_PURE_THRESHOLD) {
            return "Established";
        }

        return "Dominant";
    }

    private static String getGuidanceStageLabel(
            double progress
    ) {
        double safeProgress =
                Math.max(
                        0.0D,
                        Math.min(
                                1.0D,
                                progress
                        )
                );

        if (safeProgress < 0.02D) {
            return "Dormant";
        }

        if (safeProgress < 0.20D) {
            return "Faint";
        }

        if (safeProgress < 0.50D) {
            return "Developing";
        }

        if (safeProgress < 0.80D) {
            return "Strong";
        }

        return "Defining";
    }

    private static String buildStatusHeading(
            RecognitionNamingEligibility eligibility,
            RecognitionData data
    ) {
        if (data.isRevealPending()) {
            return "Your recognition awaits revelation.";
        }

        return switch (eligibility.status()) {
            case READY ->
                    "Your soul is ready to be recognized.";

            case ALREADY_COMMITTED ->
                    "This incarnation has been recognized.";

            case ALREADY_NAMED ->
                    "Tensura already recognizes another name.";

            case NOT_ENOUGH_LEVEL ->
                    "Your identity is still maturing.";

            case NO_RECOGNITION_SELECTION ->
                    "No recognition path has fully formed.";
        };
    }

    private static String buildStatusDetail(
            RecognitionNamingEligibility eligibility,
            RecognitionData data,
            String pathSummary
    ) {
        if (data.isRevealPending()) {
            return "Return to the Great Crystal Altar to complete the revelation.";
        }

        return switch (eligibility.status()) {
            case READY ->
                    "Seek a Great Crystal Altar.";

            case ALREADY_COMMITTED ->
                    data.getBestowedTitle().isBlank()
                            ? pathSummary
                            : "Recognition: "
                              + data.getBestowedTitle();

            case ALREADY_NAMED ->
                    "Recognition naming cannot begin while a native name is active.";

            case NOT_ENOUGH_LEVEL ->
                    "Continue developing this incarnation before seeking the altar.";

            case NO_RECOGNITION_SELECTION ->
                    "Continue acting according to your convictions.";
        };
    }

    private static String buildLevelLine(
            RecognitionNamingEligibility eligibility
    ) {
        String state =
                eligibility.currentLevel()
                        >= eligibility.requiredLevel()
                        ? "Ready"
                        : "Developing";

        return "Life experience: "
                + eligibility.currentLevel()
                + " / "
                + eligibility.requiredLevel()
                + "  •  "
                + state;
    }

    private static String pathId(
            RecognitionPath path
    ) {
        return path == null
                ? ""
                : path.getId();
    }

    private static double sanitizeScore(
            double value
    ) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return 0.0D;
        }

        return value;
    }

    private static boolean tryReserveFreshBuild(
            long now
    ) {
        synchronized (GLOBAL_BUILD_LOCK) {
            if (globalBuildWindowStartedNanos == 0L
                    || now - globalBuildWindowStartedNanos
                    >= GLOBAL_BUILD_WINDOW_NANOS) {

                globalBuildWindowStartedNanos = now;
                globalFreshBuildsInWindow = 0;
            }

            if (globalFreshBuildsInWindow
                    >= MAX_GLOBAL_FRESH_BUILDS_PER_WINDOW) {
                return false;
            }

            globalFreshBuildsInWindow++;
            return true;
        }
    }

    private static void cleanupExpiredStates(
            long now
    ) {
        if (now < nextCleanupNanos) {
            return;
        }

        nextCleanupNanos =
                now + CLEANUP_INTERVAL_NANOS;

        REQUEST_STATES.entrySet()
                .removeIf(entry -> {
                    RequestState state = entry.getValue();

                    return state == null
                            || now - state.lastActivityNanos
                            >= STATE_EXPIRY_NANOS;
                });
    }

    private enum RequestOrigin {
        COMMAND,
        CLIENT_REFRESH
    }

    private static final class RequestState {

        private long windowStartedNanos;
        private int requestsInWindow;

        private long lastResponseNanos;
        private long lastFeedbackNanos;
        private long lastActivityNanos;

        private long cacheExpiresNanos;
        private boolean cachedDebugDetails;

        private OpenRecognitionProgressScreenPayload
                cachedPayload;

        private RequestState(
                long now
        ) {
            this.windowStartedNanos = now;
            this.lastActivityNanos = now;
        }
    }
}