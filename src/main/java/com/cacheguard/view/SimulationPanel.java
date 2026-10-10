package com.cacheguard.view;

import com.cacheguard.controller.AppController;
import com.cacheguard.controller.SimulationController;
import com.cacheguard.model.AppState;
import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.model.CacheStats;
import com.cacheguard.model.Settings;
import com.cacheguard.model.Simulation;
import com.cacheguard.model.StepRecord;
import com.cacheguard.model.WorkloadGenerator;
import com.cacheguard.model.WorkloadPattern;
import com.cacheguard.util.RequestParser;
import com.cacheguard.view.components.CacheBlocksPanel;
import com.cacheguard.view.components.RequestStripPanel;
import com.cacheguard.view.components.RoundedPanel;
import com.cacheguard.view.components.StatCard;
import com.cacheguard.view.components.StatusBadge;
import com.cacheguard.view.components.TableRenderers;
import com.cacheguard.view.components.Ui;
import com.cacheguard.util.ThemeManager;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The interactive simulation: pick a policy and capacity, enter requests, then step through
 * them or run them all, watching hits, misses and evictions happen block by block.
 */
final class SimulationPanel extends ScreenPanel {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_SEQUENCE = "1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5";

    /** A ready-made lesson. */
    private record Example(String title, String sequence, int capacity, CacheAlgorithm algorithm, String lesson) {
        @Override
        public String toString() {
            return title;
        }
    }

    private static final Example NO_EXAMPLE = new Example("Load an example...", "", 0, null, "");
    private static final Example[] EXAMPLES = {
        NO_EXAMPLE,
        new Example("Belady's anomaly (FIFO)", "1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5", 3, CacheAlgorithm.FIFO,
                "FIFO with 3 blocks misses 9 times. Raise the capacity to 4 and it gets worse: 10 misses."),
        new Example("Loop bigger than the cache (MRU)", "1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5",
                4, CacheAlgorithm.MRU,
                "5 blocks cycling through 4 slots: LRU, LFU and FIFO never hit. MRU hits 12 of 20."),
        new Example("A hot block (LFU)", "1, 1, 1, 2, 3, 4, 1, 2, 3, 4, 1, 5, 6, 7, 1, 2, 3, 1", 3,
                CacheAlgorithm.LFU,
                "Block 1 is requested often. LFU keeps it (6 hits); LRU throws it out after a lull (3 hits)."),
    };

    private final transient AppController app;
    private final transient SimulationController controller;

    private final JSlider capacity = new JSlider(Settings.MIN_CAPACITY, Settings.MAX_CAPACITY, Settings.DEFAULT_CAPACITY);
    private final JLabel capacityLabel = new JLabel();
    private final JComboBox<CacheAlgorithm> algorithm = new JComboBox<>(CacheAlgorithm.values());
    private final JLabel algorithmInfo = Ui.muted(" ");
    private final JTextField sequence = new JTextField(DEFAULT_SEQUENCE);
    private final JLabel error = Ui.errorLabel();
    private final JComboBox<Example> examples = new JComboBox<>(EXAMPLES);
    private final JComboBox<WorkloadPattern> pattern = new JComboBox<>(WorkloadPattern.values());
    private final JSpinner length = new JSpinner(new SpinnerNumberModel(40, 5, Simulation.MAX_DETAILED_STEPS, 5));
    private final JButton generate = Ui.button("Generate");
    private final JButton load = Ui.button("Load");
    private final JButton step = Ui.primaryButton("Step Forward");
    private final JButton run = Ui.button("Run All");
    private final JButton reset = Ui.button("Reset");
    private final JProgressBar progress = new JProgressBar(0, 100);

