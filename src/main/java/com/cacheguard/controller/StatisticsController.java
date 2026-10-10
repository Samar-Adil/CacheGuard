package com.cacheguard.controller;

import com.cacheguard.model.AppState;
import com.cacheguard.model.SessionRecord;
import com.cacheguard.model.StatisticsSummary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Serves aggregates of the session history and tells the view when they change.
 */
public final class StatisticsController {

    private final AppState state;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public StatisticsController(AppState state) {
        this.state = state;
        state.addPropertyChangeListener(AppState.HISTORY, e -> listeners.forEach(Runnable::run));
    }

    /** Called on the event thread whenever the history changes. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public StatisticsSummary summary() {
        return StatisticsSummary.of(state.history());
    }

    /** Oldest first. */
    public List<SessionRecord> history() {
        return state.history();
    }
}
