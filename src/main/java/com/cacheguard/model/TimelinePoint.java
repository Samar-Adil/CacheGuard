package com.cacheguard.model;

/**
 * Cumulative hit percentage after {@code position} requests.
 */
public record TimelinePoint(int position, double hitPercent) {
}
