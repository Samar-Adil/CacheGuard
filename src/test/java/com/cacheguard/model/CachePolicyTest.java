package com.cacheguard.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Hand-checked scenarios for each policy, plus cross-checks against naive reference models. */
class CachePolicyTest {

    private static AccessResult<Integer> access(Cache<Integer> c, int key) {
        return c.access(key);
    }

    @Test
    void lruEvictsTheLeastRecentlyUsed() {
        Cache<Integer> c = new LRUCache<>(3);
        access(c, 1);
        access(c, 2);
        access(c, 3);
        assertTrue(access(c, 1).hit());            // order now 2,3,1
        AccessResult<Integer> r = access(c, 4);    // evicts 2
        assertFalse(r.hit());
        assertEquals(2, r.evicted());
        assertEquals(List.of(3, 1, 4), c.contents());
        assertEquals(Optional.of(3), c.nextVictim());
    }

    @Test
    void lfuEvictsTheLeastFrequentAndBreaksTiesByRecency() {
        Cache<Integer> c = new LFUCache<>(3);
        access(c, 1);
        access(c, 1);
        access(c, 2);
        access(c, 3);
        // freq: 1->2, 2->1, 3->1 ; tie between 2 and 3 goes to the older (2)
        AccessResult<Integer> r = access(c, 4);
        assertEquals(2, r.evicted());
        assertEquals("freq 2", c.annotation(1));
        assertEquals("freq 1", c.annotation(4));
    }

    @Test
    void lfuNewKeyStartsAtFrequencyOneEvenAfterHotKeys() {
        Cache<Integer> c = new LFUCache<>(2);
        for (int i = 0; i < 10; i++) {
            access(c, 1);
        }
        access(c, 2);
        assertEquals(2, access(c, 3).evicted()); // the newcomer 2 (freq 1) loses to hot key 1
        assertEquals(3, access(c, 4).evicted()); // and then 3 loses again
        assertTrue(c.contains(1));
    }

    @Test
    void fifoIgnoresHits() {
        Cache<Integer> c = new FIFOCache<>(3);
        access(c, 1);
        access(c, 2);
        access(c, 3);
        access(c, 1); // hit, but 1 is still the oldest
        assertEquals(1, access(c, 4).evicted());
        assertEquals(List.of(2, 3, 4), c.contents());
    }

    @Test
    void mruEvictsTheNewest() {
        Cache<Integer> c = new MRUCache<>(3);
        access(c, 1);
        access(c, 2);
        access(c, 3);
        assertEquals(3, access(c, 4).evicted());
        assertEquals(4, access(c, 5).evicted());
    }

    @Test
    void randomIsRepeatableAndResetReseeds() {
        List<Integer> first = victims(new RandomCache<>(4, 7L));
        List<Integer> second = victims(new RandomCache<>(4, 7L));
        assertEquals(first, second);

        Cache<Integer> c = new RandomCache<>(4, 7L);
        List<Integer> a = victims(c);
        c.reset();
        assertEquals(a, victims(c));
    }

