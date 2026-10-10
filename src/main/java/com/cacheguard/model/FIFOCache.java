package com.cacheguard.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * First-In, First-Out: a queue gives the eviction order and a hash set gives O(1) membership.
 * Hits never change the order, which is why FIFO can show Belady's anomaly.
 */
public final class FIFOCache<K> extends AbstractCache<K> {

    private final ArrayDeque<K> queue = new ArrayDeque<>();
    private final Set<K> members = new HashSet<>();

    public FIFOCache(int capacity) {
        super(CacheAlgorithm.FIFO, capacity);
    }

    @Override
    public int size() {
        return members.size();
    }

    @Override
    public boolean contains(K key) {
        return members.contains(key);
    }

    @Override
    protected void onHit(K key) {
        // FIFO ignores reuse.
    }

    @Override
    protected K evict() {
        K victim = queue.pollFirst();
        members.remove(victim);
        return victim;
    }

    @Override
    protected void insert(K key) {
        queue.addLast(key);
        members.add(key);
    }

    @Override
    protected Optional<K> peekVictim() {
        return Optional.ofNullable(queue.peekFirst());
    }

    @Override
    protected void clear() {
        queue.clear();
        members.clear();
    }

    @Override
    public List<K> contents() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    /** "queue #1" is the oldest resident key. O(n). */
    @Override
    public String annotation(K key) {
        int pos = 1;
        for (K k : queue) {
            if (k.equals(key)) {
                return "queue #" + pos;
            }
            pos++;
        }
        return "";
    }
}
