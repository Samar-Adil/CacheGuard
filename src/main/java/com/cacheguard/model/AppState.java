package com.cacheguard.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The observable root of the application model: preferences plus session history.
 * Confined to the Swing event thread once the UI is running; observers are notified
 * with the property names {@link #SETTINGS} and {@link #HISTORY}.
 */
public final class AppState {

    public static final String SETTINGS = "settings";
    public static final String HISTORY = "history";
    public static final int MAX_HISTORY = 1000;

    private final PropertyChangeSupport events = new PropertyChangeSupport(this);
    private Settings settings;
    private List<SessionRecord> history;

    public AppState(Settings settings, Collection<SessionRecord> history) {
        this.settings = settings == null ? Settings.defaults() : settings;
        this.history = normalise(history);
    }

    public void addPropertyChangeListener(String property, PropertyChangeListener l) {
        events.addPropertyChangeListener(property, l);
    }

    public void removePropertyChangeListener(String property, PropertyChangeListener l) {
        events.removePropertyChangeListener(property, l);
    }

    public Settings settings() {
        return settings;
    }

    public void setSettings(Settings value) {
        Settings old = settings;
        settings = value;
        events.firePropertyChange(SETTINGS, old, value);
    }

    /** Oldest first, unmodifiable. */
    public List<SessionRecord> history() {
        return history;
    }

    public void addSession(SessionRecord session) {
        addSessions(List.of(session));
    }

    public void addSessions(Collection<SessionRecord> sessions) {
        if (sessions.isEmpty()) {
            return;
        }
        List<SessionRecord> merged = new ArrayList<>(history);
        merged.addAll(sessions);
        replaceHistory(merged);
    }

    /** Replaces the history, dropping duplicate ids and the oldest entries beyond {@link #MAX_HISTORY}. */
    public void replaceHistory(Collection<SessionRecord> sessions) {
        List<SessionRecord> old = history;
        history = normalise(sessions);
        events.firePropertyChange(HISTORY, old, history);
    }

    public void clearHistory() {
        if (!history.isEmpty()) {
            replaceHistory(List.of());
        }
    }

    private static List<SessionRecord> normalise(Collection<SessionRecord> sessions) {
        if (sessions == null) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<SessionRecord> list = new ArrayList<>();
        for (SessionRecord s : sessions) {
            if (seen.add(s.id())) {
                list.add(s);
            }
        }
        list.sort(Comparator.comparingLong(SessionRecord::timestamp));
        if (list.size() > MAX_HISTORY) {
            list = new ArrayList<>(list.subList(list.size() - MAX_HISTORY, list.size()));
        }
        return List.copyOf(list);
    }
}
