package com.cacheguard.model;

public enum AppTheme {
    DARK("Dark"),
    LIGHT("Light");

    private final String label;

    AppTheme(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public AppTheme toggled() {
        return this == DARK ? LIGHT : DARK;
    }

    public static AppTheme parse(String text, AppTheme fallback) {
        if (text != null) {
            for (AppTheme t : values()) {
                if (t.name().equalsIgnoreCase(text.trim())) {
                    return t;
                }
            }
        }
        return fallback;
    }
}
