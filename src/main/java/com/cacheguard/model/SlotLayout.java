package com.cacheguard.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks which physical slot each cached block occupies, like a hardware cache would:
 * a miss fills the next free slot, or takes over the slot of the evicted block.
 * The eviction policies themselves know nothing about slots.
 */
final class SlotLayout {

    private final Integer[] slots;
    private final Map<Integer, Integer> positions = new HashMap<>();

    SlotLayout(int capacity) {
        this.slots = new Integer[capacity];
    }

    /** Applies the outcome of one access and returns the slot holding the requested key. */
    int apply(AccessResult<Integer> result) {
        if (result.hit()) {
            return positions.get(result.key());
        }
        int slot = result.evicted() != null
                ? positions.remove(result.evicted())
                : positions.size(); // slots only ever fill up, so the next free one is the count
        slots[slot] = result.key();
        positions.put(result.key(), slot);
        return slot;
    }

    Integer[] snapshot() {
        return slots.clone();
    }

    void clear() {
        Arrays.fill(slots, null);
        positions.clear();
    }
}
