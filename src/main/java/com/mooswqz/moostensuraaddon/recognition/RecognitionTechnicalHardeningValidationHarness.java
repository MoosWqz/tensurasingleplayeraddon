package com.mooswqz.moostensuraaddon.recognition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic debug-only validation for the 6G.8 runtime hardening rules.
 */
public final class RecognitionTechnicalHardeningValidationHarness {

    private RecognitionTechnicalHardeningValidationHarness() {
    }

    public static Report validate() {
        List<Check> checks = new ArrayList<>();

        validateRuntimeCapTable(checks);
        validateCleanupEpochs(checks);
        validateProductionLimits(checks);
        validateLiveRuntimeBounds(checks);

        return new Report(List.copyOf(checks));
    }

    private static void validateRuntimeCapTable(
            List<Check> checks
    ) {
        boolean rejectedInvalidCap = false;

        try {
            new RecognitionRuntimeCapTable<String, Entry>(
                    0,
                    Entry::age
            );
        } catch (IllegalArgumentException expected) {
            rejectedInvalidCap = true;
        }

        add(
                checks,
                "Invalid runtime cap rejected",
                rejectedInvalidCap,
                "A runtime table cannot be created with a zero-sized ceiling."
        );

        RecognitionRuntimeCapTable<String, Entry> table =
                new RecognitionRuntimeCapTable<>(
                        3,
                        Entry::age
                );

        AtomicInteger factoryCalls = new AtomicInteger();

        Entry first = table.getOrCreate(
                "first",
                () -> {
                    factoryCalls.incrementAndGet();
                    return new Entry(10L, "first");
                }
        );

        Entry repeated = table.getOrCreate(
                "first",
                () -> {
                    factoryCalls.incrementAndGet();
                    return new Entry(99L, "unexpected");
                }
        );

        add(
                checks,
                "Atomic get-or-create",
                first == repeated && factoryCalls.get() == 1,
                "An existing runtime value is reused and its factory is not called twice."
        );

        table.put("second", new Entry(20L, "second"));
        table.put("third", new Entry(30L, "third"));

        add(
                checks,
                "Runtime table reaches its cap",
                table.size() == 3,
                "The test table contains exactly three bounded entries."
        );

        table.put("fourth", new Entry(40L, "fourth"));

        add(
                checks,
                "Oldest runtime entry evicted",
                table.get("first") == null,
                "Inserting beyond the ceiling evicts the entry with the lowest age value."
        );

        add(
                checks,
                "Newest runtime entry retained",
                table.get("fourth") != null
                        && table.size() == 3,
                "The new entry is retained without allowing the table to exceed its cap."
        );

        table.put("second", new Entry(50L, "second-updated"));

        add(
                checks,
                "Existing-key update does not evict",
                table.size() == 3
                        && "second-updated".equals(
                        table.get("second").label()
                ),
                "Replacing an existing value preserves the table size."
        );

        Entry removed = table.remove("third");

        add(
                checks,
                "Explicit runtime removal",
                removed != null
                        && table.get("third") == null
                        && table.size() == 2,
                "A known entry is removed exactly once."
        );

        int removedByPredicate = table.removeIf(
                entry -> entry.getValue().age() >= 40L
        );

        add(
                checks,
                "Predicate cleanup count",
                removedByPredicate == 2
                        && table.size() == 0,
                "Predicate cleanup reports the exact number of removed entries."
        );

        table.put("tie-a", new Entry(5L, "tie-a"));
        table.put("tie-b", new Entry(5L, "tie-b"));
        table.put("tie-c", new Entry(6L, "tie-c"));
        table.put("tie-d", new Entry(7L, "tie-d"));

        add(
                checks,
                "Deterministic tie eviction",
                table.get("tie-a") == null
                        && table.get("tie-b") != null,
                "Equal-age entries are resolved by stable insertion order."
        );

        List<Map.Entry<String, Entry>> snapshot =
                table.snapshotEntries();

        boolean snapshotImmutable = false;

        try {
            snapshot.add(
                    Map.entry(
                            "illegal",
                            new Entry(0L, "illegal")
                    )
            );
        } catch (UnsupportedOperationException expected) {
            snapshotImmutable = true;
        }

        add(
                checks,
                "Runtime snapshot immutable",
                snapshotImmutable,
                "Debug snapshots cannot mutate the live runtime table."
        );

        table.clear();

        add(
                checks,
                "Runtime table clear",
                table.size() == 0,
                "Lifecycle cleanup can empty a runtime table completely."
        );
    }

