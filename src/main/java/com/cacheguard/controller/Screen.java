package com.cacheguard.controller;

/**
 * The application's top-level screens, in sidebar order.
 */
public enum Screen {
    DASHBOARD("Dashboard"),
    SIMULATION("Simulation"),
    COMPARISON("Comparison"),
    STATISTICS("Statistics"),
    SETTINGS("Settings");

    private final String title;

    Screen(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
