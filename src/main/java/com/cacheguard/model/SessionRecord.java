package com.cacheguard.model;

import com.cacheguard.util.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A finished simulation kept in the history.
 *
 * @param id             unique id, used to avoid duplicates when importing
 * @param timestamp      when it finished (epoch milliseconds)
 * @param source         "simulation" or "comparison"
 * @param requestCount   number of requests replayed
 * @param sample         the first few requests, for display
 * @param elapsedNanos   time spent simulating
 */
public record SessionRecord(String id, long timestamp, String source, CacheAlgorithm algorithm,
                            int capacity, int requestCount, long hits, long misses, long evictions,
                            String sample, long elapsedNanos) {

    public static final String SOURCE_SIMULATION = "simulation";
    public static final String SOURCE_COMPARISON = "comparison";

    public SessionRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A session needs an id");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("A session needs an algorithm");
        }
        capacity = Math.max(1, capacity);
        requestCount = Math.max(0, requestCount);
        hits = Math.max(0, hits);
        misses = Math.max(0, misses);
        evictions = Math.max(0, evictions);
        source = source == null ? SOURCE_SIMULATION : source;
        sample = sample == null ? "" : sample;
        elapsedNanos = Math.max(0, elapsedNanos);
    }

    public static SessionRecord of(SimulationResult result, String source, String sample) {
        CacheStats s = result.stats();
        return new SessionRecord(UUID.randomUUID().toString(), System.currentTimeMillis(), source,
                result.algorithm(), result.capacity(), result.requestCount(),
                s.hits(), s.misses(), s.evictions(), sample, result.elapsedNanos());
    }

    public CacheStats stats() {
        return new CacheStats(hits, misses, evictions);
    }

    public double hitPercent() {
        return stats().hitPercent();
    }

    public Instant time() {
        return Instant.ofEpochMilli(timestamp);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("timestamp", timestamp);
        m.put("source", source);
        m.put("algorithm", algorithm.name());
        m.put("capacity", capacity);
        m.put("requestCount", requestCount);
        m.put("hits", hits);
        m.put("misses", misses);
        m.put("evictions", evictions);
        m.put("sample", sample);
        m.put("elapsedNanos", elapsedNanos);
        return m;
    }

    /** @throws IllegalArgumentException if the entry is unusable (missing id or unknown algorithm) */
    public static SessionRecord fromMap(Map<String, Object> m) {
        return new SessionRecord(
                Json.string(m, "id", null),
                Json.longValue(m, "timestamp", 0L),
                Json.string(m, "source", SOURCE_SIMULATION),
                CacheAlgorithm.parse(Json.string(m, "algorithm", null)),
                Json.intValue(m, "capacity", 1),
                Json.intValue(m, "requestCount", 0),
                Json.longValue(m, "hits", 0),
                Json.longValue(m, "misses", 0),
                Json.longValue(m, "evictions", 0),
                Json.string(m, "sample", ""),
                Json.longValue(m, "elapsedNanos", 0));
    }
}
