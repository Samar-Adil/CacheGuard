package com.cacheguard.view;

import com.cacheguard.controller.AppController;
import com.cacheguard.controller.SettingsController;
import com.cacheguard.model.AppState;
import com.cacheguard.model.AppTheme;
import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.model.Settings;
import com.cacheguard.view.components.RoundedPanel;
import com.cacheguard.view.components.Ui;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Theme, defaults, and import / export of preferences and history.
 */
final class SettingsPanel extends ScreenPanel {

    private static final long serialVersionUID = 1L;

    private final transient AppController app;
    private final transient SettingsController controller;
    private final JRadioButton dark = new JRadioButton("Dark");
    private final JRadioButton light = new JRadioButton("Light");
    private final JSpinner capacity = new JSpinner(new SpinnerNumberModel(Settings.DEFAULT_CAPACITY,
            Settings.MIN_CAPACITY, Settings.MAX_CAPACITY, 1));
    private final JComboBox<CacheAlgorithm> algorithm = new JComboBox<>(CacheAlgorithm.values());
    private final JLabel historyInfo = Ui.muted(" ");
    private boolean updating;

    SettingsPanel(AppController app) {
        super("Settings", "Appearance, defaults, and your data");
        this.app = app;
        this.controller = app.settings();

        addRow(appearanceCard());
        addRow(defaultsCard());
        addRow(dataCard());

        app.state().addPropertyChangeListener(AppState.SETTINGS, e -> syncFromModel());
        app.state().addPropertyChangeListener(AppState.HISTORY, e -> updateHistoryInfo());
        syncFromModel();
        updateHistoryInfo();
    }

    // ------------------------------------------------------------------ cards

    private JComponent appearanceCard() {
        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        GridBagConstraints c = base();
        ButtonGroup group = new ButtonGroup();
        group.add(dark);
        group.add(light);
        dark.addActionListener(e -> theme(AppTheme.DARK));
        light.addActionListener(e -> theme(AppTheme.LIGHT));

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        card.add(Ui.section("Appearance"), c);
        c.gridwidth = 1;
        c.gridy = 1;
        card.add(Ui.muted("Theme"), c);
        c.gridx = 1;
        card.add(dark, c);
        c.gridx = 2;
        c.weightx = 1;
        card.add(light, c);
        return card;
    }

