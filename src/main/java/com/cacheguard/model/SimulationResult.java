package com.cacheguard.model;

import java.util.List;

/**
 * Final numbers of one algorithm run over one request stream.
 */
public record SimulationResult(CacheAlgorithm algorithm, int capacity, int requestCount,
                               CacheStats stats, List<TimelinePoint> timeline, long elapsedNanos) {

    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }
}
