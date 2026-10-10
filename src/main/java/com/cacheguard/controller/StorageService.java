package com.cacheguard.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cacheguard.model.SessionRecord;
import com.cacheguard.model.Settings;
import com.cacheguard.util.AppInfo;
import com.cacheguard.util.FileStore;
import com.cacheguard.util.Json;

/**
 * JSON persistence for settings and session history, plus import / export of both.
 * Safe to call from any thread; it holds no mutable state.
 */
public final class StorageService {

    private static final Logger LOG = Logger.getLogger(StorageService.class.getName());

    static final String SETTINGS_FILE = "settings.json";
    static final String SESSIONS_FILE = "sessions.json";
    static final int FORMAT_VERSION = 1;

    /** Content of an import file. {@code skipped} counts history entries that were unusable. */
    public record ImportedBundle(Optional<Settings> settings, List<SessionRecord> sessions, int skipped) {
    }

    private final FileStore store;

    public StorageService(FileStore store) {
        this.store = store;
    }

    public Path directory() {
        return store.directory();
    }

    // ------------------------------------------------------------------ app data

    /** Never throws: an unreadable file is set aside and defaults are used. */
    public Settings loadSettings() {
        try {
            Optional<String> text = store.read(SETTINGS_FILE);
            if (text.isPresent()) {
                return Settings.fromMap(Json.asObject(Json.parse(text.get())));
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read " + SETTINGS_FILE + ", using defaults", e);
            store.quarantine(SETTINGS_FILE);
        }
        return Settings.defaults();
    }

    /** Never throws: an unreadable file is set aside and the history starts empty. */
    public List<SessionRecord> loadSessions() {
        try {
            Optional<String> text = store.read(SESSIONS_FILE);
            if (text.isPresent()) {
                Object sessions = Json.asObject(Json.parse(text.get())).get("sessions");
                return readSessions(sessions == null ? List.of() : Json.asArray(sessions)).sessions();
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read " + SESSIONS_FILE + ", starting with an empty history", e);
            store.quarantine(SESSIONS_FILE);
        }
        return List.of();
    }

    public void saveSettings(Settings settings) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", FORMAT_VERSION);
        root.putAll(settings.toMap());
        store.write(SETTINGS_FILE, Json.pretty(root));
    }

    public void saveSessions(List<SessionRecord> sessions) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", FORMAT_VERSION);
        root.put("sessions", sessionMaps(sessions));
        store.write(SESSIONS_FILE, Json.pretty(root));
    }
    public void clearHistory() throws IOException {
    saveSessions(List.of());
    }
    // ------------------------------------------------------------------ import / export

    public void export(Path file, Settings settings, List<SessionRecord> sessions,
                       boolean includeSettings, boolean includeHistory) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("app", AppInfo.NAME);
        root.put("version", FORMAT_VERSION);
        root.put("exportedAt", Instant.now().toString());
        if (includeSettings) {
            root.put("settings", settings.toMap());
        }
        if (includeHistory) {
            root.put("sessions", sessionMaps(sessions));
        }
        FileStore.writeAtomically(file, Json.pretty(root));
    }

    /**
     * @throws IOException if the file cannot be read or is not a CacheGuard export
     */
    public ImportedBundle importFrom(Path file) throws IOException {
        Map<String, Object> root;
        try {
            root = Json.asObject(Json.parse(java.nio.file.Files.readString(file)));
        } catch (Json.JsonException e) {
            throw new IOException("This is not a valid JSON file: " + e.getMessage(), e);
        }
        Object rawSettings = root.get("settings");
        Object rawSessions = root.get("sessions");
        if (rawSettings == null && rawSessions == null) {
            throw new IOException("This file contains neither settings nor session history.");
        }
        try {
            Optional<Settings> settings = rawSettings == null
                    ? Optional.empty()
                    : Optional.of(Settings.fromMap(Json.asObject(rawSettings)));
            ImportedBundle sessions = rawSessions == null
                    ? new ImportedBundle(Optional.empty(), List.of(), 0)
                    : readSessions(Json.asArray(rawSessions));
            return new ImportedBundle(settings, sessions.sessions(), sessions.skipped());
        } catch (Json.JsonException e) {
            throw new IOException("Unexpected file structure: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static List<Map<String, Object>> sessionMaps(List<SessionRecord> sessions) {
        List<Map<String, Object>> out = new ArrayList<>(sessions.size());
        for (SessionRecord s : sessions) {
            out.add(s.toMap());
        }
        return out;
    }

    private static ImportedBundle readSessions(List<Object> raw) {
        List<SessionRecord> sessions = new ArrayList<>();
        int skipped = 0;
        for (Object item : raw) {
            try {
                sessions.add(SessionRecord.fromMap(Json.asObject(item)));
            } catch (RuntimeException e) {
                skipped++;
            }
        }
        return new ImportedBundle(Optional.empty(), sessions, skipped);
    }
}