    private static void validateCleanupEpochs(
            List<Check> checks
    ) {
        long interval =
                CivilianDefenseTracker.cleanupIntervalTicks();

        add(
                checks,
                "Cleanup interval blocks early rerun",
                !CivilianDefenseTracker.shouldRunCleanup(
                        1_000L,
                        1_000L + interval - 1L
                ),
                "Repeated player syncs inside the cleanup interval remain cheap."
        );

        add(
                checks,
                "Cleanup interval permits boundary",
                CivilianDefenseTracker.shouldRunCleanup(
                        1_000L,
                        1_000L + interval
                ),
                "Cleanup runs when the complete interval has elapsed."
        );

        add(
                checks,
                "Lower clock opens a new cleanup epoch",
                CivilianDefenseTracker.shouldRunCleanup(
                        50_000L,
                        100L
                ),
                "A newly started world cannot be blocked by the previous server's higher clock."
        );
    }

    private static void validateProductionLimits(
            List<Check> checks
    ) {
        add(
                checks,
                "Civilian tracker ceiling",
                CivilianDefenseTracker.maximumActiveAggressors()
                        == 4096,
                "The civilian encounter tracker remains bounded at 4096 records."
        );

        add(
                checks,
                "Civilian cleanup cadence",
                CivilianDefenseTracker.cleanupIntervalTicks()
                        == 200L,
                "Civilian encounter cleanup remains limited to once per ten seconds."
        );

        add(
                checks,
                "Paths request-state ceiling",
                RecognitionProgressScreenService
                        .maximumTrackedRequestStates()
                        == 1024,
                "The server cannot retain an unlimited number of stale Paths request states."
        );

        add(
                checks,
                "Global fresh-build ceiling",
                RecognitionProgressScreenService
                        .maximumFreshBuildsPerWindow()
                        == 16,
                "The existing one-second global Paths build budget remains 16."
        );

        add(
                checks,
                "Tracker ceiling exceeds UI ceiling",
                CivilianDefenseTracker.maximumActiveAggressors()
                        > RecognitionProgressScreenService
                        .maximumTrackedRequestStates(),
                "The higher-volume encounter tracker has the intentionally larger bound."
        );
    }

    private static void validateLiveRuntimeBounds(
            List<Check> checks
    ) {
        RecognitionProgressScreenService.RuntimeSnapshot ui =
                RecognitionProgressScreenService
                        .inspectRuntimeState();

        add(
                checks,
                "Live Paths state within ceiling",
                ui.trackedPlayerStates() >= 0
                        && ui.trackedPlayerStates()
                        <= ui.maximumTrackedPlayerStates(),
                "Current tracked player states: "
                        + ui.trackedPlayerStates()
                        + " / "
                        + ui.maximumTrackedPlayerStates()
                        + "."
        );

        add(
                checks,
                "Live fresh-build budget within ceiling",
                ui.freshBuildsInCurrentWindow() >= 0
                        && ui.freshBuildsInCurrentWindow()
                        <= ui.maximumFreshBuildsPerWindow(),
                "Current fresh builds: "
                        + ui.freshBuildsInCurrentWindow()
                        + " / "
                        + ui.maximumFreshBuildsPerWindow()
                        + "."
        );

        add(
                checks,
                "Live Paths clocks non-negative",
                ui.buildWindowStartedNanos() >= 0L
                        && ui.nextCleanupNanos() >= 0L,
                "Runtime nanosecond markers remain valid."
        );

        add(
                checks,
                "Tracked server-state count non-negative",
                CivilianDefenseTracker.serverStateCount() >= 0,
                "Current civilian tracker server states: "
                        + CivilianDefenseTracker.serverStateCount()
                        + "."
        );
    }

    private static void add(
            List<Check> checks,
            String name,
            boolean passed,
            String detail
    ) {
        checks.add(
                new Check(
                        name,
                        passed,
                        detail
                )
        );
    }

    private record Entry(
            long age,
            String label
    ) {
    }

    public record Check(
            String name,
            boolean passed,
            String detail
    ) {
    }

    public record Report(
            List<Check> checks
    ) {
        public Report {
            checks = checks == null
                    ? List.of()
                    : List.copyOf(checks);
        }

        public boolean passed() {
            return failedChecks() == 0;
        }

        public int passedChecks() {
            return (int) checks.stream()
                    .filter(Check::passed)
                    .count();
        }

        public int failedChecks() {
            return checks.size() - passedChecks();
        }
    }
}