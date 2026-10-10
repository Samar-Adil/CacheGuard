package com.cacheguard.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimulationTest {

    private static final int[] TEXTBOOK = {1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5};

    @Test
    void stepsReportHitsMissesAndEvictions() {
        Simulation s = new Simulation(CacheAlgorithm.LRU, 3, TEXTBOOK);
        StepRecord first = s.step();
        assertEquals(1, first.step());
        assertFalse(first.hit());
        assertNull(first.evicted());
        assertEquals(0, first.slot());
        s.step();
        s.step();
        StepRecord fourth = s.step(); // request 4 with cache {1,2,3} full -> evicts 1
        assertEquals(1, fourth.evicted());
        assertEquals(0, fourth.slot()); // takes over the evicted block's slot
        assertEquals("4, 2, 3", fourth.contentsText());
        assertEquals(4, s.position());
        assertTrue(s.hasNext());
    }

    @Test
    void runToEndMatchesSteppingAndRecordsATrace() {
        Simulation stepped = new Simulation(CacheAlgorithm.FIFO, 3, TEXTBOOK);
        while (stepped.hasNext()) {
            stepped.step();
        }
        Simulation ran = new Simulation(CacheAlgorithm.FIFO, 3, TEXTBOOK);
        assertTrue(ran.runToEnd(null, () -> false));
        assertEquals(stepped.stats(), ran.stats());
        assertEquals(9, ran.stats().misses());
        assertEquals(12, ran.steps().size());
        assertTrue(ran.isFinished());
        assertThrows(NoSuchElementException.class, ran::step);
    }

    @Test
    void resetRestoresTheInitialState() {
        Simulation s = new Simulation(CacheAlgorithm.LFU, 3, TEXTBOOK);
        s.runToEnd(null, null);
        s.reset();
        assertEquals(0, s.position());
        assertEquals(CacheStats.EMPTY, s.stats());
        assertTrue(s.steps().isEmpty());
        assertTrue(s.timeline().isEmpty());
        s.runToEnd(null, null);
        assertEquals(12, s.stats().total());
    }

    @Test
    void cancellationStopsEarlyAndCanBeResumed() {
        int[] big = new int[100_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = i % 50;
        }
        Simulation s = new Simulation(CacheAlgorithm.LRU, 8, big);
        AtomicInteger polls = new AtomicInteger();
        boolean done = s.runToEnd(null, () -> polls.incrementAndGet() > 3);
        assertFalse(done);
        assertTrue(s.position() > 0 && s.position() < big.length);
        assertTrue(s.runToEnd(null, () -> false));
        assertEquals(big.length, s.stats().total());
    }

    @Test
    void longSequencesKeepNoTraceButExactTotals() {
        int[] big = new int[Simulation.MAX_DETAILED_STEPS + 1];
        for (int i = 0; i < big.length; i++) {
            big[i] = i % 3;
        }
        Simulation s = new Simulation(CacheAlgorithm.LRU, 3, big);
        assertFalse(s.isDetailed());
        s.runToEnd(null, null);
        assertTrue(s.steps().isEmpty());
        assertEquals(3, s.stats().misses());
        assertEquals(big.length - 3L, s.stats().hits());
        assertFalse(s.timeline().isEmpty());
        assertEquals(big.length, s.timeline().get(s.timeline().size() - 1).position());
    }

    @Test
    void progressIsReported() {
        int[] big = new int[20_000];
        Simulation s = new Simulation(CacheAlgorithm.LRU, 4, big);
        AtomicInteger last = new AtomicInteger(-1);
        s.runToEnd((done, total) -> last.set(done), null);
        assertEquals(big.length, last.get());
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> new Simulation(CacheAlgorithm.LRU, 3, new int[0]));
    }

    @Test
    void slotsStayStableAcrossHits() {
        Simulation s = new Simulation(CacheAlgorithm.LRU, 3, new int[] {7, 8, 9, 7, 8});
        s.runToEnd(null, null);
        Integer[] slots = s.slots();
        assertEquals(7, slots[0]);
        assertEquals(8, slots[1]);
        assertEquals(9, slots[2]);
    }
}
