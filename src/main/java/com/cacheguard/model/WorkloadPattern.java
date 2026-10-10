package com.cacheguard.model;

/**
 * Synthetic access patterns for stress-testing the algorithms.
 */
public enum WorkloadPattern {
    UNIFORM("Uniform random", "Every block is equally likely; no policy can do much better than chance."),
    ZIPF("Skewed (Zipf)", "A few hot blocks receive most requests, like real web or database traffic."),
    LOOP("Sequential loop", "Cycles through all blocks in order; a classic worst case for LRU and FIFO."),
    SCAN_HOT("Hot set + scan", "A small hot set polluted by a long one-time scan; tests scan resistance."),
    PHASES("Shifting working set", "The working set drifts over time; tests how quickly a policy adapts.");

    private final String label;
    private final String description;

    WorkloadPattern(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return label;
    }
}
