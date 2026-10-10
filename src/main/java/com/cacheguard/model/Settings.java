package com.cacheguard.model;

import com.cacheguard.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User preferences. Immutable; use the {@code with...} methods to derive changed copies.
 * Out-of-range values are corrected on construction, so a hand-edited file can never
 * put the app into an invalid state.
 */
public record Settings(AppTheme theme, int defaultCapacity, CacheAlgorithm defaultAlgorithm) {

    public static final int MIN_CAPACITY = 2;
    public static final int MAX_CAPACITY = 16;
    public static final int DEFAULT_CAPACITY = 4;

    public Settings {
        theme = theme == null ? AppTheme.DARK : theme;
        defaultCapacity = Math.max(MIN_CAPACITY, Math.min(MAX_CAPACITY, defaultCapacity));
        defaultAlgorithm = defaultAlgorithm == null ? CacheAlgorithm.LRU : defaultAlgorithm;
    }

    public static Settings defaults() {
        return new Settings(AppTheme.DARK, DEFAULT_CAPACITY, CacheAlgorithm.LRU);
    }

    public Settings withTheme(AppTheme value) {
        return new Settings(value, defaultCapacity, defaultAlgorithm);
    }

    public Settings withDefaultCapacity(int value) {
        return new Settings(theme, value, defaultAlgorithm);
    }

    public Settings withDefaultAlgorithm(CacheAlgorithm value) {
        return new Settings(theme, defaultCapacity, value);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("theme", theme.name());
        m.put("defaultCapacity", defaultCapacity);
        m.put("defaultAlgorithm", defaultAlgorithm.name());
        return m;
    }

    /** Lenient: missing or invalid fields fall back to their defaults. */
    public static Settings fromMap(Map<String, Object> m) {
        Settings d = defaults();
        AppTheme theme = AppTheme.parse(Json.string(m, "theme", null), d.theme);
        int capacity = Json.intValue(m, "defaultCapacity", d.defaultCapacity);
        CacheAlgorithm algorithm;
        try {
            algorithm = CacheAlgorithm.parse(Json.string(m, "defaultAlgorithm", null));
        } catch (IllegalArgumentException e) {
            algorithm = d.defaultAlgorithm;
        }
        return new Settings(theme, capacity, algorithm);
    }
}
