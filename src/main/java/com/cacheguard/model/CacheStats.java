package com.cacheguard.model;

/**
 * Immutable hit / miss / eviction counters.
 */
public record CacheStats(long hits, long misses, long evictions) {

    public static final CacheStats EMPTY = new CacheStats(0, 0, 0);

    public CacheStats {
        if (hits < 0 || misses < 0 || evictions < 0) {
            throw new IllegalArgumentException("Counters must not be negative");
        }
    }

    public long total() {
        return hits + misses;
    }

    /** Hit ratio as a fraction in [0, 1]; 0 when nothing was requested. */
    public double hitRatio() {
        long t = total();
        return t == 0 ? 0.0 : (double) hits / t;
    }

    public double missRatio() {
        long t = total();
        return t == 0 ? 0.0 : (double) misses / t;
    }

    public double hitPercent() {
        return hitRatio() * 100.0;
    }

    public double missPercent() {
        return missRatio() * 100.0;
    }
}
