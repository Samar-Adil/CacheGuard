package com.cacheguard.controller;

import com.cacheguard.model.AppState;
import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.model.SessionRecord;
import com.cacheguard.model.Simulation;
import com.cacheguard.model.StepRecord;
import com.cacheguard.util.RequestParser;

import javax.swing.SwingWorker;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drives the interactive simulation: configure, step, run to the end, reset.
 * "Run all" executes on a {@link SwingWorker} so even multi-million request streams
 * never freeze the window. Call everything from the Swing event thread.
 */
public final class SimulationController {

    private static final Logger LOG = Logger.getLogger(SimulationController.class.getName());

    /** View callbacks; all are invoked on the event thread. */
    public interface Listener {
        /** A simulation was created or reset ({@code null} when the input is invalid). */
        default void onConfigured(Simulation simulation) {
        }

        default void onStep(Simulation simulation, StepRecord step) {
        }

        default void onRunStarted() {
        }

        default void onRunProgress(int percent) {
        }

        default void onRunFinished(Simulation simulation, boolean completed) {
        }

        /** The simulation reached the last request and was saved to the history. */
        default void onCompleted(Simulation simulation) {
        }

        default void onError(String message) {
        }
    }

    private final AppState state;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Simulation simulation;
    private RunWorker worker;
    private boolean recorded;

    public SimulationController(AppState state) {
        this.state = state;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public Simulation simulation() {
        return simulation;
    }

    public boolean isRunning() {
        return worker != null;
    }

    /**
     * Builds a fresh simulation from the user's input.
     *
     * @return true on success; on failure listeners get {@link Listener#onError} and there is no simulation
     */
    public boolean configure(CacheAlgorithm algorithm, int capacity, String sequenceText) {
        cancelRun();
        try {
            int[] requests = RequestParser.parse(sequenceText);
            simulation = new Simulation(algorithm, capacity, requests);
            recorded = false;
            listeners.forEach(l -> l.onConfigured(simulation));
            return true;
        } catch (IllegalArgumentException e) {
            simulation = null;
            recorded = false;
            listeners.forEach(l -> l.onConfigured(null));
            listeners.forEach(l -> l.onError(e.getMessage()));
            return false;
        }
    }

    public void stepForward() {
        if (simulation == null || isRunning() || !simulation.hasNext()) {
            return;
        }
        StepRecord rec = simulation.step();
        listeners.forEach(l -> l.onStep(simulation, rec));
        completeIfFinished();
    }

    public void runAll() {
        if (simulation == null || isRunning() || !simulation.hasNext()) {
            return;
        }
        worker = new RunWorker(simulation);
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int percent = (Integer) evt.getNewValue();
                listeners.forEach(l -> l.onRunProgress(percent));
            }
        });
        listeners.forEach(Listener::onRunStarted);
        worker.execute();
    }

    /** Asks a running "run all" to stop; the finished callback arrives once the worker has stopped. */
    public void cancelRun() {
        if (worker != null) {
            worker.requestCancel();
        }
    }

    public void reset() {
        cancelRun();
        if (simulation != null && worker == null) {
            simulation.reset();
            recorded = false;
            listeners.forEach(l -> l.onConfigured(simulation));
        }
    }

    private void completeIfFinished() {
        if (simulation != null && simulation.isFinished() && !recorded) {
            recorded = true;
            String sample = RequestParser.format(sampleOf(simulation), 24);
            state.addSession(SessionRecord.of(simulation.result(), SessionRecord.SOURCE_SIMULATION, sample));
            listeners.forEach(l -> l.onCompleted(simulation));
        }
    }

    private static int[] sampleOf(Simulation sim) {
        int n = Math.min(sim.total(), 24);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = sim.requestAt(i);
        }
        return out;
    }

    /**
     * Cancellation uses our own flag rather than {@code SwingWorker.cancel}, because cancel()
     * triggers {@code done()} immediately, while the background thread may still be stepping
     * the simulation the event thread is about to read.
     */
    private final class RunWorker extends SwingWorker<Boolean, Void> {
        private final Simulation target;
        private final AtomicBoolean cancelRequested = new AtomicBoolean();

        RunWorker(Simulation target) {
            this.target = target;
        }

        void requestCancel() {
            cancelRequested.set(true);
        }

        @Override
        protected Boolean doInBackground() {
            return target.runToEnd(
                    (done, total) -> setProgress((int) (done * 100L / total)),
                    cancelRequested::get);
        }

        @Override
        protected void done() {
            boolean completed = false;
            try {
                completed = get();
            } catch (ExecutionException e) {
                LOG.log(Level.SEVERE, "Simulation failed", e.getCause());
                String msg = "The simulation failed: " + e.getCause();
                listeners.forEach(l -> l.onError(msg));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
            boolean finished = completed;
            if (target == simulation) {
                listeners.forEach(l -> l.onRunFinished(simulation, finished));
                if (finished) {
                    completeIfFinished();
                }
            }
        }
    }
}
