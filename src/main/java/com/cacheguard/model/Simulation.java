package com.cacheguard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * One algorithm replaying one request sequence, either step by step or all at once.
 *
 * <p>Not thread-safe: an instance must be driven by a single thread at a time. The request
 * array is shared, never copied or modified, so several simulations can replay the same stream.
 *
 * <p>Sequences of up to {@link #MAX_DETAILED_STEPS} requests keep a full per-step trace.
 * Longer ones keep only counters and a sampled hit-ratio timeline, so a multi-million
 * request run needs a few kilobytes rather than gigabytes.
 */
public final class Simulation {

    public static final int MAX_DETAILED_STEPS = 10_000;
    private static final int TIMELINE_POINTS = 400;
    private static final int PROGRESS_MASK = 4096 - 1;

    /** Receives progress while running; called on the thread that runs the simulation. */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int processed, int total);
    }

    private final CacheAlgorithm algorithm;
    private final int capacity;
    private final int[] requests;
    private final Cache<Integer> cache;
    private final SlotLayout layout;
    private final boolean detailed;
    private final int sampleEvery;
    private final List<StepRecord> steps = new ArrayList<>();
    private final List<TimelinePoint> timeline = new ArrayList<>();
    private int cursor;
    private long hits;
    private long elapsedNanos;

    public Simulation(CacheAlgorithm algorithm, int capacity, int[] requests) {
        if (requests == null || requests.length == 0) {
            throw new IllegalArgumentException("A simulation needs at least one request");
        }
        this.algorithm = algorithm;
        this.capacity = capacity;
        this.requests = requests;
        this.cache = CacheFactory.create(algorithm, capacity);
        this.layout = new SlotLayout(capacity);
        this.detailed = requests.length <= MAX_DETAILED_STEPS;
        this.sampleEvery = Math.max(1, requests.length / TIMELINE_POINTS);
    }

    public CacheAlgorithm algorithm() {
        return algorithm;
    }

    public int capacity() {
        return capacity;
    }

    public int total() {
        return requests.length;
    }

    /** Number of requests processed so far. */
    public int position() {
        return cursor;
    }

    public boolean hasNext() {
        return cursor < requests.length;
    }

    public boolean isFinished() {
        return cursor >= requests.length;
    }

    /** The next request to be processed. */
    public int peekNext() {
        if (!hasNext()) {
            throw new NoSuchElementException("Simulation finished");
        }
        return requests[cursor];
    }

    public int requestAt(int index) {
        return requests[index];
    }

    /** Whether a per-step trace is kept (true for sequences up to {@link #MAX_DETAILED_STEPS}). */
    public boolean isDetailed() {
        return detailed;
    }

    public CacheStats stats() {
        return cache.stats();
    }

    public List<StepRecord> steps() {
        return Collections.unmodifiableList(steps);
    }

    public List<TimelinePoint> timeline() {
        return Collections.unmodifiableList(timeline);
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    /** Current contents by slot ({@code null} = empty). */
    public Integer[] slots() {
        return layout.snapshot();
    }

    /** Policy-specific note for the block in a slot, such as "freq 3"; empty for an empty slot. */
    public String annotation(int slot) {
        Integer key = layout.snapshot()[slot];
        return key == null ? "" : cache.annotation(key);
    }

    public Optional<Integer> nextVictim() {
        return cache.nextVictim();
    }

    /** Processes one request and returns what happened. */
    public StepRecord step() {
        if (!hasNext()) {
            throw new NoSuchElementException("Simulation finished");
        }
        long t0 = System.nanoTime();
        StepRecord rec = advance(true);
        elapsedNanos += System.nanoTime() - t0;
        return rec;
    }

    /**
     * Processes all remaining requests.
     *
     * @param progress  optional progress callback
     * @param cancelled polled regularly; return true to stop early
     * @return true if the end of the sequence was reached, false if cancelled
     */
    public boolean runToEnd(ProgressListener progress, BooleanSupplier cancelled) {
        long t0 = System.nanoTime();
        try {
            int total = requests.length;
            while (cursor < total) {
                if ((cursor & PROGRESS_MASK) == 0) {
                    if (cancelled != null && cancelled.getAsBoolean()) {
                        return false;
                    }
                    if (progress != null) {
                        progress.onProgress(cursor, total);
                    }
                }
                advance(false);
            }
            if (progress != null) {
                progress.onProgress(total, total);
            }
            return true;
        } finally {
            elapsedNanos += System.nanoTime() - t0;
        }
    }

    /** Back to the start with an empty cache and zeroed counters. */
    public void reset() {
        cache.reset();
        layout.clear();
        steps.clear();
        timeline.clear();
        cursor = 0;
        hits = 0;
        elapsedNanos = 0;
    }

    public SimulationResult result() {
        return new SimulationResult(algorithm, capacity, requests.length, cache.stats(),
                List.copyOf(timeline), elapsedNanos);
    }

    private StepRecord advance(boolean forceRecord) {
        int key = requests[cursor];
        AccessResult<Integer> r = cache.access(key);
        int slot = layout.apply(r);
        cursor++;
        if (r.hit()) {
            hits++;
        }
        if (cursor % sampleEvery == 0 || cursor == requests.length) {
            timeline.add(new TimelinePoint(cursor, 100.0 * hits / cursor));
        }
        if (detailed || forceRecord) {
            StepRecord rec = new StepRecord(cursor, key, r.hit(), r.evicted(), slot, layout.snapshot());
            if (detailed) {
                steps.add(rec);
            }
            return rec;
        }
        return null;
    }
}
