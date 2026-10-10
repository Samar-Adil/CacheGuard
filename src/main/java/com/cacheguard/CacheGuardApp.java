package com.cacheguard;

import com.cacheguard.controller.AppController;
import com.cacheguard.util.AppInfo;
import com.cacheguard.util.ThemeManager;
import com.cacheguard.view.MainFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point of CacheGuard.
 */
public final class CacheGuardApp {

    private static final Logger LOG = Logger.getLogger(CacheGuardApp.class.getName());

    private CacheGuardApp() {
    }

    public static void main(String[] args) {
        // Must be set before the first AWT call; harmless on other platforms.
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", AppInfo.NAME);

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            LOG.log(Level.SEVERE, "Unexpected error in thread " + thread.getName(), error);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Something went wrong:\n" + error + "\n\nDetails were written to the console log.",
                    AppInfo.NAME, JOptionPane.ERROR_MESSAGE));
        });

        AppController app = AppController.bootstrap();
        SwingUtilities.invokeLater(() -> {
            ThemeManager.apply(app.state().settings().theme());
            new MainFrame(app).setVisible(true);
        });
    }
}
