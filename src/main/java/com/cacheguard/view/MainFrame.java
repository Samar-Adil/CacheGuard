package com.cacheguard.view;

import com.cacheguard.controller.AppController;
import com.cacheguard.controller.Screen;
import com.cacheguard.model.AppTheme;
import com.cacheguard.util.AppInfo;
import com.cacheguard.util.ThemeManager;
import com.cacheguard.view.components.Ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * The application window: sidebar navigation, the five screens, a menu bar and a status line.
 */
public final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient AppController app;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<Screen, ScreenPanel> screens = new EnumMap<>(Screen.class);
    private final Map<Screen, JToggleButton> navButtons = new EnumMap<>(Screen.class);
    private final JLabel status = Ui.muted(" ", 12.5f);
    private final JButton themeButton = Ui.button("");
    private final Timer statusClear = new Timer(8000, e -> status.setText(" "));
    private final SettingsPanel settingsPanel;

    public MainFrame(AppController app) {
        super(AppInfo.TITLE);
        this.app = app;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setIconImages(java.util.List.of(appIcon(32), appIcon(64), appIcon(128)));

        settingsPanel = new SettingsPanel(app);
        screens.put(Screen.DASHBOARD, new DashboardPanel(app));
        screens.put(Screen.SIMULATION, new SimulationPanel(app));
        screens.put(Screen.COMPARISON, new ComparisonPanel(app));
        screens.put(Screen.STATISTICS, new StatisticsPanel(app));
        screens.put(Screen.SETTINGS, settingsPanel);
        for (Screen s : Screen.values()) {
            content.add(screens.get(s), s.name());
        }

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.add(content, BorderLayout.CENTER);
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 28, 8, 28));
        statusBar.add(status, BorderLayout.WEST);
        main.add(statusBar, BorderLayout.SOUTH);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
        setJMenuBar(buildMenuBar());

        statusClear.setRepeats(false);
        app.addNavigationListener(this::show);
        app.addStatusListener(this::announce);
        ThemeManager.addListener(this::updateThemeButton);
        updateThemeButton();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                app.shutdown();
            }
        });

        setMinimumSize(new Dimension(1000, 680));
        setSize(1320, 860);
        setLocationRelativeTo(null);
        show(Screen.DASHBOARD);
    }

    private void show(Screen screen) {
        cards.show(content, screen.name());
        navButtons.get(screen).setSelected(true);
        screens.get(screen).shown();
    }

    private void announce(String message) {
        status.setText(message);
        statusClear.restart();
    }

    // ------------------------------------------------------------------ sidebar

    private JPanel buildSidebar() {
        JPanel side = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(ThemeManager.palette().cardBorder());
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            }
        };
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(22, 16, 16, 16));
        side.setPreferredSize(new Dimension(224, 100));

        JLabel brand = new JLabel(AppInfo.NAME, new javax.swing.ImageIcon(appIcon(30)), SwingConstants.LEFT);
        brand.setIconTextGap(10);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 21f));
        brand.setAlignmentX(LEFT_ALIGNMENT);
        JLabel tag = Ui.muted("LRU / LFU / FIFO simulator", 12f);
        tag.setAlignmentX(LEFT_ALIGNMENT);
        tag.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        side.add(brand);
        side.add(tag);
        side.add(Box.createVerticalStrut(26));

        ButtonGroup group = new ButtonGroup();
        for (Screen s : Screen.values()) {
            JToggleButton b = new JToggleButton(s.title());
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setFont(b.getFont().deriveFont(14f));
            b.setMargin(new Insets(10, 14, 10, 14));
            b.setFocusPainted(false);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            b.setAlignmentX(LEFT_ALIGNMENT);
            b.putClientProperty("JButton.buttonType", "toolBar");
            b.setToolTipText(s.title() + " (Ctrl+" + (s.ordinal() + 1) + ")");
            b.addActionListener(e -> app.navigate(s));
            group.add(b);
            navButtons.put(s, b);
            side.add(b);
            side.add(Box.createVerticalStrut(4));
        }

        side.add(Box.createVerticalGlue());
        themeButton.setAlignmentX(LEFT_ALIGNMENT);
        themeButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        themeButton.addActionListener(e -> app.settings().toggleTheme());
        side.add(themeButton);
        side.add(Box.createVerticalStrut(10));
        JLabel version = Ui.muted("Version " + AppInfo.VERSION, 11.5f);
        version.setAlignmentX(LEFT_ALIGNMENT);
        side.add(version);
        return side;
    }

    private void updateThemeButton() {
        themeButton.setText(ThemeManager.current() == AppTheme.DARK ? "Switch to light theme" : "Switch to dark theme");
    }

    // ------------------------------------------------------------------ menu

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(item("Export data...", KeyStroke.getKeyStroke(KeyEvent.VK_E, shortcut()), settingsPanel::exportData));
        file.add(item("Import data...", KeyStroke.getKeyStroke(KeyEvent.VK_I, shortcut()), settingsPanel::importData));
        file.addSeparator();
        file.add(item("Exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcut()), this::close));
        bar.add(file);

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        for (Screen s : Screen.values()) {
            view.add(item(s.title(), KeyStroke.getKeyStroke(KeyEvent.VK_1 + s.ordinal(), shortcut()),
                    () -> app.navigate(s)));
        }
        view.addSeparator();
        view.add(item("Toggle dark / light theme", KeyStroke.getKeyStroke(KeyEvent.VK_T, shortcut()),
                () -> app.settings().toggleTheme()));
        bar.add(view);

        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        help.add(item("Keyboard shortcuts", null, this::showShortcuts));
        help.add(item("About " + AppInfo.NAME, null, this::showAbout));
        bar.add(help);
        return bar;
    }

    private static int shortcut() {
        return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private static JMenuItem item(String text, KeyStroke key, Runnable action) {
        JMenuItem m = new JMenuItem(new AbstractAction(text) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        if (key != null) {
            m.setAccelerator(key);
        }
        return m;
    }

    private void close() {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        dispose();
    }

    private void showShortcuts() {
        JOptionPane.showMessageDialog(this,
                "Ctrl+1 .. Ctrl+5     Switch screen\n"
                        + "Ctrl+T               Toggle dark / light theme\n"
                        + "Ctrl+E / Ctrl+I      Export / import data\n\n"
                        + "On the Simulation screen:\n"
                        + "Ctrl+Right           Step forward\n"
                        + "Ctrl+Enter           Run all (or cancel a running one)\n"
                        + "Ctrl+R               Reset",
                "Keyboard shortcuts", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                AppInfo.TITLE + "\nVersion " + AppInfo.VERSION + "\n\n"
                        + "An educational simulator for cache replacement policies:\n"
                        + "LRU, LFU, FIFO, MRU and Random.\n\n"
                        + "Java " + Runtime.version() + "  |  " + ThemeManager.lookAndFeelName(),
                "About " + AppInfo.NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ icon

    /** Draws the app icon: three stacked cache blocks on a blue rounded square. */
    static BufferedImage appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x3B82F6));
            g.fillRoundRect(0, 0, size, size, size / 3, size / 3);
            g.setColor(Color.WHITE);
            int pad = size / 5;
            int bw = size - 2 * pad;
            int bh = size / 6;
            int gap = (size - 2 * pad - 3 * bh) / 2;
            for (int i = 0; i < 3; i++) {
                int w = i == 1 ? bw : bw - size / 8;
                g.fillRoundRect(pad, pad + i * (bh + gap), w, bh, bh / 2, bh / 2);
            }
        } finally {
            g.dispose();
        }
        return img;
    }
}
