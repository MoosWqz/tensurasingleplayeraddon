package com.mooswqz.moostensuraaddon.recognition;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Minecraft-independent recent-damage and duplicate-death ledger.
 *
 * <p>The production tracker owns one ledger per server. Keeping the state
 * machine independent makes the exact expiry, life-token and exactly-once
 * rules deterministic in the runtime validation harness.</p>
 */
final class RecognitionCombatCreditLedger {

    static final long CREDIT_WINDOW_TICKS = 20L * 10L;
    static final long DUPLICATE_WINDOW_TICKS = 20L * 10L;
    static final int MAX_RECENT_CREDITS = 8192;
    static final int MAX_PROCESSED_DEATHS = 8192;

    private final long creditWindowTicks;
    private final long duplicateWindowTicks;

    private final RecognitionRuntimeCapTable<VictimKey, CreditRecord>
            recentCredits;

    private final RecognitionRuntimeCapTable<VictimKey, ProcessedDeath>
            processedDeaths;

    RecognitionCombatCreditLedger() {
        this(
                MAX_RECENT_CREDITS,
                MAX_PROCESSED_DEATHS,
                CREDIT_WINDOW_TICKS,
                DUPLICATE_WINDOW_TICKS
        );
    }

    RecognitionCombatCreditLedger(
            int maximumRecentCredits,
            int maximumProcessedDeaths,
            long creditWindowTicks,
            long duplicateWindowTicks
    ) {
        if (creditWindowTicks < 1L
                || duplicateWindowTicks < 1L) {
            throw new IllegalArgumentException(
                    "Attribution windows must be at least one tick."
            );
        }

        this.creditWindowTicks = creditWindowTicks;
        this.duplicateWindowTicks = duplicateWindowTicks;
        this.recentCredits = new RecognitionRuntimeCapTable<>(
                maximumRecentCredits,
                CreditRecord::recordedGameTime
        );
        this.processedDeaths = new RecognitionRuntimeCapTable<>(
                maximumProcessedDeaths,
                ProcessedDeath::processedGameTime
        );
    }

    synchronized void record(
            VictimKey victim,
            PlayerCredit credit,
            long currentGameTime
    ) {
        Objects.requireNonNull(victim, "A victim key is required.");
        Objects.requireNonNull(credit, "Combat credit is required.");

        long safeTime = Math.max(0L, currentGameTime);

        recentCredits.put(
                victim,
                new CreditRecord(
                        credit,
                        safeTime,
                        deadline(safeTime, creditWindowTicks)
                )
        );
    }

    synchronized Resolution consumeDeath(
            VictimKey victim,
            Optional<PlayerCredit> directCredit,
            long currentGameTime,
            Function<UUID, String> currentLifeTokenResolver
    ) {
        Objects.requireNonNull(victim, "A victim key is required.");
        Objects.requireNonNull(directCredit, "Direct credit is required.");
        Objects.requireNonNull(
                currentLifeTokenResolver,
                "A life-token resolver is required."
        );

        long safeTime = Math.max(0L, currentGameTime);
        ProcessedDeath processed = processedDeaths.get(victim);

        if (processed != null
                && safeTime <= processed.expirationGameTime()) {
            return Resolution.duplicate();
        }

        if (processed != null) {
            processedDeaths.remove(victim);
        }

        processedDeaths.put(
                victim,
                new ProcessedDeath(
                        safeTime,
                        deadline(safeTime, duplicateWindowTicks)
                )
        );

        CreditRecord stored = recentCredits.remove(victim);

        PlayerCredit selected;

        if (directCredit.isPresent()) {
            selected = directCredit.orElseThrow();
        } else if (stored == null) {
            return Resolution.noCredit();
        } else if (safeTime > stored.expirationGameTime()) {
            return Resolution.expired();
        } else {
            selected = stored.credit();
        }

        String currentLifeToken = currentLifeTokenResolver.apply(
                selected.playerUuid()
        );

        if (currentLifeToken == null
                || currentLifeToken.isBlank()
                || !currentLifeToken.equals(selected.lifeToken())) {
            return Resolution.staleLife();
        }

        return Resolution.credited(selected);
    }