    private final CacheBlocksPanel blocks = new CacheBlocksPanel();
    private final RequestStripPanel strip = new RequestStripPanel();
    private final StatusBadge badge = new StatusBadge();
    private final JLabel stepLabel = Ui.muted("Step 0 / 0", 13f);
    private final JLabel message = new JLabel(" ");
    private final StatCard hits = new StatCard("Hits");
    private final StatCard misses = new StatCard("Misses");
    private final StatCard ratio = new StatCard("Hit ratio");
    private final StatCard evictions = new StatCard("Evictions");
    private final TraceModel traceModel = new TraceModel();
    private final JTable trace = new JTable(traceModel);
    private final JLabel traceNote = Ui.muted(" ");

    private String appliedKey = "";
    private boolean syncing;
    private Settings lastSettings;

    SimulationPanel(AppController app) {
        super("Simulation", "Step through a request sequence and watch the cache decide what to keep");
        this.app = app;
        this.controller = app.simulation();
        this.lastSettings = app.state().settings();

        capacity.setValue(lastSettings.defaultCapacity());
        algorithm.setSelectedItem(lastSettings.defaultAlgorithm());

        addRow(buildControls());
        addRow(buildCacheCard());
        addRow(buildStatsRow());
        addRow(buildTraceCard());
        wireEvents();
        bindKeys();

        controller.addListener(new SimulationListener());
        app.state().addPropertyChangeListener(AppState.SETTINGS, e -> onSettingsChanged());
        updateCapacityLabel();
        updateAlgorithmInfo();
        applyConfig();
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildControls() {
        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 4, 12);

        capacity.setMajorTickSpacing(2);
        capacity.setMinorTickSpacing(1);
        capacity.setPaintTicks(true);
        capacity.setSnapToTicks(true);
        capacity.setPreferredSize(new Dimension(230, capacity.getPreferredSize().height));
        capacity.setToolTipText("Number of blocks the cache can hold (2 to 16)");
        capacityLabel.setFont(capacityLabel.getFont().deriveFont(java.awt.Font.BOLD));
        algorithm.setToolTipText("Replacement policy");
        sequence.setToolTipText("Block numbers separated by commas, spaces or semicolons");
        Ui.placeholder(sequence, "e.g. 1, 2, 3, 4, 1, 2, 5");
        length.setToolTipText("How many requests to generate");
        pattern.setToolTipText("Access pattern for generated requests");
        examples.setToolTipText("Pre-built sequences that show how the policies differ");
        progress.setStringPainted(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(140, progress.getPreferredSize().height));

        // Row 0: algorithm + capacity
        c.gridx = 0;
        c.gridy = 0;
        card(card, Ui.muted("Algorithm"), c);
        c.gridx = 1;
        card(card, algorithm, c);
        c.gridx = 2;
        card(card, Ui.muted("Cache capacity"), c);
        c.gridx = 3;
        card(card, capacity, c);
        c.gridx = 4;
        c.weightx = 1;
        card(card, capacityLabel, c);
        c.weightx = 0;

        // Row 1: algorithm description
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 4;
        card(card, algorithmInfo, c);
        c.gridwidth = 1;

        // Row 2: sequence
        c.gridx = 0;
        c.gridy = 2;
        card(card, Ui.muted("Requests"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        card(card, sequence, c);
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = 4;
        card(card, load, c);

        // Row 3: validation message
        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 4;
        card(card, error, c);
        c.gridwidth = 1;

        // Row 4: examples and generator
        JPanel helpers = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        helpers.setOpaque(false);
        helpers.add(examples);
        helpers.add(Box.createHorizontalStrut(12));
        helpers.add(Ui.muted("Generate"));
        helpers.add(pattern);
        helpers.add(length);
        helpers.add(Ui.muted("requests"));
        helpers.add(generate);
        c.gridx = 1;
        c.gridy = 4;
        c.gridwidth = 4;
        card(card, helpers, c);
        c.gridwidth = 1;

        // Row 5: actions
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(step);
        actions.add(run);
        actions.add(reset);
        actions.add(progress);
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 5;
        c.insets = new Insets(14, 0, 0, 0);
        card(card, actions, c);
        return card;
    }

    private static void card(JPanel p, JComponent comp, GridBagConstraints c) {
        p.add(comp, c);
    }

    private JComponent buildCacheCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 8));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.section("Cache blocks"), BorderLayout.WEST);
        JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(stepLabel);
        right.add(badge);
        head.add(right, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.add(blocks, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(strip, BorderLayout.CENTER);
        message.setFont(message.getFont().deriveFont(13.5f));
        south.add(message, BorderLayout.SOUTH);
        center.add(south, BorderLayout.SOUTH);
        card.add(center, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 16, 0));
        row.setOpaque(false);
        hits.setPreferredSize(new Dimension(160, 96));
        row.add(hits);
        row.add(misses);
        row.add(ratio);
        row.add(evictions);
        return row;
    }

    private JComponent buildTraceCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.section("Step-by-step trace"), BorderLayout.WEST);
        head.add(traceNote, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        Ui.style(trace);
        trace.setEnabled(false);
        trace.getColumnModel().getColumn(0).setCellRenderer(TableRenderers.number());
        trace.getColumnModel().getColumn(1).setCellRenderer(TableRenderers.number());
        trace.getColumnModel().getColumn(2).setCellRenderer(TableRenderers.outcome());
        trace.getColumnModel().getColumn(0).setPreferredWidth(60);
        trace.getColumnModel().getColumn(1).setPreferredWidth(80);
        trace.getColumnModel().getColumn(2).setPreferredWidth(80);
        trace.getColumnModel().getColumn(3).setPreferredWidth(90);
        trace.getColumnModel().getColumn(4).setPreferredWidth(360);
        JScrollPane sp = new JScrollPane(trace);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setPreferredSize(new Dimension(400, 230));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------ input handling

    private void wireEvents() {
        capacity.addChangeListener(e -> {
            updateCapacityLabel();
            if (!capacity.getValueIsAdjusting() && !syncing) {
                applyConfig();
            }
        });
        algorithm.addActionListener(e -> {
            updateAlgorithmInfo();
            if (!syncing) {
                applyConfig();
            }
        });
        sequence.addActionListener(e -> applyConfig());
        load.addActionListener(e -> applyConfig());
        step.addActionListener(e -> {
            if (ensureConfigured()) {
                controller.stepForward();
            }
        });
        run.addActionListener(e -> {
            if (controller.isRunning()) {
                controller.cancelRun();
            } else if (ensureConfigured()) {
                controller.runAll();
            }
        });
        reset.addActionListener(e -> {
            if (ensureConfigured()) {
                controller.reset();
            }
        });
        examples.addActionListener(e -> loadExample());
        generate.addActionListener(e -> generateRequests());
    }

    private void bindKeys() {
        bind("control RIGHT", "step", step);
        bind("control ENTER", "run", run);
        bind("control R", "reset", reset);
    }

    private void bind(String keystroke, String name, JButton button) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keystroke), name);
        getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (button.isEnabled() && isShowing()) {
                    button.doClick(0);
                }
            }
        });
    }

    private void loadExample() {
        Example ex = (Example) examples.getSelectedItem();
        if (ex == null || ex == NO_EXAMPLE) {
            return;
        }
        syncing = true;
        try {
            sequence.setText(ex.sequence());
            capacity.setValue(ex.capacity());
            algorithm.setSelectedItem(ex.algorithm());
        } finally {
            syncing = false;
        }
        updateCapacityLabel();
        updateAlgorithmInfo();
        applyConfig();
        message.setText(ex.lesson());
        examples.setSelectedItem(NO_EXAMPLE);
    }

    private void generateRequests() {
        int n = (Integer) length.getValue();
        int cap = capacity.getValue();
        WorkloadPattern p = (WorkloadPattern) pattern.getSelectedItem();
        int keySpace = Math.max(6, cap * 2 + 1);
        int[] reqs = WorkloadGenerator.generate(p, n, keySpace, System.nanoTime());
        sequence.setText(RequestParser.format(reqs, Integer.MAX_VALUE));
        applyConfig();
        message.setText(p.description());
    }

    private String currentKey() {
        return algorithm.getSelectedItem() + "|" + capacity.getValue() + "|" + sequence.getText();
    }

    private boolean applyConfig() {
        appliedKey = currentKey();
        return controller.configure((CacheAlgorithm) algorithm.getSelectedItem(), capacity.getValue(),
                sequence.getText());
    }

    /** Applies pending edits, so Step / Run always use what is on screen. */
    private boolean ensureConfigured() {
        if (controller.simulation() == null || !appliedKey.equals(currentKey())) {
            return applyConfig();
        }
        return true;
    }

    private void updateCapacityLabel() {
        capacityLabel.setText(capacity.getValue() + " blocks");
    }

    private void updateAlgorithmInfo() {
        CacheAlgorithm a = (CacheAlgorithm) algorithm.getSelectedItem();
        algorithmInfo.setText(a == null ? " " : a.description() + "  [" + a.dataStructure() + ", "
                + a.complexity() + " per access]");
    }

    private void onSettingsChanged() {
        Settings now = app.state().settings();
        if (lastSettings.defaultCapacity() != now.defaultCapacity()
                || lastSettings.defaultAlgorithm() != now.defaultAlgorithm()) {
            syncing = true;
            try {
                capacity.setValue(now.defaultCapacity());
                algorithm.setSelectedItem(now.defaultAlgorithm());
            } finally {
                syncing = false;
            }
            updateCapacityLabel();
            updateAlgorithmInfo();
            applyConfig();
        }
        lastSettings = now;
    }

    // ------------------------------------------------------------------ rendering

    private void updateButtons() {
        Simulation sim = controller.simulation();
        boolean running = controller.isRunning();
        boolean canAdvance = sim != null && sim.hasNext() && !running;
        step.setEnabled(canAdvance);
        run.setEnabled(running || canAdvance);
        run.setText(running ? "Cancel" : "Run All");
        reset.setEnabled(sim != null && !running && sim.position() > 0);
        capacity.setEnabled(!running);
        algorithm.setEnabled(!running);
        sequence.setEnabled(!running);
        load.setEnabled(!running);
        examples.setEnabled(!running);
        pattern.setEnabled(!running);
        length.setEnabled(!running);
        generate.setEnabled(!running);
    }

    private void renderState(Simulation sim) {
        Integer[] slots = sim.slots();
        String[] notes = new String[slots.length];
        for (int i = 0; i < slots.length; i++) {
            notes[i] = sim.annotation(i);
        }
        Optional<Integer> victim = sim.nextVictim();
        blocks.showState(slots, notes, victim.orElse(null));
        CacheStats s = sim.stats();
        hits.set(String.format("%,d", s.hits()), null).setValueColor(ThemeManager.palette().hit());
        misses.set(String.format("%,d", s.misses()), null).setValueColor(ThemeManager.palette().miss());
        ratio.set(s.total() == 0 ? "-" : String.format("%.1f%%", s.hitPercent()),
                s.total() == 0 ? "no requests yet" : String.format("miss ratio %.1f%%", s.missPercent()));
        evictions.set(String.format("%,d", s.evictions()), null);
        stepLabel.setText("Step " + String.format("%,d", sim.position()) + " / " + String.format("%,d", sim.total()));
        strip.setSimulation(sim);
    }

    private void describe(StepRecord r) {
        if (r.hit()) {
            badge.show(StatusBadge.Kind.HIT, "HIT");
            message.setText("Request " + r.key() + " is already cached (block " + r.slot() + ").");
        } else if (r.evicted() != null) {
            badge.show(StatusBadge.Kind.MISS, "MISS + EVICT");
            message.setText("Request " + r.key() + " is not cached. Cache is full, so " + r.evicted()
                    + " was evicted and " + r.key() + " took block " + r.slot() + ".");
        } else {
            badge.show(StatusBadge.Kind.MISS, "MISS");
            message.setText("Request " + r.key() + " is not cached. There was room, so it went into block "
                    + r.slot() + ".");
        }
    }

    private void showFinished(Simulation sim) {
        CacheStats s = sim.stats();
        badge.show(StatusBadge.Kind.INFO, "DONE");
        message.setText(String.format("Finished: %,d hits, %,d misses (%.1f%% hit ratio), %,d evictions. "
                + "Press Reset to run again.", s.hits(), s.misses(), s.hitPercent(), s.evictions()));
    }

    /** Receives controller events and updates the widgets. */
    private final class SimulationListener implements SimulationController.Listener {

        @Override
        public void onConfigured(Simulation sim) {
            error.setText(" ");
            traceModel.clear();
            badge.show(StatusBadge.Kind.IDLE, "READY");
            if (sim == null) {
                blocks.setCapacity(capacity.getValue());
                strip.setSimulation(null);
                stepLabel.setText("Step 0 / 0");
                hits.set("-", null);
                misses.set("-", null);
                ratio.set("-", null);
                evictions.set("-", null);
                message.setText("Fix the request list to continue.");
                traceNote.setText(" ");
            } else {
                blocks.setCapacity(sim.capacity());
                blocks.clearHighlight();
                renderState(sim);
                message.setText("Ready. Press Step Forward to process request " + sim.peekNext() + ".");
                traceNote.setText(sim.isDetailed() ? " "
                        : "Per-step trace is hidden above " + String.format("%,d", Simulation.MAX_DETAILED_STEPS)
                        + " requests; totals are still exact.");
            }
            updateButtons();
        }

        @Override
        public void onStep(Simulation sim, StepRecord rec) {
            renderState(sim);
            blocks.highlight(rec.slot(), rec.hit(), rec.evicted());
            describe(rec);
            if (sim.isDetailed()) {
                traceModel.add(rec);
                int last = traceModel.getRowCount() - 1;
                trace.scrollRectToVisible(trace.getCellRect(last, 0, true));
            }
            if (sim.isFinished()) {
                showFinished(sim);
            }
            updateButtons();
        }

        @Override
        public void onRunStarted() {
            progress.setValue(0);
            progress.setVisible(true);
            blocks.clearHighlight();
            strip.setBusy(true);
            badge.show(StatusBadge.Kind.INFO, "RUNNING");
            message.setText("Running in the background...");
            updateButtons();
        }

        @Override
        public void onRunProgress(int percent) {
            progress.setValue(percent);
        }

        @Override
        public void onRunFinished(Simulation sim, boolean completed) {
            progress.setVisible(false);
            strip.setBusy(false);
            renderState(sim);
            traceModel.setAll(sim.steps());
            if (completed) {
                showFinished(sim);
            } else {
                badge.show(StatusBadge.Kind.INFO, "CANCELLED");
                message.setText("Cancelled after " + String.format("%,d", sim.position())
                        + " requests. Step or run again to continue, or Reset to start over.");
            }
            updateButtons();
        }

        @Override
        public void onCompleted(Simulation sim) {
            app.announce("Simulation saved to history: " + sim.algorithm().shortName()
                    + String.format(", %.1f%% hit ratio", sim.stats().hitPercent()));
        }

        @Override
        public void onError(String msg) {
            error.setText(msg);
            SwingUtilities.invokeLater(() -> updateButtons());
        }
    }

    // ------------------------------------------------------------------ trace table

    private static final class TraceModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Step", "Request", "Result", "Evicted", "Cache after"};
        private final List<StepRecord> rows = new ArrayList<>();

        void add(StepRecord r) {
            rows.add(r);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void setAll(List<StepRecord> all) {
            rows.clear();
            rows.addAll(all);
            fireTableDataChanged();
        }

        void clear() {
            rows.clear();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Object getValueAt(int row, int col) {
            StepRecord r = rows.get(row);
            return switch (col) {
                case 0 -> r.step();
                case 1 -> r.key();
                case 2 -> r.outcome();
                case 3 -> r.evicted() == null ? "" : String.valueOf(r.evicted());
                default -> r.contentsText();
            };
        }
    }
}
