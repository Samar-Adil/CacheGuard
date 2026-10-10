package com.cacheguard.controller;

import com.cacheguard.model.AppState;
import com.cacheguard.model.AppTheme;
import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.model.SessionRecord;
import com.cacheguard.model.Settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles every change to preferences and every import / export of them.
 */
public final class SettingsController {

    public enum ImportMode { MERGE, REPLACE }

    public record ImportSummary(boolean settingsApplied, int sessionsImported, int skipped) {
    }

    private final AppState state;
    private final StorageService storage;

    public SettingsController(AppState state, StorageService storage) {
        this.state = state;
        this.storage = storage;
    }

    public Settings current() {
        return state.settings();
    }

    public Path dataDirectory() {
        return storage.directory();
    }

    public void setTheme(AppTheme theme) {
        state.setSettings(state.settings().withTheme(theme));
    }

    public void toggleTheme() {
        setTheme(state.settings().theme().toggled());
    }

    public void setDefaultCapacity(int capacity) {
        state.setSettings(state.settings().withDefaultCapacity(capacity));
    }

    public void setDefaultAlgorithm(CacheAlgorithm algorithm) {
        state.setSettings(state.settings().withDefaultAlgorithm(algorithm));
    }

    public void resetToDefaults() {
        state.setSettings(Settings.defaults());
    }

    public void clearHistory() {
        state.clearHistory();
    }

    public int historySize() {
        return state.history().size();
    }

    public void exportTo(Path file, boolean includePreferences, boolean includeHistory) throws IOException {
        if (!includePreferences && !includeHistory) {
            throw new IOException("Choose at least one thing to export.");
        }
        storage.export(file, state.settings(), state.history(), includePreferences, includeHistory);
    }

    /**
     * Reads {@code file} and applies it. Imported preferences replace the current ones; imported
     * history is merged (duplicates by id are ignored) or replaces the current history.
     */
    public ImportSummary importFrom(Path file, ImportMode mode) throws IOException {
        StorageService.ImportedBundle bundle = storage.importFrom(file);
        boolean settingsApplied = false;
        if (bundle.settings().isPresent()) {
            state.setSettings(bundle.settings().get());
            settingsApplied = true;
        }
        int imported = 0;
        if (!bundle.sessions().isEmpty()) {
            int before = state.history().size();
            List<SessionRecord> next = new ArrayList<>();
            if (mode == ImportMode.MERGE) {
                next.addAll(state.history());
            }
            next.addAll(bundle.sessions());
            state.replaceHistory(next);
            imported = mode == ImportMode.MERGE
                    ? Math.max(0, state.history().size() - before)
                    : state.history().size();
        }
        return new ImportSummary(settingsApplied, imported, bundle.skipped());
    }
}
