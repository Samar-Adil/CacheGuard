package com.cacheguard.model;

/**
 * One processed request.
 *
 * @param step    1-based position in the request sequence
 * @param key     requested block
 * @param hit     true if the block was already cached
 * @param evicted block that was evicted to make room, or {@code null}
 * @param slot    cache slot that holds {@code key} after this step
 * @param slots   cache contents after this step by slot ({@code null} = empty slot)
 */
public record StepRecord(int step, int key, boolean hit, Integer evicted, int slot, Integer[] slots) {

    public String outcome() {
        return hit ? "HIT" : "MISS";
    }

    /** Resident blocks in slot order, empty slots omitted. */
    public String contentsText() {
        StringBuilder sb = new StringBuilder();
        for (Integer k : slots) {
            if (k != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(k);
            }
        }
        return sb.toString();
    }
}
