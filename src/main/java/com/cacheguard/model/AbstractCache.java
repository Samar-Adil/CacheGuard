package com.cacheguard.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Template for replacement policies: handles counters and the
 * hit / miss / evict / insert sequence, so subclasses only describe their bookkeeping.
 */
public abstract class AbstractCache<K> implements Cache<K> {

    private final CacheAlgorithm algorithm;
    private final int capacity;
    private long hits;
    private long misses;
    private long evictions;

    protected AbstractCache(CacheAlgorithm algorithm, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1, was " + capacity);
        }
        this.algorithm = Objects.requireNonNull(algorithm);
        this.capacity = capacity;
    }

    @Override
    public final AccessResult<K> access(K key) {
        Objects.requireNonNull(key, "key");
        if (contains(key)) {
            hits++;
            onHit(key);
            return new AccessResult<>(key, true, null);
        }
        misses++;
        K victim = null;
        if (size() >= capacity) {
            victim = evict();
            evictions++;
        }
        insert(key);
        return new AccessResult<>(key, false, victim);
    }

    /** Update policy bookkeeping for a key that is already resident. */
    protected abstract void onHit(K key);

    /** Remove and return the victim; only called when the cache is full. */
    protected abstract K evict();

    /** Add a key that is not resident; there is guaranteed to be room. */
    protected abstract void insert(K key);

    /** Predict the victim; only called when the cache is full. */
    protected abstract Optional<K> peekVictim();

    /** Drop all resident keys and any policy state (e.g. reseed a random generator). */
    protected abstract void clear();

    @Override
    public final Optional<K> nextVictim() {
        return size() >= capacity ? peekVictim() : Optional.empty();
    }

    @Override
    public final void reset() {
        clear();
        hits = 0;
        misses = 0;
        evictions = 0;
    }

    @Override
    public final CacheStats stats() {
        return new CacheStats(hits, misses, evictions);
    }

    @Override
    public final CacheAlgorithm algorithm() {
        return algorithm;
    }

    @Override
    public final int capacity() {
        return capacity;
    }

    @Override
    public String annotation(K key) {
        return "";
    }
}
