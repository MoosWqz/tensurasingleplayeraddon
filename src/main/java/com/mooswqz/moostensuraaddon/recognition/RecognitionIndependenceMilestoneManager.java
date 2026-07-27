package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, reloadable registry of meaningful self-reliance milestones.
 *
 * <p>The manager does no ticking, world scanning or advancement enumeration.
 * Runtime scoring only checks the semantic milestone IDs already stored in
 * {@link RecognitionData}. Definitions are installed atomically after a
 * datapack reload.</p>
 */
public final class RecognitionIndependenceMilestoneManager {

    public static final int MAX_MILESTONES =
            256;

    public static final double MAX_POINTS_PER_MILESTONE =
            100.0D;

    public static final double MAXIMUM_TOTAL_SCORE =
            100.0D;

    private static final Object INSTALL_LOCK =
            new Object();

    private static volatile State state =
            State.empty();

    private RecognitionIndependenceMilestoneManager() {
    }

    public static State getState() {
        return state;
    }

    public static long getRevision() {
        return state.revision();
    }

    public static String getFingerprint() {
        return state.fingerprint();
    }

    /**
     * Calculates the active Freedom contribution from persisted semantic IDs.
     */
    public static double calculateScore(
            RecognitionData data
    ) {
        if (data == null) {
            return 0.0D;
        }

        double score =
                0.0D;

        for (Milestone milestone :
                state.milestones()) {

            if (data.containsUniqueValue(
                    RecognitionStatKeys
                            .INDEPENDENCE_MILESTONES,
                    milestone.id().toString()
            )) {
                score += milestone.points();
            }
        }

        if (!Double.isFinite(score)
                || score <= 0.0D) {
            return 0.0D;
        }

        return Math.min(
                MAXIMUM_TOTAL_SCORE,
                score
        );
    }

    /**
     * Records a configured advancement as one permanent semantic milestone.
     */
    public static boolean recordEarned(
            RecognitionData data,
            ResourceLocation advancementId
    ) {
        if (data == null
                || advancementId == null) {
            return false;
        }

        Milestone milestone =
                state.byAdvancement()
                        .get(advancementId);

        if (milestone == null) {
            return false;
        }

        return data.addUniqueValue(
                RecognitionStatKeys
                        .INDEPENDENCE_MILESTONES,
                milestone.id().toString()
        );
    }

    static State install(
            List<Milestone> rawMilestones,
            int sourceFileCount
    ) {
        List<Milestone> milestones =
                sanitizeMilestones(
                        rawMilestones
                );

        String fingerprint =
                createFingerprint(
                        milestones
                );

        synchronized (INSTALL_LOCK) {
            long nextRevision =
                    state.revision()
                            + 1L;

            State next =
                    State.create(
                            nextRevision,
                            fingerprint,
                            milestones,
                            sourceFileCount
                    );

            state = next;

            RecognitionProgressScreenService
                    .clearAll();

            return next;
        }
    }

    private static List<Milestone> sanitizeMilestones(
            List<Milestone> rawMilestones
    ) {
        if (rawMilestones == null
                || rawMilestones.isEmpty()) {
            return List.of();
        }

        if (rawMilestones.size()
                > MAX_MILESTONES) {
            throw new IllegalArgumentException(
                    "At most "
                            + MAX_MILESTONES
                            + " independence milestones may be installed."
            );
        }

        List<Milestone> sorted =
                new ArrayList<>(
                        rawMilestones
                );

        sorted.sort(
                Comparator.comparing(
                        milestone ->
                                milestone.id()
                                        .toString()
                )
        );

        Map<ResourceLocation, Milestone> byId =
                new LinkedHashMap<>();

        Map<ResourceLocation, Milestone> byAdvancement =
                new LinkedHashMap<>();

        double total =
                0.0D;

        for (Milestone milestone : sorted) {
            if (milestone == null) {
                throw new IllegalArgumentException(
                        "Independence milestone definitions cannot contain null entries."
                );
            }

            if (byId.putIfAbsent(
                    milestone.id(),
                    milestone
            ) != null) {
                throw new IllegalArgumentException(
                        "Duplicate independence milestone ID: "
                                + milestone.id()
                );
            }

            if (byAdvancement.putIfAbsent(
                    milestone.advancementId(),
                    milestone
            ) != null) {
                throw new IllegalArgumentException(
                        "Duplicate independence advancement ID: "
                                + milestone.advancementId()
                );
            }

            total += milestone.points();

            if (!Double.isFinite(total)
                    || total
                    > MAXIMUM_TOTAL_SCORE) {
                throw new IllegalArgumentException(
                        "The configured independence score exceeds the "
                                + MAXIMUM_TOTAL_SCORE
                                + " point safety maximum."
                );
            }
        }

        return List.copyOf(
                sorted
        );
    }

