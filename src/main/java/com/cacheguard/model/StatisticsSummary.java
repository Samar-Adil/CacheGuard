package com.cacheguard.model;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Aggregates over the session history: totals plus a per-algorithm breakdown.
 */
public final class StatisticsSummary {

    /** Numbers for one algorithm across all of its sessions. */
    public record AlgorithmStats(CacheAlgorithm algorithm, int sessions, long requests, long hits,
                                 long evictions, double averageHitPercent, double bestHitPercent) {
    }

    private final int sessions;
    private final long requests;
    private final long hits;
    private final long misses;
    private final long evictions;
    private final Map<CacheAlgorithm, AlgorithmStats> byAlgorithm;

    private StatisticsSummary(int sessions, long requests, long hits, long misses, long evictions,
                              Map<CacheAlgorithm, AlgorithmStats> byAlgorithm) {
        this.sessions = sessions;
        this.requests = requests;
        this.hits = hits;
        this.misses = misses;
        this.evictions = evictions;
        this.byAlgorithm = byAlgorithm;
    }

    public static StatisticsSummary of(Collection<SessionRecord> history) {
        long requests = 0;
        long hits = 0;
        long misses = 0;
        long evictions = 0;
        Map<CacheAlgorithm, double[]> acc = new EnumMap<>(CacheAlgorithm.class);
        // slots: 0 sessions, 1 requests, 2 hits, 3 evictions, 4 sum of hit %, 5 best hit %
        for (SessionRecord s : history) {
            requests += s.requestCount();
            hits += s.hits();
            misses += s.misses();
            evictions += s.evictions();
            double[] a = acc.computeIfAbsent(s.algorithm(), k -> new double[6]);
            a[0]++;
            a[1] += s.requestCount();
            a[2] += s.hits();
            a[3] += s.evictions();
            a[4] += s.hitPercent();
            a[5] = Math.max(a[5], s.hitPercent());
        }
        Map<CacheAlgorithm, AlgorithmStats> by = new EnumMap<>(CacheAlgorithm.class);
        for (CacheAlgorithm alg : CacheAlgorithm.values()) {
            double[] a = acc.get(alg);
            if (a != null) {
                by.put(alg, new AlgorithmStats(alg, (int) a[0], (long) a[1], (long) a[2], (long) a[3],
                        a[4] / a[0], a[5]));
            }
        }
        return new StatisticsSummary(history.size(), requests, hits, misses, evictions, by);
    }

    public int sessions() {
        return sessions;
    }

    public long requests() {
        return requests;
    }

    public long hits() {
        return hits;
    }

    public long misses() {
        return misses;
    }

    public long evictions() {
        return evictions;
    }

    /** Hit percentage over every request in the history. */
    public double overallHitPercent() {
        long total = hits + misses;
        return total == 0 ? 0.0 : 100.0 * hits / total;
    }

    /** Only algorithms that have at least one session. */
    public Map<CacheAlgorithm, AlgorithmStats> byAlgorithm() {
        return byAlgorithm;
    }

    /** The algorithm with the highest average hit percentage, if there is any history. */
    public Optional<AlgorithmStats> topAlgorithm() {
        AlgorithmStats best = null;
        for (AlgorithmStats a : byAlgorithm.values()) {
            if (best == null || a.averageHitPercent() > best.averageHitPercent()) {
                best = a;
            }
        }
        return Optional.ofNullable(best);
    }
}
