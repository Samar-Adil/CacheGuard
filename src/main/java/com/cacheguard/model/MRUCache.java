package com.cacheguard.model;

/**
 * Most Recently Used: same structure as LRU but the victim is the newest entry.
 * Counter-intuitive, yet optimal for cyclic scans larger than the cache.
 */
public final class MRUCache<K> extends RecencyListCache<K> {

    public MRUCache(int capacity) {
        super(CacheAlgorithm.MRU, capacity);
    }

    @Override
    protected boolean evictsMostRecent() {
        return true;
    }
}
