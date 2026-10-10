package com.cacheguard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Least Frequently Used with O(1) operations.
 *
 * <p>Each key has a frequency; keys with the same frequency live in an insertion-ordered
 * {@link LinkedHashSet} bucket. A key moves to the next bucket on every hit, so within a
 * bucket the first key is the least recently used. {@code minFrequency} always points at the
 * lowest non-empty bucket, which makes the victim lookup O(1) and breaks ties by recency.
 */
public final class LFUCache<K> extends AbstractCache<K> {

    private final Map<K, Integer> frequency = new HashMap<>();
    private final Map<Integer, LinkedHashSet<K>> buckets = new HashMap<>();
    private int minFrequency;

    public LFUCache(int capacity) {
        super(CacheAlgorithm.LFU, capacity);
    }

    @Override
    public int size() {
        return frequency.size();
    }

    @Override
    public boolean contains(K key) {
        return frequency.containsKey(key);
    }

    @Override
    protected void onHit(K key) {
        int f = frequency.get(key);
        LinkedHashSet<K> bucket = buckets.get(f);
        bucket.remove(key);
        if (bucket.isEmpty()) {
            buckets.remove(f);
            if (minFrequency == f) {
                minFrequency = f + 1;
            }
        }
        frequency.put(key, f + 1);
        buckets.computeIfAbsent(f + 1, x -> new LinkedHashSet<>()).add(key);
    }

    @Override
    protected K evict() {
        LinkedHashSet<K> bucket = buckets.get(minFrequency);
        Iterator<K> it = bucket.iterator();
        K victim = it.next();
        it.remove();
        if (bucket.isEmpty()) {
            buckets.remove(minFrequency);
        }
        frequency.remove(victim);
        return victim;
    }

    @Override
    protected void insert(K key) {
        frequency.put(key, 1);
        buckets.computeIfAbsent(1, x -> new LinkedHashSet<>()).add(key);
        minFrequency = 1;
    }

    @Override
    protected Optional<K> peekVictim() {
        return Optional.of(buckets.get(minFrequency).iterator().next());
    }

    @Override
    protected void clear() {
        frequency.clear();
        buckets.clear();
        minFrequency = 0;
    }

    @Override
    public List<K> contents() {
        List<Integer> freqs = new ArrayList<>(buckets.keySet());
        Collections.sort(freqs);
        List<K> out = new ArrayList<>(frequency.size());
        for (int f : freqs) {
            out.addAll(buckets.get(f));
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public String annotation(K key) {
        Integer f = frequency.get(key);
        return f == null ? "" : "freq " + f;
    }
}