    private JComponent defaultsCard() {
        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        GridBagConstraints c = base();
        capacity.setToolTipText("Used when the Simulation and Comparison screens open");
        capacity.addChangeListener(e -> {
            if (!updating) {
                controller.setDefaultCapacity((Integer) capacity.getValue());
            }
        });
        algorithm.addActionListener(e -> {
            if (!updating) {
                controller.setDefaultAlgorithm((CacheAlgorithm) algorithm.getSelectedItem());
            }
        });
        JButton reset = Ui.button("Reset all preferences");
        reset.addActionListener(e -> {
            controller.resetToDefaults();
            app.announce("Preferences reset to defaults.");
        });

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        card.add(Ui.section("Defaults"), c);
        c.gridwidth = 1;
        c.gridy = 1;
        card.add(Ui.muted("Cache capacity"), c);
        c.gridx = 1;
        card.add(capacity, c);
        c.gridx = 2;
        c.weightx = 1;
        card.add(Ui.muted("blocks (2 to 16)"), c);
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 2;
        card.add(Ui.muted("Algorithm"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        card.add(algorithm, c);
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 3;
        c.insets = new Insets(12, 0, 0, 0);
        card.add(reset, c);
        return card;
    }

    private JComponent dataCard() {
        RoundedPanel card = new RoundedPanel(new GridBagLayout());
        GridBagConstraints c = base();
        JButton export = Ui.button("Export...");
        JButton importBtn = Ui.button("Import...");
        JButton open = Ui.button("Open data folder");
        JButton clear = Ui.button("Clear history");
        export.addActionListener(e -> exportData());
        importBtn.addActionListener(e -> importData());
        open.addActionListener(e -> openFolder());
        clear.addActionListener(e -> clearHistory());
        open.setEnabled(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN));

        c.gridx = 0;
        c.gridy = 0;
        card.add(Ui.section("Data"), c);
        c.gridy = 1;
        card.add(Ui.muted("<html>Preferences and history are stored as JSON in<br><b>"
                + controller.dataDirectory() + "</b></html>"), c);
        c.gridy = 2;
        card.add(historyInfo, c);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(export);
        buttons.add(importBtn);
        buttons.add(open);
        buttons.add(clear);
        c.gridy = 3;
        c.weightx = 1;
        c.insets = new Insets(12, 0, 0, 0);
        card.add(buttons, c);
        return card;
    }

    private static GridBagConstraints base() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 0, 5, 18);
        return c;
    }

    // ------------------------------------------------------------------ actions

    private void theme(AppTheme theme) {
        if (!updating) {
            controller.setTheme(theme);
        }
    }

    private void syncFromModel() {
        Settings s = controller.current();
        updating = true;
        try {
            dark.setSelected(s.theme() == AppTheme.DARK);
            light.setSelected(s.theme() == AppTheme.LIGHT);
            capacity.setValue(s.defaultCapacity());
            algorithm.setSelectedItem(s.defaultAlgorithm());
        } finally {
            updating = false;
        }
    }

    private void updateHistoryInfo() {
        int n = controller.historySize();
        historyInfo.setText(n + (n == 1 ? " session" : " sessions") + " in history (the latest "
                + AppState.MAX_HISTORY + " are kept)");
    }

    /** Also reachable from the File menu. */
    void exportData() {
        JCheckBox prefs = new JCheckBox("Preferences", true);
        JCheckBox hist = new JCheckBox("Simulation history (" + controller.historySize() + " sessions)", true);
        int choice = JOptionPane.showConfirmDialog(this, new Object[] {"What should be exported?", prefs, hist},
                "Export", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        if (!prefs.isSelected() && !hist.isSelected()) {
            JOptionPane.showMessageDialog(this, "Choose at least one thing to export.", "Export",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export CacheGuard data");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
        chooser.setSelectedFile(new File("cacheguard-export-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase().endsWith(".json")) {
            target = target.resolveSibling(target.getFileName() + ".json");
        }
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this,
                target.getFileName() + " already exists. Replace it?", "Export",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            controller.exportTo(target, prefs.isSelected(), hist.isSelected());
            app.announce("Exported to " + target);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not export: " + ex.getMessage(), "Export failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Also reachable from the File menu. */
    void importData() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import CacheGuard data");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        SettingsController.ImportMode mode = SettingsController.ImportMode.MERGE;
        if (controller.historySize() > 0) {
            Object[] options = {"Merge", "Replace history", "Cancel"};
            int pick = JOptionPane.showOptionDialog(this,
                    "You already have " + controller.historySize() + " sessions in your history.\n"
                            + "Merge the imported sessions into it, or replace it?",
                    "Import", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (pick == 2 || pick == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (pick == 1) {
                mode = SettingsController.ImportMode.REPLACE;
            }
        }
        try {
            SettingsController.ImportSummary r = controller.importFrom(chooser.getSelectedFile().toPath(), mode);
            StringBuilder msg = new StringBuilder();
            if (r.settingsApplied()) {
                msg.append("Preferences imported.\n");
            }
            msg.append(r.sessionsImported()).append(" sessions imported.");
            if (r.skipped() > 0) {
                msg.append("\n").append(r.skipped()).append(" unreadable entries were skipped.");
            }
            JOptionPane.showMessageDialog(this, msg.toString(), "Import complete", JOptionPane.INFORMATION_MESSAGE);
            app.announce("Import complete.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not import: " + ex.getMessage(), "Import failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearHistory() {
        if (controller.historySize() == 0) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this,
                "Delete all " + controller.historySize() + " sessions from the history?\n"
                        + "Export them first if you want a copy.",
                "Clear history", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            controller.clearHistory();
            app.announce("History cleared.");
        }
    }

    private void openFolder() {
        try {
            Path dir = controller.dataDirectory();
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException | RuntimeException ex) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Could not open the folder: " + ex.getMessage(), "Open folder", JOptionPane.ERROR_MESSAGE));
        }
    }
}
