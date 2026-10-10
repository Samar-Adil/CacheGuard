package com.cacheguard.model;

/**
 * Creates caches by algorithm so callers never depend on concrete classes.
 */
public final class CacheFactory {

    private CacheFactory() {
    }

    public static <K> Cache<K> create(CacheAlgorithm algorithm, int capacity) {
        return switch (algorithm) {
            case LRU -> new LRUCache<>(capacity);
            case LFU -> new LFUCache<>(capacity);
            case FIFO -> new FIFOCache<>(capacity);
            case MRU -> new MRUCache<>(capacity);
            case RANDOM -> new RandomCache<>(capacity);
        };
    }
}
