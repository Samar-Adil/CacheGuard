package com.cacheguard.model;

/**
 * Outcome of one cache access.
 *
 * @param key     the requested key
 * @param hit     true if the key was already resident
 * @param evicted the key that was evicted to make room, or {@code null} if nothing was evicted
 */
public record AccessResult<K>(K key, boolean hit, K evicted) {

    public boolean evictionOccurred() {
        return evicted != null;
    }
}