    synchronized void cleanup(long currentGameTime) {
        long safeTime = Math.max(0L, currentGameTime);

        recentCredits.removeIf(
                entry -> safeTime
                        > entry.getValue().expirationGameTime()
        );

        processedDeaths.removeIf(
                entry -> safeTime
                        > entry.getValue().expirationGameTime()
        );
    }

    synchronized void clearPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        recentCredits.removeIf(
                entry -> playerUuid.equals(
                        entry.getValue()
                                .credit()
                                .playerUuid()
                )
        );
    }

    synchronized void clear() {
        recentCredits.clear();
        processedDeaths.clear();
    }

    synchronized Snapshot inspect(UUID playerUuid) {
        int playerCredits = 0;

        if (playerUuid != null) {
            for (var entry : recentCredits.snapshotEntries()) {
                if (playerUuid.equals(
                        entry.getValue()
                                .credit()
                                .playerUuid()
                )) {
                    playerCredits++;
                }
            }
        }

        return new Snapshot(
                recentCredits.size(),
                processedDeaths.size(),
                playerCredits,
                recentCredits.maximumEntries(),
                processedDeaths.maximumEntries()
        );
    }

    private static long deadline(
            long start,
            long duration
    ) {
        if (Long.MAX_VALUE - start < duration) {
            return Long.MAX_VALUE;
        }

        return start + duration;
    }

    record VictimKey(
            String dimensionId,
            UUID victimUuid
    ) {
        VictimKey {
            dimensionId = dimensionId == null
                    ? ""
                    : dimensionId.trim();
            Objects.requireNonNull(
                    victimUuid,
                    "A victim UUID is required."
            );
        }
    }

    record PlayerCredit(
            UUID playerUuid,
            String lifeToken,
            RecognitionAttributionPolicy.ActorKind actorKind
    ) {
        PlayerCredit {
            Objects.requireNonNull(
                    playerUuid,
                    "A credited player UUID is required."
            );
            lifeToken = lifeToken == null
                    ? ""
                    : lifeToken.trim();
            actorKind = actorKind == null
                    ? RecognitionAttributionPolicy.ActorKind.NONE
                    : actorKind;

            if (lifeToken.isBlank()
                    || actorKind
                    == RecognitionAttributionPolicy.ActorKind.NONE) {
                throw new IllegalArgumentException(
                        "A credited action requires a life token and owned actor kind."
                );
            }
        }
    }

    private record CreditRecord(
            PlayerCredit credit,
            long recordedGameTime,
            long expirationGameTime
    ) {
    }

    private record ProcessedDeath(
            long processedGameTime,
            long expirationGameTime
    ) {
    }

    enum ResolutionStatus {
        CREDITED,
        NO_CREDIT,
        EXPIRED,
        STALE_LIFE,
        DUPLICATE_SUPPRESSED
    }

    record Resolution(
            ResolutionStatus status,
            Optional<PlayerCredit> credit
    ) {
        Resolution {
            status = status == null
                    ? ResolutionStatus.NO_CREDIT
                    : status;
            credit = credit == null
                    ? Optional.empty()
                    : credit;
        }

        static Resolution credited(PlayerCredit credit) {
            return new Resolution(
                    ResolutionStatus.CREDITED,
                    Optional.of(credit)
            );
        }

        static Resolution noCredit() {
            return new Resolution(
                    ResolutionStatus.NO_CREDIT,
                    Optional.empty()
            );
        }

        static Resolution expired() {
            return new Resolution(
                    ResolutionStatus.EXPIRED,
                    Optional.empty()
            );
        }

        static Resolution staleLife() {
            return new Resolution(
                    ResolutionStatus.STALE_LIFE,
                    Optional.empty()
            );
        }

        static Resolution duplicate() {
            return new Resolution(
                    ResolutionStatus.DUPLICATE_SUPPRESSED,
                    Optional.empty()
            );
        }

        boolean duplicateSuppressed() {
            return status == ResolutionStatus.DUPLICATE_SUPPRESSED;
        }
    }

    record Snapshot(
            int recentCredits,
            int processedDeaths,
            int selectedPlayerCredits,
            int maximumRecentCredits,
            int maximumProcessedDeaths
    ) {
    }
}
