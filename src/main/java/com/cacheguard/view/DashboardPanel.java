package com.cacheguard.view;

import com.cacheguard.controller.AppController;
import com.cacheguard.controller.Screen;
import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.model.StatisticsSummary;
import com.cacheguard.util.AppInfo;
import com.cacheguard.util.ThemeManager;
import com.cacheguard.view.components.RoundedPanel;
import com.cacheguard.view.components.SessionTableModel;
import com.cacheguard.view.components.StatCard;
import com.cacheguard.view.components.TableRenderers;
import com.cacheguard.view.components.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.Optional;

/**
 * Overview: headline numbers, quick navigation, system specs, recent sessions
 * and a cheat sheet of the algorithms.
 */
final class DashboardPanel extends ScreenPanel {

    private static final long serialVersionUID = 1L;

    private final transient AppController app;
    private final StatCard sessions = new StatCard("Sessions run");
    private final StatCard requests = new StatCard("Requests simulated");
    private final StatCard hitRatio = new StatCard("Overall hit ratio");
    private final StatCard top = new StatCard("Top algorithm");
    private final SessionTableModel recent = new SessionTableModel(8);
    private final JLabel heapValue = new JLabel();
    private final JLabel lafValue = new JLabel();

    DashboardPanel(AppController app) {
        super("Dashboard", "Your cache experiments at a glance");
        this.app = app;

        JPanel cards = new JPanel(new GridLayout(1, 4, 16, 0));
        cards.setOpaque(false);
        cards.add(sessions);
        cards.add(requests);
        cards.add(hitRatio);
        cards.add(top);
        addRow(cards);

        JPanel middle = new JPanel(new GridBagLayout());
        middle.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        gc.gridy = 0;
        gc.gridx = 0;
        gc.weightx = 0.42;
        gc.insets = new Insets(0, 0, 0, 8);
        middle.add(quickActions(), gc);
        gc.gridx = 1;
        gc.weightx = 0.58;
        gc.insets = new Insets(0, 8, 0, 0);
        middle.add(systemSpecs(), gc);
        addRow(middle);

        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setOpaque(false);
        gc.gridx = 0;
        gc.weightx = 0.55;
        gc.insets = new Insets(0, 0, 0, 8);
        bottom.add(recentSessions(), gc);
        gc.gridx = 1;
        gc.weightx = 0.45;
        gc.insets = new Insets(0, 8, 0, 0);
        bottom.add(algorithmGuide(), gc);
        addRow(bottom);

        app.statistics().addListener(this::refresh);
        refresh();

        Timer heap = new Timer(2000, e -> updateHeap());
        heap.setInitialDelay(0);
        heap.start();
    }

    @Override
    protected void onShown() {
        refresh();
        updateHeap();
    }

    private void refresh() {
        StatisticsSummary s = app.statistics().summary();
        sessions.set(String.format("%,d", s.sessions()), s.sessions() == 0 ? "Nothing run yet" : "saved in history");
        requests.set(String.format("%,d", s.requests()), "across all sessions");
        if (s.requests() == 0) {
            hitRatio.set("-", "run a simulation");
        } else {
            hitRatio.set(String.format("%.1f%%", s.overallHitPercent()),
                    String.format("%,d hits / %,d misses", s.hits(), s.misses()));
        }
        Optional<StatisticsSummary.AlgorithmStats> best = s.topAlgorithm();
        if (best.isPresent()) {
            top.set(best.get().algorithm().shortName(),
                    String.format("avg %.1f%% hit ratio", best.get().averageHitPercent()));
        } else {
            top.set("-", "needs at least one session");
        }
        recent.setHistory(app.statistics().history());
    }

