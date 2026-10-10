package com.cacheguard.controller;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import com.cacheguard.model.AppState;
import com.cacheguard.model.Settings;
import com.cacheguard.util.FileStore;
import com.cacheguard.util.ThemeManager;

/**
 * Composition root of the controller layer: owns the model, wires the screen controllers,
 * saves changes in the background and applies theme changes.
 */
public final class AppController {

    private static final Logger LOG = Logger.getLogger(AppController.class.getName());

    private final AppState state;
    private final StorageService storage;
    private final SimulationController simulation;
    private final ComparisonController comparison;
    private final StatisticsController statistics;
    private final SettingsController settings;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cacheguard-io");
        t.setDaemon(true);
        return t;
    });
    private final List<Consumer<Screen>> navigationListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> statusListeners = new CopyOnWriteArrayList<>();
    private Screen current = Screen.DASHBOARD;

    public AppController(StorageService storage) {
        this.storage = storage;
        this.state = new AppState(storage.loadSettings(), storage.loadSessions());
        this.simulation = new SimulationController(state);
        this.comparison = new ComparisonController(state);
        this.statistics = new StatisticsController(state);
        this.settings = new SettingsController(state, storage);

        state.addPropertyChangeListener(AppState.SETTINGS, e -> {
            Settings old = (Settings) e.getOldValue();
            Settings now = (Settings) e.getNewValue();
            if (old == null || old.theme() != now.theme()) {
                ThemeManager.apply(now.theme());
            }
            persist("settings", () -> storage.saveSettings(now));
        });
        state.addPropertyChangeListener(AppState.HISTORY, e -> {
            @SuppressWarnings("unchecked")
            var snapshot = (List<com.cacheguard.model.SessionRecord>) e.getNewValue();
            persist("history", () -> storage.saveSessions(snapshot));
        });
    }

    public static AppController bootstrap() {
        return new AppController(new StorageService(new FileStore(FileStore.defaultDirectory())));
    }

    public AppState state() {
        return state;
    }

    public SimulationController simulation() {
        return simulation;
    }

    public ComparisonController comparison() {
        return comparison;
    }

    public StatisticsController statistics() {
        return statistics;
    }

    public SettingsController settings() {
        return settings;
    }
    

    public Screen currentScreen() {
        return current;
    }

    public void navigate(Screen screen) {
        current = screen;
        navigationListeners.forEach(l -> l.accept(screen));
    }

    public void addNavigationListener(Consumer<Screen> l) {
        navigationListeners.add(l);
    }

    /** Receives short messages for the status bar, always on the event thread. */
    public void addStatusListener(Consumer<String> l) {
        statusListeners.add(l);
    }

    public void announce(String message) {
        statusListeners.forEach(l -> l.accept(message));
    }

    /** Lets queued saves finish (briefly) so nothing is lost when the window closes. */
    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(3, TimeUnit.SECONDS)) {
                LOG.warning("Timed out waiting for pending saves");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface IoTask {
        void run() throws IOException;
    }

    private void persist(String what, IoTask task) {
        if (io.isShutdown()) {
            return;
        }
        io.execute(() -> {
            try {
                task.run();
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not save " + what, e);
                SwingUtilities.invokeLater(() -> announce(
                        "Could not save your " + what + " to " + storage.directory() + ": " + e.getMessage()));
            }
        });
    }
}
