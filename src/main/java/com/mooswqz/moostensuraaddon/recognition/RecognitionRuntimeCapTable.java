package com.mooswqz.moostensuraaddon.recognition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * Small synchronized runtime-only map with a strict entry ceiling.
 *
 * <p>The table is intentionally independent from Minecraft so the cap,
 * deterministic oldest-entry eviction and cleanup behaviour can be validated
 * without constructing a world or server.</p>
 */
public final class RecognitionRuntimeCapTable<K, V> {

    private final int maximumEntries;
    private final ToLongFunction<V> ageSelector;
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>();

    public RecognitionRuntimeCapTable(
            int maximumEntries,
            ToLongFunction<V> ageSelector
    ) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException(
                    "maximumEntries must be at least one."
            );
        }

        this.maximumEntries = maximumEntries;
        this.ageSelector = Objects.requireNonNull(
                ageSelector,
                "An age selector is required."
        );
    }

    public synchronized V get(K key) {
        return entries.get(key);
    }

    public synchronized V getOrCreate(
            K key,
            Supplier<V> factory
    ) {
        Objects.requireNonNull(key, "A key is required.");
        Objects.requireNonNull(factory, "A value factory is required.");

        V existing = entries.get(key);

        if (existing != null) {
            return existing;
        }

        V created = Objects.requireNonNull(
                factory.get(),
                "The value factory returned null."
        );

        ensureCapacityForNewEntry();
        entries.put(key, created);
        return created;
    }

    public synchronized V put(K key, V value) {
        Objects.requireNonNull(key, "A key is required.");
        Objects.requireNonNull(value, "A value is required.");

        if (!entries.containsKey(key)) {
            ensureCapacityForNewEntry();
        }

        return entries.put(key, value);
    }

    public synchronized V remove(K key) {
        return entries.remove(key);
    }

    public synchronized int removeIf(
            Predicate<Map.Entry<K, V>> predicate
    ) {
        Objects.requireNonNull(predicate, "A predicate is required.");

        int before = entries.size();
        entries.entrySet().removeIf(predicate);
        return before - entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    public int maximumEntries() {
        return maximumEntries;
    }

    public synchronized List<Map.Entry<K, V>> snapshotEntries() {
        List<Map.Entry<K, V>> result = new ArrayList<>(entries.size());

        for (Map.Entry<K, V> entry : entries.entrySet()) {
            result.add(
                    Map.entry(
                            entry.getKey(),
                            entry.getValue()
                    )
            );
        }

        return List.copyOf(result);
    }

    private void ensureCapacityForNewEntry() {
        while (entries.size() >= maximumEntries) {
            evictOldestEntry();
        }
    }

    private void evictOldestEntry() {
        K oldestKey = null;
        long oldestAge = Long.MAX_VALUE;

        for (Map.Entry<K, V> entry : entries.entrySet()) {
            long candidateAge = ageSelector.applyAsLong(
                    entry.getValue()
            );

            if (oldestKey == null
                    || candidateAge < oldestAge) {
                oldestKey = entry.getKey();
                oldestAge = candidateAge;
            }
        }

        if (oldestKey == null) {
            throw new IllegalStateException(
                    "The runtime table is full but has no evictable entry."
            );
        }

        entries.remove(oldestKey);
    }
}