package com.cacheguard.view;

import com.cacheguard.controller.AppController;
import com.cacheguard.controller.ComparisonController;
import com.cacheguard.model.AppState;
import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.model.SimulationResult;
import com.cacheguard.model.WorkloadGenerator;
import com.cacheguard.model.WorkloadPattern;
import com.cacheguard.util.RequestParser;
import com.cacheguard.util.ThemeManager;
import com.cacheguard.view.components.BarChartPanel;
import com.cacheguard.view.components.RoundedPanel;
import com.cacheguard.view.components.TableRenderers;
import com.cacheguard.view.components.Ui;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Runs all five algorithms on one identical request stream and shows them side by side.
 */
final class ComparisonPanel extends ScreenPanel {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_SEQUENCE =
            "1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5, 6, 1, 2, 3, 7, 1, 2, 8, 1, 2, 3, 4";

    private final transient AppController app;
    private final transient ComparisonController controller;

    private final JSpinner capacity = new JSpinner(new SpinnerNumberModel(4, ComparisonController.MIN_CAPACITY,
            ComparisonController.MAX_CAPACITY, 1));
    private final JRadioButton customMode = new JRadioButton("Custom sequence", true);
    private final JRadioButton generatedMode = new JRadioButton("Generated workload");
    private final JTextField sequence = new JTextField(DEFAULT_SEQUENCE);
    private final JComboBox<WorkloadPattern> pattern = new JComboBox<>(WorkloadPattern.values());
    private final JSpinner count = new JSpinner(new SpinnerNumberModel(200_000, 10, RequestParser.MAX_REQUESTS, 50_000));
    private final JSpinner keySpace = new JSpinner(new SpinnerNumberModel(64, 2, WorkloadGenerator.MAX_KEY_SPACE, 16));
    private final JSpinner seed = new JSpinner(new SpinnerNumberModel(42L, 0L, Long.MAX_VALUE, 1L));
    private final JLabel patternInfo = Ui.muted(" ");
    private final JButton run = Ui.primaryButton("Run comparison");
    private final JButton cancel = Ui.button("Cancel");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel error = Ui.errorLabel();

    private final JLabel summary = new JLabel("Run a comparison to see results.");
    private final ResultsModel model = new ResultsModel();
    private final JTable table = new JTable(model);
    private final BarChartPanel hitChart = new BarChartPanel();
    private final BarChartPanel evictionChart = new BarChartPanel();
    private ComparisonController.Report lastReport;

    ComparisonPanel(AppController app) {
        super("Algorithm comparison", "The same requests, five policies, side by side");
        this.app = app;
        this.controller = app.comparison();
        capacity.setValue(app.state().settings().defaultCapacity());

        addRow(buildControls());
        addRow(buildResults());
        addRow(buildCharts());

        controller.addListener(new Handler());
        app.state().addPropertyChangeListener(AppState.SETTINGS, e -> {
            if (lastReport == null && !controller.isRunning()) {
                capacity.setValue(app.state().settings().defaultCapacity());
            }
        });
        ThemeManager.addListener(this::refreshCharts);
        updateMode();
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildControls() {
        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 4, 12);

        ButtonGroup group = new ButtonGroup();
        group.add(customMode);
        group.add(generatedMode);
        customMode.addActionListener(e -> updateMode());
        generatedMode.addActionListener(e -> updateMode());
        pattern.addActionListener(e -> patternInfo.setText(((WorkloadPattern) pattern.getSelectedItem()).description()));
        patternInfo.setText(WorkloadPattern.UNIFORM.description());
        capacity.setToolTipText("Blocks the cache can hold (2 to 4,096)");
        count.setToolTipText("Number of requests to generate (up to 5,000,000)");
        keySpace.setToolTipText("How many different blocks can be requested");
        seed.setToolTipText("Same seed, same requests: makes runs repeatable");
        Ui.placeholder(sequence, "e.g. 1, 2, 3, 4, 1, 2, 5");
        progress.setStringPainted(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(180, progress.getPreferredSize().height));
        cancel.setVisible(false);

        c.gridx = 0;
        c.gridy = 0;
        card.add(Ui.muted("Cache capacity"), c);
        c.gridx = 1;
        card.add(capacity, c);
        c.gridx = 2;
        card.add(Ui.muted("blocks"), c);

        c.gridx = 0;
        c.gridy = 1;
        card.add(customMode, c);
        c.gridx = 1;
        c.gridwidth = 6;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        card.add(sequence, c);
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy = 2;
        card.add(generatedMode, c);
        JPanel gen = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        gen.setOpaque(false);
        gen.add(pattern);
        gen.add(Ui.muted("requests"));
        gen.add(count);
        gen.add(Ui.muted("distinct blocks"));
        gen.add(keySpace);
        gen.add(Ui.muted("seed"));
        gen.add(seed);
        c.gridx = 1;
        c.gridwidth = 6;
        card.add(gen, c);
        c.gridwidth = 1;

        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 6;
        card.add(patternInfo, c);
        c.gridy = 4;
        card.add(error, c);
        c.gridwidth = 1;

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(run);
        actions.add(cancel);
        actions.add(progress);
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 7;
        c.insets = new Insets(12, 0, 0, 0);
        card.add(actions, c);

        run.addActionListener(e -> start());
        cancel.addActionListener(e -> controller.cancel());
        return card;
    }