    private static String createFingerprint(
            List<Milestone> milestones
    ) {
        StringBuilder canonical =
                new StringBuilder();

        for (Milestone milestone : milestones) {
            canonical.append(
                            milestone.id()
                    )
                    .append('|')
                    .append(
                            milestone.advancementId()
                    )
                    .append('|')
                    .append(
                            Double.toHexString(
                                    milestone.points()
                            )
                    )
                    .append('\n');
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            canonical.toString()
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );

            StringBuilder result =
                    new StringBuilder(
                            hash.length * 2
                    );

            for (byte value : hash) {
                result.append(
                        String.format(
                                java.util.Locale.ROOT,
                                "%02x",
                                value & 0xFF
                        )
                );
            }

            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable for independence definition fingerprints.",
                    exception
            );
        }
    }

    public record Milestone(
            ResourceLocation id,
            ResourceLocation advancementId,
            double points
    ) {

        public Milestone {
            if (id == null) {
                throw new IllegalArgumentException(
                        "An independence milestone requires a semantic ID."
                );
            }

            if (advancementId == null) {
                throw new IllegalArgumentException(
                        "An independence milestone requires an advancement ID."
                );
            }

            if (!Double.isFinite(points)
                    || points <= 0.0D
                    || points
                    > MAX_POINTS_PER_MILESTONE) {
                throw new IllegalArgumentException(
                        "Independence milestone points must be finite, positive and at most "
                                + MAX_POINTS_PER_MILESTONE
                                + "."
                );
            }
        }
    }

    public record State(
            long revision,
            String fingerprint,
            List<Milestone> milestones,
            Map<ResourceLocation, Milestone> byId,
            Map<ResourceLocation, Milestone> byAdvancement,
            int sourceFileCount,
            double maximumScore
    ) {

        public State {
            revision =
                    Math.max(
                            0L,
                            revision
                    );

            fingerprint =
                    fingerprint == null
                            ? ""
                            : fingerprint;

            milestones =
                    milestones == null
                            ? List.of()
                            : List.copyOf(
                            milestones
                    );

            byId =
                    byId == null
                            ? Map.of()
                            : Map.copyOf(
                            byId
                    );

            byAdvancement =
                    byAdvancement == null
                            ? Map.of()
                            : Map.copyOf(
                            byAdvancement
                    );

            sourceFileCount =
                    Math.max(
                            0,
                            sourceFileCount
                    );

            maximumScore =
                    !Double.isFinite(
                            maximumScore
                    ) || maximumScore < 0.0D
                            ? 0.0D
                            : maximumScore;
        }

        private static State create(
                long revision,
                String fingerprint,
                List<Milestone> milestones,
                int sourceFileCount
        ) {
            Map<ResourceLocation, Milestone> byId =
                    new LinkedHashMap<>();

            Map<ResourceLocation, Milestone> byAdvancement =
                    new LinkedHashMap<>();

            double maximumScore =
                    0.0D;

            for (Milestone milestone : milestones) {
                byId.put(
                        milestone.id(),
                        milestone
                );

                byAdvancement.put(
                        milestone.advancementId(),
                        milestone
                );

                maximumScore +=
                        milestone.points();
            }

            return new State(
                    revision,
                    fingerprint,
                    milestones,
                    byId,
                    byAdvancement,
                    sourceFileCount,
                    maximumScore
            );
        }

        private static State empty() {
            List<Milestone> milestones =
                    List.of();

            return create(
                    0L,
                    createFingerprint(
                            milestones
                    ),
                    milestones,
                    0
            );
        }
    }
}