package com.cacheguard.model;

import java.util.Locale;

/**
 * The replacement policies CacheGuard can simulate.
 */
public enum CacheAlgorithm {

    LRU("LRU", "Least Recently Used",
            "Evicts the block that has gone unused for the longest time.",
            "HashMap + doubly linked list", "O(1)"),
    LFU("LFU", "Least Frequently Used",
            "Evicts the block with the fewest accesses; ties go to the least recently used.",
            "HashMap + frequency buckets", "O(1)"),
    FIFO("FIFO", "First-In, First-Out",
            "Evicts the oldest resident block, no matter how often it is used.",
            "ArrayDeque + HashSet", "O(1)"),
    MRU("MRU", "Most Recently Used",
            "Evicts the block that was touched last; strong on looping scans.",
            "HashMap + doubly linked list", "O(1)"),
    RANDOM("Random", "Random Replacement",
            "Evicts a random resident block (seeded, so every run is repeatable).",
            "ArrayList + index map", "O(1)");

    private final String shortName;
    private final String fullName;
    private final String description;
    private final String dataStructure;
    private final String complexity;

    CacheAlgorithm(String shortName, String fullName, String description,
                   String dataStructure, String complexity) {
        this.shortName = shortName;
        this.fullName = fullName;
        this.description = description;
        this.dataStructure = dataStructure;
        this.complexity = complexity;
    }

    public String shortName() {
        return shortName;
    }

    public String fullName() {
        return fullName;
    }

    public String description() {
        return description;
    }

    public String dataStructure() {
        return dataStructure;
    }

    /** Amortised cost of one access (hit or miss). */
    public String complexity() {
        return complexity;
    }

    /** Parses either the enum constant name or the short display name, ignoring case. */
    public static CacheAlgorithm parse(String text) {
        if (text != null) {
            String t = text.trim().toUpperCase(Locale.ROOT);
            for (CacheAlgorithm a : values()) {
                if (a.name().equals(t) || a.shortName.toUpperCase(Locale.ROOT).equals(t)) {
                    return a;
                }
            }
        }
        throw new IllegalArgumentException("Unknown algorithm: " + text);
    }

    /** Used by combo boxes. */
    @Override
    public String toString() {
        return shortName + " - " + fullName;
    }
}