    private JComponent buildResults() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.section("Results"), BorderLayout.WEST);
        card.add(head, BorderLayout.NORTH);

        Ui.style(table);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(1).setCellRenderer(TableRenderers.algorithm());
        table.getColumnModel().getColumn(2).setCellRenderer(TableRenderers.number());
        table.getColumnModel().getColumn(3).setCellRenderer(TableRenderers.number());
        table.getColumnModel().getColumn(4).setCellRenderer(TableRenderers.percentBar());
        table.getColumnModel().getColumn(5).setCellRenderer(TableRenderers.decimal(2));
        table.getColumnModel().getColumn(6).setCellRenderer(TableRenderers.number());
        table.getColumnModel().getColumn(7).setCellRenderer(TableRenderers.decimal(1));
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(190);
        table.getTableHeader().setToolTipText("Click a column header to sort");
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setPreferredSize(new Dimension(500, 6 * 31 + 6));
        sp.setMinimumSize(new Dimension(300, 6 * 31 + 6));
        card.add(sp, BorderLayout.CENTER);

        summary.setFont(summary.getFont().deriveFont(13.5f));
        card.add(summary, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildCharts() {
        JPanel row = new JPanel(new GridLayout(1, 2, 16, 0));
        row.setOpaque(false);
        hitChart.setFixedMax(100);
        hitChart.setUnit("%", 1);
        hitChart.setEmptyMessage("Run a comparison to see hit ratios");
        evictionChart.setUnit("", 0);
        evictionChart.setEmptyMessage("Run a comparison to see evictions");
        row.add(chartCard("Hit ratio (%)", hitChart));
        row.add(chartCard("Evictions", evictionChart));
        return row;
    }

    private static JComponent chartCard(String title, JComponent chart) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 8));
        card.add(Ui.section(title), BorderLayout.NORTH);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------ behaviour

    private void updateMode() {
        boolean custom = customMode.isSelected();
        sequence.setEnabled(custom && !controller.isRunning());
        boolean gen = !custom && !controller.isRunning();
        pattern.setEnabled(gen);
        count.setEnabled(gen);
        keySpace.setEnabled(gen);
        seed.setEnabled(gen);
        patternInfo.setVisible(!custom);
    }

    private void setBusy(boolean busy) {
        run.setEnabled(!busy);
        cancel.setVisible(busy);
        progress.setVisible(busy);
        capacity.setEnabled(!busy);
        customMode.setEnabled(!busy);
        generatedMode.setEnabled(!busy);
        updateMode();
    }

    private void start() {
        error.setText(" ");
        int cap = (Integer) capacity.getValue();
        if (customMode.isSelected()) {
            String text = sequence.getText();
            controller.run(cap, "custom", () -> RequestParser.parse(text));
        } else {
            WorkloadPattern p = (WorkloadPattern) pattern.getSelectedItem();
            int n = (Integer) count.getValue();
            int keys = (Integer) keySpace.getValue();
            long s = (Long) seed.getValue();
            controller.run(cap, p.label(), () -> WorkloadGenerator.generate(p, n, keys, s));
        }
    }

    private void refreshCharts() {
        if (lastReport != null) {
            showCharts(lastReport);
        }
    }

    private void showCharts(ComparisonController.Report report) {
        ThemeManager.Palette pal = ThemeManager.palette();
        List<BarChartPanel.Bar> hit = new ArrayList<>();
        List<BarChartPanel.Bar> ev = new ArrayList<>();
        for (SimulationResult r : report.results()) {
            Color color = pal.algorithmColors()[r.algorithm().ordinal()];
            hit.add(new BarChartPanel.Bar(r.algorithm().shortName(), r.stats().hitPercent(), color));
            ev.add(new BarChartPanel.Bar(r.algorithm().shortName(), r.stats().evictions(), color));
        }
        hitChart.setBars(hit);
        evictionChart.setBars(ev);
    }

    /** Receives controller events. */
    private final class Handler implements ComparisonController.Listener {
        @Override
        public void onStarted() {
            progress.setValue(0);
            setBusy(true);
            summary.setText("Running all algorithms in parallel...");
        }

        @Override
        public void onProgress(int percent) {
            progress.setValue(percent);
        }

        @Override
        public void onResults(ComparisonController.Report report) {
            setBusy(false);
            lastReport = report;
            model.setResults(report.results());
            showCharts(report);
            Optional<SimulationResult> best = report.best();
            String bestText = best.map(b -> String.format("Best: %s with %.2f%% hit ratio. ",
                    b.algorithm().shortName(), b.stats().hitPercent())).orElse("");
            summary.setText(String.format("%s%,d requests, %,d blocks, workload: %s. Finished in %,d ms.",
                    bestText, report.requestCount(), report.capacity(), report.workload(), report.elapsedMillis()));
            app.announce("Comparison saved to history (5 sessions).");
        }

        @Override
        public void onCancelled() {
            setBusy(false);
            summary.setText("Comparison cancelled.");
        }

        @Override
        public void onError(String message) {
            setBusy(false);
            error.setText(message);
            summary.setText(lastReport == null ? "Run a comparison to see results." : summary.getText());
        }
    }

    // ------------------------------------------------------------------ table model

    private static final class ResultsModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Rank", "Algorithm", "Hits", "Misses", "Hit ratio",
            "Miss ratio (%)", "Evictions", "Time (ms)"};

        private record Row(int rank, SimulationResult result) {
        }

        private List<Row> rows = List.of();

        void setResults(List<SimulationResult> results) {
            List<SimulationResult> sorted = new ArrayList<>(results);
            sorted.sort(Comparator.comparingLong((SimulationResult r) -> r.stats().hits()).reversed()
                    .thenComparingLong(r -> r.stats().evictions()));
            List<Row> next = new ArrayList<>();
            for (int i = 0; i < sorted.size(); i++) {
                next.add(new Row(i + 1, sorted.get(i)));
            }
            rows = next;
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
        public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0 -> Integer.class;
                case 1 -> CacheAlgorithm.class;
                case 2, 3, 6 -> Long.class;
                default -> Double.class;
            };
        }

        @Override
        public Object getValueAt(int row, int col) {
            Row r = rows.get(row);
            var s = r.result().stats();
            return switch (col) {
                case 0 -> r.rank();
                case 1 -> r.result().algorithm();
                case 2 -> s.hits();
                case 3 -> s.misses();
                case 4 -> s.hitPercent();
                case 5 -> s.missPercent();
                case 6 -> s.evictions();
                default -> r.result().elapsedMillis();
            };
        }
    }
}
