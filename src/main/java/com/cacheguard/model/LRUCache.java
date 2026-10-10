package com.cacheguard.model;

/**
 * Least Recently Used: HashMap + doubly linked list, O(1) for every operation.
 */
public final class LRUCache<K> extends RecencyListCache<K> {

    public LRUCache(int capacity) {
        super(CacheAlgorithm.LRU, capacity);
    }

    @Override
    protected boolean evictsMostRecent() {
        return false;
    }
}
