package com.cacheguard.view;

import com.cacheguard.controller.AppController;
import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.model.SessionRecord;
import com.cacheguard.model.StatisticsSummary;
import com.cacheguard.util.ThemeManager;
import com.cacheguard.view.components.BarChartPanel;
import com.cacheguard.view.components.LineChartPanel;
import com.cacheguard.view.components.RoundedPanel;
import com.cacheguard.view.components.SessionTableModel;
import com.cacheguard.view.components.StatCard;
import com.cacheguard.view.components.TableRenderers;
import com.cacheguard.view.components.Ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Summary cards, charts and tables over the saved session history.
 */
final class StatisticsPanel extends ScreenPanel {

    private static final long serialVersionUID = 1L;
    private static final int TREND_SESSIONS = 60;

    private final transient AppController app;
    private final StatCard sessions = new StatCard("Sessions");
    private final StatCard requests = new StatCard("Requests");
    private final StatCard hitRatio = new StatCard("Overall hit ratio");
    private final StatCard evictions = new StatCard("Evictions");
    private final StatCard top = new StatCard("Top algorithm");
    private final BarChartPanel averageChart = new BarChartPanel();
    private final LineChartPanel trendChart = new LineChartPanel();
    private final BreakdownModel breakdown = new BreakdownModel();
    private final SessionTableModel history = new SessionTableModel(0);

    StatisticsPanel(AppController app) {
        super("Statistics", "What your saved simulations say about each policy");
        this.app = app;

        JPanel cards = new JPanel(new GridLayout(1, 5, 16, 0));
        cards.setOpaque(false);
        cards.add(sessions);
        cards.add(requests);
        cards.add(hitRatio);
        cards.add(evictions);
        cards.add(top);
        addRow(cards);

        averageChart.setFixedMax(100);
        averageChart.setUnit("%", 1);
        averageChart.setEmptyMessage("Run a simulation or comparison to see data");
        trendChart.setXLabel("Session number (oldest to newest)");
        trendChart.setEmptyMessage("Run a simulation or comparison to see data");
        JPanel charts = new JPanel(new GridLayout(1, 2, 16, 0));
        charts.setOpaque(false);
        charts.add(chartCard("Average hit ratio by algorithm", averageChart));
        charts.add(chartCard("Hit ratio per session (latest " + TREND_SESSIONS + ")", trendChart));
        addRow(charts);

        addRow(tableCard("Breakdown by algorithm", breakdownTable(), 6 * 31 + 14));
        addRow(tableCard("All sessions (newest first)", historyTable(), 8 * 31 + 6));

        app.statistics().addListener(this::refresh);
        ThemeManager.addListener(this::refresh);
        refresh();
    }

    @Override
    protected void onShown() {
        refresh();
    }

    private static JComponent chartCard(String title, JComponent chart) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 8));
        card.add(Ui.section(title), BorderLayout.NORTH);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    private static JComponent tableCard(String title, JTable table, int height) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.add(Ui.section(title), BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setPreferredSize(new Dimension(400, height));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private JTable breakdownTable() {
        JTable t = Ui.style(new JTable(breakdown));
        t.setEnabled(false);
        t.getColumnModel().getColumn(0).setCellRenderer(TableRenderers.algorithm());
        t.getColumnModel().getColumn(1).setCellRenderer(TableRenderers.number());
        t.getColumnModel().getColumn(2).setCellRenderer(TableRenderers.number());
        t.getColumnModel().getColumn(3).setCellRenderer(TableRenderers.percentBar());
        t.getColumnModel().getColumn(4).setCellRenderer(TableRenderers.percentBar());
        t.getColumnModel().getColumn(5).setCellRenderer(TableRenderers.number());
        return t;
    }

    private JTable historyTable() {
        JTable t = Ui.style(new JTable(history));
        t.setEnabled(false);
        t.getColumnModel().getColumn(2).setCellRenderer(TableRenderers.algorithm());
        t.getColumnModel().getColumn(3).setCellRenderer(TableRenderers.number());
        t.getColumnModel().getColumn(4).setCellRenderer(TableRenderers.number());
        t.getColumnModel().getColumn(5).setCellRenderer(TableRenderers.percentBar());
        return t;
    }

    private void refresh() {
        StatisticsSummary s = app.statistics().summary();
        List<SessionRecord> all = app.statistics().history();
        ThemeManager.Palette pal = ThemeManager.palette();

        sessions.set(String.format("%,d", s.sessions()), s.sessions() == 0 ? "no history yet" : "in history");
        requests.set(String.format("%,d", s.requests()), "processed");
        hitRatio.set(s.requests() == 0 ? "-" : String.format("%.1f%%", s.overallHitPercent()),
                String.format("%,d hits", s.hits()));
        evictions.set(String.format("%,d", s.evictions()), "blocks evicted");
        var best = s.topAlgorithm();
        top.set(best.map(b -> b.algorithm().shortName()).orElse("-"),
                best.map(b -> String.format("avg %.1f%%", b.averageHitPercent())).orElse("no data"));

        List<BarChartPanel.Bar> bars = new ArrayList<>();
        for (StatisticsSummary.AlgorithmStats a : s.byAlgorithm().values()) {
            bars.add(new BarChartPanel.Bar(a.algorithm().shortName(), a.averageHitPercent(),
                    pal.algorithmColors()[a.algorithm().ordinal()]));
        }
        averageChart.setBars(bars);

        int from = Math.max(0, all.size() - TREND_SESSIONS);
        Map<CacheAlgorithm, List<LineChartPanel.Point>> points = new EnumMap<>(CacheAlgorithm.class);
        for (int i = from; i < all.size(); i++) {
            SessionRecord r = all.get(i);
            points.computeIfAbsent(r.algorithm(), k -> new ArrayList<>())
                    .add(new LineChartPanel.Point(i + 1, r.hitPercent()));
        }
        List<LineChartPanel.Series> series = new ArrayList<>();
        for (CacheAlgorithm a : CacheAlgorithm.values()) {
            List<LineChartPanel.Point> p = points.get(a);
            if (p != null) {
                Color c = pal.algorithmColors()[a.ordinal()];
                series.add(new LineChartPanel.Series(a.shortName(), c, p));
            }
        }
        trendChart.setSeries(series);

        breakdown.set(new ArrayList<>(s.byAlgorithm().values()));
        history.setHistory(all);
    }

    private static final class BreakdownModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Algorithm", "Sessions", "Requests", "Average hit ratio",
            "Best hit ratio", "Evictions"};
        private List<StatisticsSummary.AlgorithmStats> rows = List.of();

        void set(List<StatisticsSummary.AlgorithmStats> next) {
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
        public Object getValueAt(int row, int col) {
            StatisticsSummary.AlgorithmStats a = rows.get(row);
            return switch (col) {
                case 0 -> a.algorithm();
                case 1 -> a.sessions();
                case 2 -> a.requests();
                case 3 -> a.averageHitPercent();
                case 4 -> a.bestHitPercent();
                default -> a.evictions();
            };
        }
    }
}