    private JComponent quickActions() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 12));
        card.add(Ui.section("Quick actions"), BorderLayout.NORTH);
        JPanel list = new JPanel(new GridLayout(4, 1, 0, 10));
        list.setOpaque(false);
        list.add(tile("Run a simulation", "Step through requests and watch the cache change", Screen.SIMULATION));
        list.add(tile("Compare algorithms", "Replay one request stream through all five policies", Screen.COMPARISON));
        list.add(tile("View statistics", "Charts and totals from your history", Screen.STATISTICS));
        list.add(tile("Settings", "Theme, defaults, import and export", Screen.SETTINGS));
        card.add(list, BorderLayout.CENTER);
        return card;
    }

    private JButton tile(String title, String description, Screen target) {
        JButton b = Ui.button("<html><b>" + title + "</b><br><span style='font-size:90%'>" + description
                + "</span></html>");
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMargin(new Insets(8, 14, 8, 14));
        b.addActionListener(e -> app.navigate(target));
        return b;
    }

    private JComponent systemSpecs() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 12));
        card.add(Ui.section("System"), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        int row = 0;
        row = spec(grid, row, "Application", AppInfo.NAME + " " + AppInfo.VERSION);
        row = spec(grid, row, "Java", Runtime.version() + " (" + System.getProperty("java.vendor", "?") + ")");
        row = spec(grid, row, "JVM", System.getProperty("java.vm.name", "?"));
        row = spec(grid, row, "Operating system", System.getProperty("os.name", "?") + " "
                + System.getProperty("os.version", "") + " (" + System.getProperty("os.arch", "?") + ")");
        row = spec(grid, row, "CPU cores", String.valueOf(Runtime.getRuntime().availableProcessors()));
        row = specValue(grid, row, "Heap (used / max)", heapValue);
        row = spec(grid, row, "Display", displaySize());
        row = specValue(grid, row, "Look and feel", lafValue);
        spec(grid, row, "Data folder", app.settings().dataDirectory().toString());
        card.add(grid, BorderLayout.CENTER);
        updateHeap();
        lafValue.setText(ThemeManager.lookAndFeelName());
        ThemeManager.addListener(() -> lafValue.setText(ThemeManager.lookAndFeelName()));
        return card;
    }

    private static String displaySize() {
        if (GraphicsEnvironment.isHeadless()) {
            return "headless";
        }
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        return d.width + " x " + d.height;
    }

    private static int spec(JPanel grid, int row, String label, String value) {
        return specValue(grid, row, label, new JLabel(value));
    }

    private static int specValue(JPanel grid, int row, String label, JLabel value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(3, 0, 3, 16);
        c.gridx = 0;
        grid.add(Ui.muted(label, 12.5f), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 0);
        grid.add(value, c);
        return row + 1;
    }

    private void updateHeap() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long max = rt.maxMemory() >> 20;
        heapValue.setText(used + " MB / " + max + " MB");
    }

    private JComponent recentSessions() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 12));
        card.add(Ui.section("Recent sessions"), BorderLayout.NORTH);
        JTable table = Ui.style(new JTable(recent));
        table.setEnabled(false);
        table.getColumnModel().getColumn(2).setCellRenderer(TableRenderers.algorithm());
        table.getColumnModel().getColumn(3).setCellRenderer(TableRenderers.number());
        table.getColumnModel().getColumn(4).setCellRenderer(TableRenderers.number());
        table.getColumnModel().getColumn(5).setCellRenderer(TableRenderers.percentBar());
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setPreferredSize(new Dimension(300, 8 * 31 + 34));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private JComponent algorithmGuide() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 12));
        card.add(Ui.section("The five policies"), BorderLayout.NORTH);
        AbstractTableModel model = new AbstractTableModel() {
            private static final long serialVersionUID = 1L;

            @Override
            public int getRowCount() {
                return CacheAlgorithm.values().length;
            }

            @Override
            public int getColumnCount() {
                return 3;
            }

            @Override
            public String getColumnName(int c) {
                return new String[] {"Policy", "Data structure", "Access"}[c];
            }

            @Override
            public Object getValueAt(int r, int c) {
                CacheAlgorithm a = CacheAlgorithm.values()[r];
                return switch (c) {
                    case 0 -> a;
                    case 1 -> a.dataStructure();
                    default -> a.complexity();
                };
            }
        };
        JTable table = Ui.style(new JTable(model));
        table.setEnabled(false);
        table.getColumnModel().getColumn(0).setCellRenderer(TableRenderers.algorithm());
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(190);
        table.getColumnModel().getColumn(2).setPreferredWidth(50);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setPreferredSize(new Dimension(300, 5 * 31 + 34));
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        sp.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(sp);
        wrap.add(Box.createVerticalStrut(10));
        JLabel note = Ui.muted("<html>Every policy answers a request in O(1) amortised time, "
                + "so simulations stay fast even with millions of requests.</html>");
        note.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(note);
        card.add(wrap, BorderLayout.CENTER);
        return card;
    }
}
