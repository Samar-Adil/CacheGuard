package com.cacheguard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Random replacement. Keys live in an array list with an index map, so removing a random
 * victim is an O(1) swap-with-last. The generator is seeded and re-seeded on reset, which
 * keeps simulations repeatable and comparisons fair.
 */
public final class RandomCache<K> extends AbstractCache<K> {

    public static final long DEFAULT_SEED = 42L;

    private final long seed;
    private Random random;
    private final List<K> items = new ArrayList<>();
    private final Map<K, Integer> positions = new HashMap<>();

    public RandomCache(int capacity) {
        this(capacity, DEFAULT_SEED);
    }

    public RandomCache(int capacity, long seed) {
        super(CacheAlgorithm.RANDOM, capacity);
        this.seed = seed;
        this.random = new Random(seed);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean contains(K key) {
        return positions.containsKey(key);
    }

    @Override
    protected void onHit(K key) {
        // Random replacement keeps no usage history.
    }

    @Override
    protected K evict() {
        int idx = random.nextInt(items.size());
        K victim = items.get(idx);
        int lastIndex = items.size() - 1;
        K moved = items.remove(lastIndex);
        if (idx != lastIndex) {
            items.set(idx, moved);
            positions.put(moved, idx);
        }
        positions.remove(victim);
        return victim;
    }

    @Override
    protected void insert(K key) {
        positions.put(key, items.size());
        items.add(key);
    }

    @Override
    protected Optional<K> peekVictim() {
        return Optional.empty(); // genuinely unpredictable
    }

    @Override
    protected void clear() {
        items.clear();
        positions.clear();
        random = new Random(seed);
    }

    @Override
    public List<K> contents() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }
}
