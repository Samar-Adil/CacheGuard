package com.cacheguard.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelValueTest {

    @Test
    void statsRatios() {
        CacheStats s = new CacheStats(3, 1, 0);
        assertEquals(75.0, s.hitPercent(), 1e-9);
        assertEquals(25.0, s.missPercent(), 1e-9);
        assertEquals(0.0, CacheStats.EMPTY.hitRatio(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new CacheStats(-1, 0, 0));
    }

    @Test
    void settingsClampAndRoundTrip() {
        assertEquals(2, new Settings(AppTheme.DARK, 0, CacheAlgorithm.LRU).defaultCapacity());
        assertEquals(16, new Settings(AppTheme.DARK, 99, CacheAlgorithm.LRU).defaultCapacity());
        Settings s = new Settings(AppTheme.LIGHT, 8, CacheAlgorithm.LFU);
        assertEquals(s, Settings.fromMap(s.toMap()));
        assertEquals(Settings.defaults(), Settings.fromMap(Map.of("theme", 5, "defaultAlgorithm", "nope")));
    }

    @Test
    void sessionRoundTripAndValidation() {
        SessionRecord r = new SessionRecord("id-1", 1000L, "simulation", CacheAlgorithm.MRU, 4, 12, 5, 7, 3,
                "1, 2, 3", 99L);
        assertEquals(r, SessionRecord.fromMap(r.toMap()));
        assertEquals(5.0 / 12 * 100, r.hitPercent(), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> SessionRecord.fromMap(Map.of("id", "x", "algorithm", "BOGUS")));
        assertThrows(IllegalArgumentException.class, () -> SessionRecord.fromMap(Map.of("algorithm", "LRU")));
    }

    @Test
    void historyDeduplicatesSortsAndTrims() {
        AppState state = new AppState(Settings.defaults(), List.of());
        state.addSession(session("a", 30));
        state.addSession(session("b", 10));
        state.addSession(session("a", 30)); // duplicate id
        assertEquals(List.of("b", "a"), state.history().stream().map(SessionRecord::id).toList());

        java.util.ArrayList<SessionRecord> many = new java.util.ArrayList<>();
        for (int i = 0; i < AppState.MAX_HISTORY + 25; i++) {
            many.add(session("s" + i, i));
        }
        state.replaceHistory(many);
        assertEquals(AppState.MAX_HISTORY, state.history().size());
        assertEquals("s25", state.history().get(0).id()); // oldest 25 dropped
    }

    @Test
    void historyChangesNotifyListeners() {
        AppState state = new AppState(Settings.defaults(), List.of());
        int[] calls = {0};
        state.addPropertyChangeListener(AppState.HISTORY, e -> calls[0]++);
        state.addSession(session("a", 1));
        state.clearHistory();
        state.clearHistory(); // already empty: no event
        assertEquals(2, calls[0]);
    }

    @Test
    void statisticsSummary() {
        StatisticsSummary s = StatisticsSummary.of(List.of(
                new SessionRecord("1", 1, "simulation", CacheAlgorithm.LRU, 4, 10, 5, 5, 1, "", 0),
                new SessionRecord("2", 2, "simulation", CacheAlgorithm.LRU, 4, 10, 7, 3, 1, "", 0),
                new SessionRecord("3", 3, "simulation", CacheAlgorithm.FIFO, 4, 10, 2, 8, 4, "", 0)));
        assertEquals(3, s.sessions());
        assertEquals(30, s.requests());
        assertEquals(14, s.hits());
        assertEquals(6, s.evictions());
        assertEquals(14.0 / 30 * 100, s.overallHitPercent(), 1e-9);
        StatisticsSummary.AlgorithmStats lru = s.byAlgorithm().get(CacheAlgorithm.LRU);
        assertEquals(60.0, lru.averageHitPercent(), 1e-9);
        assertEquals(70.0, lru.bestHitPercent(), 1e-9);
        assertEquals(CacheAlgorithm.LRU, s.topAlgorithm().orElseThrow().algorithm());
        assertTrue(StatisticsSummary.of(List.of()).topAlgorithm().isEmpty());
    }

    @Test
    void workloadsAreDeterministicAndInRange() {
        for (WorkloadPattern p : WorkloadPattern.values()) {
            int[] a = WorkloadGenerator.generate(p, 2000, 50, 9L);
            int[] b = WorkloadGenerator.generate(p, 2000, 50, 9L);
            assertArrayEquals(a, b, p.name());
            for (int k : a) {
                assertTrue(k >= 1 && k <= 50, p + " produced " + k);
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> WorkloadGenerator.generate(WorkloadPattern.UNIFORM, 0, 10, 1));
        assertThrows(IllegalArgumentException.class,
                () -> WorkloadGenerator.generate(WorkloadPattern.UNIFORM, 10, 1, 1));
    }

    @Test
    void zipfIsSkewedTowardsLowKeys() {
        int[] reqs = WorkloadGenerator.generate(WorkloadPattern.ZIPF, 20_000, 100, 3L);
        int ones = 0;
        for (int k : reqs) {
            if (k == 1) {
                ones++;
            }
        }
        assertTrue(ones > 20_000 / 100 * 5, "key 1 should be far hotter than uniform, got " + ones);
    }

    private static SessionRecord session(String id, long time) {
        return new SessionRecord(id, time, "simulation", CacheAlgorithm.LRU, 4, 10, 5, 5, 1, "", 0);
    }
}
