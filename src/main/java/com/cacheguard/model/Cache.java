package com.cacheguard.model;

import java.util.List;
import java.util.Optional;

/**
 * A fixed-capacity cache with a pluggable replacement policy.
 * Implementations are not thread-safe; use one instance per thread.
 */
public interface Cache<K> {

    CacheAlgorithm algorithm();

    int capacity();

    /** Number of resident keys. */
    int size();

    boolean contains(K key);

    /**
     * Requests {@code key}: a hit if resident, otherwise a miss that inserts it,
     * evicting a victim first when the cache is full.
     */
    AccessResult<K> access(K key);

    /**
     * Resident keys ordered by eviction priority (next victim first) where the
     * policy has an order; for {@link CacheAlgorithm#RANDOM} the order is arbitrary.
     * Intended for display, may cost O(n log n).
     */
    List<K> contents();

    /** The key that would be evicted by the next miss, if the policy can predict it. */
    Optional<K> nextVictim();

    /** Short policy-specific label for a resident key (e.g. "freq 3"); display only, may be O(n). */
    String annotation(K key);

    CacheStats stats();

    /** Empties the cache and zeroes the counters. */
    void reset();
}
