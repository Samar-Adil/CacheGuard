package com.cacheguard.controller;

import com.cacheguard.model.AppState;
import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.model.SessionRecord;
import com.cacheguard.model.Simulation;
import com.cacheguard.model.SimulationResult;
import com.cacheguard.util.RequestParser;

import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replays one request stream through all algorithms at once. Each algorithm runs on its own
 * pool thread while a {@link SwingWorker} coordinates, reports progress and delivers results.
 */
public final class ComparisonController {

    private static final Logger LOG = Logger.getLogger(ComparisonController.class.getName());

    public static final int MIN_CAPACITY = 2;
    public static final int MAX_CAPACITY = 4096;

    /** Everything the comparison screen needs; results are in algorithm order. */
    public record Report(int capacity, int requestCount, String workload,
                         List<SimulationResult> results, long elapsedMillis) {

        /** Most hits wins; ties go to fewer evictions, then to algorithm order. */
        public Optional<SimulationResult> best() {
            return results.stream().max(Comparator
                    .comparingLong((SimulationResult r) -> r.stats().hits())
                    .thenComparing(r -> -r.stats().evictions()));
        }
    }

    /** View callbacks; all are invoked on the event thread. */
    public interface Listener {
        default void onStarted() {
        }

        default void onProgress(int percent) {
        }

        default void onResults(Report report) {
        }

        default void onCancelled() {
        }

        default void onError(String message) {
        }
    }

    private final AppState state;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private CompareWorker worker;

    public ComparisonController(AppState state) {
        this.state = state;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public boolean isRunning() {
        return worker != null;
    }

    /**
     * Starts a comparison.
     *
     * @param workload  short description saved with the history entries
     * @param requests  builds the request stream; runs on the worker thread, so parsing or
     *                  generating millions of requests does not block the window. May throw
     *                  {@link IllegalArgumentException} with a user-facing message.
     */
    public void run(int capacity, String workload, Supplier<int[]> requests) {
        if (worker != null) {
            return;
        }
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            listeners.forEach(l -> l.onError("Cache capacity must be between " + MIN_CAPACITY
                    + " and " + MAX_CAPACITY + " blocks."));
            return;
        }
        worker = new CompareWorker(capacity, workload, requests);
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int percent = (Integer) evt.getNewValue();
                listeners.forEach(l -> l.onProgress(percent));
            }
        });
        listeners.forEach(Listener::onStarted);
        worker.execute();
    }

    public void cancel() {
        if (worker != null) {
            worker.cancelRequested.set(true);
        }
    }

    private final class CompareWorker extends SwingWorker<Report, Void> {
        private final int capacity;
        private final String workload;
        private final Supplier<int[]> requestSupplier;
        private final AtomicBoolean cancelRequested = new AtomicBoolean();
        private int[] requests;

        CompareWorker(int capacity, String workload, Supplier<int[]> requestSupplier) {
            this.capacity = capacity;
            this.workload = workload;
            this.requestSupplier = requestSupplier;
        }

        @Override
        protected Report doInBackground() throws Exception {
            long start = System.nanoTime();
            requests = requestSupplier.get();
            CacheAlgorithm[] algorithms = CacheAlgorithm.values();
            long totalWork = (long) requests.length * algorithms.length;
            AtomicLong processed = new AtomicLong();
            ExecutorService pool = Executors.newFixedThreadPool(
                    Math.min(algorithms.length, Math.max(1, Runtime.getRuntime().availableProcessors())),
                    runnable -> {
                        Thread t = new Thread(runnable, "cacheguard-compare");
                        t.setDaemon(true);
                        return t;
                    });
            try {
                List<Future<SimulationResult>> futures = new ArrayList<>();
                for (CacheAlgorithm algorithm : algorithms) {
                    futures.add(pool.submit(() -> {
                        Simulation sim = new Simulation(algorithm, capacity, requests);
                        int[] last = {0};
                        boolean done = sim.runToEnd((p, t) -> {
                            long delta = p - last[0];
                            last[0] = p;
                            long all = processed.addAndGet(delta);
                            setProgress((int) Math.min(100, all * 100 / totalWork));
                        }, cancelRequested::get);
                        return done ? sim.result() : null;
                    }));
                }
                List<SimulationResult> results = new ArrayList<>();
                for (Future<SimulationResult> f : futures) {
                    SimulationResult r = f.get();
                    if (r == null) {
                        throw new CancellationException();
                    }
                    results.add(r);
                }
                return new Report(capacity, requests.length, workload, List.copyOf(results),
                        (System.nanoTime() - start) / 1_000_000);
            } finally {
                pool.shutdownNow();
            }
        }

        @Override
        protected void done() {
            worker = null;
            try {
                Report report = get();
                String sample = RequestParser.format(requests, 24);
                List<SessionRecord> sessions = new ArrayList<>();
                for (SimulationResult r : report.results()) {
                    sessions.add(SessionRecord.of(r, SessionRecord.SOURCE_COMPARISON, sample));
                }
                state.addSessions(sessions);
                listeners.forEach(l -> l.onResults(report));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof CancellationException) {
                    listeners.forEach(Listener::onCancelled);
                } else if (cause instanceof IllegalArgumentException) {
                    String msg = cause.getMessage();
                    listeners.forEach(l -> l.onError(msg));
                } else if (cause instanceof OutOfMemoryError) {
                    listeners.forEach(l -> l.onError("Not enough memory for this many requests. Try a smaller size."));
                } else {
                    LOG.log(Level.SEVERE, "Comparison failed", cause);
                    String msg = "The comparison failed: " + cause;
                    listeners.forEach(l -> l.onError(msg));
                }
            } catch (CancellationException e) {
                listeners.forEach(Listener::onCancelled);
            }
        }
    }
}
