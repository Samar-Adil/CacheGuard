package com.cacheguard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cacheguard.model.AppTheme;
import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.model.SessionRecord;
import com.cacheguard.model.Settings;
import com.cacheguard.util.FileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageServiceTest {

    private static SessionRecord session(String id, CacheAlgorithm alg) {
        return new SessionRecord(id, 1_700_000_000_000L, "simulation", alg, 4, 12, 5, 7, 3, "1, 2, 3", 1234L);
    }

    @Test
    void firstRunUsesDefaults(@TempDir Path dir) {
        StorageService s = new StorageService(new FileStore(dir));
        assertEquals(Settings.defaults(), s.loadSettings());
        assertTrue(s.loadSessions().isEmpty());
    }

    @Test
    void settingsAndSessionsSurviveARestart(@TempDir Path dir) throws IOException {
        Settings settings = new Settings(AppTheme.LIGHT, 8, CacheAlgorithm.LFU);
        List<SessionRecord> sessions = List.of(session("a", CacheAlgorithm.LRU), session("b", CacheAlgorithm.MRU));
        StorageService first = new StorageService(new FileStore(dir));
        first.saveSettings(settings);
        first.saveSessions(sessions);

        StorageService second = new StorageService(new FileStore(dir));
        assertEquals(settings, second.loadSettings());
        assertEquals(sessions, second.loadSessions());
    }

    @Test
    void corruptFilesAreSetAsideNotDeleted(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("settings.json"), "{ this is not json");
        Files.writeString(dir.resolve("sessions.json"), "[1,2,3]");
        StorageService s = new StorageService(new FileStore(dir));
        assertEquals(Settings.defaults(), s.loadSettings());
        assertTrue(s.loadSessions().isEmpty());
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).toList();
            assertTrue(names.stream().anyMatch(n -> n.startsWith("settings.json.corrupt-")), names.toString());
            assertTrue(names.stream().anyMatch(n -> n.startsWith("sessions.json.corrupt-")), names.toString());
        }
    }

    @Test
    void handEditedSettingsAreCorrected(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("settings.json"),
                "{\"theme\":\"LIGHT\",\"defaultCapacity\":500,\"defaultAlgorithm\":\"nonsense\"}");
        Settings s = new StorageService(new FileStore(dir)).loadSettings();
        assertEquals(AppTheme.LIGHT, s.theme());
        assertEquals(16, s.defaultCapacity());
        assertEquals(CacheAlgorithm.LRU, s.defaultAlgorithm());
    }

    @Test
    void exportThenImportRoundTrips(@TempDir Path dir) throws IOException {
        StorageService s = new StorageService(new FileStore(dir.resolve("data")));
        Settings settings = new Settings(AppTheme.LIGHT, 6, CacheAlgorithm.FIFO);
        List<SessionRecord> sessions = List.of(session("x", CacheAlgorithm.RANDOM), session("y", CacheAlgorithm.LFU));
        Path file = dir.resolve("export.json");
        s.export(file, settings, sessions, true, true);

        StorageService.ImportedBundle b = s.importFrom(file);
        assertEquals(settings, b.settings().orElseThrow());
        assertEquals(sessions, b.sessions());
        assertEquals(0, b.skipped());
    }

    @Test
    void exportCanLeaveThingsOut(@TempDir Path dir) throws IOException {
        StorageService s = new StorageService(new FileStore(dir));
        Path file = dir.resolve("prefs-only.json");
        s.export(file, Settings.defaults(), List.of(session("x", CacheAlgorithm.LRU)), true, false);
        StorageService.ImportedBundle b = s.importFrom(file);
        assertTrue(b.settings().isPresent());
        assertTrue(b.sessions().isEmpty());
    }

    @Test
    void importSkipsBadEntriesAndRejectsForeignFiles(@TempDir Path dir) throws IOException {
        StorageService s = new StorageService(new FileStore(dir));
        Path mixed = dir.resolve("mixed.json");
        Files.writeString(mixed, "{\"sessions\":[{\"id\":\"ok\",\"algorithm\":\"LRU\",\"hits\":1},"
                + "{\"id\":\"bad\",\"algorithm\":\"???\"},42,{\"algorithm\":\"LFU\"}]}");
        StorageService.ImportedBundle b = s.importFrom(mixed);
        assertEquals(1, b.sessions().size());
        assertEquals(3, b.skipped());

        Path foreign = dir.resolve("foreign.json");
        Files.writeString(foreign, "{\"hello\":\"world\"}");
        assertThrows(IOException.class, () -> s.importFrom(foreign));
        Path garbage = dir.resolve("garbage.json");
        Files.writeString(garbage, "not json at all");
        assertThrows(IOException.class, () -> s.importFrom(garbage));
    }

    @Test
    void settingsControllerMergeAndReplace(@TempDir Path dir) throws IOException {
        AppController app = new AppController(new StorageService(new FileStore(dir.resolve("app"))));
        SettingsController sc = app.settings();
        app.state().addSession(session("one", CacheAlgorithm.LRU));

        Path file = dir.resolve("in.json");
        StorageService helper = new StorageService(new FileStore(dir.resolve("other")));
        helper.export(file, Settings.defaults(), List.of(session("one", CacheAlgorithm.LRU),
                session("two", CacheAlgorithm.LFU)), false, true);

        SettingsController.ImportSummary merged = sc.importFrom(file, SettingsController.ImportMode.MERGE);
        assertEquals(1, merged.sessionsImported()); // "one" was already there
        assertEquals(2, sc.historySize());

        SettingsController.ImportSummary replaced = sc.importFrom(file, SettingsController.ImportMode.REPLACE);
        assertEquals(2, replaced.sessionsImported());
        assertEquals(2, sc.historySize());
        app.shutdown();
    }
}