    private static List<Integer> victims(Cache<Integer> c) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            AccessResult<Integer> r = c.access(i % 9);
            if (r.evicted() != null) {
                out.add(r.evicted());
            }
        }
        return out;
    }

    @Test
    void beladysAnomalyOnFifo() {
        int[] seq = {1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5};
        assertEquals(9, misses(new FIFOCache<>(3), seq));
        assertEquals(10, misses(new FIFOCache<>(4), seq));
    }

    @Test
    void knownResultsForTheBuiltInExamples() {
        int[] loop = new int[20];
        for (int i = 0; i < 20; i++) {
            loop[i] = i % 5 + 1;
        }
        assertEquals(20, misses(new LRUCache<>(4), loop));
        assertEquals(8, misses(new MRUCache<>(4), loop));

        int[] hot = {1, 1, 1, 2, 3, 4, 1, 2, 3, 4, 1, 5, 6, 7, 1, 2, 3, 1};
        assertEquals(12, misses(new LFUCache<>(3), hot));
        assertEquals(15, misses(new LRUCache<>(3), hot));
    }

    private static long misses(Cache<Integer> c, int[] seq) {
        for (int k : seq) {
            c.access(k);
        }
        return c.stats().misses();
    }

    @Test
    void capacityOneAndInvalidCapacity() {
        Cache<Integer> c = new LRUCache<>(1);
        access(c, 1);
        assertEquals(1, access(c, 2).evicted());
        assertThrows(IllegalArgumentException.class, () -> new LRUCache<Integer>(0));
        assertThrows(IllegalArgumentException.class, () -> new LFUCache<Integer>(-3));
    }

    @Test
    void statsAndResetAreConsistent() {
        for (CacheAlgorithm alg : CacheAlgorithm.values()) {
            Cache<Integer> c = CacheFactory.create(alg, 3);
            for (int k : new int[] {1, 2, 3, 1, 4, 5, 1}) {
                c.access(k);
            }
            CacheStats s = c.stats();
            assertEquals(7, s.total(), alg.name());
            assertEquals(s.misses() - 3, s.evictions(), alg.name()); // first three misses fill empty slots
            assertTrue(c.size() <= c.capacity());
            c.reset();
            assertEquals(CacheStats.EMPTY, c.stats());
            assertEquals(0, c.size());
        }
    }

    @Test
    void nextVictimIsEmptyUntilFull() {
        Cache<Integer> c = new LRUCache<>(2);
        assertEquals(Optional.empty(), c.nextVictim());
        access(c, 1);
        assertEquals(Optional.empty(), c.nextVictim());
        access(c, 2);
        assertEquals(Optional.of(1), c.nextVictim());
    }

    // ---- randomised cross-checks against simple reference models

    @Test
    void lruMatchesLinkedHashMapReference() {
        runAgainstReference(CacheAlgorithm.LRU, capacity -> {
            LinkedHashMap<Integer, Boolean> ref = new LinkedHashMap<>(16, 0.75f, true);
            return key -> {
                if (ref.containsKey(key)) {
                    ref.get(key);
                    return new Object[] {true, null};
                }
                Integer victim = null;
                if (ref.size() >= capacity) {
                    victim = ref.keySet().iterator().next();
                    ref.remove(victim);
                }
                ref.put(key, true);
                return new Object[] {false, victim};
            };
        });
    }

    @Test
    void fifoMatchesListReference() {
        runAgainstReference(CacheAlgorithm.FIFO, capacity -> {
            List<Integer> ref = new ArrayList<>();
            return key -> {
                if (ref.contains(key)) {
                    return new Object[] {true, null};
                }
                Integer victim = ref.size() >= capacity ? ref.remove(0) : null;
                ref.add(key);
                return new Object[] {false, victim};
            };
        });
    }

    @Test
    void mruMatchesListReference() {
        runAgainstReference(CacheAlgorithm.MRU, capacity -> {
            List<Integer> ref = new ArrayList<>(); // last element = most recent
            return key -> {
                if (ref.remove((Integer) key)) {
                    ref.add(key);
                    return new Object[] {true, null};
                }
                Integer victim = ref.size() >= capacity ? ref.remove(ref.size() - 1) : null;
                ref.add(key);
                return new Object[] {false, victim};
            };
        });
    }

    @Test
    void lfuMatchesNaiveScanReference() {
        runAgainstReference(CacheAlgorithm.LFU, capacity -> {
            Map<Integer, int[]> ref = new HashMap<>(); // key -> {frequency, last use}
            int[] clock = {0};
            return key -> {
                clock[0]++;
                int[] e = ref.get(key);
                if (e != null) {
                    e[0]++;
                    e[1] = clock[0];
                    return new Object[] {true, null};
                }
                Integer victim = null;
                if (ref.size() >= capacity) {
                    for (Map.Entry<Integer, int[]> en : ref.entrySet()) {
                        if (victim == null || en.getValue()[0] < ref.get(victim)[0]
                                || en.getValue()[0] == ref.get(victim)[0] && en.getValue()[1] < ref.get(victim)[1]) {
                            victim = en.getKey();
                        }
                    }
                    ref.remove(victim);
                }
                ref.put(key, new int[] {1, clock[0]});
                return new Object[] {false, victim};
            };
        });
    }

    private interface Reference {
        Object[] access(int key);
    }

    private interface ReferenceFactory {
        Reference create(int capacity);
    }

    private static void runAgainstReference(CacheAlgorithm alg, ReferenceFactory factory) {
        Random rnd = new Random(1234);
        for (int round = 0; round < 300; round++) {
            int capacity = 1 + rnd.nextInt(8);
            int keys = 2 + rnd.nextInt(14);
            Cache<Integer> cache = CacheFactory.create(alg, capacity);
            Reference ref = factory.create(capacity);
            for (int i = 0; i < 400; i++) {
                int key = rnd.nextInt(keys);
                Object[] expected = ref.access(key);
                AccessResult<Integer> actual = cache.access(key);
                String where = alg + " round " + round + " step " + i + " key " + key;
                assertEquals(expected[0], actual.hit(), where);
                assertEquals(expected[1], actual.evicted(), where);
            }
        }
    }
}
